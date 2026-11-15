#
# Copyright (C) 2021-2025 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

# AAPT
PRODUCT_AAPT_CONFIG := normal
PRODUCT_AAPT_PREF_CONFIG := xxhdpi

# 调试便利: adb 免授权 (vendor/lineage/config/common.mk 消费此开关,
# device.mk 先于 vendor/lineage/config/common_full_phone.mk 被解析, 时序满足)
# 注: 不能直接设 ro.adb.secure=0, 会与 common.mk 的值冲突 (duplicate sysprop)
WITH_ADB_INSECURE := true

PRODUCT_SYSTEM_EXT_PROPERTIES += \
    persist.sys.root_access=3 \
    service.adb.root=1

# 调试便利: 构建期注入 adb 公钥 (AOSP 官方机制, source.android.com
# "Set ADB_VENDOR_KEYS to connect over USB" 一节)。
# adb_keys 模块 (build/soong/etc/adb_keys.go) 要求 ro.debuggable=1 且
# 产物装到 /product/etc/security/adb_keys; adbd 启动时自动信任该公钥,
# 无论 ro.adb.secure 取值如何都不会弹授权框。
# 仅对本机构建生效: 公钥是公开数据, 可安全入库。
PRODUCT_ADB_KEYS := $(LOCAL_PATH)/adb_keys.pub

# Audio
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/audio/audio_policy_volumes.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_policy_volumes.xml \
    $(LOCAL_PATH)/configs/audio/default_volume_tables.xml:$(TARGET_COPY_OUT_VENDOR)/etc/default_volume_tables.xml

# Boot animation
TARGET_SCREEN_HEIGHT := 2772
TARGET_SCREEN_WIDTH := 1240

# Display
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/display/displayconfig.xml:$(TARGET_COPY_OUT_VENDOR)/etc/displayconfig/display_id_4630947194812807555.xml

$(call soong_config_set,qtidisplay,pxlw_vendor_namespace,vendor/oneplus/xigua)
$(call soong_config_set_bool,qtidisplay,pxlw_hw_iris7,true)

PRODUCT_SYSTEM_PROPERTIES += \
    sys.brightness.disable_gamma_conversion=true

# Fingerprint
TARGET_HAS_UDFPS := true

# IR
$(call inherit-product, vendor/oneplus/ir/config.mk)

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.consumerir.xml:$(TARGET_COPY_OUT_ODM)/etc/permissions/android.hardware.consumerir.xml

PRODUCT_PACKAGES += \
    android.hardware.ir-service.oplus

