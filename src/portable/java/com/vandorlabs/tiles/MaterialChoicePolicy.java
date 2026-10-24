package com.vandorlabs.tiles;

/** Saved material namespaces; absent external artwork must not destroy its identity. */
public final class MaterialChoicePolicy {
    public static final int CUSTOM_BASE=0x20000000,FILESYSTEM_BASE=0x40000000;
    public static boolean isCustom(int choice){return choice>=CUSTOM_BASE&&choice<FILESYSTEM_BASE;}
    public static boolean isExternal(int choice){return choice>=CUSTOM_BASE;}
    public static boolean valid(int choice,int builtinCount){return choice>=0&&(choice<builtinCount||isExternal(choice));}
    private MaterialChoicePolicy(){}
}
