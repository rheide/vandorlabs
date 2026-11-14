#!/usr/bin/env python3
"""Isolated real-GL glass checks. Requires Java 8, gcc, Linux EGL and LWJGL natives.

No fake shader classes are added to the mod or ordinary test classpath. The
benchmark alone loads a minimal pass-selection facade and an actual GLSL150
program; this is explicitly not OptiFine shader-pack acceptance.
"""
import argparse
import os
from pathlib import Path
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--classpath-file', type=Path, required=True)
    parser.add_argument('--natives', type=Path, required=True,
                        help='Directory containing compatible liblwjgl64.so')
    parser.add_argument('--mode', choices=('pipeline', 'retained', 'before', 'after'), required=True)
    parser.add_argument('--baseline-classes', type=Path,
                        help='Preserved deobfuscated previous build classes/JAR for before')
    parser.add_argument('--current-classes', type=Path,
                        help='Current deobfuscated build output; defaults to build/classes/java/main')
    parser.add_argument('--output-dir', type=Path,
                        default=Path('build/test-artifacts/glass-performance'))
    parser.add_argument('--build-dir', type=Path, default=Path('build/glass-egl'))
    parser.add_argument('--hardware', action='store_true',
                        help='Do not force Mesa software rendering; reported GL renderer remains authoritative')
    args = parser.parse_args()
    source = Path(__file__).resolve().parent
    root = source.parent.parent
    build = args.build_dir.resolve()
    build.mkdir(parents=True, exist_ok=True)
    classes = build / args.mode
    classes.mkdir(exist_ok=True)
    temporary = build / 'tmp'
    temporary.mkdir(exist_ok=True)
    current = (args.current_classes or root / 'build/classes/java/main').resolve()
    dependencies = [entry for entry in args.classpath_file.read_text().strip().split(os.pathsep)
                    if entry and Path(entry).resolve() != current]
    javac_classpath = os.pathsep.join([str(current)] + dependencies)
    bridge = build / 'libeglbridge.so'
    subprocess.run(['gcc', '-shared', '-fPIC', '-I' + str(args.java_home / 'include'),
                    '-I' + str(args.java_home / 'include/linux'), str(source / 'egl_bridge.c'),
                    '-Wl,-l:libEGL.so.1', '-o', str(bridge)], check=True)
    java_sources = [source / 'EglPbufferLauncher.java',
                    root / 'testclient/java/com/vandorlabs/client/GlassPanePipelineChecks.java']
    if args.mode == 'retained':
        java_sources += [root / 'testclient/java/com/vandorlabs/client/GlassPaneRetainedPipelineChecks.java']
    if args.mode in ('before', 'after'):
        java_sources += [source / 'shader_facade/net/optifine/shaders/Shaders.java',
                         root / 'testclient/java/com/vandorlabs/client/GlassPanePerformanceChecks.java',
                         root / 'src/main/java/com/vandorlabs/client/GlassPaneGpuTimers.java']
    subprocess.run([str(args.java_home / 'bin/javac'), '-proc:none', '-cp', javac_classpath,
                    '-d', str(classes)] + [str(path) for path in java_sources], check=True)
    if args.mode == 'before':
        if args.baseline_classes is None:
            parser.error('--baseline-classes is required for before; preserve the previous build before recompiling')
        production = args.baseline_classes.resolve()
    else:
        production = args.baseline_classes.resolve() if args.baseline_classes else current
    runtime_classpath = os.pathsep.join([str(classes), str(production)] + dependencies)
    environment = os.environ.copy()
    if not args.hardware:
        environment['LIBGL_ALWAYS_SOFTWARE'] = '1'
    environment['MESA_SHADER_CACHE_DISABLE'] = 'true'
    entrypoint = {'pipeline': 'GlassPanePipelineChecks', 'retained': 'GlassPaneRetainedPipelineChecks'}.get(args.mode, 'GlassPanePerformanceChecks')
    command = [str(args.java_home / 'bin/java'), '-Xmx2G',
               '-Djava.library.path=' + str(args.natives.resolve()), '-Degl.bridge=' + str(bridge),
               '-Djava.io.tmpdir=' + str(temporary), '-Dvandorlabs.glassBenchmarkMode=' + args.mode,
               '-Dvandorlabs.glassBenchmarkOutput=' + str(args.output_dir.resolve()),
               '-cp', runtime_classpath, 'EglPbufferLauncher', 'com.vandorlabs.client.' + entrypoint]
    result = subprocess.run(command, cwd=root, env=environment, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / (args.mode + '.log')).write_text(result.stdout)
    print(result.stdout, end='')
    return result.returncode


if __name__ == '__main__':
    raise SystemExit(main())
