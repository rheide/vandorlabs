package com.vandorlabs.turntable;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Native reserved cells must move together; a prism never silently crops an assembly. */
public final class TurntableBoundaries {
    private final World world;private final BlockPos min,max;
    public TurntableBoundaries(World world,BlockPos min,BlockPos max){this.world=world;this.min=min;this.max=max;}
    private void includes(BlockPos p){if(p.getX()<min.getX()||p.getY()<min.getY()||p.getZ()<min.getZ()||p.getX()>max.getX()||p.getY()>max.getY()||p.getZ()>max.getZ())throw new IllegalArgumentException("Scan volume cuts a multiblock at "+p+"; include its complete assembly");if(!world.isBlockLoaded(p))throw new IllegalArgumentException("Load all assembly cells first");}
    public void check(BlockPos p,IBlockState state,TileEntity tile){
        Block block=state.getBlock();
        boolean rampPart=block instanceof BlockControlledRamp&&tile instanceof TileEntityControlledRamp;
        if(state.getBlockHardness(world,p)<0&&!rampPart)throw new IllegalArgumentException("Cannot capture unbreakable "+block.getRegistryName()+" at "+p.getX()+", "+p.getY()+", "+p.getZ());
        if(rampPart){
            TileEntityControlledRamp part=(TileEntityControlledRamp)tile;includes(part.controller);TileEntity raw=world.getTileEntity(part.controller);
            if(!(raw instanceof TileEntityRampController)||!((TileEntityRampController)raw).owns(part))throw new IllegalArgumentException("Incomplete ramp at "+p+"; restore it with its Ramp Controller before scanning");
            TileEntityRampController ramp=(TileEntityRampController)raw;
            if(ramp.isMoving())throw new IllegalArgumentException("Stop the ramp before scanning");
            for(BlockPos cell:ramp.vehicleCells())includes(cell);for(BlockPos cell:ramp.vehicleSources())includes(cell);
        }
        if(tile instanceof net.minecraft.tileentity.TileEntityPiston)throw new IllegalArgumentException("Wait for pistons to stop before scanning");
        if(block instanceof BlockChest)for(EnumFacing side:EnumFacing.HORIZONTALS){BlockPos adjacent=p.offset(side);if(!world.isBlockLoaded(adjacent))throw new IllegalArgumentException("Load chest neighbours before scanning");if(world.getBlockState(adjacent).getBlock()==block)includes(adjacent);}
        if(block instanceof BlockBed)includes(p.offset(state.getValue(BlockBed.PART)==BlockBed.EnumPartType.FOOT?state.getValue(BlockBed.FACING):state.getValue(BlockBed.FACING).getOpposite()));
        if(block instanceof BlockDoublePlant)includes(state.getValue(BlockDoublePlant.HALF)==BlockDoublePlant.EnumBlockHalf.UPPER?p.down():p.up());
        if(block instanceof BlockPistonBase&&state.getValue(BlockPistonBase.EXTENDED))includes(p.offset(state.getValue(BlockPistonBase.FACING)));
        if(block instanceof BlockPistonExtension)includes(p.offset(state.getValue(BlockPistonExtension.FACING).getOpposite()));
        if(block.hasTileEntity(state)&&tile==null)throw new IllegalArgumentException("Missing block data at "+p);
        if(tile instanceof com.vandorlabs.gear.GearTileEntity){com.vandorlabs.gear.GearTileEntity root=((com.vandorlabs.gear.GearTileEntity)tile).loadedController();if(root==null){
            if(tile instanceof com.vandorlabs.gear.track.TrackTile){com.vandorlabs.gear.track.TrackTile track=(com.vandorlabs.gear.track.TrackTile)tile;BlockPos leader=track.leader();if(leader!=null){includes(leader);TileEntity raw=world.getTileEntity(leader);if(raw instanceof com.vandorlabs.gear.track.TrackTile)for(BlockPos member:((com.vandorlabs.gear.track.TrackTile)raw).trackMembers())includes(member);}}
            else throw new IllegalArgumentException("Complete the modular arm before scanning");
        }else for(BlockPos cell:root.members())includes(cell);}
        if(tile instanceof TileEntityCockpit){TileEntityCockpit c=(TileEntityCockpit)tile;for(BlockPos cell:c.data().cells(p,c.facing()))includes(cell);}
        if(tile instanceof TileEntityCockpitPart){TileEntityCockpit root=com.vandorlabs.cockpit.CockpitAssembly.root(world,p);if(root==null)throw new IllegalArgumentException("Incomplete cockpit");includes(root.getPos());}
        if(block instanceof com.vandorlabs.programmer.ProgrammerBlock){com.vandorlabs.programmer.ProgrammerTile root=com.vandorlabs.programmer.ProgrammerBlock.at(world,p);if(root==null)throw new IllegalArgumentException("Incomplete programmer");for(int part=0;part<4;part++)includes(com.vandorlabs.programmer.ProgrammerBlock.cell(root.getPos(),root.getProgrammerFacing(),part));}
        if(tile instanceof TileEntityCanopy){TileEntityCanopy c=((TileEntityCanopy)tile).constituent();for(BlockPos cell:c.reservedCells())includes(cell);if(c.pair!=null)includes(c.pair);}
        if(tile instanceof TileEntityConstructorPart)includes(((TileEntityConstructorPart)tile).owner());
        if(tile instanceof TileEntityConstructor&&((TileEntityConstructor)tile).formed()){BlockConstructorCore core=(BlockConstructorCore)block;for(int x=0;x<core.width;x++)for(int y=0;y<core.height;y++)for(int z=0;z<core.depth;z++)includes(((TileEntityConstructor)tile).cell(x,y,z));}
        if(tile instanceof TileEntityRampController){TileEntityRampController ramp=(TileEntityRampController)tile;if(ramp.isMoving())throw new IllegalArgumentException("Stop the ramp before scanning");for(BlockPos cell:ramp.vehicleCells())includes(cell);for(BlockPos cell:ramp.vehicleSources())includes(cell);}
        if(tile instanceof TileEntityControlledRamp)includes(((TileEntityControlledRamp)tile).controller);
        if(tile instanceof TileEntityLandingGear){TileEntityLandingGear gear=(TileEntityLandingGear)tile;if(gear.owner()!=null)includes(gear.owner());else{int radius=gear.getSize()>=3?1:0,count=(int)Math.ceil(Math.max(gear.progress,gear.getExtensionPixels()/16F))+(radius>0?1:0);for(int y=0;y<=count;y++)for(int x=gear.getSize()==4?0:-radius;x<=radius;x++)for(int z=gear.getSize()==4?0:-radius;z<=radius;z++)includes(p.add(x,-y,z));}}
        if(block instanceof BlockWheel){BlockWheel wheel=(BlockWheel)block;BlockPos root=wheel.root(p,state);includes(root);for(BlockPos cell:wheel.members(root,world.getBlockState(root)))includes(cell);}
        if(block instanceof BlockLargeProgrammableDoor){TileEntityLargeProgrammableDoor root=((BlockLargeProgrammableDoor)block).root(world,p);if(root==null)throw new IllegalArgumentException("Incomplete door");for(int x=0;x<3;x++)for(int y=0;y<3;y++)includes(root.getPos().offset(state.getValue(BlockVandorDoor.FACING).rotateYCCW(),x).up(y));}
        else if(block instanceof BlockDoor)includes(state.getValue(BlockDoor.HALF)==BlockDoor.EnumDoorHalf.UPPER?p.down():p.up());
        else if(block instanceof BlockBridgeChair||block instanceof BlockConnectedSeat||block instanceof BlockPilotSeat&&!(block instanceof BlockCockpit))includes(state.getValue(BlockBridgeChair.UPPER)?p.down():p.up());
        if(tile instanceof TileEntityProgrammableTrapdoor){NBTTagCompound tag=tile.writeToNBT(new NBTTagCompound());for(String key:new String[]{"TrapdoorPartner","TrapdoorSquare"})if(tag.hasKey(key,4))includes(BlockPos.fromLong(tag.getLong(key)));NBTTagList list=tag.getTagList("TrapdoorAssembly",4);for(int i=0;i<list.tagCount();i++)includes(BlockPos.fromLong(((NBTTagLong)list.get(i)).getLong()));}
    }
}
