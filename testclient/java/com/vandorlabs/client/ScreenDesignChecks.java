package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import com.vandorlabs.blocks.ModBlocks;
import java.util.*;

/** Shared picker rows represent designs; Bare/Framed changes artwork without changing row. */
final class ScreenDesignChecks {
    static void run() {
        int pairs=0;
        for(boolean input:new boolean[]{false,true}) {
            String[] ids=input?TileEntityAnimatedScreenSelector.INPUT_PANELS:ModBlocks.DISPLAY_SCREEN_IDS.toArray(new String[0]);
            String initial=input?ids[0]:ModBlocks.SCREEN_OPTIONS.get(0).bareId;
            ScreenTextureList picker=new ScreenTextureList(0,0,200,8,initial,ids,null,input);
            Map<?,?> options=options(picker);
            for(int choice=ScreenHousingTextures.LEGACY_COUNT;choice<ScreenHousingTextures.BUILTIN_COUNT;choice++) {
                if(!ScreenTextureList.artwork(choice,input))continue;
                picker.restore(choice);int row=picker.choice();
                require(options.containsKey(row),"legacy selection lost its design row");
                HousingTextureList.Option option=(HousingTextureList.Option)options.get(row);
                require(!option.label.matches(".*\\b(Bare|Framed)\\b.*"),"frame remains in design name");
                if(picker.hasPair()) {
                    picker.setFramed(false);String bare=picker.selected();
                    require(picker.choice()==row && !picker.framed(),"Bare changed row");
                    picker.setFramed(true);String framed=picker.selected();
                    require(picker.choice()==row && picker.framed(),"Framed changed row");
                    if(input)require(!bare.equals(framed),"input frame toggle did not select native variant");
                    else require(bare.equals(framed),"screen frame toggle changed family");
                    require(option.texture.equals(ScreenHousingTextures.fullTexture(ScreenHousingTextures.screenIndex((input?"console_inputs/":"")+(input?framed:sourceId(choice,true))+"_static"))),"thumbnail did not follow frame toggle");
                    pairs++;
                }
            }
            int general=0;picker.restore(general);require(picker.selected()==null && !picker.hasPair(),"material became native framed artwork");
        }
        require(pairs>0,"no paired artwork checked");
        System.out.println("PASS: shared Screens/Controls design rows, normalized labels, legacy Bare/Framed selection, stable-row toggle, native input variant and matching thumbnails ("+pairs+" pair checks; no GL)");
    }
    private static String sourceId(int choice,boolean framed) {
        String source=ScreenHousingTextures.entry(choice).get("source").getAsString().replace("_bare_","_framed_");
        if(source.startsWith("bare_"))source="framed_"+source.substring(5);
        return source.substring(0,source.length()-7);
    }
    private static Map<?,?> options(ScreenTextureList picker) {
        try {java.lang.reflect.Field f=ScreenTextureList.class.getDeclaredField("list");f.setAccessible(true);Object list=f.get(picker);f=HousingTextureList.class.getDeclaredField("options");f.setAccessible(true);return (Map<?,?>)f.get(list);}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
