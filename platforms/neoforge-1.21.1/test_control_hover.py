#!/usr/bin/env python3
"""Actual mounted-control hover outline cost, target frames and screenshot on a frozen standard JAR."""
import argparse,hashlib,os,socket,subprocess,tempfile,time
from pathlib import Path
from test_cube import ROOT,PLATFORM,command,world,run,launch,stop,check_log,jdk_home
from test_transmitter_copy import install

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--jar',type=Path);p.add_argument('--xvfb',action='store_true');p.add_argument('--bounded',action='store_true');a=p.parse_args()
 if a.xvfb and os.name=='nt':p.error('--xvfb requires Linux')
 env=dict(os.environ,JAVA_HOME=str(jdk_home(21)));env.pop('MOD_CLASSES',None)
 if a.bounded:env['VANDOR_HOVER_BOUNDED']='true'
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(tempfile.mkdtemp(prefix='control-hover-',dir=base))
 with (out/'build.log').open('w') as log:subprocess.run(command(*(('copySmokeJar',) if a.jar else ('jar','copySmokeJar'))),cwd=PLATFORM,env=env,stdout=log,stderr=subprocess.STDOUT,check=True)
 jar=a.jar.resolve() if a.jar else next((PLATFORM/'build/libs').glob('*.jar'));fixture=next((PLATFORM/'build/test-fixtures').glob('*copy-smoke.jar'));sha=hashlib.sha256(jar.read_bytes()).hexdigest();(out/'artifact.sha256').write_text(sha+'  '+jar.name+'\n')
 with socket.socket() as listener:listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
 serverworld=out/'server';world(serverworld,port)
 with (serverworld/'server.properties').open('a') as settings:settings.write('level-type=minecraft:flat\n')
 server=launch(command('runServer','-PmodernServerRunDirectory='+str(serverworld),'-PtransmitterPhase=control-hover',*install(serverworld,jar,fixture)),out/'server.log',env)
 try:
  deadline=time.monotonic()+180
  while 'Done (' not in (out/'server.log').read_text(errors='replace'):
   if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Hover server startup failed: '+str(out))
   time.sleep(.5)
  client=out/'client';client.mkdir();(client/'options.txt').write_text('guiScale:2\ntutorialStep:none\nrenderDistance:2\nsimulationDistance:2\nmaxFps:60\nonboardAccessibility:false\n')
  cmd=command('runClient','-PmodernClientRunDirectory='+str(client),'-PmatterClientPort='+str(port),'-PtransmitterPhase=control-hover',*install(client,jar,fixture))
  if a.xvfb:cmd=['xvfb-run','-a','-s','-screen 0 1280x720x24',*cmd];env['LIBGL_ALWAYS_SOFTWARE']='1'
  run(cmd,out/'client.log','[vandorlabs][control-hover] client PASS',env)
  check_log(out/'server.log','[vandorlabs][control-hover] server ready');
  if not (client/'screenshots/industrial-hover-native.png').is_file():raise RuntimeError('Missing actual hover screenshot')
 finally:stop(server)
 check_log(serverworld/'logs/latest.log','All dimensions are saved')
 if hashlib.sha256(jar.read_bytes()).hexdigest()!=sha:raise RuntimeError('Frozen production JAR changed')
 print('Mounted control hover PASS '+str(out),flush=True)
if __name__=='__main__':main()
