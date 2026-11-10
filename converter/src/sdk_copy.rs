//! Prepare compatible code for Android's minimum install target without translating it.
use std::io::{Read, Seek, Write};
use zip::{write::SimpleFileOptions, CompressionMethod, ZipArchive, ZipWriter};

pub fn rewrite<R: Read + Seek, W: Write + Seek>(archive: &mut ZipArchive<R>, target: W, minimum: u32, full_screen: bool)
    -> Result<W, Box<dyn std::error::Error>> {
    let mut manifest = Vec::new();
    archive.by_name("AndroidManifest.xml")?.read_to_end(&mut manifest)?;
    if let Some((old, new)) = crate::manifest::raise_target_sdk(&mut manifest, minimum)? {
        println!("fix AndroidManifest.xml: target Android version: {old} -> {new}");
    }
    if full_screen { manifest = crate::manifest::allow_full_screen(&manifest)?; }
    let mut writer = ZipWriter::new(target);
    for index in 0..archive.len() {
        let entry = archive.by_index_raw(index)?;
        let name = entry.name().to_string();
        let upper = name.to_ascii_uppercase();
        if entry.is_dir() || (upper.starts_with("META-INF/") &&
            (upper == "META-INF/MANIFEST.MF" || [".SF", ".RSA", ".DSA", ".EC"].iter().any(|suffix| upper.ends_with(suffix)))) {
            continue;
        }
        if name == "AndroidManifest.xml" {
            drop(entry);
            writer.start_file(name, SimpleFileOptions::default().compression_method(CompressionMethod::Deflated))?;
            writer.write_all(&manifest)?;
        } else if entry.compression() == CompressionMethod::Stored {
            let alignment = if name.starts_with("lib/") { 16384 } else { 4 };
            drop(entry);
            writer.start_file(name, SimpleFileOptions::default().compression_method(CompressionMethod::Stored).with_alignment(alignment))?;
            std::io::copy(&mut archive.by_index(index)?, &mut writer)?;
        } else {
            writer.raw_copy_file(entry)?;
        }
    }
    Ok(writer.finish()?)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Cursor;

    fn fixture(manifest: &[u8]) -> ZipArchive<Cursor<Vec<u8>>> {
        let mut writer = ZipWriter::new(Cursor::new(Vec::new()));
        for (name, data, method) in [
            ("AndroidManifest.xml", manifest, CompressionMethod::Deflated),
            ("classes.dex", b"original Java code".as_slice(), CompressionMethod::Deflated),
            ("lib/armeabi-v7a/libgame.so", b"original native code".as_slice(), CompressionMethod::Stored),
            ("assets/texture", b"original texture".as_slice(), CompressionMethod::Deflated),
            ("resources.arsc", b"original resources".as_slice(), CompressionMethod::Stored),
            ("META-INF/CERT.RSA", b"old signature".as_slice(), CompressionMethod::Deflated),
            ("META-INF/LICENSE", b"required attribution".as_slice(), CompressionMethod::Deflated),
        ] {
            writer.start_file(name, SimpleFileOptions::default().compression_method(method)).unwrap();
            writer.write_all(data).unwrap();
        }
        ZipArchive::new(writer.finish().unwrap()).unwrap()
    }

    #[test]
    fn sdk_copy_preserves_code_assets_and_notices_and_realigns_native_data() {
        let mut before = fixture(include_bytes!("testdata/target23.axml"));
        let output = rewrite(&mut before, Cursor::new(Vec::new()), 24, false).unwrap();
        let mut after = ZipArchive::new(output).unwrap();
        for name in ["classes.dex", "lib/armeabi-v7a/libgame.so", "assets/texture", "resources.arsc", "META-INF/LICENSE"] {
            let mut old = Vec::new();
            let mut new = Vec::new();
            before.by_name(name).unwrap().read_to_end(&mut old).unwrap();
            after.by_name(name).unwrap().read_to_end(&mut new).unwrap();
            assert_eq!(old, new, "{name} changed");
        }
        assert!(after.by_name("META-INF/CERT.RSA").is_err());
        assert_eq!(after.by_name("lib/armeabi-v7a/libgame.so").unwrap().data_start().unwrap() % 16384, 0);
        assert_eq!(after.by_name("resources.arsc").unwrap().data_start().unwrap() % 4, 0);
        let mut manifest = Vec::new();
        after.by_name("AndroidManifest.xml").unwrap().read_to_end(&mut manifest).unwrap();
        assert_eq!(crate::manifest::raise_target_sdk(&mut manifest, 24), Ok(None));
        assert_eq!(crate::manifest::raise_target_sdk(&mut manifest, 25), Ok(Some((24, 25))));
    }

    #[test]
    fn sdk_copy_rejects_an_unpatchable_manifest() {
        assert!(rewrite(&mut fixture(b"broken manifest"), Cursor::new(Vec::new()), 24, false).is_err());
    }
}
