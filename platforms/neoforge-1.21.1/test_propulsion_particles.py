#!/usr/bin/env python3
"""Inactive real particle-atlas/format/alpha-boundary GPU primitive focus, including a pack reload."""
import argparse,json,os
from pathlib import Path
from PIL import Image
from test_cube import ROOT,command,run,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 p=argparse.ArgumentParser();p.add_argument('--xvfb',action='store_true');a=p.parse_args()
 if a.xvfb and os.name=='nt':p.error('--xvfb requires Linux')
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(artifact_directory(prefix='propulsion-particles-title-',dir=base));game=out/'client';game.mkdir();(game/'options.txt').write_text('onboardAccessibility:false\ntutorialStep:none\nrenderDistance:2\nmaxFps:60\nguiScale:2\n')
 pack=game/'resourcepacks/propulsion-particle-override';textures=pack/'assets/minecraft/textures/particle';textures.mkdir(parents=True);(pack/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':34,'description':'Private plume Source-alpha and override negative'}}))
 image=Image.new('RGBA',(8,8),(0,128,255,255));image.putpixel((6,5),(0,0,0,0));image.save(textures/'critical_hit.png');Image.new('RGBA',(8,8),(255,255,255,17)).save(textures/'effect_0.png')
 reference=ROOT/'build/test-artifacts/propulsion-particle-sprites-forge.json'
 if not reference.is_file():raise AssertionError('Run exportPropulsionParticleSpritesReference first')
 launch=command('runClient','-PmodernClientRunDirectory='+str(game));env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_PROPULSION_PARTICLE_CHECK='true',VANDOR_PROPULSION_PARTICLE_PIXELS=str(reference))
 if a.xvfb:launch=['xvfb-run','-a','-s','-screen 0 1280x720x24']+launch;env['LIBGL_ALWAYS_SOFTWARE']='1'
 run(launch,out/'client.log','[vandorlabs][propulsion-particles] title PASS',env)
 for n in range(2):
  if not (game/f'screenshots/propulsion-particles-{n}.png').is_file():raise AssertionError('Missing particle capture '+str(n))
 print('Propulsion particle primitive software focus PASS: '+str(out),flush=True)
if __name__=='__main__':main()
