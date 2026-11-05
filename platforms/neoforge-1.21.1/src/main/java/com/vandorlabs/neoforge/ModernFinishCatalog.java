package com.vandorlabs.neoforge;

import com.google.gson.Gson;
import com.vandorlabs.tiles.MaterialChoicePolicy;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Generated current canonical catalog; external namespaces remain saved but render with a fallback. */
public final class ModernFinishCatalog {
    public record Entry(String id,String label,String category,boolean visible,boolean general,int canonical,String texture,float u,float v){}
    public static final List<Entry> ENTRIES;
    private static final java.util.Map<Integer,Entry> REGISTERED=new java.util.concurrent.ConcurrentHashMap<>();
    static {
        var stream=ModernFinishCatalog.class.getResourceAsStream("/assets/vandorlabs/data/housing_finishes.json");
        if(stream==null)throw new IllegalStateException("Missing native housing catalog");
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){ENTRIES=List.of(new Gson().fromJson(reader,Entry[].class));}
        catch(java.io.IOException failure){throw new ExceptionInInitializerError(failure);}
    }
    public record Choice(int choice,Entry entry){}
    /** Assets supplied by a native adapter must already be registered in the atlas. No discovery workflow is implied. */
    public static void registerAsset(int choice,Entry entry){
        if(choice<MaterialChoicePolicy.CUSTOM_BASE||entry==null||!Float.isFinite(entry.u())||!Float.isFinite(entry.v())||entry.u()<=0||entry.u()>1||entry.v()<=0||entry.v()>1)throw new IllegalArgumentException("Invalid native material asset");
        if(REGISTERED.size()>=256&&!REGISTERED.containsKey(choice))throw new IllegalStateException("Native material asset limit");
        REGISTERED.put(choice,entry);
    }
    public static List<Choice> allChoices(){var choices=new java.util.ArrayList<>(java.util.stream.IntStream.range(0,ENTRIES.size()).mapToObj(index->new Choice(index,ENTRIES.get(index))).toList());REGISTERED.forEach((choice,entry)->choices.add(new Choice(choice,entry)));return List.copyOf(choices);}
    public static boolean validChoice(int choice){return MaterialChoicePolicy.valid(choice,ENTRIES.size());}
    public static int storedChoice(int choice){return choice>=MaterialChoicePolicy.CUSTOM_BASE?choice:choice>=0&&choice<ENTRIES.size()?ENTRIES.get(choice).canonical():0;}
    public static int renderChoice(int choice){int value=storedChoice(choice);return value<ENTRIES.size()||REGISTERED.containsKey(value)?value:0;}
    public static Entry entry(int choice){int index=renderChoice(choice);return REGISTERED.getOrDefault(index,ENTRIES.get(index<ENTRIES.size()?index:0));}
    private ModernFinishCatalog(){}
}
