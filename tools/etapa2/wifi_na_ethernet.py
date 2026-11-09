#!/usr/bin/env python3
"""Converts a pktmon record of the Windows Wi-Fi hotspot into Ethernet frames that tshark and analyzuj_audit.py read.

pktmon on the Wi-Fi Direct adapter of the Windows Mobile hotspot (vwifimp.sys) records the IEEE 802.11 data frames
(already decrypted, but with the Protected bit still set) and labels the file as Ethernet - tshark then decodes
nothing (9. 11. 2026, 24 h measurement: every frame "eth:data"). Each 802.11 data frame with an LLC/SNAP header
becomes an Ethernet frame: destination and source address by the To DS / From DS bits, the EtherType from SNAP, the
payload; an A-MSDU (several packets in one frame, usual for real traffic) becomes one Ethernet frame per subframe.
Null data frames (power save) and frames without SNAP are left out and counted by reason.

Input: classic pcap (editcap -F pcap), output: pcap with link type Ethernet. Standard library only.

Usage: python3 wifi_na_ethernet.py IN.pcap OUT.pcap
"""

from __future__ import annotations

import struct
import sys

SNAP = b"\xaa\xaa\x03"
LINKTYPE_ETHERNET = 1


def convert_frame(frame: bytes, stats: dict) -> list[bytes]:
    """Ethernet frames of one 802.11 frame (an A-MSDU carries several); counts what is left out in stats."""
    def skip(reason: str) -> list[bytes]:
        stats[reason] = stats.get(reason, 0) + 1
        return []
    if len(frame) < 24:
        return skip("príliš krátke")
    fc, flags = frame[0], frame[1]
    ftype, subtype = (fc >> 2) & 3, (fc >> 4) & 0xF
    if ftype != 2:
        return skip("riadiace a správy siete")
    if subtype & 4:   # null and CF data frames carry no payload (power save signalling)
        return skip("bez dát (úspora energie)")
    to_ds, from_ds = flags & 1, flags & 2
    a1, a2, a3 = frame[4:10], frame[10:16], frame[16:22]
    hdr = 24
    if to_ds and from_ds:
        a4, hdr = frame[24:30], 30
        da, sa = a3, a4
    elif to_ds:
        da, sa = a3, a2
    elif from_ds:
        da, sa = a1, a3
    else:
        da, sa = a1, a2
    amsdu = False
    if subtype & 8:   # QoS data: QoS control (bit 7: A-MSDU), and HT control when the Order bit is set
        amsdu = bool(frame[hdr] & 0x80)
        hdr += 2
        if flags & 0x80:
            hdr += 4
    body = frame[hdr:]
    if not amsdu:
        if len(body) < 8 or body[:3] != SNAP:
            return skip("bez hlavičky SNAP (šifrované?)")
        return [da + sa + body[6:8] + body[8:]]
    # A-MSDU: subframes "DA, SA, length, MSDU (with SNAP)", each padded to 4 bytes except the last.
    out, pos = [], 0
    while pos + 14 <= len(body):
        sub_da, sub_sa = body[pos:pos + 6], body[pos + 6:pos + 12]
        length = int.from_bytes(body[pos + 12:pos + 14], "big")
        msdu = body[pos + 14:pos + 14 + length]
        if len(msdu) < 8 or msdu[:3] != SNAP:
            break
        out.append(sub_da + sub_sa + msdu[6:8] + msdu[8:])
        pos += (14 + length + 3) & ~3
    if not out:
        return skip("A-MSDU bez čitateľných rámcov")
    stats["A-MSDU (rozbalené)"] = stats.get("A-MSDU (rozbalené)", 0) + 1
    return out


def main() -> int:
    if len(sys.argv) != 3:
        sys.exit(__doc__.strip().splitlines()[-1])
    data = open(sys.argv[1], "rb").read()
    if len(data) < 24:
        sys.exit("vstup nie je záznam pcap")
    magic = data[:4]
    endian = {b"\xd4\xc3\xb2\xa1": "<", b"\x4d\x3c\xb2\xa1": "<", b"\xa1\xb2\xc3\xd4": ">", b"\xa1\xb2\x3c\x4d": ">"}.get(magic)
    if not endian:
        sys.exit("vstup nie je klasický pcap (použite editcap -F pcap)")
    major, minor, zone, sigfigs, snaplen, _link = struct.unpack(endian + "HHiIII", data[4:24])
    out = bytearray(data[:4] + struct.pack(endian + "HHiIII", major, minor, zone, sigfigs, snaplen, LINKTYPE_ETHERNET))
    pos, total, kept, stats = 24, 0, 0, {}
    while pos + 16 <= len(data):
        ts_sec, ts_frac, incl, orig = struct.unpack(endian + "IIII", data[pos:pos + 16])
        frame = data[pos + 16:pos + 16 + incl]
        pos += 16 + incl
        total += 1
        for eth in convert_frame(frame, stats):
            kept += 1
            out += struct.pack(endian + "IIII", ts_sec, ts_frac, len(eth), len(eth)) + eth
    open(sys.argv[2], "wb").write(out)
    print(f"{total} rámcov 802.11 → {kept} rámcov Ethernet; " + ", ".join(f"{k}: {v}" for k, v in sorted(stats.items())))
    return 0


if __name__ == "__main__":
    sys.exit(main())
