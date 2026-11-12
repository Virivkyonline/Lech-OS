/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.pdf;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.print.PrintManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import lech.os.ui.Ui;

import java.io.File;
import java.text.Collator;
import java.text.DateFormat;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * LECH PDF: the PDF files of the device, recent and favourite documents, search and tools (open, print, share).
 * PDF files are never rendered here: they open in the system PDF viewer (GrapheneOS PdfViewerGOS, pdf.js in the
 * sandboxed WebView).
 */
public class MainActivity extends Activity {
    /**
     * The GrapheneOS PDF Viewer of the system (module PdfViewerGOS). Verified in its Android 17 APK manifest:
     * package app.grapheneos.pdfviewer, activity app.grapheneos.pdfviewer.PdfViewer handles ACTION_VIEW with
     * mimeType application/pdf (categories DEFAULT, BROWSABLE).
     */
    static final String VIEWER = "app.grapheneos.pdfviewer";

    private static final int NAV_FILES = 0;
    private static final int NAV_SEARCH = 1;
    private static final int NAV_BOOKMARKS = 2;
    private static final int NAV_TOOLS = 3;
    private static final int TAB_FILES = 0;
    private static final int TAB_RECENT = 1;
    private static final int TAB_FAVORITES = 2;
    private static final int REQ_OPEN = 1;
    private static final int REQ_PRINT = 2;
    private static final int REQ_SHARE = 3;
    private static final int GOLD = 0xFFFFCC1F;
    private static final String DOT = " • ";

    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private int generation;

    private Library lib;
    private List<Pdf> found = new ArrayList<>();
    private boolean scanned;
    private int nav = NAV_FILES;
    private int tab = TAB_FILES;
    private String query = "";
    private DateFormat dates;

