#!/usr/bin/env python3
"""Compact actual whole-role Source row submissions, keeping local commands and independent GL poses."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path

def compact(path):
    rows=[json.loads(line) for line in path.read_text().splitlines()]
    styles=[];profiles=[];indices={}
    for row in rows:
        draw=row['draw'];height=64 if draw['data'][1]==1115684864 else 128
        # Float bits 64/128 are checked explicitly, rather than inferred from role names.
        if draw['data'][1] not in (1115684864,1124073472):raise AssertionError('Source canvas height')
        text=[{key:value for key,value in command.items() if key!='matrix'} for command in row['text']]
        style=dict(height=height,title=row['title'],trimTitle=row['trimTitle'],rows=row['rows'],stride=draw['stride'],data=draw['data'],text=text)
        key=json.dumps(style,separators=(',',':'),sort_keys=True)
        if key not in indices:indices[key]=len(styles);styles.append(style)
        if any(command['matrix']!=row['text'][0]['matrix'] for command in row['text']):raise AssertionError('Per-command text pose drift')
        profile={key:row[key] for key in ('id','meta','slot','variant','frame','small','wall','ceiling','ceilingPosition','content')}
        profile.update(style=indices[key],matrix=draw['matrix'],textMatrix=row['text'][0]['matrix']);profiles.append(profile)
    if len(rows)!=1148 or len({row['id'] for row in rows})!=7 or len(styles)!=14:raise AssertionError('Source row profile/style coverage '+str((len(rows),len(styles))))
    return dict(schema=1,sourceSha256=hashlib.sha256(path.read_bytes()).hexdigest(),styles=styles,profiles=profiles)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('capture',type=Path);parser.add_argument('output',type=Path);parser.add_argument('--previous',type=Path);args=parser.parse_args()
    if args.previous:
        old=[json.loads(line) for line in args.previous.read_text().splitlines()]
        new=[json.loads(line) for line in args.capture.read_text().splitlines()]
        for row in new:row.pop('content',None)
        if old!=new:raise AssertionError('Independent Source row commands/poses changed')
    result=compact(args.capture);payload=json.dumps(result,separators=(',',':')).encode();args.output.parent.mkdir(parents=True,exist_ok=True)
    with args.output.open('wb') as stream:
        with gzip.GzipFile(fileobj=stream,mode='wb',mtime=0) as output:output.write(payload)
    print('Source row reference PASS profiles=1148 styles=14 bytes='+str(len(payload))+' source='+result['sourceSha256'])

if __name__=='__main__':main()
