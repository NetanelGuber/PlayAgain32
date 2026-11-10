//! Linux ancillary-message headers: ARM32 size_t/alignment 4, ARM64 size_t/alignment 8.
#[derive(Debug, PartialEq)]
pub struct Message { pub level: i32, pub kind: i32, pub data: Vec<u8> }

pub fn decode(bytes: &[u8], wide: bool) -> Result<Vec<Message>, &'static str> {
    let word = if wide { 8 } else { 4 };
    let header = word + 8;
    let mut at = 0;
    let mut result = Vec::new();
    while bytes.len() - at >= header {
        let length = if wide { u64::from_le_bytes(bytes[at..at + 8].try_into().unwrap()) }
            else { u32::from_le_bytes(bytes[at..at + 4].try_into().unwrap()) as u64 };
        if length < header as u64 || length > (bytes.len() - at) as u64 { return Err("invalid ancillary length"); }
        let length = length as usize;
        result.push(Message {
            level: i32::from_le_bytes(bytes[at + word..at + word + 4].try_into().unwrap()),
            kind: i32::from_le_bytes(bytes[at + word + 4..at + header].try_into().unwrap()),
            data: bytes[at + header..at + length].to_vec(),
        });
        let step = (length + word - 1) & !(word - 1);
        if step > bytes.len() - at { break; }
        at += step;
    }
    Ok(result)
}

pub fn encode(messages: &[Message], wide: bool) -> Result<Vec<u8>, &'static str> {
    let word = if wide { 8 } else { 4 };
    let header = word + 8;
    let mut result = Vec::new();
    for message in messages {
        let length = header + message.data.len();
        let space = (length + word - 1) & !(word - 1);
        if space > 131072 || result.len() > 131072 - space { return Err("ancillary data too large"); }
        let at = result.len();
        result.resize(at + space, 0);
        if wide { result[at..at + 8].copy_from_slice(&(length as u64).to_le_bytes()); }
        else { result[at..at + 4].copy_from_slice(&(length as u32).to_le_bytes()); }
        result[at + word..at + word + 4].copy_from_slice(&message.level.to_le_bytes());
        result[at + word + 4..at + header].copy_from_slice(&message.kind.to_le_bytes());
        result[at + header..at + length].copy_from_slice(&message.data);
    }
    Ok(result)
}

pub fn convert_time(message: &mut Message, wide: bool) -> Result<(), &'static str> {
    let count = match (message.level, message.kind) { (1, 29 | 35) => 2, (1, 37) => 6, _ => return Ok(()) };
    let source = if wide { 4 } else { 8 };
    if message.data.len() != count * source { return Err("invalid timestamp control data"); }
    let mut data = Vec::new();
    for chunk in message.data.chunks_exact(source) {
        if wide { data.extend_from_slice(&(i32::from_le_bytes(chunk.try_into().unwrap()) as i64).to_le_bytes()); }
        else { data.extend_from_slice(&i32::try_from(i64::from_le_bytes(chunk.try_into().unwrap())).map_err(|_| "timestamp overflow")?.to_le_bytes()); }
    }
    message.data = data;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test] fn rights_round_trip_has_correct_32_and_64_bit_headers() {
        let messages = vec![Message { level: 1, kind: 1, data: 42i32.to_le_bytes().to_vec() }];
        let guest = encode(&messages, false).unwrap();
        let host = encode(&messages, true).unwrap();
        assert_eq!(guest.len(), 16); assert_eq!(host.len(), 24);
        assert_eq!(&guest[..4], &16u32.to_le_bytes());
        assert_eq!(&host[..8], &20u64.to_le_bytes());
        assert_eq!(decode(&guest, false).unwrap(), messages);
        assert_eq!(decode(&host, true).unwrap(), messages);
    }
    #[test] fn rejects_invalid_lengths_and_accepts_unpadded_final_message() {
        let mut raw = encode(&[Message { level: 1, kind: 2, data: vec![7] }], true).unwrap();
        raw.truncate(17); assert_eq!(decode(&raw, true).unwrap()[0].data, vec![7]);
        raw[..8].copy_from_slice(&u64::MAX.to_le_bytes()); assert!(decode(&raw, true).is_err());
        raw[..8].copy_from_slice(&8u64.to_le_bytes()); assert!(decode(&raw, true).is_err());
    }
    #[test] fn timestamp_conversion_preserves_signed_values_and_rejects_overflow() {
        let original = [-1i32, 123].into_iter().flat_map(i32::to_le_bytes).collect::<Vec<_>>();
        let mut m = Message { level: 1, kind: 29, data: original.clone() };
        convert_time(&mut m, true).unwrap(); assert_eq!(m.data.len(), 16);
        convert_time(&mut m, false).unwrap(); assert_eq!(m.data, original);
        m.data = [i64::MAX, 0].into_iter().flat_map(i64::to_le_bytes).collect();
        assert!(convert_time(&mut m, false).is_err());
    }
}
