package com.vandorlabs.neoforge.screen;
import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.neoforge.seating.NativeChairEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
/** Exact loaded owner identity; browsing permission never substitutes for current edit authorization. */
public final class NativeScreenMenu extends AbstractContainerMenu {
 public final NativeScreenEntity tile;
 private final NativeChairEntity cockpit;
 private final net.minecraft.world.level.block.Block block;
 private final int initialSlot;
 public NativeScreenMenu(MenuType<?> type,int id,BlockEntity owner){this(type,id,owner,0);}
 public NativeScreenMenu(MenuType<?> type,int id,BlockEntity owner,int initialSlot){
  super(type,id);this.initialSlot=initialSlot;
  if(owner instanceof NativeScreenEntity screen&&screen.getBlockState().getBlock() instanceof NativeScreenBlock nativeBlock
      &&nativeBlock.family()==screen.family()){tile=screen;cockpit=null;block=nativeBlock;}
  else if(owner instanceof NativeChairEntity chair&&chair.cockpitTouch()!=null){tile=null;cockpit=chair;block=chair.getBlockState().getBlock();}
  else throw new IllegalArgumentException("Screen host unavailable");
  if(initialSlot<0||initialSlot>=controlSlots())throw new IllegalArgumentException("Screen menu slot");
 }
 public NativeScreenMenu(MenuType<?> type,int id,Inventory inventory,BlockPos pos){this(type,id,inventory,pos,0);}
 public NativeScreenMenu(MenuType<?> type,int id,Inventory inventory,BlockPos pos,int slot){this(type,id,require(inventory.player,pos),slot);}
 private static BlockEntity require(Player player,BlockPos pos){
  if(!player.level().hasChunkAt(pos))throw new IllegalArgumentException("Missing loaded Screen");
  return player.level().getBlockEntity(pos);
 }
 public NativeScreenEntity tile(){return tile;}
 public boolean cockpit(){return cockpit!=null;}
 public int initialSlot(){return initialSlot;}
 public int controlSlots(){return cockpit()?cockpit.cockpitTouch().controlSlots():2;}
 public com.vandorlabs.neoforge.seating.NativeCockpitTouch cockpitTouch(){return cockpit.cockpitTouch();}
 public boolean rowsOnly(){return cockpit()||tile.yoke();}
 public int rowCapacity(int slot){return cockpit()?cockpit.cockpitTouch().rowCapacity(slot):tile.rowCapacity(slot);}
 public NativeScreenControls controls(int slot){return cockpit()?cockpit.cockpitTouch().controls(slot):tile.controls(slot);}
 private BlockEntity owner(){return cockpit()?cockpit:tile;}
 public BlockPos hostPos(){return owner().getBlockPos();}
 private boolean editing(){return cockpit()?cockpit.cockpitTouch().editing():tile.editing();}
 @Override public boolean stillValid(Player player){
  var owner=owner();var level=owner.getLevel();
  if(owner.isRemoved()||owner.getBlockState().getBlock()!=block||level instanceof ServerLevel server&&!NativeRedstoneChannels.open(server)
      ||!NativeConfigurationAccess.canEditDialog(player,owner))return false;
  return cockpit()?!cockpit.inert()&&((com.vandorlabs.neoforge.seating.NativeSeatBlock)block).complete(level,hostPos()):!tile.inert();
 }
 @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
 public static NativeScreenMenu editing(ServerPlayer player,BlockPos pos,int window){return player.getServer().isSameThread()&&NativeConfigurationAccess.canConfigure(player)&&player.containerMenu instanceof NativeScreenMenu menu&&menu.containerId==window&&menu.hostPos().equals(pos)&&!menu.editing()&&menu.stillValid(player)?menu:null;}
 public static NativeScreenMenu viewing(ServerPlayer player,BlockPos pos,int window){return player.getServer().isSameThread()&&player.containerMenu instanceof NativeScreenMenu menu&&menu.containerId==window&&menu.hostPos().equals(pos)&&!menu.editing()&&menu.stillValid(player)?menu:null;}
 public static boolean applyTransmission(ServerPlayer player,BlockPos pos,int window,NativeScreenTransmissionEdit edit){NativeScreenMenu menu=viewing(player,pos,window);return menu!=null&&!menu.cockpit()&&menu.tile.applyTransmission(edit);}
 public static boolean applyFrames(ServerPlayer player,BlockPos pos,int window,NativeScreenFrameEdit edit){NativeScreenMenu menu=editing(player,pos,window);return menu!=null&&!menu.rowsOnly()&&menu.tile.applyFrames(edit);}
 public static boolean applyLayout(ServerPlayer player,BlockPos pos,int window,NativeScreenLayoutEdit edit){NativeScreenMenu menu=editing(player,pos,window);return menu!=null&&!menu.rowsOnly()&&menu.tile.applyLayout(edit);}
 public static boolean applyActivation(ServerPlayer player,BlockPos pos,int window,NativeScreenActivationEdit edit){NativeScreenMenu menu=editing(player,pos,window);return menu!=null&&!menu.rowsOnly()&&NativeScreenGroups.applyActivation(menu.tile,edit,target->player.level()==target.getLevel()&&player.level().mayInteract(player,target.getBlockPos())&&player.mayUseItemAt(target.getBlockPos(),net.minecraft.core.Direction.UP,player.getMainHandItem()));}
 public static boolean applySurface(ServerPlayer player,BlockPos pos,int window,NativeScreenSurfaceEdit edit){NativeScreenMenu menu=editing(player,pos,window);return menu!=null&&!menu.rowsOnly()&&NativeScreenSurfaceGroups.apply(menu.tile,edit,target->player.level()==target.getLevel()&&player.level().mayInteract(player,target.getBlockPos())&&player.mayUseItemAt(target.getBlockPos(),net.minecraft.core.Direction.UP,player.getMainHandItem()));}
 public static boolean applyDisplay(ServerPlayer player,BlockPos pos,int window,NativeScreenDisplayEdit edit){NativeScreenMenu menu=editing(player,pos,window);return menu!=null&&!menu.rowsOnly()&&menu.tile.applyDisplay(edit);}
 public static boolean applyCockpit(ServerPlayer player,BlockPos pos,int window,int slot,com.vandorlabs.neoforge.seating.NativeCockpitSurfaceEdit edit){
  NativeScreenMenu menu=editing(player,pos,window);
  return menu!=null&&menu.cockpit()&&menu.cockpitTouch().configure(slot,edit);
 }
 public static boolean applyRows(ServerPlayer player,BlockPos pos,int window,int slot,NativeScreenRowEdit edit){NativeScreenMenu menu=editing(player,pos,window);return menu!=null&&(menu.cockpit()?(menu.cockpit.cockpitTouch().configure(slot,edit)):menu.tile.applyRows(slot,edit));}
}
