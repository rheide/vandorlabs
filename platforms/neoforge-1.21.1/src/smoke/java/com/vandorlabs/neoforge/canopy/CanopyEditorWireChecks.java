package com.vandorlabs.neoforge.canopy;

import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import java.nio.file.*;
import java.util.Arrays;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.*;

/** Source settings projection plus bounded codec and exact eye-to-box reach boundaries. */
public final class CanopyEditorWireChecks {
 private static int checks;
 private static void need(boolean yes,String text){checks++;if(!yes)throw new AssertionError(text);}
 private static byte[] encode(NativeCanopyEdit edit){var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);try{edit.encode(b);byte[] raw=new byte[b.readableBytes()];b.readBytes(raw);return raw;}finally{b.release();}}
 private static NativeCanopyEdit decode(byte[] raw){var b=new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(raw),RegistryAccess.EMPTY);try{return NativeCanopyEdit.decode(b);}finally{b.release();}}
 private static void refused(byte[] raw){try{decode(raw);throw new AssertionError("Malformed Canopy edit admitted");}catch(IllegalArgumentException expected){checks++;}}
 public static void main(String[] args)throws Exception {
  int profiles=0;try(var reader=Files.newBufferedReader(Path.of(args[0]))){for(String line;(line=reader.readLine())!=null;){var row=JsonParser.parseString(line).getAsJsonObject();var saved=CanopySettingsChecks.tags(row.getAsJsonObject("saved"));var settings=NativeCanopySettings.read(saved);var input=new NativeCanopyEdit(new BlockPos(-30000000,73,29999999),17,profiles,settings);byte[] raw=encode(input);need(raw.length==55+settings.data().channels.size()*4,"bounded exact bytes");var output=decode(raw);var projected=new CompoundTag();output.settings().write(projected);need(projected.equals(saved),"actual released fields survive native edit profile="+profiles);need(output.pos().equals(input.pos())&&output.window()==17&&output.request()==profiles,"session roundtrip");need(Arrays.equals(raw,encode(output)),"canonical byte roundtrip");profiles++;}}
  need(profiles==456,"frozen Source edit profiles");byte[] valid=encode(new NativeCanopyEdit(BlockPos.ZERO,1,0,NativeCanopySettings.fresh()));
  for(int length=0;length<NativeCanopyEdit.MAX_BYTES+2;length++)if(length!=55)refused(Arrays.copyOf(valid,length));
  for(int offset:new int[]{8,12,16,20,24,28,32,36,40,44,48}){byte[] bad=valid.clone();Arrays.fill(bad,offset,offset+4,(byte)255);if(offset==48)continue;refused(bad);}
  for(int at:new int[]{52,53,54}){byte[] bad=valid.clone();bad[at]=(byte)255;refused(bad);}
  var box=new AABB(0,0,0,20,1,1);need(NativeCanopyMenus.distanceSq(new Vec3(28,.5,.5),box)==64,"Source bounds range accepts far anchor");need(NativeCanopyMenus.distanceSq(new Vec3(28.001,.5,.5),box)>64,"Source bounds range refuses beyond eight");need(NativeCanopyMenus.distanceSq(new Vec3(10,.5,.5),box)==0,"inside selection bounds");need(NativeCanopyMenus.distanceSq(new Vec3(-1,-2,-3),box)==14,"diagonal closest point");
  System.out.println("[vandorlabs][canopy-editor-wire] PASS SourceProfiles="+profiles+" checks="+checks+" nativeBytes=55..311 bounded/noNBT/sessionScalars");
 }
}
