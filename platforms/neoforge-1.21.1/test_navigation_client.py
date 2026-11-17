#!/usr/bin/env python3
"""Native navigation player interactions, map raster and actual observer/table/screen rendering."""
import argparse,os,socket,time,hashlib
from pathlib import Path
from test_cube import ROOT,command,launch,stop,check_log,jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory,retain_artifacts
from navigation_test_artifact import snapshot
from contextlib import contextmanager
@contextmanager
def frozen(out):
 baseline=snapshot(out,"before")
 try:yield
 finally:snapshot(out,"after",baseline)
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--xvfb',action='store_true');parser.add_argument('--full-bay-negative',action='store_true');options=parser.parse_args()
 if options.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
 env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.navigationClientCheck=true -Dvandorlabs.navigationFullBayChecks=true');
 if options.full_bay_negative:env['JAVA_TOOL_OPTIONS']+=' -Dvandorlabs.navigationFullBayNegative=true'
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(artifact_directory(prefix='navigation-client-',dir=base))
 with frozen(out):
  with socket.socket() as listener:listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
  extra=['-x','compileJava','-x','compileSmokeJava','-x','processResources'];saved=out/'server';fixture_world(saved,port);server_log=out/'server.log';server=launch(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=navigation-client',*extra),server_log,env)
  try:
   deadline=time.monotonic()+120
   while 'Done (' not in server_log.read_text(errors='replace'):
    if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Native navigation server startup: '+str(server_log))
    time.sleep(.5)
   client=out/'client';client.mkdir();(client/'options.txt').write_text('guiScale:2\ntutorialStep:none\nrenderDistance:2\nsimulationDistance:5\nmaxFps:60\nentityShadows:false\nonboardAccessibility:false\n')
   args=command('runClient','-PtransmitterPhase=navigation-client','-PmatterClientPort='+str(port),'-PmodernClientRunDirectory='+str(client),*extra)
   if options.xvfb:args=['xvfb-run','-a','-s','-screen 0 1280x720x24']+args;env=dict(env,LIBGL_ALWAYS_SOFTWARE='1')
   process=launch(args,out/'client.log',env)
   try:
    code=process.wait(timeout=240)
    if code:raise RuntimeError('Native navigation client exit '+str(code)+': '+str(out/'client.log'))
    check_log(out/'client.log','[vandorlabs][navigation-client] client PASS')
   finally:stop(process)
   deadline=time.monotonic()+30
   while '[vandorlabs][navigation-client] logout PASS' not in server_log.read_text(errors='replace'):
    if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Native navigation logout missing: '+str(server_log))
    time.sleep(.1)
   for marker in ['setup','ownership','logout']:check_log(server_log,'[vandorlabs][navigation-client] '+marker+' PASS')
  finally:stop(server)
  deadline=time.monotonic()+30
  while time.monotonic()<deadline and 'All dimensions are saved' not in (saved/'logs/latest.log').read_text(errors='replace'):time.sleep(.1)
  check_log(saved/'logs/latest.log','All dimensions are saved')
 print('Actual native navigation player PASS: '+str(out),flush=True)
if __name__=='__main__':main()
