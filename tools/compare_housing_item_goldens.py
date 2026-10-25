#!/usr/bin/env python3
"""Compare independently exported actual legacy/native housing ItemRenderer contracts."""
import argparse
import copy
import json
import math
from pathlib import Path


def canonical(quad):
    points=quad['vertices']
    assert len(points)==4 and all(len(point)==5 and all(map(math.isfinite,point)) for point in points)
    points=min([points[i:]+points[:i] for i in range(4)],key=lambda value:tuple(round(x,6) for p in value for x in p[:3]))
    return (quad['bucket'],quad['face'],tuple(round(x,6) for p in points for x in p[:3])),points,quad


def compare(old,new):
    errors=[]
    for field in ('ao','gui3d','payloadUnchanged'):
        if old[field]!=new[field] or field=='payloadUnchanged' and not new[field]:
            errors.append(field)
    gui=max(abs(a-b) for a,b in zip(old['gui'],new['gui']))
    if gui>1e-6:
        errors.append('GUI transform')
    left=sorted(map(canonical,old['quads']),key=lambda value:value[0])
    right=sorted(map(canonical,new['quads']),key=lambda value:value[0])
    uv=0
    if [value[0] for value in left]!=[value[0] for value in right]:
        errors.append('geometry/winding/cull/face')
    else:
        for (_,a,qa),(_,b,qb) in zip(left,right):
            for field in ('material','shade','tint'):
                if qa[field]!=qb[field] and field not in errors:
                    errors.append(field)
            uv=max(uv,*(abs(x-y) for p,q in zip(a,b) for x,y in zip(p[3:],q[3:])))
        if uv>4e-4:
            errors.append('UV')
    return errors,{'max_uv_error':uv,'max_gui_error':gui}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--legacy',type=Path,required=True)
    parser.add_argument('--modern',type=Path,required=True)
    parser.add_argument('--report',type=Path,required=True)
    args=parser.parse_args()
    old_data=json.loads(args.legacy.read_text());new_data=json.loads(args.modern.read_text())
    assert old_data['format']==new_data['format']==1
    assert old_data['rectangularChoice']==new_data['rectangularChoice']
    old={row['key']:row for row in old_data['cases']};new={row['key']:row for row in new_data['cases']}
    assert len(old)==len(new) and len(old) in (28,64) and old.keys()==new.keys(),'Expected complete actual ItemRenderer cases'
    rows=[]
    for key in old:
        errors,metrics=compare(old[key],new[key]);rows.append({'key':key,'errors':errors,**metrics})
    sample=next(iter(new.values()))
    wrong=copy.deepcopy(sample);wrong['quads'][0]['material']='wrong_material'
    assert 'material' in compare(sample,wrong)[0],'Material negative accepted'
    wrong=copy.deepcopy(sample);wrong['quads'][0]['vertices'][0][3]+=.125
    assert 'UV' in compare(sample,wrong)[0],'UV negative accepted'
    wrong=copy.deepcopy(sample);wrong['payloadUnchanged']=False
    assert 'payloadUnchanged' in compare(sample,wrong)[0],'Payload mutation negative accepted'
    failures=[row for row in rows if row['errors']]
    result={'cases':rows,'failed_cases':len(failures),'status':'FAIL' if failures else 'PASS','negative_controls':['material','UV','payload']}
    args.report.write_text(json.dumps(result,indent=2)+'\n')
    if failures:
        for row in failures:
            print(f"FAIL {row['key']}: {','.join(row['errors'])}; UV={row['max_uv_error']:.8f}, GUI={row['max_gui_error']:.8f}")
        raise SystemExit(f'FAIL {len(failures)}/{len(old)} actual housing item contracts')
    print(f'PASS {len(old)} actual housing item contracts: geometry/cull/winding, materials/UV, shading, GUI transforms and retained payloads; three negatives rejected')


if __name__=='__main__':main()
