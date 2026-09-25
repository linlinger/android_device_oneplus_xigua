/*
 * Copyright (C) 2026 Oplus Project
 *
 * 应用级刷新率配置 — 解析 OPPO 原厂 refresh_rate_config.xml。
 *
 * rateId 格式: "auto-90-60-120" 四档 (0=不指定/auto, 1=90Hz, 2=60Hz, 3=120Hz)
 * 例: "2-2-2-2" = 所有模式 60Hz; "3-2-2-3" = auto:120, 90:60, 60:60, 120:120
 * 档位顺序: [auto, 90Hz, 60Hz, 120Hz]
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.oplus.refreshrate.core;

import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 应用 → 刷新率映射 (解析 OPPO 原厂配置)。
 */
public class AppConfig {
    private static final String TAG = "ORR/AppConfig";

    // rateId 索引
    private static final int IDX_AUTO = 0;
    private static final int IDX_90 = 1;
    private static final int IDX_60 = 2;
    private static final int IDX_120 = 3;

    // 解析结果: package -> rateId 数组
    private final Map<String, int[]> mAppRates = new HashMap<>();
    private boolean mLoaded = false;

    /** 加载配置文件 */
    public boolean load(File file) {
        mAppRates.clear();
        if (file == null || !file.exists()) {
            Log.w(TAG, "config not found: " + file);
            return false;
        }
        try (InputStream is = new FileInputStream(file)) {
            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            parser.setInput(is, "utf-8");

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG
                        && "item".equals(parser.getName())) {
                    String pkg = parser.getAttributeValue(null, "package");
                    String rateId = parser.getAttributeValue(null, "rateId");
                    if (pkg != null && rateId != null) {
                        int[] rates = parseRateId(rateId);
                        if (rates != null) {
                            mAppRates.put(pkg, rates);
                        }
                    }
                }
                eventType = parser.next();
            }
            mLoaded = true;
            Log.i(TAG, "loaded " + mAppRates.size() + " app configs from " + file);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "parse failed: " + e.getMessage());
            return false;
        }
    }

    /** 解析 rateId "2-2-2-2" → [2,2,2,2] */
    private int[] parseRateId(String rateId) {
        try {
            String[] parts = rateId.split("-");
            if (parts.length != 4) {
                return null;
            }
            int[] rates = new int[4];
            for (int i = 0; i < 4; i++) {
                rates[i] = Integer.parseInt(parts[i].trim());
            }
            return rates;
        } catch (Exception e) {
            return null;
        }
    }

    /** 获取应用的刷新率 (返回 Hz, 0 = 不指定) */
    public float getAppRate(String pkg, boolean isAnimating, boolean isTouching) {
        if (pkg == null) {
            return 0;
        }
        int[] rates = mAppRates.get(pkg);
        if (rates == null) {
            return 0;
        }

        // 触摸/动画: 优先 120Hz 档 (最流畅), 未指定则 90Hz 档
        // 静止: 用 60Hz 档 (省电), 未指定则 90Hz 档
        // rateId = [auto, 90Hz模式, 60Hz模式, 120Hz模式]
        int v;
        if (isAnimating || isTouching) {
            v = pick(rates, IDX_120, IDX_90, IDX_AUTO);
        } else {
            v = pick(rates, IDX_60, IDX_90, IDX_AUTO);
        }
        return rateToHz(v);
    }

    /** 按优先级取第一个非 0 值 */
    private int pick(int[] rates, int... idxs) {
        for (int i : idxs) {
            if (i >= 0 && i < rates.length && rates[i] != 0) {
                return rates[i];
            }
        }
        return 0;
    }

    /** rateId 值 → Hz */
    private float rateToHz(int v) {
        switch (v) {
            case 1: return 90f;
            case 2: return 60f;
            case 3: return 120f;
            default: return 0f;  // 0 = 不指定
        }
    }

    /** 是否已加载 */
    public boolean isLoaded() {
        return mLoaded;
    }
}
