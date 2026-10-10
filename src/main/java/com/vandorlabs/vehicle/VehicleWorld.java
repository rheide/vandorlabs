package com.vandorlabs.vehicle;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.chunk.*;
import net.minecraft.world.storage.WorldInfo;
import java.util.*;

/** Read-only local world for collision, extended block states and existing tile renderers.
 * Tiles never tick or register with the real world's redstone networks. */
public final class VehicleWorld extends World {
    public VehicleStructure structure;
    public final World parent;
    public final Map<BlockPos,TileEntity> tiles=new LinkedHashMap<>();
    private final Map<Long,Chunk> chunks=new HashMap<>();
    public BlockPos origin=BlockPos.ORIGIN;
    public int propulsionLevel;
    public static boolean isPropulsion(net.minecraft.block.Block block) {
        if(block instanceof com.vandorlabs.blocks.BlockPropulsionLight)return true;
        if(!(block instanceof com.vandorlabs.blocks.BlockShipSystem))return false;
        String kind=((com.vandorlabs.blocks.BlockShipSystem)block).kind;
        return kind.contains("drive") || kind.contains("engine") || kind.contains("thruster");
    }
    public int propulsionBrightness(BlockPos pos,int configured) {
        VehicleStructure.Cell cell=structure.at(pos);
        if(cell==null || !(cell.state.getBlock() instanceof com.vandorlabs.blocks.BlockPropulsionLight))return configured;
        return propulsionLevel;
    }
    public VehicleWorld(World parent,VehicleStructure structure) {
        super(parent.getSaveHandler(),new WorldInfo(parent.getWorldInfo()),parent.provider,new net.minecraft.profiler.Profiler(),true);
        this.parent=parent; this.structure=structure; this.chunkProvider=createChunkProvider();
        for(VehicleStructure.Cell cell:structure.cells) if(cell.tileData()!=null) {
            try {
                TileEntity tile=TileEntity.create(null,cell.tileData());
                if(tile!=null){tile.setWorld(this); tiles.put(cell.pos,tile);}
            }catch(RuntimeException | LinkageError error) {
                // The immutable snapshot remains authoritative even if a mod cannot run in this view.
                VehicleCompatibility.warn(cell.state.getBlock(),"local tile",error);
            }
        }
    }
    public void update(VehicleStructure next) {structure=next;}
    @Override protected IChunkProvider createChunkProvider() {
        return new IChunkProvider() {
            public Chunk getLoadedChunk(int x,int z){return provideChunk(x,z);}
            public Chunk provideChunk(int x,int z){return chunks.computeIfAbsent(((long)x<<32)^(z&0xffffffffL),k->new EmptyChunk(VehicleWorld.this,x,z));}
            public boolean tick(){return false;}
            public String makeString(){return "Vehicle local storage";}
            public boolean isChunkGeneratedAt(int x,int z){return true;}
        };
    }
    @Override protected boolean isChunkLoaded(int x,int z,boolean allowEmpty){return true;}
    @Override public boolean isBlockLoaded(BlockPos p){return true;}
    @Override public boolean isBlockLoaded(BlockPos p,boolean allowEmpty){return true;}
    @Override public IBlockState getBlockState(BlockPos p){VehicleStructure.Cell c=structure.at(p);if(c==null)return Blocks.AIR.getDefaultState();
        return c.state.getBlock() instanceof com.vandorlabs.blocks.BlockPropulsionLight?c.state.withProperty(com.vandorlabs.blocks.BlockPropulsionLight.POWERED,propulsionLevel>0).withProperty(com.vandorlabs.blocks.BlockPropulsionLight.PARTICLES,false):c.state;}
    @Override public TileEntity getTileEntity(BlockPos p){return tiles.get(p);}
    @Override public boolean setBlockState(BlockPos p,IBlockState state,int flags){return false;}
    @Override public void setTileEntity(BlockPos p,TileEntity tile){}
    @Override public void removeTileEntity(BlockPos p){}
    @Override public void notifyBlockUpdate(BlockPos p,IBlockState before,IBlockState after,int flags){}
    @Override public void markBlockRangeForRenderUpdate(BlockPos a,BlockPos b){}
    @Override public boolean checkLight(BlockPos p){return false;}
    @Override public int getCombinedLight(BlockPos p,int minimum){
        BlockPos absolute=origin.add(p);
        return parent.isBlockLoaded(absolute)?parent.getCombinedLight(absolute,Math.max(minimum,getBlockState(p).getLightValue(this,p))):0;
    }
    @Override public int getLightFor(EnumSkyBlock type,BlockPos p){BlockPos at=origin.add(p);return parent.isBlockLoaded(at)?parent.getLightFor(type,at):0;}
    @Override public net.minecraft.world.biome.Biome getBiome(BlockPos p){return parent.getBiome(origin.add(p));}
    @Override public long getTotalWorldTime(){return parent.getTotalWorldTime();}
    @Override public long getWorldTime(){return parent.getWorldTime();}
}
