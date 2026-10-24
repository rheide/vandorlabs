package com.vandorlabs.neoforge.screen;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Canonical half-height input art and frame counts, generated from the legacy catalog. */
public final class InputArtwork {
    public record Entry(String id,String bare,String framed,String label){public boolean paired(){return !bare.equals(framed);}public String texture(boolean frame){return frame?framed:bare;}}
    private record Catalog(String defaultInput,List<Entry> entries,Map<String,Integer> frames){}
    private static final Catalog CATALOG;
    public static final List<Entry> ENTRIES;
    public static final String DEFAULT;
    static {
        var stream=InputArtwork.class.getResourceAsStream("/assets/vandorlabs/data/input_artwork.json");if(stream==null)throw new IllegalStateException("Missing canonical input artwork");
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){CATALOG=new Gson().fromJson(reader,Catalog.class);ENTRIES=List.copyOf(CATALOG.entries);DEFAULT=CATALOG.defaultInput;}catch(java.io.IOException failure){throw new ExceptionInInitializerError(failure);}
    }
    private InputArtwork(){}
    public static boolean valid(String id){return id!=null&&CATALOG.frames.containsKey(id);}
    public static int frames(String id){return CATALOG.frames.getOrDefault(id,1);}
    public static Entry find(String id){for(Entry entry:ENTRIES)if(entry.id.equals(id)||entry.bare.equals(id)||entry.framed.equals(id))return entry;return find(DEFAULT);}
    public static boolean framed(String id){Entry entry=find(id);return entry.paired()?entry.framed.equals(id):id.contains("framed_");}
}
