#!/usr/bin/env python3
"""Validate the restricted three-builder native projection against released source bytes."""
import hashlib,json,shutil,tempfile
import generate_builder_resources as generator
from pathlib import Path
from generate_builder_resources import ROOT,OUT,IDS,SOURCE

def main():
    assets=OUT/'assets/vandorlabs';manifest=json.loads((assets/'data/native_builders.json').read_text())
    assert set(manifest['designs'])==set(IDS)
    assert {p.name for p in (assets/'models/block/builders').iterdir() if p.is_dir()}==set(IDS)
    for design in IDS:
        entry=manifest['designs'][design];target=assets/'models/block/builders'/design
        native=(target/'complete.obj').read_bytes();assert hashlib.sha256(native).hexdigest()==entry['obj_sha256']
        source=SOURCE/'models/block/universal_constructor'/design/'complete.obj';assert native==source.read_bytes()
        for line in native.decode().splitlines():
            if line.startswith('v '):assert all(-.00001<=p<=n+.00001 for p,n in zip(map(float,line.split()[1:]),(entry['width'],entry['height'],entry['depth'])))
        item=json.loads((assets/f'models/item/{design}_core.json').read_text());assert max(item['display']['gui']['scale'])<=.5
        state=json.loads((assets/f'blockstates/{design}_core.json').read_text());assert len(state['variants'])==8
        model=json.loads((target/'complete.json').read_text());assert model['loader']=='neoforge:obj' and model['flip_v'] and not model['automatic_culling']
        loot=json.loads((OUT/f'data/vandorlabs/loot_table/blocks/{design}_core.json').read_text());assert loot=={'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'vandorlabs:{design}_core'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]}
    assert {p.name for p in (OUT/'data/vandorlabs/loot_table/blocks').glob('*.json')}=={design+'_core.json' for design in IDS}
    assert not list(OUT.rglob('recipe')) and not list(OUT.rglob('recipes'))
    for reference,digest in manifest['textures'].items():
        relative=Path('assets/vandorlabs/textures')/(reference.split(':',1)[1]+'.png')
        dependency=manifest.get('shared',{}).get(reference)
        path=(OUT.parent/dependency['owner']/relative) if dependency else OUT/relative
        assert hashlib.sha256(path.read_bytes()).hexdigest()==digest
        if dependency:assert not (OUT/relative).exists() and dependency['sha256']==digest
    assert not any(word in str(path.relative_to(OUT)) for path in OUT.rglob('*') for word in ('longburn','oculus','meridian','radar','navigation','sentinel','vector_fold'))
    # The native projection must refuse differing shared artwork, not silently
    # keep whichever resource happened to be copied last by Gradle.
    with tempfile.TemporaryDirectory(prefix='vandor-builder-shared-') as directory:
        temporary=Path(directory);builder=temporary/'builder-resources'
        for reference,dependency in manifest['shared'].items():
            relative=Path('assets/vandorlabs/textures')/(reference.split(':',1)[1]+'.png')
            source=OUT.parent/dependency['owner']/relative;target=temporary/dependency['owner']/relative
            target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target)
            meta=source.with_suffix('.png.mcmeta')
            if meta.exists():shutil.copyfile(meta,target.with_suffix('.png.mcmeta'))
        reference=next(iter(manifest['shared']));relative=Path('assets/vandorlabs/textures')/(reference.split(':',1)[1]+'.png')
        changed=temporary/'cube-resources'/relative;changed.write_bytes(changed.read_bytes()+b'changed-owner')
        original=generator.OUT;generator.OUT=builder
        try:
            try:generator.main()
            except ValueError as failure:assert 'Shared builder artwork differs from owner' in str(failure)
            else:raise AssertionError('Differing shared sprite was accepted')
        finally:generator.OUT=original
    print('Native builder resources PASS exactly3 complete OBJ/MTL structures unchangedOBJ+spritebytes eight corestates paddedicons threeCoreLootTables noRecipes strictSharedSpriteMismatchRejection no otherframefamilies')
if __name__=='__main__':main()
