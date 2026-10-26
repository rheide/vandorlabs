package com.vandorlabs.gear;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.*;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.fml.relauncher.*;

/** Actual Creative click and Survival held attack on a retracted payload. */
@SideOnly(Side.CLIENT)
final class ArmPayloadMiningRuntimeChecks {
    private static final BlockPos ROOT=new BlockPos(1103,100,0);
    private static int stage,ticks,fixture;
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("arm-payload-mining fixture="+fixture+" stage="+stage+": "+why);}
    static boolean tick(Minecraft mc)throws Exception {
        if(fixture>=3)return true;
        if(mc.currentScreen!=null)mc.displayGuiScreen(null);ticks++;require(ticks<700,"bounded mining/checkpoint completion");
        if(stage==0){mc.getIntegratedServer().callFromMainThread(()->{
            WorldServer w=mc.getIntegratedServer().getWorld(0);EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);p.setGameType(GameType.CREATIVE);p.inventory.currentItem=0;p.capabilities.isFlying=true;p.sendPlayerAbilities();p.connection.setPlayerLocation(ROOT.getX()+3.5,101,1.5,90,0);
            for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-8,-1,-8),ROOT.add(8,5,8)))w.setBlockState(at,at.getY()==99?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);
            p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(GearRegistry.GEAR_EXTENDER,2));for(BlockPos at:new BlockPos[]{ROOT.down(),ROOT})require(p.getHeldItemMainhand().onItemUse(p,w,at,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,1,.5F)==EnumActionResult.SUCCESS,"place stacked arms");
            p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(fixture==0?Blocks.STONE:Blocks.CHEST));require(p.getHeldItemMainhand().onItemUse(p,w,ROOT.up(),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,1,.5F)==EnumActionResult.SUCCESS,"place payload");
            if(fixture!=0)((net.minecraft.inventory.IInventory)w.getTileEntity(ROOT.up(2))).setInventorySlotContents(0,new ItemStack(Items.DIAMOND,37));
            p.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);GearTileEntity arm=(GearTileEntity)w.getTileEntity(ROOT);require(arm.rebuild(),"form arm");arm.target(false);stage=1;return null;
        }).get();return false;}
        if(stage==1){boolean ready=mc.getIntegratedServer().callFromMainThread(()->{
            WorldServer w=mc.getIntegratedServer().getWorld(0);GearTileEntity arm=(GearTileEntity)w.getTileEntity(ROOT);if(arm.progress()!=0||ArmTransactions.locked(w,ROOT))return false;EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            Vec3d center=arm.payload().transform(arm,0,.5,.5,.5).add(new Vec3d(ROOT));
            p.fallDistance=0;p.setHealth(20);p.setGameType(fixture!=1?GameType.CREATIVE:GameType.SURVIVAL);p.motionX=p.motionY=p.motionZ=0;p.connection.setPlayerLocation(center.x+3,100,center.z,90,(float)Math.toDegrees(Math.atan2(100+p.getEyeHeight()-center.y,3)));p.setHeldItem(EnumHand.MAIN_HAND,fixture!=1?ItemStack.EMPTY:new ItemStack(Items.DIAMOND_AXE));p.inventoryContainer.detectAndSendChanges();return true;
        }).get();if(ready){stage=2;ticks=0;}return false;}
        if(stage==2){
            RayTraceResult hit=mc.objectMouseOver;if(hit==null||hit.hitInfo==null||mc.playerController.getCurrentGameType()!=(fixture!=1?GameType.CREATIVE:GameType.SURVIVAL)||fixture==1&&mc.player.getHeldItemMainhand().getItem()!=Items.DIAMOND_AXE)return false;
            require(hit.hitInfo instanceof ArmPayloadHarvest.Target,"payload target identity");
            mc.player.fallDistance=0;mc.player.motionX=mc.player.motionY=mc.player.motionZ=0;mc.setIngameFocus();mc.inGameHasFocus=true;net.minecraftforge.fml.common.ObfuscationReflectionHelper.setPrivateValue(Minecraft.class,mc,0,"field_71429_W");
            if(fixture!=1)mc.playerController.clickBlock(hit.getBlockPos(),hit.sideHit);else net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(),true);stage=3;return false;
        }
        if(ticks%100==0)System.out.println("[vandorlabs] arm-mining-input key="+mc.gameSettings.keyBindAttack.isKeyDown()+" health="+mc.player.getHealth()+" position="+mc.player.getPosition()+" focus="+mc.inGameHasFocus+" target="+(mc.objectMouseOver==null?"null":mc.objectMouseOver.hitInfo)+" controller="+mc.playerController.getClass().getName());
        boolean complete=mc.getIntegratedServer().callFromMainThread(()->{
            WorldServer w=mc.getIntegratedServer().getWorld(0);GearTileEntity arm=(GearTileEntity)w.getTileEntity(ROOT);require(arm!=null&&w.getBlockState(ROOT.up()).getBlock()==GearRegistry.GEAR_EXTENDER,"mining payload preserves both arms");if(arm.payload()!=null||ArmTransactions.locked(w,ROOT)){if(ticks%100==0)System.out.println("[vandorlabs] arm-mining-wait fixture="+fixture+" payload="+(arm.payload()!=null)+" lock="+ArmTransactions.locked(w,ROOT)+" "+ArmPayloadHarvest.diagnostic(mc.getIntegratedServer().getPlayerList().getPlayers().get(0))+" error="+arm.error());return false;}
            int diamonds=0,chests=0;for(net.minecraft.entity.item.EntityItem item:w.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class,new AxisAlignedBB(ROOT).grow(5))){if(item.getItem().getItem()==Items.DIAMOND)diamonds+=item.getItem().getCount();if(item.getItem().getItem()==Item.getItemFromBlock(Blocks.CHEST))chests+=item.getItem().getCount();}
            require(fixture==0?diamonds==0&&chests==0:diamonds==37&&chests==(fixture==1?1:0),"normal drops preserved once diamonds="+diamonds+" chests="+chests);
            for(net.minecraft.entity.item.EntityItem item:w.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class,new AxisAlignedBB(ROOT).grow(5)))w.removeEntityDangerously(item);
            require(w.isAirBlock(ROOT.up(2)),"no stale source block");arm.target(true);return true;
        }).get();if(!complete)return false;net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(),false);mc.playerController.resetBlockRemoving();
        if(++fixture<3){stage=0;ticks=0;return false;}
        System.out.println("[vandorlabs] arm-payload-mining PASS real Creative click Survival held attack both arms retained Survival and Creative chest inventories once durable unlock");return true;
    }
}
