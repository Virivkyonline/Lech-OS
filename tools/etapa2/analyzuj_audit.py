#!/usr/bin/env python3
"""Analyzes a packet capture from a Cuttlefish virtual phone and writes a report of outgoing connections.

Only traffic that originates from the Cuttlefish guest networks is evaluated, so traffic of the
host notebook itself is ignored. Hostnames are taken from DNS queries, TLS/QUIC SNI and HTTP Host
headers; IP addresses without a name can optionally be labelled with a reverse DNS (PTR) lookup.

Optionally, the DNS query log of the phone (adb shell dumpsys dnsresolver querylog) and its package
list (adb shell pm list packages -U) add a section that shows which app asked for which name.

A capture made in the phone itself (real phone, tools/etapa2/11-pixel.sh audit) is analysed with
--zariadenie: the phone's own packets are the outgoing ones of the Linux cooked capture (sll.pkttype 4),
labelled by interface (--rozhrania FILE, "ifindex<TAB>label") or address (--siete FILE, "label<TAB>address").
Private and carrier-internal destinations (router, DNS of the network) count as local network.

Usage:
    python3 tools/etapa2/analyzuj_audit.py CAPTURE.pcap --out REPORT.md [--allowlist FILE] [--ptr]
        [--title TEXT] [--strict] [--dns-log FILE --packages FILE] [--zariadenie [--rozhrania FILE] [--siete FILE]]

Requires tshark (Wireshark command line). Exit code 0 = report written; with --strict, exit code 1
when a connection is not covered by the allowlist.
"""

from __future__ import annotations

import argparse
import ipaddress
import json
import re
import socket
import subprocess
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path

# Guest networks created by cuttlefish-base (see /etc/init.d/cuttlefish-host-resources).
GUEST_NETS = [ipaddress.ip_network(n) for n in ("192.168.93.0/24", "192.168.94.0/23", "192.168.96.0/22")]
GUEST_FILTER = " || ".join(f"ip.src == {n}" for n in GUEST_NETS)
# Which virtual network a guest address belongs to (cuttlefish-host-resources address plan).
# The phone's Wi-Fi traffic leaves through the OpenWrt router VM, so "Wi-Fi/router" covers both.
SOURCE_NETS = [
    (ipaddress.ip_network("192.168.97.0/24"), "mobilná sieť telefónu"),
    (ipaddress.ip_network("192.168.93.0/24"), "mobilná sieť telefónu"),
    (ipaddress.ip_network("192.168.98.0/24"), "ethernet telefónu"),
    (ipaddress.ip_network("192.168.94.0/23"), "Wi-Fi/router OpenWrt"),
    (ipaddress.ip_network("192.168.96.0/24"), "Wi-Fi/router OpenWrt"),
]


DEVICE_MODE = False  # capture made in the phone itself (--zariadenie)
IFACE_LABELS: dict[str, str] = {}  # interface index -> network of the phone
CARRIER_NAT = ipaddress.ip_network("100.64.0.0/10")  # carrier-internal (CGNAT): DNS of the mobile operator


def read_pairs(path: Path) -> list[tuple[str, str]]:
    pairs = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip() and not line.startswith("#") and "\t" in line:
            a, _, b = line.partition("\t")
            pairs.append((a.strip(), b.strip()))
    return pairs


def use_phone_capture(siete: Path | None, rozhrania: Path | None) -> None:
    """Capture from the phone itself: its outgoing packets are its own traffic, whatever its address."""
    global GUEST_NETS, GUEST_FILTER, SOURCE_NETS, DEVICE_MODE
    DEVICE_MODE = True
    GUEST_FILTER = "sll.pkttype == 4 && (ip || ipv6)"
    nets = []
    for label, addr in read_pairs(siete) if siete else []:
        try:
            nets.append((ipaddress.ip_network(addr, strict=False), label or "telefón"))
        except ValueError:
            continue
    SOURCE_NETS = nets
    GUEST_NETS = [n for n, _ in nets]
    for index, label in read_pairs(rozhrania) if rozhrania else []:
        IFACE_LABELS[index] = label


def source_label(ip: str, ifindex: str) -> str:
    if DEVICE_MODE and ifindex in IFACE_LABELS:
        return IFACE_LABELS[ifindex]
    label = source_net(ip)
    return "telefón" if DEVICE_MODE and label == "iná" else label


