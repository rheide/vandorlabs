package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import com.vandorlabs.blocks.ModBlocks;
import java.util.*;

/** Listed artwork rows remain stable while legacy framed choices resolve to separate frames. */
final class ScreenDesignChecks {
    static void run() {
        int choices=0,aliases=0;
        for(boolean input:new boolean[]{false,true}) {
            String[] ids=input?TileEntityAnimatedScreenSelector.INPUT_PANELS:ModBlocks.DISPLAY_SCREEN_IDS.toArray(new String[0]);
            String initial=input?ids[0]:ModBlocks.SCREEN_OPTIONS.get(0).bareId;
            ScreenTextureList picker=new ScreenTextureList(0,0,200,8,initial,ids,null,input);Map<?,?> options=options(picker);
            for(int choice=ScreenHousingTextures.LEGACY_COUNT;choice<ScreenHousingTextures.BUILTIN_COUNT;choice++) {
                if(!ScreenTextureList.artwork(choice,input)||!ScreenHousingTextures.visible(ScreenHousingTextures.clamp(choice)))continue;
                picker.restore(choice);int row=picker.choice();require(options.containsKey(row),"legacy selection lost its listed artwork row");
                HousingTextureList.Option option=(HousingTextureList.Option)options.get(row);require(!option.label.matches(".*\\b(Bare|Framed)\\b.*"),"frame remains in design name");
                String nativeId=picker.selected();boolean authoredFrame=picker.framed();picker.setFramed(false);require(picker.choice()==row&&picker.framed()==authoredFrame,"separate frame reset changed artwork: choice="+choice+" row="+row+" source="+ScreenHousingTextures.entry(row).get("source")+" paired="+picker.hasPair()+" framed="+picker.framed());
                picker.setFramed(true);require(picker.choice()==row&&picker.framed()==authoredFrame&&Objects.equals(nativeId,picker.selected()),"legacy frame toggle selected retired artwork");
                require(option.texture.equals(ScreenHousingTextures.fullTexture(row)),"thumbnail does not match retained artwork");choices++;if(ScreenHousingTextures.clamp(choice)!=choice)aliases++;
            }
            picker.restore(0);require(picker.selected()==null&&!picker.hasPair(),"material became native framed artwork");
        }
        require(choices>0&&aliases>0,"no listed artwork or legacy redirects checked");
        System.out.println("PASS: shared Displays/Controls rows, separate frame choices, normalized labels, stable legacy aliases and matching retained thumbnails (choices="+choices+" aliases="+aliases+"; no GL)");
    }
    private static Map<?,?> options(ScreenTextureList picker) {
        try {java.lang.reflect.Field f=ScreenTextureList.class.getDeclaredField("list");f.setAccessible(true);Object list=f.get(picker);f=HousingTextureList.class.getDeclaredField("options");f.setAccessible(true);return (Map<?,?>)f.get(list);}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
