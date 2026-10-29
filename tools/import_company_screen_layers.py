"""Append shared company borders and six-frame display layers at native runtime sizes."""
import argparse,json
from pathlib import Path
from PIL import Image
from frame_edges import opaque_strip
ROOT=Path(__file__).resolve().parents[1]
def write_json(path,value):path.write_text(json.dumps(value,indent=2)+'\n')
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('source',type=Path);source=parser.parse_args().source
    data=ROOT/'generated-resources/assets/vandorlabs/data';textures=ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks'
    catalog=json.loads((data/'unified_textures.json').read_text());blocks=json.loads((data/'blocks.json').read_text());inputs=json.loads((data/'input_surfaces.json').read_text())
    before=[e['id'] for e in catalog]
    def append(entries,entry):
        old=next((e for e in entries if e['id']==entry['id']),None)
        if old is None:entries.append(entry)
        else:assert old==entry,entry['id']
    for border in sorted((source/'borders').glob('*/*.png')):
        company,height=border.parent.name,border.stem;image=Image.open(border).convert('RGBA');aspect=image.width/image.height
        resized=image.resize((64,round(64/aspect)),Image.Resampling.LANCZOS);canvas=Image.new('RGBA',(64,64));canvas.paste(resized,(0,0))
        stem=f'frames/company/{company}_{height}';target=textures/(stem+'.png');target.parent.mkdir(parents=True,exist_ok=True);canvas.save(target,optimize=True)
        opaque_strip(resized).save(target.with_name(target.stem+"_rail.png"),optimize=True)
        append(catalog,dict(id=f'frame_company_{company}_{height}',label=company.replace('_',' ').title()+' '+height.title(),category='Frame',menuGroup=company.replace('_',' ').title(),source=stem,frame=True,frameBorder=True,frameAspect=aspect,frameRail=stem+'_rail'))
    for metadata in sorted((source/'displays').glob('*/*/*/animation.json')):
        info=json.loads(metadata.read_text());company,height,subject=metadata.parts[-4:-1];identifier=f'company_{company}_{height}_{subject}';stem=('console_inputs/' if height=='half' else '')+identifier
        size=(128,64 if height=='half' else 128);frames=[Image.open(metadata.parent/f'frame_{i:02d}.png').convert('RGBA').resize(size,Image.Resampling.LANCZOS) for i in range(6)]
        target=textures/(stem+'_static.png');target.parent.mkdir(parents=True,exist_ok=True);frames[0].save(target,optimize=True)
        if height=='half':
            off=Image.new('RGBA',size,(0,0,0,0));off.putalpha(frames[0].getchannel('A'));off.save(textures/(stem+'_off.png'),optimize=True)
        strip=Image.new('RGBA',(size[0],size[1]*6))
        for i,frame in enumerate(frames):strip.paste(frame,(0,i*size[1]))
        animated=textures/(stem+'_animated.png');strip.save(animated,optimize=True);write_json(Path(str(animated)+'.mcmeta'),dict(animation=dict(width=size[0],height=size[1],frametime=round(info['frame_duration_ms']/50),frames=list(range(6)))))
        entry=dict(id='screen_'+identifier,label=info['company']+' '+height.title()+' '+subject.title(),category='Screens',menuGroup=company.replace('_',' ').title(),source=stem+'_static',frames=6)
        if height=='half':entry['rectangular']=True
        append(catalog,entry)
        if height=='half':append(inputs,dict(id=identifier,animated=True,frames=6))
        else:append(blocks,dict(id=identifier,type='display',**{'class':'BlockDisplaySequenced'},item=False,framed=False,programmable_only=True))
    assert [e['id'] for e in catalog[:len(before)]]==before
    write_json(data/'unified_textures.json',catalog);write_json(data/'blocks.json',blocks);write_json(data/'input_surfaces.json',inputs)
    print('Imported 12 company borders and 72 six-frame display layers; saved choices preserved')
if __name__=='__main__':main()
