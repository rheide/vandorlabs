package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.segmented.SegmentedDoorTile;
import com.vandorlabs.network.MessageSegmentedDoorUse;
import io.netty.buffer.*;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
/** Actual released 64-byte interaction body; cosmetic pose identity is distinct from editor operations. */
public final class SegmentedUseLegacyChecks {
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy();JsonArray rows=new JsonArray();int count=0;
  for(int coordinate:new int[]{0,29999872,-29999872})for(long sequence:new long[]{0,1,42})for(double pose:new double[]{0,.5,1})for(int part=0;part<4;part++){
   SegmentedDoorTile tile=new SegmentedDoorTile();tile.setWorld(new NonRenderingChecks.MemoryWorld(false));tile.setPos(new BlockPos(coordinate+part,80-part,-coordinate));tile.identity=new java.util.UUID(0x123456789abcdefL,count);tile.sequence=sequence;tile.from=pose;tile.duration=960;Vec3d point=new Vec3d(coordinate+part-.25,81.25-part,-coordinate+.5);MessageSegmentedDoorUse message=new MessageSegmentedDoorUse(tile,point);ByteBuf wire=Unpooled.buffer();try{message.toBytes(wire);if(wire.readableBytes()!=64)throw new AssertionError("Released use body length");MessageSegmentedDoorUse restored=new MessageSegmentedDoorUse();restored.fromBytes(wire.duplicate());ByteBuf again=Unpooled.buffer();try{restored.toBytes(again);if(!ByteBufUtil.hexDump(wire).equals(ByteBufUtil.hexDump(again)))throw new AssertionError("Released use roundtrip");}finally{again.release();}JsonObject row=new JsonObject();row.addProperty("hex",ByteBufUtil.hexDump(wire));rows.add(row);}finally{wire.release();}count++;
  }
  Path file=Paths.get(args[0]);Files.createDirectories(file.toAbsolutePath().getParent());Files.write(file,rows.toString().getBytes(StandardCharsets.UTF_8));SegmentedReferenceGolden.check(file.toFile(),"f54895c08d1cea79a93ec3fe95436e3c89db601766f0232664cb4ac66aa7e583");System.out.println("Actual Segmented use wire PASS cases="+count+" bytes=64 identity/sequence/pose and world point");
 }
}
