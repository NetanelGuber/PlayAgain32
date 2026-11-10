// Synthetic regression for issue #1. Contains no game code or proprietary assets.
#include <errno.h>
#include <stddef.h>
#include <stdint.h>
#include <malloc.h>
#include <stdlib.h>
#include <string.h>
#include <sys/epoll.h>
#include <unistd.h>

_Static_assert(sizeof(div_t) == 8, "ARM32 div_t size");
_Static_assert(sizeof(void *) == 4, "compile this fixture for ARM32");
_Static_assert(sizeof(struct epoll_event) == 16, "ARM32 epoll_event size");
_Static_assert(offsetof(struct epoll_event, data) == 8, "ARM32 epoll data alignment");

int test_div(void) {
    const int inputs[][4] = {{17, 5, 3, 2}, {-17, 5, -3, -2}, {17, -5, -3, 2}, {-17, -5, 3, -2}};
    for (unsigned i = 0; i < sizeof(inputs) / sizeof(inputs[0]); ++i) {
        div_t result = div(inputs[i][0], inputs[i][1]);
        if (result.quot != inputs[i][2] || result.rem != inputs[i][3]) return 1;
    }
    return 0;
}

int test_environment(void) {
    char **original = environ;
    if (!original) return 1;
    if (setenv("PLAYAGAIN32_TEST_ENV", "one", 1) || !getenv("PLAYAGAIN32_TEST_ENV")) return 2;
    char *entry = NULL;
    const unsigned prefix = sizeof("PLAYAGAIN32_TEST_ENV=") - 1;
    for (char **at = environ; *at; ++at) {
        if (!strncmp(*at, "PLAYAGAIN32_TEST_ENV=", prefix)) entry = *at;
    }
    if (!entry || strcmp(entry + prefix, "one")) return 3;
    if (setenv("PLAYAGAIN32_TEST_ENV", "bad", 0) || strcmp(getenv("PLAYAGAIN32_TEST_ENV"), "one")) return 4;
    if (setenv("PLAYAGAIN32_TEST_ENV", "two", 1) || strcmp(getenv("PLAYAGAIN32_TEST_ENV"), "two")) return 5;
    if (getenv("PLAYAGAIN32_TEST_EN")) return 6;
    errno = 0;
    if (setenv("BAD=NAME", "bad", 1) != -1 || errno != EINVAL) return 7;
    char custom[] = "PLAYAGAIN32_DIRECT=edited";
    char *replacement[] = {custom, NULL};
    environ = replacement;
    if (!getenv("PLAYAGAIN32_DIRECT") || strcmp(getenv("PLAYAGAIN32_DIRECT"), "edited")) return 8;
    if (setenv("PLAYAGAIN32_NEW", "added", 1) || strcmp(getenv("PLAYAGAIN32_DIRECT"), "edited")) return 9;
    environ = original;
    return 0;
}

int test_epoll(void) {
    errno = 0;
    if (epoll_create(0) != -1 || errno != EINVAL) return 1;
    int epfd = epoll_create(1), pair[2];
    if (epfd < 0 || pipe(pair)) return 2;
    struct epoll_event add = {.events = EPOLLIN | EPOLLONESHOT, .data.u64 = UINT64_C(0xfedcba9876543210)};
    struct { struct epoll_event event; uint32_t guard; } result = {.guard = 0x12345678};
    if (epoll_ctl(epfd, EPOLL_CTL_ADD, pair[0], &add)) return 3;
    if (epoll_wait(epfd, &result.event, 1, 0) != 0) return 4;
    if (write(pair[1], "x", 1) != 1 || epoll_wait(epfd, &result.event, 1, 100) != 1) return 5;
    if (!(result.event.events & EPOLLIN) || result.event.data.u64 != add.data.u64 || result.guard != 0x12345678) return 6;
    if (epoll_wait(epfd, &result.event, 1, 0) != 0) return 7;
    add.data.u64 = UINT64_C(0x0123456789abcdef);
    if (epoll_ctl(epfd, EPOLL_CTL_MOD, pair[0], &add) || epoll_wait(epfd, &result.event, 1, 100) != 1 || result.event.data.u64 != add.data.u64) return 8;
    if (epoll_ctl(epfd, EPOLL_CTL_DEL, pair[0], NULL) || epoll_wait(epfd, &result.event, 1, 0) != 0) return 9;
    errno = 0;
    if (epoll_wait(epfd, &result.event, 0, 0) != -1 || errno != EINVAL) return 10;
    errno = 0;
    if (epoll_wait(-1, &result.event, 1, 0) != -1 || errno != EBADF) return 11;
    close(pair[0]); close(pair[1]); close(epfd);
    epfd = epoll_create1(EPOLL_CLOEXEC);
    if (epfd < 0) return 12;
    close(epfd);
    return 0;
}

