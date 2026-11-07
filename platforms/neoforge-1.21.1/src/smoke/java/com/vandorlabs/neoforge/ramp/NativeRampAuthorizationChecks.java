package com.vandorlabs.neoforge.ramp;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Blocks;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
/** Real location receipt + chunk proof write/read; GUI permission actors are not journal completion proof. */
@net.neoforged.fml.common.EventBusSubscriber(modid="vandorlabs")
public final class NativeRampAuthorizationChecks {
 private static final BlockPos SAVE=new BlockPos(22864,100,22864),CLONE=SAVE.east(2),MISMATCH=SAVE.east(4);private static final UUID ACTOR=new UUID(0,5716);private static int checks;
 private static NativeRampEntity at(ServerLevel level,BlockPos pos){return (NativeRampEntity)level.getBlockEntity(pos);}
 static void write(ServerLevel level,NativeRampBlock block){
  level.getChunkAt(SAVE);level.setBlock(SAVE,block.defaultBlockState(),18);level.setBlock(CLONE,block.defaultBlockState(),18);level.setBlock(MISMATCH,block.defaultBlockState(),18);var owner=at(level,SAVE);var actor=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(ACTOR,"RampAuthority"));actor.setPos(SAVE.getX()+.5,SAVE.getY(),SAVE.getZ()+.5);
  need(NativeRampAuthorization.actorId(owner)==null,"fresh owner has no receipt");need(!NativeRampAuthorization.grantInstalled(owner,actor),"portable caller cannot grant outside installed placement scope");need(NativeRampAuthorization.authorize(owner,actor)&&ACTOR.equals(NativeRampAuthorization.actorId(owner)),"installed actor grants location authority");
  var portable=owner.saveWithoutMetadata(level.registryAccess());need(!portable.toString().contains(ACTOR.toString())&&!owner.getUpdateTag(level.registryAccess()).toString().contains("Nonce"),"receipt excluded from portable/public tile data");at(level,CLONE).loadWithComponents(portable,level.registryAccess());NativeRedstoneChannels.flush(level);need(NativeRampAuthorization.actorId(at(level,CLONE))==null,"copied tile cannot mint location authority");
  var fake=NativeRampAuthorization.permissionActor(owner);need(fake.getUUID().equals(ACTOR)&&fake.getGameProfile().getName().equals("Automation"),"offline permission actor preserves released identity");
  owner.loadWithComponents(portable,level.registryAccess());NativeRedstoneChannels.flush(level);need(NativeRampAuthorization.actorId(owner)==null,"in-place external read revokes old proof");need(NativeRampAuthorization.authorize(owner,actor),"reauthorize after read");owner.onChunkUnloaded();need(NativeRampAuthorization.actorId(owner)==null,"unloaded owner cannot exercise receipt");owner.onLoad();NativeRedstoneChannels.flush(level);need(ACTOR.equals(NativeRampAuthorization.actorId(owner)),"deferred dormant identity resumes matching proof");
  need(!java.util.concurrent.CompletableFuture.supplyAsync(()->NativeRampAuthorization.authorize(owner,actor)).join(),"worker cannot grant");actor.setPos(SAVE.getX()+40,SAVE.getY(),SAVE.getZ());need(!NativeRampAuthorization.authorize(owner,actor),"remote actor cannot grant new authority");need(ACTOR.equals(NativeRampAuthorization.actorId(owner)),"failed grant leaves existing receipt");
  // A real replacement must not inherit even at the same location and with copied tile choices.
  level.setBlock(CLONE,Blocks.AIR.defaultBlockState(),18);level.setBlock(CLONE,block.defaultBlockState(),18);need(NativeRampAuthorization.actorId(at(level,CLONE))==null,"replacement stays unauthorized");
  need(NativeRampAuthorization.authorize(at(level,MISMATCH),actorAt(actor,MISMATCH)),"grant separately corrupted proof owner");level.setChunkForced(SAVE.getX()>>4,SAVE.getZ()>>4,true);System.out.println("[vandorlabs][ramp-authorization] write PASS checks="+checks);
 }
 static void read(ServerLevel level){level.getChunkAt(SAVE);var owner=at(level,SAVE);need(owner!=null,"saved installed authority owner");need(NativeRampAuthorization.actorId(owner)==null,"fresh decoded owner awaits deferred proof");owner.onLoad();NativeRedstoneChannels.flush(level);need(ACTOR.equals(NativeRampAuthorization.actorId(owner)),"fresh process matches separate saved-data and chunk nonce");need(NativeRampAuthorization.permissionActor(owner).getUUID().equals(ACTOR),"offline permission actor after restart");need(NativeRampAuthorization.actorId(at(level,CLONE))==null,"saved unauthorized clone remains unbound");var mismatched=at(level,MISMATCH);mismatched.onLoad();NativeRedstoneChannels.flush(level);need(NativeRampAuthorization.actorId(mismatched)==null,"fresh process refuses mismatched saved-data/chunk nonce");System.out.println("[vandorlabs][ramp-authorization] read PASS checks="+checks);}
 private static ServerPlayer actorAt(ServerPlayer actor,BlockPos pos){actor.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);return actor;}
 @net.neoforged.bus.api.SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST) public static void corruptProof(net.neoforged.neoforge.event.level.ChunkDataEvent.Save event){if(!System.getProperty("vandorlabs.transmitterPhase","").startsWith("ramp-host-")||event.getChunk().getPos().x!=MISMATCH.getX()>>4||event.getChunk().getPos().z!=MISMATCH.getZ()>>4)return;for(var entry:event.getData().getList("NativeRampActionProofs",Tag.TAG_COMPOUND)){var row=(CompoundTag)entry;if(row.getLong("Position")==MISMATCH.asLong())row.putUUID("Nonce",new UUID(0,1));}}
 private static void need(boolean ok,String text){checks++;if(!ok)throw new AssertionError("Ramp authorization: "+text);}
}
