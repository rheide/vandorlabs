#!/usr/bin/env python3
"""Run only changed diagonal wall/glass checks; --live adds the screenshot matrix."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
# Shared rendering/placement helpers intentionally invalidate this family too.
PATTERNS = [
    'src/main/java/**/blocks/*Diagonal*.java', 'src/main/java/**/blocks/*Glass*.java',
    'src/main/java/**/blocks/BlockProgrammableWall.java', 'src/main/java/**/blocks/BlockAnimatedScreenSelector.java',
    'src/main/java/**/blocks/Panel*.java', 'src/main/java/**/blocks/ModBlocks.java',
    'src/main/java/**/tiles/TileEntityAnimatedScreenSelector.java', 'src/main/java/**/tiles/*Glass*.java',
    'src/main/java/**/client/*Diagonal*.java', 'src/main/java/**/client/*Glass*.java',
    'src/main/java/**/client/TEAnimatedScreenSelector.java', 'src/main/java/**/client/StaticSurfaceMesh.java',
    'src/main/java/**/client/WorldSurface.java', 'src/main/java/**/client/BlockSurfaceFormat.java',
    'src/main/java/**/client/HousingTextureList.java', 'src/main/java/**/client/GuiProgrammableWall.java',
    'src/main/java/**/client/ProgrammableDialogLayout.java', 'src/main/java/**/client/GuiOptionCycle.java',
    'src/main/java/**/GuiHandler.java', 'src/main/java/**/client/ReproLab.java', 'src/main/java/**/network/Message*Diagonal*.java',
    'src/main/java/**/network/Message*Glass*.java', 'src/main/java/**/network/MessageProgrammableWallShade.java',
    'src/main/java/**/items/ProgrammableSettings.java', 'src/main/java/**/items/ItemConfigurizer.java',
    'src/portable/java/**/Diagonal*.java', 'src/portable/java/**/Glass*.java',
    'testclient/java/**/Diagonal*.java', 'testclient/java/**/NonRenderingChecks.java',
    'src/main/resources/**/programmable_diagonal_glass.json', 'src/main/resources/**/configured/glass_*.json',
    'texture-packs/additional/**/programmable_glass/**/*',
    'texture-packs/default/**/space_doors/*/glass_tile.png*',
    'generated-resources/**/models/item/*/space_glass_medium.json',
    'generated-resources/**/blockstates/programmable_glass.json',
    'tools/test_glass_finishes.py', 'tools/gen_glass_item_models.py', 'testclient/analyze_diagonal_join_matrix.py', 'tools/gen_glass_finishes.py', 'testclient/run_windows.py', 'testclient/test_diagonal_joins.py',
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--live', action='store_true')
    parser.add_argument('--force', action='store_true')
    args = parser.parse_args()
    files = sorted({p for pattern in PATTERNS for p in ROOT.glob(pattern) if p.is_file()})
    digest = hashlib.sha256()
    for path in files:
        digest.update(path.relative_to(ROOT).as_posix().encode())
        digest.update(path.read_bytes())
    fingerprint = digest.hexdigest()
    stamp = ROOT / 'build/diagonal-join-checks' / ('live.json' if args.live else 'geometry.json')
    if not args.force and stamp.exists() and json.loads(stamp.read_text()).get('fingerprint') == fingerprint:
        print('SKIP: diagonal wall/glass inputs unchanged since the last successful run. Use --force to rerun.')
        return
    if os.name != 'nt':
        parser.error('This wrapper uses the Windows live-client launcher; run gradlew testDiagonalJoins directly elsewhere.')
    command = "& build/tooling/build-local.ps1 -Offline -Tasks @('testDiagonalJoins','testGlassFinishes'" + (",'jar'" if args.live else '') + ')'
    subprocess.run(['powershell', '-NoProfile', '-Command', command], cwd=ROOT, check=True)
    if args.live:
        subprocess.run([sys.executable, str(ROOT / 'testclient/run_windows.py'), '--focus', 'diagonal-joins'], cwd=ROOT, check=True)
        output = max((ROOT / 'testclient').glob('render-run.*'), key=lambda p: p.stat().st_mtime)
        analysis_env = os.environ.copy()
        bundled_packages = ROOT / 'build/tooling/python-packages'
        if bundled_packages.is_dir():
            analysis_env['PYTHONPATH'] = str(bundled_packages) + os.pathsep + analysis_env.get('PYTHONPATH', '')
        subprocess.run([sys.executable, str(ROOT / 'testclient/analyze_diagonal_join_matrix.py'), str(output)], cwd=ROOT, env=analysis_env, check=True)
    stamp.parent.mkdir(parents=True, exist_ok=True)
    stamp.write_text(json.dumps({'fingerprint': fingerprint, 'files': len(files)}))
    print('PASS: focused diagonal checks; saved incremental fingerprint for', len(files), 'inputs')


if __name__ == '__main__':
    main()
