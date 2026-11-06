#!/usr/bin/env python3
"""Project the source screen/input catalogs and legacy artwork policy into native data."""
import json,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
OUT=Path(__file__).resolve().parent/'build/generated/screen-resources/assets/vandorlabs/data/screen_catalog.json'
def bare(value):
 value=value.replace('_framed_','_bare_').replace('/framed_','/bare_')
 return 'bare_'+value[7:] if value.startswith('framed_') else value
def framed(value):return '_framed_' in value or '/framed_' in value or value.startswith('framed_')
def key(value):
 for marker in ['_bare_','_framed_']:
  if marker in value:return value.replace(marker,'_')
 for marker in ['bare_','framed_']:
  if value.startswith(marker):return value[len(marker):]
 return value
def main():
 blocks=json.loads((ROOT/'generated-resources/assets/vandorlabs/data/blocks.json').read_text())
 inputs=json.loads((ROOT/'generated-resources/assets/vandorlabs/data/input_surfaces.json').read_text())
 extra=json.loads((ROOT/'generated-resources/assets/vandorlabs/data/unified_textures.json').read_text())
 base=re.findall(r'new Finish\("([^"]+)", "([^"]+)"\)',(ROOT/'src/portable/java/com/vandorlabs/tiles/HousingFinishCatalog.java').read_text())
 if not base:raise ValueError('Missing source housing catalog')
 entries=[dict(id=i,source=s) for i,s in base]+extra;by_id={e['id']:i for i,e in enumerate(entries)};sources={}
 for i,(name,source) in enumerate(base):sources.setdefault(name,i);sources.setdefault(source,i)
 for i,e in enumerate(extra,len(base)):sources.setdefault(e['source'],i)
 redirects={};screen_aliases={};input_aliases={}
 for i,e in enumerate(extra,len(base)):
  if 'alias' in e:
   target=by_id[e['alias']];redirects[i]=target
   if e.get('category')=='Screens':
    old=re.sub('_static$','',re.sub('^console_inputs/','',e['source']));new=re.sub('_static$','',re.sub('^console_inputs/','',entries[target]['source']))
    aliases=input_aliases if e['source'].startswith('console_inputs/') else screen_aliases
    if old in aliases and aliases[old]!=new:raise ValueError('Conflicting source screen alias '+old)
    aliases[old]=new
  if e.get('detail',0)>0:
   for target,item in enumerate(extra,len(base)):
    if item.get('detail')==0 and (e.get('design') is not None and e.get('design')==item.get('design') or e.get('textureFamily') is not None and e.get('textureFamily')==item.get('textureFamily')):redirects[i]=target;break
 screens=sorted(e['id'] for e in blocks if e.get('type')=='display' and not e.get('retired',False));old_framed=sorted(e['id'] for e in blocks if e['id'] in screens and e.get('framed',False))
 b={};f={}
 for name in screens:
  if '_bare_' in name or name.startswith('bare_'):b[key(name)]=name
  elif '_framed_' in name or name.startswith('framed_'):f[key(name)]=name
  else:b[name]=name;f[name]=name
 language={line.split('=',1)[0]:line.split('=',1)[1] for line in (ROOT/'generated-resources/assets/vandorlabs/lang/en_us.lang').read_text().splitlines() if '=' in line and not line.startswith('#')}
 options=[];pairs={}
 for name in sorted(b.keys()|f.keys()):
  left=b.get(name,f.get(name));right=f.get(name,b.get(name));label=language.get('tile.vandorlabs.'+left+'.name','tile.vandorlabs.'+left+'.name')
  label=re.sub(r'\b(Bare|Framed) ','',label,count=1).replace('Control Panel','Panel').replace('Communications','Comms').replace('Engineering','Eng.');label=re.sub(' Left$',' L',label);label=re.sub(' Right$',' R',label)
  options.append(dict(key=name,bare=left,framed=right,label=label))
  if left!=right:pairs[right]=left
 choices=[]
 for i,e in enumerate(entries):
  result=i;is_framed=False
  if i>=len(base):
   source=e['source'];is_framed=framed(source);next_source=bare(source)
   if next_source!=source:result=sources.get(next_source,0) or i
  choices.append(dict(framed=is_framed,bare=result))
 family_ids=set(re.findall(r'^\s*[A-Z_]+\("([^"]+)"',(ROOT/'src/portable/java/com/vandorlabs/screen/ScreenFamily.java').read_text(),re.M))
 aliases={a:b for a,b in re.findall(r'if\("([^"]+)"\.equals\(id\)\)return "([^"]+)";',(ROOT/'src/main/java/com/vandorlabs/blocks/ModBlocks.java').read_text()) if b in family_ids}
 data=dict(screens=screens,inputs=inputs,framed=old_framed,pairs=pairs,options=options,choices=choices,screenAliases=screen_aliases,inputAliases=input_aliases,sources={s:redirects.get(i,i) for s,i in sources.items()},defaultHousing=sources['door_inner'],frame=sources['frames/frames-screen-full/frame_bolted_1254'],legacyAliases=aliases)
 OUT.parent.mkdir(parents=True,exist_ok=True);OUT.write_text(json.dumps(data,ensure_ascii=False,separators=(',',':'))+'\n')
 print('Native screen catalog:',len(screens),'screens,',len(inputs),'inputs,',len(choices),'stable choices')
if __name__=='__main__':main()
