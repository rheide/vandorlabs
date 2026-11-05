package com.vandorlabs.persistence;

import com.vandorlabs.render.TextureLayoutMode;
import com.vandorlabs.tiles.GlassTextures;
import java.util.function.IntPredicate;

/** Door-only glazing schema. Original artwork is -2; panel-glass defaults are deliberately different. */
public class DoorGlassData {
    public static final int ORIGINAL=-2;
    public final int material,shade,tint,layout;
    public DoorGlassData(int material,int shade,int tint,int layout,IntPredicate materialValid){
        if(!valid(material,shade,tint,layout,materialValid))throw new IllegalArgumentException("Invalid door glass finish");
        this.material=material;this.shade=shade;this.tint=tint;this.layout=layout;
    }
    public static boolean valid(int material,int shade,int tint,int layout,IntPredicate materialValid){return (material==ORIGINAL||materialValid.test(material))&&GlassTextures.valid(shade)&&GlassTextures.validTint(tint)&&TextureLayoutMode.valid(layout);}
    /** A platform supplies typed access and newly owned compounds; the codec never mutates a read source. */
    public interface Tag<T> {
        boolean compound(T root,String key);T child(T root,String key);T create();
        boolean integer(T tag,String key);int getInt(T tag,String key);void putInt(T tag,String key,int value);
        void put(T root,String key,T child);void remove(T root,String key);
    }
    public interface Factory<R> {R original();R create(int material,int shade,int tint,int layout);}
    public static <T,R> R read(Tag<T> access,T root,IntPredicate materialValid,Factory<R> factory){
        if(!access.compound(root,"DoorGlass"))return factory.original();
        T tag=access.child(root,"DoorGlass");int material=access.integer(tag,"Material")?access.getInt(tag,"Material"):ORIGINAL,
                shade=access.getInt(tag,"Shade"),tint=access.integer(tag,"Tint")?access.getInt(tag,"Tint"):1,
                layout=access.integer(tag,"Layout")?access.getInt(tag,"Layout"):1;
        return valid(material,shade,tint,layout,materialValid)?factory.create(material,shade,tint,layout):factory.original();
    }
    /** Scalar writer preserves the existing one-compound allocation; no settings snapshot or adapter is allocated. */
    public static <T> void write(Tag<T> access,T root,int material,int shade,int tint,int layout){
        if(material==ORIGINAL){access.remove(root,"DoorGlass");return;}
        T tag=access.create();access.putInt(tag,"Material",material);access.putInt(tag,"Shade",shade);access.putInt(tag,"Tint",tint);access.putInt(tag,"Layout",layout);access.put(root,"DoorGlass",tag);
    }
}
