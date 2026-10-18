"""Generate shared telescopic ski/pad assets with padded inventory transforms."""
import json,copy
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];ASSETS=ROOT/'src/main/resources/assets/vandorlabs'
SIZES=['small','medium','large','extra_large']
def write(rel,data):
 p=ASSETS/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,indent=2)+'\n',encoding='utf8')
def cuboid(name,a,b,texture='metal',rotation=None):
 e={'name':name,'from':a,'to':b,'faces':{f:{'texture':'#'+texture,'uv':[0,0,16,16]} for f in ['north','south','east','west','up','down']}}
 if rotation:e['rotation']=rotation
 return e

def main():
 for family in ['ski_landing_gear','landing_strut','angled_landing_strut']:
  for index,size in enumerate(SIZES):
   large=index==3;lo,hi=(-8,24) if large else (0,16);cx=8;anchor=[12,14,15,14][index];length=[2,1,.5,1][index]
   fixed=[cuboid('mount_plate',[lo,anchor,lo],[hi,16,hi]),cuboid('fixed_sleeve',[4,anchor-2,4],[12,anchor,12])]
   if family=='ski_landing_gear':
    moving=[cuboid('runner',[lo+4,0,lo],[hi-4,2,hi]),cuboid('stem',[6,2,6],[10,anchor-length,10]),cuboid('front_tip',[lo+4,1,lo],[hi-4,3,lo+3]),cuboid('rear_tip',[lo+4,1,hi-3],[hi-4,3,hi])]
   else:
    moving=[cuboid('landing_pad',[lo+1,0,lo+1],[hi-1,2,hi-1]),cuboid('rubber_foot',[lo+2,0,lo+2],[hi-2,1,hi-2],'rubber')]
    if family=='angled_landing_strut':
     moving.append(cuboid('directional_brace',[6,2,6],[10,anchor-length,10],rotation={'origin':[8,anchor-length,8],'axis':'x','angle':22.5,'rescale':False}))
    else:moving.append(cuboid('straight_column',[6,2,6],[10,anchor-length,10]))
   piston=[cuboid('piston',[6,anchor-length,6],[10,anchor,10])]
   textures={'metal':'vandorlabs:blocks/matter/casing','rubber':'vandorlabs:blocks/landing_gear/rubber','particle':'vandorlabs:blocks/matter/casing'}
   # Reuse the exact rubber identifier from the retained wheeled gear.
   original=json.loads((ASSETS/'models/block/landing_gear_small_retracted.json').read_text());textures['rubber']=original['textures'].get('rubber',original['textures'].get('tyre','minecraft:blocks/coal_block'))
   model={'textures':textures,'elements':fixed+moving+piston}
   write('models/block/'+family+'_'+size+'_retracted.json',model)
   for group,elements in [('fixed',fixed),('wheel',[e for e in moving if e['name'] not in ['front_tip','rear_tip']]),('piston',piston),('nose_front',[e for e in moving if e['name']=='front_tip']),('nose_rear',[e for e in moving if e['name']=='rear_tip'])]:write('models/item/'+family+'_'+size+'_'+group+'.json',{'textures':textures,'elements':elements})
   write('models/item/'+family+'_'+size+'.json',{'parent':'vandorlabs:block/'+family+'_'+size+'_retracted','display':{'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.22 if large else .5]*3},'firstperson_righthand':{'rotation':[0,45,0],'translation':[0,0,0],'scale':[.2]*3},'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[.2]*3}}})
  write('models/item/'+family+'.json',{'parent':'vandorlabs:item/'+family+'_small'})
  write('models/block/'+family+'_empty.json',{'textures':{'particle':'vandorlabs:blocks/matter/casing'}})
  write('blockstates/'+family+'.json',{'variants':{'extended='+str(extended).lower()+',facing='+f+',lower='+str(lower).lower():{'model':'vandorlabs:'+family+'_empty'} for extended in [False,True] for f in ['north','east','south','west'] for lower in [False,True]}})
if __name__=='__main__':main()
