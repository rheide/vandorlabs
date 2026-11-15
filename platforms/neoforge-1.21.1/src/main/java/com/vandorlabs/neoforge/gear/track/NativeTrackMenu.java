package com.vandorlabs.neoforge.gear.track;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** A standalone Track has a material/style session; attached arms keep their normal mechanism dialog. */
public final class NativeTrackMenu extends AbstractContainerMenu {
    public final BlockPos selected;
    public final java.util.UUID identity;
    public final NativeTrackEntity tile;
    public NativeTrackMenu(int window,Inventory inventory,BlockPos pos,java.util.UUID identity){super(NativeTracks.MENU,window);selected=pos.immutable();this.identity=identity;var world=inventory.player.level();if(!world.hasChunkAt(pos)||!(world.getBlockEntity(pos) instanceof NativeTrackEntity track)||!track.identity().equals(identity))throw new IllegalArgumentException("Track session identity");tile=track;}
    @Override public boolean stillValid(Player actor){return !tile.inert()&&tile.getLevel()==actor.level()&&actor.level().hasChunkAt(selected)&&actor.level().getBlockEntity(selected)==tile&&tile.identity().equals(identity)&&tile.controllerEntity()==null&&tile.visual().owner()==null&&actor.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(selected))<=64&&NativeConfigurationAccess.canEditDialog(actor,actor.level(),selected);}
    @Override public ItemStack quickMoveStack(Player actor,int index){return ItemStack.EMPTY;}
}
