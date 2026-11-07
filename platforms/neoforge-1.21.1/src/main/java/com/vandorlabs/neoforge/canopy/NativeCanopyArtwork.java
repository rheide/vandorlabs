package com.vandorlabs.neoforge.canopy;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Original authored sheet descriptors; contains no client atlas objects. */
public final class NativeCanopyArtwork {
    public record Texture(String id,String original,int width,int height,String sha256){}
    private record Data(String sourceSha256,int models,List<Texture> textures){}
    private static final Data DATA=load();
    private NativeCanopyArtwork(){}
    public static List<Texture> textures(){return DATA.textures;}
    private static Data load(){try(InputStream in=NativeCanopyArtwork.class.getResourceAsStream("/assets/vandorlabs/data/canopy_artwork.json")){if(in==null)throw new IllegalStateException("Missing native canopy artwork");Data data=new Gson().fromJson(new InputStreamReader(in,StandardCharsets.UTF_8),Data.class);if(data.models!=39||data.textures.size()!=7)throw new IllegalStateException("Native canopy artwork coverage");return new Data(data.sourceSha256,data.models,List.copyOf(data.textures));}catch(IOException error){throw new UncheckedIOException(error);}}
}
