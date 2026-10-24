package com.vandorlabs.tiles;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Forge catalog representation over the shared immutable filesystem discovery. */
public final class FilesystemTextures {
    public static final int ID_BASE=MaterialChoicePolicy.FILESYSTEM_BASE;
    private static final List<JsonObject> entries=new ArrayList<>();
    private static FilesystemTextureDiscovery.Catalog catalog;
    private FilesystemTextures(){}
    public static synchronized void initialize(Path defaultRoot){
        if(catalog!=null)return;
        catalog=FilesystemTextureDiscovery.discover(defaultRoot,FilesystemTextures::warn);
        for(FilesystemTextureDiscovery.Entry value:catalog.entries){
            JsonObject entry=new JsonObject();entry.addProperty("id",value.id);entry.addProperty("key",value.key);
            entry.addProperty("source",value.source);entry.addProperty("file",value.source);entry.addProperty("rectangular",true);
            entry.addProperty("category",value.category);entry.addProperty("label",value.label);
            if(value.hidden)entry.addProperty("hidden",true);entries.add(entry);
        }
    }
    public static int identifier(String relative){return FilesystemTextureDiscovery.identifier(relative);}
    public static List<JsonObject> entries(){return Collections.unmodifiableList(entries);}
    public static Path file(String relative){return catalog.file(relative);}
    private static void warn(String message){if(com.vandorlabs.VandorLabs.logger!=null)com.vandorlabs.VandorLabs.logger.warn(message);else System.err.println("[Vandor Labs] "+message);}
}
