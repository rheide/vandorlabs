package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import com.vandorlabs.persistence.StairState;
import com.vandorlabs.ui.SlabStairDialog;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.fluids.FluidStack;

/** Disposable real chunk-boundary pairs and a separate-process wet stale-join regression. */
public final class ShapeChunkRuntimeChecks {
    private static final BlockPos WET=new BlockPos(432,80,-32);
    public static final Direction[] FACINGS={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
    private ShapeChunkRuntimeChecks(){}
    public static BlockPos owner(int index){int offset=index>=4?32:0;return switch(index%4){case 0->new BlockPos(432,80,80+offset);case 1->new BlockPos(447,80,88+offset);case 2->new BlockPos(464,80,95+offset);default->new BlockPos(480,80,88+offset);};}
    public static BlockPos neighbor(int index){return owner(index).relative(FACINGS[index%4]);}
    public static BlockPos vanillaControl(int index){return vanillaControl(index,1);}
    private static BlockPos vanillaControl(int index,int distance){BlockPos neighbor=neighbor(index);Direction side=FACINGS[index%4].getClockWise();BlockPos control=neighbor.relative(side,distance);return new net.minecraft.world.level.ChunkPos(control).equals(new net.minecraft.world.level.ChunkPos(neighbor))?control:neighbor.relative(side.getOpposite(),distance);}
    private static void need(boolean b,String message){if(!b)throw new IllegalStateException("Shape chunk: "+message);}
    private static SlabStairDialog.State settings(int material,int tread){return new SlabStairDialog.State(material,material,1,false,new int[]{-1,-1,-1,-1,-1,-1},-1,new StairState(tread,0));}
    private static ProgrammableShapeEntity pair(ServerLevel level,BlockPos owner,Direction facing,boolean upper,int tread){
        BlockPos neighbor=owner.relative(facing);level.getChunkAt(owner);level.getChunkAt(neighbor);
        for(Direction side:Direction.values())level.setBlock(owner.relative(side),Blocks.AIR.defaultBlockState(),2);level.setBlock(owner,Blocks.AIR.defaultBlockState(),2);
        level.setBlock(owner.below(),Blocks.SMOOTH_STONE.defaultBlockState(),2);level.setBlock(neighbor.below(),Blocks.SMOOTH_STONE.defaultBlockState(),2);
        BlockState state=ShapeRegistration.STAIRS.get().defaultBlockState().setValue(StairBlock.FACING,facing).setValue(StairBlock.HALF,upper?Half.TOP:Half.BOTTOM);
        level.setBlock(owner,state,3);level.setBlock(neighbor,state.setValue(StairBlock.FACING,facing.getCounterClockWise()),3);
        var tile=(ProgrammableShapeEntity)level.getBlockEntity(owner);need(tile.applyShape(settings(18,tread)),"owner settings");need(((ProgrammableShapeEntity)level.getBlockEntity(neighbor)).applyShape(settings(19,8)),"neighbor settings");
        need(ShapeRegistration.STAIRS.get().connected(tile.getBlockState(),level,owner).getValue(StairBlock.SHAPE)==StairsShape.OUTER_LEFT,"actual outer join");return tile;
    }
    public static void clientFixture(ServerLevel level){
        for(int x=236;x<=486;x++)for(int z=-7;z<=-3;z++)floor(level,x,z);
        for(int center:new int[]{434,444,462,483})for(int x=center-2;x<=center+2;x++)for(int z=-7;z<=132;z++)floor(level,x,z);
        for(int i=0;i<8;i++){BlockPos retired=vanillaControl(i,2);if(level.getBlockState(retired).is(Blocks.LIME_CONCRETE))level.setBlock(retired,Blocks.AIR.defaultBlockState(),3);}
        for(int i=0;i<8;i++){var tile=pair(level,owner(i),FACINGS[i%4],i>=4,i%2==0?8:4);if(i==0)tile.containedFluids().set(0,new FluidStack(Fluids.WATER,1000));level.setBlock(vanillaControl(i),Blocks.LIME_CONCRETE.defaultBlockState(),3);}
    }
    private static void floor(ServerLevel level,int x,int z){BlockPos pos=new BlockPos(x,79,z);if(level.getBlockState(pos).isAir())level.setBlock(pos,Blocks.SMOOTH_STONE.defaultBlockState(),2);}
    public static void run(ServerLevel level){
        var tile=pair(level,WET,Direction.NORTH,false,4);tile.containedFluids().set(0,new FluidStack(Fluids.WATER,1000));
        var saved=tile.saveWithoutMetadata(level.registryAccess());saved.put("ChunkWetExpected",tile.containedFluids().raw(0));tile.loadWithComponents(saved,level.registryAccess());
        // Flags2 deliberately retain the saved derived join, just as loading a boundary chunk can.
        level.setBlock(WET.north(),Blocks.AIR.defaultBlockState(),2);tile.setChanged();
        need(tile.containedFluids().raw(0).equals(saved.getCompound("ChunkWetExpected")),"preparing stale join must retain the complete fluid compound");
        System.out.println("[vandorlabs][modern] shape-chunk-write PASS saved stale join with retained wet slot, deferred refresh prepared");
    }
    public static void prepareRead(ServerLevel level){level.getChunkAt(WET);level.getChunkAt(WET.north());}
    public static void read(ServerLevel level){
        need(level.getBlockEntity(WET) instanceof ProgrammableShapeEntity,"reloaded wet owner");var tile=(ProgrammableShapeEntity)level.getBlockEntity(WET);
        need(ShapeRegistration.STAIRS.get().connected(tile.getBlockState(),level,WET).getValue(StairBlock.SHAPE)==StairsShape.STRAIGHT,"reloaded missing-neighbor actual straight pose");
        need(tile.containedFluids().raw(0).equals(tile.saveWithoutMetadata(level.registryAccess()).getCompound("ChunkWetExpected")),"complete wet compound across separate process");
        complement(tile,tile.getBlockState().getCollisionShape(level,WET,CollisionContext.empty()));
        System.out.println("[vandorlabs][modern] shape-chunk-read PASS real deferred lifecycle, wet immutable volume/collision complement and exact fluid NBT after restart");
    }
    public static void complement(ProgrammableShapeEntity tile,net.minecraft.world.phys.shapes.VoxelShape shape){
        var volume=tile.fluidVolumeSnapshot();var boxes=shape.toAabbs();need(volume!=null,"published volume");
        for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++){double px=(x+.5)/16,py=(y+.5)/16,pz=(z+.5)/16;boolean solid=false;for(var box:boxes)if(box.contains(px,py,pz)){solid=true;break;}need((volume.sideAt(px,py,pz)>=0)!=solid,"actual collision/volume complement at "+x+","+y+","+z);}
    }
}
