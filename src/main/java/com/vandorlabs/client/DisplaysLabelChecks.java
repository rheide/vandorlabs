package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import java.io.File;
import java.util.*;

final class DisplaysLabelChecks {
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("Displays labels: "+why);}
    static void run(Minecraft mc,File output)throws Exception{
        int cases=0,surfaces=0;
        for(FrameMachineDesign design:FrameMachineDesign.all())for(FrameMachineDesign.Surface surface:design.surfaces)if(surface.id.toLowerCase(java.util.Locale.ROOT).contains("screen")){require(surface.label.equals("Displays"),"multiblock surface label "+design.design+"/"+surface.id);surfaces++;}
        for(String id:new String[]{"programmable_viewscreen","programmable_console","programmable_half_console","programmable_diagonal_half_screen","navigation_table_flat"}){
            Block block=Block.getBlockFromName("vandorlabs:"+id);IBlockState state=block.getDefaultState();net.minecraft.tileentity.TileEntity template=block.createTileEntity(mc.world,state);template.setPos(BlockPos.ORIGIN);VehicleWorld view=new VehicleWorld(mc.world,VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,state,template.writeToNBT(new NBTTagCompound())))));net.minecraft.tileentity.TileEntity raw=view.getTileEntity(BlockPos.ORIGIN);
            GuiScreen gui=raw instanceof TileEntitySecuredDisplay?new GuiNavigationTable(new com.vandorlabs.container.ContainerTokenSlot(mc.player.inventory,(TileEntitySecuredDisplay)raw)):block instanceof BlockProgrammableHalfConsole?new GuiProgrammableHalfConsole(mc.player.inventory,(TileEntityAnimatedScreenSelector)raw):block instanceof BlockDiagonalHalfConsole?new GuiProgrammableInput(mc.player.inventory,(TileEntityAnimatedScreenSelector)raw):new GuiAnimatedScreenSelector(mc.player.inventory,(TileEntityAnimatedScreenSelector)raw);
            for(int width:new int[]{320,480}){check(mc,gui,width);javax.imageio.ImageIO.write(DialogRenderCapture.image(mc,gui,width,320),"png",new File(output,"displays-tab-"+id+"-"+width+".png"));cases++;}
        }
        TileEntityCockpit tile=new TileEntityCockpit("cockpit");tile.setWorld(mc.world);tile.setPos(new BlockPos(2048,128,2048));GuiCockpit cockpit=new GuiCockpit(new com.vandorlabs.container.ContainerCockpit(tile,null));for(int width:new int[]{320,600}){check(mc,cockpit,width);javax.imageio.ImageIO.write(DialogRenderCapture.image(mc,cockpit,width,320),"png",new File(output,"displays-tab-cockpit-"+width+".png"));cases++;}
        System.out.println("[vandorlabs][reprolab] displays-labels PASS rendered-dialogs="+cases+" constructor-surfaces="+surfaces+" screen/console/half/diagonal/navigation/cockpit compact-and-wide captions fit");
    }
    private static void check(Minecraft mc,GuiScreen gui,int width){gui.setWorldAndResolution(mc,width,320);List<GuiButton> buttons=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(GuiScreen.class,gui,"buttonList","field_146292_n");boolean displays=false;for(GuiButton button:buttons){require(!button.displayString.equals("Display")&&!button.displayString.equals("Screen"),"singular tab caption survived");if(button.displayString.equals("Displays")){displays=true;require(mc.fontRenderer.getStringWidth(button.displayString)<=button.width-6,"Displays caption clipped");}}require(displays,"Displays tab missing "+gui.getClass().getSimpleName());}
    private DisplaysLabelChecks(){}
}
