/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.kontakty;


import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Access to the contacts provider and the call log. Contacts are written to the local (device) account. */
final class Repo {
    private Repo() {
    }

    /** A contact in a list. */
    static final class Item {
        long id;
        String lookup;
        String name;
        String photo;
        boolean starred;
        boolean hasPhone;
        String subtitle;
    }

    /** A group or a company with the number of its contacts. */
    static final class Bucket {
        long id;
        String title;
        int count;
    }

    /** A phone number or an e-mail address. */
    static final class Entry {
        String value;
        int type;
        String label;

        Entry(String value, int type, String label) {
            this.value = value;
            this.type = type;
            this.label = label;
        }
    }

    /** All data of one contact that the app shows and edits. */
    static final class Details {
        long contactId;
        String lookup;
        long rawContactId = -1;
        String displayName;
        String givenName = "";
        String familyName = "";
        String company = "";
        String jobTitle = "";
        String note = "";
        boolean starred;
        String photoUri;
        byte[] photo;
        final List<Entry> phones = new ArrayList<>();
        final List<Entry> emails = new ArrayList<>();
        final Set<Long> groups = new HashSet<>();
    }

    /** A call in the call log. */
    static final class Call {
        long id;
        String number;
        String name;
        String photo;
        int type;
        long date;
        long duration;
        int numberType;
        String numberLabel;
        int presentation;
    }

    static final int MODE_ALL = 0;
    static final int MODE_STARRED = 1;

    private static final String[] CONTACT_PROJECTION = {
        Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.PHOTO_THUMBNAIL_URI,
        Contacts.STARRED, Contacts.HAS_PHONE_NUMBER,
    };

    /** Contacts, optionally filtered by text, favourites, a group (groupId >= 0) or a company. */
    static List<Item> contacts(Context c, String filter, int mode, long groupId, String company) {
        ContentResolver r = c.getContentResolver();
        Uri uri = TextUtils.isEmpty(filter) ? Contacts.CONTENT_URI
                : Uri.withAppendedPath(Contacts.CONTENT_FILTER_URI, Uri.encode(filter));
        StringBuilder sel = new StringBuilder();
        List<String> args = new ArrayList<>();
        if (mode == MODE_STARRED) sel.append(Contacts.STARRED).append("=1");
        Set<Long> only = null;
        if (groupId >= 0) {
            only = idsOf(r, GroupMembership.CONTENT_ITEM_TYPE, GroupMembership.GROUP_ROW_ID, String.valueOf(groupId));
        } else if (company != null) {
            only = idsOf(r, Organization.CONTENT_ITEM_TYPE, Organization.COMPANY, company);
        }
        Map<Long, String> companies = companyMap(r);
        List<Item> out = new ArrayList<>();
        try (Cursor cur = r.query(uri, CONTACT_PROJECTION, sel.length() == 0 ? null : sel.toString(),
                args.isEmpty() ? null : args.toArray(new String[0]), Contacts.SORT_KEY_PRIMARY + " ASC")) {
            if (cur == null) return out;
            while (cur.moveToNext()) {
                long id = cur.getLong(0);
                if (only != null && !only.contains(id)) continue;
                Item it = new Item();
                it.id = id;
                it.lookup = cur.getString(1);
                it.name = cur.getString(2);
                it.photo = cur.getString(3);
                it.starred = cur.getInt(4) != 0;
                it.hasPhone = cur.getInt(5) != 0;
                it.subtitle = companies.get(id);
                out.add(it);
            }
        }
        return out;
    }

    private static Set<Long> idsOf(ContentResolver r, String mime, String column, String value) {
        Set<Long> ids = new HashSet<>();
        try (Cursor cur = r.query(Data.CONTENT_URI, new String[] {Data.CONTACT_ID},
                Data.MIMETYPE + "=? AND " + column + "=?", new String[] {mime, value}, null)) {
            while (cur != null && cur.moveToNext()) ids.add(cur.getLong(0));
        }
        return ids;
    }

    private static Map<Long, String> companyMap(ContentResolver r) {
        Map<Long, String> m = new HashMap<>();
        try (Cursor cur = r.query(Data.CONTENT_URI, new String[] {Data.CONTACT_ID, Organization.COMPANY},
                Data.MIMETYPE + "=?", new String[] {Organization.CONTENT_ITEM_TYPE}, null)) {
            while (cur != null && cur.moveToNext()) {
                String co = cur.getString(1);
                if (!TextUtils.isEmpty(co)) m.put(cur.getLong(0), co);
            }
        }
        return m;
    }

