#!/usr/bin/env python3
"""Probe actual privately supplied OptiFine G5 writers/hooks in the EGL pbuffer.

Supply either a fully MCP-remapped, patched G5 client JAR, or a patched G5
client plus an obfuscated-to-SRG mapping, MCP names ZIP and SpecialSource JAR.
The probe never substitutes the existing test shader facade. It does not launch
a full Forge client/world or certify a shader pack. No OptiFine binary is shipped.
"""
import argparse
import csv
import hashlib
import io
import os
from pathlib import Path
import subprocess
import zipfile


def development_client(args, build):
    if args.dev_client:
        return args.dev_client.resolve()
    for name in ('mapping_file', 'mcp_names', 'special_source'):
        if getattr(args, name) is None:
            raise SystemExit('--patched-client also requires --mapping-file, --mcp-names and --special-source')
    with zipfile.ZipFile(args.mcp_names) as archive:
        names = {row['searge']: row['name']
                 for member in ('fields.csv', 'methods.csv')
                 for row in csv.DictReader(io.TextIOWrapper(archive.open(member), encoding='utf-8'))}
    lines = []
    for line in args.mapping_file.read_text().splitlines():
        parts = line.split()
        if parts and parts[0] in ('FD:', 'MD:'):
            target = 2 if parts[0] == 'FD:' else 3
            owner, name = parts[target].rsplit('/', 1)
            parts[target] = owner + '/' + names.get(name, name)
        lines.append(' '.join(parts))
    mapping = build / 'optifine-obf-to-dev.srg'
    mapping.write_text('\n'.join(lines) + '\n')
    output = build / 'optifine-dev-client.jar'
    subprocess.run([str(args.java_home / 'bin/java'), '-Xmx1G', '-jar', str(args.special_source),
                    '--in-jar', str(args.patched_client), '--out-jar', str(output),
                    '--srg-in', str(mapping)], check=True)
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--classpath-file', type=Path, required=True)
    parser.add_argument('--natives', type=Path, required=True)
    supplied = parser.add_mutually_exclusive_group(required=True)
    supplied.add_argument('--dev-client', type=Path,
                          help='Actual patched G5 client with both classes AND members mapped to MCP names')
    supplied.add_argument('--patched-client', type=Path,
                          help='Actual G5 patched client before mapping; not the standalone installer')
    parser.add_argument('--mapping-file', type=Path, help='Obfuscated-to-SRG .srg file with MCP class names')
    parser.add_argument('--mcp-names', type=Path, help='MCP names ZIP containing fields.csv and methods.csv')
    parser.add_argument('--special-source', type=Path, help='Local SpecialSource shaded JAR')
    parser.add_argument('--current-classes', type=Path)
    parser.add_argument('--build-dir', type=Path, default=Path('build/glass-egl/optifine'))
    parser.add_argument('--output', type=Path, default=Path('build/test-artifacts/glass-performance/actual-optifine-g5.log'))
    parser.add_argument('--hardware', action='store_true')
    args = parser.parse_args()
    source = Path(__file__).resolve().parent
    root = source.parent.parent
    build = args.build_dir.resolve()
    build.mkdir(parents=True, exist_ok=True)
    classes = build / 'classes'
    classes.mkdir(exist_ok=True)
    temporary = build / 'tmp'
    temporary.mkdir(exist_ok=True)
    development = development_client(args, build)
    with zipfile.ZipFile(development) as archive:
        for name in ('net/optifine/shaders/SVertexBuilder.class',
                     'net/optifine/shaders/SVertexFormat.class',
                     'net/optifine/shaders/ShadersRender.class',
                     'net/minecraft/client/renderer/BufferBuilder.class'):
            if name not in archive.namelist():
                raise SystemExit('Missing actual patched class: ' + name)
    current = (args.current_classes or root / 'build/classes/java/main').resolve()
    production = {current, (root / 'build/classes/java/main').resolve()}
    dependencies = [entry for entry in args.classpath_file.read_text().strip().split(os.pathsep)
                    if entry and Path(entry).resolve() not in production]
    classpath = os.pathsep.join([str(development), str(current)] + dependencies)
    bridge = build / 'libeglbridge.so'
    subprocess.run(['gcc', '-shared', '-fPIC', '-I' + str(args.java_home / 'include'),
                    '-I' + str(args.java_home / 'include/linux'), str(source / 'egl_bridge.c'),
                    '-Wl,-l:libEGL.so.1', '-o', str(bridge)], check=True)
    subprocess.run([str(args.java_home / 'bin/javac'), '-proc:none', '-cp', classpath,
                    '-d', str(classes), str(source / 'EglPbufferLauncher.java'),
                    str(source / 'optifine/OptiFineG5Checks.java')], check=True)
    environment = os.environ.copy()
    if not args.hardware:
        environment['LIBGL_ALWAYS_SOFTWARE'] = '1'
    environment['MESA_SHADER_CACHE_DISABLE'] = 'true'
    command = [str(args.java_home / 'bin/java'), '-Xmx1G',
               '-Djava.library.path=' + str(args.natives.resolve()), '-Degl.bridge=' + str(bridge),
               '-Djava.io.tmpdir=' + str(temporary),
               '-Dvandorlabs.actualOptiFineJar=' + str(development),
               '-cp', str(classes) + os.pathsep + classpath, 'EglPbufferLauncher',
               'com.vandorlabs.client.OptiFineG5Checks']
    result = subprocess.run(command, cwd=root, env=environment, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    report = 'Patched MCP G5 client SHA256=' + hashlib.sha256(development.read_bytes()).hexdigest() + '\n' + result.stdout
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(report)
    print(report, end='')
    return result.returncode


if __name__ == '__main__':
    raise SystemExit(main())
