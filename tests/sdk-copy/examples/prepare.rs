// Host integration harness for the production APK metadata rewriter.
#[path = "../../../converter/src/manifest.rs"]
#[allow(dead_code)]
mod manifest;
#[path = "../../../converter/src/sdk_copy.rs"]
mod sdk_copy;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let args: Vec<String> = std::env::args().skip(1).collect();
    if args.len() < 3 || args.len() > 4 { return Err("usage: prepare INPUT OUTPUT MIN_TARGET [--fullscreen]".into()); }
    if args.len() == 4 && args[3] != "--fullscreen" { return Err("unknown option".into()); }
    let mut archive = zip::ZipArchive::new(std::fs::File::open(&args[0])?)?;
    let output = std::fs::OpenOptions::new().write(true).create_new(true).open(&args[1])?;
    sdk_copy::rewrite(&mut archive, output, args[2].parse()?, args.len() == 4)?;
    Ok(())
}
