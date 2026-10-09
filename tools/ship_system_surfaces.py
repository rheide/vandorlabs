"""Remove hidden and overlapping rectangular surfaces while preserving authored UVs."""
from collections import defaultdict

EPS = 1e-8


def glass(face):
    return face['material'].split('/')[-1] == 'glass'


def bounds(face):
    low = [min(v[i] for v in face['v']) for i in range(3)]
    high = [max(v[i] for v in face['v']) for i in range(3)]
    axis = next(i for i in range(3) if low[i] == high[i])
    axes = [i for i in range(3) if i != axis]
    return axis, low[axis], (low[axes[0]], low[axes[1]], high[axes[0]], high[axes[1]])


def overlap(a, b):
    return min(a[2], b[2]) - max(a[0], b[0]) > EPS and min(a[3], b[3]) - max(a[1], b[1]) > EPS


def subtract(rect, cut):
    if not overlap(rect, cut):
        return [rect]
    x0, y0, x1, y1 = rect
    a, b, c, d = max(x0, cut[0]), max(y0, cut[1]), min(x1, cut[2]), min(y1, cut[3])
    candidates = [(x0, y0, a, y1), (c, y0, x1, y1), (a, y0, c, b), (a, d, c, y1)]
    return [r for r in candidates if r[2]-r[0] > EPS and r[3]-r[1] > EPS]


def fragment(face, rect, axis):
    axes = [i for i in range(3) if i != axis]
    low = [min(v[i] for v in face['v']) for i in range(3)]
    high = [max(v[i] for v in face['v']) for i in range(3)]
    origin = face['v'][0]
    u_axis = next(i for i in axes if face['v'][1][i] != origin[i])
    v_axis = next(i for i in axes if face['v'][3][i] != origin[i])
    vertices, uv = [], []
    for vertex in face['v']:
        point = list(vertex)
        for index, coordinate in enumerate(axes):
            point[coordinate] = rect[index] if vertex[coordinate] == low[coordinate] else rect[index+2]
        s = (point[u_axis]-origin[u_axis]) / (face['v'][1][u_axis]-origin[u_axis])
        t = (point[v_axis]-origin[v_axis]) / (face['v'][3][v_axis]-origin[v_axis])
        uv.append([face['uv'][0][i] + s*(face['uv'][1][i]-face['uv'][0][i])
                   + t*(face['uv'][3][i]-face['uv'][0][i]) for i in range(2)])
        vertices.append(point)
    return dict(face, v=vertices, uv=uv)


def clean_surfaces(model):
    faces = model['faces']
    solids = model['collision'][:sum(not glass(f) for f in faces)//6]
    planes = defaultdict(list)
    for index, face in enumerate(faces):
        axis, plane, rect = bounds(face)
        planes[axis, plane].append((index, face, rect))
    result, solid_index = [], 0
    for index, face in enumerate(faces):
        axis, plane, rect = bounds(face)
        axes = [i for i in range(3) if i != axis]
        sign = face['normal'][axis]
        transparent = glass(face)
        owner = None if transparent else solid_index//6
        if not transparent:
            solid_index += 1
        pieces = [rect]
        cuts = []
        # Faces inside a solid or between touching cuboids are never visible.
        for box_index, box in enumerate(solids):
            if box_index == owner:
                continue
            outside = plane + sign*1e-6
            covered = box[axis] < outside < box[axis+3]
            if transparent:
                covered = box[axis]-EPS <= plane <= box[axis+3]+EPS
            if covered:
                cuts.append((box[axes[0]], box[axes[1]], box[axes[0]+3], box[axes[1]+3]))
        # Later-authored solid details own a shared plane; glass cannot replace metal.
        for other_index, other, other_rect in planes[axis, plane]:
            if other_index <= index:
                continue
            if transparent and glass(other) or not transparent and not glass(other) and other['normal'][axis] == sign:
                cuts.append(other_rect)
        for cut in cuts:
            pieces = [piece for old in pieces for piece in subtract(old, cut)]
            if not pieces:
                break
        result.extend(fragment(face, piece, axis) for piece in pieces)
    model['faces'] = result
    return len(faces), len(result)


def coplanar_overlaps(model):
    planes = defaultdict(list)
    count = 0
    for face in model['faces']:
        axis, plane, rect = bounds(face)
        # Opposite solid faces are culled by the GPU; double-sided glass isn't.
        key = axis, plane
        for other, other_rect in planes[key]:
            if overlap(rect, other_rect) and (glass(face) or glass(other) or face['normal'][axis] == other['normal'][axis]):
                count += 1
        planes[key].append((face, rect))
    return count
