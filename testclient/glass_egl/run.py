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
    parser.add_argument('--mode', choices=('pipeline', 'retained', 'before', 'after',
                                          'frames-before', 'frames-after', 'frames-pipeline',
                                          'portholes-before', 'portholes-after', 'porthole-geometry',
                                          'porthole-pipeline'), required=True)
    parser.add_argument('--baseline-classes', type=Path,
                        help='Preserved deobfuscated previous build classes/JAR for before')
    parser.add_argument('--current-classes', type=Path,
                        help='Current deobfuscated build output; defaults to build/classes/java/main')
    parser.add_argument('--output-dir', type=Path,
                        default=Path('build/test-artifacts/glass-performance'))
    parser.add_argument('--build-dir', type=Path, default=Path('build/glass-egl'))
    parser.add_argument('--hardware', action='store_true',
                        help='Do not force Mesa software rendering; reported GL renderer remains authoritative')
    parser.add_argument('--case', default='all', help='Optional focused lifecycle case for the selected test entrypoint')
    parser.add_argument('--frames-only', action='store_true', help='Exercise the optional frames-only shadow policy in a separate JVM')
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
    production_paths = {current, (root / 'build/classes/java/main').resolve()}
    dependencies = [entry for entry in args.classpath_file.read_text().strip().split(os.pathsep)
                    if entry and Path(entry).resolve() not in production_paths]
    if args.mode == 'porthole-geometry':
        dependencies += [str(root / 'build/non-rendering-checks/classes')]
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
    if args.mode.startswith('frames-'):
        java_sources += [root / 'testclient/java/com/vandorlabs/client/GlassAuthoredAtlas.java']
        if args.mode == 'frames-pipeline':
            java_sources += [root / 'testclient/java/com/vandorlabs/client/GlassFramePipelineChecks.java']
        else:
            java_sources += [source / 'shader_facade/net/optifine/shaders/Shaders.java',
                             root / 'testclient/java/com/vandorlabs/client/GlassFramePerformanceChecks.java',
                             root / 'src/main/java/com/vandorlabs/client/GlassPaneGpuTimers.java']
    if args.mode.startswith('porthole'):
        java_sources += [root / 'testclient/java/com/vandorlabs/client/GlassAuthoredAtlas.java']
        if args.mode == 'porthole-geometry':
            java_sources += [root / 'testclient/java/com/vandorlabs/client/PortholeRetainedGeometryChecks.java']
        elif args.mode == 'porthole-pipeline':
            java_sources += [source / 'shader_facade/net/optifine/shaders/Shaders.java',
                             root / 'testclient/java/com/vandorlabs/client/PortholeRetainedPipelineChecks.java']
        else:
            java_sources += [source / 'shader_facade/net/optifine/shaders/Shaders.java',
                             root / 'testclient/java/com/vandorlabs/client/GlassFramePerformanceChecks.java',
                             root / 'testclient/java/com/vandorlabs/client/PortholePerformanceChecks.java',
                             root / 'src/main/java/com/vandorlabs/client/GlassPaneGpuTimers.java']
    compiled = subprocess.run([str(args.java_home / 'bin/javac'), '-proc:none', '-cp', javac_classpath,
                               '-d', str(classes)] + [str(path) for path in java_sources])
    if compiled.returncode:
        return compiled.returncode
    if args.mode == 'before' or args.mode.endswith('-before'):
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
    entrypoint = {'pipeline': 'GlassPanePipelineChecks', 'retained': 'GlassPaneRetainedPipelineChecks',
                  'frames-pipeline': 'GlassFramePipelineChecks', 'frames-before': 'GlassFramePerformanceChecks',
                  'frames-after': 'GlassFramePerformanceChecks', 'portholes-before': 'PortholePerformanceChecks',
                  'portholes-after': 'PortholePerformanceChecks', 'porthole-geometry': 'PortholeRetainedGeometryChecks',
                  'porthole-pipeline': 'PortholeRetainedPipelineChecks'}.get(args.mode, 'GlassPanePerformanceChecks')
    command = [str(args.java_home / 'bin/java'), '-Xmx2G',
               '-Djava.library.path=' + str(args.natives.resolve()), '-Degl.bridge=' + str(bridge),
               '-Djava.io.tmpdir=' + str(temporary), '-Dvandorlabs.glassBenchmarkMode=' + args.mode,
               '-Dvandorlabs.glassPipelineCase=' + args.case,
               '-Dvandorlabs.glassBenchmarkOutput=' + str(args.output_dir.resolve()),
               '-cp', runtime_classpath, 'EglPbufferLauncher', 'com.vandorlabs.client.' + entrypoint]
    if args.frames_only:
        command.insert(1, '-Dvandorlabs.glassPaneShadows=frames')
    result = subprocess.run(command, cwd=root, env=environment, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / (args.mode + '.log')).write_text(result.stdout)
    print(result.stdout, end='')
    return result.returncode


if __name__ == '__main__':
    raise SystemExit(main())
