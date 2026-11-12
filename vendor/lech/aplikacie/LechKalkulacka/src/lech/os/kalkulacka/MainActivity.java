/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.kalkulacka;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import lech.os.ui.Ui;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** The calculator: basic and scientific keypad, unit conversion and history, in the LECH OS design. */
public class MainActivity extends Activity {
    private static final int BASIC = 0;
    private static final int SCIENTIFIC = 1;
    private static final int CONVERT = 2;
    private static final int HISTORY_SIZE = 50;

    // Key looks.
    private static final int K_NUM = 0;
    private static final int K_OP = 1;
    private static final int K_EQUALS = 2;
    private static final int K_CLEAR = 3;
    private static final int K_FN = 4;

    private static final int[] CATEGORY_NAMES = {
        R.string.cat_length, R.string.cat_mass, R.string.cat_area, R.string.cat_volume, R.string.cat_temperature,
        R.string.cat_speed, R.string.cat_time, R.string.cat_data,
    };
    private static final int[] UNIT_ARRAYS = {
        R.array.units_length, R.array.units_mass, R.array.units_area, R.array.units_volume,
        R.array.units_temperature, R.array.units_speed, R.array.units_time, R.array.units_data,
    };

    private final Editor ed = new Editor();
    private SharedPreferences prefs;
    private int tab = BASIC;
    private int category = Units.LENGTH;
    private final int[] unitFrom = new int[Units.CATEGORIES];
    private final int[] unitTo = new int[Units.CATEGORIES];
    private String convInput = "";
    private String convResult = "";

    private final List<LinearLayout> tabs = new ArrayList<>();
    private FrameLayout page;

    // Calculator views (rebuilt with the page).
    private TextView exprView;
    private TextView resultView;
    private TextView angleKey;
    private TextView angleView;

    // Conversion views (rebuilt with the page).
    private final List<TextView> chips = new ArrayList<>();
    private HorizontalScrollView chipScroll;
    private TextView fromUnit;
    private TextView toUnit;
    private TextView fromValue;
    private TextView toValue;
    private TextView formula;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("kalkulacka", MODE_PRIVATE);
        load();

        LinearLayout root = Ui.column(this);
        root.setBackground(Ui.background());
        Ui.Header header = Ui.header(this, R.mipmap.ic_kalkulacka);
        Ui.headerButton(header, R.drawable.ic_more, false, getString(R.string.more), this::showMenu);
        header.title.setText(R.string.app_name);
        header.subtitle.setText(R.string.subtitle);
        root.addView(header.view, Ui.lp(-1, -2));

        LinearLayout bar = Ui.row(this);
        bar.setBackground(Ui.shape(this, 0xCC0A2530, 0xAA34A0A0, 1.2f, 30));
        bar.setPadding(dp(4), dp(4), dp(4), dp(4));
        addTab(bar, BASIC, R.drawable.ic_kalk_calculate, R.string.tab_basic);
        addTab(bar, SCIENTIFIC, R.drawable.ic_kalk_science, R.string.tab_scientific);
        addTab(bar, CONVERT, R.drawable.ic_kalk_sync_alt, R.string.tab_convert);
        root.addView(bar, Ui.margins(Ui.lp(-1, -2), this, 14, 0, 14, 10));

        page = new FrameLayout(this);
        root.addView(page, Ui.margins(Ui.lp(-1, 0, 1), this, 12, 0, 12, 8));

        Ui.edgeToEdge(this, root);
        setContentView(root);
        selectTab(tab);
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    // ---- Tabs ----

