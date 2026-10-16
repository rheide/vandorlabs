package com.vandorlabs.client;

import com.vandorlabs.vehicle.*;
import com.vandorlabs.canopy.CanopyMesh;
import com.vandorlabs.tiles.TileEntityCanopy;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;
import java.io.File;
import java.util.concurrent.Future;

/** Normal client/server animation, with the real pilot, interaction packets and displayed frames. */
final class ExistingVehicleAnimationRuntimeChecks {
    private static final BlockPos SEAT=new BlockPos(-110,85,152),CONTROLLER=new BlockPos(-112,86,152),CANOPY=new BlockPos(-108,86,151);
    private static net.minecraft.block.state.IBlockState sourceSeat,sourceController,sourceCanopy;
    private static String sourceCanopySettings;
    private static int sourceCanopyWidth;
    private static Future<?> pending;
    private static int stage,age,frames,phaseFrames,cycle;
    private static boolean registered;
    private static volatile boolean serverOpen,serverClosed;
    private static volatile long serverMisses,serverFallbacks;
    private static long frameStart,lastStart;
    private static String lastPhase;
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("vehicle animation: "+message);}
    public static final class Observer {
        @SubscribeEvent public void frame(TickEvent.RenderTickEvent event){
            if(VehicleAnimationDiagnostics.selected==null)return;
            long now=System.nanoTime();
            if(event.phase==TickEvent.Phase.START){
                if(lastStart!=0){VehicleAnimationDiagnostics.valueAt(lastPhase,"display/frame-interval",now-lastStart);}
                frameStart=lastStart=now;lastPhase=VehicleAnimationDiagnostics.phase;frames++;
            }else if(frameStart!=0)VehicleAnimationDiagnostics.value("display/render-duration",now-frameStart);
        }
    }
    private static TileEntityCanopy shell(EntityGroundVehicle craft){
        if(craft.view!=null)for(net.minecraft.tileentity.TileEntity raw:craft.view.tiles.values())if(raw instanceof TileEntityCanopy){TileEntityCanopy t=(TileEntityCanopy)raw;if(t.draws()&&t.block()!=null&&t.block().kind.equals("long_canopy"))return t;}
        throw new IllegalStateException("vehicle animation: no authoritative Long Canopy");
    }
    private static String cache(EntityGroundVehicle craft){return "cache-entries="+craft.collisionCache.entries()+" cache-boxes="+craft.collisionCache.boxes()+" cache-hits="+craft.collisionCache.hits()+" cache-misses="+craft.collisionCache.misses()+" cache-generation="+craft.collisionCache.generation()+" cache-warming="+craft.collisionCache.warming()+" cache-bypass="+craft.collisionCache.bypass()+" cache-waits="+craft.collisionCache.waits()+" cache-fallbacks="+craft.collisionCache.fallbacks()+" cache-failure="+craft.collisionCache.failure();}
    private static void phase(String name){VehicleAnimationDiagnostics.phase=name;phaseFrames=frames;age=0;System.out.println("[vandorlabs][reprolab] vehicle-animation phase="+name);}
    private static void aim(Minecraft mc,Vec3d point){Vec3d look=point.subtract(mc.player.getPositionEyes(1)).normalize();float yaw=(float)Math.toDegrees(Math.atan2(-look.x,look.z)),pitch=(float)-Math.toDegrees(Math.asin(MathHelper.clamp(look.y,-1,1)));mc.player.rotationYaw=mc.player.prevRotationYaw=yaw;mc.player.rotationPitch=mc.player.prevRotationPitch=pitch;}
    private static void click(Minecraft mc,EntityGroundVehicle craft){
        TileEntityCanopy tile=shell(craft);VehicleInteraction.Target chosen=null;
        for(CanopyMesh.Face face:tile.faces()){
            Vec3d center=Vec3d.ZERO;for(Vec3d vertex:face.vertices)center=center.add(vertex);center=center.scale(1D/face.vertices.length);
            aim(mc,craft.toWorld(CanopyMesh.rotate(center,tile.facing()).add(new Vec3d(tile.getPos()))));
            VehicleInteraction.Target hit=VehicleInteraction.pointed(mc.player);
            if(hit!=null&&hit.craft==craft&&hit.hit.action==VehicleSelection.Action.CANOPY){net.minecraft.tileentity.TileEntity raw=craft.view.getTileEntity(hit.hit.cell.pos);if(raw instanceof TileEntityCanopy&&((TileEntityCanopy)raw).owner()==tile){chosen=hit;break;}}
        }
        require(chosen!=null,"seated real ray reaches Long Canopy from "+mc.player.getPositionEyes(1));
        mc.player.connection.sendPacket(new CPacketPlayer.Rotation(mc.player.rotationYaw,mc.player.rotationPitch,mc.player.onGround));
        VehicleNetwork.Packet packet=new VehicleNetwork.Packet(VehicleNetwork.INTERACT);packet.entity=craft.getEntityId();packet.token=craft.getUniqueID();packet.origin=chosen.hit.cell.pos;
        com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(packet);
        System.out.println("[vandorlabs][reprolab] vehicle-animation actual-canopy-packet cell="+packet.origin+" eye="+mc.player.getPositionEyes(1)+" yaw="+mc.player.rotationYaw+" pitch="+mc.player.rotationPitch);
    }
    private static void shot(Minecraft mc,File output,String name)throws Exception{javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_vehicle_animation_"+name+".png"));}
    static boolean tick(Minecraft mc,int ticks,File output)throws Exception{
        require(++age<1800,"timeout stage="+stage+" phase="+VehicleAnimationDiagnostics.phase+" mount="+mc.player.getRidingEntity());
        if(pending!=null){if(!pending.isDone())return false;pending.get();pending=null;}
        if(stage==9){System.out.println("[vandorlabs][reprolab] existing-vehicle-animation PASS actual-Taxi-assembly seated-validated-packets normal-client-server-ticks displayed-frames immediate-cold-close two-full-cycles exact-primitive-collision-parity");stage=10;return true;}
        if(stage==10)return true;
        if(stage==0){
            if(ticks<40)return false;
            if(!registered){net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new Observer());registered=true;}
            mc.displayGuiScreen(null);mc.gameSettings.thirdPersonView=0;
            System.out.println("[vandorlabs][reprolab] vehicle-animation ENV renderer="+GL11.glGetString(GL11.GL_RENDERER)+" vendor="+GL11.glGetString(GL11.GL_VENDOR)+" gl="+GL11.glGetString(GL11.GL_VERSION)+" resolution="+mc.displayWidth+"x"+mc.displayHeight+" vsync="+mc.gameSettings.enableVsync+" maxfps="+mc.gameSettings.limitFramerate+" renderDistance="+mc.gameSettings.renderDistanceChunks+" java="+System.getProperty("java.version"));
            pending=mc.getIntegratedServer().addScheduledTask(()->{
                net.minecraft.world.WorldServer world=mc.getIntegratedServer().getWorld(0);world.getChunkFromBlockCoords(SEAT);
                EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());player.dismountRidingEntity();player.setSneaking(false);player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);player.capabilities.isFlying=false;player.sendPlayerAbilities();player.setPositionAndUpdate(SEAT.getX()+.5,SEAT.getY(),SEAT.getZ()+.5);
                require(world.getBlockState(SEAT).getBlock() instanceof com.vandorlabs.blocks.BlockPilotSeat,"actual saved seat exists");
                sourceSeat=world.getBlockState(SEAT);sourceController=world.getBlockState(CONTROLLER);sourceCanopy=world.getBlockState(CANOPY);
                require(sourceController.getBlock() instanceof com.vandorlabs.blocks.BlockVehicleController,"actual saved Vehicle Controller exists");
                require(world.getTileEntity(CANOPY) instanceof TileEntityCanopy,"actual saved Long Canopy owner exists");
                TileEntityCanopy owner=(TileEntityCanopy)world.getTileEntity(CANOPY);require(owner.draws()&&owner.block().kind.equals("long_canopy"),"saved joined Long Canopy owner");sourceCanopySettings=owner.large.key();sourceCanopyWidth=owner.width();if(!owner.targetOpen)owner.toggle();
            });stage=1;age=0;return false;
        }
        if(stage==1){
            if(age<12)return false;net.minecraft.tileentity.TileEntity sourceTile=mc.world.getTileEntity(CANOPY);if(!(sourceTile instanceof TileEntityCanopy)||((TileEntityCanopy)sourceTile).progress!=1)return false;
            phase("boarding-coldclose");mc.player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);mc.player.setSneaking(false);
            mc.playerController.processRightClickBlock(mc.player,mc.world,SEAT,EnumFacing.UP,new Vec3d(SEAT).addVector(.5,.5,.5),EnumHand.MAIN_HAND);stage=2;age=0;return false;
        }
        if(!(mc.player.getRidingEntity() instanceof EntityGroundVehicle))return false;
        EntityGroundVehicle craft=(EntityGroundVehicle)mc.player.getRidingEntity();if(craft.structure==null)return false;
        if(stage==2){
            BlockPos seat=craft.seatForPassenger(mc.player);require(seat!=null&&craft.structure.at(seat)!=null&&craft.structure.at(seat).state.equals(sourceSeat),"boarded actual saved Pilot Seat state");
            require(craft.toWorld(new Vec3d(seat).addVector(.5,0,.5)).squareDistanceTo(new Vec3d(SEAT).addVector(.5,0,.5))<1,"boarded seat remains at saved source location");
            VehicleStructure.Cell controller=craft.structure.controller();require(controller!=null&&controller.pos.equals(seat.add(CONTROLLER.subtract(SEAT)))&&controller.state.equals(sourceController),"saved Controller state and exact relative position preserved");
            VehicleAnimationDiagnostics.selected=craft.getUniqueID();TileEntityCanopy tile=shell(craft);
            require(tile.getPos().equals(seat.add(CANOPY.subtract(SEAT)))&&craft.structure.at(tile.getPos()).state.equals(sourceCanopy)&&tile.width()==sourceCanopyWidth&&tile.large.key().equals(sourceCanopySettings),"saved joined Long Canopy source position, facing, width and complete geometry/settings preserved");
            System.out.println("[vandorlabs][reprolab] vehicle-animation craft="+craft.getUniqueID()+" entity="+craft.getEntityId()+" cells="+craft.structure.cells.size()+" canopy="+tile.getPos()+" width="+tile.width()+" settings="+tile.large.key()+" bbox="+craft.getEntityBoundingBox()+" "+cache(craft));
            require(craft.collisionCache.entries()<17,"first closure starts before all futureposes are prepared");cycle=-1;phase("cold-closing-after-board");if(tile.targetOpen)click(mc,craft);stage=6;return false;
        }
        if(stage==3&&frames-phaseFrames>=60){shot(mc,output,"closed");phase("opening-"+cycle);click(mc,craft);stage=4;return false;}
        if(stage==4||stage==6){
            if(pending==null){final java.util.UUID id=craft.getUniqueID();pending=mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());require(player.getRidingEntity() instanceof EntityGroundVehicle,"pilot remains aboard server craft");EntityGroundVehicle server=(EntityGroundVehicle)player.getRidingEntity();require(id.equals(server.getUniqueID()),"same authoritative craft");TileEntityCanopy tile=shell(server);serverOpen=tile.targetOpen&&tile.progress==1;serverClosed=!tile.targetOpen&&tile.progress==0;serverMisses=server.collisionCache.misses();serverFallbacks=server.collisionCache.fallbacks();
            });}
            TileEntityCanopy tile=shell(craft);
            if(stage==4&&serverOpen&&tile.targetOpen&&tile.progress==1){System.out.println("[vandorlabs][reprolab] vehicle-animation endpoint-open "+craft.collision.buildProfile()+" "+cache(craft));phase("open-settled-"+cycle);stage=5;}
            else if(stage==6&&serverClosed&&!tile.targetOpen&&tile.progress==0){System.out.println("[vandorlabs][reprolab] vehicle-animation endpoint-closed "+craft.collision.buildProfile()+" "+cache(craft));if(cycle<0){require(craft.collisionCache.misses()==0&&craft.collisionCache.fallbacks()==0,"live first client closure has zero synchronous misses/fallbacks: "+cache(craft));require(serverMisses==0&&serverFallbacks==0,"live first server closure has zero synchronous misses/fallbacks: "+serverMisses+"/"+serverFallbacks);System.out.println("[vandorlabs][reprolab] vehicle-animation immediate-cold-close PASS client-server-zero-sync-misses-and-fallbacks");cycle=0;phase("closed-baseline");stage=3;}else{phase("closed-settled-"+cycle);stage=7;}}
            return false;
        }
        if(stage==5&&frames-phaseFrames>=60){shot(mc,output,"open_"+cycle);phase("closing-"+cycle);click(mc,craft);stage=6;return false;}
        if(stage==7&&frames-phaseFrames>=60){if(++cycle<2){phase("opening-"+cycle);click(mc,craft);stage=4;return false;}VehicleAnimationDiagnostics.dump();VehicleAnimationDiagnostics.selected=null;
            pending=mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());require(player.getRidingEntity() instanceof EntityGroundVehicle,"pilot remains aboard for postmeasurement parity");try{EntityGroundVehicle saved=(EntityGroundVehicle)player.getRidingEntity();VehicleCollisionMergeRuntimeChecks.run(saved);VehicleCollisionCacheRuntimeChecks.run(saved);VehicleCanopyColdCacheRuntimeChecks.run(saved);}catch(Exception error){throw new IllegalStateException("postmeasurement collision parity and cache checks",error);}});stage=9;return false;}
        return stage==8;
    }
}