    private Ui.Header header;
    private LinearLayout tabBar;
    private final List<LinearLayout> tabs = new ArrayList<>();
    private View searchBox;
    private EditText search;
    private ListView list;
    private final Adapter adapter = new Adapter();
    private LinearLayout empty;
    private TextView emptyTitle;
    private TextView emptyText;
    private LinearLayout accessView;
    private View tools;
    private View fab;
    private final List<LinearLayout> navItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        lib = new Library(this);
        Locale loc = Locale.getDefault();
        dates = new SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(loc, "dMyyyy"), loc);

        LinearLayout root = Ui.column(this);
        root.setBackground(Ui.background());

        header = Ui.header(this, R.mipmap.ic_pdf);
        Ui.headerButton(header, R.drawable.ic_more, false, getString(R.string.more), this::showMenu);
        header.title.setText(R.string.header_title);
        header.subtitle.setText(R.string.subtitle);
        root.addView(header.view, Ui.lp(-1, -2));

        // Files / Recent / Favourites: one glass bar, the active tab glows green.
        tabBar = Ui.row(this);
        tabBar.setBackground(Ui.shape(this, 0xCC0A2530, 0xAA34A0A0, 1.2f, 30));
        int p4 = Ui.dp(this, 4);
        tabBar.setPadding(p4, p4, p4, p4);
        addTab(TAB_FILES, R.drawable.ic_folder_outline, R.string.tab_files);
        addTab(TAB_RECENT, R.drawable.ic_clock, R.string.tab_recent);
        addTab(TAB_FAVORITES, R.drawable.ic_star_border, R.string.tab_favorites);
        root.addView(tabBar, Ui.margins(Ui.lp(-1, -2), this, 14, 2, 14, 6));

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
                render();
            }
        });
        box.addView(search, Ui.lp(0, -2, 1));
        searchBox = box;
        root.addView(box, Ui.margins(Ui.lp(-1, -2), this, 16, 2, 16, 6));

        FrameLayout content = new FrameLayout(this);
        list = new ListView(this);
        list.setDivider(null);
        list.setSelector(new ColorDrawable(0));
        list.setClipToPadding(false);
        list.setFastScrollEnabled(true);
        list.setAdapter(adapter);
        content.addView(list, Ui.match());

        empty = Ui.column(this);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(Ui.dp(this, 32), 0, Ui.dp(this, 32), Ui.dp(this, 40));
        FrameLayout glow = new FrameLayout(this);
        glow.setBackground(Ui.glow(this, 100));
        ImageView big = new ImageView(this);
        big.setImageResource(R.mipmap.ic_pdf);
        glow.addView(big, new FrameLayout.LayoutParams(Ui.dp(this, 120), Ui.dp(this, 120), Gravity.CENTER));
        empty.addView(glow, Ui.lp(Ui.dp(this, 200), Ui.dp(this, 180)));
        emptyTitle = Ui.text(this, "", 22, Ui.TEXT, true);
        emptyTitle.setGravity(Gravity.CENTER);
        empty.addView(emptyTitle);
        emptyText = Ui.text(this, "", 16, Ui.TEXT_2, false);
        emptyText.setGravity(Gravity.CENTER);
        empty.addView(emptyText, Ui.margins(Ui.lp(-1, -2), this, 0, 8, 0, 0));
        content.addView(empty, Ui.match());

        accessView = Ui.column(this);
        accessView.setGravity(Gravity.CENTER);
        accessView.setPadding(Ui.dp(this, 32), 0, Ui.dp(this, 32), 0);
        TextView at = Ui.text(this, getString(R.string.access_title), 22, Ui.TEXT, true);
        at.setGravity(Gravity.CENTER);
        accessView.addView(at);
        TextView ax = Ui.text(this, getString(R.string.access_text), 16, Ui.TEXT_2, false);
        ax.setGravity(Gravity.CENTER);
        accessView.addView(ax, Ui.margins(Ui.lp(-1, -2), this, 0, 10, 0, 0));
        accessView.addView(Ui.pillButton(this, 0, getString(R.string.access_grant), v -> askAccess()),
                Ui.margins(Ui.lp(-2, -2), this, 0, 24, 0, 0));
        content.addView(accessView, Ui.match());

        tools = buildTools();
        content.addView(tools, Ui.match());

        // The round + of the design: dark glass with a neon green ring.
        fab = Ui.roundButton(this, R.drawable.ic_add, false, 64, getString(R.string.open_pdf), v -> pick(REQ_OPEN));
        Ui.clickable(fab, Ui.circle(0xF00A2418, Ui.GREEN, Ui.dp(this, 2.5f)));
        fab.setElevation(Ui.dp(this, 6));
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64),
                Gravity.BOTTOM | Gravity.END);
        fl.setMargins(0, 0, Ui.dp(this, 20), Ui.dp(this, 12));
        content.addView(fab, fl);
        root.addView(content, Ui.lp(-1, 0, 1));

        LinearLayout bar = Ui.navBar(this);
        navItems.add(Ui.navItem(bar, R.drawable.ic_folder_outline, getString(R.string.nav_files),
                v -> setNav(NAV_FILES)));
        navItems.add(Ui.navItem(bar, R.drawable.ic_search, getString(R.string.nav_search), v -> setNav(NAV_SEARCH)));
        navItems.add(Ui.navItem(bar, R.drawable.ic_bookmark_border, getString(R.string.nav_bookmarks),
                v -> setNav(NAV_BOOKMARKS)));
        navItems.add(Ui.navItem(bar, R.drawable.ic_tools, getString(R.string.nav_tools), v -> setNav(NAV_TOOLS)));
        root.addView(bar, Ui.margins(Ui.lp(-1, -2), this, 14, 4, 14, 10));

        Ui.edgeToEdge(this, root);
        setContentView(root);
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::onBack);
        updateChrome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bg.shutdownNow();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        if (requestCode == REQ_OPEN) {
            try {
                // Keeps the document readable for Recent and Favourites after a restart.
                getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // Not persistable: readable for this session only.
            }
        }
        Pdf p = Library.describe(this, u);
        if (requestCode == REQ_OPEN) {
            open(p);
        } else if (requestCode == REQ_PRINT) {
            print(p);
        } else if (requestCode == REQ_SHARE) {
            share(p);
        }
    }

    private boolean hasAccess() {
        return Environment.isExternalStorageManager();
    }

    private void askAccess() {
        Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.fromParts("package", getPackageName(), null));
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }
    }

    private void onBack() {
        if (nav != NAV_FILES) {
            setNav(NAV_FILES);
        } else if (tab != TAB_FILES) {
            setTab(TAB_FILES);
        } else {
            finish();
        }
    }

    // ---- Chrome ----

    private void addTab(int which, int icon, int label) {
        LinearLayout t = Ui.row(this);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10));
        t.addView(Ui.icon(this, icon, Ui.TEXT, 22));
        TextView l = Ui.text(this, getString(label), 16, Ui.TEXT, true);
        l.setPadding(Ui.dp(this, 8), 0, 0, 0);
        l.setSingleLine(true);
        l.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(l);
        t.setOnClickListener(v -> setTab(which));
        tabBar.addView(t, Ui.lp(0, -2, 1));
        tabs.add(t);
    }

    private void setTab(int which) {
        tab = which;
        updateChrome();
        render();
        list.setSelection(0);
    }

    private void setNav(int which) {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (nav == NAV_SEARCH && which != NAV_SEARCH) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        nav = which;
        updateChrome();
        render();
        list.setSelection(0);
        if (which == NAV_SEARCH) {
            search.requestFocus();
            search.post(() -> imm.showSoftInput(search, 0));
        }
    }

    private void updateChrome() {
        for (int k = 0; k < navItems.size(); k++) {
            LinearLayout item = navItems.get(k);
            boolean on = k == nav;
            Ui.setNav(item, on);
            Ui.clickable(item, on ? Ui.shape(this, 0x402E8A0A, 0x997BE41E, 1.2f, 24) : Ui.shape(this, 0, 0, 0, 22));
        }
        for (int k = 0; k < tabs.size(); k++) {
            LinearLayout t = tabs.get(k);
            boolean on = k == tab;
            Ui.clickable(t, on ? Ui.greenGlow(this, 26) : Ui.shape(this, 0, 0, 0, 26));
            int color = on ? Ui.ON_GREEN : Ui.TEXT;
            ((ImageView) t.getChildAt(0)).setImageTintList(ColorStateList.valueOf(color));
            ((TextView) t.getChildAt(1)).setTextColor(color);
        }
        tabBar.setVisibility(nav == NAV_FILES ? View.VISIBLE : View.GONE);
        searchBox.setVisibility(nav == NAV_SEARCH ? View.VISIBLE : View.GONE);
        header.mountains.setVisibility(nav == NAV_SEARCH ? View.GONE : View.VISIBLE);
        fab.setVisibility(nav == NAV_FILES ? View.VISIBLE : View.GONE);
        // Room under the last card for the + button.
        list.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, nav == NAV_FILES ? 88 : 10));
    }

    private void showMenu(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 1, 0, R.string.refresh);
        if (!lib.recent().isEmpty()) m.getMenu().add(0, 2, 1, R.string.clear_recent);
        m.getMenu().add(0, 3, 2, R.string.all_files_access);
        m.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: refresh(); break;
                case 2: lib.clearRecent(); render(); break;
                case 3: askAccess(); break;
                default: return false;
            }
            return true;
        });
        m.show();
    }

    // ---- Content ----

    /** Finds the PDF files of the storage again (in the background) and shows the current screen. */
    private void refresh() {
        if (list == null) return;
        render();
        if (!hasAccess()) {
            // The access screen is shown; the first scan after access is granted starts from "not scanned".
            found = new ArrayList<>();
            scanned = false;
            render();
            return;
        }
        final int gen = ++generation;
        bg.execute(() -> {
            List<Pdf> r = Library.scan(this);
            main.post(() -> {
                if (gen != generation || isDestroyed()) return;
                found = r;
                scanned = true;
                render();
            });
        });
    }

    /** Builds the rows of the current screen from what is known (no storage scan). */
    private void render() {
        if (list == null) return;
        boolean access = hasAccess();
        List<Object> rows = new ArrayList<>();
        boolean needsAccess = false;
        boolean waiting = false;
        int title = 0;
        int text = 0;
        if (nav == NAV_FILES && tab == TAB_FILES) {
            needsAccess = !access;
            waiting = !scanned;
            rows.addAll(found);
            title = R.string.empty_files_title;
            text = R.string.empty_files_text;
        } else if (nav == NAV_FILES && tab == TAB_RECENT) {
            rows.addAll(present(lib.recent()));
            title = R.string.empty_recent_title;
            text = R.string.empty_recent_text;
        } else if (nav == NAV_FILES) {
            rows.addAll(present(lib.favorites()));
            title = R.string.empty_favorites_title;
            text = R.string.empty_favorites_text;
        } else if (nav == NAV_SEARCH) {
            needsAccess = !access;
            waiting = !scanned;
            rows.addAll(matches(query));
            title = R.string.empty_search;
        } else if (nav == NAV_BOOKMARKS) {
            bookmarks(rows);
            title = R.string.empty_favorites_title;
            text = R.string.empty_favorites_text;
        }
        boolean toolsShown = nav == NAV_TOOLS;
        boolean listShown = !toolsShown && !needsAccess && !rows.isEmpty();
        tools.setVisibility(toolsShown ? View.VISIBLE : View.GONE);
        accessView.setVisibility(!toolsShown && needsAccess ? View.VISIBLE : View.GONE);
        list.setVisibility(listShown ? View.VISIBLE : View.GONE);
        empty.setVisibility(!toolsShown && !needsAccess && !waiting && rows.isEmpty() ? View.VISIBLE : View.GONE);
        if (title != 0) emptyTitle.setText(title);
        emptyText.setText(text != 0 ? getString(text) : "");
        emptyText.setVisibility(text != 0 ? View.VISIBLE : View.GONE);
        adapter.set(rows);
    }

    /** Stored documents with the current size and date of their files; files that are gone are left out. */
    private List<Pdf> present(List<Pdf> items) {
        boolean access = hasAccess();
        List<Pdf> out = new ArrayList<>();
        for (Pdf p : items) {
            if (p.path != null && access) {
                File f = new File(p.path);
                if (!f.isFile()) continue;
                Pdf c = p.copy();
                c.size = f.length();
                c.modified = f.lastModified();
                out.add(c);
            } else {
                out.add(p);
            }
        }
        return out;
    }

    /** All known PDF files (storage, recent, favourites) whose name contains the text, accents ignored. */
    private List<Pdf> matches(String text) {
        List<Pdf> all = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (Pdf p : found) if (keys.add(p.key())) all.add(p);
        for (Pdf p : present(lib.recent())) if (keys.add(p.key())) all.add(p);
        for (Pdf p : present(lib.favorites())) if (keys.add(p.key())) all.add(p);
        if (text.isEmpty()) return all;
        String q = plain(text);
        List<Pdf> out = new ArrayList<>();
        for (Pdf p : all) if (plain(p.name).contains(q)) out.add(p);
        return out;
    }

    private static String plain(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /** Bookmarks: the favourites grouped by folder; picked documents last. */
    private void bookmarks(List<Object> rows) {
        List<Pdf> favs = present(lib.favorites());
        if (favs.isEmpty()) return;
        rows.add(new Intro());
        Map<String, List<Pdf>> groups = new TreeMap<>(Collator.getInstance());
        List<Pdf> picked = new ArrayList<>();
        for (Pdf p : favs) {
            if (p.path == null) {
                picked.add(p);
            } else {
                groups.computeIfAbsent(folder(p.path), k -> new ArrayList<>()).add(p);
            }
        }
        for (Map.Entry<String, List<Pdf>> e : groups.entrySet()) {
            rows.add(new Heading(e.getKey()));
            rows.addAll(e.getValue());
        }
        if (!picked.isEmpty()) {
            rows.add(new Heading(getString(R.string.group_picked)));
            rows.addAll(picked);
        }
    }

    private String folder(String path) {
        File parent = new File(path).getParentFile();
        if (parent == null) return "/";
        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        String p = parent.getAbsolutePath();
        if (p.equals(root)) return getString(R.string.internal);
        if (p.startsWith(root + "/")) return p.substring(root.length() + 1);
        if (p.startsWith("/storage/")) return p.substring("/storage/".length());
        return p;
    }

    private static final class Heading {
        final String text;

        Heading(String text) {
            this.text = text;
        }
    }

    private static final class Intro { }

    // ---- Actions ----

    /** Opens the document in the system PDF viewer; there is deliberately no other viewer to fall back to. */
    private void open(Pdf p) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(p.contentUri(), Library.PDF);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.setPackage(VIEWER);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_viewer, Toast.LENGTH_LONG).show();
            return;
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.cannot_read, Toast.LENGTH_SHORT).show();
            return;
        }
        lib.addRecent(p);
        render();
    }

    private void share(Pdf p) {
        Uri u = p.contentUri();
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(Library.PDF);
        i.putExtra(Intent.EXTRA_STREAM, u);
        i.setClipData(ClipData.newRawUri(p.name, u));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(i, null));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.cannot_share, Toast.LENGTH_SHORT).show();
        }
    }

    private void print(Pdf p) {
        PrintManager pm = getSystemService(PrintManager.class);
        if (pm == null) {
            Toast.makeText(this, R.string.cannot_print, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            pm.print(p.name, new PdfPrintAdapter(this, p.contentUri(), p.name), null);
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.cannot_print, Toast.LENGTH_SHORT).show();
        }
    }

    /** The system file picker (DocumentsUI), PDF files only. */
    private void pick(int request) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(Library.PDF);
        try {
            startActivityForResult(i, request);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_picker, Toast.LENGTH_SHORT).show();
        }
    }

    /** Tools: choose a known PDF file (recent first) or one from the device, then print or share it. */
    private void choosePdf(int request) {
        List<Pdf> items = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (Pdf p : present(lib.recent())) if (keys.add(p.key())) items.add(p);
        for (Pdf p : found) {
            if (items.size() >= 200) break;
            if (keys.add(p.key())) items.add(p);
        }
        String[] labels = new String[items.size() + 1];
        labels[0] = getString(R.string.choose_device);
        for (int k = 0; k < items.size(); k++) labels[k + 1] = items.get(k).name;
        new AlertDialog.Builder(this)
                .setTitle(R.string.choose_pdf)
                .setItems(labels, (d, w) -> {
                    if (w == 0) {
                        pick(request);
                    } else if (request == REQ_PRINT) {
                        print(items.get(w - 1));
                    } else {
                        share(items.get(w - 1));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void itemMenu(View anchor, Pdf p) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 1, 0, R.string.open);
        m.getMenu().add(0, 2, 1, R.string.share);
        m.getMenu().add(0, 3, 2, R.string.print);
        m.getMenu().add(0, 4, 3, lib.isFavorite(p) ? R.string.remove_favorite : R.string.add_favorite);
        if (nav == NAV_FILES && tab == TAB_RECENT) m.getMenu().add(0, 5, 4, R.string.remove_recent);
        m.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: open(p); break;
                case 2: share(p); break;
                case 3: print(p); break;
                case 4: lib.toggleFavorite(p); render(); break;
                case 5: lib.removeRecent(p); render(); break;
                default: return false;
            }
            return true;
        });
        m.show();
    }

    // ---- Formatting ----

    /** "12,4 MB • 10. 11. 2025" (decimal comma and date order of the language). */
    private String meta(Pdf p) {
        StringBuilder sb = new StringBuilder();
        if (p.size >= 0) sb.append(size(p.size));
        if (p.modified > 0) {
            if (sb.length() > 0) sb.append(DOT);
            sb.append(dates.format(new Date(p.modified)));
        }
        return sb.toString();
    }

    /** Sizes in SI units like the system (1 MB = 1000 × 1000 B), one decimal for MB and GB. */
    private static String size(long b) {
        if (b < 1000) return b + " B";
        NumberFormat nf = NumberFormat.getNumberInstance();
        if (b < 100_000) {
            nf.setMaximumFractionDigits(0);
            return nf.format(b / 1000.0) + " kB";
        }
        nf.setMinimumFractionDigits(1);
        nf.setMaximumFractionDigits(1);
        if (b < 999_950_000L) return nf.format(b / 1e6) + " MB";
        return nf.format(b / 1e9) + " GB";
    }

    // ---- Rows ----

    private final class Adapter extends BaseAdapter {
        private List<Object> rows = new ArrayList<>();

        void set(List<Object> r) {
            rows = r;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int p) { return rows.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public int getViewTypeCount() { return 3; }

        @Override
        public int getItemViewType(int p) {
            Object o = rows.get(p);
            if (o instanceof Intro) return 0;
            if (o instanceof Heading) return 1;
            return 2;
        }

        @Override
        public boolean isEnabled(int p) {
            return false;
        }

        @Override
        public View getView(int p, View convert, ViewGroup parent) {
            Object o = rows.get(p);
            if (o instanceof Intro) return intro();
            if (o instanceof Heading) return heading(((Heading) o).text);
            return card((Pdf) o);
        }
    }

    /** One glass card: red PDF icon, name, size and date, favourite star and the item menu. */
    private View card(Pdf p) {
        LinearLayout r = Ui.row(this);
        r.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 2), Ui.dp(this, 10));
        Ui.clickable(r, Ui.glass(this, 22));
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(new PdfIcon());
        r.addView(icon, Ui.lp(Ui.dp(this, 42), Ui.dp(this, 48)));

        LinearLayout t = Ui.column(this);
        t.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 4), 0);
        TextView n = Ui.text(this, p.name, 17, Ui.TEXT, true);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        t.addView(n);
        TextView s = Ui.text(this, meta(p), 14, Ui.TEXT_2, false);
        s.setSingleLine(true);
        t.addView(s, Ui.margins(Ui.lp(-2, -2), this, 0, 2, 0, 0));
        r.addView(t, Ui.lp(0, -2, 1));

        boolean fav = lib.isFavorite(p);
        ImageView star = Ui.icon(this, fav ? R.drawable.ic_star : R.drawable.ic_star_border, fav ? GOLD : Ui.TEXT, 26);
        smallButton(star, getString(fav ? R.string.remove_favorite : R.string.add_favorite));
        star.setOnClickListener(v -> {
            lib.toggleFavorite(p);
            render();
        });
        r.addView(star, Ui.lp(Ui.dp(this, 44), Ui.dp(this, 44)));

        ImageView more = Ui.icon(this, R.drawable.ic_more, Ui.TEXT, 26);
        smallButton(more, getString(R.string.more));
        more.setOnClickListener(v -> itemMenu(v, p));
        r.addView(more, Ui.lp(Ui.dp(this, 40), Ui.dp(this, 44)));

        r.setOnClickListener(v -> open(p));
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        wrap.addView(r);
        return wrap;
    }

    private void smallButton(ImageView v, CharSequence description) {
        int pad = Ui.dp(this, 9);
        v.setPadding(pad, pad, pad, pad);
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), null, null));
        v.setClickable(true);
        v.setFocusable(true);
        v.setContentDescription(description);
    }

    private View heading(String text) {
        TextView h = Ui.text(this, text, 15, Ui.GREEN, true);
        h.setSingleLine(true);
        h.setEllipsize(TextUtils.TruncateAt.START);
        h.setPadding(Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 4));
        return h;
    }

    private View intro() {
        LinearLayout c = Ui.column(this);
        c.setPadding(Ui.dp(this, 6), Ui.dp(this, 2), Ui.dp(this, 6), Ui.dp(this, 2));
        c.addView(Ui.text(this, getString(R.string.bookmarks_title), 21, Ui.TEXT, true));
        c.addView(Ui.text(this, getString(R.string.bookmarks_text), 14, Ui.TEXT_2, false));
        return c;
    }

    // ---- Tools ----

    private View buildTools() {
        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.column(this);
        col.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 14));
        TextView h = Ui.text(this, getString(R.string.nav_tools), 21, Ui.TEXT, true);
        h.setPadding(Ui.dp(this, 6), Ui.dp(this, 2), Ui.dp(this, 6), Ui.dp(this, 6));
        col.addView(h);
        col.addView(toolCard(R.drawable.ic_open_file, R.string.tools_open, R.string.tools_open_sub,
                v -> pick(REQ_OPEN)), Ui.margins(Ui.lp(-1, -2), this, 0, 4, 0, 4));
        col.addView(toolCard(R.drawable.ic_print, R.string.tools_print, R.string.tools_print_sub,
                v -> choosePdf(REQ_PRINT)), Ui.margins(Ui.lp(-1, -2), this, 0, 4, 0, 4));
        col.addView(toolCard(R.drawable.ic_share, R.string.tools_share, R.string.tools_share_sub,
                v -> choosePdf(REQ_SHARE)), Ui.margins(Ui.lp(-1, -2), this, 0, 4, 0, 4));

        LinearLayout info = Ui.row(this);
        info.setBackground(Ui.shape(this, 0x66081A24, Ui.GLASS_STROKE, 1, 18));
        info.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        info.addView(Ui.icon(this, R.drawable.ic_shield, Ui.GREEN, 24));
        TextView it = Ui.text(this, getString(R.string.tools_info), 14, Ui.TEXT_2, false);
        it.setPadding(Ui.dp(this, 12), 0, 0, 0);
        info.addView(it, Ui.lp(0, -2, 1));
        col.addView(info, Ui.margins(Ui.lp(-1, -2), this, 0, 12, 0, 0));
        sv.addView(col, Ui.lp(-1, -2));
        return sv;
    }

    private View toolCard(int icon, int title, int subtitle, View.OnClickListener l) {
        LinearLayout r = Ui.row(this);
        r.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12));
        Ui.clickable(r, Ui.glass(this, 22));
        FrameLayout ib = new FrameLayout(this);
        ib.setBackground(Ui.circle(0x332E8A0A, 0x997BE41E, Ui.dp(this, 1)));
        ib.addView(Ui.icon(this, icon, Ui.GREEN, 24), new FrameLayout.LayoutParams(Ui.dp(this, 24),
                Ui.dp(this, 24), Gravity.CENTER));
        r.addView(ib, Ui.lp(Ui.dp(this, 46), Ui.dp(this, 46)));
        LinearLayout t = Ui.column(this);
        t.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 6), 0);
        t.addView(Ui.text(this, getString(title), 17, Ui.TEXT, true));
        t.addView(Ui.text(this, getString(subtitle), 14, Ui.TEXT_2, false));
        r.addView(t, Ui.lp(0, -2, 1));
        r.addView(Ui.icon(this, R.drawable.ic_chevron_right, Ui.TEXT_2, 24));
        r.setOnClickListener(l);
        return r;
    }
}
