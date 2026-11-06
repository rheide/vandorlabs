package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.vandorlabs.neoforge.credentials.*;
import java.nio.file.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Actual registered two-ID credential codec, retaining full original1.12 stack payloads. */
@EventBusSubscriber(modid="vandorlabs")
public final class CredentialStackChecks {
 private static int checks;
 private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Credential stack: "+why);}
 @SubscribeEvent public static void started(ServerStartedEvent event)throws Exception{
  if(!System.getProperty("vandorlabs.transmitterPhase","").equals("credential-stacks"))return;var registries=event.getServer().registryAccess();JsonObject reference;try(var reader=Files.newBufferedReader(Paths.get(System.getProperty("vandorlabs.credentialStackReference")))){reference=JsonParser.parseReader(reader).getAsJsonObject();}need(reference.get("golden").getAsString().equals("16d1daf2c8cd8cf7a610f82783dd4cfa3cf49b127a3c537b24ff65542bb7ecde"),"actual Forge frozen stack oracle");int cases=0;
  for(var entry:reference.getAsJsonArray("cases")){
   var c=entry.getAsJsonObject();var raw=TagParser.parseTag(c.get("input").getAsString());var before=raw.copy();var stack=NativeCredentialItems.readStack(raw,registries);need(raw.equals(before),"read never mutates original snapshot");need(stack.isEmpty()==c.get("empty").getAsBoolean(),"actual legacy Count semantics");need(NativeCredentialItems.accepts(stack,false)==c.get("card").getAsBoolean()&&NativeCredentialItems.accepts(stack,true)==c.get("chip").getAsBoolean(),"actual type/metadata validity");need((NativeCredentialItems.authorized(stack,false,127)||NativeCredentialItems.authorized(stack,true,127))==c.get("anyAuthorized").getAsBoolean(),"Any never authorizes invalid positive metadata");
   if(!stack.isEmpty()){
    need(NativeCredentialItems.company(stack)==c.get("company").getAsInt()&&stack.getHoverName().getString().equals(c.get("name").getAsString()),"actual metadata/name projection");need(NativeCredentialItems.legacyCredentialData(stack).equals(raw),"complete original root/tag/ForgeCaps retained");stack.setCount(1);var saved=NativeCredentialItems.writeStack(stack,registries);var copy=NativeCredentialItems.readStack(saved,registries);need(copy.getCount()==1&&NativeCredentialItems.company(copy)==NativeCredentialItems.company(stack)&&NativeCredentialItems.legacyCredentialData(copy).equals(raw),"modern components preserve original reader-owned one-item snapshot");saved.getCompound("components").getCompound("minecraft:custom_data").getCompound(NativeCredentialItems.LEGACY_KEY).putString("Mutated","external");need(!NativeCredentialItems.legacyCredentialData(copy).contains("Mutated")&&!NativeCredentialItems.legacyCredentialData(stack).contains("Mutated"),"modern read/write own opaque data");var exported=NativeCredentialItems.legacyCredentialData(copy);exported.putString("Mutated","external");need(!NativeCredentialItems.legacyCredentialData(copy).contains("Mutated"),"legacy payload getter is defensive");
   }cases++;
  }
  need(cases==120,"all actual saved count/type/metadata cases");var variants=NativeCredentialItems.creativeVariants();need(variants.size()==14,"two canonical IDs with seven variants each");for(int i=0;i<14;i++)need(NativeCredentialItems.company(variants.get(i))==i%7&&NativeCredentialItems.accepts(variants.get(i),i>=7),"creative order/company identity");var one=NativeCredentialItems.accessCard(1);var two=NativeCredentialItems.accessCard(1);one.setCount(12);need(two.getCount()==1,"factories return owned stacks");
  for(int invalid:new int[]{-1,7,8,Integer.MAX_VALUE}){var stack=NativeCredentialItems.accessCard(0);stack.set(DataComponents.CUSTOM_MODEL_DATA,new CustomModelData(invalid));need(!NativeCredentialItems.authorized(stack,false,127)&&NativeCredentialItems.company(stack)==invalid,"invalid native component stays inert without visual-fallback authorization");var saved=NativeCredentialItems.writeStack(stack,registries);need(NativeCredentialItems.company(NativeCredentialItems.readStack(saved,registries))==invalid,"invalid native identity retained on save/reload");}
  var mixed=new CompoundTag();mixed.putString("id","vandorlabs:access_card");mixed.putByte("Count",(byte)1);mixed.putShort("Damage",(short)7);mixed.putInt("count",1);need(!NativeCredentialItems.authorized(NativeCredentialItems.readStack(mixed,registries),false,127),"legacy identity wins over an unknown lower-case count field");
  var owned=NativeCredentialItems.accessCard(2);var data=new CompoundTag();data.putString("opaque","original");owned.set(DataComponents.CUSTOM_DATA,CustomData.of(data));owned.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("original"));var detached=NativeCredentialItems.ownedCopy(owned);detached.get(DataComponents.CUSTOM_DATA).getUnsafe().putString("opaque","changed");((net.minecraft.network.chat.MutableComponent)detached.get(DataComponents.CUSTOM_NAME)).append(" changed");need(owned.get(DataComponents.CUSTOM_DATA).copyTag().getString("opaque").equals("original")&&owned.getHoverName().getString().equals("original"),"owned copies detach opaque components and mutable names");
  for(int invalid:new int[]{-1,7})try{NativeCredentialItems.accessCard(invalid);throw new AssertionError("factory accepted invalid company");}catch(IllegalArgumentException expected){}
  var unknown=new CompoundTag();unknown.putString("id","missing:credential");unknown.putByte("Count",(byte)1);unknown.putString("Future","opaque");var snapshot=unknown.copy();need(NativeCredentialItems.readStack(unknown,registries).isEmpty()&&unknown.equals(snapshot),"unsupported snapshot left untouched for owning reader retention");
  System.out.println("[vandorlabs][credential-stacks] dedicated PASS actualForgeCases="+cases+" checks="+checks+" twoIDs/14variants,components,opaque root/tag/caps,invalid credential vs icon fallback; no general migration claim");event.getServer().halt(false);
 }
}
