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

# Inherit some common Lineage stuff.
$(call inherit-product, vendor/lineage/config/common_full_phone.mk)

# AviiumUI
# 选择是否将当前时、分、秒添加到版本号中
AVIUM_VERSION_APPEND_TIME_OF_DAY ?= false
# 构建者名称
AVIUM_MAINTAINER ?= linlinger

# Soc型号
AVIUM_SETTINGS_SOC_MODEL_NAME ?= Snapdragon 8 Gen 2
# 设备代号
AVIUM_SETTINGS_DEVICE_CODENAME ?= xigua

# 选择是否开启gms
WITH_GMS ?= true
# LatinIMEGooglePrebuilt
# 仅在构建非gms版本时起作用
# 在gms版本上会使用gms中的Gboard
TARGET_INCLUDE_GOOGLEIME ?= false
TARGET_GOOGLEIME_OVERRIDE_IME ?= false

# 将此配置指定为true来开启欺骗 Prop，
# 用于隐藏 bootloader 解锁状态
AVIUM_FORCE_SET_FAKE_PROP ?= true

# 通常在 Android 16 QPR2 上，模糊已经默认启用
# 将 TARGET_FORCE_ENABLE_BLUR 指定为true来强制开启模糊
TARGET_FORCE_ENABLE_BLUR ?= false


PRODUCT_NAME := lineage_xigua
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
