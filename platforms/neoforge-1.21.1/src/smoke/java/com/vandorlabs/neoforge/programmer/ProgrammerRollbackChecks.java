package com.vandorlabs.neoforge.programmer;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
/** Actual partial rollback and temporarily missing factory, including independent process restart. */
@EventBusSubscriber(modid="vandorlabs")
public final class ProgrammerRollbackChecks {
 static final NativeProgrammerItem[] ITEMS=new NativeProgrammerItem[2];private static BlockPos missing;private static boolean watch;private static final List<ItemStack> drops=new ArrayList<>();private static int checks;
 static boolean unavailable(BlockPos pos){return pos.equals(missing);}
 static NativeProgrammerItem item(NativeProgrammerBlock block,int mode){return ITEMS[mode]=new NativeProgrammerItem(block,new Item.Properties()){
  private BlockPos root;
  @Override protected boolean stage(Level level,BlockPos pos,BlockState state){int part=state.getValue(NativeProgrammerBlock.PART);if(part==0){root=pos.immutable();if(mode==0)missing=root;}boolean written=super.stage(level,pos,state);return part==(mode==0?0:3)?false:written;}
  @Override protected boolean restore(Level level,BlockPos pos,BlockState state){return pos.equals(root)?false:super.restore(level,pos,state);}
 };}
 private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Programmer rollback: "+why);}
 private static boolean capsule(ItemStack stack){return stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().contains("ProgrammerPlacementRecovery");}
 private static int plain(){return drops.stream().filter(stack->stack.is(ProgrammerHostChecks.block().asItem())&&!capsule(stack)).mapToInt(ItemStack::getCount).sum();}
 private static ItemStack failed(ServerLevel level,ServerPlayer actor,BlockPos root,int mode){level.getChunkAt(root);level.setBlock(root.below(),Blocks.STONE.defaultBlockState(),3);actor.setGameMode(GameType.SURVIVAL);actor.setPos(root.getX()+.5,root.getY(),root.getZ()-3);actor.setYRot(Direction.SOUTH.toYRot());var held=new ItemStack(ProgrammerHostChecks.block(),2);actor.setItemInHand(InteractionHand.MAIN_HAND,held);boolean caught=false;try{ITEMS[mode].place(new BlockPlaceContext(new UseOnContext(actor,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(root.getX()+.5,root.getY(),root.getZ()+.5),Direction.UP,root.below(),false))));}catch(IllegalStateException expected){caught=true;}need(caught&&level.getBlockState(root).is(ProgrammerHostChecks.block()),"failed rollback retains exact root state");if(mode==0)need(level.getBlockEntity(root)==null,"factory unavailable through rollback");else need(level.getBlockEntity(root) instanceof NativeProgrammerEntity owner&&owner.inert()&&owner.isEmpty(),"available failed owner becomes inert recovery");return held;}
 static void write(ServerLevel level,ServerPlayer actor,BlockPos base){for(int mode=0;mode<2;mode++){var root=base.east(mode*12);var held=failed(level,actor,root,mode);watch=true;drops.clear();level.destroyBlock(root,true);watch=false;need(held.getCount()+plain()==2,"exact usable block ownership after failed rollback mode="+mode+" source="+held.getCount()+" plainDrops="+plain());need(mode==0?drops.size()==1&&!capsule(drops.getFirst()):drops.size()==1&&capsule(drops.getFirst()),"one normal root or inert capsule");missing=null;}
  for(int mode=0;mode<2;mode++){var root=base.south(16).east(mode*12);var held=failed(level,actor,root,mode);var chest=root.east(4);level.setBlock(chest,Blocks.CHEST.defaultBlockState(),3);var inventory=(ChestBlockEntity)level.getBlockEntity(chest);inventory.setItem(0,held);actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);need(inventory.getItem(0).getCount()==(mode==0?1:2),"source stack transferred once to durable vanilla inventory");}
  System.out.println("[vandorlabs][programmer-rollback] write PASS checks="+checks);
 }
 static void load(ServerLevel level,BlockPos base){for(int mode=0;mode<2;mode++){var root=base.south(16).east(mode*12);for(var pos:new BlockPos[]{root,root.east(4)}){level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);level.getChunkAt(pos);}}}
 static void read(ServerLevel level,BlockPos base){for(int mode=0;mode<2;mode++){var root=base.south(16).east(mode*12);var inventory=(ChestBlockEntity)level.getBlockEntity(root.east(4));need(inventory!=null&&inventory.getItem(0).getCount()==(mode==0?1:2),"restart retains one source inventory");if(mode==1)need(level.getBlockEntity(root) instanceof NativeProgrammerEntity owner&&owner.inert(),"restart retains failed original inert snapshot");watch=true;drops.clear();level.destroyBlock(root,true);watch=false;need(inventory.getItem(0).getCount()+plain()==2,"restart mining cannot mint usable extra block mode="+mode);need(drops.size()==1&&(mode==0?!capsule(drops.getFirst()):capsule(drops.getFirst())),"restart yields exactly one root item or complete recovery capsule");}System.out.println("[vandorlabs][programmer-rollback] read PASS checks="+checks);}
 @SubscribeEvent public static void joined(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event){if(watch&&event.getLevel() instanceof ServerLevel&&event.getEntity() instanceof ItemEntity item)drops.add(item.getItem().copy());}
}
