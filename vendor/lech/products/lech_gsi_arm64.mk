#
# Copyright (C) 2026 The LECH OS Project
# SPDX-License-Identifier: Apache-2.0
#
# LECH OS as a generic system image (GSI) for 64-bit ARM phones (task E2-11a, first target Pixel 9).
#
# Only the system image is LECH OS; the phone keeps its own vendor, kernel and boot images. Since Android 16
# Google no longer publishes the Pixel device trees, so a full Pixel build needs community device trees
# (task E2-11b); the GSI runs on the Pixel already now, with the bootloader unlocked.
#
# Everything is inherited from the AOSP GSI aosp_arm64; LECH OS adds its identity (config/common.mk).
# The product partition is folded into the system image (system/product) and the phone's own /product and
# /system_ext partitions are not mounted (gsi_skip_mount.cfg) - the Google apps of the Pixel stay unused.
#
# Note: do not put comments at the end of assignment lines - make keeps the spaces before "#".

$(call inherit-product, $(SRC_TARGET_DIR)/product/aosp_arm64.mk)
$(call inherit-product, vendor/lech/config/common.mk)

PRODUCT_NAME := lech_gsi_arm64
PRODUCT_DEVICE := generic_arm64
PRODUCT_BRAND := LECH
PRODUCT_MANUFACTURER := LECH
PRODUCT_MODEL := LECH-PHONE

# aosp_arm64.mk switches these on only for its own product name. Mirror them like aosp_arm64_fullmte.mk does:
# the system image may contain the product files (LECH identity, overlay, boot animation), mainline modules
# are built from source (so the LECH patches in patches/ apply) and the GSI release settings are used.
# Not mirrored on purpose: PRODUCT_SOONG_DEFINED_SYSTEM_IMAGE / USE_SOONG_DEFINED_SYSTEM_IMAGE - the Soong
# system image aosp_system_image has a fixed module list without the LECH product files.
# tools/etapa2/11-zostav-gsi.sh warns when aosp_arm64.mk gains another block variable.
ifeq ($(TARGET_PRODUCT),lech_gsi_arm64)
PRODUCT_ENFORCE_ARTIFACT_PATH_REQUIREMENTS := relaxed
MODULE_BUILD_FROM_SOURCE ?= true
$(call inherit-product, $(SRC_TARGET_DIR)/product/gsi_release.mk)
endif
