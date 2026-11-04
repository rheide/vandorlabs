package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameType;

/** Real wand packets must rebuild rectangular artwork after each extrusion. */
final class SurfaceWandLiveChecks {
    private static final BlockPos BASE=new BlockPos(1880,120,128);
    private static int stage,ticks,depth=1;
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("surface-wand stage="+stage+" depth="+depth+": "+why);}
    static boolean tick(Minecraft mc)throws Exception{
        if(stage==4)return true;
        mc.displayGuiScreen(null);
        EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
        if(stage==0){mc.getIntegratedServer().addScheduledTask(()->{
            for(BlockPos p:BlockPos.getAllInBox(BASE.add(-2,-1,-2),BASE.add(9,2,6)))actor.world.setBlockToAir(p);
            net.minecraft.block.Block block=net.minecraft.block.Block.getBlockFromName("vandorlabs:turntable_surface");
            for(int x=0;x<7;x++)actor.world.setBlockState(BASE.east(x),block.getDefaultState(),3);
            Item wand=Item.REGISTRY.getObject(new ResourceLocation("betterbuilderswands:wanddiamond"));if(wand==null)wand=Item.REGISTRY.getObject(new ResourceLocation("betterbuilderswands:wandDiamond"));require(wand!=null,"installed wand required");
            actor.setGameType(GameType.CREATIVE);actor.inventory.clear();actor.inventory.currentItem=0;actor.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(wand));actor.capabilities.allowFlying=true;actor.capabilities.isFlying=true;actor.sendPlayerAbilities();actor.connection.setPlayerLocation(BASE.getX()+3.5,BASE.getY()+.5,BASE.getZ()-2,0,(float)Math.toDegrees(Math.atan2(actor.getEyeHeight(),2)));actor.sendContainerToPlayer(actor.inventoryContainer);
        }).get();mc.playerController.setGameType(GameType.CREATIVE);mc.player.inventory.currentItem=0;stage=1;ticks=0;return false;}
        if(ticks==180)System.out.println("[vandorlabs] surface-wand diagnostic "+mc.getIntegratedServer().callFromMainThread(()->{StringBuilder out=new StringBuilder("wand="+actor.getHeldItemMainhand().getTagCompound());for(int z=-3;z<=1;z++){out.append(" z=").append(z);for(int x=0;x<7;x++){net.minecraft.tileentity.TileEntity t=actor.world.getTileEntity(BASE.add(x,0,z));out.append(t instanceof TileEntityTurntableSurface?((TileEntityTurntableSurface)t).appearanceLayout().cells.size():"-").append(',');}}return out.toString();}).get());
        require(++ticks<240,"client/server setup timeout mode="+mc.playerController.getCurrentGameType()+" held="+mc.player.getHeldItemMainhand()+" slot="+mc.player.inventory.currentItem);
        if(stage==1||stage==3){
            if(ticks<20)return false;
            boolean ready=mc.getIntegratedServer().callFromMainThread(()->{for(int x=0;x<7;x++)for(int z=0;z<depth;z++){TileEntityTurntableSurface t=(TileEntityTurntableSurface)actor.world.getTileEntity(BASE.add(x,0,-z));if(t==null||t.appearanceLayout().cells.size()!=7*depth)return false;}return true;}).get();
            if(!ready)return false;
            for(int x=0;x<7;x++)for(int z=0;z<depth;z++){TileEntityTurntableSurface t=(TileEntityTurntableSurface)mc.world.getTileEntity(BASE.add(x,0,-z));if(t==null||t.appearanceLayout().cells.size()!=7*depth)return false;}
            if(depth==3){System.out.println("[vandorlabs] surface-wand PASS actual packets 7x1 -> 7x2 -> 7x3 server/client shared roots");stage=4;return true;}
            if(!com.vandorlabs.compat.BetterBuildersWandsCompat.isBbwWand(mc.player.getHeldItemMainhand())||mc.player.inventory.currentItem!=0||mc.playerController.getCurrentGameType()!=GameType.CREATIVE)return false;
            BlockPos edge=BASE.add(3,0,1-depth);mc.playerController.processRightClickBlock(mc.player,mc.world,edge,EnumFacing.NORTH,new Vec3d(edge).addVector(.5,.5,0),EnumHand.MAIN_HAND);stage=2;ticks=0;return false;
        }
        if(stage==2&&ticks>=20){depth++;mc.getIntegratedServer().addScheduledTask(()->actor.connection.setPlayerLocation(BASE.getX()+3.5,BASE.getY()+.5,BASE.getZ()-depth-1,0,(float)Math.toDegrees(Math.atan2(actor.getEyeHeight(),2)))).get();stage=3;ticks=0;}return false;
    }
    private SurfaceWandLiveChecks(){}
}
