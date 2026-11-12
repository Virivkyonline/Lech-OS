/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package lech.os.fotoaparat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * QR codes in the camera preview: decoding with ZXing core (on the phone, nothing is sent anywhere) and the meaning
 * of the text (link, Wi-Fi network, phone number, e-mail address, plain text). Plain Java, no Android classes.
 */
final class QrCodes {
    static final int TEXT = 0;
    static final int LINK = 1;
    static final int WIFI = 2;
    static final int PHONE = 3;
    static final int MAIL = 4;

    private static final Map<DecodeHintType, Object> HINTS = new EnumMap<>(DecodeHintType.class);

    static {
        HINTS.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
        HINTS.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
    }

    private QrCodes() {
    }

    /** The text of a QR code in an ARGB frame, or null when there is none. */
    static String decode(int[] argb, int width, int height) {
        try {
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, argb)));
            String text = new QRCodeReader().decode(bitmap, HINTS).getText();
            return text == null || text.isEmpty() ? null : text;
        } catch (ReaderException | RuntimeException e) {
            // No code in this frame (NotFound, Checksum, Format) or a damaged one.
            return null;
        }
    }

    /** A decoded QR code: its type and the parts the camera needs. */
    static final class Code {
        final String raw;
        final int type;
        /** Wi-Fi: network name, security (WPA, SAE, WEP, nopass), password, hidden network. */
        String ssid = "";
        String security = "";
        String password = "";
        boolean hidden;

        Code(String raw, int type) {
            this.raw = raw;
            this.type = type;
        }
    }

    static Code parse(String raw) {
        String t = raw.trim();
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) return new Code(t, LINK);
        if (lower.startsWith("tel:") && t.length() > 4) return new Code(t, PHONE);
        if (lower.startsWith("mailto:") && t.length() > 7) return new Code(t, MAIL);
        if (lower.startsWith("wifi:")) {
            Code c = new Code(t, WIFI);
            parseWifi(t.substring(5), c);
            if (!c.ssid.isEmpty()) return c;
        }
        return new Code(t, TEXT);
    }

    /** WIFI:T:WPA;S:name;P:password;H:true;; - fields end with ';', a backslash escapes \ ; , : and ". */
    private static void parseWifi(String s, Code c) {
        StringBuilder key = new StringBuilder();
        StringBuilder value = new StringBuilder();
        boolean inValue = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (inValue && ch == '\\' && i + 1 < s.length()) {
                value.append(s.charAt(++i));
            } else if (!inValue && ch == ':') {
                inValue = true;
            } else if (ch == ';') {
                if (inValue) putWifi(key.toString(), value.toString(), c);
                key.setLength(0);
                value.setLength(0);
                inValue = false;
            } else if (inValue) {
                value.append(ch);
            } else {
                key.append(ch);
            }
        }
        if (inValue) putWifi(key.toString(), value.toString(), c);
    }

    private static void putWifi(String key, String value, Code c) {
        switch (key.trim().toUpperCase(Locale.ROOT)) {
            case "S": c.ssid = value; break;
            case "T": c.security = value.trim(); break;
            case "P": c.password = value; break;
            case "H": c.hidden = "true".equalsIgnoreCase(value.trim()); break;
            default: break;
        }
    }
}
