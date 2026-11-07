package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.credentials.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;

/** Server adapter ownership tests; actual Screen tray ray and client interaction remain separate acceptance. */
final class EmbeddedChipInventoryChecks {
 private static int checks;
 private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError("Embedded chip inventory: "+why);}
 static void run(ServerLevel level){
  var at=new BlockPos(64,90,64);level.getChunkAt(at);level.setBlock(at,VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState(),3);var owner=level.getBlockEntity(at);need(owner!=null,"loaded real tile host");var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);player.setPos(64.5,90,66);boolean[] allowed={true};int[] changed={0};NativeEmbeddedChipReader[] current={null};
  var host=new NativeTokenReader.Host(){public net.minecraft.world.level.block.entity.BlockEntity owner(){return owner;}public boolean usable(net.minecraft.server.level.ServerPlayer actor){return allowed[0]&&actor==player;}public void changed(){changed[0]++;need((NativeChipJournal.textData(current[0].tokenCopy())!=null)==(current[0].journal()!=null),"journal prepared before publication");}public void schedule(int ticks){throw new AssertionError("Passive tray scheduled return");}};
  int cases=0;
  for(boolean creative:new boolean[]{false,true})for(var hand:InteractionHand.values())for(int company=0;company<7;company++)for(int count:new int[]{1,2,64}){
   player.getInventory().clearContent();player.setGameMode(creative?GameType.CREATIVE:GameType.SURVIVAL);var chip=NativeCredentialItems.dataChip(company);chip.setCount(count);var data=new CompoundTag();data.putString("ChipType","journal");data.putString("JournalTitle","COMPANY "+company);var pages=new ListTag();pages.add(StringTag.valueOf("Complete private journal"));data.put("JournalPages",pages);data.putString("PrivateField","keep");chip.set(DataComponents.CUSTOM_DATA,CustomData.of(data));var expected=chip.copyWithCount(1);player.setItemInHand(hand,chip);
   var reader=new NativeEmbeddedChipReader(host);current[0]=reader;reader.read(new CompoundTag(),level.registryAccess());int before=changed[0];need(reader.interact(player,hand),"accepted chip");need(reader.occupied()&&reader.tokenCopy().getCount()==1&&ItemStack.isSameItemSameComponents(reader.tokenCopy(),expected),"exactly one complete owner");need(player.getItemInHand(hand).getCount()==(creative?count:count-1),"source consumption");need(changed[0]==before+1&&reader.journal()!=null,"one publication/prepared text");var saved=reader.write(level.registryAccess());need(!reader.interact(player,hand==InteractionHand.MAIN_HAND?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND)||!reader.occupied(),"empty alternate hand removes");
   // Reinstall the saved owner, then check refusal leaves the complete item and hand unchanged.
   reader.read(saved,level.registryAccess());player.setItemInHand(hand,expected.copy());before=changed[0];need(!reader.interact(player,hand)&&reader.write(level.registryAccess()).equals(saved)&&changed[0]==before,"occupied tray refuses another chip");player.setItemInHand(hand,ItemStack.EMPTY);allowed[0]=false;need(!reader.interact(player,hand)&&reader.occupied(),"authorization refuses removal");allowed[0]=true;player.getInventory().clearContent();need(reader.interact(player,hand)&&!reader.occupied()&&reader.journal()==null,"empty hand removes and clears prepared text");int found=0;for(var stack:player.getInventory().items)if(ItemStack.isSameItemSameComponents(stack,expected))found+=stack.getCount();need(found==1,"one removed usable owner");
   var unsupported=new ItemStack(Items.STONE);player.setItemInHand(hand,unsupported);before=changed[0];need(!reader.interact(player,hand)&&unsupported.getCount()==1&&!reader.occupied()&&changed[0]==before,"non-chip refused");player.setItemInHand(hand,ItemStack.EMPTY);player.setItemInHand(hand==InteractionHand.MAIN_HAND?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND,expected.copy());before=changed[0];need(reader.interact(player,hand)&&!reader.occupied()&&changed[0]==before+1,"empty tray removal publishes reset while ignoring other hand and inventory");cases++;
  }
  player.getInventory().clearContent();player.setGameMode(GameType.SURVIVAL);var drop=NativeCredentialItems.dataChip(2);player.setItemInHand(InteractionHand.OFF_HAND,drop.copy());var full=new NativeEmbeddedChipReader(host);current[0]=full;full.read(new CompoundTag(),level.registryAccess());need(full.interact(player,InteractionHand.OFF_HAND),"prepare full-inventory owner");for(int slot=0;slot<player.getInventory().items.size();slot++)player.getInventory().items.set(slot,new ItemStack(Items.STONE,64));need(full.interact(player,InteractionHand.OFF_HAND)&&!full.occupied(),"full inventory removes exactly once");int dropped=0;for(var entity:level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,player.getBoundingBox().inflate(8)))if(ItemStack.isSameItemSameComponents(entity.getItem(),drop)){dropped+=entity.getItem().getCount();entity.discard();}need(dropped==1,"full inventory has exactly one dropped usable owner");
  boolean worker=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{full.interact(player,InteractionHand.OFF_HAND);return false;}catch(IllegalStateException expected){return true;}}).join();need(worker&&!full.occupied(),"worker refuses before touching player inventory");
  player.getInventory().clearContent();level.removeBlock(at,false);var stale=current[0];need(!stale.interact(player,InteractionHand.MAIN_HAND),"stale tile refuses");System.out.println("[vandorlabs][embedded-chip-inventory] PASS server adapter cases="+cases+" checks="+checks+" Survival/Creative/both hands/full components/refusals/publication ordering; player ray acceptance pending");
 }
}
