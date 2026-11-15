package com.vandorlabs.gear;

import com.vandorlabs.gear.core.GearTopology;
import java.lang.reflect.Field;
import java.util.Collections;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

/** Settings availability and endpoint guards apply to every supported landing-foot family. */
public final class LandingFootDirectionChecks {
    private static int checks;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Landing Foot direction: "+why);}
    private static GearTileEntity tile(GearTopology.FootKind kind)throws Exception{
        GearTopology.Result result=GearTopology.validate(2,kind,Collections.singletonList(new GearTopology.Cell(0,-2,0)));
        need(result.isValid(),"valid "+kind+" fixture");GearTileEntity tile=new GearTileEntity();Field field=GearTileEntity.class.getDeclaredField("assembly");field.setAccessible(true);field.set(tile,result.assembly());return tile;
    }
    public static void main(String[] args)throws Exception{
        Bootstrap.register();
        for(GearTopology.FootKind kind:new GearTopology.FootKind[]{GearTopology.FootKind.TRACK,GearTopology.FootKind.SKI,GearTopology.FootKind.WHEEL,GearTopology.FootKind.PLATE}){
            GearTileEntity tile=tile(kind);need(tile.canConfigureFootFacing(),"dialog exposes Foot for "+kind);
            for(int direction=0;direction<4;direction++){NBTTagCompound request=new NBTTagCompound();request.setInteger("FootFacing",direction);request.setBoolean("Deployed",true);tile.configure(request);need(tile.footFacing()==EnumFacing.getHorizontal(direction),kind+" applies independent direction "+direction);}
            Field progress=GearTileEntity.class.getDeclaredField("progress");progress.setAccessible(true);progress.setDouble(tile,.5);
            NBTTagCompound request=new NBTTagCompound();request.setInteger("FootFacing",0);boolean refused=false;try{tile.configure(request);}catch(IllegalArgumentException expected){refused=true;}
            need(refused&&tile.footFacing()==EnumFacing.getHorizontal(3),kind+" refuses topology edit during motion");
        }
        need(!tile(GearTopology.FootKind.NONE).canConfigureFootFacing(),"bare arm has no landing Foot option");
        System.out.println("Landing Foot direction PASS checks="+checks+" Tracks/Skis/Wheels/Plates/allDirections/movingGuard");
    }
    private LandingFootDirectionChecks(){}
}
