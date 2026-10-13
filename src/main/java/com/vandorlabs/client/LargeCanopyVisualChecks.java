package com.vandorlabs.client;
import com.vandorlabs.tiles.TileEntityCanopy;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.GuiHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.*;
import net.minecraft.util.ScreenShotHelper;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.concurrent.Future;
/** Dedicated feature regression: no full kit gallery. */
public final class LargeCanopyVisualChecks {
    private static int state,ticks;private static Future<?> pending;private static final BlockPos POS=new BlockPos(40,20,40);
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException("large-canopy-live: "+text);}
    public static boolean tick(Minecraft mc,File output)throws Exception{
        if(mc.world==null || mc.player==null || mc.getIntegratedServer()==null)return false;
        if(pending!=null){if(!pending.isDone())return false;pending.get();pending=null;ticks=0;}
        ticks++;
        if(state==0 && ticks==80){pending=mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);LargeCanopyRuntimeChecks.run(player);VehicleCanopyRuntimeChecks.check(player.world);player.inventory.setInventorySlotContents(player.inventory.currentItem,new ItemStack(ModItems.CONFIGURIZER));player.setPositionAndUpdate(40.5,20,38.5);player.capabilities.allowFlying=true;player.capabilities.isFlying=true;player.sendPlayerAbilities();});state=7;return false;}
        if(state==7 && ticks>=25){if(TileEntityCanopy.at(mc.world,POS)==null){require(ticks<200,"client destination tile did not arrive");return false;}pending=mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);player.openGui(com.vandorlabs.VandorLabs.instance,GuiHandler.GUI_LARGE_CANOPY,player.world,40,20,40);require(player.openContainer instanceof com.vandorlabs.container.ContainerLargeCanopy,"server did not authorize canopy container");});state=1;return false;}
        if(state==1 && ticks==25){require(mc.currentScreen instanceof GuiLargeCanopy,"server did not open owner settings; screen="+(mc.currentScreen==null?"null":mc.currentScreen.getClass().getName())+" tile="+TileEntityCanopy.at(mc.world,POS));LargeCanopyRuntimeChecks.icons(mc);GuiLargeCanopy gui=(GuiLargeCanopy)mc.currentScreen;
            for(String field:new String[]{"depth","scale","shade","tint"}){java.lang.reflect.Field reflected=GuiLargeCanopy.class.getDeclaredField(field);reflected.setAccessible(true);reflected.setInt(gui,field.equals("depth")?80:field.equals("scale")?40:field.equals("shade")?12:0);}gui.initGui();
            state=5;ticks=0;return false;}
        if(state==5 && ticks==5){shot(mc,output,"large_canopy_settings");((GuiLargeCanopy)mc.currentScreen).apply();state=2;ticks=0;return false;}
        if(state==2 && ticks==30){pending=mc.getIntegratedServer().addScheduledTask(()->{TileEntityCanopy tile=TileEntityCanopy.at(mc.getIntegratedServer().getWorld(0),POS);require(tile.large.depth==80 && tile.large.scale==40 && tile.large.shade==12 && tile.large.tint==0,"GUI live packet not applied");require(tile.cells().size()<125 && tile.cells().size()>25,"live hollow footprint");});state=3;return false;}
        if(state==3 && ticks==5){mc.player.closeScreen();pending=mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);player.capabilities.allowFlying=true;player.capabilities.isFlying=true;player.setPositionAndUpdate(48,26,35);player.sendPlayerAbilities();});state=6;return false;}
        if(state==6 && ticks==5){mc.player.noClip=true;mc.player.capabilities.isFlying=true;mc.player.setPosition(48,26,35);Vec3d look=new Vec3d(42.5,22.5,42.5).subtract(mc.player.getPositionEyes(1));mc.player.rotationYaw=mc.player.prevRotationYaw=(float)Math.toDegrees(Math.atan2(-look.x,look.z));mc.player.rotationPitch=mc.player.prevRotationPitch=(float)-Math.toDegrees(Math.atan2(look.y,Math.hypot(look.x,look.z)));state=4;ticks=0;return false;}
        if(state==4 && ticks==25){shot(mc,output,"large_canopy_5x5_depth80");System.out.println("[vandorlabs][reprolab] large-canopy-live PASS GUI-live palette depth-extrema rotated-picking ownership vehicle-hull padded-items screenshots=2");return true;}
        return false;
    }
    private static void shot(Minecraft mc,File output,String name)throws Exception{ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_"+name+".png"));}
}
