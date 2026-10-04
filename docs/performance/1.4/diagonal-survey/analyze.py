#!/usr/bin/env python3
"""Conservative offline merge candidates; does not modify the production renderer."""
import argparse
import csv
import json
from pathlib import Path
import numpy as np

EPS = 1e-7


def area(points):
    p = np.asarray(points)[:, :3]
    return np.linalg.norm(sum((np.cross(p[i] - p[0], p[i + 1] - p[0])
                              for i in range(1, len(p) - 1)), np.zeros(3))) / 2


def merge(a, b, i, j):
    if a['sprite'] != b['sprite']:
        return None
    pa, pb = np.asarray(a['points']), np.asarray(b['points'])
    if len(pa) != 4 or len(pb) != 4:
        return None
    all_points = np.concatenate((pa, pb))
    if np.max(np.abs(all_points[:, 5:] - pa[0, 5:])) > EPS:
        return None
    normal = np.cross(pa[1, :3] - pa[0, :3], pa[2, :3] - pa[0, :3])
    length = np.linalg.norm(normal)
    if length < EPS or np.max(np.abs((all_points[:, :3] - pa[0, :3]) @ (normal / length))) > EPS:
        return None
    # A single affine texture mapping must fit every original corner. This rejects
    # UV clamps, seams and non-affine quads even when their positions are coplanar.
    matrix = np.column_stack((all_points[:, :3], np.ones(8)))
    uv = all_points[:, 3:5]
    fitted = matrix @ np.linalg.lstsq(matrix, uv, rcond=None)[0]
    if np.max(np.abs(fitted - uv)) > EPS:
        return None
    points = [pa[(i + 1 + k) % 4] for k in range(4)] + [pb[(j + 2 + k) % 4] for k in range(2)]
    changed = True
    while changed and len(points) > 4:
        changed = False
        for k in range(len(points)):
            before, at, after = points[k - 1][:3], points[k][:3], points[(k + 1) % len(points)][:3]
            if np.linalg.norm(np.cross(at - before, after - at)) < EPS and np.dot(at - before, after - at) >= 0:
                points.pop(k)
                changed = True
                break
    if len(points) != 4:
        return None
    for k in range(4):
        u = points[(k + 1) % 4][:3] - points[k][:3]
        v = points[(k + 2) % 4][:3] - points[(k + 1) % 4][:3]
        if np.dot(np.cross(u, v), normal) <= EPS:
            return None
    if abs(area(points) - area(pa) - area(pb)) > EPS:
        return None
    return {'sprite': a['sprite'], 'points': [p.tolist() for p in points]}


def simplified(quads):
    quads = list(quads)
    while True:
        edges = {}
        found = False
        for a, face in enumerate(quads):
            points = face['points']
            for i in range(4):
                p = tuple(round(x, 7) for x in points[i][:5])
                q = tuple(round(x, 7) for x in points[(i + 1) % 4][:5])
                if p == q:
                    continue
                for b, j in edges.get((q, p), []):
                    candidate = merge(face, quads[b], i, j)
                    if candidate is not None:
                        quads[b] = candidate
                        quads.pop(a)
                        found = True
                        break
                if found:
                    break
                edges.setdefault((p, q), []).append((a, i))
            if found:
                break
        if not found:
            return quads


def analyze(source, target):
    rows = []
    for line in source.read_text().splitlines():
        row = json.loads(line)
        quads = row.pop('quads')
        reduced = simplified(quads)
        points = np.asarray([p[:3] for q in quads for p in q['points']])
        low, high = points.min(axis=0), points.max(axis=0)
        # Number of local block positions whose complete measured mesh fits within
        # a section. Counts are geometric opportunities, not a routing proof.
        fits = [sum(c + low[a] >= -EPS and c + high[a] <= 16 + EPS for c in range(16)) for a in range(3)]
        row.update(quads=len(quads) * 2, candidate_quads=len(reduced) * 2,
                   current_section_positions=14**3, contained_section_positions=int(np.prod(fits)))
        for a, name in enumerate('xyz'):
            row['min_' + name] = float(low[a])
            row['max_' + name] = float(high[a])
        rows.append(row)
    with target.open('w') as stream:
        writer = csv.DictWriter(stream, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)
    before = sum(r['quads'] for r in rows)
    after = sum(r['candidate_quads'] for r in rows)
    print(f'{len(rows)} fixtures: {before} -> {after} two-sided quads ({1-after/before:.1%} fewer candidates)')
    for mode in range(3):
        group = [r for r in rows if r['mode'] == mode and r['neighbor'] == 0 and r['fill'] == 0]
        print('Isolated unfilled mode', mode, sorted({(r['quads'], r['candidate_quads'], r['contained_section_positions']) for r in group}))
    print('Contained section positions:', min(r['contained_section_positions'] for r in rows), max(r['contained_section_positions'] for r in rows), 'current', 14**3)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('target', type=Path)
    args = parser.parse_args()
    analyze(args.source, args.target)
