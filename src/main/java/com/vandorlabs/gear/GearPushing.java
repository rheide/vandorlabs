package com.vandorlabs.gear;

import com.vandorlabs.vehicle.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Native, journalled one-block contacts; empty space never becomes a pushed attachment. */
final class GearPushing {
    static void preflight(GearTileEntity arm){
        if(!arm.pushBlocks()||!arm.canPushBlocks()||!arm.target()||arm.progress()!=0||arm.payload()==null||!arm.payload().materialized())return;
        World world=arm.getWorld();Set<BlockPos> own=arm.payload().placedCells(arm),originals=new HashSet<>();
        Map<BlockPos,VehicleStructure.Cell> virtual=new HashMap<>();
        int distance=(int)Math.ceil(Math.abs(arm.pose(0).footOffsetY()-arm.pose(1).footOffsetY()));
        if((long)own.size()*distance>ArmPayload.MAX_BLOCKS)throw new IllegalArgumentException("Push path exceeds the bounded arm scan");
        EntityPlayerMP actor=com.vandorlabs.tiles.WorldActionAuthorization.actor(arm);
        try(ArmTransactions.Scope scope=ArmTransactions.authorizing(arm)){
            // Simulate each contact in order. A block beyond a gap still needs room
            // for every remaining step, including a chain beyond the arm's tip.
            for(int step=1;step<=distance;step++){
                ArrayDeque<BlockPos> pending=new ArrayDeque<>();Map<BlockPos,VehicleStructure.Cell> pushed=new LinkedHashMap<>();
                for(BlockPos source:own)pending.add(source.offset(arm.mountDirection(),step));
                Set<BlockPos> visited=new HashSet<>();
                while(!pending.isEmpty()){
                    BlockPos at=pending.removeFirst();if(!visited.add(at)||arm.members().contains(at))continue;
                    if(!GearAssembly.loaded(world,at)||!world.getWorldBorder().contains(at))throw new IllegalArgumentException("Load the complete push path inside the world border");
                    VehicleStructure.Cell cell;
                    if(virtual.containsKey(at))cell=virtual.get(at);
                    else if(own.contains(at))cell=null;
                    else{IBlockState nativeState=world.getBlockState(at);cell=nativeState.getBlock().isAir(nativeState,world,at)?null:new VehicleStructure.Cell(at,nativeState,null);}
                    if(cell==null)continue;
                    IBlockState state=cell.state;
                    if(originals.add(cell.pos)){
                        if(originals.size()>12||state.getBlockHardness(world,cell.pos)<0||state.getBlock().getMobilityFlag(state)!=net.minecraft.block.material.EnumPushReaction.NORMAL||!state.isFullCube()||state.getBlock().hasTileEntity(state)||state.getMaterial().isLiquid())throw new IllegalArgumentException("The complete outward push path must contain at most twelve movable solid blocks");
                        if(actor==null)throw new IllegalArgumentException("Use the Configurizer before pushing blocks");
                        if(com.vandorlabs.redstone.TransmitterDrops.protectionProbe(new net.minecraftforge.event.world.BlockEvent.BreakEvent(world,cell.pos,state,actor)))throw new IllegalArgumentException("A protection rule prevented pushing the block");
                    }
                    VehicleService.permission(actor,at);VehicleService.permission(actor,at.offset(arm.mountDirection()));
                    pushed.put(at,cell);pending.add(at.offset(arm.mountDirection()));
                }
                for(BlockPos at:pushed.keySet())virtual.put(at,null);
                for(Map.Entry<BlockPos,VehicleStructure.Cell> entry:pushed.entrySet())virtual.put(entry.getKey().offset(arm.mountDirection()),entry.getValue());
            }
        }
    }
    static boolean prepare(GearTileEntity arm,double next){try(ArmTransactions.Scope scope=ArmTransactions.authorizing(arm)){return prepareAuthorized(arm,next);}}
    private static boolean prepareAuthorized(GearTileEntity arm,double next){
        if(!arm.pushBlocks()||!arm.canPushBlocks()||next<=arm.progress())return true;
        World world=arm.getWorld();Set<BlockPos> sources=new LinkedHashSet<>();
        List<AxisAlignedBB> before=GearGeometry.boxes(arm,arm.progress()),after=GearGeometry.boxes(arm,next);
        for(int index=0;index<after.size();index++){
            AxisAlignedBB box=after.get(index);if(index<before.size()&&box.equals(before.get(index)))continue;
            AxisAlignedBB inside=box.shrink(1e-6);
            if(!world.isAreaLoaded(new BlockPos(inside.minX,inside.minY,inside.minZ),new BlockPos(inside.maxX,inside.maxY,inside.maxZ),false))throw new IllegalArgumentException("Load the complete push path first");
            for(BlockPos p:BlockPos.getAllInBox(new BlockPos(inside.minX,inside.minY,inside.minZ),new BlockPos(inside.maxX,inside.maxY,inside.maxZ))){
                if(arm.members().contains(p)||world.isAirBlock(p))continue;
                List<AxisAlignedBB> collision=new ArrayList<>();IBlockState state=world.getBlockState(p);state.addCollisionBoxToList(world,p,inside,collision,null,false);
                if(!collision.isEmpty())sources.add(p.toImmutable());
            }
        }
        if(sources.isEmpty())return true;
        EntityPlayerMP actor=com.vandorlabs.tiles.WorldActionAuthorization.actor(arm);
        if(actor==null)throw new IllegalArgumentException("Use the Configurizer before pushing blocks");
        ArrayDeque<BlockPos> pending=new ArrayDeque<>(sources);Map<BlockPos,VehicleStructure.Cell> saved=new LinkedHashMap<>();
        while(!pending.isEmpty()){
            BlockPos at=pending.removeFirst();if(saved.containsKey(at))continue;
            if(!GearAssembly.loaded(world,at)||!world.getWorldBorder().contains(at))throw new IllegalArgumentException("Load the complete push path inside the world border");
            IBlockState state=world.getBlockState(at);if(state.getBlock().isAir(state,world,at))continue;
            if(saved.size()>=12||arm.members().contains(at)||state.getBlockHardness(world,at)<0||state.getBlock().getMobilityFlag(state)!=net.minecraft.block.material.EnumPushReaction.NORMAL||!state.isFullCube()||state.getBlock().hasTileEntity(state)||state.getMaterial().isLiquid())throw new IllegalArgumentException("Push up to twelve ordinary solid blocks; tile entities and immovable blocks stay protected");
            VehicleService.permission(actor,at);VehicleService.permission(actor,at.offset(arm.mountDirection()));
            if(com.vandorlabs.redstone.TransmitterDrops.protectionProbe(new net.minecraftforge.event.world.BlockEvent.BreakEvent(world,at,state,actor)))throw new IllegalArgumentException("A protection rule prevented pushing the block");
            saved.put(at,new VehicleStructure.Cell(at,state,null));pending.add(at.offset(arm.mountDirection()));
        }
        Set<BlockPos> changed=new LinkedHashSet<>(saved.keySet());for(BlockPos at:saved.keySet())changed.add(at.offset(arm.mountDirection()));
        try(ArmTransactions.Scope scope=ArmTransactions.authorizing(arm)){
            if(!ArmTransactions.ready(arm,"push",changed)){arm.awaitPayloadJournal();return false;}
            try{
                for(BlockPos at:saved.keySet()){world.removeTileEntity(at);if(!world.setBlockState(at,Blocks.AIR.getDefaultState(),2)&&!world.isAirBlock(at))throw new IllegalArgumentException("Push source could not be cleared");}
                for(VehicleStructure.Cell cell:saved.values()){BlockPos target=cell.pos.offset(arm.mountDirection());if(!world.setBlockState(target,cell.state,2)&&world.getBlockState(target)!=cell.state)throw new IllegalArgumentException("Push destination could not be installed");}
                ArmTransactions.applied(arm);arm.awaitPayloadJournal();return false;
            }catch(RuntimeException|LinkageError failure){ArmTransactions.failed(arm);throw failure;}
        }
    }
}
