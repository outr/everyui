#!/usr/bin/env python3
"""Helpers for tools/check.sh.

  extract-tree LOG OUT                   the last frozen frame's component tree from a Chrome, JavaFX, logcat or Roku log
  compare-tree GOLDEN ACTUAL [--tol PX]  structure must match exactly; positions and sizes within a tolerance
  faces FONTS_DIR FACES_SCALA            the shared face table repeats the bundled fonts' own metrics
  fps LOG [--median-min F] [--min F]     the live frame rate after the last app start meets a floor
  android-wait SERIAL OUT                wait for the app on an Android device to log its frozen frame, then save its log
  roku-install CHANNEL [--add NAME=TEXT] sideload a channel directory (extra files for test runs)
  roku-console SECONDS OUT               capture the Roku debug console

Roku commands read ROKU_IP (default 192.168.1.112) and ROKU_PASS.
"""
import argparse
import io
import os
import re
import socket
import statistics
import struct
import subprocess
import sys
import tempfile
import time
import urllib.request
import zipfile

NUM = r"[-+0-9.eE]+"
TREE_LOG = re.compile(r"\[ui\] tree (?P<indent> *)(?P<kind>.+?) x=(?P<x>%s) y=(?P<y>%s) w=(?P<w>%s) h=(?P<h>%s) rot=(?P<rot>%s)" % ((NUM,) * 5))
TREE_GOLDEN = re.compile(r"^tree (?P<indent> *)(?P<kind>.+?) x=(?P<x>%s) y=(?P<y>%s) w=(?P<w>%s) h=(?P<h>%s) rot=(?P<rot>%s)$" % ((NUM,) * 5))


def fail(message):
    print(message)
    sys.exit(1)


def read_lines(path):
    return io.open(path, encoding="utf-8", errors="replace").read().splitlines()


def cmd_extract_tree(args):
    lines = read_lines(args.log)
    starts = [i for i, line in enumerate(lines) if "[ui] frozen at" in line]
    if not starts:
        fail("no frozen frame in %s (did the app start, and was it frozen?)" % args.log)
    tree = []
    for line in lines[starts[-1] + 1:]:
        m = TREE_LOG.search(line)
        if m:
            tree.append("tree %s%s x=%s y=%s w=%s h=%s rot=%s" % (m["indent"], m["kind"], m["x"], m["y"], m["w"], m["h"], m["rot"]))
        elif tree:
            break
    if not tree:
        fail("the frozen frame logged no component tree")
    io.open(args.out, "w", encoding="utf-8").write("\n".join(tree) + "\n")
    print("%d components in the frozen frame" % len(tree))


def parse_tree(path):
    rows = []
    for n, line in enumerate(read_lines(path), 1):
        if not line.strip():
            continue
        m = TREE_GOLDEN.match(line)
        if not m:
            fail("%s:%d: not a tree line: %s" % (path, n, line))
        rows.append((m["indent"] + m["kind"], {k: float(m[k]) for k in ("x", "y", "w", "h", "rot")}))
    return rows


def cmd_compare_tree(args):
    golden, actual = parse_tree(args.golden), parse_tree(args.actual)
    problems = []
    if len(golden) != len(actual):
        problems.append("component count: golden %d, actual %d" % (len(golden), len(actual)))
    worst = 0.0
    for i, ((gk, gv), (ak, av)) in enumerate(zip(golden, actual)):
        if gk != ak:
            problems.append("component %d: golden %r, actual %r" % (i + 1, gk.strip(), ak.strip()))
            continue
        for key in ("x", "y", "w", "h", "rot"):
            tol = args.rot_tol if key == "rot" else args.tol
            delta = abs(gv[key] - av[key])
            if key != "rot":
                worst = max(worst, delta)
            if delta > tol:
                problems.append("%s: %s golden %g, actual %g (off by %g)" % (gk.strip(), key, gv[key], av[key], delta))
    if problems:
        print("%d difference(s) from the golden:" % len(problems))
        for p in problems[:25]:
            print("  " + p)
        if len(problems) > 25:
            print("  ... and %d more" % (len(problems) - 25))
        sys.exit(1)
    print("%d components match the golden (largest position/size difference %.3g px)" % (len(golden), worst))


