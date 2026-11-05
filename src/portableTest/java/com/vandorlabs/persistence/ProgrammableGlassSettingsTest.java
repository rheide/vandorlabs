package com.vandorlabs.persistence;

public final class ProgrammableGlassSettingsTest {
    private static void need(boolean value){if(!value)throw new AssertionError("Programmable glass saved settings");}
    public static void main(String[] args){
        ProgrammableGlassSettings fresh=ProgrammableGlassSettings.fresh();need(fresh.shade==0&&fresh.material==-1&&fresh.textureLayout()==1&&fresh.size==1&&fresh.join&&fresh.panelFacing==-1);
        MemoryPrimitiveData missing=new MemoryPrimitiveData();ProgrammableGlassSettings old=ProgrammableGlassSettings.read(missing,v->Math.max(0,v));need(old.legacySize&&old.size==1&&old.framed&&old.tint==1&&old.join&&old.shade==0);
        int cases=0;
        for(int layout:new int[]{-2,-1,0,1,2,3})for(boolean stretch:new boolean[]{false,true})for(int size:new int[]{-1,0,1,2,99})for(int facing:new int[]{Integer.MIN_VALUE,-5,-1,0,3,4,Integer.MAX_VALUE}){
            MemoryPrimitiveData tag=new MemoryPrimitiveData();tag.putInt("GlassTextureLayout",layout);tag.putBoolean("GlassStretch",stretch);tag.putInt("GlassSize",size);tag.putInt("GlassPanelFacing",facing);tag.putInt("GlassShade",999);tag.putInt("GlassMaterial",77);tag.putInt("GlassTint",-2);tag.putBoolean("GlassFramed",false);tag.putBoolean("GlassJoin",false);
            ProgrammableGlassSettings value=ProgrammableGlassSettings.read(tag,v->v==77?5:-1);need(value.material==5&&!value.legacySize&&value.size==Math.max(0,Math.min(1,size))&&value.panelFacing==Math.floorMod(facing,4));need(value.textureLayout()==(layout>=0&&layout<=2?layout:stretch?0:1));need(!value.framed&&!value.join&&value.tint==1);
            MemoryPrimitiveData saved=new MemoryPrimitiveData();saved.putString("Future","retained");value.write(saved);ProgrammableGlassSettings copy=ProgrammableGlassSettings.read(saved,v->v);need(copy.textureLayout()==value.textureLayout()&&copy.panelFacing==value.panelFacing&&copy.shade==value.shade&&copy.stretch==value.stretch&&saved.getString("Future").equals("retained"));cases++;
        }
        MemoryPrimitiveData wrong=new MemoryPrimitiveData();wrong.putString("GlassSize","not-an-integer");wrong.putString("GlassPanelFacing","1");wrong.putString("GlassTextureLayout","2");wrong.putString("GlassTint","2");wrong.putString("GlassMaterial","4");ProgrammableGlassSettings value=ProgrammableGlassSettings.read(wrong,v->v);need(!value.legacySize&&value.size==0&&value.panelFacing==-1&&value.layout==-1&&value.tint==1&&value.material==-1);
        System.out.println("Programmable glass persistence PASS "+cases+" layouts/stretch/size/orientation cases, missing versus mistyped metadata migration, dormant settings and non-destructive tag writes");
    }
}
