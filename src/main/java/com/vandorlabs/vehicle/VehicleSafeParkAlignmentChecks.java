package com.vandorlabs.vehicle;

import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Loaded version-one joint data must survive alignment preparation unchanged for rollback. */
public final class VehicleSafeParkAlignmentChecks {
    private static int checks;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new IllegalStateException("Safe Park alignment: "+why);}
    public static void run(WorldServer world){
        VehicleStructure initial=VehicleCouplerSectionChecks.train(world,new int[]{3,3},false,false,false,true,true);
        com.vandorlabs.tiles.TileEntityVehicleController controller=new com.vandorlabs.tiles.TileEntityVehicleController();controller.setPos(initial.controller().pos);NBTTagCompound control=controller.writeToNBT(new NBTTagCompound());
        NBTTagCompound caps=new NBTTagCompound(),opaque=new NBTTagCompound();opaque.setString("private","retained opaque previous-release capability");opaque.setByteArray("bytes",new byte[]{0,1,-1,34});caps.setTag("foreign:controller",opaque);control.setTag("ForgeCaps",caps);
        initial=VehicleSeatSettings.replaceAt(initial,initial.controller().pos,control,BlockPos.ORIGIN);
        EntityGroundVehicle craft=new EntityGroundVehicle(world);
        try{
            craft.install(initial);craft.setPosition(1000,130,1000);craft.rotationYaw=37;
            NBTTagCompound saved=new NBTTagCompound(),pose=new NBTTagCompound(),joint=new NBTTagCompound();NBTTagList joints=new NBTTagList();
            pose.setInteger("Version",1);joint.setLong("A",new BlockPos(1,0,4).toLong());joint.setLong("B",new BlockPos(1,0,5).toLong());joint.setDouble("Yaw",20);joint.setDouble("Pitch",-10);joints.appendTag(joint);pose.setTag("Joints",joints);saved.setTag("CouplerJointPose",pose);craft.articulation.read(saved);
            need(craft.articulation.jointCount()==1&&craft.articulation.jointYaw(0)==20&&craft.articulation.jointPitch(0)==-10,"actual loaded version-one bent joint");
            NBTTagCompound before=craft.structure.write(),beforePose=new NBTTagCompound();craft.articulation.write(beforePose);
            for(int turns=0;turns<4;turns++){
                VehicleRotation.Plan plan=VehicleRotation.safePlan(craft,turns);EntityGroundVehicle aligned=new EntityGroundVehicle(world);
                try{aligned.install(plan.structure);need(aligned.articulation.jointCount()==1,"copied coupler membership retained");need(aligned.articulation.jointYaw(0)==0&&aligned.articulation.jointPitch(0)==0,"prepared body layout has neutral joint angles");need(plan.structure.controller().tileData().getCompoundTag("ForgeCaps").equals(caps),"opaque controller capability unchanged");}
                finally{aligned.setDead();}
                NBTTagCompound afterPose=new NBTTagCompound();craft.articulation.write(afterPose);need(craft.structure.write().equals(before)&&afterPose.equals(beforePose),"preparing cardinal copy preserves full source and original joint pose");need(craft.posX==1000&&craft.posY==130&&craft.posZ==1000&&craft.rotationYaw==37,"preparing copy never teleports or turns live craft");
            }
            boolean refused=false;try{VehicleRotation.turns(craft.rotationYaw);}catch(IllegalArgumentException expected){refused=true;}need(refused,"ordinary parking still requires cardinal alignment");
        }finally{craft.setDead();}
        System.out.println("[vandorlabs][reprolab] safe-park-alignment PASS checks="+checks+" version1 bent-joint four-headings opaque-ForgeCaps immutable-source ordinary-heading-guard");
    }
    private VehicleSafeParkAlignmentChecks(){}
}
