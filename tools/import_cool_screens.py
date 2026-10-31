"""Import native Cool animations losslessly, retaining existing saved catalog indices."""
import argparse,hashlib,io,json,re,struct,zlib
from pathlib import Path
from zipfile import ZipFile
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/'generated-resources/assets/vandorlabs/data'
TEXTURES=ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks'

def write_json(path,value):path.write_text(json.dumps(value,indent=2)+'\n')
def digest_ids(entries):return hashlib.sha256('\n'.join(e['id'] for e in entries).encode()).hexdigest()
def append(entries,entry):
    previous=next((e for e in entries if e['id']==entry['id']),None)
    if previous is None:entries.append(entry)
    elif previous!=entry:raise ValueError('Conflicting existing entry: '+entry['id'])

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('bridge',type=Path);p.add_argument('screens',type=Path);args=p.parse_args()
    catalog=json.loads((DATA/'unified_textures.json').read_text());blocks=json.loads((DATA/'blocks.json').read_text());inputs=json.loads((DATA/'input_surfaces.json').read_text())
    prior=[e for e in catalog if not e['id'].startswith('screen_cool27_')];original_blocks=[e for e in blocks if not e['id'].startswith('cool27_')];original_inputs=[e for e in inputs if not e['id'].startswith('cool27_')]
    audit=dict(previousCatalogCount=len(prior),previousCatalogIdsSha256=digest_ids(prior),previousBlockCount=len(original_blocks),previousBlockIdsSha256=digest_ids(original_blocks),previousInputCount=len(original_inputs),previousInputIdsSha256=digest_ids(original_inputs),entries=[])
    assets={}
    for archive,is_bridge,count in [(args.bridge,True,6),(args.screens,False,15)]:
        with ZipFile(archive) as bundle:
            manifests=[name for name in bundle.namelist() if name.endswith('/manifest.json')];assert len(manifests)==1
            manifest=manifests[0];prefix=manifest.removesuffix('manifest.json');info=json.loads(bundle.read(manifest));assert len(info['screens'])==count and info['frames_per_animation']==6
            for screen in info['screens']:
                slug=screen['slug'];assert re.fullmatch('[a-z0-9_]+',slug),slug
                subject=re.sub(r'^(analysis|bridge|extra|specialist)_','',slug).replace('_',' ').title()+(' Bridge' if is_bridge else '')
                for height in ('full','half'):
                    size=(128,64 if height=='half' else 128);layout=screen['layouts'][height];assert tuple(layout['frame_dimensions'])==size
                    frames=[]
                    for index in range(6):
                        image=Image.open(io.BytesIO(bundle.read(prefix+f'frames_128/{height}/{slug}/frame_{index:02d}.png'))).convert('RGBA')
                        assert image.size==size and image.getchannel('A').getextrema()==(255,255)
                        assert hashlib.sha256(image.tobytes()).hexdigest()==layout['frame_sha256'][index]
                        frames.append(image)
                    strip=Image.open(io.BytesIO(bundle.read(prefix+layout['animation_strip']))).convert('RGBA');assert strip.size==(128,size[1]*6)
                    for index,frame in enumerate(frames):assert strip.crop((0,index*size[1],128,(index+1)*size[1])).tobytes()==frame.tobytes()
                    static=bundle.read(prefix+layout['static_png']);assert Image.open(io.BytesIO(static)).convert('RGBA').tobytes()==frames[0].tobytes()
                    metadata=json.loads(bundle.read(prefix+layout['animation_metadata']))['animation'];assert metadata==dict(width=128,height=size[1],frametime=5,interpolate=False,frames=list(range(6)))
                    assert len({frame.tobytes() for frame in frames})==6
                    identifier=f'cool27_{height}_{slug}';stem=('console_inputs/' if height=='half' else '')+identifier
                    # Existing VLTA RGB deltas preserve every opaque authored pixel.
                    rgb=[frame.convert('RGB').tobytes() for frame in frames]
                    delta=b''.join(bytes((b-a)&255 for a,b in zip(rgb[i-1],rgb[i])) for i in range(1,6))
                    encoded=b'VLTA\x02'+struct.pack('>HHHI',128,size[1],6,len(delta))+zlib.compress(delta,9)
                    assets[TEXTURES/(stem+'_static.png')]=static;assets[TEXTURES/(stem+'_animated.anim')]=encoded
                    if height=='half':
                        off=io.BytesIO();Image.new('RGBA',size,(0,0,0,255)).save(off,format='PNG',optimize=True);assets[TEXTURES/(stem+'_off.png')]=off.getvalue()
                        append(inputs,dict(id=identifier,animated=True,frames=6))
                    else:append(blocks,dict(id=identifier,type='display',**{'class':'BlockDisplaySequenced'},item=False,framed=False,programmable_only=True))
                    entry=dict(id='screen_'+identifier,label=subject+f' (Cool/{height.title()})',category='Screens',menuGroup='Cool',source=stem+'_static',frames=6)
                    if height=='half':entry['rectangular']=True
                    append(catalog,entry)
                    audit['entries'].append(dict(id=entry['id'],source=entry['source'],label=entry['label'],height=height,variant='bridge' if is_bridge else 'expanded',frameDurationMs=info['frame_duration_ms'],frameHashes=layout['frame_sha256']))
    # Validate everything before publishing any changed source files.
    assert len(audit['entries'])==42 and len({e['id'] for e in catalog})==len(catalog)
    for path,content in assets.items():path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(content)
    write_json(DATA/'unified_textures.json',catalog);write_json(DATA/'blocks.json',blocks);write_json(DATA/'input_surfaces.json',inputs)
    write_json(ROOT/'tools/cool_display_sources.json',audit)
    print('Imported 42 Cool animations / 252 native frames; existing catalog prefixes preserved; compact motion bytes:',sum(len(v) for p,v in assets.items() if p.suffix=='.anim'))
if __name__=='__main__':main()
