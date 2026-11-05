#
# Copyright (C) 2026 The LECH OS Project
# SPDX-License-Identifier: Apache-2.0
#
# Board of the LECH OS GSI for 64-bit ARM (product lech_gsi_arm64, task E2-11a).
# The AOSP GSI board build/make/target/board/generic_arm64 without the second (32-bit ARM) architecture,
# like the Cuttlefish board vsoc_arm64_only. The build finds this file by the device name
# (PRODUCT_DEVICE := lech_arm64) under vendor/.

TARGET_ARCH := arm64
TARGET_ARCH_VARIANT := armv8-a
TARGET_CPU_VARIANT := generic
TARGET_CPU_ABI := arm64-v8a

# Without a 32-bit architecture the audio server is built for the only one (as in vsoc_arm64_only).
AUDIOSERVER_MULTILIB := first

include build/make/target/board/BoardConfigGsiCommon.mk

BOARD_SEPOLICY_DIRS += build/make/target/board/generic_arm64/sepolicy
