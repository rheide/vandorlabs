package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.items.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** Native client interaction and packet round trips across the visible empty recess. */
final class MachineMountClientChecks {
    private static final BlockPos ROOT=new BlockPos(42,80,24);
    private static final String[] DESIGNS={"standalone_dish","meridian_core","oculus_drive","longburn_drive"};
    private static int fixture,point,stage,ticks;
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("Machine mount client: "+why);}
    static boolean tick(Minecraft mc)throws Exception{
        if(fixture==8)return true;
        WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUsername(mc.player.getName());
        boolean creative=fixture>=4;
        if(stage==0){
            mc.getIntegratedServer().addScheduledTask(()->{
                world.setBlockToAir(ROOT);for(BlockPos p:BlockPos.getAllInBox(ROOT.add(-1,-1,-1),ROOT.add(5,5,5)))world.setBlockToAir(p);
                FrameMachineDesign spec=FrameMachineDesign.get(DESIGNS[fixture%4]);BridgeMachineChecks.frame(world,ROOT,spec,0);BridgeMachineChecks.frame(world,ROOT,spec,2);
                TileEntityConstructor tile=(TileEntityConstructor)world.getTileEntity(ROOT);require(tile.assemble(),"fixture assembles "+spec.design);
                player.setGameType(creative?net.minecraft.world.GameType.CREATIVE:net.minecraft.world.GameType.SURVIVAL);player.inventory.currentItem=0;player.connection.sendPacket(new net.minecraft.network.play.server.SPacketHeldItemChange(0));player.capabilities.allowFlying=true;player.capabilities.isFlying=true;player.sendPlayerAbilities();player.setSneaking(false);
                MachineMountSurface mount=tile.mount();player.connection.setPlayerLocation(mount.center.x+mount.normal.x*2.5,ROOT.getY(),mount.center.z+mount.normal.z*2.5,0,0);
            }).get();mc.playerController.setGameType(creative?net.minecraft.world.GameType.CREATIVE:net.minecraft.world.GameType.SURVIVAL);mc.player.inventory.currentItem=0;mc.displayGuiScreen(null);point=0;stage=1;ticks=0;return false;
        }
        if(++ticks<10)return false;
        require(ticks<600,"bounded synchronization fixture="+fixture+" point="+point+" stage="+stage+" held="+mc.player.getHeldItemMainhand().getCount()+" root="+mc.world.getTileEntity(ROOT));
        if(stage==1){
            if(!(mc.world.getTileEntity(ROOT) instanceof TileEntityConstructor))return false;
            TileEntityConstructor tile=(TileEntityConstructor)mc.world.getTileEntity(ROOT);if(tile.mount()==null)return false;
            mc.getIntegratedServer().addScheduledTask(()->{((TileEntityConstructor)world.getTileEntity(ROOT)).takeForDrop();player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(ModItems.DATA_CHIP,2,4));player.setHeldItem(EnumHand.OFF_HAND,ItemStack.EMPTY);player.inventoryContainer.detectAndSendChanges();}).get();stage=2;ticks=0;return false;
        }
        if(stage==2){
            if(mc.player.getHeldItemMainhand().getCount()!=2||mc.playerController.isInCreativeMode()!=creative)return false;
            MachineMountSurface mount=((TileEntityConstructor)mc.world.getTileEntity(ROOT)).mount();Vec3d target=mount.center.add(mount.u.scale((point%3-1)*.12)).add(mount.v.scale((point/3-1)*.12));Vec3d delta=target.subtract(mc.player.getPositionEyes(1));
            float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));mc.player.rotationYaw=mc.player.prevRotationYaw=yaw;mc.player.rotationPitch=mc.player.prevRotationPitch=pitch;
            mc.getConnection().sendPacket(new net.minecraft.network.play.client.CPacketPlayer.Rotation(yaw,pitch,false));
            Vec3d start=mc.player.getPositionEyes(1);RayTraceResult hit=mc.world.rayTraceBlocks(start,start.add(delta.normalize().scale(5)),false,false,false);require(hit!=null,"visible recess ray reaches model");
            require(mc.playerController.processRightClickBlock(mc.player,mc.world,hit.getBlockPos(),hit.sideHit,hit.hitVec,EnumHand.MAIN_HAND)==EnumActionResult.SUCCESS,"client consumes click on "+DESIGNS[fixture%4]+" point="+point+" hit="+hit.getBlockPos());stage=3;ticks=0;return false;
        }
        mc.getIntegratedServer().addScheduledTask(()->{TileEntityConstructor tile=(TileEntityConstructor)world.getTileEntity(ROOT);require(!tile.isEmpty()&&tile.getStackInSlot(0).getMetadata()==4,"chip retained after normal packet on "+DESIGNS[fixture%4]+" point="+point);require(player.getHeldItemMainhand().getCount()==(creative?2:1),"native inventory accounting fixture="+fixture+" point="+point+" creative="+creative+" serverSlot="+player.inventory.currentItem+" serverCount="+player.getHeldItemMainhand().getCount()+" clientSlot="+mc.player.inventory.currentItem+" clientCount="+mc.player.getHeldItemMainhand().getCount());}).get();
        if(++point==9){fixture++;stage=0;if(fixture==8)System.out.println("[vandorlabs] machine-mount-client PASS 72 native packet clicks four affected machines survival/creative blank recess center/edges/corners retained chips");}else stage=1;ticks=0;return fixture==8;
    }
}
