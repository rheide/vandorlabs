package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.vandorlabs.neoforge.credentials.*;
import java.nio.file.*;
import net.minecraft.nbt.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Scalar reader policy compared with the actual previous-platform tile implementation. */
@EventBusSubscriber(modid="vandorlabs")
public final class CredentialReaderChecks {
 private static int checks;
 private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Credential reader: "+why);}
 @SubscribeEvent public static void started(ServerStartedEvent event)throws Exception{
  if(!System.getProperty("vandorlabs.transmitterPhase","").equals("credential-reader"))return;
  var registries=event.getServer().registryAccess();JsonObject reference;
  try(var reader=Files.newBufferedReader(Paths.get(System.getProperty("vandorlabs.credentialReaderReference")))){reference=JsonParser.parseReader(reader).getAsJsonObject();}
  need(reference.get("golden").getAsString().equals("c7269ee705ec594c1c5d62410c290e571f445f827579009d9ae88b4e108a1c78"),"frozen actual Forge reader oracle");
  NativeTokenReader.Host local=new NativeTokenReader.Host(){public net.minecraft.world.level.block.entity.BlockEntity owner(){throw new AssertionError("Load queried world");}public boolean usable(net.minecraft.server.level.ServerPlayer p){throw new AssertionError("Load queried authorization");}public void changed(){throw new AssertionError("Load published");}public void schedule(int ticks){throw new AssertionError("Load scheduled");}};
  int cases=0;
  for(var entry:reference.getAsJsonArray("cases")){
   var c=entry.getAsJsonObject();var raw=TagParser.parseTag(c.get("input").getAsString());var before=raw.copy();var reader=new NativeTokenReader(local,false);reader.read(raw,registries);
   need(raw.equals(before),"read owns original data");need(reader.chip()==c.get("chip").getAsBoolean()&&reader.any()==c.get("any").getAsBoolean()&&reader.allowed()==c.get("allowed").getAsInt()&&reader.behavior()==c.get("behavior").getAsInt(),"policy "+cases);
   need(reader.logoDesign()==c.get("logo").getAsInt()&&reader.outputLevel(100)==c.get("output").getAsInt(),"logo/output "+cases);
   need(reader.occupied()!=c.get("empty").getAsBoolean()&&reader.returning()==c.get("returning").getAsBoolean(),"ownership/return state "+cases);
   need(reader.tokenCopy().getCount()==c.get("count").getAsInt(),"one physical item");var saved=reader.write(registries);need(saved.getInt("TokenLogo")==c.get("savedLogo").getAsInt()&&saved.getString("FutureReader").equals("opaque"),"saved scalar/opaque fields");var reload=new NativeTokenReader(local,false);reload.read(saved,registries);need(reload.write(registries).equals(saved),"current round trip");cases++;
  }
  var unknown=new CompoundTag();var payload=new CompoundTag();payload.putString("id","unknown:credential");payload.putInt("count",1);unknown.put("InsertedToken",payload);var reader=new NativeTokenReader(local,false);reader.read(unknown,registries);need(reader.opaqueOccupied()&&!reader.matches()&&reader.write(registries).getCompound("InsertedToken").equals(payload),"unknown ownership inert and retained");
  interactions(event);
  System.out.println("[vandorlabs][credential-reader] dedicated PASS actualForgeCases="+cases+" checks="+checks+"; local-only policy/load, owned opaque recovery; interaction acceptance separate");event.getServer().halt(false);
 }
 private static void interactions(ServerStartedEvent event){
  var world=event.getServer().overworld();var pos=new net.minecraft.core.BlockPos(2700,90,2700);world.getChunkAt(pos);world.setBlock(pos,net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(),3);var owner=world.getBlockEntity(pos);
  var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(world);player.getInventory().clearContent();player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);var hand=net.minecraft.world.InteractionHand.MAIN_HAND;
  final NativeTokenReader[] service=new NativeTokenReader[1];final int[] changes={0};
  NativeTokenReader.Host host=new NativeTokenReader.Host(){public net.minecraft.world.level.block.entity.BlockEntity owner(){return owner;}public boolean usable(net.minecraft.server.level.ServerPlayer p){return p==player;}public void changed(){changes[0]++;if(service[0]!=null)need(!service[0].interact(player,hand,false),"publication cannot reenter credential action");}public void schedule(int ticks){}public net.minecraft.server.level.ServerPlayer returnPlayer(net.minecraft.server.level.ServerLevel l,java.util.UUID id){return player.getUUID().equals(id)?player:null;}};
  for(int behavior=0;behavior<4;behavior++){
   var reader=new NativeTokenReader(host,false);service[0]=reader;reader.configureLocal(true,127,behavior);player.setItemInHand(hand,NativeCredentialItems.accessCard(2));var held=player.getItemInHand(hand);need(reader.interact(player,hand,false),"insert route");need(held.isEmpty()&&reader.occupied(),"one survival owner");long now=world.getGameTime();
   if(behavior<2){need(reader.outputLevel(now)==15,"stay output");if(behavior==1){reader.expire(now+20);need(reader.outputLevel(now+20)==0,"stay pulse expires");}player.setItemInHand(hand,net.minecraft.world.item.ItemStack.EMPTY);reader.interact(player,hand,false);need(!reader.occupied()&&player.getInventory().countItem(NativeCredentialItems.ACCESS_CARD.get())==1,"manual return");}
   else{need(reader.returning()&&reader.outputLevel(now)==0,"borrow before activation");reader.expire(now+7);need(reader.outputLevel(now+7)==0,"eight tick delay");reader.expire(now+8);need(reader.outputLevel(now+8)==15,"toggle/pulse activation");var saved=reader.write(event.getServer().registryAccess());reader=new NativeTokenReader(host,false);service[0]=reader;reader.read(saved,event.getServer().registryAccess());reader.expire(now+20);need(!reader.occupied()&&player.getInventory().countItem(NativeCredentialItems.ACCESS_CARD.get())==1,"reload returns exactly once");reader.expire(now+40);need(player.getInventory().countItem(NativeCredentialItems.ACCESS_CARD.get())==1,"repeat return cannot duplicate");}
   player.getInventory().clearContent();
  }
  var reader=new NativeTokenReader(host,false);service[0]=reader;reader.configureLocal(false,2,0);player.setItemInHand(hand,NativeCredentialItems.accessCard(3));reader.interact(player,hand,false);need(!reader.occupied()&&player.getMainHandItem().getCount()==1&&reader.motion(world.getGameTime()).rejectedFor()==12,"unauthorized stays player-owned and flashes");
  player.getInventory().clearContent();service[0]=null;world.removeBlock(pos,false);
 }

}
