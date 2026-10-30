"""Split supplied six-face redstone sheets into 128px directional block textures."""
import json
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/vandorlabs'
def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
for kind in ('transmitter','receiver'):
    name='redstone_'+kind
    block_id=name+'_block'
    sheet=Image.open(ROOT/'build'/(name+'.png')).convert('RGB')
    assert sheet.width//3==sheet.height//2
    size=sheet.width//3
    folder=ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks'/name
    folder.mkdir(parents=True,exist_ok=True)
    for i,face in enumerate(('up','port','type','left','right','down')):
        x,y=i%3*size,i//3*size
        sheet.crop((x,y,x+size,y+size)).resize((128,128),Image.Resampling.NEAREST).save(folder/(face+'.png'))
    # North is the physical port. Both authored arrow sets point toward it.
    # Keep port/type faces fixed when correcting side arrow direction.
    arrows=('up','down','left','right')
    textures=dict(zip(('up','down','west','east'),['vandorlabs:blocks/'+name+'/'+a for a in arrows]))
    textures.update(north='vandorlabs:blocks/'+name+'/port',south='vandorlabs:blocks/'+name+'/type')
    write(ASSETS/'models/block'/(name+'.json'),dict(parent='block/cube',textures=dict(textures,particle=textures['north'])))
    rotations={'north':{},'south':{'y':180},'west':{'y':270},'east':{'y':90},'up':{'x':270},'down':{'x':90}}
    write(ASSETS/'blockstates'/(block_id+'.json'),dict(variants={'facing='+side:dict(model='vandorlabs:'+name,**rotation) for side,rotation in rotations.items()}))
    write(ASSETS/'models/item'/(block_id+'.json'),dict(parent='vandorlabs:block/'+name,display=dict(gui=dict(rotation=[30,225,0],scale=[.5,.5,.5]))))
print('Split two directional redstone blocks: port front, type back, four arrows following signal flow')
