# Run only project-owned fixtures; never installs games or changes device settings.
[CmdletBinding()]
param([Parameter(Mandatory)][string]$Serial)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$adb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
$runner = Join-Path $repo 'target/aarch64-linux-android/release/arm32-bridge'
$idleTest = Join-Path $repo 'target/host-tests/idle-signals'
$cases = @()
foreach ($mode in @('arm', 'thumb')) {
    $cases += @{ File = "libimports-$mode.so"; Function = 'run_import_tests' }
    $cases += @{ File = "libmono-imports-$mode.so"; Function = 'run_mono_import_tests' }
    $cases += @{ File = "libgles-binary-$mode.so"; Function = 'test_gles_program_binary' }
}
if (!(Test-Path -LiteralPath $runner)) { throw 'Build the ARM64 CLI runner first; see COMPATIBILITY-TESTING.md.' }
if (!(Test-Path -LiteralPath $idleTest)) { throw 'Run tools/build-android.ps1 first to compile the idle-signal regression.' }
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
    & $adb -s $Serial push $idleTest "$remote/idle-signals" *> $null
    if ($LASTEXITCODE) { throw 'Cannot copy the idle-signal regression.' }
    foreach ($case in $cases) {
        & $adb -s $Serial push (Join-Path $repo "target/host-tests/$($case.File)") "$remote/$($case.File)" *> $null
        if ($LASTEXITCODE) { throw 'Cannot copy a test fixture.' }
    }
    & $adb -s $Serial shell chmod 700 "$remote/bridge" "$remote/idle-signals"
    if ($LASTEXITCODE) { throw 'Cannot make the test runner executable.' }
    $libraries = ($cases | ForEach-Object { "$remote/$($_.File)" }) -join ' '
    $output = (& $adb -s $Serial shell "$remote/bridge imports $libraries" 2>&1) -join "`n"
    if ($LASTEXITCODE -or $output -notmatch '\[bridge\] 0 imports without a handler') {
        throw "Fixture import validation failed:`n$output"
    }
    foreach ($case in $cases) {
        # The CLI exits successfully for any returned integer; require an explicit zero too.
        $output = (& $adb -s $Serial shell "cd $remote && ARM32_ENGINE=jit ARM32_LOCKCHECK=1 timeout 120 ./bridge call ./$($case.File) $($case.Function)" 2>&1) -join "`n"
        if ($LASTEXITCODE -or $output -notmatch "(?m)^$($case.Function) returned 0 \(0x0\)\r?$" -or
            $output -match 'stopped:|panicked at|\[heap\] refused|\[mimalloc\].*error') {
            throw "Device regression failed: $($case.File)/$($case.Function)`n$output"
        }
        Write-Output "PASS $($case.File)/$($case.Function)"
    }
    $output = (& $adb -s $Serial shell "cd $remote && ARM32_ENGINE=jit ARM32_LOCKCHECK=1 timeout 120 ./idle-signals --exact signal_runs_between_host_calls_into_guest_code --nocapture" 2>&1) -join "`n"
    if ($LASTEXITCODE -or $output -notmatch 'test result: ok\. 1 passed; 0 failed;' -or
        $output -match 'stopped:|panicked at|\[heap\] refused|\[mimalloc\].*error') {
        throw "Idle-signal device regression failed:`n$output"
    }
    Write-Output 'PASS idle-signals/signal_runs_between_host_calls_into_guest_code'
} finally {
    # Delete only the exact files this invocation created, inside its unique directory.
    & $adb -s $Serial shell rm -f "$remote/bridge" "$remote/idle-signals" "$remote/input.dat" "$remote/output.dat" @($cases | ForEach-Object { "$remote/$($_.File)" }) *> $null
    & $adb -s $Serial shell rmdir $remote *> $null
}
