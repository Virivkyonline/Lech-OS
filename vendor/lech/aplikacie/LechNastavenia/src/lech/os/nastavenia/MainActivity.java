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
import android.text.TextUtils;
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
        boolean gap;
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

    private Entry add(int icon, int title, int subtitle, boolean gap, String... actions) {
        Entry e = new Entry(icon, 0, 0, title, subtitle, false, actions);
        e.gap = gap;
        entries.add(e);
        return e;
    }

    /**
     * The list of the Android settings (the same items, order and groups), with the LECH OS icons. Screens are the
     * activities of the Settings manifest of Android 17 (GrapheneOS platform_packages_apps_Settings, branch 17).
     */
    private void define() {
        add(R.drawable.ic_set_siet, R.string.network, R.string.network_sub, false, "cmp:com.android.settings/com.android.settings.Settings$NetworkDashboardActivity");
        // SIM card lock (SIM PIN): Settings$IccLockSettingsActivity (exported, fragment IccLockSettings). In Android it is
        // under Security & privacy > More security & privacy, where the owner did not find it (12. 11. 2026).
        add(R.drawable.ic_set_sim, R.string.sim, R.string.sim_sub, false, "cmp:com.android.settings/com.android.settings.Settings$IccLockSettingsActivity");
        add(R.drawable.ic_set_pripojene_zariadenia, R.string.devices, R.string.devices_sub, false, "cmp:com.android.settings/com.android.settings.Settings$ConnectedDeviceDashboardActivity");
        add(R.drawable.ic_set_aplikacie, R.string.apps, R.string.apps_sub, true, "cmp:com.android.settings/com.android.settings.Settings$AppDashboardActivity");
        add(R.drawable.ic_set_upozornenia, R.string.notifications, R.string.notifications_sub, false,
                "android.settings.NOTIFICATION_SETTINGS");
        add(R.drawable.ic_set_zvuk, R.string.sound, R.string.sound_sub, false, Settings.ACTION_SOUND_SETTINGS);
        // Settings$ModesSettingsActivity (the list of modes) answers ZEN_MODE_SETTINGS.
        add(R.drawable.ic_set_rezimy, R.string.modes, R.string.modes_sub, false, "android.settings.ZEN_MODE_SETTINGS");
        add(R.drawable.ic_set_obrazovka, R.string.display, R.string.display_sub, false, Settings.ACTION_DISPLAY_SETTINGS);
        add(R.drawable.ic_set_tapeta, R.string.wallpaper, R.string.wallpaper_sub, false,
                "android.settings.WALLPAPER_SETTINGS");
        add(R.drawable.ic_set_ulozisko, R.string.storage, 0, true, Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
        add(R.drawable.ic_set_bateria, R.string.battery, 0, false, Intent.ACTION_POWER_USAGE_SUMMARY);
        add(R.drawable.ic_set_system, R.string.system, R.string.system_sub, false, "cmp:com.android.settings/com.android.settings.Settings$SystemDashboardActivity");
        add(R.drawable.ic_set_informacie, R.string.about, 0, false, Settings.ACTION_DEVICE_INFO_SETTINGS);
        add(R.drawable.ic_set_sukromie, R.string.security, R.string.security_sub, true,
                Settings.ACTION_SECURITY_SETTINGS);
        add(R.drawable.ic_set_poloha, R.string.location, 0, false, Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        // "Passwords, passkeys & accounts" of Android 17 is Settings$AccountDashboardActivity.
        add(R.drawable.ic_set_hesla, R.string.passwords, R.string.passwords_sub, false, "cmp:com.android.settings/com.android.settings.Settings$AccountDashboardActivity");
        add(R.drawable.ic_set_ucty, R.string.accounts, R.string.accounts_sub, false, "cmp:com.android.settings/com.android.settings.Settings$AccountsAndBackupDashboardActivity");
        add(R.drawable.ic_set_tiesen, R.string.emergency, R.string.emergency_sub, false,
                "android.settings.EMERGENCY_SETTINGS");
        add(R.drawable.ic_set_dostupnost, R.string.accessibility, R.string.accessibility_sub, true,
                Settings.ACTION_ACCESSIBILITY_SETTINGS);
    }

    /** Values that change: storage use, battery level and the LECH OS version. */
    private void refreshLive() {
        for (Entry e : entries) {
            if (e.subtitleRes != 0) e.subtitle = getString(e.subtitleRes);
        }
        for (Entry e : entries) {
            if (e.title == R.string.storage) {
                try {
                    StatFs st = new StatFs(Environment.getDataDirectory().getPath());
                    long total = st.getTotalBytes();
                    long free = st.getAvailableBytes();
                    e.subtitle = getString(R.string.storage_sub, (int) ((total - free) * 100 / Math.max(1, total)),
                            Formatter.formatShortFileSize(this, free));
                } catch (Exception ignored) {
                    e.subtitle = "";
                }
            } else if (e.title == R.string.battery) {
                Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (b != null) {
                    int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100
                            / Math.max(1, b.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
                    int status = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    e.subtitle = status == BatteryManager.BATTERY_STATUS_FULL ? getString(R.string.battery_full)
                            : status == BatteryManager.BATTERY_STATUS_CHARGING
                            ? level + " % · " + getString(R.string.battery_charging) : level + " %";
                }
            } else if (e.title == R.string.about) {
                String name = Settings.Global.getString(getContentResolver(), Settings.Global.DEVICE_NAME);
                e.subtitle = TextUtils.isEmpty(name) ? android.os.Build.MODEL : name;
            } else if (e.title == R.string.location) {
                boolean on = getSystemService(android.location.LocationManager.class).isLocationEnabled();
                e.subtitle = getString(on ? R.string.on : R.string.off);
            }
        }
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
                // The settings search of SettingsIntelligence (APP_SEARCH_SETTINGS); without it a message.
                Intent i = new Intent("android.settings.APP_SEARCH_SETTINGS");
                i.putExtra(android.app.SearchManager.QUERY, text);
                if (!tryStart(i)) Toast.makeText(this, R.string.no_screen, Toast.LENGTH_SHORT).show();
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
            for (Entry e : entries) {
                View v = row(e);
                if (e.gap) ((LinearLayout.LayoutParams) v.getLayoutParams()).topMargin = Ui.dp(this, 14);
                listView.addView(v);
            }
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
        if (e.top == 0 && e.bottom == 0) {
            // A finished icon of the LECH OS design (branding/ikony/nastavenia).
            ImageView pic = new ImageView(this);
            pic.setImageResource(e.icon);
            pic.setElevation(Ui.dp(this, 3));
            r.addView(pic, Ui.lp(Ui.dp(this, 56), Ui.dp(this, 56)));
        } else {
            r.addView(Rows.tile(this, e.icon, e.top, e.bottom), Ui.lp(Ui.dp(this, 54), Ui.dp(this, 54)));
        }
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
            Intent i = a.startsWith("cmp:") ? new Intent().setComponent(
                    android.content.ComponentName.unflattenFromString(a.substring(4))) : new Intent(a);
            if (tryStart(i)) return;
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