def source_net(ip: str) -> str:
    try:
        addr = ipaddress.ip_address(ip)
    except ValueError:
        return "?"
    for net, label in SOURCE_NETS:
        if addr in net:
            return label
    return "iná"
LOCAL_PORTS = {67, 68, 5353, 1900}  # DHCP, mDNS, SSDP


@dataclass
class Destination:
    ip: str
    proto: str
    port: int
    packets: int = 0
    first_seen: float = float("inf")
    names: set[str] = field(default_factory=set)
    sources: set[str] = field(default_factory=set)


@dataclass
class Domain:
    name: str
    sources: set[str] = field(default_factory=set)
    count: int = 0
    first_seen: float = float("inf")


def run_tshark(pcap: Path, display_filter: str, fields: list[str]) -> list[list[str]]:
    cmd = ["tshark", "-r", str(pcap), "-n", "-Y", display_filter, "-T", "fields",
           "-E", "separator=\t", "-E", "aggregator=,", "-E", "occurrence=a"]
    for f in fields:
        cmd += ["-e", f]
    try:
        res = subprocess.run(cmd, capture_output=True, text=True, check=True)
    except FileNotFoundError:
        sys.exit("CHYBA: chýba tshark – nainštalujte ho (tools/etapa2/01-priprava-notebooku.sh).")
    except subprocess.CalledProcessError as e:
        sys.exit(f"CHYBA: tshark zlyhal: {e.stderr.strip()}")
    return [line.split("\t") for line in res.stdout.splitlines() if line.strip()]


def is_local(ip: str) -> bool:
    addr = ipaddress.ip_address(ip)
    return (addr.is_multicast or addr.is_link_local or ip == "255.255.255.255"
            or any(addr in net for net in GUEST_NETS)
            or (DEVICE_MODE and (addr.is_private or addr.is_loopback or addr in CARRIER_NAT)))


def load_allowlist(path: Path | None) -> list[str]:
    if not path or not path.exists():
        return []
    entries = []
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.split("#", 1)[0].strip()
        if line:
            entries.append(line.lower())
    return entries


def allowed(names: set[str], ip: str | None, allowlist: list[str]) -> bool:
    for entry in allowlist:
        if "/" in entry or entry.replace(".", "").isdigit():
            try:
                if ip and ipaddress.ip_address(ip) in ipaddress.ip_network(entry, strict=False):
                    return True
            except ValueError:
                pass
            continue
        for n in names:
            if n == entry or n.endswith("." + entry):
                return True
    return False


def ptr(ip: str) -> str | None:
    socket.setdefaulttimeout(2)
    try:
        return socket.gethostbyaddr(ip)[0].lower()
    except (OSError, UnicodeError):
        return None


def analyze(pcap: Path, use_ptr: bool) -> tuple[dict, dict, dict, float]:
    # Pass 1: map IP addresses to names from DNS answers (any direction).
    ip_names: dict[str, set[str]] = defaultdict(set)
    for row in run_tshark(pcap, "dns.flags.response == 1", ["dns.qry.name", "dns.a", "dns.aaaa"]):
        row += [""] * (3 - len(row))
        qname = row[0].split(",")[0].lower().rstrip(".")
        for ip in filter(None, (row[1] + "," + row[2]).split(",")):
            if qname:
                ip_names[ip].add(qname)

    # Pass 2: everything sent by the guest.
    fields = ["frame.time_relative", "ip.src", "ipv6.src", "ip.dst", "ipv6.dst", "ip.proto", "ipv6.nxt",
              "tcp.dstport", "udp.dstport",
              "dns.flags.response", "dns.qry.name", "tls.handshake.extensions_server_name", "http.host",
              "sll.ifindex"]
    destinations: dict[tuple, Destination] = {}
    domains: dict[str, Domain] = {}
    local = defaultdict(int)
    duration = 0.0

    def add_domain(name: str, source: str, t: float) -> None:
        name = name.lower().rstrip(".")
        if not name:
            return
        d = domains.setdefault(name, Domain(name))
        d.sources.add(source)
        d.count += 1
        d.first_seen = min(d.first_seen, t)

    for row in run_tshark(pcap, GUEST_FILTER, fields):
        row += [""] * (len(fields) - len(row))
        t_rel, src4, src6, dst4, dst6, proto4, proto6, tport, uport, dns_resp, qname, sni, host, ifindex = row
        t = max(0.0, float(t_rel or 0))  # "any" captures can be slightly out of order
        duration = max(duration, t)
        # First value only: ICMP errors carry the header of the packet they refer to.
        src = (src4 or src6).split(",")[0]
        dst = (dst4 or dst6).split(",")[0]
        proto = (proto4 or proto6).split(",")[0]
        if not dst:
            continue
        proto_name = {"6": "TCP", "17": "UDP", "1": "ICMP", "58": "ICMPv6"}.get(proto, proto or "?")
        port = int((tport or uport or "0").split(",")[0] or 0)
        # DNS queries to the network's own resolver (router) are local traffic, but their names count.
        if qname and dns_resp in ("0", "False", "false") and (is_local(dst) or port in LOCAL_PORTS):
            for q in qname.split(","):
                add_domain(q, "DNS", t)
        if is_local(dst) or port in LOCAL_PORTS:
            local[f"{proto_name}/{port}"] += 1
            continue
        key = (dst, proto_name, port)
        dest = destinations.setdefault(key, Destination(dst, proto_name, port))
        dest.packets += 1
        dest.sources.add(source_label(src, ifindex.split(",")[0]))
        dest.first_seen = min(dest.first_seen, t)
        if qname and dns_resp in ("0", "False", "false"):
            for q in qname.split(","):
                add_domain(q, "DNS", t)
        for s in filter(None, sni.split(",")):
            add_domain(s, "TLS", t)
            dest.names.add(s.lower())
        for h in filter(None, host.split(",")):
            h = h.split(":")[0]
            add_domain(h, "HTTP", t)
            dest.names.add(h.lower())

    for dest in destinations.values():
        dest.names |= ip_names.get(dest.ip, set())
        if use_ptr and not dest.names:
            name = ptr(dest.ip)
            if name:
                dest.names.add(f"{name} (PTR)")
    return destinations, domains, local, duration


