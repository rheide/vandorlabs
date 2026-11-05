package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockVandorSwitch;
import com.vandorlabs.tiles.TileEntityRedstoneChannel;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Actual legacy screen widgets against frozen current rectangles, IDs and all option labels. */
final class MountedControlDialogChecks {
    private static final BlockPos POS=new BlockPos(12,100,12);
    private static int ticks;private static boolean prepared;
    private static final String[] LEVERS={"compact_power_lever","industrial_power_lever","small_power_lever","large_power_lever"};
    private static final java.lang.reflect.Field BUTTONS=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(GuiScreen.class,"buttonList","field_146292_n");
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Mounted control dialog: "+message);}
    @SuppressWarnings("unchecked") private static List<GuiButton> buttons(GuiScreen screen)throws Exception{return (List<GuiButton>)BUTTONS.get(screen);}
    private static GuiButton button(GuiScreen screen,int id)throws Exception{for(GuiButton b:buttons(screen))if(b.id==id)return b;throw new IllegalStateException("Missing mounted option="+id);}
    static boolean tick(Minecraft mc)throws Exception{
        if(++ticks>300)throw new IllegalStateException("Mounted control dialog client tile timed out");
        if(!prepared){mc.getIntegratedServer().addScheduledTask(()->{varSetup(mc);}).get();prepared=true;return false;}
        for(int index=0;index<LEVERS.length;index++)if(!(mc.world.getTileEntity(POS.east(3+index*3)) instanceof TileEntityRedstoneChannel))return false;
        if(!(mc.world.getTileEntity(POS) instanceof TileEntityRedstoneChannel))return false;TileEntityRedstoneChannel tile=(TileEntityRedstoneChannel)mc.world.getTileEntity(POS);int cases=0;
        String[] heights={"Base height: Standard","Base height: +2 px","Base height: +4 px","Base height: +6 px"},tilts={"Tilt: Flat","Tilt: 15 deg","Tilt: 30 deg","Tilt: 45 deg"},directions={"Forward","Right","Backward","Left"};
        for(int height=0;height<4;height++)for(int tilt=0;tilt<4;tilt++)for(int direction=0;direction<4;direction++){
            tile.configureMount(height,tilt,direction);GuiRedstoneChannel gui=new GuiRedstoneChannel(tile);mc.displayGuiScreen(gui);int left=(gui.width-280)/2,top=(gui.height-232)/2;int[] ids={9,10,11},xs={left+14,left+14,left+144},ys={top+92,top+116,top+116},widths={252,122,122};String[] labels={heights[height],tilts[tilt],directions[direction]};
            require(gui.getXSize()==280&&gui.getYSize()==232,"current fixed panel");for(int i=0;i<3;i++){GuiButton b=button(gui,ids[i]);require(b.x==xs[i]&&b.y==ys[i]&&b.width==widths[i]&&b.height==20&&b.displayString.equals(labels[i]),"actual widget bounds/label id="+ids[i]);}cases++;
        }
        tile.configureMount(0,0,0);GuiRedstoneChannel gui=new GuiRedstoneChannel(tile);mc.displayGuiScreen(gui);GuiButton height=button(gui,9);gui.mouseClicked(height.x+2,height.y+2,1);require(height.displayString.equals("Base height: +6 px"),"actual right click previous option");gui.mouseClicked(height.x+2,height.y+2,0);require(height.displayString.equals("Base height: Standard"),"actual left click next option");mc.displayGuiScreen(null);
        int sizes=0;
        for(int index=0;index<LEVERS.length;index++)for(int size=0;size<2;size++){
            TileEntityRedstoneChannel lever=(TileEntityRedstoneChannel)mc.world.getTileEntity(POS.east(3+index*3));lever.setPowerLeverSize(size);GuiRedstoneChannel screen=new GuiRedstoneChannel(lever);mc.displayGuiScreen(screen);GuiButton option=button(screen,12);int left=(screen.width-280)/2,top=(screen.height-232)/2;String[] labels=index<2?new String[]{"Size: Compact","Size: Industrial"}:new String[]{"Size: Small","Size: Large"};
            require(option.x==left+14&&option.y==top+64&&option.width==252&&option.height==20&&option.displayString.equals(labels[size]),"lever size actual widget rectangle/label "+LEVERS[index]+" size="+size);
            screen.mouseClicked(option.x+2,option.y+2,0);require(option.displayString.equals(labels[1-size]),"lever size left next");screen.mouseClicked(option.x+2,option.y+2,1);require(option.displayString.equals(labels[size]),"lever size right previous");sizes++;
        }
        mc.displayGuiScreen(null);System.out.println("[vandorlabs][reprolab] power-lever-size-dialog PASS widgets="+sizes+" four legacy IDs actual Size12 rectangle labels left/right draft transitions");
        System.out.println("[vandorlabs][reprolab] mounted-control-dialog legacy PASS cases="+cases+" actualGuiRedstoneChannel frozen IDs labels rectangles panel right-left cycle");return true;
    }
    private static void varSetup(Minecraft mc){varSetupWorld(mc.getIntegratedServer().getWorld(0));net.minecraft.entity.player.EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);player.connection.setPlayerLocation(12,100,9,0,0);}
    private static void varSetupWorld(net.minecraft.world.WorldServer world){world.setBlockState(POS.down(),Blocks.STONE.getDefaultState(),3);world.setBlockState(POS,Block.getBlockFromName("vandorlabs:rocker_switch").getDefaultState().withProperty(BlockVandorSwitch.FACING,EnumFacing.UP),3);for(int index=0;index<LEVERS.length;index++){BlockPos pos=POS.east(3+index*3);world.setBlockState(pos.down(),Blocks.STONE.getDefaultState(),3);world.setBlockState(pos,Block.getBlockFromName("vandorlabs:"+LEVERS[index]).getDefaultState().withProperty(com.vandorlabs.blocks.BlockIndustrialLever.FLOOR,true),3);}}
}
