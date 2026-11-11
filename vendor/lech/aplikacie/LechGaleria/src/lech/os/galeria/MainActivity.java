/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.galeria;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
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
import android.widget.Toast;

import lech.os.ui.Ui;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The gallery: photos by day, albums, videos, favourites and the trash, with selection for share / edit / trash. */
public class MainActivity extends Activity {
    private static final int CHIP_PHOTOS = 0;
    private static final int CHIP_ALBUMS = 1;
    private static final int CHIP_VIDEOS = 2;
    private static final int CHIP_FAVORITES = 3;
    private static final int COLUMNS = 4;
    private static final int REQ_PERMISSIONS = 1;
    private static final int REQ_CHANGE = 2;
    private static final String[] PERMISSIONS = {
        Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO,
    };

    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private int generation;

    private int chip = CHIP_PHOTOS;
    private boolean trash;
    private boolean oldestFirst;
    private Media.Album openAlbum;
    private String openAlbumId;
    private String query = "";
    private final Set<Long> selected = new LinkedHashSet<>();
    private List<Media> shown = new ArrayList<>();

    private Ui.Header header;
    private EditText search;
    private View searchBox;
    private View chipScroll;
    private final List<LinearLayout> chips = new ArrayList<>();
    private LinearLayout navGallery;
    private LinearLayout navTrash;
    private ListView list;
    private final Adapter adapter = new Adapter();
    private LinearLayout empty;
    private TextView emptyTitle;
    private TextView emptyText;
    private LinearLayout permissionView;
    private TextView banner;

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

