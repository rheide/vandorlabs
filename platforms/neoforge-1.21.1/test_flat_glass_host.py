#!/usr/bin/env python3
"""Private ordinary Glass authority, actual Source contact replay and whole-process restart."""
import argparse,hashlib,os,re
from pathlib import Path
from test_transmitter import ROOT,command,run,world,jdk_home
from test_propulsion_world import await_shutdown
from native_test_artifacts import artifact_directory,retain_artifacts
def evidence(out):
 paths=list((ROOT/'platforms/neoforge-1.21.1/build/classes/java/main/com/vandorlabs/neoforge/glass').glob('*.class'))
 paths += [ROOT/'platforms/neoforge-1.21.1/build/classes/java/main/com/vandorlabs/neoforge/shape/NativeWallTopology.class',ROOT/'platforms/neoforge-1.21.1/build/classes/java/main/com/vandorlabs/neoforge/shape/NativeDiagonalWallTopology.class',ROOT/'platforms/neoforge-1.21.1/build/classes/java/main/com/vandorlabs/neoforge/mixin/NativeGlassRayMixin.class',ROOT/'platforms/neoforge-1.21.1/src/main/resources/vandorlabs.mixins.json']
 paths += [ROOT/'build/test-artifacts'/name for name in ['flat-glass-contact-symmetry-forge.jsonl','flat-glass-fluid-forge.jsonl']]
 (out/'classes-and-reference.sha256').write_text('\n'.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+str(p.relative_to(ROOT)) for p in sorted(paths) if p.is_file())+'\n')
@retain_artifacts
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--rays',action='store_true');parser.add_argument('--fluids',action='store_true');args=parser.parse_args()
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
 out=Path(artifact_directory(prefix='flat-glass-host-',dir=base));saved=out/'saved-world';world(saved,0)
 for phase in (['fluids'] if args.fluids else ['rays'] if args.rays else ['write','read']):
  log=out/(phase+'.log')
  try:run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=flat-glass-host-'+phase),log,'[vandorlabs][flat-glass-host] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_FLAT_GLASS_CONTACT_REFERENCE=str(ROOT/'build/test-artifacts/flat-glass-contact-symmetry-forge.jsonl'),VANDOR_FLAT_GLASS_FLUID_REFERENCE=str(ROOT/'build/test-artifacts/flat-glass-fluid-forge.jsonl')))
  finally:
   pid=re.search(r'actual server PID=(\d+)',log.read_text(errors='replace')) if log.is_file() else None
   if pid:await_shutdown(saved,int(pid.group(1)))
   evidence(out)
 print(('Private ordinary Glass fluids PASS ' if args.fluids else 'Private ordinary Glass rays PASS ' if args.rays else 'Private ordinary Glass host/save/restart PASS ')+str(out),flush=True)
if __name__=='__main__':main()
