#!/usr/bin/env python3
"""LECH OS update server v0 (task E2-08) and helper for A/B OTA packages.

Commands:
    info OTA.zip
        Prints JSON with what update_engine needs to apply the package over the network: the offset
        and size of payload.bin inside the zip (it is stored uncompressed) and the headers from
        payload_properties.txt.
    publish OTA.zip DIR --version V
        Copies the package into DIR and writes DIR/aktualizacie.json - the update list the phone
        reads (version, file, size, SHA-256, offset, headers, build fingerprint of the package).
    serve DIR [--host 127.0.0.1] [--port 8008]
        Serves DIR over HTTP with Range requests (update_engine downloads the payload in parts;
        python3 -m http.server cannot do that). Logs every request.

Only the standard library is used, so the same server can later run on any Linux host.
"""

from __future__ import annotations

import argparse
import hashlib
import http.server
import json
import os
import shutil
import struct
import sys
import zipfile
from pathlib import Path

PAYLOAD = "payload.bin"
PROPERTIES = "payload_properties.txt"
PAYLOAD_MAGIC = b"CrAU"


def ota_info(path: Path) -> dict:
    with zipfile.ZipFile(path) as archive:
        entry = archive.getinfo(PAYLOAD)
        if entry.compress_type != zipfile.ZIP_STORED:
            raise SystemExit(f"{path}: {PAYLOAD} is compressed; update_engine needs it stored")
        headers = archive.read(PROPERTIES).decode().strip().splitlines()
        metadata = {}
        if "META-INF/com/android/metadata" in archive.namelist():
            for line in archive.read("META-INF/com/android/metadata").decode().splitlines():
                key, _, value = line.partition("=")
                metadata[key] = value
    # The offset is that of the data, after the local file header (its extra field can differ from
    # the central directory, so read it from the file itself).
    with open(path, "rb") as fp:
        fp.seek(entry.header_offset)
        header = struct.unpack(zipfile.structFileHeader, fp.read(zipfile.sizeFileHeader))
        offset = entry.header_offset + zipfile.sizeFileHeader + header[-2] + header[-1]
        fp.seek(offset)
        if fp.read(4) != PAYLOAD_MAGIC:
            raise SystemExit(f"{path}: {PAYLOAD} does not start with the payload magic {PAYLOAD_MAGIC!r}")
    return {"offset": offset, "size": entry.file_size, "headers": headers,
            "post_build": metadata.get("post-build", ""), "post_timestamp": metadata.get("post-timestamp", "")}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as fp:
        for block in iter(lambda: fp.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def publish(ota: Path, directory: Path, version: str) -> dict:
    directory.mkdir(parents=True, exist_ok=True)
    target = directory / f"lech-os-{version}-ota.zip"
    shutil.copyfile(ota, target)
    info = ota_info(target)
    entry = {"verzia": version, "subor": target.name, "velkost": target.stat().st_size,
             "sha256": sha256(target), **info}
    (directory / "aktualizacie.json").write_text(
        json.dumps({"format": 0, "aktualizacie": [entry]}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8")
    return entry


class RangeHandler(http.server.SimpleHTTPRequestHandler):
    """Static files with single-range support (RFC 9110: bytes=start-end, bytes=start-, bytes=-n)."""

    def send_head(self):
        self._remaining = None
        path = Path(self.translate_path(self.path))
        if not path.is_file():
            return super().send_head()
        size = path.stat().st_size
        start, end = 0, size - 1
        partial = False
        spec = self.headers.get("Range", "")
        if spec.startswith("bytes=") and "," not in spec:
            first, _, last = spec[6:].strip().partition("-")
            try:
                if first:
                    start = int(first)
                    end = min(int(last), size - 1) if last else size - 1
                else:
                    start = max(size - int(last), 0)
                partial = True
            except ValueError:
                partial = False
            if partial and start > end:
                self.send_response(416)
                self.send_header("Content-Range", f"bytes */{size}")
                self.end_headers()
                return None
        fp = open(path, "rb")
        fp.seek(start)
        self.send_response(206 if partial else 200)
        self.send_header("Content-Type", self.guess_type(str(path)))
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(end - start + 1))
        if partial:
            self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        self.end_headers()
        self._remaining = end - start + 1
        return fp

    def copyfile(self, source, outputfile):
        remaining = getattr(self, "_remaining", None)
        while remaining is None or remaining > 0:
            block = source.read(1 << 20 if remaining is None else min(1 << 20, remaining))
            if not block:
                break
            outputfile.write(block)
            if remaining is not None:
                remaining -= len(block)

    def log_message(self, fmt, *args):
        sys.stderr.write("server: %s %s\n" % (self.address_string(), fmt % args))


def serve(directory: Path, host: str, port: int) -> None:
    handler = lambda *a, **kw: RangeHandler(*a, directory=str(directory), **kw)  # noqa: E731
    with http.server.ThreadingHTTPServer((host, port), handler) as server:
        print(f"Server aktualizácií LECH OS v0: http://{host}:{port}/ ({directory})", flush=True)
        server.serve_forever()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = parser.add_subparsers(dest="command", required=True)
    p_info = sub.add_parser("info")
    p_info.add_argument("ota", type=Path)
    p_pub = sub.add_parser("publish")
    p_pub.add_argument("ota", type=Path)
    p_pub.add_argument("directory", type=Path)
    p_pub.add_argument("--version", required=True)
    p_srv = sub.add_parser("serve")
    p_srv.add_argument("directory", type=Path)
    p_srv.add_argument("--host", default="127.0.0.1")
    p_srv.add_argument("--port", type=int, default=8008)
    args = parser.parse_args()

    if args.command == "info":
        print(json.dumps(ota_info(args.ota), indent=2))
    elif args.command == "publish":
        print(json.dumps(publish(args.ota, args.directory, args.version), ensure_ascii=False, indent=2))
    else:
        os.chdir(args.directory)
        serve(args.directory.resolve(), args.host, args.port)
    return 0


if __name__ == "__main__":
    sys.exit(main())
