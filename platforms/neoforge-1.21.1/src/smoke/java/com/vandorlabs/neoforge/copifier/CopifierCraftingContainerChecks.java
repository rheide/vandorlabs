package com.vandorlabs.neoforge.copifier;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.GameType;

/** Vanilla result-slot pickup and quick-move on both actual crafting container sizes. */
public final class CopifierCraftingContainerChecks {
    private static int checks;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Copied crafting container: "+why);}
    static void run(ServerLevel level){runTarget(level,BuiltInRegistries.ITEM.get(ResourceLocation.parse("vandorlabs:programmable_block")));}
    public static void runTarget(ServerLevel level,Item block){
        var actor=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(new UUID(0,1902),"CopiedCrafting"));actor.setGameMode(GameType.SURVIVAL);
        var table=new net.minecraft.core.BlockPos(24700,100,24700);level.getChunkAt(table);level.setBlock(table,net.minecraft.world.level.block.Blocks.CRAFTING_TABLE.defaultBlockState(),18);actor.setPos(table.getX()+.5,table.getY(),table.getZ()-2);
        var data=new CompoundTag();var values=new CompoundTag();values.putInt("wall_texture",7);data.put("DuplifierSettings",values);data.putByteArray("OpaqueOwner",new byte[65536]);var tool=new ItemStack(NativeCopifierRegistrations.ITEM.get());tool.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
        var shaped=(CraftingRecipe)level.getRecipeManager().byKey(ResourceLocation.parse("vandorlabs:duplifier")).orElseThrow().value();var toolInput=CraftingInput.of(2,2,List.of(ItemStack.EMPTY,new ItemStack(Items.REDSTONE),new ItemStack(com.vandorlabs.neoforge.VandorLabsBootstrap.MATTER_INGOT.get()),new ItemStack(Items.REDSTONE)));need(shaped.matches(toolInput,level)&&shaped.assemble(toolInput,level.registryAccess()).is(tool.getItem()),"released shaped tool recipe");
        for(int side:new int[]{2,3}){
            actor.getInventory().clearContent();AbstractContainerMenu menu=side==2?actor.inventoryMenu:new CraftingMenu(61,actor.getInventory(),ContainerLevelAccess.create(level,table));actor.containerMenu=menu;var grid=(CraftingContainer)menu.getSlot(1).container;grid.clearContent();grid.setItem(0,tool.copy());grid.setItem(1,new ItemStack(block,17));menu.slotsChanged(grid);need(menu.getSlot(0).hasItem(),"actual public result slot side="+side);
            var expected=menu.getSlot(0).getItem().copy();menu.clicked(0,0,ClickType.PICKUP,actor);need(ItemStack.matches(menu.getCarried(),expected)&&grid.getItem(1).getCount()==16&&ItemStack.matches(grid.getItem(0),tool),"pickup consumes one and retains complete tool side="+side);
            menu.clicked(side==2?9:10,0,ClickType.PICKUP,actor);need(menu.getCarried().isEmpty(),"ordinary inventory accepts result side="+side);menu.clicked(0,0,ClickType.QUICK_MOVE,actor);need(grid.getItem(1).isEmpty()&&ItemStack.matches(grid.getItem(0),tool)&&!menu.getSlot(0).hasItem(),"quick move consumes remaining stack with complete tool side="+side);
            int total=0;for(var stack:actor.getInventory().items)if(stack.is(block)){need(ItemStack.isSameItemSameComponents(stack,expected),"every output carries exact configured choices side="+side);total+=stack.getCount();}need(total==17,"exactly seventeen usable outputs side="+side);
            grid.clearContent();actor.containerMenu=actor.inventoryMenu;
        }
        System.out.println("[vandorlabs][copifier-crafting-container] PASS checks="+checks+" canonical recipes/2x2/3x3/pickup/quickmove/complete64KiBtool/exact17outputs");
    }
}
