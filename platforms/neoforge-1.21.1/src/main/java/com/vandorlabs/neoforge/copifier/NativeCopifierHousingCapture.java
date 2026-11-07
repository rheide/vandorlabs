package com.vandorlabs.neoforge.copifier;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.ModernHousingTags;
import com.vandorlabs.neoforge.redstone.NativeTransmissionMigration;
import com.vandorlabs.neoforge.shape.NativeWallEntity;
import com.vandorlabs.neoforge.shape.PanelPlayback;
import com.vandorlabs.persistence.*;
import com.vandorlabs.redstone.*;
import net.minecraft.nbt.*;

/** Inactive saved-data projection. Installed-host authorization and capture/apply remain separate. */
public final class NativeCopifierHousingCapture {
    public enum Family {
        CUBE("programmable_block",true,false,false,false,false,false),
        SLAB("programmable_slab",true,true,false,false,false,false),
        STAIRS("programmable_stairs",true,true,false,false,false,true),
        WALL("programmable_wall",false,false,false,false,false,false),
        PORTHOLE("programmable_porthole_wall",false,true,false,true,false,false),
        FULL_PORTHOLE("programmable_porthole_block",false,true,false,true,false,false),
        DIAGONAL("programmable_diagonal_wall",false,false,true,false,false,false),
        DIAGONAL_PORTHOLE("programmable_diagonal_porthole",false,true,true,true,false,false),
        DIAGONAL_GLASS("programmable_diagonal_glass",false,false,true,true,true,false);
        final String id;final boolean faces,side,diagonal,pane,glass,stairs;
        Family(String id,boolean faces,boolean side,boolean diagonal,boolean pane,boolean glass,boolean stairs){this.id=id;this.faces=faces;this.side=side;this.diagonal=diagonal;this.pane=pane;this.glass=glass;this.stairs=stairs;}
        public static Family of(String id){for(var family:values())if(family.id.equals(id))return family;throw new IllegalArgumentException("Unsupported housing family: "+id);}
    }
    public static CompoundTag capture(Family family,CompoundTag saved){
        var migration=NativeTransmissionMigration.read(saved);
        if(!migration.installable())throw new IllegalArgumentException("Unsupported installed transmission ownership");
        var tag=migration.install();var access=new ModernHousingTags(tag);
        var housing=HousingSettings.read(access,0,family.faces,ModernFinishCatalog::storedChoice,ModernFinishCatalog::validChoice);
        var out=new CompoundTag();out.putInt("wall_texture",housing.mainTexture);
        if(family.side)out.putInt("side_texture",housing.sideTexture);
        out.putBoolean("slab_tile_sides",housing.tileSides);out.putInt("texture_layout",housing.layout());
        if(family.faces){var faces=new CompoundTag();faces.putBoolean("enabled",housing.facesEnabled);if(housing.facesEnabled)faces.putIntArray("choices",housing.faces());out.put("face_textures",faces);}
        if(family.stairs){var stairs=StairState.read(access);out.putInt("stair_tread",stairs.tread);out.putInt("stair_roll",stairs.roll);}
        var walls=WallSettings.read(access);
        if(family.diagonal){out.putBoolean("diagonal_full_width",walls.fullWidth);var geometry=new CompoundTag();geometry.putInt("mode",WallSettings.geometry(walls.halfHeight,walls.fullWidth));geometry.putInt("fill",walls.fill);geometry.putInt("join",walls.join);out.put("diagonal_geometry",geometry);}
        if(family.pane){
            var glass=PanelGlassSettings.read(access,value->value==-1||ModernFinishCatalog.validChoice(value)?value:-1);
            out.putBoolean("join",walls.joinPortholes);out.putInt("porthole_shape",walls.portholeShape);out.putInt("glass_shade",glass.shade);out.putInt("glass_tint",glass.tint);out.putInt("glass_material",glass.material);
            var layout=new CompoundTag();layout.putBoolean("Stretch",glass.stretch);layout.putInt("Mode",glass.textureLayout());layout.putBoolean("TextureJoin",glass.textureJoin);PanelPlayback.read(tag).writeSettings(layout);out.put("glass_layout",layout);
            if(family.glass)out.putBoolean("framed",glass.framed);
        }
        var frames=FrameSettings.read(access,NativeWallEntity::validFrame,NativeWallEntity::validSurface,ModernFinishCatalog::validChoice);var frameTag=new CompoundTag();frames.write(new ModernHousingTags(frameTag));out.put("FrameAppearance",frameTag);
        ChannelList channels=ChannelList.of(Math.max(0,tag.getInt("RedstoneChannel")));
        if(tag.contains("RedstoneChannels",Tag.TAG_INT_ARRAY))try{channels=ChannelList.of(tag.getIntArray("RedstoneChannels"));}catch(IllegalArgumentException ignored){/* The released codec retains its scalar fallback. */}
        if(channels.size()<=1)out.putInt("redstone_channel",channels.first());else out.putIntArray("redstone_channel",channels.toArray());
        var main=tag.getCompound("ForgeData").getCompound(TransmissionSettings.SETTINGS).getCompound("main");var transmission=new CompoundTag();
        transmission.putInt("Range",TransmissionSettings.readRange(main.contains("Range",Tag.TAG_INT),main.getInt("Range"),true));transmission.putBoolean("Local",TransmissionSettings.readLocal(main.contains("Local",Tag.TAG_BYTE),main.getBoolean("Local"),false));transmission.putIntArray("Channels",TransmissionSettings.readChannels(main.contains("Channels",Tag.TAG_INT_ARRAY),main.getIntArray("Channels"),ChannelList.EMPTY).toArray());out.put("Transmission",transmission);
        return out;
    }
    private NativeCopifierHousingCapture(){}
}
