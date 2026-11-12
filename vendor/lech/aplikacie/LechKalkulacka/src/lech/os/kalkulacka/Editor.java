/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.kalkulacka;

import java.math.BigDecimal;

/**
 * What the keys of the calculator do to the expression (plain Java, no Android dependencies).
 * The expression is kept in the input form of {@link Engine}.
 */
public final class Editor {
    private static final char MINUS = Engine.MINUS;

    /** What the expression ends with. */
    enum End { START, NUMBER, VALUE, OPERATOR, PREFIX }

    /** The expression being typed; after "=" it holds the result. */
    public String expr = "";
    /** After "=": the calculation whose result is in {@link #expr}. */
    public String lastExpr = "";
    /** True right after "=": a digit starts a new calculation, an operator continues with the result. */
    public boolean evaluated;
    /** The error of the last "=" (shown until the next key). */
    public Engine.Error error = Engine.Error.NONE;
    public boolean degrees = true;

    /** The exact value of the previous result, which is the text of the first ansLength characters of expr. */
    BigDecimal ans;
    int ansLength;
    boolean ansInexact;

    // ---- Keys ----

    public void digit(char d) {
        start();
        if (evaluated) fresh();
        End e = end();
        if (e == End.VALUE) {
            expr += Engine.TIMES;
        } else if (e == End.NUMBER) {
            String num = currentNumber();
            if (num.equals("0")) {
                expr = expr.substring(0, expr.length() - 1);
            } else if (digitCount(num) >= Engine.MAX_DIGITS) {
                return;
            }
        }
        expr += d;
        checkAns();
    }

    public void comma() {
        start();
        if (evaluated) fresh();
        End e = end();
        if (e == End.NUMBER) {
            String num = currentNumber();
            if (num.indexOf(',') >= 0 || num.indexOf('E') >= 0) return;
            expr += ',';
        } else if (e == End.VALUE) {
            expr += Engine.TIMES + "0,";
        } else {
            expr += "0,";
        }
        checkAns();
    }

    /** + − × ÷ and ^ (xʸ). */
    public void operator(char op) {
        start();
        if (evaluated) {
            continueWithResult(op == '^');
        }
        switch (end()) {
            case START:
                if (op == MINUS) expr = String.valueOf(MINUS);
                return;
            case NUMBER:
                trimComma();
                expr += op;
                return;
            case VALUE:
                expr += op;
                return;
            case OPERATOR: {
                char last = last();
                if (op == MINUS && (last == Engine.TIMES || last == Engine.DIVIDE || last == '^')) {
                    expr += MINUS;
                } else {
                    expr = expr.substring(0, expr.length() - 1) + op;
                }
                return;
            }
            default: {
                char last = last();
                if (last == MINUS) {
                    // A sign typed after an operator: "5 × −" then "+" gives "5 +".
                    if (op == MINUS) return;
                    expr = expr.substring(0, expr.length() - 1);
                    if (end() == End.OPERATOR) expr = expr.substring(0, expr.length() - 1) + op;
                } else if (op == MINUS) {
                    expr += MINUS;
                }
            }
        }
    }

    public void percent() {
        postfix("%");
    }

    public void square() {
        postfix(String.valueOf(Engine.SQUARE));
    }

    public void factorial() {
        postfix("!");
    }

    /** 1/x: x⁻¹ after a number, otherwise "1 ÷". */
    public void reciprocal() {
        start();
        if (evaluated) continueWithResult(false);
        End e = end();
        if (e == End.NUMBER || e == End.VALUE) {
            trimComma();
            expr += Engine.INVERSE;
        } else {
            expr += "1" + Engine.DIVIDE;
        }
    }

    private void postfix(String p) {
        start();
        if (evaluated) continueWithResult(!p.equals("%"));
        End e = end();
        if (e != End.NUMBER && e != End.VALUE) return;
        if (p.equals("%") && last() == '%') return;
        trimComma();
        expr += p;
    }

    /** ±: changes the sign of the last number (or bracket, function, constant). */
    public void negate() {
        start();
        if (evaluated) {
            lastExpr = "";
            if (expr.startsWith(String.valueOf(MINUS))) {
                expr = expr.substring(1);
                if (ansLength > 0) ansLength--;
            } else {
                expr = MINUS + expr;
                if (ansLength > 0) ansLength++;
            }
            if (ans != null) ans = ans.negate();
            return;
        }
        End e = end();
        if (e == End.START) {
            expr = String.valueOf(MINUS);
        } else if (e == End.OPERATOR) {
            expr += MINUS;
        } else if (e == End.PREFIX) {
            if (last() == MINUS) expr = expr.substring(0, expr.length() - 1);
            else expr += MINUS;
        } else {
            int s = operandStart();
            if (s <= ansLength) ansLength = 0;
            if (s > 0 && expr.charAt(s - 1) == MINUS && Engine.isUnary(expr, s - 1)) {
                expr = expr.substring(0, s - 1) + expr.substring(s);
            } else {
                expr = expr.substring(0, s) + MINUS + expr.substring(s);
            }
        }
    }

