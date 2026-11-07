package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.tiles.TileEntityRampController;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

/** Actual controller rider selection/preflight, independent of native geometry and adapters.
 * This fixed collision world does not establish durable assembly or multiplayer transport. */
public final class RampRiderSelectionLegacyOracle {
    private static final Gson JSON=new Gson();
    private static final BlockPos ROOT=new BlockPos(8,80,8),SOURCE=new BlockPos(0,80,0);
    private static final class World extends NonRenderingChecks.MemoryWorld {
        final List<Entity> actors=new ArrayList<>();final List<AxisAlignedBB> obstacles=new ArrayList<>();
        World(){super(false);time=100;}
        @Override public <T extends Entity> List<T> getEntitiesWithinAABB(Class<? extends T> type,AxisAlignedBB query){List<T> result=new ArrayList<>();for(Entity actor:actors)if(type.isInstance(actor)&&actor.getEntityBoundingBox().intersects(query))result.add(type.cast(actor));return result;}
        @Override public List<AxisAlignedBB> getCollisionBoxes(Entity actor,AxisAlignedBB query){List<AxisAlignedBB> result=new ArrayList<>();for(AxisAlignedBB box:obstacles)if(box.intersects(query))result.add(box);return result;}
    }
    private static void set(TileEntityRampController tile,String name,Object value)throws Exception{Field field=TileEntityRampController.class.getDeclaredField(name);field.setAccessible(true);field.set(tile,value);}
    private static JsonArray values(double... values){JsonArray row=new JsonArray();for(double value:values)row.add(value);return row;}
    private static JsonArray box(AxisAlignedBB b){return values(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);}
    private static JsonArray actor(Entity entity){return values(entity.posX,entity.posY,entity.posZ,entity.getEntityBoundingBox().minX,entity.getEntityBoundingBox().minY,entity.getEntityBoundingBox().minZ,entity.getEntityBoundingBox().maxX,entity.getEntityBoundingBox().maxY,entity.getEntityBoundingBox().maxZ,entity.motionX,entity.motionY,entity.motionZ,entity.onGround?1:0,entity.fallDistance);}
    public static void main(String[] args)throws Exception{
        boolean vertical=args.length>1&&args[1].equals("--vertical");
        net.minecraft.init.Bootstrap.register();Method carry=TileEntityRampController.class.getDeclaredMethod("carryRiders",double.class,double.class,boolean.class,boolean.class);carry.setAccessible(true);
        Path target=Paths.get(args[0]);Files.createDirectories(target.getParent());int rows=0,moved=0,blocked=0;
        try(PrintWriter out=new PrintWriter(Files.newBufferedWriter(target,java.nio.charset.StandardCharsets.UTF_8))){
            for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int material=0;material<4;material++)for(int mode=vertical?5:0;mode<(vertical?10:5);mode++)for(int pixels:new int[]{4,8})for(int interpolation=0;interpolation<3;interpolation++){
                World world=new World();TileEntityRampController tile=new TileEntityRampController();tile.setWorld(world);tile.setPos(ROOT);
                IBlockState state=material==0?Blocks.STONE.getDefaultState():material==3?Blocks.STONE_STAIRS.getDefaultState().withProperty(BlockStairs.FACING,facing):Blocks.STONE_SLAB.getDefaultState().withProperty(BlockSlab.HALF,material==1?BlockSlab.EnumBlockHalf.BOTTOM:BlockSlab.EnumBlockHalf.TOP);
                List<BlockPos> sources=Arrays.asList(SOURCE,vertical?SOURCE.up():SOURCE.offset(facing));set(tile,"sources",sources);set(tile,"original",state);set(tile,"facing",facing);set(tile,"length",2);set(tile,"minAlong",vertical?80:Math.min(SOURCE.getX()*facing.getFrontOffsetX()+SOURCE.getZ()*facing.getFrontOffsetZ(),sources.get(1).getX()*facing.getFrontOffsetX()+sources.get(1).getZ()*facing.getFrontOffsetZ()));set(tile,"low",material==2?.5:0D);set(tile,"high",material==1?.5:1D);set(tile,"startHalfSteps",0);set(tile,"endHalfSteps",-4);tile.treadPixels=pixels;tile.speed=1;tile.interpolation=interpolation;tile.verticalLayout=vertical;tile.elevator=mode%5==1;tile.travelAxis=mode%5==2?1:mode%5==3?2:0;tile.extendSegments=mode%5==4;
                for(BlockPos source:sources)world.states.put(source,state);
                EntityPlayer player=new EntityPlayer(world,new com.mojang.authlib.GameProfile(new UUID(0,291107),"RiderSelection")){public boolean isCreative(){return false;}public boolean isSpectator(){return false;}};
                Entity pig=new net.minecraft.entity.passive.EntityPig(world),item=new net.minecraft.entity.item.EntityItem(world);world.actors.add(player);world.actors.add(pig);world.actors.add(item);
                for(int phase=0;phase<4;phase++)for(int placement=0;placement<8;placement++)for(int obstacle=0;obstacle<3;obstacle++){
                    double previous=phase==0?0:.5,current=phase==2?0:1;boolean fromOriginal=phase==0,toOriginal=phase==3;
                    world.obstacles.clear();if(obstacle==1)world.obstacles.add(new AxisAlignedBB(-4,80.5,-4,4,80.75,4));if(obstacle==2)world.obstacles.add(new AxisAlignedBB(.9,77,-4,1.2,84,4));
                    double high=material==1?.5:1;double y=80+high+(phase>0?-previous:0)+(placement==1?-.1:placement==2?.16:placement==3?.5:placement==4?.5000001:0);
                    for(int i=0;i<world.actors.size();i++){Entity entity=world.actors.get(i);entity.setPosition(.5+(placement==5?1:0),y,.5);entity.motionX=.125;entity.motionY=placement==6?.1000001:0;entity.motionZ=-.125;entity.noClip=placement==7;entity.onGround=false;entity.fallDistance=3;}
                    JsonObject row=new JsonObject();if(vertical)row.addProperty("vertical",true);row.addProperty("noClip",placement==7);row.addProperty("facing",facing.name());row.addProperty("material",material);row.addProperty("mode",mode);row.addProperty("pixels",pixels);row.addProperty("interpolation",interpolation);row.addProperty("previous",previous);row.addProperty("current",current);row.addProperty("fromOriginal",fromOriginal);row.addProperty("toOriginal",toOriginal);JsonArray before=new JsonArray();for(Entity entity:world.actors)before.add(actor(entity));row.add("before",before);JsonArray boxes=new JsonArray();for(AxisAlignedBB b:world.obstacles)boxes.add(box(b));row.add("obstacles",boxes);JsonArray shapes=new JsonArray();if(material==3){for(com.vandorlabs.ramp.RampGeometry.Box shape:com.vandorlabs.compat.RampStairs.boxes(world,state))shapes.add(values(shape.minX,shape.minY,shape.minZ,shape.maxX,shape.maxY,shape.maxZ));}else shapes.add(values(0,material==2?.5:0,0,1,material==1?.5:1,1));row.add("shapes",shapes);
                    boolean accepted=(Boolean)carry.invoke(tile,previous,current,fromOriginal,toOriginal);if(!accepted)blocked++;row.addProperty("accepted",accepted);JsonArray after=new JsonArray();for(Entity entity:world.actors)after.add(actor(entity));row.add("after",after);if(!before.equals(after))moved++;out.print(JSON.toJson(row));out.print('\n');rows++;
                }
            }
        }
        if(rows!=46080||moved==0||blocked==0)throw new AssertionError("Missing actual moving/blocked rider selection coverage "+rows+"/"+moved+"/"+blocked);
        SegmentedReferenceGolden.check(target.toFile(),vertical?"f12216e55f573ffe36fb1c6afd3bec1a9bc089f4307e7c155378b8879e15f484":"576ffb8bcdae5bc903dfbce9e7fc3101c13c2886cabb429b82dd99f1fd7ea676");
        StringBuilder sha=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(target)))sha.append(String.format(Locale.ROOT,"%02x",b&255));
        System.out.println("Ramp Source rider selection PASS rows="+rows+" moved="+moved+" blocked="+blocked+" sha256="+sha);
    }
}
