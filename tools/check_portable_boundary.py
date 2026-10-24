#!/usr/bin/env python3
"""Compile the complete portable tree on an empty classpath to enforce its boundary."""
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/portable/java"
FORBIDDEN = re.compile(r"\b(?:net\.(?:minecraft|minecraftforge|neoforged)|org\.lwjgl)\b")


def executable(home, name):
    return home / "bin" / (name + ".exe" if os.name == "nt" else name)


def matches_jdk(home, version):
    compiler = executable(home, "javac")
    if not compiler.is_file():
        return False
    result = subprocess.run([str(compiler), "-version"], capture_output=True, text=True, check=True)
    return bool(re.search(r"javac (?:1\.)?%d(?:\.|\s|$)" % version, result.stdout + result.stderr))


def jdk_home(version):
    for variable in ["VANDOR_JAVA%d_HOME" % version, "JAVA%d_HOME" % version]:
        configured = os.environ.get(variable)
        if configured:
            home = Path(configured)
            if not matches_jdk(home, version):
                raise RuntimeError("%s must point to a Java %d JDK: %s" % (variable, version, home))
            return home
    candidates = [Path("/usr/lib/jvm/java-%d-openjdk-amd64" % version)]
    current = os.environ.get("JAVA_HOME")
    if current:
        candidates.append(Path(current))
    javac = shutil.which("javac")
    if javac:
        candidates.append(Path(javac).resolve().parent.parent)
    for home in candidates:
        if matches_jdk(home, version):
            return home
    raise RuntimeError("Set VANDOR_JAVA%d_HOME or JAVA%d_HOME to a Java %d JDK" % (version, version, version))


def check_boundary(home, output):
    sources = sorted(SOURCE.rglob("*.java"))
    violations = [str(p.relative_to(ROOT)) for p in sources if FORBIDDEN.search(p.read_text())]
    if violations:
        raise RuntimeError("Platform dependency in portable source: " + ", ".join(violations))
    output.mkdir(parents=True, exist_ok=True)
    # Explicit sourcepath prevents javac from silently finding legacy adapter code.
    # No dependency JAR or precompiled output is on the classpath. This checks
    # same-package inheritance and fully qualified references, not only imports.
    subprocess.run([str(executable(home, "javac")), "-source", "8", "-target", "8",
                    "-Xlint:all,-options", "-Werror", "-encoding", "UTF-8",
                    "-classpath", str(output), "-sourcepath", str(SOURCE),
                    "-d", str(output)] + [str(p) for p in sources], check=True, cwd=ROOT)
    return len(sources)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jdk", type=int, choices=[8, 21], default=8)
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="vandorlabs-portable-") as directory:
        count = check_boundary(jdk_home(args.jdk), Path(directory) / "classes")
    print("Portable boundary PASS: %d classes, Java %d, no platform classpath" % (count, args.jdk))


if __name__ == "__main__":
    main()
