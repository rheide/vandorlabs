#!/usr/bin/env python3
"""Run identical shared contract fixtures under Java 8 and Java 21."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile

from check_portable_boundary import ROOT, check_boundary, executable, jdk_home


def source_fingerprint():
    digest = hashlib.sha256()
    for directory in ["src/portable/java", "src/portableTest/java"]:
        for path in sorted((ROOT / directory).rglob("*.java")):
            digest.update(str(path.relative_to(ROOT)).encode())
            digest.update(b"\0")
            digest.update(path.read_bytes())
    return digest.hexdigest()


def run_fixtures(version):
    home = jdk_home(version)
    tests = sorted((ROOT / "src/portableTest/java").rglob("*.java"))
    # This test covers loaded TileEntity identity and stays in the Forge suite.
    tests = [p for p in tests if p.name != "SignalUpdateBatchTest.java"]
    with tempfile.TemporaryDirectory(prefix="vandorlabs-contracts-%d-" % version) as directory:
        output = Path(directory) / "classes"
        count = check_boundary(home, output)
        subprocess.run([str(executable(home, "javac")), "-source", "8", "-target", "8",
                        "-Xlint:all,-options", "-Werror", "-encoding", "UTF-8",
                        "-classpath", str(output), "-sourcepath", str(ROOT / "src/portableTest/java"),
                        "-d", str(output)] + [str(p) for p in tests], check=True, cwd=ROOT)
        results = {}
        for source in tests:
            text = source.read_text()
            if not re.search(r"public\s+static\s+void\s+main\s*\(", text):
                continue
            package = re.search(r"\bpackage\s+([\w.]+)\s*;", text).group(1)
            name = package + "." + source.stem
            result = subprocess.run([str(executable(home, "java")), "-ea", "-Djava.awt.headless=true",
                                     "-cp", str(output), name], cwd=ROOT,
                                    capture_output=True, text=True, check=True)
            results[name] = {"stdout": result.stdout, "stderr": result.stderr}
        print("Java %d PASS: %d shared sources, %d contract fixtures" % (version, count, len(results)))
        return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jdk", choices=["8", "21", "both"], default="both")
    parser.add_argument("--report", type=Path, default=ROOT / "build/reports/portable/parity.json")
    args = parser.parse_args()
    fingerprint = source_fingerprint()
    try:
        versions = [8, 21] if args.jdk == "both" else [int(args.jdk)]
        results = []
        for version in versions:
            results.append(run_fixtures(version))
            if source_fingerprint() != fingerprint:
                raise RuntimeError("Portable sources changed during verification; rerun against one stable source tree")
    except subprocess.CalledProcessError as error:
        if error.stdout:
            print(error.stdout)
        if error.stderr:
            print(error.stderr)
        raise
    fixtures = results[0]
    if len(results) == 2 and fixtures != results[1]:
        other = results[1]
        changed = [name for name in sorted(set(fixtures) | set(other)) if fixtures.get(name) != other.get(name)]
        raise RuntimeError("Java 8/21 fixture output differs: " + ", ".join(changed))
    digest = hashlib.sha256(json.dumps(fixtures, sort_keys=True).encode()).hexdigest()
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps({"schema": 1, "java_versions": versions,
                                      "fixture_count": len(fixtures), "output_sha256": digest,
                                      "source_sha256": fingerprint,
                                      "fixtures": fixtures}, indent=2, sort_keys=True) + "\n")
    if len(results) == 2:
        print("Shared contract parity PASS: identical assertions/output on Java 8 and 21 (%s)" % digest[:16])
    else:
        print("Shared contracts PASS: Java %d (%s)" % (versions[0], digest[:16]))


if __name__ == "__main__":
    main()
