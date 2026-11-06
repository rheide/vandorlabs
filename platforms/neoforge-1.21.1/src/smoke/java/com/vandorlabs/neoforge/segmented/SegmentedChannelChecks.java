package com.vandorlabs.neoforge.segmented;
import com.google.gson.*;
import java.nio.file.*;
import java.util.Arrays;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
/** Actual native entity decode/save, compared with the emitted source cases. */
final class SegmentedChannelChecks {
    static void run(ServerLevel level,NativeSegmentedDoorEntity tile)throws Exception{int cases=0;var rows=JsonParser.parseString(Files.readString(Path.of(System.getProperty("vandorlabs.segmentedChannelReference")))).getAsJsonArray();for(var value:rows){var row=value.getAsJsonObject();var input=TagParser.parseTag(row.get("input").getAsString());tile.loadWithComponents(input,level.registryAccess());int[] expected=new int[row.getAsJsonArray("channels").size()];for(int i=0;i<expected.length;i++)expected[i]=row.getAsJsonArray("channels").get(i).getAsInt();if(tile.inert()||!Arrays.equals(tile.getRedstoneChannels().toArray(),expected))throw new AssertionError("Source Segment channel fallback "+row+" actual="+tile.getRedstoneChannels()+" inert="+tile.inert());var saved=tile.saveWithoutMetadata(level.registryAccess());if(!Arrays.equals(saved.getIntArray("RedstoneChannels"),expected)||!saved.getCompound("ForgeCaps").equals(input.getCompound("ForgeCaps")))throw new AssertionError("Source Segment channel save/opaque capability "+row);if(input.contains("RedstoneChannel")&&!input.get("RedstoneChannel").equals(saved.get("RedstoneChannel")))throw new AssertionError("Unrelated source-ignored scalar was overwritten");tile.loadWithComponents(saved,level.registryAccess());if(tile.inert()||!Arrays.equals(tile.getRedstoneChannels().toArray(),expected))throw new AssertionError("Channel fallback save/reload "+row);cases++;}if(cases!=10)throw new AssertionError("Channel source coverage");System.out.println("[vandorlabs][segmented-channels] PASS cases="+cases+" actual loader/save/reload/opaque caps; malformed lists do not make valid topology inert");}
}
