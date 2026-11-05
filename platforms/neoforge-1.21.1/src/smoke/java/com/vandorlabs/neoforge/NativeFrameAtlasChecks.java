package com.vandorlabs.neoforge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;

/** Resource pipeline only: verifies custom source registration, atlas contents and channel order. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeFrameAtlasChecks {
    @SubscribeEvent public static void stitched(TextureAtlasStitchedEvent event)throws Exception{
        if(!"true".equals(System.getenv("VANDOR_FRAME_ATLAS"))||!event.getAtlas().location().equals(TextureAtlas.LOCATION_BLOCKS))return;
        MessageDigest hash=MessageDigest.getInstance("SHA-256");int count=0;
        for(var entry:ModernFinishCatalog.ENTRIES){var frame=entry.frame();if(frame==null||frame.rail()==null)continue;
            String rail=frame.rail().substring("vandorlabs:frame-rails/".length());
            for(int edge=0;edge<4;edge++){
                var id=ResourceLocation.parse(frame.rail()+(edge==0?"":"_edge_"+edge));var contents=event.getAtlas().getSprite(id).contents();
                if(!contents.name().equals(id)||contents.width()!=64||contents.height()!=64)throw new AssertionError("Missing or malformed native frame sprite "+id);
                hash.update(rail.getBytes(StandardCharsets.UTF_8));hash.update((byte)0);hash.update((byte)edge);var image=contents.getOriginalImage();
                for(int y=0;y<64;y++)for(int x=0;x<64;x++){int pixel=image.getPixelRGBA(x,y);hash.update((byte)(pixel>>>24));hash.update((byte)pixel);hash.update((byte)(pixel>>>8));hash.update((byte)(pixel>>>16));}count++;
            }
        }
        String result=java.util.HexFormat.of().formatHex(hash.digest());
        if(count!=280||!result.equals(System.getenv().getOrDefault("VANDOR_FRAME_ATLAS_EXPECTED","cc742dc509e7fc26c470d5ae93df47de4c3c79d2f96ce99e988bc676743904ae")))throw new AssertionError("Native frame atlas mismatch "+count+" "+result);
        System.out.println("[vandorlabs][frame-atlas] PASS sprites="+count+" SHA256="+result);
    }
}
