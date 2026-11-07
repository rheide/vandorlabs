package com.vandorlabs.neoforge.copifier;
import com.google.gson.*;
import com.vandorlabs.neoforge.ProgrammableBlockEntity;
import java.nio.file.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@EventBusSubscriber(modid="vandorlabs")
public final class NativeCopifierConnectedChecks {
 private static int checks;private static final BlockPos BASE=new BlockPos(14800,110,14640);
 private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError("Copifier connected: "+why);}
 public static void main(String[] args)throws Exception{projection(Path.of(args[0]));}
 private static void projection(Path path)throws Exception{byte[] raw;try(var in=new GZIPInputStream(Files.newInputStream(path))){raw=in.readAllBytes();}String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw));need(hash.equals("3cc19480eda4799d01c96f54711527e914d566af17cff8fb3c4b87ccb4c3fe7a"),"Source reference");int cases=0;for(var element:JsonParser.parseString(new String(raw,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray()){var row=element.getAsJsonObject();var family=NativeCopifierHousingCapture.Family.of(row.get("block").getAsString());var input=TagParser.parseTag(row.get("input").getAsString());var before=input.copy();for(boolean ignore:new boolean[]{false,true}){var expected=TagParser.parseTag(row.get(ignore?"ignore":"strict").getAsString());var actual=NativeCopifierHousingCapture.connected(family,input,ignore);need(expected.equals(actual),"projection "+row.get("block")+" ignore="+ignore+" expected="+expected+" actual="+actual);}need(before.equals(input),"projection does not mutate");cases++;}System.out.println("[vandorlabs][copifier-connected] projection PASS SourceCases="+cases+" checks="+checks);}
 private static ProgrammableBlockEntity put(ServerLevel level,BlockPos pos){level.getChunkAt(pos);level.setBlock(pos,Blocks.AIR.defaultBlockState(),18);level.setBlock(pos,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("vandorlabs:programmable_block")).defaultBlockState(),18);return (ProgrammableBlockEntity)level.getBlockEntity(pos);}
 private static int texture(ServerLevel level,BlockPos pos){return ((ProgrammableBlockEntity)level.getBlockEntity(pos)).saveWithoutMetadata(level.registryAccess()).getInt("housingTexture");}
 private static void world(ServerLevel level){var actor=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(new java.util.UUID(0,1874),"ConnectedCopy"));actor.setGameMode(GameType.SURVIVAL);actor.setPos(BASE.getX()+.5,BASE.getY()+1,BASE.getZ()-2);var values=new CompoundTag();values.putInt("wall_texture",7);
  for(int i=0;i<12;i++)put(level,BASE.east(i));need(NativeCopifierHousingConnected.apply(level,BASE,actor,Direction.NORTH,values,false)==12,"matching beyond8block clicked reach");for(int i=0;i<12;i++){need(texture(level,BASE.east(i))==7,"all distant targets apply");level.removeBlock(BASE.east(i),false);}
  var first=put(level,BASE);put(level,BASE.offset(1,1,1));var third=put(level,BASE.offset(2,2,1));var different=put(level,BASE.offset(3,3,1));var raw=different.saveWithoutMetadata(level.registryAccess());raw.putBoolean("FaceTexturesEnabled",false);raw.putIntArray("FaceTextures",new int[]{1,2,3,4,5,6});different.loadWithComponents(raw,level.registryAccess());need(NativeCopifierHousingConnected.apply(level,BASE,actor,Direction.NORTH,values,false)==3,"corner/edge contacts; dormant face choices exclude fourth");need(texture(level,BASE.offset(3,3,1))==0,"dormant face choice not overwritten");for(var p:new BlockPos[]{BASE,BASE.offset(1,1,1),BASE.offset(2,2,1),BASE.offset(3,3,1)})level.removeBlock(p,false);
  for(String role:new String[]{"programmable_slab","programmable_stairs"})for(boolean ignore:new boolean[]{false,true}){
   var block=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("vandorlabs",role));var a=block.defaultBlockState();var b=a.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90).setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HALF,net.minecraft.world.level.block.state.properties.Half.TOP);var peer=BASE.offset(1,1,1);level.setBlock(BASE,a,18);level.setBlock(peer,b,18);
   need(NativeCopifierHousingConnected.apply(level,BASE,actor,Direction.NORTH,values,ignore)==(ignore?2:1),"strict/ignored orientation and half "+role);need(texture(level,peer)==(ignore?7:0),"orientation counterpart result");level.removeBlock(BASE,false);level.removeBlock(peer,false);
  }
  // Count physical matching cells before applying. One extra cell refuses the entire operation.
  for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++)put(level,BASE.offset(x,y,z));put(level,BASE.offset(16,0,0));need(NativeCopifierHousingConnected.apply(level,BASE,actor,Direction.NORTH,values,false)==-1,"4097cell region refused before changes");for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++)need(texture(level,BASE.offset(x,y,z))==0,"oversized region unchanged");level.removeBlock(BASE.offset(16,0,0),false);
  need(NativeCopifierHousingConnected.apply(level,BASE,actor,Direction.NORTH,values,false)==4096,"4096cell boundary accepted");need(texture(level,BASE.offset(15,15,15))==7,"far corner applied");for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++)level.removeBlock(BASE.offset(x,y,z),false);
  System.out.println("[vandorlabs][copifier-connected] world PASS checks="+checks+" corner/edge/distant/dormant/boundedWholeDiscovery");
 }
 @SubscribeEvent public static void started(ServerStartedEvent event){if(!"copifier-connected".equals(System.getProperty("vandorlabs.transmitterPhase")))return;try{projection(Path.of(System.getProperty("vandorlabs.copifierConnectedReference")));world(event.getServer().overworld());}catch(Throwable failure){failure.printStackTrace();System.out.println("[vandorlabs][copifier-connected] FAIL "+failure);}finally{event.getServer().halt(false);}}
}
