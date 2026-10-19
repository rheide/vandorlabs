package com.vandorlabs.client;

import com.vandorlabs.container.ContainerTurntable;
import com.vandorlabs.tiles.*;
import com.vandorlabs.turntable.EntityTurntable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import java.awt.Rectangle;
import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

final class TurntableDialogRuntimeChecks {
    static boolean ready(Minecraft mc){if(mc.world==null)return false;net.minecraft.tileentity.TileEntity raw=mc.world.getTileEntity(new BlockPos(341,31,120));if(!(raw instanceof TileEntityTurntableController))return false;TileEntityTurntableController tile=(TileEntityTurntableController)raw;return tile.layout!=null&&tile.display()!=null&&tile.display().snapshot!=null;}
    static void run(Minecraft mc,File output)throws Exception{
        TileEntityTurntableController tile=(TileEntityTurntableController)mc.world.getTileEntity(new BlockPos(341,31,120));if(tile==null||tile.display()==null||tile.display().snapshot==null)throw new IllegalStateException("Turntable fixture/snapshot not synchronized");
        GuiScreen old=mc.currentScreen;Field buttons;try{buttons=GuiScreen.class.getDeclaredField("buttonList");}catch(NoSuchFieldException obfuscated){buttons=GuiScreen.class.getDeclaredField("field_146292_n");}buttons.setAccessible(true);Field fields=GuiTurntable.class.getDeclaredField("fields");fields.setAccessible(true);
        EntityTurntable moving=tile.display();double subframe=((moving.angle(.75F)-moving.angle(.25F)+540)%360)-180;if(Math.abs(subframe)<.00001)throw new IllegalStateException("Captured blocks do not interpolate between rendered frames");
        try{for(int[] size:new int[][]{{320,240},{460,340},{new ScaledResolution(mc).getScaledWidth(),new ScaledResolution(mc).getScaledHeight()}}){GuiTurntable gui=new GuiTurntable(new ContainerTurntable(tile));mc.displayGuiScreen(gui);gui.setWorldAndResolution(mc,size[0],size[1]);List<Rectangle> occupied=new ArrayList<>();boolean angle=false;for(Object raw:(List<?>)buttons.get(gui)){GuiButton b=(GuiButton)raw;if(b.id==11){angle=true;if(!(b instanceof net.minecraftforge.fml.client.config.GuiSlider)||b.enabled)throw new IllegalStateException("Angle slider must wait for redstone pause");}if(b.id==5||b.displayString.contains("Enabled")||b.displayString.contains("Manual")||b.displayString.equals("Apply"))throw new IllegalStateException("Obsolete turntable control: "+b.displayString);add(occupied,new Rectangle(b.x,b.y,b.width,b.height),size);}if(!angle)throw new IllegalStateException("Missing turntable Angle slider");for(GuiTextField f:(GuiTextField[])fields.get(gui))add(occupied,new Rectangle(f.x,f.y,f.width,f.height),size);mc.getFramebuffer().bindFramebuffer(true);net.minecraft.client.renderer.GlStateManager.clear(16640);mc.entityRenderer.setupOverlayRendering();gui.drawScreen(-1,-1,0);javax.imageio.ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_turntable_dialog_"+size[0]+".png"));}}
        finally{mc.displayGuiScreen(old);}
        if(Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/turntable/turntable_block_top_on").getIconWidth()!=512)throw new IllegalStateException("High-resolution surface master was reduced");
        if(Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/turntable/turntable_block_top_on_low").getIconWidth()!=64)throw new IllegalStateException("Low-resolution surface missing");
        System.out.println("[vandorlabs][reprolab] turntable-dialog PASS three resolutions nonoverlapping controls snapshot synchronization high/low artwork");
    }
    private static void add(List<Rectangle> occupied,Rectangle next,int[] size){if(next.x<0||next.y<0||next.x+next.width>size[0]||next.y+next.height>size[1])throw new IllegalStateException("Turntable control outside screen: "+next);for(Rectangle other:occupied)if(next.intersects(other))throw new IllegalStateException("Turntable overlapping controls: "+next+" and "+other);occupied.add(next);}
}
