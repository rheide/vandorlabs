package com.vandorlabs.client;

import com.vandorlabs.blocks.ContainedFluids;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.*;
import net.minecraft.util.ResourceLocation;
import java.io.*;

/** Actual Forge two-slot persistence oracle consumed by the native storage check. */
public final class FluidSlotsLegacyChecks {
    public static void main(String[] ignored)throws Exception{
        net.minecraft.init.Bootstrap.register();
        FluidRegistry.registerFluid(new Fluid("vandorlabs:fluid_slots_probe",new ResourceLocation("blocks/water_still"),new ResourceLocation("blocks/water_flow")));
        NBTTagList cases=new NBTTagList();
        for(int container=0;container<4;container++)for(int water=0;water<2;water++){
            NBTTagCompound root=new NBTTagCompound();root.setBoolean("Waterlogged",water!=0);
            if(container==1)root.setString("ContainedFluids","wrong");if(container==2)root.setTag("ContainedFluids",new NBTTagCompound());if(container==3){NBTTagCompound d=new NBTTagCompound();d.setString("Side0","wrong");root.setTag("ContainedFluids",d);}add(cases,root);
        }
        for(String name:new String[]{"water","lava","vandorlabs:fluid_slots_probe","missing:fluid","BAD ID!",""})for(int amount:new int[]{-1,0,1,1000,Integer.MAX_VALUE})for(int type=0;type<3;type++){
            NBTTagCompound raw=new NBTTagCompound();raw.setString("FluidName",name);if(type==0)raw.setInteger("Amount",amount);else if(type==1)raw.setShort("Amount",(short)amount);else raw.setString("Amount",Integer.toString(amount));
            NBTTagCompound opaque=new NBTTagCompound();opaque.setByteArray("bytes",new byte[]{-1,2,3});opaque.setString("unicode","水µ");raw.setTag("Tag",opaque);raw.setLong("Unknown",Long.MIN_VALUE);raw.setTag("nested",opaque.copy());
            NBTTagCompound root=new NBTTagCompound(),data=new NBTTagCompound();data.setTag("Side0",raw);data.setTag("Side1",new NBTTagCompound());root.setTag("ContainedFluids",data);add(cases,root);
        }
        for(int tagType=0;tagType<3;tagType++){NBTTagCompound raw=new NBTTagCompound();raw.setString("FluidName","water");raw.setInteger("Amount",10);if(tagType==1)raw.setString("Tag","wrong");if(tagType==2)raw.setTag("Tag",new NBTTagCompound());NBTTagCompound root=new NBTTagCompound(),data=new NBTTagCompound();data.setTag("Side1",raw);root.setTag("ContainedFluids",data);add(cases,root);}
        NBTTagCompound result=new NBTTagCompound();result.setTag("Cases",cases);File output=new File(System.getProperty("vandorlabs.fluidSlotsReference"));output.getParentFile().mkdirs();try(OutputStream stream=new FileOutputStream(output)){CompressedStreamTools.writeCompressed(result,stream);}System.out.println("PASS: fluid-slots Forge oracle cases="+cases.tagCount());
    }
    private static void add(NBTTagList cases,NBTTagCompound input){
        ContainedFluids fluids=new ContainedFluids(new TileEntity(){});fluids.read(input);NBTTagCompound expected=new NBTTagCompound();fluids.write(expected);
        NBTTagCompound row=new NBTTagCompound();row.setTag("Input",input.copy());row.setTag("Expected",expected);for(int side=0;side<2;side++){row.setBoolean("Occupied"+side,fluids.occupied(side));FluidStack stack=fluids.get(side);if(stack!=null){row.setString("Fluid"+side,FluidRegistry.getFluidName(stack.getFluid()));row.setInteger("Amount"+side,stack.amount);row.setBoolean("HasTag"+side,stack.tag!=null);if(stack.tag!=null)row.setTag("Tag"+side,stack.tag.copy());}}
        cases.appendTag(row);
        if(!expected.getCompoundTag("ContainedFluids").equals(input.getCompoundTag("ContainedFluids"))&&input.hasKey("ContainedFluids",10)&&input.getCompoundTag("ContainedFluids").hasKey("Side0",10))throw new AssertionError("opaque side lost");
        NBTTagCompound output=new NBTTagCompound();fluids.write(output);output.getCompoundTag("ContainedFluids").setString("Side0","mutated");NBTTagCompound after=new NBTTagCompound();fluids.write(after);if(!after.equals(expected))throw new AssertionError("write copy alias");
    }
}
