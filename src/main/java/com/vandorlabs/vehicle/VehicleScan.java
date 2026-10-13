package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/** Bounded, server-thread flood fill. Gear cells terminate only their own branch. */
public final class VehicleScan {
    private final World world;
    private final BlockPos start;
    private final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    private final Set<BlockPos> scheduled = new HashSet<>(), visited = new HashSet<>(), gears = new HashSet<>();
    private final Map<BlockPos, VehicleStructure.Cell> cells = new LinkedHashMap<>();
    private final Set<BlockPos> rampSections=new HashSet<>();
    private int minX, minY, minZ, maxX, maxY, maxZ, seats;
    private Double gearBottomY;
    public BlockPos origin;
    public VehicleStructure result;
    public final Set<BlockPos> airBoundary=new HashSet<>();
    public String error;
    public VehicleScan(World world, BlockPos start) {
        this.world=world; this.start=start.toImmutable(); enqueue(this.start);
        minX=maxX=start.getX(); minY=maxY=start.getY(); minZ=maxZ=start.getZ();
    }
    private void enqueue(BlockPos pos){BlockPos key=pos.toImmutable();if(scheduled.add(key))queue.add(key);}
    public BlockPos startPosition(){return start;}
    private void require(boolean test, String reason) { if(!test) throw new IllegalArgumentException(reason); }
    private void support(BlockLandingGear block,BlockPos root) {
        double bottom=block.vehicleBottomY(world,root);
        require(Double.isFinite(bottom),"Cannot determine landing gear bottom.");
        require(gearBottomY==null || Math.abs(gearBottomY-bottom)<.001,
                "Landing gear bottoms must have the same Y value. Adjust their mounting height or extension before assembly.");
        gearBottomY=bottom;
    }
    private void linked(BlockPos p, Block expected) {
        require(world.isBlockLoaded(p), "A component crosses an unloaded chunk.");
        require(world.getBlockState(p).getBlock()==expected, "Incomplete "+expected.getLocalizedName()+": expected a matching part at "+p.getX()+", "+p.getY()+", "+p.getZ()+" but found "+world.getBlockState(p).getBlock().getLocalizedName()+". Replace or complete this component.");
        enqueue(p);
    }
    private static boolean slab(Block block){return block instanceof net.minecraft.block.BlockSlab || block instanceof BlockProgrammableSlab || block instanceof BlockProgrammableLightSlab;}
    /** Empty half-block faces do not bridge the craft to the hangar floor. */
    public static boolean touches(World world,BlockPos p,IBlockState state,EnumFacing face){
        BlockPos other=p.offset(face);if(!world.isBlockLoaded(other))return true;
        IBlockState next=world.getBlockState(other);
        if(next.getBlock() instanceof BlockControlledRamp)return false;
        if(next.getBlock().isAir(next,world,other))return true;
        // Only slabs have a guaranteed empty half-cell. Non-solid decorative components
        // retain the existing six-neighbour attachment contract.
        if(face.getAxis()==EnumFacing.Axis.Y && (slab(state.getBlock()) || slab(next.getBlock()))){
            net.minecraft.util.math.AxisAlignedBB a=state.getBoundingBox(world,p).offset(p),b=next.getBoundingBox(world,other).offset(other);
            final double e=1e-6;
            switch(face.getAxis()){
                case X:return Math.abs((face==EnumFacing.EAST?a.maxX:a.minX)-(face==EnumFacing.EAST?b.minX:b.maxX))<e && a.maxY>b.minY+e && a.minY<b.maxY-e && a.maxZ>b.minZ+e && a.minZ<b.maxZ-e;
                case Y:return Math.abs((face==EnumFacing.UP?a.maxY:a.minY)-(face==EnumFacing.UP?b.minY:b.maxY))<e && a.maxX>b.minX+e && a.minX<b.maxX-e && a.maxZ>b.minZ+e && a.minZ<b.maxZ-e;
                default:return Math.abs((face==EnumFacing.SOUTH?a.maxZ:a.minZ)-(face==EnumFacing.SOUTH?b.minZ:b.maxZ))<e && a.maxX>b.minX+e && a.minX<b.maxX-e && a.maxY>b.minY+e && a.minY<b.maxY-e;
            }
        }
        return true;
    }
    public boolean step(int budget) {
        if(error!=null || result!=null) return true;
        try {
            for(int i=0;i<budget && !queue.isEmpty();i++) {
                BlockPos p=queue.removeFirst(); if(!visited.add(p))continue;
                require(visited.size()<=VehicleStructure.MAX_BLOCKS*7, "Discovery limit exceeded; separate the craft from its surroundings.");
                require(world.isBlockLoaded(p), "The craft touches an unloaded chunk.");
                IBlockState state=world.getBlockState(p); Block block=state.getBlock();
                if(block instanceof BlockControlledRamp && !rampSections.contains(p))continue;
                if(block.isAir(state,world,p)){airBoundary.add(p);continue;}
                require(!state.getMaterial().isLiquid() && block!=net.minecraft.init.Blocks.BEDROCK
                        && (state.getBlockHardness(world,p)>=0 || block instanceof BlockControlledRamp), "Unsupported block: "+block.getLocalizedName());
                TileEntity tile=world.getTileEntity(p);
                require(block.getRegistryName()!=null, "Unregistered block at "+p);
                require(!block.hasTileEntity(state) || tile!=null, "Missing component data at "+p);
                require(cells.size()<VehicleStructure.MAX_BLOCKS, "Craft exceeds 4096 blocks; check for connections to the ground.");
                minX=Math.min(minX,p.getX()); minY=Math.min(minY,p.getY()); minZ=Math.min(minZ,p.getZ());
                maxX=Math.max(maxX,p.getX()); maxY=Math.max(maxY,p.getY()); maxZ=Math.max(maxZ,p.getZ());
                require(maxX-minX<64 && maxY-minY<64 && maxZ-minZ<64, "Craft exceeds 64 blocks along an axis.");
                NBTTagCompound data=tile==null?null:tile.writeToNBT(new NBTTagCompound());
                if(VehicleEscalators.active(tile))data=VehicleEscalators.paused(data);
                if(tile instanceof TileEntityControlledRamp) {
                    TileEntity owner=world.getTileEntity(((TileEntityControlledRamp)tile).controller);
                    require(owner instanceof TileEntityRampController && ((TileEntityRampController)owner).owns((TileEntityControlledRamp)tile),"Ramp ownership changed; inspect again.");
                    data=VehicleEscalators.paused(data);data.setDouble("EscalatorDistance",((TileEntityRampController)owner).beltDistance(0));
                }
                cells.put(p,new VehicleStructure.Cell(p,state,data));
                if(block instanceof BlockPilotSeat) {
                    boolean upper=state.getValue(BlockPilotSeat.UPPER);
                    if(!upper)seats++;
                    if(!upper && BlockPilotSeat.canopyAbove(world,p))enqueue(p.up());
                    else linked(upper?p.down():p.up(),block);
                } else if(block instanceof BlockBridgeChair || block instanceof BlockConnectedSeat) {
                    linked(state.getValue(BlockBridgeChair.UPPER)?p.down():p.up(),block);
                } else if(block instanceof BlockLargeProgrammableDoor) {
                    TileEntityLargeProgrammableDoor root=((BlockLargeProgrammableDoor)block).root(world,p);
                    require(root!=null && ((BlockLargeProgrammableDoor)block).complete(world,root), "Incomplete large door.");
                    for(int x=0;x<3;x++)for(int y=0;y<3;y++)linked(root.getPos().offset(state.getValue(BlockVandorDoor.FACING).rotateYCCW(),x).up(y),block);
                } else if(block instanceof BlockVandorDoor || block instanceof BlockDoor) {
                    linked(state.getValue(BlockVandorDoor.HALF)==BlockDoor.EnumDoorHalf.UPPER?p.down():p.up(),block);
                }
                if(tile instanceof TileEntityRampController) {
                    TileEntityRampController ramp=(TileEntityRampController)tile;
                    require(!ramp.isMoving(),"Wait for the ramp to stop moving before assembly.");
                    {
                        for(BlockPos source:ramp.vehicleSources()){
                            require(world.isBlockLoaded(source),"Load all ramp source chunks before assembly.");
                            TileEntity part=world.getTileEntity(source);require(world.isAirBlock(source) || part instanceof TileEntityControlledRamp && ramp.owns((TileEntityControlledRamp)part),"Ramp source changed; inspect again.");
                        }
                        for(BlockPos section:ramp.vehicleCells()) {
                            require(world.isBlockLoaded(section),"Load all ramp section chunks before assembly.");
                            TileEntity part=world.getTileEntity(section);
                            require(part instanceof TileEntityControlledRamp && ramp.owns((TileEntityControlledRamp)part),"Ramp section changed; inspect again.");
                            rampSections.add(section);airBoundary.remove(section);visited.remove(section);scheduled.add(section);queue.add(section);
                        }
                    }
                }
                if(tile instanceof TileEntityCanopy) {
                    TileEntityCanopy canopy=((TileEntityCanopy)tile).constituent();
                    require(Math.abs(canopy.progress-(canopy.targetOpen?1:0))<.001, "Wait for the canopy to stop moving.");
                    for(BlockPos member:canopy.cells())linked(member,block);
                    if(canopy.pair!=null)linked(canopy.pair,block);
                }
                if(tile instanceof TileEntityProgrammableTrapdoor && data!=null) {
                    for(String key:new String[]{"TrapdoorPartner","TrapdoorSquare"})
                        if(data.hasKey(key,4))linked(BlockPos.fromLong(data.getLong(key)),block);
                    net.minecraft.nbt.NBTTagList assembly=data.getTagList("TrapdoorAssembly",4);
                    for(int n=0;n<assembly.tagCount();n++)linked(BlockPos.fromLong(((net.minecraft.nbt.NBTTagLong)assembly.get(n)).getLong()),block);
                }
                if(block instanceof BlockTelescopicLandingGear) {
                    TileEntityLandingGear root=((BlockTelescopicLandingGear)block).root(world,p);
                    require(root!=null, "Orphaned landing gear.");
                    if(gears.add(root.getPos())) {
                        boolean extended=world.getBlockState(root.getPos()).getValue(BlockTelescopicLandingGear.EXTENDED);
                        float target=extended?root.getExtensionPixels()/16F:0;
                        require(Math.abs(root.progress-target)<.001, "Wait for landing gear to stop moving.");
                        support((BlockLandingGear)block,root.getPos());
                        linked(root.getPos(),block);
                        int size=root.getSize(), radius=size>=3?1:0, count=(int)Math.ceil(target)+(size>=3?1:0);
                        for(int y=0;y<=count;y++)for(int x=size==4?0:-radius;x<=radius;x++)for(int z=size==4?0:-radius;z<=radius;z++) {
                            BlockPos member=root.getPos().add(x,-y,z); linked(member,block);
                            TileEntity raw=world.getTileEntity(member);
                            require(member.equals(root.getPos()) || raw instanceof TileEntityLandingGear && root.getPos().equals(((TileEntityLandingGear)raw).owner()), "Invalid landing gear ownership.");
                            for(EnumFacing side:EnumFacing.HORIZONTALS) {
                                BlockPos coverPos=member.offset(side);
                                if(!world.isBlockLoaded(coverPos))continue;
                                TileEntity cover=world.getTileEntity(coverPos);
                                if(cover instanceof TileEntityProgrammableTrapdoor && ((TileEntityProgrammableTrapdoor)cover).isCover()
                                        && coverPos.offset(world.getBlockState(coverPos).getValue(BlockProgrammableTrapdoor.FACING)).equals(member))enqueue(coverPos);
                            }
                        }
                    }
                    continue;
                }
                if(block instanceof BlockLandingGear) { if(gears.add(p))support((BlockLandingGear)block,p); continue; }
                if(tile instanceof TileEntityProgrammableTrapdoor && ((TileEntityProgrammableTrapdoor)tile).isCover()) {
                    BlockPos gear=p.offset(state.getValue(BlockProgrammableTrapdoor.FACING));
                    require(world.isBlockLoaded(gear) && world.getBlockState(gear).getBlock() instanceof BlockTelescopicLandingGear,"A landing gear cover has no gear owner.");
                    enqueue(gear);continue;
                }
                if(!(block instanceof BlockControlledRamp))for(EnumFacing face:EnumFacing.values())if(touches(world,p,state,face))enqueue(p.offset(face));
            }
            if(queue.isEmpty()) {
                require(seats==1, "A craft must contain exactly one Pilot Seat.");
                require(gears.size()>=3, "A craft needs at least three landing gear blocks.");
                origin=new BlockPos(minX,minY,minZ);
                List<VehicleStructure.Cell> local=new ArrayList<>();
                for(VehicleStructure.Cell cell:cells.values())local.add(new VehicleStructure.Cell(cell.pos.subtract(origin),cell.state,
                        cell.tileData()==null?null:VehicleTileData.translated(cell.tileData(),BlockPos.ORIGIN.subtract(origin))));
                result=new VehicleStructure(local,start.subtract(origin),world.getBlockState(start).getValue(BlockPilotSeat.FACING),gears.size());
                result.encode(); // Enforce the byte limit before a preview or mutation.
            }
        } catch(Exception e) { error=e.getMessage()==null?"Cannot capture this structure.":e.getMessage(); }
        return error!=null || result!=null;
    }
}
