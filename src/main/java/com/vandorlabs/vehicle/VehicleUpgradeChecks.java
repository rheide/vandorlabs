package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityRampController;
import net.minecraft.block.Block;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/** Prior-release reservation ordering and block palettes must survive the real snapshot reader. */
public final class VehicleUpgradeChecks {
    private static void require(boolean value,String why){if(!value)throw new IllegalStateException("Vehicle upgrade: "+why);}
    public static void run(World parent){
        int cases=0;
        for(BlockPos origin:Arrays.asList(new BlockPos(103,64,-37),new BlockPos(-217,91,118),new BlockPos(17,5,31))){
            TileEntityRampController source=new TileEntityRampController();source.setPos(origin);
            NBTTagCompound saved=source.writeToNBT(new NBTTagCompound());saved.setBoolean("VehicleRampFrozen",true);
            // 2.5 serialized these HashSets in their current coordinate-dependent order.
            NBTTagList reservations=new NBTTagList();
            for(int x=3;x>=0;x--)for(int z=3;z>=0;z--){BlockPos p=origin.add(x,0,z);NBTTagCompound entry=new NBTTagCompound();entry.setInteger("X",p.getX());entry.setInteger("Y",p.getY());entry.setInteger("Z",p.getZ());entry.setLong("Pos",p.toLong());reservations.appendTag(entry);}
            saved.setTag("Cells",reservations);saved.setTag("RampCanopyBlockers",reservations.copy());
            NBTTagCompound original=saved.copy();
            for(BlockPos shift:Arrays.asList(BlockPos.ORIGIN,new BlockPos(-100,-60,40),new BlockPos(250,10,-311))){
                NBTTagCompound local=VehicleTileData.translated(saved,shift);
                TileEntity clone=TileEntity.create(null,local);require(clone instanceof TileEntityRampController,"registered ramp factory");
                VehicleStructure.Cell cell=new VehicleStructure.Cell(origin.add(shift),ModBlocks.PROGRAMMABLE_RAMP.getDefaultState(),local);
                MovingBlockSupport.requireExactTileData(cell,clone);
                require(local.getTagList("Cells",10).tagCount()==16&&local.getTagList("RampCanopyBlockers",10).tagCount()==16,"all reservation and blocker entries retained");
                require(VehicleTileData.translated(local,BlockPos.ORIGIN.subtract(shift)).equals(new VehicleStructure.Cell(origin,cell.state,saved).tileData()),"translation and inverse preserve every saved field");
                NBTTagCompound extra=local.copy();extra.setInteger("UnknownImportantData",42);boolean rejected=false;
                try{MovingBlockSupport.requireExactTileData(new VehicleStructure.Cell(cell.pos,cell.state,extra),clone);}catch(IllegalArgumentException expected){rejected=!expected.getMessage().contains("copied");}
                require(rejected,"unknown data loss still refused with operation-neutral message");
                NBTTagCompound changed=local.copy();changed.setInteger("Drop",local.getInteger("Drop")+1);rejected=false;
                try{MovingBlockSupport.requireExactTileData(new VehicleStructure.Cell(cell.pos,cell.state,changed),clone);}catch(IllegalArgumentException expected){rejected=true;}
                require(rejected,"changed ramp settings still refused");cases++;
            }
            require(saved.equals(original),"legacy source NBT remains immutable");
        }
        for(String name:new String[]{"rocket_thruster_hexagonal","ion_drive_hexagonal","plasma_vent_hexagonal","impulse_engine_hexagonal"})for(EnumFacing facing:EnumFacing.values()){
            Block block=Block.getBlockFromName("vandorlabs:"+name);
            List<VehicleStructure.Cell> cells=Arrays.asList(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PILOT_SEAT.getDefaultState(),null),new VehicleStructure.Cell(new BlockPos(1,0,0),block.getDefaultState().withProperty(BlockPropulsionLight.FACING,facing),null));
            for(boolean display:new boolean[]{false,true}){
                VehicleStructure current=display?VehicleStructure.display(cells):new VehicleStructure(cells,BlockPos.ORIGIN,EnumFacing.NORTH,3);
                NBTTagCompound old=current.write();NBTTagList palette=old.getTagList("Palette",10);
                for(int i=0;i<palette.tagCount();i++)if(palette.getCompoundTagAt(i).getString("Name").equals("vandorlabs:"+name))palette.getCompoundTagAt(i).getCompoundTag("Properties").removeTag("part");
                NBTTagCompound original=old.copy();VehicleStructure upgraded=display?VehicleStructure.readDisplay(old):VehicleStructure.read(old);
                require(upgraded.write().equals(current.write())&&old.equals(original),"2.5 hex palette migrates to SINGLE without editing original NBT");
                for(int i=0;i<palette.tagCount();i++)if(palette.getCompoundTagAt(i).getString("Name").equals("vandorlabs:"+name))palette.getCompoundTagAt(i).getCompoundTag("Properties").setString("part","unknown_part");
                boolean rejected=false;try{if(display)VehicleStructure.readDisplay(old);else VehicleStructure.read(old);}catch(IllegalArgumentException expected){rejected=true;}
                require(rejected,"unknown hex properties remain rejected");cases++;
            }
        }
        System.out.println("[vandorlabs][reprolab] vehicle-upgrade26 PASS cases="+cases+" legacy unordered ramp reservations rebase inverse data-loss negatives four hex families six faces vehicle/display palettes original NBT preserved");
    }
    private VehicleUpgradeChecks(){}
}
