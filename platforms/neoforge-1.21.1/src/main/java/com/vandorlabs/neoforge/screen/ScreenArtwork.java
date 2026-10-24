package com.vandorlabs.neoforge.screen;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Logical artwork pairs generated from the same display registry as Forge. */
public final class ScreenArtwork {
    public record Entry(String id,String bare,String framed,String label){public boolean paired(){return !bare.equals(framed);}public String texture(boolean frame){return frame?framed:bare;}}
    public static final List<Entry> ENTRIES;
    static {
        var stream=ScreenArtwork.class.getResourceAsStream("/assets/vandorlabs/data/screen_artwork.json");
        if(stream==null)throw new IllegalStateException("Missing screen artwork catalog");
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){ENTRIES=List.of(new Gson().fromJson(reader,Entry[].class));}catch(java.io.IOException error){throw new ExceptionInInitializerError(error);}
    }
    private ScreenArtwork(){}
    public static Entry find(String id){for(Entry entry:ENTRIES)if(entry.id.equals(id)||entry.bare.equals(id)||entry.framed.equals(id))return entry;return ENTRIES.stream().filter(e->e.id.equals("engineering_screen")).findFirst().orElseThrow();}
    public static boolean valid(String id){for(Entry entry:ENTRIES)if(entry.id.equals(id)||entry.bare.equals(id)||entry.framed.equals(id))return true;return false;}
}
