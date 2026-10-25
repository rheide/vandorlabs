#!/usr/bin/env python3
"""Verify actual pane-stage readbacks against their already-rendered native water backgrounds."""
import argparse,json,pathlib
import numpy as np
from PIL import Image
p=argparse.ArgumentParser();p.add_argument('--screenshots',type=pathlib.Path,required=True);p.add_argument('--report',type=pathlib.Path,required=True);a=p.parse_args()
data=json.loads((a.screenshots/'modern-wall-pane-gpu.json').read_text());cases=data['cases'];expected={f'modern-wall-pane-{g}-{m}.png' for g in ('fancy','fabulous') for m in ('stock','mild','normal','strong')}
def inspect(row):
 before=np.asarray(row['before']).reshape(48,48,4);after=np.asarray(row['after']).reshape(48,48,4)
 changed=int((np.abs(before[:,:,:3]-after[:,:,:3]).max(axis=2)>2).sum());water=int((before[:,:,2]>before[:,:,0]+10).sum())
 if changed<=64 or water<=64:raise AssertionError('Pane draw/native-water rendered negative missing: '+row['scene'])
 strong='strong' in row['scene'];assert row['depthChanged']>64 if strong else row['depthChanged']==0
 assert row['fabulous']==('fabulous' in row['scene']);return {'name':row['scene'],'changed':changed,'nativeWater':water,'depthChanged':row['depthChanged']}
assert len(cases)==8 and {r['scene'] for r in cases}==expected
report=[inspect(row) for row in cases]
for row in cases:
 im=Image.open(a.screenshots/row['scene']).convert('RGB');assert im.size==(1280,720);crop=np.asarray(im)[300:420,580:700];assert crop.std()>8,'Blank actual world capture'
 # Reusing the actual rendered background simulates a skipped pane and must fail the same comparator.
 bad=dict(row,after=row['before'])
 try:inspect(bad)
 except AssertionError:pass
 else:raise AssertionError('Rendered before-pane negative was accepted')
a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps({'pass':True,'cases':report,'renderedNegatives':8},indent=2));print('Wall pane GPU PASS8 scenes/8actual rendered native-water no-pane negatives')
