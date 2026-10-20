package com.vandorlabs.client;

import com.vandorlabs.container.ContainerTurntable;
import com.vandorlabs.tiles.*;
import com.vandorlabs.turntable.TurntableTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ScreenShotHelper;
import java.io.File;
import java.lang.reflect.*;

/** Saved compatibility, independent state selection and native atlas/menu checks. */
final class TurntableTextureRuntimeChecks {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    static void run(Minecraft mc,File output,TileEntityTurntableController fixture)throws Exception {
        TileEntityTurntableController saved=new TileEntityTurntableController();
        saved.readFromNBT(new NBTTagCompound());
        require(saved.surfaceOn.equals("original_on")&&saved.surfaceOff.equals("original_off"),"Legacy surface defaults");
        NBTTagCompound settings=new TileEntityTurntableController().writeToNBT(new NBTTagCompound());
        for(String on:TurntableTextures.IDS)for(String off:TurntableTextures.IDS){
            settings.setString("SurfaceOn",on);settings.setString("SurfaceOff",off);saved.settings(settings);
            TileEntityTurntableController restored=new TileEntityTurntableController();restored.readFromNBT(saved.getUpdateTag());
            require(on.equals(restored.surfaceOn)&&off.equals(restored.surfaceOff),"Independent surface settings survive save/update NBT");
        }
        settings.setString("SurfaceOn","invalid");settings.removeTag("SurfaceOff");String previous=saved.surfaceOff;saved.settings(settings);
        require(saved.surfaceOn.equals("original_on")&&saved.surfaceOff.equals(previous),"Invalid artwork falls back; partial settings preserve other state");
        for(String id:TurntableTextures.IDS){
            String path=TurntableTextures.texture(id,true).substring("vandorlabs:blocks/".length());
            int choice=ScreenHousingTextures.screenIndex(path);
            require("Turntable".equals(ScreenHousingTextures.category(choice))&&ScreenHousingTextures.visible(choice)&&HousingTextureList.generalTexture(choice),"Shared Turntable material: "+id);
            for(boolean high:new boolean[]{false,true})require(mc.getTextureMapBlocks().getAtlasSprite(TurntableTextures.texture(id,high)).getIconWidth()==(high?512:64),"Surface atlas resolution: "+id);
        }
        GuiScreen old=mc.currentScreen;
        Method values=GuiTurntable.class.getDeclaredMethod("values");values.setAccessible(true);
        Field channels=GuiTurntable.class.getDeclaredField("fields");channels.setAccessible(true);
        Field picker=GuiTurntable.class.getDeclaredField("textures");picker.setAccessible(true);
        try {for(int[] size:new int[][]{{320,240},{460,340}}){
            GuiTurntable gui=new GuiTurntable(new ContainerTurntable(fixture));mc.displayGuiScreen(gui);gui.setWorldAndResolution(mc,size[0],size[1]);
            ((GuiTextField[])channels.get(gui))[3].setText("65011,65012");
            for(int state=1;state<=2;state++){
                gui.actionPerformed(new GuiButton(13+state,0,0,""));
                HousingTextureList list=(HousingTextureList)picker.get(gui);
                require(list!=null,"Surface artwork list opened");
                for(int choice=0;choice<TurntableTextures.IDS.length;choice++){
                    NBTTagCompound before=(NBTTagCompound)values.invoke(gui);gui.chooseTexture(choice);
                    NBTTagCompound after=(NBTTagCompound)values.invoke(gui);
                    require(after.getString(state==1?"SurfaceOn":"SurfaceOff").equals(TurntableTextures.IDS[choice]),"Selected surface state packet");
                    require(after.getString(state==1?"SurfaceOff":"SurfaceOn").equals(before.getString(state==1?"SurfaceOff":"SurfaceOn")),"Other surface state unchanged");
                    require(after.getString("RestoreChannels").equals("65011,65012"),"Channel draft retained across selector navigation");
                }
                mc.getFramebuffer().bindFramebuffer(true);net.minecraft.client.renderer.GlStateManager.clear(16640);mc.entityRenderer.setupOverlayRendering();gui.drawScreen(-1,-1,0);
                javax.imageio.ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_turntable_texture_"+state+"_"+size[0]+".png"));
            }
            gui.actionPerformed(new GuiButton(16,0,0,""));
            require(((GuiTextField[])channels.get(gui))[3].getText().equals("65011,65012"),"Channels retained returning to controls");
        }}finally{mc.displayGuiScreen(old);}
        System.out.println("[vandorlabs][reprolab] turntable-textures PASS 36 saved state pairs legacy defaults partial/invalid settings six shared materials high/low atlas two selector pages retained channel drafts");
    }
}
