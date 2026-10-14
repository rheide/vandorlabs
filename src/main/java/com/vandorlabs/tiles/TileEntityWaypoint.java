package com.vandorlabs.tiles;

import net.minecraft.nbt.NBTTagCompound;

/** Passive marker: the visiting vehicle drives arrival timers, never a tile ticker. */
public final class TileEntityWaypoint extends TileEntityAutopilot {
    public boolean autoDepart=true;
    public int delay=100,stopMode=1,departMode=1,hoverHeight;
    public boolean directSignal=true;
    private boolean emitting;
    @Override public boolean hasLocalRedstoneSignal(){return emitting;}
    public void emit(){emitting=true;com.vandorlabs.redstone.RedstoneChannels.register(this);com.vandorlabs.redstone.RedstoneChannels.inputChanged(this);world.notifyNeighborsOfStateChange(pos,getBlockType(),false);world.scheduleUpdate(pos,getBlockType(),4);}
    public void endPulse(){emitting=false;com.vandorlabs.redstone.RedstoneChannels.unregister(this);world.notifyNeighborsOfStateChange(pos,getBlockType(),false);}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound n){super.writeToNBT(n);n.setBoolean("WaypointAuto",autoDepart);n.setBoolean("WaypointDirect",directSignal);n.setInteger("WaypointDelay",delay);n.setInteger("WaypointStop",stopMode);n.setInteger("WaypointDepart",departMode);n.setInteger("WaypointHeight",hoverHeight);return n;}
    @Override public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);autoDepart=!n.hasKey("WaypointAuto")||n.getBoolean("WaypointAuto");directSignal=!n.hasKey("WaypointDirect")||n.getBoolean("WaypointDirect");delay=Math.max(0,Math.min(72000,n.hasKey("WaypointDelay")?n.getInteger("WaypointDelay"):100));stopMode=Math.max(0,Math.min(2,n.hasKey("WaypointStop")?n.getInteger("WaypointStop"):1));departMode=Math.max(1,Math.min(2,n.hasKey("WaypointDepart")?n.getInteger("WaypointDepart"):1));hoverHeight=Math.max(0,Math.min(16,n.getInteger("WaypointHeight")));}
    @Override public void setChannelSignal(boolean value){} // Waypoints emit only while occupied.
    @Override public void onLoad(){} // No bus subscription or timer when unoccupied.
}
