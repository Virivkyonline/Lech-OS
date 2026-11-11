/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.nastavenia;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import lech.os.ui.Ui;

/** The rows of the settings, the same in the main menu and in the submenus: icon tile, texts, arrow or switch. */
final class Rows {
    private Rows() {
    }

    /** The glass row of the main menu with a coloured icon tile (icon 0: no tile). */
    static LinearLayout row(Context c, int icon, int top, int bottom, CharSequence title, CharSequence subtitle) {
        LinearLayout r = Ui.row(c);
        r.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xCC0F3340, 0xCC07202B});
        bg.setCornerRadius(Ui.dp(c, 30));
        bg.setStroke(Ui.dp(c, 1.2f), 0xAA3FA36A);
        Ui.clickable(r, bg);
        if (icon != 0) r.addView(tile(c, icon, top, bottom), Ui.lp(Ui.dp(c, 54), Ui.dp(c, 54)));
        LinearLayout t = Ui.column(c);
        t.setPadding(Ui.dp(c, icon != 0 ? 16 : 8), 0, Ui.dp(c, 8), 0);
        t.addView(Ui.text(c, title, 18, Ui.TEXT, true));
        TextView s = Ui.text(c, subtitle == null ? "" : subtitle, 14, Ui.TEXT_2, false);
        s.setMaxLines(2);
        s.setVisibility(subtitle == null || subtitle.length() == 0 ? View.GONE : View.VISIBLE);
        t.addView(s);
        r.addView(t, Ui.lp(0, -2, 1));
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.bottomMargin = Ui.dp(c, 9);
        r.setLayoutParams(lp);
        return r;
    }

    static FrameLayout tile(Context c, int icon, int top, int bottom) {
        FrameLayout tile = new FrameLayout(c);
        GradientDrawable tb = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {top, bottom});
        tb.setCornerRadius(Ui.dp(c, 16));
        tb.setStroke(Ui.dp(c, 1), 0x66FFFFFF);
        tile.setBackground(tb);
        tile.addView(Ui.icon(c, icon, Ui.TEXT, 30), new FrameLayout.LayoutParams(Ui.dp(c, 30), Ui.dp(c, 30),
                Gravity.CENTER));
        tile.setElevation(Ui.dp(c, 3));
        return tile;
    }

    /** The subtitle of a row made by row(). */
    static TextView subtitle(LinearLayout row) {
        LinearLayout t = (LinearLayout) row.getChildAt(row.getChildCount() > 1 && row.getChildAt(0) instanceof FrameLayout
                ? 1 : 0);
        return (TextView) t.getChildAt(1);
    }

    static void setSubtitle(LinearLayout row, CharSequence s) {
        TextView t = subtitle(row);
        t.setText(s);
        t.setVisibility(s == null || s.length() == 0 ? View.GONE : View.VISIBLE);
    }

    static void arrow(LinearLayout row) {
        row.addView(Ui.icon(row.getContext(), R.drawable.ic_chevron_right, Ui.TEXT, 28));
    }

    /** A green switch at the end of the row; the whole row toggles it. */
    static Switch toggle(LinearLayout row, boolean on, Switch.OnCheckedChangeListener l) {
        Context c = row.getContext();
        Switch sw = new Switch(c);
        sw.setChecked(on);
        sw.setThumbTintList(new ColorStateList(new int[][] {{android.R.attr.state_checked}, {}},
                new int[] {0xFFFFFFFF, 0xFFDFE6EA}));
        sw.setTrackTintList(new ColorStateList(new int[][] {{android.R.attr.state_checked}, {}},
                new int[] {Ui.GREEN, 0xFF37474F}));
        sw.setScaleX(1.2f);
        sw.setScaleY(1.2f);
        sw.setOnCheckedChangeListener(l);
        row.addView(sw);
        row.setOnClickListener(v -> sw.toggle());
        return sw;
    }

    /** A row with a green slider under its title (0 … max). */
    static SeekBar slider(LinearLayout row, int max, int value, SeekBar.OnSeekBarChangeListener l) {
        Context c = row.getContext();
        LinearLayout t = (LinearLayout) row.getChildAt(row.getChildCount() - 1);
        SeekBar s = new SeekBar(c);
        s.setMax(max);
        s.setProgress(value);
        s.setProgressTintList(ColorStateList.valueOf(Ui.GREEN));
        s.setThumbTintList(ColorStateList.valueOf(0xFFFFFFFF));
        s.setProgressBackgroundTintList(ColorStateList.valueOf(0x55FFFFFF));
        s.setOnSeekBarChangeListener(l);
        t.addView(s, Ui.margins(Ui.lp(-1, -2), c, -8, 8, 0, 0));
        row.setClickable(false);
        return s;
    }

    /** A segmented choice (e.g. Ring / Vibrate / Silent) in a glass bar; returns the segments. */
    static LinearLayout segments(Context c, String[] labels, int selected, java.util.function.IntConsumer onPick) {
        LinearLayout bar = Ui.row(c);
        bar.setBackground(Ui.shape(c, Ui.GLASS_STRONG, 0xAA3FA36A, 1.2f, 24));
        bar.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
        for (int k = 0; k < labels.length; k++) {
            final int idx = k;
            TextView t = Ui.text(c, labels[k], 15, Ui.TEXT, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 10));
            t.setOnClickListener(v -> onPick.accept(idx));
            bar.addView(t, Ui.lp(0, -2, 1));
        }
        mark(bar, selected);
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.bottomMargin = Ui.dp(c, 9);
        bar.setLayoutParams(lp);
        return bar;
    }

    static void mark(LinearLayout bar, int selected) {
        Context c = bar.getContext();
        for (int k = 0; k < bar.getChildCount(); k++) {
            TextView t = (TextView) bar.getChildAt(k);
            boolean on = k == selected;
            Ui.clickable(t, on ? Ui.greenGlow(c, 20) : Ui.shape(c, 0, 0, 0, 20));
            t.setTextColor(on ? Ui.ON_GREEN : Ui.TEXT_2);
        }
    }

    static TextView section(Context c, CharSequence title) {
        TextView t = Ui.text(c, title, 15, Ui.GREEN, true);
        t.setPadding(Ui.dp(c, 10), Ui.dp(c, 8), 0, Ui.dp(c, 6));
        return t;
    }
}
