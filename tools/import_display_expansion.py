"""Append Gold expansion and native Large Viewscreen/City Transit animations."""
import argparse,hashlib,io,json,re,struct,zlib
from pathlib import Path
from zipfile import ZipFile
from PIL import Image
from import_cool_screens import ROOT,DATA,TEXTURES,append,write_json,digest_ids
from restructure_screen_catalog import restructure

def png(image):
    output=io.BytesIO();image.save(output,format='PNG',optimize=True);return output.getvalue()
def frames_from_compact(root):
    base=Image.open(str(root)+'_static.png').convert('RGB');data=Path(str(root)+'_animated.anim').read_bytes();w,h,n,length=struct.unpack('>HHHI',data[5:15]);assert data[:5]==b'VLTA\x02' and base.size==(w,h) and n==6
    delta=zlib.decompress(data[15:]);assert len(delta)==length==w*h*3*5
    rgb=base.tobytes();frames=[base.convert('RGBA')]
    for i in range(5):
        rgb=bytes((a+b)&255 for a,b in zip(rgb,delta[i*w*h*3:(i+1)*w*h*3]));frames.append(Image.frombytes('RGB',(w,h),rgb).convert('RGBA'))
    return frames

def master_frames(folder,height,slug):
    result=[]
    for i in range(6):
        path=folder/'masters'/height/slug/f'frame_{i:02d}.png'
        try:frame=Image.open(path).convert('RGBA')
        except OSError:
            layers=folder/'layers'/height/slug
            base=Image.open(layers/'base_art.png').convert('RGBA')
            # The archive documents this exact authored compositing recipe.
            for other in range(6):
                composed=Image.alpha_composite(base,Image.open(layers/f'overlay_{other:02d}.png').convert('RGBA'))
                if other==i:frame=composed;continue
                intact=Image.open(folder/'masters'/height/slug/f'frame_{other:02d}.png').convert('RGBA')
                assert composed.tobytes()==intact.tobytes(),'Layer recipe differs from intact masters'
            print('Recovered authored master from matching layers:',height,slug,i)
        result.append(frame)
    return result

