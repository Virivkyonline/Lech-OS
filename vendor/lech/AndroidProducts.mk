#
# Copyright (C) 2026 The LECH OS Project
# SPDX-License-Identifier: Apache-2.0
#
# LECH OS products. The AOSP build finds every AndroidProducts.mk under vendor/ automatically,
# so this directory only has to be present in the source tree as vendor/lech
# (tools/etapa2/06-zostav-lech.sh copies it there).
#
# Product names must not contain "-": lunch splits product-release-variant on it.

PRODUCT_MAKEFILES := \
    lech_cf_x86_64_only_phone:$(LOCAL_DIR)/products/lech_cf_x86_64_only_phone.mk \
    lech_gsi_arm64:$(LOCAL_DIR)/products/lech_gsi_arm64.mk \
    lech_tokay:$(LOCAL_DIR)/products/lech_tokay.mk \

COMMON_LUNCH_CHOICES := \
    lech_cf_x86_64_only_phone-trunk_staging-userdebug \
    lech_gsi_arm64-trunk_staging-userdebug \
    lech_tokay-trunk_staging-userdebug \

