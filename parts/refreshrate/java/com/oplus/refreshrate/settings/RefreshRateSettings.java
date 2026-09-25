/*
 * Copyright (C) 2026 Oplus Project
 *
 * 刷新率设置 Fragment — 原生设置风格 (PreferenceFragmentCompat)
 * 选项: 智能模式 / 锁定 60Hz / 锁定 90Hz / 锁定 120Hz
 * 选择后发送广播给 RefreshRateService 生效。
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.oplus.refreshrate.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.oplus.refreshrate.R;
import com.oplus.refreshrate.RefreshRateService;

/**
 * 刷新率设置 (Preference 列表, 原生风格)。
 */
public class RefreshRateSettings extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_REFRESH_RATE = "refresh_rate_mode";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.refresh_rate_settings, rootKey);

        ListPreference rr = findPreference(KEY_REFRESH_RATE);
        if (rr != null) {
            rr.setOnPreferenceChangeListener(this);
            // 恢复上次选择
            int mode = getSharedPrefs().getInt("mode", 0);
            rr.setValue(String.valueOf(mode));
            updateSummary(rr, mode);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (KEY_REFRESH_RATE.equals(preference.getKey())) {
            int mode = Integer.parseInt((String) newValue);
            // 发送到服务
            Intent intent = new Intent(getContext(), RefreshRateService.class);
            intent.setAction(RefreshRateService.ACTION_SET_MODE);
            intent.putExtra(RefreshRateService.EXTRA_MODE, mode);
            getContext().startService(intent);
            // 保存
            getSharedPrefs().edit().putInt("mode", mode).apply();
            updateSummary((ListPreference) preference, mode);
            return true;
        }
        return false;
    }

    private SharedPreferences getSharedPrefs() {
        Context ctx = getContext();
        return ctx.getSharedPreferences("rr", Context.MODE_PRIVATE);
    }

    private void updateSummary(ListPreference pref, int mode) {
        String[] entries = getResources()
                .getStringArray(R.array.refresh_rate_entries);
        String[] values = getResources()
                .getStringArray(R.array.refresh_rate_values);
        for (int i = 0; i < values.length; i++) {
            if (Integer.parseInt(values[i]) == mode) {
                pref.setSummary(entries[i]);
                return;
            }
        }
        pref.setSummary(entries[0]);
    }
}
