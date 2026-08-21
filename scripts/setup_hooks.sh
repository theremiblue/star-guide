#!/usr/bin/env bash
# Copyright 2026 Dmitry Vasyliev
# SPDX-License-Identifier: GPL-3.0-or-later


cd "$(dirname "$0")/.."

mkdir -p .git/hooks
install -m 755 .githooks/pre-commit .git/hooks/pre-commit
