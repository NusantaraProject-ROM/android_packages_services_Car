/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.developeroptions.applications.specialaccess.zenaccess;

import android.app.Dialog;
import android.app.settings.SettingsEnums;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.appcompat.app.AlertDialog;

import com.android.car.developeroptions.R;
import com.android.car.developeroptions.core.instrumentation.InstrumentedDialogFragment;
import com.android.car.developeroptions.notification.ZenAccessSettings;

/**
 * Warning dialog when allowing zen access warning about the privileges being granted.
 */
public class ScaryWarningDialogFragment extends InstrumentedDialogFragment {
    static final String KEY_PKG = "p";
    static final String KEY_LABEL = "l";

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DIALOG_ZEN_ACCESS_GRANT;
    }

    public ScaryWarningDialogFragment setPkgInfo(String pkg, CharSequence label) {
        Bundle args = new Bundle();
        args.putString(KEY_PKG, pkg);
        args.putString(KEY_LABEL, TextUtils.isEmpty(label) ? pkg : label.toString());
        setArguments(args);
        return this;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Bundle args = getArguments();
        final String pkg = args.getString(KEY_PKG);
        final String label = args.getString(KEY_LABEL);

        final String title = getResources().getString(R.string.zen_access_warning_dialog_title,
                label);
        final String summary = getResources()
                .getString(R.string.zen_access_warning_dialog_summary);
        return new AlertDialog.Builder(getContext())
                .setMessage(summary)
                .setTitle(title)
                .setCancelable(true)
                .setPositiveButton(R.string.allow,
                        (dialog, id) -> ZenAccessController.setAccess(getContext(), pkg, true))
                .setNegativeButton(R.string.deny,
                        (dialog, id) -> {
                            // pass
                        })
                .create();
    }
}