def install_display(catalog,blocks,inputs,assets,audit,identifier,subject,style,height,frames,large=False,master_hashes=None):
    w,h=frames[0].size;assert len(frames)==6 and w in (128,256,512) and h==w//(2 if height=='half' else 1)
    hashes=[hashlib.sha256(f.tobytes()).hexdigest() for f in frames];assert len(set(hashes))==6
    for f in frames:assert f.size==(w,h) and f.mode=='RGBA' and f.getchannel('A').getextrema()==(255,255)
    stem=('console_inputs/' if height=='half' else '')+identifier;rgb=[f.convert('RGB').tobytes() for f in frames]
    delta=b''.join(bytes((b-a)&255 for a,b in zip(rgb[i-1],rgb[i])) for i in range(1,6))
    assets[TEXTURES/(stem+'_static.png')]=png(frames[0]);assets[TEXTURES/(stem+'_animated.anim')]=b'VLTA\x02'+struct.pack('>HHHI',w,h,6,len(delta))+zlib.compress(delta,9)
    if height=='half':
        assets[TEXTURES/(stem+'_off.png')]=png(Image.new('RGBA',(w,h),(0,0,0,255)));append(inputs,dict(id=identifier,animated=True,frames=6))
    else:append(blocks,dict(id=identifier,type='display',**{'class':'BlockDisplaySequenced'},item=False,framed=False,programmable_only=True))
    entry=dict(id='screen_'+identifier,label=subject+f' ({style}/{height.title()})'+(' Large' if large else ''),category='Screens',menuGroup=style,source=stem+'_static',frames=6)
    if height=='half':entry['rectangular']=True
    if large:entry.update(large=True,pixelWidth=w)
    append(catalog,entry);audit['entries'].append(dict(id=entry['id'],label=entry['label'],source=entry['source'],style=style,height=height,width=w,frameHashes=hashes,**({'masterFrameHashes':master_hashes} if master_hashes else {})))

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('game',type=Path);parser.add_argument('masters',type=Path);parser.add_argument('cool',type=Path);parser.add_argument('gold',type=Path);args=parser.parse_args()
    catalog=json.loads((DATA/'unified_textures.json').read_text());blocks=json.loads((DATA/'blocks.json').read_text());inputs=json.loads((DATA/'input_surfaces.json').read_text());audit=dict(entries=[]);assets={}
    for entries,key in [(catalog,'Catalog'),(blocks,'Block'),(inputs,'Input')]:
        prior=[e for e in entries if not re.match(r'(screen_)?(?:expansion27_|large27_)',e['id'])]
        audit['previous'+key+'Count']=len(prior);audit['previous'+key+'IdsSha256']=digest_ids(prior)
    from functools import partial
    install=partial(install_display,catalog,blocks,inputs,assets,audit)
    with ZipFile(args.game) as game,ZipFile(args.masters) as masters:
        manifest=next(n for n in game.namelist() if n.endswith('/manifest.json'));prefix=manifest.removesuffix('manifest.json');info=json.loads(game.read(manifest));assert len(info)==34
        mp=next(n for n in masters.namelist() if n.endswith('/manifest.json')).removesuffix('manifest.json')
        assert json.loads(masters.read(mp+'manifest.json'))==info
        for e in info:
            slug,height=e['slug'],e['layout'];size=(128,64 if height=='half' else 128);assert tuple(e['frame_size'])==size and e['frame_count']==6 and e['frame_duration_ms']==200
            frames=[Image.open(io.BytesIO(game.read(prefix+f'game/{height}/frames/{slug}/frame_{i:02d}.png'))).convert('RGBA') for i in range(6)]
            for frame in frames:assert frame.size==size
            strip=Image.open(io.BytesIO(game.read(prefix+f'game/{height}/animated/{slug}.png'))).convert('RGBA');assert strip.size==(128,size[1]*6)
            for i,frame in enumerate(frames):assert strip.crop((0,i*size[1],128,(i+1)*size[1])).tobytes()==frame.tobytes()
            assert Image.open(io.BytesIO(game.read(prefix+f'game/{height}/static/{slug}.png'))).convert('RGBA').tobytes()==frames[0].tobytes()
            assert json.loads(game.read(prefix+f'game/{height}/animated/{slug}.png.mcmeta'))['animation']==dict(width=128,height=size[1],frametime=4,interpolate=False,frames=list(range(6)))
            subject=re.sub(r'^(Operations|Engineering|Bridge|Extra) ','',e['name'])
            install('expansion27_gold_'+height+'_'+slug,subject,'Gold',height,frames)
            if slug=='operations_city_transit':
                original=[Image.open(io.BytesIO(masters.read(mp+f'masters/{height}/{slug}/frame_{i:02d}.png'))).convert('RGBA') for i in range(6)]
                size=(512,256 if height=='half' else 512);assert all(f.size==(1024,size[1]*2) for f in original)
                install('large27_gold_'+height+'_city_transit',subject,'Gold',height,[f.resize(size,Image.Resampling.LANCZOS) for f in original],True,[hashlib.sha256(f.tobytes()).hexdigest() for f in original])
    for style,folder in [('Classic',None),('Cool',args.cool),('Gold',args.gold)]:
        for height in ('full','half'):
            if folder:
                original=master_frames(folder,height,'extra_viewscreen');size=(512,256 if height=='half' else 512);assert all(f.size==(1024,size[1]*2) for f in original);frames=[f.resize(size,Image.Resampling.LANCZOS) for f in original]
            else:
                source=ROOT/'texture-packs/original/assets/vandorlabs/textures/blocks'/('console_inputs' if height=='half' else '')/'extra_bare_viewscreen';frames=frames_from_compact(source);original=frames;assert frames[0].size==(256,128 if height=='half' else 256)
            install('large27_'+style.lower()+'_'+height+'_viewscreen','Viewscreen',style,height,frames,True,[hashlib.sha256(f.tobytes()).hexdigest() for f in original])
    assert len(audit['entries'])==42 and len({e['id'] for e in catalog})==len(catalog)
    language=DATA.parent/'lang/en_us.lang';catalog,blocks,labels=restructure(catalog,blocks,language.read_text())
    for path,content in assets.items():path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(content)
    for name,value in [('unified_textures',catalog),('blocks',blocks),('input_surfaces',inputs)]:write_json(DATA/(name+'.json'),value)
    language.write_text(labels);write_json(ROOT/'tools/display_expansion_sources.json',audit)
    print('Imported 34 Gold expansion animations and eight native Large layouts; 252 frames; animation bytes:',sum(len(v) for p,v in assets.items() if p.suffix=='.anim'))
if __name__=='__main__':main()
