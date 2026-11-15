package com.vandorlabs.rack;

import com.vandorlabs.tiles.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Count navigation controllers, retaining legacy saved blocks without treating an empty Rack as an Autopilot. */
public final class ModuleAutopilots {
    private ModuleAutopilots() { }
    public static int count(TileEntity tile) {
        return tile instanceof TileEntityModuleRack ? ((TileEntityModuleRack)tile).autopilotCount()
                : tile instanceof TileEntityAutopilot && !(tile instanceof TileEntityWaypoint) ? 1 : 0;
    }
    public static int count(NBTTagCompound tile) {
        if(tile==null)return 0;
        if("vandorlabs:autopilot".equals(tile.getString("id")))return 1;
        if(!"vandorlabs:module_rack".equals(tile.getString("id")))return 0;
        int count=0; NBTTagList items=tile.getTagList("Items",10); boolean[] occupied=new boolean[4];
        for(int index=0;index<items.tagCount();index++) {
            NBTTagCompound item=items.getCompoundTagAt(index);int slot=item.getByte("Slot")&255;
            if(slot<4 && !occupied[slot] && item.getByte("Count")>0) {
                occupied[slot]=true; if(ModuleRackPolicy.AUTOPILOT_ID.equals(item.getString("id")))count++;
            }
        }
        return count;
    }
}
