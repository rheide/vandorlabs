#!/usr/bin/env python3
"""Rebuild runtime cockpit geometry, collision metadata and blank input surfaces."""
import json
import shutil
from pathlib import Path
import cockpit_geometry as geometry
from PIL import Image

PROJECT = Path(__file__).resolve().parents[1]


def components():
    chair = geometry.load_scene(PROJECT/'tools/cockpit-sources/chair_siderests.scene.json')
    yoke = geometry.load_scene(PROJECT/'tools/cockpit-sources/yoke_screen.scene.json')
    for model in (chair, yoke):
        for node in model.nodes:
            node['category'] = 'moving_control' if 'animation' in node else 'structure'
    for node in chair.nodes:
        for box in node['boxes']:
            if box['name'] in ('left_siderest', 'right_siderest'):
                box['to'][1] = 9.4
            if box['name'] == 'chair_back_padding':
                box['from'][1] = 11.0
            if box['material'] in ('cushion', 'cushion_edge'):
                box['material'] = 'red_'+box['material']
    # The long edge runs along the armrest; artwork reads from the seat.
    surface = chair.surfaces[0]
    surface.update(origin=[14.65,10.615,2.1], u=[0,0,9.4], v=[4.7,0,0], render_offset=.05)
    for node in chair.nodes:
        for box in node['boxes']:
            if box['name'] == 'touch_right_single':
                box['to'][2] = 11.5
            if box['name'] == 'touch_right_single_rim':
                box['to'][2] = 11.8
    # A 10 by 5 input deck matches the half-height keyboard artwork.
    for node in yoke.nodes:
        for box in node['boxes']:
            if box['name'] == 'screen_shelf':
                box['to'][2] = 10.4
            elif box['name'] == 'touch_center_strip':
                box['from'][0],box['to'][0]=3.0,13.0
                box['from'][2], box['to'][2] = 5.1, 10.1
            elif box['name'] == 'touch_center_strip_rim':
                box['from'][0],box['to'][0]=2.82,13.18
                box['from'][2], box['to'][2] = 4.92, 10.28
    for node in yoke.nodes:
        if node['name']=='control_yoke':node['origin'][2]+=2
    yoke.surfaces[0].update(origin=[3,8.265,10.1], u=[10,0,0], v=[0,0,-5], render_offset=.05)
    return [chair, yoke]


def main():
    models = [geometry.cockpit('cockpit', False)] + components()
    geometry.texture_files()
    for material,source in [('screen_back','door_side'),('horizontal','metal_side')]:
        shutil.copyfile(PROJECT/'texture-packs/default/assets/vandorlabs/textures/blocks/programmable_glass'/(source+'.png'),geometry.ROOT/'assets/vandorlabs/textures/models/cockpits'/(material+'.png'))
    for model in models:
        for node in model.nodes:
            if model.id=='cockpit':
                for box in node['boxes']+node.get('meshes',[]):
                    if box['material'] in ('cushion','cushion_edge'):box['material']='red_'+box['material']
            # Only the console shell and armrest casing use the selectable skin.
            casing = node['name'] in ('continuous_console_wall','fascia_solid_apron','fascia_frame','screen_bank','seat_assembly','base') or 'armrest' in node['name'] or 'siderest' in node['name'] or model.id == 'chair_siderests' and node['name'] == 'chair' or model.id == 'yoke_screen' and node['name'] == 'screen_center'
            if node['category']=='screen':
                for primitive in node['boxes']+node.get('meshes',[]):
                    if primitive['material']!='blank':primitive['material']='screen_back'
            if casing:
                for primitive in node['boxes'] + node.get('meshes', []):
                    if primitive['material'] in ('housing','housing_dark','frame'):
                        primitive['material'] = 'sidewall'
        for surface in model.surfaces:
            surface['render_offset'] = .05
        geometry.export_model(model)
        folder = geometry.ROOT/'assets/vandorlabs/cockpits'/model.id
        assembly = json.loads((folder/'assembly.json').read_text(encoding='utf-8'))
        if model.id == 'chair_siderests':
            assembly['seat_position_blocks'] = [.5,.5375,.40625]
        elif model.id == 'yoke_screen':
            assembly['seat_position_blocks'] = None
        (folder/'assembly.json').write_text(json.dumps(assembly, indent=2)+'\n', encoding='utf-8')
    assets = geometry.ROOT/'assets/vandorlabs'
    for source in assets.rglob('*'):
        if not source.is_file():
            continue
        relative = source.relative_to(assets)
        root = 'texture-packs/additional/assets/vandorlabs' if relative.parts[0] == 'textures' else 'src/main/resources/assets/vandorlabs'
        target = PROJECT/root/relative
        target.parent.mkdir(parents=True, exist_ok=True)
        if source.suffix == '.png' and target.exists():
            with Image.open(source) as fresh, Image.open(target) as existing:
                if fresh.size == existing.size and fresh.convert('RGBA').tobytes() == existing.convert('RGBA').tobytes():
                    continue
        shutil.copyfile(source, target)
    print('Rebuilt cockpit, chair and yoke assemblies and input planes')


if __name__ == '__main__':
    main()
