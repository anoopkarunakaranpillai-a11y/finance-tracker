#!/usr/bin/env python3
"""Collects Android CI results into one folder: test results (as JSON), build log tail, compiler errors and build outputs.
Usage: android_ci_report.py build|device OUT_DIR   (run from the android/ folder)"""
import glob, json, os, re, shutil, sys, xml.etree.ElementTree as ET

kind, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)

def junit(pattern):
    tests = []
    for f in sorted(glob.glob(pattern, recursive=True)):
        try:
            root = ET.parse(f).getroot()
        except Exception:
            continue
        for tc in root.iter("testcase"):
            st, msg = "Pass", ""
            for tag, s in (("failure", "Fail"), ("error", "Fail"), ("skipped", "Not Run")):
                el = tc.find(tag)
                if el is not None:
                    st = s
                    msg = ((el.get("message") or "") + "\n" + (el.text or ""))[:5000]
            tests.append({"class": tc.get("classname"), "name": tc.get("name"), "status": st,
                          "time": float(tc.get("time") or 0), "message": msg.strip()})
    return tests

def log_bits(path):
    if not os.path.exists(path):
        return [], ""
    lines = open(path, errors="replace").read().splitlines()
    errs = [l for l in lines if re.match(r"^(e: |ERROR:|FAILURE:|\* What went wrong)", l) or "error:" in l.lower() and ".kt" in l]
    return errs[:200], "\n".join(lines[-400:])

if kind == "build":
    tests = junit("app/build/test-results/**/*.xml")
    errs, tail = log_bits("../ci-build.log")
    for src, dst in [("app/build/outputs/apk/debug/app-debug.apk", "app-debug.apk"),
                     ("app/build/outputs/apk/release/app-release.apk", "app-release.apk"),
                     ("app/build/outputs/apk/release/app-release-unsigned.apk", "app-release-unsigned.apk"),
                     ("app/build/outputs/bundle/release/app-release.aab", "app-release.aab"),
                     ("app/build/reports/lint-results-debug.xml", "lint-results-debug.xml")]:
        if os.path.exists(src):
            shutil.copy(src, os.path.join(out, dst))
    lint = {}
    if os.path.exists("app/build/reports/lint-results-debug.xml"):
        r = ET.parse("app/build/reports/lint-results-debug.xml").getroot()
        for i in r.iter("issue"):
            lint[i.get("severity")] = lint.get(i.get("severity"), 0) + 1
    sizes = {f: os.path.getsize(os.path.join(out, f)) for f in os.listdir(out) if f.endswith((".apk", ".aab"))}
    summary = {"kind": "build", "compile_errors": errs, "tests": tests, "lint": lint, "outputs": sizes,
               "passed": sum(t["status"] == "Pass" for t in tests), "failed": sum(t["status"] == "Fail" for t in tests)}
    open(os.path.join(out, "build-log-tail.txt"), "w").write(tail)
    json.dump(summary, open(os.path.join(out, "build-summary.json"), "w"), indent=1)
else:
    tests = junit("app/build/outputs/androidTest-results/**/*.xml")
    errs, tail = log_bits("../device.log")
    launch = open("../launch.txt").read() if os.path.exists("../launch.txt") else ""
    info = open("../device-info.txt").read() if os.path.exists("../device-info.txt") else ""
    times = [int(x) for x in re.findall(r"TotalTime:\s*(\d+)", launch)]
    summary = {"kind": "device", "errors": errs, "tests": tests, "launch_ms": times, "device": info,
               "passed": sum(t["status"] == "Pass" for t in tests), "failed": sum(t["status"] == "Fail" for t in tests)}
    if os.path.isdir("../shots"):
        os.makedirs(os.path.join(out, "shots"), exist_ok=True)
        for f in os.listdir("../shots"):
            if f.endswith(".png") and os.path.getsize(os.path.join("../shots", f)) > 0:
                shutil.copy(os.path.join("../shots", f), os.path.join(out, "shots", f))
    open(os.path.join(out, "device-log-tail.txt"), "w").write(tail + "\n\n--- launch ---\n" + launch)
    json.dump(summary, open(os.path.join(out, "device-summary.json"), "w"), indent=1)
print(json.dumps({k: v for k, v in summary.items() if k not in ("tests",)}, indent=1)[:3000])
