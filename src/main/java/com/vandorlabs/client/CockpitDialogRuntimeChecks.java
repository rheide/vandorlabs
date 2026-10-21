package com.vandorlabs.client;

import com.vandorlabs.container.ContainerCockpit;
import com.vandorlabs.tiles.TileEntityCockpit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import java.awt.Rectangle;
import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

/** Render every cockpit page and check interactive bounds at small and normal GUI sizes. */
final class CockpitDialogRuntimeChecks {
    static void run(Minecraft mc,File output)throws Exception{
        GuiScreen previous=mc.currentScreen;int checked=0;
        Field page=GuiCockpit.class.getDeclaredField("page"),fields=GuiCockpit.class.getDeclaredField("fields");page.setAccessible(true);fields.setAccessible(true);
        Field buttons;try{buttons=GuiScreen.class.getDeclaredField("buttonList");}catch(NoSuchFieldException mapped){buttons=GuiScreen.class.getDeclaredField("field_146292_n");}buttons.setAccessible(true);
        try{
            for(int model=0;model<4;model++){
                TileEntityCockpit tile=(TileEntityCockpit)mc.world.getTileEntity(new BlockPos(222+model*6,30,120));
                require(tile!=null,"fixture synchronized");GuiCockpit gui=new GuiCockpit(new ContainerCockpit(tile,null));gui.inventorySlots.windowId=-77;mc.displayGuiScreen(gui);
                int count=(tile.data().seat==null?0:1)+1+tile.displays.size()+tile.controls.size();
                for(int[] size:new int[][]{{320,240},{460,340},{new ScaledResolution(mc).getScaledWidth(),new ScaledResolution(mc).getScaledHeight()}})for(int index=0;index<count;index++){
                    page.setInt(gui,index);gui.setWorldAndResolution(mc,size[0],size[1]);
                    List<Rectangle> occupied=new ArrayList<>();
                    for(Object raw:(List<?>)buttons.get(gui)){GuiButton button=(GuiButton)raw;if(button.visible)add(occupied,new Rectangle(button.x,button.y,button.width,button.height),size);}
                    for(Object raw:(List<?>)fields.get(gui)){GuiTextField field=(GuiTextField)raw;add(occupied,new Rectangle(field.x,field.y,field.width,field.height),size);}
                    mc.getFramebuffer().bindFramebuffer(true);net.minecraft.client.renderer.GlStateManager.clear(16640);mc.entityRenderer.setupOverlayRendering();gui.drawScreen(-1,-1,0);
                    if(index==0 || index==(tile.data().seat==null?0:1) || index==(tile.data().seat==null?0:1)+1 || index==(tile.data().seat==null?0:1)+1+tile.displays.size())javax.imageio.ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_utility22_cockpit_dialog_"+model+"_"+size[0]+"_"+index+".png"));
                    checked++;
                }
            }
        }finally{mc.displayGuiScreen(previous);if(previous==null)mc.player.openContainer=mc.player.inventoryContainer;}
        System.out.println("[vandorlabs][reprolab] cockpit-dialog PASS "+checked+" pages/resolutions visible control bounds and overlap checks");
    }
    private static void add(List<Rectangle> occupied,Rectangle next,int[] size){require(next.x>=0 && next.y>=0 && next.x+next.width<=size[0] && next.y+next.height<=size[1],"control outside screen "+next);for(Rectangle other:occupied)require(!next.intersects(other),"overlapping controls "+next+" and "+other);occupied.add(next);}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("cockpit-dialog: "+message);}
}
