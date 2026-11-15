package com.vandorlabs.gear.client;

import com.vandorlabs.gear.*;
import com.vandorlabs.tiles.TileEntityConstructor;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;

/** One cosmetic owner per main frame across separately delivered block, tile and arm packets. */
public final class CoreArmHandoff {
    private static final class Gate {UUID epoch;long checked=-1,switchAt=Long.MAX_VALUE;boolean nativeOwner;}
    private static final class Live {final WeakReference<TileEntityConstructor> tile;AxisAlignedBB bounds;Live(TileEntityConstructor tile){this.tile=new WeakReference<>(tile);bounds=tile.getRenderBoundingBox();}}
    private static final Map<GearTileEntity,List<UUID>> INDEXED=new WeakHashMap<>();
    private static final LinkedHashMap<UUID,WeakReference<GearTileEntity>> OWNERS=new LinkedHashMap<>();
    private static final WeakHashMap<TileEntityConstructor,Live> LIVE=new WeakHashMap<>();private static Live[] live=new Live[0];private static boolean dirty;
    private static final Set<TileEntityConstructor> DRAWN=Collections.newSetFromMap(new IdentityHashMap<>());private static long frame;
    private static boolean physical(TileEntity tile){Minecraft mc=Minecraft.getMinecraft();return mc.isCallingFromMinecraftThread()&&tile.getWorld()==mc.world&&mc.world!=null&&mc.world.isBlockLoaded(tile.getPos(),false)&&mc.world.getTileEntity(tile.getPos())==tile&&!tile.isInvalid();}
    public static void register(TileEntityConstructor tile){if(!physical(tile))return;Live entry=LIVE.get(tile);if(entry==null){if(LIVE.size()>=2048)return;LIVE.put(tile,new Live(tile));dirty=true;}else entry.bounds=tile.getRenderBoundingBox();}
    public static void changed(GearTileEntity arm){if(!physical(arm))return;List<UUID> old=INDEXED.remove(arm);if(old!=null)for(UUID id:old){WeakReference<GearTileEntity> ref=OWNERS.get(id);if(ref!=null&&ref.get()==arm)OWNERS.remove(id);}ArmCoreRenderTransfer transfer=arm.coreRenderTransfer;if(transfer==null)return;List<UUID> ids=new ArrayList<>();for(ArmCoreRenderTransfer.Entry e:transfer.entries){if(e.source!=null)ids.add(e.source);if(e.destination!=null)ids.add(e.destination);}for(UUID id:ids){if(OWNERS.size()>=8192)OWNERS.remove(OWNERS.keySet().iterator().next());OWNERS.put(id,new WeakReference<>(arm));}INDEXED.put(arm,ids);}
    public static void beginFrame(){frame++;DRAWN.clear();}
    private static Gate gate(GearTileEntity arm){ArmCoreRenderTransfer transfer=arm.coreRenderTransfer;Gate gate=arm.coreVisualHandoff instanceof Gate?(Gate)arm.coreVisualHandoff:null;if(gate==null||!transfer.epoch.equals(gate.epoch)){gate=new Gate();gate.epoch=transfer.epoch;arm.coreVisualHandoff=gate;}if(gate.checked==frame)return gate;gate.checked=frame;
        if(gate.nativeOwner)return gate;
        ArmPayload payload=arm.payload();boolean ready=payload!=null&&payload.materialized()&&!payload.hasOwnershipJournal()&&arm.isOperational()&&Math.abs(GearRenderer.renderedProgress(arm,1)-arm.progress())<1e-8;
        if(ready)for(ArmCoreRenderTransfer.Entry entry:transfer.entries){if(entry.destination==null||entry.at==null||!arm.getWorld().isBlockLoaded(entry.at,false)){ready=false;break;}TileEntity tile=arm.getWorld().getTileEntity(entry.at);if(!(tile instanceof TileEntityConstructor)){ready=false;break;}TileEntityConstructor core=(TileEntityConstructor)tile;if(!core.renderAppearanceReady()||!entry.destination.equals(core.renderIdentity())||!transfer.epoch.equals(core.renderEpoch)||!arm.getPos().equals(core.renderOwner)||core.renderCell!=entry.index){ready=false;break;}}
        if(ready){if(gate.switchAt==Long.MAX_VALUE)gate.switchAt=frame+1;if(frame>=gate.switchAt)gate.nativeOwner=true;}else gate.switchAt=Long.MAX_VALUE;return gate;
    }
    public static boolean fallback(GearTileEntity arm){return physical(arm)&&arm.coreRenderTransfer!=null&&arm.payload()!=null&&!gate(arm).nativeOwner;}
    public static boolean allowed(TileEntityConstructor tile){if(tile.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld)return true;if(!physical(tile)||!tile.renderAppearanceReady())return false;
        GearTileEntity arm=null;if(tile.renderOwner!=null&&tile.getWorld().isBlockLoaded(tile.renderOwner,false)){TileEntity raw=tile.getWorld().getTileEntity(tile.renderOwner);if(raw instanceof GearTileEntity){GearTileEntity candidate=(GearTileEntity)raw;if(candidate.coreRenderTransfer!=null&&candidate.coreRenderTransfer.epoch.equals(tile.renderEpoch))arm=candidate;}}
        if(arm==null){WeakReference<GearTileEntity> ref=OWNERS.get(tile.renderIdentity());if(ref!=null){GearTileEntity candidate=ref.get();if(candidate!=null&&physical(candidate)&&candidate.coreRenderTransfer!=null&&candidate.coreRenderTransfer.containsIdentity(tile.renderIdentity()))arm=candidate;}}
        return arm==null||arm.coreRenderTransfer==null||gate(arm).nativeOwner;
    }
    public static void drawn(TileEntityConstructor tile){if(physical(tile)&&!com.vandorlabs.client.VehicleRenderPass.shadow())DRAWN.add(tile);}
    static void renderMissing(float partial){if(com.vandorlabs.client.VehicleRenderPass.shadow())return;Minecraft mc=Minecraft.getMinecraft();if(mc.world==null||mc.getRenderViewEntity()==null)return;if(dirty){live=LIVE.values().toArray(new Live[0]);dirty=false;}net.minecraft.entity.Entity camera=mc.getRenderViewEntity();double x=camera.lastTickPosX+(camera.posX-camera.lastTickPosX)*partial,y=camera.lastTickPosY+(camera.posY-camera.lastTickPosY)*partial,z=camera.lastTickPosZ+(camera.posZ-camera.lastTickPosZ)*partial;Frustum frustum=null;com.vandorlabs.client.TurntableRenderState state=null;
        try{for(Live entry:live){TileEntityConstructor tile=entry.tile.get();if(tile==null||DRAWN.contains(tile)||!physical(tile)||!tile.renderReady()||!allowed(tile))continue;AxisAlignedBB box=entry.bounds;if(GearRenderAdmission.distanceSquared(box,x,y,z)>tile.getMaxRenderDistanceSquared())continue;if(frustum==null){frustum=new Frustum();frustum.setPosition(x,y,z);}if(!frustum.isBoundingBoxInFrustum(box))continue;if(state==null){mc.entityRenderer.enableLightmap();state=new com.vandorlabs.client.TurntableRenderState();}TileEntityRendererDispatcher.instance.render(tile,tile.getPos().getX()-x,tile.getPos().getY()-y,tile.getPos().getZ()-z,partial,-1,1);}}
        finally{if(state!=null){state.close();mc.entityRenderer.disableLightmap();}}
    }
    public static int retainedAddresses(){return OWNERS.size();}public static int retainedTiles(){return LIVE.size();}
    public static void clear(){INDEXED.clear();OWNERS.clear();LIVE.clear();live=new Live[0];dirty=false;DRAWN.clear();}
    private CoreArmHandoff(){}
}
