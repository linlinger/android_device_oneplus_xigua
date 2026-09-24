/*
 * Copyright (C) 2026 Oplus Project
 *
 * VRR JSON 配置解析器 — 兼容 ColorOS 的 oplus_vrr_config.json。
 *
 * 格式 (来自 ColorOS 原厂 /my_product/etc/oplus_vrr_config.json):
 *   [
 *     {"filter_name": "oplus_adfr_config"},
 *     {"version": 20250403},
 *     {"feature_sa": "true"},
 *     {"feature_osync": 3},
 *     {"sa_backlight": true, "sa_backlight_strategy": [
 *        {"fps": 120, "list": ["70:120", "300:60", "1500:120"]}, ...
 *     ]},
 *     {"game_list": [{"pkg_name": ["com.tencent.tmgp.sgame"], ...}]},
 *     {"blacklist": [包名...]},
 *     {"timeout": 3500},
 *     {"hw_enable": true}, {"sw_enable": true},
 *     ...
 *   ]
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.oplus.refreshrate.core;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * VRR 总配置解析器 (兼容 oplus_vrr_config.json)。
 * 提供: 背光→刷新率策略 / 游戏列表 / 黑名单 / 静止超时。
 */
public final class VrrConfigParser {
    private static final String TAG = "ORR/Vrr";

    // 背光→刷新率 策略条目
    public static final class BacklightStrategy {
        public int fps;                    // 目标刷新率
        public final List<int[]> map = new ArrayList<>();  // {nit, rate}

        public int getRateForBrightness(int nit) {
            // 从高到低匹配 (类似原厂 "70:120, 300:60, 1500:120")
            for (int i = map.size() - 1; i >= 0; i--) {
                int[] entry = map.get(i);
                if (nit >= entry[0]) {
                    return entry[1];
                }
            }
            return map.isEmpty() ? fps : map.get(0)[1];
        }
    }

    // 游戏配置
    public static final class GameConfig {
        public final List<String> pkgNames = new ArrayList<>();
        public boolean backlight;          // 背光策略
        public final List<BacklightStrategy> backlightStrategy = new ArrayList<>();
        public boolean kfc;                // KFC 抖动
        public int[] kfcTarget;
    }

    private String mVersion = "";
    private boolean mFeatureSa = false;
    private int mFeatureOsync = 0;
    private boolean mSaBacklight = false;
    private final List<BacklightStrategy> mSaBacklightStrategy = new ArrayList<>();
    private final List<GameConfig> mGameList = new ArrayList<>();
    private final List<String> mBlacklist = new ArrayList<>();
    private final List<String> mSwWhitelist3rd = new ArrayList<>();
    private int mTimeout = 3500;           // 静止降频超时 (ms)
    private boolean mHwEnable = false;
    private boolean mSwEnable = false;
    private boolean mAdfrEnable = false;
    private boolean mDeferredModeChange = false;

