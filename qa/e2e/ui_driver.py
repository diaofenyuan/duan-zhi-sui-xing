"""P4 端到端走查的轻量 UI 驱动。

用法：
  python qa/e2e/ui_driver.py dump            导出并列出可交互节点
  python qa/e2e/ui_driver.py tap  <text>     点击首个文本匹配的节点
  python qa/e2e/ui_driver.py tapid <resid>   点击指定 resource-id 的节点
  python qa/e2e/ui_driver.py text <text>     向当前焦点输入框输入文本
  python qa/e2e/ui_driver.py info            列出全部可见文本

依赖：adb 在 PATH 中，且只有一个设备在线。
"""

import re
import subprocess
import sys
import xml.etree.ElementTree as ET

REMOTE = "/sdcard/ui_dump.xml"
LOCAL = "ui_dump.xml"


def sh(args, check=False):
    p = subprocess.run(args, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if check and p.returncode != 0:
        raise SystemExit("command failed: %s\n%s" % (" ".join(args), p.stderr))
    return p.stdout.strip()


def dump():
    sh(["adb", "shell", "uiautomator", "dump", REMOTE])
    with open(LOCAL, "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "cat", REMOTE],
                               capture_output=True).stdout)
    return ET.parse(LOCAL).getroot()


def bounds_of(node):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not m:
        return None
    x1, y1, x2, y2 = (int(v) for v in m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def nodes(root):
    return [n for n in root.iter("node")]


def find(root, text=None, resid=None, cls=None, enabled=True):
    out = []
    for n in nodes(root):
        if enabled and n.get("enabled") != "true":
            continue
        if resid and n.get("resource-id") != resid:
            continue
        if cls and n.get("class") != cls:
            continue
        if text is not None and text not in (n.get("text") or ""):
            continue
        out.append(n)
    return out


def cmd_dump():
    root = dump()
    print("%-40s %-26s %s" % ("TEXT", "RESOURCE-ID", "BOUNDS"))
    for n in nodes(root):
        if n.get("clickable") == "true" or (n.get("text") or "").strip():
            print("%-40s %-26s %s" % ((n.get("text") or "")[:38],
                                      (n.get("resource-id") or "").replace("com.example.localai:id/", "")[:24],
                                      n.get("bounds")))


def cmd_tap(text):
    root = dump()
    cands = [n for n in find(root, text=text) if bounds_of(n)]
    if not cands:
        raise SystemExit("no node matching text: %r" % text)
    x, y = bounds_of(cands[0])
    sh(["adb", "shell", "input", "tap", str(x), str(y)])
    print("tapped %r at (%d,%d)" % (text, x, y))


def cmd_tapid(resid):
    root = dump()
    full = resid if resid.startswith("com.") else "com.example.localai:id/" + resid
    cands = [n for n in find(root, resid=full) if bounds_of(n)]
    if not cands:
        raise SystemExit("no node with id: %s" % full)
    x, y = bounds_of(cands[0])
    sh(["adb", "shell", "input", "tap", str(x), str(y)])
    print("tapped id %s at (%d,%d)" % (full, x, y))


def cmd_text(value):
    escaped = value.replace(" ", "%s").replace("'", "")
    sh(["adb", "shell", "input", "text", escaped])
    print("typed: %s" % value)


def cmd_info():
    root = dump()
    for n in nodes(root):
        t = (n.get("text") or "").strip()
        if t:
            print(t)


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return
    cmd, args = sys.argv[1], sys.argv[2:]
    {"dump": cmd_dump, "tap": cmd_tap, "tapid": cmd_tapid,
     "text": cmd_text, "info": cmd_info}[cmd](*args)


if __name__ == "__main__":
    main()
