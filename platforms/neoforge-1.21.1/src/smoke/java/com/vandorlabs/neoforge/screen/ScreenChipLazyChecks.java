package com.vandorlabs.neoforge.screen;
import com.google.gson.*;
import com.vandorlabs.neoforge.credentials.NativeCredentialItems;
import com.vandorlabs.screen.ScreenFamily;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
/** Source's explicit chipDisplay getter is distinct from its save/projection preparation. */
final class ScreenChipLazyChecks {
 private static final BlockPos BASE=new BlockPos(5600,100,5600);private static final List<String> FAILURES=new ArrayList<>();private static int mismatches,checks;
 private static void same(int actual,int expected,String label){checks++;if(actual!=expected){mismatches++;if(FAILURES.size()<12)FAILURES.add(label+" expected="+expected+" actual="+actual);}}
 static void run(ServerLevel level,Map<ScreenFamily,NativeScreenBlock> blocks)throws Exception{
  JsonObject root;try(var stream=new java.util.zip.GZIPInputStream(new java.io.FileInputStream(System.getProperty("screenChipInteractionReference")));var reader=new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)){root=JsonParser.parseReader(reader).getAsJsonObject();}CompoundTag journal=null;for(var element:root.getAsJsonArray("nbt")){var tag=TagParser.parseTag(element.getAsString());if(tag.getCompound("ScreenChipReader").contains("InsertedToken")){journal=tag.getCompound("ScreenChipReader").getCompound("InsertedToken").copy();break;}}if(journal==null)throw new AssertionError("Source journal fixture");var token=NativeCredentialItems.readStack(journal,level.registryAccess());var actor=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);actor.setGameMode(GameType.SURVIVAL);actor.setPos(BASE.getX()+.5,BASE.getY(),BASE.getZ()-1);var counter=NativeScreenChipService.class.getDeclaredField("page");counter.setAccessible(true);int profiles=0;
  for(var element:root.getAsJsonArray("saved")){var row=element.getAsJsonObject();var family=ScreenFamily.fromId(row.get("id").getAsString());int raw=row.get("raw").getAsInt(),kind=row.get("kind").getAsInt();var input=new CompoundTag();input.putInt(family==ScreenFamily.HALF_CONSOLE?"SecondarySurfaceTexture":"PrimarySurfaceTexture",-4);input.putInt("ChipDisplayPage",raw);if(kind!=0){var embedded=new CompoundTag();embedded.put("InsertedToken",kind==2?journal.copy():NativeCredentialItems.writeStack(NativeCredentialItems.dataChip(1),level.registryAccess()));input.put("ScreenChipReader",embedded);}level.getChunkAt(BASE);level.setBlock(BASE,blocks.get(family).defaultBlockState(),18);var owner=(NativeScreenEntity)level.getBlockEntity(BASE);owner.loadWithComponents(input,level.registryAccess());if(owner.inert())throw new AssertionError("Lazy Source profile failed install "+family);String label=family+" raw="+raw+" kind="+kind;
   same(owner.chipPage(),row.get("loaded").getAsInt(),"loaded "+label);same(owner.saveWithoutMetadata(level.registryAccess()).getInt("ChipDisplayPage"),row.get("saveBeforeRead").getAsInt(),"saveBeforeGetter "+label);var initial=owner.chipDisplay(owner.chipView());same(initial.journal==null?0:initial.journal.pages.size(),row.get("pages").getAsInt(),"pageCount "+label);same(owner.chipPage(),row.get("afterRead").getAsInt(),"firstExplicitGetter "+label);
   counter.setInt(owner.chips(),999);var repeated=owner.chipDisplay(owner.chipView());same(repeated.journal==initial.journal?1:0,row.get("cachedIdentity").getAsBoolean()?1:0,"sameJournalIdentity "+label);same(owner.chipPage(),row.get("afterCachedRead").getAsInt(),"cachedGetter "+label);same(owner.saveWithoutMetadata(level.registryAccess()).getInt("ChipDisplayPage"),row.get("saveCached").getAsInt(),"cachedSave "+label);
   actor.getInventory().clearContent();actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);owner.chips().interact(actor,InteractionHand.MAIN_HAND);actor.setItemInHand(InteractionHand.MAIN_HAND,token.copy());if(!owner.chips().interact(actor,InteractionHand.MAIN_HAND))throw new AssertionError("Actual lazy reset insertion refused "+label);same(owner.chipPage(),row.get("afterInsert").getAsInt(),"insertReset "+label);owner.chipDisplay(owner.chipView());same(owner.chipPage(),0,"insertClearsPending "+label);counter.setInt(owner.chips(),2);actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);owner.chips().interact(actor,InteractionHand.MAIN_HAND);same(owner.chipPage(),row.get("afterRemove").getAsInt(),"removeReset "+label);level.setBlock(BASE,Blocks.AIR.defaultBlockState(),18);profiles++;
  }
  System.out.println("[vandorlabs][screen-chip-lazy] observations profiles="+profiles+" checks="+checks+" mismatches="+mismatches+" first="+FAILURES);if(profiles!=108||mismatches!=0)throw new AssertionError("Native lazy page differs from actual Source: "+FAILURES);System.out.println("[vandorlabs][screen-chip-lazy] PASS profiles="+profiles+" firstmaterialization/cachedidentity/save/reset");
 }
}
