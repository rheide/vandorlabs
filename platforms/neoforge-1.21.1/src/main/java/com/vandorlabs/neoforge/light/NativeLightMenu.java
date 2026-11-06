package com.vandorlabs.neoforge.light;
import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import com.vandorlabs.persistence.*;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
/** Dedicated editor; loaded owner/window identity and present held tool authorize every edit. */
public final class NativeLightMenu extends AbstractContainerMenu {
 public final NativeLightEntity tile;private final NativeLightBlock block;
 public NativeLightMenu(MenuType<?> type,int id,NativeLightEntity tile){super(type,id);this.tile=java.util.Objects.requireNonNull(tile);if(!(tile.getBlockState().getBlock() instanceof NativeLightBlock owner))throw new IllegalArgumentException("Missing Light family");block=owner;}
 public NativeLightMenu(MenuType<?> type,int id,Inventory inventory,BlockPos pos){this(type,id,require(inventory.player,pos));}
 private static NativeLightEntity require(Player player,BlockPos pos){if(!player.level().hasChunkAt(pos)||!(player.level().getBlockEntity(pos) instanceof NativeLightEntity tile))throw new IllegalArgumentException("Missing loaded Light");return tile;}
 @Override public boolean stillValid(Player player){return tile.available()&&tile.getBlockState().getBlock()==block&&(!(tile.getLevel() instanceof ServerLevel server)||NativeRedstoneChannels.open(server))&&NativeConfigurationAccess.canEditDialog(player,tile);}
 @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
 public static NativeLightMenu editing(ServerPlayer player,BlockPos pos,int window){return player.getServer().isSameThread()&&NativeConfigurationAccess.canConfigure(player)&&player.containerMenu instanceof NativeLightMenu menu&&menu.containerId==window&&menu.tile.getBlockPos().equals(pos)&&menu.stillValid(player)?menu:null;}
 public static boolean apply(ServerPlayer player,BlockPos pos,int window,NativeLightEdit edit,ChannelList channels){var menu=editing(player,pos,window);return menu!=null&&menu.tile.configure(player,edit,channels);}
 public static boolean applyFrames(ServerPlayer player,BlockPos pos,int window,FrameSettings frames){var menu=editing(player,pos,window);return menu!=null&&menu.tile.configureFrames(player,frames);}
 public static boolean applyGlass(ServerPlayer player,BlockPos pos,int window,PanelGlassSettings glass){var menu=editing(player,pos,window);return menu!=null&&menu.tile.configureGlass(player,glass);}
 public static boolean applyTransmission(ServerPlayer player,BlockPos pos,int window,ChannelList channels,int range,boolean local){var menu=editing(player,pos,window);return menu!=null&&menu.tile.configureTransmission(player,channels,range,local);}
}