# Android system UIDs without a package of their own (system/core/libcutils/include/private/android_filesystem_config.h).
AID_NAMES = {0: "root", 1000: "systém Android", 1001: "telefónia (radio)", 1002: "bluetooth", 1010: "wifi",
             1013: "médiá", 1021: "gps", 1051: "dns", 1052: "dns_tether", 1073: "network_stack",
             9999: "nobody"}
QUERY_RE = re.compile(r"time=(?P<time>[\d:.]+) netId=(?P<net>\d+) uid=(?P<uid>\d+) pid=(?P<pid>-?\d+) "
                      r"hostname=(?P<host>\S*) answer=\[(?P<ans>[^\]]*)\]")


def load_packages(path: Path) -> dict[int, list[str]]:
    """Package list from `pm list packages -U` (lines like "package:com.android.phone uid:1001")."""
    packages: dict[int, list[str]] = defaultdict(list)
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        m = re.match(r"package:(\S+) uid:(\d+)", line.strip())
        if m:
            packages[int(m.group(2))].append(m.group(1))
    return packages


def app_label(uid: int, packages: dict[int, list[str]]) -> str:
    app_id = uid % 100000          # UIDs of other users are user * 100000 + app ID
    names = sorted(packages.get(app_id, []))
    label = AID_NAMES.get(app_id, "")
    if names:
        shown = ", ".join(f"`{n}`" for n in names[:3]) + (f" a {len(names) - 3} ďalších" if len(names) > 3 else "")
        label = f"{label}: {shown}" if label else shown
    return f"{label or 'neznáma aplikácia'} (UID {uid})"


def dns_by_app(dns_log: Path, packages: dict[int, list[str]], domains: dict) -> list[dict]:
    """Groups the phone's DNS query log by app and masked name ("g***"), with candidate domains from
    the capture that start with the same letter."""
    groups: dict[tuple[int, str], dict] = {}
    for line in dns_log.read_text(encoding="utf-8", errors="replace").splitlines():
        m = QUERY_RE.search(line)
        if not m:
            continue
        uid, host = int(m.group("uid")), m.group("host")
        g = groups.setdefault((uid, host), {"uid": uid, "aplikacia": app_label(uid, packages), "dopyt": host,
                                            "pocet": 0, "prvykrat": m.group("time")})
        g["pocet"] += 1
    for g in groups.values():
        letter = g["dopyt"][:1].lower()
        g["mozne_domeny"] = sorted(n for n in domains if letter and n.lower().startswith(letter))[:5]
    return sorted(groups.values(), key=lambda g: (g["prvykrat"], g["uid"]))


def fmt_time(t: float) -> str:
    return f"{t:,.1f} s".replace(",", " ") if t != float("inf") else "–"


