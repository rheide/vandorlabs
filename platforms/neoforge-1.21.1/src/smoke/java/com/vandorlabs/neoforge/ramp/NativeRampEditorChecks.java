package com.vandorlabs.neoforge.ramp;

import com.google.gson.*;
import com.vandorlabs.persistence.LegacyBlockPosition;
import com.vandorlabs.ramp.*;
import com.vandorlabs.redstone.ChannelList;
import io.netty.buffer.Unpooled;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Independent actual Source command records plus native packet bounds and world-free editor transitions. */
public final class NativeRampEditorChecks {
 private static int checks;
 private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Ramp editor "+why);}
 private static byte[] encode(NativeRampEdit edit){var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);try{edit.encode(b);byte[] raw=new byte[b.readableBytes()];b.readBytes(raw);return raw;}finally{b.release();}}
 private static NativeRampEdit decode(byte[] raw){var b=new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(raw),RegistryAccess.EMPTY);try{return NativeRampEdit.decode(b);}finally{b.release();}}
 private static void refuse(byte[] raw){try{decode(raw);throw new AssertionError("Malformed Ramp packet accepted");}catch(IllegalArgumentException expected){checks++;}}
 public static void main(String[] args)throws Exception {
  int profiles=0,valid=0,refused=0,commits=0,sourceBytes=0,nativeBytes=0;
  try(var reader=new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(Path.of(args[0]))),java.nio.charset.StandardCharsets.UTF_8))){for(String line;(line=reader.readLine())!=null;){var row=JsonParser.parseString(line).getAsJsonObject();profiles++;var raw=Base64.getDecoder().decode(row.get("wire").getAsString());need(raw.length==row.get("bytes").getAsInt()&&raw.length==94,"actual Source request bytes");var b=ByteBuffer.wrap(raw);long packed=b.getLong();var pos=new BlockPos(LegacyBlockPosition.x(packed),LegacyBlockPosition.y(packed),LegacyBlockPosition.z(packed));b.getInt();b.getInt();b.get();boolean power=b.get()!=0;b.get();boolean lift=b.get()!=0;int direction=b.getInt();b.getInt();b.getInt();int tread=b.getInt(),travel=b.getInt();boolean extend=b.get()!=0;int speed=b.getInt();boolean match=b.get()!=0;int start=b.getInt(),end=b.getInt();need(b.getInt()==0x52414D50,"Source interpolation marker");int interpolation=b.getInt();need(b.getInt()==0x45534341,"Source escalator marker");int escalator=b.getInt();need(b.getInt()==0x4D4F4154,"Source moat marker");boolean moat=b.get()!=0;int count=b.get()&255;int[] channels=new int[count];for(int i=0;i<count;i++)channels[i]=b.getInt();need(b.getInt()==0x414E4C47,"Source analog marker");boolean analog=b.get()!=0;need(b.getInt()==0x41584953,"Source axis marker");boolean vertical=b.get()!=0;need(!b.hasRemaining(),"complete Source command consumed");
   NativeRampEdit edit;try{edit=new NativeRampEdit(pos,37,profiles,new NativeRampSettings(start,end,tread,power,lift,extend,speed,match,interpolation,escalator,moat,vertical,travel,true,direction,analog,ChannelList.of(channels)));}catch(IllegalArgumentException malformed){need(!row.get("committed").getAsBoolean(),"strict native refusal cannot lose Source commit");refused++;continue;}
   byte[] nativeRaw=encode(edit);need(nativeRaw.length==58&&decode(nativeRaw).equals(edit),"all Source command choices survive native roundtrip");sourceBytes+=raw.length;nativeBytes+=nativeRaw.length;valid++;
   int profile=row.get("profile").getAsInt();boolean usable=profile<10;
   var request=new RampConfigurationPolicy.Request(start,end,tread,power,lift,Direction.from2DDataValue(direction).get3DDataValue(),travel,extend,speed,match,interpolation,escalator,moat,vertical);
   var admission=RampConfigurationPolicy.admit(request,row.get("scan").getAsInt(),true,usable,false);need(admission.accepted()==row.get("committed").getAsBoolean(),"Source actual handler choices admission profile="+profile);if(admission.accepted())commits++;
   var calls=row.getAsJsonArray("calls");if(row.get("committed").getAsBoolean())need(calls.size()==4&&calls.get(0).getAsString().equals("configure")&&calls.get(1).getAsString().equals("configured:false:true")&&calls.get(2).getAsString().equals("channels")&&calls.get(3).getAsString().equals("analog:"+analog),"Source committed activation-error channel/analog order");
  }}
  need(profiles==512&&valid==352&&refused==160&&commits==148,"frozen Source full/slab command coverage");need(sourceBytes==33088&&nativeBytes==20416,"measured comparable request totals");
  var settings=new NativeRampDraft(NativeRampSettings.defaults(),Direction.NORTH,Direction.EAST).settings();byte[] good=encode(new NativeRampEdit(BlockPos.ZERO,37,0,settings));for(int n=0;n<=NativeRampEdit.MAX_BYTES+1;n++)if(n!=50)refuse(Arrays.copyOf(good,n));
  for(int at:new int[]{8,12,16,20,24,28,32,36,40,44}){var bad=good.clone();Arrays.fill(bad,at,at+4,(byte)255);if(at==16||at==20)ByteBuffer.wrap(bad).putInt(at,Integer.MIN_VALUE);refuse(bad);}for(int at:new int[]{48,49}){var bad=good.clone();bad[at]=(byte)255;refuse(bad);}
  int transitions=0;for(Direction scan:Direction.values())for(Direction horizontal:Direction.Plane.HORIZONTAL){var draft=new NativeRampDraft(NativeRampSettings.defaults(),scan,horizontal);need(draft.settings().hasDirection()&&draft.settings().resolvedDirection(horizontal.get3DDataValue())==horizontal.get3DDataValue(),"travel fallback independent of scan");for(int i=0;i<7;i++){need(draft.mode()==i,"released seven-mode operation indices");draft=draft.action(1,1);transitions++;}need(draft.mode()==0,"forward mode wraps");for(int i=6;i>=0;i--){draft=draft.action(1,-1);need(draft.mode()==i,"reverse seven-mode cycle");transitions++;}draft=new NativeRampDraft(settings,scan,horizontal);for(int i=0;i<2;i++){draft=draft.action(24,1);need(RampAxis.allowed(RampAxis.valueOf(scan.getAxis().name()),draft.settings().verticalLayout()?RampAxis.Y:RampAxis.valueOf(Direction.from2DDataValue(draft.settings().direction()).getAxis().name())),"axis choice obeys mounting axis");}need(draft.action(22,1).settings().analogDeployment(),"live analog editor choice");}
  System.out.println("Ramp native editor PASS SourceProfiles="+profiles+" requests="+valid+" strictRefusals="+refused+" admissions="+commits+" transitions="+transitions+" checks="+checks+" SourceBytes="+sourceBytes+" nativeBytes="+nativeBytes+" nativeBody50..306/noOwnership");
 }
}
