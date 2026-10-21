"""Generate the static docs texture browser and category collages from the in-game catalogs."""
import argparse, hashlib, html, io, json, re
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'docs/texture-browser'

def catalog():
    source = (ROOT/'src/main/java/com/vandorlabs/tiles/ScreenHousingTextures.java').read_text()
    menu = json.loads((ROOT/'src/main/resources/assets/vandorlabs/data/texture_menu.json').read_text())
    entries = [dict(id=identifier, source=texture, **menu.get(identifier, {})) for identifier, texture in re.findall(r'new Finish\("([^"]+)",\s*"([^"]+)"\)', source)]
    entries += json.loads((ROOT/'generated-resources/assets/vandorlabs/data/unified_textures.json').read_text())
    glass = (ROOT/'src/main/java/com/vandorlabs/tiles/GlassTextures.java').read_text()
    arrays = [re.findall(r'"([^"]+)"', re.search(r'String\[\] '+name+r'=\{([^}]+)', glass)[1]) for name in ('IDS','LABELS')]
    entries += [dict(id='glass_'+identifier, source='programmable_glass/low/'+identifier, label=label, category='Glass') for identifier,label in zip(*arrays)]
    language = (ROOT/'generated-resources/assets/vandorlabs/lang/en_us.lang').read_text(encoding='utf-8')
    names = dict(line.split('=',1) for line in language.splitlines() if '=' in line and not line.startswith('#'))
    result = []
    for entry in entries:
        if entry.get('hidden') or entry.get('alias') or entry.get('detail',0)>0: continue
        entry = dict(entry)
        entry['label'] = entry.get('label', names.get('tile.vandorlabs.'+entry['id']+'.name', entry['id'].replace('_',' ').title()))
        if entry.get('design') is not None or entry.get('detail') is not None:entry['label']=re.sub(r' (Small|Medium|Large)$','',entry['label'])
        entry.setdefault('category','Materials')
        result.append(entry)
    return sorted(result,key=lambda e:(e['category'].casefold(),e['category'],e['label'].casefold(),e['id']))

def image(source, entry):
    relative=Path('assets/vandorlabs/textures/blocks')/(source+'.png')
    for directory in ('texture-packs/additional','texture-packs/default','generated-resources','src/main/resources'):
        path=ROOT/directory/relative
        if path.exists():break
    else:
        if source.endswith("_small"):return image(source[:-6],entry)
        raise FileNotFoundError(source)
    art=Image.open(path).convert('RGBA')
    metadata=path.with_suffix('.png.mcmeta')
    if metadata.exists() and 'animation' in json.loads(metadata.read_text()):
        animation=json.loads(metadata.read_text())['animation'];width=animation.get('width',art.width);height=animation.get('height',width)
        frames=animation.get('frames',[0]);frame=frames[0];frame=frame.get('index',0) if isinstance(frame,dict) else frame
        columns=art.width//width;left=frame%columns*width;top=frame//columns*height;art=art.crop((left,top,left+width,top+height))
    if entry.get("crop"):
        x,y=art.width//16,art.height//32;art=art.crop((x,y,art.width,art.height-y))
    art.thumbnail((128,128),Image.Resampling.LANCZOS)
    return art

def png(art):
    stream=io.BytesIO();art.save(stream,format='PNG',optimize=True);return stream.getvalue()

