"""Import native Gold layouts losslessly, appending variants without replacing saved choices."""
import argparse,hashlib,io,json,re,struct,zlib
from zipfile import ZipFile
from PIL import Image
from import_cool_screens import ROOT,DATA,TEXTURES,write_json,digest_ids,append
from restructure_screen_catalog import restructure

PREFIX='gold27_'
EXISTING={'biology','communications','medical','navigation','radar','reactor'}
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('archive');args=parser.parse_args()
    catalog=json.loads((DATA/'unified_textures.json').read_text());blocks=json.loads((DATA/'blocks.json').read_text());inputs=json.loads((DATA/'input_surfaces.json').read_text())
    audit=dict(entries=[])
    for entries,key,prefix in [(catalog,'Catalog','screen_'+PREFIX),(blocks,'Block',PREFIX),(inputs,'Input',PREFIX)]:
        prior=[e for e in entries if not e['id'].startswith(prefix)]
        audit['previous'+key+'Count']=len(prior);audit['previous'+key+'IdsSha256']=digest_ids(prior)
    assets={}
    with ZipFile(args.archive) as bundle:
        manifests=[n for n in bundle.namelist() if n.endswith('/manifest.json')];assert len(manifests)==1
        prefix=manifests[0].removesuffix('manifest.json');manifest=json.loads(bundle.read(manifests[0]));assert len(manifest)==42
        seen=set()
        for screen in manifest:
            slug=screen['slug'];height=screen['layout'];assert re.fullmatch('[a-z0-9_]+',slug) and height in ('full','half')
            assert (slug,height) not in seen;seen.add((slug,height))
            size=(128,64 if height=='half' else 128);assert tuple(screen['frame_size'])==size and screen['frame_count']==6 and screen['frame_duration_ms']==200
            subject=re.sub(r'^(analysis|bridge|extra|specialist)_','',slug).replace('_',' ').title()
            variant=slug.startswith('bridge_') and subject.lower() in EXISTING
            if variant:subject+=' Bridge'
            frames=[]
            for index in range(6):
                frame=Image.open(io.BytesIO(bundle.read(prefix+f'game/{height}/frames/{slug}/frame_{index:02d}.png'))).convert('RGBA')
                assert frame.size==size and frame.getchannel('A').getextrema()==(255,255);frames.append(frame)
            hashes=[hashlib.sha256(frame.tobytes()).hexdigest() for frame in frames];assert len(set(hashes))==6
            root=prefix+f'game/{height}/';strip=Image.open(io.BytesIO(bundle.read(root+f'animated/{slug}.png'))).convert('RGBA');assert strip.size==(128,size[1]*6)
            for index,frame in enumerate(frames):assert strip.crop((0,index*size[1],128,(index+1)*size[1])).tobytes()==frame.tobytes()
            static=bundle.read(root+f'static/{slug}.png');assert Image.open(io.BytesIO(static)).convert('RGBA').tobytes()==frames[0].tobytes()
            assert json.loads(bundle.read(root+f'animated/{slug}.png.mcmeta'))['animation']==dict(width=128,height=size[1],frametime=4,interpolate=False,frames=list(range(6)))
            identifier=PREFIX+height+'_'+slug;stem=('console_inputs/' if height=='half' else '')+identifier
            rgb=[frame.convert('RGB').tobytes() for frame in frames];delta=b''.join(bytes((b-a)&255 for a,b in zip(rgb[i-1],rgb[i])) for i in range(1,6))
            assets[TEXTURES/(stem+'_static.png')]=static;assets[TEXTURES/(stem+'_animated.anim')]=b'VLTA\x02'+struct.pack('>HHHI',128,size[1],6,len(delta))+zlib.compress(delta,9)
            if height=='half':
                off=io.BytesIO();Image.new('RGBA',size,(0,0,0,255)).save(off,format='PNG',optimize=True);assets[TEXTURES/(stem+'_off.png')]=off.getvalue();append(inputs,dict(id=identifier,animated=True,frames=6))
            else:append(blocks,dict(id=identifier,type='display',**{'class':'BlockDisplaySequenced'},item=False,framed=False,programmable_only=True))
            entry=dict(id='screen_'+identifier,label=subject+f' (Gold/{height.title()})',category='Screens',menuGroup='Gold',source=stem+'_static',frames=6)
            if height=='half':entry['rectangular']=True
            append(catalog,entry);audit['entries'].append(dict(id=entry['id'],source=entry['source'],label=entry['label'],height=height,variant='bridge' if variant else 'expanded',frameDurationMs=200,frameHashes=hashes))
    assert len(seen)==42 and {s for s,h in seen if h=='full'}=={s for s,h in seen if h=='half'}
    assert len({e['id'] for e in catalog})==len(catalog)
    language=DATA.parent/'lang/en_us.lang'
    catalog,blocks,labels=restructure(catalog,blocks,language.read_text())
    for path,content in assets.items():path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(content)
    for name,value in [('unified_textures',catalog),('blocks',blocks),('input_surfaces',inputs)]:write_json(DATA/(name+'.json'),value)
    language.write_text(labels)
    write_json(ROOT/'tools/gold_display_sources.json',audit)
    print('Imported 42 Gold animations / 252 native frames, including 12 retained-original alternatives; compact motion bytes:',sum(len(v) for p,v in assets.items() if p.suffix=='.anim'))
if __name__=='__main__':main()
