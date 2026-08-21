// Copyright 2026 Dmitry Vasyliev
// SPDX-License-Identifier: GPL-3.0-or-later

const core = @import("core.zig");

// Will write the comptime function name generator for JNI later
export fn Java_com_raydrivers_starguide_CoreBridge_add(
    env: ?*anyopaque,
    thiz: ?*anyopaque,
    a: i32,
    b: i32,
) i32 {
    _ = env;
    _ = thiz;
    return core.add(a, b);
}
