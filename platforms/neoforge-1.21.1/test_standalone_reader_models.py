#!/usr/bin/env python3
"""Actual reader3072world/128configured-item mesh corpus and native GUI icons, with reload."""
import argparse,os,hashlib,json
from PIL import Image
from pathlib import Path
from test_cube import ROOT,command,run,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts

@retain_artifacts
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--xvfb',action='store_true');parser.add_argument('--reference',type=Path,required=True);parser.add_argument('--override-reference',type=Path,default=ROOT/'build/test-artifacts/standalone-reader-override-forge.json');args=parser.parse_args();reference=args.reference.resolve()
    if not reference.is_file():parser.error('Actual Source reader render reference required')
    override=args.override_reference.resolve()
    if not override.is_file():parser.error('Export actual Source override corpus with exportStandaloneReaderOverrideReference, or pass --override-reference')
    if args.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);output=Path(artifact_directory(prefix='standalone-reader-model-',dir=base));game=output/'client';game.mkdir();(game/'options.txt').write_text('onboardAccessibility:false\ntutorialStep:none\nrenderDistance:2\nmaxFps:60\nguiScale:2\n');(output/'reference.sha256').write_text(hashlib.sha256(reference.read_bytes()).hexdigest()+'\n')
    pack=game/'resourcepacks/reader-model-override';pack.mkdir(parents=True);(pack/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':34,'description':'Reader model replacement fixture'}}))
    source=Path(__file__).resolve().parent/'build/generated/reader-resources/assets/vandorlabs'
    for chip in [False,True]:
        kind='data_chip' if chip else 'access_card';family=kind+'_slot'
        body=json.loads((source/'models/item'/str(family+'.json')).read_text());body['elements'][0]['from'][0]+=.0001;body['display']['gui']={'rotation':[17,29,3],'translation':[1,-1,0],'scale':[3,3,3]};body['display']['thirdperson_righthand']={'rotation':[11,23,7],'translation':[1,2,3],'scale':[.37,.41,.43]}
        target=pack/'assets/vandorlabs/models/item'/str(family+'.json');target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(body))
        selected=json.loads(json.dumps(body));selected['elements'][0]['from'][0]+=.75;selected_id='reader_override_selected_'+('chip' if chip else 'card');(target.parent/str(selected_id+'.json')).write_text(json.dumps(selected))
        listed='reader_geometry_fixture_'+('chip' if chip else 'card');(target.parent/str(listed+'.json')).write_text(json.dumps({'parent':'vandorlabs:item/'+family,'overrides':[{'predicate':{'custom_model_data':2},'model':'vandorlabs:item/'+selected_id}]}))
        states=json.loads((source/'blockstates'/str(family+'.json')).read_text());states['variants']['facing=north,on=false,rotation=0']['model']='vandorlabs:block/token_slots/'+family+'_south_0';target=pack/'assets/vandorlabs/blockstates'/str(listed+'.json');target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(states))
    launch=command('runClient' ,'-PmodernClientRunDirectory='+str(game));env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_STANDALONE_READER_MODEL_CHECK='true',VANDOR_STANDALONE_READER_REFERENCE=str(reference),VANDOR_STANDALONE_READER_OVERRIDE_REFERENCE=str(override))
    if args.xvfb:launch=['xvfb-run','-a','-s','-screen 0 1280x720x24']+launch;env['LIBGL_ALWAYS_SOFTWARE']='1'
    run(launch,output/'client.log','[vandorlabs][standalone-reader-model] title PASS',env)
    bounds={}
    for name in ['standalone-reader-icons.png','standalone-reader-icons-reloaded.png','standalone-reader-icons-pack.png','standalone-reader-icons-restored.png']:
        path=game/'screenshots'/name
        if not path.is_file():raise AssertionError('Missing actual item render '+name)
        image=Image.open(path).convert('RGB');rows=[]
        if image.size!=(1280,720):raise AssertionError('Unexpected GUI capture dimensions '+str(image.size))
        for index in range(128):
            x=(24+index%16*32)*2;y=(24+index//16*32)*2
            pixels=[(u,v) for v in range(32) for u in range(32) if sum(abs(a-b) for a,b in zip(image.getpixel((x+u,y+v)),(16,16,18)))>=3]
            if not pixels:raise AssertionError('Invisible reader icon '+str(index))
            box=[min(u for u,v in pixels),min(v for u,v in pixels),max(u for u,v in pixels),max(v for u,v in pixels)]
            padding=min(box[0],box[1],31-box[2],31-box[3])
            if padding<1:raise AssertionError('Reader icon lacks half-GUI-pixel padding: '+str((name,index,box)))
            rows.append(dict(index=index,bounds=box,paddingPixels=padding))
        bounds[name]=rows
    (output/'icon-bounds.json').write_text(json.dumps(bounds,sort_keys=True)+'\n')
    print('Native standalone reader model focus PASS: '+str(output),flush=True)

if __name__=='__main__':main()
