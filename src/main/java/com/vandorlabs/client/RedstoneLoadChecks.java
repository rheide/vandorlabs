package com.vandorlabs.client;

import com.vandorlabs.redstone.LoadedRedstonePower;
import com.vandorlabs.tiles.TileEntityRedstoneLight;
import com.vandorlabs.tiles.TileEntityProgrammableLight;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.HashMap;
import java.util.Map;

/** Fail deterministically on a neighbouring block read during onLoad or a read across an unloaded edge. */
final class RedstoneLoadChecks {
    static void run(World source) {
        GuardedWorld world = new GuardedWorld(source);
        BlockPos pos = new BlockPos(0,64,0);
        TileEntityRedstoneLight tile = new TileEntityRedstoneLight();
        tile.setWorld(world); tile.setPos(pos);
        world.states.put(pos,Block.REGISTRY.getObject(new net.minecraft.util.ResourceLocation("vandorlabs:rocket_thruster")).getDefaultState());
        tile.onLoad(); // Reading its own block is safe; neighbour reads and power callbacks must wait.
        world.allowReads=true;
        tile.update(); // First ordinary tick performs initialization safely.
        if (LoadedRedstonePower.isPowered(world,pos)) throw new IllegalStateException("unpowered loaded fixture");
        world.states.put(pos.east(),Blocks.REDSTONE_BLOCK.getDefaultState());
        if (!LoadedRedstonePower.isPowered(world,pos)) throw new IllegalStateException("loaded direct redstone ignored");
        world.states.clear(); world.states.put(pos.east(),Blocks.STONE.getDefaultState());
        world.states.put(pos.east().up(),Blocks.LEVER.getDefaultState()
                .withProperty(net.minecraft.block.BlockLever.POWERED,true)
                .withProperty(net.minecraft.block.BlockLever.FACING,net.minecraft.block.BlockLever.EnumOrientation.UP_X));
        if (!LoadedRedstonePower.isPowered(world,pos)) throw new IllegalStateException("loaded strong power ignored");
        world.states.clear();
        world.states.put(pos,Block.REGISTRY.getObject(new net.minecraft.util.ResourceLocation(
                "vandorlabs:programmable_light")).getDefaultState());
        world.allowReads=false;
        TileEntityProgrammableLight programmable = new TileEntityProgrammableLight();
        programmable.setWorld(world); programmable.setPos(pos);
        net.minecraft.nbt.NBTTagCompound saved = programmable.writeToNBT(new net.minecraft.nbt.NBTTagCompound());
        saved.setInteger("RedstoneChannel", 1);
        programmable.readFromNBT(saved);
        programmable.onLoad(); // Joining and channel registration must wait until chunk tile iteration ends.
        System.out.println("[vandorlabs][reprolab] redstone-load-safety PASS");
    }

    private static final class GuardedWorld extends World {
        final Map<BlockPos,IBlockState> states=new HashMap<>();
        boolean allowReads;
        GuardedWorld(World source) {
            super(source.getSaveHandler(),new net.minecraft.world.storage.WorldInfo(source.getWorldInfo()),
                    new net.minecraft.world.WorldProviderSurface(),new net.minecraft.profiler.Profiler(),false);
        }
        @Override protected net.minecraft.world.chunk.IChunkProvider createChunkProvider(){return null;}
        @Override protected boolean isChunkLoaded(int x,int z,boolean allowEmpty){return x==0&&z==0;}
        @Override public IBlockState getBlockState(BlockPos pos) {
            if(!allowReads && !pos.equals(new BlockPos(0,64,0)))throw new IllegalStateException("onLoad read neighbour during chunk tile iteration");
            if(!isBlockLoaded(pos))throw new IllegalStateException("redstone read unloaded chunk at "+pos);
            return states.getOrDefault(pos,Blocks.AIR.getDefaultState());
        }
        @Override public void markChunkDirty(BlockPos pos,TileEntity tile) { }
        @Override public boolean checkLightFor(net.minecraft.world.EnumSkyBlock type,BlockPos pos){return true;}
    }
}
