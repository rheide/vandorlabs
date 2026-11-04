package com.vandorlabs.client;

import com.vandorlabs.segmented.*;
import com.vandorlabs.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameType;

/** A real client click on a moved middle panel closes a five-by-four door. */
final class SegmentedDoorInteractionChecks {
    private static final BlockPos BASE=new BlockPos(1260,110,1200),PART=BASE.east(2).up();
    private static int stage,ticks;
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("segmented moving click stage="+stage+": "+why);}
    static boolean tick(Minecraft mc)throws Exception{
        EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
        if(stage==0){mc.displayGuiScreen(null);mc.getIntegratedServer().addScheduledTask(()->{
            for(BlockPos p:BlockPos.getAllInBox(BASE.add(-2,-2,-3),BASE.add(12,5,3)))actor.world.setBlockToAir(p);
            net.minecraft.block.Block door=net.minecraft.block.Block.getBlockFromName("vandorlabs:segmented_door");for(int x=0;x<5;x++)for(int y=0;y<4;y++)actor.world.setBlockState(BASE.add(x,y,0),door.getDefaultState(),3);for(int i=0;i<4;i++)DeferredTileLoad.flush(actor.world);
            SegmentedDoorSettings windows=new SegmentedDoorSettings(3,true,0,0,true,2,false,SegmentedDoorSettings.DEFAULT.glass);
            for(int x=0;x<5;x++)SegmentedDoorTile.at(actor.world,BASE.east(x).up(3)).configurePart(actor,com.vandorlabs.redstone.ChannelList.EMPTY,5,false,true,-1,windows);
            SegmentedDoorTile.at(actor.world,BASE.up(3)).operate(actor,true);
            actor.setGameType(GameType.CREATIVE);actor.inventory.clear();actor.inventory.currentItem=0;actor.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);actor.capabilities.allowFlying=true;actor.capabilities.isFlying=true;actor.sendPlayerAbilities();actor.connection.setPlayerLocation(BASE.getX()+7.4375,BASE.getY()+1.5-actor.getEyeHeight(),BASE.getZ()-2,0,0);actor.sendContainerToPlayer(actor.inventoryContainer);
        }).get();mc.playerController.setGameType(GameType.CREATIVE);mc.player.inventory.currentItem=0;stage=1;ticks=0;return false;}
        require(++ticks<300,"state synchronization timeout mode="+mc.playerController.getCurrentGameType()+" slot="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand()+" pos="+mc.player.getPositionVector());
        if(stage==1){SegmentedDoorTile t=SegmentedDoorTile.at(mc.world,PART);if(t==null||!t.ready()||!t.targetOpen||com.vandorlabs.client.segmented.SegmentedDoorVisuals.pose(t.root(),1)<.999||!mc.player.getHeldItemMainhand().isEmpty()||mc.player.inventory.currentItem!=0||mc.player.getDistanceSq(BASE.getX()+7.4375,BASE.getY()+1.5-mc.player.getEyeHeight(),BASE.getZ()-2)>.05)return false;
            Vec3d eye=mc.player.getPositionEyes(1);RayTraceResult hit=SegmentedDoorInteractions.trace(mc.world,eye,eye.addVector(0,0,5));require(hit!=null&&PART.equals(hit.getBlockPos()),"ray must select the visible moved middle segment");mc.playerController.processRightClickBlock(mc.player,mc.world,PART,hit.sideHit,hit.hitVec,EnumHand.MAIN_HAND);stage=2;ticks=0;return false;}
        if(stage==2){if(ticks<10)return false;boolean closed=mc.getIntegratedServer().callFromMainThread(()->{
            require(!SegmentedDoorTile.at(actor.world,PART).settings.window,"middle row stays solid");for(int x=0;x<5;x++){SegmentedDoorTile top=SegmentedDoorTile.at(actor.world,BASE.east(x).up(3));require(top.settings.window,"individual top-row openings survive motion");if(top.targetOpen)return false;}return true;
        }).get();if(!closed)return false;System.out.println("[vandorlabs][reprolab] segmented-moving-click PASS actual client packet five-by-four independent top windows straight slide one-pixel exposed edge visible middle panel closes");mc.getIntegratedServer().addScheduledTask(()->{actor.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(net.minecraft.init.Items.WATER_BUCKET));actor.connection.setPlayerLocation(PART.getX()+.5,BASE.getY()+1.5-actor.getEyeHeight(),BASE.getZ()-2,0,0);actor.sendContainerToPlayer(actor.inventoryContainer);}).get();stage=3;ticks=0;return false;}
        SegmentedDoorTile tile=SegmentedDoorTile.at(mc.world,PART);
        if(stage==3){if(tile==null||com.vandorlabs.client.segmented.SegmentedDoorVisuals.pose(tile.root(),1)>.001||mc.player.getHeldItemMainhand().getItem()!=net.minecraft.init.Items.WATER_BUCKET||mc.player.getDistanceSq(PART.getX()+.5,BASE.getY()+1.5-mc.player.getEyeHeight(),BASE.getZ()-2)>.05)return false;click(mc,tile);stage=4;ticks=0;return false;}
        if(stage==4){boolean stored=mc.getIntegratedServer().callFromMainThread(()->{SegmentedDoorTile part=SegmentedDoorTile.at(actor.world,PART);return part.fluids.any()&&part.waterOutlets==0;}).get();if(!stored||tile==null||!tile.fluids.any())return false;require(tile.shouldRenderInPass(1)&&!tile.hasFastRenderer(),"wet middle segment participates in liquid rendering");mc.getIntegratedServer().addScheduledTask(()->{actor.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);actor.sendContainerToPlayer(actor.inventoryContainer);}).get();stage=5;ticks=0;return false;}
        if(stage==5){if(tile==null||!mc.player.getHeldItemMainhand().isEmpty())return false;click(mc,tile);stage=6;ticks=0;return false;}
        if(stage==6){boolean supplying=mc.getIntegratedServer().callFromMainThread(()->SegmentedDoorTile.at(actor.world,PART).waterOutlets!=0).get();boolean flowing=false;for(EnumFacing side:EnumFacing.HORIZONTALS)flowing|=mc.world.getBlockState(PART.offset(side)).getMaterial()==net.minecraft.block.material.Material.WATER;if(!supplying||!flowing)return false;mc.getIntegratedServer().addScheduledTask(()->actor.connection.setPlayerLocation(BASE.getX()+7.4375,BASE.getY()+1.5-actor.getEyeHeight(),BASE.getZ()-2,0,0)).get();stage=7;ticks=0;return false;}
        if(stage==7){if(tile==null||com.vandorlabs.client.segmented.SegmentedDoorVisuals.pose(tile.root(),1)<.999||mc.player.getDistanceSq(BASE.getX()+7.4375,BASE.getY()+1.5-mc.player.getEyeHeight(),BASE.getZ()-2)>.05)return false;click(mc,tile);stage=8;ticks=0;return false;}
        if(stage==8){boolean drained=mc.getIntegratedServer().callFromMainThread(()->{SegmentedDoorTile part=SegmentedDoorTile.at(actor.world,PART);return part.closed()&&part.waterOutlets==0&&part.fluids.any();}).get();if(!drained)return false;System.out.println("[vandorlabs][reprolab] segmented-water-packets PASS actual bucket and moved-panel clicks contained water client liquid pass native flow and closing outlet drain stored source retained");return true;}
        return false;
    }
    private static void click(Minecraft mc,SegmentedDoorTile tile){Vec3d eye=mc.player.getPositionEyes(1);RayTraceResult hit=SegmentedDoorInteractions.hit(tile,eye,eye.addVector(0,0,5));require(hit!=null,"visible wet panel ray");mc.playerController.processRightClickBlock(mc.player,mc.world,PART,hit.sideHit,hit.hitVec,EnumHand.MAIN_HAND);}
    private SegmentedDoorInteractionChecks(){}
}
