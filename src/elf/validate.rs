//! Validate ELF metadata before it can select addresses in guest memory.

const PT_LOAD: u32 = 1;
const PT_DYNAMIC: u32 = 2;
const PT_ARM_EXIDX: u32 = 0x7000_0001;

#[derive(Clone, Debug)]
pub struct Symbol {
    pub name: String,
    pub value: u32,
    pub size: u32,
    pub kind: u8,
    pub defined: bool,
    pub weak: bool,
}

#[cfg(test)]
mod tests {
    use super::*;

    fn put(file: &mut [u8], at: usize, value: u32) {
        file[at..at + 4].copy_from_slice(&value.to_le_bytes());
    }

    fn fixture() -> Vec<u8> {
        let mut file = vec![0u8; 0x300];
        file[..7].copy_from_slice(b"\x7fELF\x01\x01\x01");
        file[16..18].copy_from_slice(&3u16.to_le_bytes());
        file[18..20].copy_from_slice(&40u16.to_le_bytes());
        put(&mut file, 20, 1);
        put(&mut file, 28, 52);
        file[40..42].copy_from_slice(&52u16.to_le_bytes());
        file[42..44].copy_from_slice(&32u16.to_le_bytes());
        file[44..46].copy_from_slice(&2u16.to_le_bytes());
        for (i, value) in [1, 0, 0, 0, 0x300, 0x400, 7, 4096].into_iter().enumerate() {
            put(&mut file, 52 + i * 4, value);
        }
        let tags = [(4, 0x1a0), (5, 0x1c0), (10, 0x30), (6, 0x200), (11, 16),
            (17, 0x240), (18, 8), (19, 8), (25, 0x260), (27, 4), (12, 0x280), (1, 1), (0, 0)];
        for (i, value) in [2, 0x100, 0x100, 0, tags.len() as u32 * 8, tags.len() as u32 * 8, 6, 4].into_iter().enumerate() {
            put(&mut file, 84 + i * 4, value);
        }
        for (i, (tag, value)) in tags.into_iter().enumerate() {
            put(&mut file, 0x100 + i * 8, tag);
            put(&mut file, 0x104 + i * 8, value);
        }
        put(&mut file, 0x1a0, 1);
        put(&mut file, 0x1a4, 2);
        put(&mut file, 0x1a8, 1);
        file[0x1c0..0x1d1].copy_from_slice(b"\0libdep.so\0hello\0");
        put(&mut file, 0x210, 11);
        put(&mut file, 0x214, 0x280);
        put(&mut file, 0x218, 4);
        file[0x21c] = 0x12;
        file[0x21e] = 1;
        put(&mut file, 0x240, 0x260);
        put(&mut file, 0x244, 0x102);
        file
    }

    fn parse(file: &[u8]) -> Result<Image, String> { Image::parse(file, 0x1000000, 4096, 0xf000000) }

    #[test]
    fn accepts_symbols_dependencies_relocations_and_initializers() {
        let image = parse(&fixture()).unwrap();
        assert_eq!(image.size, 4096);
        assert_eq!(image.symbols[1].name, "hello");
        assert_eq!(image.symbols[1].value, 0x280);
        assert_eq!(image.needed, ["libdep.so"]);
        assert_eq!(image.relocations[0].place, 0x260);
        assert_eq!(image.relocations[0].symbol, 1);
        assert_eq!(image.init_array, Some((0x260, 4)));
        assert_eq!(image.init, Some(0x280));
    }

    #[test]
    fn every_truncation_returns_an_error_without_panicking() {
        let file = fixture();
        for end in 0..file.len() { assert!(parse(&file[..end]).is_err(), "length {end}"); }
    }

    #[test]
    fn rejects_overflowed_or_out_of_file_program_tables_and_segments() {
        for (at, value) in [(28, u32::MAX), (28, 0x2ff), (56, u32::MAX), (60, u32::MAX),
            (68, 0x401), (72, u32::MAX), (72, 0x40000000)] {
            let mut file = fixture(); put(&mut file, at, value);
            assert!(parse(&file).is_err(), "field {at:#x} value {value:#x}");
        }
    }

    #[test]
    fn rejects_wrong_endianness_and_entry_sizes() {
        for at in [5, 20, 40, 42, 0x124, 0x13c] {
            let mut file = fixture(); file[at] = 3;
            assert!(parse(&file).is_err(), "field {at:#x}");
        }
    }

