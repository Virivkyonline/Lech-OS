/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.spravy;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

/** "Respond via message" when a call is declined: sends the text to the caller. */
public class RespondService extends Service {
    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        if (i != null && TelephonyManager.ACTION_RESPOND_VIA_MESSAGE.equals(i.getAction())) {
            String text = i.getStringExtra(Intent.EXTRA_TEXT);
            Uri data = i.getData();
            if (data != null && !TextUtils.isEmpty(text)) {
                String to = data.getSchemeSpecificPart();
                new Thread(() -> {
                    for (String a : to.split("[,;]")) {
                        if (!a.trim().isEmpty()) Store.send(this, a.trim(), text, -1);
                    }
                    stopSelf(startId);
                }).start();
                return START_NOT_STICKY;
            }
        }
        stopSelf(startId);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
