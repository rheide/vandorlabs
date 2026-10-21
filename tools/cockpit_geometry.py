#!/usr/bin/env python3
"""Author and export the Vandor Labs cockpit kit. Python 3 + NumPy + Pillow.

All source dimensions are Minecraft model pixels (16 = one block).
OBJ positions and runtime rig pivots are BLOCK units. +Z is the pilot's rear.
No artwork is baked onto any display: every UI surface is pure black.
"""
from __future__ import annotations

import argparse
import base64
import copy
import io
import itertools
import json
import math
from pathlib import Path
import uuid

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / 'build' / 'cockpit-generated'
STATES = ['off', 'low', 'medium', 'high']
MAT = {
    'housing': [53, 61, 73],
    'housing_dark': [35, 41, 50],
    'frame': [65, 74, 88],
    'edge': [79, 89, 105],
    'cushion': [45, 56, 73],
    'cushion_edge': [33, 42, 55],
    'mechanism': [28, 33, 40],
    'slot': [10, 12, 16],
    'amber': [225, 162, 52],
    'amber_dark': [163, 109, 34],
    'blank': [0, 0, 0],
    'sidewall': [53, 61, 73],
    'screen_back': [35, 41, 50],
    'horizontal': [53, 61, 73],
    'red_cushion': [158, 38, 42],
    'red_cushion_edge': [112, 26, 31],
}


def write_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


def vec(v):
    return np.asarray(v, dtype=float)


def rotation(angles):
    x, y, z = np.radians(angles)
    rx = np.array([[1, 0, 0], [0, math.cos(x), -math.sin(x)], [0, math.sin(x), math.cos(x)]])
    ry = np.array([[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]])
    rz = np.array([[math.cos(z), -math.sin(z), 0], [math.sin(z), math.cos(z), 0], [0, 0, 1]])
    return rz @ ry @ rx


def matrix_euler(matrix):
    """XYZ Euler angles for the existing Rz * Ry * Rx source convention."""
    y = math.asin(float(np.clip(-matrix[2, 0], -1, 1)))
    return np.degrees([math.atan2(matrix[2, 1], matrix[2, 2]), y,
                       math.atan2(matrix[1, 0], matrix[0, 0])]).tolist()


def outward_faces(vertices, faces):
    """Orient the faces of an authored convex solid away from its centroid."""
    vertices = vec(vertices)
    center = vertices.mean(0)
    result = []
    for indices in faces:
        points = vertices[indices]
        normal = np.cross(points[1]-points[0], points[2]-points[0])
        if np.dot(normal, points.mean(0)-center) < 0:
            indices = list(reversed(indices))
        result.append(list(indices))
    return result


class Model:
    def __init__(self, identifier, title, description):
        self.id, self.title, self.description = identifier, title, description
        self.nodes = []
        self.surfaces = []
        self.design_contract = {}

    def node(self, name, origin=(0, 0, 0), angles=(0, 0, 0), animation=None, category='structure'):
        n = {'name': name, 'origin': list(origin), 'rotation': list(angles), 'category': category, 'boxes': []}
        if animation:
            n['animation'] = animation
        self.nodes.append(n)
        return n

    def box(self, node, name, lo, hi, material='housing'):
        assert material in MAT
        assert all(a < b for a, b in zip(lo, hi)), (name, lo, hi)
        node['boxes'].append({'name': name, 'from': list(lo), 'to': list(hi), 'material': material})

    def mesh(self, node, name, vertices, faces, material='housing'):
        assert material in MAT
        node.setdefault('meshes', []).append({'name': name, 'vertices': vec(vertices).tolist(),
            'faces': outward_faces(vertices, faces), 'material': material})

    def prism(self, node, name, polygon, bottom, top, material='housing'):
        """Convex XZ footprint, with horizontal or per-vertex top/bottom heights."""
        count = len(polygon)
        low = [bottom]*count if np.isscalar(bottom) else bottom
        high = [top]*count if np.isscalar(top) else top
        vertices = [[x, y, z] for levels in (low, high)
                    for (x, z), y in zip(polygon, levels)]
        faces = [list(range(count)), list(range(count, count*2))]
        faces.extend([[i, (i+1)%count, (i+1)%count+count, i+count] for i in range(count)])
        self.mesh(node, name, vertices, faces, material)

    def touch(self, node, name, xmin, xmax, zmin, zmax, y, rim=.3):
        # Every panel backing AND active face is a rectangle. No key graphics.
        self.box(node, name + '_rim', [xmin-rim, y-.08, zmin-rim], [xmax+rim, y+.09, zmax+rim], 'mechanism')
        self.box(node, name, [xmin, y+.095, zmin], [xmax, y+.115, zmax], 'blank')
        self.surfaces.append({'id': name, 'role': 'touch', 'node': node['name'],
                              'origin': [xmin, y+.115, zmax],
                              'u': [xmax-xmin, 0, 0], 'v': [0, 0, zmin-zmax],
                              'normal': [0, 1, 0], 'render_offset': .025})

    def screen(self, name, origin, width, height, yaw=0, pitch=-22.5):
        n = self.node(name, origin, (pitch, yaw, 0), category='screen')
        # One simple housing and one inset face; no tiny frame geometry.
        self.box(n, name+'_housing', [-width/2, 0, -.7], [width/2, height, .7], 'frame')
        self.box(n, name+'_rear', [-width/2+.45, .45, -.95], [width/2-.45, height-.45, -.7], 'housing_dark')
        margin = 1.0
        self.box(n, name+'_black', [-width/2+margin, margin, .705], [width/2-margin, height-margin, .725], 'blank')
        self.surfaces.append({'id': name, 'role': 'screen', 'node': name,
                              'origin': [-width/2+margin, margin, .725],
                              'u': [width-2*margin, 0, 0], 'v': [0, height-2*margin, 0],
                              'normal': [0, 0, 1], 'render_offset': .025})
        return n


