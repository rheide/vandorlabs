#!/usr/bin/env python3
"""Check actual partial-fluid views, visible drain/refill and rejected false camera captures."""
import argparse
import json
from pathlib import Path
import numpy as np
from PIL import Image

NAMES=('water-joined-above','water-reference','water-inside','water-joined-below',
       'water-air-exit','water-drained','water-refilled','lava-inside','stair-water')


def read(path):
    pixels=np.asarray(Image.open(path).convert('RGB'),dtype=np.int32)
    assert pixels.shape==(720,1280,3),f'Wrong capture dimensions: {path}'
    return pixels


def horizon(pixels):
    r,g,b=pixels.transpose(2,0,1)
    sky=(r>100)&(g>130)&(b>220)&(b-r>25)&(b-g>10)
    rows=[int(np.flatnonzero(sky[:600,x])[-1]) for x in range(40,1240,40) if sky[:600,x].any()]
    assert len(rows)>=20,'Above-fluid view must include the actual open sky'
    return float(np.median(rows))


def blue_count(pixels):
    r,g,b=pixels[340:450,580:710].transpose(2,0,1)
    return int(np.count_nonzero((b>r+35)&(b>g*1.2)&(b>100)))


def world(pixels,stair=False):
    row=horizon(pixels)
    assert row<=(245 if stair else 300),f'Low/back camera cannot establish visible fluid surface: horizon={row}'
    count=blue_count(pixels)
    assert count>=(500 if stair else 1500),f'Actual blue fluid surface absent: pixels={count}'
    return {'horizon':row,'center_blue_pixels':count}


def fog(pixels,lava=False):
    mean=pixels[40:250,100:1100].mean(axis=(0,1))
    if lava:
        assert mean[0]>mean[1]+60 and mean[0]>mean[2]*5,'Actual lava fog must be orange/red'
    else:
        assert mean[2]>mean[0]+40 and mean[2]>mean[1]+25,'Actual water fog must be blue'
    return mean.tolist()


def drain(images):
    counts={name:blue_count(images[name]) for name in ('water-air-exit','water-drained','water-refilled')}
    # The crop covers the selected host, excluding most moving native water and the held item.
    assert counts['water-air-exit']-counts['water-drained']>=1000,f'Drained host retained its visible water mesh: {counts}'
    assert counts['water-refilled']-counts['water-drained']>=1000,f'Refilled host did not restore its visible water mesh: {counts}'
    return counts


def rejected(function,*args):
    try:function(*args)
    except AssertionError:return True
    raise AssertionError('False-capture negative was accepted by the positive inspector')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--screenshots',type=Path,required=True)
    parser.add_argument('--output-dir',type=Path,required=True)
    parser.add_argument('--negative-above',type=Path)
    parser.add_argument('--negative-stair',type=Path)
    parser.add_argument('--negative-mesh-dir',type=Path)
    args=parser.parse_args()
    images={name:read(args.screenshots/f'modern-contained-{name}.png') for name in NAMES}
    report={'world':{name:world(images[name],name=='stair-water') for name in ('water-joined-above','water-air-exit','water-refilled','stair-water')},
            'fog':{name:fog(images[name],name=='lava-inside') for name in ('water-reference','water-inside','water-joined-below','lava-inside')},
            'visible_drain_refill':drain(images),'negatives':[]}
    rejected(fog,images['water-inside'],True);report['negatives'].append('water-as-lava')
    rejected(fog,images['lava-inside'],False);report['negatives'].append('lava-as-water')
    unchanged=dict(images);unchanged['water-drained']=images['water-air-exit'];rejected(drain,unchanged);report['negatives'].append('unchanged-visible-water')
    if args.negative_above:
        rejected(world,read(args.negative_above));report['negatives'].append('actual-low-above-camera')
    if args.negative_stair:
        rejected(world,read(args.negative_stair),True);report['negatives'].append('actual-solid-stair-back-camera')
    if args.negative_mesh_dir:
        prior={name:read(args.negative_mesh_dir/f'modern-contained-{name}.png') for name in ('water-air-exit','water-drained','water-refilled')}
        rejected(drain,prior);report['negatives'].append('actual-stale-drained-mesh')
    args.output_dir.mkdir(parents=True,exist_ok=True)
    (args.output_dir/'contained-fluid-pixel-contracts.json').write_text(json.dumps(report,indent=2)+'\n')
    print(f'PASS9 actual fluid captures: visible surface/camera, native water/lava fog, real drain/refill; {len(report["negatives"])} false captures rejected')


if __name__=='__main__':main()
