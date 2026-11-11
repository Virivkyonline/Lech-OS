/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.kontakty;

import lech.os.ui.Ui;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Intents.Insert;
import android.provider.MediaStore;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Creates or edits a contact (in the device account). Also answers INSERT / EDIT / INSERT_OR_EDIT of other apps. */
public class EditActivity extends Activity {
    static final String EXTRA_GROUP = "lech.os.kontakty.GROUP";
    private static final int REQ_PHOTO = 1;
    private static final int[] PHONE_TYPES = {Phone.TYPE_MOBILE, Phone.TYPE_HOME, Phone.TYPE_WORK, Phone.TYPE_OTHER};
    private static final int[] EMAIL_TYPES = {Email.TYPE_HOME, Email.TYPE_WORK, Email.TYPE_OTHER};

    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private Repo.Details details = new Repo.Details();
    private boolean photoChanged;
    private boolean loaded;
    private boolean saving;

    private LinearLayout form;
    private ImageView photoView;
    private EditText given;
    private EditText family;
    private EditText company;
    private EditText jobTitle;
    private EditText note;
    private LinearLayout phones;
    private LinearLayout emails;
    private LinearLayout groupsBox;
    private final List<CheckBox> groupChecks = new ArrayList<>();
    private final List<Long> groupIds = new ArrayList<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.column(this);
        root.setBackground(Ui.background());

        Intent in = getIntent();
        String action = in.getAction();
        Uri data = in.getData();
        boolean editing = (Intent.ACTION_EDIT.equals(action) || Intent.ACTION_INSERT_OR_EDIT.equals(action))
                && data != null && !isDirectory(in);

        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        bar.addView(Ui.roundButton(this, R.drawable.ic_close, false, 44, getString(R.string.cancel),
                v -> onBack()));
        TextView title = Ui.text(this, getString(editing ? R.string.edit_contact : R.string.new_contact), 22,
                Ui.TEXT, true);
        title.setPadding(Ui.dp(this, 14), 0, 0, 0);
        bar.addView(title, Ui.lp(0, -2, 1));
        bar.addView(Ui.roundButton(this, R.drawable.ic_check, true, 48, getString(R.string.save), v -> save()));
        root.addView(bar, Ui.lp(-1, -2));

