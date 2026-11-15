/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.kontakty;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reading and writing of contact files (vCard, .vcf) without Android classes, so it can be tested on a computer.
 * Reads vCard 2.1, 3.0 and 4.0 as phones and other contact apps write them (folded lines, quoted-printable text of
 * old phones); writes vCard 3.0 in UTF-8. Kept: name, phone numbers, e-mail addresses, company and job title.
 */
final class VcfFormat {
    /** One contact of a file. */
    static final class Card {
        String display = "";
        String family = "";
        String given = "";
        String middle = "";
        String prefix = "";
        String suffix = "";
        String company = "";
        String title = "";
        /** Pairs {number, kind}; kind is one of CELL, HOME, WORK, FAX, OTHER. */
        final List<String[]> phones = new ArrayList<>();
        /** Pairs {address, kind}; kind is one of HOME, WORK, OTHER. */
        final List<String[]> emails = new ArrayList<>();

        boolean isEmpty() {
            return name().isEmpty() && phones.isEmpty() && emails.isEmpty() && company.isEmpty();
        }

        /** The name to show: the written one, else given and family name. */
        String name() {
            if (!display.trim().isEmpty()) return display.trim();
            return (given + " " + family).trim();
        }
    }

    private VcfFormat() {
    }

    static List<Card> parse(Reader in) throws IOException {
        List<Card> cards = new ArrayList<>();
        Card card = null;
        for (String line : unfold(in)) {
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String[] head = line.substring(0, colon).split(";");
            String value = line.substring(colon + 1);
            String name = head[0].toUpperCase(Locale.ROOT);
            int dot = name.lastIndexOf('.');
            if (dot >= 0) name = name.substring(dot + 1);   // "item1.TEL"
            if (name.equals("BEGIN") && value.trim().equalsIgnoreCase("VCARD")) {
                card = new Card();
                continue;
            }
            if (card == null) continue;
            if (name.equals("END") && value.trim().equalsIgnoreCase("VCARD")) {
                if (!card.isEmpty()) cards.add(card);
                card = null;
                continue;
            }
            String params = paramsOf(head);
            if (params.contains("QUOTED-PRINTABLE")) value = decodeQuotedPrintable(value, charsetOf(head));
            switch (name) {
                case "FN":
                    card.display = unescape(value).trim();
                    break;
                case "N": {
                    List<String> p = splitParts(value);
                    card.family = part(p, 0);
                    card.given = part(p, 1);
                    card.middle = part(p, 2);
                    card.prefix = part(p, 3);
                    card.suffix = part(p, 4);
                    break;
                }
                case "TEL": {
                    String number = unescape(value).trim();
                    if (number.toLowerCase(Locale.ROOT).startsWith("tel:")) number = number.substring(4);
                    if (!number.isEmpty()) card.phones.add(new String[] {number, phoneKind(params)});
                    break;
                }
                case "EMAIL": {
                    String mail = unescape(value).trim();
                    if (!mail.isEmpty()) card.emails.add(new String[] {mail, mailKind(params)});
                    break;
                }
                case "ORG":
                    card.company = part(splitParts(value), 0);
                    break;
                case "TITLE":
                    card.title = unescape(value).trim();
                    break;
                default:
                    break;
            }
        }
        return cards;
    }

    /** Appends one contact as vCard 3.0. */
    static void write(Card c, Appendable out) throws IOException {
        out.append("BEGIN:VCARD\r\nVERSION:3.0\r\n");
        out.append("N:").append(escape(c.family)).append(';').append(escape(c.given)).append(';')
                .append(escape(c.middle)).append(';').append(escape(c.prefix)).append(';')
                .append(escape(c.suffix)).append("\r\n");
        String name = c.name();
        if (name.isEmpty()) name = c.company;
        out.append("FN:").append(escape(name)).append("\r\n");
        for (String[] p : c.phones) {
            out.append("TEL;TYPE=").append(p[1]).append(':').append(escape(p[0])).append("\r\n");
        }
        for (String[] m : c.emails) {
            out.append("EMAIL;TYPE=INTERNET");
            if (!"OTHER".equals(m[1])) out.append(',').append(m[1]);
            out.append(':').append(escape(m[0])).append("\r\n");
        }
        if (!c.company.isEmpty()) out.append("ORG:").append(escape(c.company)).append("\r\n");
        if (!c.title.isEmpty()) out.append("TITLE:").append(escape(c.title)).append("\r\n");
        out.append("END:VCARD\r\n");
    }