def write_report(out: Path, title: str, pcap: Path, destinations: dict, domains: dict, local: dict,
                 duration: float, allowlist: list[str], by_app: list[dict] | None = None) -> tuple[int, dict]:
    use_allow = bool(allowlist)
    unknown = 0
    lines = [f"# {title}", "",
             f"- Dátum analýzy: {datetime.now(timezone.utc).astimezone().strftime('%Y-%m-%d %H:%M %Z')}",
             f"- Súbor so zachytenou prevádzkou: `{pcap.name}`",
             f"- Posledný paket telefónu od začiatku záznamu: {fmt_time(duration)}",
             f"- Počet rôznych cieľov mimo telefónu: **{len(destinations)}**",
             f"- Počet rôznych domén: **{len(domains)}**",
             f"- Zoznam povolených spojení: {'použitý' if use_allow else 'nepoužitý (len mapovanie)'}", ""]

    lines += ["## Domény", "", "Zdroj: DNS = dopyt na DNS, TLS = názov v šifrovanom spojení (SNI), HTTP = nešifrované HTTP.", ""]
    header = "| Doména | Zdroj | Počet | Prvýkrát |" + (" Stav |" if use_allow else "")
    lines += [header, "|" + "---|" * (5 if use_allow else 4)]
    domain_rows = []
    for d in sorted(domains.values(), key=lambda d: d.first_seen):
        row = f"| `{d.name}` | {', '.join(sorted(d.sources))} | {d.count} | {fmt_time(d.first_seen)} |"
        ok = None
        if use_allow:
            ok = allowed({d.name}, None, allowlist)
            unknown += 0 if ok else 1
            row += " povolené |" if ok else " **NEZNÁME** |"
        lines.append(row)
        domain_rows.append({"domena": d.name, "zdroj": sorted(d.sources), "pocet": d.count,
                            "prvykrat_s": round(d.first_seen, 1), "povolene": ok})
    if not domains:
        lines.append("| – | – | – | – |" + (" – |" if use_allow else ""))

    if by_app is not None:
        lines += ["", "## Kto sa pýtal – DNS dopyty podľa aplikácií (z telefónu)", "",
                  "Zo záznamu DNS resolvera v telefóne (`dumpsys dnsresolver querylog`, posledných 200 dopytov). "
                  "Android z názvu ukáže len prvé písmeno; možné domény sú domény zo záznamu prevádzky, ktoré "
                  "začínajú rovnakým písmenom.", "",
                  "| Aplikácia | Dopyt | Možné domény | Počet | Prvýkrát (čas telefónu) |", "|---|---|---|---|---|"]
        for g in by_app:
            cands = ", ".join(f"`{d}`" for d in g["mozne_domeny"]) or "–"
            lines.append(f"| {g['aplikacia']} | `{g['dopyt']}` | {cands} | {g['pocet']} | {g['prvykrat']} |")
        if not by_app:
            lines.append("| – | – | – | – | – |")

    lines += ["", "## Ciele (IP adresa, protokol, port)", "",
              ("Odkiaľ: sieť telefónu (Wi-Fi, mobilná sieť), z ktorej paket odišiel. Záznam je priamo z telefónu."
               if DEVICE_MODE else
               "Odkiaľ: sieť virtuálneho telefónu, z ktorej paket odišiel. Prevádzka telefónu cez Wi-Fi odchádza "
               "cez virtuálny router OpenWrt, preto má rovnaké označenie ako prevádzka samotného routra."), ""]
    header = "| IP adresa | Protokol/port | Názvy | Odkiaľ | Pakety | Prvýkrát |" + (" Stav |" if use_allow else "")
    lines += [header, "|" + "---|" * (7 if use_allow else 6)]
    dest_rows = []
    for dest in sorted(destinations.values(), key=lambda d: d.first_seen):
        names = ", ".join(f"`{n}`" for n in sorted(dest.names)) or "–"
        origin = ", ".join(sorted(dest.sources)) or "–"
        row = f"| {dest.ip} | {dest.proto}/{dest.port} | {names} | {origin} | {dest.packets} | {fmt_time(dest.first_seen)} |"
        ok = None
        if use_allow:
            ok = allowed({n.replace(" (ptr)", "") for n in dest.names}, dest.ip, allowlist)
            unknown += 0 if ok else 1
            row += " povolené |" if ok else " **NEZNÁME** |"
        lines.append(row)
        dest_rows.append({"ip": dest.ip, "protokol": dest.proto, "port": dest.port, "nazvy": sorted(dest.names),
                          "odkial": sorted(dest.sources),
                          "pakety": dest.packets, "prvykrat_s": round(dest.first_seen, 1), "povolene": ok})
    if not destinations:
        lines.append("| – | – | – | – | – | – |" + (" – |" if use_allow else ""))

    lines += ["", "## Lokálna sieť (router, DNS siete, multicast)" if DEVICE_MODE
              else "## Lokálna prevádzka (neopúšťa notebook)", ""]
    if local:
        lines += ["| Protokol/port | Pakety |", "|---|---|"]
        lines += [f"| {k} | {v} |" for k, v in sorted(local.items())]
    else:
        lines.append("Žiadna.")

    lines += ["", "## Obmedzenia", "",
              "- Obsah šifrovaných spojení nie je viditeľný – správa ukazuje, kam a koľko, nie čo.",
              "- Ak telefón používa šifrované DNS (DNS over TLS, port 853), názvy domén pochádzajú len z TLS/HTTP "
              "a z PTR záznamov; spojenie na port 853 je spojenie na DNS server testovacieho prostredia.",
              "- Názvy označené (PTR) sú odhad z reverzného DNS, nie názov, ktorý telefón skutočne použil."]
    if by_app is not None:
        lines.append("- DNS dopyty podľa aplikácií: Android z názvu ukáže len prvé písmeno, priradenie k doméne je preto "
                     "odhad. Spojenia bez DNS dopytu (priamo na IP adresu) v tejto časti nie sú.")
    lines.append("")
    if use_allow:
        lines.insert(7, f"- Neznáme položky: **{unknown}**")
    out.write_text("\n".join(lines), encoding="utf-8")
    summary = {"domeny": domain_rows, "ciele": dest_rows, "lokalne": dict(local), "dlzka_s": round(duration, 1),
               "nezname": unknown if use_allow else None}
    if by_app is not None:
        summary["dns_podla_aplikacii"] = by_app
    return unknown, summary


