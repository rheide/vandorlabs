package com.vandorlabs.client;

import com.google.gson.JsonObject;
import com.vandorlabs.CommonProxy;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.tiles.*;
import net.minecraft.util.EnumFacing;
import java.util.ArrayList;
import java.util.List;

/** Catalog name equivalence without caching dynamic Custom material resolution. */
final class TextureNameChecks {
    private static final JsonObject MENU=menu();
    private static JsonObject menu() {
        try {
            java.lang.reflect.Field field=ScreenHousingTextures.class.getDeclaredField("MENU");field.setAccessible(true);
            return (JsonObject)field.get(null);
        } catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
    static void run() {
        List<Integer> choices=new ArrayList<>();
        for(int i=0;i<ScreenHousingTextures.IDS.length;i++) {
            choices.add(i);choices.add(ScreenHousingTextures.choiceAt(i));
        }
        for(int choice:new int[]{-1,Integer.MAX_VALUE,CustomBlockMaterials.ID_BASE,FilesystemTextures.identifier("absent.png")})choices.add(choice);
        CommonProxy saved=VandorLabs.proxy;
        try {
            for(String dynamic:new String[]{null,"example:first","example:reloaded"}) {
                VandorLabs.proxy=dynamic==null?null:new CommonProxy(){@Override public String customTexture(int choice){return dynamic;}};
                for(int choice:choices) {
                    require(reference(choice).equals(ScreenHousingTextures.texture(choice)),"square texture changed: "+choice);
                    for(boolean lit:new boolean[]{false,true})require(referenceLit(choice,lit).equals(ScreenHousingTextures.texture(choice,lit)),"lit texture changed: "+choice);
                    for(EnumFacing side:EnumFacing.values())require(referenceStorage(choice,side).equals(ScreenHousingTextures.storageTexture(choice,side)),"storage texture changed: "+choice);
                }
            }
        } finally {VandorLabs.proxy=saved;}
        System.out.println("PASS: "+choices.size()+" texture choices retain square/full, unlit and storage names, missing-file fallback and dynamic Custom resolution");
    }
    // Released name selection, using the same immutable catalog inputs.
    static String reference(int choice) {
        if(CustomBlockMaterials.isCustom(choice) && VandorLabs.proxy!=null)return VandorLabs.proxy.customTexture(choice);
        JsonObject e=ScreenHousingTextures.entry(choice);
        return ScreenHousingTextures.fullTexture(choice)+(e!=null && e.has("design") && !"Double Doors".equals(ScreenHousingTextures.category(choice)) && ScreenHousingTextures.visible(choice)?"_half":"");
    }
    static String referenceLit(int choice,boolean lit) {
        int index=ScreenHousingTextures.localIndex(choice);
        JsonObject e=index<ScreenHousingTextures.LEGACY_COUNT && MENU.has(ScreenHousingTextures.IDS[index])
                ?MENU.getAsJsonObject(ScreenHousingTextures.IDS[index]):ScreenHousingTextures.entry(choice);
        return !lit && ScreenHousingTextures.visible(choice) && e!=null && e.has("unlit")?"vandorlabs:blocks/"+e.get("unlit").getAsString():reference(choice);
    }
    static String referenceStorage(int choice,EnumFacing face) {
        JsonObject e=ScreenHousingTextures.entry(choice);String key=face.getAxis()==EnumFacing.Axis.Y?"top":"side";
        return face==EnumFacing.NORTH || e==null || !e.has(key)?reference(choice):"vandorlabs:blocks/"+e.get(key).getAsString();
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
