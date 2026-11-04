#!/usr/bin/env python3
"""Analyzes a packet capture from a Cuttlefish virtual phone and writes a report of outgoing connections.

Only traffic that originates from the Cuttlefish guest networks is evaluated, so traffic of the
host notebook itself is ignored. Hostnames are taken from DNS queries, TLS/QUIC SNI and HTTP Host
headers; IP addresses without a name can optionally be labelled with a reverse DNS (PTR) lookup.

Usage:
    python3 tools/etapa2/analyzuj_audit.py CAPTURE.pcap --out REPORT.md [--allowlist FILE] [--ptr]
        [--title TEXT] [--strict]

Requires tshark (Wireshark command line). Exit code 0 = report written; with --strict, exit code 1
when a connection is not covered by the allowlist.
"""

from __future__ import annotations

import argparse
import ipaddress
import json
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
LOCAL_PORTS = {67, 68, 5353, 1900}  # DHCP, mDNS, SSDP


@dataclass
class Destination:
    ip: str
    proto: str
    port: int
    packets: int = 0
    first_seen: float = float("inf")
    names: set[str] = field(default_factory=set)


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
            or any(addr in net for net in GUEST_NETS))


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
    fields = ["frame.time_relative", "ip.dst", "ip.proto", "tcp.dstport", "udp.dstport",
              "dns.flags.response", "dns.qry.name", "tls.handshake.extensions_server_name", "http.host"]
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
        t_rel, dst, proto, tport, uport, dns_resp, qname, sni, host = row
        t = max(0.0, float(t_rel or 0))  # "any" captures can be slightly out of order
        duration = max(duration, t)
        if not dst:
            continue
        proto_name = {"6": "TCP", "17": "UDP", "1": "ICMP"}.get(proto.split(",")[0], proto or "?")
        port = int((tport or uport or "0").split(",")[0] or 0)
        if is_local(dst) or port in LOCAL_PORTS:
            local[f"{proto_name}/{port}"] += 1
            continue
        key = (dst, proto_name, port)
        dest = destinations.setdefault(key, Destination(dst, proto_name, port))
        dest.packets += 1
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


def fmt_time(t: float) -> str:
    return f"{t:,.1f} s".replace(",", " ") if t != float("inf") else "–"


def write_report(out: Path, title: str, pcap: Path, destinations: dict, domains: dict, local: dict,
                 duration: float, allowlist: list[str]) -> tuple[int, dict]:
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
        if use_allow:
            ok = allowed({d.name}, None, allowlist)
            unknown += 0 if ok else 1
            row += " povolené |" if ok else " **NEZNÁME** |"
        lines.append(row)
        domain_rows.append({"domena": d.name, "zdroj": sorted(d.sources), "pocet": d.count,
                            "prvykrat_s": round(d.first_seen, 1)})
    if not domains:
        lines.append("| – | – | – | – |" + (" – |" if use_allow else ""))

    lines += ["", "## Ciele (IP adresa, protokol, port)", ""]
    header = "| IP adresa | Protokol/port | Názvy | Pakety | Prvýkrát |" + (" Stav |" if use_allow else "")
    lines += [header, "|" + "---|" * (6 if use_allow else 5)]
    dest_rows = []
    for dest in sorted(destinations.values(), key=lambda d: d.first_seen):
        names = ", ".join(f"`{n}`" for n in sorted(dest.names)) or "–"
        row = f"| {dest.ip} | {dest.proto}/{dest.port} | {names} | {dest.packets} | {fmt_time(dest.first_seen)} |"
        if use_allow:
            ok = allowed({n.replace(" (ptr)", "") for n in dest.names}, dest.ip, allowlist)
            unknown += 0 if ok else 1
            row += " povolené |" if ok else " **NEZNÁME** |"
        lines.append(row)
        dest_rows.append({"ip": dest.ip, "protokol": dest.proto, "port": dest.port, "nazvy": sorted(dest.names),
                          "pakety": dest.packets, "prvykrat_s": round(dest.first_seen, 1)})
    if not destinations:
        lines.append("| – | – | – | – | – |" + (" – |" if use_allow else ""))

    lines += ["", "## Lokálna prevádzka (neopúšťa notebook)", ""]
    if local:
        lines += ["| Protokol/port | Pakety |", "|---|---|"]
        lines += [f"| {k} | {v} |" for k, v in sorted(local.items())]
    else:
        lines.append("Žiadna.")

    lines += ["", "## Obmedzenia", "",
              "- Obsah šifrovaných spojení nie je viditeľný – správa ukazuje, kam a koľko, nie čo.",
              "- Ak telefón používa šifrované DNS (DNS over TLS, port 853), názvy domén pochádzajú len z TLS/HTTP "
              "a z PTR záznamov; spojenie na port 853 je spojenie na DNS server testovacieho prostredia.",
              "- Názvy označené (PTR) sú odhad z reverzného DNS, nie názov, ktorý telefón skutočne použil.", ""]
    if use_allow:
        lines.insert(7, f"- Neznáme položky: **{unknown}**")
    out.write_text("\n".join(lines), encoding="utf-8")
    summary = {"domeny": domain_rows, "ciele": dest_rows, "lokalne": dict(local), "dlzka_s": round(duration, 1),
               "nezname": unknown if use_allow else None}
    return unknown, summary


def main() -> int:
    ap = argparse.ArgumentParser(description="Analýza sieťového auditu virtuálneho telefónu Cuttlefish")
    ap.add_argument("pcap", type=Path)
    ap.add_argument("--out", type=Path, required=True, help="cesta k výslednej správe (.md); vedľa vznikne aj .json")
    ap.add_argument("--allowlist", type=Path, help="zoznam povolených domén a IP rozsahov")
    ap.add_argument("--title", default="Sieťový audit virtuálneho telefónu")
    ap.add_argument("--ptr", action="store_true", help="doplniť názvy IP adries cez reverzné DNS")
    ap.add_argument("--strict", action="store_true", help="návratový kód 1, ak existuje neznáme spojenie")
    args = ap.parse_args()

    if not args.pcap.exists():
        sys.exit(f"CHYBA: súbor {args.pcap} neexistuje.")
    destinations, domains, local, duration = analyze(args.pcap, args.ptr)
    allowlist = load_allowlist(args.allowlist)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    unknown, summary = write_report(args.out, args.title, args.pcap, destinations, domains, local, duration, allowlist)
    args.out.with_suffix(".json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Správa: {args.out} (cieľov: {len(destinations)}, domén: {len(domains)}"
          + (f", neznámych: {unknown})" if allowlist else ")"))
    return 1 if (args.strict and allowlist and unknown) else 0


if __name__ == "__main__":
    sys.exit(main())