    private void addTab(LinearLayout bar, int which, int icon, int label) {
        LinearLayout t = Ui.row(this);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(6), dp(10), dp(6), dp(10));
        t.addView(Ui.icon(this, icon, Ui.TEXT, 22));
        TextView l = Ui.text(this, getString(label), 16, Ui.TEXT, true);
        l.setSingleLine(true);
        l.setEllipsize(TextUtils.TruncateAt.END);
        l.setPadding(dp(8), 0, 0, 0);
        t.addView(l);
        t.setOnClickListener(v -> selectTab(which));
        bar.addView(t, Ui.lp(0, -2, 1));
        tabs.add(t);
    }

    private void selectTab(int which) {
        tab = which;
        for (int k = 0; k < tabs.size(); k++) {
            LinearLayout t = tabs.get(k);
            boolean on = k == which;
            Ui.clickable(t, on ? Ui.greenGlow(this, 26) : Ui.shape(this, 0, 0, 0, 26));
            int color = on ? Ui.ON_GREEN : Ui.TEXT;
            ((ImageView) t.getChildAt(0)).setImageTintList(ColorStateList.valueOf(color));
            ((TextView) t.getChildAt(1)).setTextColor(color);
        }
        page.removeAllViews();
        exprView = null;
        resultView = null;
        angleKey = null;
        angleView = null;
        fromValue = null;
        chips.clear();
        if (which == CONVERT) buildConvert();
        else buildCalculator(which == SCIENTIFIC);
    }

    private void refresh() {
        if (tab == CONVERT) updateConvert();
        else updateCalculator();
    }

    // ---- Calculator ----

    private void buildCalculator(boolean sci) {
        LinearLayout col = Ui.column(this);

        // The display: the calculation small at the top, the result large on the right, the copy button.
        FrameLayout card = new FrameLayout(this);
        Ui.clickable(card, Ui.shape(this, 0xCC071C27, 0xAA3FA7A0, 1.5f, 26));
        card.setOnLongClickListener(v -> {
            copyResult();
            return true;
        });
        LinearLayout inner = Ui.column(this);
        inner.setPadding(dp(18), dp(8), dp(8), dp(sci ? 8 : 12));
        LinearLayout top = Ui.row(this);
        exprView = Ui.text(this, "", sci ? 18 : 21, Ui.TEXT_2, false);
        exprView.setSingleLine(true);
        exprView.setEllipsize(TextUtils.TruncateAt.START);
        exprView.setPadding(0, 0, dp(8), 0);
        top.addView(exprView, Ui.lp(0, -2, 1));
        top.addView(iconButton(R.drawable.ic_copy, getString(R.string.copy), v -> copyResult()),
                Ui.lp(dp(44), dp(44)));
        inner.addView(top, Ui.lp(-1, -2));
        resultView = new TextView(this);
        resultView.setTextColor(Ui.TEXT);
        resultView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        resultView.setMaxLines(1);
        resultView.setIncludeFontPadding(false);
        resultView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        resultView.setAutoSizeTextTypeUniformWithConfiguration(16, sci ? 46 : 64, 1, TypedValue.COMPLEX_UNIT_SP);
        resultView.setPadding(0, 0, dp(10), 0);
        inner.addView(resultView, Ui.lp(-1, dp(sci ? 54 : 80)));
        card.addView(inner, new FrameLayout.LayoutParams(-1, -2));
        if (sci) {
            angleView = Ui.text(this, "", 13, Ui.GREEN, true);
            FrameLayout.LayoutParams al = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
            al.leftMargin = dp(18);
            al.bottomMargin = dp(10);
            card.addView(angleView, al);
        }
        col.addView(card, Ui.margins(Ui.lp(-1, -2), this, 2, 0, 2, 8));

        LinearLayout pad = Ui.column(this);
        float m = sci ? 3.5f : 5;
        if (sci) {
            LinearLayout r = keyRow(pad);
            angleKey = key(r, ed.degrees ? "DEG" : "RAD", K_FN, 1, m, getString(R.string.deg_rad),
                    () -> ed.degrees = !ed.degrees);
            key(r, "sin", K_FN, 1, m, null, () -> ed.function("sin"));
            key(r, "cos", K_FN, 1, m, null, () -> ed.function("cos"));
            key(r, "tan", K_FN, 1, m, null, () -> ed.function("tan"));
            key(r, String.valueOf(Engine.PI), K_FN, 1, m, null, () -> ed.constant(Engine.PI));
            r = keyRow(pad);
            key(r, "ln", K_FN, 1, m, null, () -> ed.function("ln"));
            key(r, "log", K_FN, 1, m, null, () -> ed.function("log"));
            key(r, String.valueOf(Engine.SQRT), K_FN, 1, m, null, ed::sqrt);
            key(r, "x\u00B2", K_FN, 1, m, null, ed::square);
            key(r, "x\u02B8", K_FN, 1, m, null, () -> ed.operator('^'));
            r = keyRow(pad);
            key(r, "(", K_FN, 1, m, null, ed::open);
            key(r, ")", K_FN, 1, m, null, ed::close);
            key(r, "1/x", K_FN, 1, m, null, ed::reciprocal);
            key(r, "n!", K_FN, 1, m, null, ed::factorial);
            key(r, "e", K_FN, 1, m, null, () -> ed.constant(Engine.E));
        }
        LinearLayout r = keyRow(pad);
        key(r, "C", K_CLEAR, 1, m, getString(R.string.clear), ed::clear);
        View bs = iconKey(r, R.drawable.ic_kalk_backspace, K_NUM, 1, m, getString(R.string.backspace), ed::backspace);
        bs.setOnLongClickListener(v -> {
            ed.clear();
            refresh();
            return true;
        });
        key(r, "%", K_NUM, 1, m, null, ed::percent);
        key(r, String.valueOf(Engine.DIVIDE), K_OP, 1, m, null, () -> ed.operator(Engine.DIVIDE));
        r = keyRow(pad);
        digits(r, "789", m);
        key(r, String.valueOf(Engine.TIMES), K_OP, 1, m, null, () -> ed.operator(Engine.TIMES));
        r = keyRow(pad);
        digits(r, "456", m);
        key(r, String.valueOf(Engine.MINUS), K_OP, 1, m, null, () -> ed.operator(Engine.MINUS));
        r = keyRow(pad);
        digits(r, "123", m);
        key(r, "+", K_OP, 1, m, null, () -> ed.operator('+'));
        r = keyRow(pad);
        key(r, "\u00B1", K_NUM, 1, m, getString(R.string.plus_minus), ed::negate);
        digits(r, "0", m);
        key(r, ",", K_NUM, 1, m, null, ed::comma);
        key(r, "=", K_EQUALS, 1, m, null, this::equalsKey);
        col.addView(pad, Ui.lp(-1, 0, 1));

        page.addView(col, Ui.match());
        updateCalculator();
    }

    private void digits(LinearLayout row, String ds, float m) {
        for (char d : ds.toCharArray()) key(row, String.valueOf(d), K_NUM, 1, m, null, () -> ed.digit(d));
    }

    private void equalsKey() {
        Engine.Result r = ed.equals();
        if (r != null && r.ok() && !ed.lastExpr.isEmpty()) addHistory(ed.lastExpr, ed.expr);
    }

    private void updateCalculator() {
        if (resultView == null) return;
        String angle = ed.degrees ? "DEG" : "RAD";
        if (angleKey != null) angleKey.setText(angle);
        if (angleView != null) angleView.setText(angle);
        resultView.setTextColor(Ui.TEXT);
        if (ed.evaluated) {
            exprView.setText(ed.lastExpr.isEmpty() ? "" : Engine.display(ed.lastExpr) + " =");
            resultView.setText(Engine.display(ed.expr));
            return;
        }
        exprView.setText(Engine.display(ed.expr));
        if (ed.error != Engine.Error.NONE) {
            resultView.setTextColor(Ui.RED);
            resultView.setText(errorText(ed.error));
            return;
        }
        if (ed.expr.isEmpty()) {
            resultView.setText("0");
            return;
        }
        Engine.Result p = ed.preview();
        resultView.setText(p != null && p.ok() ? Engine.format(p.value, p.digits()) : "");
    }

    private int errorText(Engine.Error e) {
        switch (e) {
            case DIV_ZERO:
                return R.string.err_div_zero;
            case DOMAIN:
                return R.string.err_domain;
            case OVERFLOW:
                return R.string.err_overflow;
            default:
                return R.string.err_syntax;
        }
    }

    /** Copies the result (or the converted value) without the thousands spaces, with the decimal comma. */
    private void copyResult() {
        String text = null;
        if (tab == CONVERT) {
            text = convResult;
        } else if (ed.evaluated) {
            text = ed.expr;
        } else if (ed.error == Engine.Error.NONE) {
            Engine.Result p = ed.preview();
            if (p != null && p.ok()) text = Engine.toInput(p.value, p.digits());
        }
        if (text == null || text.isEmpty()) return;
        ClipboardManager cm = getSystemService(ClipboardManager.class);
        cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), Engine.plainText(text)));
    }

    // ---- Keys ----

    private LinearLayout keyRow(LinearLayout pad) {
        LinearLayout r = Ui.row(this);
        pad.addView(r, Ui.lp(-1, 0, 1));
        return r;
    }

    private TextView key(LinearLayout row, String label, int style, float weight, float margin, String description,
            Runnable action) {
        boolean sci = tab == SCIENTIFIC;
        TextView k = new TextView(this);
        k.setText(label);
        k.setGravity(Gravity.CENTER);
        k.setIncludeFontPadding(false);
        k.setSingleLine(true);
        float size;
        if (style == K_FN) size = label.length() > 2 ? 16 : 19;
        else if (style == K_OP || style == K_EQUALS) size = sci ? 28 : 36;
        else size = sci ? 24 : 31;
        k.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        k.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        k.setTextColor(keyTextColor(style));
        if (description != null) k.setContentDescription(description);
        addKey(row, k, style, weight, margin, action);
        return k;
    }

    private View iconKey(LinearLayout row, int icon, int style, float weight, float margin, String description,
            Runnable action) {
        FrameLayout k = new FrameLayout(this);
        float size = tab == SCIENTIFIC ? 26 : 30;
        k.addView(Ui.icon(this, icon, keyTextColor(style), size),
                new FrameLayout.LayoutParams(dp(size), dp(size), Gravity.CENTER));
        k.setContentDescription(description);
        addKey(row, k, style, weight, margin, action);
        return k;
    }

    private void addKey(LinearLayout row, View k, int style, float weight, float margin, Runnable action) {
        Ui.clickable(k, keyBackground(style, tab == SCIENTIFIC ? 18 : 24));
        k.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            action.run();
            refresh();
        });
        row.addView(k, Ui.margins(Ui.lp(0, -1, weight), this, margin, margin, margin, margin));
    }

    private static int keyTextColor(int style) {
        switch (style) {
            case K_OP:
                return Ui.GREEN_LIGHT;
            case K_EQUALS:
                return Ui.ON_GREEN;
            default:
                return Ui.TEXT;
        }
    }

    /** Rounded glass keys: teal numbers, green operators, bright green "=", red "C". */
    private Drawable keyBackground(int style, float radius) {
        GradientDrawable d;
        switch (style) {
            case K_EQUALS:
                return Ui.greenGlow(this, radius);
            case K_OP:
                d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xFF2F7212, 0xFF143D07});
                d.setStroke(dp(1.5f), 0xCC8EEA3A);
                break;
            case K_CLEAR:
                d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xFFC8343E, 0xFF7A111B});
                d.setStroke(dp(1.5f), 0xCCFF7B7B);
                break;
            case K_FN:
                d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xCC0F2B37, 0xCC081C26});
                d.setStroke(dp(1), Ui.GLASS_STROKE);
                break;
            default:
                d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xE6153B49, 0xE60A2330});
                d.setStroke(dp(1.2f), 0x995FB8B0);
                break;
        }
        d.setCornerRadius(dp(radius));
        return d;
    }

    private FrameLayout iconButton(int icon, String description, View.OnClickListener l) {
        FrameLayout f = new FrameLayout(this);
        Ui.clickable(f, Ui.shape(this, 0, 0, 0, 22));
        f.addView(Ui.icon(this, icon, Ui.TEXT, 24), new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        f.setContentDescription(description);
        f.setOnClickListener(l);
        return f;
    }

    // ---- Unit conversion ----

    private void buildConvert() {
        LinearLayout col = Ui.column(this);

        chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.row(this);
        for (int c = 0; c < Units.CATEGORIES; c++) {
            TextView ch = Ui.text(this, getString(CATEGORY_NAMES[c]), 15, Ui.TEXT, true);
            ch.setPadding(dp(16), dp(9), dp(16), dp(9));
            int which = c;
            ch.setOnClickListener(v -> {
                category = which;
                updateConvert();
            });
            row.addView(ch, Ui.margins(Ui.lp(-2, -2), this, 0, 0, 8, 0));
            chips.add(ch);
        }
        chipScroll.addView(row);
        col.addView(chipScroll, Ui.margins(Ui.lp(-1, -2), this, 2, 0, 2, 10));

        col.addView(unitCard(true), Ui.margins(Ui.lp(-1, -2), this, 2, 0, 2, 0));
        formula = Ui.text(this, "", 14, Ui.TEXT_3, false);
        formula.setGravity(Gravity.CENTER);
        formula.setSingleLine(true);
        formula.setEllipsize(TextUtils.TruncateAt.END);
        formula.setPadding(0, dp(6), 0, dp(6));
        col.addView(formula, Ui.lp(-1, -2));
        col.addView(unitCard(false), Ui.margins(Ui.lp(-1, -2), this, 2, 0, 2, 8));

        LinearLayout pad = Ui.column(this);
        float m = 5;
        LinearLayout r = keyRow(pad);
        convDigits(r, "789", m);
        key(r, "C", K_CLEAR, 1, m, getString(R.string.clear), () -> convInput = "");
        r = keyRow(pad);
        convDigits(r, "456", m);
        View bs = iconKey(r, R.drawable.ic_kalk_backspace, K_NUM, 1, m, getString(R.string.backspace), () -> {
            if (!convInput.isEmpty()) convInput = convInput.substring(0, convInput.length() - 1);
        });
        bs.setOnLongClickListener(v -> {
            convInput = "";
            refresh();
            return true;
        });
        r = keyRow(pad);
        convDigits(r, "123", m);
        key(r, "\u00B1", K_NUM, 1, m, getString(R.string.plus_minus), () -> convInput =
                convInput.startsWith(String.valueOf(Engine.MINUS)) ? convInput.substring(1) : Engine.MINUS + convInput);
        r = keyRow(pad);
        key(r, "0", K_NUM, 2, m, null, () -> convDigit('0'));
        key(r, ",", K_NUM, 1, m, null, () -> {
            if (convInput.indexOf(',') >= 0) return;
            if (convInput.isEmpty() || convInput.equals(String.valueOf(Engine.MINUS))) convInput += "0";
            convInput += ",";
        });
        iconKey(r, R.drawable.ic_kalk_swap_vert, K_OP, 1, m, getString(R.string.swap), () -> {
            int a = unitFrom[category];
            unitFrom[category] = unitTo[category];
            unitTo[category] = a;
        });
        col.addView(pad, Ui.lp(-1, 0, 1));

        page.addView(col, Ui.match());
        updateConvert();
        chipScroll.post(() -> {
            if (chips.isEmpty()) return;
            chipScroll.scrollTo(Math.max(0, chips.get(category).getLeft() - dp(40)), 0);
        });
    }

    private void convDigits(LinearLayout row, String ds, float m) {
        for (char d : ds.toCharArray()) key(row, String.valueOf(d), K_NUM, 1, m, null, () -> convDigit(d));
    }

    private void convDigit(char d) {
        String minus = String.valueOf(Engine.MINUS);
        if (convInput.equals("0")) convInput = "";
        else if (convInput.equals(minus + "0")) convInput = minus;
        int n = 0;
        for (char c : convInput.toCharArray()) if (c >= '0' && c <= '9') n++;
        if (n >= Engine.MAX_DIGITS) return;
        convInput += d;
    }

    /** A card with the unit picker and the value; the "from" card is the input (green edge). */
    private LinearLayout unitCard(boolean from) {
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.shape(this, 0xCC071C27, from ? 0xCC7BE41E : 0xAA3FA7A0, from ? 1.5f : 1.2f, 22));
        card.setPadding(dp(12), dp(8), dp(8), dp(6));
        LinearLayout top = Ui.row(this);
        top.setMinimumHeight(dp(44));
        LinearLayout pill = Ui.row(this);
        Ui.clickable(pill, Ui.glass(this, 18));
        pill.setPadding(dp(12), dp(6), dp(6), dp(6));
        pill.setContentDescription(getString(R.string.choose_unit));
        TextView name = Ui.text(this, "", 15, Ui.TEXT, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setMaxWidth(dp(250));
        pill.addView(name);
        pill.addView(Ui.icon(this, R.drawable.ic_kalk_expand, Ui.TEXT_2, 22));
        pill.setOnClickListener(v -> chooseUnit(v, from));
        top.addView(pill, Ui.lp(-2, -2));
        top.addView(new View(this), Ui.lp(0, 1, 1));
        if (!from) top.addView(iconButton(R.drawable.ic_copy, getString(R.string.copy), v -> copyResult()),
                Ui.lp(dp(44), dp(44)));
        card.addView(top, Ui.lp(-1, -2));
        TextView value = new TextView(this);
        value.setTextColor(from ? Ui.TEXT : Ui.GREEN_LIGHT);
        value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        value.setMaxLines(1);
        value.setIncludeFontPadding(false);
        value.setAutoSizeTextTypeUniformWithConfiguration(14, 40, 1, TypedValue.COMPLEX_UNIT_SP);
        value.setPadding(0, 0, dp(8), 0);
        card.addView(value, Ui.lp(-1, dp(50)));
        if (from) {
            fromUnit = name;
            fromValue = value;
        } else {
            toUnit = name;
            toValue = value;
            card.setOnLongClickListener(v -> {
                copyResult();
                return true;
            });
        }
        return card;
    }

    private void chooseUnit(View anchor, boolean from) {
        PopupMenu m = new PopupMenu(this, anchor);
        Menu menu = m.getMenu();
        String[] units = getResources().getStringArray(UNIT_ARRAYS[category]);
        int current = from ? unitFrom[category] : unitTo[category];
        for (int k = 0; k < Units.count(category); k++) {
            menu.add(0, k, k, unitLabel(units, k)).setCheckable(true).setChecked(k == current);
        }
        menu.setGroupCheckable(0, true, true);
        m.setOnMenuItemClickListener(item -> {
            if (from) unitFrom[category] = item.getItemId();
            else unitTo[category] = item.getItemId();
            updateConvert();
            return true;
        });
        m.show();
    }

    private void updateConvert() {
        if (fromValue == null) return;
        for (int k = 0; k < chips.size(); k++) {
            boolean on = k == category;
            TextView ch = chips.get(k);
            Ui.clickable(ch, on ? Ui.greenGlow(this, 22) : Ui.glass(this, 22));
            ch.setTextColor(on ? Ui.ON_GREEN : Ui.TEXT);
        }
        String[] units = getResources().getStringArray(UNIT_ARRAYS[category]);
        int f = unitFrom[category];
        int t = unitTo[category];
        fromUnit.setText(unitLabel(units, f));
        toUnit.setText(unitLabel(units, t));
        String in = convInput.isEmpty() ? "0" : convInput;
        fromValue.setText(withUnit(Engine.display(in), unitSymbol(units, f)));
        BigDecimal v = Engine.parseNumber(in);
        if (v == null) v = BigDecimal.ZERO;
        BigDecimal r = Units.convert(category, f, t, v);
        convResult = Engine.toInput(r, Engine.DIGITS_INEXACT);
        toValue.setText(withUnit(Engine.display(convResult), unitSymbol(units, t)));
        BigDecimal one = Units.convert(category, f, t, BigDecimal.ONE);
        formula.setText("1 " + unitSymbol(units, f) + " = " + Engine.format(one, Engine.DIGITS_INEXACT) + " "
                + unitSymbol(units, t));
    }

    /** The value large and the unit symbol smaller and grey after it. */
    private static CharSequence withUnit(String value, String symbol) {
        SpannableStringBuilder sb = new SpannableStringBuilder(value);
        int start = sb.length();
        sb.append(Engine.NBSP).append(symbol);
        sb.setSpan(new RelativeSizeSpan(0.5f), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(Ui.TEXT_2), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    /** "Meter (m)" from the resource item "Meter|m". */
    private static String unitLabel(String[] units, int k) {
        if (k >= units.length) return "?";
        String[] p = units[k].split("\\|", 2);
        return p.length < 2 ? p[0] : p[0] + " (" + p[1] + ")";
    }

    private static String unitSymbol(String[] units, int k) {
        if (k >= units.length) return "?";
        String[] p = units[k].split("\\|", 2);
        return p.length < 2 ? p[0] : p[1];
    }

    // ---- Menu and history ----

    private void showMenu(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 1, 0, R.string.history);
        m.setOnMenuItemClickListener(item -> {
            showHistory();
            return true;
        });
        m.show();
    }

    private JSONArray loadHistory() {
        try {
            return new JSONArray(prefs.getString("history", "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    /** Keeps the last 50 calculations, newest first (input form of the calculation and the result). */
    private void addHistory(String expr, String result) {
        JSONArray old = loadHistory();
        JSONArray a = new JSONArray();
        try {
            a.put(new JSONObject().put("e", expr).put("r", result));
            for (int k = 0; k < old.length() && a.length() < HISTORY_SIZE; k++) {
                JSONObject o = old.optJSONObject(k);
                if (o == null) continue;
                if (k == 0 && expr.equals(o.optString("e")) && result.equals(o.optString("r"))) continue;
                a.put(o);
            }
        } catch (JSONException e) {
            return;
        }
        prefs.edit().putString("history", a.toString()).apply();
    }

    private void showHistory() {
        JSONArray a = loadHistory();
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(R.string.history)
                .setPositiveButton(R.string.close, null);
        if (a.length() == 0) {
            b.setMessage(R.string.history_empty);
            styleDialog(b.show());
            return;
        }
        LinearLayout list = Ui.column(this);
        list.setPadding(dp(16), dp(4), dp(16), dp(8));
        list.addView(Ui.text(this, getString(R.string.history_hint), 13, Ui.TEXT_3, false),
                Ui.margins(Ui.lp(-1, -2), this, 4, 0, 4, 8));
        AlertDialog[] dialog = new AlertDialog[1];
        for (int k = 0; k < a.length(); k++) {
            JSONObject o = a.optJSONObject(k);
            if (o == null) continue;
            String e = o.optString("e");
            String r = o.optString("r");
            LinearLayout row = Ui.column(this);
            Ui.clickable(row, Ui.glass(this, 16));
            row.setPadding(dp(14), dp(8), dp(14), dp(8));
            TextView et = Ui.text(this, Engine.display(e), 15, Ui.TEXT_2, false);
            et.setSingleLine(true);
            et.setEllipsize(TextUtils.TruncateAt.START);
            et.setGravity(Gravity.END);
            row.addView(et, Ui.lp(-1, -2));
            TextView rt = Ui.text(this, "= " + Engine.display(r), 20, Ui.TEXT, true);
            rt.setSingleLine(true);
            rt.setGravity(Gravity.END);
            row.addView(rt, Ui.lp(-1, -2));
            row.setOnClickListener(v -> {
                useHistory(r, false);
                dialog[0].dismiss();
            });
            row.setOnLongClickListener(v -> {
                useHistory(e, true);
                dialog[0].dismiss();
                return true;
            });
            list.addView(row, Ui.margins(Ui.lp(-1, -2), this, 0, 0, 0, 6));
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(list);
        b.setView(sv).setNeutralButton(R.string.clear_history,
                (d, w) -> prefs.edit().remove("history").apply());
        dialog[0] = b.show();
        styleDialog(dialog[0]);
    }

    private void styleDialog(AlertDialog d) {
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(
                    new InsetDrawable(Ui.shape(this, 0xF2071C27, 0xAA3FA7A0, 1.2f, 24), dp(16)));
        }
    }

    /** A result from the history goes into the calculation; a held one replaces the whole calculation. */
    private void useHistory(String s, boolean whole) {
        if (tab == CONVERT) selectTab(BASIC);
        if (whole) ed.setExpression(s);
        else ed.insertNumber(s);
        updateCalculator();
    }

    // ---- State ----

    private void load() {
        tab = clamp(prefs.getInt("tab", BASIC), 3);
        category = clamp(prefs.getInt("category", Units.LENGTH), Units.CATEGORIES);
        for (int c = 0; c < Units.CATEGORIES; c++) {
            unitFrom[c] = clamp(prefs.getInt("from" + c, Units.DEFAULTS[c][0]), Units.count(c));
            unitTo[c] = clamp(prefs.getInt("to" + c, Units.DEFAULTS[c][1]), Units.count(c));
        }
        convInput = prefs.getString("input", "");
        try {
            JSONArray a = new JSONArray(prefs.getString("editor", "[]"));
            String[] s = new String[a.length()];
            for (int k = 0; k < s.length; k++) s[k] = a.getString(k);
            ed.restore(s);
        } catch (JSONException e) {
            // Start with an empty calculation.
        }
    }

    private void save() {
        JSONArray a = new JSONArray();
        for (String s : ed.save()) a.put(s);
        SharedPreferences.Editor e = prefs.edit().putInt("tab", tab).putInt("category", category)
                .putString("input", convInput).putString("editor", a.toString());
        for (int c = 0; c < Units.CATEGORIES; c++) e.putInt("from" + c, unitFrom[c]).putInt("to" + c, unitTo[c]);
        e.apply();
    }

    private static int clamp(int v, int count) {
        return v < 0 || v >= count ? 0 : v;
    }
}
