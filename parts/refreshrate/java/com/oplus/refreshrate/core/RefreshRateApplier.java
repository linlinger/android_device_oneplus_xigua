/*
 * Copyright (C) 2026 Oplus Project
 *
 * 刷新率应用器 — 把决策的刷新率应用到系统。
 * 支持多种后端 (从用户态到内核态):
 *   1. SurfaceFlinger setDesiredActiveMode (AOSP 16 原生接口)
 *   2. DisplayManager setRefreshRateSwitchingType (系统服务)
 *   3. sysfs /sys/class/drm/card0-DSI-1/... (内核直写, 需 root)
 *   4. oplus_display sysfs (如果存在)
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.oplus.refreshrate.core;

import android.content.Context;
import android.os.IBinder;
import android.os.ServiceManager;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;

/**
 * 刷新率应用器 — 按配置选择后端。
 */
public class RefreshRateApplier {
    private static final String TAG = "ORR/Applier";

    // 后端类型
    public static final int BACKEND_SF = 0;      // SurfaceFlinger (推荐, 无 root)
    public static final int BACKEND_SYSFS = 1;   // sysfs 直写 (需 root)
    public static final int BACKEND_OPLUS = 2;   // oplus_display sysfs

    private static Context sContext;
    private static int sBackend = BACKEND_SF;
    private static float sCurrentRate = -1f;

    /** 初始化 (由 Service 调用) */
    public static void init(Context context, int backend) {
        sContext = context.getApplicationContext();
        sBackend = backend;
        Log.i(TAG, "backend=" + backend);
    }

    /** 应用刷新率 */
    public static void apply(float rate) {
        if (sContext == null || rate <= 0) {
            return;
        }
        // 去重: 相同刷新率不重复应用
        if (Math.abs(rate - sCurrentRate) < 1.0f) {
            return;
        }
        sCurrentRate = rate;
        Log.i(TAG, "apply rate=" + rate);

        switch (sBackend) {
            case BACKEND_SYSFS:
                applySysfs(rate);
                break;
            case BACKEND_OPLUS:
                applyOplusSysfs(rate);
                break;
            case BACKEND_SF:
            default:
                applySurfaceFlinger(rate);
                break;
        }
    }

    /** 后端 1: DisplayManager 锁定 + SF 模式切换 (AOSP 16, 无需 root) */
    private static void applySurfaceFlinger(float rate) {
        try {
            // 1. 通过 DisplayManager 设置用户偏好模式 (真正的锁定)
            //    setRefreshRateSwitchingType(NONE) = 禁止自动切换
            android.hardware.display.DisplayManager dm =
                    (android.hardware.display.DisplayManager)
                    sContext.getSystemService(Context.DISPLAY_SERVICE);
            if (dm != null) {
                try {
                    dm.setRefreshRateSwitchingType(
                            android.hardware.display.DisplayManager
                                    .SWITCHING_TYPE_NONE);
                    Log.i(TAG, "switching type locked to NONE");
                } catch (Exception e) {
                    Log.e(TAG, "setRefreshRateSwitchingType failed: "
                            + e.getMessage());
                }
            }

            // 2. 同步 peak_refresh_rate (系统"流畅画面"会压刷新率, 必须同步)
            try {
                android.provider.Settings.System.putFloat(
                        sContext.getContentResolver(),
                        "peak_refresh_rate", rate);
                Log.i(TAG, "peak_refresh_rate set to " + rate);
            } catch (Exception e) {
                Log.e(TAG, "set peak_refresh_rate failed: " + e.getMessage());
            }

            // 3. 设置目标模式 (通过 DisplayManagerGlobal.setUserPreferredDisplayMode 反射)
            int modeId = getModeIdForRate(rate);
            boolean ok = setUserPreferredModeId(modeId);
            Log.i(TAG, "set userPreferredDisplayMode=" + modeId + " → " + ok);
        } catch (Exception e) {
            Log.e(TAG, "SF apply failed", e);
        }
    }

