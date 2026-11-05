#!/usr/bin/env python3
"""Focused sampled-block identities, dedicated restart, survival inventory UI and model reload."""
import argparse
import os
from pathlib import Path
import socket
import tempfile
import time
from test_cube import ROOT, command, run, launch, stop, check_log, world, jdk_home


def fixture_world(path,port):
    world(path,port)
    with (path/'server.properties').open('a') as stream:stream.write('level-type=minecraft:flat\ngenerate-structures=false\nlevel-seed=0\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains","features":false,"lakes":false}\n')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client',action='store_true');parser.add_argument('--client-only',action='store_true')
    parser.add_argument('--xvfb',action='store_true');parser.add_argument('--reference',type=Path)
    args=parser.parse_args()
    if args.client_only:args.client=True
    if args.xvfb and (not args.client or os.name=='nt'):parser.error('--xvfb requires Linux --client')
    env=dict(os.environ,JAVA_HOME=str(jdk_home(21)))
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);output=Path(tempfile.mkdtemp(prefix='sampled-material-',dir=base))
    if not args.client_only:
        saved=output/'saved-world';fixture_world(saved,0)
        for phase in ['write','read']:
            run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PsampledMaterialPhase='+phase),output/(phase+'.log'),'[vandorlabs][sampled-material] server-'+phase+' PASS',env)
            check_log(saved/'logs/latest.log','All dimensions are saved')
    if args.client:
        with socket.socket() as listener:listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
        saved=output/'client-server';fixture_world(saved,port);server_log=output/'client-server.log';server=launch(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PsampledMaterialPhase=client'),server_log,env)
        try:
            deadline=time.monotonic()+120
            while 'Done (' not in server_log.read_text(errors='replace'):
                if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Sampler client fixture server startup failed: '+str(server_log))
                time.sleep(.5)
            client=output/'client';client.mkdir();pack=client/'resourcepacks/sample-fixture';model=pack/'assets/minecraft/models/block/stone.json';model.parent.mkdir(parents=True)
            model.write_text('{"parent":"minecraft:block/cube_all","textures":{"all":"minecraft:block/stone"}}')
            (pack/'pack.mcmeta').write_text('{"pack":{"pack_format":34,"description":"Disposable sampled-material reload fixture"}}')
            (client/'options.txt').write_text('guiScale:2\ntutorialStep:none\nrenderDistance:2\nsimulationDistance:5\nmaxFps:60\nentityShadows:false\nonboardAccessibility:false\nresourcePacks:["vanilla","file/sample-fixture"]\n')
            properties=['-PsampledMaterialPhase=client','-PmatterClientPort='+str(port),'-PmodernClientRunDirectory='+str(client)]
            if args.reference:properties.append('-PcubeReference='+str(args.reference.resolve()));env=dict(env,VANDOR_CUBE_REFERENCE=str(args.reference.resolve()))
            client_command=command('runClient',*properties)
            if args.xvfb:client_command=['xvfb-run','-a','-s','-screen 0 1280x720x24']+client_command;env=dict(env,LIBGL_ALWAYS_SOFTWARE='1')
            run(client_command,output/'client.log','[vandorlabs][sampled-material] client PASS',env)
            deadline=time.monotonic()+30
            while '[vandorlabs][sampled-material] server-client-actions PASS' not in server_log.read_text(errors='replace'):
                if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Authoritative server logout assertion did not complete before shutdown: '+str(server_log))
                time.sleep(.1)
            check_log(server_log,'[vandorlabs][sampled-material] server-client-actions PASS')
            if server.poll() is not None:raise RuntimeError('Fixture server exited unexpectedly before requested shutdown')
        finally:stop(server)
        deadline=time.monotonic()+30
        while time.monotonic()<deadline and 'All dimensions are saved' not in (saved/'logs/latest.log').read_text(errors='replace'):time.sleep(.1)
        check_log(server_log,'[vandorlabs][sampled-material] server-client-actions PASS');check_log(saved/'logs/latest.log','All dimensions are saved')
    print('Sampled material focused checks passed. Development classpath/software evidence: '+str(output.relative_to(ROOT)),flush=True)


if __name__=='__main__':main()
