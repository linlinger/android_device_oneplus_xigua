/*
 * Copyright (C) 2026 Oplus Project
 *
 * 原厂配置解析器 — 兼容 ColorOS 的 refresh_rate_config.xml 格式。
 *
 * 格式 (来自 ColorOS 原厂 /my_product/etc/refresh_rate_config.xml):
 *   <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
 *   <refresh_rate_config version="20240529" ratemagic="8550T60_90_120">
 *     <item package="com.tencent.qqmusic" rateId="2-1-2-1" />
 *     <item package="..." activity="...Activity" rateId="2-2-2-3"
 *           appreqfirst="true" applowRefreshRate="30" lowRateWinType="1"
 *           disableViewOverride="true" />
 *   </refresh_rate_config>
 *
 *   rateId = "auto-90-60-120" 四档:
 *     0=unspecified, 1=90Hz, 2=60Hz, 3=120Hz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.oplus.refreshrate.core;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

/**
 * 应用级刷新率配置解析器。
 * 读取原厂 refresh_rate_config.xml, 提供 应用/Activity → 刷新率 映射。
 */
public final class RefreshRateConfigParser {
    private static final String TAG = "ORR/Config";

    // rateId 索引: 0=auto, 1=90, 2=60, 3=120
    private static final int IDX_AUTO = 0;
    private static final int IDX_90 = 1;
    private static final int IDX_60 = 2;
    private static final int IDX_120 = 3;

    /** 单个应用的配置 */
    public static final class AppConfig {
        public String pkgName;
        public String activity;           // 可选, null = 匹配整个应用
        public int[] rateIds = {0, 0, 0, 0};  // auto/90/60/120
        public boolean appRequestFirst;   // appreqfirst
        public int appLowRefreshRate;     // applowRefreshRate (低刷新率)
        public int lowRateWinType;        // lowRateWinType
        public boolean disableViewOverride;
        public boolean lowfreq;           // 视频低刷新率

        /** 获取指定模式的刷新率 (0=unspecified) */
        public int getRateForMode(int modeIndex) {
            if (modeIndex < 0 || modeIndex >= rateIds.length) {
                return 0;
            }
            switch (rateIds[modeIndex]) {
                case 1: return 90;
                case 2: return 60;
                case 3: return 120;
                default: return 0;
            }
        }

        @Override
        public String toString() {
            return "AppConfig{pkg=" + pkgName + ", activity=" + activity
                    + ", rateId=" + rateIds[0] + "-" + rateIds[1] + "-"
                    + rateIds[2] + "-" + rateIds[3] + "}";
        }
    }

    // 配置表: pkg → config (无 activity 的通用配置)
    private final Map<String, AppConfig> mConfigs = new HashMap<>();
    private String mVersion = "";
    private String mRateMagic = "";

    /** 加载配置文件 */
    public boolean load(File file) {
        if (file == null || !file.exists()) {
            Log.w(TAG, "config not found: " + file);
            return false;
        }
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            try (FileInputStream in = new FileInputStream(file)) {
                parser.setInput(in, "UTF-8");
                parse(parser);
            }
            Log.i(TAG, "loaded " + mConfigs.size() + " configs from " + file);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "load failed", e);
            return false;
        }
    }

    private void parse(XmlPullParser parser) throws Exception {
        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                String tag = parser.getName();
                if ("refresh_rate_config".equals(tag)) {
                    mVersion = parser.getAttributeValue(null, "version");
                    mRateMagic = parser.getAttributeValue(null, "ratemagic");
                } else if ("item".equals(tag)) {
                    parseItem(parser);
                }
            }
            eventType = parser.next();
        }
    }

    private void parseItem(XmlPullParser parser) {
        AppConfig cfg = new AppConfig();
        cfg.pkgName = parser.getAttributeValue(null, "package");
        cfg.activity = parser.getAttributeValue(null, "activity");
        String rateId = parser.getAttributeValue(null, "rateId");
        if (rateId != null) {
            String[] parts = rateId.split("-");
            for (int i = 0; i < parts.length && i < 4; i++) {
                try {
                    cfg.rateIds[i] = Integer.parseInt(parts[i]);
                } catch (NumberFormatException e) {
                    cfg.rateIds[i] = 0;
                }
            }
        }
        cfg.appRequestFirst = "true".equals(
                parser.getAttributeValue(null, "appreqfirst"));
        cfg.disableViewOverride = "true".equals(
                parser.getAttributeValue(null, "disableViewOverride"));
        cfg.lowfreq = "true".equals(
                parser.getAttributeValue(null, "lowfreq"));
        String low = parser.getAttributeValue(null, "applowRefreshRate");
        if (low != null) {
            try {
                cfg.appLowRefreshRate = Integer.parseInt(low);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        String lowType = parser.getAttributeValue(null, "lowRateWinType");
        if (lowType != null) {
            try {
                cfg.lowRateWinType = Integer.parseInt(lowType);
            } catch (NumberFormatException e) {
                // ignore
            }
        }

        if (cfg.pkgName != null) {
            // 无 activity 的通用配置 (activity 特定的走独立匹配)
            if (cfg.activity == null || mConfigs.containsKey(cfg.pkgName)) {
                mConfigs.put(cfg.pkgName, cfg);
            }
        }
    }

    /** 查询应用配置 (按 pkg 或 pkg+activity) */
    public AppConfig getConfig(String pkg, String activity) {
        if (pkg == null) {
            return null;
        }
        // 先找 pkg+activity 精确匹配
        // (简化: 当前按 pkg 匹配, activity 特定匹配存 key "pkg/activity")
        if (activity != null) {
            AppConfig exact = mConfigs.get(pkg + "/" + activity);
            if (exact != null) {
                return exact;
            }
        }
        return mConfigs.get(pkg);
    }

    public String getVersion() {
        return mVersion;
    }

    public String getRateMagic() {
        return mRateMagic;
    }

    public int size() {
        return mConfigs.size();
    }
}