def font_metrics(path):
    """unitsPerEm, ascender and descender (positive), as a browser reads them: OS/2's typographic metrics when the
    font sets USE_TYPO_METRICS, otherwise hhea's."""
    d = open(path, "rb").read()
    tables = {}
    for i in range(struct.unpack(">H", d[4:6])[0]):
        tag, _, offset, _ = struct.unpack(">4sIII", d[12 + 16 * i:28 + 16 * i])
        tables[tag.decode("latin-1")] = offset
    units = struct.unpack(">H", d[tables["head"] + 18:tables["head"] + 20])[0]
    ascender, descender = struct.unpack(">hh", d[tables["hhea"] + 4:tables["hhea"] + 8])
    if "OS/2" in tables:
        os2 = tables["OS/2"]
        selection = struct.unpack(">H", d[os2 + 62:os2 + 64])[0]
        if selection & 128:
            ascender, descender = struct.unpack(">hh", d[os2 + 68:os2 + 72])
    return float(units), float(ascender), float(-descender)


def cmd_faces(args):
    source = io.open(args.table, encoding="utf-8").read()
    rows = re.findall(r'new Face\("([^"]+)", (\d+), "([^"]+)", ([0-9.]+), ([0-9.]+), ([0-9.]+)\)', source)
    if not rows:
        fail("no `new Face(...)` rows in %s" % args.table)
    problems = []
    for family, weight, name, units, ascender, descender in rows:
        path = os.path.join(args.fonts, name)
        if not os.path.exists(path):
            problems.append("%s: not in %s" % (name, args.fonts))
            continue
        table = (float(units), float(ascender), float(descender))
        actual = font_metrics(path)
        if table != actual:
            problems.append("%s: the table has unitsPerEm/ascender/descender %s, the font has %s" % (name, table, actual))
        else:
            print("%s %s (%s): unitsPerEm %g, ascender %g, descender %g" % (family, weight, name, *actual))
    listed = {row[2] for row in rows}
    for name in sorted(os.listdir(args.fonts)):
        if name.endswith(".ttf") and name not in listed:
            problems.append("%s is bundled but not in the table" % name)
    if problems:
        fail("\n".join(problems))


def cmd_fps(args):
    lines = read_lines(args.log)
    starts = [i for i, line in enumerate(lines) if "[ui] Image loaded" in line]
    if not starts:
        fail("the app did not start (no '[ui] Image loaded' in the console)")
    samples = [float(m.group(1)) for line in lines[starts[-1]:] for m in [re.search(r"\[ui\] fps=(%s)" % NUM, line)] if m]
    samples = samples[args.skip:]
    if len(samples) < args.samples_min:
        fail("only %d frame-rate samples (need %d)" % (len(samples), args.samples_min))
    median, low = statistics.median(samples), min(samples)
    print("%d samples: median %.1f fps, minimum %.1f fps (floors: median %.1f, minimum %.1f)" % (len(samples), median, low, args.median_min, args.min))
    if median < args.median_min or low < args.min:
        sys.exit(1)


def cmd_android_wait(args):
    def log():
        return subprocess.run(["adb", "-s", args.serial, "logcat", "-d", "-s", "universal-ui:I", "AndroidRuntime:E"],
                              capture_output=True, text=True).stdout
    end, last = time.time() + args.timeout, None
    while time.time() < end:
        out = log()
        if "FATAL EXCEPTION" in out:
            io.open(args.out, "w", encoding="utf-8").write(out)
            fail("the app crashed:\n" + out[-4000:])
        if "[ui] frozen at" in out:
            if out == last:  # nothing new since the last look: the whole tree is in
                io.open(args.out, "w", encoding="utf-8").write(out)
                print("the frozen frame is logged")
                return
            last = out
        time.sleep(1.0)
    out = log()
    io.open(args.out, "w", encoding="utf-8").write(out)
    fail("no frozen frame within %ds; the app's log:\n%s" % (args.timeout, out[-4000:]))


