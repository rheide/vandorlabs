#!/usr/bin/env python3
"""Import native cargo artwork and generate mirrored leaves or split square bays."""
from pathlib import Path
import argparse, copy, importlib.util, json, shutil
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('doors',ROOT/'tools/import_space_doors.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
FAMILIES='plain_cargo stepped_freight observation_leaf reinforced_leaf warehouse_shutter slotted_bay cross_braced_bay split_view_bay offset_cargo twin_observation armored_biparting service_freight'.split()

def generate(source,center_source):
    assets=ROOT/'generated-resources/assets/vandorlabs'
    geometry=json.loads((ROOT/'docs/space-door-pack/hinge/geometry.json').read_text())
    for tier,height in [('low',256),('medium',512),('high',1024)]:
        for index,family in enumerate(FAMILIES):
            square=index>=4
            folder=('single-'+str(height)+'x'+str(height)) if square else ('mirrorable-'+str(height//2)+'x'+str(height))
            filename=f'{index+1:02}_{family}'+('' if square else '_left')+'.png'
            input_file=source/f'height-{height}'/folder/filename
            if index>=8:input_file=center_source/f'{height}x{height}'/(f'{index-7:02}_{family}.png')
            dest=ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks/cargo_doors'/tier/(family+'.png')
            dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(input_file,dest)
            for sliding in [False,True]:
                for framed in [False,True]:
                    for paired in [False,True]:
                        x0=1 if framed else 0;x1=16 if paired or not framed else 15
                        y0,y1=(1,31) if framed else (0,32)
                        z0,z1=(7,9) if sliding else (12.24,14.24)
                        # Native rectangular leaves use the full U range; a complete square bay uses half.
                        factor=.5 if square else 1
                        leaf=[m.plate(x0,ya,x1,yb,'leaf',[x0*factor,(32-yb)/2,x1*factor,(32-ya)/2],z0,z1)
                              for ya,yb in [(y0,16),(16,y1)]]
                        fixed=m.frame(paired=paired,rotating=not sliding) if framed else []
                        if not sliding:fixed+=m.hinges(geometry,'fixed',framed);leaf+=m.hinges(geometry,'moving',framed)
                        for right in [False,True]:
                            base='space_'+family+('_sliding_' if sliding else '_rotating_')+('framed' if framed else 'bare')+('_paired' if paired else '')+('_right_' if right else '_left_')
                            for part,elements in [('fixed',fixed),('leaf',leaf),('glass',[])]:
                                elements=m.mirror(elements) if right else copy.deepcopy(elements)
                                if square and right and part=='leaf':
                                    for element in elements:
                                        for face in element['faces'].values():
                                            if face['texture']=='#leaf':
                                                face['uv'][0]=16-face['uv'][0];face['uv'][2]=16-face['uv'][2]
                                for no_hinge in ([False,True] if not sliding and part!='glass' else [False]):
                                    name=base+part+('_no_hinges' if no_hinge else '')
                                    es=[e for e in elements if not all(f['texture']=='#hinge' for f in e['faces'].values())] if no_hinge else elements
                                    model=m.model(es)
                                    model['textures'].update(leaf='vandorlabs:blocks/cargo_doors/'+tier+'/'+family,particle='vandorlabs:blocks/cargo_doors/'+tier+'/'+family,edge='vandorlabs:blocks/dark_wall_panel',frame='vandorlabs:blocks/space_doors/'+tier+'/double_frame_metal',hinge='vandorlabs:blocks/space_doors/'+tier+'/hinge')
                                    path=assets/'models/block/detailed_doors'/tier/(name+'.json');path.write_text(json.dumps(model,indent=2)+'\n')
                                    path=assets/'models/item/detailed_doors'/tier/(name+'.json');path.write_text(json.dumps({'parent':'vandorlabs:block/detailed_doors/'+tier+'/'+name},indent=2)+'\n')
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('source',type=Path);parser.add_argument('center_source',type=Path);args=parser.parse_args();generate(args.source,args.center_source)
