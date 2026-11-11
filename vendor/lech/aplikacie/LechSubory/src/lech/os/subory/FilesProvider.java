/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.subory;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Hands single files to other apps (open, share) as content:// URIs with a temporary read grant. Not exported:
 * other apps reach a file only through a grant of this app, and only files on the shared storage.
 */
public class FilesProvider extends ContentProvider {
    static final String AUTHORITY = "lech.os.subory.files";

    static Uri uriFor(File f) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).path(f.getAbsolutePath()).build();
    }

    private static File fileOf(Uri uri) throws FileNotFoundException {
        String path = uri.getPath();
        if (path == null) throw new FileNotFoundException();
        File f = new File(path);
        try {
            String canonical = f.getCanonicalPath();
            String root = Fs.root().getCanonicalPath() + "/";
            boolean onStorage = canonical.startsWith(root) || canonical.startsWith("/storage/");
            if (!onStorage || canonical.startsWith("/storage/emulated/0/Android/data")
                    || canonical.startsWith("/storage/emulated/0/Android/obb")) {
                throw new FileNotFoundException();
            }
            return new File(canonical);
        } catch (IOException e) {
            throw new FileNotFoundException();
        }
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new SecurityException("read only");
        return ParcelFileDescriptor.open(fileOf(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        File f;
        try {
            f = fileOf(uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        String[] cols = projection == null ? new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                : projection;
        MatrixCursor c = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for (int k = 0; k < cols.length; k++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[k])) {
                row[k] = f.getName();
            } else if (OpenableColumns.SIZE.equals(cols[k])) {
                row[k] = f.length();
            }
        }
        c.addRow(row);
        return c;
    }

    @Override
    public String getType(Uri uri) {
        try {
            return Fs.mime(fileOf(uri));
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }
}
