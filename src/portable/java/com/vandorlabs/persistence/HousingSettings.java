package com.vandorlabs.persistence;

import com.vandorlabs.render.TextureLayoutMode;
import java.util.Arrays;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

/** Stable housing save fields; catalog validation belongs to the platform adapter. */
public final class HousingSettings {
    public static final String MAIN=SaveSchema.Screen.HOUSING_TEXTURE, VERSION="HousingTextureVersion",
        SIDE="SideTexture", LAYOUT="MainTextureLayout", TILE_SIDES="SlabTileSides",
        FACES_ENABLED="FaceTexturesEnabled", FACES="FaceTextures";
    public final int mainTexture,sideTexture,rawLayout;
    public final boolean tileSides,facesEnabled;
    private final int[] faces;

    private HousingSettings(int mainTexture,int sideTexture,int rawLayout,boolean tileSides,boolean facesEnabled,int[] faces) {
        this.mainTexture=mainTexture;this.sideTexture=sideTexture;
        this.rawLayout=TextureLayoutMode.valid(rawLayout)?rawLayout:-1;
        this.tileSides=tileSides;this.facesEnabled=facesEnabled;
        this.faces=faces;
    }
    public int layout(){return rawLayout<0?(tileSides?TextureLayoutMode.TILE:TextureLayoutMode.STRETCH):rawLayout;}
    public int[] faces(){return faces.clone();}
    public static HousingSettings read(HousingTags data,int previousMain,boolean previousTileSides,
            IntUnaryOperator clamp,IntPredicate validChoice) {
        boolean hasMain=data.containsInt(MAIN);
        int main=hasMain?data.getInt(MAIN):previousMain;
        if(hasMain&&!data.containsInt(VERSION))main=main==14?0:main>14?main-1:main;
        main=clamp.applyAsInt(main);
        int side=data.containsInt(SIDE)&&data.getInt(SIDE)>=0?clamp.applyAsInt(data.getInt(SIDE)):-1;
        int layout=data.containsInt(LAYOUT)?data.getInt(LAYOUT):-1;
        boolean tileSides=data.containsBoolean(TILE_SIDES)?data.getBoolean(TILE_SIDES):previousTileSides;
        int[] saved=data.getIntArray(FACES),faces=new int[6];Arrays.fill(faces,-1);
        if(saved.length==6)for(int i=0;i<6;i++)if(saved[i]>=0&&validChoice.test(saved[i]))faces[i]=saved[i];
        return new HousingSettings(main,side,layout,tileSides,data.getBoolean(FACES_ENABLED),faces);
    }
    public void write(HousingTags data) {
        write(data,mainTexture,sideTexture,rawLayout,tileSides,facesEnabled,faces());
    }
    /** The caller supplies its owned array copy; the adapter must not mutate it. */
    public static void write(HousingTags data,int main,int side,int rawLayout,boolean tileSides,boolean facesEnabled,int[] faces) {
        data.putInt(MAIN,main);data.putInt(VERSION,1);data.putInt(SIDE,side);
        data.putInt(LAYOUT,TextureLayoutMode.valid(rawLayout)?rawLayout:tileSides?TextureLayoutMode.TILE:TextureLayoutMode.STRETCH);
        data.putBoolean(TILE_SIDES,tileSides);data.putBoolean(FACES_ENABLED,facesEnabled);data.putIntArray(FACES,faces);
    }
}
