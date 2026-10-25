"""Import the native Oculus/radar models into the shared Core assembly catalog."""
import argparse,json
from pathlib import Path
from zipfile import ZipFile
from core_textures import core_texture
from generate_machine_surfaces import surfaces
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/vandorlabs'
TEX=ROOT/'texture-packs/additional/assets/vandorlabs/textures'

def write(rel,value):
 p=ASSETS/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,indent=2)+'\n')

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('archives',nargs='+',type=Path);args=parser.parse_args()
 catalog=json.loads((ASSETS/'data/frame_machines.json').read_text());imported=[]
 for path in args.archives:
  with ZipFile(path) as z:
   for name in z.namelist():
    marker='/src/main/resources/assets/mctrek/'
    if marker not in name or name.endswith('/'):continue
    rel=name.split(marker,1)[1]
    if not rel.startswith(('models/','textures/')):continue
    if rel.startswith('models/') and not rel.endswith(('.obj','.mtl')):continue
    out=TEX/rel[9:] if rel.startswith('textures/') else ASSETS/rel
    out.parent.mkdir(parents=True,exist_ok=True);data=z.read(name)
    if rel.endswith(('.obj','.mtl','.json')):data=data.replace(b'mctrek:',b'vandorlabs:')
    out.write_bytes(data)
   for name in z.namelist():
    if '/manifests/' not in name or not name.endswith('.json'):continue
    m=json.loads(z.read(name));design=m['id'];w,h,d=map(int,m['size_blocks']);states=list(m['states'])
    model=m['states'][states[0]]['obj_resource'].split('block/',1)[1].rsplit('/',2)[0]
    spec=dict(design=design,block=design+'_core',width=w,height=h,depth=d,model=model,state_models=[s+'/complete.obj' for s in states],state_labels=[s.title() for s in states],surfaces=surfaces(m['interfaces']))
    if 'chip_pad' in m['interfaces']:
     # Match the native mount's 6px frame: place it just ahead of the casing.
     a=m['interfaces']['chip_pad']['render_anchor_blocks'];spec['chip_anchor']=[a[0],a[1],a[2]-.01];spec['chip_size']=[6,6]
    if not spec.get('chip_anchor'):
     spec['chip_anchor']=[w/2,.3125,.6775 if design=='standalone_dish' else .615];spec['chip_size']=[6,6]
    if m['animation']['kind']=='split_shutters':spec['motion']='shutters'
    elif m['animation']['kind']=='yaw_sweep':spec['motion']='dish';spec['motion_pivot']=m['animation']['pivot_blocks']
    imported.append(spec)
    block=spec['block'];core_texture(TEX/'blocks/matter'/(block+'.png'),(222,187,83) if design=='oculus_drive' else (82,207,222),symbol='fold' if design=='oculus_drive' else 'meridian')
    write('models/block/'+block+'.json',{'parent':'block/cube_all','textures':{'all':'vandorlabs:blocks/matter/'+block}})
    write('models/item/'+block+'.json',{'parent':'vandorlabs:block/'+block,'display':{'gui':{'rotation':[30,225,0],'scale':[.5,.5,.5]}}})
    write('blockstates/'+block+'.json',{'variants':{'facing='+f+',formed='+str(formed).lower():{'model':'vandorlabs:'+('constructor_empty' if formed else block)} for f in ['north','east','south','west'] for formed in [False,True]}})
 catalog=[s for s in catalog if s['design'] not in {s['design'] for s in imported}]+imported
 write('data/frame_machines.json',catalog)
 print('Imported',len(imported),'Oculus/radar Core designs')
if __name__=='__main__':main()
