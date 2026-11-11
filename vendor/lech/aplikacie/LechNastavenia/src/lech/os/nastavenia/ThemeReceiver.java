/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.nastavenia;

import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;

/**
 * The look of LECH OS for the whole system, set once (the user can change it later in Settings): the dark theme
 * and the LECH green as the Material You colour of the system (Settings, quick settings, dialogs, keyboard).
 */
public class ThemeReceiver extends BroadcastReceiver {
    private static final int VERSION = 1;
    private static final String THEME_KEY = "theme_customization_overlay_packages";
    private static final String LECH_THEME = "{"
            + "\"android.theme.customization.color_source\":\"preset\","
            + "\"android.theme.customization.theme_style\":\"VIBRANT\","
            + "\"android.theme.customization.system_palette\":\"7BE41E\","
            + "\"android.theme.customization.accent_color\":\"7BE41E\"}";

    @Override
    public void onReceive(Context c, Intent i) {
        apply(c);
    }

    static void apply(Context c) {
        SharedPreferences p = c.getSharedPreferences("lech_theme", Context.MODE_PRIVATE);
        if (p.getInt("applied", 0) >= VERSION) return;
        boolean ok = true;
        try {
            Settings.Secure.putString(c.getContentResolver(), THEME_KEY, LECH_THEME);
        } catch (SecurityException e) {
            ok = false;
        }
        try {
            c.getSystemService(UiModeManager.class).setNightMode(UiModeManager.MODE_NIGHT_YES);
        } catch (SecurityException e) {
            ok = false;
        }
        if (ok) p.edit().putInt("applied", VERSION).apply();
    }
}
