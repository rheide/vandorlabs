"""Strict machine footprints with narrowly authored front-workstation exceptions."""
import math

EPSILON = 1e-5
FACES = ('north', 'south', 'west', 'east', 'up', 'down')


def inside(point, bounds):
    return len(point) == 3 and all(
        math.isfinite(value) and low - EPSILON <= value <= high + EPSILON
        for value, low, high in zip(point, bounds[:3], bounds[3:]))


def attachment_bounds(presentation):
    """Use the same shared mount coordinates as the generated Press/Gantry models."""
    result = {}
    for design, spec in presentation.items():
        if design not in ('assembly_press', 'enclosed_gantry'):
            continue
        x, y, z = spec['screenX'], spec['screenY'], spec['screenZ']
        half = spec['screenFrame'] / 2
        groups = {
            'static__display_housing_' + face:
                (x-half, y-half, z+.01, x+half, y+half, .375)
            for face in FACES if face != 'north'
        }
        for group, size, depth in (
                ('static__display_bezel', spec['screenFrame'], z+.005),
                ('static__display', spec['screenSize'], z)):
            half = size / 2
            groups[group] = (x-half, y-half, depth, x+half, y+half, depth)
        x, y, z = spec['keyboardX'], spec['keyboardY'], spec['keyboardZ']
        right, rear = x+spec['keyboardWidth'], z+spec['keyboardDepth']
        for face in FACES:
            if face != 'up':
                groups['static__keyboard_case_' + face] = (x, y-.125, z, right, y, rear)
        groups['static__keyboard'] = (x, y, z, right, y, rear)
        result[design] = groups
    return result


def valid_vertex(spec, vertex, groups, attachments):
    footprint = (0, 0, 0, spec['width'], spec['height'], spec['depth'])
    if inside(vertex, footprint):
        return True
    # Never let a shared mount change silently exceed the runtime's half-block
    # render padding. Every face using an exterior vertex must be an attachment.
    render_bounds = (-.5, -.5, -.5, spec['width']+.5, spec['height']+.5, spec['depth']+.5)
    allowed = attachments.get(spec['design'], {})
    return (inside(vertex, render_bounds) and bool(groups)
            and all(group in allowed and inside(vertex, allowed[group]) for group in groups))
