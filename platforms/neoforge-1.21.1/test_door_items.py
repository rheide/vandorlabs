#!/usr/bin/env python3
"""Title-only actual Door item selector/UVs/padding/reload and source model override."""
import argparse
import json
import os
from pathlib import Path
from test_cube import ROOT, command, run, jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts

@retain_artifacts
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--legacy-reference',type=Path,required=True);parser.add_argument('--xvfb',action='store_true');args=parser.parse_args();reference=args.legacy_reference.resolve()
    if not reference.is_file():parser.error('actual exportDoorItemReference output required')
    if args.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);output=Path(artifact_directory(prefix='door-item-title-',dir=base));game=output/'client';game.mkdir()
    (game/'options.txt').write_text('onboardAccessibility:false\ntutorialStep:none\nrenderDistance:2\nmaxFps:60\nguiScale:2\n')
    pack=game/'resourcepacks/door-item-override';model=pack/'assets/vandorlabs/models/item/detailed_doors/low/space_standard_rotating_door_framed_left_leaf.json';model.parent.mkdir(parents=True)
    (pack/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':34,'description':'Door item source override fixture'}}))
    body=json.loads((ROOT/'generated-resources/assets/vandorlabs/models/block/detailed_doors/low/space_standard_rotating_door_framed_left_leaf.json').read_text());body['textures']={key:value.replace('vandorlabs:','vandorlabs:door-artwork/') if value.startswith('vandorlabs:') else value for key,value in body['textures'].items()};body['elements'][0]['from'][0]+=0.0001;body['display']={'gui':{'scale':[.9,.9,.9]}};model.write_text(json.dumps(body))
    launch=command('runClient','-PmodernClientRunDirectory='+str(game),'-PdoorItemReference='+str(reference));env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_DOOR_ITEM_CHECKS='true')
    if args.xvfb:launch=['xvfb-run','-a','-s','-screen 0 1280x720x24']+launch;env['LIBGL_ALWAYS_SOFTWARE']='1'
    run(launch,output/'client.log','[vandorlabs][door-items] title PASS',env)
    from PIL import Image
    report=json.loads((game/'screenshots/door-item-icons-report.json').read_text());pixels=Image.open(game/'screenshots/door-item-icons.png').convert('RGB');background=(38,64,87);icons=[]
    for index,model_id in enumerate(report['models']):
        x=12+(index%report['columns'])*26;y=36+(index//report['columns'])*28
        if x+18>pixels.width or y+18>pixels.height:raise AssertionError('GUI capture clips '+model_id)
        visible=[(dx,dy) for dy in range(18) for dx in range(18) if pixels.getpixel((x+dx,y+dy))!=background]
        if visible:
            bounds=[min(v[0] for v in visible),max(v[0] for v in visible),min(v[1] for v in visible),max(v[1] for v in visible)]
            if bounds[0]<1 or bounds[1]>16 or bounds[2]<1 or bounds[3]>16:raise AssertionError('GPU icon escapes padded slot '+model_id+' '+str(bounds))
        else:bounds=None
        icons.append({'model':model_id,'visible_pixels':len(visible),'bounds':bounds})
    if len(icons)!=360:raise AssertionError('Missing GUI icon variants '+str(len(icons)))
    (output/'icon-padding-report.json').write_text(json.dumps({'icons':icons,'count':len(icons),'captured_size':pixels.size},indent=2))
    print('GPU icon slot masks PASS variants='+str(len(icons)),flush=True)
    print('Door title item reference/padding/reload PASS: '+str(output),flush=True)
if __name__=='__main__':main()
