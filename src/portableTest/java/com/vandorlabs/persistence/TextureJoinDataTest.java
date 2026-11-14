package com.vandorlabs.persistence;

import com.vandorlabs.surface.TextureJoinSettings;

/** Previous-release missing fields stay absent; explicit policies and resets round-trip. */
public final class TextureJoinDataTest {
    public static void run(){
        MemoryPrimitiveData old=new MemoryPrimitiveData();old.putString("existing-own-field","unchanged");
        TextureJoinSettings.read(old).write(old);
        need(!old.contains(TextureJoinSettings.DIRECTION_TAG)&&!old.contains(TextureJoinSettings.PARITY_TAG),"old missing fields stay absent");
        need(old.getString("existing-own-field").equals("unchanged"),"unrelated own data retained");
        for(int direction=0;direction<6;direction++)for(int parity=0;parity<3;parity++){
            MemoryPrimitiveData tag=new MemoryPrimitiveData();TextureJoinSettings settings=new TextureJoinSettings(direction,parity);settings.write(tag);
            need(settings.equals(TextureJoinSettings.read(tag)),"saved policy "+direction+"/"+parity);
        }
        MemoryPrimitiveData retained=new MemoryPrimitiveData();new TextureJoinSettings(5,2).write(retained);TextureJoinSettings.DEFAULT.write(retained);
        need(TextureJoinSettings.read(retained).equals(TextureJoinSettings.DEFAULT),"default resets retained nondefault fields");
        MemoryPrimitiveData explicit=new MemoryPrimitiveData();TextureJoinSettings.DEFAULT.write(explicit,true);
        need(explicit.containsInt(TextureJoinSettings.DIRECTION_TAG)&&explicit.containsInt(TextureJoinSettings.PARITY_TAG),"explicit old default fields retain their presence");
        System.out.println("Texture join persistence PASS previous-release missing-field preservation, eighteen policies, retained reset and unrelated data");
    }
    private static void need(boolean value,String stage){if(!value)throw new AssertionError(stage);}
    private TextureJoinDataTest(){}
}
