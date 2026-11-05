package com.vandorlabs.render;

/** Scalar housing artwork decisions. Callers retain typed tag reads and sprite coordinates. */
public final class HousingTexturePolicy {
    /** Face indices are the saved Down/Up/North/South/West/East order. */
    public static int stairLocalFace(int face,int riseFacing){
        if(face<2)return face;
        int turns=riseFacing==2?2:riseFacing==4?1:riseFacing==5?3:0;
        for(int i=0;i<turns;i++)face=counterClockwise(face);
        return face;
    }
    /** Vertical slab facings retain the released East-facing horizontal UV fallback. */
    public static int housingWorldFace(int face,int facing){
        if(face<2)return face;
        int turns=facing==2?0:facing==3?2:facing==4?1:3;
        for(int i=0;i<turns;i++)face=counterClockwise(face);
        return face;
    }
    private static int counterClockwise(int face){return face==2?4:face==4?3:face==3?5:2;}
    /** Slabs and stairs use a side finish only on horizontal faces, before face overrides. */
    public static int baseChoice(int face,int main,int side,boolean sides){
        return sides && face>=2 && side>=0?side:main;
    }
    /** Cube/slab items default to Tile when the legacy byte is absent. */
    public static int itemLayout(boolean typedLayout,int rawLayout,boolean typedSides,boolean sides){
        return typedLayout?rawLayout:!typedSides || sides?1:0;
    }
    /** A present stair item tag defaults to Stretch; an absent tag retains its base model. */
    public static int stairItemLayout(boolean typedLayout,int rawLayout,boolean sides){
        return typedLayout?rawLayout:sides?1:0;
    }
    public static double stairU(int layout,boolean fit,float u,float min,float max,double aspect){
        // Float arithmetic before widening is part of the released atlas mapping.
        return layout==2 && fit && max>min?(u-min)*16/(max-min):layout==0?u:TextureLayoutMode.tileU(u,aspect);
    }
    public static double stairV(int layout,boolean fit,float v,float min,float max,int face,double aspect){
        return fit && max>min && (layout==2 || face>=2)?(v-min)*16/(max-min):layout==0?v:TextureLayoutMode.tileV(v,aspect);
    }
    public static double itemU(boolean fit,double u,double min,double max,double width,double aspect){
        double value=(u-min)/Math.max(1e-8,max-min);
        return fit?value:TextureLayoutMode.tileU(value*width,aspect);
    }
    public static double itemV(boolean fit,double v,double min,double max,double height,double aspect){
        double value=(v-min)/Math.max(1e-8,max-min);
        return fit?value:TextureLayoutMode.tileV(value*height,aspect);
    }
    private HousingTexturePolicy(){}
}