    /** 加载 JSON 配置 */
    public boolean load(File file) {
        if (file == null || !file.exists()) {
            Log.w(TAG, "config not found: " + file);
            return false;
        }
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            JSONArray arr = new JSONArray(new String(data, StandardCharsets.UTF_8));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                if (obj.has("version")) {
                    mVersion = String.valueOf(obj.optLong("version"));
                } else if (obj.has("feature_sa")) {
                    mFeatureSa = obj.optString("feature_sa", "false").equals("true");
                } else if (obj.has("feature_osync")) {
                    mFeatureOsync = obj.optInt("feature_osync", 0);
                } else if (obj.has("sa_backlight")) {
                    mSaBacklight = obj.optBoolean("sa_backlight");
                    parseBacklightStrategy(obj.optJSONArray("sa_backlight_strategy"),
                            mSaBacklightStrategy);
                } else if (obj.has("game_list")) {
                    parseGameList(obj.optJSONArray("game_list"));
                } else if (obj.has("blacklist")) {
                    parseStringList(obj.optJSONArray("blacklist"), mBlacklist);
                } else if (obj.has("sw_whitelist_3rd")) {
                    parseStringList(obj.optJSONArray("sw_whitelist_3rd"), mSwWhitelist3rd);
                } else if (obj.has("timeout")) {
                    mTimeout = obj.optInt("timeout", 3500);
                } else if (obj.has("hw_enable")) {
                    mHwEnable = obj.optBoolean("hw_enable");
                } else if (obj.has("sw_enable")) {
                    mSwEnable = obj.optBoolean("sw_enable");
                } else if (obj.has("adfr_enable")) {
                    mAdfrEnable = obj.optBoolean("adfr_enable");
                } else if (obj.has("deferred_mode_change")) {
                    mDeferredModeChange = obj.optBoolean("deferred_mode_change");
                }
            }
            Log.i(TAG, "loaded vrr config v" + mVersion
                    + ", games=" + mGameList.size()
                    + ", blacklist=" + mBlacklist.size());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "load failed", e);
            return false;
        }
    }

    private void parseBacklightStrategy(JSONArray arr, List<BacklightStrategy> out) {
        if (arr == null) {
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject o = arr.getJSONObject(i);
                BacklightStrategy bs = new BacklightStrategy();
                bs.fps = o.optInt("fps", 60);
                JSONArray list = o.optJSONArray("list");
                if (list != null) {
                    for (int j = 0; j < list.length(); j++) {
                        String s = list.getString(j);
                        String[] parts = s.split(":");
                        if (parts.length == 2) {
                            bs.map.add(new int[]{
                                    Integer.parseInt(parts[0]),
                                    Integer.parseInt(parts[1])});
                        }
                    }
                }
                out.add(bs);
            } catch (Exception e) {
                Log.e(TAG, "parse backlight strategy failed", e);
            }
        }
    }

    private void parseGameList(JSONArray arr) {
        if (arr == null) {
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject o = arr.getJSONObject(i);
                GameConfig gc = new GameConfig();
                JSONArray pkgs = o.optJSONArray("pkg_name");
                if (pkgs != null) {
                    for (int j = 0; j < pkgs.length(); j++) {
                        gc.pkgNames.add(pkgs.getString(j));
                    }
                }
                gc.backlight = o.optBoolean("backlight", false);
                parseBacklightStrategy(o.optJSONArray("backlight_strategy"),
                        gc.backlightStrategy);
                gc.kfc = o.optBoolean("kfc", false);
                JSONArray kfcT = o.optJSONArray("kfc_target");
                if (kfcT != null && kfcT.length() >= 2) {
                    gc.kfcTarget = new int[]{kfcT.getInt(0), kfcT.getInt(1)};
                }
                mGameList.add(gc);
            } catch (Exception e) {
                Log.e(TAG, "parse game failed", e);
            }
        }
    }

    private void parseStringList(JSONArray arr, List<String> out) {
        if (arr == null) {
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            try {
                out.add(arr.getString(i));
            } catch (Exception e) {
                // skip
            }
        }
    }

    /** 查询游戏配置 */
    public GameConfig getGameConfig(String pkg) {
        for (GameConfig gc : mGameList) {
            if (gc.pkgNames.contains(pkg)) {
                return gc;
            }
        }
        return null;
    }

    public boolean isBlacklisted(String pkg) {
        return mBlacklist.contains(pkg);
    }

    public boolean isSwWhitelisted(String pkg) {
        return mSwWhitelist3rd.contains(pkg);
    }

    /** 根据背光亮度返回刷新率 (SA 策略) */
    public int getSaRateForBrightness(int nit, int defaultFps) {
        if (!mSaBacklight) {
            return defaultFps;
        }
        // 找匹配 fps 档
        for (BacklightStrategy bs : mSaBacklightStrategy) {
            if (bs.fps == defaultFps) {
                return bs.getRateForBrightness(nit);
            }
        }
        return defaultFps;
    }

    public String getVersion() {
        return mVersion;
    }

    public int getTimeout() {
        return mTimeout;
    }

    public boolean isHwEnable() {
        return mHwEnable;
    }

    public boolean isSwEnable() {
        return mSwEnable;
    }

    public boolean isDeferredModeChange() {
        return mDeferredModeChange;
    }

    public int getFeatureOsync() {
        return mFeatureOsync;
    }
}
