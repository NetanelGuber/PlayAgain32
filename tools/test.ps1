[CmdletBinding()]
param(
    [string]$RustToolchain = 'stable-x86_64-pc-windows-gnu',
    [switch]$SkipAndroid
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
Push-Location $repo
try {
    & python -m unittest discover -s tests -p 'test_*.py' -v
    if ($LASTEXITCODE) { throw 'Repository tests failed.' }
    $encodedFlags = $env:CARGO_ENCODED_RUSTFLAGS
    $rustFlags = $env:RUSTFLAGS
    try {
        $env:CARGO_ENCODED_RUSTFLAGS = $null
        $env:RUSTFLAGS = $null
        & cargo "+$RustToolchain" test --locked -p apk-sdk-copy-tests
        if ($LASTEXITCODE) { throw 'APK metadata preparation tests failed.' }
    } finally {
        $env:CARGO_ENCODED_RUSTFLAGS = $encodedFlags
        $env:RUSTFLAGS = $rustFlags
    }
    New-Item -ItemType Directory -Force -Path target/host-tests | Out-Null
    foreach ($source in @('src/elf/validate.rs', 'src/imports/threads/frame_policy.rs')) {
        $name = [IO.Path]::GetFileNameWithoutExtension($source)
        $exe = Join-Path $repo "target/host-tests/$name.exe"
        & rustup run $RustToolchain rustc --edition=2021 --test $source -o $exe
        if ($LASTEXITCODE) { throw "Host test compilation failed: $source" }
        & $exe
        if ($LASTEXITCODE) { throw "Host tests failed: $source" }
    }
    if (!$SkipAndroid) {
        Push-Location android
        try {
            & .\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug
            if ($LASTEXITCODE) { throw 'Android tests, lint or assembly failed.' }
        } finally { Pop-Location }
    }
} finally { Pop-Location }
