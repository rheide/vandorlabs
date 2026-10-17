package com.vandorlabs.vehicle;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.io.*;
import java.util.*;

/** Versioned immutable structure. Local coordinates are independent of its current world position. */
public final class VehicleStructure {
    public static final int MAX_BLOCKS=32768, MAX_AXIS=64, MAX_NBT_BYTES=4*1024*1024;
    public static final int SCHEMA=1;
    public static final class Cell {
        public final BlockPos pos;
        public final IBlockState state;
        private final NBTTagCompound tile;
        public Cell(BlockPos pos,IBlockState state,NBTTagCompound tile) {
            this.pos=pos.toImmutable();this.state=state;this.tile=tile==null?null:tile.copy();
        }
        public NBTTagCompound tileData(){return tile==null?null:tile.copy();}
        public boolean hasTileData(){return tile!=null;}
        public boolean sameTileData(Cell other){return Objects.equals(tile,other.tile);}
        public int tileInteger(String key){return tile==null?0:tile.getInteger(key);}
    }
    public final List<Cell> cells;
    public final BlockPos seat;
    public final EnumFacing facing;
    public final int gearCount;
    public final AxisAlignedBB bounds;
    private final Map<BlockPos,Cell> byPosition;
    private final List<BlockPos> pilotSeats;
    private final Cell controller;
    public final List<Cell> liftCells,exhaustCells;
    public final boolean hasThruster,hasNonWheeledGear;

