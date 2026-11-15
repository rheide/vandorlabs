package com.vandorlabs.neoforge.seating;
import com.vandorlabs.persistence.ChairSettings;
import net.minecraft.nbt.*;
/** Source clipboard keys only. Pilot Taxi is retained data and never activates a vehicle adapter. */
public final class NativeSeatCopiedSettings {
    public record Plan(ChairSettings settings,int pilotHeight,Boolean taxi,boolean applicable){}
    public static CompoundTag capture(NativeChairEntity tile){var out=new CompoundTag();var settings=tile.settings();if(tile.kind()==NativeSeatKind.PILOT){out.putInt("chair_height",tile.pilotHeight());var config=new CompoundTag();config.putBoolean("TaxiMode",tile.taxiMode());out.put("block_configuration",config);}else {out.putInt("chair_height",settings.height);if(tile.kind().connected())out.putBoolean("join",settings.join);else {out.putInt("chair_style",settings.style);out.putInt("chair_angle",settings.angle);}}return out;}
    public static Plan prepare(NativeChairEntity tile,CompoundTag values){var old=tile.settings();int style=old.style,height=old.height,angle=old.angle,pilotHeight=tile.pilotHeight();boolean join=old.join,applicable=false;Boolean taxi=null;
        for(String key:java.util.List.of("chair_style","chair_height","chair_angle"))if(values.contains(key)&&!values.contains(key,Tag.TAG_INT))throw new IllegalArgumentException("Malformed chair setting");
        if(values.contains("chair_height")){int value=values.getInt("chair_height");if(tile.kind()==NativeSeatKind.PILOT){if(value<0||value>3)throw new IllegalArgumentException("Invalid Pilot height");pilotHeight=value;}else height=ChairSettings.height(true,value);applicable=true;}
        if(tile.kind()==NativeSeatKind.CHAIR){if(values.contains("chair_style")){style=ChairSettings.style(values.getInt("chair_style"));applicable=true;}if(values.contains("chair_angle")){angle=ChairSettings.angle(values.getInt("chair_angle"));applicable=true;}}
        if(tile.kind().connected()&&values.contains("join")){if(!values.contains("join",Tag.TAG_BYTE))throw new IllegalArgumentException("Malformed chair join");join=values.getBoolean("join");applicable=true;}
        if(tile.kind()==NativeSeatKind.PILOT&&values.contains("block_configuration")){if(!values.contains("block_configuration",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Malformed Pilot settings");var config=values.getCompound("block_configuration");if(config.contains("TaxiMode")){if(!config.contains("TaxiMode",Tag.TAG_BYTE))throw new IllegalArgumentException("Malformed Taxi data");taxi=config.getBoolean("TaxiMode");applicable=true;}}
        return new Plan(new ChairSettings(style,height,angle,join),pilotHeight,taxi,applicable);
    }
    public static boolean apply(NativeChairEntity tile,Plan plan){return plan.applicable&&tile.configureCopied(plan.settings,plan.pilotHeight,plan.taxi);}
    private NativeSeatCopiedSettings(){}
}
