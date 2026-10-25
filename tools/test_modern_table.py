#!/usr/bin/env python3
"""Bounded isolated table rendering with immutable outputs and actual chunk-cache transitions."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time
from build_all import ROOT, java_home
from test_modern_runtime import stop_owned, FATAL

PLATFORM=ROOT/'platforms/neoforge-1.21.1'
IMAGES=[f'modern-table-state-{i}{suffix}.png' for i in range(32) for suffix in ('','-empty')]+[f'modern-table-boundary-{i}-{phase}.png' for i in range(8) for phase in ('unloaded','reloaded')]+['modern-table-items.png']

def hashes():
    return {str(path.relative_to(PLATFORM)):hashlib.sha256(path.read_bytes()).hexdigest() for folder in ('build/classes/java/main','build/resources/main') for path in sorted((PLATFORM/folder).rglob('*')) if path.is_file()}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port',type=int,default=25574)
    parser.add_argument('--timeout',type=int,default=900)
    parser.add_argument('--log-dir',type=Path,default=ROOT/'build/reports/modern-table')
    parser.add_argument('--legacy-models',type=Path,help='Actual Forge export from test_viewscreen.sh --focus gallery_display_table')
    args=parser.parse_args();args.log_dir.mkdir(parents=True,exist_ok=True)
    env=dict(os.environ,JAVA_HOME=java_home(21,'VANDOR_JAVA21_HOME'));env['PATH']=str(Path(env['JAVA_HOME'])/'bin')+os.pathsep+env.get('PATH','')
    wrapper=PLATFORM/('gradlew.bat' if os.name=='nt' else 'gradlew')
    base=[str(wrapper),'--offline','--no-daemon','-PmodernClientSuite=table','-PmodernClientPort='+str(args.port),'-x','compileJava','-x','processResources','-x','generateModernResources']
    def command(extra):
        result=base+extra
        if os.name=='nt':return [os.environ.get('COMSPEC','cmd.exe'),'/d','/v:off','/s','/c',subprocess.list2cmdline(result)]
        return result
    server_dir=PLATFORM/'run/server';server_dir.mkdir(parents=True,exist_ok=True)
    properties=server_dir/'server.properties';original=properties.read_bytes() if properties.exists() else None
    current=properties.read_text() if properties.exists() else 'online-mode=false\nlevel-name=table-smoke\nlevel-type=minecraft:flat\nspawn-protection=0\n'
    current='\n'.join(line for line in current.splitlines() if not line.startswith(('server-port=','server-ip=','level-name=')))+f'\nserver-ip=127.0.0.1\nserver-port={args.port}\nlevel-name=industrial-table-client\n'
    properties.write_text(current);(server_dir/'eula.txt').write_text('eula=true\n')
    screenshot_dir=PLATFORM/'run/client/screenshots';screenshot_dir.mkdir(parents=True,exist_ok=True)
    for name in IMAGES+['industrial-table-native-models.json']:(screenshot_dir/name).unlink(missing_ok=True)
    before=hashes();(args.log_dir/'outputs-before.json').write_text(json.dumps(before,indent=2)+'\n')
    server=None;client=None;started=time.time()
    try:
        with (args.log_dir/'server.log').open('w') as server_log:
            server=subprocess.Popen(command(['runServer','-PmodernClientFixture=true']),cwd=PLATFORM,env=env,stdout=server_log,stderr=subprocess.STDOUT,start_new_session=os.name!='nt')
            deadline=time.monotonic()+180
            while 'Done (' not in (args.log_dir/'server.log').read_text(errors='replace'):
                if server.poll() is not None:raise RuntimeError('Fixture server exited before ready')
                if time.monotonic()>deadline:raise RuntimeError('Fixture startup timeout')
                time.sleep(.5)
            print('Isolated table fixture ready; running native client',flush=True)
            with (args.log_dir/'client.log').open('w') as client_log:
                client_command=command(['runClient','-PmodernClientSmoke=true'])
                if os.name!='nt':client_command=['xvfb-run','-a','-s','-screen 0 1280x720x24',*client_command]
                client=subprocess.Popen(client_command,cwd=PLATFORM,env=env,stdout=client_log,stderr=subprocess.STDOUT,start_new_session=os.name!='nt')
                try:code=client.wait(timeout=args.timeout)
                except subprocess.TimeoutExpired:stop_owned(client);raise RuntimeError('Table client timeout')
            transcript=(args.log_dir/'client.log').read_text(errors='replace')
            if code or FATAL.search(transcript):raise RuntimeError('Table client failed; inspect client.log')
            for marker in ('table-client PASS','client-smoke PASS','BUILD SUCCESSFUL','Stopping!'):
                if marker not in transcript:raise RuntimeError('Missing native marker: '+marker)
            for name in IMAGES+['industrial-table-native-models.json']:
                path=screenshot_dir/name
                if not path.is_file() or path.stat().st_mtime<started-1:raise RuntimeError('Missing fresh artifact: '+name)
            command=[sys.executable,str(ROOT/'tools/test_modern_table_images.py'),'--screenshots',str(screenshot_dir),'--report',str(args.log_dir/'pixels.json')]
            if args.legacy_models:command+=['--legacy-models',str(args.legacy_models)]
            subprocess.run(command,check=True)
    finally:
        if client is not None and client.poll() is None:stop_owned(client)
        if server is not None and server.poll() is None:stop_owned(server)
        if original is None:properties.unlink(missing_ok=True)
        else:properties.write_bytes(original)
        after=hashes();(args.log_dir/'outputs-after.json').write_text(json.dumps(after,indent=2)+'\n')
        if before!=after:raise RuntimeError('Compiled native outputs changed during table run')
    report={'status':'PASS','images':len(IMAGES),'compiledFiles':len(before),'legacyModelsCompared':bool(args.legacy_models)}
    (args.log_dir/'result.json').write_text(json.dumps(report,indent=2)+'\n');print('Native table PASS:',json.dumps(report),flush=True)

if __name__=='__main__':main()
