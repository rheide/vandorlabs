package com.vandorlabs.client;

import java.lang.reflect.*;
import java.util.*;
import com.vandorlabs.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

/** Real texture-manager reloads must release and lazily reconstruct both animation families. */
final class RuntimeResourceChecks {
    static void animations(Minecraft mc)throws Exception{
        net.minecraft.tileentity.TileEntity pane=mc.world.getTileEntity(new net.minecraft.util.math.BlockPos(88,5,40));
        GlassSurfaceGroups.Layout layout=GlassSurfaceGroups.layout(pane);
        mc.world.notifyBlockUpdate(pane.getPos(),mc.world.getBlockState(pane.getPos()),mc.world.getBlockState(pane.getPos()),3);
        if(GlassSurfaceGroups.layout(pane)==layout)throw new IllegalStateException("client block update retained a joined layout");
        Method screen=TEAnimatedScreenSelector.class.getDeclaredMethod("animData",String.class),input=TEAnimatedScreenSelector.class.getDeclaredMethod("inputAnimData",String.class);
        screen.setAccessible(true);input.setAccessible(true);
        String panel=Arrays.stream(TileEntityAnimatedScreenSelector.INPUT_PANELS).filter(id->TileEntityAnimatedScreenSelector.getInputFrameCount(id)>1).findFirst().get();
        Object a=screen.invoke(null,"engineering_screen"),b=input.invoke(null,panel);
        Field texture=a.getClass().getDeclaredField("texture"),frames=a.getClass().getDeclaredField("frameCount"),cache=TEAnimatedScreenSelector.class.getDeclaredField("ANIM_CACHE");texture.setAccessible(true);frames.setAccessible(true);cache.setAccessible(true);
        ResourceLocation first=(ResourceLocation)texture.get(a),second=(ResourceLocation)texture.get(b);int af=frames.getInt(a),bf=frames.getInt(b);
        if(af<2 || bf<2 || a!=screen.invoke(null,"engineering_screen") || b!=input.invoke(null,panel))throw new IllegalStateException("animation texture was not cached");
        mc.refreshResources();
        if(!((Map<?,?>)cache.get(null)).isEmpty() || mc.getTextureManager().getTexture(first)!=null || mc.getTextureManager().getTexture(second)!=null)throw new IllegalStateException("resource reload retained animation textures");
        a=screen.invoke(null,"engineering_screen");b=input.invoke(null,panel);
        if(frames.getInt(a)!=af || frames.getInt(b)!=bf || mc.getTextureManager().getTexture(first)==null || mc.getTextureManager().getTexture(second)==null)throw new IllegalStateException("animation reload changed frame data");
        if(!GlassTextures.texture(0,0,0).equals(GlassTextures.texture(0,0,2)) || !GlassTextures.texture(0,1,0).equals(GlassTextures.texture(0,1,2)))throw new IllegalStateException("clear glass strengths have duplicate sprites");
        System.out.println("[vandorlabs][reprolab] runtime-resource-reload PASS screen-input shared-cache reuse release lazy-reload frames clear-glass-shared-sprites");
    }
    private RuntimeResourceChecks(){}
}
