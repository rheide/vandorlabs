package com.vandorlabs.client;

import com.vandorlabs.vehicle.VehicleWorld;
import java.lang.reflect.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Initialize carried Galacticraft displays without native load callbacks or client packets. */
public final class ForeignBuildingVisuals {
    private static final String SCREEN="micdoodle8.mods.galacticraft.core.tile.TileEntityScreen";
    private static Field screenField;
    public static void prepare(VehicleWorld view){
        World parent=view.parent;while(parent instanceof VehicleWorld)parent=((VehicleWorld)parent).parent;
        if(!parent.isRemote)return;
        for(TileEntity tile:view.tiles.values())if(tile.getClass().getName().equals(SCREEN))try{
            if(screenField==null)screenField=tile.getClass().getField("screen");
            if(screenField.get(tile)!=null)continue;
            NBTTagCompound data=tile.writeToNBT(new NBTTagCompound());
            int left=data.getInteger("connectionsLeft"),right=data.getInteger("connectionsRight"),up=data.getInteger("connectionsUp"),down=data.getInteger("connectionsDown");
            EnumFacing facing=EnumFacing.getFront(tile.getBlockMetadata());
            EnumFacing side=facing.getAxis().isHorizontal()?facing.rotateYCCW():EnumFacing.WEST;
            BlockPos root=tile.getPos().offset(side,left).down(down);TileEntity owner=view.getTileEntity(root);
            if(owner==null||!owner.getClass().getName().equals(SCREEN))owner=tile;
            Field screen=screenField;Object draw=screen.get(owner);
            float width=1+left+right,height=1+up+down;
            if(draw==null||!((Boolean)draw.getClass().getMethod("check",float.class,float.class).invoke(draw,width,height))){
                Class<?> type=screen.getType();Constructor<?> found=null;
                for(Constructor<?> c:type.getConstructors())if(c.getParameterCount()==3&&c.getParameterTypes()[0]==float.class&&c.getParameterTypes()[1]==float.class&&c.getParameterTypes()[2].isInstance(owner)){found=c;break;}
                if(found==null)throw new IllegalStateException("Unsupported Galacticraft screen constructor");
                draw=found.newInstance(width,height,owner);screen.set(owner,draw);
            }
            screen.set(tile,draw);tile.getClass().getField("screenOffsetx").setInt(tile,left);tile.getClass().getField("screenOffsetz").setInt(tile,up);
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot initialize carried Galacticraft display",error);}
    }
    private ForeignBuildingVisuals(){}
}
