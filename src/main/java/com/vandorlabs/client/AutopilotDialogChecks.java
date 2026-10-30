package com.vandorlabs.client;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.tiles.TileEntityAutopilot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import java.io.File;
import java.util.List;

/** The combined route page samples exactly the nine hotbar slots without consuming items. */
final class AutopilotDialogChecks {
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("Autopilot dialog: "+message);}
    static void run(Minecraft mc,File output)throws Exception{
        BlockPos pos=new BlockPos(mc.player.posX,200,mc.player.posZ);
        net.minecraft.block.state.IBlockState original=mc.world.getBlockState(pos);
        net.minecraft.tileentity.TileEntity originalTile=mc.world.getTileEntity(pos);
        NBTTagList inventory=mc.player.inventory.writeToNBT(new NBTTagList());GuiScreen old=mc.currentScreen;net.minecraft.inventory.Container container=mc.player.openContainer;
        try{
            mc.player.inventory.clear();for(int i=0;i<9;i++)mc.player.inventory.setInventorySlotContents(i,new ItemStack(Blocks.WOOL,i+1,i));
            mc.player.inventory.setInventorySlotContents(0,new ItemStack(Blocks.STONE,3));mc.player.inventory.setInventorySlotContents(8,new ItemStack(Blocks.WOOL,7,14));mc.player.inventory.setInventorySlotContents(9,new ItemStack(Blocks.GOLD_BLOCK,11));
            mc.world.setBlockState(pos,ModBlocks.AUTOPILOT.getDefaultState(),2);TileEntityAutopilot tile=(TileEntityAutopilot)mc.world.getTileEntity(pos);
            GuiAutopilot gui=new GuiAutopilot(tile);gui.inventorySlots.windowId=-99;mc.displayGuiScreen(gui);
            for(int[] size:new int[][]{{320,240},{460,340}}){
                gui.setWorldAndResolution(mc,size[0],size[1]);
                List<GuiButton> buttons=ReflectionHelper.getPrivateValue(GuiScreen.class,gui,"buttonList","field_146292_n");
                GuiButton general=null,redstone=null;int tabs=0;
                for(GuiButton b:buttons)if(b instanceof DialogTabButton){tabs++;if(b.id==100)general=b;if(b.id==101)redstone=b;}
                require(tabs==2&&general!=null&&redstone!=null,"General and Redstone are the only tabs");
                require(general.width==redstone.width&&redstone.x-general.x-general.width==4&&general.y==redstone.y,"equal tabs with consistent four-pixel gap");
                InventorySamplePicker picker=ReflectionHelper.getPrivateValue(GuiAutopilot.class,gui,"routePicker");require(picker!=null,"route picker is present directly on General");
                require(picker.width==162&&picker.height==34,"one nine-slot hotbar row");
                picker.click(picker.x+8,picker.y+24,0);require("minecraft:stone".equals(ReflectionHelper.getPrivateValue(GuiAutopilot.class,gui,"markerId")),"slot zero selects its block");
                picker.click(picker.x+8*18+8,picker.y+24,0);require("minecraft:wool".equals(ReflectionHelper.getPrivateValue(GuiAutopilot.class,gui,"markerId"))&&(Integer)ReflectionHelper.getPrivateValue(GuiAutopilot.class,gui,"markerMeta")==14,"slot eight preserves block metadata");
                require(!picker.click(picker.x+8,picker.y+42,0),"no second inventory row can be selected");picker.wheel(picker.x+8,picker.y+24,-120);picker.click(picker.x+8,picker.y+24,0);
                require("minecraft:stone".equals(ReflectionHelper.getPrivateValue(GuiAutopilot.class,gui,"markerId")),"scrolling cannot expose main inventory");
                require(mc.player.inventory.getStackInSlot(0).getCount()==3&&mc.player.inventory.getStackInSlot(8).getCount()==7&&mc.player.inventory.getStackInSlot(9).getCount()==11,"selection never consumes inventory");
                FrameFramebufferChecks.renderDialog(mc,gui,new File(output,"shot_autopilot_combined_hotbar_"+size[0]+".png"),-1,-1);
                ReflectionHelper.findMethod(GuiScreen.class,"actionPerformed","func_146284_a",GuiButton.class).invoke(gui,redstone);
                GuiTextField channels=ReflectionHelper.getPrivateValue(GuiAutopilot.class,gui,"channels");channels.setText("321,322");
                FrameFramebufferChecks.renderDialog(mc,gui,new File(output,"shot_autopilot_receive_"+size[0]+".png"),-1,-1);
                ReflectionHelper.findMethod(GuiScreen.class,"actionPerformed","func_146284_a",GuiButton.class).invoke(gui,general);
                require("321,322".equals(((GuiTextField)ReflectionHelper.getPrivateValue(GuiAutopilot.class,gui,"channels")).getText()),"tab changes preserve channel draft");
            }
            System.out.println("[vandorlabs][reprolab] autopilot-dialog-hotbar PASS combined-general equal-tabs compact320 normal460 nine-hotbar-slots metadata non-consuming no-main-inventory saved-draft");
        }finally{mc.displayGuiScreen(old);mc.player.openContainer=container;mc.player.inventory.readFromNBT(inventory);mc.world.setBlockState(pos,original,2);if(originalTile!=null)mc.world.setTileEntity(pos,originalTile);}
    }
}
