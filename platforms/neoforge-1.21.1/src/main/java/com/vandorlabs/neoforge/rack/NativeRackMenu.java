package com.vandorlabs.neoforge.rack;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.rack.AutopilotModuleSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Configuration only; a menu cannot acquire or replace physical modules. */
public final class NativeRackMenu extends AbstractContainerMenu {
    public final NativeRackEntity tile;
    public NativeRackMenu(MenuType<?> type,int id,NativeRackEntity tile){super(type,id);this.tile=java.util.Objects.requireNonNull(tile);}
    public NativeRackMenu(MenuType<?> type,int id,Inventory inventory,BlockPos pos){this(type,id,require(inventory.player,pos));}
    private static NativeRackEntity require(Player player,BlockPos pos){if(!player.level().hasChunkAt(pos)||!(player.level().getBlockEntity(pos) instanceof NativeRackEntity owner))throw new IllegalArgumentException("Missing Rack");return owner;}
    @Override public boolean stillValid(Player player){return !tile.inert()&&!tile.isRemoved()&&NativeConfigurationAccess.canEditDialog(player,tile);}
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
    private boolean authenticated(ServerPlayer player,int window,BlockPos pos){return player.getServer().isSameThread()&&player.containerMenu==this&&containerId==window&&tile.getBlockPos().equals(pos)&&stillValid(player);}
    public boolean configure(ServerPlayer player,int window,BlockPos pos,int housing,boolean emit,NativeRackData.Transmission transmission){return authenticated(player,window,pos)&&tile.configure(housing,emit,transmission);}
    public boolean configurePower(ServerPlayer player,int window,BlockPos pos,boolean out){if(!authenticated(player,window,pos))return false;tile.setPowerOut(out);return true;}
    public boolean configureModule(ServerPlayer player,int window,BlockPos pos,int slot,long sequence,AutopilotModuleSettings settings){if(!authenticated(player,window,pos)||tile.kind(slot)!=2)return false;var previous=tile.moduleSettings(slot);if(NativeRackData.marker(settings,player.registryAccess()).isEmpty()&&(!settings.marker.equals(previous.marker)||settings.markerMetadata!=previous.markerMetadata))return false;return tile.configureModule(slot,sequence,settings);}
}