def main() -> int:
    ap = argparse.ArgumentParser(description="Analýza sieťového auditu virtuálneho telefónu Cuttlefish")
    ap.add_argument("pcap", type=Path)
    ap.add_argument("--out", type=Path, required=True, help="cesta k výslednej správe (.md); vedľa vznikne aj .json")
    ap.add_argument("--allowlist", type=Path, help="zoznam povolených domén a IP rozsahov")
    ap.add_argument("--title", default="Sieťový audit virtuálneho telefónu")
    ap.add_argument("--ptr", action="store_true", help="doplniť názvy IP adries cez reverzné DNS")
    ap.add_argument("--strict", action="store_true", help="návratový kód 1, ak existuje neznáme spojenie")
    ap.add_argument("--dns-log", type=Path, help="výstup `adb shell dumpsys dnsresolver querylog`")
    ap.add_argument("--packages", type=Path, help="výstup `adb shell pm list packages -U`")
    ap.add_argument("--zariadenie", action="store_true", help="záznam zo samotného telefónu (tcpdump -i any v telefóne)")
    ap.add_argument("--rozhrania", type=Path, help="pri --zariadenie: rozhrania telefónu, riadky 'index<TAB>označenie'")
    ap.add_argument("--siete", type=Path, help="pri --zariadenie: adresy telefónu, riadky 'označenie<TAB>adresa'")
    args = ap.parse_args()

    if not args.pcap.exists():
        sys.exit(f"CHYBA: súbor {args.pcap} neexistuje.")
    if args.zariadenie or args.siete or args.rozhrania:
        use_phone_capture(args.siete, args.rozhrania)
    destinations, domains, local, duration = analyze(args.pcap, args.ptr)
    allowlist = load_allowlist(args.allowlist)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    by_app = None
    if args.dns_log and args.dns_log.exists():
        packages = load_packages(args.packages) if args.packages and args.packages.exists() else {}
        by_app = dns_by_app(args.dns_log, packages, domains)
    unknown, summary = write_report(args.out, args.title, args.pcap, destinations, domains, local, duration,
                                    allowlist, by_app)
    args.out.with_suffix(".json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Správa: {args.out} (cieľov: {len(destinations)}, domén: {len(domains)}"
          + (f", neznámych: {unknown})" if allowlist else ")"))
    return 1 if (args.strict and allowlist and unknown) else 0


if __name__ == "__main__":
    sys.exit(main())