def add_chair(m):
    n = m.node('chair', category='chair')
    for name, lo, hi, mat in [
        ('chair_base', [3, 0, 3], [13, 1.5, 13.5], 'housing_dark'),
        ('chair_pedestal', [5, 1.5, 5], [11, 5.8, 11.5], 'mechanism'),
        ('chair_seat_pan', [2, 5.8, .75], [14, 7.4, 13.6], 'frame'),
        ('chair_seat_pad', [2.8, 7.4, 1.25], [13.2, 8.6, 12.4], 'cushion'),
        ('chair_front_band', [2.8, 6.4, .7], [13.2, 7.25, .9], 'cushion_edge'),
        ('chair_back_shell', [2, 7.4, 12], [14, 22, 14.5], 'frame'),
        ('chair_back_padding', [3, 11.0, 11.68], [13, 20.9, 12.01], 'cushion'),
        ('chair_lumbar', [3, 8.5, 11.2], [13, 11.0, 11.7], 'cushion_edge'),
        ('chair_back_rear', [3, 8.3, 14.5], [13, 20.9, 14.7], 'housing_dark'),
    ]:
        m.box(n, name, lo, hi, mat)
    return n


def add_yoke(m, center=(8, -3), pedestal_height=8.5, scale=1.0):
    x, z = center
    n = m.node('control_yoke', category='controller')
    h = pedestal_height
    for name, lo, hi, material in [
        ('yoke_pedestal', [x-2, 0, z-2], [x+2, h, z+2], 'housing_dark'),
        ('yoke_base', [x-2.5, h, z-2.5], [x+2.5, h+1, z+2.5], 'frame'),
        ('yoke_socket', [x-1.5, h+1, z-1.5], [x+1.5, h+1.35, z+1.5], 'mechanism'),
        ('yoke_stalk', [x-.7, h+1.35, z-.65], [x+.7, h+4.5*scale, z+.65], 'housing_dark'),
        ('yoke_grip', [x-1.15, h+3.75*scale, z-1.1], [x+1.15, h+5.1*scale, z+1.1], 'housing'),
        ('yoke_amber_cap', [x-.95, h+5.1*scale, z-.95], [x+.95, h+5.5*scale, z+.95], 'amber'),
    ]:
        m.box(n, name, lo, hi, material)
    return n


def add_lever(m, name, x, pivot_z, surface_y=10.7):
    n = m.node(name, (x, surface_y, pivot_z), animation={
        'type': 'rotation', 'axis': [1, 0, 0], 'values': [22.5, 0, -22.5, -45], 'units': 'degrees'
    }, category='moving_control')
    m.box(n, name+'_shaft', [-.55, -.4, -.5], [.55, 3.8, .5], 'mechanism')
    m.box(n, name+'_grip', [-1.1, 3.5, -1.05], [1.1, 4.7, 1.05], 'housing')
    m.box(n, name+'_amber', [-1.08, 4.7, -1.02], [1.08, 5.2, 1.02], 'amber')
    return n


