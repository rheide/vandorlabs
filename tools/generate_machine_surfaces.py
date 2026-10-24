#!/usr/bin/env python3
"""Import authored screen/keyboard planes from the model-pack manifests."""
import argparse,json
from pathlib import Path
from zipfile import ZipFile
ROOT=Path(__file__).resolve().parents[1]
CATALOG=ROOT/'src/main/resources/assets/vandorlabs/data/frame_machines.json'

def surfaces(manifest):
    result=[]
    for key in ['screen','keyboard']:
        if key not in manifest:continue
        source=manifest[key]
        points=source.get('render_corners_blocks')
        if points is None:points=[[v/16 for v in point] for point in source.get('render_corners',source.get('render_corners_model_pixels',[]))]
        if len(points)!=4:raise ValueError('Four authored corners required')
        size=source.get('size_pixels',source.get('size_model_pixels',[]))
        label='Keyboard' if key=='keyboard' else 'Half Screen' if len(size)==2 and size[1]*2<=size[0] else 'Screen'
        result.append(dict(id=key,label=label,corners=points,normal=source.get('world_normal',source.get('normal'))))
    return result

def generate(archives):
    definitions={}
    for path in archives:
        with ZipFile(path) as archive:
            for name in archive.namelist():
                if '/manifests/' not in name or not name.endswith('.json'):continue
                manifest=json.loads(archive.read(name));key=manifest.get('id')
                if key in ('paladyne','seyr'):key+='_navigation_computer'
                definitions[key]=surfaces(manifest) if manifest.get('family')!='navigation_table' else []
    specs=json.loads(CATALOG.read_text())
    for spec in specs:
        if spec['design'] in definitions:spec['surfaces']=definitions[spec['design']]
    CATALOG.write_text(json.dumps(specs,indent=2)+'\n')
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('archives',nargs='+',type=Path);generate(parser.parse_args().archives)
