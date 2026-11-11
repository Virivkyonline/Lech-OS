/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.hodiny;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Alarm or timer time: starts the ringing; after a restart or a time change: schedules the alarms again. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (Alarms.ACTION_ALARM.equals(a)) {
            int id = i.getIntExtra(Alarms.EXTRA_ID, -1);
            Alarms.Alarm al = Alarms.find(Alarms.load(c), id);
            if (al == null || !al.enabled) {
                Alarms.schedule(c);
                return;
            }
            Alarms.rang(c, id);
            Ringer.start(c, false, al.label, id);
        } else if (Alarms.ACTION_TIMER.equals(a)) {
            Alarms.resetTimer(c);
            Ringer.start(c, true, null, -1);
        } else {
            Alarms.schedule(c);
        }
    }
}
