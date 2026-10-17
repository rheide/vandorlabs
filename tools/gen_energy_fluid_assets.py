"""Generate battery ports and a padded inventory mesh for the hexagonal tank."""
import json
from pathlib import Path
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parents[1];ASSETS=ROOT/'src/main/resources/assets/vandorlabs';TEX=ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks/matter'
def write(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,indent=2)+'\n')
def main():
 TEX.mkdir(parents=True,exist_ok=True)
 for name,color in [('port',(120,145,160)),('energy_in',(75,190,104)),('energy_out',(231,149,49))]:
  im=Image.new('RGB',(64,64),(36,45,55));d=ImageDraw.Draw(im)
  d.rectangle((2,2,61,61),outline=(132,148,158),width=3);d.ellipse((13,13,50,50),fill=(16,24,31),outline=color,width=4)
  if name=='port':d.ellipse((24,24,39,39),outline=color,width=3)
  else:
   x=32;d.polygon([(x-13,25),(x+2,25),(x+2,18),(x+15,32),(x+2,46),(x+2,39),(x-13,39)],fill=color)
   if name=='energy_out':im=im.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
  im.save(TEX/(name+'.png'),optimize=True)
 textures={k:'vandorlabs:blocks/matter/'+v for k,v in {'particle':'casing','north':'energy_in','south':'energy_out','up':'casing','down':'casing','east':'casing','west':'casing'}.items()}
 textures['east']=textures['west']='vandorlabs:blocks/imported/power/battery_cell_bank'
 write(ASSETS/'models/block/battery_block.json',{'parent':'block/cube','textures':textures,'elements':[{'from':[0,0,0],'to':[16,16,16],'faces':{f:{'texture':'#'+f,'cullface':f} for f in ['up','down','north','south','east','west']}}]})
 write(ASSETS/'blockstates/battery_block.json',{'variants':{'facing='+f:{'model':'vandorlabs:battery_block','y':y} for f,y in [('north',0),('east',90),('south',180),('west',270)]}})
 write(ASSETS/'models/item/battery_block.json',{'parent':'vandorlabs:block/battery_block','display':{'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.5,.5,.5]}}})
 write(ASSETS/'models/block/hexagonal_fuel_tank.json',{'parent':'block/block','textures':{'particle':'vandorlabs:blocks/matter/casing'}})
 write(ASSETS/'blockstates/hexagonal_fuel_tank.json',{'variants':{'facing='+f:{'model':'vandorlabs:hexagonal_fuel_tank'} for f in ['north','east','south','west','up','down']}})
 elements=[]
 for z in range(1,15):
  half=4+(min(z-1,14-z)/7)*4
  elements.append({'from':[8-half,0,z],'to':[8+half,16,z+1],'faces':{f:{'texture':'#all'} for f in ['up','down','north','south','east','west']}})
 write(ASSETS/'models/item/hexagonal_fuel_tank.json',{'parent':'block/block','textures':{'all':'vandorlabs:blocks/matter/casing','particle':'vandorlabs:blocks/matter/casing'},'elements':elements,'display':{'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.55,.55,.55]}}})
 for name,pattern,key in [('battery_block',['IRI','RMR','IRI'],{'I':'minecraft:iron_ingot','R':'minecraft:redstone','M':'vandorlabs:programmable_matter_ingot'}),('hexagonal_fuel_tank',['IGI','GMG','IGI'],{'I':'minecraft:iron_ingot','G':'minecraft:glass','M':'vandorlabs:programmable_matter_ingot'})]:
  write(ASSETS/('recipes/'+name+'.json'),{'type':'minecraft:crafting_shaped','pattern':pattern,'key':{k:{'item':v} for k,v in key.items()},'result':{'item':'vandorlabs:'+name}})
if __name__=='__main__':main()
