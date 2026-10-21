"""Stage the generated docs browser for its existing Sites project."""
import json, shutil
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
manifest=json.loads((ROOT/'tools/texture_browser_hosting.json').read_text(encoding='utf-8'))
checkout=(ROOT/'build/texture-browser-site').resolve()
destination=checkout/'dist'
assert destination.is_relative_to((ROOT/'build').resolve())
identity=checkout/'.openai/hosting.json'
if identity.exists():
    existing=json.loads(identity.read_text(encoding='utf-8'))
    if existing.get('project_id')!=manifest['project_id']:raise SystemExit('Existing Site identity differs; refusing to replace it')
else:existing={}
existing.update(project_id=manifest['project_id'],static=manifest['static'])
identity.parent.mkdir(parents=True,exist_ok=True)
temporary=identity.with_suffix('.json.tmp');temporary.write_text(json.dumps(existing,indent=2)+'\n',encoding='utf-8');temporary.replace(identity)
source=ROOT/'docs/texture-browser'
expected={path.relative_to(source) for path in source.rglob('*') if path.is_file()}
for relative in expected:
    target=destination/relative;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source/relative,target)
if destination.exists():
    for target in destination.rglob('*'):
        if target.is_file() and target.relative_to(destination) not in expected:
            assert target.resolve().is_relative_to(destination.resolve())
            target.unlink()
print('Staged generated browser for existing Site '+manifest['project_id'])
