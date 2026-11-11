/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.nastavenia;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Toast;

import lech.os.ui.Ui;

import java.util.Locale;

/**
 * A submenu of the settings in the LECH OS design (header with the mountains, the rows of the main menu). The most
 * used settings change here; "More settings" opens the matching screen of the Android settings.
 */
public class SubActivity extends Activity {
    static final String EXTRA_PAGE = "page";
    static final String NETWORK = "network";
    static final String SOUND = "sound";
    static final String DISPLAY = "display";
    static final String BATTERY = "battery";

    private String page;
    private LinearLayout list;
    private boolean building;

    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (!building) build();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        page = getIntent().getStringExtra(EXTRA_PAGE);
        if (page == null) page = NETWORK;
        LinearLayout root = Ui.column(this);
        root.setBackground(Ui.background());
        Ui.Header h = Ui.header(this, R.mipmap.ic_nastavenia);
        Ui.headerButton(h, R.drawable.ic_back, false, getString(R.string.back), v -> finish());
        int title;
        int subtitle;
        switch (page) {
            case SOUND: title = R.string.sound; subtitle = R.string.sound_sub; break;
            case DISPLAY: title = R.string.display; subtitle = R.string.display_sub; break;
            case BATTERY: title = R.string.battery; subtitle = R.string.battery_head; break;
            default: title = R.string.network; subtitle = R.string.network_sub; break;
        }
        h.title.setText(title);
        h.subtitle.setText(subtitle);
        root.addView(h.view, Ui.lp(-1, -2));
        ScrollView sc = new ScrollView(this);
        list = Ui.column(this);
        list.setPadding(Ui.dp(this, 14), Ui.dp(this, 4), Ui.dp(this, 14), Ui.dp(this, 24));
        sc.addView(list, Ui.lp(-1, -2));
        root.addView(sc, Ui.lp(-1, 0, 1));
        Ui.edgeToEdge(this, root);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_BATTERY_CHANGED);
        f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        f.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        f.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        f.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        registerReceiver(changes, f, Context.RECEIVER_EXPORTED);
        build();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(changes);
    }

    private void build() {
        building = true;
        list.removeAllViews();
        switch (page) {
            case SOUND: sound(); break;
            case DISPLAY: display(); break;
            case BATTERY: battery(); break;
            default: network(); break;
        }
        building = false;
    }

    private void open(String... actions) {
        for (String a : actions) {
            try {
                startActivity(new Intent(a));
                return;
            } catch (Exception ignored) {
                // Try the next screen.
            }
        }
        Toast.makeText(this, R.string.no_screen, Toast.LENGTH_SHORT).show();
    }

    private LinearLayout openRow(int icon, int top, int bottom, int title, CharSequence sub, String... actions) {
        LinearLayout r = Rows.row(this, icon, top, bottom, getString(title), sub);
        Rows.arrow(r);
        r.setOnClickListener(v -> open(actions));
        list.addView(r);
        return r;
    }

    private void more(int title, int sub, String... actions) {
        openRow(R.drawable.ic_settings, 0xFF7BE41E, 0xFF1E6A06, title, getString(sub), actions);
    }

    // ---- Network and internet ----

    private void network() {
        WifiManager wifi = getSystemService(WifiManager.class);
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
        boolean onWifi = nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        boolean onCell = nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
        boolean wifiOn = wifi.isWifiEnabled();

        LinearLayout w = Rows.row(this, R.drawable.ic_wifi, 0xFF42A5F5, 0xFF0D47A1, getString(R.string.wifi),
                getString(!wifiOn ? R.string.off : onWifi ? R.string.wifi_connected : R.string.wifi_on));
        Rows.toggle(w, wifiOn, (b, on) -> {
            @SuppressWarnings("deprecation")
            boolean ok = wifi.setWifiEnabled(on);
            if (!ok) open(Settings.Panel.ACTION_WIFI, Settings.ACTION_WIFI_SETTINGS);
        });
        list.addView(w);
        openRow(R.drawable.ic_wifi, 0xFF4FC3F7, 0xFF01579B, R.string.wifi_networks,
                getString(R.string.wifi_networks_sub), Settings.ACTION_WIFI_SETTINGS);

        TelephonyManager tm = getSystemService(TelephonyManager.class);
        boolean data;
        try {
            data = tm.isDataEnabled();
        } catch (Exception e) {
            data = onCell;
        }
        openRow(R.drawable.ic_phone_android, 0xFF66BB6A, 0xFF1B5E20, R.string.mobile_data,
                getString(data ? (onCell ? R.string.data_in_use : R.string.on) : R.string.off),
                Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Settings.ACTION_DATA_ROAMING_SETTINGS);
        openRow(R.drawable.ic_share, 0xFF26C6DA, 0xFF006064, R.string.hotspot, getString(R.string.hotspot_sub),
                "android.settings.TETHER_SETTINGS", Settings.ACTION_WIRELESS_SETTINGS);
        boolean airplane = Settings.Global.getInt(getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        openRow(R.drawable.ic_send, 0xFFBA68C8, 0xFF4A148C, R.string.airplane,
                getString(airplane ? R.string.on : R.string.off), Settings.ACTION_AIRPLANE_MODE_SETTINGS);
        openRow(R.drawable.ic_shield, 0xFF4FC3F7, 0xFF01579B, R.string.vpn, getString(R.string.vpn_sub),
                Settings.ACTION_VPN_SETTINGS);
        more(R.string.more_network, R.string.more_network_sub, Settings.ACTION_WIRELESS_SETTINGS);
    }

    // ---- Sound and vibration ----

    private void sound() {
        AudioManager am = getSystemService(AudioManager.class);
        int mode = am.getRingerMode();
        int sel = mode == AudioManager.RINGER_MODE_NORMAL ? 0 : mode == AudioManager.RINGER_MODE_VIBRATE ? 1 : 2;
        list.addView(Rows.segments(this, new String[] {getString(R.string.ring), getString(R.string.vibrate),
            getString(R.string.silent)}, sel, idx -> {
                int m = idx == 0 ? AudioManager.RINGER_MODE_NORMAL : idx == 1 ? AudioManager.RINGER_MODE_VIBRATE
                        : AudioManager.RINGER_MODE_SILENT;
                try {
                    am.setRingerMode(m);
                } catch (SecurityException e) {
                    // Silent needs the Do Not Disturb access of the app.
                    Toast.makeText(this, R.string.dnd_access, Toast.LENGTH_LONG).show();
                    open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                }
                build();
            }));
        volume(am, AudioManager.STREAM_RING, R.drawable.ic_bell, 0xFFFFB74D, 0xFFE65100, R.string.vol_ring);
        volume(am, AudioManager.STREAM_MUSIC, R.drawable.ic_music, 0xFFCE93D8, 0xFF6A1B9A, R.string.vol_media);
        volume(am, AudioManager.STREAM_ALARM, R.drawable.ic_alarm, 0xFF66BB6A, 0xFF1B5E20, R.string.vol_alarm);
        volume(am, AudioManager.STREAM_VOICE_CALL, R.drawable.ic_call, 0xFF42A5F5, 0xFF0D47A1, R.string.vol_call);
        systemToggle(R.drawable.ic_volume, 0xFFFFB74D, 0xFFE65100, R.string.vibrate_ring, Settings.System.VIBRATE_WHEN_RINGING);
        systemToggle(R.drawable.ic_dialpad, 0xFF90A4AE, 0xFF37474F, R.string.dial_tones,
                Settings.System.DTMF_TONE_WHEN_DIALING);
        systemToggle(R.drawable.ic_check_circle, 0xFF90A4AE, 0xFF37474F, R.string.touch_sounds,
                Settings.System.SOUND_EFFECTS_ENABLED);
        openRow(R.drawable.ic_music, 0xFFFFB74D, 0xFFE65100, R.string.ringtone, getString(R.string.ringtone_sub),
                Settings.ACTION_SOUND_SETTINGS);
        more(R.string.more_sound, R.string.more_sound_sub, Settings.ACTION_SOUND_SETTINGS);
    }

    private void volume(AudioManager am, int stream, int icon, int top, int bottom, int title) {
        int max = am.getStreamMaxVolume(stream);
        int min = am.getStreamMinVolume(stream);
        int cur = am.getStreamVolume(stream);
        LinearLayout r = Rows.row(this, icon, top, bottom, getString(title), percent(cur, max));
        Rows.slider(r, max - min, cur - min, new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (!user) return;
                try {
                    am.setStreamVolume(stream, p + min, 0);
                } catch (SecurityException e) {
                    Toast.makeText(SubActivity.this, R.string.dnd_access, Toast.LENGTH_SHORT).show();
                }
                Rows.setSubtitle(r, percent(am.getStreamVolume(stream), max));
            }

            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        list.addView(r);
    }

    private static String percent(int v, int max) {
        return String.format(Locale.getDefault(), "%d %%", max == 0 ? 0 : Math.round(v * 100f / max));
    }

    /** A switch of a system setting (Settings.System, needs the "modify system settings" access). */
    private void systemToggle(int icon, int top, int bottom, int title, String key) {
        boolean on = Settings.System.getInt(getContentResolver(), key, 0) != 0;
        LinearLayout r = Rows.row(this, icon, top, bottom, getString(title), null);
        Rows.toggle(r, on, (b, value) -> {
            if (!writeSystem(key, value ? 1 : 0)) b.setChecked(!value);
        });
        list.addView(r);
    }

    private boolean writeSystem(String key, int value) {
        if (!Settings.System.canWrite(this)) {
            Toast.makeText(this, R.string.write_access, Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.fromParts("package", getPackageName(), null)));
            } catch (Exception ignored) {
                // No such screen.
            }
            return false;
        }
        try {
            return Settings.System.putInt(getContentResolver(), key, value);
        } catch (Exception e) {
            Toast.makeText(this, R.string.no_screen, Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    // ---- Display ----

    private void display() {
        int brightness = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
        LinearLayout b = Rows.row(this, R.drawable.ic_sun, 0xFFFFD54F, 0xFFB28704, getString(R.string.brightness),
                percent(brightness, 255));
        Rows.slider(b, 255, brightness, new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (!user) return;
                android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.screenBrightness = Math.max(p, 4) / 255f;
                getWindow().setAttributes(lp);
                Rows.setSubtitle(b, percent(p, 255));
            }

            @Override public void onStartTrackingTouch(SeekBar s) { }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                if (writeSystem(Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)) {
                    writeSystem(Settings.System.SCREEN_BRIGHTNESS, Math.max(s.getProgress(), 4));
                }
                android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
                getWindow().setAttributes(lp);
                build();
            }
        });
        list.addView(b);
        boolean auto = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
                == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
        LinearLayout a = Rows.row(this, R.drawable.ic_sun, 0xFFFFB74D, 0xFFE65100, getString(R.string.auto_brightness),
                getString(R.string.auto_brightness_sub));
        Rows.toggle(a, auto, (s, on) -> {
            if (!writeSystem(Settings.System.SCREEN_BRIGHTNESS_MODE, on ? Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                    : Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)) {
                s.setChecked(!on);
            }
        });
        list.addView(a);
        boolean dark = getSystemService(UiModeManager.class).getNightMode() == UiModeManager.MODE_NIGHT_YES;
        openRow(R.drawable.ic_dnd, 0xFF7C4DFF, 0xFF311B92, R.string.dark_mode,
                getString(dark ? R.string.on : R.string.off), "android.settings.DARK_THEME_SETTINGS",
                Settings.ACTION_DISPLAY_SETTINGS);
        int timeout = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, 60_000);
        LinearLayout t = Rows.row(this, R.drawable.ic_history, 0xFF4FC3F7, 0xFF01579B, getString(R.string.screen_timeout),
                timeoutName(timeout));
        Rows.arrow(t);
        t.setOnClickListener(v -> pickTimeout());
        list.addView(t);
        systemToggle(R.drawable.ic_flip_camera, 0xFF66BB6A, 0xFF1B5E20, R.string.auto_rotate,
                Settings.System.ACCELEROMETER_ROTATION);
        openRow(R.drawable.ic_keyboard, 0xFF90A4AE, 0xFF37474F, R.string.font_size, getString(R.string.font_size_sub),
                "android.settings.TEXT_READING_SETTINGS", Settings.ACTION_DISPLAY_SETTINGS);
        more(R.string.more_display, R.string.more_display_sub, Settings.ACTION_DISPLAY_SETTINGS);
    }

    private static final int[] TIMEOUTS = {15_000, 30_000, 60_000, 120_000, 300_000, 600_000, 1_800_000};

    private String timeoutName(int ms) {
        if (ms < 60_000) return getString(R.string.seconds, ms / 1000);
        return getString(R.string.minutes, ms / 60_000);
    }

    private void pickTimeout() {
        String[] names = new String[TIMEOUTS.length];
        for (int k = 0; k < TIMEOUTS.length; k++) names[k] = timeoutName(TIMEOUTS[k]);
        new android.app.AlertDialog.Builder(this).setTitle(R.string.screen_timeout)
                .setItems(names, (d, w) -> {
                    writeSystem(Settings.System.SCREEN_OFF_TIMEOUT, TIMEOUTS[w]);
                    build();
                }).show();
    }

    // ---- Battery ----

    private void battery() {
        Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = 0;
        boolean charging = false;
        String health = "";
        float temp = 0;
        int cycles = -1;
        if (b != null) {
            level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / Math.max(1, b.getIntExtra(
                    BatteryManager.EXTRA_SCALE, 100));
            int st = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
            int h = b.getIntExtra(BatteryManager.EXTRA_HEALTH, 0);
            health = getString(h == BatteryManager.BATTERY_HEALTH_GOOD ? R.string.health_good
                    : h == BatteryManager.BATTERY_HEALTH_OVERHEAT ? R.string.health_hot
                    : h == BatteryManager.BATTERY_HEALTH_DEAD ? R.string.health_bad : R.string.health_unknown);
            temp = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
            cycles = b.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1);
        }
        BatteryManager bm = getSystemService(BatteryManager.class);
        String status;
        if (charging) {
            long left = bm.computeChargeTimeRemaining();
            status = left > 0 ? getString(R.string.charging_left, left / 3_600_000L, (left / 60_000L) % 60)
                    : getString(R.string.charging);
        } else {
            status = getString(R.string.not_charging);
        }
        LinearLayout big = Rows.row(this, R.drawable.ic_battery, charging ? 0xFF9CFF3A : 0xFF66BB6A, 0xFF1B5E20,
                level + " %", status);
        ((android.widget.TextView) ((LinearLayout) big.getChildAt(1)).getChildAt(0)).setTextSize(30);
        list.addView(big);
        StringBuilder info = new StringBuilder(getString(R.string.health, health));
        info.append(" · ").append(String.format(Locale.getDefault(), "%.1f °C", temp));
        if (cycles >= 0) info.append(" · ").append(getString(R.string.cycles, cycles));
        LinearLayout hRow = Rows.row(this, R.drawable.ic_info, 0xFF4FC3F7, 0xFF01579B, getString(R.string.battery_state),
                info);
        list.addView(hRow);
        boolean saver = getSystemService(PowerManager.class).isPowerSaveMode();
        openRow(R.drawable.ic_battery, 0xFFFFB74D, 0xFFE65100, R.string.saver,
                getString(saver ? R.string.on : R.string.off), Settings.ACTION_BATTERY_SAVER_SETTINGS);
        openRow(R.drawable.ic_apps, 0xFF9575CD, 0xFF4527A0, R.string.usage, getString(R.string.usage_sub),
                Intent.ACTION_POWER_USAGE_SUMMARY);
        more(R.string.more_battery, R.string.more_battery_sub, Intent.ACTION_POWER_USAGE_SUMMARY);
    }
}
