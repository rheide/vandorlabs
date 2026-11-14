package com.vandorlabs.neoforge.ports;

import com.vandorlabs.ports.ResourcePortSettings;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.nbt.*;

/** Same configuration keys and enum indices as Forge; resource contents are excluded. */
public final class NativePortData {
    public record Configuration(ResourcePortSettings settings,int redstoneMode,ChannelList channels){
        public Configuration {if(settings==null||redstoneMode<0||redstoneMode>2||channels==null)throw new IllegalArgumentException("Invalid resource port configuration");}
    }
    public static Configuration placed(int side){return new Configuration(ResourcePortSettings.placed(side),0,ChannelList.EMPTY);}
    public static void write(CompoundTag tag,Configuration config){var settings=config.settings;int[] faces=new int[6];for(int i=0;i<6;i++)faces[i]=settings.face(i).ordinal();tag.putIntArray("PortFaces",faces);tag.putLong("PortThroughput",settings.throughput);tag.putBoolean("PortPullInputs",settings.pullInputs);tag.putInt("PortOnTexture",settings.onTexture.ordinal());tag.putInt("PortRedstoneMode",config.redstoneMode);tag.putIntArray("RedstoneChannels",config.channels.toArray());}
    public static Configuration read(CompoundTag tag){
        if(!tag.contains("PortFaces",Tag.TAG_INT_ARRAY)||!tag.contains("PortThroughput",Tag.TAG_LONG)||!tag.contains("PortPullInputs",Tag.TAG_BYTE)||!tag.contains("PortOnTexture",Tag.TAG_INT)||!tag.contains("PortRedstoneMode",Tag.TAG_INT)||!tag.contains("RedstoneChannels",Tag.TAG_INT_ARRAY))throw new IllegalArgumentException("Incomplete resource port configuration");
        int[] packed=tag.getIntArray("PortFaces");if(packed.length!=6)throw new IllegalArgumentException("Invalid resource port face count");var faces=new ResourcePortSettings.Face[6];for(int i=0;i<6;i++){if(packed[i]<0||packed[i]>2)throw new IllegalArgumentException("Invalid resource port face mode");faces[i]=ResourcePortSettings.Face.values()[packed[i]];}
        int art=tag.getInt("PortOnTexture");if(art<0||art>2||tag.getByte("PortPullInputs")<0||tag.getByte("PortPullInputs")>1)throw new IllegalArgumentException("Invalid port artwork/pull mode");
        return new Configuration(new ResourcePortSettings(faces,tag.getLong("PortThroughput"),tag.getBoolean("PortPullInputs"),ResourcePortSettings.OnTexture.values()[art]),tag.getInt("PortRedstoneMode"),ChannelList.of(tag.getIntArray("RedstoneChannels")));
    }
    private NativePortData(){}
}
