package com.vandorlabs.copier;

import com.vandorlabs.vehicle.VehicleStructure;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;

/** Empty the copied resource owners without touching the source, configuration, or item inventories. */
final class CopyStorage {
    private CopyStorage() {}

    static boolean mekanismStore(TileEntity tile) {
        if(tile==null)return false;
        String name=tile.getClass().getName();
        return name.equals("mekanism.common.tile.TileEntityFluidTank")||name.equals("mekanism.common.tile.TileEntityEnergyCube");
    }

    static void requireAccess(TileEntity tile,EntityPlayer actor) {
        if(!mekanismStore(tile))throw new IllegalArgumentException("Mekanism storage changed; copy again");
        final boolean allowed;
        try{
            Class<?> security=Class.forName("mekanism.common.util.SecurityUtils",false,tile.getClass().getClassLoader());
            allowed=(Boolean)security.getMethod("canAccess",EntityPlayer.class,TileEntity.class).invoke(null,actor,tile);
        }catch(ReflectiveOperationException|LinkageError failure){throw new IllegalArgumentException("Cannot verify security on this installed Mekanism storage",failure);}
        if(!allowed)throw new IllegalArgumentException("You do not have access to this Mekanism storage");
    }

    static VehicleStructure survival(VehicleStructure source) {
        List<VehicleStructure.Cell> cells=new ArrayList<>(source.cells.size());
        boolean changed=false;
        for(VehicleStructure.Cell cell:source.cells){
            NBTTagCompound data=cell.tileData();
            if(data!=null&&empty(data,0)){
                changed=true;
                cells.add(new VehicleStructure.Cell(cell.pos,cell.state,data));
            }else cells.add(cell);
        }
        return changed?VehicleStructure.display(cells):source;
    }

    private static boolean empty(NBTTagCompound data,int depth) {
        if(depth>=8)throw new IllegalArgumentException("Captured material nesting exceeds eight structures");
        String id=data.getString("id");
        boolean changed=false;
        if(id.equals("vandorlabs:battery")&&data.getInteger("Energy")>0){data.setInteger("Energy",0);changed=true;}
        if(id.equals("vandorlabs:hex_tank")&&data.getCompoundTag("Tank").hasKey("FluidName",8)){
            NBTTagCompound tank=new NBTTagCompound();
            tank.setString("Empty","");
            data.setTag("Tank",tank);
            changed=true;
        }
        if(id.equals("vandorlabs:constructor")&&data.hasKey("MaxwellFuel",10)){
            data.removeTag("MaxwellFuel");
            data.removeTag("MaxwellBurnUnits");
            data.removeTag("MaxwellBurnMicro");
            data.removeTag("MaxwellActiveLevel");
            changed=true;
        }
        if(id.equals("mekanism:fluid_tank")&&data.hasKey("fluidTank",10)){data.removeTag("fluidTank");changed=true;}
        if(id.equals("mekanism:energy_cube")&&data.getDouble("electricityStored")>0){data.setDouble("electricityStored",0);changed=true;}
        if(id.startsWith("thermaldynamics:duct_fluid_")||id.startsWith("thermaldynamics:duct_energy_")){
            NBTTagCompound energy=data.getCompoundTag("Energy");
            if(energy.hasKey("Energy")){energy.removeTag("Energy");changed=true;}
            NBTTagCompound fluid=data.getCompoundTag("Fluid");
            for(String key:new String[]{"FluidName","Amount","Tag","ConnFluid"})if(fluid.hasKey(key)){
                fluid.removeTag(key);
                changed=true;
            }
        }
        if(id.startsWith("vandorlabs:")){
            NBTTagCompound payload=data.getCompoundTag("ArmPayload");
            changed|=emptyTiles(payload.getCompoundTag("Blocks").getTagList("Blocks",10),depth);
            changed|=emptyTiles(data.getTagList("RampSourceTiles",10),depth);
        }
        return changed;
    }

    private static boolean emptyTiles(NBTTagList rows,int depth) {
        boolean changed=false;
        for(int i=0;i<rows.tagCount();i++){
            NBTTagCompound row=rows.getCompoundTagAt(i);
            if(row.hasKey("Tile",10))changed|=empty(row.getCompoundTag("Tile"),depth+1);
        }
        return changed;
    }
}
