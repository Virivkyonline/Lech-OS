/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.kalkulacka;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * The calculation engine of the calculator (plain Java, no Android dependencies).
 *
 * <p>An expression is kept in its "input form": digits, the decimal comma, the operators + − × ÷ ^, the postfix
 * operators % ² ⁻¹ !, parentheses, √, the constants π and e, the functions sin cos tan ln log (always followed by
 * "(") and the exponent of a number written as "E" (results such as 1,5E−20). The basic operations are exact
 * ({@link BigDecimal} with 34 digits, so 0,1 + 0,2 = 0,3); the scientific functions are computed with doubles.
 */
public final class Engine {
    public enum Error { NONE, SYNTAX, DIV_ZERO, DOMAIN, OVERFLOW }

    /** The value of an expression, or the reason why there is none. */
    public static final class Result {
        public final BigDecimal value;
        public final Error error;
        /** True when a double based function took part (the value is then shown with fewer digits). */
        public final boolean inexact;

        Result(BigDecimal value, Error error, boolean inexact) {
            this.value = value;
            this.error = error;
            this.inexact = inexact;
        }

        public boolean ok() {
            return error == Error.NONE;
        }

        /** Significant digits to show: 15 for exact results, 12 when doubles took part. */
        public int digits() {
            return inexact ? DIGITS_INEXACT : DIGITS_EXACT;
        }
    }

    public static final char MINUS = '\u2212';
    public static final char TIMES = '\u00D7';
    public static final char DIVIDE = '\u00F7';
    public static final char PI = '\u03C0';
    public static final char E = 'e';
    public static final char SQRT = '\u221A';
    public static final char SQUARE = '\u00B2';
    public static final String INVERSE = "\u207B\u00B9";
    public static final char NBSP = '\u00A0';
    public static final String[] FUNCTIONS = {"sin", "cos", "tan", "ln", "log"};

    public static final int DIGITS_EXACT = 15;
    public static final int DIGITS_INEXACT = 12;
    /** The longest number that can be typed. */
    public static final int MAX_DIGITS = 15;

    static final MathContext MC = MathContext.DECIMAL128;
    private static final MathContext MC_DOUBLE = new MathContext(15, RoundingMode.HALF_EVEN);
    private static final int MAX_EXPONENT = 9999;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal NINETY = BigDecimal.valueOf(90);
    private static final BigDecimal FULL_TURN = BigDecimal.valueOf(360);
    private static final BigDecimal MAX_FACTORIAL = BigDecimal.valueOf(5000);
    static final BigDecimal PI_VALUE = new BigDecimal("3.141592653589793238462643383279503");
    static final BigDecimal E_VALUE = new BigDecimal("2.718281828459045235360287471352662");
    /** Stands for the previous result inside an expression being evaluated (keeps its full precision). */
    private static final char ANS = '\uE000';

    private Engine() {
    }

    // ---- Evaluation ----

    public static Result evaluate(String expr, boolean degrees) {
        return evaluate(expr, degrees, null, 0, false);
    }

    /**
     * Evaluates an expression. When {@code ans} is given, the first {@code ansLength} characters of the expression
     * are the rounded text of the previous result and its exact value {@code ans} is used instead.
     */
    public static Result evaluate(String expr, boolean degrees, BigDecimal ans, int ansLength, boolean ansInexact) {
        boolean useAns = ans != null && ansLength > 0 && ansLength <= expr.length();
        String s = useAns ? ANS + expr.substring(ansLength) : expr;
        try {
            Parser p = new Parser(tokenize(s, ans), degrees);
            if (p.toks.isEmpty()) throw new CalcException(Error.SYNTAX);
            Val v = p.expr();
            if (p.pos != p.toks.size()) throw new CalcException(Error.SYNTAX);
            BigDecimal r = v.v.round(MC);
            r = r.signum() == 0 ? BigDecimal.ZERO : r.stripTrailingZeros();
            return new Result(r, Error.NONE, p.inexact || (useAns && ansInexact));
        } catch (CalcException e) {
            return new Result(null, e.error, false);
        } catch (ArithmeticException | NumberFormatException e) {
            return new Result(null, Error.OVERFLOW, false);
        }
    }

    private static final class CalcException extends Exception {
        private static final long serialVersionUID = 1L;
        final Error error;

        CalcException(Error error) {
            super(error.name(), null, false, false);
            this.error = error;
        }
    }

