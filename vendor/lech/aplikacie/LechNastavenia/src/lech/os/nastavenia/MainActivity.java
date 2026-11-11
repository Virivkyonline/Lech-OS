/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.nastavenia;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import lech.os.ui.Ui;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The settings of LECH OS: the main sections in the LECH OS design. Each section opens the matching screen of the
 * Android settings (com.android.settings), which keeps doing the actual work.
 */
public class MainActivity extends Activity {
    private static final int REQ_VOICE = 1;

    /** One section: icon, colours of its tile, title, subtitle and the settings screens to try in order. */
    private static final class Entry {
        final int icon;
        final int top;
        final int bottom;
        final int title;
        CharSequence subtitle;
        final String[] actions;
        final boolean system;

        Entry(int icon, int top, int bottom, int title, int subtitle, boolean system, String... actions) {
            this.icon = icon;
            this.top = top;
            this.bottom = bottom;
            this.title = title;
            this.subtitle = null;
            this.subtitleRes = subtitle;
            this.system = system;
            this.actions = actions;
        }

        final int subtitleRes;
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Entry> entries = new ArrayList<>();
    private final List<Entry> systemEntries = new ArrayList<>();
    private boolean inSystem;
    private Ui.Header header;
    private EditText search;
    private LinearLayout listView;
    private ScrollView scroll;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ThemeReceiver.apply(this);
        define();
        LinearLayout root = Ui.column(this);
        root.setBackground(Ui.background());
        header = Ui.header(this, R.mipmap.ic_nastavenia);
        root.addView(header.view, Ui.lp(-1, -2));

