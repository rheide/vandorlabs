package com.vandorlabs.neoforge.gear.client;

import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.gear.core.GearMotion;
import com.vandorlabs.neoforge.gear.*;
import java.util.Arrays;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec3;

/** Independent base-half then elbow-half oracle against actual streamed authored mesh vertices. */
final class NativeGearDoubleRenderChecks {
    private static void need(boolean ok,String why){if(!ok)throw new AssertionError("Native Double emitted vertices: "+why);}
    private static final class Records implements VertexConsumer {
        double[] xyz=new double[4096];int count;
        @Override public VertexConsumer addVertex(float x,float y,float z){if(count+3>xyz.length)xyz=Arrays.copyOf(xyz,xyz.length*2);xyz[count++]=x;xyz[count++]=y;xyz[count++]=z;return this;}
        @Override public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        @Override public VertexConsumer setUv(float u,float v){return this;}
        @Override public VertexConsumer setUv1(int u,int v){return this;}
        @Override public VertexConsumer setUv2(int u,int v){return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){return this;}
    }
    private static double[] records(NativeGearEntity tile,double phase,NativeGearMeshes mesh){
        var records=new Records();var pose=new PoseStack();NativeGearRenderer.emit(tile,phase,(name,transform,cache)->mesh.draw(name,transform,false,pose.last(),records,0xF000F0,0));return Arrays.copyOf(records.xyz,records.count);
    }
    private static Vec3 rotate(Vec3 point,Vec3 origin,double ax,double az,double angle){
        var v=point.subtract(origin);double c=Math.cos(angle),s=Math.sin(angle),dot=ax*v.x+az*v.z;
        return origin.add(v.x*c-az*v.y*s+ax*dot*(1-c),v.y*c+(az*v.x-ax*v.z)*s,v.z*c+ax*v.y*s+az*dot*(1-c));
    }
    static void run(RegistryAccess registries,NativeGearMeshes mesh){
        int fixed=mesh.vertices("mount")+mesh.vertices("sleeve"),upper=mesh.vertices("swing_linkage_upper_body")+mesh.vertices("swing_linkage_elbow"),lower=mesh.vertices("swing_linkage_lower"),cases=0;long checks=0;
        need(upper+lower==mesh.vertices("swing_linkage"),"split parts retain the authored vertex count");
        for(int size=1;size<=2;size++)for(Direction mount:Direction.values())for(int height:new int[]{1,2,8})for(int fold=0;fold<4;fold++)for(int kink=0;kink<4;kink++)for(boolean level:new boolean[]{false,true})for(boolean doubled:new boolean[]{false,true}){
            var definition=size==1?NativeGearDefinition.ROTATING:NativeGearDefinition.LARGE_ROTATING;var state=NativeGearBindings.BLOCKS.get(definition.id).defaultBlockState().setValue(NativeGearBlock.ASSEMBLED,true);var tile=NativeGearBindings.ENTITY.create(BlockPos.ZERO,state);var data=new CompoundTag();NativeGearSettings.defaults().writeSaved(data);
            data.putBoolean("GearController",true);data.putInt("GearHeight",height);data.putString("GearKind","NONE");data.putString("ArmMountDirection",mount.name());data.putBoolean("ArmMiddleHinge",doubled);data.putInt("ArmHingeMode",doubled?2:0);data.putString("GearDirection",GearMotion.Direction.values()[fold].name());data.putString("ArmKinkDirection",GearMotion.Direction.values()[kink].name());data.putBoolean("ArmRotatePayload",!level);data.putDouble("ArmActiveAngle",90);data.putDouble("GearProgress",1);data.putDouble("NativeGearLastEndpoint",1);var feet=new ListTag();feet.add(new IntArrayTag(new int[]{0,-height,0}));data.put("GearFeet",feet);tile.loadWithComponents(data,registries);
            need(!tile.inert()&&tile.assembly()!=null,"admitted render snapshot size="+size+" mount="+mount+" height="+height+" fold="+fold+" kink="+kink+" double="+doubled);
            double[] rest=records(tile,1,mesh);need(rest.length>=(fixed+upper+lower)*3,"complete authored linkage");var axis=GearMotion.Direction.values()[fold];var bend=GearMotion.Direction.values()[kink];
            var pivot=new Vec3(.5,9/16D,.5);double sx=axis.axisX()!=0?size:1,sz=axis.axisZ()!=0?size:1;var elbow=new Vec3(.5+bend.axisZ()*(4.142135623730951/16)*sx,9/16D+(-1/16D-9/16D)*(height-13/16D+.25)/1.25,.5-bend.axisX()*(4.142135623730951/16)*sz);
            for(double phase:new double[]{0,.37,.75}){
                double[] actual=records(tile,phase,mesh);need(actual.length==rest.length,"stable emitted topology");double angle=Math.PI/2*(1-phase*phase*(3-2*phase));
                for(int vertex=0;vertex<fixed+upper+lower;vertex++){
                    var world=new Vec3(rest[vertex*3],rest[vertex*3+1],rest[vertex*3+2]);var v=world.subtract(.5,.5,.5);var local=new Vec3(v.dot(NativeGearOrientation.vector(mount,1,0,0))+.5,v.dot(NativeGearOrientation.vector(mount,0,1,0))+.5,v.dot(NativeGearOrientation.vector(mount,0,0,1))+.5);var expected=local;
                    if(vertex>=fixed){if(doubled){if(vertex>=fixed+upper)expected=rotate(expected,elbow,axis.axisX(),axis.axisZ(),angle/2);expected=rotate(expected,pivot,axis.axisX(),axis.axisZ(),angle/2);}else expected=rotate(expected,pivot,axis.axisX(),axis.axisZ(),angle);}
                    expected=NativeGearOrientation.point(mount,expected.x,expected.y,expected.z);
                    need(Math.abs(actual[vertex*3]-expected.x)<.00003&&Math.abs(actual[vertex*3+1]-expected.y)<.00003&&Math.abs(actual[vertex*3+2]-expected.z)<.00003,"size="+size+" mount="+mount+" height="+height+" fold="+fold+" kink="+kink+" double="+doubled+" phase="+phase+" vertex="+vertex+" expected="+expected+" actual="+actual[vertex*3]+","+actual[vertex*3+1]+","+actual[vertex*3+2]);checks++;
                }
            }cases++;
        }
        System.out.println("[vandorlabs][gear-render] double emitted PASS cases="+cases+" vertices="+checks+" actualVertexConsumer/sixMounts/regularLarge/heights128/fold4xKink4/rotateLevel/baseHalfElbowHalf/SingleSeparate");
    }
    private NativeGearDoubleRenderChecks(){}
}
