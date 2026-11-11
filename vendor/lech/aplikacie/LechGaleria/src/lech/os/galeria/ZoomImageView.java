/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.galeria;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

/** An image that fits the screen and can be zoomed (pinch, double tap) and moved; a swipe changes the image. */
final class ZoomImageView extends ImageView {
    interface Listener {
        void onSwipe(int direction);
        void onTap();
    }

    private final Matrix matrix = new Matrix();
    private final float[] values = new float[9];
    private final ScaleGestureDetector scaler;
    private final GestureDetector gestures;
    private float base = 1f;
    private Listener listener;

    ZoomImageView(Context c) {
        super(c);
        setScaleType(ScaleType.MATRIX);
        scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                float s = scale();
                float f = d.getScaleFactor();
                float target = Math.max(base, Math.min(base * 6f, s * f));
                f = target / s;
                matrix.postScale(f, f, d.getFocusX(), d.getFocusY());
                clamp();
                return true;
            }
        });
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (listener != null) listener.onTap();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (zoomed()) {
                    fit();
                } else {
                    matrix.postScale(2.5f, 2.5f, e.getX(), e.getY());
                    clamp();
                }
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (zoomed()) {
                    matrix.postTranslate(-dx, -dy);
                    clamp();
                }
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (!zoomed() && listener != null && Math.abs(vx) > Math.abs(vy) * 1.5f && Math.abs(vx) > 600) {
                    listener.onSwipe(vx < 0 ? 1 : -1);
                    return true;
                }
                return false;
            }
        });
    }

    void setListener(Listener l) {
        listener = l;
    }

    @Override
    public void setImageDrawable(Drawable d) {
        super.setImageDrawable(d);
        fit();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        fit();
    }

    private float scale() {
        matrix.getValues(values);
        return values[Matrix.MSCALE_X];
    }

    private boolean zoomed() {
        return scale() > base * 1.05f;
    }

    void fit() {
        Drawable d = getDrawable();
        if (d == null || getWidth() == 0 || getHeight() == 0) return;
        float dw = d.getIntrinsicWidth();
        float dh = d.getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) return;
        base = Math.min(getWidth() / dw, getHeight() / dh);
        matrix.reset();
        matrix.postScale(base, base);
        matrix.postTranslate((getWidth() - dw * base) / 2f, (getHeight() - dh * base) / 2f);
        setImageMatrix(matrix);
    }

    /** Keeps the image on the screen: centred when smaller, no empty edge when larger. */
    private void clamp() {
        Drawable d = getDrawable();
        if (d == null) return;
        RectF r = new RectF(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        matrix.mapRect(r);
        float dx = 0;
        float dy = 0;
        if (r.width() <= getWidth()) {
            dx = (getWidth() - r.width()) / 2f - r.left;
        } else if (r.left > 0) {
            dx = -r.left;
        } else if (r.right < getWidth()) {
            dx = getWidth() - r.right;
        }
        if (r.height() <= getHeight()) {
            dy = (getHeight() - r.height()) / 2f - r.top;
        } else if (r.top > 0) {
            dy = -r.top;
        } else if (r.bottom < getHeight()) {
            dy = getHeight() - r.bottom;
        }
        matrix.postTranslate(dx, dy);
        setImageMatrix(matrix);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaler.onTouchEvent(e);
        if (!scaler.isInProgress()) gestures.onTouchEvent(e);
        if (zoomed()) getParent().requestDisallowInterceptTouchEvent(true);
        return true;
    }
}
