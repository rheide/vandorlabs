package com.vandorlabs.neoforge.propulsion;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Public item/recipe/tool paths and independent-process saved owner verification. */
@EventBusSubscriber(modid="vandorlabs")
public final class PropulsionCanonicalChecks {
 private static final String[] IDS={"rocket_thruster","ion_drive","plasma_vent","impulse_engine","antigravity_plate","repulsor_array","vertical_hover_thruster"};
 private static final BlockPos BASE=new BlockPos(6080,90,6080);private static int checks;
 private static void need(boolean yes,String text){checks++;if(!yes)throw new AssertionError("Canonical engine "+text);}
 private static ResourceLocation id(String name){return ResourceLocation.fromNamespaceAndPath("vandorlabs",name);}
 private static NativePropulsionBlock block(String name){return (NativePropulsionBlock)BuiltInRegistries.BLOCK.get(id(name));}
 private static Item item(String name){return BuiltInRegistries.ITEM.get(id(name));}
 private static ItemStack configured(String name){var tag=new CompoundTag();tag.putInt("PropulsionSideTexture",64);tag.putInt("ParticleLevel",3);tag.putBoolean("ManualOn",false);tag.putBoolean("LightInitialized",true);tag.putString("FutureEngineChoice","opaque:choice");var root=new CompoundTag();root.put("BlockEntityTag",tag);var stack=new ItemStack(item(name),2);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(root));return stack;}
 private static void owner(ServerLevel world,BlockPos pos,String name){var tile=(NativePropulsionEntity)world.getBlockEntity(pos);need(tile!=null&&!tile.inert(),"live owner "+name);need(tile.getType()==BuiltInRegistries.BLOCK_ENTITY_TYPE.get(id("redstone_light")),"released entity identity");need(tile.settings().data().sideTexture==64&&tile.settings().data().particleLevel==3&&!tile.visual().powered(),"saved configured choices "+name);need(tile.saveWithoutMetadata(world.registryAccess()).getString("FutureEngineChoice").equals("opaque:choice"),"unknown choice retained "+name);}
 private static void place(ServerLevel world,ServerPlayer actor,BlockPos pos,String name){world.setBlock(pos,Blocks.AIR.defaultBlockState(),2);world.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),2);actor.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+2.5);actor.getInventory().selected=0;var stack=configured(name);actor.setItemInHand(InteractionHand.MAIN_HAND,stack);var result=actor.gameMode.useItemOn(actor,world,stack,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atLowerCornerOf(pos.below()).add(.25,1,.75),Direction.UP,pos.below(),false));need(result.consumesAction()&&stack.getCount()==1&&world.getBlockState(pos).is(block(name)),"actual Survival configured item "+name);NativePropulsionService.flush(world);owner(world,pos,name);var pick=block(name).getCloneItemStack(world.getBlockState(pos),new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false),world,pos,actor);need(pick.is(item(name))&&NativePropulsionItem.configuration(pick).getInt("PropulsionSideTexture")==64,"configured pick "+name);}
 private static void recipes(ServerLevel world){Item[] centers={Items.FURNACE,Items.END_ROD,Items.BLAZE_ROD,Items.PISTON,Items.ENDER_PEARL,Items.IRON_BLOCK,Items.FURNACE};Item[] sides={Items.BLAZE_POWDER,Items.LAPIS_LAZULI,Items.GLOWSTONE_DUST,Items.QUARTZ,Items.FEATHER,Items.PISTON,Items.FEATHER};for(int i=0;i<IDS.length;i++){var alloy=item("industrial_alloy_ingot");var cells=List.of(new ItemStack(alloy),new ItemStack(Items.REDSTONE),new ItemStack(alloy),new ItemStack(sides[i]),new ItemStack(centers[i]),new ItemStack(sides[i]),new ItemStack(alloy),new ItemStack(Items.REDSTONE),new ItemStack(alloy));var input=CraftingInput.of(3,3,cells);var recipe=(CraftingRecipe)world.getRecipeManager().byKey(id(IDS[i])).orElseThrow().value();var result=recipe.assemble(input,world.registryAccess());need(recipe.matches(input,world)&&result.is(item(IDS[i]))&&result.getCount()==1,"Source recipe "+IDS[i]);}}
 @SubscribeEvent public static void started(ServerStartedEvent event){String phase=System.getProperty("vandorlabs.transmitterPhase","");if(!phase.startsWith("propulsion-canonical-"))return;var server=event.getServer();System.out.println("[vandorlabs][propulsion-canonical] actual server PID="+ProcessHandle.current().pid());try{var world=server.overworld();recipes(world);var actor=new net.neoforged.neoforge.common.util.FakePlayer(world,new com.mojang.authlib.GameProfile(UUID.fromString("2590df27-6797-436a-ae7f-83c3fb778162"),"CanonicalEngine"));actor.setGameMode(GameType.SURVIVAL);for(int i=0;i<IDS.length;i++){String name=IDS[i];var pos=BASE.east(i*16);world.getChunkAt(pos);if(phase.endsWith("write")){place(world,actor,pos,name);var state=world.getBlockState(pos);need(state.getDestroySpeed(world,pos)==5&&block(name).getExplosionResistance()==6,"Source physical properties");need(state.requiresCorrectToolForDrops()&&!new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(state)&&new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(state),"Source stone pickaxe tier");actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STONE_PICKAXE));need(actor.gameMode.destroyBlock(pos)&&world.isEmptyBlock(pos),"actual Survival harvest");int count=0;for(var drop:world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(pos).inflate(2)))if(drop.getItem().is(item(name))){count+=drop.getItem().getCount();need(NativePropulsionItem.configuration(drop.getItem())==null,"Source ordinary drop is plain");drop.discard();}need(count==1,"exactly one usable harvested owner");place(world,actor,pos,name);}else{NativePropulsionService.refresh((NativePropulsionEntity)world.getBlockEntity(pos));owner(world,pos,name);}}server.saveEverything(true,true,true);System.out.println("[vandorlabs][propulsion-canonical] "+(phase.endsWith("write")?"write":"read")+" PASS checks="+checks);}catch(Throwable failed){System.err.println("[vandorlabs][propulsion-canonical] FAIL "+failed);failed.printStackTrace();}finally{server.halt(false);}}
}