    /** 反射调 DisplayManagerGlobal.setUserPreferredDisplayMode (隐藏 API) */
    private static boolean setUserPreferredModeId(int modeId) {
        try {
            Class<?> dmGlobalCls = Class.forName(
                    "android.hardware.display.DisplayManagerGlobal");
            Object dmGlobal = dmGlobalCls.getMethod("getInstance")
                    .invoke(null);

            // 从 DisplayManager 拿默认 display 的 supportedModes (服务 Context 无 display)
            android.hardware.display.DisplayManager dm =
                    (android.hardware.display.DisplayManager)
                    sContext.getSystemService(Context.DISPLAY_SERVICE);
            android.view.Display display = dm != null
                    ? dm.getDisplay(android.view.Display.DEFAULT_DISPLAY) : null;
            android.view.Display.Mode target = null;
            if (display != null) {
                for (android.view.Display.Mode m : display.getSupportedModes()) {
                    if (m.getModeId() == modeId) {
                        target = m;
                        break;
                    }
                }
            }
            if (target == null) {
                Log.w(TAG, "mode " + modeId + " not found in supportedModes");
                return false;
            }

            dmGlobalCls.getMethod("setUserPreferredDisplayMode",
                            int.class, android.view.Display.Mode.class)
                    .invoke(dmGlobal, android.view.Display.DEFAULT_DISPLAY,
                            target);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "setUserPreferredDisplayMode failed: " + e.getMessage());
            return false;
        }
    }

    /** 恢复系统自动切换 (智能模式) */
    public static void resetToAuto() {
        if (sContext == null) {
            return;
        }
        try {
            // 1. 恢复自动切换
            android.hardware.display.DisplayManager dm =
                    (android.hardware.display.DisplayManager)
                    sContext.getSystemService(Context.DISPLAY_SERVICE);
            if (dm != null) {
                dm.setRefreshRateSwitchingType(
                        android.hardware.display.DisplayManager
                                .SWITCHING_TYPE_ACROSS_AND_WITHIN_GROUPS);
                Log.i(TAG, "switching type restored to ACROSS_AND_WITHIN_GROUPS");
            }

            // 2. 清除用户偏好模式
            try {
                Class<?> dmGlobalCls = Class.forName(
                        "android.hardware.display.DisplayManagerGlobal");
                Object dmGlobal = dmGlobalCls.getMethod("getInstance")
                        .invoke(null);
                dmGlobalCls.getMethod("resetUserPreferredDisplayMode",
                                int.class)
                        .invoke(dmGlobal,
                                android.view.Display.DEFAULT_DISPLAY);
                Log.i(TAG, "userPreferredDisplayMode reset");
            } catch (Exception e) {
                Log.e(TAG, "resetUserPreferredDisplayMode failed: "
                        + e.getMessage());
            }

            // 3. 重置 peak_refresh_rate (恢复系统默认, 防止残留锁定值导致风暴)
            try {
                android.provider.Settings.System.putFloat(
                        sContext.getContentResolver(),
                        "peak_refresh_rate", 120.0f);
                Log.i(TAG, "peak_refresh_rate reset to 120.0 (default)");
            } catch (Exception e) {
                Log.e(TAG, "reset peak_refresh_rate failed: " + e.getMessage());
            }
        } catch (Exception e) {
            Log.e(TAG, "resetToAuto failed", e);
        }
    }

    /** 后端 2: sysfs 直写 (内核态, 需 root) */
    private static void applySysfs(float rate) {
        // xigua: /sys/class/drm/card0-DSI-1/modes 显示 120/90/60
        // 实际写面板命令走 oplus_display, 或直接设置 mode
        String path = "/sys/kernel/oplus_display/dsi_cmd";
        int cmd = rate >= 120 ? 0x0 : (rate >= 90 ? 1 : 2);
        writeFile(path, String.valueOf(cmd));
    }

    /** 后端 3: oplus_display sysfs */
    private static void applyOplusSysfs(float rate) {
        // 参考: /sys/kernel/oplus_display/adfr_config (0x109f 等)
        String path = "/sys/kernel/oplus_display/adfr_config";
        if (rate >= 120) {
            writeFile(path, "0x0");  // 120Hz
        } else if (rate >= 90) {
            writeFile(path, "0x1");  // 90Hz
        } else {
            writeFile(path, "0x2");  // 60Hz
        }
    }

    /** 刷新率 → modeId (xigua primary display: 120=1, 60=2, 90=3, 实测 dumpsys) */
    private static int getModeIdForRate(float rate) {
        if (rate >= 120) {
            return 1;  // 120Hz mode id=1
        } else if (rate >= 90) {
            return 3;  // 90Hz mode id=3
        } else {
            return 2;  // 60Hz mode id=2
        }
    }

    private static void writeFile(String path, String content) {
        try {
            File f = new File(path);
            if (!f.exists()) {
                Log.w(TAG, path + " not exists");
                return;
            }
            try (FileWriter w = new FileWriter(f)) {
                w.write(content);
                Log.d(TAG, "wrote " + content + " → " + path);
            }
        } catch (Exception e) {
            Log.e(TAG, "write " + path + " failed", e);
        }
    }
}