    #[test]
    fn rejects_unterminated_dynamic_table() {
        let mut file = fixture(); put(&mut file, 0x160, 1);
        assert!(parse(&file).unwrap_err().contains("DT_NULL"));
    }

    #[test]
    fn rejects_dynamic_table_outside_the_load_segment() {
        let mut file = fixture(); put(&mut file, 92, 0xfffffff8);
        assert!(parse(&file).is_err());
    }

    #[test]
    fn rejects_hash_and_symbol_table_overflows() {
        for (at, value) in [(0x104, u32::MAX), (0x1a0, u32::MAX), (0x1a4, u32::MAX),
            (0x1a4, 0x10000), (0x11c, 0x2f0)] {
            let mut file = fixture(); put(&mut file, at, value);
            assert!(parse(&file).is_err(), "field {at:#x}");
        }
    }

    #[test]
    fn rejects_out_of_bounds_and_unterminated_strings() {
        let mut file = fixture(); put(&mut file, 0x210, 0x30);
        assert!(parse(&file).is_err());
        let mut file = fixture(); file[0x1c0..0x1f0].fill(b'a');
        assert!(parse(&file).is_err());
        let mut file = fixture(); put(&mut file, 0x114, u32::MAX);
        assert!(parse(&file).is_err());
    }

    #[test]
    fn rejects_escaped_dependency_paths() {
        for name in ["../a.so", "/a.so", "a\\b.so", "C:a.so"] {
            let mut file = fixture(); file[0x1c1..0x1cb].fill(0);
            file[0x1c1..0x1c1 + name.len()].copy_from_slice(name.as_bytes());
            assert!(parse(&file).is_err(), "dependency {name}");
        }
    }

    #[test]
    fn rejects_relocation_indices_targets_and_truncated_entries() {
        for (at, value) in [(0x244, 0x202), (0x244, 0xff), (0x240, 0x3ff),
            (0x240, u32::MAX), (0x134, 7), (0x12c, u32::MAX)] {
            let mut file = fixture(); put(&mut file, at, value);
            assert!(parse(&file).is_err(), "relocation field {at:#x}");
        }
    }

    #[test]
    fn accepts_bss_relocations_and_ignores_none_relocation_targets() {
        let mut file = fixture(); put(&mut file, 0x240, 0x3fc);
        assert!(parse(&file).is_ok());
        put(&mut file, 0x240, u32::MAX); put(&mut file, 0x244, 0);
        assert!(parse(&file).is_ok());
    }

    #[test]
    fn rejects_initialization_ranges_and_rebased_symbol_overflow() {
        for (at, value) in [(0x144, u32::MAX), (0x14c, 3), (0x154, 0x400), (0x214, u32::MAX)] {
            let mut file = fixture(); put(&mut file, at, value);
            assert!(parse(&file).is_err(), "initializer field {at:#x}");
        }
        assert!(Image::parse(&fixture(), u32::MAX - 100, 4096, 0xf000000).is_err());
        assert!(Image::parse(&fixture(), 0x1000000, 4096, 1024).is_err());
    }

    #[test]
    fn checks_unwind_table_bounds_and_entry_size() {
        let mut file = fixture();
        file[44..46].copy_from_slice(&3u16.to_le_bytes());
        for (i, value) in [PT_ARM_EXIDX, 0x2c0, 0x2c0, 0, 8, 8, 4, 4].into_iter().enumerate() {
            put(&mut file, 116 + i * 4, value);
        }
        assert!(parse(&file).is_ok());
        put(&mut file, 124, 0x3fd);
        assert!(parse(&file).is_err());
        put(&mut file, 124, 0x2c0);
        put(&mut file, 132, 7); put(&mut file, 136, 7);
        assert!(parse(&file).is_err());
    }

    #[test]
    fn malformed_word_fields_never_panic() {
        for at in (0..0x300).step_by(4) {
            for value in [0, 1, u32::MAX, 0x7fffffff] {
                let mut file = fixture(); put(&mut file, at, value);
                let _ = parse(&file);
            }
        }
    }
}

#[derive(Debug)]
pub struct Segment {
    pub kind: u32,
    pub offset: u32,
    pub address: u32,
    pub file_size: u32,
    pub memory_size: u32,
    pub flags: u32,
}

#[derive(Debug)]
pub struct Relocation {
    pub place: u32,
    pub kind: u32,
    pub symbol: u32,
}

