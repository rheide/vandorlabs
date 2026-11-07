package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.light.ProgrammableLightMesh;
import com.vandorlabs.segmented.SegmentedPanelMesh;
import net.minecraft.core.Direction;

/** Shared source records through the active consumer; cardinal transforms never capture worlds or formats. */
public final class NativeLightMesh {
    private final float[] data;private final int[] colors;
    public NativeLightMesh(ProgrammableLightMesh mesh,Direction facing,SegmentedPanelMesh.Texture texture){
        java.util.Objects.requireNonNull(mesh);java.util.Objects.requireNonNull(facing);java.util.Objects.requireNonNull(texture);
        data=new float[mesh.vertices()*8];colors=new int[mesh.vertices()];java.util.Arrays.fill(colors,-1);
        mesh.emit(new Preparation(data,facing,texture));
    }
    public long retainedBytes(){return 64L+data.length*4L+colors.length*4L;}public int vertices(){return colors.length;}
    public void emit(PoseStack.Pose pose,VertexConsumer target,int light,int overlay){NativePreparedVertices.emit(data,colors,colors.length,pose,target,light,overlay);}
    /** Facing and atlas coordinates are immutable within the owning bake generation. */
    private static final class Preparation implements ProgrammableLightMesh.Sink {
        final float[] data;final Direction facing;final SegmentedPanelMesh.Texture texture;int at;
        Preparation(float[] data,Direction facing,SegmentedPanelMesh.Texture texture){this.data=data;this.facing=facing;this.texture=texture;}
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
            data[at++]=x;data[at++]=y;data[at++]=z;data[at++]=texture.u(u);data[at++]=texture.v(v);data[at++]=nx;data[at++]=ny;data[at++]=nz;
        }
    }
}