def add_continuous_fascia(m, large):
    """Three rectangular faces on shared, mitered edges; one tilt for the bank.

    The upright profile is joined first, then the WHOLE bank is tilted. Applying
    independent pitch after each panel's yaw would pull their corner edges apart.
    """
    front_z = -11.5 if large else -11.75
    chain = ([(2.5, -4), (10, front_z), (22, front_z), (29.5, -4)] if large else
             [(2.25, -3.75), (9.5, front_z), (22.5, front_z), (29.75, -3.75)])
    pitch, height, depth = (-4 if large else -4), 8, .8
    pivot = vec([16, 13, front_z])
    bank_rotation = rotation([pitch, 0, 0])
    directions = [vec([b[0]-a[0], b[1]-a[1]]) for a,b in zip(chain, chain[1:])]
    directions = [d/np.linalg.norm(d) for d in directions]
    normals = [vec([-d[1], d[0]]) for d in directions]
    # Intersections of adjacent back planes produce exact miter joints.
    backs = [vec(chain[0])-normals[0]*depth]
    for index in (1, 2):
        basis = np.asarray([normals[index-1], normals[index]])
        constant = basis @ vec(chain[index]) - depth
        backs.append(np.linalg.solve(basis, constant))
    backs.append(vec(chain[-1])-normals[-1]*depth)
    frame = m.node('fascia_frame', pivot, [pitch, 0, 0])
    apron = m.node('fascia_solid_apron')
    facets = []
    for index, label in enumerate(('left', 'center', 'right')):
        first, last = vec(chain[index]), vec(chain[index+1])
        width = float(np.linalg.norm(last-first))
        footprint = [first, last, backs[index+1], backs[index]]
        local_plan = [(float(q[0]-pivot[0]), float(q[1]-pivot[2])) for q in footprint]
        m.prism(frame, label+'_joined_frame', local_plan, 0, height, 'frame')
        base = np.asarray([[q[0], 0, q[1]] for q in local_plan]) @ bank_rotation.T + pivot
        m.prism(apron, label+'_solid_screen_riser', [(q[0], q[2]) for q in base],
                10.5, base[:,1].tolist(), 'housing')
        midpoint = (first+last)/2
        local_origin = vec([midpoint[0], 13, midpoint[1]]) - pivot
        origin = bank_rotation @ local_origin + pivot
        yaw = math.degrees(math.atan2(-directions[index][1], directions[index][0]))
        angles = matrix_euler(bank_rotation @ rotation([0, yaw, 0]))
        name = 'screen_'+label
        n = m.node(name, origin, angles, category='screen')
        margin_x, margin_y = .22, .30
        m.box(n, name+'_black', [-width/2+margin_x, margin_y, .015],
              [width/2-margin_x, height-margin_y, .035], 'blank')
        m.surfaces.append({'id': name, 'role': 'screen', 'node': name,
            'origin': [-width/2+margin_x, margin_y, .035],
            'u': [width-2*margin_x, 0, 0], 'v': [0, height-2*margin_y, 0],
            'normal': [0, 0, 1], 'render_offset': .025})
        front_corners = np.asarray([[first[0],13,first[1]], [last[0],13,last[1]],
                                   [last[0],13+height,last[1]], [first[0],13+height,first[1]]])
        front_corners = (front_corners-pivot) @ bank_rotation.T + pivot
        facets.append({'screen': name, 'frame_mesh': label+'_joined_frame',
                       'front_corners_model_pixels': front_corners.round(9).tolist(),
                       'black_inset_model_pixels': [margin_x, margin_y]})
        if label != 'center':
            # A flush rectangular touch inset in the solid common ledge.
            touch_node = m.node('touch_ledge_'+label, [midpoint[0]+(-1.25 if label=='left' else 1.25), 0, midpoint[1]], [0, yaw, 0])
            m.touch(touch_node, 'touch_'+label+'_forward', -width/2+.8, width/2-.8,
                    1.0, 1.0+(width-1.6)/2, 10.5, rim=.18)
    # A shallow angled half-console input deck, with a solid wedge into the ledge.
    n = m.node('center_input', [16,11.5,-8.2], [22.5,0,0])
    m.touch(n, 'touch_center_keyboard', -4, 4, -2, 2, 0, rim=.18)
    deck = np.asarray([[-4.18,-.08,-2.18],[4.18,-.08,-2.18],[4.18,-.08,2.18],[-4.18,-.08,2.18]]) @ rotation([22.5,0,0]).T + vec(n['origin'])
    support=m.node('center_input_base')
    m.prism(support,'center_keyboard_wedge',[(p[0],p[2]) for p in deck],10.5,deck[:,1].tolist(),'housing')
    m.design_contract['screen_band'] = {
        'construction': 'Three mitered facets with exactly shared frame edges, tilted as one bank',
        'facet_order': ['screen_left', 'screen_center', 'screen_right'],
        'bank_pitch_degrees': pitch, 'local_height_model_pixels': height,
        'frame_depth_model_pixels': depth, 'facets': facets,
        'nominal_vertical_seam_border_model_pixels': margin_x*2,
    }


