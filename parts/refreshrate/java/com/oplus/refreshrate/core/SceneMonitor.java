/*
 * Copyright (C) 2026 Oplus Project
 *
 * 场景感知 — 监听前台应用/触摸, 为刷新率投票。
 * 参考 ColorOS OPlusRefreshRateService 的场景感知逻辑:
 *   - 前台应用 → 查 OPPO 原厂配置表 (refresh_rate_config.xml) 得刷新率
 *   - 触摸/滚动 → 高刷新率
 *   - 静止 → 低刷新率
 *   - 游戏 → 高刷新率
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.oplus.refreshrate.core;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.TaskStackListener;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.List;

/**
 * 场景感知: 前台应用 + 触摸 + 动画 → 刷新率投票。
 */
public class SceneMonitor {
    private static final String TAG = "ORR/Scene";

    // 投票优先级
    private static final int P_GAME = RefreshRateCore.Priority.GAME_REQUEST;
    private static final int P_ANIM = RefreshRateCore.Priority.ANIMATION_BOOST;
    private static final int P_APP = RefreshRateCore.Priority.APP_REQUEST;

    // 刷新率档位
    public static final float RATE_120 = 120f;
    public static final float RATE_90 = 90f;
    public static final float RATE_60 = 60f;

    // 前台应用轮询间隔
    private static final long POLL_INTERVAL_MS = 1000;
    // 静止降频超时
    private static final long IDLE_TIMEOUT_MS = 3000;

    private final Context mContext;
    private final RefreshRateCore mCore;
    private final AppConfig mAppConfig;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private String mForegroundPackage = "";
    private boolean mIsGame = false;
    private boolean mIsTouchActive = false;
    private boolean mIsAnimating = false;
    private boolean mRunning = false;

    // 前台应用监听 (TaskStackListener, 等价 OPPO OplusAppSwitchManager)
    private final TaskStackListener mTaskListener = new TaskStackListener() {
        @Override
        public void onTaskFocusChanged(int taskId, boolean hasFocus) {
            if (hasFocus) {
                String pkg = getTopPackage();
                if (pkg != null && !pkg.equals(mForegroundPackage)) {
                    mForegroundPackage = pkg;
                    onAppChanged();
                }
            }
        }
    };

