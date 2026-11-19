#!/usr/bin/env python3
"""Typed idle legacy conversion and actual actorless cross-chunk recovery; one server at a time."""
import argparse,os,shutil
from pathlib import Path
from test_transmitter import ROOT,command,run,jdk_home
from test_sampled_materials import fixture_world
from connected_player_fixture import run_connected_player
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--recover-world', type=Path, help='Retry cold assertions and reversal in a stopped recovery runtime')
    mode.add_argument('--automatic', action='store_true', help='Check opt-in automatic conversion across cold starts')
    mode.add_argument('--inventory', action='store_true', help='Check automatic conversion in a connected player inventory')
    parser.add_argument('--xvfb', action='store_true')
    args = parser.parse_args()
    if args.inventory:
        out = run_connected_player('legacy-wheel-inventory', args.xvfb,
                                   ('vandorlabs.autoConvertLandingGear=true', 'vandorlabs.legacyWheelInventoryChecks=true'))
        print('Legacy wheel inventory PASS ' + str(out), flush=True)
        return
    out = Path(artifact_directory(prefix='legacy-migration-', dir=ROOT/'build/test-artifacts'))
    saved = out/'saved-world'
    if args.recover_world:
        shutil.copytree(args.recover_world.resolve(), saved)
    else:
        fixture_world(saved, 0)
    if args.automatic:
        for phase in ('seed', 'convert', 'verify'):
            flag = ' -Dvandorlabs.autoConvertLandingGear=true' if phase != 'seed' else ''
            env = dict(os.environ, JAVA_HOME=str(jdk_home(21)),
                       JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS', '') + flag
                       + ' -Dvandorlabs.telescopicLanding=true -Dvandorlabs.legacyAutomaticPhase=' + phase)
            run(command('runServer', '-PmodernServerRunDirectory=' + str(saved)), out/(phase + '.log'),
                '[vandorlabs][legacy-automatic] ' + phase + ' PASS', env)
        print('Automatic legacy wheel conversion PASS ' + str(out), flush=True)
        return
    for phase in (('recover',) if args.recover_world else ('write', 'crash', 'recover')):
        env = dict(os.environ, JAVA_HOME=str(jdk_home(21)),
                   JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS', '')
                   + ' -Dvandorlabs.telescopicLanding=true -Dvandorlabs.legacyMigrationChecks=true -Dvandorlabs.legacyMigrationPhase=' + phase)
        log = out/(phase + '.log')
        run(command('runServer', '-PmodernServerRunDirectory=' + str(saved)), log,
            '[vandorlabs][legacy-migration] ' + phase + ' PASS', env)
        if phase == 'crash':
            text = log.read_text(errors='replace')
            marker = text.index('sourceOnlyCrossChunkSave/noNativeRoot/abruptHalt')
            if 'Stopping server' in text[marker:]:
                raise AssertionError('Legacy source-only writer shut down gracefully')
    print('Typed legacy maintenance migration/recovery PASS '+str(out),flush=True)
if __name__=='__main__':main()
