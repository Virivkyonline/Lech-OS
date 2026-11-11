/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.kalendar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract.CalendarAlerts;
import android.text.TextUtils;
import android.text.format.DateUtils;

/** EVENT_REMINDER of the calendar provider: a notification for each event whose reminder time came. */
public class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "reminders";

    @Override
    public void onReceive(Context c, Intent i) {
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                show(c, i.getData());
            } catch (Exception ignored) {
                // No permission or provider.
            } finally {
                pending.finish();
            }
        }).start();
    }

    private static void show(Context c, Uri data) {
        long alarmTime = -1;
        if (data != null) {
            try {
                alarmTime = Long.parseLong(data.getLastPathSegment());
            } catch (NumberFormatException ignored) {
                // Without the time: all alerts that are scheduled and due.
            }
        }
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, c.getString(R.string.channel),
                    NotificationManager.IMPORTANCE_HIGH));
        }
        String sel = CalendarAlerts.STATE + "=" + CalendarAlerts.STATE_SCHEDULED + " AND "
                + CalendarAlerts.ALARM_TIME + (alarmTime > 0 ? "=" + alarmTime : "<=" + System.currentTimeMillis());
        try (Cursor cur = c.getContentResolver().query(CalendarAlerts.CONTENT_URI, new String[] {
                    CalendarAlerts._ID, CalendarAlerts.EVENT_ID, CalendarAlerts.TITLE, CalendarAlerts.BEGIN,
                    CalendarAlerts.END, CalendarAlerts.EVENT_LOCATION, CalendarAlerts.ALL_DAY},
                sel, null, null)) {
            while (cur != null && cur.moveToNext()) {
                long alertId = cur.getLong(0);
                long eventId = cur.getLong(1);
                String title = cur.getString(2);
                long begin = cur.getLong(3);
                boolean allDay = cur.getInt(6) != 0;
                String where = cur.getString(5);
                String when = allDay ? c.getString(R.string.all_day)
                        : DateUtils.formatDateRange(c, begin, cur.getLong(4), DateUtils.FORMAT_SHOW_TIME
                                | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_ALL);
                String text = TextUtils.isEmpty(where) ? when : when + " · " + where;
                Intent open = new Intent(c, EditActivity.class).putExtra(EditActivity.EXTRA_EVENT, eventId)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                PendingIntent pi = PendingIntent.getActivity(c, (int) eventId, open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                Notification n = new Notification.Builder(c, CHANNEL)
                        .setSmallIcon(R.drawable.ic_event)
                        .setColor(0xFF7BE41E)
                        .setContentTitle(TextUtils.isEmpty(title) ? c.getString(R.string.app_name) : title)
                        .setContentText(text)
                        .setCategory(Notification.CATEGORY_EVENT)
                        .setAutoCancel(true)
                        .setContentIntent(pi)
                        .build();
                try {
                    nm.notify("event", (int) alertId, n);
                } catch (SecurityException ignored) {
                    // Notifications not allowed.
                }
                ContentValues v = new ContentValues();
                v.put(CalendarAlerts.STATE, CalendarAlerts.STATE_FIRED);
                c.getContentResolver().update(ContentUris.withAppendedId(CalendarAlerts.CONTENT_URI, alertId), v,
                        null, null);
            }
        }
    }
}
