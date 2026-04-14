/*
 * Copyright (c) 2020 EKA2L1 Team
 *
 * This file is part of EKA2L1 project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.eka2l1.info;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.github.eka2l1.BuildConfig;
import com.github.eka2l1.R;

public class AboutDialogFragment extends DialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String versionText = getString(R.string.version) + BuildConfig.VERSION_NAME + '-' + BuildConfig.GIT_HASH;
        String bodyHtml = new StringBuilder()
                .append(getText(R.string.about_website))
                .append(getText(R.string.about_github))
                .append(getText(R.string.about_crowdin))
                .append(getText(R.string.about_dualstick_modification))
                .append(getText(R.string.about_copyright))
                .append(getText(R.string.about_icon_by))
                .append(getText(R.string.about_icon_author))
                .toString();

        View contentView = AboutDialogHelper.createAboutContentView(requireContext(), versionText, bodyHtml);

        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setTitle(R.string.app_name)
                .setIcon(R.mipmap.ic_ducky)
                .setView(contentView)
                .setPositiveButton(R.string.about_special_thanks_title, (d, w) -> {
                    SpecialThanksDialogFragment specialThanksFragment = new SpecialThanksDialogFragment();
                    specialThanksFragment.show(getParentFragmentManager(), "specialThanks");
                })
                .setNegativeButton(R.string.about_translator, (d, w) -> {
                    TranslatorsDialogFragment translatorsDialogFragment = new TranslatorsDialogFragment();
                    translatorsDialogFragment.show(getParentFragmentManager(), "translators");
                });
        return builder.create();
    }
}
