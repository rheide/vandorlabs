#!/usr/bin/env python3
"""Import the supplied Voxel Machines cuboids and exact atlases; no model redesign."""
import argparse,json,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
MAPPING={'heavy_constructor':['synthesis_tower'],'automated_fabricator':['assembly_press'],'industrial_printer':['enclosed_gantry'],'comms_relay':['sentinel_array','standalone_antenna'],'network_node':['survey_dish','standalone_dish'],'deep_space_array':['vector_fold_array'],'ion_drive':['meridian_core'],'plasma_drive':['longburn_drive'],'fusion_drive':['oculus_drive']}
def main(source):
    source=Path(source)/'src/main/resources/assets/voxelmachines'
    archive=ROOT/'assets-src/voxel-cores';archive.mkdir(parents=True,exist_ok=True)
    lines=['package com.vandorlabs.geometry;','/** Generated from the supplied, unmodified Voxel Machines cuboids. */','final class ImportedCoreData {','static String model(String id){']
    for model,ids in MAPPING.items():
        raw=(source/f'models/block/{model}.json').read_bytes();(archive/f'{model}.json').write_bytes(raw)
        image=ROOT/f'texture-packs/additional/assets/vandorlabs/textures/blocks/voxel_cores/{model}.png';image.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source/f'textures/blocks/{model}.png',image)
        data=json.loads(raw);boxes=[]
        for element in data['elements']:
            if 'rotation' in element:raise ValueError('Rotated cuboid unsupported: '+model)
            numbers=[round(v*4) for v in element['from']+element['to']]
            for name in ('west','east','down','up','north','south'):
                face=element['faces'][name]
                if face.get('rotation',0):raise ValueError('Rotated UV unsupported')
                numbers+=face['uv']
            boxes.append(','.join(str(v) for v in numbers))
        for id in ids:lines.append('if(id.equals("'+id+'"))return "'+model+'";')
        # Separate methods avoid the JVM method size and string constant limits.
        lines.append('')
    lines+=['return null;}','static String[] boxes(String model){']
    for model in MAPPING:lines.append('if(model.equals("'+model+'"))return '+model+'();')
    lines+=['throw new IllegalArgumentException(model); }']
    for model in MAPPING:
        data=json.loads((archive/f'{model}.json').read_text());encoded=[]
        for e in data['elements']:
            values=[round(v*4) for v in e['from']+e['to']]
            for name in ('west','east','down','up','north','south'):values+=e['faces'][name]['uv']
            encoded.append(','.join(str(v) for v in values))
        lines.append('private static String[] '+model+'(){return new String[]{'+','.join(json.dumps(v) for v in encoded)+'};}')
    for model,ids in MAPPING.items():
        data=json.loads((archive/f'{model}.json').read_text())
        data['textures']={key:value.replace('voxelmachines:blocks/','vandorlabs:blocks/voxel_cores/') for key,value in data['textures'].items()}
        data['display']['gui']['scale']=[.54,.54,.54]
        for id in ids:
            block={'meridian_core':'meridian_drive_core'}.get(id,id+'_core')
            (ROOT/f'src/main/resources/assets/vandorlabs/models/block/{block}.json').write_text(json.dumps(data,indent=2)+'\n')
    lines+=['private ImportedCoreData(){}','}']
    (ROOT/'src/portable/java/com/vandorlabs/geometry/ImportedCoreData.java').write_text('\n'.join(lines)+'\n')
    (archive/'mapping.json').write_text(json.dumps(MAPPING,indent=2)+'\n')
    print('Imported nine supplied cuboid models/atlases for eleven existing Core IDs')
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('source');main(p.parse_args().source)
