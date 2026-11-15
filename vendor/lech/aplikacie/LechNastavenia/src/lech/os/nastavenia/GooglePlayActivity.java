/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package lech.os.nastavenia;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import lech.os.ui.Ui;

/**
 * "Apps from Google Play" (docs/18-google-play-v-sandboxe.md): LECH OS has no Google services. The official way to
 * apps that need them is the sandboxed Google Play of the GrapheneOS base (ADR-0012, "Google zone"): the App Store
 * of the base, hidden from the home screen, installs Google Play services and the Play Store as ordinary apps. The
 * button opens its page of Google Play services; the user installs it there himself. Third-party clients of Google
 * Play are only mentioned (owner, 14. 11. 2026: not preinstalled, no link, the choice is the user's).
 */
public class GooglePlayActivity extends Activity {
    /** App Store of the base; activity and SHOW_APP_INFO filter verified in GrapheneOS/AppStore AndroidManifest.xml. */
    static final String STORE = "app.grapheneos.apps";
    private static final String STORE_MAIN = "app.grapheneos.apps.ui.MainActivity";
    private static final String PLAY_SERVICES = "com.google.android.gms";

    private TextView state;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root = Ui.column(this);
        root.setBackground(Ui.background());
        Ui.Header header = Ui.header(this, R.mipmap.ic_nastavenia);
        header.title.setText(R.string.gplay_title);
        header.subtitle.setText(R.string.subtitle);
        root.addView(header.view, Ui.lp(-1, -2));

        ScrollView scroll = new ScrollView(this);
        LinearLayout page = Ui.column(this);
        int pad = Ui.dp(this, 20);
        page.setPadding(pad, Ui.dp(this, 4), pad, pad);

        page.addView(paragraph(R.string.gplay_intro, Ui.TEXT));
        page.addView(heading(R.string.gplay_zone_title));
        page.addView(paragraph(R.string.gplay_zone_text, Ui.TEXT_2));
        state = Ui.text(this, "", 16, Ui.GREEN, true);
        state.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 4));
        page.addView(state, Ui.lp(-1, -2));
        LinearLayout button = Ui.pillButton(this, R.drawable.ic_shield, getString(R.string.gplay_zone_button),
                v -> openStore());
        LinearLayout.LayoutParams lp = Ui.lp(-2, -2);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = Ui.dp(this, 10);
        page.addView(button, lp);
        page.addView(paragraph(R.string.gplay_zone_sees, Ui.TEXT_2));
        // Settings of the sandboxed Google Play of the base (GmsCompat): Android Auto permissions, location
        // rerouting, links to the Play services settings.
        page.addView(heading(R.string.gplay_auto_title));
        page.addView(paragraph(R.string.gplay_auto_text, Ui.TEXT_2));
        LinearLayout zone = Ui.pillButton(this, R.drawable.ic_settings, getString(R.string.gplay_auto_button),
                v -> openZoneSettings());
        LinearLayout.LayoutParams zlp = Ui.lp(-2, -2);
        zlp.gravity = Gravity.CENTER_HORIZONTAL;
        zlp.topMargin = Ui.dp(this, 10);
        page.addView(zone, zlp);
        page.addView(heading(R.string.gplay_other_title));
        page.addView(paragraph(R.string.gplay_other_text, Ui.TEXT_2));

        scroll.addView(page, Ui.lp(-1, -2));
        root.addView(scroll, Ui.lp(-1, 0, 1));
        Ui.edgeToEdge(this, root);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        state.setText(installed(this, PLAY_SERVICES) ? R.string.gplay_state_on : R.string.gplay_state_off);
    }

    static boolean installed(Activity a, String pkg) {
        try {
            a.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private TextView heading(int text) {
        TextView t = Ui.text(this, getString(text), 19, Ui.TEXT, true);
        t.setPadding(0, Ui.dp(this, 22), 0, Ui.dp(this, 6));
        return t;
    }

    private TextView paragraph(int text, int color) {
        TextView t = Ui.text(this, getString(text), 16, color, false);
        t.setLineSpacing(Ui.dp(this, 3), 1f);
        t.setPadding(0, Ui.dp(this, 8), 0, 0);
        return t;
    }

    /**
     * The settings of the zone (app.grapheneos.gmscompat/.MainActivity) need the signature|privileged permission
     * app.grapheneos.gmscompat.SHOW_UI, which this app does not hold (seen on the phone 15. 11. 2026). The Apps
     * screen of the system settings holds the entry that opens them (res/xml/apps.xml, key sandboxed_google_play),
     * so the button opens that screen: com.android.settings/.Settings$AppDashboardActivity (exported, verified in
     * the Settings manifest of the base).
     */
    private void openZoneSettings() {
        Intent i = new Intent().setComponent(
                new ComponentName("com.android.settings", "com.android.settings.Settings$AppDashboardActivity"));
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.no_screen, Toast.LENGTH_SHORT).show();
        }
    }

    /** The page of Google Play services in the App Store of the base, else its main screen, else a message. */
    private void openStore() {
        Intent info = new Intent(Intent.ACTION_SHOW_APP_INFO).setPackage(STORE)
                .putExtra(Intent.EXTRA_PACKAGE_NAME, PLAY_SERVICES);
        Intent main = new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(STORE, STORE_MAIN));
        for (Intent i : new Intent[] {info, main}) {
            try {
                startActivity(i);
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // Try the next one.
            }
        }
        Toast.makeText(this, R.string.no_screen, Toast.LENGTH_SHORT).show();
    }
}
