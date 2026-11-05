package com.vandorlabs.neoforge.door;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Captures actual root/mate identities supplied by the owning door lifecycle. No slots or registry activation. */
public final class NativeDoorMenu extends AbstractContainerMenu {
    public final NativeProgrammableDoorEntity tile;
    private final NativeProgrammableDoorEntity mate;
    private final NativeDoorLinks links;
    public NativeDoorMenu(MenuType<?> type,int id,NativeProgrammableDoorEntity tile){super(type,id);this.tile=java.util.Objects.requireNonNull(tile);if(!(tile.getBlockState().getBlock() instanceof NativeDoorLinks access))throw new IllegalArgumentException("Door link lifecycle unavailable");links=access;if(!links.configurationLinksLoaded(tile))throw new IllegalArgumentException("Incomplete loaded door links");mate=links.configurationMate(tile);if(mate==tile||mate!=null&&(mate.getLevel()!=tile.getLevel()||mate.large()!=tile.large()))throw new IllegalArgumentException("Invalid linked door editor");}
    @Override public boolean stillValid(Player player){return NativeConfigurationAccess.canEditDialog(player,tile)&&tile.getBlockState().getBlock()==links&&links.configurationLinksLoaded(tile)&&(mate==null||NativeConfigurationAccess.canEditDialog(player,mate))&&links.configurationMate(tile)==mate;}
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
    public static boolean apply(ServerPlayer player,BlockPos pos,int window,NativeDoorEdit edit,ChannelList channels){
        if(!player.getServer().isSameThread()||!NativeConfigurationAccess.canConfigure(player)||!(player.containerMenu instanceof NativeDoorMenu menu)||menu.containerId!=window||!menu.tile.getBlockPos().equals(pos)||!menu.stillValid(player)||channels==null||!menu.tile.accepts(edit)||menu.mate!=null&&!menu.mate.accepts(edit))return false;
        // Both settings are installed before either observer is notified. No inventory/world ownership transfer occurs here.
        boolean first=menu.tile.stage(edit,channels),second=menu.mate!=null&&menu.mate.stage(edit,channels);
        if(first)menu.tile.publish();if(second)menu.mate.publish();return true;
    }
}