def cockpit(identifier, large):
    m = Model(identifier, 'Cockpit',
              'Complete two-by-two cockpit: one solid U-shaped console wall, a joined three-facet blank screen bank, integrated chair, rectangular touch insets, center yoke and animated throttles.')
    plinth = m.node('assembly_floor')
    m.box(plinth, 'shared_floor_plinth', [.25, 0, -15.5], [31.75, .65, 14.7], 'housing_dark')
    chair = add_chair(m)
    chair['origin'][0] = 8
    for box in chair['boxes']:
        if box['name'] == 'chair_back_shell':
            box['to'][1] = 21.2
        elif box['name'] == 'chair_back_padding':
            box['to'][1] = 20.7
    add_yoke(m, center=(16, -4.5), pedestal_height=7.0)

    # Five convex sections are a flush-connected solid U wall. Their interfaces
    # are shared faces, with no feet, open undersides, independent shelves or gaps.
    shell = m.node('continuous_console_wall')
    oz = -5.25 if large else -4.65
    fz = -14.9 if large else -15.15
    ox = 9 if large else 8.75
    iz = -1.2 if large else -.9
    ix = 11.4 if large else 11.1
    inner_front = -6.2
    sections = {
        'left_return': [(.25,1.5),(.25,oz),(7,iz),(7,1.5)],
        'left_diagonal_wall': [(.25,oz),(ox,fz),(ix,inner_front),(7,iz)],
        'front_wall': [(ox,fz),(32-ox,fz),(32-ix,inner_front),(ix,inner_front)],
        'right_diagonal_wall': [(32-ox,fz),(31.75,oz),(25,iz),(32-ix,inner_front)],
        'right_return': [(31.75,oz),(31.75,1.5),(25,1.5),(25,iz)],
    }
    for name, polygon in sections.items():
        m.prism(shell, name, polygon, .65, 10.5, 'housing')
    for side in ('left', 'right'):
        left = side == 'left'
        xmin, xmax = (.25, 7) if left else (25, 31.75)
        n = m.node('armrest_'+side)
        m.box(n, side+'_solid_wall', [xmin,.65,1.5], [xmax,8.9,14.5], 'housing')
        m.box(n, side+'_armrest_top', [xmin,8.9,1.5], [xmax,10.5,14.5], 'housing')
        if left:
            lever_x = [4.15] if large else [2.15, 5.25]
            for index, x in enumerate(lever_x):
                name = 'throttle_left' if index == 0 else 'throttle_right'
                m.box(n, name+'_slot_rim', [x-.9,10.5,.5], [x+.9,10.65,7.7], 'frame')
                m.box(n, name+'_slot', [x-.68,10.65,.7], [x+.68,10.675,7.5], 'slot')
                add_lever(m, name, x, 4.2)
        else:
            xmax_panel = xmax-(1.05 if large else .85)
            panel_depth = xmax_panel-xmin-.75
            m.touch(n, 'touch_right_keyboard', xmin+.75, xmax_panel, 3.0, 3.0+panel_depth*2, 10.5)
            panel=m.surfaces[-1]
            panel['origin']=[xmin+.75,10.615,3.0]
            panel['u']=[0,0,panel_depth*2];panel['v']=[panel_depth,0,0]
    add_continuous_fascia(m, large)
    # One rigid moving bank avoids a separate model and draw for every display.
    bank=m.node('screen_bank',animation={'type':'translation','axis':[0,1,0],'values':[0,-4,-8,-12]},category='moving_screen')
    for node in list(m.nodes):
        if node['name']=='fascia_frame' or node['name'] in ('screen_left','screen_center','screen_right'):
            matrix,offset=node_transform(node,neutral=True)
            for primitive in node['boxes']+node.get('meshes',[]):
                vertices=corners(primitive) @ matrix.T + offset
                faces=primitive['faces'] if 'vertices' in primitive else [indices for _,indices,_ in FACES]
                m.mesh(bank,node['name']+'_'+primitive['name'],vertices,faces,primitive['material'])
            node['boxes']=[];node['meshes']=[]
    for name,x,z in [('screen_lift_button',27.5,2.05),('seat_slide_button',3.95,10.35)]:
        support=m.node(name+'_rim')
        m.box(support,name+'_bezel',[x-1.15,10.5,z-.75],[x+1.15,10.65,z+.75],'mechanism')
        button=m.node(name,[x,10.7,z],animation={'type':'translation','axis':[0,-1,0],'values':[0,.15,.3,.45]},category='mechanism_button')
        m.box(button,name+'_cap',[-.9,0,-.55],[.9,.30,.55],'amber')
    seat_assembly=m.node('seat_assembly',animation={'type':'translation','axis':[0,0,1],'values':[0,16/3,32/3,16]},category='moving_seat')
    for node in list(m.nodes):
        if node['name'] in ('chair','armrest_left','armrest_right','screen_lift_button_rim','seat_slide_button_rim'):
            matrix,offset=node_transform(node,neutral=True)
            for primitive in node['boxes']+node.get('meshes',[]):
                vertices=corners(primitive) @ matrix.T + offset
                faces=primitive['faces'] if 'vertices' in primitive else [indices for _,indices,_ in FACES]
                m.mesh(seat_assembly,node['name']+'_'+primitive['name'],vertices,faces,primitive['material'])
            node['boxes']=[];node['meshes']=[]
    m.design_contract['console_shell'] = {
        'construction': 'Solid floor-to-deck U wall with shared section faces and one common floor plinth',
        'deck_height_model_pixels': 10.5,
        'inside_side_edges_model_pixels': [7,25],
        'rear_wall_extent_model_pixels': 14.5,
        'rear_chair_extent_model_pixels': 14.7,
        'rear_access': 'Open above the common floor; no back wall or rear extension',
        'forward_sections': list(sections),
        'forbidden_parts': ['independent screen posts', 'table legs', 'disconnected touch shelves'],
    }
    return m


