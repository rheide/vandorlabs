#!/usr/bin/env python3
"""Private standalone canopy host, guarded save and complete process restart."""
import os,hashlib,subprocess,time
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
def await_exit(phase):
 deadline=time.monotonic()+60
 marker="-Dvandorlabs.transmitterPhase=canopy-host-"+phase
 while True:
  running=subprocess.check_output([str(jdk_home(21)/"bin"/("jps.exe" if os.name=="nt" else "jps")),"-lv"],text=True)
  if marker not in running:return
  if time.monotonic()>=deadline:raise RuntimeError("Canopy server JVM did not exit: "+running)
  time.sleep(.25)
@retain_artifacts
def main():
 output=Path(artifact_directory(prefix='canopy-host-native-',dir=ROOT/'build/test-artifacts'));saved=output/'saved-world';world(saved,0)
 for phase in ['write','read']:
  run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=canopy-host-'+phase),output/(phase+'.log'),'[vandorlabs][canopy-host] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
  check_log(saved/'logs/latest.log','All dimensions are saved')
  await_exit(phase)
 evidence=[]
 for path in sorted((ROOT/'platforms/neoforge-1.21.1/build/classes/java/main/com/vandorlabs/neoforge/canopy').glob('*.class')):evidence.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name)
 (output/'classes.sha256').write_text('\n'.join(evidence)+'\n');print('Native Canopy host focused PASS: '+str(output),flush=True)
if __name__=='__main__':main()
