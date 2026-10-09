"""Import six distinct high-detail Rivet assemblies with per-part collision."""
import argparse
import ast
import importlib.util
import json
import math
from pathlib import Path
from import_ship_systems import ROOT
from kit_textures import import_textures
from planar_surfaces import clean, overlap_count


def part_collisions(kit):
    spec = importlib.util.spec_from_file_location('reference_mesh', kit/'mesh.py')
    mesh = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mesh)
    nodes = [n for n in ast.parse((kit/'generate_models.py').read_text(encoding='utf-8')).body
             if isinstance(n, (ast.ClassDef, ast.FunctionDef))]
    scope = {'Mesh':mesh.Mesh, 'math':math}
    exec(compile(ast.Module(body=nodes,type_ignores=[]), '<reference geometry>', 'exec'), scope)
    craft = scope['Craft']
    box, solid = craft.box, craft.solid

    def record(method):
        def wrapped(self, *args, **kwargs):
            start = len(self.m.faces)
            result = method(self, *args, **kwargs)
            vertices = [v for f in self.m.faces[start:] for v in f['v']]
            self.collision.append([min(v[i] for v in vertices) for i in range(3)]
                                  + [max(v[i] for v in vertices) for i in range(3)])
            return result
        return wrapped

    craft.box, craft.solid = record(box), record(solid)
    init = craft.__init__
    def initialize(self, kind):
        init(self, kind)
        self.collision = []
    craft.__init__ = initialize
    result = {}
    for kind in ('reactor', 'crossflow', 'dampener'):
        for large in (False, True):
            c, _ = scope['reactor'](large) if kind == 'reactor' else scope['flow'](large, kind == 'dampener')
            for f in c.m.faces:
                if f['material'] != 'glass':
                    continue
                low = [min(v[i] for v in f['v']) for i in range(3)]
                high = [max(v[i] for v in f['v']) for i in range(3)]
                axis = next(i for i in range(3) if low[i] == high[i])
                low[axis] -= 1/128
                high[axis] += 1/128
                c.collision.append(low+high)
            result[f'rivet_ref_{kind}_{"large" if large else "small"}'] = c.collision
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('kit', type=Path)
    kit = parser.parse_args().kit
    models = json.loads((kit/'model_catalog.json').read_text(encoding='utf-8'))
    assert len(models) == 12
    collision = part_collisions(kit)
    for m in models:
        # The authored foot pads start 1/32 above the origin; seat them on Y=0.
        for f in m['faces']:
            f['v'] = [[x,y-1/32,z] for x,y,z in f['v']]
        m['collision'] = [[x0,y0-1/32,z0,x1,y1-1/32,z1]
                          for x0,y0,z0,x1,y1,z1 in collision[m['stem']]]
        m['footprint'] = [m['occupancy_xyz'][0],m['occupancy_xyz'][2]]
        if not m['active']:
            m['block_id'] = m['stem']
    clipped = clean(models)
    assert overlap_count(models) == 0
    target = ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks/ship_systems/rivet_reference'
    import_textures(models, kit/'forge/resources/assets/rivetref/textures/blocks', target)
    assets = ROOT/'src/main/resources/assets/vandorlabs'
    items = [m for m in models if not m['active']]
    for m in models:
        for f in m['faces']:
            f['material'] = 'rivet_reference/'+f['material']
    names = {'reactor':'Reactor', 'crossflow':'Cross-flow Core', 'dampener':'Inertial Dampener'}
    for m in items:
        name = m['block_id']
        for folder, data in [('blockstates', {'variants':{'normal':{'model':'vandorlabs:ship_system_empty'}}}),
                             ('models/item', {'parent':'block/block'})]:
            (assets/folder/(name+'.json')).write_text(json.dumps(data)+'\n',encoding='utf-8')
        baseline = 'rivet_'+m['category']+'_'+m['size']+'_g64'
        ingredients = [{'item':'vandorlabs:'+baseline}, {'item':'vandorlabs:programmable_matter_ingot'},
                       {'item':'minecraft:iron_ingot'}, {'item':'minecraft:quartz'}]
        (assets/'recipes'/(name+'.json')).write_text(json.dumps({'type':'minecraft:crafting_shapeless',
            'ingredients':ingredients,'result':{'item':'vandorlabs:'+name}})+'\n',encoding='utf-8')
    (assets/'data/rivet_reference_meshes.json').write_text(json.dumps(models,separators=(',',':'))+'\n',encoding='utf-8')
    lang = ROOT/'generated-resources/assets/vandorlabs/lang/en_us.lang'
    lines = [line for line in lang.read_text(encoding='utf-8').splitlines()
             if not any(line.startswith('tile.'+m['block_id']+'.') for m in items)]
    lines += ['tile.'+m['block_id']+'.name=Rivet Dynamics Reference '+names[m['category']]+' '+m['size'].title() for m in items]
    lang.write_text('\n'.join(lines)+'\n',encoding='utf-8')
    print(f'Imported six reference assemblies, twelve state meshes; clipped {clipped} coplanar overlaps')


if __name__ == '__main__':
    main()
