#!/usr/bin/env python3
"""Source identity/set mapping and actual standard-JAR pixels, independently of native rendering."""
import argparse,gzip,hashlib,json,io,zipfile
from pathlib import Path
from PIL import Image
from generate_storage_resources import ROOT,ASSETS

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--jar',type=Path);options=parser.parse_args()
    data=json.loads((ASSETS/'data/storage_artwork.json').read_text());reference=json.loads(gzip.decompress((ROOT/'testclient/fixtures/storage-source-reference.json.gz').read_bytes()))
    entries={e['choice']:e for e in data['sets']};textures={e['id']:e for e in data['textures']}
    assert data['schema']==1 and len(entries)==14 and len(textures)==42
    for row in reference['materials']:
        e=entries[row['choice']];assert row['faces']==[e['top'],e['top'],e['front'],e['side'],e['side'],e['side']]
    jar=zipfile.ZipFile(options.jar) if options.jar else None
    try:
        for name,e in textures.items():
            path=ASSETS/('textures/'+name.split(':',1)[1]+'.png')
            with Image.open(path) as native:
                native=native.convert('RGBA');assert native.size==(e['canvas'],e['canvas']) and e['canvas']<=256
                crop=native.crop((0,0,e['width'],e['height']));assert hashlib.sha256(crop.tobytes()).hexdigest()==e['rgba']
                assert native.getpixel((native.width-1,native.height-1))==crop.getpixel((crop.width-1,crop.height-1))
                if jar:
                    with Image.open(io.BytesIO(jar.read('assets/'+name.split(':',1)[0]+'/textures/'+name.split(':',1)[1]+'.png'))) as source:
                        assert source.convert('RGBA').size==crop.size and source.convert('RGBA').tobytes()==crop.tobytes(),name
    finally:
        if jar:jar.close()
    print('Storage resource PASS14sets/42sprites/Sourcepaths/standard-content/padding; actualJar='+str(options.jar is not None))
if __name__=='__main__':main()
