package com.vandorlabs.vehicle;

import com.vandorlabs.physics.RigidTransform;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Independent frozen actual v1 constructor/joint/limiter math, not a world-read callback. */
public final class VehicleCouplerAngularLegacyReference {
    public static void main(String[] args)throws Exception{
        if(VehicleArticulation.MAX_ANGLE!=75)throw new AssertionError("Legacy angle authority changed");
        Class<?> type=Class.forName("com.vandorlabs.vehicle.VehicleArticulation$Joint");Constructor<?> constructor=type.getDeclaredConstructor(BlockPos.class,BlockPos.class,int.class,int.class,double[].class,double[].class);constructor.setAccessible(true);Field hx=type.getDeclaredField("hx"),hz=type.getDeclaredField("hz");hx.setAccessible(true);hz.setAccessible(true);
        double[] angles={-76,-75.0001,-75,-74.9999,-60,-30,0,30,60,74.9999,75,75.0001,76};int records=6*10*angles.length*angles.length*2;
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(bytes)){
            out.writeInt(0x43414c31);out.writeInt(records);
            for(EnumFacing facing:EnumFacing.values()){
                double[] pivot={1.5+facing.getFrontOffsetX()*.5,2.5+facing.getFrontOffsetY()*.5,3.5+facing.getFrontOffsetZ()*.5};
                double[][] levers={{facing.getFrontOffsetX()*2,facing.getFrontOffsetZ()*2},{0,0},{.009999999,0},{.01,0},{.010000001,0},{2,0},{0,2},{-2,-3},{1e-12,-1e-12},{.01,0}};
                for(int leverIndex=0;leverIndex<levers.length;leverIndex++){double[] lever=levers[leverIndex],at=leverIndex==9?new double[]{0,0,0}:pivot;double[] axle={at[0]+lever[0],at[1]+(facing==EnumFacing.DOWN?-2:2),at[2]+lever[1]};Object joint=constructor.newInstance(new BlockPos(1,2,3),new BlockPos(1,2,3).offset(facing),0,1,at,axle);double x=hx.getDouble(joint),z=hz.getDouble(joint);
                    for(double rawYaw:angles)for(double rawPitch:angles)for(int kind=0;kind<2;kind++){
                        double[] limited=kind==0?new double[]{rawYaw,rawPitch}:RigidTransform.limitedAngles(rawYaw,rawPitch,VehicleArticulation.MAX_ANGLE);double y=limited[0],p=limited[1];RigidTransform actual=RigidTransform.joint(0,0,0,x,z,y,p);
                        // Exact saved-v1 expression, independently labeled; no new admission helper involved.
                        boolean accepted=Double.isFinite(y)&&Double.isFinite(p)&&Math.abs(y)<=VehicleArticulation.MAX_ANGLE&&Math.abs(p)<=VehicleArticulation.MAX_ANGLE&&actual.angle()<=VehicleArticulation.MAX_ANGLE+1e-6;
                        out.writeByte(facing.getIndex());out.writeByte(kind);out.writeDouble(axle[0]-at[0]);out.writeDouble(axle[2]-at[2]);out.writeDouble(x);out.writeDouble(z);out.writeDouble(y);out.writeDouble(p);out.writeBoolean(accepted);for(double value:actual.r)out.writeDouble(value);
                    }
                }
            }
        }
        byte[] data=bytes.toByteArray();if(data.length>4*1024*1024)throw new AssertionError("Bounded legacy fixture");Path file=Paths.get("build/test-artifacts/coupler-angular-legacy.bin");Files.createDirectories(file.getParent());Files.write(file,data);StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(data))hash.append(String.format("%02x",b&255));
        if(!hash.toString().equals("02b586262faefba9dbf59acb372476908fe13b6b35c39cedc1cf06026e3a52f3"))throw new AssertionError("Frozen actual legacy math changed: "+hash);
        System.out.println("Actual legacy Coupler angular reference PASS records="+records+" bytes="+data.length+" sha256="+hash+" sixfaces/actualJointCtor/joint/limitedAngles; saved-read-expression labels, no world-read claim");
    }
    private VehicleCouplerAngularLegacyReference(){}
}
