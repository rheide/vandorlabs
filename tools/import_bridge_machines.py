"""Import the supplied drive/navigation assets without copying authoring instructions."""
import argparse, json
from pathlib import Path
from zipfile import ZipFile
from core_textures import core_texture

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/vandorlabs'
TEXTURES=ROOT/'texture-packs/additional/assets/vandorlabs/textures'


def write(rel, data):
    p=ASSETS/rel;p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(data,indent=2)+'\n')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archives',nargs='+',type=Path)
    args=parser.parse_args()
    specs=[]
    for archive in args.archives:
        with ZipFile(archive) as z:
            for name in z.namelist():
                marker='/src/main/resources/assets/mctrek/'
                if marker not in name:continue
                rel=name.split(marker,1)[1]
                if rel.endswith('/') or '/parts/' in rel:continue
                if rel.startswith('models/') and not rel.endswith(('.obj','.mtl')):continue
                if not rel.startswith(('textures/','models/','blockstates/')):continue
                # Frame cores use the existing empty/solid blockstate route.
                if rel.startswith('blockstates/') and 'navigation_table' not in rel:continue
                p=TEXTURES/rel[9:] if rel.startswith('textures/') else ASSETS/rel
                p.parent.mkdir(parents=True,exist_ok=True)
                data=z.read(name)
                if rel.endswith(('.obj','.mtl','.json')):data=data.replace(b'mctrek:',b'vandorlabs:')
                p.write_bytes(data)
            for name in z.namelist():
                if '/manifests/' not in name or not name.endswith('.json'):continue
                m=json.loads(z.read(name))
                if m.get('id') in ('paladyne','seyr') and m.get('family')=='navigation_computer_core':
                    model=m['id'];w,h,d=map(int,m['size_blocks_xyz'])
                    specs.append(dict(design=model+'_navigation_computer',block=model+'_navigation_core',width=w,height=h,depth=d,
                                      model='navigation_computer_core/'+model,state_models=[state+'/complete.obj' for state in ['off','idle','computing']]))
                    continue
                if m.get('id') not in ('meridian_core','vector_fold_array','longburn_drive'):continue
                model=m['id'];block='meridian_drive_core' if model=='meridian_core' else model+'_core'
                a=m['attachments']['data_card'];w,h,d=map(int,m['size_blocks_xyz'])
                root=m['states']['idle']['model'].replace('mctrek:block/','').rsplit('/',1)[0]
                specs.append(dict(design=model,block=block,width=w,height=h,depth=d,model=root,
                                  chip_anchor=a['render_anchor_blocks'],chip_size=a['size_pixels']))
    old=[dict(design=design,block=design+'_core',width=w,height=h,depth=3,model='universal_constructor/'+design)
         for design,w,h in [('assembly_press',4,3),('synthesis_tower',3,5),('enclosed_gantry',5,3)]]
    specs.sort(key=lambda s:s['design']);write('data/frame_machines.json',old+specs)
    colors={'meridian_core':((222,187,83),'meridian'),'vector_fold_array':((222,187,83),'fold'),'longburn_drive':((233,168,74),'thrust'),
            'paladyne_navigation_computer':((195,214,228),'meridian'),'seyr_navigation_computer':((71,168,226),'fold')}
    for spec in specs:
        block=spec['block'];color,symbol=colors[spec['design']]
        core_texture(TEXTURES/'blocks/matter'/(block+'.png'),color,symbol=symbol)
        write('models/block/'+block+'.json',{'parent':'block/cube_all','textures':{'all':'vandorlabs:blocks/matter/'+block}})
        write('models/item/'+block+'.json',{'parent':'vandorlabs:block/'+block,'display':{'gui':{'rotation':[30,225,0],'scale':[.5,.5,.5]}}})
        write('blockstates/'+block+'.json',{'variants':{'facing='+f+',formed='+str(formed).lower():{'model':'vandorlabs:'+('constructor_empty' if formed else block)} for f in ['north','east','south','west'] for formed in [False,True]}})
    # Runtime layout and tests are maintained by the mod; never overwrite fixes
    # with the original example source from an artwork archive.
    from generate_navigation_mounts import generate
    generate()
    print(f'Imported {len(specs)} frame machines and two modular navigation tables')


if __name__=='__main__':main()
