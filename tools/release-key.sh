#!/bin/bash
# Makes PlayAgain32's release signing key, once. The password is random, goes
# straight into KDE Wallet (wallet kdewallet, folder PlayAgain32, entry
# release-key) and is never printed; the key itself goes to
# ~/dev/playagain32-key/playagain32-release.p12, outside the public repo.
# tools/build-app.sh --public reads both. Losing the key means people must
# uninstall PlayAgain32 to update it, so the key folder and the wallet are in
# the daily Kopia backup.
#
# usage: tools/release-key.sh
set -euo pipefail
KEY_DIR=$HOME/dev/playagain32-key
KEYSTORE=$KEY_DIR/playagain32-release.p12
WALLET=kdewallet FOLDER=PlayAgain32 ENTRY=release-key

if [ -e "$KEYSTORE" ]; then
    echo "The key already exists: $KEYSTORE (not replaced)" >&2
    exit 1
fi

# kwallet-query can't make folders, so ask the wallet service directly.
call() { qdbus6 org.kde.kwalletd6 /modules/kwalletd6 "$@"; }
handle=$(call org.kde.KWallet.open "$WALLET" 0 playagain32-release-key)
[ "$(call org.kde.KWallet.hasFolder "$handle" "$FOLDER" playagain32-release-key)" = true ] ||
    call org.kde.KWallet.createFolder "$handle" "$FOLDER" playagain32-release-key >/dev/null

if kwallet-query -f "$FOLDER" -r "$ENTRY" "$WALLET" >/dev/null 2>&1 &&
    [ -n "$(kwallet-query -f "$FOLDER" -r "$ENTRY" "$WALLET" 2>/dev/null)" ]; then
    echo "KDE Wallet already holds a release-key password; not replacing it." >&2
    exit 1
fi
password=$(head -c 30 /dev/urandom | base64 | tr -d '/+=')
printf %s "$password" | kwallet-query -f "$FOLDER" -w "$ENTRY" "$WALLET"
# Read back: only build with a password the wallet really holds.
[ "$(kwallet-query -f "$FOLDER" -r "$ENTRY" "$WALLET")" = "$password" ] || { echo "KDE Wallet didn't keep the password." >&2; exit 1; }

mkdir -p "$KEY_DIR"
chmod 700 "$KEY_DIR"
export PLAYAGAIN32_KEY_PASSWORD=$password
keytool -genkeypair -keystore "$KEYSTORE" -storetype PKCS12 -alias playagain32 \
    -keyalg RSA -keysize 4096 -validity 10000 \
    -dname "CN=PlayAgain32" \
    -storepass:env PLAYAGAIN32_KEY_PASSWORD -keypass:env PLAYAGAIN32_KEY_PASSWORD
chmod 600 "$KEYSTORE"
keytool -list -keystore "$KEYSTORE" -storepass:env PLAYAGAIN32_KEY_PASSWORD | grep -i -A1 "playagain32"
echo "made $KEYSTORE"
