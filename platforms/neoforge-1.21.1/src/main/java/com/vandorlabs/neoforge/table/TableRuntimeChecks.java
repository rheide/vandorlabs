package com.vandorlabs.neoforge.table;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.Shapes;

/** Opt-in installed connection, support and saved-state contracts. */
public final class TableRuntimeChecks {
    private static final BlockPos POS=new BlockPos(400,80,-32);
    public static final Direction[] FACES={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
    public static BlockPos gallery(int index){return new BlockPos(index/8*6,80,index%8*6);}
    public static net.minecraft.world.level.block.state.BlockState galleryState(int index){return TableRegistration.BLOCK.get().defaultBlockState().setValue(TableBlock.FACING,FACES[index/8]).setValue(TableBlock.UPSIDE_DOWN,index%8>=4);}
    /** Four pairs straddle the cardinal chunk edges, preserving the root in its original chunk. */
    public static BlockPos boundary(int index){return switch(index){case 0->new BlockPos(63,80,0);case 1->new BlockPos(96,80,0);case 2->new BlockPos(80,80,15);case 3->new BlockPos(80,80,32);case 4->new BlockPos(96,80,8);case 5->new BlockPos(63,80,8);case 6->new BlockPos(88,80,32);default->new BlockPos(88,80,15);};}
    public static Direction outward(int index){Direction face=FACES[index/2];return (index&1)==0?face.getClockWise():face.getCounterClockWise();}
    private TableRuntimeChecks(){}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Modern industrial table: "+message);}
    public static void run(ServerLevel level){
        exactAdapter();
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
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);var held=player.getMainHandItem();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WOODEN_PICKAXE));require(!player.hasCorrectToolForDrops(level.getBlockState(POS)),"wooden pickaxe harvest boundary");player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_PICKAXE));require(player.hasCorrectToolForDrops(level.getBlockState(POS)),"stone pickaxe harvest boundary");
        var drops=net.minecraft.world.level.block.Block.getDrops(level.getBlockState(POS),level,POS,null,player,player.getMainHandItem());require(drops.size()==1&&drops.get(0).is(TableRegistration.ITEM.get())&&!drops.get(0).has(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA)&&!drops.get(0).has(net.minecraft.core.component.DataComponents.CUSTOM_DATA),"plain table loot");
        require(block.getExplosionResistance()==6&&base.getDestroySpeed(level,POS)==5&&base.getMapColor(level,POS)==net.minecraft.world.level.material.MapColor.METAL,"canonical iron housing properties");
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);BlockPos placement=new BlockPos(408,80,-32);level.getChunkAt(placement);player.setPos(408.5,80,-35);
        for(Direction facing:FACES)for(Direction clicked:new Direction[]{Direction.UP,Direction.DOWN}){
            level.setBlock(placement,Blocks.AIR.defaultBlockState(),3);level.setBlock(placement.above(),Blocks.AIR.defaultBlockState(),3);level.setBlock(placement.below(),Blocks.AIR.defaultBlockState(),3);BlockPos support=placement.relative(clicked.getOpposite());level.setBlock(support,Blocks.STONE.defaultBlockState(),3);player.setYRot(facing.getOpposite().toYRot());var item=new net.minecraft.world.item.ItemStack(TableRegistration.ITEM.get());player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,item);
            var result=item.getItem().useOn(new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(support).add(clicked.getStepX()*.5,clicked.getStepY()*.5,clicked.getStepZ()*.5),clicked,support,false)));
            var placed=level.getBlockState(placement);require(result.consumesAction()&&placed.is(block)&&placed.getValue(TableBlock.FACING)==facing&&placed.getValue(TableBlock.UPSIDE_DOWN)==(clicked==Direction.DOWN),"actual item placement facing/inversion");
            for(var rotation:net.minecraft.world.level.block.Rotation.values()){var rotated=block.rotate(placed.setValue(TableBlock.LEFT,true).setValue(TableBlock.RIGHT,true),rotation);require(rotated.getValue(TableBlock.FACING)==rotation.rotate(facing)&&rotated.getValue(TableBlock.UPSIDE_DOWN)==placed.getValue(TableBlock.UPSIDE_DOWN)&&!rotated.getValue(TableBlock.LEFT)&&!rotated.getValue(TableBlock.RIGHT),"rotation must discard derived connections");}
        }
        level.setBlock(placement,Blocks.AIR.defaultBlockState(),3);level.setBlock(placement.above(),Blocks.AIR.defaultBlockState(),3);level.setBlock(placement.below(),Blocks.AIR.defaultBlockState(),3);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,held);
        System.out.println("[vandorlabs][modern] table-write PASS "+cases+" facing/inversion/connection states, native support, selection, collision and NBT roundtrip");
    }
    private static void exactAdapter(){
        var first=new net.minecraft.world.phys.AABB(0,0,0,.5,1,1);var second=new net.minecraft.world.phys.AABB(.50000003,0,0,1,1,1);
        var exact=com.vandorlabs.neoforge.geometry.ExactVoxelShapes.boxes(java.util.List.of(first,second));double middle=.500000015;
        require(exact.toAabbs().stream().noneMatch(box->box.contains(middle,.5,.5)),"exact adapter must retain sub-epsilon authored gap");
        var merged=Shapes.or(Shapes.box(0,0,0,.5,1,1),Shapes.box(.50000003,0,0,1,1,1));require(merged.toAabbs().stream().anyMatch(box->box.contains(middle,.5,.5)),"standard epsilon-union negative control must collapse gap");
        require(com.vandorlabs.neoforge.geometry.ExactVoxelShapes.boxes(java.util.List.of()).isEmpty(),"empty exact shape");
        java.util.List<net.minecraft.world.phys.AABB> dense=new java.util.ArrayList<>();for(int i=0;i<60;i++)dense.add(new net.minecraft.world.phys.AABB(i*2,i*2,i*2,i*2+1,i*2+1,i*2+1));
        try{com.vandorlabs.neoforge.geometry.ExactVoxelShapes.boxes(dense);throw new IllegalStateException("unbounded exact grid accepted");}catch(IllegalArgumentException expected){require(expected.getMessage().contains("exceeds"),"dense-grid guard reason");}
        try{com.vandorlabs.neoforge.geometry.ExactVoxelShapes.boxes(java.util.List.of(new net.minecraft.world.phys.AABB(Double.NaN,0,0,1,1,1)));throw new IllegalStateException("NaN geometry accepted");}catch(IllegalArgumentException expected){require(expected.getMessage().contains("Non-finite"),"non-finite guard reason");}
    }
    public static void prepareRead(ServerLevel level){level.getChunkAt(POS);}
    public static void read(ServerLevel level){var state=level.getBlockState(POS);require(state.is(TableRegistration.BLOCK.get())&&state.getValue(TableBlock.UPSIDE_DOWN)&&state.getValue(TableBlock.FACING)==Direction.NORTH,"saved block state");System.out.println("[vandorlabs][modern] table-read PASS native saved identity/facing/inversion");}
    public static void clientFixture(ServerLevel level){
        level.setDayTime(6000);level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,level.getServer());level.setWeatherParameters(100000,0,false,false);
        for(int x=-12;x<=110;x++)for(int z=-12;z<=50;z++){level.setBlock(new BlockPos(x,78,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);for(int y=79;y<=83;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        for(int i=0;i<32;i++){BlockPos pos=gallery(i);var state=galleryState(i);int mask=i%4;Direction face=state.getValue(TableBlock.FACING);level.setBlock(pos,state,3);if((mask&1)!=0)level.setBlock(pos.relative(face.getCounterClockWise()),state,3);if((mask&2)!=0)level.setBlock(pos.relative(face.getClockWise()),state,3);}
        for(int i=0;i<8;i++){BlockPos pos=boundary(i);Direction edge=outward(i);var state=TableRegistration.BLOCK.get().defaultBlockState().setValue(TableBlock.FACING,FACES[i/2]).setValue(TableBlock.UPSIDE_DOWN,(i&1)!=0);level.setBlock(pos,state,3);level.setBlock(pos.relative(edge),state,3);}
    }
}