FACES = [
    ('north', [0, 3, 2, 1], [0, 0, -1]),
    ('south', [4, 5, 6, 7], [0, 0, 1]),
    ('west', [0, 4, 7, 3], [-1, 0, 0]),
    ('east', [1, 2, 6, 5], [1, 0, 0]),
    ('down', [0, 1, 5, 4], [0, -1, 0]),
    ('up', [3, 7, 6, 2], [0, 1, 0]),
]


def corners(box):
    if 'vertices' in box:
        return vec(box['vertices'])
    a, b = box['from'], box['to']
    return np.asarray([[a[0], a[1], a[2]], [b[0], a[1], a[2]], [b[0], b[1], a[2]], [a[0], b[1], a[2]],
                       [a[0], a[1], b[2]], [b[0], a[1], b[2]], [b[0], b[1], b[2]], [a[0], b[1], b[2]]], dtype=float)


def node_transform(node, detent=0, neutral=False):
    matrix = rotation(node['rotation'])
    offset = vec(node['origin'])
    anim = node.get('animation')
    if anim and not neutral:
        i = min(int(detent), 2)
        f = max(0., min(3., float(detent))) - i
        value = anim['values'][i] * (1-f) + anim['values'][i+1] * f
        if anim['type'] == 'rotation':
            matrix = matrix @ rotation(vec(anim['axis']) * value)
        else:
            offset = offset + matrix @ (vec(anim['axis']) * value)
    return matrix, offset


def primitives_world(m, detent=0, static_only=False, moving_only=None):
    for n in m.nodes:
        if static_only and 'animation' in n:
            continue
        if moving_only is not None and n['name'] != moving_only:
            continue
        mat, offset = node_transform(n, detent, neutral=moving_only is not None)
        if moving_only is not None:
            mat, offset = np.eye(3), np.zeros(3)
        for box in n['boxes'] + n.get('meshes', []):
            yield n, box, corners(box) @ mat.T + offset, mat


def export_obj(m, path, detent=0, static_only=False, moving_only=None):
    path.parent.mkdir(parents=True, exist_ok=True)
    out = ['# Vandor Labs cockpit kit | units: BLOCKS | +Y up | pilot faces -Z',
           '# Named objects preserve semantic parts. All screen/touch faces are pure black.',
           'mtllib materials.mtl', 's off']
    v_count = vt_count = vn_count = 0
    for node, box, verts, matrix in primitives_world(m, detent, static_only, moving_only):
        out += ['', f"g {node['name']}__{box['name']}", 'usemtl '+box['material']]
        for p in verts / 16:
            out.append('v '+' '.join(f'{x:.8f}' for x in p))
        # Consistent full material-square mapping. Surface replacement uses separate UV metadata.
        for u, v in [(0, 0), (1, 0), (1, 1), (0, 1)]:
            out.append(f'vt {u:.8f} {v:.8f}')
        faces = box['faces'] if 'vertices' in box else [indices for _, indices, _ in FACES]
        for indices in faces:
            norm = np.cross(verts[indices[1]]-verts[indices[0]], verts[indices[2]]-verts[indices[0]])
            norm /= np.linalg.norm(norm)
            material=box['material']
            if (node['name']=='fascia_frame' or node['name'].startswith('screen_')) and norm[2]<-.2 and material!='blank':material='screen_back'
            if norm[1]>.7 and material in ('housing','housing_dark','frame','sidewall','screen_back'):
                material='horizontal'
            out.append('usemtl '+material)
            out.append('vn '+' '.join(f'{x:.8f}' for x in norm))
            vn_count += 1
            out.append('f '+' '.join(f'{v_count+i+1}/{vt_count+j+1}/{vn_count}' for j, i in enumerate(indices)))
        v_count += len(verts)
        vt_count += 4
    path.write_text('\n'.join(out)+'\n', encoding='utf-8')


def texture_files():
    folder = ROOT/'assets/vandorlabs/textures/models/cockpits'
    folder.mkdir(parents=True, exist_ok=True)
    for name, rgb in MAT.items():
        # Uniform tiles deliberately preserve the approved low-complexity art direction.
        # Colors live in textures, not duplicate MTL multipliers.
        Image.new('RGBA', (16, 16), tuple(rgb)+(255,)).save(folder/(name+'.png'))


def material_file(path, forge=False):
    lines = ['# Simple opaque nearest-neighbor materials. No UI imagery.', '# Texture colors are authoritative; Kd is neutral.']
    for name in MAT:
        texture = f'vandorlabs:models/cockpits/{name}' if forge else f'../../assets/vandorlabs/textures/models/cockpits/{name}.png'
        lines += ['', 'newmtl '+name, 'Ka 1.0 1.0 1.0', 'Kd 1.0 1.0 1.0', 'Ks 0.0 0.0 0.0', 'Ns 0', 'd 1.0', 'illum 1', 'map_Kd '+texture]
    path.write_text('\n'.join(lines)+'\n', encoding='utf-8')


