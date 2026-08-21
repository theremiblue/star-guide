// Copyright 2026 Dmitry Vasyliev
// SPDX-License-Identifier: GPL-3.0-or-later

const Self = @This();

const std = @import("std");
const builtin = @import("builtin");

ndk_root: []const u8,
sysroot: []const u8,
android_api_level: u32,

pub fn init(b: *std.Build) Self {
    const ndk_path = b.option(
        []const u8,
        "android-ndk",
        "Path to Android NDK",
    ) orelse @panic("missing -Dandroid-ndk=<path>");
    const android_api_level = b.option(
        u32,
        "android-api",
        "Android API level for native targets",
    ) orelse @panic("missing -Dandroid-api=<level>");

    const host_prebuilt = detectHostPrebuilt();

    const sysroot = b.pathJoin(&.{
        ndk_path,
        "toolchains",
        "llvm",
        "prebuilt",
        host_prebuilt,
        "sysroot",
    });

    return .{
        .ndk_root = ndk_path,
        .sysroot = sysroot,
        .android_api_level = android_api_level,
    };
}

pub fn query(self: Self, cpu_arch: std.Target.Cpu.Arch) std.Target.Query {
    return .{
        .cpu_arch = cpu_arch,
        .os_tag = .linux,
        .abi = .android,
        .android_api_level = self.android_api_level,
    };
}

pub fn libcFile(
    self: Self,
    b: *std.Build,
    cpu_arch: std.Target.Cpu.Arch,
) std.Build.LazyPath {
    const triple = androidTriple(cpu_arch);

    const include_dir = b.pathJoin(&.{
        self.sysroot,
        "usr",
        "include",
    });

    const arch_include_dir = b.pathJoin(&.{
        self.sysroot,
        "usr",
        "include",
        triple,
    });

    const crt_dir = b.pathJoin(&.{
        self.sysroot,
        "usr",
        "lib",
        triple,
        b.fmt("{d}", .{self.android_api_level}),
    });

    const cfg = b.fmt(
        \\include_dir={s}
        \\sys_include_dir={s}
        \\crt_dir={s}
        \\msvc_lib_dir=
        \\kernel32_lib_dir=
        \\gcc_dir=
        \\
    , .{
        include_dir,
        arch_include_dir,
        crt_dir,
    });

    const wf = b.addWriteFiles();

    return wf.add(
        b.fmt("libc-{s}-{d}.conf", .{ triple, self.android_api_level }),
        cfg,
    );
}

fn androidTriple(cpu_arch: std.Target.Cpu.Arch) []const u8 {
    return switch (cpu_arch) {
        .aarch64 => "aarch64-linux-android",
        .x86_64 => "x86_64-linux-android",
        .arm => "arm-linux-androideabi",
        .x86 => "i686-linux-android",
        else => @panic("unsupported android arch"),
    };
}

fn detectHostPrebuilt() []const u8 {
    return switch (builtin.os.tag) {
        .linux => "linux-x86_64",
        .macos => "darwin-x86_64",
        .windows => "windows-x86_64",
        else => @panic("unsupported host OS for Android NDK"),
    };
}
