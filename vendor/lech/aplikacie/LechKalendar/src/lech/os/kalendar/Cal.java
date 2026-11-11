/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.kalendar;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Instances;
import android.provider.CalendarContract.Reminders;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Events of the calendar provider (the local LECH OS calendar and any other on the phone) and local tasks. */
final class Cal {
    static final String ACCOUNT = "LECH OS";
    static final int[] COLORS = {0xFF29B6F6, 0xFFAB47BC, 0xFF66DD22, 0xFFFFA726, 0xFF26C6DA, 0xFFEF5350};

    private Cal() {
    }

    /** One occurrence of an event. */
    static final class Item {
        long eventId;
        long begin;
        long end;
        boolean allDay;
        String title;
        String location;
        int color;
        boolean hasAlarm;
        String rrule;
    }

    /** An event for the editor. */
    static final class Event {
        long id = -1;
        String title = "";
        String location = "";
        String description = "";
        long start;
        long end;
        boolean allDay;
        int color = COLORS[0];
        int reminder = -1; // minutes before, -1 = none
        String rrule;
    }

    /** A task (stored in the app). */
    static final class Task {
        long id;
        String title;
        boolean done;
    }

    private static Uri asSync(Uri u) {
        return u.buildUpon().appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT)
                .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build();
    }

    /** The id of the local LECH OS calendar; created on first use. */
    static synchronized long calendar(Context c) {
        ContentResolver r = c.getContentResolver();
        try (Cursor cur = r.query(Calendars.CONTENT_URI, new String[] {Calendars._ID},
                Calendars.ACCOUNT_NAME + "=? AND " + Calendars.ACCOUNT_TYPE + "=?",
                new String[] {ACCOUNT, CalendarContract.ACCOUNT_TYPE_LOCAL}, null)) {
            if (cur != null && cur.moveToFirst()) return cur.getLong(0);
        }
        ContentValues v = new ContentValues();
        v.put(Calendars.ACCOUNT_NAME, ACCOUNT);
        v.put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL);
        v.put(Calendars.NAME, "lech");
        v.put(Calendars.CALENDAR_DISPLAY_NAME, c.getString(R.string.calendar_name));
        v.put(Calendars.CALENDAR_COLOR, 0xFF7BE41E);
        v.put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER);
        v.put(Calendars.OWNER_ACCOUNT, ACCOUNT);
        v.put(Calendars.VISIBLE, 1);
        v.put(Calendars.SYNC_EVENTS, 1);
        v.put(Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().getID());
        Uri u = r.insert(asSync(Calendars.CONTENT_URI), v);
        return u == null ? -1 : ContentUris.parseId(u);
    }

    /** Occurrences between begin and end (milliseconds), sorted. */
    static List<Item> items(Context c, long begin, long end, String query) {
        Uri.Builder b = Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, begin);
        ContentUris.appendId(b, end);
        List<Item> out = new ArrayList<>();
        try (Cursor cur = c.getContentResolver().query(b.build(), new String[] {
                    Instances.EVENT_ID, Instances.BEGIN, Instances.END, Instances.ALL_DAY, Instances.TITLE,
                    Instances.EVENT_LOCATION, Instances.DISPLAY_COLOR, Instances.HAS_ALARM, Instances.RRULE},
                Instances.VISIBLE + "=1", null, Instances.BEGIN + " ASC")) {
            while (cur != null && cur.moveToNext()) {
                Item it = new Item();
                it.eventId = cur.getLong(0);
                it.allDay = cur.getInt(3) != 0;
                it.begin = cur.getLong(1);
                it.end = cur.getLong(2);
                if (it.allDay) {
                    it.begin = utcToLocal(it.begin);
                    it.end = utcToLocal(it.end);
                }
                it.title = cur.getString(4);
                it.location = cur.getString(5);
                it.color = cur.getInt(6);
                it.hasAlarm = cur.getInt(7) != 0;
                it.rrule = cur.getString(8);
                if (query != null && !query.isEmpty()) {
                    String hay = ((it.title == null ? "" : it.title) + " " + (it.location == null ? "" : it.location))
                            .toLowerCase(Locale.ROOT);
                    if (!hay.contains(query.toLowerCase(Locale.ROOT))) continue;
                }
                out.add(it);
            }
        } catch (SecurityException ignored) {
            // No permission yet.
        }
        Collections.sort(out, (x, y) -> x.allDay != y.allDay ? (x.allDay ? -1 : 1) : Long.compare(x.begin, y.begin));
        return out;
    }

    /** All-day events are stored at UTC midnight; shown on the same calendar day locally. */
    static long utcToLocal(long utc) {
        Calendar u = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        u.setTimeInMillis(utc);
        Calendar l = Calendar.getInstance();
        l.clear();
        l.set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH));
        return l.getTimeInMillis();
    }

    static long localToUtc(long local) {
        Calendar l = Calendar.getInstance();
        l.setTimeInMillis(local);
        Calendar u = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        u.clear();
        u.set(l.get(Calendar.YEAR), l.get(Calendar.MONTH), l.get(Calendar.DAY_OF_MONTH));
        return u.getTimeInMillis();
    }

    static Event load(Context c, long id) {
        ContentResolver r = c.getContentResolver();
        try (Cursor cur = r.query(ContentUris.withAppendedId(Events.CONTENT_URI, id), new String[] {
                    Events.TITLE, Events.EVENT_LOCATION, Events.DESCRIPTION, Events.DTSTART, Events.DTEND,
                    Events.ALL_DAY, Events.DISPLAY_COLOR, Events.RRULE, Events.DURATION}, null, null, null)) {
            if (cur == null || !cur.moveToFirst()) return null;
            Event e = new Event();
            e.id = id;
            e.title = nz(cur.getString(0));
            e.location = nz(cur.getString(1));
            e.description = nz(cur.getString(2));
            e.allDay = cur.getInt(5) != 0;
            e.start = cur.getLong(3);
            e.end = cur.isNull(4) ? e.start + duration(cur.getString(8)) : cur.getLong(4);
            if (e.allDay) {
                e.start = utcToLocal(e.start);
                e.end = utcToLocal(e.end);
            }
            e.color = cur.getInt(6);
            e.rrule = cur.getString(7);
            try (Cursor rc = r.query(Reminders.CONTENT_URI, new String[] {Reminders.MINUTES},
                    Reminders.EVENT_ID + "=?", new String[] {String.valueOf(id)}, Reminders.MINUTES + " ASC")) {
                if (rc != null && rc.moveToFirst()) e.reminder = rc.getInt(0);
            }
            return e;
        }
    }

    /** RFC 2445 duration such as P3600S or P1D, in milliseconds. */
    private static long duration(String d) {
        if (TextUtils.isEmpty(d)) return 3_600_000L;
        try {
            String s = d.replace("P", "").replace("T", "");
            if (s.endsWith("S")) return Long.parseLong(s.substring(0, s.length() - 1)) * 1000L;
            if (s.endsWith("D")) return Long.parseLong(s.substring(0, s.length() - 1)) * 86_400_000L;
            if (s.endsWith("W")) return Long.parseLong(s.substring(0, s.length() - 1)) * 7 * 86_400_000L;
        } catch (NumberFormatException ignored) {
            // Fall back to one hour.
        }
        return 3_600_000L;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** Saves an event (to the local calendar when new) with its reminder. */
    static long save(Context c, Event e) {
        ContentResolver r = c.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(Events.TITLE, e.title);
        v.put(Events.EVENT_LOCATION, e.location);
        v.put(Events.DESCRIPTION, e.description);
        v.put(Events.ALL_DAY, e.allDay ? 1 : 0);
        long start = e.allDay ? localToUtc(e.start) : e.start;
        long end = e.allDay ? localToUtc(e.end) : e.end;
        if (e.allDay && end <= start) end = start + 86_400_000L;
        v.put(Events.DTSTART, start);
        v.put(Events.EVENT_TIMEZONE, e.allDay ? "UTC" : TimeZone.getDefault().getID());
        if (TextUtils.isEmpty(e.rrule)) {
            v.put(Events.DTEND, end);
            v.putNull(Events.DURATION);
            v.putNull(Events.RRULE);
        } else {
            v.putNull(Events.DTEND);
            v.put(Events.DURATION, "P" + Math.max(1, (end - start) / 1000) + "S");
            v.put(Events.RRULE, e.rrule);
        }
        v.put(Events.HAS_ALARM, e.reminder >= 0 ? 1 : 0);
        long id = e.id;
        if (id < 0) {
            v.put(Events.CALENDAR_ID, calendar(c));
            v.put(Events.EVENT_COLOR, e.color);
            Uri u = r.insert(asSync(Events.CONTENT_URI), v);
            if (u == null) return -1;
            id = ContentUris.parseId(u);
        } else {
            if (isOwn(c, id)) {
                v.put(Events.EVENT_COLOR, e.color);
                r.update(asSync(ContentUris.withAppendedId(Events.CONTENT_URI, id)), v, null, null);
            } else {
                r.update(ContentUris.withAppendedId(Events.CONTENT_URI, id), v, null, null);
            }
            r.delete(Reminders.CONTENT_URI, Reminders.EVENT_ID + "=?", new String[] {String.valueOf(id)});
        }
        if (e.reminder >= 0) {
            ContentValues rv = new ContentValues();
            rv.put(Reminders.EVENT_ID, id);
            rv.put(Reminders.MINUTES, e.reminder);
            rv.put(Reminders.METHOD, Reminders.METHOD_ALERT);
            r.insert(Reminders.CONTENT_URI, rv);
        }
        return id;
    }

    private static boolean isOwn(Context c, long eventId) {
        try (Cursor cur = c.getContentResolver().query(ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
                new String[] {Events.CALENDAR_ID}, null, null, null)) {
            return cur != null && cur.moveToFirst() && cur.getLong(0) == calendar(c);
        }
    }

    static void delete(Context c, long id) {
        c.getContentResolver().delete(ContentUris.withAppendedId(Events.CONTENT_URI, id), null, null);
    }

    // ---- Settings and tasks ----

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("kalendar", Context.MODE_PRIVATE);
    }

    static int defaultReminder(Context c) {
        return prefs(c).getInt("reminder", 15);
    }

    static void setDefaultReminder(Context c, int minutes) {
        prefs(c).edit().putInt("reminder", minutes).apply();
    }

    static List<Task> tasks(Context c) {
        List<Task> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("tasks", "[]"));
            for (int k = 0; k < a.length(); k++) {
                JSONObject o = a.getJSONObject(k);
                Task t = new Task();
                t.id = o.getLong("id");
                t.title = o.getString("t");
                t.done = o.optBoolean("d");
                out.add(t);
            }
        } catch (Exception ignored) {
            // No tasks.
        }
        return out;
    }

    static void saveTasks(Context c, List<Task> tasks) {
        JSONArray a = new JSONArray();
        try {
            for (Task t : tasks) {
                JSONObject o = new JSONObject();
                o.put("id", t.id);
                o.put("t", t.title);
                o.put("d", t.done);
                a.put(o);
            }
        } catch (Exception ignored) {
            // Not reachable.
        }
        prefs(c).edit().putString("tasks", a.toString()).apply();
    }

    static int iconFor(String title) {
        String t = title == null ? "" : java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        if (t.matches(".*(obed|vecer|ranajk|restaur|kava|coffee|lunch|dinner).*")) return R.drawable.ic_restaurant;
        if (t.matches(".*(trening|fitnes|beh|sport|posilnov|gym|workout).*")) return R.drawable.ic_fitness;
        if (t.matches(".*(narodenin|meniny|oslav|birthday).*")) return R.drawable.ic_cake;
        if (t.matches(".*(porad|praca|stretnut|meeting|work|klient|stavb).*")) return R.drawable.ic_work;
        return R.drawable.ic_event;
    }
}
