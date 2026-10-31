"""Import matching Classic/Cool/Gold Door Controller animations with stable saved choices."""
import argparse,hashlib,io,json
from zipfile import ZipFile
from PIL import Image
from import_display_expansion import ROOT,DATA,TEXTURES,install_display,write_json,digest_ids,restructure

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('classic_cool');p.add_argument('gold');args=p.parse_args()
    catalog=json.loads((DATA/'unified_textures.json').read_text());blocks=json.loads((DATA/'blocks.json').read_text());inputs=json.loads((DATA/'input_surfaces.json').read_text());assets={};path=ROOT/'tools/door_display_sources.json'
    audit={k:v for k,v in json.loads(path.read_text()).items() if k!='entries'} if path.exists() else {}
    for entries,key in [(catalog,'Catalog'),(blocks,'Block'),(inputs,'Input')]:
        if 'previous'+key+'Count' not in audit:audit['previous'+key+'Count']=len(entries);audit['previous'+key+'IdsSha256']=digest_ids(entries)
    audit['entries']=[]
    def add(bundle,prefix,style,height,framepath,staticpath,strippath,metapath,hashes=None):
        size=(128,64 if height=='half' else 128);frames=[Image.open(io.BytesIO(bundle.read(prefix+framepath.format(i=i)))).convert('RGBA') for i in range(6)];assert all(f.size==size for f in frames)
        if hashes:assert [hashlib.sha256(f.tobytes()).hexdigest() for f in frames]==hashes
        static=Image.open(io.BytesIO(bundle.read(prefix+staticpath))).convert('RGBA');strip=Image.open(io.BytesIO(bundle.read(prefix+strippath))).convert('RGBA');assert static.tobytes()==frames[0].tobytes() and strip.size==(128,size[1]*6)
        for i,f in enumerate(frames):assert strip.crop((0,i*size[1],128,(i+1)*size[1])).tobytes()==f.tobytes()
        assert json.loads(bundle.read(prefix+metapath))['animation']==dict(width=128,height=size[1],frametime=4 if style=='gold' else 5,interpolate=False,frames=list(range(6)))
        install_display(catalog,blocks,inputs,assets,audit,'door27_'+style+'_'+height+'_controller','Door Controller',style.title(),height,frames)
    with ZipFile(args.classic_cool) as z:
        m=next(n for n in z.namelist() if n.endswith('/manifest.json'));prefix=m.removesuffix('manifest.json');info=json.loads(z.read(m));assert info['independent_layouts']==4 and info['frames_per_animation']==6
        for e in info['assets']:
            style,height=e['style'],e['layout'];add(z,prefix,style,height,f'{style}/frames_128/{height}/door_controller/frame_{{i:02d}}.png',e['static_png'],e['animation_strip'],e['animation_metadata'],e['frame_sha256'])
    with ZipFile(args.gold) as z:
        m=next(n for n in z.namelist() if n.endswith('/door_controller/manifest.json'));prefix=m.removesuffix('manifest.json');info=json.loads(z.read(m));assert len(info)==2
        for e in info:
            height=e['layout'];assert e['frame_count']==6;root=f'game/{height}/';add(z,prefix,'gold',height,root+'frames/door_controller/frame_{i:02d}.png',root+'static/door_controller.png',root+'animated/door_controller.png',root+'animated/door_controller.png.mcmeta')
    assert len(audit['entries'])==6
    language=DATA.parent/'lang/en_us.lang';catalog,blocks,labels=restructure(catalog,blocks,language.read_text())
    for p,b in assets.items():p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b)
    for name,value in [('unified_textures',catalog),('blocks',blocks),('input_surfaces',inputs)]:write_json(DATA/(name+'.json'),value)
    language.write_text(labels);write_json(path,audit);print('Imported six Door Controller layouts / 36 native frames across Classic/Cool/Gold')
if __name__=='__main__':main()
