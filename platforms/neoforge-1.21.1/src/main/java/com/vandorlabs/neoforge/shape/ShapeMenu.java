package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.neoforge.VandorLabsModern;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Exact installed shape session over the common configurizer and held-item edit permissions. */
public final class ShapeMenu extends AbstractContainerMenu {
    public static final DeferredHolder<MenuType<?>,MenuType<ShapeMenu>> TYPE=VandorLabsModern.MENUS.register("programmable_shape",()->net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id,inventory,buffer)->new ShapeMenu(id,inventory,buffer.readBlockPos())));
    public final ProgrammableShapeEntity tile;public final BlockPos pos;
    public ShapeMenu(int id,Inventory inventory,BlockPos pos){super(TYPE.get(),id);this.pos=pos.immutable();if(!(inventory.player.level().getBlockEntity(pos) instanceof ProgrammableShapeEntity entity))throw new IllegalArgumentException("Missing programmable shape");tile=entity;}
    public static void initialize(){}
    public static void open(ServerPlayer player,ProgrammableShapeEntity tile){if(NativeConfigurationAccess.canEdit(player,tile))player.openMenu(new SimpleMenuProvider((id,inventory,p)->new ShapeMenu(id,inventory,tile.getBlockPos()),net.minecraft.network.chat.Component.translatable(tile.isStair()?"gui.vandorlabs.stairs.title":"gui.vandorlabs.slab.title")),buffer->buffer.writeBlockPos(tile.getBlockPos()));}
    @Override public boolean stillValid(Player player){return player.containerMenu==this&&NativeConfigurationAccess.canEdit(player,tile);}
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
}
