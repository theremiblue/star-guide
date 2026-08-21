// Copyright 2026 Dmitry Vasyliev
// SPDX-License-Identifier: GPL-3.0-or-later

const std = @import("std");

const AndroidNdk = @import("build/AndroidNdk.zig");

const AndroidTarget = struct {
    cpu_arch: std.Target.Cpu.Arch,
    jni_abi: []const u8,
};

// TODO: read more about the android targets
//       and expand the list.
//
//       For now this is my phone and emulator on macos
const android_targets: []const AndroidTarget = &.{
    .{
        .cpu_arch = .aarch64,
        .jni_abi = "arm64-v8a",
    },
    .{
        .cpu_arch = .x86_64,
        .jni_abi = "x86_64",
    },
};

fn addCoreLibraryTarget(
    b: *std.Build,
    optimize: std.builtin.OptimizeMode,
    ndk: AndroidNdk,
    android_target: AndroidTarget,
) void {
    const target = b.resolveTargetQuery(
        ndk.query(android_target.cpu_arch),
    );

    const lib = b.addLibrary(.{
        .name = "core",
        .linkage = .dynamic,
        .root_module = b.createModule(.{
            .root_source_file = b.path("src/ffi_android.zig"),
            .target = target,
            .optimize = optimize,
            // TODO: I haven't figured out how NOT to link link libc
            //       I don't fully understand the context here, so
            //       that's why the comment exists
            //
            //       Zig uses getauxval only to determine cpu capabilities
            //       Nothing else is referenced in binary
            //
            //       in my understanding we need to add NEEDS libc (bionic)
            //       so that loader resolves the reference
            .link_libc = true,
        }),
    });

    lib.setLibCFile(ndk.libcFile( b, android_target.cpu_arch));

    const install = b.addInstallArtifact(lib, .{
        .dest_dir = .{ .override = .{ .custom = android_target.jni_abi } },
    });
    b.getInstallStep().dependOn(&install.step);
}

pub fn build(b: *std.Build) void {
    // TODO: in my understanding, linking to NDK is mandatory
    //       to link to libc. Should investigate deeper
    const ndk = AndroidNdk.init(b);
    b.sysroot = ndk.sysroot;

    const optimize = b.standardOptimizeOption(.{});

    for (android_targets) |android_target| {
        addCoreLibraryTarget(b, optimize, ndk, android_target);
    }
}
