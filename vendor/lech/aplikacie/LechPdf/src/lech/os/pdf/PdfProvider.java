/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.pdf;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Locale;

/**
 * Hands single PDF files of the shared storage to the PDF viewer, the print spooler and share targets as read-only
 * content:// URIs. Not exported: other apps reach a file only through a temporary grant of this app, and only
 * regular *.pdf files on the shared storage (never app-private folders).
 */
public class PdfProvider extends ContentProvider {
    static final String AUTHORITY = "lech.os.pdf.files";

    static Uri uriFor(File f) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).path(f.getAbsolutePath()).build();
    }

    private static File fileOf(Uri uri) throws FileNotFoundException {
        String path = uri.getPath();
        if (path == null) throw new FileNotFoundException();
        try {
            String canonical = new File(path).getCanonicalPath();
            String root = Environment.getExternalStorageDirectory().getCanonicalPath() + "/";
            boolean onStorage = canonical.startsWith(root) || canonical.startsWith("/storage/");
            if (!onStorage || canonical.contains("/Android/data/") || canonical.contains("/Android/obb/")
                    || !canonical.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                throw new FileNotFoundException();
            }
            File f = new File(canonical);
            if (!f.isFile()) throw new FileNotFoundException();
            return f;
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
        return Library.PDF;
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
