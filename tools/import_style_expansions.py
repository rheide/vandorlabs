"""Append matching Classic/Cool expansion animations and 512px City Transit alternatives."""
import argparse,hashlib,io,json
from pathlib import Path
from zipfile import ZipFile
from PIL import Image
from import_display_expansion import ROOT,DATA,TEXTURES,install_display,write_json,digest_ids,restructure

SLUGS={'power_grid_status':'engineering_power_grid','transit_city_map':'operations_city_transit','ship_deck_plan':'operations_deck_plan'}
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('classic',type=Path);p.add_argument('cool',type=Path);args=p.parse_args()
    catalog=json.loads((DATA/'unified_textures.json').read_text());blocks=json.loads((DATA/'blocks.json').read_text());inputs=json.loads((DATA/'input_surfaces.json').read_text());byid={e['id']:e for e in catalog};assets={}
    audit_path=ROOT/'tools/style_expansion_sources.json';audit={k:v for k,v in json.loads(audit_path.read_text()).items() if k!='entries'} if audit_path.exists() else {}
    for entries,key in [(catalog,'Catalog'),(blocks,'Block'),(inputs,'Input')]:
        if 'previous'+key+'Count' not in audit:audit['previous'+key+'Count']=len(entries);audit['previous'+key+'IdsSha256']=digest_ids(entries)
    audit['entries']=[]
    for style,archive in [('classic',args.classic),('cool',args.cool)]:
        with ZipFile(archive) as bundle:
            manifest=next(n for n in bundle.namelist() if n.endswith('/manifest.json'));prefix=manifest.removesuffix('manifest.json');info=json.loads(bundle.read(manifest));assert info['styles']==[style] and info['screen_subjects']==17 and info['frames_per_animation']==6
            for screen in info['screens']:
                slug=screen['slug'];goldslug=SLUGS.get(slug,slug);gold=byid['screen_expansion27_gold_full_'+goldslug];subject=gold['label'].removesuffix(' (Gold/Full)')
                for height in ('full','half'):
                    layout=screen['styles'][style][height];size=(128,64 if height=='half' else 128)
                    frames=[Image.open(io.BytesIO(bundle.read(prefix+f'{style}/frames_128/{height}/{slug}/frame_{i:02d}.png'))).convert('RGBA') for i in range(6)]
                    assert all(f.size==size for f in frames) and [hashlib.sha256(f.tobytes()).hexdigest() for f in frames]==layout['frame_sha256']
                    static=Image.open(io.BytesIO(bundle.read(prefix+layout['static_png']))).convert('RGBA');strip=Image.open(io.BytesIO(bundle.read(prefix+layout['strip_png']))).convert('RGBA');assert static.tobytes()==frames[0].tobytes() and strip.size==(128,size[1]*6)
                    for i,f in enumerate(frames):assert strip.crop((0,i*size[1],128,(i+1)*size[1])).tobytes()==f.tobytes()
                    assert json.loads(bundle.read(prefix+layout['metadata']))['animation']==dict(width=128,height=size[1],frametime=5,interpolate=False,frames=list(range(6)))
                    install_display(catalog,blocks,inputs,assets,audit,'expansion27_'+style+'_'+height+'_'+slug,subject,style.title(),height,frames)
                    if slug=='transit_city_map':
                        masters=[Image.open(io.BytesIO(bundle.read(prefix+f'{style}/masters/{height}/{slug}/frame_{i:02d}.png'))).convert('RGBA') for i in range(6)];size=(512,256 if height=='half' else 512);assert all(f.size==(1024,size[1]*2) for f in masters)
                        install_display(catalog,blocks,inputs,assets,audit,'large27_'+style+'_'+height+'_city_transit',subject,style.title(),height,[f.resize(size,Image.Resampling.LANCZOS) for f in masters],True,[hashlib.sha256(f.tobytes()).hexdigest() for f in masters])
    assert len(audit['entries'])==72
    language=DATA.parent/'lang/en_us.lang';catalog,blocks,labels=restructure(catalog,blocks,language.read_text())
    for path,content in assets.items():path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(content)
    for name,value in [('unified_textures',catalog),('blocks',blocks),('input_surfaces',inputs)]:write_json(DATA/(name+'.json'),value)
    language.write_text(labels);write_json(audit_path,audit)
    print('Imported 68 Classic/Cool expansion animations and four 512px City Transit alternatives, 432 native frames; names match Gold')
if __name__=='__main__':main()
