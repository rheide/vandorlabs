"""Keep only artwork belonging to listed Displays/Controls, including their runtime states."""
import argparse,json,re
from pathlib import Path
from zipfile import ZipFile
from generate_texture_browser import catalog,ROOT

def inventory():
    data=ROOT/'generated-resources/assets/vandorlabs/data'
    entries=json.loads((data/'unified_textures.json').read_text())
    roots={e['source'].removesuffix('_static') for e in entries if e.get('category')=='Screens' or e.get('category','').startswith('Controls')}
    roots.update(e['id'] for e in json.loads((data/'blocks.json').read_text()) if e.get('type')=='display')
    roots.update('console_inputs/'+e['id'] for e in json.loads((data/'input_surfaces.json').read_text()))
    unified={'unified/'+e['id'] for e in entries if e.get('category')=='Screens' or e.get('category','').startswith('Controls')}
    allowed=set()
    for e in catalog():
        if not e['category'].startswith(('Displays','Controls')):continue
        source=e['source'];allowed.add(source+'.png')
        if e.get('rectangular'):allowed.add('unified/'+e['id']+'.png')
        if source.endswith('_static'):
            root=source.removesuffix('_static')
            allowed.update(root+suffix for suffix in ('_animated.png','_animated.anim','_animated.png.mcmeta'))
            if source.startswith('console_inputs/'):
                allowed.update((root+'_off.png',root+'_item.png'))
    return roots|unified,{'textures/blocks/'+p for p in allowed}

def unused(names,aliases):
    roots,allowed=inventory();allowed={aliases.get(p,p) for p in allowed};result=[]
    for path in names:
        if not path.startswith('textures/blocks/'):continue
        relative=path.removeprefix('textures/blocks/')
        # Specular/normal maps are generated from retained diffuse pixels later.
        if relative.endswith(('_s.png','_n.png')):continue
        root=re.sub(r'\.(?:png|anim)(?:\.mcmeta)?$','',relative)
        native=root;root=re.sub(r'_(?:static|animated|off|item)$','',root)
        if (root in roots or native in roots) and path not in allowed:result.append(path)
    return result

def stage(directory):
    names=[p.relative_to(directory).as_posix() for p in directory.rglob('*') if p.is_file()]
    removed=unused(names,{});size=sum((directory/p).stat().st_size for p in removed)
    for path in removed:(directory/path).unlink()
    print(f'Unlisted screen artwork: omitted {len(removed)} resources ({size} bytes); listed animations and Off states retained')

def check_jar(path):
    with ZipFile(path) as jar:
        prefix='assets/vandorlabs/';names=[n.removeprefix(prefix) for n in jar.namelist() if n.startswith(prefix)]
        aliases=json.loads(jar.read(prefix+'data/texture_aliases.json'));orphans=unused(names,aliases)
        assert not orphans,'Unlisted screen artwork remains: '+str(orphans)
    print('Screen resource menu audit PASS: every packaged screen/control diffuse or animation belongs to a listed texture; shared aliases retain no discarded PNGs')

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--stage',type=Path);p.add_argument('--check-jar',type=Path);args=p.parse_args()
    if args.stage:stage(args.stage)
    if args.check_jar:check_jar(args.check_jar)
