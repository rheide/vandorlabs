package com.vandorlabs.items;

import com.google.gson.*;
import net.minecraft.inventory.EntityEquipmentSlot;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** A separate, stable choice namespace for native armor UV atlases and their icons. */
public final class ArmorTextures {
    public static final class Entry {
        public final int choice;
        public final EntityEquipmentSlot slot;
        public final String name, label, worn, icon, model;
        private Entry(JsonObject json) {
            choice=json.get("choice").getAsInt();
            slot=EntityEquipmentSlot.valueOf(json.get("slot").getAsString());
            name=json.get("name").getAsString();label=json.get("label").getAsString();
            worn=json.get("worn").getAsString();icon=json.get("icon").getAsString();
            model=json.get("model").getAsString();
        }
    }
    public static final List<Entry> ALL;
    private static final Map<Integer,Entry> CHOICES=new HashMap<>();
    static {
        List<Entry> entries=new ArrayList<>();
        try(InputStream stream=ArmorTextures.class.getResourceAsStream("/assets/vandorlabs/data/armor_textures.json")) {
            if(stream==null)throw new IllegalStateException("Armor catalog missing");
            for(JsonElement value:new JsonParser().parse(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonArray()) {
                Entry entry=new Entry(value.getAsJsonObject());
                if(entry.choice<0x10000000 || entry.choice>=0x20000000
                        || entry.slot.getSlotType()!=EntityEquipmentSlot.Type.ARMOR
                        || CHOICES.put(entry.choice,entry)!=null)throw new IllegalStateException("Invalid or duplicate armor choice: "+entry.choice);
                entries.add(entry);
            }
        } catch(IOException e) {throw new ExceptionInInitializerError(e);}
        ALL=Collections.unmodifiableList(entries);
    }
    private ArmorTextures() { }
    public static Entry entry(int choice) {return CHOICES.get(choice);}
    public static boolean fits(int choice,EntityEquipmentSlot slot) {
        Entry entry=entry(choice);return entry!=null && entry.slot==slot;
    }
}
