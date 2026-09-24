/*
 * Copyright (C) 2026 Oplus Project
 *
 * Oplus RefreshRate Service — 刷新率调度服务
 *
 * 功能:
 *   1. 锁定模式: 用户选 60/90/120Hz, 系统固定该刷新率 (任务1)
 *   2. 智能模式: 场景感知投票调度 (任务2, OPlusRefreshRateSelector 移植)
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.oplus.refreshrate;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;

import com.oplus.refreshrate.core.EnvironmentChecker;
import com.oplus.refreshrate.core.RefreshRateApplier;
import com.oplus.refreshrate.core.RefreshRateConfigParser;
import com.oplus.refreshrate.core.RefreshRateCore;
import com.oplus.refreshrate.core.SceneMonitor;
import com.oplus.refreshrate.core.SecondaryDisplayHider;
import com.oplus.refreshrate.core.VrrConfigParser;

import java.io.File;

/**
 * 刷新率调度服务 (前台服务, 保持运行)。
 */
public class RefreshRateService extends Service {
    private static final String TAG = "ORR/Service";

    // 动作 (由设置 app 或系统发送)
    public static final String ACTION_SET_MODE = "com.oplus.refreshrate.SET_MODE";
    public static final String EXTRA_MODE = "mode";  // 0=auto, 1=60, 2=90, 3=120
    public static final String ACTION_SET_BACKEND = "com.oplus.refreshrate.SET_BACKEND";
    public static final String EXTRA_BACKEND = "backend";

    // 当前模式
    private int mMode = 0;  // 0=智能, 1=60Hz, 2=90Hz, 3=120Hz

    private RefreshRateCore mCore;
    private SceneMonitor mSceneMonitor;
    private RefreshRateConfigParser mRefreshRateConfig;
    private VrrConfigParser mVrrConfig;

    /** 找第一个存在的配置文件 */
    private File findConfig(String... paths) {
        for (String p : paths) {
            File f = new File(p);
            if (f.exists()) {
                return f;
            }
        }
        return null;
    }

    /** 启动前台服务 (带通知, Android 12+ 必需) */
    private void startForegroundServiceWithNotification() {
        String channelId = "refreshrate";
        NotificationChannel channel = new NotificationChannel(channelId,
                "刷新率调度", NotificationManager.IMPORTANCE_LOW);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.createNotificationChannel(channel);
        }
        Notification notification = new Notification.Builder(this, channelId)
                .setContentTitle("刷新率调度")
                .setContentText("正在管理屏幕刷新率")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build();
        startForeground(1, notification);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate");
        mCore = new RefreshRateCore();

        // 前台服务通知 (Android 12+ 必须, 否则服务被杀)
        startForegroundServiceWithNotification();

        // 默认后端: SF (无 root 也可用)
        RefreshRateApplier.init(this, RefreshRateApplier.BACKEND_SF);

        // 场景监听 (智能模式)
        mSceneMonitor = new SceneMonitor(this, mCore);

        // 环境检查 (支持其他手机)
        EnvironmentChecker.Capabilities caps = EnvironmentChecker.check();
        if (!caps.supported) {
            Log.w(TAG, "environment not supported, service runs in safe mode");
            // 不支持时不隐藏 secondary, 仅提供锁定模式
        }

        // 加载原厂配置 (兼容 ColorOS 路径, 也支持自定义路径)
        mRefreshRateConfig = new RefreshRateConfigParser();
        mRefreshRateConfig.load(findConfig(
                "/my_product/etc/refresh_rate_config.xml",
                "/product/etc/refresh_rate_config.xml",
                "/data/system/refresh_rate_config.xml",
                "/system/etc/refresh_rate_config.xml"));
        mVrrConfig = new VrrConfigParser();
        mVrrConfig.load(findConfig(
                "/my_product/etc/oplus_vrr_config.json",
                "/product/etc/oplus_vrr_config.json",
                "/data/system/oplus_vrr_config.json",
                "/system/etc/oplus_vrr_config.json"));
        Log.i(TAG, "refresh configs: " + mRefreshRateConfig.size()
                + " apps, vrr v" + mVrrConfig.getVersion()
                + ", timeout=" + mVrrConfig.getTimeout());

        // 隐藏 secondary display (与 ColorOS 一致, 单 display 语义)
        // 仅在支持的环境中执行, 延迟等待 HWC/display 服务就绪
        if (caps.supported && caps.hasDisplayConfig) {
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(SecondaryDisplayHider::hideSecondary, 5000);
        }

        registerReceiver(mCommandReceiver, new IntentFilter(ACTION_SET_MODE));
        registerReceiver(mCommandReceiver, new IntentFilter(ACTION_SET_BACKEND));

        // 恢复上次模式
        mMode = getSharedPreferences("rr", MODE_PRIVATE).getInt("mode", 0);
        applyMode();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            handleAction(intent);
        }
        return START_STICKY;
    }

    private final BroadcastReceiver mCommandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handleAction(intent);
        }
    };

    private void handleAction(Intent intent) {
        String action = intent.getAction();
        if (ACTION_SET_MODE.equals(action)) {
            int mode = intent.getIntExtra(EXTRA_MODE, 0);
            setMode(mode);
        } else if (ACTION_SET_BACKEND.equals(action)) {
            int backend = intent.getIntExtra(EXTRA_BACKEND, RefreshRateApplier.BACKEND_SF);
            RefreshRateApplier.init(this, backend);
            Log.i(TAG, "backend changed to " + backend);
        }
    }

    /** 设置模式: 0=智能, 1=60, 2=90, 3=120 */
    public void setMode(int mode) {
        mMode = mode;
        getSharedPreferences("rr", MODE_PRIVATE).edit().putInt("mode", mode).apply();
        Log.i(TAG, "mode set to " + mode);
        applyMode();
    }

    private void applyMode() {
        switch (mMode) {
            case 1:  // 锁定 60Hz
                mCore.clearAllVotes();
                mCore.setVote(RefreshRateCore.Priority.MAX,
                        new RefreshRateCore.Vote(60f, 60f, "lock60"));
                break;
            case 2:  // 锁定 90Hz
                mCore.clearAllVotes();
                mCore.setVote(RefreshRateCore.Priority.MAX,
                        new RefreshRateCore.Vote(90f, 90f, "lock90"));
                break;
            case 3:  // 锁定 120Hz
                mCore.clearAllVotes();
                mCore.setVote(RefreshRateCore.Priority.MAX,
                        new RefreshRateCore.Vote(120f, 120f, "lock120"));
                break;
            case 0:
            default:  // 智能
                mCore.clearAllVotes();
                break;
        }
        // 立即应用
        float rate = mCore.getPerfectRefreshRate(false);
        if (rate > 0) {
            RefreshRateApplier.apply(rate);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (mSceneMonitor != null) {
            mSceneMonitor.shutdown();
        }
        unregisterReceiver(mCommandReceiver);
        super.onDestroy();
    }
}
