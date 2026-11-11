/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.subory;

import android.content.Context;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Files.FileColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** File operations and the file categories of the media store. */
final class Fs {
    static final int CAT_IMAGES = 0;
    static final int CAT_AUDIO = 1;
    static final int CAT_VIDEOS = 2;
    static final int CAT_DOCUMENTS = 3;
    static final int CAT_ARCHIVES = 4;
    static final int CAT_RECENT = 5;

    static final int SORT_NAME = 0;
    static final int SORT_DATE = 1;
    static final int SORT_SIZE = 2;

    private static final String[] DOCUMENT_TYPES = {
        "application/pdf", "application/msword", "application/rtf", "text/plain", "text/csv", "text/html",
        "application/vnd.oasis.opendocument.text", "application/vnd.oasis.opendocument.spreadsheet",
        "application/vnd.oasis.opendocument.presentation", "application/vnd.ms-excel",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    };
    private static final String[] ARCHIVE_TYPES = {
        "application/zip", "application/x-zip-compressed", "application/x-7z-compressed", "application/x-rar-compressed",
        "application/vnd.rar", "application/gzip", "application/x-gzip", "application/x-tar", "application/x-bzip2",
        "application/x-xz",
    };

    private Fs() {
    }

    static File root() {
        return Environment.getExternalStorageDirectory();
    }

