/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.spravy;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.provider.Telephony;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import lech.os.ui.Ui;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Conversations (all, SMS, MMS) and blocked numbers, in the LECH OS design. */
public class MainActivity extends Activity {
    private static final int CHIP_ALL = 0;
    private static final int CHIP_SMS = 1;
    private static final int CHIP_MMS = 2;
    private static final int CHIP_BLOCKED = 3;
    private static final int REQ_PERMISSIONS = 1;
    private static final int REQ_ROLE = 2;
    static final String[] PERMISSIONS = {
        Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_CONTACTS, Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.READ_PHONE_STATE,
    };

    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private int generation;
    private int chip = CHIP_ALL;
    private String query = "";

    private Ui.Header header;
    private View searchBox;
    private EditText search;
    private final List<LinearLayout> chips = new ArrayList<>();
    private ListView list;
    private final Adapter adapter = new Adapter();
    private LinearLayout empty;
    private TextView emptyTitle;
    private TextView emptyText;
    private LinearLayout defaultBanner;

    private final ContentObserver observer = new ContentObserver(main) {
        @Override
        public void onChange(boolean selfChange) {
            reload();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.column(this);
        root.setBackground(Ui.background());
        header = Ui.header(this, R.mipmap.ic_spravy);
        header.title.setText(R.string.app_name);
        header.subtitle.setText(R.string.subtitle);
        Ui.headerButton(header, R.drawable.ic_search, false, getString(R.string.search), v -> toggleSearch());
        Ui.headerButton(header, R.drawable.ic_more, false, getString(R.string.more), this::showMenu);
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
        search.setTextSize(17);
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

        defaultBanner = Ui.column(this);
        defaultBanner.setBackground(Ui.shape(this, Ui.GLASS_STRONG, Ui.GREEN, 1.5f, 22));
        defaultBanner.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        defaultBanner.addView(Ui.text(this, getString(R.string.default_title), 17, Ui.TEXT, true));
        defaultBanner.addView(Ui.text(this, getString(R.string.default_text), 14, Ui.TEXT_2, false));
        LinearLayout setDefault = Ui.pillButton(this, 0, getString(R.string.default_set), v -> askDefault());
        setDefault.setPadding(Ui.dp(this, 20), Ui.dp(this, 10), Ui.dp(this, 20), Ui.dp(this, 10));
        defaultBanner.addView(setDefault, Ui.margins(Ui.lp(-2, -2), this, 0, 10, 0, 0));
        root.addView(defaultBanner, Ui.margins(Ui.lp(-1, -2), this, 14, 0, 14, 8));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = Ui.row(this);
        chipRow.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 8), 0);
        addChip(chipRow, CHIP_ALL, R.drawable.ic_message, R.string.chip_all);
        addChip(chipRow, CHIP_SMS, R.drawable.ic_sms, R.string.chip_sms);
        addChip(chipRow, CHIP_MMS, R.drawable.ic_photo, R.string.chip_mms);
        addChip(chipRow, CHIP_BLOCKED, R.drawable.ic_shield, R.string.chip_blocked);
        hs.addView(chipRow);
        root.addView(hs, Ui.margins(Ui.lp(-1, -2), this, 0, 2, 0, 6));

