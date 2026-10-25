package com.vandorlabs.neoforge.control;

import com.vandorlabs.neoforge.VandorLabsModern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Legacy binary settings are available to ordinary survival builders, through an exact session. */
public final class ControlMenu extends AbstractContainerMenu {
    public static final DeferredHolder<MenuType<?>,MenuType<ControlMenu>> TYPE=VandorLabsModern.MENUS.register("mounted_control",()->net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id,inventory,buffer)->new ControlMenu(id,inventory,buffer.readBlockPos())));
    public final MountedControlEntity tile;
    public final BlockPos pos;
    public ControlMenu(int id,Inventory inventory,BlockPos pos){super(TYPE.get(),id);this.pos=pos.immutable();if(!(inventory.player.level().getBlockEntity(pos) instanceof MountedControlEntity control))throw new IllegalArgumentException("Missing mounted control");tile=control;}
    public static void initialize(){}
    public static boolean canEdit(Player player,MountedControlEntity tile){var level=tile.getLevel();BlockPos pos=tile.getBlockPos();return level!=null&&player.isAlive()&&!player.isSpectator()&&player.level()==level&&level.hasChunkAt(pos)&&level.getBlockEntity(pos)==tile&&player.distanceToSqr(pos.getX(),pos.getY(),pos.getZ())<=64&&level.mayInteract(player,pos)&&player.mayUseItemAt(pos,Direction.UP,player.getMainHandItem());}
    public static void open(ServerPlayer player,MountedControlEntity tile){if(canEdit(player,tile))player.openMenu(new SimpleMenuProvider((id,inventory,p)->new ControlMenu(id,inventory,tile.getBlockPos()),Component.literal("Redstone Channels")),buffer->buffer.writeBlockPos(tile.getBlockPos()));}
    @Override public boolean stillValid(Player player){return canEdit(player,tile);}
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
}
