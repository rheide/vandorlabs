package com.vandorlabs.client;

import com.vandorlabs.animation.ReversibleVisualPose;
import com.vandorlabs.animation.VisualClock;
import com.vandorlabs.cockpit.CockpitActuator;
import com.vandorlabs.tiles.TileEntityCockpit;
import net.minecraft.nbt.NBTTagCompound;

/** Reproduce remote clock jumps and delayed/repeated tile updates without a renderer. */
public final class CockpitVisualMotionChecks {
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException("cockpit visual motion: "+text);}
    public static void main(String[] args){
        net.minecraft.init.Bootstrap.register();
        for(String id:new String[]{"cockpit","cockpit_single","chair_siderests","yoke_screen"}){
            com.vandorlabs.cockpit.CockpitData data=com.vandorlabs.cockpit.CockpitData.get(id);
            require(!data.cells.isEmpty()&&!data.boxes.isEmpty(),"complete variant geometry "+id);
            if(id.startsWith("cockpit")){require(data.seat!=null&&Double.isFinite(data.riderY),"variant rider metadata "+id);require(data.pivots.keySet().stream().filter(part->part.startsWith("throttle_")).count()==(id.equals("cockpit_single")?1:2),"variant physical grip metadata "+id);}
            for(com.vandorlabs.cockpit.CockpitData.Surface surface:data.surfaces){com.vandorlabs.cockpit.CockpitScreenLayout layout=new com.vandorlabs.cockpit.CockpitScreenLayout(surface,3);require(Math.abs((surface.u.lengthVector()/128)/(surface.v.lengthVector()/layout.height)-1)<.015,"square display pixel scale "+id+surface.id);}
        }
        CockpitActuator legacy=new CockpitActuator();legacy.toggle(1000);
        double skipped=legacy.sample(1028),rewound=legacy.sample(1003);
        System.out.println("Legacy world-clock reproduction: first pose="+skipped+" corrected pose="+rewound);
        require(skipped>.98&&rewound<.03,"negative control must reproduce skipped and rewound motion");
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
        TileEntityCockpit tile=new TileEntityCockpit();tile.setWorld(world);
        require(CockpitVisualMotion.sampleAt(tile,true,0,0)==0,"closed initial state");
        tile.screenLift.toggle(1000);NBTTagCompound tag=new NBTTagCompound();tile.screenLift.write(tag,"CockpitScreenLift");
        require(CockpitVisualMotion.sampleAt(tile,true,0,0)==0,"target change starts continuously");
        double previous=0;int distinct=0;
        for(int frame=1;frame<=240;frame++){
            world.time=frame%3==0?1000000:frame%3==1?-1000000:1003;
            if(frame%17==0)tile.readFromNBT(tag.copy());
            if(frame==120){tile=new TileEntityCockpit();tile.readFromNBT(tag.copy());tile.setWorld(world);}
            double now=frame/8D,value=CockpitVisualMotion.sampleAt(tile,true,now,0),t=now/30,expected=t*t*(3-2*t);
            require(Math.abs(value-expected)<1e-10,"clock correction or packet reset at frame "+frame);
            require(value>=previous,"pose rewound");if(value>previous)distinct++;previous=value;
        }
        require(distinct==240&&previous==1,"all sub-tick frames remain distinct");
        require(CockpitVisualMotion.sampleAt(tile,false,30,0)==0,"screen movement must not move seat");
        tile.seatSlide.toggle(1000);require(CockpitVisualMotion.sampleAt(tile,false,30,0)==0,"seat target start");
        double before=CockpitVisualMotion.sampleAt(tile,false,42,0);
        tile.seatSlide.toggle(1001);require(Math.abs(CockpitVisualMotion.sampleAt(tile,false,42,0)-before)<1e-12,"continuous reversal");
        require(CockpitVisualMotion.sampleAt(tile,false,72,0)==0,"reverse completes");
        TileEntityCockpit other=new TileEntityCockpit();other.setWorld(new NonRenderingChecks.MemoryWorld(true));
        require(CockpitVisualMotion.sampleAt(other,true,72,0)==0,"world isolation");
        ReversibleVisualPose pose=new ReversibleVisualPose(30);pose.sample(false,0,0);pose.sample(true,0,0);
        double current=pose.sample(true,15,0);require(pose.sample(true,14,0)==current,"local clock cannot rewind");
        require(pose.sample(true,15,-1)<current,"previous rider position uses previous pose");
        require(pose.sample(true,15,0)==current,"previous-pose query cannot mutate current motion");
        VisualClock clock=new VisualClock();clock.sample(0,false);double active=clock.sample(500000000,false);
        require(clock.sample(1500000000L,true)==active&&clock.sample(2500000000L,false)==active,"pause and resume do not jump");
        System.out.println("Cockpit visual motion PASS frames="+distinct+" clock-jumps duplicate-updates replaced-vehicle-tiles reversal rider-history pause world-isolation");
    }
}
