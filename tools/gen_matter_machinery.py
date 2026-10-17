"""Generate compact pixel artwork and recipes for programmable matter machinery."""
import json
from pathlib import Path
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/vandorlabs'
TEXTURES=ROOT/'texture-packs/additional/assets/vandorlabs/textures'
def write(path,value):
 path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n')
def main():
 target=TEXTURES/'blocks/matter';target.mkdir(parents=True,exist_ok=True)
 # Same graphite casing and cyan instruments as the ramp controller.
 for name in ['casing','front','lens','matter','core']:
  image=Image.new('RGB',(64,64),(45,53,63));d=ImageDraw.Draw(image)
  d.rectangle((1,1,62,62),outline=(125,143,154),width=2)
  d.rectangle((5,5,58,58),outline=(20,28,35),width=2)
  for x,y in [(7,7),(55,7),(7,55),(55,55)]:d.rectangle((x,y,x+2,y+2),fill=(153,165,172))
  if name=='casing':
   for y in range(20,48,6):d.rectangle((14,y,49,y+2),fill=(22,29,36))
  if name=='front':
   d.rectangle((13,12,50,35),fill=(15,24,30));d.rectangle((16,15,47,32),outline=(86,204,219),width=2)
   d.rectangle((15,43,48,52),fill=(20,23,30));d.rectangle((20,46,43,48),fill=(182,48,51))
  if name in ['lens','core']:
   for r,c in [(24,(142,154,163)),(21,(18,25,32)),(18,(86,178,200)),(14,(29,70,91)),(10,(109,210,230)),(5,(198,243,244))]:
    d.ellipse((32-r,32-r,31+r,31+r),fill=c)
   d.line((22,22,28,16),fill=(230,255,255),width=2)
  if name=='matter':
   for y in range(14,54,10):
    for x in range(14,54,10):
     d.rectangle((x,y,x+4,y+4),fill=(79,176,188));d.line((x+4,y+2,x+10,y+2),fill=(46,103,118))
  if name=='core':
   image=image.convert('RGBA');image.putalpha(Image.new('L',(64,64),0));d=ImageDraw.Draw(image);d.rounded_rectangle((8,8,55,55),radius=14,fill=(46,65,78,255),outline=(142,175,185,255),width=3)
   d.ellipse((18,18,45,45),fill=(49,151,185,255),outline=(165,237,244,255),width=3)
  image.save(target/(name+'.png'),optimize=True)
 model={'parent':'block/cube','textures':{k:'vandorlabs:blocks/matter/'+v for k,v in {'particle':'casing','down':'casing','up':'lens','north':'front','south':'casing','east':'casing','west':'casing'}.items()},'elements':[{'from':[0,0,0],'to':[16,16,16],'faces':{f:{'texture':'#'+f,'cullface':f} for f in ['down','up','north','south','east','west']}}]}
 write(ASSETS/'models/block/programmable_matter_infuser.json',model)
 write(ASSETS/'blockstates/programmable_matter_infuser.json',{'variants':{'facing='+f:{'model':'vandorlabs:programmable_matter_infuser','y':y} for f,y in [('north',0),('east',90),('south',180),('west',270)]}})
 write(ASSETS/'models/block/programmable_matter_block.json',{'parent':'block/cube_all','textures':{'all':'vandorlabs:blocks/matter/matter'}})
 write(ASSETS/'blockstates/programmable_matter_block.json',{'variants':{'normal':{'model':'vandorlabs:programmable_matter_block'}}})
 for name in ['programmable_matter_infuser','programmable_matter_block']:write(ASSETS/('models/item/'+name+'.json'),{'parent':'vandorlabs:block/'+name,'display':{'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.5,.5,.5]}}})
 write(ASSETS/'models/item/infuser_core.json',{'parent':'item/generated','textures':{'layer0':'vandorlabs:blocks/matter/core'}})
 def recipe(name,pattern,key,result,count=1):write(ASSETS/('recipes/'+name+'.json'),{'type':'minecraft:crafting_shaped','pattern':pattern,'key':{k:{'item':v} for k,v in key.items()},'result':{'item':result,'count':count}})
 recipe('programmable_matter_block',['MMM']*3,{'M':'vandorlabs:programmable_matter_ingot'},'vandorlabs:programmable_matter_block')
 recipe('programmable_matter_unpack',['M'],{'M':'vandorlabs:programmable_matter_block'},'vandorlabs:programmable_matter_ingot',9)
 recipe('programmable_matter_infuser',['IGI','ICI','IRI'],{'I':'minecraft:iron_ingot','G':'minecraft:glass','C':'vandorlabs:infuser_core','R':'minecraft:redstone'},'vandorlabs:programmable_matter_infuser')
if __name__=='__main__':main()
