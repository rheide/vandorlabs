package com.vandorlabs.neoforge.light;

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

/** Actual public recipes, configured Survival interactions and process-reloaded canonical owners. */
@EventBusSubscriber(modid="vandorlabs")
public final class LightCanonicalChecks {
    private static final String[] NAMES={"programmable_light","programmable_light_frame","programmable_light_slab"};
    private static final BlockPos BASE=new BlockPos(7744,110,7744);
    private static int checks;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Canonical Light: "+why);}
    private static ResourceLocation id(int kind){return ResourceLocation.fromNamespaceAndPath("vandorlabs",NAMES[kind]);}
    private static NativeLightBlock block(int kind){return (NativeLightBlock)BuiltInRegistries.BLOCK.get(id(kind));}
    private static NativeLightItem item(int kind){return (NativeLightItem)BuiltInRegistries.ITEM.get(id(kind));}
    private static ItemStack configured(int kind){var tag=new CompoundTag();tag.putInt("LightLevel",6);tag.putBoolean("LightOn",false);tag.putInt("housingTexture",14);tag.putInt("HousingTextureVersion",1);tag.putString("FutureLightChoice","opaque:choice");var root=new CompoundTag();root.put("BlockEntityTag",tag);var stack=new ItemStack(item(kind),2);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(root));return stack;}
    private static NativeLightEntity owner(ServerLevel level,BlockPos pos){return level.getBlockEntity(pos) instanceof NativeLightEntity light?light:null;}
    private static void assertOwner(ServerLevel level,BlockPos pos,int kind){var tile=owner(level,pos);need(tile!=null&&!tile.inert()&&tile.kind()==NativeLightBlock.Kind.values()[kind],"correct canonical kind "+kind);need(tile.getType()==BuiltInRegistries.BLOCK_ENTITY_TYPE.get(id(0)),"released shared entity ID "+kind);need(!tile.visual().on()&&tile.settings().light().configuredLevel==6&&tile.visual().housing().mainTexture==14,"configured manual state "+kind);need(tile.itemSettings().getString("FutureLightChoice").equals("opaque:choice"),"unknown choices survive "+kind);}
    private static void place(ServerLevel level,ServerPlayer actor,BlockPos pos,int kind){level.getChunkAt(pos);for(var direction:Direction.values())level.setBlock(pos.relative(direction),Blocks.AIR.defaultBlockState(),2);level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),2);actor.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+2.5);actor.setYRot(180);actor.setXRot(0);actor.setYHeadRot(180);actor.setYBodyRot(180);actor.getInventory().selected=0;var stack=configured(kind);actor.setItemInHand(InteractionHand.MAIN_HAND,stack);var result=actor.gameMode.useItemOn(actor,level,stack,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atLowerCornerOf(pos.below()).add(.25,1,.75),Direction.UP,pos.below(),false));need(result.consumesAction()&&stack.getCount()==1&&level.getBlockState(pos).is(block(kind)),"normal Survival placement "+kind);NativeLightService.flush(level);assertOwner(level,pos,kind);var pick=block(kind).getCloneItemStack(level.getBlockState(pos),new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false),level,pos,actor);need(pick.is(item(kind))&&NativeLightItem.configuration(pick).getString("FutureLightChoice").equals("opaque:choice"),"configured pick "+kind);}
    private static void recipes(ServerLevel level){
        var matter=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("vandorlabs:programmable_block")));
        var inputs=List.of(CraftingInput.of(2,1,List.of(matter,new ItemStack(Items.GLOWSTONE_DUST))),CraftingInput.of(2,1,List.of(new ItemStack(item(0)),new ItemStack(Items.IRON_BARS))),CraftingInput.of(3,1,List.of(new ItemStack(item(0)),new ItemStack(item(0)),new ItemStack(item(0)))));
        for(int kind=0;kind<3;kind++){var recipe=(CraftingRecipe)level.getRecipeManager().byKey(id(kind)).orElseThrow().value();var input=inputs.get(kind);var result=recipe.assemble(input,level.registryAccess());need(recipe.matches(input,level)&&result.is(item(kind))&&result.getCount()==(kind==2?6:1),"actual source recipe "+kind);}
    }
    @SubscribeEvent public static void started(ServerStartedEvent event){String phase=System.getProperty("vandorlabs.transmitterPhase","");if(!phase.startsWith("light-canonical-"))return;var server=event.getServer();try{var level=server.overworld();recipes(level);var actor=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.fromString("8acc5c13-8da1-4e27-a24a-73fcd0a567cb"),"CanonicalLight"));actor.setGameMode(GameType.SURVIVAL);
        for(int kind=0;kind<3;kind++){var pos=BASE.east(kind*16);level.getChunkAt(pos);if(phase.endsWith("write")){place(level,actor,pos,kind);var state=level.getBlockState(pos);need(state.getDestroySpeed(level,pos)==5&&block(kind).getExplosionResistance()==6,"released physical hardness and effective explosion resistance "+kind);need(!new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(state)&&new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(state)&&state.requiresCorrectToolForDrops(),"Source stone pickaxe tier "+kind);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STONE_PICKAXE));need(actor.gameMode.destroyBlock(pos)&&level.isEmptyBlock(pos),"normal Survival harvest "+kind);int count=0;for(var drop:level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(pos).inflate(2)))if(drop.getItem().is(item(kind))){count+=drop.getItem().getCount();need(NativeLightItem.configuration(drop.getItem()).getInt("LightLevel")==6,"removed tile harvest choices "+kind);drop.discard();}need(count==1,"exactly one harvest "+kind);place(level,actor,pos,kind);}else assertOwner(level,pos,kind);}
        server.saveEverything(true,true,true);System.out.println("[vandorlabs][light-canonical] "+(phase.endsWith("write")?"write":"read")+" PASS checks="+checks);
    }catch(Throwable failure){failure.printStackTrace();System.out.println("[vandorlabs][light-canonical] FAIL "+failure);}finally{server.halt(false);}}
}
