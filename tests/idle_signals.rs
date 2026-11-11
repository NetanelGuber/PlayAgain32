#![cfg(all(target_os = "android", target_arch = "aarch64"))]

use std::sync::atomic::Ordering;
use std::thread;
use std::time::{Duration, Instant};

use arm32_bridge::{imports::threads, runtime};

#[test]
fn signal_runs_between_host_calls_into_guest_code() {
    runtime::init(false);
    assert!(runtime::parallel(), "this regression exercises the parallel translator");

    let target = threads::current_identity();
    let signal = libc::SIGUSR1 as u32;
    let action = runtime::shared().misc_alloc(16, 4);
    let getpid = runtime::stub_for("getpid");
    // Guest ARM32 sigaction: handler, mask, flags, restorer. A benign bridge
    // stub serves as the handler, so the test needs no game or fixture APK.
    for (i, value) in [getpid, 0, 0, 0].into_iter().enumerate() {
        runtime::mem().write_u32(action + i as u32 * 4, value);
    }
    let sigaction = runtime::stub_for("sigaction");
    assert_eq!(runtime::call_guest(runtime::engine(), sigaction, &[signal, action, 0]).unwrap() as u32, 0);

    let handle = target.handle;
    thread::spawn(move || {
        let kill = runtime::stub_for("pthread_kill");
        let result = runtime::call_guest(runtime::engine(), kill, &[handle, signal]).unwrap() as u32;
        runtime::drop_engine();
        assert_eq!(result, 0);
    })
    .join()
    .unwrap();

    let deadline = Instant::now() + Duration::from_secs(3);
    while target.delivered.load(Ordering::Acquire) == 0 && Instant::now() < deadline {
        thread::sleep(Duration::from_millis(5));
    }
    let delivered = target.delivered.load(Ordering::Acquire);
    let pid = runtime::call_guest(runtime::engine(), getpid, &[]).unwrap() as u32;
    runtime::drop_engine();
    runtime::stop_engine_builder().unwrap();
    assert_eq!(delivered, 1, "signal waited for another guest call");
    assert_eq!(pid, std::process::id());
}
