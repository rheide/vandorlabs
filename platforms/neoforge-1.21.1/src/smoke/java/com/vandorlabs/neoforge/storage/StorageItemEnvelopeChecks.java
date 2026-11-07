package com.vandorlabs.neoforge.storage;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
/** Explicit native item envelopes cannot borrow missing ownership fields from a fresh placement draft. */
final class StorageItemEnvelopeChecks {
 private static int checks;private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError("Storage item envelope: "+why);}
 static void run(ServerLevel level,BlockPos pos){int version=SharedConstants.getCurrentVersion().getDataVersion().getVersion();for(int mode=0;mode<3;mode++){var raw=new CompoundTag();if(mode!=2)raw.putInt(NativeStorageInventory.SCHEMA,1);if(mode!=1)raw.putInt(NativeStorageInventory.DATA_VERSION,version);if(mode==1)raw.put("Items",new ListTag());var original=raw.copy();boolean refused=false;try{NativeStorageItemSettings.prepare(StorageHostChecks.TYPE,pos,StorageHostChecks.BLOCK.defaultBlockState(),raw,level.registryAccess());}catch(IllegalArgumentException expected){refused=true;}need(refused&&raw.equals(original),"incomplete supplied native inventory refused before draft merge mode="+mode);
  level.getChunkAt(pos);level.setBlock(pos,Blocks.AIR.defaultBlockState(),18);level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),18);var actor=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(java.util.UUID.fromString("c853bb03-a632-4ec2-bf37-10896f453271"),"StorageEnvelope"));actor.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);actor.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-2);var stack=new ItemStack(StorageHostChecks.BLOCK.asItem(),2);var root=new CompoundTag();root.put("BlockEntityTag",raw.copy());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(root));actor.setItemInHand(InteractionHand.MAIN_HAND,stack);var result=actor.gameMode.useItemOn(actor,level,stack,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atBottomCenterOf(pos),Direction.UP,pos.below(),false));need(!result.consumesAction()&&level.isEmptyBlock(pos)&&stack.getCount()==2&&stack.get(DataComponents.CUSTOM_DATA).copyTag().equals(root),"real item preflight does not mutate cell/consume/normalize incomplete owner mode="+mode);}
  var complete=new CompoundTag();NativeStorageInventory.write(complete,NativeStorageInventory.empty(),level.registryAccess());need(NativeStorageItemSettings.prepare(StorageHostChecks.TYPE,pos,StorageHostChecks.BLOCK.defaultBlockState(),complete,level.registryAccess()).snapshot().getList("Items",Tag.TAG_COMPOUND).isEmpty(),"complete explicit empty envelope accepted");for(int kind=0;kind<2;kind++){var raw=new CompoundTag();if(kind==1)raw.putInt("Items",17);need(NativeStorageItemSettings.prepare(StorageHostChecks.TYPE,pos,StorageHostChecks.BLOCK.defaultBlockState(),raw,level.registryAccess()).snapshot().getList("Items",Tag.TAG_COMPOUND).isEmpty(),"unmarked Source missing/wrong type remains factory-empty");}System.out.println("[vandorlabs][storage-item-envelope] PASS checks="+checks+" incomplete-native/refused-before-mutation/explicit-empty/legacy-factory");
 }
}
