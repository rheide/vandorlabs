"""Check appended expansion/Large choices and every native animation pixel in the final JAR."""
import argparse,hashlib,io,json,struct,zlib
from zipfile import ZipFile
from PIL import Image
from import_cool_screens import ROOT,DATA,TEXTURES,digest_ids
from block_catalog_compatibility import historical_blocks

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--jar');args=p.parse_args()
    catalog=json.loads((DATA/'unified_textures.json').read_text());blocks=json.loads((DATA/'blocks.json').read_text());inputs=json.loads((DATA/'input_surfaces.json').read_text());entries={e['id']:e for e in catalog}
    audits=[('display_expansion_sources',42,{128:34,256:2,512:6}),('style_expansion_sources',72,{128:68,256:0,512:4}),('door_display_sources',6,{128:6}),('classic_half_remake_sources',5,{128:5})]
    jar=ZipFile(args.jar) if args.jar else None;aliases=json.loads(jar.read('assets/vandorlabs/data/texture_aliases.json')) if jar else {}
    def read(relative):return jar.read('assets/vandorlabs/'+aliases.get('textures/blocks/'+relative,'textures/blocks/'+relative)) if jar else (TEXTURES/relative).read_bytes()
    try:
        for name,count,widths in audits:
            audit=json.loads((ROOT/'tools'/ (name+'.json')).read_text());assert len(audit['entries'])==count
            for values,key in [(catalog,'Catalog'),(historical_blocks(blocks),'Block'),(inputs,'Input')]:
                if 'previous'+key+'Count' in audit:assert digest_ids(values[:audit['previous'+key+'Count']])==audit['previous'+key+'IdsSha256']
            for width,number in widths.items():assert sum(e['width']==width for e in audit['entries'])==number
            for expected in audit['entries']:
                entry=entries[expected['id']];assert entry['label']==expected['label'].replace(' Bridge (',' (') and entry['menuGroup']==expected['style'] and not entry.get('hidden')
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
                if jar:assert json.loads(jar.read('assets/vandorlabs/data/pbr_materials.json'))['maps']['textures/blocks/'+entry['source']+'.png']['profile']=='display',entry['id']
        if jar:assert json.loads(jar.read('assets/vandorlabs/data/unified_textures.json'))==catalog
    finally:
        if jar:jar.close()
    print('Display expansions PASS: 113 standard animations across all three styles, 12 Large layouts; 750 exact native frames at 128/256/512px; saved prefixes retained'+(' in exact JAR' if args.jar else ''))
if __name__=='__main__':main()
