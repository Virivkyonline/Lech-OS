/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.pdf;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Prints a PDF document as it is: the file is streamed unchanged to the print spooler, which lays out the pages
 * itself. The app does not parse or render the PDF.
 */
final class PdfPrintAdapter extends PrintDocumentAdapter {
    private final Context context;
    private final Uri uri;
    private final String name;
    private final Handler main = new Handler(Looper.getMainLooper());

    PdfPrintAdapter(Context context, Uri uri, String name) {
        this.context = context.getApplicationContext();
        this.uri = uri;
        String n = name == null || name.isEmpty() ? "document" : name;
        this.name = n.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf") ? n : n + ".pdf";
    }

    @Override
    public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes, CancellationSignal cancel,
            LayoutResultCallback callback, Bundle extras) {
        if (cancel.isCanceled()) {
            callback.onLayoutCancelled();
            return;
        }
        PrintDocumentInfo info = new PrintDocumentInfo.Builder(name)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build();
        callback.onLayoutFinished(info, true);
    }

    @Override
    public void onWrite(PageRange[] pages, ParcelFileDescriptor destination, CancellationSignal cancel,
            WriteResultCallback callback) {
        // Copy off the main thread; the callbacks are delivered on the main thread.
        new Thread(() -> {
            // The destination descriptor belongs to the print framework, which closes it; the stream is not closed.
            FileOutputStream out = new FileOutputStream(destination.getFileDescriptor());
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("no stream");
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    if (cancel.isCanceled()) {
                        main.post(callback::onWriteCancelled);
                        return;
                    }
                    out.write(buf, 0, n);
                }
                out.flush();
                main.post(() -> callback.onWriteFinished(new PageRange[] {PageRange.ALL_PAGES}));
            } catch (IOException | RuntimeException e) {
                final String msg = e.getMessage();
                main.post(() -> callback.onWriteFailed(msg));
            }
        }, "LechPdfPrint").start();
    }
}
