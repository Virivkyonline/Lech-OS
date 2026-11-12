/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.pdf;

import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

/** One PDF document: a file of the shared storage (path) or a document picked in the system file picker (uri). */
final class Pdf {
    /** Absolute path of a file on the shared storage, or null. */
    String path;
    /** content:// URI of a picked document (persisted read grant), or null. */
    String uri;
    String name;
    /** Size in bytes, -1 when unknown. */
    long size = -1;
    /** Last modification in ms, 0 when unknown. */
    long modified;
    /** When it was opened (recent) or marked (favourite), in ms. */
    long time;

    String key() {
        return path != null ? path : uri;
    }

    /** The URI handed to the viewer, the print spooler and share targets. */
    Uri contentUri() {
        return path != null ? PdfProvider.uriFor(new File(path)) : Uri.parse(uri);
    }

    Pdf copy() {
        Pdf p = new Pdf();
        p.path = path;
        p.uri = uri;
        p.name = name;
        p.size = size;
        p.modified = modified;
        p.time = time;
        return p;
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        if (path != null) o.put("p", path);
        if (uri != null) o.put("u", uri);
        o.put("n", name);
        o.put("s", size);
        o.put("m", modified);
        o.put("t", time);
        return o;
    }

    static Pdf fromJson(JSONObject o) {
        Pdf p = new Pdf();
        p.path = o.optString("p", null);
        p.uri = o.optString("u", null);
        p.name = o.optString("n", "");
        p.size = o.optLong("s", -1);
        p.modified = o.optLong("m", 0);
        p.time = o.optLong("t", 0);
        return p.key() == null ? null : p;
    }
}
