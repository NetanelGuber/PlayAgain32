// Complete issue #2 import-family regression; no game code/assets.
#define _GNU_SOURCE 1
#include <errno.h>
#include <fcntl.h>
#include <grp.h>
#include <inttypes.h>
#include <limits.h>
#include <malloc.h>
#include <math.h>
#include <pwd.h>
#include <signal.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/file.h>
#include <sys/ptrace.h>
#include <sys/sendfile.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <sys/uio.h>
#include <sys/wait.h>
#include <unistd.h>

// Legacy Bionic export used by Mono, absent from current public NDK headers.
extern int tkill(pid_t, int);

_Static_assert(sizeof(struct passwd) == 24, "Bionic ARM32 omits pw_gecos");
_Static_assert(sizeof(struct group) == 16, "ARM32 group layout");
_Static_assert(sizeof(struct msghdr) == 28, "ARM32 message layout");
_Static_assert(sizeof(struct cmsghdr) == 12, "ARM32 ancillary layout");
_Static_assert(sizeof(off_t) == 4, "ARM32 sendfile offset");

int test_mono_numbers(void) {
    union { float f; uint32_t bits; } x;
    float (*volatile absolute)(float) = fabsf;
    x.bits = 0x80000000u; x.f = absolute(x.f); if (x.bits != 0) return 1;
    x.bits = 0xffc12345u; x.f = absolute(x.f); if (x.bits != 0x7fc12345u) return 2;
    if (absolute(-3.5f) != 3.5f) return 3;
    char *end;
    errno = EINTR;
    if (strtoimax("-9223372036854775808x", &end, 10) != INT64_MIN || *end != 'x' || errno != EINTR) return 4;
    errno = 0;
    if (strtoimax("-9223372036854775809", &end, 10) != INT64_MIN || errno != ERANGE) return 5;
    errno = 0;
    if (strtoumax("18446744073709551615x", &end, 10) != UINT64_MAX || *end != 'x' || errno) return 6;
    errno = 0;
    if (strtoumax("18446744073709551616", &end, 10) != UINT64_MAX || errno != ERANGE) return 7;
    return 0;
}

int test_mono_strings(void) {
    char tokens[] = ",a,,b,"; char *next = tokens;
    const char *expected[] = {"", "a", "", "b", ""};
    for (int i = 0; i < 5; ++i) { char *p = strsep(&next, ","); if (!p || strcmp(p, expected[i])) return 1; }
    if (next || strsep(&next, ",")) return 2;
    if (setenv("PLAYAGAIN32_REMOVE", "v", 1) || setenv("PLAYAGAIN32_REMOVE_MORE", "v", 1) || unsetenv("PLAYAGAIN32_REMOVE")) return 3;
    if (getenv("PLAYAGAIN32_REMOVE") || !getenv("PLAYAGAIN32_REMOVE_MORE")) return 4;
    if (unsetenv("PLAYAGAIN32_ABSENT")) return 5;
    errno = 0; if (unsetenv("bad=name") != -1 || errno != EINVAL) return 6;
    char path[4096];
    if (realpath(".", path) != path || path[0] != '/') return 7;
    char *allocated = realpath(".", NULL);
    if (!allocated || strcmp(path, allocated)) return 8;
    free(allocated);
    return 0;
}

int test_mono_accounts(void) {
    struct passwd *user = getpwuid(getuid());
    if (!user || !user->pw_name || !user->pw_dir || user->pw_uid != getuid()) return 1;
    char name[256]; strncpy(name, user->pw_name, sizeof(name)); name[255] = 0;
    user = getpwnam(name); if (!user || user->pw_uid != getuid()) return 2;
    struct group *group = getgrgid(getgid());
    if (!group || !group->gr_name || !group->gr_mem || group->gr_gid != getgid()) return 3;
    strncpy(name, group->gr_name, sizeof(name)); name[255] = 0;
    group = getgrnam(name); if (!group || group->gr_gid != getgid()) return 4;
    unsigned before = mallinfo().arena;
    for (int i = 0; i < 10000; ++i) { if (!getpwuid(getuid()) || !getgrgid(getgid())) return 5; }
    if (mallinfo().arena - before > 65536) return 6;
    if (getpwnam("playagain32_nonexistent_user_7f3a") || getgrnam("playagain32_nonexistent_group_7f3a")) return 7;
    return 0;
}

int test_mono_files(void) {
    const char *a = "input.dat", *b = "output.dat";
    int input = open(a, O_CREAT | O_TRUNC | O_RDWR, 0600), output = open(b, O_CREAT | O_TRUNC | O_RDWR, 0600);
    if (input < 0 || output < 0) return 1;
    int other = open(a, O_RDWR);
    if (other < 0 || flock(input, LOCK_EX | LOCK_NB)) return 2;
    errno = 0;
    if (flock(other, LOCK_EX | LOCK_NB) != -1 || (errno != EAGAIN && errno != EWOULDBLOCK)) return 3;
    if (flock(input, LOCK_UN) || flock(other, LOCK_EX | LOCK_NB) || flock(other, LOCK_UN)) return 4;
    if (write(input, "abcdef", 6) != 6 || lseek(input, 0, SEEK_SET)) return 5;
    struct { off_t offset; uint32_t guard; } position = {2, 0x12345678};
    if (sendfile(output, input, &position.offset, 3) != 3 || position.offset != 5 || position.guard != 0x12345678) return 6;
    if (lseek(input, 0, SEEK_CUR) != 0 || sendfile(output, input, NULL, 2) != 2 || lseek(input, 0, SEEK_CUR) != 2) return 7;
    char data[5]; if (lseek(output, 0, SEEK_SET) || read(output, data, 5) != 5 || memcmp(data, "cdeab", 5)) return 8;
    close(input); close(output); close(other); unlink(a); unlink(b);
    return 0;
}