    public void open() {
        start();
        if (evaluated) continueWithResult(false);
        End e = end();
        if (e == End.NUMBER || e == End.VALUE) {
            trimComma();
            expr += Engine.TIMES;
        }
        expr += '(';
    }

    public void close() {
        start();
        if (evaluated) return;
        End e = end();
        if ((e != End.NUMBER && e != End.VALUE) || openBrackets() <= 0) return;
        trimComma();
        expr += ')';
    }

    /** sin cos tan ln log: inserts "sin(". */
    public void function(String name) {
        insertPrefix(name + "(");
    }

    public void sqrt() {
        insertPrefix(String.valueOf(Engine.SQRT));
    }

    /** π and e. */
    public void constant(char c) {
        insertPrefix(String.valueOf(c));
    }

    private void insertPrefix(String s) {
        start();
        if (evaluated) fresh();
        End e = end();
        if (e == End.NUMBER || e == End.VALUE) {
            trimComma();
            expr += Engine.TIMES;
        }
        expr += s;
    }

    public void backspace() {
        start();
        if (evaluated) {
            evaluated = false;
            lastExpr = "";
        }
        if (expr.isEmpty()) return;
        int cut = 1;
        for (String f : Engine.FUNCTIONS) {
            if (expr.endsWith(f + "(")) cut = f.length() + 1;
        }
        if (expr.endsWith(Engine.INVERSE)) cut = Engine.INVERSE.length();
        expr = expr.substring(0, expr.length() - cut);
        // Never leave a bare exponent mark ("1,5E−").
        if (expr.endsWith("E" + MINUS)) expr = expr.substring(0, expr.length() - 2);
        else if (expr.endsWith("E")) expr = expr.substring(0, expr.length() - 1);
        checkAns();
    }

    public void clear() {
        fresh();
        error = Engine.Error.NONE;
    }

    /** Puts a number (e.g. from the history) into the calculation: appended after an operator, otherwise new. */
    public void insertNumber(String number) {
        start();
        End e = end();
        if (evaluated || e == End.START || e == End.NUMBER || e == End.VALUE) {
            fresh();
        }
        expr += number;
    }

    /** Replaces the calculation (e.g. from the history). */
    public void setExpression(String s) {
        fresh();
        expr = s;
    }

    // ---- Results ----

    /** The live result while typing: incomplete endings are left out, open brackets closed; null if none. */
    public Engine.Result preview() {
        String p = prepared();
        if (p.isEmpty()) return null;
        return Engine.evaluate(p, degrees, ans, ansLength, ansInexact);
    }

    /**
     * "=": on success the result moves into the expression and the calculation into {@link #lastExpr};
     * on an error the expression stays and {@link #error} is set. Returns null when there is nothing to compute.
     */
    public Engine.Result equals() {
        if (evaluated) return null;
        String p = prepared();
        if (p.isEmpty()) return null;
        Engine.Result r = Engine.evaluate(p, degrees, ans, ansLength, ansInexact);
        if (!r.ok()) {
            error = r.error;
            return r;
        }
        lastExpr = Engine.isNumber(p) ? "" : p;
        expr = Engine.toInput(r.value, r.digits());
        ans = r.value;
        ansLength = expr.length();
        ansInexact = r.inexact;
        evaluated = true;
        error = Engine.Error.NONE;
        return r;
    }

    /** The expression without an unfinished end ("5 +", "sin(") and with the open brackets closed. */
    public String prepared() {
        String s = expr;
        boolean changed = true;
        while (changed && !s.isEmpty()) {
            changed = false;
            for (String f : Engine.FUNCTIONS) {
                if (s.endsWith(f + "(")) {
                    s = s.substring(0, s.length() - f.length() - 1);
                    changed = true;
                }
            }
            if (s.isEmpty()) break;
            char c = s.charAt(s.length() - 1);
            if (c == '+' || c == MINUS || c == Engine.TIMES || c == Engine.DIVIDE || c == '^' || c == '('
                    || c == Engine.SQRT || c == ',') {
                s = s.substring(0, s.length() - 1);
                changed = true;
            }
        }
        StringBuilder sb = new StringBuilder(s);
        for (int k = brackets(s); k > 0; k--) sb.append(')');
        return sb.toString();
    }

    // ---- State ----

    /** The state as text for saving (expression, last calculation, flags, exact previous result). */
    public String[] save() {
        return new String[] {expr, lastExpr, Boolean.toString(evaluated), Boolean.toString(degrees),
            ans == null ? "" : ans.toString(), Integer.toString(ansLength), Boolean.toString(ansInexact)};
    }

    public void restore(String[] s) {
        if (s == null || s.length < 7) return;
        try {
            expr = s[0];
            lastExpr = s[1];
            evaluated = Boolean.parseBoolean(s[2]);
            degrees = Boolean.parseBoolean(s[3]);
            ans = s[4].isEmpty() ? null : new BigDecimal(s[4]);
            ansLength = Integer.parseInt(s[5]);
            ansInexact = Boolean.parseBoolean(s[6]);
            if (ansLength > expr.length()) ansLength = 0;
        } catch (RuntimeException e) {
            fresh();
        }
    }

