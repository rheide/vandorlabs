package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.*;
import com.vandorlabs.gear.*;
import com.vandorlabs.gear.track.TrackBlock;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Physical ground contacts determine Taxi curvature; cargo above the ground plane contributes nothing. */
public final class VehicleSteering {
    public static final double LARGE=1,MEDIUM=.65,SMALL=.45,TRACK=.08,REFERENCE_WHEELBASE=4;
    public static final class Profile {
        public final int wheels,tracks;
        public final double meanKindRadius,wheelbase,radiusFactor;
        Profile(int wheels,int tracks,double mean,double span){this.wheels=wheels;this.tracks=tracks;meanKindRadius=mean;wheelbase=Math.max(1,Math.min(VehicleStructure.MAX_AXIS,span));radiusFactor=mean*wheelbase/REFERENCE_WHEELBASE;}
        public boolean tracksOnly(){return tracks>0&&wheels==0;}
    }
    /** Unknown/legacy snapshots without usable contacts retain the original steering. */
    public static final Profile DEFAULT=new Profile(0,0,1,REFERENCE_WHEELBASE);
    private VehicleStructure observed;
    private VehicleWorld observedWorld;
    private int revision;
    private List<VehicleStructure.Cell> candidates=Collections.emptyList();
    private Profile cached=DEFAULT;
    public Profile profile(VehicleWorld world){
        if(world==null)return DEFAULT;
        if(observed!=world.structure||observedWorld!=world){observed=world.structure;observedWorld=world;candidates=new ArrayList<>();for(VehicleStructure.Cell cell:observed.cells){Block block=cell.state.getBlock();if(world.structure.isActive(cell.pos)&&(block instanceof BlockLandingGear||block instanceof BlockPropulsionLight&&cell.state.getValue(BlockPropulsionLight.FACING)==EnumFacing.DOWN))candidates.add(cell);}revision=Integer.MIN_VALUE;}
        if(revision!=world.tileRevision){revision=world.tileRevision;cached=measure(world,candidates);}
        return cached;
    }
    private static final class Contact {
        final double bottom,forward,factor;
        Contact(double bottom,double forward,double factor){this.bottom=bottom;this.forward=forward;this.factor=factor;}
    }
    private static Profile measure(VehicleWorld world,List<VehicleStructure.Cell> candidates){
        List<Contact> contacts=new ArrayList<>();double lowest=Double.POSITIVE_INFINITY;Set<BlockPos> physical=new HashSet<>();
        for(VehicleStructure.Cell cell:candidates){
            Block block=cell.state.getBlock();BlockPos at=cell.pos;IBlockState state=cell.state;double factor=0,x=at.getX()+.5,z=at.getZ()+.5,bottom;
            if(block instanceof GearBlock){
                GearBlock gear=(GearBlock)block;
                if(!gear.componentRoot(at,state).equals(at))continue;
                TileEntity raw=world.getTileEntity(at);GearTileEntity controller=raw instanceof GearTileEntity?((GearTileEntity)raw).loadedController():null;
                if(controller!=null&&(!controller.isLandingAssembly()||controller.mountDirection()!=EnumFacing.DOWN||!controller.landingDeployed()))continue;
                if(controller==null&&!(block instanceof TrackBlock))continue;
                if(gear.kind()==GearBlock.Kind.WHEEL){factor=gear.sizeClass()==2?LARGE:SMALL;if(gear.width()==2){BlockPos offset=LargeGearBlock.offset(state.getValue(GearBlock.FACING),1,1);x+=offset.getX()*.5;z+=offset.getZ()*.5;}}
                else if(gear.kind()==GearBlock.Kind.TRACK)factor=TRACK;
                else if(controller==null||!at.equals(controller.getPos()))continue;
                bottom=gear.vehicleBottomY(world,at);
            }else if(block instanceof BlockWheel){
                BlockWheel wheel=(BlockWheel)block;if(state.getValue(BlockWheel.PART)!=0)continue;factor=wheel.wheelSize()==2?LARGE:wheel.wheelSize()==1?MEDIUM:SMALL;
                if(wheel.wheelSize()==2){EnumFacing along=state.getValue(BlockWheel.FACING).rotateY();x+=along.getFrontOffsetX()*.5;z+=along.getFrontOffsetZ()*.5;}bottom=wheel.vehicleBottomY(world,at);
            }else if(block instanceof BlockTelescopicLandingGear){
                if(state.getValue(BlockTelescopicLandingGear.LOWER)||!state.getValue(BlockTelescopicLandingGear.EXTENDED))continue;
                TileEntity raw=world.getTileEntity(at);int size=raw instanceof com.vandorlabs.tiles.TileEntityLandingGear?((com.vandorlabs.tiles.TileEntityLandingGear)raw).getSize():0;factor=block instanceof BlockNonWheeledLandingGear?0:size>=2?LARGE:size==1?MEDIUM:SMALL;bottom=((BlockLandingGear)block).vehicleBottomY(world,at);
            }else if(block instanceof BlockLandingGear)bottom=((BlockLandingGear)block).vehicleBottomY(world,at);
            else bottom=at.getY();
            if(!Double.isFinite(bottom)||!physical.add(at))continue;
            double forward=world.structure.facing.getAxis()==EnumFacing.Axis.X?x:z;
            contacts.add(new Contact(bottom,forward,factor));lowest=Math.min(lowest,bottom);
        }
        int wheels=0,tracks=0;double total=0,min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        for(Contact contact:contacts)if(contact.bottom-lowest<=.250001&&contact.factor>0){if(contact.factor==TRACK)tracks++;else wheels++;total+=contact.factor;min=Math.min(min,contact.forward);max=Math.max(max,contact.forward);}
        return wheels+tracks==0?DEFAULT:new Profile(wheels,tracks,total/(wheels+tracks),max-min);
    }
}
