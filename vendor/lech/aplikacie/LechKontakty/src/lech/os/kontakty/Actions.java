/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.kontakty;

import lech.os.ui.Ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.Toast;

import java.util.function.Consumer;

/** Calls, messages, e-mails and small dialogs shared by the screens. */
final class Actions {
    static final String[] PERMISSIONS = {
        Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG, Manifest.permission.CALL_PHONE,
    };

    private Actions() {
    }

    static boolean granted(Context c, String permission) {
        return c.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean canReadContacts(Context c) {
        return granted(c, Manifest.permission.READ_CONTACTS) && granted(c, Manifest.permission.WRITE_CONTACTS);
    }

    /** Calls a number through Telecom (the in-call screen is the one of the phone app). */
    static void call(Activity a, String number) {
        if (TextUtils.isEmpty(number)) return;
        Uri uri = Uri.fromParts("tel", number, null);
        try {
            if (granted(a, Manifest.permission.CALL_PHONE)) {
                a.getSystemService(TelecomManager.class).placeCall(uri, new Bundle());
            } else {
                a.startActivity(new Intent(Intent.ACTION_DIAL, uri));
            }
        } catch (Exception e) {
            Toast.makeText(a, R.string.call_failed, Toast.LENGTH_SHORT).show();
        }
    }

    static void message(Activity a, String number) {
        start(a, new Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)));
    }

    static void email(Activity a, String address) {
        start(a, new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", address, null)));
    }

    static void start(Activity a, Intent i) {
        try {
            a.startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(a, R.string.no_app, Toast.LENGTH_SHORT).show();
        }
    }

    static void confirm(Activity a, int message, int positive, Runnable onYes) {
        new AlertDialog.Builder(a)
                .setMessage(message)
                .setPositiveButton(positive, (d, w) -> onYes.run())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    static void askText(Activity a, int title, String initial, Consumer<String> onOk) {
        EditText e = new EditText(a);
        e.setSingleLine(true);
        e.setText(initial);
        e.setHint(R.string.group_name);
        int pad = Ui.dp(a, 20);
        android.widget.FrameLayout f = new android.widget.FrameLayout(a);
        f.setPadding(pad, Ui.dp(a, 8), pad, 0);
        f.addView(e);
        new AlertDialog.Builder(a)
                .setTitle(title)
                .setView(f)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    String s = e.getText().toString().trim();
                    if (!s.isEmpty()) onOk.accept(s);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    static String phoneType(Context c, int type, String label) {
        switch (type) {
            case Phone.TYPE_MOBILE: return c.getString(R.string.type_mobile);
            case Phone.TYPE_HOME: return c.getString(R.string.type_home);
            case Phone.TYPE_WORK: return c.getString(R.string.type_work);
            case Phone.TYPE_CUSTOM: return TextUtils.isEmpty(label) ? c.getString(R.string.type_other) : label;
            default: return c.getString(R.string.type_other);
        }
    }

    static String emailType(Context c, int type, String label) {
        switch (type) {
            case Email.TYPE_HOME: return c.getString(R.string.type_home);
            case Email.TYPE_WORK: return c.getString(R.string.type_work);
            case Email.TYPE_MOBILE: return c.getString(R.string.type_mobile);
            case Email.TYPE_CUSTOM: return TextUtils.isEmpty(label) ? c.getString(R.string.type_other) : label;
            default: return c.getString(R.string.type_other);
        }
    }
}
