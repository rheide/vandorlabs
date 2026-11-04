package com.vandorlabs.client;

import com.vandorlabs.segmented.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameType;

/** Actual Minecraft middle-click, inventory packet, then creative and survival placement. */
final class SegmentedDoorPickLiveChecks {
    private static final BlockPos SOURCE=new BlockPos(1300,115,1300),FIRST=SOURCE.east(3),SECOND=SOURCE.east(6);
    private static final SegmentedDoorSettings SETTINGS=new SegmentedDoorSettings(3,true,2,0,true,2,true,new DoorGlassAppearance(-1,2,3,2),2);
    private static int stage,ticks;
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("segmented pick stage="+stage+": "+why);}
    static boolean tick(Minecraft mc)throws Exception{
        if(stage==7)return true;
        EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
        if(stage==0){mc.displayGuiScreen(null);mc.getIntegratedServer().addScheduledTask(()->{
            for(BlockPos p:BlockPos.getAllInBox(SOURCE.add(-1,-1,-3),SECOND.add(1,3,1)))actor.world.setBlockToAir(p);
            net.minecraft.block.Block block=net.minecraft.block.Block.getBlockFromName("vandorlabs:segmented_door");actor.world.setBlockState(SOURCE,block.getDefaultState(),3);for(int n=0;n<3;n++)DeferredTileLoad.flush(actor.world);
            SegmentedDoorTile.at(actor.world,SOURCE).configurePart(actor,ChannelList.of(64777,64778),20,false,false,-1,SETTINGS);
            for(BlockPos p:new BlockPos[]{SOURCE,FIRST,SECOND})actor.world.setBlockState(p.add(0,0,-2),net.minecraft.init.Blocks.STONE.getDefaultState(),3);
            actor.world.setBlockState(FIRST.down(),net.minecraft.init.Blocks.STONE.getDefaultState(),3);actor.world.setBlockState(SECOND.down(),net.minecraft.init.Blocks.STONE.getDefaultState(),3);
            actor.setGameType(GameType.CREATIVE);actor.inventory.clear();actor.inventory.currentItem=0;actor.connection.setPlayerLocation(SOURCE.getX()+.5,SOURCE.getY()+1,SOURCE.getZ()-1.5,0,0);actor.sendContainerToPlayer(actor.inventoryContainer);
        }).get();mc.playerController.setGameType(GameType.CREATIVE);mc.player.inventory.currentItem=0;stage=1;ticks=0;return false;}
        require(++ticks<300,"synchronization timeout mode="+mc.playerController.getCurrentGameType()+" slot="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand()+" pos="+mc.player.getPositionVector());
        if(stage==1){SegmentedDoorTile source=SegmentedDoorTile.at(mc.world,SOURCE);if(source==null||!source.settings.equals(SETTINGS)||!mc.player.getHeldItemMainhand().isEmpty()||mc.player.getDistanceSq(SOURCE.getX()+.5,SOURCE.getY()+1,SOURCE.getZ()-1.5)>.05)return false;
            Vec3d eye=mc.player.getPositionEyes(1);mc.objectMouseOver=SegmentedDoorInteractions.hit(source,eye,eye.add(new Vec3d(SOURCE).addVector(.5,.5,.5).subtract(eye).normalize().scale(5)));require(mc.objectMouseOver!=null,"native source panel ray");net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(Minecraft.class,"middleClickMouse","func_147112_ai").invoke(mc);stage=2;ticks=0;return false;}
        if(stage==2){boolean selected=mc.getIntegratedServer().callFromMainThread(()->validStack(actor.getHeldItemMainhand())).get();if(!selected||!validStack(mc.player.getHeldItemMainhand()))return false;move(mc,actor,FIRST,GameType.CREATIVE);stage=3;ticks=0;return false;}
        if(stage==3||stage==5){BlockPos target=stage==3?FIRST:SECOND;GameType mode=stage==3?GameType.CREATIVE:GameType.SURVIVAL;if(mc.playerController.getCurrentGameType()!=mode||!validStack(mc.player.getHeldItemMainhand())||mc.player.getDistanceSq(target.getX()+.5,target.getY()+1,target.getZ()-1.5)>.05)return false;
            mc.playerController.processRightClickBlock(mc.player,mc.world,target.down(),EnumFacing.UP,new Vec3d(target).addVector(.5,0,.5),EnumHand.MAIN_HAND);stage++;ticks=0;return false;}
        if(stage==4||stage==6){BlockPos target=stage==4?FIRST:SECOND;boolean ready=mc.getIntegratedServer().callFromMainThread(()->validTile(SegmentedDoorTile.at(actor.world,target))).get();if(!ready||!validTile(SegmentedDoorTile.at(mc.world,target)))return false;
            if(stage==4){mc.getIntegratedServer().addScheduledTask(()->{ItemStack stack=actor.getHeldItemMainhand().copy();stack.setCount(2);actor.setHeldItem(EnumHand.MAIN_HAND,stack);}).get();move(mc,actor,SECOND,GameType.SURVIVAL);stage=5;ticks=0;return false;}
            if(mc.player.getHeldItemMainhand().getCount()!=1)return false;require(mc.getIntegratedServer().callFromMainThread(()->!SegmentedDoorTile.at(actor.world,SOURCE).identity.equals(SegmentedDoorTile.at(actor.world,target).identity)).get(),"placed segment receives independent ownership");
            System.out.println("[vandorlabs][reprolab] segmented-middle-place PASS actual middle-click creative inventory packet creative/survival native placement all settings independent identity survival consumes one");stage=7;return true;}
        return false;
    }
    private static void move(Minecraft mc,EntityPlayerMP actor,BlockPos target,GameType mode)throws Exception{mc.getIntegratedServer().addScheduledTask(()->{actor.setGameType(mode);actor.inventory.currentItem=mc.player.inventory.currentItem;actor.connection.setPlayerLocation(target.getX()+.5,target.getY()+1,target.getZ()-1.5,0,0);actor.sendContainerToPlayer(actor.inventoryContainer);}).get();mc.playerController.setGameType(mode);}
    private static boolean validStack(ItemStack stack){return stack.hasTagCompound()&&stack.getSubCompound("BlockEntityTag")!=null&&SegmentedDoorSettings.read(stack.getSubCompound("BlockEntityTag")).equals(SETTINGS);}
    private static boolean validTile(SegmentedDoorTile tile){return tile!=null&&tile.settings.equals(SETTINGS)&&tile.ticksPerSection==20&&!tile.localRedstone&&!tile.signalOpens&&tile.getRedstoneChannels().equals(ChannelList.of(64777,64778));}
    private SegmentedDoorPickLiveChecks(){}
}
