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
        if(state==1 && ticks>=25){if(!(mc.world.getTileEntity(POS) instanceof com.vandorlabs.tiles.TileEntityPilotSeat)){require(ticks<200,"client seat tile did not arrive");return;}pending=mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);VehicleService.previewAssembly(player,POS);require(player.openContainer instanceof com.vandorlabs.container.ContainerRedstoneChannel,"server seat container missing");});state=2;return;}
        if(state==2 && ticks==40){require(mc.currentScreen instanceof GuiRedstoneChannel,"failed scan must retain settings dialog; screen="+(mc.currentScreen==null?"null":mc.currentScreen.getClass().getName()));
            GuiRedstoneChannel gui=(GuiRedstoneChannel)mc.currentScreen;boolean disabled=false;
            for(GuiButton button:gui.inspectionButtons()){if(button.id==20)disabled=!button.enabled;if(button.id==18 || button.id==19 || button.id==15)require(button.enabled,"independent settings disabled");}
            require(disabled,"invalid assembly action must be disabled");gui.actionPerformed(new GuiButton(19,0,0,"height"));gui.actionPerformed(new GuiButton(18,0,0,"trigger"));state=3;ticks=0;return;
        }
        if(state==3 && ticks==20){pending=mc.getIntegratedServer().addScheduledTask(()->{com.vandorlabs.tiles.TileEntityPilotSeat seat=(com.vandorlabs.tiles.TileEntityPilotSeat)mc.getIntegratedServer().getWorld(0).getTileEntity(POS);require(seat.getHeight()==1,"height packet rejected after invalid scan");});mc.player.closeScreen();scenario++;state=scenario<2?0:4;return;}
        if(state==4 && ticks==10){System.out.println("[vandorlabs][reprolab] pilot-inspection PASS invalid-and-oversized dialogs, disabled assembly, usable trigger and live height");mc.shutdown();}
    }
}