    private static final int NUM = 0;
    private static final int OP = 1;
    private static final int POST = 2;
    private static final int LP = 3;
    private static final int RP = 4;
    private static final int FN = 5;
    private static final int ROOT = 6;

    private static final class Tok {
        final int type;
        final char op;
        final BigDecimal value;
        final String fn;

        Tok(int type, char op, BigDecimal value, String fn) {
            this.type = type;
            this.op = op;
            this.value = value;
            this.fn = fn;
        }
    }

    private static List<Tok> tokenize(String s, BigDecimal ans) throws CalcException {
        List<Tok> out = new ArrayList<>();
        int n = s.length();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (c == ' ' || c == NBSP) {
                i++;
            } else if (isDigit(c) || c == ',' || c == '.') {
                int j = i;
                while (j < n && (isDigit(s.charAt(j)) || s.charAt(j) == ',' || s.charAt(j) == '.')) j++;
                String num = s.substring(i, j).replace(',', '.');
                if (num.indexOf('.') != num.lastIndexOf('.')) throw new CalcException(Error.SYNTAX);
                if (num.endsWith(".")) num = num.substring(0, num.length() - 1);
                if (num.isEmpty()) throw new CalcException(Error.SYNTAX);
                if (j < n && s.charAt(j) == 'E') {
                    int k = j + 1;
                    StringBuilder ex = new StringBuilder();
                    if (k < n && (s.charAt(k) == MINUS || s.charAt(k) == '-')) {
                        ex.append('-');
                        k++;
                    } else if (k < n && s.charAt(k) == '+') {
                        k++;
                    }
                    int digits = k;
                    while (k < n && isDigit(s.charAt(k))) ex.append(s.charAt(k++));
                    if (k == digits || k - digits > 6) throw new CalcException(Error.SYNTAX);
                    num = num + "E" + ex;
                    j = k;
                }
                out.add(new Tok(NUM, 'n', check(new BigDecimal(num)), null));
                i = j;
            } else if (c == ANS) {
                out.add(new Tok(NUM, 'n', ans, null));
                i++;
            } else if (c == PI) {
                out.add(new Tok(NUM, 'n', PI_VALUE, null));
                i++;
            } else if (c == E) {
                out.add(new Tok(NUM, 'n', E_VALUE, null));
                i++;
            } else if (c == '+' || c == MINUS || c == '-' || c == TIMES || c == '*' || c == DIVIDE || c == '/'
                    || c == '^') {
                char op = c == '-' ? MINUS : c == '*' ? TIMES : c == '/' ? DIVIDE : c;
                out.add(new Tok(OP, op, null, null));
                i++;
            } else if (c == '%' || c == SQUARE || c == '!') {
                out.add(new Tok(POST, c, null, null));
                i++;
            } else if (s.startsWith(INVERSE, i)) {
                out.add(new Tok(POST, 'r', null, null));
                i += INVERSE.length();
            } else if (c == '(') {
                out.add(new Tok(LP, c, null, null));
                i++;
            } else if (c == ')') {
                out.add(new Tok(RP, c, null, null));
                i++;
            } else if (c == SQRT) {
                out.add(new Tok(ROOT, c, null, null));
                i++;
            } else {
                String fn = null;
                for (String f : FUNCTIONS) {
                    if (s.startsWith(f, i)) {
                        fn = f;
                        break;
                    }
                }
                if (fn == null) throw new CalcException(Error.SYNTAX);
                out.add(new Tok(FN, 'f', null, fn));
                i += fn.length();
            }
        }
        return out;
    }

    /** A value and whether it is a bare percentage ("10 %"), which + and − take from the left operand. */
    private static final class Val {
        final BigDecimal v;
        final boolean percent;

        Val(BigDecimal v, boolean percent) {
            this.v = v;
            this.percent = percent;
        }
    }

    /** Recursive descent: expr = term {+|− term}; term = unary {×|÷|implicit unary}; unary = −unary | √unary | power. */
    private static final class Parser {
        final List<Tok> toks;
        final boolean degrees;
        int pos;
        boolean inexact;

        Parser(List<Tok> toks, boolean degrees) {
            this.toks = toks;
            this.degrees = degrees;
        }

        Tok peek() {
            return pos < toks.size() ? toks.get(pos) : null;
        }

        boolean peekOp(char op) {
            Tok t = peek();
            return t != null && t.type == OP && t.op == op;
        }

        Val expr() throws CalcException {
            Val left = term();
            while (peekOp('+') || peekOp(MINUS)) {
                char op = toks.get(pos++).op;
                Val right = term();
                // Phone calculator percent: 50 + 10 % = 55, 50 − 10 % = 45.
                BigDecimal r = right.percent ? left.v.multiply(right.v, MC) : right.v;
                left = new Val(check(op == '+' ? left.v.add(r, MC) : left.v.subtract(r, MC)), false);
            }
            return left;
        }

        Val term() throws CalcException {
            Val left = unary();
            while (true) {
                Tok t = peek();
                if (t == null) break;
                if (t.type == OP && (t.op == TIMES || t.op == DIVIDE)) {
                    pos++;
                    Val right = unary();
                    left = new Val(t.op == TIMES ? multiply(left.v, right.v) : divide(left.v, right.v), false);
                } else if (t.type == NUM || t.type == LP || t.type == FN || t.type == ROOT) {
                    // Implicit multiplication: 2π, 2(3 + 4), 3 sin(30).
                    Val right = unary();
                    left = new Val(multiply(left.v, right.v), false);
                } else {
                    break;
                }
            }
            return left;
        }

        Val unary() throws CalcException {
            Tok t = peek();
            if (t != null && t.type == OP && (t.op == MINUS || t.op == '+')) {
                pos++;
                Val v = unary();
                return t.op == MINUS ? new Val(v.v.negate(), v.percent) : v;
            }
            if (t != null && t.type == ROOT) {
                pos++;
                Val v = unary();
                return new Val(sqrt(v.v), false);
            }
            return power();
        }

        Val power() throws CalcException {
            Val base = postfix();
            if (peekOp('^')) {
                pos++;
                Val exponent = unary();
                return new Val(power(base.v, exponent.v), false);
            }
            return base;
        }

        Val postfix() throws CalcException {
            Val v = primary();
            while (peek() != null && peek().type == POST) {
                char op = toks.get(pos++).op;
                if (op == '%') {
                    v = new Val(v.v.divide(HUNDRED), true);
                } else if (op == SQUARE) {
                    v = new Val(multiply(v.v, v.v), false);
                } else if (op == 'r') {
                    v = new Val(divide(BigDecimal.ONE, v.v), false);
                } else {
                    v = new Val(factorial(v.v), false);
                }
            }
            return v;
        }

        Val primary() throws CalcException {
            Tok t = peek();
            if (t == null) throw new CalcException(Error.SYNTAX);
            pos++;
            if (t.type == NUM) return new Val(t.value, false);
            if (t.type == LP) {
                Val v = expr();
                expect(RP);
                return new Val(v.v, false);
            }
            if (t.type == FN) {
                expect(LP);
                Val a = expr();
                expect(RP);
                return new Val(function(t.fn, a.v), false);
            }
            throw new CalcException(Error.SYNTAX);
        }

        void expect(int type) throws CalcException {
            Tok t = peek();
            if (t == null || t.type != type) throw new CalcException(Error.SYNTAX);
            pos++;
        }

        BigDecimal function(String fn, BigDecimal x) throws CalcException {
            inexact = true;
            switch (fn) {
                case "sin":
                case "cos":
                case "tan":
                    return trig(fn, x);
                case "ln":
                    if (x.signum() <= 0) throw new CalcException(Error.DOMAIN);
                    if (Math.abs(exponent(x)) < 300) return fromDouble(Math.log(x.doubleValue()));
                    return fromDouble(log10(x) * Math.log(10));
                default:
                    if (x.signum() <= 0) throw new CalcException(Error.DOMAIN);
                    return fromDouble(log10(x));
            }
        }

        /** sin, cos and tan; exact at multiples of 90° (π/2), so sin 180° = 0 and tan 90° is undefined. */
        BigDecimal trig(String fn, BigDecimal x) throws CalcException {
            double rad;
            int quadrant = -1;
            if (degrees) {
                BigDecimal r = x.remainder(FULL_TURN);
                if (r.remainder(NINETY).signum() == 0) {
                    quadrant = Math.floorMod(r.divide(NINETY).intValue(), 4);
                }
                rad = Math.toRadians(r.doubleValue());
            } else {
                rad = x.doubleValue();
                if (Double.isInfinite(rad)) throw new CalcException(Error.OVERFLOW);
                double q = rad / (Math.PI / 2);
                double nearest = Math.rint(q);
                if (Math.abs(nearest) < 1e9 && Math.abs(q - nearest) < 1e-12) {
                    quadrant = (int) Math.floorMod((long) nearest, 4L);
                }
            }
            if (quadrant >= 0) {
                int sin = quadrant == 1 ? 1 : quadrant == 3 ? -1 : 0;
                int cos = quadrant == 0 ? 1 : quadrant == 2 ? -1 : 0;
                if (fn.equals("sin")) return BigDecimal.valueOf(sin);
                if (fn.equals("cos")) return BigDecimal.valueOf(cos);
                if (cos == 0) throw new CalcException(Error.DOMAIN);
                return BigDecimal.ZERO;
            }
            double v = fn.equals("sin") ? Math.sin(rad) : fn.equals("cos") ? Math.cos(rad) : Math.tan(rad);
            return fromDouble(v);
        }

        BigDecimal power(BigDecimal x, BigDecimal y) throws CalcException {
            if (y.signum() == 0) return BigDecimal.ONE;
            if (x.signum() == 0) {
                if (y.signum() < 0) throw new CalcException(Error.DIV_ZERO);
                return BigDecimal.ZERO;
            }
            boolean integer = isInteger(y);
            if (!integer && x.signum() < 0) throw new CalcException(Error.DOMAIN);
            if (x.abs().compareTo(BigDecimal.ONE) == 0) {
                return x.signum() < 0 && y.toBigInteger().testBit(0) ? BigDecimal.ONE.negate() : BigDecimal.ONE;
            }
            double magnitude = log10(x.abs()) * y.doubleValue();
            if (Double.isNaN(magnitude) || magnitude > MAX_EXPONENT) throw new CalcException(Error.OVERFLOW);
            if (magnitude < -MAX_EXPONENT) return BigDecimal.ZERO;
            if (integer && y.abs().compareTo(BigDecimal.valueOf(999999)) <= 0) {
                return check(x.pow(y.intValueExact(), MC));
            }
            inexact = true;
            boolean negative = x.signum() < 0 && y.toBigInteger().testBit(0);
            BigDecimal r;
            if (Math.abs(magnitude) < 300) {
                r = fromDouble(Math.pow(x.abs().doubleValue(), y.doubleValue()));
            } else {
                // Beyond the range of doubles: 10^magnitude = mantissa × 10^exponent.
                int exponent = (int) Math.floor(magnitude);
                r = fromDouble(Math.pow(10, magnitude - exponent)).scaleByPowerOfTen(exponent);
            }
            return check(negative ? r.negate() : r);
        }
    }

    private static BigDecimal multiply(BigDecimal a, BigDecimal b) throws CalcException {
        if (a.signum() != 0 && b.signum() != 0 && exponent(a) + exponent(b) > MAX_EXPONENT + 1) {
            throw new CalcException(Error.OVERFLOW);
        }
        return check(a.multiply(b, MC));
    }

    private static BigDecimal divide(BigDecimal a, BigDecimal b) throws CalcException {
        if (b.signum() == 0) throw new CalcException(Error.DIV_ZERO);
        if (a.signum() != 0 && exponent(a) - exponent(b) > MAX_EXPONENT + 1) throw new CalcException(Error.OVERFLOW);
        return check(a.divide(b, MC));
    }

    private static BigDecimal sqrt(BigDecimal x) throws CalcException {
        if (x.signum() < 0) throw new CalcException(Error.DOMAIN);
        if (x.signum() == 0) return BigDecimal.ZERO;
        return check(x.sqrt(MC));
    }

    private static BigDecimal factorial(BigDecimal x) throws CalcException {
        if (x.signum() < 0 || !isInteger(x)) throw new CalcException(Error.DOMAIN);
        if (x.compareTo(MAX_FACTORIAL) > 0) throw new CalcException(Error.OVERFLOW);
        int n = x.intValueExact();
        BigDecimal r = BigDecimal.ONE;
        for (int k = 2; k <= n; k++) r = check(r.multiply(BigDecimal.valueOf(k), MC));
        return r;
    }

    private static boolean isInteger(BigDecimal x) {
        return x.signum() == 0 || x.stripTrailingZeros().scale() <= 0;
    }

    /** The decimal exponent of the leading digit (123 → 2, 0,05 → −2). */
    private static int exponent(BigDecimal x) {
        return x.precision() - x.scale() - 1;
    }

    /** Numbers beyond ±10^9999 are refused; tiny ones become 0. */
    private static BigDecimal check(BigDecimal x) throws CalcException {
        if (x.signum() == 0) return x;
        int e = exponent(x);
        if (e > MAX_EXPONENT) throw new CalcException(Error.OVERFLOW);
        if (e < -MAX_EXPONENT) return BigDecimal.ZERO;
        return x;
    }

    private static double log10(BigDecimal x) {
        int e = exponent(x);
        if (Math.abs(e) < 300) return Math.log10(x.doubleValue());
        return Math.log10(x.movePointLeft(e).doubleValue()) + e;
    }

    /** A double result rounded to 15 digits, which also removes the binary noise (sin 30° = 0,5). */
    private static BigDecimal fromDouble(double d) throws CalcException {
        if (Double.isNaN(d)) throw new CalcException(Error.DOMAIN);
        if (Double.isInfinite(d)) throw new CalcException(Error.OVERFLOW);
        return check(new BigDecimal(d, MC_DOUBLE));
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    // ---- Text ----

    /**
     * A number in input form, rounded to {@code digits} significant digits: "1600", "0,3", "−5", "1,5E−20".
     * Numbers of 10^15 and more or below 10^−7 use the exponent form.
     */
    public static String toInput(BigDecimal v, int digits) {
        if (v.signum() == 0) return "0";
        BigDecimal r = v.round(new MathContext(digits, RoundingMode.HALF_UP)).stripTrailingZeros();
        int e = exponent(r);
        if (e >= 15 || e < -7) {
            BigDecimal m = r.movePointLeft(e).stripTrailingZeros();
            return plain(m) + "E" + (e < 0 ? MINUS + Integer.toString(-e) : Integer.toString(e));
        }
        return plain(r);
    }

    private static String plain(BigDecimal b) {
        return b.toPlainString().replace('.', ',').replace('-', MINUS);
    }

    /** A number for the screen: grouped thousands, decimal comma ("1 600", "0,3"). */
    public static String format(BigDecimal v, int digits) {
        return display(toInput(v, digits));
    }

    /** The text to copy: the result without the thousands spaces and with an ASCII minus ("-1600,5"). */
    public static String plainText(String input) {
        return input.replace(MINUS, '-');
    }

    /** True when the input is just a number ("−12,5", "1,5E−20"), not a calculation. */
    public static boolean isNumber(String input) {
        return input.matches("\u2212?[0-9]*,?[0-9]*(E\u2212?[0-9]+)?") && input.matches(".*[0-9].*");
    }

    /** Parses a number in input form ("−1 600,5", "1,5E−3", "7,"); null if it is not one. */
    public static BigDecimal parseNumber(String input) {
        String s = input.replace(String.valueOf(NBSP), "").replace(" ", "").replace(MINUS, '-').replace(',', '.');
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        if (s.isEmpty() || s.equals("-")) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The expression for the screen: spaces around + − × ÷, grouped thousands ("(325 + 475) × 2", "10 %"). */
    public static String display(String s) {
        StringBuilder sb = new StringBuilder();
        int n = s.length();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (isDigit(c) || c == ',') {
                int j = i;
                while (j < n && isDigit(s.charAt(j))) j++;
                sb.append(group(s.substring(i, j)));
                i = j;
                if (i < n && s.charAt(i) == ',') {
                    j = i + 1;
                    while (j < n && isDigit(s.charAt(j))) j++;
                    sb.append(s, i, j);
                    i = j;
                }
                if (i < n && s.charAt(i) == 'E') {
                    j = i + 1;
                    if (j < n && s.charAt(j) == MINUS) j++;
                    while (j < n && isDigit(s.charAt(j))) j++;
                    sb.append(s, i, j);
                    i = j;
                }
                continue;
            }
            if (c == '+' || c == TIMES || c == DIVIDE || (c == MINUS && !isUnary(s, i))) {
                sb.append(' ').append(c).append(' ');
            } else if (c == '%') {
                sb.append(NBSP).append('%');
            } else {
                sb.append(c);
            }
            i++;
        }
        return sb.toString().trim();
    }

    /** True when the minus at {@code i} is a sign (at the start, after an operator or an opening bracket). */
    public static boolean isUnary(String s, int i) {
        if (i == 0) return true;
        char p = s.charAt(i - 1);
        return p == '+' || p == MINUS || p == TIMES || p == DIVIDE || p == '^' || p == '(' || p == SQRT;
    }

    private static String group(String digits) {
        if (digits.length() < 4) return digits;
        StringBuilder sb = new StringBuilder();
        int first = digits.length() % 3;
        if (first > 0) sb.append(digits, 0, first);
        for (int k = first; k < digits.length(); k += 3) {
            if (sb.length() > 0) sb.append(NBSP);
            sb.append(digits, k, k + 3);
        }
        return sb.toString();
    }
}
