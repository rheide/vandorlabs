package com.vandorlabs.client;

import com.vandorlabs.gear.*;
import com.vandorlabs.vehicle.*;
import com.vandorlabs.network.MessageGearMotion;
import io.netty.buffer.*;
import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;

/** Live geometry equivalence, rigid-hull reuse and compact packet/interpolation regressions. */
final class GearPerformanceRuntimeChecks {
    static void run(EntityGroundVehicle craft)throws Exception{
        VehicleStructure baseline=craft.structure;
        List<GearTileEntity> roots=new ArrayList<>();for(TileEntity tile:craft.view.tiles.values())if(tile instanceof GearTileEntity&&((GearTileEntity)tile).isOperational())roots.add((GearTileEntity)tile);
        require(!roots.isEmpty(),"operational modular gear fixture");
        VehicleCollisionCache cache=new VehicleCollisionCache();cache.reset(craft.view,new VehicleCollision(craft.view));
        GearTileEntity root=roots.get(0);double saved=root.progress();long builds=cache.modularHullBuilds();
        try{
            for(double p:new double[]{1,.875,.5,.125,0,.5,1}){
                root.captureProgress(p);VehicleCollision split=cache.collision(craft.view),fresh=new VehicleCollision(craft.view);
                require(split.bounds.equals(fresh.bounds),"split local bounds at "+p);
                require(covers(split.boxes,fresh.boxes)&&covers(fresh.boxes,split.boxes),"exact occupied-volume parity at "+p);
                for(float yaw:new float[]{0,23,90,179.5F})require(close(split.bounds(yaw),fresh.bounds(yaw)),"posed bounds at "+p+" yaw="+yaw+" split="+split.bounds(yaw)+" fresh="+fresh.bounds(yaw));
            }
            require(cache.modularHullBuilds()==builds+1,"one rigid hull build across seven independent gear poses");
        }finally{root.captureProgress(saved);cache.clear();}
        NBTTagCompound data=root.writeToNBT(new NBTTagCompound());
        GearTileEntity copy=new GearTileEntity();copy.setWorld(craft.view);copy.readFromNBT(data);
        require(copy.acceptMotion(copy.motionRevision(),100,true,false),"current motion revision accepted");
        require(!copy.acceptMotion(copy.motionRevision(),100,false,true),"duplicate tick rejected");
        require(!copy.acceptMotion(copy.motionRevision()+1,101,false,true),"different topology revision rejected");
        copy.correctProgress(.25);require(Math.abs(copy.renderProgress(0)-saved)<1e-9&&Math.abs(copy.renderProgress(1)-.25)<1e-9,"correction preserves interpolation endpoints");
        VehicleStructure.Cell before=baseline.at(root.getPos());NBTTagCompound animated=before.tileData();animated.setDouble("GearProgress",.375);animated.setBoolean("GearBlocked",true);animated.setInteger("GearRevision",100);
        VehicleStructure.Cell pose=new VehicleStructure.Cell(before.pos,before.state,animated);
        require(before.sameGearConfiguration(pose),"pose and revision preserve baked hull geometry");animated.setString("GearDirection","EAST".equals(animated.getString("GearDirection"))?"WEST":"EAST");
        require(!before.sameGearConfiguration(new VehicleStructure.Cell(before.pos,before.state,animated)),"configuration still invalidates geometry");
        NBTTagCompound taxiA=before.tileData(),taxiB=before.tileData(),taxiState=new NBTTagCompound();taxiState.setInteger("Schema",1);taxiState.setInteger("Phase",0);taxiState.setInteger("Height",3);taxiState.setInteger("Amount",2);taxiState.setDouble("Level",.25);taxiA.setTag(com.vandorlabs.gear.GearTaxiState.KEY,taxiState.copy());taxiState.setDouble("Level",1.5);taxiState.setInteger("Settle",4);taxiState.setBoolean("Expand",true);taxiB.setTag(com.vandorlabs.gear.GearTaxiState.KEY,taxiState);
        require(new VehicleStructure.Cell(before.pos,before.state,taxiA).sameGearConfiguration(new VehicleStructure.Cell(before.pos,before.state,taxiB)),"Taxi pose levels reuse the baked rigid hull");taxiState.setInteger("Amount",1);require(!new VehicleStructure.Cell(before.pos,before.state,taxiA).sameGearConfiguration(new VehicleStructure.Cell(before.pos,before.state,taxiB)),"Taxi retained topology still invalidates the baked hull");
        ByteBuf bytes=Unpooled.buffer();bytes.writeInt(craft.dimension);bytes.writeInt(craft.getEntityId());bytes.writeLong(craft.getUniqueID().getMostSignificantBits());bytes.writeLong(craft.getUniqueID().getLeastSignificantBits());bytes.writeLong(100);bytes.writeByte(1);bytes.writeLong(root.getPos().toLong());bytes.writeInt(root.motionRevision());bytes.writeDouble(.5);bytes.writeBoolean(true);bytes.writeBoolean(false);
        MessageGearMotion packet=new MessageGearMotion();packet.fromBytes(bytes);require(packet.poses.size()==1&&packet.poses.get(0).progress==.5,"bounded pose packet round trip");bytes.release();
        for(TileEntity tile:craft.view.tiles.values())if(tile instanceof GearTileEntity){GearTileEntity gear=(GearTileEntity)tile;require(gear.hasFastRenderer(),"gear uses shared world buffer");if(gear.loadedController()!=null&&gear.loadedController()!=gear)require(!com.vandorlabs.gear.client.GearRenderer.visible(gear),"bound member emits no duplicate group");}
        System.out.println("[vandorlabs][reprolab] gear-performance PASS exact-volume split-hull-reuse baked-pose-invalidation interpolation revision bounded-packet batched-members");
    }
    /** Subtracting axis-aligned boxes proves coverage even when merging changes the partition. */
    private static boolean covers(List<AxisAlignedBB> source,List<AxisAlignedBB> target){
        for(AxisAlignedBB original:source){if(original.maxX<=original.minX||original.maxY<=original.minY||original.maxZ<=original.minZ)continue;
            List<AxisAlignedBB> pending=new ArrayList<>();pending.add(original);
            for(AxisAlignedBB cover:target){List<AxisAlignedBB> next=new ArrayList<>();for(AxisAlignedBB b:pending){if(!b.intersects(cover)){next.add(b);continue;}AxisAlignedBB i=b.intersect(cover);
                add(next,b.minX,b.minY,b.minZ,i.minX,b.maxY,b.maxZ);add(next,i.maxX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);
                add(next,i.minX,b.minY,b.minZ,i.maxX,i.minY,b.maxZ);add(next,i.minX,i.maxY,b.minZ,i.maxX,b.maxY,b.maxZ);
                add(next,i.minX,i.minY,b.minZ,i.maxX,i.maxY,i.minZ);add(next,i.minX,i.minY,i.maxZ,i.maxX,i.maxY,b.maxZ);
            }pending=next;if(pending.isEmpty())break;}
            if(!pending.isEmpty())return false;
        }return true;
    }
    private static void add(List<AxisAlignedBB> out,double x,double y,double z,double xx,double yy,double zz){if(xx-x>1e-10&&yy-y>1e-10&&zz-z>1e-10)out.add(new AxisAlignedBB(x,y,z,xx,yy,zz));}
    private static boolean close(AxisAlignedBB a,AxisAlignedBB b){return Math.abs(a.minX-b.minX)<1e-9&&Math.abs(a.minY-b.minY)<1e-9&&Math.abs(a.minZ-b.minZ)<1e-9&&Math.abs(a.maxX-b.maxX)<1e-9&&Math.abs(a.maxY-b.maxY)<1e-9&&Math.abs(a.maxZ-b.maxZ)<1e-9;}
    private static void require(boolean ok,String reason){if(!ok)throw new IllegalStateException("gear performance: "+reason);}
}
