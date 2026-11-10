//! Bionic ARM32 passwd/group results contain guest pointers, never host pointers.
use crate::{handler, runtime::{mem, shared, ArgSource, Handler, Ret, Uc}};

const LIMIT: usize = 65536;

fn fail(uc: &Uc, error: i32) -> Ret {
    mem().write_u32(uc.get_data().errno_slot, crate::runtime::guest_errno(error) as u32);
    Ret::U32(0)
}

unsafe fn text(pointer: *const libc::c_char) -> Option<Vec<u8>> {
    (!pointer.is_null()).then(|| std::ffi::CStr::from_ptr(pointer).to_bytes().to_vec())
}

fn area(uc: &Uc, group: bool, size: usize) -> Result<u32, i32> {
    if size > LIMIT { return Err(libc::ERANGE); }
    let key = (uc.get_data().id, group);
    let mut runtime = shared();
    if let Some(&(pointer, capacity)) = runtime.state.identity_buffers.get(&key) {
        if capacity as usize >= size { return Ok(pointer); }
    }
    runtime.state.identity_buffers.try_reserve(1).map_err(|_| libc::ENOMEM)?;
    let capacity = size.max(128).next_power_of_two() as u32;
    let pointer = crate::runtime::heap().malloc(capacity);
    if pointer == 0 { return Err(libc::ENOMEM); }
    if let Some((old, _)) = runtime.state.identity_buffers.insert(key, (pointer, capacity)) {
        crate::runtime::heap().free(old);
    }
    Ok(pointer)
}

fn write_text(at: &mut u32, value: Option<&Vec<u8>>) -> u32 {
    let Some(value) = value else { return 0 };
    let result = *at;
    mem().write_bytes(result, value);
    mem().write_u8(result + value.len() as u32, 0);
    *at += value.len() as u32 + 1;
    result
}

fn lookup(uc: &Uc, argument: u32, by_name: bool, group: bool) -> Ret {
    if by_name && argument == 0 { return fail(uc, libc::EINVAL); }
    let mut buffer = vec![0u8; 1024];
    loop {
        let mut pwd: libc::passwd = unsafe { std::mem::zeroed() };
        let mut grp: libc::group = unsafe { std::mem::zeroed() };
        let mut user_result = std::ptr::null_mut();
        let mut group_result = std::ptr::null_mut();
        let status = unsafe {
            match (group, by_name) {
                (false, false) => libc::getpwuid_r(argument, &mut pwd, buffer.as_mut_ptr().cast(), buffer.len(), &mut user_result),
                (false, true) => libc::getpwnam_r(mem().host(argument).cast(), &mut pwd, buffer.as_mut_ptr().cast(), buffer.len(), &mut user_result),
                (true, false) => libc::getgrgid_r(argument, &mut grp, buffer.as_mut_ptr().cast(), buffer.len(), &mut group_result),
                (true, true) => libc::getgrnam_r(mem().host(argument).cast(), &mut grp, buffer.as_mut_ptr().cast(), buffer.len(), &mut group_result),
            }
        };
        if status == libc::ERANGE && buffer.len() < LIMIT { buffer.resize(buffer.len() * 2, 0); continue; }
        if status != 0 { return fail(uc, status); }
        if (!group && user_result.is_null()) || (group && group_result.is_null()) { return Ret::U32(0); }
        if !group {
            let strings = unsafe { [text(pwd.pw_name), text(pwd.pw_passwd), text(pwd.pw_dir), text(pwd.pw_shell)] };
            let size = 24 + strings.iter().flatten().map(|s| s.len() + 1).sum::<usize>();
            let base = match area(uc, false, size) { Ok(base) => base, Err(error) => return fail(uc, error) };
            let mut at = base + 24;
            let pointers = strings.iter().map(|s| write_text(&mut at, s.as_ref())).collect::<Vec<_>>();
            // ARM32 Bionic omits pw_gecos: six words, unlike the ARM64 host's struct.
            for (index, value) in [pointers[0], pointers[1], pwd.pw_uid, pwd.pw_gid, pointers[2], pointers[3]].iter().enumerate() {
                mem().write_u32(base + index as u32 * 4, *value);
            }
            return Ret::U32(base);
        }
        let name = unsafe { text(grp.gr_name) };
        let password = unsafe { text(grp.gr_passwd) };
        let mut members = Vec::new();
        if !grp.gr_mem.is_null() {
            for index in 0..4096 {
                let item = unsafe { *grp.gr_mem.add(index) };
                if item.is_null() { break; }
                if index == 4095 { return fail(uc, libc::ERANGE); }
                members.push(unsafe { text(item).unwrap() });
            }
        }
        let size = 16 + (members.len() + 1) * 4 + name.as_ref().map_or(0, |s| s.len() + 1)
            + password.as_ref().map_or(0, |s| s.len() + 1) + members.iter().map(|s| s.len() + 1).sum::<usize>();
        let base = match area(uc, true, size) { Ok(base) => base, Err(error) => return fail(uc, error) };
        let list = base + 16;
        let mut at = list + (members.len() as u32 + 1) * 4;
        let name = write_text(&mut at, name.as_ref());
        let password = write_text(&mut at, password.as_ref());
        for (index, member) in members.iter().enumerate() { mem().write_u32(list + index as u32 * 4, write_text(&mut at, Some(member))); }
        mem().write_u32(list + members.len() as u32 * 4, 0);
        for (index, value) in [name, password, grp.gr_gid, list].iter().enumerate() { mem().write_u32(base + index as u32 * 4, *value); }
        return Ret::U32(base);
    }
}

pub fn handler(name: &str) -> Option<Handler> {
    match name {
        "getpwuid" => handler!(|uc, a| lookup(uc, a.u32(), false, false)),
        "getpwnam" => handler!(|uc, a| lookup(uc, a.u32(), true, false)),
        "getgrgid" => handler!(|uc, a| lookup(uc, a.u32(), false, true)),
        "getgrnam" => handler!(|uc, a| lookup(uc, a.u32(), true, true)),
        _ => None,
    }
}
