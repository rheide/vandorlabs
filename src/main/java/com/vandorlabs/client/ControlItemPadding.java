package com.vandorlabs.client;

import net.minecraft.client.renderer.block.model.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.util.vector.Vector3f;

/** Fit configured 3D controls after GUI rotation, including their extended mounting depth. */
final class ControlItemPadding {
    static Vec3d project(Vec3d point,ItemTransformVec3f gui){
        double x=(point.x-.5)*gui.scale.x,y=(point.y-.5)*gui.scale.y,z=(point.z-.5)*gui.scale.z;
        double rz=Math.toRadians(gui.rotation.z),rx=Math.toRadians(gui.rotation.x),ry=Math.toRadians(gui.rotation.y);
        double xx=x*Math.cos(rz)-y*Math.sin(rz);y=x*Math.sin(rz)+y*Math.cos(rz);x=xx;
        double xxY=x*Math.cos(ry)+z*Math.sin(ry);z=z*Math.cos(ry)-x*Math.sin(ry);x=xxY;
        return new Vec3d(x,y*Math.cos(rx)-z*Math.sin(rx),0);
    }
    static ItemCameraTransforms fit(IBakedModel model,ItemCameraTransforms source){
        ItemTransformVec3f gui=source.gui;double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
        for(EnumFacing side:new EnumFacing[]{null,EnumFacing.UP,EnumFacing.DOWN,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.EAST,EnumFacing.WEST})for(BakedQuad q:model.getQuads(null,side,0)){
            int[] data=q.getVertexData();int stride=data.length/4;
            for(int i=0;i<4;i++){Vec3d p=project(new Vec3d(Float.intBitsToFloat(data[i*stride]),Float.intBitsToFloat(data[i*stride+1]),Float.intBitsToFloat(data[i*stride+2])),gui);minX=Math.min(minX,p.x);maxX=Math.max(maxX,p.x);minY=Math.min(minY,p.y);maxY=Math.max(maxY,p.y);}
        }
        if(!Double.isFinite(minX))return source;
        float scale=(float)Math.min(.75/Math.max(maxX-minX,maxY-minY),.85);
        ItemTransformVec3f padded=new ItemTransformVec3f(gui.rotation,new Vector3f((float)(-(minX+maxX)*scale/2),(float)(-(minY+maxY)*scale/2),gui.translation.z),new Vector3f(gui.scale.x*scale,gui.scale.y*scale,gui.scale.z*scale));
        return new ItemCameraTransforms(source.thirdperson_left,source.thirdperson_right,source.firstperson_left,source.firstperson_right,source.head,padded,source.ground,source.fixed);
    }
    private ControlItemPadding(){}
}