    /** Groups of the device (not deleted), with the number of contacts. */
    static List<Bucket> groups(Context c) {
        List<Bucket> out = new ArrayList<>();
        try (Cursor cur = c.getContentResolver().query(Groups.CONTENT_SUMMARY_URI,
                new String[] {Groups._ID, Groups.TITLE, Groups.SUMMARY_COUNT},
                Groups.DELETED + "=0", null, Groups.TITLE + " COLLATE LOCALIZED ASC")) {
            while (cur != null && cur.moveToNext()) {
                Bucket b = new Bucket();
                b.id = cur.getLong(0);
                b.title = cur.getString(1);
                b.count = cur.getInt(2);
                if (!TextUtils.isEmpty(b.title)) out.add(b);
            }
        }
        return out;
    }

    /** Companies named in the contacts, with the number of contacts. */
    static List<Bucket> companies(Context c) {
        Map<String, Set<Long>> m = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<Long, String> e : companyMap(c.getContentResolver()).entrySet()) {
            Set<Long> s = m.get(e.getValue());
            if (s == null) {
                s = new HashSet<>();
                m.put(e.getValue(), s);
            }
            s.add(e.getKey());
        }
        List<Bucket> out = new ArrayList<>();
        for (Map.Entry<String, Set<Long>> e : m.entrySet()) {
            Bucket b = new Bucket();
            b.id = -1;
            b.title = e.getKey();
            b.count = e.getValue().size();
            out.add(b);
        }
        return out;
    }

    static long createGroup(Context c, String title) {
        ContentValues v = new ContentValues();
        v.put(Groups.TITLE, title);
        v.put(Groups.GROUP_VISIBLE, 1);
        Uri u = c.getContentResolver().insert(Groups.CONTENT_URI, v);
        return u == null ? -1 : ContentUris.parseId(u);
    }

    static void renameGroup(Context c, long id, String title) {
        ContentValues v = new ContentValues();
        v.put(Groups.TITLE, title);
        c.getContentResolver().update(ContentUris.withAppendedId(Groups.CONTENT_URI, id), v, null, null);
    }

    static void deleteGroup(Context c, long id) {
        c.getContentResolver().delete(ContentUris.withAppendedId(Groups.CONTENT_URI, id), null, null);
    }

    /** The contact id for a contact, lookup or raw contact URI, or -1. */
    static long resolveContact(Context c, Uri uri) {
        if (uri == null) return -1;
        ContentResolver r = c.getContentResolver();
        try {
            String s = uri.toString();
            if (s.contains("/raw_contacts/")) {
                Uri lookup = RawContacts.getContactLookupUri(r, uri);
                if (lookup == null) return -1;
                uri = lookup;
            }
            Uri contact = Contacts.lookupContact(r, uri);
            if (contact != null) return ContentUris.parseId(contact);
            return ContentUris.parseId(uri);
        } catch (Exception e) {
            return -1;
        }
    }

    /** Loads a contact, or null when it does not exist. */
    static Details load(Context c, long contactId) {
        ContentResolver r = c.getContentResolver();
        Details d = new Details();
        d.contactId = contactId;
        try (Cursor cur = r.query(ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId),
                new String[] {Contacts.DISPLAY_NAME_PRIMARY, Contacts.STARRED, Contacts.PHOTO_URI, Contacts.LOOKUP_KEY},
                null, null, null)) {
            if (cur == null || !cur.moveToFirst()) return null;
            d.displayName = cur.getString(0);
            d.starred = cur.getInt(1) != 0;
            d.photoUri = cur.getString(2);
            d.lookup = cur.getString(3);
        }
        try (Cursor cur = r.query(Data.CONTENT_URI, new String[] {
                    Data.RAW_CONTACT_ID, Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4, Data.DATA5,
                    Data.DATA15, Data.IS_PRIMARY},
                Data.CONTACT_ID + "=?", new String[] {String.valueOf(contactId)},
                Data.RAW_CONTACT_ID + " ASC, " + Data.IS_PRIMARY + " DESC, " + Data._ID + " ASC")) {
            while (cur != null && cur.moveToNext()) {
                long raw = cur.getLong(0);
                if (d.rawContactId < 0) d.rawContactId = raw;
                String mime = cur.getString(1);
                if (StructuredName.CONTENT_ITEM_TYPE.equals(mime)) {
                    if (raw == d.rawContactId) {
                        d.givenName = nz(cur.getString(3));
                        d.familyName = nz(cur.getString(4));
                        if (d.givenName.isEmpty() && d.familyName.isEmpty()) d.givenName = nz(cur.getString(2));
                    }
                } else if (Phone.CONTENT_ITEM_TYPE.equals(mime)) {
                    String n = cur.getString(2);
                    if (!TextUtils.isEmpty(n) && !contains(d.phones, n)) {
                        d.phones.add(new Entry(n, cur.getInt(3), cur.getString(4)));
                    }
                } else if (Email.CONTENT_ITEM_TYPE.equals(mime)) {
                    String n = cur.getString(2);
                    if (!TextUtils.isEmpty(n) && !contains(d.emails, n)) {
                        d.emails.add(new Entry(n, cur.getInt(3), cur.getString(4)));
                    }
                } else if (Organization.CONTENT_ITEM_TYPE.equals(mime)) {
                    if (d.company.isEmpty()) d.company = nz(cur.getString(2));
                    if (d.jobTitle.isEmpty()) d.jobTitle = nz(cur.getString(5));
                } else if (Note.CONTENT_ITEM_TYPE.equals(mime)) {
                    if (d.note.isEmpty()) d.note = nz(cur.getString(2));
                } else if (GroupMembership.CONTENT_ITEM_TYPE.equals(mime)) {
                    d.groups.add(cur.getLong(2));
                } else if (Photo.CONTENT_ITEM_TYPE.equals(mime)) {
                    if (d.photo == null && raw == d.rawContactId) d.photo = cur.getBlob(7);
                }
            }
        }
        return d;
    }

    private static boolean contains(List<Entry> list, String value) {
        for (Entry e : list) if (e.value.equals(value)) return true;
        return false;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /**
     * Saves a contact: a new raw contact in the local account (rawContactId < 0), or the data rows of an existing
     * raw contact are replaced. photoChanged: d.photo is the new photo (null = no photo). Returns the contact id.
     */
    static long save(Context c, Details d, boolean photoChanged) throws Exception {
        ContentResolver r = c.getContentResolver();
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        boolean insert = d.rawContactId < 0;
        if (insert) {
            ops.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                    .withValue(RawContacts.ACCOUNT_TYPE, null)
                    .withValue(RawContacts.ACCOUNT_NAME, null)
                    .build());
        } else {
            String raw = String.valueOf(d.rawContactId);
            List<String> mimes = new ArrayList<>();
            mimes.add(StructuredName.CONTENT_ITEM_TYPE);
            mimes.add(Phone.CONTENT_ITEM_TYPE);
            mimes.add(Email.CONTENT_ITEM_TYPE);
            mimes.add(Organization.CONTENT_ITEM_TYPE);
            mimes.add(Note.CONTENT_ITEM_TYPE);
            mimes.add(GroupMembership.CONTENT_ITEM_TYPE);
            if (photoChanged) mimes.add(Photo.CONTENT_ITEM_TYPE);
            for (String m : mimes) {
                ops.add(ContentProviderOperation.newDelete(Data.CONTENT_URI)
                        .withSelection(Data.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?", new String[] {raw, m})
                        .build());
            }
        }
        if (!d.givenName.isEmpty() || !d.familyName.isEmpty()) {
            ops.add(data(insert, d.rawContactId, StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(StructuredName.GIVEN_NAME, emptyToNull(d.givenName))
                    .withValue(StructuredName.FAMILY_NAME, emptyToNull(d.familyName))
                    .build());
        }
        boolean first = true;
        for (Entry e : d.phones) {
            ops.add(data(insert, d.rawContactId, Phone.CONTENT_ITEM_TYPE)
                    .withValue(Phone.NUMBER, e.value)
                    .withValue(Phone.TYPE, e.type)
                    .withValue(Data.IS_PRIMARY, first ? 1 : 0)
                    .build());
            first = false;
        }
        for (Entry e : d.emails) {
            ops.add(data(insert, d.rawContactId, Email.CONTENT_ITEM_TYPE)
                    .withValue(Email.ADDRESS, e.value)
                    .withValue(Email.TYPE, e.type)
                    .build());
        }
        if (!d.company.isEmpty() || !d.jobTitle.isEmpty()) {
            ops.add(data(insert, d.rawContactId, Organization.CONTENT_ITEM_TYPE)
                    .withValue(Organization.COMPANY, emptyToNull(d.company))
                    .withValue(Organization.TITLE, emptyToNull(d.jobTitle))
                    .withValue(Organization.TYPE, Organization.TYPE_WORK)
                    .build());
        }
        if (!d.note.isEmpty()) {
            ops.add(data(insert, d.rawContactId, Note.CONTENT_ITEM_TYPE).withValue(Note.NOTE, d.note).build());
        }
        for (Long g : d.groups) {
            ops.add(data(insert, d.rawContactId, GroupMembership.CONTENT_ITEM_TYPE)
                    .withValue(GroupMembership.GROUP_ROW_ID, g)
                    .build());
        }
        if ((insert || photoChanged) && d.photo != null) {
            ops.add(data(insert, d.rawContactId, Photo.CONTENT_ITEM_TYPE)
                    .withValue(Photo.PHOTO, d.photo)
                    .withValue(Data.IS_SUPER_PRIMARY, 1)
                    .build());
        }
        android.content.ContentProviderResult[] res = r.applyBatch(ContactsContract.AUTHORITY, ops);
        long raw = insert ? ContentUris.parseId(res[0].uri) : d.rawContactId;
        long contactId = -1;
        try (Cursor cur = r.query(ContentUris.withAppendedId(RawContacts.CONTENT_URI, raw),
                new String[] {RawContacts.CONTACT_ID}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) contactId = cur.getLong(0);
        }
        if (contactId >= 0) setStarred(c, contactId, d.starred);
        return contactId;
    }

    private static ContentProviderOperation.Builder data(boolean insert, long raw, String mime) {
        ContentProviderOperation.Builder b = ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValue(Data.MIMETYPE, mime);
        return insert ? b.withValueBackReference(Data.RAW_CONTACT_ID, 0) : b.withValue(Data.RAW_CONTACT_ID, raw);
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    static void setStarred(Context c, long contactId, boolean starred) {
        ContentValues v = new ContentValues();
        v.put(Contacts.STARRED, starred ? 1 : 0);
        c.getContentResolver().update(ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId), v, null, null);
    }

    static void delete(Context c, long contactId) {
        c.getContentResolver().delete(ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId), null, null);
    }

    /** The first (primary) phone number of a contact, or null. */
    static String primaryPhone(Context c, long contactId) {
        try (Cursor cur = c.getContentResolver().query(Phone.CONTENT_URI, new String[] {Phone.NUMBER},
                Phone.CONTACT_ID + "=?", new String[] {String.valueOf(contactId)},
                Phone.IS_SUPER_PRIMARY + " DESC, " + Phone.IS_PRIMARY + " DESC, " + Phone._ID + " ASC")) {
            if (cur != null && cur.moveToFirst()) return cur.getString(0);
        }
        return null;
    }

    /** The contact of a phone number, or -1. */
    static long contactForNumber(Context c, String number) {
        if (TextUtils.isEmpty(number)) return -1;
        try (Cursor cur = c.getContentResolver().query(
                Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                new String[] {PhoneLookup.CONTACT_ID}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) return cur.getLong(0);
        } catch (Exception ignored) {
            // An invalid number.
        }
        return -1;
    }

    /** Contacts whose numbers contain the dialled digits (for the dial pad), at most limit. */
    static List<String[]> numberMatches(Context c, String digits, int limit) {
        List<String[]> out = new ArrayList<>();
        if (TextUtils.isEmpty(digits)) return out;
        Set<String> seen = new HashSet<>();
        try (Cursor cur = c.getContentResolver().query(
                Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, Uri.encode(digits)),
                new String[] {Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER, Phone.PHOTO_THUMBNAIL_URI}, null, null, null)) {
            while (cur != null && cur.moveToNext() && out.size() < limit) {
                String key = cur.getString(0) + "|" + cur.getString(1);
                if (seen.add(key)) out.add(new String[] {cur.getString(0), cur.getString(1), cur.getString(2)});
            }
        } catch (Exception ignored) {
            // No match.
        }
        return out;
    }

    /** The call log, newest first. */
    static List<Call> calls(Context c, int limit) {
        List<Call> out = new ArrayList<>();
        try (Cursor cur = c.getContentResolver().query(CallLog.Calls.CONTENT_URI, new String[] {
                    CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.CACHED_PHOTO_URI,
                    CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.CACHED_NUMBER_TYPE,
                    CallLog.Calls.CACHED_NUMBER_LABEL, CallLog.Calls.NUMBER_PRESENTATION},
                null, null, CallLog.Calls.DATE + " DESC")) {
            while (cur != null && cur.moveToNext() && out.size() < limit) {
                Call k = new Call();
                k.id = cur.getLong(0);
                k.number = cur.getString(1);
                k.name = cur.getString(2);
                k.photo = cur.getString(3);
                k.type = cur.getInt(4);
                k.date = cur.getLong(5);
                k.duration = cur.getLong(6);
                k.numberType = cur.getInt(7);
                k.numberLabel = cur.getString(8);
                k.presentation = cur.getInt(9);
                out.add(k);
            }
        }
        return out;
    }

    static void deleteCall(Context c, long id) {
        c.getContentResolver().delete(CallLog.Calls.CONTENT_URI, CallLog.Calls._ID + "=?",
                new String[] {String.valueOf(id)});
    }

    static void clearCalls(Context c) {
        c.getContentResolver().delete(CallLog.Calls.CONTENT_URI, null, null);
    }
}
