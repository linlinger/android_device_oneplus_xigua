/*
 * Copyright (C) 2026 Oplus Project
 *
 * 刷新率设置界面 — 原生设置风格 (Preference, 参考 LTPO Settings)
 * 用户选择 60/90/120Hz 或智能模式。
 * 集成到系统设置 (com.android.settings.action.IA_SETTINGS, 显示分类)。
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.oplus.refreshrate.settings;

import android.os.Bundle;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

/**
 * 刷新率设置 Activity (原生设置风格)。
 */
public class RefreshRateSettingsActivity extends CollapsingToolbarBaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportFragmentManager().beginTransaction().replace(
                com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                new RefreshRateSettings()).commit();
    }
}
