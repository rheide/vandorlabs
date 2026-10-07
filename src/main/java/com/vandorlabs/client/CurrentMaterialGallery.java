package com.vandorlabs.client;

import com.vandorlabs.items.ItemProgrammableArmor;
import com.vandorlabs.tiles.ScreenHousingTextures;
import java.util.*;

/** The gallery follows currently visible bundled materials, excluding per-install filesystem additions. */
final class CurrentMaterialGallery {
    static List<Integer> choices() {
        List<Integer> result=new ArrayList<>();
        for(int choice=0;choice<ScreenHousingTextures.BUILTIN_COUNT;choice++)
            if(ScreenHousingTextures.visible(choice) && HousingTextureList.generalTexture(choice))result.add(choice);
        result.sort(Comparator.comparing((Integer choice)->ScreenHousingTextures.category(choice),String.CASE_INSENSITIVE_ORDER)
                .thenComparing(HousingTextureList::name,String.CASE_INSENSITIVE_ORDER).thenComparingInt(Integer::intValue));
        return result;
    }
}
