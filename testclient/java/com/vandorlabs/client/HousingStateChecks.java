package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.property.IExtendedBlockState;
import java.util.*;

/** Extended-state compatibility against Forge's sequential property updates. */
final class HousingStateChecks {
    static void run() {
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
        BlockPos pos=new BlockPos(8,100,8);int cases=0;
        for(Block block:new Block[]{ModBlocks.PROGRAMMABLE_BLOCK,ModBlocks.PROGRAMMABLE_SLAB,new BlockProgrammableStorage(),new BlockProgrammableStairs()}) {
            for(IBlockState state:block.getBlockState().getValidStates())for(int settings=0;settings<4;settings++) {
                world.setBlockState(pos,state,2);
                TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);
                tile.setHousingTexture(settings+1);tile.setSideTexture(settings==0?-1:settings+5);
                tile.setSlabTileSides((settings&1)!=0);
                tile.setFaceTextures(new FaceTextures(settings>1,new int[]{-1,1,2,-1,4,5}));
                IExtendedBlockState actual=(IExtendedBlockState)block.getExtendedState(state,world,pos);
                IExtendedBlockState reference=((IExtendedBlockState)state)
                        .withProperty(ProgrammableHousingState.FINISH,tile.getHousingTexture())
                        .withProperty(ProgrammableHousingState.SIDE_FINISH,tile.getSideTexture())
                        .withProperty(ProgrammableHousingState.FACES,tile.getFaceTextures())
                        .withProperty(ProgrammableHousingState.TILE_SIDES,tile.isSlabTileSides()?1:0);
                if(actual.getUnlistedNames().contains(ProgrammableHousingState.VISIBLE))reference=(IExtendedBlockState)
                        ReferenceHousingState.extend(state,world,pos,block instanceof BlockProgrammableSlab);
                require(block.getExtendedState(actual,world,pos)==actual,"unchanged sampled state lost identity");
                direct(reference,actual);
                same(reference,actual);
                require(actual.getClean()==state,"extended state lost canonical clean state");
                for(IProperty<?> property:state.getPropertyKeys())transitions(property,reference,actual);
                same(reference.withProperty(ProgrammableHousingState.SIDE_FINISH,15),actual.withProperty(ProgrammableHousingState.SIDE_FINISH,15));
                // Construction must not modify the canonical blockstate's optionals.
                for(Optional<?> value:((IExtendedBlockState)state).getUnlistedProperties().values())require(!value.isPresent(),"mutated shared clean state");
                cases++;
            }
            concurrentReads(block,world,pos);
            externalProperties(block,world,pos);
            world.clear();
        }
        System.out.println("PASS: "+cases+" housing snapshots match Forge direct/map reads, transitions, clean states, external properties and concurrent publication");
    }
    private static void direct(IExtendedBlockState expected,IExtendedBlockState actual) {
        // These reads happen before asking for the complete unlisted map.
        for(net.minecraftforge.common.property.IUnlistedProperty<?> property:expected.getUnlistedNames()) {
            require(Objects.equals(expected.getValue(property),actual.getValue(property)),"direct unlisted read differs");
            noOp(property,actual);
        }
        require(actual.getUnlistedNames().equals(expected.getUnlistedNames()),"unlisted names differ");
        net.minecraftforge.common.property.IUnlistedProperty<Integer> missing=ProgrammableHousingState.integer("absent_check");
        sameFailure(()->expected.getValue(missing),()->actual.getValue(missing));
        sameFailure(()->expected.withProperty(missing,4),()->actual.withProperty(missing,4));
        sameFailure(()->expected.withProperty(ProgrammableHousingState.FINISH,null),()->actual.withProperty(ProgrammableHousingState.FINISH,null));
    }
    private static <V> void noOp(net.minecraftforge.common.property.IUnlistedProperty<V> property,IExtendedBlockState state) {
        require(state.withProperty(property,state.getValue(property))==state,"no-op unlisted update changed identity");
    }
    private static void externalProperties(Block block,NonRenderingChecks.MemoryWorld world,BlockPos pos) {
        IBlockState canonical=block.getDefaultState();
        java.util.List<net.minecraftforge.common.property.IUnlistedProperty<?>> keys=new ArrayList<>(((IExtendedBlockState)canonical).getUnlistedNames());
        net.minecraftforge.common.property.IUnlistedProperty<String> extra=new net.minecraftforge.common.property.IUnlistedProperty<String>() {
            public String getName(){return "external_check";}
            public boolean isValid(String value){return true;}
            public Class<String> getType(){return String.class;}
            public String valueToString(String value){return value;}
        };
        keys.add(extra);
        net.minecraftforge.common.property.ExtendedBlockState container=new net.minecraftforge.common.property.ExtendedBlockState(block,
                canonical.getPropertyKeys().toArray(new IProperty<?>[0]),keys.toArray(new net.minecraftforge.common.property.IUnlistedProperty<?>[0]));
        IExtendedBlockState source=((IExtendedBlockState)container.getBaseState()).withProperty(extra,"preserved");
        world.setBlockState(pos,source,2);
        IExtendedBlockState actual=(IExtendedBlockState)block.getExtendedState(source,world,pos);
        require("preserved".equals(actual.getValue(extra)),"external unlisted value was discarded");
        require("changed".equals(actual.withProperty(extra,"changed").getValue(extra)),"external property transition failed");
        require(actual.withProperty(extra,null).getValue(extra)==null,"external nullable property transition failed");
        net.minecraftforge.common.property.IUnlistedProperty<Integer> missing=ProgrammableHousingState.integer("missing");
        sameFailure(()->source.getValue(missing),()->actual.getValue(missing));
    }
    private static void concurrentReads(Block block,NonRenderingChecks.MemoryWorld world,BlockPos pos) {
        IExtendedBlockState expected=(IExtendedBlockState)block.getExtendedState(block.getDefaultState(),world,pos);
        Map<?,?> values=expected.getUnlistedProperties();
        IExtendedBlockState shared=(IExtendedBlockState)block.getExtendedState(block.getDefaultState(),world,pos);
        java.util.concurrent.CountDownLatch start=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Object> published=new java.util.concurrent.atomic.AtomicReference<>();
        Thread[] readers=new Thread[8];
        for(int i=0;i<readers.length;i++) {
            readers[i]=new Thread(()->{
                try {
                    start.await();
                    for(int j=0;j<100;j++) {
                        for(net.minecraftforge.common.property.IUnlistedProperty<?> property:expected.getUnlistedNames())
                            require(Objects.equals(expected.getValue(property),shared.getValue(property)),"concurrent direct read changed");
                        Object map=shared.getUnlistedProperties();published.compareAndSet(null,map);
                        require(map==published.get() && values.equals(map),"concurrent map publication differs");
                    }
                } catch(Throwable error){failure.compareAndSet(null,error);}
            },"housing-state-check");readers[i].start();
        }
        start.countDown();
        try{for(Thread reader:readers)reader.join();}
        catch(InterruptedException error){Thread.currentThread().interrupt();throw new AssertionError(error);}
        if(failure.get()!=null)throw new AssertionError("concurrent housing state read",failure.get());
    }
    private static void sameFailure(Runnable expected,Runnable actual) {
        RuntimeException before=failure(expected),after=failure(actual);
        require(before!=null && after!=null && before.getClass()==after.getClass()
                && Objects.equals(before.getMessage(),after.getMessage()),"Forge exception behavior changed");
    }
    private static RuntimeException failure(Runnable action) {
        try{action.run();return null;}catch(RuntimeException expected){return expected;}
    }
    private static <T extends Comparable<T>> void transitions(IProperty<T> property,IExtendedBlockState expected,IExtendedBlockState actual) {
        for(T value:property.getAllowedValues()) {
            IExtendedBlockState before=(IExtendedBlockState)expected.withProperty(property,value);
            IExtendedBlockState after=(IExtendedBlockState)actual.withProperty(property,value);
            same(before,after);
            require((before==expected)==(after==actual),"listed update changed Forge identity behavior");
            for(T again:property.getAllowedValues()) {
                IExtendedBlockState nextBefore=(IExtendedBlockState)before.withProperty(property,again);
                IExtendedBlockState nextAfter=(IExtendedBlockState)after.withProperty(property,again);
                same(nextBefore,nextAfter);
                require((nextBefore==before)==(nextAfter==after),"repeated listed update changed Forge identity behavior");
            }
        }
    }
    private static void same(IExtendedBlockState expected,IExtendedBlockState actual) {
        require(expected.getBlock()==actual.getBlock() && expected.getProperties().equals(actual.getProperties()),"listed properties changed");
        require(expected.getUnlistedProperties().equals(actual.getUnlistedProperties()),"unlisted properties changed");
        require(expected.getClean()==actual.getClean(),"clean state changed");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
