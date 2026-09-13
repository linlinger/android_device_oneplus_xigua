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

import android.os.SystemProperties;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class Utils {
    private static final String TAG = "IrisBypassUtils";

    /**
     * Write a value to a sysfs node, using root if direct write fails.
     * @return true on success
     */
    public static boolean writeValue(String filename, String value) {
        if (filename == null) {
            return false;
        }
        // Try direct write first (works when app runs with system/root perms)
        try {
            FileOutputStream fos = new FileOutputStream(new File(filename));
            fos.write(value.getBytes());
            fos.flush();
            fos.close();
            return true;
        } catch (Exception e) {
            Log.d(TAG, "Direct write failed, trying root: " + e.getMessage());
        }

        // Fall back to su (Magisk/KernelSU)
        return writeValueWithRoot(filename, value);
    }

    private static boolean writeValueWithRoot(String filename, String value) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("echo " + value + " > " + filename + "\n");
            os.writeBytes("exit\n");
            os.flush();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            Log.e(TAG, "Root write failed: " + e.getMessage());
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    public static String getFileValue(String filename, String defValue) {
        String value = readLine(filename);
        return value != null ? value : defValue;
    }

    public static boolean fileWritable(String filename) {
        return fileExists(filename) && new File(filename).canWrite();
    }

    public static boolean fileExists(String filename) {
        if (filename == null) {
            return false;
        }
        return new File(filename).exists();
    }

    private static String readLine(String filename) {
        if (filename == null) {
            return null;
        }
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(filename)));
            return br.readLine();
        } catch (IOException e) {
            return null;
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
