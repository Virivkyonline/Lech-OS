/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.hodiny;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import lech.os.ui.Ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** The clock: alarms, world clock, timer and stopwatch in the LECH OS design. */
public class MainActivity extends Activity {
    private static final int ALARM = 0;
    private static final int CLOCK = 1;
    private static final int TIMER = 2;
    private static final int STOPWATCH = 3;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int tab = ALARM;
    private Ui.Header header;
    private final List<LinearLayout> tabs = new ArrayList<>();
    private FrameLayout pages;
    private TextView bigTime;
    private TextView bigDate;
    private LinearLayout content;
    private View fab;

    // Timer and stopwatch views (rebuilt on each render).
    private TextView timerText;
    private TextView stopwatchText;
    private NumberPicker pickH;
    private NumberPicker pickM;
    private NumberPicker pickS;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            update();
            handler.postDelayed(this, tab == STOPWATCH ? 50 : 500);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.column(this);
        root.setBackground(Ui.background());
        header = Ui.header(this, R.mipmap.ic_hodiny);
        header.title.setText(R.string.app_name);
        header.subtitle.setText(R.string.subtitle);
        root.addView(header.view, Ui.lp(-1, -2));

        LinearLayout bar = Ui.row(this);
        bar.setBackground(Ui.shape(this, 0xCC0A2530, 0xAA34A0A0, 1.2f, 34));
        bar.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        addTab(bar, ALARM, R.drawable.ic_alarm, R.string.tab_alarm);
        addTab(bar, CLOCK, R.drawable.ic_history, R.string.tab_clock);
        addTab(bar, TIMER, R.drawable.ic_hourglass, R.string.tab_timer);
        addTab(bar, STOPWATCH, R.drawable.ic_stopwatch, R.string.tab_stopwatch);
        root.addView(bar, Ui.margins(Ui.lp(-1, -2), this, 14, 0, 14, 4));

