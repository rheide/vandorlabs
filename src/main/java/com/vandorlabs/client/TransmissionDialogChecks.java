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
        for(String id:new String[]{"thruster_lever","redstone_receiver_block","pilot_seat","rocket_thruster_hexagonal","programmable_light", "wall_slider","industrial_power_lever","redstone_transmitter_block","access_card_slot","data_chip_slot","waypoint","autopilot","programmable_pressure_plate","cockpit"})blocks.add(Block.getBlockFromName("vandorlabs:"+id));
        try{for(Block block:blocks){require(block!=null,"registered host");mc.world.setBlockState(pos,block.getDefaultState(),2);TileEntity tile=mc.world.getTileEntity(pos);if(tile instanceof TileEntityAnimatedScreenSelector)((TileEntityAnimatedScreenSelector)tile).setSurfaceTexture(0,TileEntityAnimatedScreenSelector.REDSTONE_SURFACE);GuiContainer gui;
            if(tile instanceof com.vandorlabs.redstone.RedstoneChannelMember&&!(tile instanceof TileEntityProgrammableLight)&&com.vandorlabs.redstone.TransmitterSlots.supported((com.vandorlabs.redstone.RedstoneChannelMember)tile))com.vandorlabs.redstone.TransmitterSlots.configure((com.vandorlabs.redstone.RedstoneChannelMember)tile,com.vandorlabs.redstone.ChannelList.of(65291),16,com.vandorlabs.redstone.TransmitterSlots.localRedstone(tile));
            if(tile instanceof TileEntityCockpit)gui=new GuiCockpit(new ContainerCockpit((TileEntityCockpit)tile,null));
            else if(tile instanceof TileEntityWirelessRedstone)gui=new GuiWirelessRedstone(new ContainerWirelessRedstone((TileEntityWirelessRedstone)tile));
            else if(tile instanceof TileEntityTokenSlot)gui=new GuiTokenSlot(new ContainerTokenSlot(mc.player.inventory,(TileEntityTokenSlot)tile));
            else if(tile instanceof PlateTile)gui=new GuiPressurePlate(new PlateContainer((PlateTile)tile));
            else if(tile instanceof TileEntityAutopilot)gui=new GuiAutopilot((TileEntityAutopilot)tile);
            else if(tile instanceof TileEntityProgrammableLight)gui=new GuiProgrammableLight(mc.player.inventory,(TileEntityProgrammableLight)tile);
            else if(tile instanceof TileEntityAnimatedScreenSelector){TileEntityAnimatedScreenSelector screen=(TileEntityAnimatedScreenSelector)tile;
                if(block instanceof BlockProgrammableFullInput)gui=new GuiAnimatedScreenSelector(mc.player.inventory,screen);
                else if(block instanceof BlockProgrammableInput)gui=new GuiProgrammableInput(mc.player.inventory,screen);
                else if(block instanceof BlockProgrammableHalfConsole)gui=new GuiProgrammableHalfConsole(mc.player.inventory,screen);
                else gui=new GuiAnimatedScreenSelector(mc.player.inventory,screen);
            }else gui=new GuiRedstoneChannel((com.vandorlabs.redstone.RedstoneChannelMember)tile);
            gui.inventorySlots.windowId=-97;mc.displayGuiScreen(gui);
            for(int[] size:new int[][]{{320,240},{460,340}}){gui.setWorldAndResolution(mc,size[0],size[1]);List<GuiButton> buttons=ReflectionHelper.getPrivateValue(GuiScreen.class,gui,"buttonList","field_146292_n");
                for(GuiButton button:new ArrayList<>(buttons))if(button.visible&&button.enabled&&button.displayString.equals("Redstone")){ReflectionHelper.findMethod(GuiScreen.class,"actionPerformed","func_146284_a",GuiButton.class).invoke(gui,button);break;}
                InlineTransmitterWidget.beforeDraw(new net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Pre(gui,0,0,0));
                buttonBounds(gui);
                List<GuiButton> controls=InlineTransmitterWidget.controls(gui);boolean source=!(tile instanceof TileEntityProgrammableLight)&&com.vandorlabs.redstone.TransmitterSlots.supported((com.vandorlabs.redstone.RedstoneChannelMember)tile);require(controls.size()==(source?2:0),block.getRegistryName()+" source controls only");
                for(GuiButton control:controls){require(control.x>=0&&control.y>=0&&control.x+control.width<=gui.width&&control.y+control.height<=gui.height,"control in viewport "+block.getRegistryName());for(GuiButton other:buttons)if(other.visible)require(!overlap(control.x,control.y,control.width,control.height,other),"control overlaps "+other.displayString+" "+block.getRegistryName());}
                GuiTextField field=InlineTransmitterWidget.channelsField(gui);if(field!=null&&field.getVisible())for(GuiButton other:buttons)if(other.visible)require(!overlap(field.x,field.y,field.width,field.height,other),"channels overlap "+other.displayString+" "+block.getRegistryName());
                InlineTransmitterWidget.afterDraw(new net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Post(gui,0,0,0));
                java.awt.image.BufferedImage shot=FrameFramebufferChecks.renderDialog(mc,gui,new File(output,"shot_redstone_"+block.getRegistryName().getResourcePath()+"_"+size[0]+".png"),-1,-1);
                if(source){GuiButton slider=controls.get(0),local=controls.get(1);int track=shot.getRGB(slider.x+slider.width-12,slider.y+8),thumbX=slider.x+(int)(((net.minecraftforge.fml.client.config.GuiSlider)slider).sliderValue*(slider.width-8));int thumb=shot.getRGB(thumbX+4,slider.y+8);require(((thumb>>16)&255)-((track>>16)&255)>30,"slider track must be recessed beneath its distinct handle "+block.getRegistryName());int dark=0;for(int yy=local.y+3;yy<local.y+local.height-3;yy++)if((shot.getRGB(local.x+local.width/2,yy)&0xffffff)<0x101010)dark++;require(dark<4,"local toggle has a middle texture seam "+block.getRegistryName());}
                if(block.getRegistryName().getResourcePath().equals("thruster_lever")){
                    net.minecraftforge.fml.client.config.GuiSlider slider=(net.minecraftforge.fml.client.config.GuiSlider)controls.get(0);
                    FrameFramebufferChecks.renderDialog(mc,gui,new File(output,"shot_thruster_range_hover_"+size[0]+".png"),slider.x+slider.width/2,slider.y+10);
                    require(InlineTransmitterWidget.mouseInput(gui,slider.x+4,slider.y+10,0),"range drag begins on track");
                    FrameFramebufferChecks.renderDialog(mc,gui,new File(output,"shot_thruster_range_drag_"+size[0]+".png"),slider.x+4+(slider.width-8)*2/3,slider.y+10);
                    slider.mouseReleased(0,0);require(slider.getValueInt()==4,"drag reaches the 64-block step");
                    FrameFramebufferChecks.renderDialog(mc,gui,new File(output,"shot_thruster_range_released_"+size[0]+".png"),-1,-1);
                }
                cases++;
            }
        }}finally{mc.displayGuiScreen(old);mc.player.openContainer=oldContainer;mc.world.setBlockState(pos,previous,2);if(original!=null)mc.world.setTileEntity(pos,original);}
        CompleteRedstoneDialogAudit.run(mc,output,pos);
        System.out.println("[vandorlabs][reprolab] transmission-dialogs PASS cases="+cases+" compact320 normal460 all-host-families no-overlap no-item-slots");
    }
    private static void buttonBounds(GuiContainer gui){
        List<GuiButton> buttons=ReflectionHelper.getPrivateValue(GuiScreen.class,gui,"buttonList","field_146292_n");
        for(GuiButton a:buttons)if(a.visible){require(a.x>=0&&a.y>=0&&a.x+a.width<=gui.width&&a.y+a.height<=gui.height,"button outside viewport "+gui.getClass().getSimpleName()+" "+a.displayString);
            for(GuiButton b:buttons)if(a!=b&&b.visible)require(!overlap(a.x,a.y,a.width,a.height,b),"buttons overlap "+gui.getClass().getSimpleName()+" "+a.displayString+" / "+b.displayString);
        }
    }
    private static boolean overlap(int x,int y,int width,int height,GuiButton b){return x<b.x+b.width&&x+width>b.x&&y<b.y+b.height&&y+height>b.y;}
}
