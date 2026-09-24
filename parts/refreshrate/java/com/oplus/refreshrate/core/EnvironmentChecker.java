/*
 * Copyright (C) 2026 Oplus Project
 *
 * 环境检查 — 运行时检测设备是否支持刷新率调度。
 *
 * 兼容性策略:
 *   1. 检测 /sys/kernel/oplus_display/ (OPPO/OnePlus 内核节点)
 *   2. 检测 /sys/class/drm/card0-DSI-1/modes (多档刷新率)
 *   3. 检测 SurfaceFlinger 可用性
 *   4. 检测 IDisplayConfig AIDL 服务 (secondary 隐藏)
 *
 * 支持多设备: 有 oplus_display 或有多个 DSI 模式即可工作。
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.oplus.refreshrate.core;

import android.os.IBinder;
import android.util.Log;

import java.io.File;

/**
 * 运行时环境检查器。
 */
public final class EnvironmentChecker {
    private static final String TAG = "ORR/Env";

    // 检测路径
    private static final String OPLUS_DISPLAY_SYSFS = "/sys/kernel/oplus_display";
    private static final String DRM_DSI1_MODES = "/sys/class/drm/card0-DSI-1/modes";
    private static final String IRIS_SYSFS = "/sys/kernel/iris";
    private static final String DISPLAY_CONFIG_SERVICE =
            "vendor.qti.hardware.display.config.IDisplayConfig/default";

    // 检测结果
    public static final class Capabilities {
        public boolean hasOplusDisplay = false;   // OPPO/OnePlus 内核节点
        public boolean hasDsiModes = false;       // 多档刷新率
        public boolean hasIris = false;           // IRIS 芯片
        public boolean hasDisplayConfig = false;  // 高通 IDisplayConfig (secondary 隐藏)
        public boolean hasSurfaceFlinger = false; // SF 服务
        public boolean supported = false;         // 总体是否支持

        @Override
        public String toString() {
            return "Capabilities{oplusDisplay=" + hasOplusDisplay
                    + ", dsiModes=" + hasDsiModes
                    + ", iris=" + hasIris
                    + ", displayConfig=" + hasDisplayConfig
                    + ", surfaceFlinger=" + hasSurfaceFlinger
                    + ", supported=" + supported + "}";
        }
    }

    private EnvironmentChecker() {
    }

    /** 检测设备能力 */
    public static Capabilities check() {
        Capabilities caps = new Capabilities();

        caps.hasOplusDisplay = new File(OPLUS_DISPLAY_SYSFS).exists();
        caps.hasDsiModes = new File(DRM_DSI1_MODES).exists();
        caps.hasIris = new File(IRIS_SYSFS).exists();

        caps.hasDisplayConfig = checkService(DISPLAY_CONFIG_SERVICE);
        caps.hasSurfaceFlinger = checkService("SurfaceFlinger");

        // 支持条件: 有 oplus_display 或有多个 DSI 模式
        caps.supported = caps.hasOplusDisplay || caps.hasDsiModes;

        Log.i(TAG, "environment: " + caps);
        return caps;
    }

    /** 检查 binder 服务是否存在 */
    private static boolean checkService(String name) {
        try {
            IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class)
                    .invoke(null, name);
            return binder != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** 读取支持的刷新率列表 (从 DRM modes) */
    public static int[] getSupportedRates() {
        try {
            String modes = readFile(DRM_DSI1_MODES);
            if (modes == null || modes.isEmpty()) {
                // fallback: oplus_display 常见档位
                return new int[]{60, 90, 120};
            }
            // 解析 "1240x2772x120cmd" 格式, 提取刷新率
            java.util.TreeSet<Integer> rates = new java.util.TreeSet<>();
            for (String line : modes.split("\n")) {
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("x(\\d+)cmd").matcher(line);
                if (m.find()) {
                    rates.add(Integer.parseInt(m.group(1)));
                }
            }
            if (rates.isEmpty()) {
                return new int[]{60, 90, 120};
            }
            int[] result = new int[rates.size()];
            int i = 0;
            for (int r : rates) {
                result[i++] = r;
            }
            return result;
        } catch (Exception e) {
            return new int[]{60, 90, 120};
        }
    }

    private static String readFile(String path) {
        try {
            java.io.File f = new File(path);
            if (!f.exists()) {
                return null;
            }
            byte[] data = new byte[(int) Math.min(f.length(), 4096)];
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                int n = in.read(data);
                return n > 0 ? new String(data, 0, n) : "";
            }
        } catch (Exception e) {
            return null;
        }
    }
}
