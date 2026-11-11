/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.subory;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.util.LruCache;
import android.util.Size;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import lech.os.ui.Ui;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The file manager: categories, storage, folders, browsing, search, copy / move / delete, open and share. */
public class MainActivity extends Activity {
    private static final int HOME = 0;
    private static final int FOLDER = 1;
    private static final int CATEGORY = 2;
    private static final int SEARCH = 3;
    private static final int GOLD = 0xFFFFC21A;

    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final ExecutorService thumbs = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> thumbCache = new LruCache<String, Bitmap>(16 << 20) {
        @Override
        protected int sizeOf(String k, Bitmap v) {
            return v.getByteCount();
        }
    };
    private int generation;

    private int mode = HOME;
    private File dir;
    private int category;
    private String query = "";
    private int sort = Fs.SORT_NAME;
    private boolean hidden;
    private boolean grid = true;
    private final Set<File> selected = new LinkedHashSet<>();
    private final List<File> clip = new ArrayList<>();
    private boolean clipMove;
    private List<File> shownFiles = new ArrayList<>();

    private Ui.Header header;
    private View searchBox;
    private EditText search;
    private ListView list;
    private final Adapter adapter = new Adapter();
    private TextView emptyView;
    private LinearLayout accessView;
    private LinearLayout pasteBar;
    private TextView pasteText;
    private LinearLayout navFiles;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.column(this);
        root.setBackground(Ui.background());

