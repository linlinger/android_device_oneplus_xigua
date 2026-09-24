/*
 * Copyright (C) 2026 Oplus Project
 *
 * 场景感知: 监听系统状态变化, 为刷新率投票。
 * 净室实现, 行为对齐 ColorOS OPlusRefreshRateService 的场景感知逻辑:
 *   - 前台应用类型 (游戏/视频/普通) → 不同投票
 *   - 触摸/滚动 → 高刷新率投票
 *   - 静止 → 低刷新率投票
 *   - 动画 → 高刷新率投票
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.oplus.refreshrate.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * 场景监听器 — 收集系统状态并投票。
 */
public class SceneMonitor {
    private static final String TAG = "ORR/Scene";

    // 投票优先级
    private static final int P_APP = RefreshRateCore.Priority.APP_REQUEST;
    private static final int P_GAME = RefreshRateCore.Priority.GAME_REQUEST;
    private static final int P_ANIM = RefreshRateCore.Priority.ANIMATION_BOOST;
    private static final int P_TOUCH = RefreshRateCore.Priority.FLEXIBLE_WINDOW;

    // 刷新率档位 (xigua: 120/90/60)
    public static final float RATE_120 = 120f;
    public static final float RATE_90 = 90f;
    public static final float RATE_60 = 60f;

    private final Context mContext;
    private final RefreshRateCore mCore;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    // 当前状态
    private String mForegroundPackage = "";
    private boolean mIsGame = false;
    private boolean mIsTouchActive = false;
    private boolean mIsAnimating = false;

    // 静止检测 (无触摸 N 秒后降频)
    private static final long IDLE_TIMEOUT_MS = 3000;
    private final Runnable mIdleRunnable = () -> {
        if (!mIsTouchActive) {
            applyIdleVote();
        }
    };

    public SceneMonitor(Context context, RefreshRateCore core) {
        mContext = context;
        mCore = core;
        registerReceivers();
    }

    private void registerReceivers() {
        IntentFilter filter = new IntentFilter();
        // 应用切换 (需要权限, 见 AndroidManifest)
        filter.addAction("com.oplus.refreshrate.APP_FOREGROUND");
        // 触摸状态
        filter.addAction("com.oplus.refreshrate.TOUCH_ACTIVE");
        filter.addAction("com.oplus.refreshrate.TOUCH_IDLE");
        // 动画
        filter.addAction("com.oplus.refreshrate.ANIM_START");
        filter.addAction("com.oplus.refreshrate.ANIM_END");
        mContext.registerReceiver(mReceiver, filter);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) {
                return;
            }
            switch (action) {
                case "com.oplus.refreshrate.APP_FOREGROUND":
                    onAppForeground(intent.getStringExtra("pkg"),
                            intent.getStringExtra("activity"));
                    break;
                case "com.oplus.refreshrate.TOUCH_ACTIVE":
                    onTouchActive();
                    break;
                case "com.oplus.refreshrate.TOUCH_IDLE":
                    onTouchIdle();
                    break;
                case "com.oplus.refreshrate.ANIM_START":
                    onAnimStart();
                    break;
                case "com.oplus.refreshrate.ANIM_END":
                    onAnimEnd();
                    break;
            }
        }
    };

    /** 前台应用变化 */
    private void onAppForeground(String pkg, String activity) {
        mForegroundPackage = pkg == null ? "" : pkg;
        mIsGame = isGameApp(mForegroundPackage);
        Log.d(TAG, "foreground=" + mForegroundPackage + " game=" + mIsGame);

        if (mIsGame) {
            mCore.setVote(P_GAME, new RefreshRateCore.Vote(RATE_120, RATE_60, "game:" + mForegroundPackage));
        } else {
            mCore.clearVote(P_GAME);
            applyIdleVote();
        }
        onVotesChanged();
    }

    /** 触摸激活 */
    private void onTouchActive() {
        mIsTouchActive = true;
        mCore.setVote(P_TOUCH, new RefreshRateCore.Vote(RATE_120, RATE_90, "touch"));
        mHandler.removeCallbacks(mIdleRunnable);
        onVotesChanged();
    }

    /** 触摸停止 */
    private void onTouchIdle() {
        mIsTouchActive = false;
        mHandler.removeCallbacks(mIdleRunnable);
        mHandler.postDelayed(mIdleRunnable, IDLE_TIMEOUT_MS);
    }

    /** 静止降频 */
    private void applyIdleVote() {
        mCore.clearVote(P_TOUCH);
        if (mIsAnimating) {
            mCore.setVote(P_ANIM, new RefreshRateCore.Vote(RATE_120, RATE_60, "anim"));
        } else if (!mIsGame) {
            mCore.setVote(P_APP, new RefreshRateCore.Vote(RATE_60, RATE_60, "idle:" + mForegroundPackage));
        }
        onVotesChanged();
    }

    /** 动画开始 */
    private void onAnimStart() {
        mIsAnimating = true;
        mCore.setVote(P_ANIM, new RefreshRateCore.Vote(RATE_120, RATE_60, "anim"));
        onVotesChanged();
    }

    /** 动画结束 */
    private void onAnimEnd() {
        mIsAnimating = false;
        mCore.clearVote(P_ANIM);
        applyIdleVote();
        onVotesChanged();
    }

    /** 投票变化 → 通知调度器 */
    private void onVotesChanged() {
        float rate = mCore.getPerfectRefreshRate(false);
        Log.d(TAG, "votes changed, final rate=" + rate);
        if (rate > 0) {
            RefreshRateApplier.apply(rate);
        }
    }

    /** 游戏应用识别 (可扩展) */
    private boolean isGameApp(String pkg) {
        // 常见游戏包名 (可配置化)
        if (pkg == null) {
            return false;
        }
        return pkg.contains("tencent") || pkg.contains("miHoYo")
                || pkg.contains("netease") || pkg.contains("game");
    }

    public void shutdown() {
        mContext.unregisterReceiver(mReceiver);
    }
}
