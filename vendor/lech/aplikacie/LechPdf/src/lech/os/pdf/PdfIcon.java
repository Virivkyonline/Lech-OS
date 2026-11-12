/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.pdf;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/** The red PDF file icon of the list: a page with a folded corner and a "PDF" label, drawn in code. */
final class PdfIcon extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private int alpha = 255;

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float w = b.width();
        float h = b.height();
        float x = b.left;
        float y = b.top;
        int saved = alpha < 255 ? c.saveLayerAlpha(x, y, x + w, y + h, alpha) : -1;

        // The page with the top right corner folded.
        float pl = x + w * 0.16f;
        float pr = x + w * 0.94f;
        float pt = y + h * 0.03f;
        float pb = y + h * 0.97f;
        float fold = (pr - pl) * 0.34f;
        float r = w * 0.08f;
        path.reset();
        path.moveTo(pl + r, pt);
        path.lineTo(pr - fold, pt);
        path.lineTo(pr, pt + fold);
        path.lineTo(pr, pb - r);
        path.quadTo(pr, pb, pr - r, pb);
        path.lineTo(pl + r, pb);
        path.quadTo(pl, pb, pl, pb - r);
        path.lineTo(pl, pt + r);
        path.quadTo(pl, pt, pl + r, pt);
        path.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, pt, 0, pb, 0xFFFF6B6B, 0xFFD32F2F, Shader.TileMode.CLAMP));
        c.drawPath(path, paint);
        paint.setShader(null);

        // The folded corner.
        path.reset();
        path.moveTo(pr - fold, pt);
        path.lineTo(pr - fold, pt + fold - r * 0.6f);
        path.quadTo(pr - fold, pt + fold, pr - fold + r * 0.6f, pt + fold);
        path.lineTo(pr, pt + fold);
        path.close();
        paint.setColor(0xFFFFD6D6);
        c.drawPath(path, paint);

        // The label, reaching over the left edge of the page.
        rect.set(x + w * 0.02f, y + h * 0.50f, x + w * 0.86f, y + h * 0.82f);
        float lr = w * 0.07f;
        paint.setColor(0xFFB71C1C);
        c.drawRoundRect(rect, lr, lr, paint);
        paint.setColor(0xFFFFFFFF);
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        float size = rect.height() * 0.68f;
        paint.setTextSize(size);
        float maxW = rect.width() * 0.86f;
        float tw = paint.measureText("PDF");
        if (tw > maxW) paint.setTextSize(size * maxW / tw);
        Paint.FontMetrics fm = paint.getFontMetrics();
        c.drawText("PDF", rect.centerX(), rect.centerY() - (fm.ascent + fm.descent) / 2f, paint);
        if (saved >= 0) c.restoreToCount(saved);
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        paint.setColorFilter(cf);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
