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
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("Cockpit controls: "+message);}
}
