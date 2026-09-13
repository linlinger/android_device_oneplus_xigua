/*
 * Copyright (C) 2026 AviumUI Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aviumui.irisbypass;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

public class IrisBypassSettings extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {
    private static final String TAG = IrisBypassSettings.class.getSimpleName();

    private static final String KEY_BYPASS_SWITCH = "iris_bypass_enabled";

    /**
     * IRIS chip Analog Bypass sysfs node.
     * 1 = force ABYP (SOC direct-to-panel, IRIS powered down)
     * 0 = normal IRIS processing
     */
    private static final String FILE_ABYP = "/sys/kernel/iris/abyp_opt";

    private SwitchPreferenceCompat mBypassSwitch;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.iris_bypass_settings);

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        mBypassSwitch = (SwitchPreferenceCompat) findPreference(KEY_BYPASS_SWITCH);
        if (Utils.fileWritable(FILE_ABYP)) {
            mBypassSwitch.setEnabled(true);
            String current = Utils.getFileValue(FILE_ABYP, "0");
            boolean enabled = "1".equals(current != null ? current.trim() : null);
            mBypassSwitch.setChecked(sharedPrefs.getBoolean(KEY_BYPASS_SWITCH, enabled));
            mBypassSwitch.setOnPreferenceChangeListener(this);
        } else {
            mBypassSwitch.setEnabled(false);
            mBypassSwitch.setSummary(R.string.iris_bypass_not_available);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mBypassSwitch) {
            boolean enabled = (Boolean) newValue;
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            sharedPrefs.edit().putBoolean(KEY_BYPASS_SWITCH, enabled).apply();
            boolean ok = Utils.writeValue(FILE_ABYP, enabled ? "1" : "0");
            if (!ok) {
                Log.e(TAG, "Failed to write " + FILE_ABYP + " = " + (enabled ? "1" : "0"));
                mBypassSwitch.setChecked(!enabled);
            }
            return ok;
        }

        return false;
    }
}
