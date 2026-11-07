package com.vandorlabs.neoforge.copifier;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.trapdoor.NativeTrapdoorSettings;
import com.vandorlabs.persistence.*;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;

/** Source setter order for one leaf; topology, actor admission and power remain on the installed host. */
public final class NativeCopifierTrapdoorEdit {
    public record State(NativeTrapdoorSettings settings,boolean offsetEligible){}
    public record Plan(boolean applicable,boolean unpair,State state){}
    private static int number(CompoundTag tag,String key,int old){return tag.contains(key,Tag.TAG_INT)?tag.getInt(key):old;}
    private static boolean flag(CompoundTag tag,String key,boolean old){return tag.contains(key,Tag.TAG_BYTE)?tag.getBoolean(key):old;}
    private static ChannelList channels(CompoundTag values,ChannelList old){if(values.contains("redstone_channel",Tag.TAG_INT_ARRAY))try{return ChannelList.of(values.getIntArray("redstone_channel"));}catch(IllegalArgumentException malformed){return old;}return values.contains("redstone_channel",Tag.TAG_INT)?ChannelList.of(Math.max(0,values.getInt("redstone_channel"))):old;}
    public static State read(boolean diagonal,int facing,boolean offset,CompoundTag fields){var s=NativeTrapdoorSettings.read(fields,diagonal);return new State(new NativeTrapdoorSettings(s.choices(),s.channels(),Direction.from3DDataValue(facing).get2DDataValue(),s.hingeSide(),s.rotationFlipped(),diagonal),offset);}
    public static CompoundTag capture(State state){
        var s=state.settings();var d=s.choices();var out=new CompoundTag();out.putInt("wall_texture",d.texture);
        if(s.diagonal()){out.putInt("trapdoor_hinge_side",s.hingeSide());out.putBoolean("trapdoor_rotation_flipped",s.rotationFlipped());var geometry=new CompoundTag();geometry.putInt("mode",d.position);out.put("diagonal_geometry",geometry);}else out.putInt("trapdoor_position",d.position);
        if(state.offsetEligible())out.putInt("trapdoor_cover_facing",s.facing());out.putBoolean("trapdoor_slide_over_surface",d.overSurface);out.putBoolean("trapdoor_tile_texture",d.tileTexture);out.putInt("trapdoor_texture_layout",d.layout());out.putBoolean("trapdoor_cover",d.cover);out.putBoolean("door_sliding",d.sliding);if(s.diagonal())out.putBoolean("trapdoor_slide_into_wall",d.intoWall);out.putInt("redstone_trigger",d.trigger);if(s.channels().size()<=1)out.putInt("redstone_channel",s.channels().first());else out.putIntArray("redstone_channel",s.channels().toArray());return out;
    }
    public static Plan prepare(State current,CompoundTag values,boolean selected){
        if(!NativeCopiedSettingsValidation.valid(values))throw new IllegalArgumentException("Malformed copied Trapdoor settings");var s=current.settings();var old=s.choices();boolean diagonal=s.diagonal();
        boolean applicable=values.contains("trapdoor_texture_layout",Tag.TAG_INT)||values.contains("trapdoor_cover_facing",Tag.TAG_INT)||values.contains("trapdoor_tile_texture",Tag.TAG_BYTE)||values.contains("trapdoor_cover",Tag.TAG_BYTE)||values.contains("wall_texture",Tag.TAG_INT)||(diagonal?values.contains("diagonal_geometry",Tag.TAG_COMPOUND):values.contains("trapdoor_position",Tag.TAG_INT))||values.contains("door_sliding",Tag.TAG_BYTE)||values.contains("redstone_trigger",Tag.TAG_INT)||values.contains("redstone_channel");
        if(!applicable)return new Plan(false,false,current);
        int texture=old.texture,position=old.position,layout=old.rawLayout,trigger=old.trigger,facing=s.facing(),hinge=s.hingeSide();boolean tiled=old.tileTexture,cover=old.cover,sliding=old.sliding,into=old.intoWall,over=old.overSurface,flipped=s.rotationFlipped();var channels=s.channels();
        if(values.contains("trapdoor_tile_texture",Tag.TAG_BYTE)){boolean next=values.getBoolean("trapdoor_tile_texture");if(next!=tiled){layout=-1;tiled=next;}}
        if(values.contains("trapdoor_texture_layout",Tag.TAG_INT)){int next=values.getInt("trapdoor_texture_layout");if(next!=(layout<0?tiled?1:0:layout)){layout=next;tiled=next==1;}}
        if(values.contains("trapdoor_cover",Tag.TAG_BYTE)){boolean next=values.getBoolean("trapdoor_cover");if(!next||current.offsetEligible())cover=next;}
        if(values.contains("trapdoor_cover_facing",Tag.TAG_INT)&&current.offsetEligible())facing=Math.abs(values.getInt("trapdoor_cover_facing")%4);
        if(values.contains("door_sliding",Tag.TAG_BYTE)){if(!diagonal&&values.contains("trapdoor_slide_over_surface",Tag.TAG_BYTE))over=values.getBoolean("trapdoor_slide_over_surface");if(diagonal&&values.contains("trapdoor_slide_into_wall",Tag.TAG_BYTE))into=values.getBoolean("trapdoor_slide_into_wall");}
        int nextTexture=number(values,"wall_texture",texture),nextPosition=diagonal?values.contains("diagonal_geometry",Tag.TAG_COMPOUND)?Math.max(0,Math.min(2,values.getCompound("diagonal_geometry").getInt("mode"))):position:number(values,"trapdoor_position",position),nextTrigger=number(values,"redstone_trigger",trigger);var nextChannels=channels(values,channels);
        boolean core=nextTexture>=0&&ModernFinishCatalog.validChoice(nextTexture)&&nextPosition>=0&&nextPosition<=2&&SpaceDoorData.validTrigger(nextTrigger);
        if(core){texture=ModernFinishCatalog.storedChoice(nextTexture);position=nextPosition;trigger=nextTrigger;sliding=flag(values,"door_sliding",sliding);channels=nextChannels;}
        if(selected&&diagonal&&values.contains("door_sliding",Tag.TAG_BYTE)){if(values.contains("trapdoor_hinge_side",Tag.TAG_INT))hinge=Math.max(0,Math.min(2,values.getInt("trapdoor_hinge_side")));if(values.contains("trapdoor_rotation_flipped",Tag.TAG_BYTE))flipped=values.getBoolean("trapdoor_rotation_flipped");}
        // The generic channel setter runs after the family callback, even if configure ignored invalid core fields.
        if(selected&&values.contains("redstone_channel"))channels=nextChannels;
        var data=new TrapdoorData(texture,position,layout,trigger,tiled,cover,sliding,into,over);var next=new NativeTrapdoorSettings(data,channels,facing,hinge,flipped,diagonal);
        return new Plan(true,core&&diagonal&&(old.position==2)!=(position==2),new State(next,current.offsetEligible()));
    }
    private NativeCopifierTrapdoorEdit(){}
}
