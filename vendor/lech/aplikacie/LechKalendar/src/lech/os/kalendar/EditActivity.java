/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.kalendar;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ContentUris;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Events;
import android.text.InputType;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import lech.os.ui.Ui;

import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Creates or edits an event: title, place, time, all day, reminder, repeat, colour and note. */
public class EditActivity extends Activity {
    static final String EXTRA_EVENT = "event";
    static final String EXTRA_BEGIN = "begin";
    private static final int[] REMINDERS = {-1, 0, 5, 10, 15, 30, 60, 1440};
    private static final String[] RULES = {null, "FREQ=DAILY", "FREQ=WEEKLY", "FREQ=MONTHLY", "FREQ=YEARLY"};
    private static final int[] RULE_NAMES = {R.string.rep_none, R.string.rep_daily, R.string.rep_weekly,
        R.string.rep_monthly, R.string.rep_yearly};

    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Cal.Event event = new Cal.Event();
    private boolean loaded;

    private EditText title;
    private EditText location;
    private EditText note;
    private Switch allDay;
    private TextView startDate;
    private TextView startTime;
    private TextView endDate;
    private TextView endTime;
    private TextView reminder;
    private TextView repeat;
    private LinearLayout colors;
    private View deleteButton;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.column(this);
        root.setBackground(Ui.background());
        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        bar.addView(Ui.roundButton(this, R.drawable.ic_close, false, 44, getString(R.string.cancel), v -> finish()));
        TextView head = Ui.text(this, getString(R.string.new_event), 22, Ui.TEXT, true);
        head.setPadding(Ui.dp(this, 14), 0, 0, 0);
        bar.addView(head, Ui.lp(0, -2, 1));
        deleteButton = Ui.roundButton(this, R.drawable.ic_delete, false, 44, getString(R.string.delete),
                v -> delete());
        bar.addView(deleteButton, Ui.margins(Ui.lp(Ui.dp(this, 44), Ui.dp(this, 44)), this, 0, 0, 10, 0));
        bar.addView(Ui.roundButton(this, R.drawable.ic_check, true, 48, getString(R.string.save), v -> save()));
        root.addView(bar, Ui.lp(-1, -2));

