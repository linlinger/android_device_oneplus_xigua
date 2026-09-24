/*
 * Copyright (C) 2026 Oplus Project
 *
 * 刷新率设置界面 — 用户选择 60/90/120Hz 或智能模式。
 * 集成到系统设置 (com.android.settings.action.IA_SETTINGS)。
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.oplus.refreshrate.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.oplus.refreshrate.R;
import com.oplus.refreshrate.RefreshRateService;

/**
 * 刷新率设置 Activity (简单 RadioGroup UI)。
 */
public class RefreshRateSettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_refresh_rate);

        RadioGroup group = findViewById(R.id.rr_group);
        group.setOnCheckedChangeListener((g, checkedId) -> {
            int mode;
            if (checkedId == R.id.rr_auto) {
                mode = 0;
            } else if (checkedId == R.id.rr_60) {
                mode = 1;
            } else if (checkedId == R.id.rr_90) {
                mode = 2;
            } else {
                mode = 3;
            }
            // 发送到服务
            Intent intent = new Intent(this, RefreshRateService.class);
            intent.setAction(RefreshRateService.ACTION_SET_MODE);
            intent.putExtra(RefreshRateService.EXTRA_MODE, mode);
            startService(intent);
            Toast.makeText(this, "刷新率已设置", Toast.LENGTH_SHORT).show();
        });
    }
}