    public VehicleStructure(List<Cell> cells,BlockPos seat,EnumFacing facing,int gearCount) {
        if(cells.isEmpty()||cells.size()>MAX_BLOCKS||!facing.getAxis().isHorizontal()||gearCount<1)
            throw new IllegalArgumentException("Invalid vehicle structure");
        Map<BlockPos,Cell> positions=new LinkedHashMap<>();
        List<BlockPos> seats=new ArrayList<>();List<Cell> lifts=new ArrayList<>(),exhausts=new ArrayList<>();boolean thruster=false,nonWheeled=false;Cell control=null;
        int x=0,y=0,z=0;
        for(Cell cell:cells) {
            if(cell.pos.getX()<0||cell.pos.getY()<0||cell.pos.getZ()<0
                    ||cell.pos.getX()>=MAX_AXIS||cell.pos.getY()>=MAX_AXIS||cell.pos.getZ()>=MAX_AXIS
                    ||positions.put(cell.pos,cell)!=null)throw new IllegalArgumentException("Invalid vehicle cell");
            x=Math.max(x,cell.pos.getX()+1);y=Math.max(y,cell.pos.getY()+1);z=Math.max(z,cell.pos.getZ()+1);
            if(cell.state.getBlock() instanceof com.vandorlabs.blocks.BlockPilotSeat
                    && !cell.state.getValue(com.vandorlabs.blocks.BlockPilotSeat.UPPER))seats.add(cell.pos);
            if(control==null&&cell.state.getBlock() instanceof com.vandorlabs.blocks.BlockVehicleController)control=cell;
            if(com.vandorlabs.blocks.BlockNonWheeledLandingGear.forbidsTaxi(cell.state.getBlock()))nonWheeled=true;
            if(VehicleFlight.exhaust(cell.state.getBlock()))exhausts.add(cell);
            if(VehicleFlight.lift(cell.state.getBlock()))lifts.add(cell);
            else if(cell.state.getBlock() instanceof com.vandorlabs.blocks.BlockPropulsionLight)thruster=true;
        }
        if(!positions.containsKey(seat)||!(positions.get(seat).state.getBlock() instanceof com.vandorlabs.blocks.BlockPilotSeat))
            throw new IllegalArgumentException("Missing pilot seat");
        this.cells=Collections.unmodifiableList(new ArrayList<>(cells));this.byPosition=Collections.unmodifiableMap(positions);
        this.pilotSeats=Collections.unmodifiableList(seats);this.controller=control;this.liftCells=Collections.unmodifiableList(lifts);this.exhaustCells=Collections.unmodifiableList(exhausts);this.hasThruster=thruster;this.hasNonWheeledGear=nonWheeled;
        this.seat=seat.toImmutable();this.facing=facing;this.gearCount=gearCount;this.bounds=new AxisAlignedBB(0,0,0,x,y,z);
    }
    public Cell at(BlockPos pos){return byPosition.get(pos);}
    public List<BlockPos> pilotSeats(){
        return pilotSeats;
    }
    public Cell controller(){
        return controller;
    }
    public boolean matches(World world,BlockPos origin) {
        for(Cell cell:cells) {
            BlockPos p=origin.add(cell.pos);
            if(!world.isBlockLoaded(p))return false;
            VehicleStructure.Cell original=VehicleEscalators.original(world,this,origin,p);
            if(original!=null){
                if(original.state!=cell.state)return false;
                NBTTagCompound expected=cell.tileData(),actual=original.tileData()==null?null:VehicleTileData.translated(original.tileData(),BlockPos.ORIGIN.subtract(origin));
                if(!java.util.Objects.equals(expected,actual))return false;continue;
            }
            if(world.getBlockState(p)!=cell.state)return false;
            TileEntity tile=world.getTileEntity(p);
            if(cell.tile==null){if(tile!=null)return false;}
            else {
                if(tile==null)return false;
                NBTTagCompound captured=tile.writeToNBT(new NBTTagCompound());
                if(cell.tile.getBoolean("VehicleRampFrozen")) {
                    if(tile instanceof com.vandorlabs.tiles.TileEntityRampController)for(BlockPos source:((com.vandorlabs.tiles.TileEntityRampController)tile).vehicleSources()) {
                        if(!world.isBlockLoaded(source))return false;
                        TileEntity part=world.getTileEntity(source);
                        if(!world.isAirBlock(source) && !(part instanceof com.vandorlabs.tiles.TileEntityControlledRamp && ((com.vandorlabs.tiles.TileEntityRampController)tile).owns((com.vandorlabs.tiles.TileEntityControlledRamp)part)))return false;
                    }
                    captured=VehicleEscalators.paused(captured);
                    // Live escalators may advance while the preview is open.
                    // Configuration/ownership must match, but capture phase is fixed.
                    captured.setDouble("EscalatorDistance",cell.tile.getDouble("EscalatorDistance"));
                } else if(VehicleEscalators.active(tile) && cell.tile.getTagList("Sources",10).tagCount()==0)captured=VehicleEscalators.legacyPaused(captured);
                NBTTagCompound actual=VehicleTileData.translated(captured,BlockPos.ORIGIN.subtract(origin));
                NBTTagCompound saved=cell.tile.copy();
                // A short pulse can fall while the bounded scan is still running.
                if(tile instanceof com.vandorlabs.tiles.TileEntityPilotSeat){VehicleSeatSettings.clearSignalEdges(actual);VehicleSeatSettings.clearSignalEdges(saved);}
                if(!saved.equals(actual))return false;
            }
        }
        return true;
    }
    public NBTTagCompound write() {
        NBTTagCompound out=new NBTTagCompound();out.setInteger("Schema",SCHEMA);
        out.setLong("Seat",seat.toLong());out.setInteger("Facing",facing.getHorizontalIndex());out.setInteger("GearCount",gearCount);
        Map<IBlockState,Integer> palette=new LinkedHashMap<>();NBTTagList states=new NBTTagList(), blocks=new NBTTagList();
        for(Cell cell:cells) {
            Integer id=palette.get(cell.state);
            if(id==null){id=palette.size();palette.put(cell.state,id);states.appendTag(NBTUtil.writeBlockState(new NBTTagCompound(),cell.state));}
            NBTTagCompound tag=new NBTTagCompound();tag.setLong("Pos",cell.pos.toLong());tag.setInteger("State",id);
            if(cell.tile!=null)tag.setTag("Tile",cell.tile.copy());blocks.appendTag(tag);
        }
        out.setTag("Palette",states);out.setTag("Blocks",blocks);return out;
    }
    public static VehicleStructure read(NBTTagCompound tag) {
        if(tag.getInteger("Schema")!=SCHEMA)throw new IllegalArgumentException("Unsupported vehicle schema");
        NBTTagList palette=tag.getTagList("Palette",10),blocks=tag.getTagList("Blocks",10);
        if(blocks.tagCount()>MAX_BLOCKS||palette.tagCount()>MAX_BLOCKS||palette.tagCount()==0)
            throw new IllegalArgumentException("Oversize vehicle data");
        List<Cell> cells=new ArrayList<>();
        for(int i=0;i<blocks.tagCount();i++) {
            NBTTagCompound b=blocks.getCompoundTagAt(i);int state=b.getInteger("State");
            if(state<0||state>=palette.tagCount())throw new IllegalArgumentException("Invalid vehicle palette index");
            NBTTagCompound saved=palette.getCompoundTagAt(state);
            // Mode is a render-only Ramp property added after existing vehicle saves.
            if("vandorlabs:programmable_ramp".equals(saved.getString("Name")) && !saved.getCompoundTag("Properties").hasKey("mode")){
                saved=saved.copy();NBTTagCompound properties=saved.getCompoundTag("Properties");properties.setString("mode","0");saved.setTag("Properties",properties);
            }
            net.minecraft.util.ResourceLocation name=new net.minecraft.util.ResourceLocation(saved.getString("Name"));
            if(!net.minecraft.block.Block.REGISTRY.containsKey(name))throw new IllegalArgumentException("Missing vehicle block "+name);
            IBlockState parsed=NBTUtil.readBlockState(saved);
            if(!NBTUtil.writeBlockState(new NBTTagCompound(),parsed).equals(saved))throw new IllegalArgumentException("Unknown vehicle block properties");
            if(parsed.getBlock()==net.minecraft.init.Blocks.AIR)throw new IllegalArgumentException("Air in vehicle structure");
            cells.add(new Cell(BlockPos.fromLong(b.getLong("Pos")),parsed,b.hasKey("Tile",10)?b.getCompoundTag("Tile"):null));
        }
        int facing=tag.getInteger("Facing");if(facing<0||facing>3)throw new IllegalArgumentException("Invalid vehicle facing");
        return new VehicleStructure(cells,BlockPos.fromLong(tag.getLong("Seat")),EnumFacing.getHorizontal(facing),tag.getInteger("GearCount"));
    }
    public byte[] encode() throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        DataOutputStream output=new DataOutputStream(new FilterOutputStream(bytes) {
            private int size;
            @Override public void write(int value)throws IOException{if(++size>MAX_NBT_BYTES)throw new IOException("Vehicle data exceeds 4 MiB");out.write(value);}
            @Override public void write(byte[] b,int off,int len)throws IOException{size+=len;if(size>MAX_NBT_BYTES)throw new IOException("Vehicle data exceeds 4 MiB");out.write(b,off,len);}
        });
        CompressedStreamTools.write(write(),output);output.flush();return bytes.toByteArray();
    }
    public static VehicleStructure decode(byte[] bytes)throws IOException {
        if(bytes.length>MAX_NBT_BYTES)throw new IOException("Oversize vehicle snapshot");
        return read(CompressedStreamTools.read(new DataInputStream(new ByteArrayInputStream(bytes)),new NBTSizeTracker(MAX_NBT_BYTES*4L)));
    }
}
