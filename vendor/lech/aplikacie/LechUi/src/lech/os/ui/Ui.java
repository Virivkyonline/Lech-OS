/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.text.InputType;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;

/** Colours, shapes and small view builders of the LECH OS design (dark glass, neon green). */
public final class Ui {
    public static final int BG_TOP = 0xFF07192A;
    public static final int BG_BOTTOM = 0xFF020B14;
    public static final int GREEN = 0xFF7BE41E;
    public static final int GREEN_LIGHT = 0xFFA6FF4D;
    public static final int GREEN_DARK = 0xFF1E6A06;
    public static final int GLASS = 0xB30B2431;
    public static final int GLASS_STRONG = 0xE60D2A38;
    public static final int GLASS_STROKE = 0x8034707A;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_2 = 0xFFC3D2DD;
    public static final int TEXT_3 = 0xFF8297A8;
    public static final int ON_GREEN = 0xFF06210A;
    public static final int RED = 0xFFFF6B6B;

    private static final int[] AVATAR_COLORS = {
        0xFF2E7D32, 0xFF00838F, 0xFF1565C0, 0xFF6A1B9A, 0xFFAD1457, 0xFFEF6C00, 0xFF4E6E0E, 0xFF37474F,
    };

    private static final LruCache<String, Bitmap> PHOTOS = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private Ui() {
    }

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static GradientDrawable background() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {BG_TOP, 0xFF051424, BG_BOTTOM});
    }

    public static GradientDrawable shape(Context c, int fill, int stroke, float strokeDp, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        if (strokeDp > 0) d.setStroke(Math.max(1, dp(c, strokeDp)), stroke);
        return d;
    }

    public static GradientDrawable glass(Context c, float radiusDp) {
        return shape(c, GLASS, GLASS_STROKE, 1, radiusDp);
    }

    public static GradientDrawable greenGlow(Context c, float radiusDp) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {GREEN_LIGHT, GREEN, 0xFF4FB80C});
        d.setCornerRadius(dp(c, radiusDp));
        d.setStroke(dp(c, 1.5f), 0xFFC8FF8A);
        return d;
    }

    public static GradientDrawable circle(int fill, int stroke, int strokePx) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(fill);
        if (strokePx > 0) d.setStroke(strokePx, stroke);
        return d;
    }

    /** A touch ripple over a background (null: ripple only, bounded by the view). */
    public static Drawable ripple(Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), content,
                content == null ? new GradientDrawable() : null);
    }

    public static void clickable(View v, Drawable bg) {
        v.setBackground(ripple(bg));
        v.setClickable(true);
        v.setFocusable(true);
    }

    public static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return t;
    }

    public static ImageView icon(Context c, int res, int color, float sizeDp) {
        ImageView i = new ImageView(c);
        i.setImageResource(res);
        i.setImageTintList(ColorStateList.valueOf(color));
        i.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return i;
    }

    /** A round glass button with an icon; green = the main action. */
    public static FrameLayout roundButton(Context c, int iconRes, boolean green, float sizeDp, CharSequence description,
            View.OnClickListener l) {
        FrameLayout f = new FrameLayout(c);
        GradientDrawable bg = green
                ? circle(0xFF2E8A0A, 0xFFB6FF6A, dp(c, 2))
                : circle(GLASS_STRONG, GLASS_STROKE, dp(c, 1));
        if (green) {
            bg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            bg.setColors(new int[] {GREEN, 0xFF2C8C08});
            bg.setGradientRadius(dp(c, sizeDp * 0.7f));
        }
        clickable(f, bg);
        ImageView i = icon(c, iconRes, green ? TEXT : TEXT, sizeDp * 0.5f);
        f.addView(i, new FrameLayout.LayoutParams(dp(c, sizeDp * 0.5f), dp(c, sizeDp * 0.5f), Gravity.CENTER));
        f.setContentDescription(description);
        f.setOnClickListener(l);
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return f;
    }

    /** The big green pill button ("+ Pridať kontakt"). */
    public static LinearLayout pillButton(Context c, int iconRes, CharSequence label, View.OnClickListener l) {
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xFF2F7F10, 0xFF124A06});
        bg.setCornerRadius(dp(c, 32));
        bg.setStroke(dp(c, 2), GREEN);
        clickable(b, bg);
        int pad = dp(c, 16);
        b.setPadding(dp(c, 28), pad, dp(c, 32), pad);
        if (iconRes != 0) {
            ImageView i = icon(c, iconRes, TEXT, 26);
            b.addView(i);
        }
        TextView t = text(c, label, 19, TEXT, true);
        t.setPadding(dp(c, 14), 0, 0, 0);
        b.addView(t);
        b.setOnClickListener(l);
        return b;
    }

    /** A text field on glass. */
    public static EditText field(Context c, CharSequence hint, int inputType) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setInputType(inputType);
        e.setSingleLine((inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0);
        e.setTextColor(TEXT);
        e.setHintTextColor(TEXT_3);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        e.setBackground(shape(c, GLASS, GLASS_STROKE, 1, 16));
        e.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        return e;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    public static LinearLayout.LayoutParams margins(LinearLayout.LayoutParams p, Context c, float l, float t, float r, float b) {
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    /** Edge to edge: the root keeps clear of the status bar, the navigation bar and the keyboard. */
    public static void edgeToEdge(Activity a, View root) {
        a.getWindow().setDecorFitsSystemWindows(false);
        root.setOnApplyWindowInsetsListener((v, in) -> {
            Insets i = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime()
                    | WindowInsets.Type.displayCutout());
            v.setPadding(i.left, i.top, i.right, i.bottom);
            return WindowInsets.CONSUMED;
        });
    }

    /** A round avatar: the contact photo, or the initial on a colour of the name. */
    public static ImageView avatar(Context c, String name, String photoUri, float sizeDp) {
        ImageView v = new ImageView(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        setAvatar(v, name, photoUri, sizeDp);
        return v;
    }

    public static void setAvatar(ImageView v, String name, String photoUri, float sizeDp) {
        Context c = v.getContext();
        int px = dp(c, sizeDp);
        Bitmap b = null;
        if (photoUri != null) {
            String key = photoUri + "@" + px;
            b = PHOTOS.get(key);
            if (b == null) {
                b = loadRound(c, Uri.parse(photoUri), px);
                if (b != null) PHOTOS.put(key, b);
            }
        }
        if (b == null) b = initials(c, name, px);
        v.setImageBitmap(b);
    }

    public static void forgetPhotos() {
        PHOTOS.evictAll();
    }

    private static Bitmap loadRound(Context c, Uri uri, int px) {
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            Bitmap src = BitmapFactory.decodeStream(in);
            if (src == null) return null;
            return roundCrop(src, px);
        } catch (Exception e) {
            return null;
        }
    }

    public static Bitmap roundCrop(Bitmap src, int px) {
        int side = Math.min(src.getWidth(), src.getHeight());
        Bitmap sq = Bitmap.createBitmap(src, (src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side);
        Bitmap scaled = Bitmap.createScaledBitmap(sq, px, px, true);
        Bitmap out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new android.graphics.BitmapShader(scaled, android.graphics.Shader.TileMode.CLAMP,
                android.graphics.Shader.TileMode.CLAMP));
        cv.drawCircle(px / 2f, px / 2f, px / 2f, p);
        return out;
    }

    /** A bitmap cropped to a square with rounded corners. */
    public static Bitmap roundCorners(Bitmap src, int radiusPx) {
        int side = Math.min(src.getWidth(), src.getHeight());
        Bitmap out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        android.graphics.BitmapShader sh = new android.graphics.BitmapShader(src,
                android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP);
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setTranslate(-(src.getWidth() - side) / 2f, -(src.getHeight() - side) / 2f);
        sh.setLocalMatrix(m);
        p.setShader(sh);
        cv.drawRoundRect(0, 0, side, side, radiusPx, radiusPx, p);
        return out;
    }

    private static Bitmap initials(Context c, String name, int px) {
        Bitmap out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        String n = name == null ? "" : name.trim();
        int color = AVATAR_COLORS[Math.abs(n.hashCode()) % AVATAR_COLORS.length];
        p.setColor(color);
        cv.drawCircle(px / 2f, px / 2f, px / 2f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1, px / 40f));
        p.setColor(0x66FFFFFF);
        cv.drawCircle(px / 2f, px / 2f, px / 2f - p.getStrokeWidth(), p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.WHITE);
        String letter = "";
        if (!n.isEmpty()) {
            int cp = n.codePointAt(0);
            letter = Character.isLetterOrDigit(cp) ? new String(Character.toChars(cp)).toUpperCase() : "";
        }
        if (letter.isEmpty()) {
            Drawable d = c.getDrawable(R.drawable.ic_person);
            int inset = px / 4;
            d.setBounds(inset, inset, px - inset, px - inset);
            d.setTint(Color.WHITE);
            d.draw(cv);
        } else {
            p.setTextSize(px * 0.44f);
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            p.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm = p.getFontMetrics();
            cv.drawText(letter, px / 2f, px / 2f - (fm.ascent + fm.descent) / 2f, p);
        }
        return out;
    }

    /** A soft green glow behind the big icon of an empty screen. */
    public static GradientDrawable glow(Context c, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        d.setColors(new int[] {0x667BE41E, 0x227BE41E, 0x00000000});
        d.setGradientRadius(dp(c, radiusDp));
        return d;
    }

    /** The header of a LECH OS app: logo, app icon, title, subtitle, round buttons and the mountains. */
    public static final class Header {
        public LinearLayout view;
        public TextView title;
        public TextView subtitle;
        public LinearLayout buttons;
        public View mountains;
    }

    public static Header header(Context c, int appIcon) {
        Header h = new Header();
        LinearLayout v = column(c);
        ImageView logo = new ImageView(c);
        logo.setImageResource(R.drawable.lech_logo);
        logo.setAdjustViewBounds(true);
        logo.setContentDescription("LECH OS");
        LinearLayout.LayoutParams lp = lp(-2, dp(c, 40));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(c, 4);
        v.addView(logo, lp);

        LinearLayout row = row(c);
        row.setPadding(dp(c, 16), dp(c, 6), dp(c, 12), 0);
        FrameLayout iconBox = new FrameLayout(c);
        iconBox.setBackground(shape(c, 0x33000000, 0x667BE41E, 1.5f, 22));
        ImageView icon = new ImageView(c);
        icon.setImageResource(appIcon);
        int p = dp(c, 3);
        iconBox.setPadding(p, p, p, p);
        iconBox.addView(icon, match());
        row.addView(iconBox, lp(dp(c, 68), dp(c, 68)));
        LinearLayout titles = column(c);
        titles.setPadding(dp(c, 14), 0, dp(c, 6), 0);
        h.title = text(c, "", 28, TEXT, true);
        h.title.setSingleLine(true);
        h.title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        h.subtitle = text(c, "", 15, TEXT_2, false);
        h.subtitle.setSingleLine(true);
        titles.addView(h.title);
        titles.addView(h.subtitle);
        row.addView(titles, lp(0, -2, 1));
        h.buttons = row(c);
        row.addView(h.buttons);
        v.addView(row, lp(-1, -2));

        FrameLayout m = new FrameLayout(c);
        ImageView hills = new ImageView(c);
        hills.setImageResource(R.drawable.hlavicka_hory);
        hills.setScaleType(ImageView.ScaleType.CENTER_CROP);
        m.addView(hills, match());
        View fadeTop = new View(c);
        fadeTop.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {BG_TOP, 0x0007192A}));
        m.addView(fadeTop, new FrameLayout.LayoutParams(-1, dp(c, 36), Gravity.TOP));
        View fadeBottom = new View(c);
        fadeBottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[] {0xFF061626, 0x00061626}));
        m.addView(fadeBottom, new FrameLayout.LayoutParams(-1, dp(c, 24), Gravity.BOTTOM));
        h.mountains = m;
        LinearLayout.LayoutParams mp = lp(-1, dp(c, 84));
        mp.topMargin = dp(c, 6);
        v.addView(m, mp);
        h.view = v;
        return h;
    }

    /** Adds a round button to the header (10 dp apart). */
    public static View headerButton(Header h, int icon, boolean green, CharSequence description,
            View.OnClickListener l) {
        Context c = h.view.getContext();
        FrameLayout b = roundButton(c, icon, green, 46, description, l);
        LinearLayout.LayoutParams p = lp(dp(c, 46), dp(c, 46));
        if (h.buttons.getChildCount() > 0) p.leftMargin = dp(c, 10);
        h.buttons.addView(b, p);
        return b;
    }

    /** A filter chip (icon + label); see setChip. */
    public static LinearLayout chip(Context c, int icon, CharSequence label) {
        LinearLayout ch = row(c);
        ch.setPadding(dp(c, 16), dp(c, 10), dp(c, 20), dp(c, 10));
        ch.addView(icon(c, icon, TEXT, 22));
        TextView t = text(c, label, 16, TEXT, true);
        t.setPadding(dp(c, 10), 0, 0, 0);
        ch.addView(t);
        setChip(ch, false);
        return ch;
    }

    public static void setChip(LinearLayout ch, boolean on) {
        Context c = ch.getContext();
        clickable(ch, on ? greenGlow(c, 26) : glass(c, 26));
        int color = on ? ON_GREEN : TEXT;
        ((ImageView) ch.getChildAt(0)).setImageTintList(ColorStateList.valueOf(color));
        ((TextView) ch.getChildAt(1)).setTextColor(color);
    }

    /** The bottom navigation bar on glass; items are added with navItem. */
    public static LinearLayout navBar(Context c) {
        LinearLayout nav = row(c);
        nav.setBackground(shape(c, 0xE60A2330, 0x9946A06E, 1.2f, 30));
        nav.setPadding(dp(c, 6), dp(c, 8), dp(c, 6), dp(c, 8));
        return nav;
    }

    public static LinearLayout navItem(LinearLayout nav, int icon, CharSequence label, View.OnClickListener l) {
        Context c = nav.getContext();
        LinearLayout item = column(c);
        item.setGravity(Gravity.CENTER);
        item.setPadding(0, dp(c, 4), 0, dp(c, 4));
        clickable(item, shape(c, 0, 0, 0, 22));
        item.addView(icon(c, icon, TEXT, 30));
        TextView t = text(c, label, 14, TEXT, false);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        item.addView(t);
        item.setOnClickListener(l);
        nav.addView(item, lp(0, -2, 1));
        return item;
    }

    public static void setNav(LinearLayout item, boolean on) {
        int color = on ? GREEN : TEXT;
        ((ImageView) item.getChildAt(0)).setImageTintList(ColorStateList.valueOf(color));
        TextView t = (TextView) item.getChildAt(1);
        t.setTextColor(color);
        t.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
    }

    public static ViewGroup.LayoutParams match() {
        return new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }
}