# Fingerprint
$(call soong_config_set,surfaceflinger,udfps_lib,//hardware/oplus:libudfps_extension.oplus)
$(call soong_config_set_bool,qtidisplay,oplus_udfps,true)

# LiveDisplay
$(call soong_config_set_bool,OPLUS_LINEAGE_LIVEDISPLAY_HAL,ENABLE_AF,true)
$(call soong_config_set_bool,OPLUS_LINEAGE_LIVEDISPLAY_HAL,ENABLE_SE,false)

# Overlays
DEVICE_PACKAGE_OVERLAYS += \
    $(LOCAL_PATH)/overlay-lineage

PRODUCT_PACKAGES += \
    OPlusFrameworksResTarget \
    OPlusSettingsProviderResTarget \
    OPlusSettingsResTarget \
    OPlusSystemUIResTarget \
    OPlusWifiResTarget

# OplusRefreshRate (vendor 版刷新率调度, linlinger/PJA110-RefreshRateVendor)
# 装 /vendor/priv-app/OplusRefreshRateVendor; 平台签名 privapp,
# A17 seapp_contexts 首条匹配 (seinfo=platform + targetSdk<37) → platform_app_36 域,
# 对应 sepolicy/vendor/platform_app_refreshrate.te
PRODUCT_PACKAGES += \
    OplusRefreshRateVendor

# 注: 不提供 privapp-permissions 白名单 —— manifest 里的权限要么是纯 signature
# (平台签名自动授予, 白名单反而触发 ro.control_privapp_permissions=enforce 的
# unknown-permission 违规 → SystemServer crash loop / bootloop), 要么是
# appop/normal 级 (不需要白名单)

# Lineage 组件的 privapp 白名单 (system_ext 分区, 必须与 priv-app 同分区):
# Settings → lineageos.permission.MANAGE_LIVEDISPLAY
# SystemUI → lineageos.permission.HARDWARE_ABSTRACTION_ACCESS
# 这棵树 ro.control_privapp_permissions=enforce 无条件开启
# (vendor/lineage/config/common.mk), 且 frameworks/base 的
# privapp-permissions-platform.xml (/system) 没有条目 → 缺失即 bootloop
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/permissions/privapp-permissions-com.android.settings.xml:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/permissions/privapp-permissions-com.android.settings.xml \
    $(LOCAL_PATH)/permissions/privapp-permissions-com.android.systemui.xml:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/permissions/privapp-permissions-com.android.systemui.xml

# Power
$(call soong_config_set,qtipower,mode_ext_lib,power-ext-oplus)

# Regional properties
REGIONAL_PROP_FILES := $(wildcard $(LOCAL_PATH)/properties/*/*.prop)

PRODUCT_COPY_FILES += $(foreach f,$(REGIONAL_PROP_FILES), \
    $(f):$(TARGET_COPY_OUT_ODM)/etc/$(patsubst $(LOCAL_PATH)/properties/%,%,$(f)) \
    $(f):$(TARGET_COPY_OUT_RECOVERY)/root/vendor/odm/etc/$(patsubst $(LOCAL_PATH)/properties/%,%,$(f)))

# Sensors
PRODUCT_PACKAGES += \
    sensors.oplus

# Soong namespaces
PRODUCT_SOONG_NAMESPACES += \
    $(LOCAL_PATH)

# A17 (Lineage 24.0 构建) 中 PRODUCT_SOONG_NAMESPACES 被列为 readonly-late
# product 变量, BoardConfigQcom.mk 在 BoardConfig 阶段的追加全部失效
# (wlan/sm8550/bootctrl/thermal/data-ipa/commonsys 都不再导出), 导致:
#  - frameworks libwifi_hal_vendor_impl_defaults (qcwcn) 找不到 libwifi-hal-qcom
#  - sm8550 display 相关跨 namespace 引用断链
# 这里在 product 侧复刻 A16 (AviumUI) 时代 BoardConfigQcom 生效的 namespace 集合
PRODUCT_SOONG_NAMESPACES += \
    hardware/qcom-caf/sm8550 \
    hardware/qcom-caf/bootctrl \
    hardware/qcom-caf/sm8550/data-ipa-cfg-mgr \
    hardware/qcom-caf/thermal \
    hardware/qcom-caf/wlan \
    hardware/qcom-caf/wlan/qcwcn \
    vendor/qcom/opensource/commonsys/display \
    vendor/qcom/opensource/commonsys-intf/display \
    vendor/qcom/opensource/dataservices \
    vendor/qcom/opensource/audio-hal/st-hal-ar-legacy

# Touch
$(call soong_config_set_bool,OPLUS_LINEAGE_TOUCH_HAL,USE_OPLUSTOUCH,true)
$(call soong_config_set,OPLUS_LINEAGE_TOUCH_HAL,INCLUDE_DIR,$(LOCAL_PATH)/touch/include)

# Vibrator
PRODUCT_PACKAGES += \
    vendor.qti.hardware.vibrator.service.oplus

$(call soong_config_set_bool,OPLUS_LINEAGE_VIBRATOR_HAL,USE_EFFECT_STREAM,true)

# Inherit from the common OEM chipset makefile.
$(call inherit-product, device/oneplus/sm8550-common/common.mk)

# Inherit from the proprietary files makefile.
$(call inherit-product, vendor/oneplus/xigua/xigua-vendor.mk)
