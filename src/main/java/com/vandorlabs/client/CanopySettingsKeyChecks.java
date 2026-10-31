package com.vandorlabs.client;

import com.vandorlabs.canopy.*;
import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.tiles.TileEntityCanopy;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.nbt.NBTTagCompound;

/** Exact former key spelling, direct mutable edits and bounded model-name tables. */
final class CanopySettingsKeyChecks {
    private static String reference(LargeCanopySettings s){return s.depth+":"+s.scale+":"+s.shade+":"+s.tint+":"+s.mount+":"+s.motion+":"+s.hinge+":"+s.angle+":"+s.material+":"+s.textureLayout()+":"+s.slideForward+":"+s.openable+":"+s.redstone+":"+s.activateOnPower+":"+s.channels;}
    private static void check(LargeCanopySettings s){String key=s.key();if(!key.equals(reference(s))||s.key()!=key)throw new IllegalStateException("Canopy key value/reuse mismatch");}
    static void run(){
        try{
            int cases=0;
            for(int shade=0;shade<19;shade++)for(int tint=0;tint<4;tint++)for(int material:new int[]{-1,0}){
                LargeCanopySettings s=new LargeCanopySettings();s.shade=shade;s.tint=tint;s.material=material;check(s);
                NBTTagCompound first=new NBTTagCompound();s.write(first);s.key();NBTTagCompound second=new NBTTagCompound();s.write(second);
                if(!first.equals(second))throw new IllegalStateException("Canopy cache changed saved settings");cases++;
            }
            LargeCanopySettings s=new LargeCanopySettings();check(s);
            for(java.lang.reflect.Field field:LargeCanopySettings.class.getFields()){
                if(java.lang.reflect.Modifier.isStatic(field.getModifiers()))continue;
                if(field.getType()==int.class)field.setInt(s,field.getInt(s)+1);
                else if(field.getType()==boolean.class)field.setBoolean(s,!field.getBoolean(s));
                else if(field.getType()==ChannelList.class)field.set(s,ChannelList.of(7,8));
                check(s);cases++;
            }
            s.textureLayout(2);check(s);String retained=s.key();s.tileMaterial=!s.tileMaterial;
            if(s.key()!=retained)throw new IllegalStateException("Semantically identical layout rebuilt key");
            s.channels=ChannelList.of(7,8);retained=s.key();s.channels=ChannelList.of(7,8);
            if(s.key()!=retained)throw new IllegalStateException("Equal channel bank rebuilt key");
            java.lang.reflect.Method model=TileEntityCanopy.class.getDeclaredMethod("modelId",BlockCanopy.class,int.class);model.setAccessible(true);
            for(String kind:new String[]{"regular_canopy_glass","angled_front_1x1","angled_front_1x2"}){
                BlockCanopy block=new BlockCanopy(kind);
                for(int mask=0;mask<16;mask++){
                    int connected=mask&11;String expected=kind.equals("regular_canopy_glass")?String.format(java.util.Locale.ROOT,"regular_1x1_%02x",mask):connected==0?kind:String.format(java.util.Locale.ROOT,"%s_%02x",kind,connected);
                    String result=(String)model.invoke(null,block,mask);
                    if(!expected.equals(result)||result!=model.invoke(null,block,mask))throw new IllegalStateException("Canopy model name changed "+kind+":"+mask);cases++;
                }
            }
            for(String kind:new String[]{"canopy","long_canopy","front_canopy_glass","large_front_canopy_glass"}){
                BlockCanopy block=new BlockCanopy(kind);
                if(!model.invoke(null,block,0).equals(kind))throw new IllegalStateException("Fixed canopy model changed");cases++;
            }
            System.out.println("[vandorlabs][reprolab] canopy-settings-key PASS cases="+cases+" exact prior key strings direct field/layout/channel edits no saved data changes model masks stable retained keys");
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private CanopySettingsKeyChecks(){}
}