        ScrollView sc = new ScrollView(this);
        LinearLayout form = Ui.column(this);
        form.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 32));
        sc.addView(form, Ui.lp(-1, -2));
        root.addView(sc, Ui.lp(-1, 0, 1));

        title = Ui.field(this, getString(R.string.title_hint), InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        title.setTextSize(20);
        form.addView(title, Ui.margins(Ui.lp(-1, -2), this, 0, 4, 0, 10));
        location = Ui.field(this, getString(R.string.location_hint), InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        form.addView(location, Ui.margins(Ui.lp(-1, -2), this, 0, 0, 0, 10));

        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.glass(this, 20));
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        LinearLayout adRow = Ui.row(this);
        adRow.addView(Ui.text(this, getString(R.string.all_day), 17, Ui.TEXT, false), Ui.lp(0, -2, 1));
        allDay = new Switch(this);
        allDay.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
        allDay.setTrackTintList(new android.content.res.ColorStateList(new int[][] {{android.R.attr.state_checked},
            {}}, new int[] {Ui.GREEN, 0xFF37474F}));
        allDay.setOnCheckedChangeListener((b, on) -> {
            event.allDay = on;
            showTimes();
        });
        adRow.addView(allDay);
        card.addView(adRow, Ui.margins(Ui.lp(-1, -2), this, 0, 4, 0, 4));
        startDate = pickText();
        startTime = pickText();
        endDate = pickText();
        endTime = pickText();
        card.addView(timeRow(R.string.starts, startDate, startTime));
        card.addView(timeRow(R.string.ends, endDate, endTime));
        form.addView(card, Ui.margins(Ui.lp(-1, -2), this, 0, 0, 0, 10));
        startDate.setOnClickListener(v -> pickDate(true));
        endDate.setOnClickListener(v -> pickDate(false));
        startTime.setOnClickListener(v -> pickTime(true));
        endTime.setOnClickListener(v -> pickTime(false));

        reminder = pickText();
        repeat = pickText();
        form.addView(optionRow(R.drawable.ic_bell, R.string.reminder, reminder));
        form.addView(optionRow(R.drawable.ic_restore, R.string.repeat, repeat));
        reminder.setOnClickListener(this::pickReminder);
        repeat.setOnClickListener(this::pickRepeat);

        TextView ct = Ui.text(this, getString(R.string.color), 15, Ui.GREEN, true);
        form.addView(ct, Ui.margins(Ui.lp(-1, -2), this, 6, 12, 0, 6));
        colors = Ui.row(this);
        for (int col : Cal.COLORS) {
            View dot = new View(this);
            dot.setTag(col);
            dot.setOnClickListener(v -> {
                event.color = (Integer) v.getTag();
                showColors();
            });
            LinearLayout.LayoutParams lp = Ui.lp(Ui.dp(this, 40), Ui.dp(this, 40));
            lp.rightMargin = Ui.dp(this, 12);
            colors.addView(dot, lp);
        }
        form.addView(colors, Ui.margins(Ui.lp(-1, -2), this, 6, 0, 0, 12));

        note = Ui.field(this, getString(R.string.description_hint), InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        note.setMinLines(3);
        note.setGravity(Gravity.TOP);
        form.addView(note, Ui.lp(-1, -2));

        Ui.edgeToEdge(this, root);
        setContentView(root);

        Intent in = getIntent();
        long id = in.getLongExtra(EXTRA_EVENT, -1);
        Uri data = in.getData();
        if (id < 0 && data != null && (Intent.ACTION_EDIT.equals(in.getAction())
                || Intent.ACTION_VIEW.equals(in.getAction()))) {
            try {
                id = ContentUris.parseId(data);
            } catch (Exception ignored) {
                id = -1;
            }
        }
        if (id >= 0) {
            head.setText(R.string.edit_event);
            final long eid = id;
            bg.execute(() -> {
                Cal.Event e = null;
                try {
                    e = Cal.load(this, eid);
                } catch (Exception ignored) {
                    // No access.
                }
                final Cal.Event fe = e;
                main.post(() -> {
                    if (fe == null) {
                        finish();
                        return;
                    }
                    event = fe;
                    fill();
                });
            });
        } else {
            deleteButton.setVisibility(View.GONE);
            long begin = in.getLongExtra(EXTRA_BEGIN, in.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1));
            Calendar c = Calendar.getInstance();
            if (begin > 0) {
                Calendar b = Calendar.getInstance();
                b.setTimeInMillis(begin);
                c.set(b.get(Calendar.YEAR), b.get(Calendar.MONTH), b.get(Calendar.DAY_OF_MONTH));
            }
            if (begin <= 0 || in.hasExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME)) {
                c.add(Calendar.HOUR_OF_DAY, 1);
            } else {
                c.set(Calendar.HOUR_OF_DAY, Math.min(23, Calendar.getInstance().get(Calendar.HOUR_OF_DAY) + 1));
            }
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            if (in.hasExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME)) c.setTimeInMillis(begin);
            event.start = c.getTimeInMillis();
            long end = in.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1);
            event.end = end > event.start ? end : event.start + 3_600_000L;
            event.allDay = in.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false);
            String t = in.getStringExtra(Events.TITLE);
            if (t != null) event.title = t;
            String l = in.getStringExtra(Events.EVENT_LOCATION);
            if (l != null) event.location = l;
            String d = in.getStringExtra(Events.DESCRIPTION);
            if (d != null) event.description = d;
            event.reminder = Cal.defaultReminder(this);
            fill();
            title.requestFocus();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bg.shutdownNow();
    }

    private TextView pickText() {
        TextView t = Ui.text(this, "", 17, Ui.TEXT, true);
        t.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        Ui.clickable(t, Ui.shape(this, 0x22FFFFFF, 0, 0, 14));
        return t;
    }

    private View timeRow(int label, TextView date, TextView time) {
        LinearLayout r = Ui.row(this);
        r.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        r.addView(Ui.text(this, getString(label), 16, Ui.TEXT_2, false), Ui.lp(0, -2, 1));
        r.addView(date);
        r.addView(time, Ui.margins(Ui.lp(-2, -2), this, 8, 0, 0, 0));
        return r;
    }

    private View optionRow(int icon, int label, TextView value) {
        LinearLayout r = Ui.row(this);
        r.setBackground(Ui.glass(this, 20));
        r.setPadding(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        r.addView(Ui.icon(this, icon, Ui.GREEN, 24));
        TextView l = Ui.text(this, getString(label), 16, Ui.TEXT_2, false);
        l.setPadding(Ui.dp(this, 12), 0, 0, 0);
        r.addView(l, Ui.lp(0, -2, 1));
        r.addView(value);
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.bottomMargin = Ui.dp(this, 10);
        r.setLayoutParams(lp);
        return r;
    }

    private void fill() {
        title.setText(event.title);
        location.setText(event.location);
        note.setText(event.description);
        allDay.setChecked(event.allDay);
        showTimes();
        showOptions();
        showColors();
        loaded = true;
    }

    private void showTimes() {
        int df = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_ALL;
        startDate.setText(DateUtils.formatDateTime(this, event.start, df));
        endDate.setText(DateUtils.formatDateTime(this, event.allDay ? event.end - 1 : event.end, df));
        java.text.DateFormat tf = DateFormat.getTimeFormat(this);
        startTime.setText(tf.format(event.start));
        endTime.setText(tf.format(event.end));
        startTime.setVisibility(event.allDay ? View.GONE : View.VISIBLE);
        endTime.setVisibility(event.allDay ? View.GONE : View.VISIBLE);
    }

    private String reminderName(int m) {
        if (m < 0) return getString(R.string.rem_none);
        if (m == 0) return getString(R.string.rem_at);
        if (m == 60) return getString(R.string.rem_hour);
        if (m == 1440) return getString(R.string.rem_day);
        return getString(R.string.rem_min, m);
    }

    private void showOptions() {
        reminder.setText(reminderName(event.reminder));
        int k = 0;
        for (int i = 1; i < RULES.length; i++) {
            if (event.rrule != null && event.rrule.startsWith(RULES[i])) k = i;
        }
        repeat.setText(event.rrule != null && k == 0 ? event.rrule : getString(RULE_NAMES[k]));
    }

    private void showColors() {
        for (int k = 0; k < colors.getChildCount(); k++) {
            View d = colors.getChildAt(k);
            int col = (Integer) d.getTag();
            d.setBackground(Ui.circle(col, 0xFFFFFFFF, col == event.color ? Ui.dp(this, 3) : 0));
        }
    }

    private void pickDate(boolean start) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(start ? event.start : (event.allDay ? event.end - 1 : event.end));
        new DatePickerDialog(this, (v, y, m, d) -> {
            Calendar n = Calendar.getInstance();
            n.setTimeInMillis(start ? event.start : event.end);
            n.set(y, m, d);
            if (start) {
                long len = event.end - event.start;
                event.start = n.getTimeInMillis();
                event.end = event.start + Math.max(len, event.allDay ? 86_400_000L : 0);
            } else {
                event.end = n.getTimeInMillis() + (event.allDay ? 86_400_000L : 0);
            }
            showTimes();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void pickTime(boolean start) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(start ? event.start : event.end);
        new TimePickerDialog(this, (v, h, m) -> {
            Calendar n = Calendar.getInstance();
            n.setTimeInMillis(start ? event.start : event.end);
            n.set(Calendar.HOUR_OF_DAY, h);
            n.set(Calendar.MINUTE, m);
            if (start) {
                long len = event.end - event.start;
                event.start = n.getTimeInMillis();
                event.end = event.start + Math.max(len, 0);
            } else {
                event.end = n.getTimeInMillis();
            }
            showTimes();
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), DateFormat.is24HourFormat(this)).show();
    }

    private void pickReminder(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        for (int k = 0; k < REMINDERS.length; k++) m.getMenu().add(0, k, k, reminderName(REMINDERS[k]));
        m.setOnMenuItemClickListener(item -> {
            event.reminder = REMINDERS[item.getItemId()];
            showOptions();
            return true;
        });
        m.show();
    }

    private void pickRepeat(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        for (int k = 0; k < RULES.length; k++) m.getMenu().add(0, k, k, RULE_NAMES[k]);
        m.setOnMenuItemClickListener(item -> {
            event.rrule = RULES[item.getItemId()];
            showOptions();
            return true;
        });
        m.show();
    }

    private void save() {
        if (!loaded) return;
        event.title = title.getText().toString().trim();
        event.location = location.getText().toString().trim();
        event.description = note.getText().toString().trim();
        if (TextUtils.isEmpty(event.title)) {
            Toast.makeText(this, R.string.title_needed, Toast.LENGTH_SHORT).show();
            return;
        }
        if (event.allDay) {
            Calendar s = Calendar.getInstance();
            s.setTimeInMillis(event.start);
            s.set(Calendar.HOUR_OF_DAY, 0);
            s.set(Calendar.MINUTE, 0);
            s.set(Calendar.SECOND, 0);
            s.set(Calendar.MILLISECOND, 0);
            event.start = s.getTimeInMillis();
            Calendar e = Calendar.getInstance();
            e.setTimeInMillis(Math.max(event.end - 1, event.start));
            e.set(Calendar.HOUR_OF_DAY, 0);
            e.set(Calendar.MINUTE, 0);
            e.set(Calendar.SECOND, 0);
            e.set(Calendar.MILLISECOND, 0);
            e.add(Calendar.DAY_OF_YEAR, 1);
            event.end = e.getTimeInMillis();
        }
        if (event.end < event.start) {
            Toast.makeText(this, R.string.end_before_start, Toast.LENGTH_SHORT).show();
            return;
        }
        final Cal.Event e = event;
        bg.execute(() -> {
            long id;
            try {
                id = Cal.save(this, e);
            } catch (Exception ex) {
                id = -1;
            }
            final long fid = id;
            main.post(() -> {
                Toast.makeText(this, fid >= 0 ? R.string.saved : R.string.save_failed, Toast.LENGTH_SHORT).show();
                if (fid >= 0) {
                    setResult(RESULT_OK);
                    finish();
                }
            });
        });
    }

    private void delete() {
        if (event.id < 0) return;
        new AlertDialog.Builder(this)
                .setMessage(TextUtils.isEmpty(event.rrule) ? R.string.delete_event_q : R.string.delete_series_q)
                .setPositiveButton(R.string.delete, (d, w) -> bg.execute(() -> {
                    try {
                        Cal.delete(this, event.id);
                    } catch (Exception ignored) {
                        // Read-only calendar.
                    }
                    main.post(this::finish);
                }))
                .setNegativeButton(R.string.cancel, null).show();
    }
}
