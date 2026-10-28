package com.vandorlabs.client;

import com.vandorlabs.cockpit.*;
import com.vandorlabs.container.ContainerCockpit;
import com.vandorlabs.redstone.*;
import com.vandorlabs.tiles.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.*;
import net.minecraft.util.math.*;

/** Posed targeting, four-position outputs, malformed edits and saved layout compatibility. */
final class CockpitControlRuntimeChecks {
    static void run(TileEntityCockpit tile,EntityPlayerMP player){
        if(!tile.design().equals("cockpit"))return;
        NBTTagCompound before=tile.writeToNBT(new NBTTagCompound());ContainerCockpit dialog=new ContainerCockpit(tile,null);
        try{
            sharedModule(tile);
            for(int count=1;count<=2;count++){
                NBTTagCompound mode=new NBTTagCompound();mode.setInteger("ThrottleCount",count);dialog.update(player,0,mode);
                require(tile.throttleCount()==count,"server layout edit");
                require(tile.data().pivots.keySet().stream().filter(id->id.startsWith("throttle_")).count()==count,"physical grip count");
                require(tile.controls.stream().filter(c->c.isThrottle()).count()==2,"both saved controls retained");
                require(tile.data().cells.equals(CockpitData.get("cockpit").cells),"layout keeps occupied cells");
                int base=2;
                for(int i=0;i<tile.data().surfaces.size();i++){
                    CockpitData.Surface surface=tile.data().surfaces.get(i);TileEntityCockpit.Display display=tile.display(surface.id);display.active=false;display.texture=-1;
                    Vec3d p=surface.point(.5,.5),start=CockpitData.rotate(p.add(surface.normal.scale(.1)),tile.facing()),end=CockpitData.rotate(p.subtract(surface.normal.scale(.02)),tile.facing());
                    require(tile.configurationPage(start,end)==base+i,"blank/inactive screen configuration target "+surface.id);
                    CockpitScreenLayout layout=new CockpitScreenLayout(surface,3);
                    require(Math.abs((surface.u.lengthVector()/128)/(surface.v.lengthVector()/layout.height)-1)<.015,"square display pixels "+surface.id);
                    display.active=true;display.texture=TileEntityAnimatedScreenSelector.REDSTONE_SURFACE;
                    for(int row=0;row<3;row++){
                        TileEntityCockpit.Control c=tile.control(surface.id+"/"+row);c.kind=1;
                        for(int segment=0;segment<4;segment++){
                            double u=(60+(segment+.5)*15)/128D,v=1-(row+.5)/3;
                            p=surface.point(u,v);start=CockpitData.rotate(p.add(surface.normal.scale(.02)),tile.facing());end=CockpitData.rotate(p.subtract(surface.normal.scale(.02)),tile.facing());
                            require(tile.use(start,end,player)&&c.level==c.sliderLevel(segment),"visible slider segment maps to output "+surface.id+"/"+row+"/"+segment);
                        }
                    }
                    display.texture=-1;
                }
                for(String id:new String[]{"throttle_left","throttle_right"}){
                    TileEntityCockpit.Control c=tile.control(id);int index=tile.controls.indexOf(c),channel=id.endsWith("left")?65501:65502;
                    NBTTagCompound settings=new NBTTagCompound();settings.setInteger("Control",index);settings.setInteger("Kind",0);settings.setString("Label","Throttle");settings.setString("Channels",Integer.toString(channel));settings.setInteger("Min",3);settings.setInteger("Max",12);
                    dialog.update(player,0,settings);require(c.kind==1&&c.min==3&&c.max==12,"throttle limits and forced four-step mode");
                    for(int step=0;step<4;step++){
                        NBTTagCompound operation=new NBTTagCompound();operation.setInteger("Control",index);operation.setDouble("Column",step/3D);dialog.update(player,1,operation);
                        require(c.level==TileEntitySignalControl.levelForStep(step,3,12),"Thruster Lever output parity");
                        require(tile.visualDetent(id,0)==step,"custom output keeps exact visual detent");
                        require(RedstoneChannels.level(tile.getWorld(),channel)==(c.active()?c.level:0),"inactive right throttle contributes no signal");
                        if(!tile.data().pivots.containsKey(id))continue;
                        Vec3d eye=new Vec3d(tile.seatLocation().x,tile.data().riderY+player.getEyeHeight(),tile.seatLocation().z),pivot=tile.data().pivots.get(id);
                        double angle=Math.toRadians(22.5-step*22.5);Vec3d target=pivot.addVector(0,Math.cos(angle)*4.5/16,Math.sin(angle)*4.5/16+tile.seatSlide(0)),end=eye.add(target.subtract(eye).normalize().scale(8));
                        require(tile.configurationPage(CockpitData.rotate(eye,tile.facing()),CockpitData.rotate(end,tile.facing()))==base+tile.displays.size()+index,"grip configuration page "+id);
                        require(tile.use(CockpitData.rotate(eye,tile.facing()),CockpitData.rotate(end,tile.facing()),player)&&c.level==c.sliderLevel((step+1)%4),"custom limits cycle every physical position");
                    }
                    NBTTagCompound stable=tile.writeToNBT(new NBTTagCompound());settings.setInteger("Min",0);dialog.update(player,0,settings);require(stable.equals(tile.writeToNBT(new NBTTagCompound())),"invalid limits rejected before mutation");
                }
                TileEntityCockpit copy=new TileEntityCockpit();copy.readFromNBT(tile.writeToNBT(new NBTTagCompound()));
                require(copy.throttleCount()==count&&copy.control("throttle_right").channels.equals(tile.control("throttle_right").channels),"layout and hidden control survive NBT");
                NBTTagCompound invalid=new NBTTagCompound();invalid.setInteger("ThrottleCount",3);dialog.update(player,0,invalid);require(tile.throttleCount()==count,"invalid layout rejected");
            }
            tile.setThrottleCount(1);TileEntityCockpit.Control right=tile.control("throttle_right");right.activate(1);require(RedstoneChannels.level(tile.getWorld(),65502)==0,"hidden throttle suppressed");tile.setThrottleCount(2);require(RedstoneChannels.level(tile.getWorld(),65502)==right.level,"hidden throttle restored");
            NBTTagCompound legacy=tile.writeToNBT(new NBTTagCompound());legacy.removeTag("CockpitThrottles");NBTTagList controls=legacy.getTagList("CockpitControls",10);for(int i=0;i<controls.tagCount();i++)controls.getCompoundTagAt(i).setInteger("Kind",0);
            TileEntityCockpit loaded=new TileEntityCockpit();loaded.readFromNBT(legacy);require(loaded.throttleCount()==2&&loaded.control("throttle_left").kind==1,"legacy twin default and toggle repair");
        }finally{tile.readFromNBT(before);}
        System.out.println("[vandorlabs][reprolab] cockpit-controls PASS single/twin layouts, four positions, custom limits, screen/grip pages, slider hits, persistence and rejected edits "+tile.facing());
    }
    static void install(com.vandorlabs.redstone.RedstoneChannelMember member,int tier,ChannelList channels){net.minecraft.item.ItemStack stack=new net.minecraft.item.ItemStack(com.vandorlabs.items.ModItems.REDSTONE_TRANSMITTER,1,tier);com.vandorlabs.items.ItemRedstoneTransmitter.configure(stack,channels);TransmitterSlots.set(member,stack);}
    private static void sharedModule(TileEntityCockpit tile){
        TransmitterSlots.set(tile,net.minecraft.item.ItemStack.EMPTY);
        TileEntityCockpit.Control left=tile.control("throttle_left"),right=tile.control("throttle_right");
        left.setRedstoneChannels(ChannelList.of(65491));right.setRedstoneChannels(ChannelList.of(65492));
        require(left.transmissionRange()<0&&right.transmissionRange()<0,"no cockpit module disables all controls");
        install(tile,0,ChannelList.of(65493));
        require(left.transmissionRange()==16&&right.transmissionRange()==16,"one shared range for both throttles");
        require(left.transmittedChannels().equals(ChannelList.of(65491))&&right.transmittedChannels().equals(ChannelList.of(65492)),"shared module preserves independent targets");
        right.setRedstoneChannels(ChannelList.EMPTY);require(right.transmittedChannels().equals(ChannelList.of(65493)),"blank control uses module defaults");
        left.setLocalLevel(8);right.setLocalLevel(4);TileEntityCockpit copy=new TileEntityCockpit();copy.readFromNBT(tile.writeToNBT(new NBTTagCompound()));
        require(copy.control("throttle_left").latchedLevel(65491)==8&&copy.control("throttle_right").latchedLevel(65493)==4,"explicit and default channel banks survive reload");
        TransmitterSlots.set(tile,net.minecraft.item.ItemStack.EMPTY);copy.readFromNBT(tile.writeToNBT(new NBTTagCompound()));require(TransmitterSlots.get(copy).isEmpty(),"removed shared module stays empty after reload");
        NBTTagCompound legacy=tile.writeToNBT(new NBTTagCompound());legacy.removeTag(TransmitterSlots.SCHEMA);legacy.removeTag("ForgeData");
        NBTTagCompound migrated=TransmitterSlots.normalize(legacy);TileEntityCockpit upgraded=new TileEntityCockpit();upgraded.readFromNBT(migrated);
        require(TransmitterSlots.get(upgraded).getMetadata()==2&&TransmitterSlots.savedSlots(upgraded).getKeySet().size()==1,"legacy cockpit gets one shared Long Range module");
        require(upgraded.control("throttle_left").channels.equals(left.channels),"legacy independent target retained");
        NBTTagCompound forge=new NBTTagCompound(),slots=new NBTTagCompound();net.minecraft.item.ItemStack shortItem=new net.minecraft.item.ItemStack(com.vandorlabs.items.ModItems.REDSTONE_TRANSMITTER,1,0),longItem=new net.minecraft.item.ItemStack(com.vandorlabs.items.ModItems.REDSTONE_TRANSMITTER,1,2);
        com.vandorlabs.items.ItemRedstoneTransmitter.configure(shortItem,ChannelList.of(65494));com.vandorlabs.items.ItemRedstoneTransmitter.configure(longItem,ChannelList.of(65495));slots.setTag("cockpit_throttle_left",shortItem.writeToNBT(new NBTTagCompound()));slots.setTag("cockpit_throttle_right",longItem.writeToNBT(new NBTTagCompound()));forge.setTag("VandorTransmitters",slots);legacy.setTag("ForgeData",forge);
        upgraded.readFromNBT(TransmitterSlots.normalize(legacy));require(TransmitterSlots.get(upgraded).getMetadata()==2&&TransmitterSlots.savedSlots(upgraded).getKeySet().size()==2,"existing cockpit modules move strongest to main and preserve recoverable extras exactly once");
        require(!TransmitterSlots.savedSlots(upgraded).hasKey("cockpit_throttle_right")&&TransmitterSlots.savedSlots(upgraded).hasKey("cockpit_throttle_left"),"promoted item has one physical owner");
        install(tile,2,ChannelList.EMPTY);
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("Cockpit controls: "+message);}
}