#[derive(Debug)]
pub struct Image {
    pub size: u32,
    pub segments: Vec<Segment>,
    pub symbols: Vec<Symbol>,
    pub needed: Vec<String>,
    pub relocations: Vec<Relocation>,
    pub init: Option<u32>,
    pub init_array: Option<(u32, u32)>,
}

fn bytes(file: &[u8], at: u32, size: u32) -> Result<&[u8], String> {
    let end = at.checked_add(size).ok_or("ELF file range overflows")?;
    file.get(at as usize..end as usize).ok_or_else(|| "ELF file range is truncated".into())
}

fn word(file: &[u8], at: u32) -> Result<u32, String> {
    Ok(u32::from_le_bytes(bytes(file, at, 4)?.try_into().unwrap()))
}

fn half(file: &[u8], at: u32) -> Result<u16, String> {
    Ok(u16::from_le_bytes(bytes(file, at, 2)?.try_into().unwrap()))
}

fn string(table: &[u8], offset: u32) -> Result<String, String> {
    let rest = table.get(offset as usize..).ok_or("ELF string offset is out of bounds")?;
    let end = rest.iter().position(|b| *b == 0).ok_or("ELF string is not terminated")?;
    Ok(String::from_utf8_lossy(&rest[..end]).into_owned())
}

