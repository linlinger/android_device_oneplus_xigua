#
# Copyright (C) 2021-2025 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

# AAPT
PRODUCT_AAPT_CONFIG := normal
PRODUCT_AAPT_PREF_CONFIG := xxhdpi

# Audio
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/audio/audio_policy_volumes.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_policy_volumes.xml \
    $(LOCAL_PATH)/configs/audio/default_volume_tables.xml:$(TARGET_COPY_OUT_VENDOR)/etc/default_volume_tables.xml

# Boot animation
TARGET_SCREEN_HEIGHT := 2772
TARGET_SCREEN_WIDTH := 1240

# Display
PRODUCT_PACKAGES += \
    OplusLtpo \
    OplusIrisBypass \
    OplusRefreshRate

PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/display/displayconfig.xml:$(TARGET_COPY_OUT_VENDOR)/etc/displayconfig/display_id_4630947194812807555.xml \
    $(LOCAL_PATH)/configs/display/vrr/refresh_rate_config.xml:$(TARGET_COPY_OUT_PRODUCT)/etc/refresh_rate_config.xml \
    $(LOCAL_PATH)/configs/display/vrr/oplus_vrr_config.json:$(TARGET_COPY_OUT_PRODUCT)/etc/oplus_vrr_config.json

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

# Power
$(call soong_config_set,qtipower,mode_ext_lib,power-ext-oplus)

# Regional properties
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/recovery/root/vendor/odm/etc/22851/build.default.prop:$(TARGET_COPY_OUT_ODM)/etc/22851/build.default.prop

# Sensors
PRODUCT_PACKAGES += \
    sensors.oplus

# Soong namespaces
PRODUCT_SOONG_NAMESPACES += \
    $(LOCAL_PATH)

# Touch
$(call soong_config_set_bool,OPLUS_LINEAGE_TOUCH_HAL,USE_OPLUSTOUCH,true)

# Vibrator
PRODUCT_PACKAGES += \
    vendor.qti.hardware.vibrator.service.oplus

$(call soong_config_set_bool,OPLUS_LINEAGE_VIBRATOR_HAL,USE_EFFECT_STREAM,true)

# Inherit from the common OEM chipset makefile.
$(call inherit-product, device/oneplus/sm8550-common/common.mk)

# Inherit from the proprietary files makefile.
$(call inherit-product, vendor/oneplus/xigua/xigua-vendor.mk)