int test_execv(void) {
    char *args[] = {"true", NULL};
    errno = 0;
    return execv("/system/bin/true", args) == -1 && errno == ENOSYS ? 0 : 1;
}

static unsigned environment_growth;
int test_environment_memory(void) {
    char value[8192];
    memset(value, 'x', sizeof(value) - 1);
    value[sizeof(value) - 1] = 0;
    for (int i = 0; i < 64; ++i) {
        if (setenv("PLAYAGAIN32_MEMORY", i & 1 ? value : "short", 1)) return 1;
    }
    unsigned before = mallinfo().arena;
    for (int i = 0; i < 10000; ++i) {
        if (setenv("PLAYAGAIN32_MEMORY", i & 1 ? value : "short", 1)) return 2;
    }
    environment_growth = mallinfo().arena - before;
    return environment_growth > 65536 ? 3 : 0;
}

int measure_environment_growth(void) {
    return test_environment_memory() ? -1 : (int)environment_growth;
}

int test_environment_allocation_failure(void) {
    static void *blocks[8192];
    static char *replacement[1025];
    char value[8192];
    memset(value, 'x', sizeof(value) - 1); value[sizeof(value) - 1] = 0;
    if (setenv("PLAYAGAIN32_FAILURE", "before", 1)) return 1;
    char **original = environ;
    unsigned count = 0;
    const unsigned sizes[] = {1048576, 16384, 1024, 64};
    for (unsigned i = 0; i < sizeof(sizes) / sizeof(sizes[0]); ++i) {
        while (count < sizeof(blocks) / sizeof(blocks[0])) {
            void *block = malloc(sizes[i]);
            if (!block) break;
            blocks[count++] = block;
        }
    }
    errno = 0;
    int ok = setenv("PLAYAGAIN32_FAILURE", value, 1) == -1 && errno == ENOMEM &&
             !strcmp(getenv("PLAYAGAIN32_FAILURE"), "before");
    if (!count) return 2;
    free(blocks[--count]);
    for (unsigned i = 0; i < 1024; ++i) replacement[i] = "KEEP=value";
    replacement[1024] = NULL;
    environ = replacement;
    errno = 0;
    ok = ok && setenv("NEW", "value", 1) == -1 && errno == ENOMEM && environ == replacement;
    while (count) free(blocks[--count]);
    environ = original;
    return ok ? 0 : 3;
}

int test_epoll_resources(void) {
    for (int i = 0; i < 2048; ++i) {
        int fd = epoll_create1(EPOLL_CLOEXEC);
        if (fd < 0 || close(fd)) return 1;
    }
    int fd = epoll_create1(EPOLL_CLOEXEC);
    if (fd < 0) return 2;
    struct epoll_event event = {0};
    errno = 0;
    int ok = epoll_wait(fd, &event, INT32_MAX, 0) == -1 && errno == EINVAL;
    errno = 0;
    ok = ok && epoll_wait(fd, (struct epoll_event *)(uintptr_t)0xfffffff8u, 1, 0) == -1 && errno == EFAULT;
    close(fd);
    return ok ? 0 : 3;
}

int run_import_tests(void) {
    return (test_div() ? 1 : 0) | (test_environment() ? 2 : 0) |
           (test_epoll() ? 4 : 0) | (test_execv() ? 8 : 0) |
           (test_environment_memory() ? 16 : 0) | (test_environment_allocation_failure() ? 32 : 0) |
           (test_epoll_resources() ? 64 : 0);
}