def world_surfaces(m):
    lookup = {n['name']: n for n in m.nodes}
    result = []
    for surface in m.surfaces:
        matrix, offset = node_transform(lookup[surface['node']])
        origin = matrix @ vec(surface['origin']) + offset
        u, v = matrix @ vec(surface['u']), matrix @ vec(surface['v'])
        normal = matrix @ vec(surface['normal'])
        result.append({
            'id': surface['id'], 'role': surface['role'], 'node': surface['node'],
            'origin_blocks': (origin/16).round(8).tolist(),
            'u_vector_blocks': (u/16).round(8).tolist(),
            'v_vector_blocks': (v/16).round(8).tolist(),
            'normal': normal.round(8).tolist(),
            'corners_blocks': [(p/16).round(8).tolist() for p in [origin, origin+u, origin+u+v, origin+v]],
            'size_model_pixels': [round(float(np.linalg.norm(u)), 6), round(float(np.linalg.norm(v)), 6)],
            'overlay_offset_blocks': surface['render_offset']/16,
            'uv_convention': 'origin is lower-left; u runs right, v runs up/away across the face',
            'material': 'blank', 'base_rgb': [0, 0, 0], 'rectangular': True,
        })
    return result


def model_metadata(m):
    # Include all four poses AND interior values for swept bounds.
    verts = np.concatenate([v for pose in np.linspace(0, 3, 49) for _, _, v, _ in primitives_world(m, pose)])
    lo, hi = verts.min(0), verts.max(0)
    all_cells = set()
    collision_boxes = []
    collision_solids = []
    for node, box, vertices, _ in primitives_world(m, 0):
        bmin, bmax = vertices.min(0)/16, vertices.max(0)/16
        if node['category'] == 'moving_control' or box['material'] == 'blank':
            continue
        # This intentionally reserves every cell touched by the rotated part's
        # conservative world AABB, safe for multiblock placement.
        cmin, cmax = np.floor(bmin+1e-6).astype(int), np.floor(bmax-1e-6).astype(int)
        for cell in itertools.product(*(range(a, b+1) for a, b in zip(cmin, cmax))):
            all_cells.add(tuple(int(x) for x in cell))
        if box['material'] != 'blank':
            collision_boxes.append({'part': node['name'], 'box': box['name'],
                                    'min': bmin.round(6).tolist(), 'max': bmax.round(6).tolist()})
            if 'vertices' in box:
                collision_solids.append({'part':node['name'], 'mesh':box['name'],
                    'vertices_blocks':(vertices/16).round(8).tolist(), 'faces':box['faces']})
    metadata = {
        'bounds_model_pixels': [*lo.round(6), *hi.round(6)],
        'bounds_blocks': [*(lo/16).round(8), *(hi/16).round(8)],
        'width_depth_height_blocks': [round((hi[0]-lo[0])/16, 4), round((hi[2]-lo[2])/16, 4), round((hi[1]-lo[1])/16, 4)],
        'anchor': [0, 0, 0], 'facing': 'north', 'rotation_pivot_blocks': [.5, 0, .5],
        'reserved_cells': [list(c) for c in sorted(all_cells)],
        'reservation_method': 'union of conservative per-part AABB cells; render only once at the anchor',
        'collision_boxes': collision_boxes,
        'collision_convex_solids': collision_solids,
        'collision_note': 'Per-part AABBs are broad-phase bounds. The angled wall AABBs overestimate the footwell; use collision_convex_solids to derive tighter cell-local collision shapes. Do not make the full multiblock one solid collision box.',
        'design_contract': copy.deepcopy(m.design_contract),
    }
    if m.id in ('cockpit',):
        # Derived from the source-verified vanilla 1.12.2 riding reference.
        # The entity origin and cushion/hip points are intentionally distinct.
        hip = vec([16, 12.04, 9.3])/16
        entity = hip - vec([0, .9375*(1.501-12/16), 0])
        eye = entity + vec([0, 1.62, 0])
        metadata.update({
            'footprint_blocks': {'width': 2, 'depth': 2},
            'anchor_role': 'rear-left lower block for NORTH-facing placement; chair centered between the rear two blocks',
            'seat_position_blocks': [1, 8.6/16, 9.3/16],
            'seat_position_role': 'cushion surface reference, not the rider entity origin',
            'rider_hip_blocks': hip.round(8).tolist(),
            'rider_entity_origin_blocks': entity.round(8).tolist(),
            'rider_eye_blocks': eye.round(8).tolist(),
            'rider_reference': 'Vanilla Minecraft 1.12.2 classic player, riding, full outer skin, ordinary eye height; see validation/player_reference.json',
            'rider_position_note': 'Place the passenger entity at rider_entity_origin_blocks after facing rotation. Account for the mount and passenger Y offsets. Do not use the hip or cushion point as entity Y.',
            'clear_armrest_width_model_pixels': 18,
        })
    return metadata


