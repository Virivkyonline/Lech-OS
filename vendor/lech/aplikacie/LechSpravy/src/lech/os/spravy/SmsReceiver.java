/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.spravy;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.SmsMessage;

/** A received SMS (as the default SMS app): stored in the inbox and shown as a notification. Blocked: dropped. */
public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (!Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(i.getAction())) return;
        SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(i);
        if (parts == null || parts.length == 0) return;
        StringBuilder body = new StringBuilder();
        for (SmsMessage p : parts) {
            if (p != null && p.getMessageBody() != null) body.append(p.getMessageBody());
        }
        String address = parts[0].getOriginatingAddress();
        if (address != null && Store.isBlocked(c, address)) return;
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                ContentValues v = new ContentValues();
                v.put(Telephony.Sms.ADDRESS, address);
                v.put(Telephony.Sms.BODY, body.toString());
                v.put(Telephony.Sms.DATE, System.currentTimeMillis());
                v.put(Telephony.Sms.DATE_SENT, parts[0].getTimestampMillis());
                v.put(Telephony.Sms.READ, 0);
                v.put(Telephony.Sms.SEEN, 0);
                v.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX);
                int sub = i.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1);
                if (sub >= 0) v.put(Telephony.Sms.SUBSCRIPTION_ID, sub);
                long thread = Store.threadFor(c, address);
                v.put(Telephony.Sms.THREAD_ID, thread);
                Uri u = c.getContentResolver().insert(Telephony.Sms.Inbox.CONTENT_URI, v);
                if (u != null) Notifier.show(c, thread, address, body.toString());
            } catch (Exception ignored) {
                // Provider not available.
            } finally {
                pending.finish();
            }
        }).start();
    }
}
