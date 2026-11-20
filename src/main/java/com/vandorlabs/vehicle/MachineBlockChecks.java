package com.vandorlabs.vehicle;

import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Every installed standalone machine variant, with nonempty owned state and all headings. */
public final class MachineBlockChecks {
    public static void run(WorldServer world){
        int tested=0;Set<String> classes=new TreeSet<>(),failures=new TreeSet<>();
        for(Block block:Block.REGISTRY){String domain=block.getRegistryName().getResourceDomain();if(!domain.equals("thermalexpansion")&&!domain.equals("mekanism"))continue;
            Set<IBlockState> seen=Collections.newSetFromMap(new IdentityHashMap<>());
            for(int meta=0;meta<16;meta++){
                IBlockState state;try{state=block.getStateFromMeta(meta);}catch(RuntimeException unused){continue;}
                if(!seen.add(state)||!block.hasTileEntity(state))continue;TileEntity tile=block.createTileEntity(world,state);if(!MachineBlockCompat.supported(tile))continue;
                String type=tile.getClass().getName();classes.add(type);
                for(EnumFacing facing:EnumFacing.values())try{
                    tile=block.createTileEntity(world,state);tile.setWorld(world);tile.setPos(BlockPos.ORIGIN);
                    if(tile instanceof net.minecraft.inventory.IInventory&&((net.minecraft.inventory.IInventory)tile).getSizeInventory()>0)((net.minecraft.inventory.IInventory)tile).setInventorySlotContents(0,new net.minecraft.item.ItemStack(net.minecraft.init.Items.DIAMOND,3));
                    NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());
                    if(saved.hasKey("Facing",1))saved.setByte("Facing",(byte)facing.getIndex());if(saved.hasKey("facing",3))saved.setInteger("facing",facing.getIndex());
                    if(saved.hasKey("Energy",3))saved.setInteger("Energy",1234);if(saved.hasKey("electricityStored",6))saved.setDouble("electricityStored",2345);
                    if(saved.hasKey("SideCache",7)){byte[] sides=saved.getByteArray("SideCache");for(int i=0;i<sides.length;i++)sides[i]=(byte)(i%3);saved.setByteArray("SideCache",sides);}
                    tile.readFromNBT(saved);saved=tile.writeToNBT(new NBTTagCompound());
                    VehicleStructure source=VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,state,saved))),cycle=source;
                    MovingBlockSupport.validate(source,new VehicleWorld(world,source));
                    for(int turn=0;turn<4;turn++){cycle=VehicleRotation.rotate(cycle,new VehicleWorld(world,cycle),1);MovingBlockSupport.validate(cycle,new VehicleWorld(world,cycle));}
                    if(cycle.cells.get(0).state!=source.cells.get(0).state||!cycle.cells.get(0).tileData().equals(saved))throw new IllegalStateException("four-turn own state mismatch");tested++;
                }catch(RuntimeException|LinkageError failure){if(failures.size()<20)failures.add(type+" "+state+" facing="+facing+": "+failure);}
            }
        }
        if(!failures.isEmpty())throw new IllegalStateException("Installed standalone machine failures="+failures);
        if(tested==0)throw new IllegalStateException("No installed standalone machine fixtures");
        System.out.println("[vandorlabs] arm-machine-matrix PASS variants/faces="+tested+" classes="+classes);
    }
    private MachineBlockChecks(){}
}
