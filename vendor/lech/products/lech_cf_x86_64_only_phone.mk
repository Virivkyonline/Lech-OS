#
# Copyright (C) 2026 The LECH OS Project
# SPDX-License-Identifier: Apache-2.0
#
# LECH OS for Cuttlefish: the 64-bit x86 virtual phone (tasks E2-04 and E2-05).
#
# Everything is inherited from the AOSP Cuttlefish phone aosp_cf_x86_64_only_phone; LECH OS adds
# its identity on top (config/common.mk). The device (PRODUCT_DEVICE) stays the same, so the board
# configuration and the out/target/product/vsoc_x86_64_only directory are reused and the build
# continues incrementally from the plain AOSP build.
#
# Note: do not put comments at the end of assignment lines - make keeps the spaces before "#".

$(call inherit-product, device/google/cuttlefish/vsoc_x86_64_only/phone/aosp_cf.mk)
$(call inherit-product, vendor/lech/config/common.mk)

PRODUCT_NAME := lech_cf_x86_64_only_phone
PRODUCT_DEVICE := vsoc_x86_64_only
PRODUCT_BRAND := LECH
PRODUCT_MANUFACTURER := LECH
PRODUCT_MODEL := LECH-PHONE

# The Cuttlefish phone switches these build modes on only for its own product name
# (see the end of aosp_cf.mk). Mirror them so this product is built exactly the same way.
# tools/etapa2/06-zostav-lech.sh warns when aosp_cf.mk gains a block that is not mirrored here.
ifeq ($(TARGET_PRODUCT),lech_cf_x86_64_only_phone)
PRODUCT_SOONG_ONLY := $(RELEASE_SOONG_ONLY_CUTTLEFISH)
PRODUCT_SOONG_INCREMENTAL_ANALYSIS := $(RELEASE_SOONG_INCREMENTAL_ANALYSIS)
endif
