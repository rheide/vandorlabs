package com.vandorlabs.client;

import com.vandorlabs.container.ContainerModularGear;
import com.vandorlabs.gear.GearTileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.util.math.BlockPos;
import java.awt.Rectangle;
import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

final class ModularGearDialogRuntimeChecks {
    static boolean ready(Minecraft mc,String shot){int index=shot.contains("_1_")?1:shot.contains("_2_")?2:0;BlockPos p=new BlockPos(390+index*10,32,120);if(!mc.world.isAreaLoaded(p.add(-3,-5,-3),p.add(5,3,3),false))return false;net.minecraft.tileentity.TileEntity raw=mc.world.getTileEntity(p);return raw instanceof GearTileEntity&&((GearTileEntity)raw).isOperational();}
    static void run(Minecraft mc,File output)throws Exception{
        GearTileEntity tile=(GearTileEntity)mc.world.getTileEntity(new BlockPos(390,32,120));if(tile==null||!tile.isOperational())throw new IllegalStateException("Modular gear dialog fixture missing");GuiScreen old=mc.currentScreen;Field buttons;try{buttons=GuiScreen.class.getDeclaredField("buttonList");}catch(NoSuchFieldException obfuscated){buttons=GuiScreen.class.getDeclaredField("field_146292_n");}buttons.setAccessible(true);Field field=GuiModularGear.class.getDeclaredField("channels");field.setAccessible(true);
        try{for(int[] size:new int[][]{{320,240},{460,340},{new ScaledResolution(mc).getScaledWidth(),new ScaledResolution(mc).getScaledHeight()}}){GuiModularGear gui=new GuiModularGear(new ContainerModularGear(tile));mc.displayGuiScreen(gui);gui.setWorldAndResolution(mc,size[0],size[1]);List<Rectangle> occupied=new ArrayList<>();for(Object raw:(List<?>)buttons.get(gui)){GuiButton b=(GuiButton)raw;add(occupied,new Rectangle(b.x,b.y,b.width,b.height),size);}GuiTextField f=(GuiTextField)field.get(gui);add(occupied,new Rectangle(f.x,f.y,f.width,f.height),size);mc.getFramebuffer().bindFramebuffer(true);net.minecraft.client.renderer.GlStateManager.clear(16640);mc.entityRenderer.setupOverlayRendering();gui.drawScreen(-1,-1,0);javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_modular_gear_dialog_"+size[0]+".png"));}}
        finally{mc.displayGuiScreen(old);}
        System.out.println("[vandorlabs][reprolab] modular-gear-dialog PASS three resolutions visible nonoverlapping controls");
    }
    private static void add(List<Rectangle> occupied,Rectangle next,int[] size){if(next.x<0||next.y<0||next.x+next.width>size[0]||next.y+next.height>size[1])throw new IllegalStateException("Gear control outside screen "+next);for(Rectangle old:occupied)if(old.intersects(next))throw new IllegalStateException("Gear controls overlap "+next);occupied.add(next);}
}