def generate():
    files={};entries=catalog();artwork={}
    for entry in entries:
        art=image(entry['source'],entry);data=png(art);digest=hashlib.sha256(data).hexdigest()[:20];filename='thumbs/'+digest+'.png';files[filename]=data;entry['thumbnail']=filename;entry['size']=list(art.size);artwork[entry['id']]=art
    categories=sorted({parent for entry in entries for parent in ['/'.join(entry['category'].split('/')[:level]) for level in range(1,len(entry['category'].split('/'))+1)]},key=lambda p:(p.casefold(),p))
    collages={};font=ImageFont.load_default(size=14)
    for category in categories:
        members=[e for e in entries if e['category']==category or e['category'].startswith(category+'/')];columns=min(8,len(members));rows=(len(members)+columns-1)//columns
        collage=Image.new('RGB',(columns*152,42+rows*170),(25,35,44));draw=ImageDraw.Draw(collage);draw.text((12,12),category+'  /  '+str(len(members))+' textures',font=font,fill=(226,238,245))
        for index,entry in enumerate(members):
            x=index%columns*152;y=42+index//columns*170
            for yy in range(0,128,8):
                for xx in range(0,128,8):draw.rectangle((x+12+xx,y+yy,x+19+xx,y+yy+7),fill=(58,66,74) if (xx+yy)//8%2 else (42,50,58))
            art=artwork[entry['id']];collage.paste(art,(x+12+(128-art.width)//2,y+(128-art.height)//2),art)
            label=entry['label'];lines=[]
            while label:
                length=len(label)
                while length>1 and draw.textlength(label[:length],font=font)>140:length-=1
                if length<len(label) and ' ' in label[:length]:length=label.rfind(' ',0,length)
                lines.append(label[:length]);label=label[length:].lstrip()
            for line,text in enumerate(lines[:2]):draw.text((x+6,y+132+line*16),text,font=font,fill=(226,238,245))
        filename='collages/'+re.sub(r'[^a-z0-9]+','-',category.lower()).strip('-')+'.png';files[filename]=png(collage);collages[category]=filename
    payload=json.dumps(dict(entries=entries,collages=collages),ensure_ascii=False,separators=(',',':')).replace('</','<\/')
    template=(ROOT/'tools/texture_browser.html').read_text(encoding='utf-8');files['index.html']=template.replace('/*CATALOG*/{}',payload).encode('utf-8')
    hosting=ROOT/'tools/texture_browser_hosting.json'
    browser_url=json.loads(hosting.read_text(encoding='utf-8'))['url'] if hosting.exists() else 'texture-browser/index.html'
    overview=['# Texture overview','', f'[Open the interactive texture browser]({browser_url}). Search by name or category, expand nested categories, and click a thumbnail for a larger preview. The [local browser](texture-browser/index.html) also works without a server.','', 'These collages cover the built-in texture picker, including both light states and the native glass finishes. Hidden compatibility aliases and duplicate resolution tiers are omitted. Custom textures from local installations are not included.','']
    collage_html=['<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Vandor Labs texture collages</title><style>'+template.split('<style>')[1].split('</style>')[0]+'</style><header><h1>Vandor Labs / Texture collages</h1><a href="index.html">Interactive texture browser</a></header><main>']
    for category in categories:
        if '/' not in category:
            overview.extend(['## '+category,'',f'![{category} textures](texture-browser/{collages[category]})',''])
            collage_html.append('<section><h2>'+html.escape(category)+'</h2><a href="'+collages[category]+'"><img style="width:100%;height:auto" loading="lazy" alt="'+html.escape(category)+' textures" src="'+collages[category]+'"></a></section>')
    files['overview.html']=(''.join(collage_html)+'</main></html>').encode('utf-8')
    files['../texture-overview.md']=('\n'.join(overview)).encode('utf-8')
    return files,len(entries),len(categories)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();files,count,categories=generate()
    stale=[]
    for relative,data in files.items():
        path=DEST/relative
        if args.check:
            if not path.exists() or path.read_bytes()!=data:stale.append(relative)
        else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
    expected={str((DEST/relative).resolve()) for relative in files}
    if DEST.exists():
        for path in DEST.rglob('*'):
            if path.is_file() and str(path.resolve()) not in expected:
                if args.check:stale.append(str(path.relative_to(DEST)))
                else:path.unlink()
    if stale:raise SystemExit('Texture browser is stale; regenerate with tools/generate_texture_browser.py: '+', '.join(stale[:8]))
    print(('Verified' if args.check else 'Generated')+f' {count} textures in {categories} nested categories and category collages')
if __name__=='__main__':main()
