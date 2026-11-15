package com.vandorlabs.neoforge.surface.client;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.surface.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Two actual baked records check padded/cropped metadata in an already-running player JVM. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeSurfaceCropChecks {
    private static boolean done;private static int checks;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Surface crop: "+why);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!Boolean.getBoolean("vandorlabs.surfaceCropChecks")||done)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.getOverlay()!=null)return;need(NativeSurfaceBindings.enabled()&&BuiltInRegistries.BLOCK.get(ResourceLocation.parse("vandorlabs:turntable_surface"))==NativeSurfaceBindings.BLOCK,"canonical Surface registration");var state=NativeSurfaceBindings.BLOCK.defaultBlockState();var model=mc.getBlockRenderer().getBlockModel(state);for(String id:new String[]{"door_observation_low","screen_console_inputs_analysis_framed_gas_giant_static"}){
        String selected="material:"+id;var choice=NativeSurfaceArtwork.material(selected);need(choice>=0,"existing material identity");var entry=ModernFinishCatalog.option(choice);need(Math.abs(NativeSurfaceArtwork.aspect(selected)-entry.u()/(double)entry.v())<1e-12,"native aspect follows authored crop metadata");var visual=new NativeSurfaceEntity.Visual(selected,2,1,1,0,0,Direction.UP,false);var data=net.neoforged.neoforge.client.model.data.ModelData.builder().with(NativeSurfaceEntity.VISUAL,visual).build();var quads=model.getQuads(state,null,net.minecraft.util.RandomSource.create(0),data,net.minecraft.client.renderer.RenderType.cutoutMipped());float minU=Float.POSITIVE_INFINITY,minV=minU,maxU=Float.NEGATIVE_INFINITY,maxV=maxU,minZ=minU,maxZ=maxU;int art=0;net.minecraft.client.renderer.texture.TextureAtlasSprite sprite=null;
        for(var quad:quads)if(quad.getSprite().contents().name().equals(ResourceLocation.parse(entry.texture()))){art++;sprite=quad.getSprite();var vertices=quad.getVertices();int stride=vertices.length/4;for(int vertex=0;vertex<4;vertex++){int offset=vertex*stride;float u=Float.intBitsToFloat(vertices[offset+4]),v=Float.intBitsToFloat(vertices[offset+5]),z=Float.intBitsToFloat(vertices[offset+2]);minU=Math.min(minU,u);maxU=Math.max(maxU,u);minV=Math.min(minV,v);maxV=Math.max(maxV,v);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);}}
        need(art==2&&sprite!=null,"actual emitted artwork triangles");need(Math.abs(minU-sprite.getU0())<1e-6&&Math.abs(minV-sprite.getV0())<1e-6,"emitted atlas origin");need(Math.abs(maxU-(sprite.getU0()+(sprite.getU1()-sprite.getU0())*entry.u()))<1e-6&&Math.abs(maxV-(sprite.getV0()+(sprite.getV1()-sprite.getV0())*entry.v()))<1e-6,"emitted UVs exclude padded atlas area");if(entry.u()!=entry.v())need(Math.abs((maxZ-minZ)-entry.v()/entry.u())<1e-6,"Fit physical artwork preserves non-square aspect");
    }done=true;System.out.println("[vandorlabs][surface-crop] PASS checks="+checks+" canonical/materialMetadata/emittedUV/noPadding/nonSquareFit");}
}
