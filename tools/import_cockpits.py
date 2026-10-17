#!/usr/bin/env python3
"""Import the authored cockpit geometry, rig and collision/surface metadata."""
from pathlib import Path
from zipfile import ZipFile

ROOT=Path(__file__).resolve().parents[1]

def import_assets():
    with ZipFile(ROOT/'build/vandor_labs_cockpit_models_1.12.2.zip') as archive:
        for name in archive.namelist():
            if '/assets/vandorlabs/' not in name or name.endswith('/'):continue
            relative=name.split('/assets/vandorlabs/',1)[1]
            destination='texture-packs/additional/assets/vandorlabs' if relative.startswith('textures/') else 'src/main/resources/assets/vandorlabs'
            target=ROOT/destination/relative
            target.parent.mkdir(parents=True,exist_ok=True)
            target.write_bytes(archive.read(name))
        for name in ('CockpitModels','CockpitMotion','CockpitRenderer'):
            source=next(p for p in archive.namelist() if p.endswith('/'+name+'.java'))
            target=ROOT/'src/main/java/com/vandorlabs/client/cockpits'/(name+'.java')
            target.parent.mkdir(parents=True,exist_ok=True)
            target.write_bytes(archive.read(source))
    print('Imported four cockpit/peripheral assemblies with authored collision, rig and surface metadata')

if __name__=='__main__':import_assets()
