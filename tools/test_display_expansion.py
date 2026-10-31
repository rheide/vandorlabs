"""Check appended expansion choices and every native animation pixel in the final JAR."""
import argparse,hashlib,io,json,struct,zlib
from pathlib import Path
from zipfile import ZipFile
from PIL import Image
from import_cool_screens import ROOT,DATA,TEXTURES,digest_ids

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--jar',type=Path);args=p.parse_args();audit=json.loads((ROOT/'tools/display_expansion_sources.json').read_text())
    catalog=json.loads((DATA/'unified_textures.json').read_text());blocks=json.loads((DATA/'blocks.json').read_text());inputs=json.loads((DATA/'input_surfaces.json').read_text())
    for values,key in [(catalog,'Catalog'),(blocks,'Block'),(inputs,'Input')]:assert digest_ids(values[:audit['previous'+key+'Count']])==audit['previous'+key+'IdsSha256']
    entries={e['id']:e for e in catalog};assert len(audit['entries'])==42
    assert sum(e['width']==128 for e in audit['entries'])==34 and sum(e['width']==256 for e in audit['entries'])==2 and sum(e['width']==512 for e in audit['entries'])==6
    jar=ZipFile(args.jar) if args.jar else None;aliases=json.loads(jar.read('assets/vandorlabs/data/texture_aliases.json')) if jar else {}
    def read(relative):
        return jar.read('assets/vandorlabs/'+aliases.get('textures/blocks/'+relative,'textures/blocks/'+relative)) if jar else (TEXTURES/relative).read_bytes()
    try:
        for expected in audit['entries']:
            entry=entries[expected['id']];assert entry['label']==expected['label'] and entry['menuGroup']==expected['style'] and not entry.get('hidden')
            w=expected['width'];h=w//(2 if expected['height']=='half' else 1);root=entry['source'].removesuffix('_static');base=Image.open(io.BytesIO(read(root+'_static.png'))).convert('RGBA');assert base.size==(w,h) and base.getchannel('A').getextrema()==(255,255)
            data=read(root+'_animated.anim');assert data[:5]==b'VLTA\x02' and struct.unpack('>HHHI',data[5:15])==(w,h,6,w*h*3*5)
            delta=zlib.decompress(data[15:]);assert len(delta)==w*h*3*5;rgb=base.convert('RGB').tobytes();hashes=[hashlib.sha256(base.tobytes()).hexdigest()]
            for i in range(5):
                rgb=bytes((a+b)&255 for a,b in zip(rgb,delta[i*w*h*3:(i+1)*w*h*3]));hashes.append(hashlib.sha256(Image.frombytes('RGB',(w,h),rgb).convert('RGBA').tobytes()).hexdigest())
            assert hashes==expected['frameHashes'] and len(set(hashes))==6,entry['id']
            identifier=root.removeprefix('console_inputs/')
            if expected['height']=='half':
                assert dict(id=identifier,animated=True,frames=6) in inputs;off=Image.open(io.BytesIO(read(root+'_off.png'))).convert('RGBA');assert off.size==(w,h) and off.getextrema()==((0,0),(0,0),(0,0),(255,255))
            else:assert next(e for e in blocks if e['id']==identifier)['programmable_only']
            if w>128:assert entry['large'] and entry['pixelWidth']==w and entry['label'].endswith(' Large')
        if jar:
            assert json.loads(jar.read('assets/vandorlabs/data/unified_textures.json'))==catalog
            maps=json.loads(jar.read('assets/vandorlabs/data/pbr_materials.json'))['maps']
            for e in audit['entries']:assert maps['textures/blocks/'+e['source']+'.png']['profile']=='display',e['id']
    finally:
        if jar:jar.close()
    print('Display expansion PASS: 34 standard Gold animations, six Large Viewscreen and two Large City Transit layouts; 252 exact native frames at 128/256/512px; saved prefixes retained'+(' in exact JAR' if args.jar else ''))
if __name__=='__main__':main()
