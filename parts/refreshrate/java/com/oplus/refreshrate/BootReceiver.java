/*
 * Copyright (C) 2026 Oplus Project
 *
 * 开机自启接收器 — 无手动干预时自动启动刷新率调度服务。
 * 监听 BOOT_COMPLETED (开机) 和 MY_PACKAGE_REPLACED (OTA 升级后)。
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.oplus.refreshrate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "ORR/Boot";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        Log.i(TAG, "boot receiver: " + action);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            startService(context);
        }
    }

    /** 启动刷新率调度服务 (静默后台, 无前台通知) */
    public static void startService(Context context) {
        try {
            Intent svc = new Intent(context, RefreshRateService.class);
            context.startService(svc);
            Log.i(TAG, "service started");
        } catch (Exception e) {
            Log.e(TAG, "service start failed: " + e.getMessage());
        }
    }
}
