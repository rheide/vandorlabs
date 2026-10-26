package com.vandorlabs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import java.lang.reflect.*;

/** Vanilla calls updateController after its voxel raycast and before processing held input. */
final class OffsetTrapdoorController extends PlayerControllerMP {
    OffsetTrapdoorController(Minecraft mc,PlayerControllerMP previous) {
        super(mc,mc.getConnection());
        try {
            // Preserve game mode, selected slot, and any breaking state on installation.
            for(Field field:PlayerControllerMP.class.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
                field.setAccessible(true);field.set(this,field.get(previous));
            }
        } catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot preserve client controller state",e);}
    }
    private com.vandorlabs.gear.ArmPayloadHarvest.Target miningPayload;
    private boolean minePayload(){
        net.minecraft.util.math.RayTraceResult hit=Minecraft.getMinecraft().objectMouseOver;
        if(hit==null||!(hit.hitInfo instanceof com.vandorlabs.gear.ArmPayloadHarvest.Target))return false;
        miningPayload=(com.vandorlabs.gear.ArmPayloadHarvest.Target)hit.hitInfo;
        com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageArmPayloadDig(miningPayload.root,miningPayload.cell,false));return true;
    }
    @Override public boolean clickBlock(net.minecraft.util.math.BlockPos pos,net.minecraft.util.EnumFacing face){return minePayload()||super.clickBlock(pos,face);}
    @Override public boolean onPlayerDamageBlock(net.minecraft.util.math.BlockPos pos,net.minecraft.util.EnumFacing face){return minePayload()||super.onPlayerDamageBlock(pos,face);}
    @Override public void resetBlockRemoving(){if(miningPayload!=null){com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageArmPayloadDig(miningPayload.root,miningPayload.cell,true));miningPayload=null;}super.resetBlockRemoving();}
    @Override public void updateController() {
        super.updateController();OffsetTrapdoorSelection.updateTarget(1);
    }
    @Override public net.minecraft.util.EnumActionResult processRightClickBlock(
            net.minecraft.client.entity.EntityPlayerSP player,net.minecraft.client.multiplayer.WorldClient world,
            net.minecraft.util.math.BlockPos pos,net.minecraft.util.EnumFacing side,
            net.minecraft.util.math.Vec3d hit,net.minecraft.util.EnumHand hand) {
        net.minecraft.tileentity.TileEntity gear=world.getTileEntity(pos);
        if(hand==net.minecraft.util.EnumHand.MAIN_HAND&&gear instanceof com.vandorlabs.gear.GearTileEntity){
            com.vandorlabs.gear.GearTileEntity root=((com.vandorlabs.gear.GearTileEntity)gear).loadedController();
            if(root!=null&&!root.fullyDeployed()){super.updateController();com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageModularGearUse(pos));return net.minecraft.util.EnumActionResult.SUCCESS;}
        }
        com.vandorlabs.tiles.TileEntityCanopy tile=com.vandorlabs.tiles.TileEntityCanopy.at(world,pos);
        if(tile!=null && player.getDistanceSqToCenter(tile.owner().getPos())>49){
            super.updateController();
            com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageCanopyUse(tile.owner().getPos(),hand,hit));
            return net.minecraft.util.EnumActionResult.SUCCESS;
        }
        return super.processRightClickBlock(player,world,pos,side,hit,hand);
    }
}
