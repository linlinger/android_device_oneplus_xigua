#
# Copyright (C) 2021-2025 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

# Partitions
BOARD_SUPER_PARTITION_SIZE := 16106127360

# Include the common OEM chipset BoardConfig.
include device/oneplus/sm8550-common/BoardConfigCommon.mk

DEVICE_PATH := device/oneplus/xigua

# Assert
TARGET_OTA_ASSERT_DEVICE := OP5943L1

# Display
TARGET_SCREEN_DENSITY := 420

# Kernel
TARGET_KERNEL_ADDITIONAL_FLAGS += CONFIG_XIGUA_DTB=y

# Sepolicy (OplusRefreshRate vendor app 规则)
# /sys/kernel/iris (IRIS7 独显) 的 genfs 标签采用"上游优先"策略:
#   1. crdroid 17.0 hardware/oplus/sepolicy/qti/vendor/genfs_contexts 已把
#      iris 标为 vendor_sysfs_graphics (类型定义在 sepolicy_vndr/sm8550) →
#      直接依赖上游, 不本地重复标注 (genfscon 同路径重复 = duplicate entry)
#   2. 上游未提供时 (其他树/上游回滚) 才追加 vendor-iris-fallback 目录,
#      补上同款 genfscon 条目
#   访问授权 (sepolicy/vendor/platform_app_refreshrate.te → vendor_sysfs_graphics)
#   两种情况通用, 不需要条件化。
IRIS_UPSTREAM_GENFS := $(strip $(shell grep -hs "genfscon sysfs /kernel/iris" hardware/oplus/sepolicy/qti/vendor/genfs_contexts 2>/dev/null))
ifeq ($(IRIS_UPSTREAM_GENFS),)
BOARD_VENDOR_SEPOLICY_DIRS += $(DEVICE_PATH)/sepolicy/vendor-iris-fallback
endif
BOARD_VENDOR_SEPOLICY_DIRS += $(DEVICE_PATH)/sepolicy/vendor

# Properties
TARGET_ODM_PROP += $(DEVICE_PATH)/properties/odm.prop
TARGET_PRODUCT_PROP += $(DEVICE_PATH)/properties/product.prop
TARGET_SYSTEM_EXT_PROP += $(DEVICE_PATH)/properties/system_ext.prop
TARGET_VENDOR_PROP += $(DEVICE_PATH)/properties/vendor.prop

# Recovery
TARGET_RECOVERY_UI_MARGIN_HEIGHT := 103

# Include the proprietary files BoardConfig.
include vendor/oneplus/xigua/BoardConfigVendor.mk