    // 前台应用轮询 (兜底, 防 TaskStackListener 漏事件)
    private final Runnable mPollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mRunning) {
                return;
            }
            String fg = getForegroundPackage();
            if (!fg.equals(mForegroundPackage)) {
                mForegroundPackage = fg;
                onAppChanged();
            }
            mHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    private final Runnable mIdleRunnable = () -> {
        if (!mIsTouchActive) {
            applyIdleVote();
        }
    };

    public SceneMonitor(Context context, RefreshRateCore core, AppConfig appConfig) {
        mContext = context;
        mCore = core;
        mAppConfig = appConfig;
        registerReceivers();
    }

    public void start() {
        mRunning = true;
        // 注册 TaskStackListener (前台应用切换监听, 等价 OPPO OplusAppSwitchManager)
        try {
            ActivityTaskManager.getInstance()
                    .registerTaskStackListener(mTaskListener);
            Log.i(TAG, "task stack listener registered");
        } catch (Exception e) {
            Log.w(TAG, "registerTaskStackListener failed: " + e.getMessage());
        }
        mHandler.post(mPollRunnable);
        Log.i(TAG, "scene monitor started");
    }

    /** 当前前台包名 (ActivityTaskManager, 兜底用法) */
    private String getTopPackage() {
        try {
            ActivityTaskManager atm = ActivityTaskManager.getInstance();
            List<ActivityManager.RunningTaskInfo> tasks =
                    atm.getTasks(1);
            if (tasks != null && !tasks.isEmpty()) {
                return tasks.get(0).topActivity.getPackageName();
            }
        } catch (Exception e) {
            // ignore
        }
        return getForegroundPackage();
    }

    private void registerReceivers() {
        IntentFilter filter = new IntentFilter();
        // 触摸状态 (由系统/辅助服务发送)
        filter.addAction("com.oplus.refreshrate.TOUCH_ACTIVE");
        filter.addAction("com.oplus.refreshrate.TOUCH_IDLE");
        mContext.registerReceiver(mReceiver, filter,
                Context.RECEIVER_NOT_EXPORTED);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) {
                return;
            }
            switch (action) {
                case "com.oplus.refreshrate.TOUCH_ACTIVE":
                    onTouchActive();
                    break;
                case "com.oplus.refreshrate.TOUCH_IDLE":
                    onTouchIdle();
                    break;
            }
        }
    };

    /** 前台应用变化 (轮询触发) */
    private void onAppChanged() {
        mIsGame = isGameApp(mForegroundPackage);
        Log.d(TAG, "foreground=" + mForegroundPackage + " game=" + mIsGame);

        // 查配置表: 刚切换前台视为"活动" (触摸档), 静止降频由 idle 处理
        float appRate = 0;
        if (mAppConfig != null) {
            appRate = mAppConfig.getAppRate(mForegroundPackage,
                    true /* isAnimating */, true /* isTouching */);
        }
        if (mIsGame) {
            mCore.setVote(P_GAME,
                    new RefreshRateCore.Vote(RATE_120, RATE_60,
                            "game:" + mForegroundPackage));
        } else if (appRate > 0) {
            mCore.clearVote(P_GAME);
            mCore.setVote(P_APP,
                    new RefreshRateCore.Vote(appRate, RATE_60,
                            "app:" + mForegroundPackage));
        } else {
            mCore.clearVote(P_GAME);
            applyIdleVote();
        }
        onVotesChanged();
    }

    /** 触摸激活 */
    private void onTouchActive() {
        mIsTouchActive = true;
        mCore.setVote(RefreshRateCore.Priority.FLEXIBLE_WINDOW,
                new RefreshRateCore.Vote(RATE_120, RATE_90, "touch"));
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
        mCore.clearVote(RefreshRateCore.Priority.FLEXIBLE_WINDOW);
        if (mIsAnimating) {
            mCore.setVote(P_ANIM,
                    new RefreshRateCore.Vote(RATE_120, RATE_60, "anim"));
        } else if (!mIsGame) {
            mCore.setVote(P_APP,
                    new RefreshRateCore.Vote(RATE_60, RATE_60,
                            "idle:" + mForegroundPackage));
        }
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

    /** 前台应用 (UsageStatsManager 轮询) */
    private String getForegroundPackage() {
        try {
            UsageStatsManager usm = (UsageStatsManager)
                    mContext.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) {
                return mForegroundPackage;
            }
            long now = System.currentTimeMillis();
            UsageEvents events = usm.queryEvents(now - 5000, now);
            String fg = mForegroundPackage;
            UsageEvents.Event event = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    fg = event.getPackageName();
                }
            }
            return fg == null ? "" : fg;
        } catch (Exception e) {
            return mForegroundPackage;
        }
    }

    /** 游戏应用识别 */
    private boolean isGameApp(String pkg) {
        if (pkg == null) {
            return false;
        }
        return pkg.contains("tencent") || pkg.contains("miHoYo")
                || pkg.contains("netease") || pkg.contains("game")
                || pkg.contains("unity") || pkg.contains("epic")
                || pkg.contains("supercell");
    }

    public void shutdown() {
        mRunning = false;
        try {
            ActivityTaskManager.getInstance()
                    .unregisterTaskStackListener(mTaskListener);
        } catch (Exception ignored) {
        }
        mHandler.removeCallbacks(mPollRunnable);
        mHandler.removeCallbacks(mIdleRunnable);
        try {
            mContext.unregisterReceiver(mReceiver);
        } catch (Exception ignored) {
        }
    }
}