impl Image {
    pub fn parse(file: &[u8], base: u32, page: u32, available: u32) -> Result<Self, String> {
        if bytes(file, 0, 52)?.get(..7) != Some(b"\x7fELF\x01\x01\x01") {
            return Err("not a little-endian ELF32 file".into());
        }
        if half(file, 18)? != 40 || word(file, 20)? != 1 || half(file, 40)? != 52 || half(file, 42)? != 32 {
            return Err("unsupported ARM ELF header".into());
        }
        let table = word(file, 28)?;
        let count = u32::from(half(file, 44)?);
        bytes(file, table, count.checked_mul(32).ok_or("ELF program table overflows")?)?;
        let mut segments = Vec::new();
        for i in 0..count {
            let at = table + i * 32;
            let segment = Segment {
                kind: word(file, at)?, offset: word(file, at + 4)?, address: word(file, at + 8)?,
                file_size: word(file, at + 16)?, memory_size: word(file, at + 20)?, flags: word(file, at + 24)?,
            };
            if matches!(segment.kind, PT_LOAD | PT_DYNAMIC | PT_ARM_EXIDX) {
                bytes(file, segment.offset, segment.file_size)?;
                segment.address.checked_add(segment.memory_size).ok_or("ELF segment address overflows")?;
                if segment.file_size > segment.memory_size {
                    return Err("ELF segment file size exceeds memory size".into());
                }
            }
            segments.push(segment);
        }
        let end = segments.iter().filter(|s| s.kind == PT_LOAD)
            .map(|s| s.address + s.memory_size).max().ok_or("no loadable segments")?;
        if !page.is_power_of_two() {
            return Err("invalid ELF page size".into());
        }
        let size = end.checked_add(page - 1).ok_or("ELF image size overflows")? & !(page - 1);
        if size == 0 || size > available || base.checked_add(size).is_none() {
            return Err("ELF image does not fit in the library area".into());
        }
        let mut image = Self { size, segments, symbols: Vec::new(), needed: Vec::new(), relocations: Vec::new(), init: None, init_array: None };
        let dynamic = image.segments.iter().find(|s| s.kind == PT_DYNAMIC).ok_or("no dynamic section")?;
        if dynamic.file_size % 8 != 0 {
            return Err("truncated ELF dynamic entry".into());
        }
        let dynamic_bytes = image.file_bytes(file, dynamic.address, dynamic.file_size)?;
        let mut tags = Vec::new();
        let mut terminated = false;
        for entry in dynamic_bytes.chunks_exact(8) {
            let tag = u32::from_le_bytes(entry[..4].try_into().unwrap());
            if tag == 0 { terminated = true; break; }
            tags.push((tag, u32::from_le_bytes(entry[4..].try_into().unwrap())));
        }
        if !terminated { return Err("ELF dynamic section has no DT_NULL".into()); }
        let tag = |wanted| tags.iter().find(|(t, _)| *t == wanted).map(|(_, value)| *value);
        if tag(11).is_some_and(|size| size != 16) || tag(19).is_some_and(|size| size != 8) {
            return Err("unsupported ELF symbol or relocation entry size".into());
        }
        let strings = image.file_bytes(file, tag(5).ok_or("no string table")?, tag(10).ok_or("no DT_STRSZ")?)?;
        let hash_address = tag(4).ok_or("no DT_HASH")?;
        let hash = image.file_bytes(file, hash_address, 8)?;
        let buckets = word(hash, 0)?;
        let symbol_count = word(hash, 4)?;
        let hash_size = buckets.checked_add(symbol_count).and_then(|n| n.checked_add(2)).and_then(|n| n.checked_mul(4))
            .ok_or("ELF hash size overflows")?;
        image.file_bytes(file, hash_address, hash_size)?;
        let symbols = image.file_bytes(file, tag(6).ok_or("no symbol table")?, symbol_count.checked_mul(16).ok_or("ELF symbol table overflows")?)?;
        for entry in symbols.chunks_exact(16) {
            let value = word(entry, 4)?;
            let defined = half(entry, 14)? != 0;
            if defined { base.checked_add(value).ok_or("ELF symbol address overflows")?; }
            image.symbols.push(Symbol {
                name: string(strings, word(entry, 0)?)?, value, size: word(entry, 8)?,
                kind: entry[12] & 0xf, defined, weak: entry[12] >> 4 == 2,
            });
        }
        for (_, offset) in tags.iter().filter(|(t, _)| *t == 1) {
            let needed = string(strings, *offset)?;
            // Dependencies may select files beside the game, never arbitrary host paths.
            if needed.is_empty() || needed.contains(['/', '\\', ':']) || matches!(needed.as_str(), "." | "..") {
                return Err("ELF dependency must be a library file name".into());
            }
            image.needed.push(needed);
        }
        for (address_tag, size_tag) in [(17, 18), (23, 2)] {
            let (address, size) = match (tag(address_tag), tag(size_tag)) {
                (None, None) => continue,
                (Some(address), Some(size)) => (address, size),
                _ => return Err("incomplete ELF relocation table".into()),
            };
            if size % 8 != 0 { return Err("truncated ELF relocation entry".into()); }
            for entry in image.file_bytes(file, address, size)?.chunks_exact(8) {
                let place = word(entry, 0)?;
                let info = word(entry, 4)?;
                let (kind, symbol) = (info & 0xff, info >> 8);
                if !matches!(kind, 0 | 2 | 21 | 22 | 23) { return Err(format!("unsupported relocation type {kind}")); }
                if kind != 0 { image.memory_range(place, 4)?; }
                if matches!(kind, 2 | 21 | 22) && symbol >= symbol_count {
                    return Err("ELF relocation symbol index is out of bounds".into());
                }
                image.relocations.push(Relocation { place, kind, symbol });
            }
        }
        if let Some(init) = tag(12) {
            image.memory_range(init & !1, 1)?;
            image.init = Some(init);
        }
        image.init_array = match (tag(25), tag(27)) {
            (None, None) => None,
            (Some(address), Some(size)) if size % 4 == 0 => {
                image.memory_range(address, size)?;
                Some((address, size))
            }
            _ => return Err("invalid ELF initialization array".into()),
        };
        for segment in image.segments.iter().filter(|s| s.kind == PT_ARM_EXIDX) {
            image.memory_range(segment.address, segment.memory_size)?;
            if segment.memory_size % 8 != 0 { return Err("truncated ELF unwind entry".into()); }
        }
        Ok(image)
    }

    pub fn memory_range(&self, address: u32, size: u32) -> Result<(), String> {
        let end = address.checked_add(size).ok_or("ELF memory range overflows")?;
        if self.segments.iter().any(|s| s.kind == PT_LOAD && address >= s.address && end <= s.address + s.memory_size) {
            Ok(())
        } else { Err("ELF memory range is outside loadable segments".into()) }
    }

    fn file_bytes<'a>(&self, file: &'a [u8], address: u32, size: u32) -> Result<&'a [u8], String> {
        let end = address.checked_add(size).ok_or("ELF table address overflows")?;
        let segment = self.segments.iter().find(|s| s.kind == PT_LOAD && address >= s.address && end <= s.address + s.file_size)
            .ok_or("ELF table is outside file-backed loadable segments")?;
        let offset = segment.offset.checked_add(address - segment.address).ok_or("ELF table offset overflows")?;
        bytes(file, offset, size)
    }
}
