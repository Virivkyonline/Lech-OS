/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.spravy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/** Notifications of new messages, with reply and "mark as read" from the notification. */
public final class Notifier {
    private static final String CHANNEL = "messages";
    static final String KEY_REPLY = "reply";
    private static final int MMS_ID = 1;

    private Notifier() {
    }

    private static NotificationManager nm(Context c) {
        NotificationManager n = c.getSystemService(NotificationManager.class);
        if (n.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, c.getString(R.string.notification_channel),
                    NotificationManager.IMPORTANCE_HIGH);
            ch.enableVibration(true);
            n.createNotificationChannel(ch);
        }
        return n;
    }

    static void show(Context c, long thread, String address, String body) {
        String who = Store.label(c, address);
        Intent open = new Intent(c, ConversationActivity.class).putExtra(ConversationActivity.EXTRA_THREAD, thread)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, (int) thread, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent reply = new Intent(c, Action.class).setAction("reply").putExtra("thread", thread)
                .putExtra("address", address);
        PendingIntent rpi = PendingIntent.getBroadcast(c, (int) thread * 2, reply,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        RemoteInput ri = new RemoteInput.Builder(KEY_REPLY).setLabel(c.getString(R.string.reply_hint)).build();
        Intent read = new Intent(c, Action.class).setAction("read").putExtra("thread", thread);
        PendingIntent mpi = PendingIntent.getBroadcast(c, (int) thread * 2 + 1, read,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_sms)
                .setColor(0xFF7BE41E)
                .setContentTitle(who)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .addAction(new Notification.Action.Builder(null, c.getString(R.string.reply), rpi)
                        .addRemoteInput(ri).build())
                .addAction(new Notification.Action.Builder(null, c.getString(R.string.mark_read), mpi).build())
                .build();
        try {
            nm(c).notify(tag(thread), 0, n);
        } catch (SecurityException ignored) {
            // Notifications not allowed.
        }
    }

    static void showMms(Context c) {
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_sms)
                .setColor(0xFF7BE41E)
                .setContentTitle(c.getString(R.string.mms_incoming))
                .setContentText(c.getString(R.string.mms_not_supported))
                .setStyle(new Notification.BigTextStyle().bigText(c.getString(R.string.mms_not_supported)))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();
        try {
            nm(c).notify(MMS_ID, n);
        } catch (SecurityException ignored) {
            // Notifications not allowed.
        }
    }

    private static String tag(long thread) {
        return "thread-" + thread;
    }

    static void cancel(Context c, long thread) {
        c.getSystemService(NotificationManager.class).cancel(tag(thread), 0);
    }

    static void cancelAll(Context c) {
        c.getSystemService(NotificationManager.class).cancelAll();
    }

    /** Reply and "mark as read" from a notification. */
    public static class Action extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            long thread = i.getLongExtra("thread", -1);
            final PendingResult pending = goAsync();
            new Thread(() -> {
                try {
                    if ("reply".equals(i.getAction())) {
                        Bundle in = RemoteInput.getResultsFromIntent(i);
                        CharSequence text = in == null ? null : in.getCharSequence(KEY_REPLY);
                        String address = i.getStringExtra("address");
                        if (text != null && text.length() > 0 && address != null) {
                            Store.send(c, address, text.toString(), -1);
                        }
                    }
                    Store.markRead(c, thread);
                } finally {
                    pending.finish();
                }
            }).start();
        }
    }
}
