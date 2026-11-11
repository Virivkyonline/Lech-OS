/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.galeria;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.MediaStore.Files.FileColumns;
import android.text.TextUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** A photo or a video of the media store, and the queries of the gallery. */
final class Media {
    long id;
    Uri uri;
    boolean video;
    long date;
    long duration;
    String name;
    String bucket;
    String bucketId;
    int width;
    int height;
    long size;
    boolean favorite;
    String mime;

    /** An album: a folder with photos or videos. */
    static final class Album {
        String id;
        String title;
        final List<Media> items = new ArrayList<>();
    }

    static final int ALL = 0;
    static final int VIDEOS = 1;
    static final int FAVORITES = 2;
    static final int TRASH = 3;

    private static final String[] PROJECTION = {
        FileColumns._ID, FileColumns.MEDIA_TYPE, MediaStore.MediaColumns.DATE_TAKEN, FileColumns.DATE_ADDED,
        MediaStore.MediaColumns.DURATION, FileColumns.DISPLAY_NAME, MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
        MediaStore.MediaColumns.BUCKET_ID, FileColumns.WIDTH, FileColumns.HEIGHT, FileColumns.SIZE,
        MediaStore.MediaColumns.IS_FAVORITE, FileColumns.MIME_TYPE, MediaStore.MediaColumns.ORIENTATION,
    };

    /** Photos and videos of one kind, newest first (or oldest first). */
    static List<Media> query(Context c, int kind, boolean oldestFirst) {
        ContentResolver r = c.getContentResolver();
        Uri files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL);
        StringBuilder sel = new StringBuilder();
        if (kind == VIDEOS) {
            sel.append(FileColumns.MEDIA_TYPE).append('=').append(FileColumns.MEDIA_TYPE_VIDEO);
        } else {
            sel.append(FileColumns.MEDIA_TYPE).append(" IN (").append(FileColumns.MEDIA_TYPE_IMAGE).append(',')
                    .append(FileColumns.MEDIA_TYPE_VIDEO).append(')');
        }
        if (kind == FAVORITES) sel.append(" AND ").append(MediaStore.MediaColumns.IS_FAVORITE).append("=1");
        Bundle args = new Bundle();
        args.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, sel.toString());
        if (kind == TRASH) args.putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY);
        List<Media> out = new ArrayList<>();
        try (Cursor cur = r.query(files, PROJECTION, args, null)) {
            while (cur != null && cur.moveToNext()) {
                Media m = new Media();
                m.id = cur.getLong(0);
                m.video = cur.getInt(1) == FileColumns.MEDIA_TYPE_VIDEO;
                m.uri = ContentUris.withAppendedId(m.video
                        ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                        : MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), m.id);
                m.date = cur.isNull(2) || cur.getLong(2) <= 0 ? cur.getLong(3) * 1000L : cur.getLong(2);
                m.duration = cur.getLong(4);
                m.name = cur.getString(5);
                m.bucket = cur.getString(6);
                m.bucketId = cur.getString(7);
                m.width = cur.getInt(8);
                m.height = cur.getInt(9);
                int orientation = cur.getInt(13);
                if (orientation == 90 || orientation == 270) {
                    int w = m.width;
                    m.width = m.height;
                    m.height = w;
                }
                m.size = cur.getLong(10);
                m.favorite = cur.getInt(11) != 0;
                m.mime = cur.getString(12);
                out.add(m);
            }
        }
        Collections.sort(out, (a, b) -> oldestFirst ? Long.compare(a.date, b.date) : Long.compare(b.date, a.date));
        return out;
    }

    /** Albums (folders) of the items, the album with the newest item first. */
    static List<Album> albums(List<Media> items) {
        Map<String, Album> m = new LinkedHashMap<>();
        for (Media it : items) {
            String key = it.bucketId == null ? "" : it.bucketId;
            Album a = m.get(key);
            if (a == null) {
                a = new Album();
                a.id = key;
                a.title = TextUtils.isEmpty(it.bucket) ? "—" : it.bucket;
                m.put(key, a);
            }
            a.items.add(it);
        }
        return new ArrayList<>(m.values());
    }

    /** Items whose file or album name contains the text (case and accents ignored). */
    static List<Media> filter(List<Media> items, String text) {
        String q = plain(text);
        List<Media> out = new ArrayList<>();
        for (Media it : items) {
            if (plain(it.name).contains(q) || plain(it.bucket).contains(q)) out.add(it);
        }
        return out;
    }

    static String plain(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    static List<Uri> uris(List<Media> items) {
        List<Uri> out = new ArrayList<>();
        for (Media m : items) out.add(m.uri);
        return out;
    }

    static String duration(long ms) {
        long s = ms / 1000;
        if (s >= 3600) return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }
}