def roku_target():
    ip = os.environ.get("ROKU_IP", "192.168.1.112")
    password = os.environ.get("ROKU_PASS")
    if not password:
        fail("set ROKU_PASS to the Roku developer password")
    return ip, password


def cmd_roku_install(args):
    ip, password = roku_target()
    archive = io.BytesIO()
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as z:
        for base, _, files in os.walk(args.channel):
            for name in sorted(files):
                full = os.path.join(base, name)
                z.write(full, os.path.relpath(full, args.channel))
        for extra in args.add:
            name, _, text = extra.partition("=")
            z.writestr(name, text)
    # curl, as tools/roku-acceptance.sh does: urllib's digest auth sends the whole body before the challenge and the
    # Roku closes the connection on it
    with tempfile.TemporaryDirectory() as work:
        package = os.path.join(work, "channel.zip")
        open(package, "wb").write(archive.getvalue())
        html = subprocess.run(
            ["curl", "-s", "--digest", "-u", "rokudev:" + password, "-F", "mysubmit=Install", "-F", "archive=@" + package,
             "http://%s/plugin_install" % ip],
            capture_output=True, text=True, timeout=180).stdout
    status = re.search(r"Install Success|Identical|Failed", html)
    print("install: %s" % (status.group(0) if status else "no status in the response"))
    if not status or status.group(0) == "Failed":
        fail("the Roku rejected the package (a BrightScript compile error?)")
    if status.group(0) == "Identical":
        # the same package is already installed: restart it so this run's output is fresh
        for path in ("/keypress/Home", "/launch/dev"):
            urllib.request.urlopen(urllib.request.Request("http://%s:8060%s" % (ip, path), data=b"", method="POST"), timeout=10).read()
            time.sleep(2)


def cmd_roku_console(args):
    ip, _ = roku_target()
    connection = socket.create_connection((ip, 8085), timeout=5)
    connection.settimeout(1.0)
    captured, end = b"", time.time() + args.seconds
    while time.time() < end:
        try:
            chunk = connection.recv(4096)
            if not chunk:
                break
            captured += chunk
        except socket.timeout:
            pass
    open(args.out, "wb").write(captured)
    print("captured %d bytes of console output" % len(captured))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    p = sub.add_parser("extract-tree"); p.add_argument("log"); p.add_argument("out"); p.set_defaults(run=cmd_extract_tree)
    p = sub.add_parser("compare-tree"); p.add_argument("golden"); p.add_argument("actual")
    p.add_argument("--tol", type=float, default=1.0); p.add_argument("--rot-tol", type=float, default=0.001); p.set_defaults(run=cmd_compare_tree)
    p = sub.add_parser("faces"); p.add_argument("fonts"); p.add_argument("table"); p.set_defaults(run=cmd_faces)
    p = sub.add_parser("fps"); p.add_argument("log")
    p.add_argument("--median-min", type=float, default=59.5); p.add_argument("--min", type=float, default=58.5)
    p.add_argument("--skip", type=int, default=3); p.add_argument("--samples-min", type=int, default=20); p.set_defaults(run=cmd_fps)
    p = sub.add_parser("android-wait"); p.add_argument("serial"); p.add_argument("out")
    p.add_argument("--timeout", type=float, default=90); p.set_defaults(run=cmd_android_wait)
    p = sub.add_parser("roku-install"); p.add_argument("channel"); p.add_argument("--add", action="append", default=[]); p.set_defaults(run=cmd_roku_install)
    p = sub.add_parser("roku-console"); p.add_argument("seconds", type=float); p.add_argument("out"); p.set_defaults(run=cmd_roku_console)
    args = parser.parse_args()
    args.run(args)


if __name__ == "__main__":
    main()
