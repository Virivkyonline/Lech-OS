/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.spravy;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.Telephony;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import lech.os.ui.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One conversation: message bubbles and the text field; also a new message (with the recipient field). */
public class ConversationActivity extends Activity {
    static final String EXTRA_THREAD = "thread";

    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private long thread = -1;
    private String address;
    private int generation;

    private TextView title;
    private ImageView avatar;
    private View callButton;
    private LinearLayout toRow;
    private EditText to;
    private LinearLayout suggestions;
    private ListView list;
    private final Adapter adapter = new Adapter();
    private EditText input;

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

        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        bar.addView(Ui.roundButton(this, R.drawable.ic_back, false, 44, getString(R.string.back), v -> finish()));
        avatar = Ui.avatar(this, "", null, 44);
        bar.addView(avatar, Ui.margins(Ui.lp(Ui.dp(this, 44), Ui.dp(this, 44)), this, 12, 0, 0, 0));
        title = Ui.text(this, getString(R.string.new_message), 20, Ui.TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 8), 0);
        bar.addView(title, Ui.lp(0, -2, 1));
        callButton = Ui.roundButton(this, R.drawable.ic_call, true, 44, getString(R.string.call), v -> call());
        bar.addView(callButton);
        bar.addView(Ui.roundButton(this, R.drawable.ic_more, false, 44, getString(R.string.more), this::showMenu),
                Ui.margins(Ui.lp(Ui.dp(this, 44), Ui.dp(this, 44)), this, 10, 0, 0, 0));
        root.addView(bar, Ui.lp(-1, -2));

        toRow = Ui.column(this);
        LinearLayout tr = Ui.row(this);
        tr.setBackground(Ui.shape(this, 0xCC08202B, 0xFF5FC21A, 1.5f, 26));
        tr.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 12), 0);
        tr.addView(Ui.text(this, getString(R.string.to), 16, Ui.GREEN, true));
        to = new EditText(this);
        to.setHint(R.string.to_hint);
        to.setBackground(null);
        to.setSingleLine(true);
        to.setTextColor(Ui.TEXT);
        to.setHintTextColor(Ui.TEXT_2);
        to.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        to.setPadding(Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14));
        to.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                suggest(s.toString().trim());
            }
        });
        tr.addView(to, Ui.lp(0, -2, 1));
        ImageView pick = Ui.icon(this, R.drawable.ic_person, Ui.GREEN, 26);
        int pp = Ui.dp(this, 8);
        pick.setPadding(pp, pp, pp, pp);
        pick.setLayoutParams(Ui.lp(Ui.dp(this, 44), Ui.dp(this, 44)));
        pick.setContentDescription(getString(R.string.nav_contacts));
        Ui.clickable(pick, null);
        pick.setOnClickListener(v -> pickContact());
        tr.addView(pick);
        toRow.addView(tr, Ui.lp(-1, -2));
        suggestions = Ui.column(this);
        toRow.addView(suggestions, Ui.lp(-1, -2));
        root.addView(toRow, Ui.margins(Ui.lp(-1, -2), this, 14, 0, 14, 6));

        list = new ListView(this);
        list.setDivider(null);
        list.setSelector(new android.graphics.drawable.ColorDrawable(0));
        list.setStackFromBottom(true);
        list.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
        list.setClipToPadding(false);
        list.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        list.setAdapter(adapter);
        root.addView(list, Ui.lp(-1, 0, 1));

        LinearLayout inputRow = Ui.row(this);
        inputRow.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 10));
        input = new EditText(this);
        input.setHint(R.string.type_message);
        input.setTextColor(Ui.TEXT);
        input.setHintTextColor(Ui.TEXT_2);
        input.setTextSize(17);
        input.setMaxLines(5);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setBackground(Ui.shape(this, 0xCC0A2530, 0xAA34A0A0, 1.2f, 26));
        input.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 12));
        inputRow.addView(input, Ui.lp(0, -2, 1));
        inputRow.addView(Ui.roundButton(this, R.drawable.ic_send, true, 52, getString(R.string.send), v -> send()),
                Ui.margins(Ui.lp(Ui.dp(this, 52), Ui.dp(this, 52)), this, 10, 0, 0, 0));
        root.addView(inputRow, Ui.lp(-1, -2));

        Ui.edgeToEdge(this, root);
        setContentView(root);
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handle(intent);
    }

    private void handle(Intent i) {
        thread = i.getLongExtra(EXTRA_THREAD, -1);
        address = null;
        Uri data = i.getData();
        if (data != null && ("sms".equals(data.getScheme()) || "smsto".equals(data.getScheme())
                || "mms".equals(data.getScheme()) || "mmsto".equals(data.getScheme()))) {
            String a = data.getSchemeSpecificPart();
            int q = a.indexOf('?');
            if (q >= 0) a = a.substring(0, q);
            if (!TextUtils.isEmpty(a)) address = a.split("[,;]")[0].trim();
        }
        String body = i.getStringExtra("sms_body");
        if (body == null) body = i.getStringExtra(Intent.EXTRA_TEXT);
        if (body != null) input.setText(body);
        if (thread >= 0 || address != null) {
            bg.execute(() -> {
                if (thread < 0 && address != null) {
                    try {
                        thread = Store.threadFor(this, address);
                    } catch (Exception ignored) {
                        // No access.
                    }
                } else if (address == null) {
                    address = Store.threadAddress(this, thread);
                }
                main.post(this::showHeader);
            });
        } else {
            showHeader();
            to.requestFocus();
        }
    }

    private void showHeader() {
        boolean known = address != null;
        toRow.setVisibility(known ? View.GONE : View.VISIBLE);
        callButton.setVisibility(known ? View.VISIBLE : View.GONE);
        if (known) {
            Store.Who w = Store.who(this, address);
            title.setText(Store.label(this, address));
            Ui.setAvatar(avatar, w.name == null ? "" : w.name, w.photo, 44);
            input.requestFocus();
        } else {
            title.setText(R.string.new_message);
            Ui.setAvatar(avatar, "", null, 44);
        }
        reload();
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            getContentResolver().registerContentObserver(Telephony.MmsSms.CONTENT_URI, true, observer);
            getContentResolver().registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer);
        } catch (SecurityException ignored) {
            // No permission.
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

    private void reload() {
        if (thread < 0) {
            adapter.set(new ArrayList<>());
            return;
        }
        final int gen = ++generation;
        final long t = thread;
        bg.execute(() -> {
            List<Store.Message> m = Store.messages(this, t);
            Store.markRead(this, t);
            main.post(() -> {
                if (gen == generation && !isDestroyed()) adapter.set(m);
            });
        });
    }

    private void suggest(String text) {
        suggestions.removeAllViews();
        if (text.isEmpty()) return;
        final String q = text;
        bg.execute(() -> {
            List<String[]> found = new ArrayList<>();
            try (Cursor cur = getContentResolver().query(Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, Uri.encode(q)),
                    new String[] {Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER, Phone.PHOTO_THUMBNAIL_URI}, null, null,
                    null)) {
                while (cur != null && cur.moveToNext() && found.size() < 5) {
                    found.add(new String[] {cur.getString(0), cur.getString(1), cur.getString(2)});
                }
            } catch (Exception ignored) {
                // No contacts.
            }
            main.post(() -> {
                if (!q.equals(to.getText().toString().trim())) return;
                suggestions.removeAllViews();
                if (q.matches("[+0-9 ()-]{3,}")) {
                    found.add(0, new String[] {q, q, null});
                }
                for (String[] f : found) {
                    LinearLayout r = Ui.row(this);
                    r.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
                    Ui.clickable(r, Ui.glass(this, 18));
                    r.addView(Ui.avatar(this, f[0].equals(f[1]) ? "" : f[0], f[2], 36));
                    LinearLayout tt = Ui.column(this);
                    tt.setPadding(Ui.dp(this, 12), 0, 0, 0);
                    tt.addView(Ui.text(this, f[0], 16, Ui.TEXT, true));
                    if (!f[0].equals(f[1])) tt.addView(Ui.text(this, f[1], 13, Ui.TEXT_2, false));
                    r.addView(tt, Ui.lp(0, -2, 1));
                    r.setOnClickListener(v -> choose(f[1]));
                    suggestions.addView(r, Ui.margins(Ui.lp(-1, -2), this, 0, 6, 0, 0));
                }
            });
        });
    }

    private static final int REQ_PICK = 7;

    /** Chooses a phone number of a contact (the contacts app picker). */
    private void pickContact() {
        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {android.Manifest.permission.READ_CONTACTS}, 2);
            return;
        }
        // All contacts with a number, as a simple list.
        bg.execute(() -> {
            List<String[]> all = new ArrayList<>();
            try (Cursor cur = getContentResolver().query(Phone.CONTENT_URI, new String[] {Phone.DISPLAY_NAME_PRIMARY,
                    Phone.NUMBER}, null, null, Phone.DISPLAY_NAME_PRIMARY + " COLLATE LOCALIZED ASC")) {
                while (cur != null && cur.moveToNext()) all.add(new String[] {cur.getString(0), cur.getString(1)});
            } catch (Exception ignored) {
                // No contacts.
            }
            main.post(() -> {
                String[] labels = new String[all.size()];
                for (int k = 0; k < all.size(); k++) labels[k] = all.get(k)[0] + "\n" + all.get(k)[1];
                new AlertDialog.Builder(this).setTitle(R.string.nav_contacts)
                        .setItems(labels, (d, w) -> {
                            to.setText(all.get(w)[0]);
                            choose(all.get(w)[1]);
                        })
                        .setNegativeButton(R.string.cancel, null).show();
            });
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 2 && results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pickContact();
        }
    }

    private void choose(String number) {
        address = number;
        suggestions.removeAllViews();
        bg.execute(() -> {
            try {
                thread = Store.threadFor(this, number);
            } catch (Exception ignored) {
                thread = -1;
            }
            main.post(this::showHeader);
        });
    }

    private void send() {
        String body = input.getText().toString();
        if (body.trim().isEmpty()) return;
        if (address == null) {
            String typed = to.getText().toString().trim();
            if (typed.matches("[+0-9 ()-]{3,}")) {
                address = typed;
            } else {
                Toast.makeText(this, R.string.no_recipient, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        if (!MainActivity.isDefault(this)) {
            Toast.makeText(this, R.string.not_default_send, Toast.LENGTH_LONG).show();
            return;
        }
        input.setText("");
        final String a = address;
        bg.execute(() -> {
            try {
                Store.send(this, a, body, -1);
                if (thread < 0) thread = Store.threadFor(this, a);
            } catch (Exception ignored) {
                // Failed messages show as failed.
            }
            main.post(this::showHeader);
        });
    }

    private void call() {
        if (address == null) return;
        Intent i = new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", address, null));
        try {
            startActivity(i);
        } catch (Exception ignored) {
            // No phone app.
        }
    }

    private void showMenu(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        if (address != null) m.getMenu().add(0, 1, 0, R.string.block_number);
        if (thread >= 0) m.getMenu().add(0, 2, 1, R.string.delete_conversation);
        m.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                new AlertDialog.Builder(this).setMessage(getString(R.string.block_q, Store.label(this, address)))
                        .setPositiveButton(R.string.block_number, (d, w) -> bg.execute(() -> {
                            try {
                                Store.block(this, address);
                                main.post(() -> Toast.makeText(this, R.string.blocked_done, Toast.LENGTH_SHORT).show());
                            } catch (Exception ignored) {
                                // Not allowed.
                            }
                        }))
                        .setNegativeButton(R.string.cancel, null).show();
            } else {
                new AlertDialog.Builder(this).setMessage(R.string.delete_conversation_q)
                        .setPositiveButton(R.string.delete, (d, w) -> bg.execute(() -> {
                            try {
                                Store.deleteThread(this, thread);
                            } catch (Exception ignored) {
                                // Not the default SMS app.
                            }
                            main.post(this::finish);
                        }))
                        .setNegativeButton(R.string.cancel, null).show();
            }
            return true;
        });
        m.show();
    }

    // ---- Bubbles ----

    private final class Adapter extends BaseAdapter {
        private List<Store.Message> rows = new ArrayList<>();

        void set(List<Store.Message> r) {
            rows = r;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int p) { return rows.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public boolean isEnabled(int p) { return false; }

        @Override
        public View getView(int p, View convert, ViewGroup parent) {
            Store.Message m = rows.get(p);
            boolean showDay = p == 0 || dayOf(rows.get(p - 1).date) != dayOf(m.date);
            return bubble(m, showDay);
        }
    }

    private static long dayOf(long t) {
        return (t + java.util.TimeZone.getDefault().getOffset(t)) / DateUtils.DAY_IN_MILLIS;
    }

    private View bubble(Store.Message m, boolean showDay) {
        LinearLayout wrap = Ui.column(this);
        if (showDay) {
            TextView d = Ui.text(this, DateUtils.formatDateTime(this, m.date, DateUtils.FORMAT_SHOW_DATE
                    | DateUtils.FORMAT_SHOW_WEEKDAY), 13, Ui.TEXT_3, true);
            d.setGravity(Gravity.CENTER);
            d.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 6));
            wrap.addView(d, Ui.lp(-1, -2));
        }
        LinearLayout b = Ui.column(this);
        b.setPadding(Ui.dp(this, 14), Ui.dp(this, 9), Ui.dp(this, 14), Ui.dp(this, 7));
        if (m.incoming) {
            b.setBackground(Ui.shape(this, 0xE60D2A38, 0x8834707A, 1, 20));
        } else {
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {0xFF2F8A10, 0xFF1A5E08});
            g.setCornerRadius(Ui.dp(this, 20));
            g.setStroke(Ui.dp(this, 1), m.status == 2 ? Ui.RED : 0xFF7BE41E);
            b.setBackground(g);
        }
        if (m.image != null) {
            ImageView img = new ImageView(this);
            img.setAdjustViewBounds(true);
            img.setMaxHeight(Ui.dp(this, 260));
            try {
                img.setImageBitmap(android.graphics.ImageDecoder.decodeBitmap(
                        android.graphics.ImageDecoder.createSource(getContentResolver(), m.image), (dec, info, src) -> {
                            int w = info.getSize().getWidth();
                            int max = Ui.dp(this, 240);
                            if (w > max) dec.setTargetSize(max, info.getSize().getHeight() * max / w);
                        }));
                b.addView(img, Ui.lp(-2, -2));
            } catch (Exception ignored) {
                // Unreadable part.
            }
        }
        if (!TextUtils.isEmpty(m.body)) {
            TextView t = Ui.text(this, m.body, 16, Ui.TEXT, false);
            t.setAutoLinkMask(android.text.util.Linkify.WEB_URLS | android.text.util.Linkify.PHONE_NUMBERS
                    | android.text.util.Linkify.EMAIL_ADDRESSES);
            t.setLinkTextColor(m.incoming ? Ui.GREEN_LIGHT : 0xFFE8FFD0);
            b.addView(t);
        }
        String meta = DateUtils.formatDateTime(this, m.date, DateUtils.FORMAT_SHOW_TIME);
        if (m.mms) meta = getString(R.string.mms_label) + " · " + meta;
        if (m.status == 1) meta = getString(R.string.sending);
        if (m.status == 2) meta = getString(R.string.failed);
        TextView mt = Ui.text(this, meta, 12, m.status == 2 ? 0xFFFFB3B3 : m.incoming ? Ui.TEXT_3 : 0xFFCDEFB8,
                false);
        mt.setGravity(Gravity.END);
        b.addView(mt, Ui.lp(-1, -2));
        b.setOnLongClickListener(v -> {
            PopupMenu pm = new PopupMenu(this, v);
            if (!TextUtils.isEmpty(m.body)) pm.getMenu().add(0, 1, 0, R.string.copy_text);
            pm.getMenu().add(0, 2, 1, R.string.delete_message);
            pm.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) {
                    getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("sms", m.body));
                    Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
                } else {
                    bg.execute(() -> {
                        try {
                            Store.deleteMessage(this, m);
                        } catch (Exception ignored) {
                            // Not the default SMS app.
                        }
                    });
                }
                return true;
            });
            pm.show();
            return true;
        });
        if (m.status == 2) {
            b.setOnClickListener(v -> bg.execute(() -> {
                try {
                    Store.resend(this, m, address);
                } catch (Exception ignored) {
                    // Still failing.
                }
            }));
        }
        LinearLayout.LayoutParams lp = Ui.lp(-2, -2);
        lp.gravity = m.incoming ? Gravity.START : Gravity.END;
        lp.topMargin = Ui.dp(this, 3);
        lp.bottomMargin = Ui.dp(this, 3);
        if (m.incoming) lp.rightMargin = Ui.dp(this, 56); else lp.leftMargin = Ui.dp(this, 56);
        wrap.addView(b, lp);
        FrameLayout f = new FrameLayout(this);
        f.addView(wrap);
        return f;
    }
}
