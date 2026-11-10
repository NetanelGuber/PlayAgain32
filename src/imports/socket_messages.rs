use crate::runtime::{mem, ArgSource, Handler, Ret, Uc};
use crate::handler;
use super::message_layout::{self, Message};

fn fail(uc: &Uc, error: i32) -> Ret {
    mem().write_u32(uc.get_data().errno_slot, crate::runtime::guest_errno(error) as u32);
    Ret::U32(u32::MAX)
}

fn range(pointer: u32, size: u32) -> bool { size == 0 || (pointer != 0 && pointer.checked_add(size - 1).is_some()) }

fn close_rights(message: &Message) {
    if message.level == libc::SOL_SOCKET && matches!(message.kind, libc::SCM_RIGHTS | 4) {
        for fd in message.data.chunks_exact(4) { unsafe { libc::close(i32::from_le_bytes(fd.try_into().unwrap())); } }
    }
}

fn message(uc: &Uc, fd: i32, header: u32, flags: i32, receive: bool) -> Ret {
    if !range(header, 28) { return fail(uc, libc::EFAULT); }
    let m = mem();
    let (name, name_len, vectors, count, control, capacity) = (m.read_u32(header), m.read_u32(header + 4),
        m.read_u32(header + 8), m.read_u32(header + 12), m.read_u32(header + 16), m.read_u32(header + 20));
    if count > 1024 { return fail(uc, libc::EMSGSIZE); }
    if capacity > 65536 { return fail(uc, libc::ENOBUFS); }
    if !range(vectors, count * 8) || !range(name, name_len) || !range(control, capacity) { return fail(uc, libc::EFAULT); }
    let mut parts = Vec::new();
    let mut total = 0u64;
    for index in 0..count {
        let (data, length) = (m.read_u32(vectors + index * 8), m.read_u32(vectors + index * 8 + 4));
        if !range(data, length) { return fail(uc, libc::EFAULT); }
        total += length as u64;
        if total > i32::MAX as u64 { return fail(uc, libc::EMSGSIZE); }
        parts.push(libc::iovec { iov_base: m.host_or_null(data).cast(), iov_len: length as usize });
    }
    let mut ancillary = if receive {
        vec![0u8; if capacity == 0 { 0 } else { capacity as usize * 2 + 16 }]
    } else {
        let mut messages = match message_layout::decode(m.bytes(control, capacity as usize), false) { Ok(value) => value, Err(_) => return fail(uc, libc::EINVAL) };
        for message in &mut messages {
            if message_layout::convert_time(message, true).is_err() { return fail(uc, libc::EINVAL); }
            if message.level == libc::SOL_SOCKET && message.kind == libc::SCM_RIGHTS && message.data.len() % 4 != 0 { return fail(uc, libc::EINVAL); }
        }
        match message_layout::encode(&messages, true) { Ok(value) => value, Err(_) => return fail(uc, libc::ENOBUFS) }
    };
    let mut host: libc::msghdr = unsafe { std::mem::zeroed() };
    host.msg_name = m.host_or_null(name).cast(); host.msg_namelen = name_len;
    host.msg_iov = parts.as_mut_ptr(); host.msg_iovlen = parts.len() as _;
    host.msg_control = if ancillary.is_empty() { std::ptr::null_mut() } else { ancillary.as_mut_ptr().cast() };
    host.msg_controllen = ancillary.len() as _;
    let result = super::sockets::blocking(uc, || unsafe {
        if receive { libc::recvmsg(fd, &mut host, flags) } else { libc::sendmsg(fd, &host, flags) }
    } as i64);
    if !receive || !matches!(result, Ret::U32(n) if (n as i32) >= 0) { return result; }
    m.write_u32(header + 4, host.msg_namelen);
    let messages = match message_layout::decode(&ancillary[..(host.msg_controllen as usize).min(ancillary.len())], true) {
        Ok(value) => value, Err(_) => { m.write_u32(header + 20, 0); m.write_u32(header + 24, (host.msg_flags | libc::MSG_CTRUNC) as u32); return result; }
    };
    let mut out = Vec::new();
    let mut output_flags = host.msg_flags;
    for mut message in messages {
        if message_layout::convert_time(&mut message, false).is_err() {
            close_rights(&message); output_flags |= libc::MSG_CTRUNC; continue;
        }
        let available = capacity as usize - out.len();
        if available < 12 { close_rights(&message); output_flags |= libc::MSG_CTRUNC; continue; }
        let mut count = message.data.len().min(available - 12);
        let rights = message.level == libc::SOL_SOCKET && matches!(message.kind, libc::SCM_RIGHTS | 4);
        if rights { count &= !3; }
        if count < message.data.len() {
            if rights {
                for chunk in message.data[count..].chunks_exact(4) { unsafe { libc::close(i32::from_le_bytes(chunk.try_into().unwrap())); } }
            }
            message.data.truncate(count); output_flags |= libc::MSG_CTRUNC;
        }
        let encoded = message_layout::encode(&[message], false).expect("bounded ancillary message");
        let copy = encoded.len().min(available);
        out.extend_from_slice(&encoded[..copy]);
    }
    if !out.is_empty() { m.write_bytes(control, &out); }
    m.write_u32(header + 20, out.len() as u32); m.write_u32(header + 24, output_flags as u32);
    result
}

pub fn handler(name: &str) -> Option<Handler> {
    match name {
        "sendmsg" => handler!(|uc, a| { let (fd, header, flags) = (a.i32(), a.u32(), a.i32()); message(uc, fd, header, flags, false) }),
        "recvmsg" => handler!(|uc, a| { let (fd, header, flags) = (a.i32(), a.u32(), a.i32()); message(uc, fd, header, flags, true) }),
        _ => None,
    }
}
