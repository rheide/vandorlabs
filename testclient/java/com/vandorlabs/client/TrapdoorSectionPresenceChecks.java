package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TrapdoorSectionPresence;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.chunk.*;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import java.util.*;

/** Live palette mutation, resizing, conservative fallbacks and loaded-only section boundaries. */
final class TrapdoorSectionPresenceChecks {
    static void run() {
        net.minecraft.world.chunk.BlockStateContainer data=new net.minecraft.world.chunk.BlockStateContainer();
        require(!TrapdoorSectionPresence.mayContain(data),"air palette was not rejected");
        data.set(0,0,0,Blocks.STONE.getDefaultState());require(!TrapdoorSectionPresence.mayContain(data),"stone palette was not rejected");
        data.set(1,0,0,ModBlocks.PROGRAMMABLE_TRAPDOOR.getDefaultState());
        require(TrapdoorSectionPresence.mayContain(data),"new owner was missed before tile loading");
        data.set(1,0,0,Blocks.AIR.getDefaultState());
        require(TrapdoorSectionPresence.mayContain(data),"unused palette entry must remain a conservative positive");
        List<IBlockState> states=new ArrayList<>();
        for(Block block:Block.REGISTRY)for(IBlockState state:block.getBlockState().getValidStates())
            if(!(block instanceof BlockProgrammableTrapdoor))states.add(state);
        int checks=0;
        for(int count:new int[]{0,15,16,31,32,63,64,127,128,255,256,300}) {
            data=new net.minecraft.world.chunk.BlockStateContainer();
            for(int i=0;i<count;i++)data.set(i&15,(i>>8)&15,(i>>4)&15,states.get(i));
            require(TrapdoorSectionPresence.mayContain(data)==(count>256),"ordinary palette rejection/global fallback changed at "+count);
            for(IBlockState owner:new IBlockState[]{ModBlocks.PROGRAMMABLE_TRAPDOOR.getDefaultState(),ModBlocks.PROGRAMMABLE_DIAGONAL_TRAPDOOR.getDefaultState()}) {
                data.set(15,15,15,owner);
                require(TrapdoorSectionPresence.mayContain(data),"palette growth hid an owner at "+count);checks++;
            }
        }
        require(TrapdoorSectionPresence.mayContain(new net.minecraft.world.chunk.BlockStateContainer(){}),"custom storage was trusted");
        try {
            java.lang.reflect.Field palette=net.minecraft.world.chunk.BlockStateContainer.class.getDeclaredField("palette");palette.setAccessible(true);
            data=new net.minecraft.world.chunk.BlockStateContainer();
            palette.set(data,new BlockStatePaletteLinear(4,data){});
            require(TrapdoorSectionPresence.mayContain(data),"custom palette was trusted");
            // A malformed null slot must not terminate the scan before a later owner.
            BlockStatePaletteLinear linear=new BlockStatePaletteLinear(4,data);
            linear.idFor(Blocks.AIR.getDefaultState());linear.idFor(null);linear.idFor(ModBlocks.PROGRAMMABLE_TRAPDOOR.getDefaultState());
            palette.set(data,linear);require(TrapdoorSectionPresence.mayContain(data),"null palette slot hid a later owner");
        } catch(ReflectiveOperationException error){throw new AssertionError(error);}
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
        LoadedProvider provider=new LoadedProvider();
        for(int x=-1;x<=0;x++)for(int z=-1;z<=0;z++)provider.chunks.put(key(x,z),new Chunk(world,x,z));
        require(!TrapdoorSectionPresence.mayContainLoaded(provider,-2,14,-2,2,18,2),"empty loaded sections were not rejected");
        require(provider.lookups==4,"section query requested unexpected chunks");
        for(int x=-1;x<=0;x++)for(int z=-1;z<=0;z++)for(int y=0;y<2;y++) {
            Chunk chunk=provider.chunks.get(key(x,z));ExtendedBlockStorage storage=new ExtendedBlockStorage(y*16,true);
            storage.set(0,0,0,ModBlocks.PROGRAMMABLE_TRAPDOOR.getDefaultState());chunk.getBlockStorageArray()[y]=storage;
            require(TrapdoorSectionPresence.mayContainLoaded(provider,-2,14,-2,2,18,2),"boundary section missed");checks++;
            chunk.getBlockStorageArray()[y]=null;
        }
        Chunk previous=provider.chunks.remove(key(0,0));
        require(!TrapdoorSectionPresence.mayContainLoaded(provider,-2,14,-2,2,18,2),"unloaded chunk was used");
        ExtendedBlockStorage replacement=new ExtendedBlockStorage(16,true);replacement.set(0,0,0,ModBlocks.PROGRAMMABLE_TRAPDOOR.getDefaultState());
        previous.getBlockStorageArray()[1]=replacement;provider.chunks.put(key(0,0),previous);
        require(TrapdoorSectionPresence.mayContainLoaded(provider,-2,14,-2,2,18,2),"reloaded chunk reused a negative result");
        require(!TrapdoorSectionPresence.mayContainLoaded(provider,0,256,0,4,260,4),"outside-height query inspected terrain");
        require(TrapdoorSectionPresence.mayContainLoaded(provider,-256,0,-256,256,255,256),"large query did not fall back");
        require(TrapdoorSectionPresence.mayContain(world,0,0,0,4,4,4),"custom world state provider was bypassed");
        System.out.println("PASS: "+checks+" trapdoor palette resize/boundary cases, live insertion/removal, null slots, custom fallbacks and loaded-only chunk access");
    }
    private static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    private static final class LoadedProvider implements IChunkProvider {
        final Map<Long,Chunk> chunks=new HashMap<>();int lookups;
        public Chunk getLoadedChunk(int x,int z){lookups++;return chunks.get(key(x,z));}
        public Chunk provideChunk(int x,int z){throw new AssertionError("presence query loaded a chunk");}
        public boolean tick(){return false;}
        public String makeString(){return "trapdoor presence check";}
        public boolean isChunkGeneratedAt(int x,int z){throw new AssertionError("presence query inspected terrain generation");}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
