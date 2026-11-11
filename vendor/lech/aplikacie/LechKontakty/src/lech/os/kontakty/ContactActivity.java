/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.kontakty;

import lech.os.ui.Ui;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One contact: photo, name, company, phone numbers, e-mails, note and groups, with call / message / e-mail. */
public class ContactActivity extends Activity {
    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private long contactId = -1;
    private Repo.Details details;
    private LinearLayout body;
    private ImageView starButton;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.column(this);
        root.setBackground(Ui.background());

        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        bar.addView(Ui.roundButton(this, R.drawable.ic_back, false, 44, getString(R.string.back), v -> finish()));
        bar.addView(new View(this), Ui.lp(0, 1, 1));
        FrameLayout star = Ui.roundButton(this, R.drawable.ic_star_border, false, 44,
                getString(R.string.favorite_add), v -> toggleStar());
        starButton = (ImageView) star.getChildAt(0);
        bar.addView(star);
        bar.addView(Ui.roundButton(this, R.drawable.ic_edit, false, 44, getString(R.string.edit), v -> edit()),
                Ui.margins(Ui.lp(Ui.dp(this, 44), Ui.dp(this, 44)), this, 10, 0, 10, 0));
        bar.addView(Ui.roundButton(this, R.drawable.ic_delete, false, 44, getString(R.string.delete), v ->
                Actions.confirm(this, R.string.delete_contact_q, R.string.delete, () -> bg.execute(() -> {
                    Repo.delete(this, contactId);
                    main.post(this::finish);
                }))));
        root.addView(bar, Ui.lp(-1, -2));

