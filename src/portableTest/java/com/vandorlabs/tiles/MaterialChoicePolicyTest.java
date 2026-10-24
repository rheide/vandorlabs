package com.vandorlabs.tiles;

public final class MaterialChoicePolicyTest {
    private static void check(boolean value){if(!value)throw new AssertionError("Material choice namespace changed");}
    public static void main(String[] args){
        check(!MaterialChoicePolicy.valid(-1,434));check(MaterialChoicePolicy.valid(0,434));check(MaterialChoicePolicy.valid(433,434));
        check(!MaterialChoicePolicy.valid(434,434));check(!MaterialChoicePolicy.valid(0x1fffffff,434));
        check(MaterialChoicePolicy.CUSTOM_BASE==0x20000000&&MaterialChoicePolicy.FILESYSTEM_BASE==0x40000000);
        for(int choice:new int[]{0x20000000,0x23456789,0x3fffffff,0x40000000,0x456789ab,Integer.MAX_VALUE})check(MaterialChoicePolicy.valid(choice,434)&&MaterialChoicePolicy.isExternal(choice));
        check(MaterialChoicePolicy.isCustom(0x23456789));check(!MaterialChoicePolicy.isCustom(0x456789ab));
        System.out.println("MaterialChoicePolicyTest PASS stable builtin/custom/filesystem namespaces and unresolved saved identifiers");
    }
}
