#
# Copyright (C) 2026 The LECH OS Project
# SPDX-License-Identifier: Apache-2.0
#
# LECH OS for the Pixel 9 (tokay) – full build with own vendor, boot and vbmeta images (task E2-11b).
#
# The device support comes from GrapheneOS (ADR-0002, manifests/lech-pixel.xml): adevtool generates
# vendor/google_devices/tokay from the stock factory image of Google (vendor files, firmware, overlays and the
# IMS of the Pixel for SMS and VoLTE), the device makefiles are part of adevtool (vendor/adevtool/config/mk)
# and the kernel is the prebuilt one of GrapheneOS (device/google/caimito-kernels/6.1).
# tools/etapa2/12-priprav-pixel.sh prepares all of this, tools/etapa2/12-zostav-pixel.sh builds the product.
#
# Note: do not put comments at the end of assignment lines - make keeps the spaces before "#".

ifeq ($(wildcard vendor/google_devices/tokay/tokay.mk),)
$(error LECH OS: chýba vendor/google_devices/tokay - spustite tools/etapa2/12-priprav-pixel.sh)
endif

# Kernel of the Pixel 9 family. GrapheneOS names the directory with its release flag RELEASE_KERNEL_TOKAY_DIR,
# which AOSP does not have; the adevtool device makefile only sets these when they are still empty (?=).
TARGET_KERNEL_DIR := device/google/caimito-kernels/6.1/grapheneos
TARGET_BOARD_KERNEL_HEADERS := $(TARGET_KERNEL_DIR)/kernel-headers

# Development build: the generic ARMv8-A target of the LECH OS GSI instead of ARMv9 with memory tagging (MTE),
# so the system already compiled for the GSI is reused (patches/vendor/adevtool/0002). Release builds: false.
LECH_PIXEL_GENERIC_CPU := true

$(call inherit-product, vendor/google_devices/tokay/tokay.mk)
$(call inherit-product, vendor/lech/config/common.mk)

PRODUCT_NAME := lech_tokay
PRODUCT_DEVICE := tokay
PRODUCT_BRAND := LECH
PRODUCT_MANUFACTURER := LECH
PRODUCT_MODEL := LECH-PHONE
