/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.kalendar;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.ContentObserver;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import lech.os.ui.Ui;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The calendar: month, week, day and agenda views, reminders, tasks; events of the calendar provider. */
public class MainActivity extends Activity {
    private static final int MONTH = 0;
    private static final int WEEK = 1;
    private static final int DAY = 2;
    private static final int AGENDA = 3;
    private static final int NAV_CALENDAR = 0;
    private static final int NAV_REMINDERS = 1;
    private static final int NAV_TASKS = 2;
    private static final int NAV_PLAN = 3;
    private static final long DAY_MS = 86_400_000L;
    private static final String[] PERMISSIONS = {
        Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR, Manifest.permission.POST_NOTIFICATIONS,
    };

    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private int generation;
    private int view = MONTH;
    private int nav = NAV_CALENDAR;
    private final Calendar selected = Calendar.getInstance();
    private String query = "";

    private Ui.Header header;
    private View searchBox;
    private EditText search;
    private LinearLayout viewBar;
    private final List<LinearLayout> viewTabs = new ArrayList<>();
    private final List<LinearLayout> navItems = new ArrayList<>();
    private LinearLayout content;
    private ScrollView scroll;
    private View fab;

    private final ContentObserver observer = new ContentObserver(main) {
        @Override
        public void onChange(boolean selfChange) {
            reload();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        clearTime(selected);
        LinearLayout root = Ui.column(this);
        root.setBackground(Ui.background());
        header = Ui.header(this, R.mipmap.ic_kalendar);
        header.title.setText(R.string.app_name);
        header.subtitle.setText(R.string.subtitle);
        Ui.headerButton(header, R.drawable.ic_search, false, getString(R.string.search), v -> toggleSearch());
        Ui.headerButton(header, R.drawable.ic_today, false, getString(R.string.go_today), v -> {
            selected.setTimeInMillis(System.currentTimeMillis());
            clearTime(selected);
            reload();
        });
        root.addView(header.view, Ui.lp(-1, -2));

        LinearLayout box = Ui.row(this);
        box.setBackground(Ui.shape(this, 0xCC08202B, 0xFF5FC21A, 1.5f, 30));
        box.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 10), 0);
        box.addView(Ui.icon(this, R.drawable.ic_search, Ui.TEXT_2, 24));
        search = new EditText(this);
        search.setHint(R.string.search_hint);
        search.setBackground(null);
        search.setSingleLine(true);
        search.setTextColor(Ui.TEXT);
        search.setHintTextColor(Ui.TEXT_2);
        search.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim();
                reload();
            }
        });
        box.addView(search, Ui.lp(0, -2, 1));
        searchBox = box;
        box.setVisibility(View.GONE);
        root.addView(box, Ui.margins(Ui.lp(-1, -2), this, 16, 0, 16, 6));

        viewBar = Ui.row(this);
        viewBar.setBackground(Ui.shape(this, 0xCC0A2530, 0xAA34A0A0, 1.2f, 30));
        viewBar.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        addView(MONTH, R.drawable.ic_event, R.string.view_month);
        addView(WEEK, R.drawable.ic_view_week, R.string.view_week);
        addView(DAY, R.drawable.ic_today, R.string.view_day);
        addView(AGENDA, R.drawable.ic_list, R.string.view_agenda);
        root.addView(viewBar, Ui.margins(Ui.lp(-1, -2), this, 14, 0, 14, 4));

        FrameLayout pages = new FrameLayout(this);
        scroll = new ScrollView(this);
        content = Ui.column(this);
        content.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 96));
        scroll.addView(content, Ui.lp(-1, -2));
        pages.addView(scroll, Ui.match());
        fab = Ui.roundButton(this, R.drawable.ic_add, true, 68, getString(R.string.new_event), v -> add());
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(Ui.dp(this, 68), Ui.dp(this, 68),
                Gravity.BOTTOM | Gravity.END);
        fl.setMargins(0, 0, Ui.dp(this, 20), Ui.dp(this, 12));
        pages.addView(fab, fl);
        root.addView(pages, Ui.lp(-1, 0, 1));

        LinearLayout bar = Ui.navBar(this);
        navItems.add(Ui.navItem(bar, R.drawable.ic_event, getString(R.string.nav_calendar), v -> setNav(NAV_CALENDAR)));
        navItems.add(Ui.navItem(bar, R.drawable.ic_bell, getString(R.string.nav_reminders),
                v -> setNav(NAV_REMINDERS)));
        navItems.add(Ui.navItem(bar, R.drawable.ic_check_circle, getString(R.string.nav_tasks),
                v -> setNav(NAV_TASKS)));
        navItems.add(Ui.navItem(bar, R.drawable.ic_today, getString(R.string.nav_plan), v -> setNav(NAV_PLAN)));
        Ui.navItem(bar, R.drawable.ic_settings, getString(R.string.nav_settings), v -> settings());
        root.addView(bar, Ui.margins(Ui.lp(-1, -2), this, 12, 4, 12, 10));

        Ui.edgeToEdge(this, root);
        setContentView(root);
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> {
                    if (searchBox.getVisibility() == View.VISIBLE) {
                        toggleSearch();
                    } else if (nav != NAV_CALENDAR || view != MONTH) {
                        view = MONTH;
                        setNav(NAV_CALENDAR);
                    } else {
                        finish();
                    }
                });
        if (!granted()) requestPermissions(PERMISSIONS, 1);
        setNav(NAV_CALENDAR);
    }

    private boolean granted() {
        return checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            getContentResolver().registerContentObserver(CalendarContract.CONTENT_URI, true, observer);
        } catch (SecurityException ignored) {
            // No permission yet.
        }
        reload();
    }

    @Override
    protected void onStop() {
        super.onStop();
        getContentResolver().unregisterContentObserver(observer);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bg.shutdownNow();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        try {
            getContentResolver().registerContentObserver(CalendarContract.CONTENT_URI, true, observer);
        } catch (SecurityException ignored) {
            // Still denied.
        }
        reload();
    }

    private static void clearTime(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }

    private void addView(int which, int icon, int label) {
        LinearLayout t = Ui.row(this);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10));
        t.addView(Ui.icon(this, icon, Ui.TEXT, 22));
        TextView l = Ui.text(this, getString(label), 15, Ui.TEXT, true);
        l.setPadding(Ui.dp(this, 6), 0, 0, 0);
        l.setSingleLine(true);
        t.addView(l);
        t.setOnClickListener(v -> {
            view = which;
            setNav(NAV_CALENDAR);
        });
        viewBar.addView(t, Ui.lp(0, -2, 1));
        viewTabs.add(t);
    }

    private void setNav(int which) {
        nav = which;
        if (which == NAV_PLAN) view = AGENDA;
        for (int k = 0; k < navItems.size(); k++) Ui.setNav(navItems.get(k), k == which);
        for (int k = 0; k < viewTabs.size(); k++) {
            LinearLayout t = viewTabs.get(k);
            boolean on = (which == NAV_CALENDAR || which == NAV_PLAN) && k == view;
            Ui.clickable(t, on ? Ui.greenGlow(this, 26) : Ui.shape(this, 0, 0, 0, 26));
            int color = on ? Ui.ON_GREEN : Ui.TEXT;
            ((ImageView) t.getChildAt(0)).setImageTintList(ColorStateList.valueOf(color));
            ((TextView) t.getChildAt(1)).setTextColor(color);
        }
        viewBar.setVisibility(which == NAV_CALENDAR || which == NAV_PLAN ? View.VISIBLE : View.GONE);
        reload();
        scroll.scrollTo(0, 0);
    }

    private void toggleSearch() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (searchBox.getVisibility() == View.VISIBLE) {
            searchBox.setVisibility(View.GONE);
            search.setText("");
            imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        } else {
            searchBox.setVisibility(View.VISIBLE);
            search.requestFocus();
            imm.showSoftInput(search, 0);
        }
    }

    private void add() {
        if (nav == NAV_TASKS) {
            addTask();
            return;
        }
        startActivity(new Intent(this, EditActivity.class).putExtra(EditActivity.EXTRA_BEGIN,
                selected.getTimeInMillis()));
    }

    private void open(Cal.Item it) {
        startActivity(new Intent(this, EditActivity.class).putExtra(EditActivity.EXTRA_EVENT, it.eventId));
    }

    private void settings() {
        int[] values = {-1, 0, 5, 10, 15, 30, 60, 1440};
        String[] names = new String[values.length];
        int current = 0;
        for (int k = 0; k < values.length; k++) {
            int m = values[k];
            names[k] = m < 0 ? getString(R.string.rem_none) : m == 0 ? getString(R.string.rem_at)
                    : m == 60 ? getString(R.string.rem_hour) : m == 1440 ? getString(R.string.rem_day)
                    : getString(R.string.rem_min, m);
            if (m == Cal.defaultReminder(this)) current = k;
        }
        new AlertDialog.Builder(this).setTitle(R.string.default_reminder)
                .setSingleChoiceItems(names, current, (d, w) -> {
                    Cal.setDefaultReminder(this, values[w]);
                    d.dismiss();
                })
                .setNegativeButton(R.string.cancel, null).show();
    }

    // ---- Loading ----

    private void reload() {
        if (content == null) return;
        if (nav == NAV_TASKS) {
            renderTasks();
            return;
        }
        if (!granted()) {
            content.removeAllViews();
            TextView t = Ui.text(this, getString(R.string.permission_needed), 17, Ui.TEXT, false);
            t.setGravity(Gravity.CENTER);
            content.addView(t, Ui.margins(Ui.lp(-1, -2), this, 0, 40, 0, 16));
            LinearLayout b = Ui.pillButton(this, 0, getString(R.string.permission_grant),
                    v -> requestPermissions(PERMISSIONS, 1));
            LinearLayout.LayoutParams lp = Ui.lp(-2, -2);
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            content.addView(b, lp);
            return;
        }
        final int gen = ++generation;
        final long[] range = range();
        final String q = query;
        bg.execute(() -> {
            List<Cal.Item> items;
            try {
                items = Cal.items(this, range[0], range[1], q);
            } catch (Exception e) {
                items = new ArrayList<>();
            }
            final List<Cal.Item> fItems = items;
            main.post(() -> {
                if (gen != generation || isDestroyed()) return;
                render(fItems, range);
            });
        });
    }

    /** The time range the current view needs. */
    private long[] range() {
        Calendar c = (Calendar) selected.clone();
        if (!query.isEmpty()) {
            Calendar from = Calendar.getInstance();
            clearTime(from);
            from.add(Calendar.YEAR, -1);
            Calendar to = (Calendar) from.clone();
            to.add(Calendar.YEAR, 3);
            return new long[] {from.getTimeInMillis(), to.getTimeInMillis()};
        }
        if (nav == NAV_REMINDERS || view == AGENDA) {
            Calendar from = Calendar.getInstance();
            clearTime(from);
            if (view == AGENDA && nav != NAV_REMINDERS) from.setTimeInMillis(selected.getTimeInMillis());
            Calendar to = (Calendar) from.clone();
            to.add(Calendar.DAY_OF_YEAR, nav == NAV_REMINDERS ? 60 : 31);
            return new long[] {from.getTimeInMillis(), to.getTimeInMillis()};
        }
        if (view == MONTH) {
            c.set(Calendar.DAY_OF_MONTH, 1);
            int back = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
            c.add(Calendar.DAY_OF_YEAR, -back);
            long from = c.getTimeInMillis();
            c.add(Calendar.DAY_OF_YEAR, 42);
            return new long[] {from, c.getTimeInMillis()};
        }
        if (view == WEEK) {
            int back = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
            c.add(Calendar.DAY_OF_YEAR, -back);
            long from = c.getTimeInMillis();
            c.add(Calendar.DAY_OF_YEAR, 7);
            return new long[] {from, c.getTimeInMillis()};
        }
        long from = c.getTimeInMillis();
        c.add(Calendar.DAY_OF_YEAR, 1);
        return new long[] {from, c.getTimeInMillis()};
    }

    private void render(List<Cal.Item> items, long[] range) {
        content.removeAllViews();
        if (!query.isEmpty()) {
            agenda(items, R.string.empty_search);
            return;
        }
        if (nav == NAV_REMINDERS) {
            List<Cal.Item> withAlarm = new ArrayList<>();
            for (Cal.Item it : items) if (it.hasAlarm && it.end >= System.currentTimeMillis()) withAlarm.add(it);
            agenda(withAlarm, R.string.no_reminders);
            return;
        }
        if (view == MONTH) {
            month(items, range[0]);
            dayList(items, selected.getTimeInMillis());
        } else if (view == WEEK) {
            navigator(DateUtils.formatDateRange(this, range[0], range[1] - 1, DateUtils.FORMAT_SHOW_DATE
                    | DateUtils.FORMAT_ABBREV_MONTH), 7);
            for (long d = range[0]; d < range[1]; d = addDays(d, 1)) {
                dayHeading(d);
                List<Cal.Item> of = on(items, d);
                if (of.isEmpty()) {
                    TextView e = Ui.text(this, "—", 15, Ui.TEXT_3, false);
                    e.setPadding(Ui.dp(this, 20), 0, 0, Ui.dp(this, 4));
                    content.addView(e);
                }
                for (Cal.Item it : of) content.addView(eventRow(it));
            }
        } else if (view == DAY) {
            navigator(DateUtils.formatDateTime(this, range[0], DateUtils.FORMAT_SHOW_DATE
                    | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_YEAR), 1);
            dayList(items, range[0]);
        } else {
            agenda(items, R.string.no_events);
        }
    }

    private static long addDays(long t, int days) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.add(Calendar.DAY_OF_YEAR, days);
        return c.getTimeInMillis();
    }

    private static List<Cal.Item> on(List<Cal.Item> items, long dayStart) {
        long dayEnd = addDays(dayStart, 1);
        List<Cal.Item> out = new ArrayList<>();
        for (Cal.Item it : items) {
            if (it.begin < dayEnd && (it.end > dayStart || it.begin >= dayStart)) out.add(it);
        }
        return out;
    }

    /** Month name with the previous / next arrows; step: 0 = month, 7 = week, 1 = day. */
    private void navigator(CharSequence title, int step) {
        LinearLayout r = Ui.row(this);
        r.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 6));
        r.addView(Ui.roundButton(this, R.drawable.ic_chevron_left, false, 48, getString(R.string.prev),
                v -> move(step, -1)));
        TextView t = Ui.text(this, title, 24, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setOnClickListener(v -> {
            selected.setTimeInMillis(System.currentTimeMillis());
            clearTime(selected);
            reload();
        });
        r.addView(t, Ui.lp(0, -2, 1));
        r.addView(Ui.roundButton(this, R.drawable.ic_chevron_right, false, 48, getString(R.string.next),
                v -> move(step, 1)));
        content.addView(r, Ui.lp(-1, -2));
    }

    private void move(int step, int dir) {
        if (step == 0) {
            selected.set(Calendar.DAY_OF_MONTH, 1);
            selected.add(Calendar.MONTH, dir);
        } else {
            selected.add(Calendar.DAY_OF_YEAR, step * dir);
        }
        reload();
    }

    private void month(List<Cal.Item> items, long gridStart) {
        String name = DateFormat.format("LLLL yyyy", selected).toString();
        navigator(name.substring(0, 1).toUpperCase(Locale.getDefault()) + name.substring(1), 0);
        String[] names = getString(R.string.days_short).split(",");
        LinearLayout head = Ui.row(this);
        for (int k = 0; k < 7; k++) {
            TextView t = Ui.text(this, names[k], 17, k >= 5 ? Ui.GREEN : Ui.TEXT, true);
            t.setGravity(Gravity.CENTER);
            head.addView(t, Ui.lp(0, -2, 1));
        }
        content.addView(head, Ui.margins(Ui.lp(-1, -2), this, 0, 4, 0, 4));
        View line = new View(this);
        line.setBackgroundColor(0x3334A0A0);
        content.addView(line, Ui.lp(-1, Ui.dp(this, 1)));
        Calendar today = Calendar.getInstance();
        clearTime(today);
        int month = selected.get(Calendar.MONTH);
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(gridStart);
        for (int w = 0; w < 6; w++) {
            LinearLayout row = Ui.row(this);
            boolean anyInMonth = false;
            for (int k = 0; k < 7; k++) {
                long day = c.getTimeInMillis();
                boolean inMonth = c.get(Calendar.MONTH) == month;
                anyInMonth |= inMonth;
                LinearLayout cell = Ui.column(this);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 4));
                TextView n = Ui.text(this, String.valueOf(c.get(Calendar.DAY_OF_MONTH)), 20,
                        !inMonth ? Ui.TEXT_3 : k >= 5 ? Ui.GREEN_LIGHT : Ui.TEXT, false);
                n.setGravity(Gravity.CENTER);
                boolean isToday = day == today.getTimeInMillis();
                boolean isSel = day == selected.getTimeInMillis();
                if (isToday) {
                    GradientDrawable g = Ui.greenGlow(this, 14);
                    g.setColors(new int[] {0xFF2E6A0E, 0xFF184A06});
                    n.setBackground(g);
                    n.setTypeface(null, android.graphics.Typeface.BOLD);
                } else if (isSel) {
                    n.setBackground(Ui.shape(this, 0x22FFFFFF, Ui.GREEN, 1.5f, 14));
                }
                cell.addView(n, Ui.lp(Ui.dp(this, 44), Ui.dp(this, 36)));
                LinearLayout dots = Ui.row(this);
                dots.setGravity(Gravity.CENTER);
                int count = 0;
                for (Cal.Item it : on(items, day)) {
                    if (count++ >= 3) break;
                    View d = new View(this);
                    d.setBackground(Ui.circle(it.color == 0 ? Ui.GREEN : it.color | 0xFF000000, 0, 0));
                    LinearLayout.LayoutParams dl = Ui.lp(Ui.dp(this, 7), Ui.dp(this, 7));
                    dl.setMargins(Ui.dp(this, 1), 0, Ui.dp(this, 1), 0);
                    dots.addView(d, dl);
                }
                cell.addView(dots, Ui.lp(-1, Ui.dp(this, 9)));
                cell.setOnClickListener(v -> {
                    selected.setTimeInMillis(day);
                    reload();
                });
                cell.setOnLongClickListener(v -> {
                    selected.setTimeInMillis(day);
                    add();
                    return true;
                });
                row.addView(cell, Ui.lp(0, -2, 1));
                c.add(Calendar.DAY_OF_YEAR, 1);
            }
            if (w < 5 || anyInMonth) content.addView(row, Ui.lp(-1, -2));
        }
        View line2 = new View(this);
        line2.setBackgroundColor(0x3334A0A0);
        content.addView(line2, Ui.margins(Ui.lp(-1, Ui.dp(this, 1)), this, 0, 4, 0, 0));
    }

    private void dayHeading(long day) {
        LinearLayout r = Ui.row(this);
        r.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 6));
        View bar = new View(this);
        bar.setBackground(Ui.shape(this, Ui.GREEN, 0, 0, 2));
        r.addView(bar, Ui.lp(Ui.dp(this, 4), Ui.dp(this, 24)));
        boolean today = DateUtils.isToday(day);
        String date = DateUtils.formatDateTime(this, day, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_WEEKDAY
                | DateUtils.FORMAT_ABBREV_WEEKDAY | DateUtils.FORMAT_NUMERIC_DATE | DateUtils.FORMAT_NO_YEAR);
        TextView t = Ui.text(this, today ? getString(R.string.events_today, date) : getString(R.string.events_on,
                date), 20, Ui.TEXT, true);
        t.setPadding(Ui.dp(this, 12), 0, 0, 0);
        r.addView(t);
        content.addView(r, Ui.lp(-1, -2));
    }

    private void dayList(List<Cal.Item> items, long day) {
        dayHeading(day);
        List<Cal.Item> of = on(items, day);
        if (of.isEmpty()) {
            TextView e = Ui.text(this, getString(R.string.no_events), 15, Ui.TEXT_3, false);
            e.setPadding(Ui.dp(this, 16), Ui.dp(this, 6), 0, 0);
            content.addView(e);
        }
        for (Cal.Item it : of) content.addView(eventRow(it));
    }

    private void agenda(List<Cal.Item> items, int emptyText) {
        if (items.isEmpty()) {
            TextView e = Ui.text(this, getString(emptyText), 16, Ui.TEXT_2, false);
            e.setGravity(Gravity.CENTER);
            content.addView(e, Ui.margins(Ui.lp(-1, -2), this, 0, 30, 0, 0));
            return;
        }
        long lastDay = -1;
        for (Cal.Item it : items) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(it.begin);
            clearTime(c);
            if (c.getTimeInMillis() != lastDay) {
                lastDay = c.getTimeInMillis();
                dayHeading(lastDay);
            }
            content.addView(eventRow(it));
        }
    }

    private View eventRow(Cal.Item it) {
        int color = it.color == 0 ? Ui.GREEN : it.color | 0xFF000000;
        LinearLayout r = Ui.row(this);
        Ui.clickable(r, Ui.shape(this, 0xCC0A2530, 0xAA34A0A0, 1.2f, 24));
        r.setPadding(0, 0, Ui.dp(this, 12), 0);
        View bar = new View(this);
        bar.setBackground(Ui.shape(this, color, 0, 0, 3));
        r.addView(bar, Ui.margins(Ui.lp(Ui.dp(this, 5), Ui.dp(this, 46)), this, 10, 0, 0, 0));
        String time = it.allDay ? getString(R.string.all_day) : DateFormat.getTimeFormat(this).format(it.begin)
                + " – " + DateFormat.getTimeFormat(this).format(it.end);
        TextView tt = Ui.text(this, time, 15, Ui.TEXT, true);
        tt.setPadding(Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16));
        tt.setMinWidth(Ui.dp(this, 118));
        r.addView(tt);
        FrameLayout ic = new FrameLayout(this);
        ic.setBackground(Ui.shape(this, 0x33000000, 0, 0, 14));
        ic.addView(Ui.icon(this, Cal.iconFor(it.title), color, 28), new FrameLayout.LayoutParams(Ui.dp(this, 28),
                Ui.dp(this, 28), Gravity.CENTER));
        r.addView(ic, Ui.lp(Ui.dp(this, 52), Ui.dp(this, 52)));
        LinearLayout t = Ui.column(this);
        t.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), 0, Ui.dp(this, 8));
        TextView n = Ui.text(this, TextUtils.isEmpty(it.title) ? "—" : it.title, 17, Ui.TEXT, true);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(n);
        if (!TextUtils.isEmpty(it.location)) {
            LinearLayout loc = Ui.row(this);
            loc.addView(Ui.icon(this, R.drawable.ic_place, Ui.TEXT_2, 15));
            TextView l = Ui.text(this, it.location, 14, Ui.TEXT_2, false);
            l.setSingleLine(true);
            l.setEllipsize(TextUtils.TruncateAt.END);
            l.setPadding(Ui.dp(this, 4), 0, 0, 0);
            loc.addView(l);
            t.addView(loc);
        }
        if (it.hasAlarm && nav == NAV_REMINDERS) {
            t.addView(Ui.text(this, DateUtils.formatDateTime(this, it.begin, DateUtils.FORMAT_SHOW_DATE
                    | DateUtils.FORMAT_ABBREV_ALL), 13, Ui.TEXT_3, false));
        }
        r.addView(t, Ui.lp(0, -2, 1));
        r.setOnClickListener(v -> open(it));
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.bottomMargin = Ui.dp(this, 8);
        r.setLayoutParams(lp);
        return r;
    }

    // ---- Tasks ----

    private void renderTasks() {
        content.removeAllViews();
        List<Cal.Task> tasks = Cal.tasks(this);
        if (tasks.isEmpty()) {
            TextView e = Ui.text(this, getString(R.string.no_tasks), 16, Ui.TEXT_2, false);
            e.setGravity(Gravity.CENTER);
            content.addView(e, Ui.margins(Ui.lp(-1, -2), this, 0, 30, 0, 0));
            return;
        }
        boolean anyDone = false;
        for (Cal.Task t : tasks) {
            anyDone |= t.done;
            LinearLayout r = Ui.row(this);
            r.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
            Ui.clickable(r, Ui.shape(this, 0xCC0A2530, 0xAA34A0A0, 1.2f, 24));
            r.addView(Ui.icon(this, t.done ? R.drawable.ic_check_box : R.drawable.ic_check_box_blank,
                    t.done ? Ui.GREEN : Ui.TEXT_2, 28));
            TextView n = Ui.text(this, t.title, 17, t.done ? Ui.TEXT_3 : Ui.TEXT, !t.done);
            if (t.done) n.setPaintFlags(n.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            n.setPadding(Ui.dp(this, 14), 0, 0, 0);
            r.addView(n, Ui.lp(0, -2, 1));
            r.setOnClickListener(v -> {
                List<Cal.Task> all = Cal.tasks(this);
                for (Cal.Task x : all) if (x.id == t.id) x.done = !x.done;
                Cal.saveTasks(this, all);
                renderTasks();
            });
            r.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setMessage(t.title)
                        .setPositiveButton(R.string.delete, (d, w) -> {
                            List<Cal.Task> all = Cal.tasks(this);
                            all.removeIf(x -> x.id == t.id);
                            Cal.saveTasks(this, all);
                            renderTasks();
                        })
                        .setNegativeButton(R.string.cancel, null).show();
                return true;
            });
            LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
            lp.bottomMargin = Ui.dp(this, 8);
            content.addView(r, lp);
        }
        if (anyDone) {
            TextView clear = Ui.text(this, getString(R.string.delete_done), 15, Ui.GREEN, true);
            clear.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
            Ui.clickable(clear, null);
            clear.setOnClickListener(v -> {
                List<Cal.Task> all = Cal.tasks(this);
                all.removeIf(x -> x.done);
                Cal.saveTasks(this, all);
                renderTasks();
            });
            content.addView(clear, Ui.lp(-2, -2));
        }
    }

    private void addTask() {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setHint(R.string.task_hint);
        FrameLayout f = new FrameLayout(this);
        int p = Ui.dp(this, 20);
        f.setPadding(p, Ui.dp(this, 8), p, 0);
        f.addView(e);
        new AlertDialog.Builder(this).setTitle(R.string.add_task).setView(f)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    String s = e.getText().toString().trim();
                    if (s.isEmpty()) return;
                    List<Cal.Task> all = Cal.tasks(this);
                    Cal.Task t = new Cal.Task();
                    t.id = System.currentTimeMillis();
                    t.title = s;
                    all.add(0, t);
                    Cal.saveTasks(this, all);
                    renderTasks();
                })
                .setNegativeButton(R.string.cancel, null).show();
    }
}
