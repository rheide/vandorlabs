package com.vandorlabs.neoforge.copier;

import com.vandorlabs.copier.core.CopySettings;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Configuration only; no inventory slots or client-supplied construction data. */
public final class NativeCopyMenu extends AbstractContainerMenu {
    public final NativeCopyControllerEntity tile;private final UUID identity;
    public NativeCopyMenu(MenuType<?> type,int window,NativeCopyControllerEntity tile){super(type,window);this.tile=java.util.Objects.requireNonNull(tile);identity=tile.key();}
    public NativeCopyMenu(MenuType<?> type,int window,Inventory inventory,BlockPos pos,UUID identity){this(type,window,require(inventory.player,pos));if(!this.identity.equals(identity))throw new IllegalArgumentException("Copy Controller replaced");}
    private static NativeCopyControllerEntity require(Player player,BlockPos pos){if(!player.level().hasChunkAt(pos)||!(player.level().getBlockEntity(pos) instanceof NativeCopyControllerEntity tile))throw new IllegalArgumentException("Copy Controller unavailable");return tile;}
    @Override public boolean stillValid(Player actor){if(!identity.equals(tile.key())||tile.inert()||tile.isRemoved()||tile.getLevel()!=actor.level()||!actor.level().hasChunkAt(tile.getBlockPos())||actor.level().getBlockEntity(tile.getBlockPos())!=tile)return false;if(com.vandorlabs.neoforge.NativeConfigurationAccess.canEditDialog(actor,tile))return true;
        return actor instanceof ServerPlayer player&&actor.level() instanceof net.minecraft.server.level.ServerLevel world&&actor.distanceToSqr(tile.getBlockPos().getCenter())<=64&&world.mayInteract(actor,tile.getBlockPos())&&actor.mayUseItemAt(tile.getBlockPos(),net.minecraft.core.Direction.UP,actor.getMainHandItem())&&com.vandorlabs.neoforge.transfer.NativeCellTransactions.sessionHeader(world,tile.getBlockPos(),identity,player)!=null;}
    @Override public ItemStack quickMoveStack(Player actor,int slot){return ItemStack.EMPTY;}
    public boolean apply(ServerPlayer actor,int window,BlockPos pos,int operation,CopySettings choices){if(!actor.getServer().isSameThread()||actor.containerMenu!=this||containerId!=window||!tile.getBlockPos().equals(pos)||!stillValid(actor)||tile.busy())return false;try{if(operation==0)return tile.configure(actor,choices);if(!choices.equals(tile.settings()))return false;if(operation==1)tile.request(actor);else if(operation==2)tile.checkAreas(actor);else return false;return true;}catch(IllegalArgumentException refusal){tile.failure(refusal.getMessage());return false;}}
}
