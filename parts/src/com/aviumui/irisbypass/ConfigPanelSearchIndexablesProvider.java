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

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.SearchIndexablesContract;
import android.provider.SearchIndexablesProvider;

public class ConfigPanelSearchIndexablesProvider extends SearchIndexablesProvider {
    private static final String TAG = "ConfigPanelSearchIndexablesProvider";

    private static SearchIndexablesContract.XmlResourceIndexable[] INDEXABLE_RES =
            new SearchIndexablesContract.XmlResourceIndexable[] {
                new SearchIndexablesContract.XmlResourceIndexable(1, R.xml.iris_bypass_settings,
                        IrisBypassSettingsActivity.class.getName(),
                        R.drawable.ic_settings_device),
            };

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor queryXmlResources(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(SearchIndexablesContract.INDEXABLES_XML_RES_COLUMNS);
        for (SearchIndexablesContract.XmlResourceIndexable sir : INDEXABLE_RES) {
            cursor.addRow(generateResourceRef(sir));
        }
        return cursor;
    }

    @Override
    public Cursor queryRawData(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(SearchIndexablesContract.INDEXABLES_RAW_COLUMNS);
        return cursor;
    }

    @Override
    public Cursor queryNonIndexableKeys(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(
                SearchIndexablesContract.NON_INDEXABLES_KEYS_COLUMNS);
        return cursor;
    }

    private static Object[] generateResourceRef(SearchIndexablesContract.XmlResourceIndexable sir) {
        Object[] ref = new Object[7];
        ref[SearchIndexablesContract.COLUMN_INDEX_XML_RES_RANK] = sir.rank;
        ref[SearchIndexablesContract.COLUMN_INDEX_XML_RES_RESID] = sir.xmlResId;
        ref[SearchIndexablesContract.COLUMN_INDEX_XML_RES_CLASS_NAME] = null;
        ref[SearchIndexablesContract.COLUMN_INDEX_XML_RES_ICON_RESID] = sir.iconResId;
        ref[SearchIndexablesContract.COLUMN_INDEX_XML_RES_INTENT_ACTION] =
                "com.android.settings.action.EXTRA_SETTINGS";
        ref[SearchIndexablesContract.COLUMN_INDEX_XML_RES_INTENT_TARGET_PACKAGE] =
                "com.aviumui.irisbypass";
        ref[SearchIndexablesContract.COLUMN_INDEX_XML_RES_INTENT_TARGET_CLASS] = sir.className;
        return ref;
    }
}
