package com.vandorlabs.neoforge.table;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.Shapes;

/** Opt-in installed connection, support and saved-state contracts. */
public final class TableRuntimeChecks {
    private static final BlockPos POS=new BlockPos(144,80,8);
    private TableRuntimeChecks(){}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Modern industrial table: "+message);}
    public static void run(ServerLevel level){
        level.getChunkAt(POS);var block=TableRegistration.BLOCK.get();int cases=0;
        for(Direction face:Direction.Plane.HORIZONTAL)for(boolean inverted:new boolean[]{false,true})for(int mask=0;mask<4;mask++){
            for(Direction side:Direction.Plane.HORIZONTAL)level.setBlock(POS.relative(side),Blocks.AIR.defaultBlockState(),3);
            var state=block.defaultBlockState().setValue(TableBlock.FACING,face).setValue(TableBlock.UPSIDE_DOWN,inverted);level.setBlock(POS,state,3);
            if((mask&1)!=0)level.setBlock(POS.relative(face.getCounterClockWise()),state,3);if((mask&2)!=0)level.setBlock(POS.relative(face.getClockWise()),state,3);
            var actual=block.actual(level.getBlockState(POS),level,POS);require(actual.getValue(TableBlock.LEFT)==((mask&1)!=0)&&actual.getValue(TableBlock.RIGHT)==((mask&2)!=0),"derived matching neighbours");
            require(level.getBlockState(POS).getShape(level,POS).bounds().equals(Shapes.block().bounds()),"legacy full-cell selection");
            for(Direction side:Direction.values())require(actual.isFaceSturdy(level,POS,side)==(side==(inverted?Direction.DOWN:Direction.UP)),"single tabletop support face "+side);
            require(actual.getLightBlock(level,POS)==0&&actual.getLightEmission(level,POS)==0&&!actual.canOcclude(),"transparent unlit housing");
            require(!actual.getCollisionShape(level,POS).isEmpty()&&level.getBlockEntity(POS)==null,"native multipart collision without block entity");
            var tag=net.minecraft.nbt.NbtUtils.writeBlockState(actual);var loaded=net.minecraft.nbt.NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),tag);require(loaded==actual,"native block-state NBT roundtrip");cases++;
        }
        // A neighbour of another inversion or facing cannot suppress the end strut.
        var base=block.defaultBlockState();level.setBlock(POS,base,3);level.setBlock(POS.west(),base.setValue(TableBlock.UPSIDE_DOWN,true),3);level.setBlock(POS.east(),base.setValue(TableBlock.FACING,Direction.EAST),3);require(!block.actual(base,level,POS).getValue(TableBlock.LEFT)&&!block.actual(base,level,POS).getValue(TableBlock.RIGHT),"incompatible neighbour exclusion");
        for(Direction side:Direction.Plane.HORIZONTAL)level.setBlock(POS.relative(side),Blocks.AIR.defaultBlockState(),3);level.setBlock(POS,base.setValue(TableBlock.UPSIDE_DOWN,true),3);
        System.out.println("[vandorlabs][modern] table-write PASS "+cases+" facing/inversion/connection states, native support, selection, collision and NBT roundtrip");
    }
    public static void prepareRead(ServerLevel level){level.getChunkAt(POS);}
    public static void read(ServerLevel level){var state=level.getBlockState(POS);require(state.is(TableRegistration.BLOCK.get())&&state.getValue(TableBlock.UPSIDE_DOWN)&&state.getValue(TableBlock.FACING)==Direction.NORTH,"saved block state");System.out.println("[vandorlabs][modern] table-read PASS native saved identity/facing/inversion");}
}