def export_bbmodel(m):
    """Editable generic Blockbench source, with named groups and actual animation tracks."""
    uid = lambda s: str(uuid.uuid5(uuid.NAMESPACE_URL, 'vandorlabs-cockpit-v1/'+m.id+'/'+s))
    textures = []
    for i, name in enumerate(MAT):
        data = (ROOT/'assets/vandorlabs/textures/models/cockpits'/f'{name}.png').read_bytes()
        textures.append({'path': '', 'name': name+'.png', 'folder': 'models/cockpits', 'namespace': 'vandorlabs',
                         'id': str(i), 'particle': name == 'housing', 'render_mode': 'default', 'render_sides': 'auto',
                         'uuid': uid('texture/'+name), 'width': 16, 'height': 16, 'uv_width': 16, 'uv_height': 16,
                         'source': 'data:image/png;base64,'+base64.b64encode(data).decode()})
    elements, outliner = [], []
    material_indices = {name:i for i, name in enumerate(MAT)}
    for n in m.nodes:
        children = []
        for box in n['boxes']:
            element_id = uid(n['name']+'/'+box['name'])
            children.append(element_id)
            elements.append({'name': box['name'], 'type': 'cube', 'uuid': element_id,
                             'from': (vec(box['from'])+vec(n['origin'])).tolist(),
                             'to': (vec(box['to'])+vec(n['origin'])).tolist(),
                             'origin': n['origin'], 'rotation': [0,0,0], 'box_uv': False,
                             'faces': {face: {'uv': [0,0,16,16], 'texture': material_indices[box['material']]} for face,_,_ in FACES}})
        for mesh in n.get('meshes', []):
            element_id = uid(n['name']+'/'+mesh['name'])
            children.append(element_id)
            vertex_names = [f'v{i}' for i in range(len(mesh['vertices']))]
            # Blockbench mesh vertices are local to the element origin. Cubes
            # above use absolute from/to coordinates and a different convention.
            vertices = {key:vec(point).tolist()
                        for key,point in zip(vertex_names,mesh['vertices'])}
            faces = {}
            for index, indices in enumerate(mesh['faces']):
                keys = [vertex_names[i] for i in indices]
                uv = [[0,0],[16,0],[16,16],[0,16]]
                faces[f'f{index}'] = {'vertices':keys, 'uv':dict(zip(keys,uv)),
                                      'texture':material_indices[mesh['material']]}
            elements.append({'name':mesh['name'], 'type':'mesh', 'uuid':element_id,
                             'origin':n['origin'], 'rotation':[0,0,0],
                             'vertices':vertices, 'faces':faces})
        # Static generic-group transform is the same XYZ Euler transform as the exporter.
        # Animated groups have their neutral pose; selecting a named detent animation applies it.
        outliner.append({'name': n['name'], 'uuid': uid('node/'+n['name']), 'origin': n['origin'],
                         'rotation': n['rotation'], 'export': True, 'visibility': True, 'children': children})
    animations = []
    moving = [n for n in m.nodes if 'animation' in n]
    for pose, state in enumerate(STATES if moving else []):
        animators = {}
        for n in moving:
            animation = n['animation']
            value = vec(animation['axis'])*animation['values'][pose]
            channel = 'rotation' if animation['type'] == 'rotation' else 'position'
            animators[uid('node/'+n['name'])] = {'name': n['name'], 'type': 'bone', 'keyframes': [
                {'channel': channel, 'data_points': [{'x': str(value[0]), 'y': str(value[1]), 'z': str(value[2])}],
                 'uuid': uid('key/'+state+'/'+n['name']), 'time': 0, 'interpolation': 'linear'}]}
        animations.append({'name': state, 'uuid': uid('animation/'+state), 'loop': 'hold', 'length': 1,
                           'override': False, 'snapping': 20, 'animators': animators})
    if moving:
        animators = {}
        timing = [(0,0),(.6,0),(1.0,1),(1.6,1),(2.0,2),(2.6,2),(3.0,3),(3.8,3),(4.2,2),(4.8,2),(5.2,1),(5.8,1),(6.2,0),(6.8,0)]
        for n in moving:
            a = n['animation']; keyframes = []
            for time, detent in timing:
                values = vec(a['axis'])*a['values'][detent]
                keyframes.append({'channel': 'rotation' if a['type']=='rotation' else 'position',
                                  'data_points':[dict(zip(['x','y','z'], map(str, values)))],
                                  'uuid':uid(f'cycle/{n["name"]}/{time}'), 'time':time, 'interpolation':'linear'})
            animators[uid('node/'+n['name'])] = {'name': n['name'], 'type':'bone', 'keyframes':keyframes}
        animations.append({'name':'detent_cycle','uuid':uid('animation/cycle'),'loop':'loop','length':6.8,'override':False,'snapping':20,'animators':animators})
    write_json(ROOT/'blockbench'/f'{m.id}.bbmodel', {
        'meta': {'format_version':'4.10', 'model_format':'free', 'box_uv':False}, 'name':m.title,
        'model_identifier':m.id, 'visible_box':[4,4,2], 'resolution':{'width':16,'height':16},
        'elements':elements,'outliner':outliner,'textures':textures,'animations':animations,
    })


