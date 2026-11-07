package com.vandorlabs.tiles;

/** Subcategories retain their parent's material eligibility and dedicated picker boundary. */
public final class HousingCategoryPolicy {
    private HousingCategoryPolicy(){}
    private static boolean in(String category,String parent){return category.equals(parent)||category.startsWith(parent+"/");}
    public static boolean door(String category){return in(category,"Doors");}
    public static boolean doubleDoor(String category){return in(category,"Double Doors");}
}
