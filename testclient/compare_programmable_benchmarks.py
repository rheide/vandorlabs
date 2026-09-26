#!/usr/bin/env python3
"""Compare matching live rendering microbenchmarks; never interpret these as FPS."""
import argparse
import csv
from pathlib import Path


def read(path):
    with path.open() as source:
        return {(r['block'], r['variant'], r['count']): r for r in csv.DictReader(source)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('before', type=Path)
    parser.add_argument('after', type=Path)
    args = parser.parse_args()
    before, after = read(args.before), read(args.after)
    if before.keys() != after.keys():
        raise SystemExit('Benchmark cases do not match')
    print('| Block / variant (64 instances) | Before submission ms | After submission ms | Change | After completion ms |')
    print('| --- | ---: | ---: | ---: | ---: |')
    for key, a in after.items():
        if key[2] != '64':
            continue
        b = before[key]
        old, new = float(b['submit_p50_ms']), float(a['submit_p50_ms'])
        label = key[0].replace('vandorlabs:', '').replace('minecraft:', 'vanilla ')
        if key[1] != 'default':
            label += ' / ' + key[1]
        print(f'| {label} | {old:.3f} | {new:.3f} | {(new/old-1)*100:+.1f}% | {float(a["complete_p50_ms"]):.3f} |')


if __name__ == '__main__':
    main()
