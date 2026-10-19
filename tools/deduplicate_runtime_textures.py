"""Store identical PNGs once, retaining resource paths through the shared-texture pack."""
import argparse,hashlib,json,re
from collections import defaultdict
from pathlib import Path

def stage(directory):
    groups=defaultdict(list)
    for path in sorted((directory/'textures').rglob('*.png')):
        groups[hashlib.sha256(path.read_bytes()).digest()].append(path)
    aliases={};saved=0
    for paths in groups.values():
        if len(paths)<2:continue
        # Prefer the retained standard door/glass artwork over its old low-tier path.
        paths.sort(key=lambda p:('/medium/' not in p.as_posix(),'/low/' in p.as_posix(),len(p.as_posix()),p.as_posix()))
        canonical=paths[0].relative_to(directory).as_posix()
        for duplicate in paths[1:]:
            aliases[duplicate.relative_to(directory).as_posix()]=canonical
            saved+=duplicate.stat().st_size;duplicate.unlink()
    output=directory/'data/texture_aliases.json';output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps(dict(sorted(aliases.items())),indent=2)+'\n')
    # Dynmap reads ZIP entries directly instead of Minecraft's resource manager.
    def redirect(match):
        name=match.group(1);prefix='assets/vandorlabs/';relative=name[len(prefix):] if name.startswith(prefix) else name if name.startswith('textures/') else 'textures/'+name
        target=aliases.get(relative)
        if target is None:return match.group(0)
        return 'filename='+(prefix+target if name.startswith(prefix) else target if name.startswith('textures/') else target[len('textures/'):])
    for path in directory.glob('dynmap*.txt'):path.write_text(re.sub(r'filename=([^,\n]+)',redirect,path.read_text()))
    print(f'Shared runtime artwork: {len(aliases)} compatibility paths share PNGs; {saved} duplicate PNG bytes omitted')

def check_jar(path):
    from zipfile import ZipFile
    with ZipFile(path) as jar:
        prefix='assets/vandorlabs/';aliases=json.loads(jar.read(prefix+'data/texture_aliases.json'))
        assert aliases,'shared texture manifest is empty'
        for alias,target in aliases.items():
            assert alias.startswith('textures/') and target.startswith('textures/') and '..' not in alias and '..' not in target
            assert prefix+alias not in jar.namelist() and prefix+target in jar.namelist(),(alias,target)
        hashes={}
        for name in jar.namelist():
            if name.startswith(prefix+'textures/') and name.endswith('.png'):
                digest=hashlib.sha256(jar.read(name)).digest();assert digest not in hashes,(name,hashes.get(digest));hashes[digest]=name
    print(f'PASS: {len(aliases)} legacy texture paths resolve to unique packaged PNGs')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--stage',type=Path);parser.add_argument('--check-jar',type=Path);args=parser.parse_args()
    if args.stage:stage(args.stage)
    if args.check_jar:check_jar(args.check_jar)
