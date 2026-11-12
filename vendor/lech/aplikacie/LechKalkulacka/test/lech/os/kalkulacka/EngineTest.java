/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.kalkulacka;

import java.math.BigDecimal;

/**
 * Plain Java test of the calculator engine, the key handling and the unit conversion (not part of the app).
 *
 * <pre>
 * cd vendor/lech/aplikacie/LechKalkulacka
 * javac -encoding UTF-8 -d /tmp/kalk-test src/lech/os/kalkulacka/{Engine,Editor,Units}.java \
 *     test/lech/os/kalkulacka/EngineTest.java
 * java -cp /tmp/kalk-test lech.os.kalkulacka.EngineTest
 * </pre>
 */
public final class EngineTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        precedence();
        percent();
        decimals();
        errors();
        bigNumbers();
        scientific();
        keys();
        formatting();
        units();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    // ---- Expressions ----

    private static void precedence() {
        eval("2+3×4", "14");
        eval("(325+475)×2", "1 600");
        eval("10−4−3", "3");
        eval("100÷10÷2", "5");
        eval("2×3+4×5", "26");
        eval("−5+2", "−3");
        eval("5×−3", "−15");
        eval("5−−3", "8");
        eval("2^3^2", "512");
        eval("−2²", "−4");
        eval("2(3+4)", "14");
        eval("(1+2)(3+4)", "21");
        eval("2^−1", "0,5");
    }

    private static void percent() {
        eval("50+10%", "55");
        eval("50−10%", "45");
        eval("200×10%", "20");
        eval("10%", "0,1");
        eval("200÷10%", "2 000");
        eval("50+200×10%", "70");
        eval("(50+10%)×2", "110");
    }

    private static void decimals() {
        eval("0,1+0,2", "0,3");
        eval("1÷3", "0,333333333333333");
        eval("1÷3×3", "1");
        eval("2÷3", "0,666666666666667");
        eval("1,5×4", "6");
        eval("7,", "7");
        eval("0,000001×0,000001", "1E−12");
        eval("1,5E−20×2", "3E−20");
    }

    private static void errors() {
        error("5÷0", Engine.Error.DIV_ZERO);
        error("5÷(2−2)", Engine.Error.DIV_ZERO);
        error("0⁻¹", Engine.Error.DIV_ZERO);
        error("√(−4)", Engine.Error.DOMAIN);
        error("ln(0)", Engine.Error.DOMAIN);
        error("log(−1)", Engine.Error.DOMAIN);
        error("tan(90)", Engine.Error.DOMAIN);
        error("(−3)!", Engine.Error.DOMAIN);
        error("2,5!", Engine.Error.DOMAIN);
        error("(−8)^0,5", Engine.Error.DOMAIN);
        error("10^10000", Engine.Error.OVERFLOW);
        error("9^9^9", Engine.Error.OVERFLOW);
        error("5000!×5000!", Engine.Error.OVERFLOW);
        error("5+×", Engine.Error.SYNTAX);
        error("()", Engine.Error.SYNTAX);
    }

    private static void bigNumbers() {
        eval("999999999999999+1", "1E15");
        eval("123456789×1000", "123 456 789 000");
        eval("999999999999×999999999999", "9,99999999998E23");
        eval("2^100", "1,26765060022823E30");
        eval("10^−10000", "0");
        eval("20!", "2,43290200817664E18");
        eval("170!", "7,257415615308E306");
        eval("1E9999×10", null);
    }

    private static void scientific() {
        eval("sin(30)", "0,5");
        eval("cos(60)", "0,5");
        eval("tan(45)", "1");
        eval("sin(180)", "0");
        eval("cos(90)", "0");
        eval("sin(−90)", "−1");
        eval("sin(390)", "0,5");
        evalRad("sin(π)", "0");
        evalRad("cos(π)", "−1");
        evalRad("sin(π÷6)", "0,5");
        errorRad("tan(π÷2)", Engine.Error.DOMAIN);
        eval("ln(e)", "1");
        eval("ln(1)", "0");
        eval("log(1000)", "3");
        eval("log(0,01)", "−2");
        eval("√9", "3");
        eval("√2", "1,4142135623731");
        eval("√2²", "2");
        eval("√(2)²", "2");
        eval("4²", "16");
        eval("2^10", "1 024");
        eval("2^0,5", "1,41421356237");
        eval("4⁻¹", "0,25");
        eval("5!", "120");
        eval("0!", "1");
        eval("2π", "6,28318530717959");
        eval("π", "3,14159265358979");
        eval("e", "2,71828182845905");
        eval("3sin(30)", "1,5");
        eval("2√9", "6");
        eval("(−2)^3", "−8");
        eval("(−1)^1000001", "−1");
        eval("1,0001^100000", "22 015,4560485522");
    }

    // ---- Keys ----

    private static void keys() {
        Editor e = new Editor();
        type(e, "50+10%");
        check("50 + 10 % =", "55", resultOf(e));

        e = new Editor();
        type(e, "200×10%");
        check("200 × 10 % =", "20", resultOf(e));

        e = new Editor();
        type(e, "0,1+0,2");
        check("0,1 + 0,2 =", "0,3", resultOf(e));

        e = new Editor();
        type(e, "1÷3");
        e.equals();
        type(e, "×3");
        check("1 ÷ 3 = × 3 = (exact previous result)", "1", resultOf(e));

        e = new Editor();
        type(e, "5÷0");
        Engine.Result r = e.equals();
        check("5 ÷ 0 =", "DIV_ZERO", r.error.name());
        check("5 ÷ 0 keeps the expression", "5÷0", e.expr);
        check("5 ÷ 0 error flag", "DIV_ZERO", e.error.name());
        e.backspace();
        check("error cleared by ⌫", "NONE", e.error.name());

        e = new Editor();
        type(e, "12+5");
        e.equals();
        check("= moves result", "17", e.expr);
        check("= keeps the calculation", "12+5", e.lastExpr);
        type(e, "3");
        check("digit after = starts anew", "3", e.expr);

        e = new Editor();
        type(e, "12+5");
        e.equals();
        type(e, "+1");
        check("operator after = continues", "17+1", e.expr);

        e = new Editor();
        type(e, "5+×");
        check("operator replaces operator", "5×", e.expr);
        type(e, "−");
        check("minus after × is a sign", "5×−", e.expr);
        type(e, "+");
        check("operator after a sign replaces both", "5+", e.expr);

        e = new Editor();
        type(e, "5");
        e.negate();
        check("± on 5", "−5", e.expr);
        e.negate();
        check("± twice", "5", e.expr);
        type(e, "−3");
        e.negate();
        check("± on 5 − 3", "5−−3", e.expr);
        check("5 − −3 =", "8", resultOf(e));

        e = new Editor();
        type(e, "000");
        check("leading zeros", "0", e.expr);
        type(e, ",,5");
        check("one comma", "0,5", e.expr);
        e = new Editor();
        type(e, ",5");
        check("comma first", "0,5", e.expr);

        e = new Editor();
        type(e, "1234567890123456789");
        check("15 digits at most", "123456789012345", e.expr);

        e = new Editor();
        type(e, "2×(3+4");
        check("preview closes brackets", "14", previewOf(e));
        type(e, "+");
        check("preview without the last operator", "14", previewOf(e));

        e = new Editor();
        e.function("sin");
        type(e, "30");
        check("sin(30 preview", "0,5", previewOf(e));
        e.backspace();
        e.backspace();
        e.backspace();
        check("⌫ removes sin( at once", "", e.expr);

        e = new Editor();
        type(e, "5");
        e.equals();
        e.negate();
        e.square();
        check("(−5)² after =", "(−5)²", e.expr);
        check("(−5)² =", "25", resultOf(e));

        e = new Editor();
        type(e, "2");
        e.constant(Engine.PI);
        check("2π implicit ×", "2×π", e.expr);

        e = new Editor();
        e.reciprocal();
        type(e, "4");
        check("1/x without a number", "0,25", resultOf(e));

        e = new Editor();
        e.degrees = false;
        e.function("sin");
        e.constant(Engine.PI);
        check("sin(π) in RAD", "0", resultOf(e));

        e = new Editor();
        type(e, "7+8");
        e.equals();
        String[] saved = e.save();
        Editor f = new Editor();
        f.restore(saved);
        check("restored expression", "15", f.expr);
        check("restored calculation", "7+8", f.lastExpr);
        check("restored flag", "true", Boolean.toString(f.evaluated));

        e = new Editor();
        type(e, "5×");
        e.insertNumber("−3");
        check("history number after an operator", "5×−3", e.expr);
        e.insertNumber("2");
        check("history number replaces a number", "2", e.expr);
    }

    // ---- Formatting ----

    private static void formatting() {
        check("display", "(325 + 475) × 2", Engine.display("(325+475)×2"));
        check("display grouping", "1 600 + 12 345,678", Engine.display("1600+12345,678").replace('\u00A0', ' '));
        check("display sign", "5 × −3", Engine.display("5×−3"));
        check("display percent", "50 + 10 %", Engine.display("50+10%").replace('\u00A0', ' '));
        check("display exponent", "1,5E−20", Engine.display("1,5E−20"));
        check("format", "1 600", Engine.format(new BigDecimal("1600"), 15).replace('\u00A0', ' '));
        check("format negative", "−1 234 567,5", Engine.format(new BigDecimal("-1234567.5"), 15).replace('\u00A0', ' '));
        check("format tiny", "1,23E−8", Engine.format(new BigDecimal("0.0000000123"), 15));
        check("format small", "0,0000123", Engine.format(new BigDecimal("0.0000123"), 15));
        check("format rounding", "0,666666666667", Engine.format(new BigDecimal("0.6666666666666666"), 12));
        check("plain text", "-1600,5", Engine.plainText("−1600,5"));
        check("parse", "1600.5", Engine.parseNumber("1\u00A0600,5").toPlainString());
        check("parse sign", "-0.25", Engine.parseNumber("−0,25").toPlainString());
        check("parse exponent", "0.0015", Engine.parseNumber("1,5E−3").toPlainString());
        check("parse trailing comma", "7", Engine.parseNumber("7,").toPlainString());
        check("is number", "true", Boolean.toString(Engine.isNumber("−12,5")));
        check("is not number", "false", Boolean.toString(Engine.isNumber("12+5")));
    }

    // ---- Units ----

    private static void units() {
        conv(Units.LENGTH, 3, 1, "1", "100");
        conv(Units.LENGTH, 5, 1, "1", "2,54");
        conv(Units.LENGTH, 8, 4, "1", "1,609344");
        conv(Units.LENGTH, 3, 6, "1", "3,28083989501");
        conv(Units.MASS, 6, 3, "1", "0,45359237");
        conv(Units.MASS, 3, 2, "1", "100");
        conv(Units.AREA, 5, 3, "1", "10 000");
        conv(Units.AREA, 10, 5, "1", "0,40468564224");
        conv(Units.VOLUME, 6, 3, "1", "1 000");
        conv(Units.VOLUME, 9, 3, "1", "3,785411784");
        conv(Units.TEMPERATURE, 0, 1, "100", "212");
        conv(Units.TEMPERATURE, 1, 0, "212", "100");
        conv(Units.TEMPERATURE, 1, 0, "−40", "−40");
        conv(Units.TEMPERATURE, 0, 2, "0", "273,15");
        conv(Units.TEMPERATURE, 1, 0, "100", "37,7777777778");
        conv(Units.SPEED, 1, 0, "36", "10");
        conv(Units.SPEED, 0, 1, "1", "3,6");
        conv(Units.SPEED, 3, 1, "1", "1,852");
        conv(Units.TIME, 3, 2, "1,5", "90");
        conv(Units.TIME, 6, 4, "1", "365");
        conv(Units.DATA, 4, 3, "1", "1 000");
        conv(Units.DATA, 7, 1, "1", "1 048 576");
        conv(Units.DATA, 1, 0, "1", "8");
        for (int c = 0; c < Units.CATEGORIES; c++) {
            int[] d = Units.DEFAULTS[c];
            if (d[0] >= Units.count(c) || d[1] >= Units.count(c)) check("defaults " + c, "valid", "out of range");
        }
    }

    // ---- Helpers ----

    private static void eval(String expr, String expected) {
        Engine.Result r = Engine.evaluate(expr, true);
        if (expected == null) {
            check(expr, "OVERFLOW", r.ok() ? Engine.format(r.value, r.digits()) : r.error.name());
            return;
        }
        check(expr, expected, r.ok() ? Engine.format(r.value, r.digits()).replace('\u00A0', ' ') : r.error.name());
    }

    private static void evalRad(String expr, String expected) {
        Engine.Result r = Engine.evaluate(expr, false);
        check(expr + " (RAD)", expected, r.ok() ? Engine.format(r.value, r.digits()) : r.error.name());
    }

    private static void error(String expr, Engine.Error expected) {
        Engine.Result r = Engine.evaluate(expr, true);
        check(expr, expected.name(), r.ok() ? Engine.format(r.value, r.digits()) : r.error.name());
    }

    private static void errorRad(String expr, Engine.Error expected) {
        Engine.Result r = Engine.evaluate(expr, false);
        check(expr + " (RAD)", expected.name(), r.ok() ? Engine.format(r.value, r.digits()) : r.error.name());
    }

    private static void conv(int cat, int from, int to, String in, String expected) {
        BigDecimal v = Units.convert(cat, from, to, Engine.parseNumber(in));
        check("unit " + cat + ":" + from + "→" + to + " " + in, expected,
                Engine.format(v, Engine.DIGITS_INEXACT).replace('\u00A0', ' '));
    }

    /** Types keys: digits, "," and the operators + − × ÷ % ^. */
    private static void type(Editor e, String keys) {
        for (char c : keys.toCharArray()) {
            if (c >= '0' && c <= '9') e.digit(c);
            else if (c == ',') e.comma();
            else if (c == '%') e.percent();
            else if (c == '(') e.open();
            else if (c == ')') e.close();
            else e.operator(c);
        }
    }

    private static String resultOf(Editor e) {
        Engine.Result r = e.equals();
        if (r == null) return "null";
        return r.ok() ? Engine.display(e.expr).replace('\u00A0', ' ') : r.error.name();
    }

    private static String previewOf(Editor e) {
        Engine.Result r = e.preview();
        if (r == null) return "null";
        return r.ok() ? Engine.format(r.value, r.digits()).replace('\u00A0', ' ') : r.error.name();
    }

    private static void check(String what, String expected, String actual) {
        if (expected.equals(actual)) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL " + what + ": expected [" + expected + "] got [" + actual + "]");
        }
    }
}
