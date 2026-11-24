package com.vandorlabs.vehicle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;

/** Each rider walks in hull coordinates while the Minecraft mount carries the crew. */
final class FlightCabin {
    private static final DataParameter<NBTTagCompound> POSITION =
            EntityDataManager.createKey(EntityGroundVehicle.class, DataSerializers.COMPOUND_TAG);
    private final EntityGroundVehicle craft;
    private final Map<UUID,Walker> walkers=new HashMap<>();

    private static final class Walker {
        Vec3d feet,previous,rendered;
        double vertical;
        int sequence=-1,forward,right;
        boolean jump;
        long lastInput=Long.MIN_VALUE;

        Walker(Vec3d feet){this.feet=this.previous=this.rendered=feet;}
    }

    FlightCabin(EntityGroundVehicle craft) { this.craft = craft; }

    static void define(EntityGroundVehicle craft) {
        craft.getDataManager().register(POSITION, new NBTTagCompound());
    }

    void changed(DataParameter<?> key) {
        if (!POSITION.equals(key)||!craft.world.isRemote) return;
        NBTTagCompound data=craft.getDataManager().get(POSITION);
        NBTTagList list=data.getTagList("Walkers",10);
        Map<UUID,Walker> next=new HashMap<>();
        for(int i=0;i<list.tagCount();i++){
            NBTTagCompound row=list.getCompoundTagAt(i);
            UUID id=row.getUniqueId("Walker");
            Vec3d feet=feet(row);
            Walker walker=walkers.get(id);
            if(walker==null)walker=new Walker(feet);
            else walker.feet=feet;
            next.put(id,walker);
        }
        walkers.clear();walkers.putAll(next);
    }

    boolean walking(Entity rider) { return rider!=null&&walkers.containsKey(rider.getUniqueID()); }

    void clientTick() {
        for(Walker w:walkers.values()){
            w.previous=w.rendered;
            Vec3d delta=w.feet.subtract(w.rendered);
            double distance=delta.lengthVector();
            w.rendered=distance<.001?w.feet:w.rendered.add(delta.scale(Math.min(.55,.32/distance)));
        }
    }

    Vec3d position(Entity rider, float partial) {
        Walker w=rider==null?null:walkers.get(rider.getUniqueID());
        if(w==null)return null;
        if(!craft.world.isRemote)return w.feet;
        return w.previous.add(w.rendered.subtract(w.previous).scale(Math.max(0,Math.min(1,partial))));
    }

    void clear() { if(!walkers.isEmpty()){walkers.clear();publish();} }
    void remove(Entity rider){if(walkers.remove(rider.getUniqueID())!=null)publish();}

    private NBTTagCompound snapshot(boolean saving) {
        NBTTagCompound tag=new NBTTagCompound();
        NBTTagList list=new NBTTagList();
        for(Map.Entry<UUID,Walker> entry:walkers.entrySet()){
            Walker w=entry.getValue();
            NBTTagCompound row=new NBTTagCompound();
            row.setUniqueId("Walker",entry.getKey());
            row.setDouble("X",w.feet.x);row.setDouble("Y",w.feet.y);row.setDouble("Z",w.feet.z);
            if(saving)row.setDouble("Vertical",w.vertical);
            list.appendTag(row);
        }
        tag.setTag("Walkers",list);
        return tag;
    }
    private void publish(){craft.getDataManager().set(POSITION,snapshot(false));}
    void write(NBTTagCompound state) { if(!walkers.isEmpty())state.setTag("FlightCabin",snapshot(true)); }
    private static Vec3d feet(NBTTagCompound row){return new Vec3d(row.getDouble("X"),row.getDouble("Y"),row.getDouble("Z"));}

    void read(NBTTagCompound state) {
        walkers.clear();
        if(!craft.flying()||craft.collision==null)return;
        NBTTagCompound tag=state.getCompoundTag("FlightCabin");
        NBTTagList list=tag.getTagList("Walkers",10);
        if(tag.hasUniqueId("Walker")){list=new NBTTagList();list.appendTag(tag);}
        for(int i=0;i<Math.min(list.tagCount(),craft.structure.passengerSeats().size());i++){
            NBTTagCompound row=list.getCompoundTagAt(i);
            if(!row.hasUniqueId("Walker"))continue;
            Vec3d point=feet(row);
            double vertical=row.getDouble("Vertical");
            if(!Double.isFinite(point.x)||!Double.isFinite(point.y)||!Double.isFinite(point.z)||!Double.isFinite(vertical))continue;
            if(!craft.collision.bounds.grow(2).contains(point)||!craft.collision.clearBody(point.x,point.z,point.y+.0001,.3,1.7998))continue;
            Walker walker=new Walker(point);
            walker.vertical=Math.max(-3.92,Math.min(.42,vertical));
            walkers.put(row.getUniqueId("Walker"),walker);
        }
        publish();
    }