        header = Ui.header(this, R.mipmap.ic_galeria);
        Ui.headerButton(header, R.drawable.ic_search, false, getString(R.string.search), v -> toggleSearch());
        Ui.headerButton(header, R.drawable.ic_camera, true, getString(R.string.camera), v -> openCamera());
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
        root.addView(box, Ui.margins(Ui.lp(-1, -2), this, 16, 4, 16, 6));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = Ui.row(this);
        chipRow.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 8), 0);
        addChip(chipRow, CHIP_PHOTOS, R.drawable.ic_photo, R.string.chip_photos);
        addChip(chipRow, CHIP_ALBUMS, R.drawable.ic_folder, R.string.chip_albums);
        addChip(chipRow, CHIP_VIDEOS, R.drawable.ic_play, R.string.chip_videos);
        addChip(chipRow, CHIP_FAVORITES, R.drawable.ic_star, R.string.chip_favorites);
        hs.addView(chipRow);
        chipScroll = hs;
        root.addView(hs, Ui.margins(Ui.lp(-1, -2), this, 0, 2, 0, 4));

        banner = Ui.text(this, "", 14, Ui.TEXT_2, false);
        banner.setPadding(Ui.dp(this, 20), Ui.dp(this, 4), Ui.dp(this, 20), Ui.dp(this, 4));
        banner.setVisibility(View.GONE);
        root.addView(banner, Ui.lp(-1, -2));

        FrameLayout content = new FrameLayout(this);
        list = new ListView(this);
        list.setDivider(null);
        list.setSelector(new android.graphics.drawable.ColorDrawable(0));
        list.setClipToPadding(false);
        list.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), Ui.dp(this, 10));
        list.setFastScrollEnabled(true);
        list.setAdapter(adapter);
        content.addView(list, Ui.match());
        empty = Ui.column(this);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(Ui.dp(this, 32), 0, Ui.dp(this, 32), Ui.dp(this, 40));
        FrameLayout glow = new FrameLayout(this);
        glow.setBackground(Ui.glow(this, 100));
        ImageView big = new ImageView(this);
        big.setImageResource(R.mipmap.ic_galeria);
        glow.addView(big, new FrameLayout.LayoutParams(Ui.dp(this, 130), Ui.dp(this, 130), Gravity.CENTER));
        empty.addView(glow, Ui.lp(Ui.dp(this, 210), Ui.dp(this, 190)));
        emptyTitle = Ui.text(this, "", 22, Ui.TEXT, true);
        emptyTitle.setGravity(Gravity.CENTER);
        empty.addView(emptyTitle);
        emptyText = Ui.text(this, "", 16, Ui.TEXT_2, false);
        emptyText.setGravity(Gravity.CENTER);
        empty.addView(emptyText, Ui.margins(Ui.lp(-1, -2), this, 0, 8, 0, 0));
        content.addView(empty, Ui.match());
        permissionView = Ui.column(this);
        permissionView.setGravity(Gravity.CENTER);
        permissionView.setPadding(Ui.dp(this, 32), 0, Ui.dp(this, 32), 0);
        TextView pt = Ui.text(this, getString(R.string.permission_needed), 18, Ui.TEXT, false);
        pt.setGravity(Gravity.CENTER);
        permissionView.addView(pt);
        permissionView.addView(Ui.pillButton(this, 0, getString(R.string.permission_grant),
                v -> requestPermissions(PERMISSIONS, REQ_PERMISSIONS)), Ui.margins(Ui.lp(-2, -2), this, 0, 24, 0, 0));
        content.addView(permissionView, Ui.match());
        root.addView(content, Ui.lp(-1, 0, 1));

        LinearLayout nav = Ui.navBar(this);
        navGallery = Ui.navItem(nav, R.drawable.ic_photo, getString(R.string.nav_gallery), v -> home());
        Ui.navItem(nav, R.drawable.ic_share, getString(R.string.nav_share), v -> shareSelected());
        Ui.navItem(nav, R.drawable.ic_edit, getString(R.string.nav_edit), v -> editSelected());
        navTrash = Ui.navItem(nav, R.drawable.ic_delete, getString(R.string.nav_trash), v -> trashAction());
        Ui.navItem(nav, R.drawable.ic_apps, getString(R.string.nav_more), this::showMore);
        root.addView(nav, Ui.margins(Ui.lp(-1, -2), this, 14, 4, 14, 10));

        Ui.edgeToEdge(this, root);
        setContentView(root);
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::onBack);
        if (!canRead()) requestPermissions(PERMISSIONS, REQ_PERMISSIONS);
        updateChrome();
    }

    private boolean canRead() {
        return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onStart() {
        super.onStart();
        getContentResolver().registerContentObserver(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                true, observer);
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
        if (requestCode == REQ_CHANGE) {
            selected.clear();
            Thumbs.clear();
            updateChrome();
            reload();
        }
    }

    private void onBack() {
        if (!selected.isEmpty()) {
            selected.clear();
            updateChrome();
            adapter.notifyDataSetChanged();
        } else if (searchBox.getVisibility() == View.VISIBLE) {
            toggleSearch();
        } else if (openAlbumId != null) {
            openAlbumId = null;
            openAlbum = null;
            updateChrome();
            reload();
        } else if (trash || chip != CHIP_PHOTOS) {
            home();
        } else {
            finish();
        }
    }

    // ---- Chrome ----

    private void addChip(LinearLayout row, int which, int icon, int label) {
        LinearLayout c = Ui.chip(this, icon, getString(label));
        c.setOnClickListener(v -> {
            chip = which;
            trash = false;
            openAlbumId = null;
            openAlbum = null;
            selected.clear();
            updateChrome();
            reload();
        });
        row.addView(c, Ui.margins(Ui.lp(-2, -2), this, 0, 0, 10, 0));
        chips.add(c);
    }

    private void updateChrome() {
        for (int k = 0; k < chips.size(); k++) Ui.setChip(chips.get(k), !trash && k == chip);
        Ui.setNav(navGallery, !trash);
        Ui.setNav(navTrash, trash);
        if (!selected.isEmpty()) {
            header.title.setText(getString(R.string.selected, selected.size()));
            header.subtitle.setText(R.string.cancel_selection);
            header.subtitle.setOnClickListener(v -> onBack());
        } else {
            header.subtitle.setOnClickListener(null);
            if (trash) {
                header.title.setText(R.string.trash_title);
                header.subtitle.setText(R.string.trash_info);
            } else if (openAlbum != null) {
                header.title.setText(openAlbum.title);
                header.subtitle.setText(getString(R.string.items, openAlbum.items.size()));
            } else {
                header.title.setText(R.string.app_name);
                header.subtitle.setText(R.string.subtitle);
            }
        }
        chipScroll.setVisibility(trash ? View.GONE : View.VISIBLE);
    }

    private void home() {
        trash = false;
        chip = CHIP_PHOTOS;
        openAlbum = null;
        openAlbumId = null;
        selected.clear();
        updateChrome();
        reload();
        list.setSelection(0);
    }

    private void toggleSearch() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (searchBox.getVisibility() == View.VISIBLE) {
            search.setText("");
            searchBox.setVisibility(View.GONE);
            imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        } else {
            searchBox.setVisibility(View.VISIBLE);
            search.requestFocus();
            imm.showSoftInput(search, 0);
        }
    }

    private void openCamera() {
        try {
            startActivity(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_app, Toast.LENGTH_SHORT).show();
        }
    }

    private void showMenu(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 1, 0, R.string.sort_newest).setCheckable(true).setChecked(!oldestFirst);
        m.getMenu().add(0, 2, 1, R.string.sort_oldest).setCheckable(true).setChecked(oldestFirst);
        m.getMenu().add(0, 3, 2, R.string.trash_title);
        m.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 3) {
                openTrash();
            } else {
                oldestFirst = item.getItemId() == 2;
                reload();
            }
            return true;
        });
        m.show();
    }

    // ---- Selection actions ----

    private List<Media> selection() {
        List<Media> out = new ArrayList<>();
        for (Media m : shown) if (selected.contains(m.id)) out.add(m);
        return out;
    }

    private void shareSelected() {
        List<Media> s = selection();
        if (s.isEmpty()) {
            Toast.makeText(this, R.string.select_first, Toast.LENGTH_SHORT).show();
            return;
        }
        share(this, s);
    }

    static void share(Activity a, List<Media> s) {
        Intent i;
        if (s.size() == 1) {
            i = new Intent(Intent.ACTION_SEND);
            i.putExtra(Intent.EXTRA_STREAM, s.get(0).uri);
            i.setType(s.get(0).mime == null ? (s.get(0).video ? "video/*" : "image/*") : s.get(0).mime);
        } else {
            i = new Intent(Intent.ACTION_SEND_MULTIPLE);
            i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(Media.uris(s)));
            boolean video = false;
            boolean image = false;
            for (Media m : s) {
                if (m.video) video = true; else image = true;
            }
            i.setType(video && image ? "*/*" : video ? "video/*" : "image/*");
        }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            a.startActivity(Intent.createChooser(i, null));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(a, R.string.no_app, Toast.LENGTH_SHORT).show();
        }
    }

    private void editSelected() {
        List<Media> s = selection();
        if (s.size() != 1) {
            Toast.makeText(this, s.isEmpty() ? R.string.select_first : R.string.select_one, Toast.LENGTH_SHORT).show();
            return;
        }
        if (s.get(0).video) {
            Toast.makeText(this, R.string.edit_video, Toast.LENGTH_SHORT).show();
            return;
        }
        openViewer(s, 0, true);
    }

    private void trashAction() {
        List<Media> s = selection();
        if (trash) {
            showMore(navTrash);
            return;
        }
        if (s.isEmpty()) {
            openTrash();
            return;
        }
        request(MediaStore.createTrashRequest(getContentResolver(), Media.uris(s), true));
    }

    private void openTrash() {
        trash = true;
        openAlbum = null;
        openAlbumId = null;
        selected.clear();
        updateChrome();
        reload();
    }

    private void showMore(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        List<Media> s = selection();
        if (trash) {
            if (!s.isEmpty()) {
                m.getMenu().add(0, 10, 0, R.string.restore);
                m.getMenu().add(0, 11, 1, R.string.delete_forever);
            }
            if (!shown.isEmpty()) m.getMenu().add(0, 12, 2, R.string.empty_trash_action);
        } else if (!s.isEmpty()) {
            m.getMenu().add(0, 20, 0, R.string.add_favorite);
            m.getMenu().add(0, 21, 1, R.string.remove_favorite);
            m.getMenu().add(0, 22, 2, R.string.move_to_trash);
        }
        if (!shown.isEmpty()) m.getMenu().add(0, 1, 5, R.string.select_all);
        if (!s.isEmpty()) m.getMenu().add(0, 2, 6, R.string.cancel_selection);
        m.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    for (Media x : shown) selected.add(x.id);
                    break;
                case 2:
                    selected.clear();
                    break;
                case 10:
                    request(MediaStore.createTrashRequest(getContentResolver(), Media.uris(s), false));
                    break;
                case 11:
                    request(MediaStore.createDeleteRequest(getContentResolver(), Media.uris(s)));
                    break;
                case 12:
                    request(MediaStore.createDeleteRequest(getContentResolver(), Media.uris(shown)));
                    break;
                case 20:
                    request(MediaStore.createFavoriteRequest(getContentResolver(), Media.uris(s), true));
                    break;
                case 21:
                    request(MediaStore.createFavoriteRequest(getContentResolver(), Media.uris(s), false));
                    break;
                case 22:
                    request(MediaStore.createTrashRequest(getContentResolver(), Media.uris(s), true));
                    break;
                default:
                    return false;
            }
            updateChrome();
            adapter.notifyDataSetChanged();
            return true;
        });
        m.show();
    }

    /** Asks the user to confirm a change of the media store (trash, delete, favourite). */
    private void request(PendingIntent pi) {
        try {
            startIntentSenderForResult(pi.getIntentSender(), REQ_CHANGE, null, 0, 0, 0);
        } catch (IntentSender.SendIntentException e) {
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void openViewer(List<Media> items, int index, boolean edit) {
        ViewerActivity.items = new ArrayList<>(items);
        Intent i = new Intent(this, ViewerActivity.class);
        i.putExtra(ViewerActivity.EXTRA_INDEX, index);
        i.putExtra(ViewerActivity.EXTRA_EDIT, edit);
        i.putExtra(ViewerActivity.EXTRA_TRASH, trash);
        startActivityForResult(i, REQ_CHANGE);
    }

    // ---- Loading ----

    private void reload() {
        if (list == null) return;
        boolean allowed = canRead();
        permissionView.setVisibility(allowed ? View.GONE : View.VISIBLE);
        if (!allowed) {
            empty.setVisibility(View.GONE);
            list.setVisibility(View.GONE);
            return;
        }
        final int gen = ++generation;
        final int c = chip;
        final boolean t = trash;
        final boolean old = oldestFirst;
        final String q = query;
        final String albumId = openAlbumId;
        bg.execute(() -> {
            List<Media> items;
            List<Object> rows = new ArrayList<>();
            Media.Album album = null;
            try {
                int kind = t ? Media.TRASH : c == CHIP_VIDEOS ? Media.VIDEOS : c == CHIP_FAVORITES ? Media.FAVORITES
                        : Media.ALL;
                items = Media.query(this, kind, old);
                if (!q.isEmpty()) items = Media.filter(items, q);
                if (!t && c == CHIP_ALBUMS && albumId == null && q.isEmpty()) {
                    List<Media.Album> albums = Media.albums(items);
                    for (int k = 0; k < albums.size(); k += 2) {
                        rows.add(new Media.Album[] {albums.get(k), k + 1 < albums.size() ? albums.get(k + 1) : null});
                    }
                    items = new ArrayList<>();
                } else if (albumId != null) {
                    for (Media.Album a : Media.albums(items)) {
                        if (a.id.equals(albumId)) album = a;
                    }
                    items = album == null ? new ArrayList<>() : album.items;
                    addByDay(rows, items);
                } else {
                    addByDay(rows, items);
                }
            } catch (Exception e) {
                items = new ArrayList<>();
            }
            final List<Media> fItems = items;
            final Media.Album fAlbum = album;
            main.post(() -> {
                if (gen != generation || isDestroyed()) return;
                shown = fItems;
                if (albumId != null) openAlbum = fAlbum;
                Set<Long> still = new HashSet<>();
                for (Media m : fItems) still.add(m.id);
                selected.retainAll(still);
                adapter.set(rows);
                boolean none = rows.isEmpty();
                list.setVisibility(none ? View.GONE : View.VISIBLE);
                empty.setVisibility(none ? View.VISIBLE : View.GONE);
                emptyText.setVisibility(View.GONE);
                if (!q.isEmpty()) {
                    emptyTitle.setText(R.string.empty_search);
                } else if (t) {
                    emptyTitle.setText(R.string.empty_trash);
                } else if (c == CHIP_ALBUMS && albumId == null) {
                    emptyTitle.setText(R.string.empty_albums);
                } else if (c == CHIP_VIDEOS) {
                    emptyTitle.setText(R.string.empty_videos);
                } else if (c == CHIP_FAVORITES) {
                    emptyTitle.setText(R.string.empty_favorites);
                } else {
                    emptyTitle.setText(R.string.empty_photos);
                    emptyText.setText(R.string.empty_photos_text);
                    emptyText.setVisibility(View.VISIBLE);
                }
                updateChrome();
            });
        });
    }

    /** A day heading (with the number of items) and rows of four items. */
    private void addByDay(List<Object> rows, List<Media> items) {
        int k = 0;
        while (k < items.size()) {
            long day = dayStart(items.get(k).date);
            int end = k;
            while (end < items.size() && dayStart(items.get(end).date) == day) end++;
            rows.add(new Day(day, end - k));
            for (int r = k; r < end; r += COLUMNS) {
                Media[] row = new Media[COLUMNS];
                for (int x = 0; x < COLUMNS && r + x < end; x++) row[x] = items.get(r + x);
                rows.add(row);
            }
            k = end;
        }
    }

    private static long dayStart(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private String dayLabel(long day) {
        long today = dayStart(System.currentTimeMillis());
        if (day == today) return getString(R.string.today);
        if (day == today - DateUtils.DAY_IN_MILLIS || dayStart(today - 1) == day) return getString(R.string.yesterday);
        int flags = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_WEEKDAY;
        Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        c.setTimeInMillis(day);
        if (c.get(Calendar.YEAR) != year) flags |= DateUtils.FORMAT_SHOW_YEAR;
        return DateUtils.formatDateTime(this, day, flags);
    }

    private static final class Day {
        final long day;
        final int count;

        Day(long day, int count) {
            this.day = day;
            this.count = count;
        }
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
            return o instanceof Day ? 0 : o instanceof Media[] ? 1 : 2;
        }

        @Override
        public boolean isEnabled(int p) {
            return false;
        }

        @Override
        public View getView(int p, View convert, ViewGroup parent) {
            Object o = rows.get(p);
            if (o instanceof Day) return dayRow((Day) o, convert);
            if (o instanceof Media[]) return mediaRow((Media[]) o, convert, parent.getWidth());
            return albumRow((Media.Album[]) o, parent.getWidth());
        }
    }

    private View dayRow(Day d, View convert) {
        LinearLayout r = convert instanceof LinearLayout ? (LinearLayout) convert : null;
        if (r == null) {
            r = Ui.row(this);
            r.setPadding(Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 8));
            TextView a = Ui.text(this, "", 21, Ui.TEXT, true);
            r.addView(a, Ui.lp(0, -2, 1));
            TextView b = Ui.text(this, "", 15, Ui.TEXT_2, false);
            r.addView(b);
        }
        ((TextView) r.getChildAt(0)).setText(dayLabel(d.day));
        ((TextView) r.getChildAt(1)).setText(getString(R.string.items, d.count));
        return r;
    }

    private View mediaRow(Media[] items, View convert, int width) {
        LinearLayout r;
        int gap = Ui.dp(this, 6);
        if (convert instanceof LinearLayout) {
            r = (LinearLayout) convert;
        } else {
            r = Ui.row(this);
            for (int k = 0; k < COLUMNS; k++) r.addView(cell(), Ui.lp(0, 0, 1));
        }
        int inner = (width > 0 ? width : getResources().getDisplayMetrics().widthPixels) - list.getPaddingLeft()
                - list.getPaddingRight();
        int side = (inner - gap * (COLUMNS - 1)) / COLUMNS;
        for (int k = 0; k < COLUMNS; k++) {
            FrameLayout c = (FrameLayout) r.getChildAt(k);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) c.getLayoutParams();
            lp.width = side;
            lp.height = side;
            lp.weight = 0;
            lp.leftMargin = k == 0 ? 0 : gap;
            lp.bottomMargin = gap;
            c.setLayoutParams(lp);
            bindCell(c, items[k], side);
        }
        return r;
    }

    private FrameLayout cell() {
        FrameLayout c = new FrameLayout(this);
        c.setBackground(Ui.shape(this, 0xFF0D2230, Ui.GLASS_STROKE, 1, 16));
        c.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Ui.dp(v.getContext(), 16));
            }
        });
        c.setClipToOutline(true);
        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        c.addView(img, Ui.match());
        LinearLayout badge = Ui.row(this);
        badge.setBackground(Ui.shape(this, 0x99000000, 0, 0, 10));
        badge.setPadding(Ui.dp(this, 5), Ui.dp(this, 2), Ui.dp(this, 7), Ui.dp(this, 2));
        badge.addView(Ui.icon(this, R.drawable.ic_play, Ui.TEXT, 14));
        TextView dur = Ui.text(this, "", 11, Ui.TEXT, true);
        dur.setPadding(Ui.dp(this, 3), 0, 0, 0);
        badge.addView(dur);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        bl.setMargins(0, 0, Ui.dp(this, 5), Ui.dp(this, 5));
        c.addView(badge, bl);
        ImageView fav = Ui.icon(this, R.drawable.ic_star, 0xFFFFD54F, 16);
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(Ui.dp(this, 16), Ui.dp(this, 16),
                Gravity.BOTTOM | Gravity.START);
        fl.setMargins(Ui.dp(this, 6), 0, 0, Ui.dp(this, 6));
        c.addView(fav, fl);
        View shade = new View(this);
        shade.setBackgroundColor(0x5507192A);
        c.addView(shade, Ui.match());
        ImageView check = Ui.icon(this, R.drawable.ic_check_circle, Ui.GREEN, 24);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24),
                Gravity.TOP | Gravity.END);
        cl.setMargins(0, Ui.dp(this, 6), Ui.dp(this, 6), 0);
        check.setBackground(Ui.circle(0xFF06210A, 0, 0));
        c.addView(check, cl);
        View fg = new View(this);
        Ui.clickable(fg, null);
        c.addView(fg, Ui.match());
        return c;
    }

    private void bindCell(FrameLayout c, Media m, int side) {
        c.setVisibility(m == null ? View.INVISIBLE : View.VISIBLE);
        if (m == null) return;
        ImageView img = (ImageView) c.getChildAt(0);
        Thumbs.load(this, img, m.uri, Math.min(side, Ui.dp(this, 128)));
        View badge = c.getChildAt(1);
        badge.setVisibility(m.video ? View.VISIBLE : View.GONE);
        if (m.video) ((TextView) ((LinearLayout) badge).getChildAt(1)).setText(Media.duration(m.duration));
        c.getChildAt(2).setVisibility(m.favorite ? View.VISIBLE : View.GONE);
        boolean sel = selected.contains(m.id);
        c.getChildAt(3).setVisibility(sel ? View.VISIBLE : View.GONE);
        c.getChildAt(4).setVisibility(sel ? View.VISIBLE : View.GONE);
        c.setContentDescription(m.name);
        View fg = c.getChildAt(5);
        fg.setOnClickListener(v -> {
            if (!selected.isEmpty()) {
                toggle(m);
            } else {
                openViewer(shown, shown.indexOf(m), false);
            }
        });
        fg.setOnLongClickListener(v -> {
            toggle(m);
            return true;
        });
    }

    private void toggle(Media m) {
        if (!selected.remove(m.id)) selected.add(m.id);
        updateChrome();
        adapter.notifyDataSetChanged();
    }

    private View albumRow(Media.Album[] albums, int width) {
        LinearLayout r = Ui.row(this);
        int gap = Ui.dp(this, 10);
        int inner = (width > 0 ? width : getResources().getDisplayMetrics().widthPixels) - list.getPaddingLeft()
                - list.getPaddingRight();
        int side = (inner - gap) / 2;
        for (int k = 0; k < 2; k++) {
            Media.Album a = albums[k];
            LinearLayout card = Ui.column(this);
            LinearLayout.LayoutParams lp = Ui.lp(side, -2);
            lp.leftMargin = k == 0 ? 0 : gap;
            lp.topMargin = Ui.dp(this, 10);
            r.addView(card, lp);
            if (a == null) {
                card.setVisibility(View.INVISIBLE);
                continue;
            }
            FrameLayout cover = cell();
            cover.setLayoutParams(Ui.lp(side, side));
            card.addView(cover);
            bindCell(cover, a.items.get(0), side);
            cover.getChildAt(1).setVisibility(View.GONE);
            cover.getChildAt(2).setVisibility(View.GONE);
            cover.getChildAt(5).setOnLongClickListener(null);
            cover.getChildAt(5).setOnClickListener(v -> {
                openAlbumId = a.id;
                openAlbum = a;
                selected.clear();
                updateChrome();
                reload();
            });
            TextView t = Ui.text(this, a.title, 17, Ui.TEXT, true);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(t, Ui.margins(Ui.lp(-1, -2), this, 4, 8, 4, 0));
            card.addView(Ui.text(this, getString(R.string.items, a.items.size()), 14, Ui.TEXT_3, false),
                    Ui.margins(Ui.lp(-1, -2), this, 4, 0, 4, 0));
        }
        return r;
    }

    static Uri contentUri(Media m) {
        return m.uri;
    }
}
