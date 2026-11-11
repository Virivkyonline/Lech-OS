/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.spravy;

import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BlockedNumberContract;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.Telephony;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The SMS / MMS provider of the phone: conversations, messages, sending, blocked numbers and contact names. */
final class Store {
    static final String ACTION_SENT = "lech.os.spravy.SENT";

    private Store() {
    }

    /** A conversation in the list. */
    static final class Conversation {
        long threadId;
        String address;
        String name;
        String photo;
        String snippet;
        long date;
        int unread;
        boolean mms;
    }

    /** One message of a conversation. */
    static final class Message {
        long id;
        boolean mms;
        boolean incoming;
        String body;
        long date;
        int status; // 0 = ok, 1 = sending, 2 = failed
        Uri image;
        String address;
    }

    /** A contact name and photo for an address. */
    static final class Who {
        String name;
        String photo;
        long contactId = -1;
    }

    private static final Map<String, Who> WHO = new HashMap<>();

    static synchronized Who who(Context c, String address) {
        if (TextUtils.isEmpty(address)) return new Who();
        Who w = WHO.get(address);
        if (w != null) return w;
        w = new Who();
        try (Cursor cur = c.getContentResolver().query(
                Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address)),
                new String[] {PhoneLookup.DISPLAY_NAME, PhoneLookup.PHOTO_THUMBNAIL_URI, PhoneLookup.CONTACT_ID},
                null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                w.name = cur.getString(0);
                w.photo = cur.getString(1);
                w.contactId = cur.getLong(2);
            }
        } catch (Exception ignored) {
            // Not a phone number (an alphanumeric sender) or no permission.
        }
        WHO.put(address, w);
        return w;
    }

    static synchronized void forgetNames() {
        WHO.clear();
    }

    static String label(Context c, String address) {
        Who w = who(c, address);
        if (!TextUtils.isEmpty(w.name)) return w.name;
        return TextUtils.isEmpty(address) ? c.getString(R.string.unknown) : address;
    }

    // ---- Conversations ----

    static List<Conversation> conversations(Context c) {
        ContentResolver r = c.getContentResolver();
        Map<Long, Conversation> m = new HashMap<>();
        // SMS: the newest message of each thread and the unread count.
        try (Cursor cur = r.query(Telephony.Sms.CONTENT_URI, new String[] {
                    Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE,
                    Telephony.Sms.READ, Telephony.Sms.TYPE},
                null, null, Telephony.Sms.DATE + " DESC")) {
            while (cur != null && cur.moveToNext()) {
                long t = cur.getLong(0);
                Conversation cv = m.get(t);
                if (cv == null) {
                    cv = new Conversation();
                    cv.threadId = t;
                    cv.address = cur.getString(1);
                    cv.snippet = cur.getString(2);
                    cv.date = cur.getLong(3);
                    m.put(t, cv);
                }
                if (cur.getInt(4) == 0 && cur.getInt(5) == Telephony.Sms.MESSAGE_TYPE_INBOX) cv.unread++;
            }
        } catch (Exception ignored) {
            // No access.
        }
        // MMS: newer than the SMS of the thread, or threads with MMS only.
        try (Cursor cur = r.query(Telephony.Mms.CONTENT_URI, new String[] {
                    Telephony.Mms._ID, Telephony.Mms.THREAD_ID, Telephony.Mms.DATE, Telephony.Mms.READ,
                    Telephony.Mms.MESSAGE_BOX},
                null, null, Telephony.Mms.DATE + " DESC")) {
            while (cur != null && cur.moveToNext()) {
                long id = cur.getLong(0);
                long t = cur.getLong(1);
                long date = cur.getLong(2) * 1000L;
                Conversation cv = m.get(t);
                if (cv == null || date > cv.date) {
                    if (cv == null) {
                        cv = new Conversation();
                        cv.threadId = t;
                        m.put(t, cv);
                    }
                    cv.date = date;
                    cv.mms = true;
                    String text = mmsText(c, id);
                    cv.snippet = TextUtils.isEmpty(text) ? c.getString(R.string.mms_photo) : text;
                    String a = mmsAddress(c, id, cur.getInt(4) == Telephony.Mms.MESSAGE_BOX_INBOX);
                    if (a != null) cv.address = a;
                }
                if (cur.getInt(3) == 0 && cur.getInt(4) == Telephony.Mms.MESSAGE_BOX_INBOX) cv.unread++;
            }
        } catch (Exception ignored) {
            // No MMS.
        }
        List<Conversation> out = new ArrayList<>(m.values());
        for (Conversation cv : out) {
            Who w = who(c, cv.address);
            cv.name = w.name;
            cv.photo = w.photo;
        }
        Collections.sort(out, (a, b) -> Long.compare(b.date, a.date));
        return out;
    }

    /** Messages of a thread, oldest first. */
    static List<Message> messages(Context c, long threadId) {
        ContentResolver r = c.getContentResolver();
        List<Message> out = new ArrayList<>();
        String sel = "thread_id=?";
        String[] args = {String.valueOf(threadId)};
        try (Cursor cur = r.query(Telephony.Sms.CONTENT_URI, new String[] {Telephony.Sms._ID, Telephony.Sms.TYPE,
                    Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.ADDRESS}, sel, args, null)) {
            while (cur != null && cur.moveToNext()) {
                Message msg = new Message();
                msg.id = cur.getLong(0);
                int type = cur.getInt(1);
                msg.incoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX;
                msg.status = type == Telephony.Sms.MESSAGE_TYPE_OUTBOX || type == Telephony.Sms.MESSAGE_TYPE_QUEUED ? 1
                        : type == Telephony.Sms.MESSAGE_TYPE_FAILED ? 2 : 0;
                msg.body = cur.getString(2);
                msg.date = cur.getLong(3);
                msg.address = cur.getString(4);
                out.add(msg);
            }
        } catch (Exception ignored) {
            // No access.
        }
        try (Cursor cur = r.query(Telephony.Mms.CONTENT_URI, new String[] {Telephony.Mms._ID,
                    Telephony.Mms.MESSAGE_BOX, Telephony.Mms.DATE}, sel, args, null)) {
            while (cur != null && cur.moveToNext()) {
                Message msg = new Message();
                msg.id = cur.getLong(0);
                msg.mms = true;
                msg.incoming = cur.getInt(1) == Telephony.Mms.MESSAGE_BOX_INBOX;
                msg.date = cur.getLong(2) * 1000L;
                msg.body = mmsText(c, msg.id);
                msg.image = mmsImage(c, msg.id);
                out.add(msg);
            }
        } catch (Exception ignored) {
            // No MMS.
        }
        Collections.sort(out, (a, b) -> Long.compare(a.date, b.date));
        return out;
    }

    private static String mmsText(Context c, long id) {
        StringBuilder sb = new StringBuilder();
        try (Cursor cur = c.getContentResolver().query(Uri.parse("content://mms/part"),
                new String[] {"_id", "ct", "text"}, "mid=?", new String[] {String.valueOf(id)}, null)) {
            while (cur != null && cur.moveToNext()) {
                if ("text/plain".equals(cur.getString(1)) && cur.getString(2) != null) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(cur.getString(2));
                }
            }
        } catch (Exception ignored) {
            // No parts.
        }
        return sb.toString();
    }

    private static Uri mmsImage(Context c, long id) {
        try (Cursor cur = c.getContentResolver().query(Uri.parse("content://mms/part"),
                new String[] {"_id", "ct"}, "mid=?", new String[] {String.valueOf(id)}, null)) {
            while (cur != null && cur.moveToNext()) {
                String ct = cur.getString(1);
                if (ct != null && ct.startsWith("image/")) {
                    return Uri.parse("content://mms/part/" + cur.getLong(0));
                }
            }
        } catch (Exception ignored) {
            // No parts.
        }
        return null;
    }

    private static String mmsAddress(Context c, long id, boolean incoming) {
        // 137 = from, 151 = to (PduHeaders).
        String type = incoming ? "137" : "151";
        try (Cursor cur = c.getContentResolver().query(Uri.parse("content://mms/" + id + "/addr"),
                new String[] {"address", "type"}, "type=?", new String[] {type}, null)) {
            if (cur != null && cur.moveToFirst()) return cur.getString(0);
        } catch (Exception ignored) {
            // No address.
        }
        return null;
    }

    /** The address of a thread (its first recipient). */
    static String threadAddress(Context c, long threadId) {
        try (Cursor cur = c.getContentResolver().query(Telephony.Sms.CONTENT_URI,
                new String[] {Telephony.Sms.ADDRESS}, "thread_id=?", new String[] {String.valueOf(threadId)},
                Telephony.Sms.DATE + " DESC")) {
            if (cur != null && cur.moveToFirst()) return cur.getString(0);
        } catch (Exception ignored) {
            // Fall through.
        }
        try (Cursor cur = c.getContentResolver().query(Telephony.Mms.CONTENT_URI, new String[] {Telephony.Mms._ID,
                    Telephony.Mms.MESSAGE_BOX}, "thread_id=?", new String[] {String.valueOf(threadId)}, null)) {
            if (cur != null && cur.moveToFirst()) {
                return mmsAddress(c, cur.getLong(0), cur.getInt(1) == Telephony.Mms.MESSAGE_BOX_INBOX);
            }
        } catch (Exception ignored) {
            // No MMS.
        }
        return null;
    }

    static long threadFor(Context c, String address) {
        return Telephony.Threads.getOrCreateThreadId(c, address);
    }

    static void markRead(Context c, long threadId) {
        ContentValues v = new ContentValues();
        v.put(Telephony.Sms.READ, 1);
        v.put(Telephony.Sms.SEEN, 1);
        try {
            c.getContentResolver().update(Telephony.Sms.CONTENT_URI, v, "thread_id=? AND read=0",
                    new String[] {String.valueOf(threadId)});
            ContentValues mv = new ContentValues();
            mv.put(Telephony.Mms.READ, 1);
            mv.put(Telephony.Mms.SEEN, 1);
            c.getContentResolver().update(Telephony.Mms.CONTENT_URI, mv, "thread_id=? AND read=0",
                    new String[] {String.valueOf(threadId)});
        } catch (Exception ignored) {
            // Not the default SMS app.
        }
        Notifier.cancel(c, threadId);
    }

    static void markAllRead(Context c) {
        ContentValues v = new ContentValues();
        v.put(Telephony.Sms.READ, 1);
        v.put(Telephony.Sms.SEEN, 1);
        try {
            c.getContentResolver().update(Telephony.Sms.CONTENT_URI, v, "read=0", null);
            ContentValues mv = new ContentValues();
            mv.put(Telephony.Mms.READ, 1);
            c.getContentResolver().update(Telephony.Mms.CONTENT_URI, mv, "read=0", null);
        } catch (Exception ignored) {
            // Not the default SMS app.
        }
        Notifier.cancelAll(c);
    }

    static void deleteThread(Context c, long threadId) {
        c.getContentResolver().delete(ContentUris.withAppendedId(Telephony.Threads.CONTENT_URI, threadId), null, null);
    }

    static void deleteMessage(Context c, Message m) {
        Uri base = m.mms ? Telephony.Mms.CONTENT_URI : Telephony.Sms.CONTENT_URI;
        c.getContentResolver().delete(ContentUris.withAppendedId(base, m.id), null, null);
    }

    // ---- Sending ----

    /** Writes the message to the outbox and sends it; the result moves it to sent or failed. */
    static void send(Context c, String address, String body, int subId) {
        ContentValues v = new ContentValues();
        v.put(Telephony.Sms.ADDRESS, address);
        v.put(Telephony.Sms.BODY, body);
        v.put(Telephony.Sms.DATE, System.currentTimeMillis());
        v.put(Telephony.Sms.READ, 1);
        v.put(Telephony.Sms.SEEN, 1);
        v.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX);
        v.put(Telephony.Sms.THREAD_ID, threadFor(c, address));
        if (subId >= 0) v.put(Telephony.Sms.SUBSCRIPTION_ID, subId);
        Uri uri = c.getContentResolver().insert(Telephony.Sms.CONTENT_URI, v);
        SmsManager sm = subId >= 0 ? c.getSystemService(SmsManager.class).createForSubscriptionId(subId)
                : c.getSystemService(SmsManager.class);
        ArrayList<String> parts = sm.divideMessage(body);
        ArrayList<PendingIntent> sent = new ArrayList<>();
        for (int k = 0; k < parts.size(); k++) {
            Intent i = new Intent(ACTION_SENT, uri, c, SentReceiver.class);
            i.putExtra("last", k == parts.size() - 1);
            sent.add(PendingIntent.getBroadcast(c, (int) (uri == null ? 0 : ContentUris.parseId(uri)) * 16 + k, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE));
        }
        try {
            sm.sendMultipartTextMessage(address, null, parts, sent, null);
        } catch (Exception e) {
            if (uri != null) setType(c, uri, Telephony.Sms.MESSAGE_TYPE_FAILED);
        }
    }

    static void resend(Context c, Message m, String address) {
        deleteMessage(c, m);
        send(c, address, m.body, -1);
    }

    static void setType(Context c, Uri uri, int type) {
        ContentValues v = new ContentValues();
        v.put(Telephony.Sms.TYPE, type);
        c.getContentResolver().update(uri, v, null, null);
    }

    // ---- Blocked numbers ----

    static List<String[]> blocked(Context c) {
        List<String[]> out = new ArrayList<>();
        try (Cursor cur = c.getContentResolver().query(BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                new String[] {BlockedNumberContract.BlockedNumbers.COLUMN_ID,
                    BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER}, null, null, null)) {
            while (cur != null && cur.moveToNext()) {
                out.add(new String[] {String.valueOf(cur.getLong(0)), cur.getString(1)});
            }
        } catch (Exception ignored) {
            // Only the default SMS / phone app may read them.
        }
        return out;
    }

    static boolean isBlocked(Context c, String address) {
        try {
            return BlockedNumberContract.isBlocked(c, address);
        } catch (Exception e) {
            return false;
        }
    }

    static void block(Context c, String address) {
        ContentValues v = new ContentValues();
        v.put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, address);
        c.getContentResolver().insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, v);
    }

    static void unblock(Context c, long id) {
        c.getContentResolver().delete(ContentUris.withAppendedId(BlockedNumberContract.BlockedNumbers.CONTENT_URI, id),
                null, null);
    }

    static String normalize(String number) {
        String n = PhoneNumberUtils.normalizeNumber(number);
        return TextUtils.isEmpty(n) ? number : n.toLowerCase(Locale.ROOT);
    }
}