    boolean toggle(EntityPlayerMP player) {
        if(craft.transferInProgress||!craft.flying()||player.getRidingEntity()!=craft)return false;
        BlockPos seat=craft.seatForPassenger(player);
        if(walking(player))return craft.board(player,seat);
        if(!craft.poweredFlight()||craft.collision==null)return false;
        Vec3d localSeat=craft.toLocal(craft.seatPosition(craft.flightPose(),player,1));
        Vec3d best=null;
        double bestDistance=Double.POSITIVE_INFINITY;
        for(int dz=-2;dz<=2;dz++)for(int dx=-2;dx<=2;dx++){
            if(dx==0&&dz==0)continue;
            double x=seat.getX()+.5+dx,z=seat.getZ()+.5+dz;
            double y=craft.collision.deckHeight(x,z,localSeat.y-1.25,localSeat.y+.6,player.width/2,player.height);
            if(!Double.isFinite(y))continue;
            Vec3d candidate=new Vec3d(x,y,z);
            boolean occupied=false;
            for(Walker w:walkers.values())if(candidate.squareDistanceTo(w.feet)<.36){occupied=true;break;}
            if(occupied)continue;
            double distance=candidate.squareDistanceTo(localSeat);
            if(distance<bestDistance){best=candidate;bestDistance=distance;}
        }
        if(best==null){
            player.sendStatusMessage(new TextComponentString("No clear standing space beside this seat."),true);
            return false;
        }
        walkers.put(player.getUniqueID(),new Walker(best));
        publish();
        player.sendStatusMessage(new TextComponentString("Walking on deck. Click an available seat to sit."),true);
        return true;
    }

    void input(EntityPlayerMP player,int sequence,byte forward,byte right,boolean jump){
        Walker w=walkers.get(player.getUniqueID());
        if(w==null||sequence<=w.sequence||Math.abs((int)forward)>1||Math.abs((int)right)>1)return;
        w.sequence=sequence;w.forward=forward;w.right=right;w.jump=jump;w.lastInput=craft.world.getTotalWorldTime();
    }

    void serverTick(){
        if(walkers.isEmpty())return;
        boolean moved=false;
        // A rider can detach during movement; Minecraft mutates its passenger list on dismount.
        for(Entity rider:new ArrayList<>(craft.getPassengers())){
            Walker w=walkers.get(rider.getUniqueID());
            if(w!=null&&rider instanceof EntityPlayerMP)moved|=move((EntityPlayerMP)rider,w);
        }
        if(moved)publish();
    }

    private boolean move(EntityPlayerMP player,Walker w){
        boolean recent=w.lastInput>=craft.world.getTotalWorldTime()-10;
        int forward=recent?w.forward:0,right=recent?w.right:0;
        double angle=Math.toRadians(player.rotationYaw-craft.pilotViewReference(craft.flightPose()));
        double speed=.215/Math.max(1,Math.hypot(forward,right));
        double dx=-(forward*Math.sin(angle)+right*Math.cos(angle))*speed;
        double dz=(forward*Math.cos(angle)-right*Math.sin(angle))*speed;
        double radius=player.width/2;
        Vec3d p=w.feet;
        AxisAlignedBB body=new AxisAlignedBB(p.x-radius,p.y,p.z-radius,p.x+radius,p.y+player.height,p.z+radius);
        VehicleCollision hull=craft.collision;
        boolean grounded=hull.clipCabin(body,-.001,1)>-.001;
        double dy=grounded&&recent&&w.jump?.42:w.vertical;
        double y=hull.clipCabin(body,dy,1);
        body=body.offset(0,y,0);
        double x=hull.clipCabin(body,dx,0);
        double z=hull.clipCabin(body.offset(x,0,0),dz,2);
        if(grounded&&dy<=0&&(x!=dx||z!=dz)){
            double up=hull.clipCabin(body,.6,1);
            AxisAlignedBB raised=body.offset(0,up,0);
            double sx=hull.clipCabin(raised,dx,0);
            double sz=hull.clipCabin(raised.offset(sx,0,0),dz,2);
            if(sx*sx+sz*sz>x*x+z*z){x=sx;z=sz;y+=up+hull.clipCabin(raised.offset(x,0,z),-up,1);}
        }
        Vec3d next=p.addVector(x,y,z);
        w.vertical=((y==dy?dy:0)-.08)*.98;
        if(next.equals(p))return false;
        w.feet=next;
        AxisAlignedBB box=body.offset(x,next.y-body.minY,z);
        if(!hull.bounds.grow(0,2,0).intersects(box))exit(player,next,x,y,z);
        return true;
    }

    private void exit(EntityPlayerMP player,Vec3d local,double dx,double dy,double dz){
        Vec3d exit=craft.toWorld(local);
        Vec3d drift=craft.toWorld(local.addVector(dx,dy,dz)).subtract(exit);
        exit=exit.addVector(craft.cruise.x()-craft.posX,craft.flightAltitude()-craft.posY,craft.cruise.z()-craft.posZ);
        craft.flightDismount=true;
        try{player.dismountRidingEntity();}finally{craft.flightDismount=false;}
        if(player.isRiding())return;
        VehicleNetwork.releaseTeleportedRider(player,craft);
        player.connection.setPlayerLocation(exit.x,exit.y,exit.z,player.rotationYaw,player.rotationPitch);
        player.motionX=drift.x+craft.motionX;player.motionY=drift.y+craft.motionY;player.motionZ=drift.z+craft.motionZ;
        player.fallDistance=0;player.velocityChanged=true;
    }
}
