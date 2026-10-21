"""Extract upright token fronts and build closed one-model-pixel-thick props."""
import argparse,json
from pathlib import Path
from PIL import Image,ImageFilter
ROOT=Path(__file__).resolve().parents[1]
DESIGNS=('unbranded','pinnacle','ashiro','paladyne','vektor','seyr','vandor')
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--source',type=Path,default=ROOT/'build');args=parser.parse_args()
 textures=ROOT/'texture-packs/additional/assets/vandorlabs/textures/items';models=ROOT/'src/main/resources/assets/vandorlabs/models/item'
 for kind,file,width,height in [('chip','datachips.png',12,12),('card','datacards.png',8.5,14)]:
  sheet=Image.open(args.source/file).convert('RGBA')
  for i,name in enumerate(DESIGNS):
   cell=sheet.crop((round(i*sheet.width/7),0,round((i+1)*sheet.width/7),sheet.height));mask=cell.getchannel('A').point(lambda a:255 if a>=128 else 0).filter(ImageFilter.MedianFilter(7));bounds=mask.getbbox()
   if not bounds:raise ValueError('Empty token '+name)
   art=cell.crop(bounds);background=Image.new('RGBA',art.size,(35,40,44,255));background.alpha_composite(art);art=background.convert('RGB').resize((128,128),Image.Resampling.NEAREST).convert('RGBA')
   identifier='data_'+kind+'_'+name;art.save(textures/(identifier+'.png'),optimize=True)
   texture='vandorlabs:items/'+identifier;edge='vandorlabs:items/data_'+kind+'_edge'
   faces={side:{'texture':'#front' if side in ('north','south') else '#edge','uv':[0,0,16,16]} for side in ('north','south','east','west','up','down')}
   model={'ambientocclusion':False,'textures':{'front':texture,'edge':edge,'particle':texture},'elements':[{'from':[8-width/2,8-height/2,7.5],'to':[8+width/2,8+height/2,8.5],'faces':faces}],
    'display':{'gui':{'rotation':[10,-15,0],'scale':[.85,.85,.85]},'firstperson_righthand':{'rotation':[0,-10,0],'translation':[1,2,0],'scale':[.65,.65,.65]},'firstperson_lefthand':{'rotation':[0,10,0],'translation':[1,2,0],'scale':[.65,.65,.65]},'thirdperson_righthand':{'rotation':[0,90,0],'translation':[0,2,0],'scale':[.5,.5,.5]},'thirdperson_lefthand':{'rotation':[0,-90,0],'translation':[0,2,0],'scale':[.5,.5,.5]},'ground':{'translation':[0,2,0],'scale':[.5,.5,.5]},'fixed':{'scale':[1,1,1]}}}
   (models/(identifier+'.json')).write_text(json.dumps(model,separators=(',',':'))+'\n',encoding='utf-8')
  Image.new('RGBA',(128,128),(45,50,55,255)).save(textures/('data_'+kind+'_edge.png'),optimize=True)
 print('Extracted 14 upright 128px token fronts; closed models have one-pixel depth')
if __name__=='__main__':main()
