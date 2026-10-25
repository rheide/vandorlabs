#!/usr/bin/env python3
"""Export actual native housing item models in an owned disposable paired runtime."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import signal
import shutil
import socket
import subprocess
import sys
import tempfile
import time
from build_all import java_home
from test_modern_runtime import PLATFORM, stop_owned


def hashes():
    return {str(path.relative_to(PLATFORM)): hashlib.sha256(path.read_bytes()).hexdigest()
            for folder in ('build/classes/java/main', 'build/resources/main')
            for path in sorted((PLATFORM/folder).rglob('*')) if path.is_file()}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port',type=int,default=25582)
    parser.add_argument('--timeout',type=int,default=240)
    parser.add_argument('--log-dir',type=Path,required=True)
    parser.add_argument('--legacy-models',type=Path)
    parser.add_argument('--legacy-image',type=Path)
    args=parser.parse_args()
    if bool(args.legacy_models) != bool(args.legacy_image):
        parser.error('--legacy-models and --legacy-image must be supplied together')
    for reference in (args.legacy_models,args.legacy_image):
        if reference is not None and not reference.is_file():
            parser.error('Missing legacy reference: '+str(reference))
    if not 1024 <= args.port <= 65535 or args.timeout <= 0:
        parser.error('Valid unprivileged port and positive timeout required')
    with socket.socket() as probe:
        probe.bind(('127.0.0.1',args.port))
    args.log_dir.mkdir(parents=True,exist_ok=True)
    args.log_dir=args.log_dir.resolve()
    before=hashes()
    if not before:
        raise RuntimeError('Build and prepare native runs before exporting')
    (args.log_dir/'outputs-before.json').write_text(json.dumps(before,indent=2)+'\n')
    names=('ProgrammableModel.class','ProgrammableModel$UniformHousingItem.class','JoinedCubeQuads.class','shape/ShapeModel.class')
    parents={name: before['build/classes/java/main/com/vandorlabs/neoforge/'+name] for name in names}
    (args.log_dir/'parent-models-before.json').write_text(json.dumps(parents,indent=2)+'\n')
    env=dict(os.environ,JAVA_HOME=java_home(21,'VANDOR_JAVA21_HOME'))
    env['PATH']=str(Path(env['JAVA_HOME'])/'bin')+os.pathsep+env.get('PATH','')
    wrapper=PLATFORM/('gradlew.bat' if os.name=='nt' else 'gradlew')
    def command(task,properties):
        values=[str(wrapper),task,'--offline','--no-daemon',*properties]
        for excluded in ('compileJava','processResources','generateModernResources'):
            values+=['-x',excluded]
        return [os.environ.get('COMSPEC','cmd.exe'),'/d','/v:off','/s','/c',subprocess.list2cmdline(values)] if os.name=='nt' else values
    processes=[]
    fixture=None
    previous=signal.getsignal(signal.SIGTERM)
    def interrupted(signum,frame):
        raise KeyboardInterrupt()
    signal.signal(signal.SIGTERM,interrupted)
    report=args.log_dir/'result.json'
    report.write_text(json.dumps({'status':'RUNNING'})+'\n')
    try:
        (PLATFORM/'run').mkdir(exist_ok=True)
        fixture=tempfile.TemporaryDirectory(prefix='housing-item-fixture-',dir=PLATFORM/'run')
        server_dir=Path(fixture.name).resolve()
        (server_dir/'eula.txt').write_text('eula=true\n')
        (server_dir/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={args.port}\nonline-mode=false\nlevel-name=fixture\nlevel-type=minecraft:flat\nspawn-protection=0\nview-distance=6\nsimulation-distance=6\nenable-rcon=false\n')
        server_log=args.log_dir/'server.log'
        with server_log.open('w') as stream:
            server=subprocess.Popen(command('runServer',['-PmodernClientFixture=true','-PmodernClientSuite=housing-items','-PmodernServerRunDirectory='+str(server_dir)]),cwd=PLATFORM,env=env,stdout=stream,stderr=subprocess.STDOUT,start_new_session=os.name!='nt')
            processes.append(server)
            deadline=time.monotonic()+180
            while 'Done (' not in server_log.read_text(errors='replace'):
                if server.poll() is not None or time.monotonic()>deadline:
                    raise RuntimeError('Owned housing item server did not become ready')
                time.sleep(.5)
            client_command=command('runClient',['-PmodernClientSmoke=true','-PmodernClientSuite=housing-items','-PmodernClientPort='+str(args.port)])
            if os.name!='nt' and not os.environ.get('DISPLAY'):
                client_command=['xvfb-run','-a','--server-args=-screen 0 1400x900x24 -ac +extension GLX +render -noreset',*client_command]
                env['LIBGL_ALWAYS_SOFTWARE']='1'
            client_log=args.log_dir/'client.log'
            started=time.time_ns()
            with client_log.open('w') as output:
                client=subprocess.Popen(client_command,cwd=PLATFORM,env=env,stdout=output,stderr=subprocess.STDOUT,start_new_session=os.name!='nt')
                processes.append(client)
                try:
                    code=client.wait(timeout=args.timeout)
                except subprocess.TimeoutExpired:
                    raise RuntimeError('Owned housing item client exceeded timeout')
            text=client_log.read_text(errors='replace')
            markers=['housing-items-model PASS','housing-items-client PASS','client-smoke PASS']
            if code or any('[vandorlabs][modern] '+marker not in text for marker in markers) or 'BUILD SUCCESSFUL' not in text or 'Stopping!' not in text:
                raise RuntimeError('Native item runtime lacked explicit model/gallery/clean completion evidence')
            files={}
            for name in ('modern-housing-item-goldens.json','modern-housing-items.png'):
                path=PLATFORM/'run/client/screenshots'/name
                if not path.is_file() or path.stat().st_mtime_ns<started:
                    raise RuntimeError('Missing or stale item artifact '+name)
                files[name]=hashlib.sha256(path.read_bytes()).hexdigest()
                shutil.copy2(path,args.log_dir/name)
            model=json.loads((PLATFORM/'run/client/screenshots/modern-housing-item-goldens.json').read_text())
            if len(model['cases']) not in (28,64):
                raise RuntimeError('Incomplete native baseline item export')
            if args.legacy_models is not None:
                tools=Path(__file__).resolve().parent
                for script,reference,current,output in (
                    ('compare_housing_item_goldens.py',args.legacy_models,'modern-housing-item-goldens.json','model-comparison.json'),
                    ('test_housing_item_images.py',args.legacy_image,'modern-housing-items.png','image-comparison.json')):
                    comparison=[sys.executable,str(tools/script),'--legacy',str(reference.resolve()),
                                '--modern',str(PLATFORM/'run/client/screenshots'/current),
                                '--report',str(args.log_dir/output)]
                    if script=='test_housing_item_images.py':comparison+=['--cases',str(len(model['cases']))]
                    subprocess.run(comparison,check=True)
            result={'status':'PASS','legacy_parity_checked':args.legacy_models is not None,'port':args.port,'parent_models':parents,'files':files,'markers':markers,'compiled_outputs':len(before)}
    except BaseException as error:
        report.write_text(json.dumps({'status':'FAILED','error':str(error)},indent=2)+'\n')
        raise
    finally:
        for process in reversed(processes):
            if process.poll() is None:
                stop_owned(process)
        if fixture is not None:
            fixture.cleanup()
        signal.signal(signal.SIGTERM,previous)
        after=hashes()
        (args.log_dir/'outputs-after.json').write_text(json.dumps(after,indent=2)+'\n')
        if before!=after:
            report.write_text(json.dumps({'status':'FAILED','error':'compiled outputs changed'})+'\n')
            raise RuntimeError('Compiled native outputs changed during baseline export')
    report.write_text(json.dumps(result,indent=2)+'\n')
    detail='legacy model and rendered-padding parity passed' if args.legacy_models else 'baseline exported without a legacy parity assertion'
    print(f'Native housing items PASS:{len(model["cases"])} actual models/gallery; '+detail+'; owned fixture stopped/discarded and compiled outputs unchanged',flush=True)


if __name__=='__main__':
    main()
