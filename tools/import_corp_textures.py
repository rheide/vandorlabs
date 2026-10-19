"""Append company materials and light-only ON/OFF pairs without moving saved choices."""
import argparse,json
from pathlib import Path
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
MANIFEST=ROOT/'texture-packs/additional/corp-catalog.json'
CATALOG=ROOT/'generated-resources/assets/vandorlabs/data/unified_textures.json'
TEXTURES=ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks'
COMPANIES=('pinnacle','ashiro','paladyne','vektor','seyr','vandor')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('directory',type=Path);source=parser.parse_args().directory
    entries=[]
    for company in COMPANIES:
        for kind in ('logo','bg','mural'):
            filename=company+('_logo' if company=='ashiro' else '') if kind=='logo' else company+'_'+kind
            entries.append((source/(filename+'.png'),dict(id=f'corp_{company}_{kind}',label=company.title()+' '+{'logo':'Logo','bg':'Background','mural':'Mural'}[kind],category='Corp',source=f'corp/{company}_{kind}',**({'rectangular':True} if kind=='mural' else {}))))
        for state in ('on','off'):
            entry=dict(id=f'corp_{company}_light_{state}',label=company.title()+' '+state.title(),category='Corp/Lights',source=f'corp/lights/{company}_{state}',lightOnly=True)
            if state=='on':entry['unlit']=f'corp/lights/{company}_off'
            else:entry['off']=True
            entries.append((source/'lights'/(company+'_'+state+'.png'),entry))
    catalog=json.loads(CATALOG.read_text());known={e['id']:e for e in catalog}
    for path,entry in entries:
        image=Image.open(path).convert('RGBA')
        if max(image.size)>128:
            size=tuple(round(n*128/max(image.size)) for n in image.size)
            image=image.convert('RGBa').resize(size,Image.Resampling.LANCZOS).convert('RGBA')
        target=TEXTURES/(entry['source']+'.png');target.parent.mkdir(parents=True,exist_ok=True);image.save(target,optimize=True)
        if entry['id'] in known:
            assert known[entry['id']]==entry,'Existing company material metadata changed'
        else:catalog.append(entry)
    MANIFEST.write_text(json.dumps([e for _,e in entries],indent=2)+'\n')
    CATALOG.write_text(json.dumps(catalog,indent=2)+'\n')
    print('Imported 18 Corp materials and six light-only ON/OFF pairs; prior choice order preserved')

if __name__=='__main__':main()