        FrameLayout content = new FrameLayout(this);
        list = new ListView(this);
        list.setDivider(null);
        list.setSelector(new android.graphics.drawable.ColorDrawable(0));
        list.setClipToPadding(false);
        list.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 90));
        list.setAdapter(adapter);
        content.addView(list, Ui.match());
        empty = Ui.column(this);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(Ui.dp(this, 32), 0, Ui.dp(this, 32), Ui.dp(this, 60));
        emptyTitle = Ui.text(this, "", 21, Ui.TEXT, true);
        emptyTitle.setGravity(Gravity.CENTER);
        empty.addView(emptyTitle);
        emptyText = Ui.text(this, "", 16, Ui.TEXT_2, false);
        emptyText.setGravity(Gravity.CENTER);
        empty.addView(emptyText, Ui.margins(Ui.lp(-1, -2), this, 0, 8, 0, 0));
        content.addView(empty, Ui.match());
        View fab = Ui.roundButton(this, R.drawable.ic_edit, true, 64, getString(R.string.new_message), v -> {
            if (chip == CHIP_BLOCKED) {
                addBlocked();
            } else {
                startActivity(new Intent(this, ConversationActivity.class));
            }
        });
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64),
                Gravity.BOTTOM | Gravity.END);
        fl.setMargins(0, 0, Ui.dp(this, 22), Ui.dp(this, 14));
        content.addView(fab, fl);
        root.addView(content, Ui.lp(-1, 0, 1));

        LinearLayout nav = Ui.navBar(this);
        LinearLayout messages = Ui.navItem(nav, R.drawable.ic_message, getString(R.string.nav_messages), v -> {
            chip = CHIP_ALL;
            updateChips();
            reload();
        });
        Ui.setNav(messages, true);
        Ui.navItem(nav, R.drawable.ic_group, getString(R.string.nav_contacts), v -> {
            // LECH Kontakty first; another contacts app through the app category as a fallback.
            try {
                startActivity(new Intent(Intent.ACTION_MAIN).setClassName("lech.os.kontakty",
                        "lech.os.kontakty.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e) {
                try {
                    startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CONTACTS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ignored) {
                    // No contacts app.
                }
            }
        });
        Ui.navItem(nav, R.drawable.ic_settings, getString(R.string.nav_settings), this::showSettings);
        root.addView(nav, Ui.margins(Ui.lp(-1, -2), this, 14, 4, 14, 10));

        Ui.edgeToEdge(this, root);
        setContentView(root);
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> {
                    if (searchBox.getVisibility() == View.VISIBLE) {
                        toggleSearch();
                    } else if (chip != CHIP_ALL) {
                        chip = CHIP_ALL;
                        updateChips();
                        reload();
                    } else {
                        finish();
                    }
                });
        updateChips();
        askMissing();
    }

    /** Asks for each permission that is not granted yet (also those the first start should have granted). */
    private void askMissing() {
        List<String> missing = new ArrayList<>();
        for (String p : PERMISSIONS) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        }
        if (!missing.isEmpty()) requestPermissions(missing.toArray(new String[0]), REQ_PERMISSIONS);
    }

    private boolean granted() {
        return checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean isDefault(Activity a) {
        RoleManager rm = a.getSystemService(RoleManager.class);
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_SMS) && rm.isRoleHeld(RoleManager.ROLE_SMS)) return true;
        return a.getPackageName().equals(Telephony.Sms.getDefaultSmsPackage(a));
    }

    private void askDefault() {
        RoleManager rm = getSystemService(RoleManager.class);
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_SMS)) {
            startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_SMS), REQ_ROLE);
        } else {
            // The role screen is not available: the default apps page of the settings.
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
            } catch (Exception ignored) {
                // No settings screen.
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            getContentResolver().registerContentObserver(Telephony.MmsSms.CONTENT_URI, true, observer);
            getContentResolver().registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer);
        } catch (SecurityException ignored) {
            // No permission yet.
        }
        Store.forgetNames();
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
        reload();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ROLE) {
            if (!isDefault(this)) {
                // Not granted from the dialog: the default apps page, where the user picks the SMS app.
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
                } catch (Exception ignored) {
                    // No settings screen.
                }
            }
            askMissing();
            reload();
        }
    }

    private void addChip(LinearLayout row, int which, int icon, int label) {
        LinearLayout c = Ui.chip(this, icon, getString(label));
        c.setOnClickListener(v -> {
            chip = which;
            updateChips();
            reload();
        });
        row.addView(c, Ui.margins(Ui.lp(-2, -2), this, 0, 0, 10, 0));
        chips.add(c);
    }

    private void updateChips() {
        for (int k = 0; k < chips.size(); k++) Ui.setChip(chips.get(k), k == chip);
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

    private void showMenu(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 1, 0, R.string.mark_all_read);
        m.getMenu().add(0, 2, 1, R.string.chip_blocked);
        m.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                bg.execute(() -> Store.markAllRead(this));
            } else {
                chip = CHIP_BLOCKED;
                updateChips();
                reload();
            }
            return true;
        });
        m.show();
    }

    private void showSettings(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 1, 0, R.string.default_app);
        m.getMenu().add(0, 2, 1, R.string.notification_settings);
        m.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                askDefault();
            } else {
                Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(i);
            }
            return true;
        });
        m.show();
    }

    private void addBlocked() {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        FrameLayout f = new FrameLayout(this);
        int p = Ui.dp(this, 20);
        f.setPadding(p, Ui.dp(this, 8), p, 0);
        f.addView(e);
        new android.app.AlertDialog.Builder(this).setTitle(R.string.add_blocked).setView(f)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    String n = e.getText().toString().trim();
                    if (!n.isEmpty()) bg.execute(() -> {
                        try {
                            Store.block(this, n);
                        } catch (Exception ignored) {
                            // Not allowed.
                        }
                        main.post(this::reload);
                    });
                })
                .setNegativeButton(R.string.cancel, null).show();
    }

    // ---- Loading ----

    private void reload() {
        if (list == null) return;
        defaultBanner.setVisibility(isDefault(this) ? View.GONE : View.VISIBLE);
        if (!granted()) {
            adapter.set(new ArrayList<>());
            list.setVisibility(View.GONE);
            empty.setVisibility(View.VISIBLE);
            emptyTitle.setText(R.string.permission_needed);
            emptyText.setVisibility(View.GONE);
            return;
        }
        final int gen = ++generation;
        final int c = chip;
        final String q = Store.normalize(query).toLowerCase();
        final String qText = query.toLowerCase();
        bg.execute(() -> {
            List<Object> rows = new ArrayList<>();
            try {
                if (c == CHIP_BLOCKED) {
                    rows.addAll(Store.blocked(this));
                } else {
                    for (Store.Conversation cv : Store.conversations(this)) {
                        if (c == CHIP_SMS && cv.mms) continue;
                        if (c == CHIP_MMS && !cv.mms) continue;
                        if (!qText.isEmpty()) {
                            String hay = ((cv.name == null ? "" : cv.name) + " " + cv.address + " "
                                    + (cv.snippet == null ? "" : cv.snippet)).toLowerCase();
                            if (!hay.contains(qText) && !hay.contains(q)) continue;
                        }
                        rows.add(cv);
                    }
                }
            } catch (Exception ignored) {
                // Provider busy.
            }
            main.post(() -> {
                if (gen != generation || isDestroyed()) return;
                adapter.set(rows);
                boolean none = rows.isEmpty();
                list.setVisibility(none ? View.GONE : View.VISIBLE);
                empty.setVisibility(none ? View.VISIBLE : View.GONE);
                emptyText.setVisibility(View.GONE);
                if (c == CHIP_BLOCKED) {
                    emptyTitle.setText(R.string.empty_blocked);
                } else if (!qText.isEmpty()) {
                    emptyTitle.setText(R.string.empty_search);
                } else {
                    emptyTitle.setText(R.string.empty);
                    emptyText.setText(R.string.empty_text);
                    emptyText.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    static String when(Activity a, long t) {
        Calendar now = Calendar.getInstance();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        if (DateUtils.isToday(t)) {
            return DateUtils.formatDateTime(a, t, DateUtils.FORMAT_SHOW_TIME);
        }
        if (DateUtils.isToday(t + DateUtils.DAY_IN_MILLIS)) return a.getString(R.string.yesterday);
        int flags = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NUMERIC_DATE;
        if (c.get(Calendar.YEAR) == now.get(Calendar.YEAR)) flags |= DateUtils.FORMAT_NO_YEAR;
        return DateUtils.formatDateTime(a, t, flags);
    }

    private final class Adapter extends BaseAdapter {
        private List<Object> rows = new ArrayList<>();

        void set(List<Object> r) {
            rows = r;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int p) { return rows.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public boolean isEnabled(int p) { return false; }

        @Override
        public View getView(int p, View convert, ViewGroup parent) {
            Object o = rows.get(p);
            if (o instanceof Store.Conversation) return conversationRow((Store.Conversation) o);
            return blockedRow((String[]) o);
        }
    }

    private View conversationRow(Store.Conversation cv) {
        LinearLayout r = Ui.row(this);
        r.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        Ui.clickable(r, Ui.shape(this, 0xCC0A2530, 0xAA34A0A0, 1.2f, 28));
        String name = !TextUtils.isEmpty(cv.name) ? cv.name : TextUtils.isEmpty(cv.address)
                ? getString(R.string.unknown) : cv.address;
        r.addView(Ui.avatar(this, TextUtils.isEmpty(cv.name) ? "" : cv.name, cv.photo, 52));
        LinearLayout t = Ui.column(this);
        t.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 8), 0);
        TextView n = Ui.text(this, name, 18, Ui.TEXT, true);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(n);
        TextView s = Ui.text(this, cv.snippet == null ? "" : cv.snippet, 15, cv.unread > 0 ? Ui.TEXT : Ui.TEXT_2,
                cv.unread > 0);
        s.setSingleLine(true);
        s.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(s);
        r.addView(t, Ui.lp(0, -2, 1));
        LinearLayout right = Ui.column(this);
        right.setGravity(Gravity.END);
        right.addView(Ui.text(this, when(this, cv.date), 14, Ui.TEXT_2, false));
        if (cv.unread > 0) {
            TextView badge = Ui.text(this, String.valueOf(cv.unread), 13, Ui.ON_GREEN, true);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(Ui.circle(Ui.GREEN, 0xFFC8FF8A, Ui.dp(this, 1)));
            LinearLayout.LayoutParams bl = Ui.lp(Ui.dp(this, 26), Ui.dp(this, 26));
            bl.topMargin = Ui.dp(this, 4);
            bl.gravity = Gravity.END;
            right.addView(badge, bl);
        }
        r.addView(right);
        r.setOnClickListener(v -> startActivity(new Intent(this, ConversationActivity.class)
                .putExtra(ConversationActivity.EXTRA_THREAD, cv.threadId)));
        r.setOnLongClickListener(v -> {
            new android.app.AlertDialog.Builder(this).setMessage(R.string.delete_conversation_q)
                    .setPositiveButton(R.string.delete, (d, w) -> bg.execute(() -> {
                        try {
                            Store.deleteThread(this, cv.threadId);
                        } catch (Exception ignored) {
                            // Not the default SMS app.
                        }
                    }))
                    .setNegativeButton(R.string.cancel, null).show();
            return true;
        });
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        wrap.addView(r);
        return wrap;
    }

    private View blockedRow(String[] b) {
        LinearLayout r = Ui.row(this);
        r.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12));
        r.setBackground(Ui.glass(this, 24));
        r.addView(Ui.icon(this, R.drawable.ic_block, Ui.RED, 28));
        TextView n = Ui.text(this, Store.label(this, b[1]), 17, Ui.TEXT, true);
        n.setPadding(Ui.dp(this, 14), 0, 0, 0);
        r.addView(n, Ui.lp(0, -2, 1));
        TextView u = Ui.text(this, getString(R.string.unblock_number), 15, Ui.GREEN, true);
        u.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        Ui.clickable(u, null);
        u.setOnClickListener(v -> bg.execute(() -> {
            try {
                Store.unblock(this, Long.parseLong(b[0]));
            } catch (Exception ignored) {
                // Not allowed.
            }
            main.post(this::reload);
        }));
        r.addView(u);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        wrap.addView(r);
        return wrap;
    }
}
