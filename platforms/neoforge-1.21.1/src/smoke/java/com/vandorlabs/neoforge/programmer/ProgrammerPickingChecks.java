package com.vandorlabs.neoforge.programmer;
import com.google.gson.*;
import com.vandorlabs.programmer.ProgrammerPicking;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import net.minecraft.core.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
/** Native world-free authored rays replayed from the actual released source stream. */
@EventBusSubscriber(modid="vandorlabs")
public final class ProgrammerPickingChecks {
 private static int checks;
 private static void need(boolean value,String reason){checks++;if(!value)throw new AssertionError("Programmer picking: "+reason);}
 private static Vec3 point(JsonArray p){return new Vec3(p.get(0).getAsDouble(),p.get(1).getAsDouble(),p.get(2).getAsDouble());}
 @SubscribeEvent public static void started(ServerStartedEvent event){if(!System.getProperty("vandorlabs.transmitterPhase","").equals("programmer-picking"))return;try{Path geometry=Path.of(System.getenv("VANDOR_PROGRAMMER_GEOMETRY"));var mesh=ProgrammerPicking.read(Files.newInputStream(geometry.resolve("static.obj")),Files.newInputStream(geometry.resolve("screen.obj")));int cases=0,hits=0;var hash=MessageDigest.getInstance("SHA-256");try(var reader=Files.newBufferedReader(Path.of(System.getenv("VANDOR_PROGRAMMER_RAYS_REFERENCE")))){String line;while((line=reader.readLine())!=null){var row=JsonParser.parseString(line).getAsJsonObject();if(row.has("golden")){need(row.get("golden").getAsString().equals("0aaef112119fe6e8f5f72e5462843a3d4af766426f35055a143dc93fd4a8ce54"),"frozen source stream");need(java.util.HexFormat.of().formatHex(hash.digest()).equals(row.get("golden").getAsString()),"actual stream hash");continue;}hash.update((line+"\n").getBytes(StandardCharsets.UTF_8));var root=new BlockPos(7,80,-11);var facing=Direction.valueOf(row.get("facing").getAsString());int part=row.get("part").getAsInt();var hit=NativeProgrammerPicking.trace(mesh,root,facing,part,point(row.getAsJsonArray("from")),point(row.getAsJsonArray("to")));need((hit!=null)==row.has("hit"),"hit existence case="+cases);if(hit!=null){var expected=point(row.getAsJsonArray("hit"));need(hit.point().distanceToSqr(expected)<1e-24,"hit position case="+cases);need(hit.side().name().equals(row.get("side").getAsString())&&hit.slot()==row.get("slot").getAsInt()&&hit.group().equals(row.get("group").getAsString()),"side/socket/group case="+cases);var cell=NativeProgrammerBlock.cell(root,facing,part);need(hit.cell().equals(cell)&&hit.blockHit().getBlockPos().equals(cell),"reserved-cell native hit");hits++;}cases++;}}need(cases==27744&&hits==7280,"entire released corpus");System.out.println("[vandorlabs][programmer-picking] PASS SourceCases="+cases+" hits="+hits+" triangles="+mesh.triangles()+" checks="+checks);}catch(Throwable failure){failure.printStackTrace();System.out.println("[vandorlabs][programmer-picking] FAIL "+failure);}finally{event.getServer().halt(false);}}
}
