/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.spravy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * An MMS notification from the network (WAP push). Downloading and decoding MMS is not part of the first version:
 * the user is told that an MMS arrived; the message stays on the server of the operator.
 */
public class MmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Notifier.showMms(c);
    }
}
