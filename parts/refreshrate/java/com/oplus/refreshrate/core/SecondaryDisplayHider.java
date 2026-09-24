/*
 * Copyright (C) 2026 Oplus Project
 *
 * Secondary display 隐藏器 — 让 HWC 只看到 1 个 display (与 ColorOS 一致)。
 *
 * 机制 (高通 HWC 原生接口):
 *   IDisplayConfig.setDisplayStatus(DisplayType.BUILTIN2, ExternalStatus.OFFLINE)
 *   → HWC SetSecondaryDisplayStatus → SetDisplayStatus(Offline)
 *   → SetPowerMode(Off) → secondary (DSI-2) 关闭 → SF 不再枚举
 *
 * ColorOS 的 displaypanelfeature 就是通过这个接口隐藏 secondary 的。
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.oplus.refreshrate.core;

import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

/**
 * 通过 vendor.qti.hardware.display.config IDisplayConfig AIDL
 * 隐藏 secondary display (BUILTIN2)。
 *
 * 注意: 需要 system 权限 (platform 签名) 才能绑定 vendor AIDL。
 */
public final class SecondaryDisplayHider {
    private static final String TAG = "ORR/Secondary";

    // AIDL 接口的 service 名
    private static final String DISPLAY_CONFIG_SERVICE =
            "vendor.qti.hardware.display.config.IDisplayConfig/default";

    // DisplayType.BUILTIN2 = 4 (secondary / IRIS 输出链路)
    private static final int DISPLAY_TYPE_BUILTIN2 = 4;
    // ExternalStatus.OFFLINE = 1
    private static final int STATUS_OFFLINE = 1;

    private SecondaryDisplayHider() {
    }

    /**
     * 隐藏 secondary display。
     * 通过反射调用 AIDL (避免直接依赖 vendor AIDL 类, 保持净室/独立)。
     */
    public static void hideSecondary() {
        try {
            // 方式 1: 反射调用生成的 AIDL Stub (如果编译环境有)
            boolean ok = tryReflection();
            if (ok) {
                Log.i(TAG, "secondary display hidden (reflection)");
                return;
            }
            // 方式 2: 直接 binder 调用 (无编译依赖)
            ok = tryBinderCall();
            Log.i(TAG, ok ? "secondary display hidden (binder)"
                          : "secondary display hide failed");
        } catch (Throwable t) {
            Log.e(TAG, "hideSecondary failed", t);
        }
    }

    /** 方式 1: 反射调用 vendor.qti.hardware.display.config.IDisplayConfig */
    private static boolean tryReflection() {
        try {
            Class<?> clazz = Class.forName(
                    "vendor.qti.hardware.display.config.IDisplayConfig");
            // 实际生成的类有 Stub.asInterface(IBinder)
            Class<?> stubClass = Class.forName(
                    "vendor.qti.hardware.display.config.IDisplayConfig$Stub");
            IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class)
                    .invoke(null, DISPLAY_CONFIG_SERVICE);
            if (binder == null) {
                Log.w(TAG, DISPLAY_CONFIG_SERVICE + " not found");
                return false;
            }
            Object svc = stubClass.getMethod("asInterface", IBinder.class)
                    .invoke(null, binder);
            if (svc == null) {
                return false;
            }
            // setDisplayStatus(int displayType, int status)
            clazz.getMethod("setDisplayStatus", int.class, int.class)
                    .invoke(svc, DISPLAY_TYPE_BUILTIN2, STATUS_OFFLINE);
            return true;
        } catch (Exception e) {
            Log.d(TAG, "reflection failed: " + e.getMessage());
            return false;
        }
    }

    /** 方式 2: 直接 binder transaction (transaction code 需从 AIDL 确认) */
    private static boolean tryBinderCall() {
        try {
            IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class)
                    .invoke(null, DISPLAY_CONFIG_SERVICE);
            if (binder == null) {
                return false;
            }
            // setDisplayStatus 在 IDisplayConfig AIDL 里的 transaction code
            // (需从接口定义确认, 或用反射)
            android.os.Parcel data = android.os.Parcel.obtain();
            android.os.Parcel reply = android.os.Parcel.obtain();
            try {
                data.writeInterfaceToken(
                        "vendor.qti.hardware.display.config.IDisplayConfig");
                data.writeInt(DISPLAY_TYPE_BUILTIN2);
                data.writeInt(STATUS_OFFLINE);
                // transaction code 从 1 开始; setDisplayStatus 的位置需确认
                boolean ok = binder.transact(1, data, reply, 0);
                reply.readException();
                return ok;
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Exception e) {
            Log.e(TAG, "binder call failed: " + e.getMessage());
            return false;
        }
    }
}
