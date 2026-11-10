# Run only project-owned fixtures; never installs games or changes device settings.
[CmdletBinding()]
param([Parameter(Mandatory)][string]$Serial)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$adb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
$runner = Join-Path $repo 'target/aarch64-linux-android/release/arm32-bridge'
$cases = @()
foreach ($mode in @('arm', 'thumb')) {
    $cases += @{ File = "libimports-$mode.so"; Function = 'run_import_tests' }
    $cases += @{ File = "libmono-imports-$mode.so"; Function = 'run_mono_import_tests' }
    $cases += @{ File = "libgles-binary-$mode.so"; Function = 'test_gles_program_binary' }
}
if (!(Test-Path -LiteralPath $runner)) { throw 'Build the ARM64 CLI runner first; see COMPATIBILITY-TESTING.md.' }
foreach ($case in $cases) {
    if (!(Test-Path -LiteralPath (Join-Path $repo "target/host-tests/$($case.File)"))) {
        throw 'Run tools/test.ps1 first to compile the ARM/Thumb fixtures.'
    }
}
$remote = '/data/local/tmp/playagain32-tests-' + [guid]::NewGuid().ToString('N')
& $adb -s $Serial shell mkdir $remote
if ($LASTEXITCODE) { throw 'Cannot create the device test directory.' }
try {
    & $adb -s $Serial push $runner "$remote/bridge" *> $null
    if ($LASTEXITCODE) { throw 'Cannot copy the test runner.' }
    foreach ($case in $cases) {
        & $adb -s $Serial push (Join-Path $repo "target/host-tests/$($case.File)") "$remote/$($case.File)" *> $null
        if ($LASTEXITCODE) { throw 'Cannot copy a test fixture.' }
    }
    & $adb -s $Serial shell chmod 700 "$remote/bridge"
    if ($LASTEXITCODE) { throw 'Cannot make the test runner executable.' }
    $libraries = ($cases | ForEach-Object { "$remote/$($_.File)" }) -join ' '
    $output = (& $adb -s $Serial shell "$remote/bridge imports $libraries" 2>&1) -join "`n"
    if ($LASTEXITCODE -or $output -notmatch '\[bridge\] 0 imports without a handler') {
        throw "Fixture import validation failed:`n$output"
    }
    foreach ($case in $cases) {
        # The CLI exits successfully for any returned integer; require an explicit zero too.
        $output = (& $adb -s $Serial shell "ARM32_ENGINE=jit ARM32_LOCKCHECK=1 timeout 120 $remote/bridge call $remote/$($case.File) $($case.Function)" 2>&1) -join "`n"
        if ($LASTEXITCODE -or $output -notmatch "(?m)^$($case.Function) returned 0 \(0x0\)\r?$" -or
            $output -match 'stopped:|panicked at|\[heap\] refused|\[mimalloc\].*error') {
            throw "Device regression failed: $($case.File)/$($case.Function)`n$output"
        }
        Write-Output "PASS $($case.File)/$($case.Function)"
    }
} finally {
    # Delete only the exact files this invocation created, inside its unique directory.
    & $adb -s $Serial shell rm "$remote/bridge" @($cases | ForEach-Object { "$remote/$($_.File)" }) *> $null
    & $adb -s $Serial shell rmdir $remote *> $null
}
