/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.kalkulacka;

import java.math.BigDecimal;

/**
 * Offline unit conversion (plain Java, no Android dependencies). The unit names and symbols are in the resources
 * (string arrays "name|symbol" in the same order as the factors here).
 */
public final class Units {
    public static final int LENGTH = 0;
    public static final int MASS = 1;
    public static final int AREA = 2;
    public static final int VOLUME = 3;
    public static final int TEMPERATURE = 4;
    public static final int SPEED = 5;
    public static final int TIME = 6;
    public static final int DATA = 7;
    public static final int CATEGORIES = 8;

    /**
     * Size of each unit in the base unit of its category (m, kg, m², l, –, m/s, s, B), exact by definition,
     * written as "number" or "numerator/denominator".
     */
    private static final String[][] FACTORS = {
        // mm, cm, dm, m, km, in, ft, yd, mi, nmi
        {"0.001", "0.01", "0.1", "1", "1000", "0.0254", "0.3048", "0.9144", "1609.344", "1852"},
        // mg, g, dag, kg, t, oz, lb
        {"0.000001", "0.001", "0.01", "1", "1000", "0.028349523125", "0.45359237"},
        // mm², cm², dm², m², a, ha, km², in², ft², yd², ac, mi²
        {"0.000001", "0.0001", "0.01", "1", "100", "10000", "1000000", "0.00064516", "0.09290304", "0.83612736",
            "4046.8564224", "2589988.110336"},
        // ml, cl, dl, l, hl, cm³, m³, US fl oz, US pt, US gal, UK gal
        {"0.001", "0.01", "0.1", "1", "100", "0.001", "1000", "0.0295735295625", "0.473176473", "3.785411784",
            "4.54609"},
        // °C, °F, K (converted by formula)
        {"1", "1", "1"},
        // m/s, km/h, mph, kn, ft/s
        {"1", "1000/3600", "0.44704", "1852/3600", "0.3048"},
        // ms, s, min, h, day, week, year (365 days)
        {"0.001", "1", "60", "3600", "86400", "604800", "31536000"},
        // bit, B, kB, MB, GB, TB, KiB, MiB, GiB, TiB
        {"1/8", "1", "1000", "1000000", "1000000000", "1000000000000", "1024", "1048576", "1073741824",
            "1099511627776"},
    };

    /** The units shown first in each category (from, to). */
    public static final int[][] DEFAULTS = {{3, 6}, {3, 6}, {3, 4}, {3, 9}, {0, 1}, {1, 0}, {3, 2}, {4, 3}};

    private static final BigDecimal ZERO_C = new BigDecimal("273.15");
    private static final BigDecimal THIRTY_TWO = BigDecimal.valueOf(32);
    private static final BigDecimal FIVE = BigDecimal.valueOf(5);
    private static final BigDecimal NINE = BigDecimal.valueOf(9);

    private Units() {
    }

    public static int count(int category) {
        return FACTORS[category].length;
    }

    /** Converts {@code v} from one unit of the category to another. */
    public static BigDecimal convert(int category, int from, int to, BigDecimal v) {
        BigDecimal r;
        if (from == to) {
            r = v;
        } else if (category == TEMPERATURE) {
            r = fromKelvin(to, toKelvin(from, v));
        } else {
            BigDecimal[] f = factor(category, from);
            BigDecimal[] t = factor(category, to);
            // v × from / to as one division, so exact factors stay exact.
            r = v.multiply(f[0]).multiply(t[1]).divide(f[1].multiply(t[0]), Engine.MC);
        }
        return r.signum() == 0 ? BigDecimal.ZERO : r.round(Engine.MC).stripTrailingZeros();
    }

    private static BigDecimal[] factor(int category, int unit) {
        String s = FACTORS[category][unit];
        int slash = s.indexOf('/');
        if (slash < 0) return new BigDecimal[] {new BigDecimal(s), BigDecimal.ONE};
        return new BigDecimal[] {new BigDecimal(s.substring(0, slash)), new BigDecimal(s.substring(slash + 1))};
    }

    private static BigDecimal toKelvin(int unit, BigDecimal v) {
        switch (unit) {
            case 0:
                return v.add(ZERO_C);
            case 1:
                return v.subtract(THIRTY_TWO).multiply(FIVE).divide(NINE, Engine.MC).add(ZERO_C);
            default:
                return v;
        }
    }

    private static BigDecimal fromKelvin(int unit, BigDecimal k) {
        switch (unit) {
            case 0:
                return k.subtract(ZERO_C);
            case 1:
                return k.subtract(ZERO_C).multiply(NINE).divide(FIVE, Engine.MC).add(THIRTY_TWO);
            default:
                return k;
        }
    }
}
