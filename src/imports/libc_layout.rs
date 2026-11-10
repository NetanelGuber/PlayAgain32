/// ARM32 div_t contains two signed 32-bit words, quotient followed by remainder.
pub fn div_result(numerator: i32, denominator: i32) -> Option<[u8; 8]> {
    let mut result = [0; 8];
    result[..4].copy_from_slice(&numerator.checked_div(denominator)?.to_le_bytes());
    result[4..].copy_from_slice(&numerator.checked_rem(denominator)?.to_le_bytes());
    Some(result)
}

pub fn valid_environment_name(name: &[u8]) -> bool {
    !name.is_empty() && !name.contains(&b'=')
}

pub fn environment_value_offset(entry: &[u8], name: &[u8]) -> Option<usize> {
    if !valid_environment_name(name) { return None; }
    entry.strip_prefix(name).filter(|suffix| suffix.first() == Some(&b'=')).map(|_| name.len() + 1)
}

// Bionic ARM32 aligns the event's 64-bit data to 8, unlike packed x86-64 epoll_event.
pub const EPOLL_EVENT_SIZE: usize = 16;

pub fn read_epoll_event(bytes: &[u8]) -> (u32, u64) {
    (u32::from_le_bytes(bytes[..4].try_into().unwrap()), u64::from_le_bytes(bytes[8..16].try_into().unwrap()))
}

pub fn write_epoll_event(bytes: &mut [u8], events: u32, data: u64) {
    bytes[..4].copy_from_slice(&events.to_le_bytes());
    bytes[8..16].copy_from_slice(&data.to_le_bytes());
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn signed_quotient_and_remainder_follow_c_rules() {
        for (n, d, q, r) in [(17, 5, 3i32, 2i32), (-17, 5, -3, -2), (17, -5, -3, 2), (-17, -5, 3, -2), (i32::MIN, 1, i32::MIN, 0)] {
            let bytes = div_result(n, d).unwrap();
            assert_eq!(&bytes[..4], &q.to_le_bytes());
            assert_eq!(&bytes[4..], &r.to_le_bytes());
        }
    }

    #[test]
    fn undefined_c_divisions_do_not_panic() {
        assert_eq!(div_result(1, 0), None);
        assert_eq!(div_result(i32::MIN, -1), None);
    }

    #[test]
    fn environment_lookup_requires_the_whole_name_and_preserves_raw_bytes() {
        assert_eq!(environment_value_offset(b"PATH=/bin", b"PATH"), Some(5));
        assert_eq!(environment_value_offset(b"PATH_EXTRA=/bad", b"PATH"), None);
        assert_eq!(environment_value_offset(b"EMPTY=", b"EMPTY"), Some(6));
        assert_eq!(environment_value_offset(b"RAW=\xff", b"RAW"), Some(4));
        for invalid in [b"".as_slice(), b"A=B"] {
            assert!(!valid_environment_name(invalid));
            assert_eq!(environment_value_offset(b"A=B=C", invalid), None);
        }
    }

    #[test]
    fn epoll_preserves_64_bit_cookie_padding_and_adjacent_events() {
        let mut bytes = [0xa5; EPOLL_EVENT_SIZE * 2 + 4];
        write_epoll_event(&mut bytes[..16], 0x80000001, 0xfedcba9876543210);
        write_epoll_event(&mut bytes[16..32], 0x40000004, 0x0123456789abcdef);
        assert_eq!(read_epoll_event(&bytes[..16]), (0x80000001, 0xfedcba9876543210));
        assert_eq!(read_epoll_event(&bytes[16..32]), (0x40000004, 0x0123456789abcdef));
        assert_eq!(&bytes[4..8], &[0xa5; 4]);
        assert_eq!(&bytes[20..24], &[0xa5; 4]);
        assert_eq!(&bytes[32..], &[0xa5; 4]);
    }
}
