package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.motion.PlatformRiders;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.*;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.*;

/** Actual released support clipping, recent-floor event and vanilla axis clipping; no GPU. */
public final class PlatformRiderLegacyOracle {
    private static final Gson JSON=new Gson();private static int count;
    private static JsonArray box(AxisAlignedBB b){if(b==null)return null;JsonArray a=new JsonArray();for(double v:new double[]{b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ})a.add(v);return a;}
    private static JsonArray vector(double... values){JsonArray a=new JsonArray();for(double value:values)a.add(value);return a;}
    private static void out(PrintWriter output,JsonObject row){output.print(JSON.toJson(row));output.print('\n');count++;}
    private static Entity entity(NonRenderingChecks.MemoryWorld world,int kind){switch(kind){
        case 0:return new EntityPlayer(world,new com.mojang.authlib.GameProfile(new UUID(0,290711),"PlatformOracle")){public boolean isCreative(){return false;}public boolean isSpectator(){return false;}};
        case 1:return new EntityItem(world);case 2:return new EntityPig(world);case 3:return new EntityBoat(world);default:return new EntityMinecartEmpty(world);
    }}
    private static void flags(Entity entity,Entity carrier,int flags,double velocity){entity.isDead=false;entity.dismountRidingEntity();if((flags&4)!=0&&!entity.startRiding(carrier,true))throw new AssertionError("Source mount fixture");entity.isDead=(flags&1)!=0;entity.noClip=(flags&2)!=0;entity.motionY=velocity;if(entity instanceof EntityPlayer)((EntityPlayer)entity).capabilities.isFlying=(flags&8)!=0;entity.setPosition(1.25,80,-3.5);}
    public static void main(String[] args)throws Exception {
        net.minecraft.init.Bootstrap.register();Path target=Paths.get(args[0]);Files.createDirectories(target.getParent());
        int collision=0,recent=0,clear=0;
        try(PrintWriter output=new PrintWriter(Files.newBufferedWriter(target,java.nio.charset.StandardCharsets.UTF_8))){
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);Entity carrier=new EntityBoat(world);
            for(int kind=0;kind<5;kind++){Entity entity=entity(world,kind);
                for(int flags=0;flags<16;flags++)for(double velocity:new double[]{-.1,0,.1,.1000000001,.5})for(boolean moving:new boolean[]{false,true})for(double bottom:new double[]{-1,-.1,0,.25})for(double top:new double[]{-.01,0,1e-9,.1,.4999999999,.5,.5000000001}){
                    if(top<bottom)continue;flags(entity,carrier,flags,velocity);double feet=entity.getEntityBoundingBox().minY;AxisAlignedBB source=new AxisAlignedBB(.9,feet+bottom,-3.8,1.6,feet+top,-3.2);AxisAlignedBB result=PlatformRiders.supportCollision(source,entity,moving);
                    JsonObject row=new JsonObject();row.addProperty("kind","support");row.addProperty("entity",kind);row.addProperty("flags",flags);row.addProperty("velocity",velocity);row.addProperty("moving",moving);row.addProperty("feet",feet);row.add("box",box(source));row.add("out",result==null?JsonNull.INSTANCE:box(result));row.addProperty("eligible",PlatformRiders.eligible(entity));row.addProperty("vehicle",PlatformRiders.vehicle(entity));out(output,row);collision++;
                }entity.dismountRidingEntity();
            }
            for(boolean remote:new boolean[]{false,true}){world=new NonRenderingChecks.MemoryWorld(remote);carrier=new EntityBoat(world);
                for(int kind=0;kind<2;kind++){Entity entity=entity(world,kind);
                    for(int flags=0;flags<16;flags++)for(int age:new int[]{-1,0,1,20,21})for(int offset=0;offset<6;offset++)for(double feetOffset:new double[]{-.0001,0,.25})for(double topOffset:new double[]{-.00100001,-.001,0,.001,.00100001}){
                        flags(entity,carrier,0,0);entity.dimension=0;world.time=100;PlatformRiders.supported(entity);
                        double x=offset==1?.75:offset==2?.7500001:0,z=offset==3?.75:offset==4?.7500001:0;
                        flags(entity,carrier,flags,.2);entity.setPosition(1.25+x,80+feetOffset,-3.5+z);if(offset==5)entity.dimension=1;world.time=100+age;
                        AxisAlignedBB source=new AxisAlignedBB(.9,79,-3.8,1.6,80+topOffset,-3.2);List<AxisAlignedBB> boxes=new ArrayList<>();boxes.add(source);
                        PlatformRiders.recentCollision(new net.minecraftforge.event.world.GetCollisionBoxesEvent(world,entity,entity.getEntityBoundingBox().grow(.5),boxes));
                        JsonObject row=new JsonObject();row.addProperty("kind","recent");row.addProperty("entity",kind);row.addProperty("remote",remote);row.addProperty("flags",flags);row.addProperty("age",age);row.addProperty("dimensionMatches",offset!=5);row.addProperty("x",entity.posX);row.addProperty("z",entity.posZ);row.addProperty("feet",entity.getEntityBoundingBox().minY);row.add("box",box(source));row.add("out",boxes.isEmpty()?JsonNull.INSTANCE:box(boxes.get(0)));out(output,row);recent++;
                    }entity.dismountRidingEntity();
                }
            }
            world=new NonRenderingChecks.MemoryWorld(false);Entity entity=entity(world,0);AxisAlignedBB body=new AxisAlignedBB(0,80,0,1,81,1);entity.setEntityBoundingBox(body);
            AxisAlignedBB[] solids={new AxisAlignedBB(-2,79,-2,3,80,3),new AxisAlignedBB(-2,81,-2,3,82,3),new AxisAlignedBB(1,79,-2,2,82,3),new AxisAlignedBB(-2,79,1,3,82,2)};
            for(int mask=0;mask<16;mask++)for(double dx:new double[]{-1,-.5,0,.5,1})for(double dy:new double[]{-1,-.5,0,.5,1})for(double dz:new double[]{-1,-.5,0,.5,1}){
                List<AxisAlignedBB> obstacles=new ArrayList<>();JsonArray boxes=new JsonArray();for(int i=0;i<solids.length;i++)if((mask&(1<<i))!=0){obstacles.add(solids[i]);boxes.add(box(solids[i]));}
                double x=dx,y=dy,z=dz;for(AxisAlignedBB obstacle:obstacles)y=obstacle.calculateYOffset(body,y);AxisAlignedBB moved=body.offset(0,y,0);for(AxisAlignedBB obstacle:obstacles)x=obstacle.calculateXOffset(moved,x);moved=moved.offset(x,0,0);for(AxisAlignedBB obstacle:obstacles)z=obstacle.calculateZOffset(moved,z);
                JsonObject row=new JsonObject();row.addProperty("kind","clear");row.add("body",box(body));row.add("delta",vector(dx,dy,dz));row.add("obstacles",boxes);row.add("clipped",vector(x,y,z));row.addProperty("clear",PlatformRiders.clear(entity,new Vec3d(dx,dy,dz),obstacles));out(output,row);clear++;
            }
        }
        // Outside the golden: allocation baseline for the released support publication path.
        NonRenderingChecks.MemoryWorld costWorld=new NonRenderingChecks.MemoryWorld(false);Entity costEntity=entity(costWorld,1);
        for(int i=0;i<20000;i++)PlatformRiders.supported(costEntity);
        com.sun.management.ThreadMXBean allocations=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();long thread=Thread.currentThread().getId(),start=allocations.getThreadAllocatedBytes(thread);
        for(int i=0;i<10000;i++)PlatformRiders.supported(costEntity);
        System.out.println("Platform Source support publication 10000 calls allocatedBytes="+(allocations.getThreadAllocatedBytes(thread)-start));
        SegmentedReferenceGolden.check(target.toFile(),"79dfbdeaefef0ed5eb70e523a04afe51b59ff6cd4ea1dcb613ba9ba1b7040ae7");
        StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(target)))hash.append(String.format(Locale.ROOT,"%02x",b&255));System.out.println("Platform Source rider reference PASS support="+collision+" recent="+recent+" clear="+clear+" total="+count+" sha256="+hash);
    }
}
