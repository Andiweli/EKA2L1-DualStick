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

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.BulletSpan;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.text.HtmlCompat;

public final class AboutDialogHelper {
    private AboutDialogHelper() {
    }

    public static View createAboutContentView(Context context, CharSequence versionText, CharSequence bodyHtml) {
        LinearLayout container = createContainer(context);

        TextView versionView = createBaseTextView(context);
        versionView.setTypeface(versionView.getTypeface(), Typeface.BOLD);
        versionView.setText(versionText);
        container.addView(versionView);

        TextView bodyView = createBaseTextView(context);
        bodyView.setMovementMethod(LinkMovementMethod.getInstance());

        SpannableStringBuilder bodyText = new SpannableStringBuilder(
                HtmlCompat.fromHtml(bodyHtml.toString(), HtmlCompat.FROM_HTML_MODE_LEGACY)
        );
        while (bodyText.length() > 0 && Character.isWhitespace(bodyText.charAt(0))) {
            bodyText.delete(0, 1);
        }
        bodyView.setText(bodyText);

        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        bodyParams.topMargin = dp(context, 8);
        container.addView(bodyView, bodyParams);

        return wrapInScrollView(context, container);
    }

    public static View createBulletedListView(Context context, CharSequence rawContent) {
        LinearLayout container = createContainer(context);

        TextView listView = createBaseTextView(context);
        listView.setText(buildBulletedList(context, rawContent));
        container.addView(listView);

        return wrapInScrollView(context, container);
    }

    private static LinearLayout createContainer(Context context) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        int paddingHorizontal = dp(context, 20);
        int paddingVertical = dp(context, 14);
        container.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);
        return container;
    }

    private static TextView createBaseTextView(Context context) {
        TextView tv = new TextView(context);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        return tv;
    }

    private static ScrollView wrapInScrollView(Context context, View child) {
        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(child, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return scrollView;
    }

    private static CharSequence buildBulletedList(Context context, CharSequence rawContent) {
        String normalized = HtmlCompat.fromHtml(rawContent.toString(), HtmlCompat.FROM_HTML_MODE_LEGACY)
                .toString()
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        String[] lines = normalized.split("\\n");
        SpannableStringBuilder builder = new SpannableStringBuilder();
        int bulletGap = dp(context, 10);

        for (String line : lines) {
            String item = line.trim();
            if (item.isEmpty()) {
                continue;
            }

            item = item.replaceFirst("^[-•]\\s*", "");
            if (item.isEmpty()) {
                continue;
            }

            int start = builder.length();
            builder.append(item).append('\n');
            builder.setSpan(new BulletSpan(bulletGap), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        if (builder.length() > 0) {
            builder.delete(builder.length() - 1, builder.length());
        }

        return builder;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