        ScrollView scroll = new ScrollView(this);
        form = Ui.column(this);
        form.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 32));
        scroll.addView(form, Ui.lp(-1, -2));
        root.addView(scroll, Ui.lp(-1, 0, 1));
        Ui.edgeToEdge(this, root);
        setContentView(root);
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::onBack);
        buildForm();

        if (!Actions.canReadContacts(this)) {
            requestPermissions(Actions.PERMISSIONS, 1);
        }
        if (editing) {
            bg.execute(() -> {
                long id = Repo.resolveContact(this, data);
                Repo.Details d = id >= 0 ? Repo.load(this, id) : null;
                main.post(() -> {
                    if (d == null) {
                        Toast.makeText(this, R.string.contact_missing, Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    details = d;
                    fill(in);
                });
            });
        } else {
            long group = in.getLongExtra(EXTRA_GROUP, -1);
            if (group >= 0) details.groups.add(group);
            fill(in);
        }
    }

    private boolean isDirectory(Intent i) {
        String type = i.resolveType(getContentResolver());
        return type != null && type.startsWith("vnd.android.cursor.dir");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bg.shutdownNow();
    }

    private void buildForm() {
        FrameLayout ph = new FrameLayout(this);
        photoView = new ImageView(this);
        ph.addView(photoView, new FrameLayout.LayoutParams(Ui.dp(this, 112), Ui.dp(this, 112), Gravity.CENTER));
        FrameLayout cam = Ui.roundButton(this, R.drawable.ic_camera, true, 40, getString(R.string.photo_change),
                this::photoMenu);
        ph.addView(cam, new FrameLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40), Gravity.BOTTOM | Gravity.END));
        photoView.setOnClickListener(this::photoMenu);
        LinearLayout.LayoutParams pl = Ui.lp(Ui.dp(this, 124), Ui.dp(this, 118));
        pl.gravity = Gravity.CENTER_HORIZONTAL;
        pl.bottomMargin = Ui.dp(this, 12);
        form.addView(ph, pl);
        setPhoto(null);

        int name = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS;
        given = addField(R.string.first_name, name);
        family = addField(R.string.last_name, name);
        company = addField(R.string.company, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        jobTitle = addField(R.string.job_title, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);

        heading(R.string.section_phones);
        phones = Ui.column(this);
        form.addView(phones, Ui.lp(-1, -2));
        form.addView(addLink(R.string.add_phone, () -> addEntry(phones, "", Phone.TYPE_MOBILE, true, true)));

        heading(R.string.section_emails);
        emails = Ui.column(this);
        form.addView(emails, Ui.lp(-1, -2));
        form.addView(addLink(R.string.add_email, () -> addEntry(emails, "", Email.TYPE_HOME, false, true)));

        heading(R.string.section_note);
        note = Ui.field(this, getString(R.string.note), InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        note.setMinLines(2);
        form.addView(note, Ui.lp(-1, -2));

        groupsBox = Ui.column(this);
        form.addView(groupsBox, Ui.lp(-1, -2));
    }

    private EditText addField(int hint, int type) {
        EditText e = Ui.field(this, getString(hint), type);
        form.addView(e, Ui.margins(Ui.lp(-1, -2), this, 0, 0, 0, 10));
        return e;
    }

    private void heading(int title) {
        TextView t = Ui.text(this, getString(title), 15, Ui.GREEN, true);
        form.addView(t, Ui.margins(Ui.lp(-1, -2), this, 6, 14, 0, 6));
    }

    private View addLink(int label, Runnable r) {
        LinearLayout l = Ui.row(this);
        l.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        Ui.clickable(l, null);
        l.addView(Ui.icon(this, R.drawable.ic_add, Ui.GREEN, 22));
        TextView t = Ui.text(this, getString(label), 16, Ui.GREEN, true);
        t.setPadding(Ui.dp(this, 10), 0, 0, 0);
        l.addView(t);
        l.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams p = Ui.lp(-2, -2);
        l.setLayoutParams(p);
        return l;
    }

    /** One phone number or e-mail: the field, a type button and a remove button. */
    private void addEntry(LinearLayout list, String value, int type, boolean phone, boolean focus) {
        LinearLayout r = Ui.row(this);
        EditText e = Ui.field(this, getString(phone ? R.string.phone : R.string.email),
                phone ? InputType.TYPE_CLASS_PHONE
                        : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        e.setText(value);
        r.addView(e, Ui.lp(0, -2, 1));
        TextView t = Ui.text(this, "", 14, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14));
        Ui.clickable(t, Ui.glass(this, 16));
        t.setTag(type);
        t.setText(phone ? Actions.phoneType(this, type, null) : Actions.emailType(this, type, null));
        t.setOnClickListener(v -> {
            PopupMenu m = new PopupMenu(this, v);
            int[] types = phone ? PHONE_TYPES : EMAIL_TYPES;
            for (int k = 0; k < types.length; k++) {
                m.getMenu().add(0, k, k, phone ? Actions.phoneType(this, types[k], null)
                        : Actions.emailType(this, types[k], null));
            }
            m.setOnMenuItemClickListener(item -> {
                int nt = types[item.getItemId()];
                t.setTag(nt);
                t.setText(item.getTitle());
                return true;
            });
            m.show();
        });
        r.addView(t, Ui.margins(Ui.lp(-2, -2), this, 8, 0, 0, 0));
        ImageView x = Ui.icon(this, R.drawable.ic_close, Ui.TEXT_3, 22);
        int p = Ui.dp(this, 9);
        x.setPadding(p, p, p, p);
        x.setLayoutParams(Ui.margins(Ui.lp(Ui.dp(this, 40), Ui.dp(this, 40)), this, 4, 0, 0, 0));
        x.setContentDescription(getString(R.string.delete));
        Ui.clickable(x, null);
        x.setOnClickListener(v -> list.removeView(r));
        r.addView(x);
        list.addView(r, Ui.margins(Ui.lp(-1, -2), this, 0, 0, 0, 10));
        if (focus) e.requestFocus();
    }

    private void fill(Intent in) {
        Repo.Details d = details;
        given.setText(d.givenName);
        family.setText(d.familyName);
        company.setText(d.company);
        jobTitle.setText(d.jobTitle);
        note.setText(d.note);
        // Values passed by another app (INSERT / INSERT_OR_EDIT extras).
        String name = in.getStringExtra(Insert.NAME);
        if (!TextUtils.isEmpty(name) && d.givenName.isEmpty() && d.familyName.isEmpty()) {
            int sp = name.trim().lastIndexOf(' ');
            if (sp > 0) {
                given.setText(name.trim().substring(0, sp));
                family.setText(name.trim().substring(sp + 1));
            } else {
                given.setText(name.trim());
            }
        }
        String co = in.getStringExtra(Insert.COMPANY);
        if (!TextUtils.isEmpty(co) && d.company.isEmpty()) company.setText(co);
        String jt = in.getStringExtra(Insert.JOB_TITLE);
        if (!TextUtils.isEmpty(jt) && d.jobTitle.isEmpty()) jobTitle.setText(jt);
        String nt = in.getStringExtra(Insert.NOTES);
        if (!TextUtils.isEmpty(nt) && d.note.isEmpty()) note.setText(nt);

        phones.removeAllViews();
        for (Repo.Entry e : d.phones) addEntry(phones, e.value, e.type, true, false);
        String ph = in.getStringExtra(Insert.PHONE);
        if (!TextUtils.isEmpty(ph)) {
            addEntry(phones, ph, in.getIntExtra(Insert.PHONE_TYPE, Phone.TYPE_MOBILE), true, false);
        }
        if (phones.getChildCount() == 0) addEntry(phones, "", Phone.TYPE_MOBILE, true, false);

        emails.removeAllViews();
        for (Repo.Entry e : d.emails) addEntry(emails, e.value, e.type, false, false);
        String em = in.getStringExtra(Insert.EMAIL);
        if (!TextUtils.isEmpty(em)) addEntry(emails, em, Email.TYPE_HOME, false, false);

        if (d.photo != null) {
            try {
                setPhoto(android.graphics.BitmapFactory.decodeByteArray(d.photo, 0, d.photo.length));
            } catch (Exception ignored) {
                setPhoto(null);
            }
        } else if (d.photoUri != null) {
            Ui.setAvatar(photoView, d.displayName, d.photoUri, 112);
        }
        loaded = true;
        if (Actions.canReadContacts(this)) {
            bg.execute(() -> {
                List<Repo.Bucket> groups = Repo.groups(this);
                main.post(() -> showGroups(groups));
            });
        }
        if (d.rawContactId < 0) given.requestFocus();
    }

    private void showGroups(List<Repo.Bucket> groups) {
        groupsBox.removeAllViews();
        groupChecks.clear();
        groupIds.clear();
        if (groups.isEmpty()) return;
        TextView t = Ui.text(this, getString(R.string.section_groups), 15, Ui.GREEN, true);
        groupsBox.addView(t, Ui.margins(Ui.lp(-1, -2), this, 6, 14, 0, 6));
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.glass(this, 18));
        card.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
        for (Repo.Bucket b : groups) {
            CheckBox c = new CheckBox(this);
            c.setText(b.title);
            c.setTextColor(Ui.TEXT);
            c.setTextSize(16);
            c.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));
            c.setChecked(details.groups.contains(b.id));
            card.addView(c);
            groupChecks.add(c);
            groupIds.add(b.id);
        }
        groupsBox.addView(card, Ui.lp(-1, -2));
    }

    // ---- Photo ----

    private void photoMenu(View anchor) {
        boolean has = details.photo != null || (!photoChanged && details.photoUri != null);
        if (!has) {
            pickPhoto();
            return;
        }
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 1, 0, R.string.photo_choose);
        m.getMenu().add(0, 2, 1, R.string.photo_remove);
        m.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                pickPhoto();
            } else {
                details.photo = null;
                details.photoUri = null;
                photoChanged = true;
                setPhoto(null);
            }
            return true;
        });
        m.show();
    }

    private void pickPhoto() {
        Intent i = new Intent(MediaStore.ACTION_PICK_IMAGES);
        try {
            startActivityForResult(i, REQ_PHOTO);
        } catch (Exception e) {
            Intent g = new Intent(Intent.ACTION_GET_CONTENT);
            g.setType("image/*");
            Actions.start(this, Intent.createChooser(g, getString(R.string.photo_choose)));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PHOTO || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        bg.execute(() -> {
            try {
                Bitmap b = ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), uri),
                        (dec, info, src) -> {
                            int w = info.getSize().getWidth();
                            int h = info.getSize().getHeight();
                            int max = Math.max(w, h);
                            if (max > 1440) dec.setTargetSize(w * 1440 / max, h * 1440 / max);
                            dec.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                        });
                int side = Math.min(b.getWidth(), b.getHeight());
                Bitmap sq = Bitmap.createBitmap(b, (b.getWidth() - side) / 2, (b.getHeight() - side) / 2, side, side);
                Bitmap out = Bitmap.createScaledBitmap(sq, Math.min(side, 720), Math.min(side, 720), true);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                out.compress(Bitmap.CompressFormat.JPEG, 90, bytes);
                byte[] jpeg = bytes.toByteArray();
                main.post(() -> {
                    details.photo = jpeg;
                    photoChanged = true;
                    setPhoto(out);
                });
            } catch (Exception e) {
                main.post(() -> Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void setPhoto(Bitmap b) {
        if (b == null) {
            Ui.setAvatar(photoView, given == null ? "" : given.getText().toString(), null, 112);
        } else {
            photoView.setImageBitmap(Ui.roundCrop(b, Ui.dp(this, 112)));
        }
    }

    // ---- Saving ----

    private void collect() {
        Repo.Details d = details;
        d.givenName = given.getText().toString().trim();
        d.familyName = family.getText().toString().trim();
        d.company = company.getText().toString().trim();
        d.jobTitle = jobTitle.getText().toString().trim();
        d.note = note.getText().toString().trim();
        d.phones.clear();
        collectEntries(phones, d.phones);
        d.emails.clear();
        collectEntries(emails, d.emails);
        if (!groupChecks.isEmpty()) {
            d.groups.clear();
            for (int k = 0; k < groupChecks.size(); k++) {
                if (groupChecks.get(k).isChecked()) d.groups.add(groupIds.get(k));
            }
        }
    }

    private static void collectEntries(LinearLayout list, List<Repo.Entry> out) {
        for (int k = 0; k < list.getChildCount(); k++) {
            LinearLayout r = (LinearLayout) list.getChildAt(k);
            String v = ((EditText) r.getChildAt(0)).getText().toString().trim();
            int type = (Integer) r.getChildAt(1).getTag();
            if (!v.isEmpty()) out.add(new Repo.Entry(v, type, null));
        }
    }

    private void save() {
        if (saving || !loaded) return;
        collect();
        Repo.Details d = details;
        if (d.givenName.isEmpty() && d.familyName.isEmpty() && d.phones.isEmpty() && d.emails.isEmpty()
                && d.company.isEmpty()) {
            Toast.makeText(this, R.string.save_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Actions.canReadContacts(this)) {
            requestPermissions(Actions.PERMISSIONS, 1);
            return;
        }
        saving = true;
        final boolean isNew = d.rawContactId < 0;
        final boolean pc = photoChanged;
        bg.execute(() -> {
            long id;
            try {
                id = Repo.save(this, d, pc);
            } catch (Exception e) {
                id = -1;
            }
            final long contactId = id;
            main.post(() -> {
                saving = false;
                if (contactId < 0) {
                    Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show();
                    return;
                }
                Ui.forgetPhotos();
                Uri uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId);
                setResult(RESULT_OK, new Intent().setData(uri));
                if (isNew && getCallingActivity() == null) {
                    Intent v = new Intent(this, ContactActivity.class);
                    v.setData(uri);
                    startActivity(v);
                }
                finish();
            });
        });
    }

    private void onBack() {
        if (!loaded) {
            finish();
            return;
        }
        boolean dirty = given.getText().length() > 0 || family.getText().length() > 0
                || company.getText().length() > 0 || note.getText().length() > 0 || photoChanged;
        if ((dirty && details.rawContactId < 0) || photoChanged) {
            Actions.confirm(this, R.string.discard_q, R.string.discard, this::finish);
        } else {
            finish();
        }
    }
}