        LinearLayout box = Ui.row(this);
        box.setBackground(Ui.shape(this, 0xCC08202B, 0xFF5FC21A, 1.5f, 30));
        box.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 10), 0);
        box.addView(Ui.icon(this, R.drawable.ic_search, Ui.TEXT_2, 24));
        search = new EditText(this);
        search.setHint(R.string.search_hint);
        search.setBackground(null);
        search.setSingleLine(true);
        search.setTextColor(Ui.TEXT);
        search.setHintTextColor(Ui.TEXT_2);
        search.setTextSize(17);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setPadding(Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                render();
            }
        });
        box.addView(search, Ui.lp(0, -2, 1));
        Intent voice = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        if (voice.resolveActivity(getPackageManager()) != null) {
            ImageView mic = Ui.icon(this, R.drawable.ic_mic, Ui.TEXT, 26);
            mic.setContentDescription(getString(R.string.voice_search));
            int p = Ui.dp(this, 8);
            mic.setPadding(p, p, p, p);
            mic.setLayoutParams(Ui.lp(Ui.dp(this, 42), Ui.dp(this, 42)));
            Ui.clickable(mic, null);
            mic.setOnClickListener(v -> {
                voice.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                try {
                    startActivityForResult(voice, REQ_VOICE);
                } catch (ActivityNotFoundException ignored) {
                    // No recogniser.
                }
            });
            box.addView(mic);
        }
        root.addView(box, Ui.margins(Ui.lp(-1, -2), this, 16, 0, 16, 8));

        scroll = new ScrollView(this);
        listView = Ui.column(this);
        listView.setPadding(Ui.dp(this, 14), Ui.dp(this, 2), Ui.dp(this, 14), Ui.dp(this, 16));
        scroll.addView(listView, Ui.lp(-1, -2));
        root.addView(scroll, Ui.lp(-1, 0, 1));

        Ui.edgeToEdge(this, root);
        setContentView(root);
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> {
                    if (search.length() > 0) {
                        search.setText("");
                    } else if (inSystem) {
                        inSystem = false;
                        render();
                    } else {
                        finish();
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLive();
        render();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VOICE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty()) search.setText(r.get(0));
        }
    }

    private void define() {
        entries.add(new Entry(R.drawable.ic_wifi, 0xFF42A5F5, 0xFF0D47A1, R.string.network, R.string.network_sub, false,
                Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_WIFI_SETTINGS));
        entries.add(new Entry(R.drawable.ic_bluetooth, 0xFF448AFF, 0xFF1A237E, R.string.devices, R.string.devices_sub,
                false, "android.settings.CONNECTED_DEVICES_SETTINGS", Settings.ACTION_BLUETOOTH_SETTINGS));
        entries.add(new Entry(R.drawable.ic_apps, 0xFF9575CD, 0xFF4527A0, R.string.apps, R.string.apps_sub, false,
                "android.settings.APPS_SETTINGS", Settings.ACTION_APPLICATION_SETTINGS));
        entries.add(new Entry(R.drawable.ic_bell, 0xFFF06292, 0xFF880E4F, R.string.notifications,
                R.string.notifications_sub, false, "android.settings.NOTIFICATION_SETTINGS",
                Settings.ACTION_APP_NOTIFICATION_SETTINGS));
        entries.add(new Entry(R.drawable.ic_volume, 0xFFFFB74D, 0xFFE65100, R.string.sound, R.string.sound_sub, false,
                Settings.ACTION_SOUND_SETTINGS));
        entries.add(new Entry(R.drawable.ic_dnd, 0xFF7C4DFF, 0xFF311B92, R.string.modes, R.string.modes_sub, false,
                "android.settings.MODES_SETTINGS", "android.settings.ZEN_MODE_SETTINGS",
                Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS));
        entries.add(new Entry(R.drawable.ic_sun, 0xFFFFD54F, 0xFFB28704, R.string.display, R.string.display_sub, false,
                Settings.ACTION_DISPLAY_SETTINGS));
        entries.add(new Entry(R.drawable.ic_photo, 0xFF81C784, 0xFF1B5E20, R.string.wallpaper, R.string.wallpaper_sub,
                false, "android.settings.WALLPAPER_SETTINGS", Intent.ACTION_SET_WALLPAPER));
        entries.add(new Entry(R.drawable.ic_storage, 0xFF4DD0E1, 0xFF006064, R.string.storage, 0, false,
                Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
        entries.add(new Entry(R.drawable.ic_battery, 0xFF66BB6A, 0xFF1B5E20, R.string.battery, 0, false,
                Intent.ACTION_POWER_USAGE_SUMMARY, Settings.ACTION_BATTERY_SAVER_SETTINGS));
        entries.add(new Entry(R.drawable.ic_shield, 0xFF4FC3F7, 0xFF01579B, R.string.security, R.string.security_sub,
                false, "android.settings.SECURITY_PRIVACY_SETTINGS", Settings.ACTION_SECURITY_SETTINGS,
                Settings.ACTION_PRIVACY_SETTINGS));
        entries.add(new Entry(R.drawable.ic_sync, 0xFFBA68C8, 0xFF4A148C, R.string.system, R.string.system_sub, true));

        systemEntries.add(new Entry(R.drawable.ic_language, 0xFF4FC3F7, 0xFF01579B, R.string.languages,
                R.string.languages_sub, false, "android.settings.LANGUAGE_SETTINGS", Settings.ACTION_LOCALE_SETTINGS));
        systemEntries.add(new Entry(R.drawable.ic_history, 0xFF81C784, 0xFF1B5E20, R.string.date_time,
                R.string.date_time_sub, false, Settings.ACTION_DATE_SETTINGS));
        systemEntries.add(new Entry(R.drawable.ic_accessibility, 0xFF64B5F6, 0xFF0D47A1, R.string.accessibility,
                R.string.accessibility_sub, false, Settings.ACTION_ACCESSIBILITY_SETTINGS));
        systemEntries.add(new Entry(R.drawable.ic_restore, 0xFFFFB74D, 0xFFE65100, R.string.reset, R.string.reset_sub,
                false, "android.settings.RESET_DASHBOARD", "android.settings.SYSTEM_SETTINGS"));
        systemEntries.add(new Entry(R.drawable.ic_info, 0xFFBA68C8, 0xFF4A148C, R.string.about, 0, false,
                Settings.ACTION_DEVICE_INFO_SETTINGS));
        systemEntries.add(new Entry(R.drawable.ic_keyboard, 0xFF90A4AE, 0xFF37474F, R.string.developer,
                R.string.developer_sub, false, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        systemEntries.add(new Entry(R.drawable.ic_apps, 0xFF7BE41E, 0xFF1E6A06, R.string.all_settings,
                R.string.all_settings_sub, false, Settings.ACTION_SETTINGS));
    }

    /** Values that change: storage use, battery level and the LECH OS version. */
    private void refreshLive() {
        for (Entry e : entries) {
            if (e.subtitleRes != 0) e.subtitle = getString(e.subtitleRes);
        }
        for (Entry e : systemEntries) {
            if (e.subtitleRes != 0) e.subtitle = getString(e.subtitleRes);
        }
        try {
            StatFs st = new StatFs(Environment.getDataDirectory().getPath());
            long total = st.getTotalBytes();
            long used = total - st.getAvailableBytes();
            entries.get(8).subtitle = getString(R.string.storage_sub, (int) (used * 100 / Math.max(1, total)),
                    Formatter.formatShortFileSize(this, used), Formatter.formatShortFileSize(this, total));
        } catch (Exception ignored) {
            entries.get(8).subtitle = "";
        }
        Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b != null) {
            int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int status = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
            entries.get(9).subtitle = getString(R.string.battery_sub, level * 100 / Math.max(1, scale),
                    getString(charging ? R.string.battery_charging : R.string.battery_not_charging));
        }
        systemEntries.get(4).subtitle = getString(R.string.about_sub, lechVersion());
    }

    private static String lechVersion() {
        try {
            Process p = new ProcessBuilder("/system/bin/getprop", "ro.lech.version").redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String v = r.readLine();
                if (v != null && !v.trim().isEmpty()) return v.trim();
            }
        } catch (Exception ignored) {
            // Fall back to the build name.
        }
        return android.os.Build.DISPLAY;
    }

    private void render() {
        listView.removeAllViews();
        String q = plain(search.getText().toString().trim());
        boolean searching = !q.isEmpty();
        if (searching) {
            header.title.setText(R.string.app_name);
            header.subtitle.setText(R.string.subtitle);
            int found = 0;
            List<Entry> all = new ArrayList<>(entries);
            all.addAll(systemEntries);
            for (Entry e : all) {
                if (e.system) continue;
                String text = plain(getString(e.title) + " " + (e.subtitle == null ? "" : e.subtitle));
                if (text.contains(q)) {
                    listView.addView(row(e));
                    found++;
                }
            }
            if (found == 0) {
                TextView t = Ui.text(this, getString(R.string.empty_search), 17, Ui.TEXT_2, false);
                t.setGravity(Gravity.CENTER);
                t.setPadding(0, Ui.dp(this, 24), 0, Ui.dp(this, 8));
                listView.addView(t, Ui.lp(-1, -2));
            }
            final String text = search.getText().toString().trim();
            Entry s = new Entry(R.drawable.ic_search, 0xFF7BE41E, 0xFF1E6A06, R.string.search_system, 0, false);
            s.subtitle = text;
            View r = row(s);
            r.setOnClickListener(v -> {
                Intent i = new Intent("android.settings.APP_SEARCH_SETTINGS");
                if (!tryStart(i)) {
                    i = new Intent("com.android.settings.action.SETTINGS_SEARCH");
                    i.putExtra(android.app.SearchManager.QUERY, text);
                    if (!tryStart(i)) tryStart(new Intent(Settings.ACTION_SETTINGS));
                }
            });
            listView.addView(r);
            return;
        }
        if (inSystem) {
            header.title.setText(R.string.system);
            header.subtitle.setText(R.string.subtitle);
            LinearLayout back = Ui.row(this);
            back.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 8));
            Ui.clickable(back, null);
            back.addView(Ui.icon(this, R.drawable.ic_chevron_left, Ui.GREEN, 28));
            back.addView(Ui.text(this, getString(R.string.back), 17, Ui.GREEN, true));
            back.setOnClickListener(v -> {
                inSystem = false;
                render();
            });
            listView.addView(back, Ui.lp(-2, -2));
            for (Entry e : systemEntries) listView.addView(row(e));
        } else {
            header.title.setText(R.string.app_name);
            header.subtitle.setText(R.string.subtitle);
            for (Entry e : entries) listView.addView(row(e));
        }
    }

    private View row(Entry e) {
        LinearLayout r = Ui.row(this);
        r.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xCC0F3340, 0xCC07202B});
        bg.setCornerRadius(Ui.dp(this, 30));
        bg.setStroke(Ui.dp(this, 1.2f), 0xAA3FA36A);
        Ui.clickable(r, bg);
        FrameLayout tile = new FrameLayout(this);
        GradientDrawable tb = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {e.top, e.bottom});
        tb.setCornerRadius(Ui.dp(this, 16));
        tb.setStroke(Ui.dp(this, 1), 0x66FFFFFF);
        tile.setBackground(tb);
        tile.addView(Ui.icon(this, e.icon, Ui.TEXT, 30), new FrameLayout.LayoutParams(Ui.dp(this, 30),
                Ui.dp(this, 30), Gravity.CENTER));
        tile.setElevation(Ui.dp(this, 3));
        r.addView(tile, Ui.lp(Ui.dp(this, 54), Ui.dp(this, 54)));
        LinearLayout t = Ui.column(this);
        t.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 8), 0);
        t.addView(Ui.text(this, getString(e.title), 18, Ui.TEXT, true));
        if (e.subtitle != null && e.subtitle.length() > 0) {
            TextView s = Ui.text(this, e.subtitle, 14, Ui.TEXT_2, false);
            s.setMaxLines(2);
            t.addView(s);
        }
        r.addView(t, Ui.lp(0, -2, 1));
        r.addView(Ui.icon(this, R.drawable.ic_chevron_right, Ui.TEXT, 28));
        r.setOnClickListener(v -> open(e));
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.bottomMargin = Ui.dp(this, 9);
        r.setLayoutParams(lp);
        return r;
    }

    private void open(Entry e) {
        if (e.system) {
            inSystem = true;
            render();
            scroll.scrollTo(0, 0);
            return;
        }
        for (String a : e.actions) {
            if (tryStart(new Intent(a))) return;
        }
        Toast.makeText(this, R.string.no_screen, Toast.LENGTH_SHORT).show();
    }

    private boolean tryStart(Intent i) {
        try {
            startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException ex) {
            return false;
        }
    }

    private static String plain(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
