package com.vandorlabs.neoforge.trigger;

import com.vandorlabs.neoforge.VandorLabsModern;
import com.vandorlabs.neoforge.NativeConfigurationAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class TriggerMenu extends AbstractContainerMenu {
    public static final DeferredHolder<MenuType<?>,MenuType<TriggerMenu>> TYPE=VandorLabsModern.MENUS.register("programmable_trigger",()->net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id,inventory,buffer)->new TriggerMenu(id,inventory,buffer.readBlockPos())));
    public final TriggerEntity tile;public final BlockPos pos;
    public TriggerMenu(int id,Inventory inventory,BlockPos pos){super(TYPE.get(),id);this.pos=pos.immutable();if(!(inventory.player.level().getBlockEntity(pos) instanceof TriggerEntity entity))throw new IllegalArgumentException("Missing programmable trigger");tile=entity;}
    public static void initialize(){}
    public static void open(ServerPlayer player,TriggerEntity tile){if(NativeConfigurationAccess.canEdit(player,tile))player.openMenu(new SimpleMenuProvider((id,inventory,p)->new TriggerMenu(id,inventory,tile.getBlockPos()),Component.literal("Programmable Trigger Block")),buffer->buffer.writeBlockPos(tile.getBlockPos()));}
    @Override public boolean stillValid(Player player){return NativeConfigurationAccess.canEdit(player,tile);}
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
}
