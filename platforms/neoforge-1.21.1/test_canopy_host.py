#!/usr/bin/env python3
"""Private standalone canopy host, guarded save and complete process restart."""
import os,hashlib,subprocess,time,argparse
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
def await_exit(phase,kind):
 deadline=time.monotonic()+60
 marker="-Dvandorlabs.transmitterPhase="+kind+phase
 while True:
  running=subprocess.check_output([str(jdk_home(21)/"bin"/("jps.exe" if os.name=="nt" else "jps")),"-lv"],text=True)
  if marker not in running:return
  if time.monotonic()>=deadline:raise RuntimeError("Canopy server JVM did not exit: "+running)
  time.sleep(.25)
@retain_artifacts
def main():
 options=argparse.ArgumentParser();group=options.add_mutually_exclusive_group();group.add_argument("--reservations",action="store_true");group.add_argument("--placement",action="store_true");group.add_argument("--item",action="store_true");group.add_argument("--configuration",action="store_true");group.add_argument("--motion",action="store_true");group.add_argument("--harvest",action="store_true");group.add_argument("--harvest-items",action="store_true");args=options.parse_args();kind="canopy-host-item-harvest-" if args.harvest_items else "canopy-host-harvest-" if args.harvest else "canopy-host-motion-" if args.motion else "canopy-host-configuration-" if args.configuration else "canopy-host-item-" if args.item else "canopy-host-placement-" if args.placement else "canopy-host-reservations-" if args.reservations else "canopy-host-"
 output=Path(artifact_directory(prefix='canopy-host-native-',dir=ROOT/'build/test-artifacts'));saved=output/'saved-world';world(saved,0)
 for phase in ['write','read']:
  run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase='+kind+phase),output/(phase+'.log'),'[vandorlabs][canopy-host] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_CANOPY_MOTION_REFERENCE=str(ROOT/"build/test-artifacts/canopy-motion-forge.jsonl"),VANDOR_CANOPY_APPLY_REFERENCE=str(ROOT/"build/test-artifacts/canopy-apply-forge.jsonl"),VANDOR_CANOPY_JOINED_REFERENCE=str(ROOT/"build/test-artifacts/canopy-joined-forge.jsonl"),VANDOR_CANOPY_RESERVATION_REFERENCE=str(ROOT/"build/test-artifacts/canopy-reservations-forge.jsonl"),VANDOR_CANOPY_CONFIGURED_REFERENCE=str(ROOT/"build/test-artifacts/canopy-configured-item-forge.jsonl"),VANDOR_CANOPY_PLACEMENT_REFERENCE=str(ROOT/"build/test-artifacts/canopy-placement-forge.jsonl")))
  check_log(saved/'logs/latest.log','All dimensions are saved')
  await_exit(phase,kind)
 evidence=[]
 for path in sorted((ROOT/'platforms/neoforge-1.21.1/build/classes/java/main/com/vandorlabs/neoforge/canopy').glob('*.class')):evidence.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name)
 (output/'classes.sha256').write_text('\n'.join(evidence)+'\n');print('Native Canopy host focused PASS: '+str(output),flush=True)
if __name__=='__main__':main()
