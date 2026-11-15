package com.vandorlabs.neoforge.copier;
import com.vandorlabs.copier.core.CopySettings;import com.vandorlabs.redstone.ChannelList;import net.minecraft.nbt.*;import java.util.Set;
final class NativeCopySettingsData {
 static CompoundTag write(CopySettings settings){var tag=new CompoundTag();tag.putInt("Height",settings.height);tag.putInt("Increment",settings.increment);tag.putInt("Activation",settings.activation);tag.putIntArray("Channels",settings.channels.toArray());tag.putBoolean("Chip",settings.chip);return tag;}
 static CopySettings read(CompoundTag tag){if(!tag.getAllKeys().equals(Set.of("Height","Increment","Activation","Channels","Chip"))||!tag.contains("Height",Tag.TAG_INT)||!tag.contains("Increment",Tag.TAG_INT)||!tag.contains("Activation",Tag.TAG_INT)||!tag.contains("Channels",Tag.TAG_INT_ARRAY)||!tag.contains("Chip",Tag.TAG_BYTE)||tag.getByte("Chip")<0||tag.getByte("Chip")>1)throw new IllegalArgumentException("Copy recipe settings schema");return new CopySettings(tag.getInt("Height"),tag.getInt("Increment"),tag.getInt("Activation"),ChannelList.of(tag.getIntArray("Channels")),tag.getBoolean("Chip"));}
 private NativeCopySettingsData(){}
}
