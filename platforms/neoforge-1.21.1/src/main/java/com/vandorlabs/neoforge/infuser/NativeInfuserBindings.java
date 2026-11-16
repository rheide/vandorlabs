package com.vandorlabs.neoforge.infuser;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.items.IItemHandler;
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
public final class NativeInfuserBindings{
 public static NativeInfuserBlock BLOCK;public static Item CORE;public static BlockEntityType<NativeInfuserEntity> ENTITY;public static MenuType<NativeInfuserMenu> MENU;
 private static ResourceLocation id(String name){return ResourceLocation.fromNamespaceAndPath("vandorlabs",name);}
 @SubscribeEvent public static void register(RegisterEvent event){event.register(Registries.BLOCK,h->{BLOCK=new NativeInfuserBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F).requiresCorrectToolForDrops().sound(SoundType.METAL));h.register(id("programmable_matter_infuser"),BLOCK);});event.register(Registries.ITEM,h->{h.register(id("programmable_matter_infuser"),new BlockItem(BLOCK,new Item.Properties()));CORE=new Item(new Item.Properties());h.register(id("infuser_core"),CORE);});event.register(Registries.BLOCK_ENTITY_TYPE,h->{ENTITY=BlockEntityType.Builder.of(NativeInfuserEntity::new,BLOCK).build(null);h.register(id("programmable_matter_infuser"),ENTITY);});event.register(Registries.MENU,h->{MENU=net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((window,inventory,buffer)->new NativeInfuserMenu(MENU,window,inventory,buffer.readBlockPos()));h.register(id("matter_infuser"),MENU);});}
 @SubscribeEvent public static void creative(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event){if(event.getTabKey().location().equals(id("vandorlabs"))){event.accept(BLOCK);event.accept(CORE);}}
 public static boolean open(Player player,Level level,BlockPos pos){if(level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null||!(level.getBlockEntity(pos) instanceof NativeInfuserEntity owner)||!owner.canUse(player))return false;if(player instanceof ServerPlayer actor){if(!owner.available())return false;actor.openMenu(new SimpleMenuProvider((window,inventory,ignored)->new NativeInfuserMenu(MENU,window,inventory,owner),BLOCK.getName()),buffer->buffer.writeBlockPos(pos));}return true;}
 @SubscribeEvent public static void capabilities(RegisterCapabilitiesEvent event){event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,ENTITY,(tile,side)->new Handler(tile,side));}
 private static final class Handler implements IItemHandler{
  private final NativeInfuserEntity owner;private final Direction side;private final int[] slots;
  Handler(NativeInfuserEntity owner,Direction side){this.owner=owner;this.side=side;slots=side==null?new int[]{0,1,2}:owner.getSlotsForFace(side);}
  private int physical(int slot){return slot>=0&&slot<slots.length?slots[slot]:-1;}
  public int getSlots(){return slots.length;}public ItemStack getStackInSlot(int slot){int index=physical(slot);return index<0||!owner.available()?ItemStack.EMPTY:owner.getItem(index).copy();}public int getSlotLimit(int slot){return physical(slot)<0?0:64;}
  public boolean isItemValid(int slot,ItemStack stack){int index=physical(slot);return index>=0&&owner.canPlaceItemThroughFace(index,stack,side);}
  public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){int index=physical(slot);if(!isItemValid(slot,stack))return stack;var before=owner.getItem(index);if(!before.isEmpty()&&!ItemStack.isSameItemSameComponents(before,stack))return stack;int amount=Math.min(stack.getCount(),Math.min(64,stack.getMaxStackSize())-before.getCount());if(amount<=0)return stack;if(!simulate){if(before.isEmpty())owner.setItem(index,stack.copyWithCount(amount));else{before.grow(amount);owner.setChanged();}}return stack.copyWithCount(stack.getCount()-amount);}
  public ItemStack extractItem(int slot,int count,boolean simulate){int index=physical(slot);if(index<0||count<=0||!owner.canTakeItemThroughFace(index,owner.getItem(index),side))return ItemStack.EMPTY;var item=owner.getItem(index);return simulate?item.copyWithCount(Math.min(count,item.getCount())):owner.removeItem(index,count);}
 }
 private NativeInfuserBindings(){}
}
