"""Omit obsolete artwork only after checking model, catalog, code and Dynmap references."""
from pathlib import Path
import re,json

ROOT=Path(__file__).resolve().parents[1]

def candidate(path):
    return (path.startswith('blocks/dynmap/') and path.endswith('.png') and not path.endswith(('_s.png','_n.png'))
        or path.startswith('blocks/console_inputs/') and path.endswith('_item.png')
        or re.fullmatch(r'blocks/space_doors/(?:low|medium)/(?:double_frame_glass|expansion_glass_tile|glass_overlay|glass_interior|.+_window_mask)\.png',path)
        or re.fullmatch(r'blocks/(?:quiet_.+|border_.+|push_button_(?:lit|unlit)|rocker_switch_(?:lit|unlit))\.png',path))

def unused(files):
    java='\n'.join(p.read_text(errors='replace') for root in ('src/main/java','src/portable/java') for p in (ROOT/root).rglob('*.java'))
    references=java+'\n'+'\n'.join(text for name,text in files.items() if name.endswith(('.json','.mtl')) and name not in ('data/texture_aliases.json','data/pbr_materials.json'))
    # Dedup targets used by any native path remain live even when their shortest
    # physical filename happens to be a generated Dynmap export. Export-to-export
    # aliases and the PBR inventory itself do not establish usage.
    aliases=json.loads(files.get('data/texture_aliases.json','{}'))
    native_targets={target for alias,target in aliases.items() if not alias.startswith('textures/blocks/dynmap/')}
    dynmap='\n'.join(line for name,text in files.items() if name.startswith('dynmap') for line in text.splitlines() if not line.startswith('texture:'))
    used_ids=set(re.findall(r'[\w:./-]+',dynmap))
    for token in list(used_ids):
        while ':' in token:
            token=token.partition(':')[2];used_ids.add(token)
    ids_by_path={}
    for name,text in files.items():
        if not name.startswith('dynmap'):continue
        for line in text.splitlines():
            if not line.startswith('texture:'):continue
            identifier=re.search(r'texture:id=([^,]+)',line);filename=re.search(r'filename=([^,\n]+)',line)
            if identifier and filename:
                path=filename[1].removeprefix('assets/vandorlabs/').removeprefix('textures/')
                ids_by_path.setdefault(path,set()).add(identifier[1])
    result=[]
    for name in files:
        if not name.startswith('textures/'):continue
        path=name[len('textures/'):]
        if not candidate(path) or name in native_targets or path in references or path.removesuffix('.png') in references or (not path.startswith('blocks/dynmap/') and Path(path).stem in references):continue
        if ids_by_path.get(path,set()) & used_ids:continue
        result.append(name)
    return result

def stage(directory):
    # Binary resources need only their names; metadata and native OBJ materials provide roots.
    files={p.relative_to(directory).as_posix():p.read_text() if p.suffix in ('.json','.mtl','.txt') else '' for p in directory.rglob('*') if p.is_file()}
    removed=unused(files);saved=sum((directory/path).stat().st_size for path in removed)
    for path in removed:
        (directory/path).unlink()
        metadata=directory/(path+'.mcmeta')
        if metadata.exists():metadata.unlink()
    for path in directory.glob('dynmap*.txt'):
        lines=path.read_text().splitlines()
        path.write_text('\n'.join(line for line in lines if not (line.startswith('texture:') and any('filename='+name[9:] in line or '/textures/'+name[9:] in line for name in removed)))+'\n')
    print(f'Unused runtime artwork: omitted {len(removed)} images ({saved} bytes); live Dynmap references retained')

def check_jar(path):
    from zipfile import ZipFile
    with ZipFile(path) as jar:
        prefix='assets/vandorlabs/'
        files={name[len(prefix):]:jar.read(name).decode() if name.endswith(('.json','.mtl','.txt')) else '' for name in jar.namelist() if name.startswith(prefix) and not name.endswith('/')}
        assert not unused(files),'unused artwork remains in JAR'
        constructor_models=[name for name in files if name.startswith('models/block/universal_constructor/') and name.endswith(('.obj','.mtl'))]
        assert len(constructor_models)==6 and all(name.count('/')==4 and name.endswith(('/complete.obj','/materials.mtl')) for name in constructor_models),'unused constructor model exports remain in JAR'
        import io,json
        from PIL import Image
        aliases=json.loads(files['data/texture_aliases.json'])
        for tier in ('low','medium'):
            assert f'textures/blocks/programmable_glass/{tier}/clear_mild.png' not in files
            strong=f'textures/blocks/programmable_glass/{tier}/clear_opaque.png'
            target=aliases.get(strong,strong)
            assert target in files, 'Opaque Clear artwork is missing'
            with Image.open(io.BytesIO(jar.read(prefix+target))) as image:
                assert image.convert('RGBA').getchannel('A').getextrema()==(255,255), 'Opaque Clear must retain opaque pixels'
        for name,text in files.items():
            if name.startswith('dynmap'):
                for filename in re.findall(r'filename=([^,\n]+)',text):
                    if filename.startswith('assets/') and not filename.startswith(prefix):continue
                    if filename.startswith(prefix):filename=filename[len(prefix):]
                    elif not filename.startswith('textures/'):filename='textures/'+filename
                    assert filename in files,(name,filename)
    print('PASS: no audited unused artwork or duplicate Mild Clear; Opaque Clear remains opaque; all Dynmap images resolve')

if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--stage',type=Path);parser.add_argument('--check-jar',type=Path);args=parser.parse_args()
    if args.stage:stage(args.stage)
    if args.check_jar:check_jar(args.check_jar)
    if not args.stage and not args.check_jar:parser.error('provide --stage or --check-jar')
