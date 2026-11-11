"""Publication checks. No Android SDK or third-party Python packages required."""
from pathlib import Path
import re
import subprocess
import unittest
from tools.verify_apk import contains_private_build_path

ROOT = Path(__file__).resolve().parents[1]


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


class PublicationTests(unittest.TestCase):
    def test_idle_armies_gles_dynamic_lookups_are_capability_gated(self):
        generated = (ROOT / "src/imports/gles_generated.rs").read_text(encoding="utf-8")
        gles = (ROOT / "src/imports/gles.rs").read_text(encoding="utf-8")
        resolver = (ROOT / "src/imports/libc_calls.rs").read_text(encoding="utf-8")
        imports = (ROOT / "src/imports/mod.rs").read_text(encoding="utf-8")
        generator = (ROOT / "tools/gen_gles.py").read_text(encoding="utf-8")
        dynamic_only = gles.split("pub fn dynamic_lookup_only", 1)[1].split("\n}", 1)[0]

        # These optional extension names were exposed by the device driver, but
        # the bridge previously returned NULL because it had no guest wrappers.
        for name in [
            "glLabelObjectEXT",
            "glGetObjectLabelEXT",
            "glDrawBuffersEXT",
            "glBlitFramebufferNV",
            "glMapBufferRangeEXT",
        ]:
            with self.subTest(name=name):
                self.assertIn(name, generator)
                self.assertTrue(name in generated or name == "glMapBufferRangeEXT")
                self.assertIn(name, dynamic_only)
                self.assertIn("!gles::dynamic_lookup_only(name)", imports)

        self.assertIn("phone_function_available(name)", resolver)
        self.assertIn("dynamic_lookup_only(name)", resolver)
        self.assertNotIn("glFlushMappedBufferRangeEXT", dynamic_only)
        self.assertIn('"glFlushMappedBufferRangeEXT" => handler!', gles)
        self.assertIn("upload(target, start + offset, length, copy + offset)", gles)
        self.assertIn("eglGetProcAddress(symbol.as_ptr())", gles)
        # Do not pretend the two unavailable desktop/vendor calls exist.
        for unsupported in ["glFramebufferTexture3D", "glTexImage2DMultisample"]:
            self.assertNotIn(f'"{unsupported}" =>', generated)

    def test_binary_build_path_detection(self):
        for value in [r"C:\Users\private-builder\.cargo\src\code.rs", "C:/Users/private-builder/.cargo/code.rs",
                      "/home/private-builder/project/code.rs", "/Users/private-builder/project/code.rs"]:
            self.assertTrue(contains_private_build_path(value.encode()))
            self.assertTrue(contains_private_build_path(value.encode("utf-16-le")))
        for value in [b"/src/playagain32/src/mem.rs", b"/build-user/.cargo/registry/code.rs", b"/rustc/hash/library/std.rs"]:
            self.assertFalse(contains_private_build_path(value))

    def test_private_and_generated_files_are_ignored(self):
        paths = [".env", ".env.local", "signing/release.jks", "release.keystore",
                 "private.pem", "game.apk", "main.game.obb",
                 "android/local.properties", "target/test.exe",
                 "dist/app.apk", ".build/tools/bin.exe",
                 "android/app/src/main/jniLibs/arm64-v8a/libarm32_bridge.so"]
        ignored = git("check-ignore", "--no-index", *paths).decode().splitlines()
        self.assertEqual(set(paths), set(ignored))

    def test_publishable_files_have_no_private_keys_or_build_outputs(self):
        paths = git("ls-files", "--cached", "--others", "--exclude-standard", "-z")
        forbidden = re.compile(r"(^|/)(\.env(?:\..*)?|local\.properties)$|"
                               r"\.(?:keystore|jks|p12|pfx|pem|key|apk|obb|apks|apkm|xapk)$|"
                               r"^(?:target|dist|\.build|android/app/build)/", re.I)
        private_key = re.compile(rb"-----BEGIN (?:[A-Z0-9]+ )?PRIVATE" + rb" KEY-----")
        for raw in paths.split(b"\0"):
            if not raw:
                continue
            name = raw.decode("utf-8")
            path = ROOT / name
            if not path.is_file():  # Pending source renames are allowed before staging.
                continue
            self.assertIsNone(forbidden.search(name), f"Do not publish {name}")
            if path.stat().st_size < 2 * 1024 * 1024:
                self.assertIsNone(private_key.search(path.read_bytes()),
                                  f"Private key found in {name}")

    def test_public_document_links_resolve(self):
        for name in ["README.md", "CONTRIBUTING.md", "SECURITY.md", "docs/RELEASING.md"]:
            path = ROOT / name
            for target in re.findall(r"\]\(([^\s)]+)\)", path.read_text(encoding="utf-8")):
                if "://" in target or target.startswith("#"):
                    continue
                self.assertTrue((path.parent / target.split("#")[0]).exists(),
                                f"Broken link in {name}: {target}")


if __name__ == "__main__":
    unittest.main()