    /** Logical lines: a line that starts with a space continues the one before; "=" ends a quoted-printable line. */
    private static List<String> unfold(Reader in) throws IOException {
        List<String> lines = new ArrayList<>();
        BufferedReader r = new BufferedReader(in);
        StringBuilder cur = null;
        boolean softBreak = false;
        String raw;
        while ((raw = r.readLine()) != null) {
            if (cur != null && softBreak) {
                cur.setLength(cur.length() - 1);
                cur.append(raw);
            } else if (cur != null && !raw.isEmpty() && (raw.charAt(0) == ' ' || raw.charAt(0) == '\t')) {
                cur.append(raw, 1, raw.length());
            } else {
                if (cur != null) lines.add(cur.toString());
                cur = new StringBuilder(raw);
            }
            String s = cur.toString();
            int colon = s.indexOf(':');
            softBreak = colon > 0 && s.endsWith("=")
                    && s.substring(0, colon).toUpperCase(Locale.ROOT).contains("QUOTED-PRINTABLE");
        }
        if (cur != null) lines.add(cur.toString());
        return lines;
    }

    private static String paramsOf(String[] head) {
        StringBuilder b = new StringBuilder();
        for (int i = 1; i < head.length; i++) b.append(';').append(head[i].toUpperCase(Locale.ROOT));
        return b.toString();
    }

    private static Charset charsetOf(String[] head) {
        for (int i = 1; i < head.length; i++) {
            String p = head[i].toUpperCase(Locale.ROOT);
            if (p.startsWith("CHARSET=")) {
                try {
                    return Charset.forName(head[i].substring(8).trim());
                } catch (RuntimeException e) {
                    return StandardCharsets.UTF_8;
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static String phoneKind(String params) {
        if (params.contains("FAX")) return "FAX";
        if (params.contains("CELL")) return "CELL";
        if (params.contains("WORK")) return "WORK";
        if (params.contains("HOME")) return "HOME";
        return params.contains("VOICE") || params.isEmpty() || params.contains("PREF") ? "CELL" : "OTHER";
    }

    private static String mailKind(String params) {
        if (params.contains("WORK")) return "WORK";
        if (params.contains("HOME")) return "HOME";
        return "OTHER";
    }

    private static String part(List<String> parts, int i) {
        return i < parts.size() ? parts.get(i).trim() : "";
    }

    /** Splits a structured value at ";" that is not escaped, and unescapes the parts. */
    private static List<String> splitParts(String value) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\' && i + 1 < value.length()) {
                cur.append(ch).append(value.charAt(++i));
            } else if (ch == ';') {
                parts.add(unescape(cur.toString()));
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        parts.add(unescape(cur.toString()));
        return parts;
    }

    private static String unescape(String s) {
        if (s.indexOf('\\') < 0) return s;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i);
                b.append(next == 'n' || next == 'N' ? ' ' : next);
            } else {
                b.append(ch);
            }
        }
        return b.toString();
    }

    private static String escape(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' || ch == ';' || ch == ',') {
                b.append('\\').append(ch);
            } else if (ch == '\n') {
                b.append("\\n");
            } else if (ch != '\r') {
                b.append(ch);
            }
        }
        return b.toString();
    }

    private static String decodeQuotedPrintable(String s, Charset charset) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '=' && i + 2 < s.length() && isHex(s.charAt(i + 1)) && isHex(s.charAt(i + 2))) {
                out.write(Integer.parseInt(s.substring(i + 1, i + 3), 16));
                i += 2;
            } else {
                byte[] b = String.valueOf(ch).getBytes(charset);
                out.write(b, 0, b.length);
            }
        }
        return new String(out.toByteArray(), charset);
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
    }
}
