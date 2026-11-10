// Test the production ZIP/manifest code on the host without linking the Android runtime.
#![allow(dead_code)]
#[path = "../../../converter/src/manifest.rs"]
mod manifest;
#[path = "../../../converter/src/sdk_copy.rs"]
mod sdk_copy;
