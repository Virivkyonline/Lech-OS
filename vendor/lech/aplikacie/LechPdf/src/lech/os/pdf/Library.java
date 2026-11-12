/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.pdf;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.MediaStore.Files.FileColumns;
import android.provider.OpenableColumns;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** The PDF files of the shared storage (media store) and the recent and favourite documents (preferences). */
final class Library {
    static final String PDF = "application/pdf";
    private static final int MAX_RECENT = 30;
    private static final String KEY_RECENT = "recent";
    private static final String KEY_FAVORITES = "favorites";

    private final ContentResolver resolver;
    private final SharedPreferences prefs;
    private final List<Pdf> recent = new ArrayList<>();
    private final List<Pdf> favorites = new ArrayList<>();
    private final Set<String> favoriteKeys = new HashSet<>();

    Library(Context c) {
        resolver = c.getContentResolver();
        prefs = c.getSharedPreferences("library", Context.MODE_PRIVATE);
        read(KEY_RECENT, recent);
        read(KEY_FAVORITES, favorites);
        for (Pdf p : favorites) favoriteKeys.add(p.key());
    }

    // ---- Shared storage ----

    /** All PDF files of the shared storage, newest first (needs access to all files to see other apps' files). */
    static List<Pdf> scan(Context c) {
        Bundle q = new Bundle();
        q.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, FileColumns.MIME_TYPE + "=?");
        q.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, new String[] {PDF});
        q.putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, new String[] {FileColumns.DATE_MODIFIED});
        q.putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING);
        String[] cols = {FileColumns.DATA, FileColumns.DISPLAY_NAME, FileColumns.SIZE, FileColumns.DATE_MODIFIED};
        List<Pdf> out = new ArrayList<>();
        try (Cursor cur = c.getContentResolver().query(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                cols, q, null)) {
            while (cur != null && cur.moveToNext()) {
                String path = cur.getString(0);
                if (path == null || path.contains("/.")) continue;
                File f = new File(path);
                if (!f.isFile()) continue;
                Pdf p = new Pdf();
                p.path = path;
                p.name = cur.isNull(1) ? f.getName() : cur.getString(1);
                p.size = cur.isNull(2) ? f.length() : cur.getLong(2);
                p.modified = cur.isNull(3) ? f.lastModified() : cur.getLong(3) * 1000L;
                out.add(p);
            }
        } catch (Exception ignored) {
            // No access yet: nothing found.
        }
        return out;
    }

    /** Name, size and date of a document picked in the system file picker. */
    static Pdf describe(Context c, Uri u) {
        Pdf p = new Pdf();
        p.uri = u.toString();
        try (Cursor cur = c.getContentResolver().query(u, null, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                int n = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int s = cur.getColumnIndex(OpenableColumns.SIZE);
                int m = cur.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED);
                if (n >= 0 && !cur.isNull(n)) p.name = cur.getString(n);
                if (s >= 0 && !cur.isNull(s)) p.size = cur.getLong(s);
                if (m >= 0 && !cur.isNull(m)) p.modified = cur.getLong(m);
            }
        } catch (Exception ignored) {
            // Keep what is known.
        }
        if (p.name == null || p.name.isEmpty()) {
            String last = u.getLastPathSegment();
            p.name = last == null ? "PDF" : last.substring(last.lastIndexOf('/') + 1);
        }
        return p;
    }

    // ---- Recent ----

    List<Pdf> recent() {
        return recent;
    }

    void addRecent(Pdf p) {
        remove(recent, p.key());
        Pdf c = p.copy();
        c.time = System.currentTimeMillis();
        recent.add(0, c);
        while (recent.size() > MAX_RECENT) releaseIfUnused(recent.remove(recent.size() - 1));
        write(KEY_RECENT, recent);
    }

    void removeRecent(Pdf p) {
        Pdf old = remove(recent, p.key());
        write(KEY_RECENT, recent);
        if (old != null) releaseIfUnused(old);
    }

    void clearRecent() {
        List<Pdf> old = new ArrayList<>(recent);
        recent.clear();
        write(KEY_RECENT, recent);
        for (Pdf p : old) releaseIfUnused(p);
    }

    boolean isRecent(Pdf p) {
        for (Pdf r : recent) if (r.key().equals(p.key())) return true;
        return false;
    }

    // ---- Favourites ----

    List<Pdf> favorites() {
        return favorites;
    }

    boolean isFavorite(Pdf p) {
        return favoriteKeys.contains(p.key());
    }

    void toggleFavorite(Pdf p) {
        if (favoriteKeys.remove(p.key())) {
            Pdf old = remove(favorites, p.key());
            write(KEY_FAVORITES, favorites);
            if (old != null) releaseIfUnused(old);
        } else {
            Pdf c = p.copy();
            c.time = System.currentTimeMillis();
            favorites.add(0, c);
            favoriteKeys.add(c.key());
            write(KEY_FAVORITES, favorites);
        }
    }

    // ---- Storage ----

    /** A picked document keeps its persisted read grant only while it is recent or a favourite. */
    private void releaseIfUnused(Pdf p) {
        if (p.uri == null || isRecent(p) || isFavorite(p)) return;
        try {
            resolver.releasePersistableUriPermission(Uri.parse(p.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Not persisted (any more).
        }
    }

    private static Pdf remove(List<Pdf> list, String key) {
        for (Iterator<Pdf> it = list.iterator(); it.hasNext(); ) {
            Pdf p = it.next();
            if (p.key().equals(key)) {
                it.remove();
                return p;
            }
        }
        return null;
    }

    private void read(String key, List<Pdf> into) {
        try {
            JSONArray a = new JSONArray(prefs.getString(key, "[]"));
            for (int k = 0; k < a.length(); k++) {
                Pdf p = Pdf.fromJson(a.getJSONObject(k));
                if (p != null) into.add(p);
            }
        } catch (JSONException ignored) {
            // Damaged list: start empty.
        }
    }

    private void write(String key, List<Pdf> list) {
        JSONArray a = new JSONArray();
        try {
            for (Pdf p : list) a.put(p.toJson());
        } catch (JSONException ignored) {
            // Cannot happen with plain values.
        }
        prefs.edit().putString(key, a.toString()).apply();
    }
}
