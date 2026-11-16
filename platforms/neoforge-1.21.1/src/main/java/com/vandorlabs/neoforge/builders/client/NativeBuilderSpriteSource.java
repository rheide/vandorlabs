package com.vandorlabs.neoforge.builders.client;

import com.google.gson.JsonParser;
import com.mojang.serialization.MapCodec;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.renderer.texture.atlas.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** Only exact builder dependencies are stitched; no scans of the other frame-machine resource trees. */
public final class NativeBuilderSpriteSource implements SpriteSource {
    public static final MapCodec<NativeBuilderSpriteSource> CODEC=MapCodec.unit(NativeBuilderSpriteSource::new);
    public static final SpriteSourceType TYPE=new SpriteSourceType(CODEC);
    @Override public SpriteSourceType type(){return TYPE;}
    @Override public void run(ResourceManager manager,Output output){
        var manifest=ResourceLocation.parse("vandorlabs:data/native_builders.json");
        try(var reader=new InputStreamReader(manager.getResourceOrThrow(manifest).open(),StandardCharsets.UTF_8)){
            var textures=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("textures");
            for(String name:textures.keySet()){
                var id=ResourceLocation.parse(name);var path=ResourceLocation.fromNamespaceAndPath(id.getNamespace(),"textures/"+id.getPath()+".png");var resource=manager.getResourceOrThrow(path);
                output.add(id,loader->loader.loadSprite(id,resource,(location,size,image,metadata)->new net.minecraft.client.renderer.texture.SpriteContents(location,size,image,metadata)));
            }
        }catch(java.io.IOException failure){throw new IllegalStateException("Missing fixed native builder artwork",failure);}
    }
}
