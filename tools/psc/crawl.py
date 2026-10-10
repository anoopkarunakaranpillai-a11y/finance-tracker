#!/usr/bin/env python3
"""Catalogue Kerala PSC previous question papers and answer keys from the official website.
Polite crawler: identifies itself, honours robots.txt, waits between requests, only reads public listing pages.
Writes psc/catalogue.json, psc/answerkeys.json and appends to psc/sync-log.json."""
import json, re, time, html, hashlib, os, sys, urllib.request, urllib.robotparser, datetime
BASE = "https://www.keralapsc.gov.in"
UA = "AnoopsFinanceTracker-PSC-sync/1.0 (+https://github.com/anoopkarunakaranpillai-a11y/finance-tracker)"
DELAY = float(os.environ.get("PSC_DELAY", "2"))
OUT = os.path.join(os.path.dirname(__file__), "..", "..", "psc")
rp = urllib.robotparser.RobotFileParser(); rp.set_url(BASE + "/robots.txt"); rp.read()
log = {"started": datetime.datetime.utcnow().isoformat() + "Z", "requests": 0, "errors": []}
def get(url, tries=3):
    if not rp.can_fetch(UA, url):
        raise RuntimeError("robots.txt disallows " + url)
    for t in range(tries):
        try:
            time.sleep(DELAY); log["requests"] += 1
            r = urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=60)
            return r.read().decode("utf-8", "replace")
        except Exception as e:
            log["errors"].append({"url": url, "try": t + 1, "error": str(e)[:200]}); time.sleep(5 * (t + 1))
    raise RuntimeError("failed " + url)
def clean(s): return re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", " ", s))).strip()
def rows(h):
    for tr in re.findall(r"<tr>(.*?)</tr>", h, re.S):
        tds = re.findall(r"<td[^>]*>(.*?)</td>", tr, re.S)
        if len(tds) < 2: continue
        links = re.findall(r'href="([^"]+\.pdf)"', tr, re.I)
        yield [clean(t) for t in tds], [l if l.startswith("http") else BASE + l for l in links]
def last_page(h):
    m = re.search(r'pager__item--last">\s*<a href="\?[^"]*page=(\d+)', h)
    return int(m.group(1)) if m else 0
def crawl(path):
    out, h = [], get(BASE + path + "?tid=All&page=0"); n = last_page(h)
    for p in range(0, n + 1):
        if p: h = get(BASE + path + "?tid=All&page=%d" % p)
        for cells, links in rows(h): out.append({"cells": cells, "pdfs": links, "listPage": p})
    return out, n + 1
def parse_paper(r):
    c = r["cells"]; body = " ".join(c[2:-1]) if len(c) > 3 else (c[2] if len(c) > 2 else "")
    code = re.search(r"Paper Code\s*:?-?\s*([0-9A-Za-z/ ]+?)(?:\s+Date|$)", body)
    date = re.search(r"Date Of Test\s*:?-?\s*([0-9]{1,2}[./-][0-9]{1,2}[./-][0-9]{2,4})", body)
    title = c[1]; year = c[0]
    d = None
    if date:
        dd, mm, yy = re.split(r"[./-]", date.group(1)); yy = ("20" + yy) if len(yy) == 2 else yy
        try: d = datetime.date(int(yy), int(mm), int(dd)).isoformat()
        except ValueError: d = None
    pdf = r["pdfs"][0] if r["pdfs"] else None
    pid = hashlib.sha1(((code.group(1).strip() if code else "") + "|" + title + "|" + (pdf or "")).encode()).hexdigest()[:12]
    return {"id": pid, "title": title, "code": code.group(1).strip() if code else None, "year": int(year) if year.isdigit() else (int(d[:4]) if d else None),
            "date": d, "pdf": pdf, "body": body, "listPage": r["listPage"]}
if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    papers_raw, np = crawl("/previous-question-papers")
    papers = [parse_paper(r) for r in papers_raw]
    keys = {}
    for path in ["/answerkey_omrexams", "/answerkey_onlineexams"]:
        try:
            raw, n = crawl(path); keys[path] = {"pages": n, "rows": raw}
        except Exception as e:
            keys[path] = {"error": str(e)}
    try: copy = clean(get(BASE + "/copyright"))
    except Exception as e: copy = "ERROR " + str(e)
    now = datetime.datetime.utcnow().isoformat() + "Z"
    json.dump({"source": BASE + "/previous-question-papers", "checked": now, "listPages": np, "count": len(papers), "papers": papers}, open(os.path.join(OUT, "catalogue.json"), "w"), ensure_ascii=False, indent=1)
    json.dump({"checked": now, "sources": keys}, open(os.path.join(OUT, "answerkeys-raw.json"), "w"), ensure_ascii=False, indent=1)
    i = copy.find("Copyright"); open(os.path.join(OUT, "copyright.txt"), "w").write(copy[i:i + 4000] if i >= 0 else copy[:4000])
    log.update({"finished": now, "papers": len(papers), "listPages": np, "answerKeyRows": {k: len(v.get("rows", [])) for k, v in keys.items()}})
    lp = os.path.join(OUT, "sync-log.json"); L = json.load(open(lp)) if os.path.exists(lp) else []; L.append(log); json.dump(L[-200:], open(lp, "w"), indent=1)
    print(json.dumps({k: log[k] for k in ["papers", "listPages", "answerKeyRows", "requests"]}), "errors:", len(log["errors"]))
