"""Encode and validate the deterministic in-game 2.3 documentation captures."""
import argparse
from pathlib import Path
from PIL import Image, ImageChops

ROOT=Path(__file__).resolve().parents[1]

def encode(folder, destination):
    paths=sorted(folder.glob('frame-*.png'))
    if len(paths)<24:raise ValueError('Incomplete capture: '+str(folder))
    frames=[Image.open(path).convert('RGB') for path in paths]
    if any(frame.size!=(640,420) for frame in frames):raise ValueError('Unexpected capture resolution')
    if not any(ImageChops.difference(frames[0],frame).getbbox() for frame in frames[1:]):raise ValueError('Static animation: '+folder.name)
    contact=Image.new('RGB',(160*8,105*((len(frames)+7)//8)))
    for i,frame in enumerate(frames):contact.paste(frame.resize((160,105)),((i%8)*160,(i//8)*105))
    palette=contact.quantize(colors=256,method=Image.Quantize.MEDIANCUT)
    encoded=[frame.quantize(palette=palette,dither=Image.Dither.NONE) for frame in frames]
    destination.parent.mkdir(parents=True,exist_ok=True)
    encoded[0].save(destination,save_all=True,append_images=encoded[1:],duration=50,loop=0,optimize=True,disposal=1)
    with Image.open(destination) as gif:
        if not gif.is_animated or gif.n_frames<(4 if folder.name=='animation-advanced-chair-height' else 8) or gif.info.get('loop')!=0:raise ValueError('Invalid animated GIF: '+str(destination))
        duration=0
        for i in range(gif.n_frames):gif.seek(i);gif.load();duration+=gif.info.get('duration',0)
        if duration!=50*len(frames):raise ValueError('Animation timing changed')
    frames[0].save(destination.with_suffix('.png'),optimize=True)
    print(f'{destination.relative_to(ROOT)}: {len(frames)} captured frames, {duration}ms, {destination.stat().st_size} bytes')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('captures',type=Path,nargs='+');args=parser.parse_args()
    for capture in args.captures:
        folders=sorted(capture.glob('animation-*'))
        if not folders:raise ValueError('No animation captures in '+str(capture))
        for folder in folders:encode(folder,ROOT/'docs/images/gallery/version-2.3'/(folder.name[len('animation-'):]+'.gif'))

if __name__=='__main__':main()
