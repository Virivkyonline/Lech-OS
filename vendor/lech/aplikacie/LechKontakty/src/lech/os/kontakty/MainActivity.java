/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.kontakty;

import lech.os.ui.Ui;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
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

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The main screen: contacts (all, favourites, groups, companies), call history and the dial pad. */
public class MainActivity extends Activity {
    private static final int TAB_CONTACTS = 0;
    private static final int TAB_HISTORY = 1;
    private static final int TAB_DIALPAD = 2;
    private static final int TAB_PHONE = 3;
    private static final int NAV_SETTINGS = -1;
    private static final int CHIP_ALL = 0;
    private static final int CHIP_FAVORITES = 1;
    private static final int CHIP_GROUPS = 2;
    private static final int CHIP_COMPANIES = 3;
    private static final int REQ_PERMISSIONS = 1;
    private static final int REQ_VOICE = 2;
    private static final String STATE_TAB = "tab";
    private static final String STATE_CHIP = "chip";

    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private int generation;

    private int tab = TAB_CONTACTS;
    /** The phone app (PhoneActivity): phone home, its navigation and header. */
    private boolean phone;
    private View addButton;
    private LinearLayout phonePage;
    private EditText phoneSearch;
    private TextView phoneHeading;
    private View phoneShowAll;
    private LinearLayout phoneList;
    private int chip = CHIP_ALL;
    private Repo.Bucket openGroup;
    private String openCompany;

    private TextView title;
    private TextView subtitle;
    private ImageView headerIcon;
    private View headerButtons;
    private View mountains;
    private FrameLayout pages;
    private LinearLayout contactsPage;
    private LinearLayout historyPage;
    private LinearLayout dialpadPage;
    private EditText search;
    private final List<LinearLayout> chips = new ArrayList<>();
    private final List<LinearLayout> navItems = new ArrayList<>();
    private ListView contactList;
    private final RowAdapter contactAdapter = new RowAdapter();
    private LinearLayout contactsEmpty;
    private TextView contactsEmptyTitle;
    private TextView contactsEmptyText;
    private View contactsEmptyButton;
    private ListView historyList;
    private final RowAdapter historyAdapter = new RowAdapter();
    private TextView historyEmpty;
    private LinearLayout permissionView;

    private TextView dialNumber;
    private LinearLayout dialMatches;
    private View dialAddContact;
    private ToneGenerator tones;

