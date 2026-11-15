package com.vandorlabs.gear.client;

import com.vandorlabs.gear.*;
import com.vandorlabs.gear.core.GearMotion;
import com.vandorlabs.vehicle.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.nbt.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** Compare emitted linkage vertices to independent composed rotations, including authored elbow artwork. */
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class ArmDoubleHingeRenderChecks {
    private static void need(boolean ok,String why){if(!ok)throw new IllegalStateException("arm-double-render: "+why);}
    private static int vertices(String name)throws Exception{
        java.lang.reflect.Field field=GearModels.class.getDeclaredField("MESHES");field.setAccessible(true);Object mesh=((Map<?,?>)field.get(null)).get(name);need(mesh!=null,"baked model "+name);
        field=mesh.getClass().getDeclaredField("quads");field.setAccessible(true);return ((List<?>)field.get(mesh)).size()*4;
    }
    private static double[] records(GearTileEntity tile,double phase){
        NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());saved.setDouble("GearProgress",phase);tile.readFromNBT(saved);
        BufferBuilder buffer=new BufferBuilder(65536);buffer.begin(7,DefaultVertexFormats.BLOCK);new GearRenderer().renderUncached(tile,0,0,0,0,buffer);buffer.finishDrawing();
        int count=buffer.getVertexCount(),stride=DefaultVertexFormats.BLOCK.getIntegerSize()*4;double[] result=new double[count*3];java.nio.ByteBuffer bytes=buffer.getByteBuffer();
        for(int n=0;n<count;n++)for(int axis=0;axis<3;axis++)result[n*3+axis]=bytes.getFloat(n*stride+axis*4);return result;
    }
    private static Vec3d rotate(Vec3d point,Vec3d origin,double ax,double az,double angle){
        Vec3d v=point.subtract(origin);double c=Math.cos(angle),s=Math.sin(angle),dot=ax*v.x+az*v.z;
        return origin.addVector(v.x*c-az*v.y*s+ax*dot*(1-c),v.y*c+(az*v.x-ax*v.z)*s,v.z*c+ax*v.y*s+az*dot*(1-c));
    }
    public static void run(Minecraft mc)throws Exception{
        int fixed=vertices("mount")+vertices("sleeve"),upper=vertices("swing_linkage_upper_body")+vertices("swing_linkage_elbow"),lower=vertices("swing_linkage_lower"),cases=0;long checks=0;
        need(upper+lower==vertices("swing_linkage"),"split parts preserve authored vertex count");
        for(int size=1;size<=2;size++)for(EnumFacing mount:EnumFacing.values())for(int height:new int[]{1,2,8})for(int fold=0;fold<4;fold++)for(int kink=0;kink<4;kink++)for(boolean level:new boolean[]{false,true})for(boolean doubled:new boolean[]{false,true}){
            GearBlock block=size==1?GearRegistry.GEAR_SWING_ARM:GearRegistry.LARGE_GEAR_SWING_ARM;GearTileEntity seed=new GearTileEntity();seed.setPos(BlockPos.ORIGIN);NBTTagCompound data=seed.writeToNBT(new NBTTagCompound());
            data.setBoolean("GearController",true);data.setBoolean("GearOperational",true);data.setInteger("GearHeight",height);data.setString("GearKind","NONE");data.setString("GearMode",level?"ROTATE_LEVEL":"ROTATE");data.setString("ArmMountDirection",mount.name());data.setBoolean("ArmMiddleHinge",doubled);if(doubled)data.setInteger("ArmHingeMode",2);data.setString("GearDirection",GearMotion.Direction.values()[fold].name());data.setString("ArmKinkDirection",GearMotion.Direction.values()[kink].name());data.setBoolean("ArmRotatePayload",!level);data.setDouble("ArmActiveAngle",90);
            NBTTagList feet=new NBTTagList();feet.appendTag(new NBTTagIntArray(new int[]{0,-height,0}));data.setTag("GearFeet",feet);
            VehicleStructure scene=VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,block.getDefaultState().withProperty(GearBlock.ASSEMBLED,true),data)));GearTileEntity tile=(GearTileEntity)new VehicleWorld(mc.world,scene).getTileEntity(BlockPos.ORIGIN);
            double[] rest=records(tile,1);need(rest.length>=(fixed+upper+lower)*3,"complete linkage");GearMotion.Direction axis=GearMotion.Direction.values()[fold],bend=GearMotion.Direction.values()[kink];
            Vec3d pivot=new Vec3d(.5,9/16D,.5);double sx=axis.axisX()!=0?size:1,sz=axis.axisZ()!=0?size:1;
            Vec3d elbow=new Vec3d(.5+bend.axisZ()*(4.142135623730951/16)*sx,9/16D+(-1/16D-9/16D)*(height-13/16D+.25)/1.25,.5-bend.axisX()*(4.142135623730951/16)*sz);
            for(double phase:new double[]{0,.37,.75}){
                double[] actual=records(tile,phase);need(rest.length==actual.length,"stable part topology");double angle=Math.PI/2*(1-phase*phase*(3-2*phase));
                for(int vertex=0;vertex<fixed+upper+lower;vertex++){
                    Vec3d world=new Vec3d(rest[vertex*3],rest[vertex*3+1],rest[vertex*3+2]);Vec3d relative=world.subtract(.5,.5,.5);Vec3d local=new Vec3d(relative.dotProduct(ArmOrientation.vector(mount,1,0,0))+.5,relative.dotProduct(ArmOrientation.vector(mount,0,1,0))+.5,relative.dotProduct(ArmOrientation.vector(mount,0,0,1))+.5);
                    Vec3d expected=local;
                    if(vertex>=fixed){if(doubled){if(vertex>=fixed+upper)expected=rotate(expected,elbow,axis.axisX(),axis.axisZ(),angle/2);expected=rotate(expected,pivot,axis.axisX(),axis.axisZ(),angle/2);}else expected=rotate(expected,pivot,axis.axisX(),axis.axisZ(),angle);}
                    expected=ArmOrientation.point(mount,expected.x,expected.y,expected.z);
                    need(Math.abs(actual[vertex*3]-expected.x)<.00003&&Math.abs(actual[vertex*3+1]-expected.y)<.00003&&Math.abs(actual[vertex*3+2]-expected.z)<.00003,"emitted base/elbow frame size="+size+" mount="+mount+" height="+height+" fold="+fold+" kink="+kink+" double="+doubled+" phase="+phase+" vertex="+vertex);checks++;
                }
            }cases++;
        }
        System.out.println("[vandorlabs][reprolab] arm-double-render PASS cases="+cases+" vertices="+checks+" actualEmitted/sixMounts/regularLarge/heights128/foldKinkIndependent/splitAngles/legacySeparate");
    }
    private ArmDoubleHingeRenderChecks(){}
}
