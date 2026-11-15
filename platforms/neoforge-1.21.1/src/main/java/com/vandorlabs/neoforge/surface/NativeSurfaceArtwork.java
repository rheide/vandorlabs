package com.vandorlabs.neoforge.surface;

import com.vandorlabs.surface.core.SurfaceArtwork;
import com.vandorlabs.neoforge.ModernFinishCatalog;

/** Source string settings and current native material choices name the same artwork. */
public final class NativeSurfaceArtwork {
    public static int material(String id){
        if(id!=null&&id.startsWith("choice:"))try{int choice=Integer.parseInt(id.substring(7));return ModernFinishCatalog.validChoice(choice)?choice:-1;}catch(NumberFormatException invalid){return -1;}
        if(id!=null&&id.startsWith("material:")){String key=id.substring(9);for(int i=0;i<ModernFinishCatalog.ENTRIES.size();i++)if(ModernFinishCatalog.ENTRIES.get(i).id().equals(key))return i;}
        return -1;
    }
    public static boolean valid(String id){return SurfaceArtwork.index(id)>=0||material(id)>=0;}
    public static String normalize(String id,boolean on){return valid(id)?id:SurfaceArtwork.IDS[on?1:0];}
    public static String texture(String id){int index=SurfaceArtwork.index(id);return index>=0?"vandorlabs:blocks/"+SurfaceArtwork.FILES[index]:ModernFinishCatalog.option(Math.max(0,material(id))).texture();}
    public static float usedU(String id){int material=material(id);return material<0?1:ModernFinishCatalog.option(material).u();}
    public static float usedV(String id){int material=material(id);return material<0?1:ModernFinishCatalog.option(material).v();}
    public static double aspect(String id){return usedU(id)/(double)usedV(id);}

    public static int choice(String id){int index=SurfaceArtwork.index(id);if(index>=0){String key=index<6?"turntable_"+SurfaceArtwork.IDS[index]:"surface_"+SurfaceArtwork.IDS[index].replace('-','_');for(int i=0;i<ModernFinishCatalog.ENTRIES.size();i++)if(ModernFinishCatalog.ENTRIES.get(i).id().equals(key))return i;return -index-1;}return Math.max(0,material(id));}
    public static String fromChoice(int choice){if(choice<0){int index=-choice-1;if(index>=SurfaceArtwork.IDS.length)throw new IllegalArgumentException("Unknown Surface artwork");return SurfaceArtwork.IDS[index];}if(!ModernFinishCatalog.validChoice(choice))throw new IllegalArgumentException("Unknown Surface artwork");return choice<ModernFinishCatalog.ENTRIES.size()?"material:"+ModernFinishCatalog.ENTRIES.get(choice).id():"choice:"+choice;}
    private NativeSurfaceArtwork(){}
}
