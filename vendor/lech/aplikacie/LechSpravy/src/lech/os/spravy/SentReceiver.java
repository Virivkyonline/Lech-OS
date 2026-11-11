/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.spravy;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;

/** The result of sending one part of an SMS: the message moves from the outbox to sent or failed. */
public class SentReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (i.getData() == null) return;
        boolean ok = getResultCode() == Activity.RESULT_OK;
        if (!ok) {
            Store.setType(c, i.getData(), Telephony.Sms.MESSAGE_TYPE_FAILED);
        } else if (i.getBooleanExtra("last", true)) {
            Store.setType(c, i.getData(), Telephony.Sms.MESSAGE_TYPE_SENT);
        }
    }
}
