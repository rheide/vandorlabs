package com.vandorlabs.neoforge.screen;
import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
/** Exact loaded owner identity; browsing permission never substitutes for current edit authorization. */
public final class NativeScreenMenu extends AbstractContainerMenu {
 public final NativeScreenEntity tile;private final NativeScreenBlock block;
 public NativeScreenMenu(MenuType<?> type,int id,NativeScreenEntity tile){super(type,id);this.tile=java.util.Objects.requireNonNull(tile);if(!(tile.getBlockState().getBlock() instanceof NativeScreenBlock owner)||owner.family()!=tile.family())throw new IllegalArgumentException("Screen block family unavailable");block=owner;}
 public NativeScreenMenu(MenuType<?> type,int id,Inventory inventory,BlockPos pos){this(type,id,require(inventory.player,pos));}
 private static NativeScreenEntity require(Player player,BlockPos pos){if(!player.level().hasChunkAt(pos)||!(player.level().getBlockEntity(pos) instanceof NativeScreenEntity tile))throw new IllegalArgumentException("Missing loaded Screen");return tile;}
 public NativeScreenEntity tile(){return tile;}
 @Override public boolean stillValid(Player player){return !tile.inert()&&!tile.isRemoved()&&tile.getBlockState().getBlock()==block&&(!(tile.getLevel() instanceof ServerLevel server)||NativeRedstoneChannels.open(server))&&NativeConfigurationAccess.canEditDialog(player,tile);}
 @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
 public static NativeScreenMenu editing(ServerPlayer player,BlockPos pos,int window){return player.getServer().isSameThread()&&NativeConfigurationAccess.canConfigure(player)&&player.containerMenu instanceof NativeScreenMenu menu&&menu.containerId==window&&menu.tile.getBlockPos().equals(pos)&&!menu.tile.editing()&&menu.stillValid(player)?menu:null;}
 public static boolean applyActivation(ServerPlayer player,BlockPos pos,int window,NativeScreenActivationEdit edit){NativeScreenMenu menu=editing(player,pos,window);return menu!=null&&NativeScreenGroups.applyActivation(menu.tile,edit,target->player.level()==target.getLevel()&&player.level().mayInteract(player,target.getBlockPos())&&player.mayUseItemAt(target.getBlockPos(),net.minecraft.core.Direction.UP,player.getMainHandItem()));}
 public static boolean applySurface(ServerPlayer player,BlockPos pos,int window,NativeScreenSurfaceEdit edit){NativeScreenMenu menu=editing(player,pos,window);return menu!=null&&menu.tile.applySurface(edit);}
 public static boolean applyDisplay(ServerPlayer player,BlockPos pos,int window,NativeScreenDisplayEdit edit){NativeScreenMenu menu=editing(player,pos,window);return menu!=null&&menu.tile.applyDisplay(edit);}
 public static boolean applyRows(ServerPlayer player,BlockPos pos,int window,int slot,NativeScreenRowEdit edit){NativeScreenMenu menu=editing(player,pos,window);return menu!=null&&menu.tile.controlService().configure(slot,edit);}
}
