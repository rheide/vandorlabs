package com.vandorlabs.gear;

import com.vandorlabs.gear.core.GearMotion;
import java.util.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Disposable actual-world stationary-hold fixture, not a vehicle transaction or recovery bypass. */
public final class ArmParkingHoldWorldChecks {
    public static final class Profile {
        public final BlockPos root,leaf,power;public final EnumFacing face;public final GearMotion.Mode mode;public final int size;
        Profile(BlockPos root,BlockPos leaf,EnumFacing face,GearMotion.Mode mode,int size){this.root=root;this.leaf=leaf;this.face=face;this.mode=mode;this.size=size;power=root.offset(face.getAxis()==EnumFacing.Axis.X?EnumFacing.NORTH:EnumFacing.EAST);}
    }
    private static int checks;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Arm stationary hold: "+why);}
    private static GearTileEntity at(WorldServer world,BlockPos root){TileEntity tile=world.getTileEntity(root);need(tile instanceof GearTileEntity,"installed root "+root);return (GearTileEntity)tile;}
    public static List<Profile> setup(WorldServer world,EntityPlayerMP actor){
        List<Profile> profiles=new ArrayList<>();int index=0;
        for(GearMotion.Mode mode:new GearMotion.Mode[]{GearMotion.Mode.EXTEND,GearMotion.Mode.ROTATE,GearMotion.Mode.ROTATE_LEVEL})for(EnumFacing face:EnumFacing.values())for(int size:new int[]{1,2}){
            BlockPos root=new BlockPos(20+(index%6)*14,80,20+(index/6)*14);index++;
            for(BlockPos p:BlockPos.getAllInBox(root.add(-6,-7,-6),root.add(6,7,6))){world.getChunkFromBlockCoords(p);if(!world.isAirBlock(p)){world.removeTileEntity(p);world.setBlockState(p,Blocks.AIR.getDefaultState(),2);}}
            world.setBlockState(root.offset(face.getOpposite()),Blocks.STONE.getDefaultState(),2);
            GearBlock block=mode==GearMotion.Mode.EXTEND?(size==1?GearRegistry.GEAR_EXTENDER:GearRegistry.LARGE_GEAR_EXTENDER):(size==1?GearRegistry.GEAR_SWING_ARM:GearRegistry.LARGE_GEAR_SWING_ARM);
            for(int section=0;section<3;section++){BlockPos p=root.offset(face,section);need(((ItemBlock)Item.getItemFromBlock(block)).placeBlockAt(new ItemStack(block),actor,world,p,face,.5F,.5F,.5F,block.getDefaultState()),"actual item callback "+mode+" "+face+" size"+size);}
            GearTileEntity arm=at(world,root);arm.operator(actor);need(arm.rebuild(),"formed actual column "+mode+" "+face+" "+arm.error());NBTTagCompound settings=arm.settings();settings.setBoolean("RotatePayload",mode==GearMotion.Mode.ROTATE);settings.setBoolean("Deployed",true);arm.configure(settings);
            BlockPos leaf=root.offset(face,3);world.setBlockState(leaf,Blocks.STONE.getDefaultState(),2);ArmPayload payload=ArmPayload.discover(arm,actor);need(payload!=null&&payload.materialized()&&payload.blocks.cells.size()==1,"ordinary native attachment captured read-only");
            NBTTagCompound held=arm.writeToNBT(new NBTTagCompound());held.setTag("ArmPayload",payload.write());held.setBoolean("GearParkingHold",true);held.setBoolean("GearTargetDeployed",false);held.setBoolean("GearSignalKnown",false);held.setString("FutureParkingField","retained");NBTTagCompound caps=new NBTTagCompound();caps.setLong("ExternalOwner",1000+index);held.setTag("ForgeCaps",caps);
            world.removeTileEntity(root);GearTileEntity loaded=new GearTileEntity();loaded.readFromNBT(held);loaded.setWorld(world);loaded.validate();world.setTileEntity(root,loaded);loaded.operator(actor);world.notifyBlockUpdate(root,world.getBlockState(root),world.getBlockState(root),3);
            profiles.add(new Profile(root,leaf,face,mode,size));
        }
        return Collections.unmodifiableList(profiles);
    }
    public static void stable(WorldServer world,List<Profile> profiles){
        for(Profile p:profiles){GearTileEntity arm=at(world,p.root);need(arm.parkingHeld()&&!arm.parkingQuarantined()&&arm.isOperational()&&arm.progress()==1&&!arm.target(),"world ticks preserve held pose/target "+p.mode+" "+p.face+" size"+p.size+" "+arm.error());need(arm.mountDirection()==p.face&&arm.owner().equals(p.root),"mount/controller survives reload");need(arm.payload()!=null&&arm.payload().materialized()&&world.getBlockState(p.leaf).getBlock()==Blocks.STONE,"exact native payload owner remains");NBTTagCompound saved=arm.writeToNBT(new NBTTagCompound());need(saved.getString("FutureParkingField").equals("retained")&&saved.getCompoundTag("ForgeCaps").hasKey("ExternalOwner",4),"native save retains opaque original");}
    }
    public static void inputEdges(WorldServer world,List<Profile> profiles){
        for(int i=1;i<profiles.size();i++){Profile p=profiles.get(i);GearTileEntity arm=at(world,p.root);NBTTagCompound tag=arm.writeToNBT(new NBTTagCompound());tag.setInteger("GearTrigger",1);arm.readFromNBT(tag);world.setBlockState(p.power,Blocks.REDSTONE_BLOCK.getDefaultState(),2);arm.input();need(arm.parkingHeld()&&!arm.target(),"first powered sample preserves held intent");world.setBlockState(p.power,Blocks.AIR.getDefaultState(),2);arm.input();need(!arm.parkingHeld()&&arm.target(),"real local power edge releases stationary hold");}
    }
    public static int checks(){return checks;}
    private ArmParkingHoldWorldChecks(){}
}
