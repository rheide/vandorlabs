"""Replace five existing Classic Half animations without changing saved catalog identities."""
import argparse,hashlib,io,json
from zipfile import ZipFile
from PIL import Image
from import_display_expansion import DATA,ROOT,TEXTURES,install_display,write_json

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('archive');args=p.parse_args()
    catalog=json.loads((DATA/'unified_textures.json').read_text());assets={};audit={'entries':[]}
    with ZipFile(args.archive) as z:
        assert z.testzip() is None
        for path in z.namelist():
            if path.lower().endswith('.png'):
                image=Image.open(io.BytesIO(z.read(path)));image.verify();Image.open(io.BytesIO(z.read(path))).load()
        manifest=next(n for n in z.namelist() if n.endswith('/manifest.json'));prefix=manifest.removesuffix('manifest.json')
        for e in json.loads(z.read(manifest)):
            label=e['name']+' (Classic/Half)';matches=[c for c in catalog if c['label']==label and not c.get('hidden')];assert len(matches)==1,label;entry=matches[0]
            stem=entry['source'].removeprefix('console_inputs/').removesuffix('_static');slug=e['slug'];frames=[Image.open(io.BytesIO(z.read(prefix+f'game/half/frames/{slug}/frame_{i:02d}.png'))).convert('RGBA') for i in range(6)]
            strip=Image.open(io.BytesIO(z.read(prefix+f'game/half/animated/{slug}.png'))).convert('RGBA');assert strip.size==(128,384)
            for i,f in enumerate(frames):assert f.tobytes()==strip.crop((0,i*64,128,(i+1)*64)).tobytes()
            assert Image.open(io.BytesIO(z.read(prefix+f'game/half/static/{slug}.png'))).convert('RGBA').tobytes()==frames[0].tobytes()
            assert json.loads(z.read(prefix+f'game/half/animated/{slug}.png.mcmeta'))['animation']==dict(width=128,height=64,frametime=4,interpolate=False,frames=list(range(6)))
            install_display([],[],[],assets,audit,stem,e['name'],'Classic','half',frames)
            audit['entries'][-1]['id']=entry['id']
    assert len(audit['entries'])==5
    for path,data in assets.items():path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
    write_json(ROOT/'tools/classic_half_remake_sources.json',audit)
    print('Classic Half remakes: five existing selections replaced, 30 native frames; every supplied PNG decoded cleanly')
if __name__=='__main__':main()
