"""发布前脱敏扫描（P5）。

检查受 Git 跟踪的文本文件是否含有不应入库的内容：
  1. 个人路径（含用户名的 Windows / macOS / Linux 家目录）
  2. 邮箱地址
  3. 凭据痕迹（Authorization 头、私钥块、常见令牌前缀）
  4. 设备序列号（adb -s 传入的非模拟器序列号、ro.serialno 取值）

用法：
  python qa/release/log_redaction_scan.py            # 扫描全部跟踪文件
  python qa/release/log_redaction_scan.py --verbose  # 同时列出通过的文件数

退出码：发现任何问题返回 1，否则 0。仅作报告，不修改文件。
"""

import re
import subprocess
import sys

# 允许出现的占位路径（不含任何真实用户名）。
ALLOWED = re.compile(
    r'Users[\\/](?:<[^>]+>|you|user|%USERNAME%)(?![A-Za-z0-9_])', re.IGNORECASE)

# 内置的第三方源码（llama.cpp 上游）自带作者署名与示例路径，不属于本工程内容，整体跳过；
# 其许可与归属由 NOTICE / assets/licenses 负责，不在本扫描范围内。
SKIP_PREFIX = (
    'app/src/main/cpp/engine_llama/',
    # 随包第三方许可正文必须逐字保留，其中的作者邮箱是许可要求的一部分，不属于本工程泄露。
    'app/src/main/assets/licenses/',
)

CHECKS = [
    ("个人路径", re.compile(r'[A-Za-z]:\\Users\\[^\\\s"\']+')),
    ("个人路径", re.compile(r'/(?:Users|home)/[A-Za-z0-9._-]+')),
    # 排除 Kotlin/Java 的 this@Label、it@Label 写法，以及 @JvmField 一类注解。
    ("邮箱", re.compile(r'(?<![A-Za-z0-9_.%+-])(?!this@|it@|super@|label@)'
                        r'[A-Za-z0-9._%+-]{2,64}@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,24}')),
    ("凭据", re.compile(r'(?i)authorization\s*:\s*(?!\$\{|<|%|\*|Bearer\s*<)[^\s"\']{6,}')),
    ("凭据", re.compile(r'-----BEGIN [A-Z ]*PRIVATE KEY-----')),
    ("凭据", re.compile(r'\b(?:sk-[A-Za-z0-9]{16,}|ghp_[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{12,})\b')),
    ("设备序列号", re.compile(r'ro\.serialno[^\n]{0,20}?=\s*[A-Za-z0-9]{6,}')),
    # 设备序列号通常含大写或数字；借此排除 -s globstar 之类同形参数。
    ("设备序列号", re.compile(r'-s\s+(?!emulator-|\$\{)(?=[A-Za-z0-9]{8,})(?=[A-Za-z0-9]*[A-Z0-9])[A-Za-z0-9]{8,}')),
]

# 明确允许的例外：公开仓库地址、示例邮箱、构建产物中的第三方归属。
LINE_ALLOW = re.compile(r'(github\.com[:/]|example\.com|apache\.org|huggingface\.co|@JvmField|'
                        r'@param|@return|@link|@string|@drawable|@color|@style|@id|@null|@Override|'
                        r'@Throws|@Test|@Before|@JvmStatic|@Synchronized|@Volatile|@Database|@Entity|'
                        r'@Dao|@Query|@Insert|@Update|@Delete|@PrimaryKey|@ColumnInfo|@Ignore|'
                        r'@ForeignKey|@Index|@TypeConverter|@RunWith|@Suppress|@Deprecated)')

BINARY_SUFFIX = ('.png', '.jpg', '.jpeg', '.gguf', '.jar', '.aar', '.so', '.onnx', '.pdf',
                 '.mp3', '.mp4', '.zip', '.apk', '.p12', '.jks', '.webp', '.ico', '.ttf', '.otf')


def tracked_files():
    out = subprocess.run(['git', 'ls-files'], capture_output=True, text=True,
                         encoding='utf-8', errors='replace', check=True).stdout
    return [line for line in out.splitlines() if line.strip()]


def main():
    verbose = '--verbose' in sys.argv
    findings = []
    scanned = 0
    for path in tracked_files():
        if path.lower().endswith(BINARY_SUFFIX) or path.startswith(SKIP_PREFIX):
            continue
        try:
            with open(path, 'r', encoding='utf-8') as handle:
                lines = handle.read().splitlines()
        except (OSError, UnicodeDecodeError):
            continue  # 二进制或非 UTF-8 文件由其它检查负责
        scanned += 1
        for number, line in enumerate(lines, 1):
            if LINE_ALLOW.search(line):
                continue
            for kind, pattern in CHECKS:
                match = pattern.search(line)
                if not match:
                    continue
                if kind == "个人路径" and ALLOWED.search(line):
                    continue
                findings.append((kind, path, number, match.group(0)[:80]))

    for kind, path, number, hit in findings:
        print("[%s] %s:%d  %s" % (kind, path, number, hit))
    print("扫描文件 %d 个，命中 %d 处" % (scanned, len(findings)))
    if verbose:
        print("（--verbose：以上为全部结果）")
    return 1 if findings else 0


if __name__ == '__main__':
    sys.exit(main())
