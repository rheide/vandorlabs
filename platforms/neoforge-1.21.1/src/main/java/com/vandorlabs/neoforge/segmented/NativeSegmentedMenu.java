package com.vandorlabs.neoforge.segmented;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** One installed-section editor; the host checks every affected member before each transaction. */
public final class NativeSegmentedMenu extends AbstractContainerMenu {
    public final NativeSegmentedDoorEntity tile;
    public NativeSegmentedMenu(MenuType<?> type,int id,NativeSegmentedDoorEntity tile){
        super(type,id);this.tile=java.util.Objects.requireNonNull(tile);
    }
    public NativeSegmentedMenu(MenuType<?> type,int id,Inventory inventory,BlockPos pos){
        this(type,id,require(inventory.player,pos));
    }
    private static NativeSegmentedDoorEntity require(Player player,BlockPos pos){
        var level=player.level();if(!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof NativeSegmentedDoorEntity tile))
            throw new IllegalArgumentException("Missing Segmented Door section");
        return tile;
    }
    @Override public boolean stillValid(Player player){
        return !tile.inert() && NativeConfigurationAccess.canEditDialog(player,tile)
                && (!(tile.getLevel() instanceof net.minecraft.server.level.ServerLevel server)||NativeRedstoneChannels.open(server));
    }
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
    public static boolean apply(ServerPlayer player,NativeSegmentedEdit edit){
        if(!player.getServer().isSameThread() || !(player.containerMenu instanceof NativeSegmentedMenu menu)
                || menu.containerId!=edit.window() || !menu.stillValid(player))return false;
        var tile=menu.tile;
        switch(edit.operation()){
            case 0 -> tile.configure(player,edit.channels(),edit.ticks(),edit.local(),edit.opens());
            case 1,2 -> tile.operate(player,edit.operation()==1);
            case 3 -> tile.configureTexture(player,edit.texture());
            case 4 -> tile.configureAll(player,edit.channels(),edit.ticks(),edit.local(),edit.opens(),edit.texture(),edit.settings());
            case 6,7 -> tile.configureCellTexture(player,edit.texture(),edit.operation()==7);
            case 5 -> tile.configurePart(player,edit.channels(),edit.ticks(),edit.local(),edit.opens(),edit.texture(),edit.settings());
            default -> throw new IllegalArgumentException("Unknown Segmented Door operation");
        }
        return true;
    }
}