        ScrollView scroll = new ScrollView(this);
        body = Ui.column(this);
        body.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 24));
        scroll.addView(body, Ui.lp(-1, -2));
        root.addView(scroll, Ui.lp(-1, 0, 1));
        Ui.edgeToEdge(this, root);
        setContentView(root);

        if (!Actions.canReadContacts(this)) {
            requestPermissions(Actions.PERMISSIONS, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bg.shutdownNow();
    }

    private void load() {
        if (!Actions.canReadContacts(this)) return;
        final android.net.Uri uri = getIntent().getData();
        final long known = contactId;
        bg.execute(() -> {
            long id = known >= 0 ? known : Repo.resolveContact(this, uri);
            Repo.Details d = id >= 0 ? Repo.load(this, id) : null;
            main.post(() -> {
                if (isDestroyed()) return;
                if (d == null) {
                    Toast.makeText(this, R.string.contact_missing, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                contactId = id;
                details = d;
                Ui.forgetPhotos();
                render();
            });
        });
    }

    private void render() {
        Repo.Details d = details;
        body.removeAllViews();
        starButton.setImageResource(d.starred ? R.drawable.ic_star : R.drawable.ic_star_border);
        starButton.setImageTintList(android.content.res.ColorStateList.valueOf(d.starred ? 0xFFFFD54F : Ui.TEXT));
        ((View) starButton.getParent()).setContentDescription(
                getString(d.starred ? R.string.favorite_remove : R.string.favorite_add));

        FrameLayout photo = new FrameLayout(this);
        photo.setBackground(Ui.glow(this, 100));
        ImageView av = Ui.avatar(this, d.displayName, d.photoUri, 128);
        photo.addView(av, new FrameLayout.LayoutParams(Ui.dp(this, 128), Ui.dp(this, 128), Gravity.CENTER));
        LinearLayout.LayoutParams pl = Ui.lp(Ui.dp(this, 200), Ui.dp(this, 170));
        pl.gravity = Gravity.CENTER_HORIZONTAL;
        body.addView(photo, pl);

        String name = TextUtils.isEmpty(d.displayName) ? getString(R.string.no_name) : d.displayName;
        TextView n = Ui.text(this, name, 28, Ui.TEXT, true);
        n.setGravity(Gravity.CENTER);
        body.addView(n, Ui.lp(-1, -2));
        String sub = join(d.jobTitle, d.company);
        if (!sub.isEmpty()) {
            TextView s = Ui.text(this, sub, 16, Ui.TEXT_2, false);
            s.setGravity(Gravity.CENTER);
            body.addView(s, Ui.margins(Ui.lp(-1, -2), this, 0, 4, 0, 0));
        }

        LinearLayout actions = Ui.row(this);
        actions.setGravity(Gravity.CENTER);
        final String phone = d.phones.isEmpty() ? null : d.phones.get(0).value;
        final String mail = d.emails.isEmpty() ? null : d.emails.get(0).value;
        actions.addView(action(R.drawable.ic_call, R.string.call, phone != null, () -> Actions.call(this, phone)));
        actions.addView(action(R.drawable.ic_message, R.string.message, phone != null,
                () -> Actions.message(this, phone)));
        actions.addView(action(R.drawable.ic_email, R.string.email, mail != null, () -> Actions.email(this, mail)));
        body.addView(actions, Ui.margins(Ui.lp(-1, -2), this, 0, 20, 0, 8));

        if (!d.phones.isEmpty()) {
            LinearLayout card = section(R.string.section_phones);
            for (Repo.Entry e : d.phones) {
                card.addView(line(e.value, Actions.phoneType(this, e.type, e.label), R.drawable.ic_call,
                        () -> Actions.call(this, e.value), R.drawable.ic_message, () -> Actions.message(this, e.value)));
            }
        }
        if (!d.emails.isEmpty()) {
            LinearLayout card = section(R.string.section_emails);
            for (Repo.Entry e : d.emails) {
                card.addView(line(e.value, Actions.emailType(this, e.type, e.label), R.drawable.ic_email,
                        () -> Actions.email(this, e.value), 0, null));
            }
        }
        if (!d.company.isEmpty() || !d.jobTitle.isEmpty()) {
            section(R.string.section_company).addView(line(d.company.isEmpty() ? d.jobTitle : d.company,
                    d.company.isEmpty() ? null : d.jobTitle, 0, null, 0, null));
        }
        if (!d.note.isEmpty()) {
            section(R.string.section_note).addView(line(d.note, null, 0, null, 0, null));
        }
        if (!d.groups.isEmpty()) {
            bg.execute(() -> {
                List<Repo.Bucket> all = Repo.groups(this);
                StringBuilder sb = new StringBuilder();
                for (Repo.Bucket b : all) {
                    if (d.groups.contains(b.id)) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(b.title);
                    }
                }
                main.post(() -> {
                    if (sb.length() > 0 && details == d) {
                        section(R.string.section_groups).addView(line(sb.toString(), null, 0, null, 0, null));
                    }
                });
            });
        }
    }

    private static String join(String a, String b) {
        if (TextUtils.isEmpty(a)) return b == null ? "" : b;
        if (TextUtils.isEmpty(b)) return a;
        return a + " · " + b;
    }

    private View action(int icon, int label, boolean enabled, Runnable r) {
        LinearLayout c = Ui.column(this);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        View b = Ui.roundButton(this, icon, enabled, 60, getString(label), v -> r.run());
        b.setEnabled(enabled);
        b.setAlpha(enabled ? 1f : 0.4f);
        c.addView(b);
        TextView t = Ui.text(this, getString(label), 14, enabled ? Ui.TEXT : Ui.TEXT_3, false);
        t.setGravity(Gravity.CENTER);
        c.addView(t, Ui.margins(Ui.lp(-2, -2), this, 0, 6, 0, 0));
        c.setLayoutParams(Ui.margins(Ui.lp(-2, -2), this, 18, 0, 18, 0));
        return c;
    }

    private LinearLayout section(int title) {
        TextView t = Ui.text(this, getString(title), 15, Ui.GREEN, true);
        body.addView(t, Ui.margins(Ui.lp(-1, -2), this, 8, 18, 0, 6));
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.glass(this, 22));
        card.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        body.addView(card, Ui.lp(-1, -2));
        return card;
    }

    private View line(String value, String label, int icon1, Runnable a1, int icon2, Runnable a2) {
        LinearLayout r = Ui.row(this);
        r.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10));
        LinearLayout t = Ui.column(this);
        TextView v = Ui.text(this, value, 17, Ui.TEXT, false);
        v.setTextIsSelectable(true);
        t.addView(v);
        if (!TextUtils.isEmpty(label)) t.addView(Ui.text(this, label, 13, Ui.TEXT_3, false));
        r.addView(t, Ui.lp(0, -2, 1));
        if (icon2 != 0) {
            r.addView(Ui.roundButton(this, icon2, false, 42, "", v2 -> a2.run()),
                    Ui.margins(Ui.lp(Ui.dp(this, 42), Ui.dp(this, 42)), this, 8, 0, 0, 0));
        }
        if (icon1 != 0) {
            r.addView(Ui.roundButton(this, icon1, true, 42, "", v1 -> a1.run()),
                    Ui.margins(Ui.lp(Ui.dp(this, 42), Ui.dp(this, 42)), this, 10, 0, 0, 0));
            Ui.clickable(r, null);
            r.setOnClickListener(x -> a1.run());
        }
        return r;
    }

    private void toggleStar() {
        if (details == null) return;
        final boolean now = !details.starred;
        details.starred = now;
        render();
        bg.execute(() -> Repo.setStarred(this, contactId, now));
    }

    private void edit() {
        if (contactId < 0) return;
        Intent i = new Intent(Intent.ACTION_EDIT,
                ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId));
        i.setClass(this, EditActivity.class);
        startActivity(i);
    }
}