    private final ContentObserver observer = new ContentObserver(main) {
        @Override
        public void onChange(boolean selfChange) {
            reload();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        phone = this instanceof PhoneActivity;
        tab = home();
        if (state != null) {
            tab = state.getInt(STATE_TAB, home());
            chip = state.getInt(STATE_CHIP, CHIP_ALL);
        }
        LinearLayout root = Ui.column(this);
        root.setBackground(Ui.background());
        root.addView(buildHeader(), Ui.lp(-1, -2));
        pages = new FrameLayout(this);
        root.addView(pages, Ui.lp(-1, 0, 1));
        contactsPage = buildContactsPage();
        historyPage = buildHistoryPage();
        dialpadPage = buildDialpadPage();
        pages.addView(contactsPage, Ui.match());
        pages.addView(historyPage, Ui.match());
        pages.addView(dialpadPage, Ui.match());
        phonePage = buildPhonePage();
        pages.addView(phonePage, Ui.match());
        permissionView = buildPermissionView();
        pages.addView(permissionView, Ui.match());
        root.addView(buildNavigation(), Ui.margins(Ui.lp(-1, -2), this, 14, 4, 14, 10));
        Ui.edgeToEdge(this, root);
        setContentView(root);
        handleIntent(getIntent());
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::onBack);
        if (!Actions.canReadContacts(this)) requestPermissions(Actions.PERMISSIONS, REQ_PERMISSIONS);
        selectTab(tab);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private int home() {
        return phone ? TAB_PHONE : TAB_CONTACTS;
    }

    private void handleIntent(Intent i) {
        if (i != null && Intent.ACTION_VIEW.equals(i.getAction())) tab = TAB_CONTACTS;
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_TAB, tab);
        out.putInt(STATE_CHIP, chip);
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            getContentResolver().registerContentObserver(ContactsContract.AUTHORITY_URI, true, observer);
            getContentResolver().registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer);
        } catch (SecurityException ignored) {
            // No permission yet; onResume reloads after it is granted.
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
        if (tones != null) tones.release();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        try {
            getContentResolver().unregisterContentObserver(observer);
            getContentResolver().registerContentObserver(ContactsContract.AUTHORITY_URI, true, observer);
            getContentResolver().registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer);
        } catch (SecurityException ignored) {
            // Still not granted.
        }
        reload();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VOICE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty()) search.setText(r.get(0));
        }
    }

    private void onBack() {
        if (tab == TAB_CONTACTS && (openGroup != null || openCompany != null)) {
            openGroup = null;
            openCompany = null;
            reload();
        } else if (tab == TAB_CONTACTS && search.getText().length() > 0) {
            search.setText("");
        } else if (tab == TAB_PHONE && phoneSearch.getText().length() > 0) {
            phoneSearch.setText("");
        } else if (tab != home()) {
            selectTab(home());
        } else {
            finish();
        }
    }

    // ---- Header ----

    private View buildHeader() {
        LinearLayout h = Ui.column(this);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.lech_logo);
        logo.setAdjustViewBounds(true);
        logo.setContentDescription("LECH OS");
        LinearLayout.LayoutParams lp = Ui.lp(-2, Ui.dp(this, 40));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = Ui.dp(this, 4);
        h.addView(logo, lp);

        LinearLayout row = Ui.row(this);
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 12), 0);
        FrameLayout iconBox = new FrameLayout(this);
        iconBox.setBackground(Ui.shape(this, 0x33000000, 0x667BE41E, 1.5f, 22));
        headerIcon = new ImageView(this);
        headerIcon.setImageResource(this instanceof PhoneActivity ? R.mipmap.ic_telefon : R.mipmap.ic_kontakty);
        int p = Ui.dp(this, 3);
        iconBox.setPadding(p, p, p, p);
        iconBox.addView(headerIcon, Ui.match());
        row.addView(iconBox, Ui.lp(Ui.dp(this, 68), Ui.dp(this, 68)));

        LinearLayout titles = Ui.column(this);
        titles.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 6), 0);
        title = Ui.text(this, "", 28, Ui.TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        subtitle = Ui.text(this, "", 15, Ui.TEXT_2, false);
        subtitle.setSingleLine(true);
        titles.addView(title);
        titles.addView(subtitle);
        row.addView(titles, Ui.lp(0, -2, 1));

        LinearLayout buttons = Ui.row(this);
        buttons.addView(Ui.roundButton(this, R.drawable.ic_search, false, 46, getString(R.string.search),
                v -> focusSearch()));
        View add = Ui.roundButton(this, R.drawable.ic_add, true, 46, getString(R.string.add_contact),
                v -> addContact(null));
        addButton = add;
        buttons.addView(add, Ui.margins(Ui.lp(Ui.dp(this, 46), Ui.dp(this, 46)), this, 10, 0, 10, 0));
        buttons.addView(Ui.roundButton(this, R.drawable.ic_more, false, 46, getString(R.string.more),
                this::showMenu));
        headerButtons = buttons;
        row.addView(buttons);
        h.addView(row, Ui.lp(-1, -2));

        FrameLayout m = new FrameLayout(this);
        ImageView hills = new ImageView(this);
        hills.setImageResource(R.drawable.hlavicka_hory);
        hills.setScaleType(ImageView.ScaleType.CENTER_CROP);
        m.addView(hills, Ui.match());
        View fadeTop = new View(this);
        fadeTop.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {Ui.BG_TOP, 0x0007192A}));
        m.addView(fadeTop, new FrameLayout.LayoutParams(-1, Ui.dp(this, 36), Gravity.TOP));
        View fadeBottom = new View(this);
        fadeBottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[] {0xFF061626, 0x00061626}));
        m.addView(fadeBottom, new FrameLayout.LayoutParams(-1, Ui.dp(this, 24), Gravity.BOTTOM));
        mountains = m;
        LinearLayout.LayoutParams mp = Ui.lp(-1, Ui.dp(this, 84));
        mp.topMargin = Ui.dp(this, 6);
        h.addView(m, mp);
        return h;
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        if (tab == TAB_HISTORY) {
            menu.getMenu().add(0, 4, 0, R.string.menu_clear_history);
        } else {
            menu.getMenu().add(0, 1, 0, R.string.menu_new_group);
            if (openGroup != null) {
                menu.getMenu().add(0, 2, 0, R.string.menu_rename_group);
                menu.getMenu().add(0, 3, 0, R.string.menu_delete_group);
            }
        }
        menu.setOnMenuItemClickListener(item -> {
            final Repo.Bucket g = openGroup;
            switch (item.getItemId()) {
                case 1:
                    Actions.askText(this, R.string.menu_new_group, "", t -> background(() -> Repo.createGroup(this, t)));
                    chip = CHIP_GROUPS;
                    openGroup = null;
                    openCompany = null;
                    selectTab(TAB_CONTACTS);
                    return true;
                case 2:
                    Actions.askText(this, R.string.menu_rename_group, g.title, t -> {
                        g.title = t;
                        background(() -> Repo.renameGroup(this, g.id, t));
                    });
                    return true;
                case 3:
                    Actions.confirm(this, R.string.delete_group_q, R.string.delete, () -> {
                        openGroup = null;
                        background(() -> Repo.deleteGroup(this, g.id));
                    });
                    return true;
                case 4:
                    Actions.confirm(this, R.string.clear_history_q, R.string.delete,
                            () -> background(() -> Repo.clearCalls(this)));
                    return true;
                default:
                    return false;
            }
        });
        menu.show();
    }

    private void background(Runnable r) {
        bg.execute(() -> {
            try {
                r.run();
            } catch (Exception ignored) {
                // The observer reloads what is there.
            }
            main.post(this::reload);
        });
    }

    // ---- Contacts page ----

    private LinearLayout buildContactsPage() {
        LinearLayout page = Ui.column(this);

        LinearLayout box = Ui.row(this);
        GradientDrawable bg = Ui.shape(this, 0xCC08202B, 0xFF5FC21A, 1.5f, 30);
        box.setBackground(bg);
        box.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 10), 0);
        box.addView(Ui.icon(this, R.drawable.ic_search, Ui.TEXT_2, 24));
        search = new EditText(this);
        search.setHint(R.string.search_hint);
        search.setBackground(null);
        search.setSingleLine(true);
        search.setTextColor(Ui.TEXT);
        search.setHintTextColor(Ui.TEXT_2);
        search.setTextSize(17);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        search.setPadding(Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                reload();
            }
        });
        box.addView(search, Ui.lp(0, -2, 1));
        Intent voice = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        if (voice.resolveActivity(getPackageManager()) != null) {
            ImageView mic = Ui.icon(this, R.drawable.ic_mic, Ui.TEXT, 26);
            mic.setContentDescription(getString(R.string.voice_search));
            int p = Ui.dp(this, 8);
            mic.setPadding(p, p, p, p);
            mic.setLayoutParams(Ui.lp(Ui.dp(this, 42), Ui.dp(this, 42)));
            Ui.clickable(mic, null);
            mic.setOnClickListener(v -> {
                voice.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                try {
                    startActivityForResult(voice, REQ_VOICE);
                } catch (Exception ignored) {
                    // No recogniser after all.
                }
            });
            box.addView(mic);
        }
        page.addView(box, Ui.margins(Ui.lp(-1, -2), this, 16, 4, 16, 0));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = Ui.row(this);
        chipRow.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 10), 0);
        addChip(chipRow, CHIP_ALL, R.drawable.ic_group, R.string.chip_all);
        addChip(chipRow, CHIP_FAVORITES, R.drawable.ic_star, R.string.chip_favorites);
        addChip(chipRow, CHIP_GROUPS, R.drawable.ic_group, R.string.chip_groups);
        addChip(chipRow, CHIP_COMPANIES, R.drawable.ic_business, R.string.chip_companies);
        hs.addView(chipRow);
        page.addView(hs, Ui.margins(Ui.lp(-1, -2), this, 0, 14, 0, 6));

        FrameLayout content = new FrameLayout(this);
        contactList = new ListView(this);
        contactList.setDivider(null);
        contactList.setSelector(new android.graphics.drawable.ColorDrawable(0));
        contactList.setClipToPadding(false);
        contactList.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 12));
        contactList.setAdapter(contactAdapter);
        contactList.setFastScrollEnabled(true);
        content.addView(contactList, Ui.match());

        contactsEmpty = Ui.column(this);
        contactsEmpty.setGravity(Gravity.CENTER_HORIZONTAL);
        contactsEmpty.setPadding(Ui.dp(this, 28), Ui.dp(this, 24), Ui.dp(this, 28), 0);
        FrameLayout glow = new FrameLayout(this);
        glow.setBackground(Ui.glow(this, 110));
        ImageView big = new ImageView(this);
        big.setImageResource(R.mipmap.ic_kontakty);
        glow.addView(big, new FrameLayout.LayoutParams(Ui.dp(this, 150), Ui.dp(this, 150), Gravity.CENTER));
        contactsEmpty.addView(glow, Ui.lp(Ui.dp(this, 230), Ui.dp(this, 210)));
        contactsEmptyTitle = Ui.text(this, "", 24, Ui.TEXT, true);
        contactsEmptyTitle.setGravity(Gravity.CENTER);
        contactsEmpty.addView(contactsEmptyTitle, Ui.margins(Ui.lp(-1, -2), this, 0, 4, 0, 0));
        contactsEmptyText = Ui.text(this, "", 17, Ui.TEXT_2, false);
        contactsEmptyText.setGravity(Gravity.CENTER);
        contactsEmpty.addView(contactsEmptyText, Ui.margins(Ui.lp(-1, -2), this, 0, 10, 0, 0));
        contactsEmptyButton = Ui.pillButton(this, R.drawable.ic_add, getString(R.string.add_contact),
                v -> addContact(null));
        contactsEmpty.addView(contactsEmptyButton, Ui.margins(Ui.lp(-2, -2), this, 0, 30, 0, 0));
        android.widget.ScrollView es = new android.widget.ScrollView(this);
        es.addView(contactsEmpty, Ui.lp(-1, -2));
        content.addView(es, Ui.match());
        page.addView(content, Ui.lp(-1, 0, 1));
        return page;
    }

    private void addChip(LinearLayout row, int which, int icon, int label) {
        LinearLayout c = Ui.row(this);
        c.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 20), Ui.dp(this, 10));
        ImageView i = Ui.icon(this, icon, Ui.TEXT, 22);
        c.addView(i);
        TextView t = Ui.text(this, getString(label), 16, Ui.TEXT, true);
        t.setPadding(Ui.dp(this, 10), 0, 0, 0);
        c.addView(t);
        c.setOnClickListener(v -> {
            chip = which;
            openGroup = null;
            openCompany = null;
            updateChips();
            reload();
        });
        row.addView(c, Ui.margins(Ui.lp(-2, -2), this, 0, 0, 10, 0));
        chips.add(c);
    }

    private void updateChips() {
        for (int k = 0; k < chips.size(); k++) {
            LinearLayout c = chips.get(k);
            boolean on = k == chip;
            Ui.clickable(c, on ? Ui.greenGlow(this, 26) : Ui.glass(this, 26));
            int color = on ? Ui.ON_GREEN : Ui.TEXT;
            ((ImageView) c.getChildAt(0)).setImageTintList(android.content.res.ColorStateList.valueOf(color));
            ((TextView) c.getChildAt(1)).setTextColor(color);
        }
    }

    private void focusSearch() {
        if (tab != TAB_CONTACTS) selectTab(TAB_CONTACTS);
        search.requestFocus();
        getSystemService(InputMethodManager.class).showSoftInput(search, 0);
    }

    private void addContact(String phone) {
        Intent i = new Intent(this, EditActivity.class);
        if (phone != null) i.putExtra(ContactsContract.Intents.Insert.PHONE, phone);
        if (openGroup != null) i.putExtra(EditActivity.EXTRA_GROUP, openGroup.id);
        if (openCompany != null) i.putExtra(ContactsContract.Intents.Insert.COMPANY, openCompany);
        startActivity(i);
    }

    private void openContact(long id) {
        Intent i = new Intent(this, ContactActivity.class);
        i.setData(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id));
        startActivity(i);
    }

    // ---- Phone home (PhoneActivity) ----

    private LinearLayout buildPhonePage() {
        LinearLayout page = Ui.column(this);
        LinearLayout tiles = Ui.row(this);
        tiles.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        phoneTile(tiles, R.drawable.ic_star_border, R.string.tile_speed_dial, () -> {
            chip = CHIP_FAVORITES;
            selectTab(TAB_CONTACTS);
        });
        phoneTile(tiles, R.drawable.ic_person, R.string.tab_contacts, () -> {
            chip = CHIP_ALL;
            selectTab(TAB_CONTACTS);
        });
        phoneTile(tiles, R.drawable.ic_history, R.string.tab_history, () -> selectTab(TAB_HISTORY));
        phoneTile(tiles, R.drawable.ic_voicemail, R.string.tile_voicemail, () -> Actions.voicemail(this));
        page.addView(tiles, Ui.lp(-1, -2));

        LinearLayout box = Ui.row(this);
        box.setBackground(Ui.shape(this, 0xCC08202B, 0xFF9CFF3A, 2f, 30));
        box.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 10), 0);
        box.addView(Ui.icon(this, R.drawable.ic_search, Ui.TEXT, 24));
        phoneSearch = new EditText(this);
        phoneSearch.setHint(R.string.phone_search_hint);
        phoneSearch.setBackground(null);
        phoneSearch.setSingleLine(true);
        phoneSearch.setTextColor(Ui.TEXT);
        phoneSearch.setHintTextColor(Ui.TEXT_2);
        phoneSearch.setTextSize(16);
        phoneSearch.setPadding(Ui.dp(this, 14), Ui.dp(this, 15), Ui.dp(this, 8), Ui.dp(this, 15));
        phoneSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                reload();
            }
        });
        box.addView(phoneSearch, Ui.lp(0, -2, 1));
        page.addView(box, Ui.margins(Ui.lp(-1, -2), this, 16, 14, 16, 4));

        LinearLayout head = Ui.row(this);
        head.setPadding(Ui.dp(this, 22), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 6));
        phoneHeading = Ui.text(this, getString(R.string.recent_calls), 21, Ui.TEXT, true);
        head.addView(phoneHeading, Ui.lp(0, -2, 1));
        LinearLayout all = Ui.row(this);
        Ui.clickable(all, null);
        all.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), 0, Ui.dp(this, 4));
        all.addView(Ui.text(this, getString(R.string.show_all), 15, Ui.TEXT_2, false));
        all.addView(Ui.icon(this, R.drawable.ic_chevron_right, Ui.TEXT_2, 22));
        all.setOnClickListener(v -> selectTab(TAB_HISTORY));
        phoneShowAll = all;
        head.addView(all);
        page.addView(head, Ui.lp(-1, -2));

        android.widget.ScrollView sc = new android.widget.ScrollView(this);
        phoneList = Ui.column(this);
        sc.addView(phoneList, Ui.lp(-1, -2));
        page.addView(sc, Ui.lp(-1, 0, 1));

        LinearLayout dial = Ui.pillButton(this, R.drawable.ic_dialpad, getString(R.string.dial_number),
                v -> selectTab(TAB_DIALPAD));
        LinearLayout.LayoutParams dl = Ui.margins(Ui.lp(-2, -2), this, 0, 10, 0, 6);
        dl.gravity = Gravity.CENTER_HORIZONTAL;
        page.addView(dial, dl);
        return page;
    }

    private void phoneTile(LinearLayout row, int icon, int label, Runnable r) {
        LinearLayout t = Ui.column(this);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 10));
        Ui.clickable(t, Ui.shape(this, 0xCC0A2A33, 0xAA5FC21A, 1.5f, 20));
        t.addView(Ui.icon(this, icon, Ui.TEXT, 32));
        TextView l = Ui.text(this, getString(label), 14, Ui.TEXT, true);
        l.setGravity(Gravity.CENTER);
        l.setMaxLines(2);
        t.addView(l, Ui.margins(Ui.lp(-1, -2), this, 0, 6, 0, 0));
        t.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = Ui.lp(0, Ui.dp(this, 96), 1);
        if (row.getChildCount() > 0) lp.leftMargin = Ui.dp(this, 8);
        row.addView(t, lp);
    }

    // ---- History page ----

    private LinearLayout buildHistoryPage() {
        LinearLayout page = Ui.column(this);
        FrameLayout f = new FrameLayout(this);
        historyList = new ListView(this);
        historyList.setDivider(null);
        historyList.setSelector(new android.graphics.drawable.ColorDrawable(0));
        historyList.setAdapter(historyAdapter);
        f.addView(historyList, Ui.match());
        historyEmpty = Ui.text(this, getString(R.string.empty_history), 18, Ui.TEXT_2, false);
        historyEmpty.setGravity(Gravity.CENTER);
        f.addView(historyEmpty, Ui.match());
        page.addView(f, Ui.lp(-1, 0, 1));
        return page;
    }

    // ---- Dial pad ----

    private static final String[][] KEYS = {
        {"1", ""}, {"2", "ABC"}, {"3", "DEF"},
        {"4", "GHI"}, {"5", "JKL"}, {"6", "MNO"},
        {"7", "PQRS"}, {"8", "TUV"}, {"9", "WXYZ"},
        {"*", ""}, {"0", "+"}, {"#", ""},
    };

    private LinearLayout buildDialpadPage() {
        LinearLayout page = Ui.column(this);
        page.setGravity(Gravity.CENTER_HORIZONTAL);

        dialMatches = Ui.column(this);
        page.addView(dialMatches, Ui.margins(Ui.lp(-1, 0, 1), this, 16, 0, 16, 0));

        LinearLayout numberRow = Ui.row(this);
        numberRow.setPadding(Ui.dp(this, 56), 0, Ui.dp(this, 8), 0);
        dialNumber = Ui.text(this, "", 34, Ui.TEXT, false);
        dialNumber.setGravity(Gravity.CENTER);
        dialNumber.setSingleLine(true);
        dialNumber.setEllipsize(TextUtils.TruncateAt.START);
        numberRow.addView(dialNumber, Ui.lp(0, -2, 1));
        ImageView back = Ui.icon(this, R.drawable.ic_backspace, Ui.TEXT_2, 28);
        int p = Ui.dp(this, 10);
        back.setPadding(p, p, p, p);
        back.setLayoutParams(Ui.lp(Ui.dp(this, 48), Ui.dp(this, 48)));
        back.setContentDescription(getString(R.string.delete_digit));
        Ui.clickable(back, null);
        back.setOnClickListener(v -> {
            CharSequence s = dialNumber.getText();
            if (s.length() > 0) setDialNumber(s.subSequence(0, s.length() - 1).toString());
        });
        back.setOnLongClickListener(v -> {
            setDialNumber("");
            return true;
        });
        numberRow.addView(back);
        page.addView(numberRow, Ui.lp(-1, -2));

        TextView add = Ui.text(this, getString(R.string.add_to_contacts), 15, Ui.GREEN, true);
        add.setGravity(Gravity.CENTER);
        add.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        Ui.clickable(add, null);
        add.setOnClickListener(v -> addContact(dialNumber.getText().toString()));
        dialAddContact = add;
        page.addView(add, Ui.lp(-2, -2));

        LinearLayout grid = Ui.column(this);
        for (int r = 0; r < 4; r++) {
            LinearLayout row = Ui.row(this);
            row.setGravity(Gravity.CENTER);
            for (int k = 0; k < 3; k++) {
                String[] key = KEYS[r * 3 + k];
                row.addView(dialKey(key[0], key[1]), Ui.margins(Ui.lp(Ui.dp(this, 76), Ui.dp(this, 76)),
                        this, 14, 6, 14, 6));
            }
            grid.addView(row);
        }
        page.addView(grid, Ui.margins(Ui.lp(-2, -2), this, 0, 6, 0, 8));

        View call = Ui.roundButton(this, R.drawable.ic_call, true, 72, getString(R.string.call), v -> {
            String n = dialNumber.getText().toString();
            if (n.isEmpty()) {
                fillLastNumber();
            } else {
                Actions.call(this, n);
            }
        });
        page.addView(call, Ui.margins(Ui.lp(Ui.dp(this, 72), Ui.dp(this, 72)), this, 0, 0, 0, 10));
        setDialNumber("");
        return page;
    }

    private View dialKey(String digit, String letters) {
        LinearLayout k = Ui.column(this);
        k.setGravity(Gravity.CENTER);
        Ui.clickable(k, Ui.circle(Ui.GLASS_STRONG, Ui.GLASS_STROKE, Ui.dp(this, 1)));
        TextView d = Ui.text(this, digit, 30, Ui.TEXT, false);
        d.setGravity(Gravity.CENTER);
        k.addView(d);
        if (!letters.isEmpty()) {
            TextView l = Ui.text(this, letters, 11, Ui.TEXT_3, true);
            l.setGravity(Gravity.CENTER);
            l.setLetterSpacing(0.15f);
            k.addView(l);
        }
        k.setContentDescription(digit);
        k.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            tone(digit.charAt(0));
            setDialNumber(dialNumber.getText() + digit);
        });
        if ("0".equals(digit)) {
            k.setOnLongClickListener(v -> {
                setDialNumber(dialNumber.getText() + "+");
                return true;
            });
        }
        return k;
    }

    private void tone(char c) {
        int t;
        if (c >= '0' && c <= '9') {
            t = ToneGenerator.TONE_DTMF_0 + (c - '0');
        } else if (c == '*') {
            t = ToneGenerator.TONE_DTMF_S;
        } else {
            t = ToneGenerator.TONE_DTMF_P;
        }
        try {
            if (tones == null) tones = new ToneGenerator(AudioManager.STREAM_DTMF, 70);
            tones.startTone(t, 120);
        } catch (RuntimeException ignored) {
            // No tone generator.
        }
    }

    private void setDialNumber(String n) {
        dialNumber.setText(n);
        dialAddContact.setVisibility(n.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        final int gen = ++generation;
        final String digits = n;
        if (!Actions.canReadContacts(this)) return;
        bg.execute(() -> {
            List<String[]> m = Repo.numberMatches(this, digits, 3);
            main.post(() -> {
                if (gen == generation || digits.equals(dialNumber.getText().toString())) showMatches(m);
            });
        });
    }

    private void showMatches(List<String[]> matches) {
        dialMatches.removeAllViews();
        View spacer = new View(this);
        dialMatches.addView(spacer, Ui.lp(-1, 0, 1));
        for (String[] m : matches) {
            LinearLayout r = Ui.row(this);
            r.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
            Ui.clickable(r, Ui.glass(this, 18));
            r.addView(Ui.avatar(this, m[0], m[2], 40));
            LinearLayout t = Ui.column(this);
            t.setPadding(Ui.dp(this, 12), 0, 0, 0);
            t.addView(Ui.text(this, m[0], 16, Ui.TEXT, true));
            t.addView(Ui.text(this, m[1], 14, Ui.TEXT_2, false));
            r.addView(t, Ui.lp(0, -2, 1));
            r.addView(Ui.icon(this, R.drawable.ic_call, Ui.GREEN, 22));
            r.setOnClickListener(v -> Actions.call(this, m[1]));
            dialMatches.addView(r, Ui.margins(Ui.lp(-1, -2), this, 0, 4, 0, 0));
        }
    }

    private void fillLastNumber() {
        if (!Actions.granted(this, android.Manifest.permission.READ_CALL_LOG)) return;
        bg.execute(() -> {
            List<Repo.Call> c = Repo.calls(this, 20);
            for (Repo.Call k : c) {
                if (k.type == CallLog.Calls.OUTGOING_TYPE && !TextUtils.isEmpty(k.number)) {
                    main.post(() -> setDialNumber(k.number));
                    return;
                }
            }
        });
    }

    // ---- Navigation ----

    private View buildNavigation() {
        LinearLayout nav = Ui.row(this);
        GradientDrawable bg = Ui.shape(this, 0xE60A2330, 0x9946A06E, 1.2f, 30);
        nav.setBackground(bg);
        nav.setPadding(Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8));
        if (phone) {
            addNav(nav, TAB_PHONE, R.drawable.ic_call, R.string.nav_phone);
            addNav(nav, TAB_CONTACTS, R.drawable.ic_person, R.string.tab_contacts);
            addNav(nav, TAB_HISTORY, R.drawable.ic_history, R.string.nav_history);
            addNav(nav, NAV_SETTINGS, R.drawable.ic_settings, R.string.nav_settings);
        } else {
            addNav(nav, TAB_CONTACTS, R.drawable.ic_person, R.string.tab_contacts);
            addNav(nav, TAB_HISTORY, R.drawable.ic_history, R.string.tab_history);
            addNav(nav, TAB_DIALPAD, R.drawable.ic_dialpad, R.string.tab_dialpad);
        }
        return nav;
    }

    private void addNav(LinearLayout nav, int which, int icon, int label) {
        LinearLayout item = Ui.column(this);
        item.setGravity(Gravity.CENTER);
        item.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        Ui.clickable(item, Ui.shape(this, 0, 0, 0, 22));
        item.addView(Ui.icon(this, icon, Ui.TEXT, 30));
        TextView t = Ui.text(this, getString(label), 14, Ui.TEXT, false);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        item.addView(t);
        item.setTag(which);
        item.setOnClickListener(v -> {
            if (which == NAV_SETTINGS) {
                Actions.start(this, new Intent(android.telecom.TelecomManager.ACTION_SHOW_CALL_SETTINGS));
            } else {
                selectTab(which);
            }
        });
        nav.addView(item, Ui.lp(0, -2, 1));
        navItems.add(item);
    }

    private void selectTab(int which) {
        tab = which;
        for (int k = 0; k < navItems.size(); k++) {
            LinearLayout item = navItems.get(k);
            Ui.setNav(item, ((Integer) item.getTag()) == which);
        }
        if (which == TAB_PHONE) {
            title.setText(R.string.phone_name);
            subtitle.setText(R.string.phone_subtitle);
        } else if (which == TAB_CONTACTS) {
            title.setText(R.string.app_name);
            subtitle.setText(R.string.subtitle_contacts);
        } else if (which == TAB_HISTORY) {
            title.setText(R.string.tab_history);
            subtitle.setText(R.string.subtitle_history);
        } else {
            title.setText(R.string.tab_dialpad);
            subtitle.setText(R.string.subtitle_dialpad);
        }
        headerButtons.setVisibility(which == TAB_DIALPAD ? View.GONE : View.VISIBLE);
        addButton.setVisibility(which == TAB_PHONE || which == TAB_HISTORY ? View.GONE : View.VISIBLE);
        mountains.setVisibility(which == TAB_DIALPAD ? View.GONE : View.VISIBLE);
        if (which != TAB_CONTACTS) {
            getSystemService(InputMethodManager.class).hideSoftInputFromWindow(search.getWindowToken(), 0);
        }
        if (which != TAB_PHONE && phoneSearch != null) {
            getSystemService(InputMethodManager.class).hideSoftInputFromWindow(phoneSearch.getWindowToken(), 0);
        }
        updateChips();
        updatePages();
        reload();
    }

    private void updatePages() {
        boolean allowed = Actions.canReadContacts(this);
        permissionView.setVisibility(allowed ? View.GONE : View.VISIBLE);
        contactsPage.setVisibility(allowed && tab == TAB_CONTACTS ? View.VISIBLE : View.GONE);
        historyPage.setVisibility(allowed && tab == TAB_HISTORY ? View.VISIBLE : View.GONE);
        dialpadPage.setVisibility(tab == TAB_DIALPAD ? View.VISIBLE : View.GONE);
        phonePage.setVisibility(allowed && tab == TAB_PHONE ? View.VISIBLE : View.GONE);
        if (tab == TAB_DIALPAD) permissionView.setVisibility(View.GONE);
    }

    private LinearLayout buildPermissionView() {
        LinearLayout v = Ui.column(this);
        v.setGravity(Gravity.CENTER);
        v.setPadding(Ui.dp(this, 32), 0, Ui.dp(this, 32), 0);
        TextView t = Ui.text(this, getString(R.string.permission_needed), 18, Ui.TEXT, false);
        t.setGravity(Gravity.CENTER);
        v.addView(t);
        v.addView(Ui.pillButton(this, 0, getString(R.string.permission_grant), x -> {
            if (shouldShowRequestPermissionRationale(android.Manifest.permission.READ_CONTACTS)
                    || !Actions.canReadContacts(this)) {
                requestPermissions(Actions.PERMISSIONS, REQ_PERMISSIONS);
            }
        }), Ui.margins(Ui.lp(-2, -2), this, 0, 24, 0, 0));
        return v;
    }

    // ---- Loading ----

    private void reload() {
        if (title == null) return;
        updatePages();
        if (!Actions.canReadContacts(this)) return;
        final int gen = ++generation;
        final int t = tab;
        final int c = chip;
        final String filter = search.getText().toString().trim();
        final Repo.Bucket group = openGroup;
        final String company = openCompany;
        final boolean calls = Actions.granted(this, android.Manifest.permission.READ_CALL_LOG);
        final String phoneQuery = phoneSearch == null ? "" : phoneSearch.getText().toString().trim();
        bg.execute(() -> {
            List<Object> rows = new ArrayList<>();
            int empty = 0;
            try {
                if (t == TAB_PHONE) {
                    if (!phoneQuery.isEmpty()) {
                        rows.addAll(Repo.contacts(this, phoneQuery, Repo.MODE_ALL, -1, null));
                        empty = R.string.empty_search;
                    } else if (calls) {
                        rows.addAll(Repo.calls(this, 6));
                        empty = R.string.empty_history;
                    }
                } else if (t == TAB_HISTORY) {
                    if (calls) rows.addAll(Repo.calls(this, 500));
                } else if (t == TAB_CONTACTS) {
                    if (!filter.isEmpty()) {
                        rows.addAll(Repo.contacts(this, filter, Repo.MODE_ALL, -1, null));
                        empty = R.string.empty_search;
                    } else if (group != null) {
                        rows.add(new Back(group.title));
                        rows.addAll(Repo.contacts(this, null, Repo.MODE_ALL, group.id, null));
                        empty = R.string.empty_group;
                    } else if (company != null) {
                        rows.add(new Back(company));
                        rows.addAll(Repo.contacts(this, null, Repo.MODE_ALL, -1, company));
                        empty = R.string.empty_group;
                    } else if (c == CHIP_FAVORITES) {
                        rows.addAll(Repo.contacts(this, null, Repo.MODE_STARRED, -1, null));
                        empty = R.string.empty_favorites;
                    } else if (c == CHIP_GROUPS) {
                        rows.addAll(Repo.groups(this));
                        empty = R.string.empty_groups;
                    } else if (c == CHIP_COMPANIES) {
                        rows.addAll(Repo.companies(this));
                        empty = R.string.empty_companies;
                    } else {
                        addWithSections(rows, Repo.contacts(this, null, Repo.MODE_ALL, -1, null));
                    }
                }
            } catch (Exception e) {
                // Provider busy or permission revoked: show what we have.
            }
            final int emptyText = empty;
            main.post(() -> {
                if (gen != generation || isDestroyed()) return;
                show(t, rows, emptyText);
            });
        });
    }

    private static void addWithSections(List<Object> rows, List<Repo.Item> items) {
        String last = null;
        for (Repo.Item it : items) {
            String s = section(it.name);
            if (!s.equals(last)) {
                rows.add(s);
                last = s;
            }
            rows.add(it);
        }
    }

    private static String section(String name) {
        if (TextUtils.isEmpty(name)) return "#";
        String n = name.trim();
        if (n.isEmpty()) return "#";
        int cp = n.codePointAt(0);
        if (!Character.isLetter(cp)) return "#";
        String first = new String(Character.toChars(cp)).toUpperCase(Locale.getDefault());
        if (n.length() > 1 && (first.equals("C") || first.equals("Č")) && Character.toUpperCase(n.charAt(1)) == 'H'
                && Locale.getDefault().getLanguage().equals("sk")) {
            return "CH";
        }
        String base = Normalizer.normalize(first, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        if (Locale.getDefault().getLanguage().equals("sk") && (first.equals("Č") || first.equals("Š")
                || first.equals("Ž") || first.equals("Ď") || first.equals("Ť") || first.equals("Ľ")
                || first.equals("Ň") || first.equals("Ř"))) {
            return first;
        }
        return base.isEmpty() ? first : base;
    }

    private void show(int t, List<Object> rows, int emptyText) {
        if (t == TAB_PHONE) {
            boolean searching = phoneSearch.getText().toString().trim().length() > 0;
            phoneHeading.setText(searching ? R.string.results : R.string.recent_calls);
            phoneShowAll.setVisibility(searching ? View.GONE : View.VISIBLE);
            phoneList.removeAllViews();
            for (Object o : rows) {
                View v = o instanceof Repo.Call ? callRow((Repo.Call) o) : contactRow((Repo.Item) o);
                phoneList.addView(v, Ui.lp(-1, -2));
            }
            if (rows.isEmpty() && emptyText != 0) {
                TextView e = Ui.text(this, getString(emptyText), 16, Ui.TEXT_3, false);
                e.setGravity(Gravity.CENTER);
                e.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 20));
                phoneList.addView(e, Ui.lp(-1, -2));
            }
            return;
        }
        if (t == TAB_HISTORY) {
            historyAdapter.set(rows);
            historyEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
            return;
        }
        if (t != TAB_CONTACTS) return;
        contactAdapter.set(rows);
        boolean onlyBack = rows.size() == 1 && rows.get(0) instanceof Back;
        boolean empty = rows.isEmpty() || onlyBack;
        View emptyScroll = (View) contactsEmpty.getParent();
        boolean bigEmpty = empty && emptyText == 0;
        if (bigEmpty) {
            contactsEmptyTitle.setText(R.string.empty_title);
            contactsEmptyText.setText(R.string.empty_text);
            contactsEmptyText.setVisibility(View.VISIBLE);
            contactsEmptyTitle.setVisibility(View.VISIBLE);
            contactsEmptyButton.setVisibility(View.VISIBLE);
            contactsEmpty.getChildAt(0).setVisibility(View.VISIBLE);
        } else if (empty) {
            contactsEmptyTitle.setVisibility(View.GONE);
            contactsEmptyText.setText(emptyText);
            contactsEmptyText.setVisibility(View.VISIBLE);
            contactsEmpty.getChildAt(0).setVisibility(View.GONE);
            boolean canAdd = emptyText == R.string.empty_group || emptyText == R.string.empty_search;
            contactsEmptyButton.setVisibility(canAdd ? View.VISIBLE : View.GONE);
        }
        emptyScroll.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (onlyBack) {
            // Keep the back row visible above the empty text.
            emptyScroll.setPadding(0, Ui.dp(this, 64), 0, 0);
        } else {
            emptyScroll.setPadding(0, 0, 0, 0);
        }
        contactList.setVisibility(empty && !onlyBack ? View.GONE : View.VISIBLE);
    }

    /** The row that leads back from a group or a company to the list of them. */
    private static final class Back {
        final String title;

        Back(String title) {
            this.title = title;
        }
    }

    // ---- Rows ----

    private final class RowAdapter extends BaseAdapter {
        private List<Object> rows = new ArrayList<>();

        void set(List<Object> r) {
            rows = r;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 5;
        }

        @Override
        public int getItemViewType(int position) {
            Object o = rows.get(position);
            if (o instanceof Repo.Item) return 0;
            if (o instanceof Repo.Bucket) return 1;
            if (o instanceof Repo.Call) return 2;
            if (o instanceof Back) return 3;
            return 4;
        }

        @Override
        public boolean isEnabled(int position) {
            return !(rows.get(position) instanceof String);
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            Object o = rows.get(position);
            if (o instanceof Repo.Item) return contactRow((Repo.Item) o);
            if (o instanceof Repo.Bucket) return bucketRow((Repo.Bucket) o);
            if (o instanceof Repo.Call) return callRow((Repo.Call) o);
            if (o instanceof Back) return backRow((Back) o);
            TextView s = Ui.text(MainActivity.this, (String) o, 15, Ui.GREEN, true);
            s.setPadding(Ui.dp(MainActivity.this, 28), Ui.dp(MainActivity.this, 10), 0, Ui.dp(MainActivity.this, 2));
            return s;
        }
    }

    private View card(View content) {
        FrameLayout f = new FrameLayout(this);
        f.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 4));
        f.addView(content);
        return f;
    }

    private View contactRow(Repo.Item it) {
        LinearLayout r = Ui.row(this);
        r.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10));
        Ui.clickable(r, Ui.glass(this, 20));
        String name = TextUtils.isEmpty(it.name) ? getString(R.string.no_name) : it.name;
        r.addView(Ui.avatar(this, it.name, it.photo, 48));
        LinearLayout t = Ui.column(this);
        t.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 6), 0);
        TextView n = Ui.text(this, name, 17, Ui.TEXT, true);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(n);
        if (!TextUtils.isEmpty(it.subtitle)) {
            TextView s = Ui.text(this, it.subtitle, 14, Ui.TEXT_3, false);
            s.setSingleLine(true);
            s.setEllipsize(TextUtils.TruncateAt.END);
            t.addView(s);
        }
        r.addView(t, Ui.lp(0, -2, 1));
        if (it.starred) r.addView(Ui.icon(this, R.drawable.ic_star, 0xFFFFD54F, 20));
        if (it.hasPhone) {
            View call = Ui.roundButton(this, R.drawable.ic_call, true, 40, getString(R.string.call), v ->
                    bg.execute(() -> {
                        String number = Repo.primaryPhone(this, it.id);
                        main.post(() -> Actions.call(this, number));
                    }));
            r.addView(call, Ui.margins(Ui.lp(Ui.dp(this, 40), Ui.dp(this, 40)), this, 10, 0, 4, 0));
        }
        r.setOnClickListener(v -> openContact(it.id));
        return card(r);
    }

    private View bucketRow(Repo.Bucket b) {
        LinearLayout r = Ui.row(this);
        r.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14));
        Ui.clickable(r, Ui.glass(this, 20));
        boolean group = b.id >= 0;
        FrameLayout ic = new FrameLayout(this);
        ic.setBackground(Ui.circle(0x332E8A0A, 0x887BE41E, Ui.dp(this, 1)));
        ImageView i = Ui.icon(this, group ? R.drawable.ic_group : R.drawable.ic_business, Ui.GREEN, 24);
        ic.addView(i, new FrameLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24), Gravity.CENTER));
        r.addView(ic, Ui.lp(Ui.dp(this, 46), Ui.dp(this, 46)));
        LinearLayout t = Ui.column(this);
        t.setPadding(Ui.dp(this, 14), 0, 0, 0);
        TextView n = Ui.text(this, b.title, 17, Ui.TEXT, true);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(n);
        t.addView(Ui.text(this, getString(R.string.contacts_count, b.count), 14, Ui.TEXT_3, false));
        r.addView(t, Ui.lp(0, -2, 1));
        r.addView(Ui.icon(this, R.drawable.ic_chevron_right, Ui.TEXT_3, 24));
        r.setOnClickListener(v -> {
            if (group) {
                openGroup = b;
            } else {
                openCompany = b.title;
            }
            reload();
        });
        return card(r);
    }

    private View backRow(Back b) {
        LinearLayout r = Ui.row(this);
        r.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        Ui.clickable(r, null);
        r.addView(Ui.icon(this, R.drawable.ic_chevron_left, Ui.GREEN, 28));
        TextView t = Ui.text(this, b.title, 20, Ui.TEXT, true);
        t.setPadding(Ui.dp(this, 6), 0, 0, 0);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(t, Ui.lp(0, -2, 1));
        r.setOnClickListener(v -> {
            openGroup = null;
            openCompany = null;
            reload();
        });
        return card(r);
    }

    private View callRow(Repo.Call k) {
        LinearLayout r = Ui.row(this);
        r.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10));
        Ui.clickable(r, Ui.glass(this, 20));
        boolean hidden = k.presentation != CallLog.Calls.PRESENTATION_ALLOWED || TextUtils.isEmpty(k.number);
        String who = !TextUtils.isEmpty(k.name) ? k.name
                : hidden ? getString(k.presentation == CallLog.Calls.PRESENTATION_RESTRICTED
                        ? R.string.private_number : R.string.unknown_number)
                : k.number;
        r.addView(Ui.avatar(this, TextUtils.isEmpty(k.name) ? "" : k.name, k.photo, 46));
        LinearLayout t = Ui.column(this);
        t.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 6), 0);
        boolean missed = k.type == CallLog.Calls.MISSED_TYPE || k.type == CallLog.Calls.REJECTED_TYPE;
        TextView n = Ui.text(this, who, 17, missed ? Ui.RED : Ui.TEXT, true);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(n);
        LinearLayout meta = Ui.row(this);
        int icon;
        int color;
        int what;
        if (missed) {
            icon = R.drawable.ic_call_missed;
            color = Ui.RED;
            what = R.string.missed;
        } else if (k.type == CallLog.Calls.OUTGOING_TYPE) {
            icon = R.drawable.ic_call_out;
            color = Ui.GREEN;
            what = R.string.outgoing;
        } else {
            icon = R.drawable.ic_call_in;
            color = 0xFF4FC3F7;
            what = R.string.incoming;
        }
        ImageView ti = Ui.icon(this, icon, color, 16);
        ti.setContentDescription(getString(what));
        meta.addView(ti);
        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(k.name) && !hidden) {
            sb.append(Actions.phoneType(this, k.numberType, k.numberLabel)).append(" · ");
        }
        sb.append(DateUtils.getRelativeDateTimeString(this, k.date, DateUtils.MINUTE_IN_MILLIS,
                DateUtils.WEEK_IN_MILLIS, DateUtils.FORMAT_ABBREV_ALL));
        TextView m = Ui.text(this, sb, 13, Ui.TEXT_3, false);
        m.setSingleLine(true);
        m.setEllipsize(TextUtils.TruncateAt.END);
        m.setPadding(Ui.dp(this, 6), 0, 0, 0);
        meta.addView(m);
        t.addView(meta);
        r.addView(t, Ui.lp(0, -2, 1));
        if (k.duration > 0) {
            r.addView(Ui.text(this, android.text.format.DateUtils.formatElapsedTime(k.duration), 14, Ui.TEXT_2,
                    false));
        }
        if (!hidden) {
            View call = Ui.roundButton(this, R.drawable.ic_call, true, 40, getString(R.string.call),
                    v -> Actions.call(this, k.number));
            r.addView(call, Ui.margins(Ui.lp(Ui.dp(this, 40), Ui.dp(this, 40)), this, 10, 0, 4, 0));
            r.setOnClickListener(v -> bg.execute(() -> {
                long id = Repo.contactForNumber(this, k.number);
                main.post(() -> {
                    if (id >= 0) {
                        openContact(id);
                    } else {
                        addContact(k.number);
                    }
                });
            }));
        }
        r.setOnLongClickListener(v -> {
            Actions.confirm(this, R.string.delete_call_q, R.string.delete,
                    () -> background(() -> Repo.deleteCall(this, k.id)));
            return true;
        });
        return card(r);
    }
}