static volatile sig_atomic_t signalled;
static void signal_handler(int signal) { if (signal == SIGUSR1) signalled++; }
int test_mono_process(void) {
    uid_t real, effective, saved;
    if (getresuid(&real, &effective, &saved) || real != getuid() || effective != geteuid()) return 1;
    if (setresuid((uid_t)-1, (uid_t)-1, (uid_t)-1)) return 2;
    errno = 0; if (execve("/system/bin/true", (char *[]){"true", NULL}, (char *[]){NULL}) != -1 || errno != ENOSYS) return 3;
    errno = 0; if (ptrace(PTRACE_TRACEME, 0, NULL, NULL) != -1 || errno != ENOSYS) return 4;
    int status = 0x12345678;
    errno = 0; if (waitpid(-1, &status, WNOHANG) != -1 || errno != ECHILD || status != 0x12345678) return 5;
    struct sigaction action = {0}; action.sa_handler = signal_handler; sigemptyset(&action.sa_mask);
    if (sigaction(SIGUSR1, &action, NULL) || tkill(gettid(), 0) || tkill(gettid(), SIGUSR1) || signalled != 1) return 6;
    errno = 0; if (tkill(INT_MAX, 0) != -1 || errno != ESRCH) return 7;
    return 0;
}

int test_mono_messages(void) {
    int pair[2]; if (socketpair(AF_UNIX, SOCK_DGRAM, 0, pair)) return 1;
    struct iovec parts[2] = {{"ab", 2}, {"cd", 2}};
    struct msghdr send = {0}; send.msg_iov = parts; send.msg_iovlen = 2;
    if (sendmsg(pair[0], &send, 0) != 4) return 2;
    char left[2], right[2]; struct iovec output[2] = {{left, 2}, {right, 2}};
    struct msghdr receive = {0}; receive.msg_iov = output; receive.msg_iovlen = 2;
    if (recvmsg(pair[1], &receive, 0) != 4 || memcmp(left, "ab", 2) || memcmp(right, "cd", 2)) return 3;
    int pipefd[2]; if (pipe(pipefd)) return 4;
    union { struct cmsghdr align; char bytes[CMSG_SPACE(sizeof(int))]; } control = {0}, incoming = {0};
    char byte = 'x'; struct iovec one = {&byte, 1};
    send.msg_iov = &one; send.msg_iovlen = 1; send.msg_control = control.bytes; send.msg_controllen = sizeof(control);
    struct cmsghdr *c = CMSG_FIRSTHDR(&send); c->cmsg_level = SOL_SOCKET; c->cmsg_type = SCM_RIGHTS; c->cmsg_len = CMSG_LEN(sizeof(int));
    memcpy(CMSG_DATA(c), &pipefd[1], sizeof(int));
    receive.msg_iov = &one; receive.msg_iovlen = 1; receive.msg_control = incoming.bytes; receive.msg_controllen = sizeof(incoming);
    if (sendmsg(pair[0], &send, 0) != 1 || recvmsg(pair[1], &receive, MSG_CMSG_CLOEXEC) != 1 || (receive.msg_flags & MSG_CTRUNC)) return 5;
    c = CMSG_FIRSTHDR(&receive); if (!c || c->cmsg_level != SOL_SOCKET || c->cmsg_type != SCM_RIGHTS) return 6;
    int received; memcpy(&received, CMSG_DATA(c), sizeof(int));
    if (!(fcntl(received, F_GETFD) & FD_CLOEXEC) || write(received, "z", 1) != 1 || read(pipefd[0], &byte, 1) != 1 || byte != 'z') return 7;
    close(received);
    for (int i = 0; i < 2048; ++i) {
        receive.msg_controllen = sizeof(struct cmsghdr);
        if (sendmsg(pair[0], &send, 0) != 1 || recvmsg(pair[1], &receive, 0) != 1 || !(receive.msg_flags & MSG_CTRUNC)) return 8;
    }
    close(pipefd[0]); close(pipefd[1]); close(pair[0]); close(pair[1]);
    return 0;
}

int run_mono_import_tests(void) {
    return (test_mono_numbers() ? 1 : 0) | (test_mono_strings() ? 2 : 0) | (test_mono_accounts() ? 4 : 0) |
           (test_mono_files() ? 8 : 0) | (test_mono_process() ? 16 : 0) | (test_mono_messages() ? 32 : 0);
}