def export_model(m):
    metadata = model_metadata(m)
    source = {'schema':'vandorlabs.cockpit.scene.v1', 'id':m.id, 'title':m.title, 'description':m.description,
              'units_per_block':16, 'axis':{'x':'pilot right','y':'up','z':'pilot rear; forward is negative Z'},
              'node_transform':'T(origin) * Rz * Ry * Rx; boxes and convex meshes are node-local; animation applied after static rotation',
              'materials':{k:{'rgb':v,'texture':f'vandorlabs:models/cockpits/{k}'} for k,v in MAT.items()},
              'nodes':m.nodes, 'surfaces':m.surfaces, 'metadata':metadata}
    write_json(ROOT/'source'/f'{m.id}.scene.json', source)
    resource = f'vandorlabs:block/cockpits/{m.id}/'
    rig = {'schema_version':1, 'id':m.id, 'units_per_block':16, 'static_model':resource+'static.obj',
           'poses':{s:resource+s+'.obj' for s in STATES if s=='off' or any('animation' in n for n in m.nodes)},
           'render_bounds_blocks':metadata['bounds_blocks'], 'parts':[]}
    for n in m.nodes:
        if 'animation' in n:
            a = n['animation']
            rig['parts'].append({'id':n['name'], 'model':resource+n['name']+'.obj',
                                 'pivot':(vec(n['origin'])/16).tolist(), 'type':a['type'], 'axis':a['axis'],
                                 'values':a['values'] if a['type']=='rotation' else (vec(a['values'])/16).tolist(), 'interactive':n['category']=='moving_control'})
    runtime = ROOT/'assets/vandorlabs/cockpits'/m.id
    write_json(runtime/'rig.json', rig)
    write_json(runtime/'surfaces.json', {'schema_version':1, 'model':m.id, 'units':'blocks', 'surfaces':world_surfaces(m)})
    write_json(runtime/'assembly.json', metadata)
    portable = ROOT/'obj'/m.id
    native = ROOT/'assets/vandorlabs/models/block/cockpits'/m.id
    for folder, forge in [(portable, False),(native,True)]:
        folder.mkdir(parents=True,exist_ok=True)
        material_file(folder/'materials.mtl',forge)
        export_obj(m,folder/'static.obj',static_only=True)
        for state in rig['poses']:
            export_obj(m,folder/(state+'.obj'),detent=STATES.index(state))
        for n in m.nodes:
            if 'animation' in n:
                export_obj(m,folder/(n['name']+'.obj'),moving_only=n['name'])
    export_bbmodel(m)
    return {'id':m.id,'title':m.title,'description':m.description,'bounds_blocks':metadata['bounds_blocks'],
            'size_blocks':metadata['width_depth_height_blocks'], 'boxes':sum(len(n['boxes']) for n in m.nodes),
            'convex_meshes':sum(len(n.get('meshes',[])) for n in m.nodes),
            'quads':sum(len(n['boxes'])*6+sum(len(mesh['faces']) for mesh in n.get('meshes',[])) for n in m.nodes),'surfaces':len(m.surfaces),
            'moving_parts':len(rig['parts']),'poses':list(rig['poses']), 'reserved_cells':metadata['reserved_cells']}


def write_catalog(catalog):
    write_json(ROOT/'catalog.json',{'schema_version':1,'kit_revision':3,'target_minecraft':'1.12.2',
                                   'reference_forge':'14.23.5.2860','model_format':'Wavefront OBJ/MTL',
                                   'states':STATES,'models':catalog})
    write_json(ROOT/'pack.mcmeta',{'pack':{'pack_format':3,'description':'Vandor Labs cockpit model kit — blank UI surfaces'}})


def build_all():
    texture_files()
    models = [cockpit('cockpit',False)]
    catalog = [export_model(m) for m in models]
    write_catalog(catalog)
    return models,catalog


def load_scene(path):
    data=json.loads(Path(path).read_text())
    m=Model(data['id'],data['title'],data['description'])
    m.nodes=data['nodes'];m.surfaces=data['surfaces']
    m.design_contract=data.get('metadata',{}).get('design_contract',{})
    return m


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--from-source',action='store_true',help='Re-export edited source/*.scene.json instead of re-authoring the default geometry.')
    args=parser.parse_args()
    if args.from_source:
        texture_files()
        result=[export_model(load_scene(p)) for p in sorted((ROOT/'source').glob('*.scene.json'))]
        write_catalog(result)
    else:
        _,result=build_all()
    for item in result:
        print(item['id'],item['boxes'],'cuboids;',item['convex_meshes'],'convex shell sections;',item['surfaces'],'black rectangles;',item['size_blocks'],'blocks (width, depth, height)')
