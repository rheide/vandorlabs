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

/** Actual released belt-step callbacks, with source surfaces and history captured independently. */
public final class RampBeltTransitionLegacyOracle {
    private static final Gson JSON=new Gson();private static final BlockPos ROOT=new BlockPos(8,80,8),SOURCE=new BlockPos(0,80,0);
    private static final Class<?> TYPE=TileEntityRampController.class;
    private static Object get(TileEntityRampController tile,String name)throws Exception{Field f=TYPE.getDeclaredField(name);f.setAccessible(true);return f.get(tile);}
    private static void set(TileEntityRampController tile,String name,Object value)throws Exception{RampRiderSelectionLegacyOracle.set(tile,name,value);}
    private static JsonArray position(Vec3d p){return RampRiderSelectionLegacyOracle.values(p.x,p.y,p.z);}
    @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();Method reset=TYPE.getDeclaredMethod("resetEscalatorRiders"),carry=TYPE.getDeclaredMethod("carryRiders",double.class,double.class,boolean.class,boolean.class);reset.setAccessible(true);carry.setAccessible(true);Method step=TYPE.getDeclaredMethod("stepEscalator"),surfaces=TYPE.getDeclaredMethod("beltSurfaces",double.class),support=TYPE.getDeclaredMethod("support",List.class,AxisAlignedBB.class);step.setAccessible(true);surfaces.setAccessible(true);support.setAccessible(true);
        Path target=Paths.get(args[0]);Files.createDirectories(target.getParent());int count=0,moved=0,refused=0,anchors=0;
        try(PrintWriter out=new PrintWriter(Files.newBufferedWriter(target,java.nio.charset.StandardCharsets.UTF_8))){
            for(int transition=0;transition<3;transition++)for(EnumFacing face:EnumFacing.HORIZONTALS)for(int material=0;material<4;material++)for(int pixels:new int[]{4,8})for(int speed=0;speed<3;speed++)for(int mode=1;mode<=2;mode++){
                RampRiderSelectionLegacyOracle.World world=new RampRiderSelectionLegacyOracle.World();TileEntityRampController tile=new TileEntityRampController();tile.setWorld(world);tile.setPos(ROOT);world.tiles.put(ROOT,tile);
                IBlockState state=material==0?Blocks.STONE.getDefaultState():material==3?Blocks.STONE_STAIRS.getDefaultState().withProperty(BlockStairs.FACING,face):Blocks.STONE_SLAB.getDefaultState().withProperty(BlockSlab.HALF,material==1?BlockSlab.EnumBlockHalf.BOTTOM:BlockSlab.EnumBlockHalf.TOP);
                List<BlockPos> sources=Arrays.asList(SOURCE,transition==0||transition==2?SOURCE.up():SOURCE.offset(face));for(BlockPos p:sources)world.states.put(p,state);set(tile,"sources",sources);set(tile,"original",state);set(tile,"facing",face);set(tile,"length",2);set(tile,"minAlong",transition==0||transition==2?80:0);set(tile,"verticalLayout",transition==0||transition==2);set(tile,"low",material==2?.5:0D);set(tile,"high",material==1?.5:1D);set(tile,"startHalfSteps",0);set(tile,"endHalfSteps",-4);tile.escalator=mode;tile.speed=speed;tile.treadPixels=pixels;
                EntityPlayer player=new EntityPlayer(world,new com.mojang.authlib.GameProfile(new UUID(0,291109),"BeltRiderReference")){public boolean isCreative(){return false;}public boolean isSpectator(){return false;}};world.actors.add(player);world.actors.add(new net.minecraft.entity.passive.EntityPig(world));world.actors.add(new net.minecraft.entity.item.EntityItem(world));Map<UUID,Vec3d> history=(Map<UUID,Vec3d>)get(tile,"beltRiderPositions");
                for(double distance:new double[]{0,.125,1.99})for(int scenario=0;scenario<8;scenario++)for(int obstacle=0;obstacle<3;obstacle++){
                    world.time=100;tile.error=false;tile.status="reference";set(tile,"lastStepTick",99L);set(tile,"beltDistance",distance);set(tile,"beltRunning",true);set(tile,"beltRequested",true);history.clear();world.obstacles.clear();
                    if(obstacle==1)world.obstacles.add(new AxisAlignedBB(-4,80.5,-4,4,80.75,4));if(obstacle==2)world.obstacles.add(new AxisAlignedBB(.85,76,-4,1.2,84,4));
                    List<AxisAlignedBB> beforeSurfaces=(List<AxisAlignedBB>)surfaces.invoke(tile,distance);JsonArray inputHistory=new JsonArray(),before=new JsonArray(),steps=new JsonArray();
                    double along=scenario==7?1.4:0;
                    for(int i=0;i<world.actors.size();i++){Entity entity=world.actors.get(i);entity.noClip=false;entity.motionX=.125;entity.motionY=0;entity.motionZ=-.125;entity.onGround=false;entity.fallDistance=3;entity.setPosition(.5+face.getFrontOffsetX()*along,81,.5+face.getFrontOffsetZ()*along);double top=(Double)support.invoke(null,beforeSurfaces,entity.getEntityBoundingBox());if(!Double.isFinite(top))top=81;entity.setPosition(entity.posX,top+(scenario==1?.16:0),entity.posZ);Vec3d previous=entity.getPositionVector();
                        if(scenario==2)entity.noClip=true;if(scenario==3||scenario==5)entity.motionY=.2;
                        if(scenario==4||scenario==5){history.put(entity.getUniqueID(),previous);if(scenario==4)entity.setPosition(entity.posX+.1,entity.posY+.05,entity.posZ);}
                        if(scenario==6){entity.motionX=face.getFrontOffsetX()*.1;entity.motionZ=face.getFrontOffsetZ()*.1;}
                        inputHistory.add(history.containsKey(entity.getUniqueID())?position(history.get(entity.getUniqueID())):JsonNull.INSTANCE);before.add(RampRiderSelectionLegacyOracle.actor(entity));steps.add(entity.stepHeight);
                    }
                    JsonObject row=new JsonObject();row.addProperty("transition",transition);row.addProperty("facing",face.name());row.addProperty("material",material);row.addProperty("pixels",pixels);row.addProperty("speed",speed);row.addProperty("mode",mode);row.addProperty("distance",distance);row.addProperty("scenario",scenario);row.add("before",before);row.add("history",inputHistory);row.add("stepHeights",steps);JsonArray boxes=new JsonArray();for(AxisAlignedBB b:world.obstacles)boxes.add(RampRiderSelectionLegacyOracle.box(b));row.add("obstacles",boxes);
                    JsonArray shapes=new JsonArray();if(material==3){for(com.vandorlabs.ramp.RampGeometry.Box b:com.vandorlabs.compat.RampStairs.boxes(world,state))shapes.add(RampRiderSelectionLegacyOracle.values(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ));}else shapes.add(RampRiderSelectionLegacyOracle.values(0,material==2?.5:0,0,1,material==1?.5:1,1));row.add("shapes",shapes);
                    boolean accepted;if(transition==0){step.invoke(tile);accepted=!tile.error;}else accepted=(Boolean)(transition==1?reset.invoke(tile):carry.invoke(tile,1D,0D,false,true));JsonArray after=new JsonArray(),saved=new JsonArray(),noClip=new JsonArray();for(Entity entity:world.actors){after.add(RampRiderSelectionLegacyOracle.actor(entity));saved.add(history.containsKey(entity.getUniqueID())?position(history.get(entity.getUniqueID())):JsonNull.INSTANCE);noClip.add(entity.noClip);}row.add("after",after);row.add("savedHistory",saved);row.add("noClipAfter",noClip);row.addProperty("accepted",accepted);row.addProperty("nextDistance",get(tile,"beltDistance").toString());if(!accepted)refused++;if(!before.equals(after))moved++;if(scenario==4&&!before.get(2).equals(after.get(2)))anchors++;out.print(JSON.toJson(row));out.print('\n');count++;
                }
            }
        }
        if(count!=41472||moved==0)throw new AssertionError("Belt callback coverage "+count+"/"+moved+"/"+refused+"/"+anchors);

        SegmentedReferenceGolden.check(target.toFile(),"fbb85855ddc7b1bfb6c3fe67085f8be493279cb13af7790dadac087453e7d3c0");
        StringBuilder sha=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(target)))sha.append(String.format(Locale.ROOT,"%02x",b&255));System.out.println("Ramp Source belt transitions PASS rows="+count+" moved="+moved+" refused="+refused+" anchored="+anchors+" sha256="+sha+"; fixed collision world, actual TCP/ownership separate");
    }
}
