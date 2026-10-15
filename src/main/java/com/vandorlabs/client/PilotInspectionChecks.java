package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.vehicle.*;
import com.vandorlabs.items.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.init.Blocks;

/** Focused real-server invalid/oversized inspection and independent settings regression. */
public final class PilotInspectionChecks {
    private static int ticks,state,scenario;private static java.util.concurrent.Future<?> pending;
    private static final BlockPos POS=new BlockPos(8,100,8);
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException("pilot-inspection: "+text);}
    public static void tick(Minecraft mc) throws Exception {
        if(mc.world==null || mc.player==null || mc.getIntegratedServer()==null)return;
        if(pending!=null){if(!pending.isDone())return;pending.get();pending=null;ticks=0;}
        ticks++;
        if(state==0 && ticks>=80){final boolean oversized=scenario==1;pending=mc.getIntegratedServer().addScheduledTask(()->{
            EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            player.setPositionAndUpdate(8.5,100,10.5);player.capabilities.allowFlying=true;player.capabilities.isFlying=true;player.sendPlayerAbilities();player.inventory.setInventorySlotContents(player.inventory.currentItem,new ItemStack(ModItems.CONFIGURIZER));
            for(int x=0;x<70;x++)player.world.setBlockToAir(POS.east(x));
            player.world.setBlockState(POS,ModBlocks.PILOT_SEAT.getDefaultState(),3);
            player.world.setBlockState(POS.up(),ModBlocks.PILOT_SEAT.getDefaultState().withProperty(BlockPilotSeat.UPPER,true),3);
            if(oversized)for(int x=1;x<70;x++)player.world.setBlockState(POS.east(x),Blocks.IRON_BLOCK.getDefaultState(),3);
        });state=1;return;}
        if(state==1 && ticks>=25){if(!(mc.world.getTileEntity(POS) instanceof com.vandorlabs.tiles.TileEntityPilotSeat)){require(ticks<200,"client seat tile did not arrive");return;}pending=mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);VehicleService.previewAssembly(player,POS);});state=2;return;}
        if(state==2 && ticks==40){
            for(GuiButton button:VehicleTaxiRuntimeChecks.previewLayout(mc)){require(button.id!=0,"seat settings never contain controller assembly actions");if(button.id==2||button.id==4||button.id==5||button.id==6)require(button.enabled,"independent settings disabled");}
            GuiOptionCycleRuntimeChecks.check(mc);
            VehicleTaxiRuntimeChecks.screenshot(mc,new java.io.File(System.getProperty("vandorlabs.reprolab")),"shot_pilot_invalid_preview_"+scenario+".png");
            VehicleTaxiRuntimeChecks.button(mc,6);state=3;ticks=0;return;
        }
        if(state==3 && ticks==20){require(mc.currentScreen instanceof GuiPilotModes,"invalid scan still exposes mode-channel child");VehicleTaxiRuntimeChecks.screenshot(mc,new java.io.File(System.getProperty("vandorlabs.reprolab")),"shot_pilot_invalid_mode_channels_"+scenario+".png");VehicleTaxiRuntimeChecks.button(mc,4);state=4;ticks=0;return;}
        if(state==4 && ticks==20){VehicleTaxiRuntimeChecks.previewLayout(mc);VehicleTaxiRuntimeChecks.button(mc,5);VehicleTaxiRuntimeChecks.button(mc,4);state=5;ticks=0;return;}
        if(state==5 && ticks==20){pending=mc.getIntegratedServer().addScheduledTask(()->{com.vandorlabs.tiles.TileEntityPilotSeat seat=(com.vandorlabs.tiles.TileEntityPilotSeat)mc.getIntegratedServer().getWorld(0).getTileEntity(POS);require(seat.getHeight()==1,"height packet rejected after invalid scan");});VehicleTaxiRuntimeChecks.button(mc,1);scenario++;state=scenario<2?0:6;return;}
        if(state==6 && ticks>=10){if(!CapturedPilotInspectionChecks.tick(mc))return;state=7;GuiOptionCycleRuntimeChecks.checkNewDialogs(mc);System.out.println("[vandorlabs][reprolab] pilot-inspection PASS disconnected-and-oversized seat-settings-only no-controller-action usable-trigger live-height no-save mode-channel-child screenshots");mc.shutdown();}
    }
}
