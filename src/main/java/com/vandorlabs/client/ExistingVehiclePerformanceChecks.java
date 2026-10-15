package com.vandorlabs.client;

import com.vandorlabs.vehicle.*;
import com.vandorlabs.tiles.TileEntityCanopy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import org.lwjgl.opengl.GL11;
import java.io.File;
import java.util.*;
import java.util.concurrent.Future;

/** Opt-in measurements of one saved craft in an explicitly supplied disposable world copy. */
final class ExistingVehiclePerformanceChecks {
    private static Future<?> pending;
    private static java.lang.reflect.Method oldTurnReference,oldClipReference;
    private static void referenceMethods(){if(oldTurnReference!=null)return;try{oldTurnReference=VehicleCollision.class.getDeclaredMethod("legacyTurn",net.minecraft.world.World.class,Entity.class,double.class,double.class,double.class,float.class,float.class);oldClipReference=VehicleCollision.class.getDeclaredMethod("legacyClip",net.minecraft.world.World.class,Entity.class,double.class,double.class,double.class,double.class,int.class,float.class);oldTurnReference.setAccessible(true);oldClipReference.setAccessible(true);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
    private static VehicleStructure closed,opened;
    private static int stage;
    private static double savedX,savedY,savedZ;
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("existing vehicle performance: "+why);}
    private static void stats(String name,long[] samples,String details){long[] a=samples.clone();Arrays.sort(a);System.out.println("[vandorlabs][reprolab] existing-vehicle-profile "+name+" samples="+a.length+" median-us="+a[a.length/2]/1000+" p95-us="+a[(a.length-1)*95/100]/1000+" max-us="+a[a.length-1]/1000+" "+details);}
    private static TileEntityCanopy shell(EntityGroundVehicle craft){for(net.minecraft.tileentity.TileEntity raw:craft.view.tiles.values())if(raw instanceof TileEntityCanopy){TileEntityCanopy t=(TileEntityCanopy)raw;if(t.draws()&&t.block()!=null&&t.block().kind.equals("long_canopy"))return t;}throw new IllegalStateException("saved craft lacks an authoritative long canopy");}
    private static void endpoint(EntityGroundVehicle craft,boolean open){TileEntityCanopy t=shell(craft);t.targetOpen=open;for(int i=0;i<17;i++)VehicleCanopies.tick(craft);require(t.progress==(open?1:0),"canopy endpoint reached");}
    static boolean tick(Minecraft mc,int ticks,File output)throws Exception{
        if(pending!=null){if(!pending.isDone())return false;pending.get();pending=null;}
        if(stage==0){if(ticks<40)return false;stage=1;pending=mc.getIntegratedServer().addScheduledTask(()->{
            WorldServer world=mc.getIntegratedServer().getWorld(0);String[] xyz=System.getProperty("vandorlabs.vehiclePerformancePos","-149,69,53").split(",");BlockPos near=new BlockPos(Integer.parseInt(xyz[0]),Integer.parseInt(xyz[1]),Integer.parseInt(xyz[2]));
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.getChunkFromBlockCoords(near.add(x*16,0,z*16));
            EntityGroundVehicle saved=null;double distance=Double.MAX_VALUE;for(Entity raw:world.loadedEntityList)if(raw instanceof EntityGroundVehicle&&!raw.isDead&&raw.getDistanceSq(near)<distance){saved=(EntityGroundVehicle)raw;distance=raw.getDistanceSq(near);}require(saved!=null&&distance<256,"configured saved craft loaded");
            savedX=saved.posX;savedY=saved.posY;savedZ=saved.posZ;System.out.println("[vandorlabs][reprolab] existing-vehicle-profile saved-cells="+saved.structure.cells.size()+" block-stride="+net.minecraft.client.renderer.vertex.DefaultVertexFormats.BLOCK.getNextOffset());EntityGroundVehicle craft=new EntityGroundVehicle(world);craft.install(saved.structure);craft.setPosition(saved.posX,saved.posY,saved.posZ);endpoint(craft,false);closed=craft.structure;
            long[] moving=new long[16];shell(craft).targetOpen=true;for(int i=0;i<16;i++){long start=System.nanoTime();VehicleCanopies.tick(craft);moving[i]=System.nanoTime()-start;}opened=craft.structure;stats("motion-and-collision-rebuild",moving,"cells="+opened.cells.size()+" boxes="+craft.collision.boxes.size()+" shell-faces="+shell(craft).faces().size()+" "+craft.collision.buildProfile());
            for(int state=0;state<2;state++){craft.install(state==0?closed:opened);for(float yaw:new float[]{0,45,90}){long[] collision=new long[16],reference=new long[16],picking=new long[16];referenceMethods();for(int i=0;i<16;i++){
                float from=yaw+i*.125f,next;double clipped;long start=System.nanoTime();craft.collision.begin(world,saved);try{next=craft.collision.turn(world,saved,craft.posX,craft.posY,craft.posZ,from,1);clipped=craft.collision.clip(world,saved,craft.posX,craft.posY,craft.posZ,.12,0,next);}finally{craft.collision.end();}collision[i]=System.nanoTime()-start;
                start=System.nanoTime();craft.collision.begin(world,saved);try{float old=((Number)oldTurnReference.invoke(craft.collision,world,saved,craft.posX,craft.posY,craft.posZ,from,1f)).floatValue();double oldMove=((Number)oldClipReference.invoke(craft.collision,world,saved,craft.posX,craft.posY,craft.posZ,.12,0,old)).doubleValue();require(old==next&&Math.abs(oldMove-clipped)<1e-6,"paired heading/translation unchanged");}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}finally{craft.collision.end();}reference[i]=System.nanoTime()-start;
                start=System.nanoTime();VehicleSelection.local(craft.structure,craft.view,new Vec3d(6,4,-3),new Vec3d(6,2,15),1024);picking[i]=System.nanoTime()-start;
            }String details="state="+(state==0?"closed":"open")+" yaw="+yaw+" boxes="+craft.collision.boxes.size();stats("steer-and-move",collision,details);stats("steer-and-move-original-reference",reference,details);stats("pick",picking,details);}
                equivalence(world,craft);}
            CanopySubdivisionRuntimeChecks.run();VehicleTerrainReplayRuntimeChecks.run(world);
        });return false;}
        if(stage<=6){int variant=stage-1;VehicleStructure structure=variant<3?closed:opened;EntityGroundVehicle craft=new EntityGroundVehicle(mc.world);craft.install(structure);craft.setPosition(savedX,savedY,savedZ);craft.rotationYaw=craft.prevRotationYaw=(variant%3)*45;measureRender(mc,craft,output,"vehicle-"+(variant<3?"closed":"open")+"-yaw"+(variant%3)*45);stage++;return false;}
        if(stage==7){System.out.println("[vandorlabs][reprolab] existing-vehicle-performance PASS saved-craft closed-open heading render-GPU collision steering picking");stage++;}return true;
    }
    private static void equivalence(WorldServer world,EntityGroundVehicle craft){
        try{
            java.lang.reflect.Method oldTurn=VehicleCollision.class.getDeclaredMethod("legacyTurn",net.minecraft.world.World.class,Entity.class,double.class,double.class,double.class,float.class,float.class);
            java.lang.reflect.Method oldClip=VehicleCollision.class.getDeclaredMethod("legacyClip",net.minecraft.world.World.class,Entity.class,double.class,double.class,double.class,double.class,int.class,float.class);oldTurn.setAccessible(true);oldClip.setAccessible(true);
            BlockPos arena=new BlockPos(240,200,240);List<BlockPos> obstacles=new ArrayList<>();
            for(int x=0;x<=craft.structure.bounds.maxX;x+=3)for(int z=0;z<=craft.structure.bounds.maxZ;z+=3)obstacles.add(arena.add(x,-1,z));
            for(int y=0;y<5;y++)obstacles.add(arena.add(5,y,4));
            for(BlockPos at:obstacles){require(world.isAirBlock(at),"isolated equivalence arena is empty");world.setBlockState(at,net.minecraft.init.Blocks.STONE.getDefaultState(),2);}
            int checks=0;try{
                for(float yaw:new float[]{0,13,45,90,179.5f}){
                    AxisAlignedBB actualBounds=craft.collision.bounds(yaw),expected=null;for(VehicleCollision.OrientedBox box:craft.collision.pose(yaw))expected=expected==null?box.broad:expected.union(box.broad);
                    require(expected.equals(actualBounds),"bounds preserve the exact oriented-box union");
                    for(double dx:new double[]{0,.25})for(float change:new float[]{-2,2}){
                        craft.collision.begin(world,craft);float next;try{next=craft.collision.turn(world,craft,arena.getX()+dx,arena.getY(),arena.getZ(),yaw,change);}finally{craft.collision.end();}
                        craft.collision.begin(world,craft);float old;try{old=((Number)oldTurn.invoke(craft.collision,world,craft,(double)arena.getX()+dx,(double)arena.getY(),(double)arena.getZ(),yaw,change)).floatValue();}finally{craft.collision.end();}
                        require(next==old,"cluster steering preserves obstacle contact yaw="+yaw+" delta="+change);checks++;
                    }
                    for(int axis=0;axis<3;axis++)for(double amount:new double[]{-.6,.6}){
                        craft.collision.begin(world,craft);double next;try{next=craft.collision.clip(world,craft,arena.getX(),arena.getY(),arena.getZ(),amount,axis,yaw);}finally{craft.collision.end();}
                        craft.collision.begin(world,craft);double old;try{old=((Number)oldClip.invoke(craft.collision,world,craft,(double)arena.getX(),(double)arena.getY(),(double)arena.getZ(),amount,axis,yaw)).doubleValue();}finally{craft.collision.end();}
                        require(Math.abs(next-old)<1e-6,"cluster translation preserves contact axis="+axis+" yaw="+yaw+" values="+next+"/"+old);checks++;
                    }
                }
            }finally{for(BlockPos at:obstacles)world.setBlockToAir(at);}
            System.out.println("[vandorlabs][reprolab] existing-vehicle-collision-equivalence PASS checks="+checks+" exact-box-bounds positive-negative-turn all-translation-axes obstacle-floor-column boxes="+craft.collision.boxes.size());
        }catch(ReflectiveOperationException error){throw new IllegalStateException("collision equivalence reference unavailable",error);}
    }
    private static void measureRender(Minecraft mc,EntityGroundVehicle craft,File output,String label)throws Exception{
        Framebuffer fb=new Framebuffer(320,240,true);RenderGroundVehicle renderer=new RenderGroundVehicle(mc.getRenderManager());long[] cpu=new long[24],gpu=new long[24];GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-12,12,-9,9,-60,60);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try{fb.bindFramebuffer(true);for(int sample=-8;sample<24;sample++){GlStateManager.loadIdentity();GlStateManager.rotate(20,1,0,0);GlStateManager.rotate(30,0,1,0);GlStateManager.translate(-craft.structure.bounds.maxX/2,-craft.structure.bounds.maxY/2,-craft.structure.bounds.maxZ/2);GlStateManager.depthMask(true);GlStateManager.clearColor(.1f,.15f,.2f,1);GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);GlStateManager.enableDepth();mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);GL11.glFinish();RenderGroundVehicle.beginFrame();long start=System.nanoTime();renderer.doRender(craft,0,0,0,craft.rotationYaw,1);long uploaded=System.nanoTime();GL11.glFinish();if(sample>=0){cpu[sample]=uploaded-start;gpu[sample]=System.nanoTime()-uploaded;}}
            javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(320,240,fb),"png",new File(output,"shot_existing_"+label+".png"));stats("render-cpu",cpu,label+" cells="+craft.structure.cells.size()+" tiles="+RenderGroundVehicle.tileDraws());stats("render-GPU-finish",gpu,label);
        }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);GL11.glPopAttrib();fb.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);GlStateManager.enableDepth();GlStateManager.depthMask(true);GlStateManager.enableCull();GlStateManager.enableLighting();GlStateManager.color(1,1,1,1);craft.setDead();RenderGroundVehicle.cleanup(mc.world);}
    }
}
