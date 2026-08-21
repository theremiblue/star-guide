#!/usr/bin/env bash
# Copyright 2026 Dmitry Vasyliev
# SPDX-License-Identifier: GPL-3.0-or-later
#
# NOTE: Using @wakeful's fork because his
# PR #209 (Zig support) remains unmerged:
# https://github.com/google/addlicense/pull/209


cd "$(dirname "$0")/.."

files=()
while IFS= read -r file; do
    files+=("$file")
done < <(git ls-files)

ignore_args=()
while IFS= read -r ignore; do
    ignore_args+=(-ignore "$ignore")
done < .addlicenseignore

addlicense \
    -s \
    -l GPL-3.0-or-later \
    -c "$(git config --get user.name)" \
    -y 2026 \
    "${ignore_args[@]}" \
    "${files[@]}"
