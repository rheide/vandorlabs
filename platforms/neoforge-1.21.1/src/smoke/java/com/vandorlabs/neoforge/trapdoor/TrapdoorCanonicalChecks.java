package com.vandorlabs.neoforge.trapdoor;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Public registry/recipe/normal item-use and mining acceptance with a separate saved process. */
@EventBusSubscriber(modid="vandorlabs")
public final class TrapdoorCanonicalChecks {
    private static final BlockPos BASE=new BlockPos(5392,112,5392);
    private static int checks;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Canonical Trapdoor: "+why);}
    private static ResourceLocation id(boolean diagonal){return ResourceLocation.fromNamespaceAndPath("vandorlabs",diagonal?NativeTrapdoorRegistrations.DIAGONAL_ID:NativeTrapdoorRegistrations.ID);}
    private static NativeTrapdoorBlock block(boolean diagonal){return (NativeTrapdoorBlock)BuiltInRegistries.BLOCK.get(id(diagonal));}
    private static NativeTrapdoorItem item(boolean diagonal){return (NativeTrapdoorItem)BuiltInRegistries.ITEM.get(id(diagonal));}
    private static ItemStack configured(boolean diagonal){var stack=new ItemStack(item(diagonal),2);var root=new net.minecraft.nbt.CompoundTag();var tag=new net.minecraft.nbt.CompoundTag();tag.putInt("housingTexture",7);tag.putInt("TrapdoorPosition",1);tag.putBoolean("TrapdoorSliding",true);tag.putInt("TrapdoorTrigger",0);tag.putIntArray("RedstoneChannels",new int[]{29,31});root.put("BlockEntityTag",tag);stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(root));return stack;}
    private static void actorAt(ServerPlayer actor,BlockPos pos){actor.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+2.5);actor.setYRot(180);actor.setXRot(45);actor.setYHeadRot(180);actor.setYBodyRot(180);actor.getInventory().selected=0;}
    private static void place(ServerLevel level,ServerPlayer actor,BlockPos pos,boolean diagonal){
        level.getChunk(pos);for(var direction:Direction.values())level.setBlock(pos.relative(direction),Blocks.AIR.defaultBlockState(),2);level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),2);
        actorAt(actor,pos);var stack=configured(diagonal);actor.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var result=actor.gameMode.useItemOn(actor,level,stack,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atLowerCornerOf(pos.below()).add(.25,1,.75),Direction.UP,pos.below(),false));
        need(result.consumesAction()&&stack.getCount()==1&&level.getBlockState(pos).is(block(diagonal)),"normal Survival configured use "+diagonal+" "+result);
        var tile=NativeTrapdoorEntity.at(level,pos);need(tile!=null&&!tile.inert()&&tile.getType()==BuiltInRegistries.BLOCK_ENTITY_TYPE.get(id(diagonal)),"canonical entity factory "+diagonal);
        need(tile.settings().choices().texture==7&&tile.settings().choices().sliding&&tile.settings().channels().equals(com.vandorlabs.redstone.ChannelList.of(29,31)),"configured choices installed "+diagonal);
        var pick=block(diagonal).getCloneItemStack(level.getBlockState(pos),new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false),level,pos,actor);
        need(pick.is(item(diagonal))&&NativeTrapdoorItem.configuration(pick).getInt("housingTexture")==7,"canonical configured pick "+diagonal);
    }
    private static void recipes(ServerLevel level){
        var alloy=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("vandorlabs:industrial_alloy_ingot")));var matter=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("vandorlabs:programmable_matter_ingot")));
        var regular=(net.minecraft.world.item.crafting.CraftingRecipe)level.getRecipeManager().byKey(id(false)).orElseThrow().value();var input=CraftingInput.of(3,2,List.of(alloy,alloy,alloy,alloy,matter,alloy));
        need(regular.matches(input,level)&&regular.assemble(input,level.registryAccess()).is(item(false))&&regular.assemble(input,level.registryAccess()).getCount()==2,"actual two-output regular recipe");
        var diagonal=(net.minecraft.world.item.crafting.CraftingRecipe)level.getRecipeManager().byKey(id(true)).orElseThrow().value();var upgrade=CraftingInput.of(2,1,List.of(new ItemStack(item(false)),alloy));need(diagonal.matches(upgrade,level)&&diagonal.assemble(upgrade,level.registryAccess()).is(item(true))&&diagonal.assemble(upgrade,level.registryAccess()).getCount()==1,"actual diagonal upgrade recipe");
    }
    @SubscribeEvent public static void started(ServerStartedEvent event){
        String phase=System.getProperty("vandorlabs.transmitterPhase","");if(!phase.startsWith("trapdoor-canonical-"))return;var server=event.getServer();
        try{ServerLevel level=server.overworld();var actor=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.fromString("0f69c44e-27d3-4f87-bbcd-14f0abeb8a02"),"CanonicalTrapdoor"));actor.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            recipes(level);
            for(boolean diagonal:new boolean[]{false,true}){
                BlockPos pos=BASE.east(diagonal?16:0);level.getChunk(pos);
                if(phase.endsWith("write")){
                    place(level,actor,pos,diagonal);
                    var state=level.getBlockState(pos);var tool=new ItemStack(Items.WOODEN_PICKAXE);need(tool.isCorrectToolForDrops(state)&&!ItemStack.EMPTY.isCorrectToolForDrops(state)&&state.requiresCorrectToolForDrops(),"pickaxe tier and empty-hand refusal "+diagonal);
                    actor.setItemInHand(InteractionHand.MAIN_HAND,tool);need(actor.gameMode.destroyBlock(pos)&&level.isEmptyBlock(pos),"actual Survival harvest "+diagonal);
                    var drops=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(pos).inflate(2));int count=0;for(var dropped:drops)if(dropped.getItem().is(item(diagonal))){count+=dropped.getItem().getCount();need(NativeTrapdoorItem.configuration(dropped.getItem()).getInt("housingTexture")==7,"post-removal configured material "+diagonal);dropped.discard();}need(count==1,"exactly one canonical harvest "+diagonal+" count="+count);
                    place(level,actor,pos,diagonal);
                }else{var tile=NativeTrapdoorEntity.at(level,pos);need(tile!=null&&!tile.inert()&&tile.getType()==BuiltInRegistries.BLOCK_ENTITY_TYPE.get(id(diagonal))&&tile.settings().choices().texture==7&&tile.settings().choices().sliding,"canonical separate-process saved owner "+diagonal);}
            }
            System.out.println("[vandorlabs][trapdoor-canonical] "+(phase.endsWith("write")?"write":"read")+" PASS checks="+checks);
        }catch(Throwable failure){failure.printStackTrace();System.out.println("[vandorlabs][trapdoor-canonical] FAIL "+failure);}
        finally{server.halt(false);}
    }
}
