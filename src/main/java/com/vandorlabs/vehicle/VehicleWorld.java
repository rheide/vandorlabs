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
 * Only explicit vehicle adapters animate tiles and subscribe supported channel controls. */
public final class VehicleWorld extends World {
    public VehicleStructure structure;
    public EntityGroundVehicle craft;
    public final World parent;
    public final Map<BlockPos,TileEntity> tiles=new LinkedHashMap<>();
    private final Map<Long,Chunk> chunks=new HashMap<>();
    public BlockPos origin=BlockPos.ORIGIN;
    public int propulsionLevel;
    public net.minecraft.util.math.Vec3d propulsionMotion=net.minecraft.util.math.Vec3d.ZERO;
    public boolean hover;
    public int tileRevision;
    public java.util.function.Consumer<BlockPos> controlUpdates;
    public static boolean isMovingControl(net.minecraft.block.Block block) {
        return block instanceof com.vandorlabs.blocks.BlockSignalControl && ((com.vandorlabs.blocks.BlockSignalControl)block).hasSelectableType();
    }
    public static boolean isPropulsion(net.minecraft.block.Block block) {
        return block instanceof com.vandorlabs.blocks.BlockPropulsionLight;
    }
    public double recordMotion(double dx,double dy,double dz,float yaw){
        double speed=Math.sqrt(dx*dx+dy*dy+dz*dz),angle=Math.toRadians(yaw),c=Math.cos(angle),s=Math.sin(angle);
        if(speed>.002)propulsionMotion=new net.minecraft.util.math.Vec3d(c*dx+s*dz,dy,-s*dx+c*dz);
        return speed;
    }
    /** Cache key changes only when a directional group switches, not on every velocity sample. */
    public int propulsionAppearance(){
        return propulsionLevel|(hover?16:0)|(propulsionMotion.x>.002?32:propulsionMotion.x<-.002?64:0)
                |(propulsionMotion.y>.002?128:propulsionMotion.y<-.002?256:0)|(propulsionMotion.z>.002?512:propulsionMotion.z<-.002?1024:0);
    }
    public int propulsionBrightness(BlockPos pos,int configured) {
        VehicleStructure.Cell cell=structure.at(pos);
        if(cell==null || !(cell.state.getBlock() instanceof com.vandorlabs.blocks.BlockPropulsionLight))return configured;
        if(VehicleFlight.lift(cell.state.getBlock()))return hover?15:0;
        if(channelPropulsion(pos)!=null)return propulsionLevel>0&&!VehicleFlight.opposes(cell.state,propulsionMotion)?0:configured;
        if(!VehicleFlight.opposes(cell.state,propulsionMotion))return 0;
        return propulsionLevel;
    }
    private com.vandorlabs.tiles.TileEntityRedstoneLight channelPropulsion(BlockPos pos){
        TileEntity raw=tiles.get(pos);
        return raw instanceof com.vandorlabs.tiles.TileEntityRedstoneLight && !((com.vandorlabs.tiles.TileEntityRedstoneLight)raw).getRedstoneChannels().isEmpty()?(com.vandorlabs.tiles.TileEntityRedstoneLight)raw:null;
    }
    public VehicleWorld(World parent,VehicleStructure structure) {
        super(parent.getSaveHandler(),new WorldInfo(parent.getWorldInfo()),parent.provider,new net.minecraft.profiler.Profiler(),true);
        this.parent=parent; this.structure=structure; this.chunkProvider=createChunkProvider();
        for(VehicleStructure.Cell cell:structure.cells) if(cell.hasTileData()) {
            try {
                TileEntity tile=TileEntity.create(null,cell.tileData());
                if(tile!=null){tile.setWorld(this); tiles.put(cell.pos,tile);}
            }catch(RuntimeException | LinkageError error) {
                // The immutable snapshot remains authoritative even if a mod cannot run in this view.
                VehicleCompatibility.warn(cell.state.getBlock(),"local tile",error);
            }
        }
    }
    public void update(VehicleStructure next) {
        VehicleStructure before=structure;structure=next;
        if(tiles.keySet().stream().anyMatch(pos->next.at(pos)==null || !next.at(pos).hasTileData()))tileRevision++;
        tiles.keySet().removeIf(pos->next.at(pos)==null || !next.at(pos).hasTileData());
        for(VehicleStructure.Cell cell:next.cells)if(cell.hasTileData()) {
            VehicleStructure.Cell old=before.at(cell.pos);
            if(old!=null && old.state!=cell.state)tileRevision++;
            if(old!=null && old.state.getBlock()==cell.state.getBlock() && old.sameTileData(cell) && tiles.containsKey(cell.pos))continue;
            tiles.remove(cell.pos);
            tileRevision++;
            try {
                TileEntity tile=TileEntity.create(null,cell.tileData());
                if(tile!=null){tile.setWorld(this);tiles.put(cell.pos,tile);}
            }catch(RuntimeException | LinkageError error){VehicleCompatibility.warn(cell.state.getBlock(),"local tile update",error);}
        }
    }
    @Override protected IChunkProvider createChunkProvider() {
        return new IChunkProvider() {
            // Global collision handlers inspect loaded chunks. A lookup must
            // never allocate a chunk or post capability events for this view.
            public Chunk getLoadedChunk(int x,int z){return chunks.get(chunkKey(x,z));}
            public Chunk provideChunk(int x,int z){return chunks.computeIfAbsent(chunkKey(x,z),k->new Chunk(VehicleWorld.this,x,z));}
            public boolean tick(){return false;}
            public String makeString(){return "Vehicle local storage";}
            public boolean isChunkGeneratedAt(int x,int z){return true;}
        };
    }
    private static long chunkKey(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    @Override protected boolean isChunkLoaded(int x,int z,boolean allowEmpty){return true;}
    @Override public boolean isBlockLoaded(BlockPos p){return true;}
    @Override public boolean isBlockLoaded(BlockPos p,boolean allowEmpty){return true;}
    @Override public IBlockState getBlockState(BlockPos p){VehicleStructure.Cell c=structure.at(p);if(c==null)return Blocks.AIR.getDefaultState();
        if(!(c.state.getBlock() instanceof com.vandorlabs.blocks.BlockPropulsionLight))return c.state;
        com.vandorlabs.tiles.TileEntityRedstoneLight light=channelPropulsion(p);
        boolean powered=VehicleFlight.lift(c.state.getBlock())?hover:light==null?propulsionLevel>0:light.isSignalBrightness()?(light.isRedstoneInverted()?15-light.getChannelLevel():light.getChannelLevel())>0:(light.getChannelLevel()>0)!=light.isRedstoneInverted();
        if(!VehicleFlight.lift(c.state.getBlock())&&(light==null||propulsionLevel>0)&&!VehicleFlight.opposes(c.state,propulsionMotion))powered=false;
        return c.state.withProperty(com.vandorlabs.blocks.BlockPropulsionLight.POWERED,powered).withProperty(com.vandorlabs.blocks.BlockPropulsionLight.PARTICLES,false);}
    @Override public TileEntity getTileEntity(BlockPos p){return tiles.get(p);}
    @Override public boolean setBlockState(BlockPos p,IBlockState state,int flags){return false;}
    @Override public void setTileEntity(BlockPos p,TileEntity tile){}
    @Override public void removeTileEntity(BlockPos p){}
    /** Carried tiles are saved by VehicleSignals, never by a synthetic chunk.
     * Chunk construction posts global capability events; a remote view on a
     * physical server must not send other mods down their client-world paths. */
    @Override public void markChunkDirty(BlockPos p,TileEntity tile){
        if(tile!=null && tiles.get(p)==tile)controlChanged(p);
    }
    // Only explicit vehicle adapters may update controls. Inherited comparator
    // callbacks would run arbitrary neighbouring mod tiles in this read-only view.
    @Override public void updateComparatorOutputLevel(BlockPos p,net.minecraft.block.Block block){}
    @Override public boolean isSideSolid(BlockPos p,net.minecraft.util.EnumFacing side,boolean fallback){
        return isValid(p)?getBlockState(p).isSideSolid(this,p,side):fallback;
    }
    @Override public int getBlockLightOpacity(BlockPos p){return getBlockState(p).getLightOpacity(this,p);}
    private void controlChanged(BlockPos p){if(controlUpdates!=null)controlUpdates.accept(p.toImmutable());}
    @Override public void notifyBlockUpdate(BlockPos p,IBlockState before,IBlockState after,int flags){controlChanged(p);}
    @Override public void markBlockRangeForRenderUpdate(BlockPos a,BlockPos b){}
    @Override public boolean checkLight(BlockPos p){return false;}
    @Override public boolean checkLightFor(EnumSkyBlock type,BlockPos p){return false;}
    @Override public void setLightFor(EnumSkyBlock type,BlockPos p,int value){}
    /** Light readers used by foreign block models must not fall back to local chunks. */
    @Override public int getLight(BlockPos p){return loadedParent(p,0,parent::getLight);}
    @Override public int getLight(BlockPos p,boolean neighbors){
        return neighbors?neighborLight(p,at->getLight(at,false)):loadedParent(p,0,absolute->parent.getLight(absolute,false));
    }
    private int neighborLight(BlockPos p,java.util.function.ToIntFunction<BlockPos> read){
        if(loadedParent(p,false,absolute->parent.getBlockState(absolute).useNeighborBrightness())){
            int light=0;
            for(net.minecraft.util.EnumFacing face:net.minecraft.util.EnumFacing.VALUES)if(face!=net.minecraft.util.EnumFacing.DOWN)
                light=Math.max(light,read.applyAsInt(p.offset(face)));
            return light;
        }
        return read.applyAsInt(p);
    }
    @Override public int getLightFromNeighborsFor(EnumSkyBlock type,BlockPos p){
        // World.getLightFromNeighborsFor is client-only in 1.12; use the shared
        // primitive so this override also works during physical-server checks.
        return type==EnumSkyBlock.SKY&&!provider.hasSkyLight()?0:neighborLight(p,at->getLightFor(type,at));
    }
    @Override public boolean canSeeSky(BlockPos p){return loadedParent(p,false,parent::canSeeSky);}
    private <T> T loadedParent(BlockPos p,T fallback,java.util.function.Function<BlockPos,T> read){
        BlockPos absolute=origin.add(p);
        return parent.isBlockLoaded(absolute)?read.apply(absolute):fallback;
    }
    @Override public int getCombinedLight(BlockPos p,int minimum){
        return loadedParent(p,0,absolute->parent.getCombinedLight(absolute,Math.max(minimum,getBlockState(p).getLightValue(this,p))));
    }
    @Override public int getLightFor(EnumSkyBlock type,BlockPos p){return loadedParent(p,0,absolute->parent.getLightFor(type,absolute));}
    @Override public net.minecraft.world.biome.Biome getBiome(BlockPos p){return parent.getBiome(origin.add(p));}
    @Override public long getTotalWorldTime(){return parent.getTotalWorldTime();}
    @Override public long getWorldTime(){return parent.getWorldTime();}
}
