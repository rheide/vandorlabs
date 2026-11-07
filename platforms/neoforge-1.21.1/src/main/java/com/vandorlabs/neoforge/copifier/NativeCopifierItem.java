package com.vandorlabs.neoforge.copifier;

import com.vandorlabs.neoforge.NativeOwnsBlockInteraction;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Inactive ordinary item route. Connected/crafting/undo and other target adapters remain separate. */
public class NativeCopifierItem extends Item implements NativeOwnsBlockInteraction {
    private final Supplier<MenuType<NativeCopifierMenu>> menus;
    public NativeCopifierItem(Properties properties,Supplier<MenuType<NativeCopifierMenu>> menus){super(properties.stacksTo(1));this.menus=java.util.Objects.requireNonNull(menus);}
    public static CompoundTag data(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();}
    private static void store(ServerPlayer player,ItemStack stack,CompoundTag root){if(root==null)stack.remove(DataComponents.CUSTOM_DATA);else stack.set(DataComponents.CUSTOM_DATA,CustomData.of(root));player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();}
    @Override public Component getName(ItemStack stack){var base=super.getName(stack);var root=data(stack);String source=root.getString(NativeCopifierClipboard.SOURCE);if(source.isEmpty())return base;return base.copy().append(" - ").append(source.startsWith("block.vandorlabs.")?Component.translatable(source):Component.literal(source));}
    @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context){return useOn(context);}
    @Override public InteractionResult useOn(UseOnContext context){
        if(context.getHand()!=InteractionHand.MAIN_HAND||context.getPlayer()==null)return InteractionResult.PASS;
        if(context.getLevel().isClientSide)return InteractionResult.SUCCESS;
        if(!(context.getPlayer() instanceof ServerPlayer player)||player.isSpectator()||!player.isAlive()||!player.getServer().isSameThread()||context.getLevel()!=player.level()||context.getItemInHand().getItem()!=this||context.getItemInHand()!=player.getMainHandItem()||context.getItemInHand().getCount()!=1)return InteractionResult.FAIL;
        var level=player.serverLevel();var pos=context.getClickedPos();var stack=context.getItemInHand();var root=data(stack);
        if(player.isShiftKeyDown()){
            var captured=NativeCopifierHousingWorld.capture(level,pos,player,context.getClickedFace());if(captured==null)return InteractionResult.FAIL;
            store(player,stack,NativeCopifierClipboard.copied(root,captured,level.getBlockState(pos).getBlock().getDescriptionId()));return InteractionResult.CONSUME;
        }
        if(NativeCopifierOptions.connected(root)||!NativeCopifierClipboard.hasCopy(root))return InteractionResult.FAIL;
        var selected=NativeCopifierOptions.selected(NativeCopifierClipboard.settings(root),NativeCopifierOptions.maskWords(root));
        return NativeCopifierHousingWorld.applySingle(level,pos,player,context.getClickedFace(),selected)?InteractionResult.CONSUME:InteractionResult.FAIL;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        var stack=player.getItemInHand(hand);if(hand!=InteractionHand.MAIN_HAND||player.isSpectator())return InteractionResultHolder.pass(stack);
        if(player instanceof ServerPlayer server){if(!server.getServer().isSameThread()||!player.isAlive()||level!=player.level()||stack.getItem()!=this||stack.getCount()!=1)return InteractionResultHolder.fail(stack);if(player.isShiftKeyDown())store(server,stack,NativeCopifierClipboard.clear(data(stack)));else{var type=menus.get();if(type==null)return InteractionResultHolder.fail(stack);server.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inventory,owner)->new NativeCopifierMenu(type,id,inventory),getName(stack)));}}
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
}
