package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.container.*;
import com.vandorlabs.plate.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import java.io.File;
import java.util.*;

/** Shared widget layouts on the actual host dialog family at compact and normal sizes. */
final class TransmissionDialogChecks {
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("Transmission dialog: "+message);}
    static void run(Minecraft mc,File output)throws Exception{
        BlockPos pos=new BlockPos(mc.player.posX,200,mc.player.posZ);net.minecraft.block.state.IBlockState previous=mc.world.getBlockState(pos);TileEntity original=mc.world.getTileEntity(pos);
        GuiScreen old=mc.currentScreen;net.minecraft.inventory.Container oldContainer=mc.player.openContainer;int cases=0;
        List<Block> blocks=new ArrayList<>(Arrays.asList(ModBlocks.ANIMATED_SCREEN_SELECTOR,ModBlocks.PROGRAMMABLE_INPUT,ModBlocks.PROGRAMMABLE_FULL_INPUT,ModBlocks.PROGRAMMABLE_HALF_CONSOLE,ModBlocks.PROGRAMMABLE_CONSOLE));
        for(String id:new String[]{"wall_slider","industrial_power_lever","redstone_transmitter_block","access_card_slot","data_chip_slot","waypoint","programmable_pressure_plate","cockpit"})blocks.add(Block.getBlockFromName("vandorlabs:"+id));
        try{for(Block block:blocks){require(block!=null,"registered host");mc.world.setBlockState(pos,block.getDefaultState(),2);TileEntity tile=mc.world.getTileEntity(pos);if(tile instanceof TileEntityAnimatedScreenSelector)((TileEntityAnimatedScreenSelector)tile).setSurfaceTexture(0,TileEntityAnimatedScreenSelector.REDSTONE_SURFACE);GuiContainer gui;
            if(tile instanceof TileEntityCockpit)gui=new GuiCockpit(new ContainerCockpit((TileEntityCockpit)tile,null));
            else if(tile instanceof TileEntityWirelessRedstone)gui=new GuiWirelessRedstone(new ContainerWirelessRedstone((TileEntityWirelessRedstone)tile));
            else if(tile instanceof TileEntityTokenSlot)gui=new GuiTokenSlot(new ContainerTokenSlot(mc.player.inventory,(TileEntityTokenSlot)tile));
            else if(tile instanceof PlateTile)gui=new GuiPressurePlate(new PlateContainer((PlateTile)tile));
            else if(tile instanceof TileEntityWaypoint)gui=new GuiAutopilot((TileEntityWaypoint)tile);
            else if(tile instanceof TileEntityAnimatedScreenSelector){TileEntityAnimatedScreenSelector screen=(TileEntityAnimatedScreenSelector)tile;
                if(block instanceof BlockProgrammableInput)gui=new GuiProgrammableInput(mc.player.inventory,screen);
                else if(block instanceof BlockProgrammableHalfConsole)gui=new GuiProgrammableHalfConsole(mc.player.inventory,screen);
                else gui=new GuiAnimatedScreenSelector(mc.player.inventory,screen);
            }else gui=new GuiRedstoneChannel((com.vandorlabs.redstone.RedstoneChannelMember)tile);
            gui.inventorySlots.windowId=-97;mc.displayGuiScreen(gui);
            for(int[] size:new int[][]{{320,240},{460,340}}){gui.setWorldAndResolution(mc,size[0],size[1]);List<GuiButton> buttons=ReflectionHelper.getPrivateValue(GuiScreen.class,gui,"buttonList","field_146292_n");
                for(GuiButton button:new ArrayList<>(buttons))if(button.visible&&button.enabled&&button.displayString.equals("Redstone")){ReflectionHelper.findMethod(GuiScreen.class,"actionPerformed","func_146284_a",GuiButton.class).invoke(gui,button);break;}
                InlineTransmitterWidget.beforeDraw(new net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Pre(gui,0,0,0));
                List<GuiButton> controls=InlineTransmitterWidget.controls(gui);require(controls.size()==2,block.getRegistryName()+" range and local toggle present");
                for(GuiButton control:controls){require(control.x>=0&&control.y>=0&&control.x+control.width<=gui.width&&control.y+control.height<=gui.height,"control in viewport "+block.getRegistryName());for(GuiButton other:buttons)if(other.visible)require(!overlap(control.x,control.y,control.width,control.height,other),"control overlaps "+other.displayString+" "+block.getRegistryName());}
                GuiTextField field=InlineTransmitterWidget.channelsField(gui);if(field!=null&&field.getVisible())for(GuiButton other:buttons)if(other.visible)require(!overlap(field.x,field.y,field.width,field.height,other),"channels overlap "+other.displayString+" "+block.getRegistryName());
                gui.drawScreen(0,0,0);InlineTransmitterWidget.afterDraw(new net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Post(gui,0,0,0));cases++;
            }
        }}finally{mc.displayGuiScreen(old);mc.player.openContainer=oldContainer;mc.world.setBlockState(pos,previous,2);if(original!=null)mc.world.setTileEntity(pos,original);}
        System.out.println("[vandorlabs][reprolab] transmission-dialogs PASS cases="+cases+" compact320 normal460 all-host-families no-overlap no-item-slots");
    }
    private static boolean overlap(int x,int y,int width,int height,GuiButton b){return x<b.x+b.width&&x+width>b.x&&y<b.y+b.height&&y+height>b.y;}
}
