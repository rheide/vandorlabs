"""Generate tower casing, workstation and exact flush NORTH-facing four-bay rack."""
import json,math,re
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/vandorlabs'
TARGET=ASSETS/'models/block/universal_constructor/synthesis_tower'
TEX=ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks/universal_constructor'
MARKER='# Generated native attachments'
def main():
    constants=(ROOT/'src/portable/java/com/vandorlabs/machines/SynthesisPresentation.java').read_text()
    def value(name):return float(re.search(r'double '+name+r'=([.0-9]+);',constants).group(1))
    raw=(TARGET/'complete.obj').read_text().split(MARKER)[0];lines=[];group=''
    retired={'static__pedestal_casing','static__console_shelf','console_hull','square_screen_bezel','square_screen'}
    for line in raw.splitlines():
        if line.startswith('g '):group=line[2:]
        if group in retired and line.startswith(('g ','usemtl ','f ')):continue
        lines.append(line)
    obj='\n'.join(lines).rstrip()+'\n'+MARKER+'\n'
    mtl=(TARGET/'materials.mtl').read_text().split(MARKER)[0].rstrip()+'\n'+MARKER+'\n'
    nv=sum(l.startswith('v ') for l in obj.splitlines());nt=sum(l.startswith('vt ') for l in obj.splitlines());count=0
    def material(name,texture,color=(1,1,1)):
        nonlocal mtl
        mtl+='newmtl '+name+'\nKa 1 1 1\nKd %s %s %s\nd 1\nillum 1\nmap_Kd '%color+texture+'\n\n'
    def quad(name,mat,points,uv=((0,0),(1,0),(1,1),(0,1))):
        nonlocal obj,nv,nt,count
        obj+='g '+name+'\nusemtl '+mat+'\n'
        for x,y,z in points:obj+='v %.8f %.8f %.8f\n'%(x,y,z)
        for u,v in uv:obj+='vt %.8f %.8f\n'%(u,v)
        obj+='f '+' '.join('%d/%d'%(nv+i,nt+i) for i in range(1,5))+'\n';nv+=4;nt+=4;count+=1
    material('synth_casing','vandorlabs:blocks/universal_constructor/synthesis_casing')
    material('synth_service','vandorlabs:blocks/universal_constructor/synthesis_service')
    material('synth_bezel','vandorlabs:blocks/resource_ports/port_hull')
    material('synth_screen','minecraft:blocks/concrete_black')
    source=ROOT/'texture-packs/default/assets/vandorlabs/textures/blocks/console_inputs/input_surfaces_keyboard_static.png'
    with Image.open(source) as im:
        authored=im.convert('RGBA');assert authored.size==(128,64)
        image=Image.new('RGBA',(128,128));image.paste(authored,(0,0));image.save(TEX/'synthesis_keyboard.png',optimize=True)
    material('synth_keyboard','vandorlabs:blocks/universal_constructor/synthesis_keyboard')
    def cuts(lo,hi,extra=()):return sorted({lo,hi,*[float(n) for n in range(math.ceil(lo),math.ceil(hi)) if lo<n<hi],*[v for v in extra if lo<v<hi]})
    def uv_rect(a,b,c,d):return ((a,d),(b,d),(b,c),(a,c))
    # Subdivide in world block units so the new64px casing never stretches over the3x2 plinth.
    rx,ry,rz=value('RACK_X'),value('RACK_Y'),value('RACK_Z')
    xs=cuts(0,3,(rx,rx+1));ys=cuts(0,2,(ry,ry+1))
    for x0,x1 in zip(xs,xs[1:]):
        for y0,y1 in zip(ys,ys[1:]):
            if rx<(x0+x1)/2<rx+1 and ry<(y0+y1)/2<ry+1:continue
            quad('synth_front_'+str(count),'synth_casing',[(x0,y1,.375),(x1,y1,.375),(x1,y0,.375),(x0,y0,.375)],uv_rect(x0-math.floor(x0),x1-math.floor(x0),y0-math.floor(y0),y1-math.floor(y0)))
    for x0,x1 in zip(cuts(0,3),cuts(0,3)[1:]):
        ys=cuts(0,2,(.5,1.5))
        for y0,y1 in zip(ys,ys[1:]):
            if x0 in (0,2) and y1<=1:continue # actual bottom-corner functional ports replace these faces
            service=x0==1 and .5<=y0 and y1<=1.5
            uv=uv_rect(0,1,y0-.5,y1-.5) if service else uv_rect(0,1,y0-math.floor(y0),y1-math.floor(y0))
            quad('synth_rear_'+str(count),'synth_service' if service else 'synth_casing',[(x1,y1,3),(x0,y1,3),(x0,y0,3),(x1,y0,3)],uv)
    zs=cuts(.375,3)
    for z0,z1 in zip(zs,zs[1:]):
        for y0,y1 in zip(cuts(0,2),cuts(0,2)[1:]):
            uv=uv_rect(z0-math.floor(z0),z1-math.floor(z0),y0,y1) if y0==0 else uv_rect(z0-math.floor(z0),z1-math.floor(z0),0,1)
            if not (y0==0 and z0>=2):quad('synth_left_'+str(count),'synth_casing',[(0,y1,z1),(0,y1,z0),(0,y0,z0),(0,y0,z1)],uv)
            quad('synth_right_'+str(count),'synth_casing',[(3,y1,z0),(3,y1,z1),(3,y0,z1),(3,y0,z0)],uv)
        for x0,x1 in zip(cuts(0,3),cuts(0,3)[1:]):
            uv=((0,z0-math.floor(z0)),(1,z0-math.floor(z0)),(1,z1-math.floor(z0)),(0,z1-math.floor(z0)))
            quad('synth_top_'+str(count),'synth_casing',[(x0,2,z0),(x0,2,z1),(x1,2,z1),(x1,2,z0)],uv)
            if not (z0>=2 and x0 in (0,2)):quad('synth_bottom_'+str(count),'synth_casing',[(x0,0,z1),(x0,0,z0),(x1,0,z0),(x1,0,z1)],uv)
    def front(name,mat,x0,y0,x1,y1,z):quad(name,mat,[(x0,y1,z),(x1,y1,z),(x1,y0,z),(x0,y0,z)],((0,1),(1,1),(1,0),(0,0)))
    cx,cy,cz=value('SCREEN_X'),value('SCREEN_Y'),value('SCREEN_Z');s=value('SCREEN_FRAME')/2
    front('synth_screen_bezel','synth_bezel',cx-s,cy-s,cx+s,cy+s,cz+.012)
    s=value('SCREEN_SIZE')/2;front('synth_screen_display','synth_screen',cx-s,cy-s,cx+s,cy+s,cz)
    kx,ky,kz=value('KEYBOARD_X'),value('KEYBOARD_Y'),value('KEYBOARD_Z');kw,kd=value('KEYBOARD_WIDTH'),value('KEYBOARD_DEPTH')
    quad('synth_keyboard','synth_keyboard',[(kx,ky,kz),(kx,ky,kz+kd),(kx+kw,ky,kz+kd),(kx+kw,ky,kz)],((0,.5),(0,1),(1,1),(1,.5)))
    front('synth_keyboard_lip','synth_bezel',kx-.04,ky-.125,kx+kw+.04,ky,kz-.002)
    quad('synth_keyboard_left','synth_bezel',[(kx-.04,ky,kz),(kx-.04,ky,kz+kd),(kx-.04,ky-.125,kz+kd),(kx-.04,ky-.125,kz)])
    quad('synth_keyboard_right','synth_bezel',[(kx+kw+.04,ky,kz+kd),(kx+kw+.04,ky,kz),(kx+kw+.04,ky-.125,kz),(kx+kw+.04,ky-.125,kz+kd)])
    faces={'north':(3,2,1,0),'south':(6,7,4,5),'west':(7,3,0,4),'east':(2,6,5,1),'up':(7,6,2,3),'down':(0,1,5,4)}
    for name,path,ox,oy,oz,north in [('rack','module_rack/shell.json',rx,ry,rz,True),('chip','token_slots/data_chip_slot_north_0.json',value('CHIP_X')-.5,value('CHIP_Y')-.5,value('CHIP_Z')-.975,False)]:
        model=json.loads((ASSETS/'models/block'/path).read_text())
        for key,texture in model['textures'].items():material('synth_'+name+'_'+key,texture)
        for index,element in enumerate(model['elements']):
            x,y,z=[v/16 for v in element['from']];X,Y,Z=[v/16 for v in element['to']];points=[(x,y,z),(X,y,z),(X,Y,z),(x,Y,z),(x,y,Z),(X,y,Z),(X,Y,Z),(x,Y,Z)]
            for side,face in element['faces'].items():
                p=[]
                for n in faces[side]:
                    px,py,pz=points[n]
                    if north:px,pz=1-px,1-pz
                    p.append((ox+px,oy+py,oz+pz))
                a,b,c,d=face.get('uv',[0,0,16,16]);quad('synth_'+name+'_'+str(index)+'_'+side,'synth_'+name+'_'+face['texture'][1:],p,((a/16,1-b/16),(c/16,1-b/16),(c/16,1-d/16),(a/16,1-d/16)))
    ports=[('power_in_off',[(2,0,3),(3,0,3),(3,1,3),(2,1,3)]),('power_in_off',[(2,0,2),(3,0,2),(3,0,3),(2,0,3)]),('matter_in_off',[(0,0,3),(1,0,3),(1,1,3),(0,1,3)]),('matter_out_off',[(0,0,2),(1,0,2),(1,0,3),(0,0,3)]),('matter_out_off',[(0,0,2),(0,0,3),(0,1,3),(0,1,2)])]
    for texture in sorted({name for name,points in ports}):material('synth_port_'+texture,'vandorlabs:blocks/resource_ports/'+texture)
    for index,(texture,points) in enumerate(ports):quad('synth_port_'+str(index),'synth_port_'+texture,points)
    (TARGET/'complete.obj').write_text(obj);(TARGET/'materials.mtl').write_text(mtl.rstrip()+'\n')
    catalog=ASSETS/'data/frame_machines.json';data=json.loads(catalog.read_text())
    for design in data:
        if design['design']=='synthesis_tower':design['chip_anchor']=[value('CHIP_X'),value('CHIP_Y'),value('CHIP_Z')]
    catalog.write_text(json.dumps(data,indent=2)+'\n')
if __name__=='__main__':main()
