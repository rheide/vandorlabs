package com.vandorlabs.neoforge.trapdoor;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** A dedicated installed-leaf editor; the host validates the entire affected group before mutation. */
public final class NativeTrapdoorMenu extends AbstractContainerMenu {
    public final NativeTrapdoorEntity tile;
    public NativeTrapdoorMenu(MenuType<?> type,int id,NativeTrapdoorEntity tile){super(type,id);this.tile=java.util.Objects.requireNonNull(tile);}
    public NativeTrapdoorMenu(MenuType<?> type,int id,Inventory inventory,BlockPos pos){this(type,id,require(inventory.player,pos));}
    private static NativeTrapdoorEntity require(Player player,BlockPos pos){
        var tile=NativeTrapdoorEntity.at(player.level(),pos);
        if(tile==null)throw new IllegalArgumentException("Missing trapdoor leaf");
        return tile;
    }
    @Override public boolean stillValid(Player player){return !tile.inert()&&NativeConfigurationAccess.canEdit(player,tile)
            &&(!(tile.getLevel() instanceof net.minecraft.server.level.ServerLevel server)||NativeRedstoneChannels.open(server));}
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
    public static boolean apply(ServerPlayer player,NativeTrapdoorEdit edit){
        if(!player.getServer().isSameThread()||!(player.containerMenu instanceof NativeTrapdoorMenu menu)
                ||menu.containerId!=edit.window()||!menu.stillValid(player))return false;
        return menu.tile.configure(player,edit.settings(),edit.inverted(),Direction.from2DDataValue(edit.settings().facing()));
    }
}
