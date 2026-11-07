package com.vandorlabs.neoforge.copifier;

import com.vandorlabs.neoforge.canopy.NativeCanopySettings;
import com.vandorlabs.persistence.CanopySettingsData;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.nbt.*;

/** Source clipboard choices only; installed ownership, resizing and motion remain in the Canopy adapter. */
public final class NativeCopifierCanopyEdit {
    public record Plan(boolean applicable,NativeCanopySettings settings){}
    private static boolean opening(String kind){return kind.equals("canopy")||kind.equals("long_canopy");}
    private static int number(CompoundTag tag,String key,int old){return tag.contains(key,Tag.TAG_INT)?tag.getInt(key):old;}
    public static CompoundTag capture(String kind,NativeCanopySettings settings){
        var d=settings.data();var out=new CompoundTag();out.putInt("glass_shade",d.shade);out.putInt("glass_tint",d.tint);out.putInt("glass_material",d.material);var layout=new CompoundTag();layout.putBoolean("Tile",d.tileMaterial);layout.putInt("Mode",d.textureLayout());out.put("glass_layout",layout);
        var config=new CompoundTag();if(CanopySettingsData.supports(kind)){config.putInt("CanopyDepth",d.depth);config.putInt("CanopyScale",d.scale);config.putInt("CanopyHinge",d.hinge);config.putBoolean("CanopyOpenable",d.openable);}if(opening(kind)){config.putInt("CanopyMotion",d.motion);config.putBoolean("CanopySlideForward",d.slideForward);config.putInt("CanopyAngle",d.angle);}if(!config.isEmpty())out.put("block_configuration",config);
        if(CanopySettingsData.supports(kind)||opening(kind)){out.putInt("redstone_trigger",!d.redstone?0:d.activateOnPower?1:2);out.putIntArray("redstone_channel",d.channels.toArray());}return out;
    }
    public static Plan prepare(String kind,NativeCanopySettings current,CompoundTag values){
        if(!NativeCopiedSettingsValidation.valid(values))throw new IllegalArgumentException("Invalid Canopy clipboard");boolean configurable=CanopySettingsData.supports(kind)||opening(kind);
        boolean applicable=values.contains("glass_shade",Tag.TAG_INT)||values.contains("glass_tint",Tag.TAG_INT)||values.contains("glass_material",Tag.TAG_INT)||values.contains("glass_layout",Tag.TAG_COMPOUND)||configurable&&(values.contains("redstone_channel")||values.contains("redstone_trigger")||values.contains("block_configuration"));if(!applicable)return new Plan(false,current);
        var saved=new CompoundTag();current.write(saved);if(values.contains("block_configuration",Tag.TAG_COMPOUND)){var config=values.getCompound("block_configuration");String[] keys=CanopySettingsData.supports(kind)?new String[]{"CanopyDepth","CanopyScale","CanopyHinge","CanopyOpenable"}:opening(kind)?new String[]{"CanopyMotion","CanopySlideForward","CanopyAngle"}:new String[0];for(String key:keys)if(config.contains(key))saved.put(key,config.get(key).copy());}
        var d=NativeCanopySettings.read(saved).data();int layout=d.textureLayout();boolean tile=d.tileMaterial;if(values.contains("glass_layout",Tag.TAG_COMPOUND)){var l=values.getCompound("glass_layout");if(l.contains("Tile",Tag.TAG_BYTE)){layout=l.getBoolean("Tile")?1:0;tile=layout==1;}if(l.contains("Mode",Tag.TAG_INT)){layout=l.getInt("Mode");if(layout<0||layout>2)throw new IllegalArgumentException("Canopy texture layout");tile=layout==1;}}
        boolean redstone=d.redstone,on=d.activateOnPower;ChannelList channels=d.channels;if(configurable){if(values.contains("redstone_trigger",Tag.TAG_INT)){int t=values.getInt("redstone_trigger");if(t<0||t>2)return new Plan(false,current);redstone=t!=0;on=t!=2;}if(values.contains("redstone_channel",Tag.TAG_INT_ARRAY))try{channels=ChannelList.of(values.getIntArray("redstone_channel"));}catch(IllegalArgumentException malformed){}else if(values.contains("redstone_channel",Tag.TAG_INT))channels=ChannelList.of(Math.max(0,values.getInt("redstone_channel")));}
        var next=new CanopySettingsData(d.depth,d.scale,number(values,"glass_shade",d.shade),number(values,"glass_tint",d.tint),d.mount,d.motion,d.hinge,d.angle,number(values,"glass_material",d.material),d.openable,redstone,d.slideForward,tile,on,layout,channels);return new Plan(next.valid(),next.valid()?new NativeCanopySettings(next):current);
    }
    private NativeCopifierCanopyEdit(){}
}
