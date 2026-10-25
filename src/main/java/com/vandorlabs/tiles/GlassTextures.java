package com.vandorlabs.tiles;

/** Stable glass finish IDs: the original three shades retain their saved IDs. */
public final class GlassTextures {
    public static final String[] IDS={"clear","soft_cyan","soft_dark_grey","white","orange","magenta","light_blue","yellow","lime","pink","grey","light_grey","cyan","purple","blue","brown","green","red","black"};
    public static final String[] LABELS={"Clear","Soft Cyan","Soft Dark Grey","White","Orange","Magenta","Light Blue","Yellow","Lime","Pink","Grey","Light Grey","Cyan","Purple","Blue","Brown","Green","Red","Black"};
    public static boolean valid(int id){return id>=0 && id<IDS.length;}
    public static int clamp(int id){return valid(id)?id:0;}
    public static String texture(int id,int size){return "vandorlabs:blocks/programmable_glass/low/"+IDS[clamp(id)];}
    public static String fullTexture(int id,int size){return texture(id,size).replace(":blocks/",":textures/blocks/")+".png";}
    public static boolean validTint(int tint){return tint>=0 && tint<3;}
    public static int clampTint(int tint){return validTint(tint)?tint:1;}
    public static String tintLabel(int tint){return new String[]{"Mild","Normal","Strong"}[clampTint(tint)];}
    public static String texture(int id,int size,int tint){return texture(id,size)+(tint==2?"_strong":clamp(id)==0?"":tint==0?"_mild":"");}
    public static String fullTexture(int id,int size,int tint){return texture(id,size,tint).replace(":blocks/",":textures/blocks/")+".png";}
    private GlassTextures() { }
}
