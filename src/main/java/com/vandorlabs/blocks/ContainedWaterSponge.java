package com.vandorlabs.blocks;

import com.vandorlabs.world.WorldListener;
import java.util.*;
import net.minecraft.block.BlockSponge;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Extend a vanilla sponge's bounded flood search through stored water without removing its block. */
@Mod.EventBusSubscriber(modid="vandorlabs")
public final class ContainedWaterSponge {
    private ContainedWaterSponge(){}
    private static final Map<World,Observer> OBSERVERS=new WeakHashMap<>();
    @SubscribeEvent public static void load(WorldEvent.Load event){if(!event.getWorld().isRemote){Observer observer=new Observer();OBSERVERS.put(event.getWorld(),observer);event.getWorld().addEventListener(observer);}}
    @SubscribeEvent public static void unload(WorldEvent.Unload event){Observer observer=OBSERVERS.remove(event.getWorld());if(observer!=null)event.getWorld().removeEventListener(observer);}
    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST)
    public static void placed(net.minecraftforge.event.world.BlockEvent.PlaceEvent event){
        Observer observer=OBSERVERS.get(event.getWorld());if(observer!=null&&dry(event.getWorld().getBlockState(event.getPos())))observer.absorb(event.getWorld(),event.getPos(),false);
    }
    @SubscribeEvent public static void neighbors(net.minecraftforge.event.world.BlockEvent.NeighborNotifyEvent event){
        Observer observer=OBSERVERS.get(event.getWorld());if(observer==null||observer.absorbing)return;
        if(dry(event.getWorld().getBlockState(event.getPos())))observer.absorb(event.getWorld(),event.getPos(),false);
        if(WaterloggedParts.supports(event.getState()))for(EnumFacing face:event.getNotifiedSides()){BlockPos next=event.getPos().offset(face);if(event.getWorld().isBlockLoaded(next)&&dry(event.getWorld().getBlockState(next)))observer.absorb(event.getWorld(),next,false);}
    }
    private static boolean water(IBlockState state){return state.getBlock()==Blocks.WATER||state.getBlock()==Blocks.FLOWING_WATER;}
    private static boolean dry(IBlockState state){return state.getBlock()==Blocks.SPONGE&&!state.getValue(BlockSponge.WET);}
    private static final class Observer extends WorldListener {
        // Vanilla removes native water before changing the sponge to wet. Retain those
        // cells for this tick so its search can continue into contained water beyond them.
        final Set<BlockPos> removed=new HashSet<>();long tick=Long.MIN_VALUE;boolean absorbing;
        @Override public void notifyBlockUpdate(World world,BlockPos pos,IBlockState before,IBlockState after,int flags){
            if(absorbing||world.captureBlockSnapshots)return;
            if(tick!=world.getTotalWorldTime()){removed.clear();tick=world.getTotalWorldTime();}
            if(water(before)&&after.getBlock()==Blocks.AIR){if(removed.size()<4096)removed.add(pos.toImmutable());}
            else if(after.getBlock()!=Blocks.AIR)removed.remove(pos);
            // Placement notifications can carry the pre-onBlockAdded dry state after
            // vanilla has already made the sponge wet. Never absorb a second time.
            IBlockState current=world.getBlockState(pos);
            if(dry(current)||dry(before)&&current.getBlock()==Blocks.SPONGE){absorb(world,pos,!dry(current));return;}
            // Filling a supported block next to an existing dry sponge must activate it too.
            if(WaterloggedParts.supports(after))for(EnumFacing face:EnumFacing.values()){
                BlockPos next=pos.offset(face);if(world.isBlockLoaded(next)&&dry(world.getBlockState(next)))absorb(world,next,false);
            }
        }
        private void absorb(World world,BlockPos sponge,boolean vanillaWet){
            absorbing=true;
            try{
                ArrayDeque<BlockPos> queue=new ArrayDeque<>();ArrayDeque<Integer> depths=new ArrayDeque<>();Set<BlockPos> visited=new HashSet<>();List<BlockPos> drained=new ArrayList<>();
                queue.add(sponge);depths.add(0);visited.add(sponge);int count=0;boolean contained=false;
                while(!queue.isEmpty()&&count<64){BlockPos at=queue.remove();int depth=depths.remove();
                    for(EnumFacing face:EnumFacing.values()){
                        BlockPos next=at.offset(face);if(!visited.add(next)||!world.isBlockLoaded(next))continue;IBlockState state=world.getBlockState(next);
                        boolean bridge=vanillaWet&&state.getBlock()==Blocks.AIR&&removed.contains(next),wet=water(state);
                        ContainedFluids data=WaterloggedParts.supports(state)?WaterloggedParts.data(world.getTileEntity(next)):null;
                        boolean stored=false;if(data!=null)for(int side=0;side<2;side++){FluidStack fluid=data.get(side);if(fluid!=null&&fluid.getFluid()==FluidRegistry.WATER){data.set(side,null);stored=true;}}
                        if(!bridge&&!wet&&!stored)continue;
                        if(wet){world.setBlockState(next,Blocks.AIR.getDefaultState(),2);drained.add(next);}contained|=stored;
                        count++;if(depth<6){queue.add(next);depths.add(depth+1);}if(count>=64)break;
                    }
                }
                if(contained||!drained.isEmpty()){
                    if(dry(world.getBlockState(sponge)))world.setBlockState(sponge,Blocks.SPONGE.getDefaultState().withProperty(BlockSponge.WET,true),2);
                    for(BlockPos pos:drained)world.notifyNeighborsOfStateChange(pos,Blocks.AIR,false);
                    if(!vanillaWet)world.playEvent(2001,sponge,net.minecraft.block.Block.getStateId(Blocks.WATER.getDefaultState()));
                }
            }finally{absorbing=false;}
        }
    }
}
