package com.vandorlabs.vehicle;

import com.vandorlabs.config.BlockAvailability;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;

/** Real startup config files govern admission, never saved ownership or recovery. */
public final class VehicleArticulationLimitChecks {
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("Articulation config: "+why);}
    private static VehicleStructure train(WorldServer world,int bodies,boolean parallel){int[] lift=new int[bodies];Arrays.fill(lift,3);return VehicleCouplerSectionChecks.train(world,lift,parallel,false,false,true,true);}
    private static void limit(Path file,int value)throws Exception{
        Files.write(file,("vehicles {\n I:maxArticulationPoints="+value+"\n}\n").getBytes(StandardCharsets.UTF_8));BlockAvailability.load(file.toFile());
    }
    private static void admission(WorldServer world,int bodies,boolean parallel,boolean allowed){
        boolean accepted=true;try{VehicleArticulation.requireAssemblyLimit(train(world,bodies,parallel));}catch(IllegalArgumentException refusal){require(refusal.getMessage().contains("configured limit"),"specific admission error");accepted=false;}
        require(accepted==allowed,"bodies="+bodies+" parallel="+parallel+" limit="+BlockAvailability.vehicleMaxArticulationPoints());
    }
    public static void run(WorldServer world)throws Exception{
        Path directory=Files.createTempDirectory(world.getSaveHandler().getWorldDirectory().toPath(),"articulation-config-");Path file=directory.resolve("blocks.cfg");
        java.io.File original=new java.io.File(net.minecraftforge.fml.common.Loader.instance().getConfigDir(),"vandorlabs-blocks.cfg");
        try{
            BlockAvailability.load(file.toFile());require(BlockAvailability.vehicleMaxArticulationPoints()==7,"absent option preserves previous eight-body limit");require(new String(Files.readAllBytes(file),StandardCharsets.UTF_8).contains("I:maxArticulationPoints=7"),"default written to real config");
            limit(file,0);admission(world,1,false,true);admission(world,2,false,false);admission(world,3,true,true);
            limit(file,1);admission(world,2,false,true);admission(world,3,false,false);
            limit(file,7);admission(world,8,false,true);admission(world,9,false,false);
            limit(file,8);admission(world,9,false,true);
            EntityGroundVehicle source=new EntityGroundVehicle(world),restored=new EntityGroundVehicle(world);
            try{
                source.install(train(world,9,false));source.setPosition(1500,100,1500);NBTTagCompound saved=source.writeToNBT(new NBTTagCompound());
                limit(file,0);restored.readFromNBT(saved);
                require(restored.structure!=null&&restored.structure.write().equals(source.structure.write())&&restored.articulation.jointCount()==8,"lowered limit preserves complete saved vehicle and eight hinges");
                require(restored.articulation.parkable(),"lowered admission limit does not reject parking alignment");
                for(int x=1500>>4;x<=(1500+(int)restored.structure.bounds.maxX)>>4;x++)for(int z=1500>>4;z<=(1500+(int)restored.structure.bounds.maxZ)>>4;z++)world.getChunkFromChunkCoords(x,z);
                VehicleRotation.plan(restored);
            }finally{source.setDead();restored.setDead();}
            limit(file,-1);require(BlockAvailability.vehicleMaxArticulationPoints()==0,"negative input bounded");
            limit(file,VehicleArticulation.MAX_JOINTS+1);require(BlockAvailability.vehicleMaxArticulationPoints()==VehicleArticulation.MAX_JOINTS,"oversized input bounded by saved/network joint capacity");
        }finally{BlockAvailability.load(original);Files.deleteIfExists(file);Files.deleteIfExists(directory);}
        System.out.println("[vandorlabs][reprolab] articulation-config PASS generated default7 zero/one/seven/eight limits real hinge counts rigid parallel links saved nine-body reload after lowering range bounds");
    }
    private VehicleArticulationLimitChecks(){}
}
