#!/usr/bin/env python3
"""Freeze actual released non-Vehicle chip canvas submissions and font metrics."""
import argparse,gzip,hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
EXPECTED='666c0cc85fa016b926b39abf2e6c3b1ec0dd8b28edb7be6e1d2767c10a6f1c77'
def main():
 p=argparse.ArgumentParser();p.add_argument('capture',type=Path);p.add_argument('--check',action='store_true');a=p.parse_args();raw=a.capture.read_bytes()
 if len(raw)>2*1024*1024 or hashlib.sha256(raw).hexdigest()!=EXPECTED:raise AssertionError('Released journal capture changed')
 rows=[json.loads(line) for line in raw.decode().splitlines()]
 if len(rows)!=144 or {r['variant'] for r in rows}!=set(range(12)) or {r['height'] for r in rows}!={136,160,320,640}:raise AssertionError('Incomplete journal capture')
 data=gzip.compress(json.dumps({'sourceSha256':EXPECTED,'profiles':rows},separators=(',',':'),ensure_ascii=False).encode(),mtime=0);target=ROOT/'testclient/fixtures/screen-journal-overlay-reference.json.gz'
 if a.check:
  if target.read_bytes()!=data:raise AssertionError('Stale compact journal reference')
 else:target.write_bytes(data)
 print('Released journal reference PASS profiles=144 compressedBytes='+str(len(data)))
if __name__=='__main__':main()
