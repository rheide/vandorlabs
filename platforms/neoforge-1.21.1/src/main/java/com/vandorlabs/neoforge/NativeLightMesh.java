package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.light.ProgrammableLightMesh;
import com.vandorlabs.segmented.SegmentedPanelMesh;
import net.minecraft.core.Direction;

/** Shared source records through the active consumer; cardinal transforms never capture worlds or formats. */
public final class NativeLightMesh {
    private final ProgrammableLightMesh mesh;private final Direction facing;private final SegmentedPanelMesh.Texture texture;
    private static final ThreadLocal<Emission> SCRATCH=ThreadLocal.withInitial(Emission::new);
    public NativeLightMesh(ProgrammableLightMesh mesh,Direction facing,SegmentedPanelMesh.Texture texture){this.mesh=java.util.Objects.requireNonNull(mesh);this.facing=java.util.Objects.requireNonNull(facing);this.texture=java.util.Objects.requireNonNull(texture);}
    public long retainedBytes(){return 64+mesh.retainedBytes();}public int vertices(){return mesh.vertices();}
    public void emit(PoseStack.Pose pose,VertexConsumer target,int light,int overlay){var scratch=SCRATCH.get();if(scratch.target!=null)throw new IllegalStateException("Reentrant Light emission");scratch.pose=pose;scratch.target=target;scratch.light=light;scratch.overlay=overlay;scratch.facing=facing;scratch.texture=texture;try{mesh.emit(scratch);}finally{scratch.target=null;scratch.pose=null;scratch.texture=null;scratch.facing=null;}}
    private static final class Emission implements ProgrammableLightMesh.Sink {
        PoseStack.Pose pose;VertexConsumer target;int light,overlay;Direction facing;SegmentedPanelMesh.Texture texture;
        final org.joml.Vector3f position=new org.joml.Vector3f(),normal=new org.joml.Vector3f();
        public void vertex(double px,double py,double pz,double u,double v,float nx,float ny,float nz){
            float x=(float)(px/16),y=(float)(py/16),z=(float)(pz/16),a=nx,b=ny,c=nz;
            switch(facing){
                case DOWN->{y=(float)(pz/16);z=1-(float)(py/16);ny=c;nz=-b;}
                case UP->{y=1-(float)(pz/16);z=(float)(py/16);ny=-c;nz=b;}
                case SOUTH->{x=1-x;z=1-z;nx=-a;nz=-c;}
                case WEST->{x=(float)(pz/16);z=1-(float)(px/16);nx=c;nz=-a;}
                case EAST->{x=1-(float)(pz/16);z=(float)(px/16);nx=-c;nz=a;}
                default->{}
            }
            pose.pose().transformPosition(x,y,z,position);pose.transformNormal(nx,ny,nz,normal);
            target.addVertex(position.x,position.y,position.z).setColor(255,255,255,255).setUv(texture.u(u),texture.v(v)).setOverlay(overlay).setLight(light).setNormal(normal.x,normal.y,normal.z);
        }
    }
}
