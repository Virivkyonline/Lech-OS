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
# which AOSP does not have; the adevtool device makefile only sets these when they are still undefined (?=).
TARGET_KERNEL_DIR := device/google/caimito-kernels/6.1/grapheneos
# The kernel package has no kernel-headers directory (adevtool would point there) and Soong reports a missing
# source path as an error. All vendor modules are prebuilt from the stock image, so no kernel headers are needed:
# empty, as on Cuttlefish.
TARGET_BOARD_KERNEL_HEADERS :=

# Development build: the generic ARMv8-A target of the LECH OS GSI instead of ARMv9 with memory tagging (MTE),
# so the system already compiled for the GSI is reused (patches/vendor/adevtool/0002). Release builds: false.
LECH_PIXEL_GENERIC_CPU := true

# Verified boot (E2-12): vbmeta - the hashes of boot, init_boot, vendor_boot, vendor_kernel_boot, dtbo, pvmfw and the
# hash trees of system, system_ext, product, vendor, system_dlkm, vendor_dlkm - is signed with the LECH OS AVB key
# instead of the public AOSP test key (external/avb/test/data/testkey_rsa4096.pem, public key sha1 2597c218...).
# Its public part is flashed into the phone as avb_custom_key before the bootloader is locked
# (tools/etapa2/11-pixel.sh zamknut). The key is created and copied by tools/etapa2/06-zostav-lech.sh.
ifeq ($(wildcard vendor/lech-keys/test/avb.pem),)
$(error LECH OS: chýba kľúč AVB vendor/lech-keys/test/avb.pem - spustite tools/etapa2/06-zostav-lech.sh --len-skopirovat)
endif
BOARD_AVB_ALGORITHM := SHA256_RSA4096
BOARD_AVB_KEY_PATH := vendor/lech-keys/test/avb.pem

# Mainline modules from source, so the LECH patches in packages/modules apply (as in lech_gsi_arm64.mk).
# The release configuration is cp2a (tools/etapa2/12-zostav-pixel.sh): vendor API level 202604 of the stock
# vendor files.
MODULE_BUILD_FROM_SOURCE := true

# No eSIM support for now: the eSIM apps of Google are left out (patches/vendor/adevtool/0004), so the device does
# not announce the feature either (vendor/lech/config/sysconfig/lech-no-esim.xml).
PRODUCT_COPY_FILES += \
    vendor/lech/config/sysconfig/lech-no-esim.xml:$(TARGET_COPY_OUT_PRODUCT)/etc/sysconfig/lech-no-esim.xml \

# Browser and WebView Vanadium of GrapheneOS (E3-01, ADR-0011; external/vanadium from manifests/lech-pixel.xml)
# instead of the AOSP WebView of June 2026 and its test shell Browser2: LechVanadium overrides both, and
# LechVanadiumOverlay makes Vanadium the WebView provider and the default browser (vendor/lech/vanadium).
# Pixel 9 has no 32-bit apps: the arm64 variants.
PRODUCT_PACKAGES += \
    TrichromeWebView \
    TrichromeChrome \
    LechVanadium \
    LechVanadiumOverlay \

# Calculator (ExactCalculator, Apache-2.0, with its own crcalc) and the PDF viewer of GrapheneOS (PdfViewerGOS, MIT,
# prebuilt signed by GrapheneOS): both from manifests/lech-pixel.xml, docs/17-chyba-oproti-androidu.md.
PRODUCT_PACKAGES += \
    ExactCalculator \
    PdfViewerGOS \

# App store F-Droid (owner, 12. 11. 2026): downloaded and verified (publisher's certificate) before every build by
# tools/etapa2/tretie_aplikacie.py into vendor/lech/tretie; absent when no verified copy exists.
-include vendor/lech/tretie/tretie.mk

# Own apps of LECH OS in the LECH OS design (stage 3, vendor/lech/aplikacie): LechKontakty replaces the AOSP
# Contacts, LechGaleria the AOSP Gallery2 (both by overrides in their Android.bp). LechSubory and LechNastavenia
# sit beside DocumentsUI and Settings, which stay as the system file picker and the settings screens; their
# launcher entries are hidden (vendor/lech/overlay/launcher). LechDialerOverlay: Slovak texts the Dialer lacks.
# LechSpravy replaces the AOSP Messaging and is the default SMS app (LechSmsOverlay); LechHodiny the DeskClock,
# LechKalendar the AOSP Calendar, LechFotoaparat the AOSP Camera2.
PRODUCT_PACKAGES += \
    LechKontakty \
    LechGaleria \
    LechSubory \
    LechNastavenia \
    LechSpravy \
    LechHodiny \
    LechKalendar \
    LechFotoaparat \
    LechSmsOverlay \
    LechLauncherOverlay \
    LechDialerOverlay \

# Gate 1 test only: an update that does not start, to check the automatic return to the previous slot
# (tools/etapa2/12-zostav-pixel.sh --chybna sets LECH_TEST_BROKEN_UPDATE; vendor/lech/config/init/init.lech-test-chybna.rc).
ifeq ($(LECH_TEST_BROKEN_UPDATE),true)
PRODUCT_COPY_FILES += \
    vendor/lech/config/init/init.lech-test-chybna.rc:$(TARGET_COPY_OUT_PRODUCT)/etc/init/init.lech-test-chybna.rc \

endif

$(call inherit-product, vendor/google_devices/tokay/tokay.mk)
$(call inherit-product, vendor/lech/config/common.mk)

PRODUCT_NAME := lech_tokay
PRODUCT_DEVICE := tokay
PRODUCT_BRAND := LECH
PRODUCT_MANUFACTURER := LECH
PRODUCT_MODEL := LECH-PHONE
