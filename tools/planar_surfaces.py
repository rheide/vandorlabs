"""Clip coincident convex faces without moving surfaces or changing their UVs."""
import math
from collections import defaultdict

EPS = 1e-8


def base_material(face):
    return face.get('base_material', face['material'].split('/')[-1])


def plane(face):
    n = face['normal']
    length = math.sqrt(sum(x*x for x in n))
    n = [x/length for x in n]
    if next(x for x in n if abs(x) > EPS) < 0:
        n = [-x for x in n]
    return tuple(round(x, 7) for x in n) + (round(sum(n[i]*face['v'][0][i] for i in range(3)), 7),)


def projected(face):
    axis = max(range(3), key=lambda i: abs(face['normal'][i]))
    return [i for i in range(3) if i != axis]


def area(vertices, axes):
    return sum(a[axes[0]]*b[axes[1]]-b[axes[0]]*a[axes[1]]
               for a, b in zip(vertices, vertices[1:]+vertices[:1]))/2


def split(poly, a, b, axes, sign):
    def distance(point):
        return sign*((b[axes[0]]-a[axes[0]])*(point[axes[1]]-a[axes[1]])
                     -(b[axes[1]]-a[axes[1]])*(point[axes[0]]-a[axes[0]]))
    inside, outside = [], []
    for current, following in zip(poly, poly[1:]+poly[:1]):
        d, e = distance(current), distance(following)
        (inside if d >= -EPS else outside).append(current)
        if (d > EPS and e < -EPS) or (d < -EPS and e > EPS):
            t = d/(d-e)
            point = [x+t*(y-x) for x, y in zip(current, following)]
            inside.append(point); outside.append(point)
    return inside, outside


def subtract(poly, cut, axes):
    sign = 1 if area(cut, axes) > 0 else -1
    remaining, pieces = poly, []
    for a, b in zip(cut, cut[1:]+cut[:1]):
        remaining, outside = split(remaining, a, b, axes, sign)
        if len(outside) >= 3 and abs(area(outside, axes)) > EPS:
            pieces.append(outside)
        if len(remaining) < 3 or abs(area(remaining, axes)) <= EPS:
            return [poly]
    return pieces


def competing(a, b):
    return (a.get('double') or b.get('double')
            or sum(x*y for x, y in zip(a['normal'], b['normal'])) > 0)


def clean(models):
    overlaps = 0
    for model in models:
        groups = defaultdict(list)
        for face in model['faces']:
            # Animated shell and mount faces must keep their own geometry.
            group = face.get('group', 'fixed') if isinstance(model.get('animation'), dict) else 'fixed'
            groups[(plane(face), group)].append(face)
        result = []
        for faces in groups.values():
            for index, face in enumerate(faces):
                axes = projected(face)
                pieces = [[v+uv for v, uv in zip(face['v'], face['uv'])]]
                for other in faces[index+1:]:
                    if not competing(face, other):
                        continue
                    # Metal owns shared glass planes regardless of authoring order.
                    if base_material(face) != 'glass' and base_material(other) == 'glass':
                        continue
                    next_pieces = [piece for old in pieces for piece in subtract(old, other['v'], axes)]
                    if abs(sum(abs(area(p, axes)) for p in pieces)-sum(abs(area(p, axes)) for p in next_pieces)) > EPS:
                        overlaps += 1
                    pieces = next_pieces
                if base_material(face) == 'glass':
                    for other in faces[:index]:
                        if base_material(other) != 'glass':
                            pieces = [piece for old in pieces for piece in subtract(old, other['v'], axes)]
                for piece in pieces:
                    result.append(dict(face, v=[p[:3] for p in piece], uv=[p[3:] for p in piece]))
        model['faces'] = result
    return overlaps


def overlap_count(models):
    count = 0
    for model in models:
        groups = defaultdict(list)
        for face in model['faces']:
            group = face.get('group', 'fixed') if isinstance(model.get('animation'), dict) else 'fixed'
            key = plane(face), group
            axes = projected(face)
            for other in groups[key]:
                if competing(face, other):
                    pieces = subtract(face['v'], other['v'], axes)
                    if abs(area(face['v'], axes))-sum(abs(area(p, axes)) for p in pieces) > EPS:
                        count += 1
            groups[key].append(face)
    return count
