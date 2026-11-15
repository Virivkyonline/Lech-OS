/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.kontakty;

import android.content.ContentProviderOperation;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Import and export of the contacts as a contact file (.vcf): moving the contacts from and to another phone without
 * any account or cloud. The file format is in {@link VcfFormat}.
 */
final class Vcf {
    private Vcf() {
    }

    /** Writes all contacts of the phone into the file; returns their number. */
    static int exportAll(Context ctx, Uri file) throws Exception {
        List<VcfFormat.Card> cards = readAll(ctx);
        try (OutputStream os = ctx.getContentResolver().openOutputStream(file, "wt")) {
            if (os == null) throw new java.io.IOException("no output");
            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
            for (VcfFormat.Card c : cards) VcfFormat.write(c, w);
            w.flush();
        }
        return cards.size();
    }

    /**
     * Adds the contacts of the file to the phone (stored in the phone, no account). A contact that is already
     * there with the same name and the same first number is skipped, so a file can be imported again. Returns
     * {added, skipped}.
     */
    static int[] importFile(Context ctx, Uri file) throws Exception {
        List<VcfFormat.Card> cards;
        try (InputStream is = ctx.getContentResolver().openInputStream(file)) {
            if (is == null) throw new java.io.IOException("no input");
            cards = VcfFormat.parse(new InputStreamReader(is, StandardCharsets.UTF_8));
        }
        Set<String> known = new HashSet<>();
        for (VcfFormat.Card c : readAll(ctx)) known.add(key(c));
        int added = 0;
        int skipped = 0;
        for (VcfFormat.Card c : cards) {
            if (!known.add(key(c))) {
                skipped++;
                continue;
            }
            ArrayList<ContentProviderOperation> ops = new ArrayList<>();
            ops.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                    .withValue(RawContacts.ACCOUNT_TYPE, null)
                    .withValue(RawContacts.ACCOUNT_NAME, null)
                    .build());
            boolean parts = !(c.family + c.given + c.middle + c.prefix + c.suffix).isEmpty();
            ContentProviderOperation.Builder name = row(StructuredName.CONTENT_ITEM_TYPE);
            if (parts) {
                name.withValue(StructuredName.FAMILY_NAME, c.family)
                        .withValue(StructuredName.GIVEN_NAME, c.given)
                        .withValue(StructuredName.MIDDLE_NAME, c.middle)
                        .withValue(StructuredName.PREFIX, c.prefix)
                        .withValue(StructuredName.SUFFIX, c.suffix);
            } else {
                name.withValue(StructuredName.DISPLAY_NAME, c.name());
            }
            if (parts || !c.name().isEmpty()) ops.add(name.build());
            for (String[] p : c.phones) {
                ops.add(row(Phone.CONTENT_ITEM_TYPE)
                        .withValue(Phone.NUMBER, p[0])
                        .withValue(Phone.TYPE, phoneType(p[1]))
                        .build());
            }
            for (String[] m : c.emails) {
                ops.add(row(Email.CONTENT_ITEM_TYPE)
                        .withValue(Email.ADDRESS, m[0])
                        .withValue(Email.TYPE, "WORK".equals(m[1]) ? Email.TYPE_WORK
                                : "HOME".equals(m[1]) ? Email.TYPE_HOME : Email.TYPE_OTHER)
                        .build());
            }
            if (!c.company.isEmpty() || !c.title.isEmpty()) {
                ops.add(row(Organization.CONTENT_ITEM_TYPE)
                        .withValue(Organization.COMPANY, c.company)
                        .withValue(Organization.TITLE, c.title)
                        .withValue(Organization.TYPE, Organization.TYPE_WORK)
                        .build());
            }
            ctx.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
            added++;
        }
        return new int[] {added, skipped};
    }

    /** A data row of the raw contact that the first operation of the batch creates. */
    private static ContentProviderOperation.Builder row(String mime) {
        return ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, mime);
    }

    private static String key(VcfFormat.Card c) {
        String phone = c.phones.isEmpty() ? "" : c.phones.get(0)[0].replaceAll("[^0-9+]", "");
        return c.name().toLowerCase(java.util.Locale.ROOT) + "|" + phone;
    }

    private static int phoneType(String kind) {
        switch (kind) {
            case "HOME":
                return Phone.TYPE_HOME;
            case "WORK":
                return Phone.TYPE_WORK;
            case "FAX":
                return Phone.TYPE_FAX_WORK;
            case "OTHER":
                return Phone.TYPE_OTHER;
            default:
                return Phone.TYPE_MOBILE;
        }
    }

    private static String phoneKind(int type) {
        switch (type) {
            case Phone.TYPE_HOME:
                return "HOME";
            case Phone.TYPE_WORK:
            case Phone.TYPE_WORK_MOBILE:
                return "WORK";
            case Phone.TYPE_FAX_WORK:
            case Phone.TYPE_FAX_HOME:
                return "FAX";
            case Phone.TYPE_MOBILE:
                return "CELL";
            default:
                return "OTHER";
        }
    }

    /** All contacts of the phone with the fields a contact file keeps. */
    private static List<VcfFormat.Card> readAll(Context ctx) {
        List<VcfFormat.Card> cards = new ArrayList<>();
        String[] cols = {Data.CONTACT_ID, Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4,
                Data.DATA5, Data.DATA6};
        String where = Data.MIMETYPE + " IN (?,?,?,?)";
        String[] args = {StructuredName.CONTENT_ITEM_TYPE, Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE,
                Organization.CONTENT_ITEM_TYPE};
        try (Cursor c = ctx.getContentResolver().query(Data.CONTENT_URI, cols, where, args, Data.CONTACT_ID)) {
            if (c == null) return cards;
            long last = -1;
            VcfFormat.Card card = null;
            while (c.moveToNext()) {
                long id = c.getLong(0);
                if (card == null || id != last) {
                    card = new VcfFormat.Card();
                    cards.add(card);
                    last = id;
                }
                String mime = c.getString(1);
                if (StructuredName.CONTENT_ITEM_TYPE.equals(mime)) {
                    if (!card.display.isEmpty()) continue;   // the name of the first raw contact
                    card.display = text(c, 2);
                    card.given = text(c, 3);
                    card.family = text(c, 4);
                    card.prefix = text(c, 5);
                    card.middle = text(c, 6);
                    card.suffix = text(c, 7);
                } else if (Phone.CONTENT_ITEM_TYPE.equals(mime)) {
                    String number = text(c, 2);
                    if (!number.isEmpty()) card.phones.add(new String[] {number, phoneKind(c.getInt(3))});
                } else if (Email.CONTENT_ITEM_TYPE.equals(mime)) {
                    String mail = text(c, 2);
                    int type = c.getInt(3);
                    if (!mail.isEmpty()) {
                        card.emails.add(new String[] {mail,
                                type == Email.TYPE_WORK ? "WORK" : type == Email.TYPE_HOME ? "HOME" : "OTHER"});
                    }
                } else if (Organization.CONTENT_ITEM_TYPE.equals(mime)) {
                    if (card.company.isEmpty()) card.company = text(c, 2);
                    if (card.title.isEmpty()) card.title = text(c, 5);
                }
            }
        }
        List<VcfFormat.Card> filled = new ArrayList<>();
        for (VcfFormat.Card card : cards) {
            if (!card.isEmpty()) filled.add(card);
        }
        return filled;
    }

    private static String text(Cursor c, int column) {
        String s = c.getString(column);
        return s == null ? "" : s.trim();
    }
}
