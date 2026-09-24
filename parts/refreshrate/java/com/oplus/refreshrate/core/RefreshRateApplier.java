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

    /** 后端 1: SurfaceFlinger setDesiredActiveMode (AOSP 16) */
    private static void applySurfaceFlinger(float rate) {
        try {
            IBinder sf = ServiceManager.getService("SurfaceFlinger");
            if (sf == null) {
                Log.w(TAG, "SurfaceFlinger service not found");
                return;
            }
            // AOSP 16: ISurfaceComposer.setDesiredActiveMode(int modeId)
            // 需要先查 modeId (通过 dumpsys 或 DisplayManager)
            // 这里用反射调 Transaction.setDesiredActiveMode 或 SF 接口
            // TODO: 实现 modeId 查询 (120/90/60 → modeId)
            int modeId = getModeIdForRate(rate);
            Log.d(TAG, "SF setDesiredActiveMode modeId=" + modeId);
            // android.os.Parcel 方式调用 (需要 Binder)
            // 更可靠: 通过 DisplayManager.setRefreshRateSwitchingType
        } catch (Exception e) {
            Log.e(TAG, "SF apply failed", e);
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

    /** 刷新率 → modeId (xigua 面板: 120=0, 60=1, 90=2 来自 dumpsys) */
    private static int getModeIdForRate(float rate) {
        if (rate >= 120) {
            return 0;
        } else if (rate >= 90) {
            return 2;
        } else {
            return 1;
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
