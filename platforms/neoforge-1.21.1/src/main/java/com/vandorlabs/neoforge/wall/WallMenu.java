package com.vandorlabs.neoforge.wall;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.neoforge.VandorLabsModern;
import com.vandorlabs.ui.WallDialog;
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

/** The installed wall and current container jointly authorize every settings edit. */
public final class WallMenu extends AbstractContainerMenu {
    public static final DeferredHolder<MenuType<?>,MenuType<WallMenu>> TYPE=VandorLabsModern.MENUS.register("programmable_wall",()->net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id,inventory,buffer)->new WallMenu(id,inventory,buffer.readBlockPos())));
    public final WallEntity tile;
    public final BlockPos pos;
    public WallMenu(int id,Inventory inventory,BlockPos pos){super(TYPE.get(),id);this.pos=pos.immutable();if(!(inventory.player.level().getBlockEntity(pos) instanceof WallEntity entity))throw new IllegalArgumentException("Missing programmable wall");tile=entity;}
    public static void initialize(){}
    public static void open(ServerPlayer player,WallEntity tile){if(NativeConfigurationAccess.canEdit(player,tile))player.openMenu(new SimpleMenuProvider((id,inventory,p)->new WallMenu(id,inventory,tile.getBlockPos()),Component.translatable(WallDialog.TITLE_KEY)),buffer->buffer.writeBlockPos(tile.getBlockPos()));}
    @Override public boolean stillValid(Player player){return player.containerMenu==this&&NativeConfigurationAccess.canEdit(player,tile);}
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
}