        pages = new FrameLayout(this);
        ScrollView sc = new ScrollView(this);
        content = Ui.column(this);
        content.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 110));
        sc.addView(content, Ui.lp(-1, -2));
        pages.addView(sc, Ui.match());
        fab = Ui.roundButton(this, R.drawable.ic_add, true, 76, getString(R.string.add_alarm), v -> {
            if (tab == ALARM) editAlarm(null);
            else addCity();
        });
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(Ui.dp(this, 76), Ui.dp(this, 76),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        fl.bottomMargin = Ui.dp(this, 22);
        pages.addView(fab, fl);
        root.addView(pages, Ui.lp(-1, 0, 1));

        Ui.edgeToEdge(this, root);
        setContentView(root);
        handleIntent(getIntent());
        selectTab(tab);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
        selectTab(tab);
    }

    /** Requests of other apps: set an alarm or a timer, show alarms or timers. */
    private void handleIntent(Intent i) {
        String a = i == null ? null : i.getAction();
        if (AlarmClock.ACTION_SET_ALARM.equals(a) && i.hasExtra(AlarmClock.EXTRA_HOUR)) {
            List<Alarms.Alarm> list = Alarms.load(this);
            Alarms.Alarm al = new Alarms.Alarm();
            al.id = Alarms.newId(list);
            al.hour = i.getIntExtra(AlarmClock.EXTRA_HOUR, 7);
            al.minute = i.getIntExtra(AlarmClock.EXTRA_MINUTES, 0);
            String msg = i.getStringExtra(AlarmClock.EXTRA_MESSAGE);
            al.label = msg == null ? "" : msg;
            ArrayList<Integer> days = i.getIntegerArrayListExtra(AlarmClock.EXTRA_DAYS);
            if (days != null) {
                for (int d : days) al.days |= 1 << ((d + 5) % 7);
            }
            list.add(al);
            Alarms.save(this, list);
            tab = ALARM;
            if (i.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false)) finish();
        } else if (AlarmClock.ACTION_SET_TIMER.equals(a) && i.hasExtra(AlarmClock.EXTRA_LENGTH)) {
            long ms = i.getIntExtra(AlarmClock.EXTRA_LENGTH, 60) * 1000L;
            Alarms.startTimer(this, ms, ms);
            tab = TIMER;
            if (i.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false)) finish();
        } else if (AlarmClock.ACTION_SHOW_TIMERS.equals(a)) {
            tab = TIMER;
        } else if (AlarmClock.ACTION_SHOW_ALARMS.equals(a)) {
            tab = ALARM;
        }
    }

    private boolean askedAccess;

    /** Notifications and the full-screen ringing screen need the user's consent (Android 14 and newer). */
    private boolean askAccess() {
        if (askedAccess) return false;
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            askedAccess = true;
            requestPermissions(new String[] {android.Manifest.permission.POST_NOTIFICATIONS}, 1);
            return true;
        }
        android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
        if (!nm.canUseFullScreenIntent()) {
            askedAccess = true;
            Toast.makeText(this, R.string.fullscreen_needed, Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.fromParts("package", getPackageName(), null)));
            } catch (Exception ignored) {
                // Not available.
            }
            return true;
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        askedAccess = false;
        android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
        if (!nm.canUseFullScreenIntent()) askAccess();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
        handler.post(tick);
        if (askAccess()) return;
        AlarmManager am = getSystemService(AlarmManager.class);
        if (!am.canScheduleExactAlarms() && !Alarms.load(this).isEmpty()) {
            Toast.makeText(this, R.string.exact_needed, Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.fromParts("package", getPackageName(), null)));
            } catch (Exception ignored) {
                // Not available.
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    private void addTab(LinearLayout bar, int which, int icon, int label) {
        LinearLayout t = Ui.column(this);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        t.addView(Ui.icon(this, icon, Ui.TEXT, 28));
        TextView l = Ui.text(this, getString(label), 15, Ui.TEXT, true);
        l.setGravity(Gravity.CENTER);
        t.addView(l);
        t.setOnClickListener(v -> selectTab(which));
        bar.addView(t, Ui.lp(0, -2, 1));
        tabs.add(t);
    }

    private void selectTab(int which) {
        tab = which;
        for (int k = 0; k < tabs.size(); k++) {
            LinearLayout t = tabs.get(k);
            boolean on = k == which;
            Ui.clickable(t, on ? Ui.greenGlow(this, 30) : Ui.shape(this, 0, 0, 0, 30));
            int color = on ? Ui.ON_GREEN : Ui.TEXT;
            ((ImageView) t.getChildAt(0)).setImageTintList(ColorStateList.valueOf(color));
            ((TextView) t.getChildAt(1)).setTextColor(color);
        }
        render();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    // ---- Rendering ----

    private void render() {
        content.removeAllViews();
        timerText = null;
        stopwatchText = null;
        if (Ringer.isRinging()) {
            // While an alarm rings, the first thing in the app is the button that stops it.
            LinearLayout stop = Ui.pillButton(this, R.drawable.ic_close, getString(R.string.dismiss), v -> {
                Ringer.stop(this);
                handler.postDelayed(this::render, 300);
            });
            LinearLayout.LayoutParams sl = Ui.margins(Ui.lp(-2, -2), this, 0, 12, 0, 4);
            sl.gravity = Gravity.CENTER_HORIZONTAL;
            content.addView(stop, sl);
        }
        fab.setVisibility(tab == ALARM || tab == CLOCK ? View.VISIBLE : View.GONE);
        if (tab == ALARM || tab == CLOCK) {
            bigTime = Ui.text(this, "", 84, Ui.TEXT, false);
            bigTime.setGravity(Gravity.CENTER);
            bigTime.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
            content.addView(bigTime, Ui.margins(Ui.lp(-1, -2), this, 0, 8, 0, 0));
            bigDate = Ui.text(this, "", 20, Ui.TEXT, true);
            bigDate.setGravity(Gravity.CENTER);
            content.addView(bigDate, Ui.margins(Ui.lp(-1, -2), this, 0, 0, 0, 16));
            if (tab == ALARM) renderAlarms(); else renderCities();
        } else if (tab == TIMER) {
            renderTimer();
        } else {
            renderStopwatch();
        }
        update();
    }

    private LinearLayout card() {
        LinearLayout r = Ui.row(this);
        r.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        Ui.clickable(r, Ui.shape(this, 0xCC0A2530, 0xAA34A0A0, 1.2f, 30));
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.bottomMargin = Ui.dp(this, 10);
        r.setLayoutParams(lp);
        return r;
    }

    private void renderAlarms() {
        List<Alarms.Alarm> list = Alarms.load(this);
        if (list.isEmpty()) {
            TextView e = Ui.text(this, getString(R.string.no_alarms), 16, Ui.TEXT_2, false);
            e.setGravity(Gravity.CENTER);
            content.addView(e, Ui.margins(Ui.lp(-1, -2), this, 0, 20, 0, 0));
            return;
        }
        long now = System.currentTimeMillis();
        for (Alarms.Alarm a : list) {
            LinearLayout r = card();
            FrameLayout ic = new FrameLayout(this);
            ic.setBackground(Ui.circle(0xFF06141D, 0, 0));
            ic.addView(Ui.icon(this, R.drawable.ic_alarm, a.enabled ? Ui.GREEN : 0xFF4FC3F7, 30),
                    new FrameLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 30), Gravity.CENTER));
            r.addView(ic, Ui.lp(Ui.dp(this, 56), Ui.dp(this, 56)));
            LinearLayout t = Ui.column(this);
            t.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 8), 0);
            TextView time = Ui.text(this, String.format(Locale.ROOT, "%02d:%02d", a.hour, a.minute), 34,
                    a.enabled ? Ui.TEXT : Ui.TEXT_2, true);
            t.addView(time);
            String sub = days(a.days);
            if (!TextUtils.isEmpty(a.label)) sub = a.label + " · " + sub;
            if (a.enabled) {
                long in = Alarms.next(a, now) - now;
                long h = in / 3_600_000L;
                long m = (in / 60_000L) % 60 + 1;
                if (m == 60) {
                    h++;
                    m = 0;
                }
                String left = h > 0 ? getString(R.string.hours_minutes, (int) h, (int) m)
                        : getString(R.string.minutes, (int) m);
                sub = sub + " · " + left;
            }
            TextView s = Ui.text(this, sub, 15, Ui.TEXT_2, false);
            s.setMaxLines(2);
            t.addView(s);
            r.addView(t, Ui.lp(0, -2, 1));
            Switch sw = new Switch(this);
            sw.setChecked(a.enabled);
            sw.setThumbTintList(new ColorStateList(new int[][] {{android.R.attr.state_checked}, {}},
                    new int[] {0xFFFFFFFF, 0xFFE0E0E0}));
            sw.setTrackTintList(new ColorStateList(new int[][] {{android.R.attr.state_checked}, {}},
                    new int[] {Ui.GREEN, 0xFF37474F}));
            sw.setScaleX(1.3f);
            sw.setScaleY(1.3f);
            sw.setOnCheckedChangeListener((b, on) -> {
                List<Alarms.Alarm> all = Alarms.load(this);
                Alarms.Alarm x = Alarms.find(all, a.id);
                if (x != null) {
                    x.enabled = on;
                    x.snoozeUntil = 0;
                    Alarms.save(this, all);
                }
                handler.post(this::render);
            });
            r.addView(sw);
            r.setOnClickListener(v -> editAlarm(a));
            content.addView(r);
        }
    }

    private String days(int d) {
        if (d == 0) return getString(R.string.once);
        if (d == 0x7f) return getString(R.string.every_day);
        if (d == 0x1f) return getString(R.string.weekdays);
        if (d == 0x60) return getString(R.string.weekend);
        String[] names = getString(R.string.days_short).split(",");
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < 7; k++) {
            if ((d & (1 << k)) != 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(names[k]);
            }
        }
        return sb.toString();
    }

    private void editAlarm(Alarms.Alarm existing) {
        LinearLayout v = Ui.column(this);
        v.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), 0);
        TimePicker tp = new TimePicker(this);
        tp.setIs24HourView(DateFormat.is24HourFormat(this));
        Calendar now = Calendar.getInstance();
        tp.setHour(existing != null ? existing.hour : now.get(Calendar.HOUR_OF_DAY));
        tp.setMinute(existing != null ? existing.minute : 0);
        v.addView(tp, Ui.lp(-1, -2));
        final int[] days = {existing != null ? existing.days : 0};
        LinearLayout dayRow = Ui.row(this);
        dayRow.setGravity(Gravity.CENTER);
        String[] names = getString(R.string.days_short).split(",");
        for (int k = 0; k < 7; k++) {
            final int bit = 1 << k;
            TextView d = Ui.text(this, names[k], 14, Ui.TEXT, true);
            d.setGravity(Gravity.CENTER);
            Runnable paint = () -> {
                boolean on = (days[0] & bit) != 0;
                d.setBackground(on ? Ui.circle(Ui.GREEN, 0, 0) : Ui.circle(0x33FFFFFF, 0, 0));
                d.setTextColor(on ? Ui.ON_GREEN : Ui.TEXT);
            };
            paint.run();
            d.setOnClickListener(x -> {
                days[0] ^= bit;
                paint.run();
            });
            LinearLayout.LayoutParams lp = Ui.lp(Ui.dp(this, 38), Ui.dp(this, 38));
            lp.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
            dayRow.addView(d, lp);
        }
        v.addView(dayRow, Ui.margins(Ui.lp(-1, -2), this, 0, 4, 0, 10));
        EditText label = new EditText(this);
        label.setHint(R.string.label_hint);
        label.setSingleLine(true);
        label.setText(existing != null ? existing.label : "");
        v.addView(label, Ui.lp(-1, -2));
        ScrollView sv = new ScrollView(this);
        sv.addView(v);
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(existing == null ? R.string.add_alarm : R.string.edit_alarm)
                .setView(sv)
                .setPositiveButton(R.string.save, (dlg, w) -> {
                    List<Alarms.Alarm> list = Alarms.load(this);
                    Alarms.Alarm a = existing == null ? null : Alarms.find(list, existing.id);
                    if (a == null) {
                        a = new Alarms.Alarm();
                        a.id = Alarms.newId(list);
                        list.add(a);
                    }
                    a.hour = tp.getHour();
                    a.minute = tp.getMinute();
                    a.days = days[0];
                    a.label = label.getText().toString().trim();
                    a.enabled = true;
                    a.snoozeUntil = 0;
                    Alarms.save(this, list);
                    long in = Alarms.next(a, System.currentTimeMillis());
                    Toast.makeText(this, getString(R.string.alarm_in, DateUtils.formatDateTime(this, in,
                            DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_WEEKDAY)), Toast.LENGTH_SHORT).show();
                    render();
                })
                .setNegativeButton(R.string.cancel, null);
        if (existing != null) {
            b.setNeutralButton(R.string.delete, (dlg, w) -> {
                List<Alarms.Alarm> list = Alarms.load(this);
                Alarms.Alarm a = Alarms.find(list, existing.id);
                if (a != null) list.remove(a);
                Alarms.save(this, list);
                render();
            });
        }
        b.show();
    }

    // ---- World clock ----

    private void renderCities() {
        List<String> ids = Alarms.cities(this);
        if (ids.isEmpty()) {
            TextView e = Ui.text(this, getString(R.string.no_cities), 16, Ui.TEXT_2, false);
            e.setGravity(Gravity.CENTER);
            content.addView(e, Ui.margins(Ui.lp(-1, -2), this, 0, 20, 0, 0));
            return;
        }
        for (String id : ids) {
            TimeZone tz = TimeZone.getTimeZone(id);
            LinearLayout r = card();
            LinearLayout t = Ui.column(this);
            t.addView(Ui.text(this, city(id), 20, Ui.TEXT, true));
            t.addView(Ui.text(this, diff(tz), 14, Ui.TEXT_2, false));
            r.addView(t, Ui.lp(0, -2, 1));
            TextView time = Ui.text(this, "", 30, Ui.TEXT, true);
            time.setTag(tz);
            r.addView(time);
            r.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setMessage(city(id))
                        .setPositiveButton(R.string.remove, (d, w) -> {
                            List<String> all = Alarms.cities(this);
                            all.remove(id);
                            Alarms.setCities(this, all);
                            render();
                        })
                        .setNegativeButton(R.string.cancel, null).show();
                return true;
            });
            content.addView(r);
        }
    }

    private static String city(String id) {
        int s = id.lastIndexOf('/');
        return (s >= 0 ? id.substring(s + 1) : id).replace('_', ' ');
    }

    private String diff(TimeZone tz) {
        long now = System.currentTimeMillis();
        long d = tz.getOffset(now) - TimeZone.getDefault().getOffset(now);
        if (d == 0) return getString(R.string.same_time);
        long mins = Math.abs(d) / 60_000L;
        String s = mins % 60 == 0 ? (mins / 60) + " h" : (mins / 60) + " h " + (mins % 60) + " min";
        return getString(d > 0 ? R.string.hour_diff_ahead : R.string.hour_diff_behind, s);
    }

    private void addCity() {
        LinearLayout v = Ui.column(this);
        EditText q = new EditText(this);
        q.setHint(R.string.search_city);
        q.setSingleLine(true);
        v.addView(q, Ui.lp(-1, -2));
        ListView lv = new ListView(this);
        List<String> all = new ArrayList<>();
        for (String id : TimeZone.getAvailableIDs()) {
            if (id.contains("/") && !id.startsWith("Etc/") && !id.startsWith("SystemV/")) all.add(id);
        }
        List<String> shown = new ArrayList<>(all);
        List<String> labels = new ArrayList<>();
        for (String id : shown) labels.add(city(id) + "  (" + id.substring(0, id.indexOf('/')) + ")");
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels);
        lv.setAdapter(ad);
        v.addView(lv, Ui.lp(-1, Ui.dp(this, 360)));
        AlertDialog dlg = new AlertDialog.Builder(this).setTitle(R.string.add_city).setView(v)
                .setNegativeButton(R.string.cancel, null).create();
        q.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                String f = s.toString().trim().toLowerCase(Locale.ROOT);
                shown.clear();
                labels.clear();
                for (String id : all) {
                    if (f.isEmpty() || id.toLowerCase(Locale.ROOT).replace('_', ' ').contains(f)) {
                        shown.add(id);
                        labels.add(city(id) + "  (" + id.substring(0, id.indexOf('/')) + ")");
                    }
                }
                ad.notifyDataSetChanged();
            }
        });
        lv.setOnItemClickListener((p, view, pos, id) -> {
            List<String> cities = Alarms.cities(this);
            if (!cities.contains(shown.get(pos))) cities.add(shown.get(pos));
            Alarms.setCities(this, cities);
            dlg.dismiss();
            render();
        });
        dlg.show();
    }

    // ---- Timer ----

    private void renderTimer() {
        long end = Alarms.timerEnd(this);
        long left = Alarms.timerLeft(this);
        boolean running = end > System.currentTimeMillis();
        boolean paused = !running && left > 0;
        if (running || paused) {
            timerText = Ui.text(this, "", 76, Ui.TEXT, false);
            timerText.setGravity(Gravity.CENTER);
            timerText.setTypeface(android.graphics.Typeface.create("sans-serif-light",
                    android.graphics.Typeface.NORMAL));
            content.addView(timerText, Ui.margins(Ui.lp(-1, -2), this, 0, 40, 0, 40));
            LinearLayout row = Ui.row(this);
            row.setGravity(Gravity.CENTER);
            row.addView(Ui.pillButton(this, running ? R.drawable.ic_pause : R.drawable.ic_play_arrow,
                    getString(running ? R.string.pause : R.string.resume), v -> {
                        if (Alarms.timerEnd(this) > System.currentTimeMillis()) {
                            Alarms.pauseTimer(this);
                        } else {
                            Alarms.startTimer(this, Alarms.timerLeft(this), Alarms.timerTotal(this));
                        }
                        render();
                    }));
            View reset = Ui.roundButton(this, R.drawable.ic_close, false, 60, getString(R.string.reset), v -> {
                Alarms.resetTimer(this);
                render();
            });
            row.addView(reset, Ui.margins(Ui.lp(Ui.dp(this, 60), Ui.dp(this, 60)), this, 16, 0, 0, 0));
            content.addView(row, Ui.lp(-1, -2));
            return;
        }
        LinearLayout pick = Ui.row(this);
        pick.setGravity(Gravity.CENTER);
        pickH = picker(23);
        pickM = picker(59);
        pickS = picker(59);
        pickM.setValue(5);
        pick.addView(pickH);
        pick.addView(Ui.text(this, getString(R.string.hours), 18, Ui.TEXT_2, false));
        pick.addView(pickM, Ui.margins(Ui.lp(-2, -2), this, 14, 0, 0, 0));
        pick.addView(Ui.text(this, getString(R.string.mins), 18, Ui.TEXT_2, false));
        pick.addView(pickS, Ui.margins(Ui.lp(-2, -2), this, 14, 0, 0, 0));
        pick.addView(Ui.text(this, getString(R.string.secs), 18, Ui.TEXT_2, false));
        content.addView(pick, Ui.margins(Ui.lp(-1, -2), this, 0, 30, 0, 30));
        LinearLayout start = Ui.pillButton(this, R.drawable.ic_play_arrow, getString(R.string.start), v -> {
            long ms = ((pickH.getValue() * 60L + pickM.getValue()) * 60L + pickS.getValue()) * 1000L;
            if (ms > 0) {
                Alarms.startTimer(this, ms, ms);
                render();
            }
        });
        LinearLayout.LayoutParams sl = Ui.lp(-2, -2);
        sl.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(start, sl);
    }

    private NumberPicker picker(int max) {
        NumberPicker p = new NumberPicker(this);
        p.setMinValue(0);
        p.setMaxValue(max);
        p.setFormatter(v -> String.format(Locale.ROOT, "%02d", v));
        p.setTextColor(Ui.TEXT);
        p.setTextSize(Ui.dp(this, 30));
        p.setSelectionDividerHeight(Ui.dp(this, 2));
        return p;
    }

    // ---- Stopwatch ----

    private void renderStopwatch() {
        long[] sw = Alarms.stopwatch(this);
        boolean running = sw[0] > 0;
        stopwatchText = Ui.text(this, "", 68, Ui.TEXT, false);
        stopwatchText.setGravity(Gravity.CENTER);
        stopwatchText.setTypeface(android.graphics.Typeface.create("sans-serif-light",
                android.graphics.Typeface.NORMAL));
        content.addView(stopwatchText, Ui.margins(Ui.lp(-1, -2), this, 0, 40, 0, 30));
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.CENTER);
        View second = Ui.roundButton(this, running ? R.drawable.ic_flag : R.drawable.ic_close, false, 60,
                getString(running ? R.string.lap : R.string.reset), v -> {
                    long[] s = Alarms.stopwatch(this);
                    if (s[0] > 0) {
                        long t = s[1] + System.currentTimeMillis() - s[0];
                        String laps = Alarms.laps(this);
                        Alarms.setLaps(this, laps.isEmpty() ? String.valueOf(t) : laps + "," + t);
                    } else {
                        Alarms.setStopwatch(this, 0, 0);
                        Alarms.setLaps(this, "");
                    }
                    render();
                });
        row.addView(second, Ui.lp(Ui.dp(this, 60), Ui.dp(this, 60)));
        View main = Ui.roundButton(this, running ? R.drawable.ic_pause : R.drawable.ic_play_arrow, true, 84,
                getString(running ? R.string.pause : R.string.start), v -> {
                    long[] s = Alarms.stopwatch(this);
                    if (s[0] > 0) {
                        Alarms.setStopwatch(this, 0, s[1] + System.currentTimeMillis() - s[0]);
                    } else {
                        Alarms.setStopwatch(this, System.currentTimeMillis(), s[1]);
                    }
                    render();
                });
        row.addView(main, Ui.margins(Ui.lp(Ui.dp(this, 84), Ui.dp(this, 84)), this, 30, 0, 0, 0));
        content.addView(row, Ui.lp(-1, -2));
        String laps = Alarms.laps(this);
        if (!laps.isEmpty()) {
            List<String> list = new ArrayList<>(Arrays.asList(laps.split(",")));
            long prev = 0;
            List<View> views = new ArrayList<>();
            for (int k = 0; k < list.size(); k++) {
                long t = Long.parseLong(list.get(k));
                LinearLayout r = card();
                r.addView(Ui.text(this, getString(R.string.lap_n, k + 1), 17, Ui.TEXT_2, false), Ui.lp(0, -2, 1));
                r.addView(Ui.text(this, "+" + format(t - prev, true), 15, Ui.TEXT_3, false),
                        Ui.margins(Ui.lp(-2, -2), this, 0, 0, 16, 0));
                r.addView(Ui.text(this, format(t, true), 18, Ui.TEXT, true));
                views.add(0, r);
                prev = t;
            }
            View spacer = new View(this);
            content.addView(spacer, Ui.lp(-1, Ui.dp(this, 20)));
            for (View v : views) content.addView(v);
        }
    }

    private static String format(long ms, boolean hundredths) {
        long s = ms / 1000;
        String base = s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
                : String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60);
        return hundredths ? base + String.format(Locale.ROOT, ".%02d", (ms / 10) % 100) : base;
    }

    // ---- Ticking ----

    private void update() {
        long now = System.currentTimeMillis();
        if (bigTime != null && (tab == ALARM || tab == CLOCK)) {
            bigTime.setText(DateFormat.getTimeFormat(this).format(new Date(now)));
            bigDate.setText(DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdM"),
                    now));
            if (tab == CLOCK) {
                for (int k = 0; k < content.getChildCount(); k++) {
                    View c = content.getChildAt(k);
                    if (c instanceof LinearLayout && ((LinearLayout) c).getChildCount() == 2) {
                        View t = ((LinearLayout) c).getChildAt(1);
                        if (t.getTag() instanceof TimeZone) {
                            java.text.DateFormat f = DateFormat.getTimeFormat(this);
                            f.setTimeZone((TimeZone) t.getTag());
                            ((TextView) t).setText(f.format(new Date(now)));
                        }
                    }
                }
            }
        }
        if (timerText != null) {
            long end = Alarms.timerEnd(this);
            long left = end > 0 ? end - now : Alarms.timerLeft(this);
            if (end > 0 && left <= 0) {
                render();
                return;
            }
            timerText.setText(format(Math.max(0, left) + 999, false));
        }
        if (stopwatchText != null) {
            long[] s = Alarms.stopwatch(this);
            long t = s[1] + (s[0] > 0 ? now - s[0] : 0);
            stopwatchText.setText(format(t, true));
        }
    }
}
