package com.vandorlabs.gear.client;

import com.vandorlabs.client.VehicleRenderPass;
import com.vandorlabs.gear.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;

/** Main-view fallback for a visible attachment whose source section was culled. */
final class GearRenderAdmission {
    private static final Set<GearTileEntity> DRAWN=Collections.newSetFromMap(new IdentityHashMap<>()),MECHANISMS=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Consumer<GearTileEntity> VISITOR=GearRenderAdmission::missing;
    private static Minecraft client;private static Frustum frustum;private static double x,y,z;private static float partial;
    private static com.vandorlabs.client.TurntableRenderState renderState;
    private static GearTileEntity hiddenPayload;private static int fallbackDraws;
    static void beginFrame(){DRAWN.clear();MECHANISMS.clear();fallbackDraws=0;}
    static void clear(){beginFrame();client=null;frustum=null;hiddenPayload=null;}
    private static boolean physical(GearTileEntity tile){return tile.getWorld()==Minecraft.getMinecraft().world&&!VehicleRenderPass.shadow();}
    static void drawn(GearTileEntity tile){if(physical(tile))DRAWN.add(tile);}
    static void mechanism(GearTileEntity tile){if(physical(tile))MECHANISMS.add(tile);}
    static boolean mechanismDrawn(GearTileEntity tile){return physical(tile)&&MECHANISMS.contains(tile);}
    static boolean payloadAllowed(GearTileEntity tile){return hiddenPayload!=tile;}
    static int fallbackDraws(){return fallbackDraws;}
    static boolean admitted(GearTileEntity tile){return DRAWN.contains(tile)||MECHANISMS.contains(tile);}
    static double distanceSquared(AxisAlignedBB b,double x,double y,double z){double dx=Math.max(b.minX-x,Math.max(0,x-b.maxX)),dy=Math.max(b.minY-y,Math.max(0,y-b.maxY)),dz=Math.max(b.minZ-z,Math.max(0,z-b.maxZ));return dx*dx+dy*dy+dz*dz;}
    static void renderMissing(float framePartial){
        if(VehicleRenderPass.shadow())return;Minecraft mc=Minecraft.getMinecraft();Entity camera=mc.getRenderViewEntity();if(mc.world==null||camera==null)return;
        client=mc;partial=framePartial;x=camera.lastTickPosX+(camera.posX-camera.lastTickPosX)*partial;y=camera.lastTickPosY+(camera.posY-camera.lastTickPosY)*partial;z=camera.lastTickPosZ+(camera.posZ-camera.lastTickPosZ)*partial;
        try{GearGeometry.visitRoots(mc.world,VISITOR);}finally{if(renderState!=null){renderState.close();renderState=null;mc.entityRenderer.disableLightmap();}client=null;frustum=null;hiddenPayload=null;}
    }
    private static boolean visible(AxisAlignedBB b,GearTileEntity tile){return b!=null&&distanceSquared(b,x,y,z)<=tile.getMaxRenderDistanceSquared()&&frustum.isBoundingBoxInFrustum(b);}
    private static void missing(GearTileEntity root){
        if(DRAWN.contains(root)||root.isInvalid()||root.getWorld()!=client.world||!client.world.isBlockLoaded(root.getPos(),false)||client.world.getTileEntity(root.getPos())!=root||!root.isOperational())return;
        ArmPayload payload=root.payload();if(payload==null||payload.materialized()||payload.hasOwnershipJournal())return;
        if(frustum==null){frustum=new Frustum();frustum.setPosition(x,y,z);}
        double progress=GearRenderer.renderedProgress(root,partial);boolean body=visible(ArmPayloadRenderer.bounds(root,progress,false),root),mechanism=visible(ArmPayloadRenderer.bounds(root,progress,true),root);if(!body&&!mechanism)return;
        if(renderState==null){client.entityRenderer.enableLightmap();renderState=new com.vandorlabs.client.TurntableRenderState();}
        GearTileEntity previous=hiddenPayload;if(!body)hiddenPayload=root;
        try{TileEntityRendererDispatcher.instance.render(root,root.getPos().getX()-x,root.getPos().getY()-y,root.getPos().getZ()-z,partial,-1,1);fallbackDraws++;}finally{hiddenPayload=previous;}
    }
    private GearRenderAdmission(){}
}
