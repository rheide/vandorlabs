"""Validate saved prefixes and all 252 imported style animation frames, including the JAR."""
import argparse,hashlib,io,json,struct,zlib
from pathlib import Path
from zipfile import ZipFile
from PIL import Image
from block_catalog_compatibility import historical_blocks
ROOT=Path(__file__).resolve().parents[1];DATA=ROOT/'generated-resources/assets/vandorlabs/data';TEXTURES=ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks'
def digest_ids(entries):return hashlib.sha256('\n'.join(e['id'] for e in entries).encode()).hexdigest()
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--jar',type=Path);p.add_argument('--style',choices=('Cool','Gold'),default='Cool');args=p.parse_args();style=args.style;audit=json.loads((ROOT/('tools/'+style.lower()+'_display_sources.json')).read_text())
    catalog=json.loads((DATA/'unified_textures.json').read_text());blocks=json.loads((DATA/'blocks.json').read_text());inputs=json.loads((DATA/'input_surfaces.json').read_text())
    for entries,key in [(catalog,'Catalog'),(historical_blocks(blocks),'Block'),(inputs,'Input')]:assert digest_ids(entries[:audit['previous'+key+'Count']])==audit['previous'+key+'IdsSha256']
    byid={e['id']:e for e in catalog};byblock={e['id']:e for e in blocks};byinput={e['id']:e for e in inputs}
    assert len(audit['entries'])==42 and len({e['id'] for e in audit['entries']})==42
    for height in ('full','half'):
        assert sum(e.get('menuGroup')==style and e['id'].startswith(('screen_company_','screen_'+style.lower()+'27_')) and not e.get('hidden') and e['source'].startswith('console_inputs/')==(height=='half') for e in catalog)==21
        assert sum(e['height']==height and e['variant']=='bridge' for e in audit['entries'])==6
        assert sum(e['height']==height and e['variant']=='expanded' for e in audit['entries'])==15
    jar=ZipFile(args.jar) if args.jar else None
    aliases=json.loads(jar.read('assets/vandorlabs/data/texture_aliases.json')) if jar else {}
    def read(relative):
        if jar:
            path='textures/blocks/'+relative;return jar.read('assets/vandorlabs/'+aliases.get(path,path))
        return (TEXTURES/relative).read_bytes()
    try:
        for expected in audit['entries']:
            entry=byid[expected['id']];assert entry['label']==expected['label'].replace(' Bridge (',' (') and entry['menuGroup']==style and not entry.get('hidden') and entry['frames']==6
            root=entry['source'].removesuffix('_static');identifier=root.removeprefix('console_inputs/');height=64 if expected['height']=='half' else 128
            if height==64:assert byinput[identifier]==dict(id=identifier,animated=True,frames=6)
            else:assert byblock[identifier]['programmable_only'] and not byblock[identifier].get('retired')
            base=Image.open(io.BytesIO(read(root+'_static.png'))).convert('RGBA');assert base.size==(128,height) and base.getchannel('A').getextrema()==(255,255)
            encoded=read(root+'_animated.anim');assert encoded[:5]==b'VLTA\x02' and struct.unpack('>HHHI',encoded[5:15])==(128,height,6,128*height*3*5)
            delta=zlib.decompress(encoded[15:]);assert len(delta)==128*height*3*5
            rgb=base.convert('RGB').tobytes();hashes=[hashlib.sha256(base.tobytes()).hexdigest()]
            for index in range(5):
                rgb=bytes((a+b)&255 for a,b in zip(rgb,delta[index*128*height*3:(index+1)*128*height*3]));frame=Image.frombytes('RGB',(128,height),rgb).convert('RGBA');hashes.append(hashlib.sha256(frame.tobytes()).hexdigest())
            assert hashes==expected['frameHashes'] and len(set(hashes))==6,entry['id']
            if height==64:
                off=Image.open(io.BytesIO(read(root+'_off.png'))).convert('RGBA');assert off.size==(128,64) and off.getextrema()==((0,0),(0,0),(0,0),(255,255))
        if jar:assert json.loads(jar.read('assets/vandorlabs/data/unified_textures.json'))==catalog
    finally:
        if jar:jar.close()
    print(style+' screens PASS: 42 animations, 252 exact authored frames, saved catalog/block/input prefixes retained, 21 retained primary/expanded choices per height, six preferred Bridge designs per height'+(' in exact JAR' if args.jar else ''))
if __name__=='__main__':main()
