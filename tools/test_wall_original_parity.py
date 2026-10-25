#!/usr/bin/env python3
"""Compare original 1.12 actual draw records against actual native section-model records."""
import argparse,json,math,pathlib
import numpy as np
p=argparse.ArgumentParser();p.add_argument('--legacy',required=True);p.add_argument('--modern',required=True);p.add_argument('--report',required=True);a=p.parse_args()
old=json.load(open(a.legacy));new=json.load(open(a.modern))
for label,data in [('legacy',old),('modern',new)]:
 keys=[row['key'] for row in data['cases']]
 if len(keys)!=696 or len(set(keys))!=696:
  pathlib.Path(a.report).write_text(json.dumps({'pass':False,'reason':label+' requires exactly696 unique cases','count':len(keys),'unique':len(set(keys))},indent=2))
  raise SystemExit(label+' requires exactly696 unique cases')
native={r['key']:r for r in new['cases']};fail=[];stats={'cases':0,'quads':0,'maximumPositionError':0.,'maximumUvError':0.,'maximumNormalError':0.}
def oldquads(row):
 # Original fixture labels are settings IDs; actual selected sprite is authoritative.
 # Plain walls ignore a dormant SideTexture=2 field and really draw the fixed metal sprite.
 aliases={m['choice']:('26' if m['sprite']=='vandorlabs:blocks/programmable_glass/metal_side' else m['choice']) for m in row['materials']}
 out=[]
 for batch in row['batches']:
  matrix=np.array(batch['pose'],dtype=float).reshape((4,4),order='F');norm=np.linalg.inv(matrix[:3,:3]).T
  for q in batch['quads']:
   vertices=[]
   for v in q['vertices']:
    position=matrix@np.array(v[:3]+[1.]);normal=norm@np.array(v[5:8]);normal/=max(np.linalg.norm(normal),1e-30)
    vertices.append(list(position[:3])+v[3:5]+list(normal)+v[8:12])
   out.append({'pass':batch['pass'],'depthWrite':batch['depthWrite'],'material':aliases[q['material']],'vertices':vertices})
 return out
# Match complete quads including attached UV/color; winding is immaterial because old culling is disabled.
def error(expected,actual):
 best=None
 for reverse in (False,True):
  vv=actual['vertices'][::-1] if reverse else actual['vertices']
  for shift in range(4):
   array=np.array(vv[shift:]+vv[:shift],float);delta=np.abs(np.array(expected['vertices'])-array)
   values=(float(delta[:,:3].max()),float(delta[:,3:5].max()),float(delta[:,5:8].max()),float(delta[:,8:12].max()))
   score=max(values[0]/1e-6,values[1]/5e-5,values[2]/(1/127+1e-6),values[3]/.1)
   if best is None or score<best[0]:best=(score,values)
 return best
def center(q):return tuple(sum(v[k] for v in q['vertices'])/4 for k in range(3))
for row in old['cases']:
 key=row['key'];stats['cases']+=1
 if key not in native:fail.append({'key':key,'reason':'missing native case'});continue
 expected=oldquads(row);actual=list(native[key]['quads']);stats['quads']+=len(expected)
 for q in actual:q['_center']=center(q)
 if len(actual)!=len(expected):fail.append({'key':key,'reason':'quad count','old':len(expected),'native':len(actual)});continue
 for index,q in enumerate(expected):
  qc=center(q)
  candidates=[(j,error(q,n)) for j,n in enumerate(actual) if max(abs(qc[k]-n['_center'][k]) for k in range(3))<1e-5 and (n['pass'],n['depthWrite'],n['material'])==(q['pass'],q['depthWrite'],q['material'])]
  if not candidates:fail.append({'key':key,'quad':index,'reason':'material/pass/depth mismatch','expected':{k:q[k] for k in ('pass','depthWrite','material')}});break
  j,(score,values)=min(candidates,key=lambda e:e[1][0]);actual.pop(j)
  for field,value in zip(('maximumPositionError','maximumUvError','maximumNormalError'),values):stats[field]=max(stats[field],value)
  if score>1:fail.append({'key':key,'quad':index,'reason':'attached vertex position/UV/normal/color mismatch','errors':values});break
if len(native)!=len(old['cases']):fail.append({'reason':'case coverage count mismatch','old':len(old['cases']),'native':len(native)})
report={'pass':not fail,'reference':old['reference'],'tolerances':{'position':1e-6,'uv':5e-5,'normal':1/127+1e-6,'color':0},'stats':stats,'failures':fail}
pathlib.Path(a.report).write_text(json.dumps(report,indent=2));print(json.dumps({'pass':not fail,'stats':stats,'failures':fail[:8]},indent=2));raise SystemExit(0 if not fail else 1)