        header = Ui.header(this, R.mipmap.ic_subory);
        Ui.headerButton(header, R.drawable.ic_menu, false, getString(R.string.menu), this::showPlaces);
        Ui.headerButton(header, R.drawable.ic_search, false, getString(R.string.search), v -> toggleSearch());
        Ui.headerButton(header, R.drawable.ic_more, false, getString(R.string.more), this::showMore);
        header.title.setText(R.string.app_name);
        header.subtitle.setText(R.string.subtitle);
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
                if (query.length() >= 2) {
                    mode = SEARCH;
                    reload();
                } else if (mode == SEARCH) {
                    mode = dir == null ? HOME : FOLDER;
                    reload();
                }
            }
        });
        box.addView(search, Ui.lp(0, -2, 1));
        searchBox = box;
        box.setVisibility(View.GONE);
        root.addView(box, Ui.margins(Ui.lp(-1, -2), this, 16, 0, 16, 6));

        FrameLayout content = new FrameLayout(this);
        list = new ListView(this);
        list.setDivider(null);
        list.setSelector(new android.graphics.drawable.ColorDrawable(0));
        list.setClipToPadding(false);
        list.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 10));
        list.setFastScrollEnabled(true);
        list.setAdapter(adapter);
        content.addView(list, Ui.match());
        emptyView = Ui.text(this, "", 18, Ui.TEXT_2, false);
        emptyView.setGravity(Gravity.CENTER);
        content.addView(emptyView, Ui.match());
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
        root.addView(content, Ui.lp(-1, 0, 1));

        pasteBar = Ui.row(this);
        pasteBar.setBackground(Ui.shape(this, Ui.GLASS_STRONG, Ui.GREEN, 1.5f, 24));
        pasteBar.setPadding(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
        pasteText = Ui.text(this, "", 15, Ui.TEXT, false);
        pasteBar.addView(pasteText, Ui.lp(0, -2, 1));
        TextView cancel = Ui.text(this, getString(R.string.cancel), 15, Ui.TEXT_2, true);
        cancel.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        Ui.clickable(cancel, null);
        cancel.setOnClickListener(v -> {
            clip.clear();
            updateChrome();
        });
        pasteBar.addView(cancel);
        LinearLayout paste = Ui.pillButton(this, R.drawable.ic_paste, getString(R.string.paste_here), v -> paste());
        paste.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 18), Ui.dp(this, 8));
        pasteBar.addView(paste);
        root.addView(pasteBar, Ui.margins(Ui.lp(-1, -2), this, 14, 4, 14, 0));

        LinearLayout nav = Ui.navBar(this);
        navFiles = Ui.navItem(nav, R.drawable.ic_folder, getString(R.string.nav_files), v -> goHome());
        Ui.navItem(nav, R.drawable.ic_folder_add, getString(R.string.nav_new_folder), v -> newFolder());
        Ui.navItem(nav, R.drawable.ic_copy, getString(R.string.nav_copy), v -> toClip(false));
        Ui.navItem(nav, R.drawable.ic_move, getString(R.string.nav_move), v -> toClip(true));
        Ui.navItem(nav, R.drawable.ic_delete, getString(R.string.nav_delete), v -> deleteSelected());
        root.addView(nav, Ui.margins(Ui.lp(-1, -2), this, 14, 6, 14, 10));

        Ui.edgeToEdge(this, root);
        setContentView(root);
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::onBack);
        updateChrome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bg.shutdownNow();
        thumbs.shutdownNow();
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
        if (!selected.isEmpty()) {
            selected.clear();
            updateChrome();
            adapter.notifyDataSetChanged();
        } else if (searchBox.getVisibility() == View.VISIBLE) {
            toggleSearch();
        } else if (mode == FOLDER && dir != null) {
            File parent = dir.getParentFile();
            if (parent == null || isVolumeRoot(dir)) {
                goHome();
            } else {
                dir = parent;
                reload();
            }
        } else if (mode != HOME) {
            goHome();
        } else {
            finish();
        }
    }

    private boolean isVolumeRoot(File f) {
        for (StorageVolume v : getSystemService(StorageManager.class).getStorageVolumes()) {
            File d = v.getDirectory();
            if (d != null && d.equals(f)) return true;
        }
        return false;
    }

    // ---- Navigation ----

    private void goHome() {
        mode = HOME;
        dir = null;
        selected.clear();
        if (searchBox.getVisibility() == View.VISIBLE) toggleSearch();
        reload();
    }

    private void openDir(File d) {
        mode = FOLDER;
        dir = d;
        selected.clear();
        reload();
        list.setSelection(0);
    }

    private void openCategory(int cat) {
        mode = CATEGORY;
        category = cat;
        dir = null;
        selected.clear();
        reload();
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

    private void showPlaces(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        List<StorageVolume> vols = getSystemService(StorageManager.class).getStorageVolumes();
        int id = 0;
        for (StorageVolume v : vols) {
            if (v.getDirectory() == null) continue;
            m.getMenu().add(0, id, id, v.isPrimary() ? getString(R.string.internal) : v.getDescription(this));
            id++;
        }
        m.getMenu().add(0, 100, 100, R.string.dir_download);
        m.getMenu().add(0, 101, 101, R.string.recent);
        m.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 100) {
                openDir(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
            } else if (item.getItemId() == 101) {
                openCategory(Fs.CAT_RECENT);
            } else {
                int k = 0;
                for (StorageVolume v : vols) {
                    if (v.getDirectory() == null) continue;
                    if (k++ == item.getItemId()) openDir(v.getDirectory());
                }
            }
            return true;
        });
        m.show();
    }

    private void showMore(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        List<File> s = new ArrayList<>(selected);
        if (s.size() == 1) m.getMenu().add(0, 1, 0, R.string.rename);
        boolean onlyFiles = !s.isEmpty();
        for (File f : s) if (f.isDirectory()) onlyFiles = false;
        if (onlyFiles) m.getMenu().add(0, 2, 1, R.string.share);
        if (s.size() == 1) m.getMenu().add(0, 3, 2, R.string.details);
        if (!shownFiles.isEmpty() && mode != HOME) m.getMenu().add(0, 4, 3, R.string.select_all);
        if (!s.isEmpty()) m.getMenu().add(0, 5, 4, R.string.cancel_selection);
        m.getMenu().add(1, 10, 10, R.string.sort_name).setCheckable(true).setChecked(sort == Fs.SORT_NAME);
        m.getMenu().add(1, 11, 11, R.string.sort_date).setCheckable(true).setChecked(sort == Fs.SORT_DATE);
        m.getMenu().add(1, 12, 12, R.string.sort_size).setCheckable(true).setChecked(sort == Fs.SORT_SIZE);
        m.getMenu().add(1, 13, 13, R.string.show_hidden).setCheckable(true).setChecked(hidden);
        m.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: rename(s.get(0)); break;
                case 2: share(s); break;
                case 3: details(s.get(0)); break;
                case 4: selected.addAll(shownFiles); break;
                case 5: selected.clear(); break;
                case 10: sort = Fs.SORT_NAME; reload(); break;
                case 11: sort = Fs.SORT_DATE; reload(); break;
                case 12: sort = Fs.SORT_SIZE; reload(); break;
                case 13: hidden = !hidden; reload(); break;
                default: return false;
            }
            updateChrome();
            adapter.notifyDataSetChanged();
            return true;
        });
        m.show();
    }

    private void updateChrome() {
        Ui.setNav(navFiles, mode == HOME);
        if (!selected.isEmpty()) {
            header.title.setText(getString(R.string.selected, selected.size()));
            header.subtitle.setText(R.string.cancel_selection);
            header.subtitle.setOnClickListener(v -> onBack());
        } else {
            header.subtitle.setOnClickListener(null);
            header.title.setText(mode == FOLDER && dir != null && !isVolumeRoot(dir) ? label(dir)
                    : mode == CATEGORY ? getString(categoryLabel(category)) : getString(R.string.app_name));
            header.subtitle.setText(mode == FOLDER && dir != null ? place(dir) : getString(R.string.subtitle));
        }
        pasteBar.setVisibility(clip.isEmpty() ? View.GONE : View.VISIBLE);
        if (!clip.isEmpty()) {
            pasteText.setText((clipMove ? getString(R.string.nav_move) : getString(R.string.nav_copy)) + ": "
                    + clip.size());
        }
        header.mountains.setVisibility(mode == HOME ? View.VISIBLE : View.GONE);
    }

    private String place(File d) {
        String rootPath = Fs.root().getAbsolutePath();
        String p = d.getAbsolutePath();
        if (p.startsWith(rootPath)) return getString(R.string.internal) + p.substring(rootPath.length()).replace("/", " › ");
        return p;
    }

    private static int categoryLabel(int c) {
        switch (c) {
            case Fs.CAT_IMAGES: return R.string.cat_images;
            case Fs.CAT_AUDIO: return R.string.cat_audio;
            case Fs.CAT_VIDEOS: return R.string.cat_videos;
            case Fs.CAT_DOCUMENTS: return R.string.cat_documents;
            case Fs.CAT_ARCHIVES: return R.string.cat_archives;
            default: return R.string.recent;
        }
    }

    // ---- Standard folders ----

    private static final Map<String, Integer> NAMES = new HashMap<>();
    private static final Map<String, Integer> ICONS = new HashMap<>();
    private static final String[] ORDER = {"Documents", "Download", "DCIM", "Pictures", "Movies", "Music",
        "Recordings", "Podcasts", "Audiobooks", "Ringtones", "Alarms", "Notifications"};

    static {
        NAMES.put("Documents", R.string.dir_documents);
        NAMES.put("Download", R.string.dir_download);
        NAMES.put("DCIM", R.string.dir_dcim);
        NAMES.put("Pictures", R.string.dir_pictures);
        NAMES.put("Movies", R.string.dir_movies);
        NAMES.put("Music", R.string.dir_music);
        NAMES.put("Ringtones", R.string.dir_ringtones);
        NAMES.put("Alarms", R.string.dir_alarms);
        NAMES.put("Notifications", R.string.dir_notifications);
        NAMES.put("Recordings", R.string.dir_recordings);
        NAMES.put("Podcasts", R.string.dir_podcasts);
        NAMES.put("Audiobooks", R.string.dir_audiobooks);
        ICONS.put("Documents", R.drawable.ic_note);
        ICONS.put("Download", R.drawable.ic_download);
        ICONS.put("DCIM", R.drawable.ic_camera);
        ICONS.put("Pictures", R.drawable.ic_photo);
        ICONS.put("Movies", R.drawable.ic_play);
        ICONS.put("Music", R.drawable.ic_music);
        ICONS.put("Recordings", R.drawable.ic_mic);
        ICONS.put("Podcasts", R.drawable.ic_mic);
        ICONS.put("Audiobooks", R.drawable.ic_music);
        ICONS.put("Ringtones", R.drawable.ic_music);
        ICONS.put("Alarms", R.drawable.ic_history);
        ICONS.put("Notifications", R.drawable.ic_message);
    }

    private boolean isStandard(File f) {
        File p = f.getParentFile();
        return p != null && p.equals(Fs.root()) && NAMES.containsKey(f.getName());
    }

    private String label(File f) {
        if (isStandard(f)) return getString(NAMES.get(f.getName()));
        return f.getName();
    }

    // ---- Loading ----

    private void reload() {
        if (list == null) return;
        updateChrome();
        boolean access = hasAccess();
        accessView.setVisibility(access ? View.GONE : View.VISIBLE);
        if (!access) {
            list.setVisibility(View.GONE);
            emptyView.setVisibility(View.GONE);
            return;
        }
        final int gen = ++generation;
        final int m = mode;
        final File d = dir;
        final int cat = category;
        final String q = query;
        final int s = sort;
        final boolean h = hidden;
        final boolean g = grid;
        bg.execute(() -> {
            List<Object> rows = new ArrayList<>();
            List<File> files = new ArrayList<>();
            try {
                if (m == HOME) {
                    rows.add(new Categories());
                    for (StorageVolume v : getSystemService(StorageManager.class).getStorageVolumes()) {
                        File vd = v.getDirectory();
                        if (vd == null) continue;
                        StatFs st = new StatFs(vd.getPath());
                        Volume vol = new Volume();
                        vol.dir = vd;
                        vol.title = v.isPrimary() ? getString(R.string.internal) : v.getDescription(this);
                        vol.removable = v.isRemovable();
                        vol.total = st.getTotalBytes();
                        vol.used = vol.total - st.getAvailableBytes();
                        rows.add(vol);
                    }
                    rows.add(new Heading());
                    List<File> top = Fs.list(Fs.root(), Fs.SORT_NAME, h);
                    List<File> folders = new ArrayList<>();
                    for (String n : ORDER) {
                        File f = new File(Fs.root(), n);
                        if (f.isDirectory()) folders.add(f);
                    }
                    for (File f : top) {
                        if (f.isDirectory() && !NAMES.containsKey(f.getName()) && !f.getName().equals("Android")) {
                            folders.add(f);
                        }
                    }
                    for (File f : top) if (!f.isDirectory()) folders.add(f);
                    addEntries(rows, folders, g);
                    files = folders;
                } else if (m == FOLDER) {
                    files = Fs.list(d, s, h);
                    addEntries(rows, files, g);
                } else if (m == CATEGORY) {
                    files = Fs.category(this, cat, 500);
                    if (s != Fs.SORT_DATE && cat != Fs.CAT_RECENT) Fs.sort(files, s);
                    rows.addAll(files);
                } else {
                    files = Fs.search(d == null ? Fs.root() : d, q, h, 300);
                    rows.addAll(files);
                }
            } catch (Exception e) {
                // Unreadable folder: shown as empty.
            }
            final List<File> fFiles = files;
            main.post(() -> {
                if (gen != generation || isDestroyed()) return;
                shownFiles = fFiles;
                selected.retainAll(new java.util.HashSet<>(fFiles));
                adapter.set(rows);
                boolean none = m != HOME && rows.isEmpty();
                list.setVisibility(none ? View.GONE : View.VISIBLE);
                emptyView.setVisibility(none ? View.VISIBLE : View.GONE);
                emptyView.setText(m == SEARCH ? R.string.empty_search
                        : m == CATEGORY ? R.string.empty_category : R.string.empty_folder);
                updateChrome();
            });
        });
    }

    /** Folders as a grid of four (when grid view) and files as rows. */
    private static void addEntries(List<Object> rows, List<File> entries, boolean grid) {
        if (!grid) {
            rows.addAll(entries);
            return;
        }
        List<File> dirs = new ArrayList<>();
        List<File> files = new ArrayList<>();
        for (File f : entries) {
            if (f.isDirectory()) dirs.add(f); else files.add(f);
        }
        for (int k = 0; k < dirs.size(); k += 4) {
            File[] r = new File[4];
            for (int x = 0; x < 4 && k + x < dirs.size(); x++) r[x] = dirs.get(k + x);
            rows.add(r);
        }
        rows.addAll(files);
    }

    private static final class Categories { }

    private static final class Heading { }

    private static final class Volume {
        File dir;
        String title;
        boolean removable;
        long total;
        long used;
    }

    // ---- Actions ----

    private void open(File f) {
        if (f.isDirectory()) {
            openDir(f);
            return;
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(Fs.uri(f), Fs.mime(f));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
        }
    }

    private void share(List<File> files) {
        Intent i;
        if (files.size() == 1) {
            i = new Intent(Intent.ACTION_SEND);
            i.putExtra(Intent.EXTRA_STREAM, Fs.uri(files.get(0)));
            i.setType(Fs.mime(files.get(0)));
        } else {
            i = new Intent(Intent.ACTION_SEND_MULTIPLE);
            ArrayList<Uri> uris = new ArrayList<>();
            for (File f : files) uris.add(Fs.uri(f));
            i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            i.setType("*/*");
        }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(i, null));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
        }
    }

    private File target() {
        return mode == FOLDER && dir != null ? dir : Fs.root();
    }

    private void newFolder() {
        if (!hasAccess()) return;
        ask(R.string.nav_new_folder, R.string.folder_name, "", name -> {
            File f = new File(target(), name);
            if (f.exists()) {
                Toast.makeText(this, R.string.exists, Toast.LENGTH_SHORT).show();
            } else if (!f.mkdirs()) {
                Toast.makeText(this, R.string.failed, Toast.LENGTH_SHORT).show();
            }
            reload();
        });
    }

    private void rename(File f) {
        ask(R.string.rename, R.string.new_name, f.getName(), name -> {
            File to = new File(f.getParentFile(), name);
            if (to.exists()) {
                Toast.makeText(this, R.string.exists, Toast.LENGTH_SHORT).show();
                return;
            }
            List<String> changed = new ArrayList<>();
            if (!Fs.move(f, to, changed)) Toast.makeText(this, R.string.failed, Toast.LENGTH_SHORT).show();
            Fs.scan(this, changed);
            selected.clear();
            reload();
        });
    }

    private void details(File f) {
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.file_path)).append(": ").append(place(f.getParentFile() == null ? f
                : f.getParentFile())).append('\n');
        if (f.isFile()) sb.append(getString(R.string.file_size)).append(": ")
                .append(Formatter.formatFileSize(this, f.length())).append('\n');
        else sb.append(getString(R.string.items, Fs.count(f, hidden))).append('\n');
        sb.append(getString(R.string.file_date)).append(": ").append(DateUtils.formatDateTime(this, f.lastModified(),
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_TIME));
        new AlertDialog.Builder(this).setTitle(f.getName()).setMessage(sb.toString())
                .setPositiveButton(R.string.ok, null).show();
    }

    private void toClip(boolean move) {
        if (selected.isEmpty()) {
            Toast.makeText(this, R.string.select_first, Toast.LENGTH_SHORT).show();
            return;
        }
        clip.clear();
        clip.addAll(selected);
        clipMove = move;
        selected.clear();
        updateChrome();
        adapter.notifyDataSetChanged();
    }

    private void paste() {
        final File dest = target();
        for (File f : clip) {
            if (f.isDirectory() && Fs.inside(dest, f)) {
                Toast.makeText(this, R.string.dest_inside, Toast.LENGTH_LONG).show();
                return;
            }
        }
        final List<File> items = new ArrayList<>(clip);
        final boolean move = clipMove;
        clip.clear();
        updateChrome();
        runWithProgress(move ? R.string.moving : R.string.copying, changed -> {
            boolean ok = true;
            for (File f : items) {
                File parent = f.getParentFile();
                if (move && parent != null && parent.equals(dest)) continue;
                File to = Fs.freeName(dest, f.getName());
                ok &= move ? Fs.move(f, to, changed) : Fs.copy(f, to, changed);
            }
            return ok;
        });
    }

    private void deleteSelected() {
        if (selected.isEmpty()) {
            Toast.makeText(this, R.string.select_first, Toast.LENGTH_SHORT).show();
            return;
        }
        final List<File> items = new ArrayList<>(selected);
        new AlertDialog.Builder(this)
                .setMessage(R.string.delete_q)
                .setPositiveButton(R.string.nav_delete, (d, w) -> {
                    selected.clear();
                    runWithProgress(R.string.deleting, changed -> {
                        boolean ok = true;
                        for (File f : items) ok &= Fs.delete(f, changed);
                        return ok;
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private interface Job {
        boolean run(List<String> changed);
    }

    private void runWithProgress(int message, Job job) {
        LinearLayout v = Ui.row(this);
        v.setPadding(Ui.dp(this, 24), Ui.dp(this, 20), Ui.dp(this, 24), Ui.dp(this, 20));
        v.addView(new ProgressBar(this));
        TextView t = Ui.text(this, getString(message), 17, 0xFF202020, false);
        t.setPadding(Ui.dp(this, 16), 0, 0, 0);
        v.addView(t);
        AlertDialog dlg = new AlertDialog.Builder(this).setView(v).setCancelable(false).show();
        bg.execute(() -> {
            List<String> changed = new ArrayList<>();
            boolean ok;
            try {
                ok = job.run(changed);
            } catch (Exception e) {
                ok = false;
            }
            Fs.scan(this, changed);
            final boolean fOk = ok;
            main.post(() -> {
                dlg.dismiss();
                Toast.makeText(this, fOk ? R.string.done : R.string.failed, Toast.LENGTH_SHORT).show();
                updateChrome();
                reload();
            });
        });
    }

    private interface TextResult {
        void accept(String s);
    }

    private void ask(int title, int hint, String initial, TextResult r) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setHint(hint);
        e.setText(initial);
        int dot = initial.lastIndexOf('.');
        e.setSelection(0, dot > 0 ? dot : initial.length());
        FrameLayout f = new FrameLayout(this);
        int p = Ui.dp(this, 20);
        f.setPadding(p, Ui.dp(this, 8), p, 0);
        f.addView(e);
        new AlertDialog.Builder(this).setTitle(title).setView(f)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    String s = e.getText().toString().trim();
                    if (!s.isEmpty() && !s.contains("/") && !s.equals(".") && !s.equals("..")) r.accept(s);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void toggle(File f) {
        if (!selected.remove(f)) selected.add(f);
        updateChrome();
        adapter.notifyDataSetChanged();
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
        @Override public int getViewTypeCount() { return 5; }

        @Override
        public int getItemViewType(int p) {
            Object o = rows.get(p);
            if (o instanceof Categories) return 0;
            if (o instanceof Volume) return 1;
            if (o instanceof Heading) return 2;
            if (o instanceof File[]) return 3;
            return 4;
        }

        @Override
        public boolean isEnabled(int p) {
            return false;
        }

        @Override
        public View getView(int p, View convert, ViewGroup parent) {
            Object o = rows.get(p);
            if (o instanceof Categories) return categories();
            if (o instanceof Volume) return volume((Volume) o);
            if (o instanceof Heading) return heading();
            if (o instanceof File[]) return folderGrid((File[]) o, parent.getWidth());
            return fileRow((File) o);
        }
    }

    private View categories() {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout r = Ui.row(this);
        int[][] cats = {
            {Fs.CAT_IMAGES, R.drawable.ic_photo, 0xFF4FC3F7, R.string.cat_images},
            {Fs.CAT_AUDIO, R.drawable.ic_music, 0xFFCE93D8, R.string.cat_audio},
            {Fs.CAT_VIDEOS, R.drawable.ic_play, 0xFF9CFF3A, R.string.cat_videos},
            {Fs.CAT_DOCUMENTS, R.drawable.ic_note, 0xFF64B5F6, R.string.cat_documents},
            {Fs.CAT_ARCHIVES, R.drawable.ic_archive, GOLD, R.string.cat_archives},
        };
        int w = (getResources().getDisplayMetrics().widthPixels - list.getPaddingLeft() - list.getPaddingRight()
                - Ui.dp(this, 8) * 4) / 5;
        w = Math.max(w, Ui.dp(this, 70));
        for (int[] c : cats) {
            LinearLayout t = Ui.column(this);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 10));
            Ui.clickable(t, Ui.glass(this, 18));
            t.addView(Ui.icon(this, c[1], c[2], 34));
            TextView l = Ui.text(this, getString(c[3]), 13, Ui.TEXT, true);
            l.setSingleLine(true);
            l.setGravity(Gravity.CENTER);
            t.addView(l, Ui.margins(Ui.lp(-1, -2), this, 0, 6, 0, 0));
            t.setOnClickListener(v -> openCategory(c[0]));
            LinearLayout.LayoutParams lp = Ui.lp(w, -2);
            if (r.getChildCount() > 0) lp.leftMargin = Ui.dp(this, 8);
            r.addView(t, lp);
        }
        hs.addView(r);
        hs.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 6));
        return hs;
    }

    private View volume(Volume v) {
        LinearLayout r = Ui.row(this);
        r.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        Ui.clickable(r, Ui.glass(this, 26));
        r.addView(Ui.icon(this, v.removable ? R.drawable.ic_sd : R.drawable.ic_phone_android,
                v.removable ? 0xFF4FC3F7 : 0xFF4DD0E1, 32));
        LinearLayout t = Ui.column(this);
        t.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 10), 0);
        t.addView(Ui.text(this, v.title, 17, Ui.TEXT, true));
        t.addView(Ui.text(this, getString(R.string.of, Formatter.formatShortFileSize(this, v.used),
                Formatter.formatShortFileSize(this, v.total)), 14, Ui.TEXT_2, false));
        r.addView(t, Ui.lp(0, -2, 1));
        int pct = v.total > 0 ? (int) (v.used * 100 / v.total) : 0;
        FrameLayout bar = new FrameLayout(this);
        bar.setBackground(Ui.shape(this, 0x33FFFFFF, 0, 0, 4));
        View fill = new View(this);
        fill.setBackground(Ui.greenGlow(this, 4));
        int bw = Ui.dp(this, 90);
        bar.addView(fill, new FrameLayout.LayoutParams(bw * pct / 100, -1));
        r.addView(bar, Ui.lp(bw, Ui.dp(this, 8)));
        TextView p = Ui.text(this, String.format(Locale.getDefault(), "%d %%", pct), 15, Ui.TEXT, false);
        p.setPadding(Ui.dp(this, 10), 0, 0, 0);
        r.addView(p);
        r.addView(Ui.icon(this, R.drawable.ic_chevron_right, Ui.TEXT_2, 24));
        r.setOnClickListener(x -> openDir(v.dir));
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        wrap.addView(r);
        return wrap;
    }

    private View heading() {
        LinearLayout r = Ui.row(this);
        r.setPadding(Ui.dp(this, 6), Ui.dp(this, 14), 0, Ui.dp(this, 6));
        r.addView(Ui.text(this, getString(R.string.folders), 21, Ui.TEXT, true), Ui.lp(0, -2, 1));
        LinearLayout sw = Ui.row(this);
        sw.setBackground(Ui.glass(this, 20));
        ImageView g = Ui.icon(this, R.drawable.ic_apps, grid ? Ui.ON_GREEN : Ui.TEXT, 20);
        ImageView l = Ui.icon(this, R.drawable.ic_list, grid ? Ui.TEXT : Ui.ON_GREEN, 20);
        for (ImageView i : new ImageView[] {g, l}) {
            int p = Ui.dp(this, 9);
            i.setPadding(Ui.dp(this, 16), p, Ui.dp(this, 16), p);
            i.setLayoutParams(Ui.lp(Ui.dp(this, 54), Ui.dp(this, 38)));
            sw.addView(i);
        }
        Ui.clickable(grid ? g : l, Ui.greenGlow(this, 20));
        g.setContentDescription(getString(R.string.view_grid));
        l.setContentDescription(getString(R.string.view_list));
        g.setOnClickListener(v -> {
            grid = true;
            reload();
        });
        l.setOnClickListener(v -> {
            grid = false;
            reload();
        });
        r.addView(sw);
        return r;
    }

    private View folderGrid(File[] dirs, int width) {
        LinearLayout r = Ui.row(this);
        int gap = Ui.dp(this, 8);
        int inner = (width > 0 ? width : getResources().getDisplayMetrics().widthPixels)
                - list.getPaddingLeft() - list.getPaddingRight();
        int side = (inner - gap * 3) / 4;
        for (int k = 0; k < 4; k++) {
            File f = dirs[k];
            LinearLayout t = Ui.column(this);
            t.setGravity(Gravity.CENTER_HORIZONTAL);
            t.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 10));
            LinearLayout.LayoutParams lp = Ui.lp(side, -2);
            lp.leftMargin = k == 0 ? 0 : gap;
            lp.topMargin = gap;
            r.addView(t, lp);
            if (f == null) {
                t.setVisibility(View.INVISIBLE);
                continue;
            }
            boolean sel = selected.contains(f);
            Ui.clickable(t, sel ? Ui.shape(this, 0x552E8A0A, Ui.GREEN, 2, 18) : Ui.glass(this, 18));
            FrameLayout ic = new FrameLayout(this);
            ic.addView(Ui.icon(this, R.drawable.ic_folder, GOLD, 52), new FrameLayout.LayoutParams(
                    Ui.dp(this, 52), Ui.dp(this, 52), Gravity.CENTER));
            Integer badge = ICONS.get(f.getName());
            if (badge != null && isStandard(f)) {
                FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20),
                        Gravity.CENTER);
                bl.topMargin = Ui.dp(this, 4);
                ic.addView(Ui.icon(this, badge, 0xFF8A5A00, 20), bl);
            }
            t.addView(ic, Ui.lp(Ui.dp(this, 56), Ui.dp(this, 52)));
            TextView n = Ui.text(this, label(f), 14, Ui.TEXT, true);
            n.setSingleLine(true);
            n.setEllipsize(TextUtils.TruncateAt.END);
            n.setGravity(Gravity.CENTER);
            t.addView(n, Ui.margins(Ui.lp(-1, -2), this, 0, 6, 0, 0));
            TextView c = Ui.text(this, "", 12, Ui.TEXT_3, false);
            c.setGravity(Gravity.CENTER);
            t.addView(c, Ui.lp(-1, -2));
            countInto(c, f);
            t.setOnClickListener(v -> {
                if (!selected.isEmpty()) toggle(f); else open(f);
            });
            t.setOnLongClickListener(v -> {
                toggle(f);
                return true;
            });
        }
        return r;
    }

    private void countInto(TextView t, File f) {
        t.setTag(f);
        final boolean h = hidden;
        thumbs.execute(() -> {
            int n = Fs.count(f, h);
            main.post(() -> {
                if (f.equals(t.getTag())) t.setText(getString(R.string.items, n));
            });
        });
    }

    private View fileRow(File f) {
        LinearLayout r = Ui.row(this);
        r.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        boolean sel = selected.contains(f);
        Ui.clickable(r, sel ? Ui.shape(this, 0x552E8A0A, Ui.GREEN, 2, 18) : Ui.glass(this, 18));
        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bindIcon(icon, f);
        r.addView(icon, Ui.lp(Ui.dp(this, 44), Ui.dp(this, 44)));
        LinearLayout t = Ui.column(this);
        t.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 6), 0);
        TextView n = Ui.text(this, isStandard(f) ? label(f) : f.getName(), 16, Ui.TEXT, true);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        t.addView(n);
        TextView s = Ui.text(this, "", 13, Ui.TEXT_3, false);
        s.setSingleLine(true);
        String date = DateUtils.formatDateTime(this, f.lastModified(), DateUtils.FORMAT_SHOW_DATE
                | DateUtils.FORMAT_ABBREV_MONTH);
        if (f.isDirectory()) {
            countInto(s, f);
        } else {
            s.setText(Formatter.formatShortFileSize(this, f.length()) + " · " + date);
        }
        t.addView(s);
        r.addView(t, Ui.lp(0, -2, 1));
        if (sel) r.addView(Ui.icon(this, R.drawable.ic_check_circle, Ui.GREEN, 24));
        r.setOnClickListener(v -> {
            if (!selected.isEmpty()) toggle(f); else open(f);
        });
        r.setOnLongClickListener(v -> {
            toggle(f);
            return true;
        });
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 3));
        wrap.addView(r);
        return wrap;
    }

    private void bindIcon(ImageView v, File f) {
        int pad = Ui.dp(this, 8);
        if (f.isDirectory()) {
            v.setImageResource(R.drawable.ic_folder);
            v.setImageTintList(android.content.res.ColorStateList.valueOf(GOLD));
            v.setPadding(pad / 2, pad / 2, pad / 2, pad / 2);
            return;
        }
        String mime = Fs.mime(f);
        int icon = R.drawable.ic_file;
        int color = Ui.TEXT_2;
        if (mime.startsWith("image/")) {
            icon = R.drawable.ic_photo;
            color = 0xFF4FC3F7;
        } else if (mime.startsWith("video/")) {
            icon = R.drawable.ic_play;
            color = 0xFF9CFF3A;
        } else if (mime.startsWith("audio/")) {
            icon = R.drawable.ic_music;
            color = 0xFFCE93D8;
        } else if (mime.contains("zip") || mime.contains("rar") || mime.contains("7z") || mime.contains("tar")
                || mime.contains("gzip")) {
            icon = R.drawable.ic_archive;
            color = GOLD;
        } else if (mime.startsWith("text/") || mime.contains("pdf") || mime.contains("document")
                || mime.contains("sheet") || mime.contains("presentation") || mime.contains("msword")) {
            icon = R.drawable.ic_note;
            color = 0xFF64B5F6;
        }
        v.setImageResource(icon);
        v.setImageTintList(android.content.res.ColorStateList.valueOf(color));
        v.setBackground(Ui.shape(this, 0x22FFFFFF, 0, 0, 12));
        v.setPadding(pad, pad, pad, pad);
        if (mime.startsWith("image/") || mime.startsWith("video/")) {
            final String key = f.getAbsolutePath();
            v.setTag(key);
            Bitmap b = thumbCache.get(key);
            if (b != null) {
                showThumb(v, b);
                return;
            }
            final boolean video = mime.startsWith("video/");
            final int px = Ui.dp(this, 44);
            thumbs.execute(() -> {
                Bitmap bm = null;
                try {
                    bm = video ? android.media.ThumbnailUtils.createVideoThumbnail(f, new Size(px, px), null)
                            : android.media.ThumbnailUtils.createImageThumbnail(f, new Size(px, px), null);
                } catch (Exception | OutOfMemoryError ignored) {
                    // Keep the type icon.
                }
                final Bitmap res = bm;
                if (res != null) thumbCache.put(key, res);
                main.post(() -> {
                    if (res != null && key.equals(v.getTag())) showThumb(v, res);
                });
            });
        }
    }

    private void showThumb(ImageView v, Bitmap b) {
        v.setImageTintList(null);
        v.setPadding(0, 0, 0, 0);
        v.setImageBitmap(Ui.roundCorners(b, Ui.dp(this, 10)));
    }
}
