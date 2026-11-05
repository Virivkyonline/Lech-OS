#
# Copyright (C) 2026 The LECH OS Project
# SPDX-License-Identifier: Apache-2.0
#
# LECH OS version - the single place to change it.
# Format: MAJOR.MINOR.PATCH[-suffix]; no spaces (build properties cannot contain them).
# An environment variable LECH_VERSION overrides it for one build (tools/etapa2/08-aktualizacia.sh
# builds the test update this way).

LECH_VERSION ?= 0.1.0-dev
