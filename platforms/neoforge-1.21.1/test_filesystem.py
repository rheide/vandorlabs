#!/usr/bin/env python3
"""Focused installed-PNG catalog, separate-process persistence and native reload checks."""
import argparse
import os
from pathlib import Path
import socket
import tempfile
import time
from PIL import Image
from test_cube import ROOT, PLATFORM, command, run, launch, stop, check_log, world, jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts


def pattern(path, width=32, height=64, phase=0):
    path.parent.mkdir(parents=True, exist_ok=True)
    image=Image.new('RGBA',(width,height))
    image.putdata([((x*7+phase*31)&255,(y*3+phase*13)&255,(x+y+phase*19)&255,255) for y in range(height) for x in range(width)])
    image.save(path)


def fixtures(game, present=True):
    root=game/'config/vandorlabs/textures'
    if present:pattern(root/'Native/portrait.png')
    pattern(root/'Native/peer.png',16,16,2)
    # Persistent discovery must not inherit the transient native-asset cache's 256-entry bound.
    for index in range(257):pattern(root/('Bulk/probe%03d.png'%index),16,16,index)
    return root


def client_run(output, peer, args, env):
    with socket.socket() as listener:
        listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
    server_world=output/(peer+'-server');world(server_world,port);fixtures(server_world)
    server_log=output/(peer+'-server.log');server=launch(command('runServer','-PmodernServerRunDirectory='+str(server_world),'-PfilesystemPhase=client'),server_log,env)
    try:
        deadline=time.monotonic()+120
        while 'Done (' not in server_log.read_text(errors='replace'):
            if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Fixture server startup failed: '+str(server_log))
            time.sleep(.5)
        client=output/(('missing' if peer=='restored' else peer)+'-client')
        if peer!='restored':client.mkdir();fixtures(client,present=peer=='installed')
        (client/'options.txt').write_text('guiScale:2\ntutorialStep:none\nrenderDistance:2\nsimulationDistance:5\nmaxFps:60\nentityShadows:false\nonboardAccessibility:false\n')
        client_properties=['-PfilesystemPhase=client','-PfilesystemPeer='+peer,'-PmatterClientPort='+str(port),'-PmodernClientRunDirectory='+str(client)]
        if args.reference:
            client_properties.append('-PcubeReference='+str(args.reference.resolve()));env=dict(env,VANDOR_CUBE_REFERENCE=str(args.reference.resolve()))
        launch_client=command('runClient',*client_properties)
        if args.xvfb:launch_client=['xvfb-run','-a','-s','-screen 0 1280x720x24']+launch_client;env=dict(env,LIBGL_ALWAYS_SOFTWARE='1')
        run(launch_client,output/(peer+'-client.log'),'[vandorlabs][filesystem] client-'+peer+' PASS',env)
    finally:stop(server)
    deadline=time.monotonic()+30
    while time.monotonic()<deadline and 'All dimensions are saved' not in (server_world/'logs/latest.log').read_text(errors='replace'):time.sleep(.1)
    check_log(server_world/'logs/latest.log','All dimensions are saved')
    check_log(server_log,'[vandorlabs][filesystem] server-client-actions PASS')


@retain_artifacts
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client',action='store_true');parser.add_argument('--xvfb',action='store_true');parser.add_argument('--reference',type=Path)
    parser.add_argument('--client-only',action='store_true');parser.add_argument('--peer',choices=['both','installed','missing'],default='both')
    args=parser.parse_args()
    if args.client_only:args.client=True
    if args.xvfb and (not args.client or os.name=='nt'):parser.error('--xvfb requires --client on Linux')
    env=dict(os.environ,JAVA_HOME=str(jdk_home(21)))
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);output=Path(artifact_directory(prefix='filesystem-native-',dir=str(base)))
    if not args.client_only:
        saved=output/'saved-world';world(saved,0);root=fixtures(saved)
        for phase in ['write','read','missing']:
            if phase=='missing':(root/'Native/portrait.png').unlink()
            run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PfilesystemPhase='+phase),output/(phase+'.log'),'[vandorlabs][filesystem] server-'+phase+' PASS',env)
            check_log(saved/'logs/latest.log','All dimensions are saved')
    if args.client:
        for peer in (['installed','missing','restored'] if args.peer=='both' else ['missing','restored'] if args.peer=='missing' else ['installed']):client_run(output,peer,args,env)
    print('Filesystem checks passed; discovery is startup-only. Development-classpath/software evidence only: '+str(output.relative_to(ROOT)),flush=True)


if __name__=='__main__':
    try:main()
    except (RuntimeError,TimeoutError) as error:raise SystemExit('Filesystem smoke failed: '+str(error))
