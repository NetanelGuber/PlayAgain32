"""Reject private build-machine home paths in distributable APK contents."""
from pathlib import Path
import re
import sys
import zipfile

HOME_PATH = re.compile(rb"[a-z]:[\\/]+Users[\\/]+[^\\/\s]+[\\/]|/(?:Users|home)/[^/\s]+/", re.I)


def contains_private_build_path(data):
    return bool(HOME_PATH.search(data) or HOME_PATH.search(data.replace(b"\0", b"")))


def verify(apk):
    with zipfile.ZipFile(apk) as archive:
        return [item.filename for item in archive.infolist()
                if not item.is_dir() and contains_private_build_path(archive.read(item))]


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("usage: python tools/verify_apk.py APP.apk")
    failures = verify(Path(sys.argv[1]))
    if failures:
        sys.exit("Private build paths found in APK entries: " + ", ".join(failures))
    print("APK privacy check passed: no build-user home paths.")