    static String mime(File f) {
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return "application/octet-stream";
        String t = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(dot + 1).toLowerCase(Locale.ROOT));
        return t == null ? "application/octet-stream" : t;
    }

    /** The children of a folder, folders first, sorted. */
    static List<File> list(File dir, int sort, boolean hidden) {
        File[] all = dir.listFiles();
        List<File> out = new ArrayList<>();
        if (all == null) return out;
        for (File f : all) if (hidden || !f.getName().startsWith(".")) out.add(f);
        sort(out, sort);
        return out;
    }

    static void sort(List<File> files, int sort) {
        final Collator col = Collator.getInstance();
        col.setStrength(Collator.PRIMARY);
        Comparator<File> by;
        if (sort == SORT_DATE) {
            by = (a, b) -> Long.compare(b.lastModified(), a.lastModified());
        } else if (sort == SORT_SIZE) {
            by = (a, b) -> Long.compare(b.length(), a.length());
        } else {
            by = (a, b) -> col.compare(a.getName(), b.getName());
        }
        Collections.sort(files, (a, b) -> {
            boolean da = a.isDirectory();
            boolean db = b.isDirectory();
            if (da != db) return da ? -1 : 1;
            return by.compare(a, b);
        });
    }

    static int count(File dir, boolean hidden) {
        String[] names = dir.list();
        if (names == null) return 0;
        if (hidden) return names.length;
        int n = 0;
        for (String s : names) if (!s.startsWith(".")) n++;
        return n;
    }

    /** Files of a category (or the recent files), newest first, from the media store. */
    static List<File> category(Context c, int cat, int limit) {
        StringBuilder sel = new StringBuilder();
        List<String> args = new ArrayList<>();
        switch (cat) {
            case CAT_IMAGES:
                sel.append(FileColumns.MEDIA_TYPE).append('=').append(FileColumns.MEDIA_TYPE_IMAGE);
                break;
            case CAT_AUDIO:
                sel.append(FileColumns.MEDIA_TYPE).append('=').append(FileColumns.MEDIA_TYPE_AUDIO);
                break;
            case CAT_VIDEOS:
                sel.append(FileColumns.MEDIA_TYPE).append('=').append(FileColumns.MEDIA_TYPE_VIDEO);
                break;
            case CAT_DOCUMENTS:
                in(sel, args, DOCUMENT_TYPES);
                break;
            case CAT_ARCHIVES:
                in(sel, args, ARCHIVE_TYPES);
                break;
            default:
                sel.append(FileColumns.MIME_TYPE).append(" IS NOT NULL");
                break;
        }
        Bundle q = new Bundle();
        q.putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, sel.toString());
        if (!args.isEmpty()) {
            q.putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args.toArray(new String[0]));
        }
        q.putStringArray(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS,
                new String[] {FileColumns.DATE_MODIFIED});
        q.putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION,
                android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING);
        q.putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limit);
        List<File> out = new ArrayList<>();
        try (Cursor cur = c.getContentResolver().query(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                new String[] {FileColumns.DATA}, q, null)) {
            while (cur != null && cur.moveToNext()) {
                String p = cur.getString(0);
                if (p == null) continue;
                File f = new File(p);
                if (f.isFile() && !p.contains("/.")) out.add(f);
            }
        } catch (Exception ignored) {
            // No access yet.
        }
        return out;
    }

    private static void in(StringBuilder sel, List<String> args, String[] types) {
        sel.append(FileColumns.MIME_TYPE).append(" IN (");
        for (int k = 0; k < types.length; k++) {
            sel.append(k == 0 ? "?" : ",?");
            args.add(types[k]);
        }
        sel.append(')');
    }

    /** Files and folders under dir whose names contain the text (accents and case ignored), at most limit. */
    static List<File> search(File dir, String text, boolean hidden, int limit) {
        List<File> out = new ArrayList<>();
        String q = plain(text);
        ArrayList<File> stack = new ArrayList<>();
        stack.add(dir);
        while (!stack.isEmpty() && out.size() < limit) {
            File d = stack.remove(stack.size() - 1);
            File[] all = d.listFiles();
            if (all == null) continue;
            for (File f : all) {
                if (!hidden && f.getName().startsWith(".")) continue;
                if (plain(f.getName()).contains(q)) out.add(f);
                if (f.isDirectory() && !f.getName().equals("Android")) stack.add(f);
                if (out.size() >= limit) break;
            }
        }
        sort(out, SORT_NAME);
        return out;
    }

    static String plain(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    /** A free name in dir: "name", "name (2)", "name (3)"… */
    static File freeName(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        for (int k = 2; ; k++) {
            f = new File(dir, base + " (" + k + ")" + ext);
            if (!f.exists()) return f;
        }
    }

    /** Whether dest is src or lies inside it. */
    static boolean inside(File dest, File src) {
        try {
            String d = dest.getCanonicalPath() + "/";
            String s = src.getCanonicalPath() + "/";
            return d.startsWith(s);
        } catch (IOException e) {
            return true;
        }
    }

    static boolean copy(File src, File dest, List<String> changed) {
        if (src.isDirectory()) {
            if (!dest.mkdirs() && !dest.isDirectory()) return false;
            boolean ok = true;
            File[] kids = src.listFiles();
            if (kids != null) for (File k : kids) ok &= copy(k, new File(dest, k.getName()), changed);
            return ok;
        }
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[256 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (IOException e) {
            dest.delete();
            return false;
        }
        dest.setLastModified(src.lastModified());
        changed.add(dest.getAbsolutePath());
        return true;
    }

    static boolean move(File src, File dest, List<String> changed) {
        List<String> old = new ArrayList<>();
        collect(src, old);
        if (src.renameTo(dest)) {
            changed.addAll(old);
            collect(dest, changed);
            return true;
        }
        if (copy(src, dest, changed)) {
            return delete(src, changed);
        }
        return false;
    }

    static boolean delete(File f, List<String> changed) {
        boolean ok = true;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) ok &= delete(k, changed);
        }
        changed.add(f.getAbsolutePath());
        return f.delete() && ok;
    }

    private static void collect(File f, List<String> out) {
        out.add(f.getAbsolutePath());
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) collect(k, out);
        }
    }

    /** Tells the media store about added, changed and removed files (so the gallery shows them). */
    static void scan(Context c, List<String> paths) {
        if (paths.isEmpty()) return;
        MediaScannerConnection.scanFile(c.getApplicationContext(), paths.toArray(new String[0]), null, null);
    }

    static Uri uri(File f) {
        return FilesProvider.uriFor(f);
    }

    static List<File> asList(File... f) {
        return new ArrayList<>(Arrays.asList(f));
    }
}
