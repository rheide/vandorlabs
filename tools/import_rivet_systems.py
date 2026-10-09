"""Import both Rivet geometry tiers with exact cuboid collision and static sprites."""
import argparse
import ast
import importlib.util
import json
from pathlib import Path
from import_ship_systems import ROOT, collision_boxes
from kit_textures import import_textures
from planar_surfaces import clean, overlap_count
from machine_accents import COLORS


def collision_source(kit):
    spec = importlib.util.spec_from_file_location('rivet_mesh', kit / 'mesh.py')
    mesh = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mesh)
    # Evaluate pure geometry builders only; the kit's top-level authoring loop
    # writes external files and must not execute during a host-mod import.
    tree = ast.parse((kit / 'generate_models.py').read_text(encoding='utf-8'))
    nodes = [node for node in tree.body if isinstance(node, (ast.FunctionDef, ast.ClassDef))
             or isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'FUN' for t in node.targets)]
    namespace = {'Mesh': mesh.Mesh}
    exec(compile(ast.Module(body=nodes, type_ignores=[]), '<rivet geometry builders>', 'exec'), namespace)
    return namespace


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('kit', type=Path)
    kit = parser.parse_args().kit
    models = json.loads((kit / 'model_catalog.json').read_text(encoding='utf-8'))
    assert len(models) == 72
    builders = collision_source(kit)
    collisions = {}
    for model in models:
        stem = model['stem']
        if stem not in collisions:
            builder = builders['Build'](model['category'], '_large_' in stem, model['detail_grid'])
            bounds = builders['FUN'][model['category']](builder)
            assert list(bounds) == model['bounds_xyz'], stem
            collisions[stem] = collision_boxes({'faces': builder.m.faces})
        model['collision'] = collisions[stem]
        model['footprint'] = [model['occupancy_xyz'][0], model['occupancy_xyz'][2]]
        if not model['active']:
            model['block_id'] = stem
    overlaps = clean(models)
    assert overlap_count(models) == 0
    assets = ROOT / 'src/main/resources/assets/vandorlabs'
    target = ROOT / 'texture-packs/additional/assets/vandorlabs/textures/blocks/ship_systems/rivet'
    source = kit / 'forge/resources/assets/rivet/textures/blocks'
    import_textures(models, source, target)
    palette = json.loads((kit / 'palette.json').read_text(encoding='utf-8'))
    roles = {'computer':'navy_blue', 'shield':'orange', 'deck':'red', 'reactor':'red',
             'crossflow':'red', 'dampener':'light_green', 'gravity':'dark_green', 'tractor':'light_blue', 'sensor':'yellow'}
    assert all(tuple(palette['energy_'+kind][:3]) == COLORS[color] for kind, color in roles.items())
    items = [m for m in models if not m['active']]
    kinds = list(roles)
    for model in models:
        for face in model['faces']:
            face['material'] = 'rivet/' + face['material']
    for model in items:
        name = model['block_id']
        for folder, data in [('blockstates', {'variants': {'normal': {'model': 'vandorlabs:ship_system_empty'}}}),
                             ('models/item', {'parent': 'block/block'})]:
            (assets / folder / (name+'.json')).write_text(json.dumps(data)+'\n', encoding='utf-8')
        if model['detail_grid'] == 64:
            ingredients = [{'item': 'vandorlabs:'+name.replace('_g64','_g32')},
                           {'item': 'vandorlabs:programmable_matter_ingot'}, {'item': 'minecraft:iron_nugget'}]
        elif '_large_' in name:
            ingredients = [{'item': 'vandorlabs:'+name.replace('_large_', '_small_')},
                           {'item': 'vandorlabs:programmable_matter_ingot'},
                           {'item': 'vandorlabs:programmable_matter_ingot'}, {'item': 'minecraft:iron_block'}]
        else:
            index = kinds.index(model['category'])
            ingredients = [{'item': 'vandorlabs:programmable_matter_ingot'}, {'item': 'minecraft:coal', 'data': 0}]
            ingredients += [{'item': 'minecraft:iron_ingot'}]*(index%3+1)
            ingredients += [{'item': 'minecraft:gold_ingot'}]*(index//3+1)
        (assets/'recipes'/(name+'.json')).write_text(json.dumps({'type':'minecraft:crafting_shapeless',
                'ingredients':ingredients,'result':{'item':'vandorlabs:'+name}})+'\n', encoding='utf-8')
    (assets/'data/rivet_system_meshes.json').write_text(json.dumps(models,separators=(',',':'))+'\n', encoding='utf-8')
    lang = ROOT/'generated-resources/assets/vandorlabs/lang/en_us.lang'
    lines = [line for line in lang.read_text(encoding='utf-8').splitlines()
             if not any(line.startswith('tile.'+m['block_id']+'.') for m in items)]
    lines += ['tile.'+m['block_id']+'.name=Rivet Dynamics '+m['label'].replace(' · OFF','').replace(' · ',' ') for m in items]
    lang.write_text('\n'.join(lines)+'\n', encoding='utf-8')
    print(f'Imported 36 Rivet items, 72 state meshes; clipped {overlaps} coplanar overlaps')


if __name__ == '__main__':
    main()
