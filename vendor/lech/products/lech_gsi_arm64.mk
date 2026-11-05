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
# Composed like the AOSP GSI aosp_arm64 (build/make/target/product/aosp_arm64.mk), but 64-bit only:
# core_64_bit_only.mk and the board vendor/lech/board/lech_arm64 without the 32-bit ARM architecture, like the
# Cuttlefish phone vsoc_arm64_only. The Pixel 9 runs no 32-bit code (its CPU cores have no AArch32), and with
# both architectures the Soong analysis needed more memory than the build notebook has (5. 11. 2026).
# The product partition is folded into the system image (system/product) and the phone's own /product and
# /system_ext partitions are not mounted (gsi_skip_mount.cfg) - the Google apps of the Pixel stay unused.
# tools/etapa2/11-zostav-gsi.sh warns when aosp_arm64.mk gains an inherited makefile or a product block
# variable that is missing here.
#
# Note: do not put comments at the end of assignment lines - make keeps the spaces before "#".

# System image (aosp_arm64.mk: core_64_bit.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit_only.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/generic_system.mk)

# system_ext (folded into the system image)
$(call inherit-product, $(SRC_TARGET_DIR)/product/handheld_system_ext.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/telephony_system_ext.mk)

# pKVM
$(call inherit-product-if-exists, packages/modules/Virtualization/apex/product_packages.mk)

# product (folded into the system image)
$(call inherit-product, $(SRC_TARGET_DIR)/product/aosp_product.mk)

$(call inherit-product, $(SRC_TARGET_DIR)/board/generic_arm64/device.mk)
AB_OTA_UPDATER := true
AB_OTA_PARTITIONS ?= system

# aosp_arm64.mk switches these on only for its own product name; mirrored for LECH OS: the system image may
# contain the product files (LECH identity, overlay, boot animation), mainline modules are built from source
# (so the LECH patches in patches/ apply) and the GSI release settings are used.
# Not mirrored on purpose: PRODUCT_SOONG_DEFINED_SYSTEM_IMAGE / USE_SOONG_DEFINED_SYSTEM_IMAGE - the Soong
# system image aosp_system_image has a fixed module list without the LECH product files.
ifeq ($(TARGET_PRODUCT),lech_gsi_arm64)
PRODUCT_ENFORCE_ARTIFACT_PATH_REQUIREMENTS := relaxed
MODULE_BUILD_FROM_SOURCE ?= true
$(call inherit-product, $(SRC_TARGET_DIR)/product/gsi_release.mk)
endif

# The 64-bit zygote script lives in the root of the system image (as in aosp_64bitonly_x86_64.mk).
PRODUCT_ARTIFACT_PATH_REQUIREMENT_ALLOWED_LIST += \
    root/init.zygote64.rc \

PRODUCT_NO_BIONIC_PAGE_SIZE_MACRO := true

$(call inherit-product, vendor/lech/config/common.mk)

PRODUCT_NAME := lech_gsi_arm64
PRODUCT_DEVICE := lech_arm64
PRODUCT_BRAND := LECH
PRODUCT_MANUFACTURER := LECH
PRODUCT_MODEL := LECH-PHONE
