package com.vandorlabs.neoforge;

import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.model.data.ModelData;

/** One native adapter for the current GUI-space padding, including rotated/deep shape items. */
final class NativeItemPadding {
    private static final Direction[] FACES={null,Direction.UP,Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST};
    static ItemTransforms fit(BakedModel model,ItemTransforms base){
        var gui=base.gui;double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
        for(Direction face:FACES)for(var q:model.getQuads(null,face,RandomSource.create(0),ModelData.EMPTY,null)){
            int[] raw=q.getVertices();int stride=raw.length/4;for(int i=0;i<4;i++){
                double x=(Float.intBitsToFloat(raw[i*stride])-.5)*gui.scale.x(),y=(Float.intBitsToFloat(raw[i*stride+1])-.5)*gui.scale.y(),z=(Float.intBitsToFloat(raw[i*stride+2])-.5)*gui.scale.z();
                double rz=Math.toRadians(gui.rotation.z()),rx=Math.toRadians(gui.rotation.x()),ry=Math.toRadians(gui.rotation.y());double xx=x*Math.cos(rz)-y*Math.sin(rz);y=x*Math.sin(rz)+y*Math.cos(rz);x=xx;double xxY=x*Math.cos(ry)+z*Math.sin(ry);z=z*Math.cos(ry)-x*Math.sin(ry);x=xxY;y=y*Math.cos(rx)-z*Math.sin(rx);
                minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
            }
        }
        if(!Double.isFinite(minX))return base;float scale=(float)Math.min(.75/Math.max(maxX-minX,maxY-minY),.85);
        var guiFit=new ItemTransform(gui.rotation,new org.joml.Vector3f((float)(-(minX+maxX)*scale/2),(float)(-(minY+maxY)*scale/2),gui.translation.z()),new org.joml.Vector3f(gui.scale).mul(scale));
        return new ItemTransforms(base.thirdPersonLeftHand,base.thirdPersonRightHand,base.firstPersonLeftHand,base.firstPersonRightHand,base.head,guiFit,base.ground,base.fixed,base.moddedTransforms);
    }
    private NativeItemPadding(){}
}