    // ---- Helpers ----

    private void start() {
        error = Engine.Error.NONE;
    }

    private void fresh() {
        expr = "";
        lastExpr = "";
        evaluated = false;
        ans = null;
        ansLength = 0;
        ansInexact = false;
    }

    /** An operator after "=": the calculation goes on with the result (a negative one in brackets for ^ ² !). */
    private void continueWithResult(boolean bracketNegative) {
        evaluated = false;
        lastExpr = "";
        if (bracketNegative && expr.startsWith(String.valueOf(MINUS)) && ansLength == expr.length()) {
            expr = "(" + expr + ")";
            ansLength = expr.length();
        }
    }

    /** The exact previous result only stands for the start of the expression while that text is unchanged. */
    private void checkAns() {
        if (ansLength == 0) return;
        if (ansLength > expr.length()) {
            ansLength = 0;
        } else if (ansLength < expr.length()) {
            char c = expr.charAt(ansLength);
            if ((c >= '0' && c <= '9') || c == ',' || c == 'E') ansLength = 0;
        }
        if (ansLength == 0) ans = null;
    }

    End end() {
        if (expr.isEmpty()) return End.START;
        char c = last();
        if ((c >= '0' && c <= '9') || c == ',') return End.NUMBER;
        if (c == ')' || c == Engine.PI || c == Engine.E || c == '%' || c == Engine.SQUARE || c == '!'
                || expr.endsWith(Engine.INVERSE)) {
            return End.VALUE;
        }
        if (c == MINUS) return Engine.isUnary(expr, expr.length() - 1) ? End.PREFIX : End.OPERATOR;
        if (c == '+' || c == Engine.TIMES || c == Engine.DIVIDE || c == '^') return End.OPERATOR;
        return End.PREFIX;
    }

    private char last() {
        return expr.charAt(expr.length() - 1);
    }

    private void trimComma() {
        if (expr.endsWith(",")) expr = expr.substring(0, expr.length() - 1);
    }

    /** The digits and comma at the end of the expression (with an exponent: the whole "1,5E−20"). */
    private String currentNumber() {
        int i = expr.length();
        while (i > 0 && isNumberChar(expr.charAt(i - 1))) i--;
        if (i >= 2 && expr.charAt(i - 1) == MINUS && expr.charAt(i - 2) == 'E') {
            i -= 2;
            while (i > 0 && isNumberChar(expr.charAt(i - 1))) i--;
        } else if (i >= 1 && expr.charAt(i - 1) == 'E') {
            i--;
            while (i > 0 && isNumberChar(expr.charAt(i - 1))) i--;
        }
        return expr.substring(i);
    }

    private static int digitCount(String num) {
        int e = num.indexOf('E');
        String m = e >= 0 ? num.substring(e + 1) : num;
        int n = 0;
        for (int k = 0; k < m.length(); k++) {
            if (m.charAt(k) >= '0' && m.charAt(k) <= '9') n++;
        }
        // The exponent of a result has at most 4 digits.
        return e >= 0 ? (n >= 4 ? Engine.MAX_DIGITS : 0) : n;
    }

    private static boolean isNumberChar(char c) {
        return (c >= '0' && c <= '9') || c == ',';
    }

    /** Where the last operand starts: a number, constant or bracket, with its postfix operators and √. */
    int operandStart() {
        int i = expr.length();
        while (i > 0) {
            char c = expr.charAt(i - 1);
            if (c == '%' || c == Engine.SQUARE || c == '!') {
                i--;
            } else if (expr.startsWith(Engine.INVERSE, i - Engine.INVERSE.length()) && i >= Engine.INVERSE.length()) {
                i -= Engine.INVERSE.length();
            } else {
                break;
            }
        }
        if (i == 0) return 0;
        char c = expr.charAt(i - 1);
        if (c == ')') {
            int depth = 0;
            int j = i - 1;
            for (; j >= 0; j--) {
                char k = expr.charAt(j);
                if (k == ')') {
                    depth++;
                } else if (k == '(') {
                    depth--;
                    if (depth == 0) break;
                }
            }
            if (j < 0) return 0;
            i = j;
            while (i > 0 && Character.isLetter(expr.charAt(i - 1)) && expr.charAt(i - 1) != Engine.E
                    && expr.charAt(i - 1) != 'E' && expr.charAt(i - 1) != Engine.PI) {
                i--;
            }
        } else if (c == Engine.PI || c == Engine.E) {
            i--;
        } else {
            i -= currentNumberBefore(i).length();
        }
        while (i > 0 && expr.charAt(i - 1) == Engine.SQRT) i--;
        return i;
    }

    private String currentNumberBefore(int end) {
        String saved = expr;
        expr = expr.substring(0, end);
        String n = currentNumber();
        expr = saved;
        return n;
    }

    private int openBrackets() {
        return brackets(expr);
    }

    private static int brackets(String s) {
        int open = 0;
        for (int k = 0; k < s.length(); k++) {
            if (s.charAt(k) == '(') open++;
            else if (s.charAt(k) == ')') open--;
        }
        return open;
    }
}
