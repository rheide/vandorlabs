package com.vandorlabs.client;

import com.vandorlabs.canopy.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import java.util.*;

/** Ordered exact-coordinate reference for the original recursive collision tessellation. */
final class CanopySubdivisionRuntimeChecks {
    private static int cases;private static long boxes;
    static void run(){
        LargeCanopySettings longSettings=new LargeCanopySettings();longSettings.motion=1;longSettings.angle=2;
        for(double pose:new double[]{0,.375,1})for(EnumFacing face:new EnumFacing[]{EnumFacing.NORTH,EnumFacing.WEST})check(OpeningCanopyGeometry.faces(3,3,2,false),"long_canopy",3,longSettings,pose,face,new BlockPos(-149,69,53),false);
        LargeCanopySettings front=new LargeCanopySettings();front.depth=24;front.scale=50;front.openable=true;
        for(int hinge:new int[]{0,2}){front.hinge=hinge;check(LargeCanopyGeometry.faces(5,front,true),"large_front_canopy_glass",5,front,.375,EnumFacing.SOUTH,new BlockPos(-152,85,166),true);}
        for(EnumFacing face:EnumFacing.HORIZONTALS)check(CanopyMesh.MODELS.get("angled_front_1x2"),"angled_front_1x2",1,new LargeCanopySettings(),0,face,new BlockPos(1024,71,-1024),false);
        System.out.println("[vandorlabs][reprolab] canopy-subdivision-equivalence PASS cases="+cases+" ordered-boxes="+boxes+" exact-coordinates negative-large-origins four-facings long-hinge front-window-glazing rails");
    }
    private static void check(List<CanopyMesh.Face> source,String kind,int width,LargeCanopySettings settings,double progress,EnumFacing facing,BlockPos pos,boolean window){
        List<CanopyMesh.Face> posed=CanopyPose.faces(source,kind,width,settings,progress);
        List<AxisAlignedBB> actual=window?CanopyMesh.windowCollision(posed,facing,pos):CanopyMesh.collision(posed,"posed",width,0,facing,pos),expected=new ArrayList<>();Set<AxisAlignedBB> rails=new HashSet<>();
        for(CanopyMesh.Face face:posed){
            if(window&&!face.glazing&&!face.baseMaterial.equals("glass"))continue;
            Vec3d[] vertices=new Vec3d[face.vertices.length];for(int i=0;i<vertices.length;i++)vertices[i]=CanopyMesh.rotate(window?face.vertices[i]:CanopyMesh.pose(face.vertices[i],"posed",width,0,face.group.equals("shell")),facing);
            for(int i=1;i<vertices.length-1;i++)reference(expected,vertices[0],vertices[i],vertices[i+1],3,pos);
            if(window)for(int i=0;i<vertices.length;i++){
                Vec3d a=vertices[i],b=vertices[(i+1)%vertices.length];int steps=Math.max(1,(int)Math.ceil(a.distanceTo(b)*8));
                for(int step=0;step<steps;step++){Vec3d from=a.add(b.subtract(a).scale(step/(double)steps)),to=a.add(b.subtract(a).scale((step+1D)/steps));rails.add(new AxisAlignedBB(from,to).grow(1D/16+.002).offset(pos));}
            }
        }
        if(actual.isEmpty())throw new IllegalStateException("subdivision fixture has no geometry: "+kind);
        int surfaceCount=expected.size();boolean same=window?actual.size()==surfaceCount+rails.size()&&actual.subList(0,surfaceCount).equals(expected)&&new HashSet<>(actual.subList(surfaceCount,actual.size())).equals(rails):actual.equals(expected);
        expected.addAll(rails);if(!same){
            int mismatch=0;while(mismatch<Math.min(actual.size(),expected.size())&&actual.get(mismatch).equals(expected.get(mismatch)))mismatch++;
            throw new IllegalStateException("canopy subdivision differs "+kind+" pose="+progress+" facing="+facing+" index="+mismatch+" counts="+actual.size()+"/"+expected.size()+" actual="+(mismatch<actual.size()?actual.get(mismatch):null)+" expected="+(mismatch<expected.size()?expected.get(mismatch):null));
        }cases++;boxes+=actual.size();
    }
    private static void reference(List<AxisAlignedBB> boxes,Vec3d a,Vec3d b,Vec3d c,int depth,BlockPos pos){
        boolean flat=Math.max(a.x,Math.max(b.x,c.x))-Math.min(a.x,Math.min(b.x,c.x))<.0001||Math.max(a.y,Math.max(b.y,c.y))-Math.min(a.y,Math.min(b.y,c.y))<.0001||Math.max(a.z,Math.max(b.z,c.z))-Math.min(a.z,Math.min(b.z,c.z))<.0001;
        if(depth==0||flat){boxes.add(new AxisAlignedBB(Math.min(a.x,Math.min(b.x,c.x)),Math.min(a.y,Math.min(b.y,c.y)),Math.min(a.z,Math.min(b.z,c.z)),Math.max(a.x,Math.max(b.x,c.x)),Math.max(a.y,Math.max(b.y,c.y)),Math.max(a.z,Math.max(b.z,c.z))).grow(.002).offset(pos));return;}
        Vec3d ab=a.add(b).scale(.5),bc=b.add(c).scale(.5),ca=c.add(a).scale(.5);reference(boxes,a,ab,ca,depth-1,pos);reference(boxes,ab,b,bc,depth-1,pos);reference(boxes,ca,bc,c,depth-1,pos);reference(boxes,ab,bc,ca,depth-1,pos);
    }
}
