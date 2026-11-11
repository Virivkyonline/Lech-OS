/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.galeria;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.graphics.Insets;
import android.graphics.Matrix;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import lech.os.ui.Ui;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A photo or a video on the whole screen: zoom, swipe, favourite, share, a simple edit (rotate) and the trash. */
public class ViewerActivity extends Activity {
    static final String EXTRA_INDEX = "index";
    static final String EXTRA_EDIT = "edit";
    static final String EXTRA_TRASH = "trash";
    private static final int REQ_TRASH = 1;
    private static final int REQ_FAVORITE = 2;
    private static final int REQ_RESTORE = 3;
    private static final int REQ_DELETE = 4;

    /** The items of the gallery screen that opened the viewer (the same process). */
    static List<Media> items;

    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private List<Media> list = new ArrayList<>();
    private int index;
    private boolean external;
    private boolean inTrash;
    private boolean editing;
    private int rotation;
    private Bitmap shownBitmap;
    private boolean barsVisible = true;
    private int generation;

    private FrameLayout stage;
    private ZoomImageView image;
    private VideoView video;
    private LinearLayout topBar;
    private LinearLayout bottomBar;
    private TextView title;
    private TextView subtitle;
    private ImageView favButton;
    private View favBox;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent in = getIntent();
        inTrash = in.getBooleanExtra(EXTRA_TRASH, false);
        if (items != null && in.getData() == null) {
            list = items;
            index = Math.max(0, Math.min(in.getIntExtra(EXTRA_INDEX, 0), list.size() - 1));
        } else if (in.getData() != null) {
            external = true;
            Media m = new Media();
            m.uri = in.getData();
            String type = in.resolveType(getContentResolver());
            m.mime = type;
            m.video = type != null && type.startsWith("video/");
            m.name = m.uri.getLastPathSegment();
            list = new ArrayList<>(Collections.singletonList(m));
        }
        if (list.isEmpty()) {
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        stage = new FrameLayout(this);
        root.addView(stage, Ui.match());

        topBar = Ui.row(this);
        topBar.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xCC000000, 0x00000000}));
        topBar.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 20));
        topBar.addView(Ui.roundButton(this, R.drawable.ic_back, false, 44, getString(R.string.back), v -> finish()));
        LinearLayout titles = Ui.column(this);
        titles.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 8), 0);
        title = Ui.text(this, "", 17, Ui.TEXT, true);
        title.setSingleLine(true);
        subtitle = Ui.text(this, "", 13, Ui.TEXT_2, false);
        subtitle.setSingleLine(true);
        titles.addView(title);
        titles.addView(subtitle);
        topBar.addView(titles, Ui.lp(0, -2, 1));
        FrameLayout fav = Ui.roundButton(this, R.drawable.ic_star_border, false, 44, getString(R.string.add_favorite),
                v -> favorite());
        favButton = (ImageView) fav.getChildAt(0);
        favBox = fav;
        topBar.addView(fav);
        topBar.addView(Ui.roundButton(this, R.drawable.ic_info, false, 44, getString(R.string.details), v -> info()),
                Ui.margins(Ui.lp(Ui.dp(this, 44), Ui.dp(this, 44)), this, 10, 0, 0, 0));
        root.addView(topBar, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        bottomBar = Ui.navBar(this);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        bl.setMargins(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 10));
        root.addView(bottomBar, bl);

        root.setOnApplyWindowInsetsListener((v, ins) -> {
            Insets i = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            topBar.setPadding(Ui.dp(this, 12) + i.left, Ui.dp(this, 8) + i.top, Ui.dp(this, 12) + i.right,
                    Ui.dp(this, 20));
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) bottomBar.getLayoutParams();
            p.bottomMargin = Ui.dp(this, 10) + i.bottom;
            p.leftMargin = Ui.dp(this, 14) + i.left;
            p.rightMargin = Ui.dp(this, 14) + i.right;
            bottomBar.setLayoutParams(p);
            return WindowInsets.CONSUMED;
        });
        getWindow().setDecorFitsSystemWindows(false);
        setContentView(root);
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> {
                    if (editing) {
                        setEditing(false);
                    } else {
                        finish();
                    }
                });
        editing = in.getBooleanExtra(EXTRA_EDIT, false) && !list.get(index).video;
        show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bg.shutdownNow();
        if (!external) items = null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (video != null) video.pause();
    }

    private Media current() {
        return list.get(index);
    }

    private void show() {
        Media m = current();
        rotation = 0;
        shownBitmap = null;
        stage.removeAllViews();
        image = null;
        if (video != null) video.stopPlayback();
        video = null;
        if (m.video) {
            FrameLayout holder = new FrameLayout(this);
            video = new VideoView(this);
            holder.addView(video, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
            MediaController mc = new MediaController(this);
            mc.setAnchorView(video);
            video.setMediaController(mc);
            video.setVideoURI(m.uri);
            video.setOnErrorListener((mp, what, extra) -> {
                Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
                return true;
            });
            video.setOnPreparedListener(mp -> video.start());
            holder.setOnTouchListener(new SwipeTouch());
            stage.addView(holder, Ui.match());
        } else {
            image = new ZoomImageView(this);
            image.setListener(new ZoomImageView.Listener() {
                @Override
                public void onSwipe(int direction) {
                    move(direction);
                }

                @Override
                public void onTap() {
                    toggleBars();
                }
            });
            stage.addView(image, Ui.match());
            loadImage(m);
        }
        title.setText(m.date > 0 ? DateUtils.formatDateTime(this, m.date, DateUtils.FORMAT_SHOW_DATE
                | DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_TIME) : (m.name == null ? "" : m.name));
        subtitle.setText(list.size() > 1 ? (index + 1) + " / " + list.size() : (m.bucket == null ? "" : m.bucket));
        updateFavorite();
        favBox.setVisibility(external || inTrash ? View.GONE : View.VISIBLE);
        buildBottom();
    }

    private void loadImage(Media m) {
        final int gen = ++generation;
        final int max = Math.max(getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels) * 2;
        bg.execute(() -> {
            Bitmap b = decode(m.uri, max);
            main.post(() -> {
                if (gen != generation || isDestroyed() || image == null) return;
                if (b == null) {
                    Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
                    return;
                }
                shownBitmap = b;
                image.setImageBitmap(b);
            });
        });
    }

    private Bitmap decode(Uri uri, int max) {
        try {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), uri), (d, info, s) -> {
                int w = info.getSize().getWidth();
                int h = info.getSize().getHeight();
                int big = Math.max(w, h);
                if (big > max) d.setTargetSize(Math.max(1, w * max / big), Math.max(1, h * max / big));
                d.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private void move(int direction) {
        if (editing) return;
        int next = index + direction;
        if (next < 0 || next >= list.size()) return;
        index = next;
        show();
    }

    private void toggleBars() {
        barsVisible = !barsVisible;
        topBar.setVisibility(barsVisible ? View.VISIBLE : View.GONE);
        bottomBar.setVisibility(barsVisible ? View.VISIBLE : View.GONE);
        WindowInsetsController c = getWindow().getInsetsController();
        if (c != null) {
            if (barsVisible) {
                c.show(WindowInsets.Type.systemBars());
            } else {
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                c.hide(WindowInsets.Type.systemBars());
            }
        }
    }

    private void buildBottom() {
        bottomBar.removeAllViews();
        if (editing) {
            Ui.navItem(bottomBar, R.drawable.ic_rotate, getString(R.string.rotate), v -> rotate());
            Ui.navItem(bottomBar, R.drawable.ic_close, getString(R.string.cancel), v -> setEditing(false));
            LinearLayout save = Ui.navItem(bottomBar, R.drawable.ic_check, getString(R.string.save_copy),
                    v -> saveCopy());
            Ui.setNav(save, true);
        } else if (inTrash) {
            Ui.navItem(bottomBar, R.drawable.ic_restore, getString(R.string.restore), v -> request(
                    MediaStore.createTrashRequest(getContentResolver(), single(), false).getIntentSender(),
                    REQ_RESTORE));
            Ui.navItem(bottomBar, R.drawable.ic_delete, getString(R.string.delete_forever), v -> request(
                    MediaStore.createDeleteRequest(getContentResolver(), single()).getIntentSender(), REQ_DELETE));
        } else {
            Ui.navItem(bottomBar, R.drawable.ic_share, getString(R.string.nav_share),
                    v -> MainActivity.share(this, Collections.singletonList(current())));
            if (!current().video) {
                Ui.navItem(bottomBar, R.drawable.ic_edit, getString(R.string.nav_edit), v -> setEditing(true));
            }
            if (!external) {
                Ui.navItem(bottomBar, R.drawable.ic_delete, getString(R.string.nav_trash), v -> request(
                        MediaStore.createTrashRequest(getContentResolver(), single(), true).getIntentSender(),
                        REQ_TRASH));
            }
        }
    }

    private List<Uri> single() {
        return Collections.singletonList(current().uri);
    }

    // ---- Edit (rotate, saved as a copy) ----

    private void setEditing(boolean on) {
        editing = on;
        if (!on && rotation != 0 && image != null && shownBitmap != null) {
            rotation = 0;
            image.setImageBitmap(shownBitmap);
        }
        buildBottom();
    }

    private void rotate() {
        if (shownBitmap == null || image == null) return;
        rotation = (rotation + 90) % 360;
        image.setImageBitmap(rotated(shownBitmap, rotation));
    }

    private static Bitmap rotated(Bitmap b, int degrees) {
        if (degrees == 0) return b;
        Matrix m = new Matrix();
        m.postRotate(degrees);
        return Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
    }

    private void saveCopy() {
        final Media m = current();
        final int deg = rotation;
        bg.execute(() -> {
            boolean ok = false;
            Uri out = null;
            try {
                Bitmap full = decode(m.uri, 4096);
                if (full != null) {
                    Bitmap r = rotated(full, deg);
                    String base = m.name == null ? "foto" : m.name.replaceAll("\\.[^.]*$", "");
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Images.Media.DISPLAY_NAME, base + "-upravene.jpg");
                    v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                    v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LECH");
                    v.put(MediaStore.Images.Media.IS_PENDING, 1);
                    out = getContentResolver().insert(
                            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
                    if (out != null) {
                        try (OutputStream os = getContentResolver().openOutputStream(out)) {
                            ok = r.compress(Bitmap.CompressFormat.JPEG, 95, os);
                        }
                        ContentValues done = new ContentValues();
                        done.put(MediaStore.Images.Media.IS_PENDING, 0);
                        getContentResolver().update(out, done, null, null);
                    }
                }
            } catch (Exception | OutOfMemoryError e) {
                ok = false;
            }
            final boolean saved = ok;
            final Uri fOut = out;
            main.post(() -> {
                if (!saved && fOut != null) {
                    try {
                        getContentResolver().delete(fOut, null, null);
                    } catch (Exception ignored) {
                        // Pending file is cleaned up by the media store.
                    }
                }
                Toast.makeText(this, saved ? R.string.saved : R.string.save_failed, Toast.LENGTH_SHORT).show();
                if (saved) {
                    setResult(RESULT_OK);
                    setEditing(false);
                }
            });
        });
    }

    // ---- Favourite, trash, details ----

    private void updateFavorite() {
        boolean f = current().favorite;
        favButton.setImageResource(f ? R.drawable.ic_star : R.drawable.ic_star_border);
        favButton.setImageTintList(android.content.res.ColorStateList.valueOf(f ? 0xFFFFD54F : Ui.TEXT));
        favBox.setContentDescription(getString(f ? R.string.remove_favorite : R.string.add_favorite));
    }

    private void favorite() {
        request(MediaStore.createFavoriteRequest(getContentResolver(), single(), !current().favorite)
                .getIntentSender(), REQ_FAVORITE);
    }

    private void request(IntentSender s, int code) {
        try {
            startIntentSenderForResult(s, code, null, 0, 0, 0);
        } catch (IntentSender.SendIntentException e) {
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        setResult(RESULT_OK);
        if (requestCode == REQ_FAVORITE) {
            current().favorite = !current().favorite;
            updateFavorite();
            return;
        }
        // Trashed, restored or deleted: the item leaves this list.
        list.remove(index);
        if (list.isEmpty()) {
            finish();
            return;
        }
        if (index >= list.size()) index = list.size() - 1;
        show();
    }

    private void info() {
        Media m = current();
        StringBuilder sb = new StringBuilder();
        line(sb, R.string.file_name, m.name);
        if (m.date > 0) {
            line(sb, R.string.file_date, DateUtils.formatDateTime(this, m.date, DateUtils.FORMAT_SHOW_DATE
                    | DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_WEEKDAY));
        }
        if (m.width > 0 && m.height > 0) line(sb, R.string.file_resolution, m.width + " × " + m.height);
        if (m.size > 0) line(sb, R.string.file_size, Formatter.formatFileSize(this, m.size));
        line(sb, R.string.file_album, m.bucket);
        new AlertDialog.Builder(this).setTitle(R.string.details).setMessage(sb.toString().trim())
                .setPositiveButton(R.string.ok, null).show();
    }

    private void line(StringBuilder sb, int label, String value) {
        if (value == null || value.isEmpty()) return;
        sb.append(getString(label)).append(": ").append(value).append('\n');
    }

    /** Swipes over a video change the item; a tap shows or hides the bars. */
    private final class SwipeTouch implements View.OnTouchListener {
        private final android.view.GestureDetector g = new android.view.GestureDetector(ViewerActivity.this,
                new android.view.GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(android.view.MotionEvent e) {
                        return true;
                    }

                    @Override
                    public boolean onSingleTapConfirmed(android.view.MotionEvent e) {
                        toggleBars();
                        return true;
                    }

                    @Override
                    public boolean onFling(android.view.MotionEvent a, android.view.MotionEvent b, float vx, float vy) {
                        if (Math.abs(vx) > Math.abs(vy) * 1.5f && Math.abs(vx) > 600) {
                            move(vx < 0 ? 1 : -1);
                            return true;
                        }
                        return false;
                    }
                });

        @Override
        public boolean onTouch(View v, android.view.MotionEvent e) {
            return g.onTouchEvent(e);
        }
    }
}
