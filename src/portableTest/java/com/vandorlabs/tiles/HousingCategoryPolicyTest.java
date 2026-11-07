package com.vandorlabs.tiles;
public final class HousingCategoryPolicyTest {
    private static void check(boolean value){if(!value)throw new AssertionError("Door category boundary");}
    public static void main(String[] args){for(String c:new String[]{"Doors","Doors/Glass","Doors/Utility","Doors/Pinnacle"})check(HousingCategoryPolicy.door(c)&&!HousingCategoryPolicy.doubleDoor(c));for(String c:new String[]{"Double Doors","Double Doors/Cargo"})check(!HousingCategoryPolicy.door(c)&&HousingCategoryPolicy.doubleDoor(c));for(String c:new String[]{"Materials","Glass","Door","Doorsmith","Frames/Doors"})check(!HousingCategoryPolicy.door(c)&&!HousingCategoryPolicy.doubleDoor(c));System.out.println("Housing categories PASS11");}
}
