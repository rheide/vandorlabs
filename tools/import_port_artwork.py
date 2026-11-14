#!/usr/bin/env python3
"""Import port artwork at the established block resolution and append its Hull face."""
import argparse
import hashlib
import json
from pathlib import Path
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
CATALOG=ROOT/'generated-resources/assets/vandorlabs/data/unified_textures.json'
TARGET=ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks/resource_ports'
NAMES=['port-hull']+[kind+'-'+direction+'-'+state for kind in ('power','fluid','matter') for direction in ('in','out') for state in ('off','on')]
PREFIX_COUNT=850
PREFIX_SHA='3eb656e2f4d2dd01c98f5be1f2c23d1a27642336bcbbe895177287dd23e53768'
ENTRY=dict(id='port_hull',label='Port Hull',category='Hull',source='resource_ports/port_hull')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source',type=Path,nargs='?')
    parser.add_argument('--check',action='store_true')
    args=parser.parse_args()
    if not args.check and args.source is None:parser.error('source artwork directory required')
    entries=json.loads(CATALOG.read_text())
    assert hashlib.sha256('\n'.join(e['id'] for e in entries[:PREFIX_COUNT]).encode()).hexdigest()==PREFIX_SHA,'Earlier saved texture choices moved'
    if len(entries)==PREFIX_COUNT:entries.append(ENTRY)
    assert entries[PREFIX_COUNT]==ENTRY,'Port Hull saved choice moved'
    for name in NAMES:
        target=TARGET/(name.replace('-','_')+'.png')
        if args.source:
            with Image.open(args.source/(name+'.png')) as original:
                art=original.convert('RGBA').resize((64,64),Image.Resampling.LANCZOS)
                if args.check:
                    with Image.open(target) as actual:assert actual.size==(64,64) and actual.convert('RGBA').tobytes()==art.tobytes(),name
                else:
                    target.parent.mkdir(parents=True,exist_ok=True);art.save(target,optimize=True)
        with Image.open(target) as actual:assert actual.size==(64,64),name
    if args.check:assert json.loads(CATALOG.read_text())==entries,'Missing Hull entry'
    else:CATALOG.write_text(json.dumps(entries,indent=2)+'\n')
    print('Port artwork PASS: thirteen64x64 faces; original850 saved texture IDs/order retained; Port Hull appended')

if __name__=='__main__':main()
