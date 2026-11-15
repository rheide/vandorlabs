package com.vandorlabs.neoforge.table;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.Shapes;

/** Opt-in installed connection, support and saved-state contracts. */
@net.neoforged.fml.common.EventBusSubscriber(modid="vandorlabs")
public final class TableRuntimeChecks {
    @net.neoforged.bus.api.SubscribeEvent public static void started(net.neoforged.neoforge.event.server.ServerStartedEvent event){
        String phase=System.getProperty("vandorlabs.tablePhase","");if(phase.isEmpty())return;
        var world=event.getServer().overworld();
        try{if(phase.equals("write"))run(world);else if(phase.equals("read"))read(world);else throw new IllegalArgumentException("Unknown table phase");
            event.getServer().saveEverything(true,true,true);
        }catch(Throwable failure){failure.printStackTrace();System.out.println("[vandorlabs][modern] table-"+phase+" FAIL "+failure);}
        event.getServer().halt(false);
    }
    private static final BlockPos POS=new BlockPos(144,80,8);
    public static final Direction[] FACES={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
    public static BlockPos gallery(int index){return new BlockPos(index/8*6,80,index%8*6);}
    public static net.minecraft.world.level.block.state.BlockState galleryState(int index){return TableBindings.BLOCK.defaultBlockState().setValue(TableBlock.FACING,FACES[index/8]).setValue(TableBlock.UPSIDE_DOWN,index%8>=4);}
    /** Four pairs straddle the cardinal chunk edges, preserving the root in its original chunk. */
    public static BlockPos boundary(int index){return switch(index){case 0->new BlockPos(63,80,0);case 1->new BlockPos(96,80,0);case 2->new BlockPos(80,80,15);case 3->new BlockPos(80,80,32);case 4->new BlockPos(96,80,8);case 5->new BlockPos(63,80,8);case 6->new BlockPos(88,80,32);default->new BlockPos(88,80,15);};}
    public static Direction outward(int index){Direction face=FACES[index/2];return (index&1)==0?face.getClockWise():face.getCounterClockWise();}
    private TableRuntimeChecks(){}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Modern industrial table: "+message);}
    public static void run(ServerLevel level){

        level.getChunkAt(POS);var block=TableBindings.BLOCK;int cases=0;
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
        var drops=net.minecraft.world.level.block.Block.getDrops(level.getBlockState(POS),level,POS,null,player,player.getMainHandItem());require(drops.size()==1&&drops.get(0).is(TableBindings.ITEM)&&!drops.get(0).has(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA)&&!drops.get(0).has(net.minecraft.core.component.DataComponents.CUSTOM_DATA),"plain table loot");
        require(block.getExplosionResistance()==6&&base.getDestroySpeed(level,POS)==5&&base.getMapColor(level,POS)==net.minecraft.world.level.material.MapColor.METAL,"canonical iron housing properties");
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);BlockPos placement=new BlockPos(152,80,8);level.getChunkAt(placement);player.setPos(152.5,80,5);
        for(Direction facing:FACES)for(Direction clicked:new Direction[]{Direction.UP,Direction.DOWN}){
            level.setBlock(placement,Blocks.AIR.defaultBlockState(),3);level.setBlock(placement.above(),Blocks.AIR.defaultBlockState(),3);level.setBlock(placement.below(),Blocks.AIR.defaultBlockState(),3);BlockPos support=placement.relative(clicked.getOpposite());level.setBlock(support,Blocks.STONE.defaultBlockState(),3);player.setYRot(facing.getOpposite().toYRot());var item=new net.minecraft.world.item.ItemStack(TableBindings.ITEM);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,item);
            var result=item.getItem().useOn(new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(support).add(clicked.getStepX()*.5,clicked.getStepY()*.5,clicked.getStepZ()*.5),clicked,support,false)));
            var placed=level.getBlockState(placement);require(result.consumesAction()&&placed.is(block)&&placed.getValue(TableBlock.FACING)==facing&&placed.getValue(TableBlock.UPSIDE_DOWN)==(clicked==Direction.DOWN),"actual item placement facing/inversion");
            for(var rotation:net.minecraft.world.level.block.Rotation.values()){var rotated=block.rotate(placed.setValue(TableBlock.LEFT,true).setValue(TableBlock.RIGHT,true),rotation);require(rotated.getValue(TableBlock.FACING)==rotation.rotate(facing)&&rotated.getValue(TableBlock.UPSIDE_DOWN)==placed.getValue(TableBlock.UPSIDE_DOWN)&&!rotated.getValue(TableBlock.LEFT)&&!rotated.getValue(TableBlock.RIGHT),"rotation must discard derived connections");}
        }
        level.setBlock(placement,Blocks.AIR.defaultBlockState(),3);level.setBlock(placement.above(),Blocks.AIR.defaultBlockState(),3);level.setBlock(placement.below(),Blocks.AIR.defaultBlockState(),3);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,held);
        System.out.println("[vandorlabs][modern] table-write PASS "+cases+" facing/inversion/connection states, native support, selection, collision and NBT roundtrip");
    }
    public static void prepareRead(ServerLevel level){level.getChunkAt(POS);}
    public static void read(ServerLevel level){var state=level.getBlockState(POS);require(state.is(TableBindings.BLOCK)&&state.getValue(TableBlock.UPSIDE_DOWN)&&state.getValue(TableBlock.FACING)==Direction.NORTH,"saved block state");System.out.println("[vandorlabs][modern] table-read PASS native saved identity/facing/inversion");}
}
