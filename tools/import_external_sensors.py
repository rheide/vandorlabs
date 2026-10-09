"""Import exterior sensor tiers into the shared decorative machinery system."""
import argparse
import ast
import importlib.util
import json
from pathlib import Path
from import_ship_systems import ROOT, collision_boxes
from kit_textures import import_textures
from machine_accents import apply_accents
from planar_surfaces import clean, overlap_count


def builders(kit):
    spec = importlib.util.spec_from_file_location('sensor_mesh', kit/'mesh.py')
    mesh = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mesh)
    tree = ast.parse((kit/'generate_models.py').read_text(encoding='utf-8'))
    nodes = [node for node in tree.body if isinstance(node, (ast.FunctionDef, ast.ClassDef))]
    scope = {'Mesh': mesh.Mesh}
    exec(compile(ast.Module(body=nodes, type_ignores=[]), '<sensor geometry>', 'exec'), scope)
    return scope


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('kit', type=Path)
    kit = parser.parse_args().kit
    models = json.loads((kit/'model_catalog.json').read_text(encoding='utf-8'))
    assert len(models) == 20
    scope = builders(kit)
    collisions = {}
    for model in models:
        stem = model['stem']
        if stem not in collisions:
            b = scope['B'](model['manufacturer'], model['detail_grid'])
            scope[model['manufacturer']](b)
            b.detail()
            collisions[stem] = collision_boxes({'faces': b.m.faces})
        # Rear Z=1 becomes the shared mounting plane Y=0. This proper
        # rotation preserves winding and leaves both sensor axes on the hull.
        for face in model['faces']:
            face['v'] = [[x, 1-z, y] for x, y, z in face['v']]
            nx, ny, nz = face['normal']
            face['normal'] = [nx, -nz, ny]
        model['collision'] = [[x0, 1-z1, y0, x1, 1-z0, y1]
                              for x0, y0, z0, x1, y1, z1 in collisions[stem]]
        model['occupancy_xyz'] = model['bounds_xyz'] = [2, 1, 2]
        model['footprint'] = [2, 2]
        model['category'] = 'analysis'
        model['description'] = 'Exterior sensor; mounting plane Y=0, outward +Y.'
        if not model['active']:
            model['block_id'] = stem
    clipped = clean(models)
    assert overlap_count(models) == 0
    target = ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks/ship_systems/external'
    import_textures(models, kit/'forge/resources/assets/shipsensors/textures/blocks', target)
    apply_accents(models, target)
    assets = ROOT/'src/main/resources/assets/vandorlabs'
    items = [m for m in models if not m['active']]
    for model in models:
        for face in model['faces']:
            face['material'] = 'external/'+face['material']
    for index, model in enumerate(items):
        name = model['block_id']
        for folder, data in [('blockstates', {'variants': {'normal': {'model': 'vandorlabs:ship_system_empty'}}}),
                             ('models/item', {'parent': 'block/block'})]:
            (assets/folder/(name+'.json')).write_text(json.dumps(data)+'\n', encoding='utf-8')
        if model['detail_grid'] == 64:
            ingredients = [{'item':'vandorlabs:'+name.replace('_g64', '_g32')},
                           {'item':'vandorlabs:programmable_matter_ingot'}, {'item':'minecraft:iron_nugget'}]
        else:
            ingredients = [{'item':'vandorlabs:programmable_matter_ingot'}, {'item':'minecraft:quartz'},
                           {'item':'minecraft:comparator'}, {'item':'minecraft:dye','data':[1,5,10,4,15][index//2]}]
        (assets/'recipes'/(name+'.json')).write_text(json.dumps({'type':'minecraft:crafting_shapeless',
            'ingredients':ingredients,'result':{'item':'vandorlabs:'+name}})+'\n', encoding='utf-8')
    (assets/'data/external_sensor_meshes.json').write_text(json.dumps(models,separators=(',',':'))+'\n', encoding='utf-8')
    lang = ROOT/'generated-resources/assets/vandorlabs/lang/en_us.lang'
    lines = [line for line in lang.read_text(encoding='utf-8').splitlines()
             if not any(line.startswith('tile.'+m['block_id']+'.') for m in items)]
    lines += ['tile.'+m['block_id']+'.name='+m['label'].replace(' · OFF','').replace(' · ',' ') for m in items]
    lang.write_text('\n'.join(lines)+'\n', encoding='utf-8')
    print(f'Imported {len(items)} exterior sensors, {len(models)} states; clipped {clipped} coplanar overlaps')


if __name__ == '__main__':
    main()
