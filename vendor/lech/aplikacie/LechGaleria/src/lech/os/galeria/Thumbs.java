/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.galeria;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads thumbnails of the media store in the background and keeps the recent ones in memory. */
final class Thumbs {
    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>((int) Math.min(Runtime.getRuntime().maxMemory() / 6, 96L << 20)) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Thumbs() {
    }

    static void load(Context c, ImageView v, Uri uri, int px) {
        final String key = uri + "@" + px;
        v.setTag(key);
        Bitmap b = CACHE.get(key);
        if (b != null) {
            v.setImageBitmap(b);
            return;
        }
        v.setImageDrawable(null);
        final Context app = c.getApplicationContext();
        POOL.execute(() -> {
            if (!key.equals(v.getTag())) return;
            Bitmap bm;
            try {
                bm = app.getContentResolver().loadThumbnail(uri, new Size(px, px), null);
            } catch (Exception e) {
                bm = null;
            }
            final Bitmap res = bm;
            if (res != null) CACHE.put(key, res);
            MAIN.post(() -> {
                if (key.equals(v.getTag())) v.setImageBitmap(res);
            });
        });
    }

    static void clear() {
        CACHE.evictAll();
    }
}
