#
# Copyright (C) 2021-2025 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

# Inherit from those products. Most specific first.
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit_only.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base_telephony.mk)

# Inherit from xigua device
$(call inherit-product, device/oneplus/xigua/device.mk)

# GMS (PixelOS 自家 GMS 树, vendor/pixel/gms)
# 注意: vendor/lineage 的 partner_gms.mk 走的是 vendor/partner_gms/, 本树不存在,
# 必须直接显式继承 vendor/pixel/gms/products/gms.mk
WITH_GMS := true
$(call inherit-product, vendor/pixel/gms/products/gms.mk)

# Inherit some common Lineage stuff.
$(call inherit-product, vendor/lineage/config/common_full_phone.mk)

PRODUCT_NAME := custom_xigua
PRODUCT_DEVICE := xigua
PRODUCT_MANUFACTURER := OnePlus
PRODUCT_BRAND := OnePlus
PRODUCT_MODEL := PJA110

PRODUCT_GMS_CLIENTID_BASE := android-oneplus

PRODUCT_BUILD_PROP_OVERRIDES += \
    BuildDesc="qssi-user 16 BP2A.250605.015 1778381433017 release-keys" \
    BuildFingerprint=OnePlus/PJA110/OP5943L1:16/BP2A.250605.015/T.4668907-22a0229-225984e:user/release-keys \
    DeviceName=OP5943L1 \
    DeviceProduct=PJA110 \
    SystemDevice=OP5943L1 \
    SystemName=PJA110
