/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.hodiny;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.VibratorManager;
import android.text.TextUtils;

/** Rings an alarm or a finished timer: sound, vibration and a full-screen notification (RingActivity). */
public class Ringer extends Service {
    static final String ACTION_STOP = "lech.os.hodiny.STOP";
    static final String ACTION_SNOOZE = "lech.os.hodiny.SNOOZE";
    static final String EXTRA_TIMER = "timer";
    static final String EXTRA_LABEL = "label";
    private static final String CHANNEL = "alarm";
    private static final int NOTIFICATION = 10;
    private static final long MAX_RING = 10 * 60_000L;

    private MediaPlayer player;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int alarmId = -1;

    static void start(Context c, boolean timer, String label, int id) {
        Intent i = new Intent(c, Ringer.class).putExtra(EXTRA_TIMER, timer).putExtra(EXTRA_LABEL, label)
                .putExtra(Alarms.EXTRA_ID, id);
        c.startForegroundService(i);
    }

    static void stop(Context c) {
        c.startService(new Intent(ACTION_STOP, null, c, Ringer.class));
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        if (i != null && ACTION_STOP.equals(i.getAction())) {
            stopRinging();
            return START_NOT_STICKY;
        }
        if (i != null && ACTION_SNOOZE.equals(i.getAction())) {
            if (alarmId >= 0) Alarms.snooze(this, alarmId, 10);
            stopRinging();
            return START_NOT_STICKY;
        }
        boolean timer = i != null && i.getBooleanExtra(EXTRA_TIMER, false);
        String label = i == null ? null : i.getStringExtra(EXTRA_LABEL);
        alarmId = i == null ? -1 : i.getIntExtra(Alarms.EXTRA_ID, -1);
        String title = getString(timer ? R.string.timer_done : R.string.alarm_title);
        String text = TextUtils.isEmpty(label) ? android.text.format.DateFormat.getTimeFormat(this)
                .format(new java.util.Date()) : label;

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, getString(R.string.channel_alarm),
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setSound(null, null);
            ch.setBypassDnd(true);
            nm.createNotificationChannel(ch);
        }
        Intent ring = new Intent(this, RingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION).putExtra(EXTRA_TIMER, timer).putExtra(EXTRA_LABEL, text);
        PendingIntent full = PendingIntent.getActivity(this, 0, ring,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stopPi = PendingIntent.getService(this, 1, new Intent(ACTION_STOP, null, this, Ringer.class),
                PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder nb = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(timer ? R.drawable.ic_hourglass : R.drawable.ic_alarm)
                .setColor(0xFF7BE41E)
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(timer ? Notification.CATEGORY_ALARM : Notification.CATEGORY_ALARM)
                .setOngoing(true)
                .setFullScreenIntent(full, true)
                .setContentIntent(full)
                .addAction(new Notification.Action.Builder(null, getString(R.string.dismiss), stopPi).build());
        if (!timer) {
            PendingIntent snoozePi = PendingIntent.getService(this, 2,
                    new Intent(ACTION_SNOOZE, null, this, Ringer.class), PendingIntent.FLAG_IMMUTABLE);
            nb.addAction(new Notification.Action.Builder(null, getString(R.string.snooze), snoozePi).build());
        }
        startForeground(NOTIFICATION, nb.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        try {
            startActivity(ring);
        } catch (Exception ignored) {
            // Background start not allowed: the full-screen notification shows it.
        }
        play();
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::stopRinging, MAX_RING);
        return START_NOT_STICKY;
    }

    private void play() {
        stopSound();
        Uri uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            player.setDataSource(this, uri);
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception e) {
            stopSound();
        }
        try {
            getSystemService(VibratorManager.class).getDefaultVibrator().vibrate(
                    VibrationEffect.createWaveform(new long[] {0, 800, 600}, 0),
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
        } catch (Exception ignored) {
            // No vibrator.
        }
    }

    private void stopSound() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
                // Not started.
            }
            player.release();
            player = null;
        }
    }

    private void stopRinging() {
        handler.removeCallbacksAndMessages(null);
        stopSound();
        try {
            getSystemService(VibratorManager.class).getDefaultVibrator().cancel();
        } catch (Exception ignored) {
            // No vibrator.
        }
        sendBroadcast(new Intent(RingActivity.ACTION_FINISH).setPackage(getPackageName()));
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        stopSound();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
