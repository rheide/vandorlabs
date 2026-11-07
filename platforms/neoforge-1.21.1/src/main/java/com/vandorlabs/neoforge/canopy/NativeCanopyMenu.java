package com.vandorlabs.neoforge.canopy;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/** Installed root editor session. Physical ownership never travels through the settings transport. */
public final class NativeCanopyMenu extends AbstractContainerMenu {
    public final NativeCanopyEntity tile;
    public NativeCanopyMenu(MenuType<?> type,int id,NativeCanopyEntity tile){super(type,id);this.tile=java.util.Objects.requireNonNull(tile);}
    public NativeCanopyMenu(MenuType<?> type,int id,Inventory inventory,BlockPos pos){this(type,id,require(inventory.player,pos));}
    private static NativeCanopyEntity require(Player player,BlockPos pos){
        var tile=NativeCanopyLifecycle.at(player.level(),pos);
        if(tile==null||NativeCanopyLifecycle.owner(tile)!=tile)throw new IllegalArgumentException("Missing Canopy owner");return tile;
    }
    @Override public boolean stillValid(Player player){return NativeCanopyMenus.usable(player,tile);}
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
    public boolean apply(ServerPlayer player,int window,BlockPos pos,NativeCanopySettings settings){
        return player.getServer().isSameThread()&&player.containerMenu==this&&containerId==window
            &&tile.getBlockPos().equals(pos)&&NativeConfigurationAccess.canConfigure(player)&&stillValid(player)
            &&NativeCanopyConfiguration.apply(tile,player,settings);
    }
}
