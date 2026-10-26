package com.vandorlabs.client;

import com.vandorlabs.container.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.turntable.TurntableTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.nbt.NBTTagCompound;
import java.io.File;
import java.lang.reflect.*;

/** Appearance now belongs to surfaces; legacy controller artwork remains readable for deferred migration. */
final class TurntableTextureRuntimeChecks {
    private static void require(boolean value,String why){if(!value)throw new IllegalStateException("Surface artwork: "+why);}
    static void run(Minecraft mc,File output,TileEntityTurntableController controller)throws Exception{
        SurfaceRenderChecks.run(mc);
        NBTTagCompound legacy=new NBTTagCompound();legacy.setString("SurfaceOn","basalt");legacy.setString("SurfaceOff","graphite_ivory");TileEntityTurntableController old=new TileEntityTurntableController();old.readFromNBT(legacy);require(old.hasLegacySurfaceAppearance()&&old.surfaceOn.equals("basalt")&&old.surfaceOff.equals("graphite_ivory"),"old controller choices retained for deferred migration");
        for(String on:TurntableTextures.IDS)for(String off:TurntableTextures.IDS){TileEntityTurntableSurface surface=new TileEntityTurntableSurface();surface.migrateLegacy(on,off);TileEntityTurntableSurface copy=new TileEntityTurntableSurface();copy.readFromNBT(surface.getUpdateTag());require(copy.surfaceOn.equals(on)&&copy.surfaceOff.equals(off)&&copy.textureLayout==0,"independent surface choices and old square layout survive NBT");}
        for(String id:TurntableTextures.IDS){int choice=TurntableTextures.materialChoice(id);require("Surface".equals(ScreenHousingTextures.category(choice))&&ScreenHousingTextures.visible(choice)&&HousingTextureList.generalTexture(choice),"shared Surface category: "+id);for(boolean high:new boolean[]{false,true})require(mc.getTextureMapBlocks().getAtlasSprite(TurntableTextures.texture(id,high)).getIconWidth()==(high?512:64),"native surface atlas resolution "+id);}
        TileEntityTurntableSurface fixture=(TileEntityTurntableSurface)mc.world.getTileEntity(controller.layout.root);require(fixture!=null,"live surface fixture synchronized");
        GuiScreen previous=mc.currentScreen;Field on=GuiProgrammableSurface.class.getDeclaredField("on"),off=GuiProgrammableSurface.class.getDeclaredField("off");on.setAccessible(true);off.setAccessible(true);
        try{for(int[] size:new int[][]{{320,240},{460,340}}){
            GuiProgrammableSurface gui=new GuiProgrammableSurface(new ContainerSurface(fixture));gui.inventorySlots.windowId=-81;mc.displayGuiScreen(gui);gui.setWorldAndResolution(mc,size[0],size[1]);
            for(int state=0;state<2;state++){
                if(state==1)gui.actionPerformed(new GuiButton(0,0,0,""));
                for(String id:TurntableTextures.IDS){String other=(String)(state==0?on:off).get(gui);int choice=TurntableTextures.materialChoice(id);gui.choose(choice);require((state==0?off:on).get(gui).equals(TurntableTextures.fromMaterialChoice(choice)),"clicked surface texture changes selected state");require((state==0?on:off).get(gui).equals(other),"other surface state preserved");}
                gui.choose(ScreenHousingTextures.INDUSTRIAL_BLOCK);require((state==0?off:on).get(gui).equals(TurntableTextures.fromMaterialChoice(ScreenHousingTextures.INDUSTRIAL_BLOCK)),"standard block artwork selectable on surface");
            }
            GuiTurntable controls=new GuiTurntable(new ContainerTurntable(controller));mc.displayGuiScreen(controls);controls.setWorldAndResolution(mc,size[0],size[1]);Method values=GuiTurntable.class.getDeclaredMethod("values");values.setAccessible(true);NBTTagCompound settings=(NBTTagCompound)values.invoke(controls);require(!settings.hasKey("SurfaceOn")&&!settings.hasKey("SurfaceOff"),"controller no longer configures artwork");
        }}finally{mc.displayGuiScreen(previous);}
        System.out.println("[vandorlabs][reprolab] turntable-textures PASS surface-owned196 saved pairs legacy migration defaults14 shared Surface materials high/low atlases independent surface states main catalog controller controls separated");
    }
}
