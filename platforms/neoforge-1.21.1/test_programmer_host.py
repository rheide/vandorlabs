#!/usr/bin/env python3
"""Private root-only Programmer host, physical inventory and opaque snapshot restart."""
import os,argparse,json
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--inventory-reference',type=Path,default=ROOT/'build/test-artifacts/programmer-inventory-forge.json');parser.add_argument('--wire-reference',type=Path,default=ROOT/'build/test-artifacts/programmer-wire-forge.json');parser.add_argument('--world-rays-reference',type=Path,default=ROOT/'build/test-artifacts/programmer-world-rays-forge.jsonl');parser.add_argument('--geometry',type=Path,default=ROOT/'src/main/resources/assets/vandorlabs/models/block/data_chip_programmer/ready/parts');args=parser.parse_args();world_rays=args.world_rays_reference.resolve();rays_lines=world_rays.read_text().splitlines();assert len(rays_lines)==6937 and json.loads(rays_lines[-1])['golden']=='ba684407a8d4cffedc2b87accb8cf3f43371015c5bdf68a9d3f3b242d3c03bb9';geometry=args.geometry.resolve();assert (geometry/'static.obj').is_file() and (geometry/'screen.obj').is_file();wire=args.wire_reference.resolve();wire_source=json.loads(wire.read_text());assert wire_source['golden']=='1ab9e097109d822c9382f571957444293258799e27c41053bb881045a1b8f69a' and len(wire_source['cases'])==286;reference=args.inventory_reference.resolve();source=json.loads(reference.read_text());assert source['golden']=='488ea87cd420cc0f0520ed3e1f21d54d44ac393d240743099698485ae8b3f678' and len(source['cases'])==1800
 output=Path(artifact_directory(prefix='programmer-host-',dir=ROOT/'build/test-artifacts'));saved=output/'saved-world';world(saved,0)
 with (saved/'server.properties').open('a') as properties:properties.write('level-type=minecraft:flat\nlevel-seed=0\ngenerate-structures=false\n')
 for phase in ('write','read'):
  run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=programmer-host-'+phase),output/(phase+'.log'),'[vandorlabs][programmer-host] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_PROGRAMMER_INVENTORY_REFERENCE=str(reference),VANDOR_PROGRAMMER_WIRE_REFERENCE=str(wire),VANDOR_PROGRAMMER_WORLD_RAYS_REFERENCE=str(world_rays),VANDOR_PROGRAMMER_GEOMETRY=str(geometry)))
  check_log(saved/'logs/latest.log','All dimensions are saved')
 print('Private native Programmer host write/restart PASS: '+str(output),flush=True)
if __name__=='__main__':main()
