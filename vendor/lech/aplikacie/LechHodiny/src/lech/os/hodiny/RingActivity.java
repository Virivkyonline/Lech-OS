/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.hodiny;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import lech.os.ui.Ui;

import java.util.Date;

/** The ringing screen, also over the lock screen: time, name, turn off and snooze. */
public class RingActivity extends Activity {
    static final String ACTION_FINISH = "lech.os.hodiny.FINISH_RING";

    private final BroadcastReceiver finish = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        boolean timer = getIntent().getBooleanExtra(Ringer.EXTRA_TIMER, false);
        String label = getIntent().getStringExtra(Ringer.EXTRA_LABEL);

        LinearLayout root = Ui.column(this);
        root.setBackground(Ui.background());
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.Header h = Ui.header(this, R.mipmap.ic_hodiny);
        h.title.setText(timer ? R.string.timer_done : R.string.alarm_title);
        h.subtitle.setText(label == null ? "" : label);
        root.addView(h.view, Ui.lp(-1, -2));

        TextView time = Ui.text(this, DateFormat.getTimeFormat(this).format(new Date()), 88, Ui.TEXT, false);
        time.setGravity(Gravity.CENTER);
        root.addView(time, Ui.margins(Ui.lp(-1, -2), this, 0, 40, 0, 0));
        TextView l = Ui.text(this, label == null ? "" : label, 22, Ui.TEXT_2, false);
        l.setGravity(Gravity.CENTER);
        root.addView(l, Ui.lp(-1, -2));

        root.addView(new android.view.View(this), Ui.lp(-1, 0, 1));
        if (!timer) {
            LinearLayout snooze = Ui.pillButton(this, R.drawable.ic_alarm, getString(R.string.snooze), v -> {
                Ringer.snooze(this);
                finish();
            });
            LinearLayout.LayoutParams sl = Ui.margins(Ui.lp(-2, -2), this, 0, 0, 0, 18);
            sl.gravity = Gravity.CENTER_HORIZONTAL;
            root.addView(snooze, sl);
        }
        android.view.View off = Ui.roundButton(this, R.drawable.ic_close, true, 96, getString(R.string.dismiss), v -> {
            Ringer.stop(this);
            finish();
        });
        LinearLayout.LayoutParams ol = Ui.margins(Ui.lp(Ui.dp(this, 96), Ui.dp(this, 96)), this, 0, 0, 0, 12);
        ol.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(off, ol);
        TextView offText = Ui.text(this, getString(R.string.dismiss), 18, Ui.TEXT, true);
        offText.setGravity(Gravity.CENTER);
        root.addView(offText, Ui.margins(Ui.lp(-1, -2), this, 0, 0, 0, 48));

        Ui.edgeToEdge(this, root);
        setContentView(root);
        registerReceiver(finish, new IntentFilter(ACTION_FINISH), Context.RECEIVER_NOT_EXPORTED);
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> { });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(finish);
    }
}
