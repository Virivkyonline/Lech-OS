#!/usr/bin/env python3
"""Reads an Android Security Bulletin page (source.android.com/docs/security/bulletin/YYYY/YYYY-MM-01) and lists
its fixes with their AOSP commits, for the monthly update of LECH OS (gate 1: monthly update rehearsal, docs/14).

Output (JSON on stdout):
    {"published": "...", "fixes": [{"cve", "component", "patch_level", "severity", "type", "versions",
                                     "commits": [{"project", "sha"}]}]}

Only the standard library is used. The page layout: an <h2>/<h3> per security patch level and component, then a
table whose rows start with the CVE id; the References cell links to android.googlesource.com/<project>/+/<sha>.

Usage: python3 bulletin.py PAGE.html
"""

from __future__ import annotations

import json
import re
import sys
from html.parser import HTMLParser

CVE = re.compile(r"CVE-\d{4}-\d{4,}")
COMMIT = re.compile(r"https?://android\.googlesource\.com/(?P<project>[\w./+-]+?)/\+/(?P<sha>[0-9a-f]{7,40})\b")
LEVEL = re.compile(r"(\d{4}-\d{2}-\d{2}) security patch level", re.I)
SEVERITIES = ("Critical", "High", "Moderate", "Low")
TYPES = ("RCE", "EoP", "ID", "DoS")


class Bulletin(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.heading: str | None = None
        self.heading_text = ""
        self.patch_level = ""
        self.component = ""
        self.in_cell = False
        self.cell_text = ""
        self.cell_links: list[str] = []
        self.row: list[tuple[str, list[str]]] | None = None
        self.fixes: list[dict] = []
        self.text = []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag in ("h2", "h3", "h4"):
            self.heading, self.heading_text = tag, ""
        elif tag == "tr":
            self.row = []
        elif tag in ("td", "th") and self.row is not None:
            self.in_cell, self.cell_text, self.cell_links = True, "", []
        elif tag == "a" and self.in_cell and attrs.get("href"):
            self.cell_links.append(attrs["href"])

    def handle_endtag(self, tag):
        if tag == self.heading:
            text = " ".join(self.heading_text.split())
            level = LEVEL.search(text)
            if level:
                self.patch_level = level.group(1)
            elif tag != "h2" or not self.patch_level:
                self.component = text
            else:
                self.component = text
            self.heading = None
        elif tag in ("td", "th") and self.in_cell and self.row is not None:
            self.row.append((" ".join(self.cell_text.split()), self.cell_links))
            self.in_cell = False
        elif tag == "tr" and self.row is not None:
            self.finish_row(self.row)
            self.row = None

    def handle_data(self, data):
        self.text.append(data)
        if self.heading:
            self.heading_text += data
        if self.in_cell:
            self.cell_text += data

    def finish_row(self, row):
        if not row:
            return
        cve = CVE.search(row[0][0])
        if not cve:
            return
        texts = [t for t, _ in row]
        commits = []
        for _, links in row:
            for href in links:
                m = COMMIT.match(href)
                if m:
                    commits.append({"project": m.group("project"), "sha": m.group("sha")})
        self.fixes.append({
            "cve": cve.group(0),
            "component": self.component,
            "patch_level": self.patch_level,
            "severity": next((s for s in SEVERITIES if any(t == s for t in texts)), ""),
            "type": next((s for s in TYPES if any(t == s for t in texts)), ""),
            "versions": next((t for t in texts[1:] if re.fullmatch(r"[\d.,\s\-QPR]+", t) and re.search(r"\d", t)), ""),
            "commits": commits,
        })


def main() -> int:
    if len(sys.argv) != 2:
        sys.exit(__doc__.strip().splitlines()[-1])
    parser = Bulletin()
    parser.feed(open(sys.argv[1], encoding="utf-8", errors="replace").read())
    text = " ".join("".join(parser.text).split())
    published = re.search(r"Published (\w+ \d{1,2}, \d{4})", text)
    print(json.dumps({"published": published.group(1) if published else "", "fixes": parser.fixes},
                     ensure_ascii=False, indent=1))
    return 0


if __name__ == "__main__":
    sys.exit(main())
