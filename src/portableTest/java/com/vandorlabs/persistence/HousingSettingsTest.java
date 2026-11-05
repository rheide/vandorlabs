package com.vandorlabs.persistence;

import java.util.Arrays;

public final class HousingSettingsTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static HousingSettings read(HousingTags tags,int previous,boolean sides){
        return HousingSettings.read(tags,previous,sides,value->value>=0&&value<100?value:0,value->value>=0&&value<100);
    }
    public static void main(String[] ignored){
        MemoryPrimitiveData tags=new MemoryPrimitiveData();
        check(read(tags,15,true).mainTexture==15,"missing main must retain previous without migration");
        tags.putString(HousingSettings.MAIN,"14");
        check(read(tags,15,true).mainTexture==15,"mistyped main must retain previous");
        for(int saved:new int[]{0,13,14,15,29}){
            tags=new MemoryPrimitiveData();tags.putInt(HousingSettings.MAIN,saved);
            check(read(tags,7,false).mainTexture==(saved==14?0:saved>14?saved-1:saved),"unversioned migration "+saved);
            tags.putString(HousingSettings.VERSION,"1");
            check(read(tags,7,false).mainTexture==(saved==14?0:saved>14?saved-1:saved),"mistyped version");
            tags.putInt(HousingSettings.VERSION,1);
            check(read(tags,7,false).mainTexture==saved,"versioned index "+saved);
        }
        for(boolean previous:new boolean[]{false,true})for(int layout:new int[]{-99,-1,0,1,2,3,99}){
            tags=new MemoryPrimitiveData();tags.putInt(HousingSettings.LAYOUT,layout);
            HousingSettings state=read(tags,3,previous);
            check(state.layout()==(layout>=0&&layout<=2?layout:previous?1:0),"layout/fallback");
            check(state.rawLayout==(layout>=0&&layout<=2?layout:-1),"raw layout sentinel");
            check(state.tileSides==previous,"missing slab bool preserves previous");
            tags.putInt(HousingSettings.TILE_SIDES,previous?0:1);
            check(read(tags,3,previous).tileSides==previous,"mistyped slab bool preserves previous");
            tags.putBoolean(HousingSettings.TILE_SIDES,!previous);
            check(read(tags,3,previous).tileSides!=previous,"explicit slab bool");
        }
        tags=new MemoryPrimitiveData();int[] faces={1,2,3,4,5,6};tags.putIntArray(HousingSettings.FACES,faces);
        HousingSettings state=read(tags,3,true);
        check(!state.facesEnabled&&Arrays.equals(state.faces(),faces),"disabled choices preserved");
        int[] copy=state.faces();copy[0]=99;check(state.faces()[0]==1,"immutable faces");
        tags.putBoolean(HousingSettings.FACES_ENABLED,true);tags.putInt(HousingSettings.SIDE,7);
        state=read(tags,3,true);MemoryPrimitiveData output=new MemoryPrimitiveData();state.write(output);
        HousingSettings restored=read(output,0,false);
        check(restored.mainTexture==3&&restored.sideTexture==7&&restored.layout()==1&&restored.facesEnabled&&Arrays.equals(restored.faces(),faces),"complete roundtrip");
        tags.putIntArray(HousingSettings.FACES,new int[]{1,2});
        check(Arrays.equals(read(tags,0,false).faces(),new int[]{-1,-1,-1,-1,-1,-1}),"short face array");
        tags.putIntArray(HousingSettings.FACES,new int[]{-2,100,0,1,2,3});
        check(Arrays.equals(read(tags,0,false).faces(),new int[]{-1,-1,0,1,2,3}),"invalid face choices");
        System.out.println("Housing settings PASS: typed prior-value defaults, unversioned grille migration, layouts, six immutable face overrides");
    }
}
