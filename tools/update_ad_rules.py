#!/usr/bin/env python3
"""
合并广告域名规则 → app/src/main/assets/ad-rules.runtime.json

  手工规则 rules/ad-rules.base.json（allow / block / observe，针对闪动校园等抓包整理）
+ GitHub 现成规则 anti-AD（MIT，privacy-protection-tools/anti-AD，面向国内广告/追踪）

规则：
- allow 优先：与 allow、observe 冲突的外部域名一律丢掉（observe 仍保持“默认放行、可选拦截”）。
- 永不拦截 PROTECTED 里的后缀（学校、闪动校园本体、政府/教育网）。
- 仅用 Python 标准库；离线时保留上次生成的文件。

用法：python tools/update_ad_rules.py
"""
import json
import os
import sys
import time
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = os.path.join(ROOT, "rules", "ad-rules.base.json")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "ad-rules.runtime.json")

SOURCES = [
    ("anti-AD", "https://raw.githubusercontent.com/privacy-protection-tools/anti-AD/master/anti-ad-domains.txt"),
]
MIRRORS = [
    "https://cdn.jsdelivr.net/gh/privacy-protection-tools/anti-AD@master/anti-ad-domains.txt",
]
PROTECTED = [
    "edu.cn", "ecut.edu.cn", "gov.cn",
    "lptiyu.com",            # 闪动校园本体
    "weixin.qq.com", "wechat.com", "alipay.com", "taobao.com",
]


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "tangli-rules/1.0"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read().decode("utf-8", "replace")


def parse_domains(text):
    out = []
    for line in text.splitlines():
        line = line.strip().lower()
        if not line or line[0] in "#!":
            continue
        if line.startswith("0.0.0.0 ") or line.startswith("127.0.0.1 "):
            line = line.split()[1]
        if "/" in line or "*" in line or " " in line or "." not in line:
            continue
        out.append(line.rstrip("."))
    return out


def related(d, s):
    """d 与 s 是同一域名，或一方是另一方的子域。"""
    return d == s or d.endswith("." + s) or s.endswith("." + d)


def under(d, s):
    return d == s or d.endswith("." + s)


def main():
    base = json.load(open(BASE, encoding="utf-8"))
    allow = set(base.get("allow", []))
    observe = set(base.get("observe", []))
    block = set(base.get("block", []))
    keep_out = allow | observe

    external, used = set(), []
    for name, url in SOURCES:
        text = None
        for u in [url] + MIRRORS:
            try:
                text = fetch(u)
                break
            except Exception as e:  # noqa
                print("  fetch failed:", u, e, file=sys.stderr)
        if text is None:
            print("无法下载 %s，保留旧文件" % name, file=sys.stderr)
            return 1
        ver = next((l.split("=", 1)[1] for l in text.splitlines() if l.startswith("#VER=")), "")
        doms = parse_domains(text)
        used.append({"name": name, "url": url, "version": ver, "count": len(doms)})
        external.update(doms)

    # 冲突过滤：按后缀索引 keep_out，避免 O(n*m)
    def labels_suffixes(d):
        parts = d.split(".")
        return {".".join(parts[i:]) for i in range(len(parts))}

    dropped = 0
    merged = set(block)
    keep_suffix = {}
    for k in keep_out:
        for suf in labels_suffixes(k):
            keep_suffix.setdefault(suf, []).append(k)
    for d in external:
        if any(under(d, p) for p in PROTECTED):
            dropped += 1
            continue
        # d 是 keep_out 某项的父域或子域/同域
        if d in keep_suffix or any(s in keep_out for s in labels_suffixes(d)):
            dropped += 1
            continue
        merged.add(d)

    # 去掉被父域覆盖的冗余子域，减小体积
    def parents(d):
        parts = d.split(".")
        return [".".join(parts[i:]) for i in range(1, len(parts) - 1)]

    final = sorted(d for d in merged if not any(p in merged for p in parents(d)))

    out = {
        "version": 2,
        "generated": time.strftime("%Y-%m-%d %H:%M:%S"),
        "note": base.get("note", ""),
        "sources": [{"name": "handpicked", "count": len(block)}] + used,
        "allow": sorted(allow),
        "block": final,
        "observe": sorted(observe),
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
    print("block=%d (handpicked %d + external %d, dropped %d conflicts) allow=%d observe=%d -> %s"
          % (len(final), len(block), len(external), dropped, len(allow), len(observe), OUT))
    return 0


if __name__ == "__main__":
    sys.exit(main())
