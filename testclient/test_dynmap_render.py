#!/usr/bin/env python3
"""Render a saved programmable-block fixture with a real, isolated Forge/Dynmap server."""
import argparse
import hashlib
from pathlib import Path
import re
import shutil
import socket
import subprocess
import tempfile
import time
from zipfile import ZipFile

def validate_render(output):
    from PIL import Image
    import json
    for phase in ('prepare','render'):
        text=(output/(phase+'.log')).read_text(errors='replace')
        for error in ('Format error','not enough textures','Invalid modellist','Invalid texture block name','Error loading custom renderer'):
            if error in text:raise AssertionError(f'{error}: {output}/{phase}.log')
    labels=json.loads((output/'fixtures.json').read_text())
    text=(output/'render.log').read_text(errors='replace')
    if text.count('Block placed')<len(labels)+4:raise AssertionError('Incomplete block fixture')
    files=[p for p in (output/'dynmap/web/tiles/world').rglob('*.png') if 'fixture' in str(p)]
    if not files:raise AssertionError('Dynmap produced no fixture PNG tiles')
    richest=0
    for path in files:
        if path.name.startswith('z'):continue
        with Image.open(path) as image:richest=max(richest,len(image.convert('RGB').getcolors(1000000) or []))
    if richest<300:raise AssertionError('Map contains no sufficiently detailed textured fixture tile')
    return len(files)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--forge',required=True,type=Path)
    parser.add_argument('--dynmap',required=True,type=Path)
    parser.add_argument('--jar',required=True,type=Path)
    parser.add_argument('--java',default='java')
    parser.add_argument('--mod',action='append',default=[],type=Path,help='Additional installed compatibility JARs')
    args=parser.parse_args()
    root=Path(__file__).resolve().parents[1]
    output=Path(tempfile.mkdtemp(prefix='dynmap-render-',dir=root/'build'))
    print('Isolated Dynmap fixture:',output,flush=True)
    (output/'mods').mkdir();(output/'dynmap').mkdir()
    for mod in (args.jar,args.dynmap,*args.mod):shutil.copyfile(mod,output/'mods'/mod.name)
    (output/'artifact.sha256').write_text(hashlib.sha256(args.jar.read_bytes()).hexdigest()+'\n')
    with socket.socket() as sock:sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
    (output/'eula.txt').write_text('eula=true\n')
    (output/'server.properties').write_text(f'online-mode=false\nserver-ip=127.0.0.1\nserver-port={port}\nlevel-type=FLAT\ngenerate-structures=false\nspawn-animals=false\nspawn-monsters=false\nview-distance=3\nallow-nether=false\nmax-tick-time=120000\n')
    with ZipFile(args.dynmap) as archive:config=archive.read('configuration.txt').decode()
    config=config.replace('disable-webserver: false','disable-webserver: true').replace('image-format: jpg-q90','image-format: png')
    config=config.replace('org.dynmap.InternalClientUpdateComponent','org.dynmap.JsonFileClientUpdateComponent')
    config+='\nparallelrendercnt: 1\nupdate-min-tps: 0\nfullrender-min-tps: 0\n'
    (output/'dynmap/configuration.txt').write_text(config)
    (output/'dynmap/worlds.txt').write_text('''worlds:
  - name: world
    enabled: true
    maps:
      - class: org.dynmap.hdmap.HDMap
        name: fixture
        title: Fixture
        prefix: fixture
        perspective: iso_SE_30_hires
        shader: stdtexture
        lighting: default
        image-format: png
''')
    for phase in ('prepare','render'):
        logpath=output/(phase+'.log')
        with logpath.open('w') as log:
            process=subprocess.Popen([args.java,'-Xmx2G','-jar',str(args.forge.resolve()),'nogui'],cwd=output,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True)
            def command(line):process.stdin.write(line+'\n');process.stdin.flush()
            def wait(marker,timeout=180):
                deadline=time.monotonic()+timeout
                while time.monotonic()<deadline:
                    content=logpath.read_text(errors='replace')
                    if any(error in content for error in ('NoSuchMethodError','Exception in server tick loop','Encountered an unexpected exception','Error loading custom renderer')):raise RuntimeError('Dynmap/server error: '+str(logpath))
                    if marker in content:return
                    if process.poll() is not None:raise RuntimeError('Server exited: '+str(logpath))
                    time.sleep(.25)
                raise TimeoutError(f'{marker}: {logpath}')
            try:
                wait('Done (');command('dynmap pause all')
                if phase=='prepare':command('setworldspawn 8 12 8')
                else:
                    command('fill -2 11 -2 24 11 24 minecraft:stone')
                    labels=[]
                    for i,facing in enumerate(('north','east','south','west')):
                        x=i*5
                        for z,block,properties,tag in (
                            (0,'programmable_slab','half=bottom', '{housingTexture:1}'),
                            (3,'programmable_stairs','half=bottom,shape=straight','{housingTexture:17,HousingTextureVersion:1,StairTread:4}'),
                            (6,'programmable_diagonal_wall','inverted=false,depth=0','{housingTexture:1,DiagonalFullWidth:1b,DiagonalFill:3}'),
                            (9,'programmable_wall','inverted=false,depth=0','{housingTexture:3}'),
                            (12,'programmable_door','half=lower,open=false','{SpaceDesign:2,SpaceFramed:1b}'),
                            (16,'regular_canopy_glass','', '{CanopyShade:1,CanopyTint:1}'),
                            (20,'programmable_slab','half=top','{housingTexture:84,HousingTextureVersion:1}'),
                            (23,'programmable_diagonal_wall','inverted=true,depth=0','{housingTexture:1,DiagonalHalfHeight:1b,DiagonalFill:3}')):
                            state='facing='+facing+(','+properties if properties else '')
                            command(f'setblock {x} 12 {z} vandorlabs:{block} {state} replace {tag}')
                            if block=='programmable_door':command(f'setblock {x} 13 {z} vandorlabs:{block} half=upper,hinge=left')
                            labels.append({'x':x,'y':12,'z':z,'block':block,'facing':facing})
                    command('save-all flush');command('say VANDOR_DYNMAP_FIXTURE_SAVED');wait('VANDOR_DYNMAP_FIXTURE_SAVED')
                    content=logpath.read_text(errors='replace')
                    if any(error in content for error in ('Cannot place block','Invalid block state','Invalid block property','is not a valid state','Data tag parsing failed')):raise RuntimeError('Fixture placement failed: '+str(logpath))
                    import json
                    (output/'fixtures.json').write_text(json.dumps(labels,indent=2)+'\n')
                    command('dynmap pause none');command('dynmap radiusrender world 8 8 24 fixture')
                    wait('render of map',timeout=240)
                    # Completion wording differs between Forge builds; require the explicit completion.
                    wait('completed -',timeout=240)
                    count=validate_render(output)
                    print('Dynmap render PASS:',count,'PNG tiles;',output,flush=True)
                command('stop');process.wait(timeout=45)
                if process.returncode:raise RuntimeError('Unclean server shutdown')
            finally:
                if process.poll() is None:
                    try:command('stop');process.wait(timeout=30)
                    except Exception:process.kill();process.wait(timeout=10)

if __name__=='__main__':main()
