package com.vandorlabs.neoforge;
import com.vandorlabs.neoforge.programmer.*;
import com.vandorlabs.neoforge.credentials.NativeCredentialItems;
import com.vandorlabs.neoforge.inventory.NativeInventoryItems;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
/** Copy must not clone or discard unsupported ownership; Program may consume and preserve it. */
@EventBusSubscriber(modid="vandorlabs")
public final class ProgrammerCopyOwnershipChecks {
 private static int checks;
 private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Programmer Copy ownership: "+why);}
 @SubscribeEvent public static void started(ServerStartedEvent event){if(!"programmer-copy-ownership".equals(System.getProperty("vandorlabs.transmitterPhase")))return;var server=event.getServer();try{var registries=server.registryAccess();int profiles=0;for(boolean chip:new boolean[]{false,true}){var plain=chip?NativeCredentialItems.dataChip(2):NativeCredentialItems.accessCard(2);need(NativeInventoryItems.ownershipBearing(plain)&&!NativeInventoryItems.containedOwnership(plain),"ordinary credential distinct from nested contents");for(int variant=0;variant<6;variant++)for(boolean sourceSide:new boolean[]{false,true}){var owned=plain.copy();switch(variant){case 0->owned.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(new ItemStack(Items.STICK))));case 1->owned.set(DataComponents.BUNDLE_CONTENTS,new BundleContents(List.of(new ItemStack(Items.STICK))));case 2->owned.set(DataComponents.CHARGED_PROJECTILES,ChargedProjectiles.of(new ItemStack(Items.ARROW)));case 3->{var inner=new ItemStack(Items.CHEST);inner.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(NativeCredentialItems.dataChip(4))));owned.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(inner)));}default->{var raw=new CompoundTag();raw.putString("id",chip?"vandorlabs:data_chip":"vandorlabs:access_card");raw.putByte("Count",(byte)1);raw.putShort("Damage",(short)2);if(variant==4){var caps=new CompoundTag();caps.putByteArray("foreign:owner",new byte[]{3,17,29});raw.put("ForgeCaps",caps);}else raw.putString("ForgeCaps","opaque malformed owner");owned=NativeInventoryItems.legacy(raw,registries);}}
  var source=sourceSide?owned:plain.copy();var target=sourceSide?plain.copy():owned;var beforeSource=NativeInventoryItems.write(source,registries);var beforeTarget=NativeInventoryItems.write(target,registries);boolean refused=false;try{NativeProgrammerRules.committedCopy(source,target);}catch(IllegalArgumentException expected){refused=true;}need(refused,"unsafe Copy accepted variant="+variant+" source="+sourceSide+" chip="+chip);need(!NativeProgrammerRules.copyProblem(source,target).isEmpty(),"Copy eligibility refuses unsupported owner");need(beforeSource.equals(NativeInventoryItems.write(source,registries))&&beforeTarget.equals(NativeInventoryItems.write(target,registries)),"both owners unchanged");var result=NativeProgrammerRules.programmedCopy(ItemStack.EMPTY,owned,NativeProgrammerDraft.empty());need(result.getCount()==1&&NativeCredentialItems.company(result)==2&&Objects.equals(result.get(DataComponents.CONTAINER),owned.get(DataComponents.CONTAINER))&&Objects.equals(result.get(DataComponents.BUNDLE_CONTENTS),owned.get(DataComponents.BUNDLE_CONTENTS))&&Objects.equals(result.get(DataComponents.CHARGED_PROJECTILES),owned.get(DataComponents.CHARGED_PROJECTILES))&&NativeCredentialItems.legacyCredentialData(result).equals(NativeCredentialItems.legacyCredentialData(owned)),"Program transform preserves single transferred owner");profiles++;}
  var empty=plain.copy();empty.set(DataComponents.CONTAINER,ItemContainerContents.EMPTY);empty.set(DataComponents.BUNDLE_CONTENTS,BundleContents.EMPTY);empty.set(DataComponents.CHARGED_PROJECTILES,ChargedProjectiles.EMPTY);need(!NativeInventoryItems.containedOwnership(empty)&&NativeProgrammerRules.copyProblem(empty,plain).isEmpty(),"empty nested components do not block ordinary data Copy");}
  System.out.println("[vandorlabs][programmer-copy-ownership] PASS profiles="+profiles+" checks="+checks);
 }catch(Throwable failure){failure.printStackTrace();System.out.println("[vandorlabs][programmer-copy-ownership] FAIL "+failure);}finally{server.halt(false);}}
}
