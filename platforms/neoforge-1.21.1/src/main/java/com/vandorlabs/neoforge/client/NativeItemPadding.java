package com.vandorlabs.neoforge.client;

import com.vandorlabs.render.ControlItemProjection;
import com.vandorlabs.render.ControlMount.Point;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Native item-transform boundary over the same projected padding policy as Forge. */
public final class NativeItemPadding {
    public static ItemTransforms fit(BakedModel model,ItemTransforms source){
        ItemTransform gui=source.gui;double x0=Double.POSITIVE_INFINITY,y0=x0,x1=Double.NEGATIVE_INFINITY,y1=x1;
        for(Direction side:new Direction[]{null,Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST})for(BakedQuad quad:model.getQuads(null,side,RandomSource.create(0),ModelData.EMPTY,null)){
            int[] data=quad.getVertices();int stride=data.length/4;
            for(int i=0;i<4;i++){int at=i*stride;Point p=ControlItemProjection.project(new Point(Float.intBitsToFloat(data[at]),Float.intBitsToFloat(data[at+1]),Float.intBitsToFloat(data[at+2])),gui.rotation.x,gui.rotation.y,gui.rotation.z,gui.scale.x,gui.scale.y,gui.scale.z);x0=Math.min(x0,p.x);x1=Math.max(x1,p.x);y0=Math.min(y0,p.y);y1=Math.max(y1,p.y);}
        }
        if(!Double.isFinite(x0))return source;
        float scale=ControlItemProjection.scale(x0,y0,x1,y1);ItemTransform padded=new ItemTransform(gui.rotation,new org.joml.Vector3f((float)(-(x0+x1)*scale/2),(float)(-(y0+y1)*scale/2),gui.translation.z),new org.joml.Vector3f(gui.scale).mul(scale));
        return new ItemTransforms(source.thirdPersonLeftHand,source.thirdPersonRightHand,source.firstPersonLeftHand,source.firstPersonRightHand,source.head,padded,source.ground,source.fixed);
    }
    private NativeItemPadding(){}
}
