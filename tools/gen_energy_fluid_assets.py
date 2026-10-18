"""Generate battery ports and a padded inventory mesh for the hexagonal tank."""
import json
from pathlib import Path
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parents[1];ASSETS=ROOT/'src/main/resources/assets/vandorlabs';TEX=ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks/matter'
def write(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,indent=2)+'\n')
def main():
 TEX.mkdir(parents=True,exist_ok=True)
 source=ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks/imported'
 # Keep the round fuel port centered on an end cap; map the image once over the hexagon.
 Image.open(source/'fuel/fuel_port_round.png').convert('RGB').crop((14,7,126,119)).resize((128,128),Image.Resampling.NEAREST).save(TEX/'port.png')
 dock=Image.open(source/'power/battery_charging_dock.png').convert('RGB').crop((23,38,117,132)).resize((128,128),Image.Resampling.NEAREST)
 for name,color in [('energy_in',(75,220,110)),('energy_out',(255,167,53))]:
  im=dock.copy();d=ImageDraw.Draw(im)
  d.rectangle((3,3,124,124),outline=color,width=3)
  d.rectangle((48,106,79,114),fill=color)
  d.text((52,90),'IN' if name=='energy_in' else 'OUT',fill=color)
  im.save(TEX/(name+'.png'),optimize=True)
 im=Image.new('RGB',(32,32),(15,23,28));d=ImageDraw.Draw(im)
 d.rectangle((1,1,30,30),outline=(51,64,71),width=2)
 for yy in range(5,30,6):d.line((4,yy,27,yy),fill=(22,32,38))
 im.save(TEX/'tank_empty.png')
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
