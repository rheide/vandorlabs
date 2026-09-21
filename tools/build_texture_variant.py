#!/usr/bin/env python3
"""Replace a built mod jar's texture tree with one named texture variant."""

import argparse
import hashlib
from pathlib import Path
import tempfile
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo


PREFIX = "assets/vandorlabs/textures/"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True, type=Path)
    parser.add_argument("--textures", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--name", required=True)
    args = parser.parse_args()

    variant = sorted(path for path in args.textures.rglob("*") if path.is_file())
    variant_names = {PREFIX + path.relative_to(args.textures).as_posix()
                     for path in variant}
    with ZipFile(str(args.base)) as source:
        base_names = {name for name in source.namelist()
                      if name.startswith(PREFIX) and not name.endswith("/")}
        missing = sorted(base_names - variant_names)
        unexpected = sorted(variant_names - base_names)
        if missing or unexpected:
            raise SystemExit("texture variant mismatch: %d missing, %d unexpected\nmissing: %s\nunexpected: %s"
                             % (len(missing), len(unexpected), missing[:10], unexpected[:10]))
        args.output.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.NamedTemporaryFile(dir=str(args.output.parent), suffix=".jar",
                                         delete=False) as handle:
            temporary = Path(handle.name)
        try:
            with ZipFile(str(temporary), "w", ZIP_DEFLATED, compresslevel=9) as output:
                for info in source.infolist():
                    if info.filename.startswith(PREFIX):
                        continue
                    output.writestr(info, source.read(info.filename))
                for path in variant:
                    name = PREFIX + path.relative_to(args.textures).as_posix()
                    info = ZipInfo(name, (2026, 9, 20, 0, 0, 0))
                    info.compress_type = ZIP_DEFLATED
                    info.external_attr = 0o100644 << 16
                    output.writestr(info, path.read_bytes())
                output.comment = ("Vandor Labs texture variant: " + args.name).encode("utf-8")
            temporary.replace(args.output)
            args.output.chmod(0o644)
        finally:
            if temporary.exists():
                temporary.unlink()

    print("built %s (%d textures, sha256 %s)" % (args.output, len(variant),
          hashlib.sha256(args.output.read_bytes()).hexdigest()))


if __name__ == "__main__":
    main()
