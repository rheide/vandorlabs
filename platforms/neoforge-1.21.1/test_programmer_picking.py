#!/usr/bin/env python3
"""Frozen actual source rays through the inactive Java 21 authored picker."""
import argparse,os,json,hashlib
from pathlib import Path
from test_transmitter import ROOT,command,run,world,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--reference',type=Path,default=ROOT/'build/test-artifacts/programmer-rays-forge.jsonl');parser.add_argument('--geometry',type=Path,default=ROOT/'src/main/resources/assets/vandorlabs/models/block/data_chip_programmer/ready/parts');args=parser.parse_args();reference=args.reference.resolve();geometry=args.geometry.resolve();lines=reference.read_bytes().splitlines(keepends=True);summary=json.loads(lines[-1]);assert summary['golden']=='0aaef112119fe6e8f5f72e5462843a3d4af766426f35055a143dc93fd4a8ce54' and len(lines)==27745 and hashlib.sha256(b''.join(lines[:-1])).hexdigest()==summary['golden'];assert (geometry/'static.obj').is_file() and (geometry/'screen.obj').is_file()
 output=Path(artifact_directory(prefix='programmer-picking-',dir=ROOT/'build/test-artifacts'));saved=output/'world';world(saved,0)
 run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=programmer-picking'),output/'server.log','[vandorlabs][programmer-picking] PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_PROGRAMMER_RAYS_REFERENCE=str(reference),VANDOR_PROGRAMMER_GEOMETRY=str(geometry)))
 print('Inactive native Programmer picking PASS: '+str(output),flush=True)
if __name__=='__main__':main()
