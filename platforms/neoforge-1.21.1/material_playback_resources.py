"""Direct (non-atlas) display textures for per-host playback; no global sprite animation."""
import json
import struct
import zlib
from pathlib import Path
from PIL import Image


def _source(root, name, suffix):
    for directory in ['texture-packs/additional', 'texture-packs/default', 'src/main/resources']:
        path=root/directory/('assets/vandorlabs/textures/blocks/'+name+suffix)
        if path.is_file():return path
    return None


def decode(base, encoded):
    """Same base-relative RGB deltas as CompactAnimationDecoder, including opaque later frames."""
    data=encoded.read_bytes()
    if len(data)<15 or data[:5]!=b'VLTA\x02':raise ValueError('Invalid playback animation: '+str(encoded))
    width,height,count,length=struct.unpack('>HHHI',data[5:15])
    if base.size!=(width,height) or count<2 or width*height*count>64*1024*1024 or length!=(count-1)*width*height*3:raise ValueError('Playback dimensions: '+str(encoded))
    delta=zlib.decompress(data[15:])
    if len(delta)!=length:raise ValueError('Playback delta length: '+str(encoded))
    strip=Image.new('RGBA',(width,height*count));strip.paste(base,(0,0));rgb=base.convert('RGB').tobytes();size=width*height*3
    for frame in range(1,count):
        offset=(frame-1)*size;rgb=bytes((a+b)&255 for a,b in zip(rgb,delta[offset:offset+size]));strip.paste(Image.frombytes('RGB',(width,height),rgb).convert('RGBA'),(0,frame*height))
    return strip,count


def decode_animation(root,name,base):
    """Released decoder order: straight-alpha layered PNG first, then legacy compact RGB deltas."""
    layered=_source(root,name,'_animated.png')
    if layered is not None:
        with Image.open(layered) as image:
            if image.width!=base.width or image.height%base.height or not 2<=image.height//base.height<=64 or image.width>128 or base.height>128:
                raise ValueError('Layered playback dimensions: '+str(layered))
            return image.convert('RGBA'),image.height//base.height
    packed=_source(root,name,'_animated.anim')
    return None if packed is None else decode(base,packed)


class PlaybackResources:
    def __init__(self,root,assets):
        self.root=root;self.assets=assets;self.done={}
        self.displays={entry['id'] for entry in json.loads((root/'generated-resources/assets/vandorlabs/data/blocks.json').read_text()) if entry.get('type')=='display' and not entry.get('retired',False)}
        self.inputs={entry['id']:entry.get('frames',1) if entry.get('animated',False) else 1 for entry in json.loads((root/'generated-resources/assets/vandorlabs/data/input_surfaces.json').read_text())}
        source=_source(root,'screen_off','.png')
        if source is None:raise ValueError('Missing plain screen Off artwork')
        self.off='vandorlabs:textures/playback/screen_off.png';target=assets/'textures/playback/screen_off.png';target.parent.mkdir(parents=True,exist_ok=True)
        with Image.open(source) as image:image.convert('RGBA').save(target,optimize=True)

    def entry(self,original,canonical):
        if original.get('category')!='Screens':return None
        source=canonical['source'];key=canonical['id']
        if key in self.done:return self.done[key]
        path=_source(self.root,source,'.png')
        if path is None:raise ValueError('Missing playback static '+source)
        base=Image.open(path).convert('RGBA');prefix='textures/playback/'+key
        base.save(self.assets/(prefix+'_static.png'),optimize=True)
        texture='vandorlabs:'+prefix+'_static.png';animation=None;count=1
        input_source=source.startswith('console_inputs/');name=source[15:] if input_source else source
        if name.endswith('_static'):name=name[:-7]
        animated=self.inputs.get(name,1)>1 if input_source else name in self.displays
        decoded=decode_animation(self.root,('console_inputs/' if input_source else '')+name,base) if animated else None
        if decoded is not None:
            strip,count=decoded;strip.save(self.assets/(prefix+'_animated.png'),optimize=True);animation='vandorlabs:'+prefix+'_animated.png'
        result=dict(staticTexture=texture,offTexture=self.off,animationTexture=animation,frameCount=count,aspect=base.width/base.height);self.done[key]=result;return result
