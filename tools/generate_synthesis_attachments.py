"""Refresh the Synthesis Tower's native chip holder and rack shell geometry."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources/assets/vandorlabs/models/block'
TARGET=ROOT/'universal_constructor/synthesis_tower'
MARKER='# Generated native attachments'
def main():
    obj=(TARGET/'complete.obj').read_text().split(MARKER)[0].rstrip()+'\n'
    mtl=(TARGET/'materials.mtl').read_text().split(MARKER)[0].rstrip()+'\n'
    nv=sum(l.startswith('v ') for l in obj.splitlines());nt=sum(l.startswith('vt ') for l in obj.splitlines())
    obj+=MARKER+'\n';mtl+=MARKER+'\n'
    faces={'north':(0,1,2,3),'south':(5,4,7,6),'west':(4,0,3,7),'east':(1,5,6,2),'up':(3,2,6,7),'down':(4,5,1,0)}
    for name,path,ox,oy,oz,scale,reverse in [('rack','module_rack/shell.json',1.97,.20,0,.72,True),('chip','token_slots/data_chip_slot_north_0.json',1.82,1,-.625,1,False)]:
        model=json.loads((ROOT/path).read_text())
        for key,texture in model['textures'].items():
            mtl+='newmtl synth_'+name+'_'+key+'\nKa 1 1 1\nKd 1 1 1\nd 1\nillum 1\nmap_Kd '+texture+'\n\n'
        for index,element in enumerate(model['elements']):
            x,y,z=[v/16 for v in element['from']];X,Y,Z=[v/16 for v in element['to']]
            points=[(x,y,z),(X,y,z),(X,Y,z),(x,Y,z),(x,y,Z),(X,y,Z),(X,Y,Z),(x,Y,Z)]
            for side,face in element['faces'].items():
                obj+='g synth_'+name+'_'+str(index)+'_'+side+'\nusemtl synth_'+name+'_'+face['texture'][1:]+'\n'
                for v in faces[side]:
                    px,py,pz=points[v]
                    if reverse:px,pz=1-px,1-pz
                    obj+='v %.8f %.8f %.8f\n'%(3-(ox+px*scale),oy+py*scale,oz+pz*scale)
                a,b,c,d=face.get('uv',[0,0,16,16]);uv=[(a/16,1-d/16),(c/16,1-d/16),(c/16,1-b/16),(a/16,1-b/16)]
                for u,v in uv:obj+='vt %.8f %.8f\n'%(u,v)
                obj+='f '+' '.join('%d/%d'%(nv+i,nt+i) for i in range(1,5))+'\n';nv+=4;nt+=4
    # Off-state native port faces also belong to full finished-design previews.
    ports=[('power_in_off',[(2,0,3),(3,0,3),(3,1,3),(2,1,3)]),
           ('power_in_off',[(2,0,2),(3,0,2),(3,0,3),(2,0,3)]),
           ('matter_in_off',[(0,0,3),(1,0,3),(1,1,3),(0,1,3)]),
           ('matter_out_off',[(0,0,2),(1,0,2),(1,0,3),(0,0,3)]),
           ('matter_out_off',[(0,0,2),(0,0,3),(0,1,3),(0,1,2)])]
    for texture in sorted({name for name,points in ports}):
        mtl+='newmtl synth_port_'+texture+'\nKa 1 1 1\nKd 1 1 1\nd 1\nillum 1\nmap_Kd vandorlabs:blocks/resource_ports/'+texture+'\n\n'
    for index,(texture,points) in enumerate(ports):
        obj+='g synth_port_'+str(index)+'\nusemtl synth_port_'+texture+'\n'
        for x,y,z in points:obj+='v %s %s %s\n'%(x,y,z)
        for u,v in [(0,0),(1,0),(1,1),(0,1)]:obj+='vt %s %s\n'%(u,v)
        obj+='f '+' '.join('%d/%d'%(nv+i,nt+i) for i in range(1,5))+'\n';nv+=4;nt+=4
    (TARGET/'complete.obj').write_text(obj);(TARGET/'materials.mtl').write_text(mtl.rstrip()+'\n')
if __name__=='__main__':main()
