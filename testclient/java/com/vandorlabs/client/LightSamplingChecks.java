package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityProgrammableLight;
import net.minecraft.nbt.NBTTagCompound;

/** Shared policy must retain lazy sampling, including manual and already-joined render reads. */
public final class LightSamplingChecks {
    private static final class Probe extends TileEntityProgrammableLight {
        int levels,power;
        @Override public int getSignalLevel(){levels++;return 10;}
        @Override protected boolean isTriggerPowered(){power++;return true;}
    }
    private static void field(Probe tile,String name,Object value)throws Exception {
        java.lang.reflect.Field field=TileEntityProgrammableLight.class.getDeclaredField(name);
        field.setAccessible(true);field.set(tile,value);
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.init.Bootstrap.register();
        for(int mode=0;mode<5;mode++){
            Probe tile=new Probe();boolean numeric=mode>=3,joined=mode==2||mode==4;
            field(tile,"signalBrightness",numeric);field(tile,"join",joined);field(tile,"trigger",mode==1||mode==2?1:0);field(tile,"joinedLevel",7);
            for(int n=0;n<1000;n++){tile.getLightLevel();tile.isOn();}
            int expectedLevels=mode==3?2000:0,expectedPower=mode==1?1000:0;
            if(tile.levels!=expectedLevels||tile.power!=expectedPower)throw new AssertionError("Light input sampling mode="+mode+" levels="+tile.levels+" expected="+expectedLevels+" power="+tile.power+" expected="+expectedPower);
        }
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityProgrammableLight.class,new net.minecraft.util.ResourceLocation("vandorlabs","light_sampling_reference"));
        TileEntityProgrammableLight tile=new TileEntityProgrammableLight();tile.setJoinedSignalLevel(123);
        NBTTagCompound tag=tile.writeToNBT(new NBTTagCompound());
        if(tag.getInteger("JoinedSignalLevel")!=123)throw new AssertionError("Legacy live setter values must remain saveable");
        tile.readFromNBT(tag);
        if(tile.writeToNBT(new NBTTagCompound()).getInteger("JoinedSignalLevel")!=15)throw new AssertionError("Saved input normalization must remain at read boundary");
        System.out.println("Forge Light shared policy PASS lazyReads=5000 exact-input-counts raw-save/read-normalization");
    }
}
