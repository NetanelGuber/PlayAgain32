# Build Android runtime binaries from source, then the app. Never embeds a signing key.
[CmdletBinding()]
param(
    [string]$Sdk = $env:ANDROID_HOME,
    [string]$Ndk = $env:NDK,
    [string]$RustToolchain = 'stable-x86_64-pc-windows-gnu',
    [switch]$NativeOnly
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
if (!$Sdk) { $Sdk = $env:ANDROID_SDK_ROOT }
if (!$Sdk) { throw 'Set ANDROID_HOME or pass -Sdk with the Android SDK directory.' }
if (!$Ndk) {
    $Ndk = (Get-ChildItem -LiteralPath (Join-Path $Sdk 'ndk') -Directory |
        Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1).FullName
}
$llvm = Join-Path $Ndk 'toolchains/llvm/prebuilt/windows-x86_64'
$bin = Join-Path $llvm 'bin'
$cmake = (Get-ChildItem -LiteralPath (Join-Path $Sdk 'cmake') -Directory |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1).FullName
$clang = Join-Path $bin 'clang.exe'
if (!(Test-Path -LiteralPath $clang)) { throw "NDK clang not found: $clang" }
if (!$env:LIBCLANG_PATH) { $env:LIBCLANG_PATH = Join-Path $repo '.build/python-tools/clang/native' }
if (!(Test-Path -LiteralPath (Join-Path $env:LIBCLANG_PATH 'libclang.dll'))) {
    throw 'Set LIBCLANG_PATH to libclang.dll, or install libclang==18.1.1 with pip --target .build/python-tools.'
}
if (!(Get-Command sh -ErrorAction SilentlyContinue)) {
    $gitShell = Join-Path $env:ProgramFiles 'Git/usr/bin'
    if (!(Test-Path -LiteralPath (Join-Path $gitShell 'sh.exe'))) { throw 'Unicorn requires sh from Git for Windows on PATH.' }
    $env:PATH = "$gitShell;$env:PATH"
}
$rustSysroot = (& rustup run $RustToolchain rustc --print sysroot).Trim()
if ($LASTEXITCODE) { throw "Rust toolchain is unavailable: $RustToolchain" }
$rustHostTools = Join-Path $rustSysroot 'lib/rustlib/x86_64-pc-windows-gnu/bin/self-contained'
$env:CARGO_TARGET_X86_64_PC_WINDOWS_GNU_LINKER = Join-Path $rustSysroot 'lib/rustlib/x86_64-pc-windows-gnu/bin/rust-lld.exe'
# GNU dlltool needs a separate assembler; NDK's LLVM equivalent is self-contained.
$hostTools = Join-Path $repo '.build/host-tools'
New-Item -ItemType Directory -Force -Path $hostTools | Out-Null
Copy-Item -LiteralPath (Join-Path $bin 'llvm-dlltool.exe') -Destination (Join-Path $hostTools 'dlltool.exe') -Force
Copy-Item -LiteralPath (Join-Path $bin 'llvm-strings.exe') -Destination (Join-Path $hostTools 'strings.exe') -Force
$env:PATH = "$hostTools;$rustHostTools;$(Join-Path $cmake 'bin');$bin;$env:PATH"
$env:ANDROID_HOME = $Sdk
$env:NDK = $Ndk
$env:ANDROID_NDK_HOME = $Ndk
$env:CMAKE_TOOLCHAIN_FILE = Join-Path $Ndk 'build/cmake/android.toolchain.cmake'
$env:CC_aarch64_linux_android = $clang
$env:CXX_aarch64_linux_android = Join-Path $bin 'clang++.exe'
$env:AR_aarch64_linux_android = Join-Path $bin 'llvm-ar.exe'
$pathMaps = @("-ffile-prefix-map=$repo=/src/playagain32", "-ffile-prefix-map=$env:USERPROFILE=/build-user",
    "-ffile-prefix-map=$($repo.Replace('\', '/'))=/src/playagain32", "-ffile-prefix-map=$($env:USERPROFILE.Replace('\', '/'))=/build-user")
$env:CC_SHELL_ESCAPED_FLAGS = '1'
$env:CFLAGS_aarch64_linux_android = '--target=aarch64-linux-android30 ' + (($pathMaps | ForEach-Object { '"' + $_ + '"' }) -join ' ')
$env:CXXFLAGS_aarch64_linux_android = $env:CFLAGS_aarch64_linux_android
$env:CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER = $clang
$builtins = Get-ChildItem -LiteralPath (Join-Path $llvm 'lib/clang') -Recurse -Filter libclang_rt.builtins-aarch64-android.a | Select-Object -First 1
if (!$builtins) { throw 'NDK ARM64 compiler builtins archive is missing.' }
# Encoded flags preserve paths containing spaces as individual Rust arguments.
$env:CARGO_ENCODED_RUSTFLAGS = @('-C', 'link-arg=--target=aarch64-linux-android30', '-C', "link-arg=$($builtins.FullName)", '-L', (Join-Path $repo 'android-link-stubs'),
    "--remap-path-prefix=$repo=/src/playagain32", "--remap-path-prefix=$env:USERPROFILE=/build-user",
    "--remap-path-prefix=$($repo.Replace('\', '/'))=/src/playagain32", "--remap-path-prefix=$($env:USERPROFILE.Replace('\', '/'))=/build-user") -join [char]31
$env:CLANG_PATH = $clang
$clangHeaders = Join-Path $builtins.Directory.Parent.Parent.FullName 'include'
$env:BINDGEN_EXTRA_CLANG_ARGS_aarch64_linux_android = "--target=aarch64-linux-android30 --sysroot=`"$(Join-Path $llvm 'sysroot')`" -isystem `"$clangHeaders`""
$env:MSYS_NO_PATHCONV = '1'
$env:MSYS2_ARG_CONV_EXCL = '*'
# This vendored Unicorn configure only checks pkg-config exists; it makes no queries.
$env:PKG_CONFIG = 'true'
Push-Location $repo
try {
    & cargo "+$RustToolchain" build --locked --release --target aarch64-linux-android --lib -p arm32-bridge -p gamebridge-pacer
    if ($LASTEXITCODE) { throw 'Native runtime build failed.' }
    & cargo "+$RustToolchain" build --locked --release --target aarch64-linux-android -p apk-convert
    if ($LASTEXITCODE) { throw 'Converter build failed.' }
    & cargo "+$RustToolchain" build --locked --release --target aarch64-linux-android --bin arm32-bridge
    if ($LASTEXITCODE) { throw 'Device test runner build failed.' }
    $artifacts = & cargo "+$RustToolchain" test --locked --release --target aarch64-linux-android --test idle_signals --no-run --message-format=json
    if ($LASTEXITCODE) { throw 'Idle-signal regression compilation failed.' }
    $idleTest = $artifacts | ForEach-Object { $_ | ConvertFrom-Json } |
        Where-Object { $_.reason -eq 'compiler-artifact' -and $_.target.name -eq 'idle_signals' -and $_.executable } |
        Select-Object -Last 1 -ExpandProperty executable
    if (!$idleTest) { throw 'Cargo did not produce the idle-signal test executable.' }
    New-Item -ItemType Directory -Force -Path (Join-Path $repo 'target/host-tests') | Out-Null
    Copy-Item -LiteralPath $idleTest -Destination (Join-Path $repo 'target/host-tests/idle-signals') -Force
    $libs = Join-Path $repo 'android/app/src/main/jniLibs/arm64-v8a'
    New-Item -ItemType Directory -Force -Path $libs | Out-Null
    $out = Join-Path $repo 'target/aarch64-linux-android/release'
    foreach ($name in @('libarm32_bridge.so', 'libgamebridge_pacer.so')) {
        & (Join-Path $bin 'llvm-strip.exe') -o (Join-Path $libs $name) (Join-Path $out $name)
        if ($LASTEXITCODE) { throw "Stripping $name failed." }
    }
    & (Join-Path $bin 'llvm-strip.exe') -o (Join-Path $libs 'libapkconvert.so') (Join-Path $out 'apk-convert')
    if ($LASTEXITCODE) { throw 'Stripping converter failed.' }
    & $clang --target=aarch64-linux-android30 @pathMaps -O2 -shared -fPIC '-Wl,--build-id=none' -o (Join-Path $libs 'libstandin.so') (Join-Path $repo 'tools/standin/standin.c') -llog -ldl
    if ($LASTEXITCODE) { throw 'Stand-in library build failed.' }
    foreach ($name in @('libarm32_bridge.so', 'libgamebridge_pacer.so', 'libapkconvert.so', 'libstandin.so')) {
        $file = Join-Path $libs $name
        $header = [IO.File]::ReadAllBytes($file)
        if ($header.Length -lt 64 -or $header[0] -ne 127 -or $header[1] -ne 69 -or $header[2] -ne 76 -or $header[3] -ne 70 -or $header[4] -ne 2 -or $header[5] -ne 1 -or [BitConverter]::ToUInt16($header, 16) -ne 3 -or [BitConverter]::ToUInt16($header, 18) -ne 183) {
            throw "Expected an ARM64 ELF64 ET_DYN binary: $file"
        }
        if ($name -eq 'libapkconvert.so' -and [BitConverter]::ToUInt64($header, 24) -eq 0) { throw 'Converter executable has no entry point.' }
    }
    $embeddedKey = Join-Path $repo 'android/app/src/main/assets/debug.keystore'
    if (Test-Path -LiteralPath $embeddedKey) { throw 'Remove assets/debug.keystore; private signing keys must not be packaged.' }
    if (!$NativeOnly) {
        Push-Location (Join-Path $repo 'android')
        try {
            & .\gradlew.bat --no-daemon assembleDebug
            if ($LASTEXITCODE) { throw 'Android app build failed.' }
        } finally { Pop-Location }
    }
} finally { Pop-Location }
