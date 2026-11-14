package com.vandorlabs.client;

import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NBTTagCompound;
import com.vandorlabs.vehicle.*;

/** Render actual family dialogs at compact and ordinary sizes; retain every control group. */
final class TextureJoinDialogRuntimeChecks {
    @SuppressWarnings("unchecked")
    static void run(Minecraft mc,File output)throws Exception{
        java.lang.reflect.Field field=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(GuiScreen.class,"buttonList","field_146292_n");
        String[] families={"programmable_block","programmable_slab","programmable_stairs","programmable_wall","programmable_porthole_wall","programmable_porthole_block","programmable_diagonal_wall","programmable_diagonal_porthole","programmable_diagonal_glass","programmable_glass","programmable_storage"};
        int count=0;
        for(String family:families){
            Block block=Block.getBlockFromName("vandorlabs:"+family);need(block!=null,"registered family "+family);
            net.minecraft.block.state.IBlockState state=block.getDefaultState();BlockPos pos=new BlockPos(1,30,1);
            TileEntity seed=block.createTileEntity(mc.world,state);need(seed!=null,"family tile "+family);seed.setPos(pos);
            VehicleWorld view=new VehicleWorld(mc.world,VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(pos,state,seed.writeToNBT(new NBTTagCompound())))));
            TileEntity tile=view.getTileEntity(pos);GuiScreen gui=tile instanceof com.vandorlabs.tiles.TileEntityProgrammableGlass
                    ?new GuiProgrammableGlass((com.vandorlabs.tiles.TileEntityProgrammableGlass)tile)
                    :new GuiProgrammableWall(mc.player.inventory,(com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)tile);
            for(int[] size:new int[][]{{320,240},{480,360},{640,480}}){
                gui.setWorldAndResolution(mc,size[0],size[1]);List<GuiButton> buttons=(List<GuiButton>)field.get(gui);
                com.vandorlabs.ui.ProgrammableDialogLayout panel=new com.vandorlabs.ui.ProgrammableDialogLayout(size[0],size[1]);
                List<GuiButton> visible=new ArrayList<>();boolean join=false,parity=false;
                for(GuiButton button:buttons)if(button.visible){
                    need(button.x>=panel.left&&button.y>=panel.top&&button.x+button.width<=panel.left+panel.width&&button.y+button.height<=panel.top+panel.height,family+" bounds id="+button.id+" at "+size[0]);
                    need(button.height>=10,family+" readable control id="+button.id);
                    need(mc.fontRenderer.getStringWidth(button.displayString)<=button.width-4,family+" caption fits id="+button.id+" label="+button.displayString);
                    if(button.displayString.startsWith("Texture: "))need(button.displayString.equals("Texture: Tile")||button.displayString.equals("Texture: Stretch")||button.displayString.equals("Texture: Fit (Aspect)"),family+" texture caption "+button.displayString);
                    if(button.id==240)join=true;if(button.id==241){parity=true;need(!button.enabled,"default filter inactive "+family);}
                    for(GuiButton other:visible)need(button.x>=other.x+other.width||other.x>=button.x+button.width||button.y>=other.y+other.height||other.y>=button.y+button.height,family+" overlapping controls "+button.id+"/"+other.id+" at "+size[0]);
                    visible.add(button);
                }
                boolean supported=!family.equals("programmable_slab")&&!family.equals("programmable_stairs")&&!family.equals("programmable_storage");
                need(join==supported&&parity==supported,"join controls match actual supported family "+family);
                mc.getFramebuffer().framebufferClear();mc.getFramebuffer().bindFramebuffer(true);mc.entityRenderer.setupOverlayRendering();mc.entityRenderer.disableLightmap();net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.color(1,1,1,1);gui.drawScreen(-1,-1,0);
                ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"material-join-dialog-"+family+"-"+size[0]+".png"));count++;
            }
        }
        System.out.println("[vandorlabs][reprolab] texture-join-dialogs PASS rendered="+count+" eleven families compact/ordinary controls bounds nonoverlap captions enabled-state and screenshots");
    }
    private static void need(boolean value,String stage){if(!value)throw new IllegalStateException("Texture join dialogs: "+stage);}
    private TextureJoinDialogRuntimeChecks(){}
}
