/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.hodiny;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/** Alarms (stored in the app's preferences), their scheduling with the alarm manager, and the timer. */
final class Alarms {
    private static final String PREFS = "hodiny";
    static final String ACTION_ALARM = "lech.os.hodiny.ALARM";
    static final String ACTION_TIMER = "lech.os.hodiny.TIMER";
    static final String EXTRA_ID = "id";

    /** One alarm; days: bit 0 = Monday … bit 6 = Sunday, 0 = once. */
    static final class Alarm {
        int id;
        int hour;
        int minute;
        int days;
        String label = "";
        boolean enabled = true;
        long snoozeUntil;
    }

    private Alarms() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static synchronized List<Alarm> load(Context c) {
        List<Alarm> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("alarms", "[]"));
            for (int k = 0; k < a.length(); k++) {
                JSONObject o = a.getJSONObject(k);
                Alarm al = new Alarm();
                al.id = o.getInt("id");
                al.hour = o.getInt("h");
                al.minute = o.getInt("m");
                al.days = o.optInt("d");
                al.label = o.optString("l");
                al.enabled = o.optBoolean("e", true);
                al.snoozeUntil = o.optLong("s");
                out.add(al);
            }
        } catch (Exception ignored) {
            // Empty or broken: no alarms.
        }
        Collections.sort(out, (x, y) -> Integer.compare(x.hour * 60 + x.minute, y.hour * 60 + y.minute));
        return out;
    }

    static synchronized void save(Context c, List<Alarm> list) {
        JSONArray a = new JSONArray();
        try {
            for (Alarm al : list) {
                JSONObject o = new JSONObject();
                o.put("id", al.id);
                o.put("h", al.hour);
                o.put("m", al.minute);
                o.put("d", al.days);
                o.put("l", al.label);
                o.put("e", al.enabled);
                o.put("s", al.snoozeUntil);
                a.put(o);
            }
        } catch (Exception ignored) {
            // Not reachable with these types.
        }
        prefs(c).edit().putString("alarms", a.toString()).apply();
        schedule(c);
    }

    static Alarm find(List<Alarm> list, int id) {
        for (Alarm a : list) if (a.id == id) return a;
        return null;
    }

    static int newId(List<Alarm> list) {
        int max = 0;
        for (Alarm a : list) max = Math.max(max, a.id);
        return max + 1;
    }

    /** The next time the alarm rings, in milliseconds. */
    static long next(Alarm a, long now) {
        if (a.snoozeUntil > now) return a.snoozeUntil;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, a.hour);
        c.set(Calendar.MINUTE, a.minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= now) c.add(Calendar.DAY_OF_YEAR, 1);
        if (a.days != 0) {
            for (int k = 0; k < 7; k++) {
                int dow = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7; // Monday = 0
                if ((a.days & (1 << dow)) != 0) break;
                c.add(Calendar.DAY_OF_YEAR, 1);
            }
        }
        return c.getTimeInMillis();
    }

    /** Schedules the nearest enabled alarm (one alarm clock at a time, as the system shows it). */
    static void schedule(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        long now = System.currentTimeMillis();
        Alarm best = null;
        long bestAt = Long.MAX_VALUE;
        for (Alarm a : load(c)) {
            if (!a.enabled) continue;
            long at = next(a, now);
            if (at < bestAt) {
                bestAt = at;
                best = a;
            }
        }
        PendingIntent op = PendingIntent.getBroadcast(c, 1, new Intent(ACTION_ALARM, null, c, AlarmReceiver.class)
                .putExtra(EXTRA_ID, best == null ? -1 : best.id),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (best == null) {
            am.cancel(op);
            return;
        }
        PendingIntent show = PendingIntent.getActivity(c, 2, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        try {
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(bestAt, show), op);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, bestAt, op);
        }
    }

    /** After an alarm rang: a one-time alarm turns off, a snooze ends; the next one is scheduled. */
    static void rang(Context c, int id) {
        List<Alarm> list = load(c);
        Alarm a = find(list, id);
        if (a != null) {
            a.snoozeUntil = 0;
            if (a.days == 0) a.enabled = false;
        }
        save(c, list);
    }

    static void snooze(Context c, int id, int minutes) {
        List<Alarm> list = load(c);
        Alarm a = find(list, id);
        if (a != null) {
            a.enabled = true;
            a.snoozeUntil = System.currentTimeMillis() + minutes * 60_000L;
        }
        save(c, list);
    }

    // ---- Timer (one at a time) ----

    static long timerEnd(Context c) {
        return prefs(c).getLong("timerEnd", 0);
    }

    static long timerLeft(Context c) {
        return prefs(c).getLong("timerLeft", 0);
    }

    static long timerTotal(Context c) {
        return prefs(c).getLong("timerTotal", 0);
    }

    static void startTimer(Context c, long millis, long total) {
        long end = System.currentTimeMillis() + millis;
        prefs(c).edit().putLong("timerEnd", end).putLong("timerLeft", 0).putLong("timerTotal", total).apply();
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent op = timerIntent(c);
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, op);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, op);
        }
    }

    static void pauseTimer(Context c) {
        long left = Math.max(0, timerEnd(c) - System.currentTimeMillis());
        prefs(c).edit().putLong("timerEnd", 0).putLong("timerLeft", left).apply();
        c.getSystemService(AlarmManager.class).cancel(timerIntent(c));
    }

    static void resetTimer(Context c) {
        prefs(c).edit().putLong("timerEnd", 0).putLong("timerLeft", 0).putLong("timerTotal", 0).apply();
        c.getSystemService(AlarmManager.class).cancel(timerIntent(c));
    }

    private static PendingIntent timerIntent(Context c) {
        return PendingIntent.getBroadcast(c, 3, new Intent(ACTION_TIMER, null, c, AlarmReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // ---- Stopwatch ----

    static long[] stopwatch(Context c) {
        SharedPreferences p = prefs(c);
        return new long[] {p.getLong("swStart", 0), p.getLong("swAcc", 0)};
    }

    static void setStopwatch(Context c, long start, long acc) {
        prefs(c).edit().putLong("swStart", start).putLong("swAcc", acc).apply();
    }

    static String laps(Context c) {
        return prefs(c).getString("swLaps", "");
    }

    static void setLaps(Context c, String laps) {
        prefs(c).edit().putString("swLaps", laps).apply();
    }

    // ---- World clock ----

    static List<String> cities(Context c) {
        List<String> out = new ArrayList<>();
        for (String s : prefs(c).getString("cities", "").split(";")) if (!s.isEmpty()) out.add(s);
        return out;
    }

    static void setCities(Context c, List<String> ids) {
        prefs(c).edit().putString("cities", String.join(";", ids)).apply();
    }
}
