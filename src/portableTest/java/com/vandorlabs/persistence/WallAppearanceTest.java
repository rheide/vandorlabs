package com.vandorlabs.persistence;

import com.vandorlabs.tiles.GlassTextures;

public final class WallAppearanceTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Wall appearance "+checks);}
    private static WallAppearance read(MemoryPrimitiveData tag){return WallAppearance.read(tag,v->WallAppearance.clampMaterial(v,i->i>=0&&i<40));}
    public static void main(String[] args){
        MemoryPrimitiveData tag=new MemoryPrimitiveData();WallAppearance defaults=read(tag);check(defaults.shade==2&&defaults.tint==1&&defaults.material==-1&&defaults.shape==0);check(defaults.framed&&defaults.textureJoin&&!defaults.stretch&&!defaults.join&&defaults.textureLayout()==1);check(WallAppearance.fresh(true).join&&!WallAppearance.fresh(false).join);
        for(String key:new String[]{"GlassShade","GlassTint","GlassMaterial","PortholeShape","GlassTextureLayout"})tag.putString(key,"1");defaults=read(tag);check(defaults.shade==2&&defaults.tint==1&&defaults.material==-1&&defaults.shape==0&&defaults.layout==-1);
        for(int value=-3;value<=45;value++){
            for(String key:new String[]{"GlassShade","GlassTint","GlassMaterial","PortholeShape","GlassTextureLayout"})tag.putInt(key,value);WallAppearance a=read(tag);
            check(a.shade==(GlassTextures.valid(value)?value:0));check(a.tint==(GlassTextures.validTint(value)?value:1));check(a.material==(value==-1||value>=0&&value<40?value:-1));check(a.shape==Math.max(0,Math.min(3,value)));check(a.layout==(value>=0&&value<=2?value:-1));
        }
        for(boolean stretch:new boolean[]{false,true})for(boolean framed:new boolean[]{false,true})for(boolean textureJoin:new boolean[]{false,true})for(boolean join:new boolean[]{false,true})for(int mode=-1;mode<=2;mode++){
            WallAppearance a=new WallAppearance(18,2,25,framed,stretch,textureJoin,join,3,mode);MemoryPrimitiveData saved=new MemoryPrimitiveData();a.write(saved);WallAppearance b=read(saved);check(b.shade==18&&b.tint==2&&b.material==25&&b.shape==3);check(b.framed==framed&&b.stretch==stretch&&b.textureJoin==textureJoin&&b.join==join);check(b.textureLayout()==a.textureLayout());check(saved.getInt("GlassTextureLayout")==a.textureLayout());
        }
        tag=new MemoryPrimitiveData();tag.putString("GlassFramed","wrong");tag.putString("GlassTextureJoin","wrong");check(!read(tag).framed&&!read(tag).textureJoin);
        check(WallAppearance.opacity(-1,2)==1F);check(WallAppearance.opacity(0,0)==.35F);check(WallAppearance.opacity(0,1)==.65F);check(WallAppearance.opacity(0,2)==1F);
        System.out.println("WallAppearanceTest PASS "+checks+" typed defaults/malformed/roundtrip checks");
    }
}
