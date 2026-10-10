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
    private final Set<BlockPos> visited = new HashSet<>(), gears = new HashSet<>();
    private final Map<BlockPos, VehicleStructure.Cell> cells = new LinkedHashMap<>();
    private int minX, minY, minZ, maxX, maxY, maxZ, seats;
    public BlockPos origin;
    public VehicleStructure result;
    public final Set<BlockPos> airBoundary=new HashSet<>();
    public String error;
    public VehicleScan(World world, BlockPos start) {
        this.world=world; this.start=start.toImmutable(); queue.add(this.start);
        minX=maxX=start.getX(); minY=maxY=start.getY(); minZ=maxZ=start.getZ();
    }
    public BlockPos startPosition(){return start;}
    private void require(boolean test, String reason) { if(!test) throw new IllegalArgumentException(reason); }
    private void linked(BlockPos p, Block expected) {
        require(world.isBlockLoaded(p), "A component crosses an unloaded chunk.");
        require(world.getBlockState(p).getBlock()==expected, "Incomplete "+expected.getLocalizedName()+": expected a matching part at "+p.getX()+", "+p.getY()+", "+p.getZ()+" but found "+world.getBlockState(p).getBlock().getLocalizedName()+". Replace or complete this component.");
        queue.add(p.toImmutable());
    }
    public boolean step(int budget) {
        if(error!=null || result!=null) return true;
        try {
            for(int i=0;i<budget && !queue.isEmpty();i++) {
                BlockPos p=queue.removeFirst(); if(!visited.add(p))continue;
                require(visited.size()<=VehicleStructure.MAX_BLOCKS*7, "Discovery limit exceeded; separate the craft from its surroundings.");
                require(world.isBlockLoaded(p), "The craft touches an unloaded chunk.");
                IBlockState state=world.getBlockState(p); Block block=state.getBlock();
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
                cells.put(p,new VehicleStructure.Cell(p,state,data));
                if(block instanceof BlockPilotSeat) {
                    boolean upper=state.getValue(BlockPilotSeat.UPPER);
                    if(!upper)seats++;
                    linked(upper?p.down():p.up(),block);
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
                    for(BlockPos member:ramp.vehicleCells()) {
                        linked(member,ModBlocks.CONTROLLED_RAMP);
                        TileEntity part=world.getTileEntity(member);
                        require(part instanceof TileEntityControlledRamp && ((TileEntityControlledRamp)part).belongsTo(p) && ramp.owns((TileEntityControlledRamp)part),"Invalid ramp component ownership.");
                    }
                    for(BlockPos source:ramp.vehicleSources()){require(world.isBlockLoaded(source),"A ramp source crosses an unloaded chunk.");queue.add(source);}
                }
                if(tile instanceof TileEntityControlledRamp) {
                    TileEntityControlledRamp ramp=(TileEntityControlledRamp)tile;
                    require(!ramp.isMoving(),"Wait for the ramp to stop moving before assembly.");
                    linked(ramp.controller,ModBlocks.PROGRAMMABLE_RAMP);
                    TileEntity owner=world.getTileEntity(ramp.controller);
                    require(owner instanceof TileEntityRampController && ((TileEntityRampController)owner).owns(ramp),"Orphaned ramp section.");
                }
                if(tile instanceof TileEntityCanopy) {
                    TileEntityCanopy canopy=((TileEntityCanopy)tile).constituent();
                    require(Math.abs(canopy.progress-(canopy.targetOpen?1:0))<.001, "Wait for the canopy to stop moving.");
                    for(BlockPos member:((BlockCanopy)block).cells(canopy.getPos(),canopy.facing()))linked(member,block);
                    if(canopy.pair!=null)linked(canopy.pair,block);
                }
                if(tile instanceof TileEntityShipSystem) {
                    TileEntityShipSystem system=(TileEntityShipSystem)tile;
                    BlockPos root=system.anchor==null?p:system.anchor;
                    for(BlockPos member:((BlockShipSystem)block).cells(root,state.getValue(BlockShipSystem.FACING),system.mount))linked(member,block);
                    if(system.consoleOwner!=null)linked(system.consoleOwner,block);
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
                                        && coverPos.offset(world.getBlockState(coverPos).getValue(BlockProgrammableTrapdoor.FACING)).equals(member))queue.add(coverPos);
                            }
                        }
                    }
                    continue;
                }
                if(block instanceof BlockLandingGear) { gears.add(p); continue; }
                if(tile instanceof TileEntityProgrammableTrapdoor && ((TileEntityProgrammableTrapdoor)tile).isCover()) {
                    BlockPos gear=p.offset(state.getValue(BlockProgrammableTrapdoor.FACING));
                    require(world.isBlockLoaded(gear) && world.getBlockState(gear).getBlock() instanceof BlockTelescopicLandingGear,"A landing gear cover has no gear owner.");
                    queue.add(gear);continue;
                }
                for(EnumFacing face:EnumFacing.values())queue.add(p.offset(face));
            }
            if(queue.isEmpty()) {
                require(seats==1, "A craft must contain exactly one Pilot Seat.");
                require(!gears.isEmpty(), "A craft must contain landing gear.");
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
