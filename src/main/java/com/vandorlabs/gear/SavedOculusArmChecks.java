package com.vandorlabs.gear;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.VehicleStructure;
import com.vandorlabs.network.*;
import java.io.File;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Exact copied Oculus/dual-column regression; no frame or support blocks are replaced by setup. */
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class SavedOculusArmChecks {
    private static final BlockPos LEFT=new BlockPos(-73,83,145),RIGHT=new BlockPos(-71,83,145),CORE=new BlockPos(-73,88,145);
    private static final boolean JOINED=Boolean.getBoolean("vandorlabs.savedOculusJoined");
    private static int ticks,stage,started,checks,prepared,detached;private static boolean obstacleWitness;private static boolean sent,settingsSent,finished;private static double minimum=1;private static String transientPose,status="starting";
    private static final Map<BlockPos,IBlockState> states=new LinkedHashMap<>();private static final Map<BlockPos,NBTTagCompound> tiles=new LinkedHashMap<>();private static final Map<BlockPos,IBlockState> supports=new LinkedHashMap<>();
    private static List<Vec3d> nativeVertices;private static int emittedSamples,minimumOwners=9,maximumOwners;
    private static Vec3d camera,aim;private static BlockPos source;private static int[] coreNormal;private static BlockPos selected;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new IllegalStateException("saved-oculus stage="+stage+" tick="+ticks+" "+status+": "+why);}
    private static GearTileEntity root(World world,BlockPos at){TileEntity raw=world.getTileEntity(at);need(raw instanceof GearTileEntity,"saved arm exists "+at);GearTileEntity owner=((GearTileEntity)raw).loadedController();need(owner!=null,"saved loaded controller "+at);return owner;}
    private static void location(EntityPlayerMP actor,GearTileEntity arm){
        Vec3d target=null,eye=null;RayTraceResult selectedHit=null;
        for(AxisAlignedBB box:arm.collisionBoxes()){if(target!=null)break;if(box.minY>arm.getPos().getY()+1.2)continue;Vec3d center=new Vec3d((box.minX+box.maxX)/2,(box.minY+box.maxY)/2,(box.minZ+box.maxZ)/2);
            for(EnumFacing side:EnumFacing.HORIZONTALS){for(double lift:new double[]{2,3,1.5}){Vec3d candidate=center.add(new Vec3d(side.getDirectionVec()).scale(3)).addVector(0,lift,0);AxisAlignedBB body=new AxisAlignedBB(candidate.x-.3,candidate.y-actor.getEyeHeight(),candidate.z-.3,candidate.x+.3,candidate.y-actor.getEyeHeight()+actor.height,candidate.z+.3);if(!actor.world.getCollisionBoxes(actor,body).isEmpty()||!actor.world.checkNoEntityCollision(body,actor))continue;RayTraceResult hit=GearInteractions.trace(actor.world,candidate,center);if(hit!=null&&arm.members().contains(hit.getBlockPos())){target=hit.hitVec;eye=candidate;selectedHit=hit;break;}}if(target!=null)break;}}
        need(target!=null,"actual visible mounting-base ray available");source=selectedHit.getBlockPos();aim=target;Vec3d d=target.subtract(eye);actor.setSneaking(false);actor.connection.setPlayerLocation(eye.x,eye.y-actor.getEyeHeight(),eye.z,(float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));camera=new Vec3d(eye.x,eye.y-actor.getEyeHeight(),eye.z);actor.motionX=actor.motionY=actor.motionZ=0;
    }
    private static void snapshot(WorldServer world){
        TileEntity raw=world.getTileEntity(CORE);need(raw instanceof TileEntityConstructor&&((TileEntityConstructor)raw).formed(),"exact Oculus owner is formed");TileEntityConstructor core=(TileEntityConstructor)raw;EnumFacing facing=core.facing();coreNormal=new int[]{facing.getFrontOffsetX(),0,facing.getFrontOffsetZ()};
        for(int x=0;x<3;x++)for(int y=0;y<3;y++)for(int z=0;z<3;z++){BlockPos at=core.cell(x,y,z);states.put(at,world.getBlockState(at));TileEntity tile=world.getTileEntity(at);need(tile!=null,"all27 original reservations loaded");tiles.put(at,tile.writeToNBT(new NBTTagCompound()));}
        for(BlockPos base:new BlockPos[]{LEFT,RIGHT})for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){BlockPos at=base.add(x,-1,z);supports.put(at,world.getBlockState(at));}
    }
    private static void unchanged(WorldServer world){for(Map.Entry<BlockPos,IBlockState> entry:states.entrySet()){BlockPos at=entry.getKey();need(world.getBlockState(at)==entry.getValue(),"native Core state/facing unchanged at "+at);TileEntity tile=world.getTileEntity(at);need(tile!=null&&tiles.get(at).equals(tile.writeToNBT(new NBTTagCompound())),"exact Core own/opaque fields unchanged at "+at);}for(Map.Entry<BlockPos,IBlockState> entry:supports.entrySet())need(world.getBlockState(entry.getKey())==entry.getValue(),"original mounting/ground support retained "+entry.getKey());}
    private static void projection(GearTileEntity arm,String where){
        ArmPayload payload=arm.payload();if(payload==null||payload.materialized())return;
        double phase=arm.progress();for(VehicleStructure.Cell cell:payload.blocks.cells)if(cell.state.getBlock() instanceof BlockConstructorCore){
            EnumFacing facing=cell.state.getValue(BlockVandorDirectional.FACING);Vec3d a=payload.transform(arm,phase,cell.pos.getX()+.5,cell.pos.getY()+.5,cell.pos.getZ()+.5),b=payload.transform(arm,phase,cell.pos.getX()+.5+facing.getFrontOffsetX(),cell.pos.getY()+.5,cell.pos.getZ()+.5+facing.getFrontOffsetZ()),normal=b.subtract(a);a=a.add(new Vec3d(arm.getPos()));
            if(normal.squareDistanceTo(new Vec3d(coreNormal[0],coreNormal[1],coreNormal[2]))>1e-10||phase==1&&a.squareDistanceTo(new Vec3d(CORE).addVector(.5,.5,.5))>1e-10)transientPose=where+" phase="+phase+" center="+a+" normal="+normal+" localFacing="+facing;
            System.out.println("[vandorlabs][saved-oculus] projection "+where+" phase="+phase+" center="+a+" normal="+normal+" facing="+facing);
        }
    }
    private static void obstacles(GearTileEntity arm){
        List<AxisAlignedBB> before=GearGeometry.boxes(arm,1),after=GearGeometry.boxes(arm,23/24D);int emitted=0;System.out.println("[vandorlabs][saved-oculus] sweep boxes before="+before.size()+" after="+after.size());
        for(int i=0;i<Math.min(before.size(),after.size())&&emitted<12;i++){if(before.get(i).equals(after.get(i)))continue;AxisAlignedBB sweep=GearGeometry.swept(arm,i,before.get(i),after.get(i),1,23/24D);for(AxisAlignedBB hit:GearGeometry.foreign(arm.getWorld(),arm,sweep)){String other="terrain";for(BlockPos at:new BlockPos[]{LEFT,RIGHT}){GearTileEntity peer=root(arm.getWorld(),at);if(peer!=arm)for(AxisAlignedBB box:peer.collisionBoxes())if(box.equals(hit))other="other-independent-arm@"+peer.getPos();}System.out.println("[vandorlabs][saved-oculus] obstacle index="+i+" sweep="+sweep+" hit="+hit+" owner="+other);if(++emitted>=12)break;}}
    }
    private static List<Vec3d> emitted(TileEntityConstructor tile){
        net.minecraft.client.renderer.BufferBuilder buffer=new net.minecraft.client.renderer.BufferBuilder(1024*1024);buffer.begin(7,net.minecraft.client.renderer.vertex.DefaultVertexFormats.BLOCK);
        com.vandorlabs.client.constructors.TEConstructor renderer=(com.vandorlabs.client.constructors.TEConstructor)net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.instance.<TileEntityConstructor>getRenderer(tile);
        com.vandorlabs.client.constructors.MachineVisualMotion.at(0,()->renderer.renderTileEntityFast(tile,tile.getPos().getX(),tile.getPos().getY(),tile.getPos().getZ(),0,-1,1,buffer));buffer.finishDrawing();need(buffer.getVertexCount()>0&&buffer.getVertexCount()<=65536,"bounded actual authored Core emitted vertices");
        net.minecraft.client.renderer.vertex.VertexFormat format=buffer.getVertexFormat();int offset=-1;for(int i=0;i<format.getElementCount();i++)if(format.getElement(i).getUsage()==net.minecraft.client.renderer.vertex.VertexFormatElement.EnumUsage.POSITION){offset=format.getOffset(i);break;}need(offset>=0,"actual emitted format includes positions");int stride=format.getNextOffset();java.nio.ByteBuffer raw=buffer.getByteBuffer();List<Vec3d> result=new ArrayList<>(buffer.getVertexCount());for(int i=0;i<buffer.getVertexCount();i++){int at=i*stride+offset;result.add(new Vec3d(raw.getFloat(at),raw.getFloat(at+4),raw.getFloat(at+8)));}return result;
    }
    private static void clientWitness(Minecraft mc){
        TileEntity nativeCore=mc.world.getTileEntity(CORE);int owners=nativeCore instanceof TileEntityConstructor&&((TileEntityConstructor)nativeCore).renderReady()?1:0;
        if(nativeVertices==null&&owners==1)nativeVertices=emitted((TileEntityConstructor)nativeCore);
        Set<GearTileEntity> visited=Collections.newSetFromMap(new IdentityHashMap<GearTileEntity,Boolean>());
        for(BlockPos pos:new BlockPos[]{LEFT,RIGHT}){TileEntity raw=mc.world.getTileEntity(pos);if(!(raw instanceof GearTileEntity))continue;GearTileEntity arm=((GearTileEntity)raw).loadedController();if(arm==null||!visited.add(arm)||arm.payload()==null||arm.payload().materialized()||arm.payload().hasOwnershipJournal())continue;
            for(TileEntity body:arm.payload().view(arm).tiles.values())if(body instanceof TileEntityConstructor&&((TileEntityConstructor)body).renderReady()){
                owners++;if(nativeVertices!=null&&arm.progress()==1&&emittedSamples<8){double rendered=com.vandorlabs.gear.client.GearRenderer.renderedProgress(arm,1);List<Vec3d> captured=emitted((TileEntityConstructor)body);need(captured.size()==nativeVertices.size(),"native/captured actual authored vertex count unchanged");double error=0;for(int i=0;i<captured.size();i++){Vec3d local=captured.get(i);Vec3d world=arm.payload().transform(arm,rendered,local.x,local.y,local.z).add(new Vec3d(arm.getPos()));error=Math.max(error,world.distanceTo(nativeVertices.get(i)));}System.out.println("[vandorlabs][saved-oculus] actual-emitted vertices="+captured.size()+" authority="+arm.progress()+" rendered="+rendered+" maxWorldError="+error+" owners="+owners);if(error>.0001)transientPose="actual-emitted authority="+arm.progress()+" rendered="+rendered+" worldVertexError="+error;emittedSamples++;}
            }
        }
        minimumOwners=Math.min(minimumOwners,owners);maximumOwners=Math.max(maximumOwners,owners);if(owners>1)transientPose="duplicate native/captured Core render owners="+owners;
    }
    private static String clientSync="pending";
    private static boolean ready(Minecraft mc)throws Exception{boolean ack=ArmInteractionSync.acknowledged(mc);clientSync="clientMode="+mc.playerController.getCurrentGameType()+" slot="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand()+" pos="+mc.player.getPositionVector()+" wanted="+camera+" nativeVertices="+(nativeVertices==null?0:nativeVertices.size())+" server="+ArmInteractionSync.status();return camera!=null&&mc.playerController.getCurrentGameType()==GameType.CREATIVE&&mc.player.inventory.currentItem==0&&mc.player.getDistanceSq(camera.x,camera.y,camera.z)<.0001&&ack;}
    public static boolean tick(Minecraft mc,File output)throws Exception{
        if(Boolean.getBoolean("vandorlabs.coreHandoff"))return ArmCoreHandoffChecks.tick(mc,output);
        if(Boolean.getBoolean("vandorlabs.savedCoreVisual4"))return SavedCoreVisualChecks.tick(mc,output);
        if(Boolean.getBoolean("vandorlabs.dishContinuity"))return ArmDishContinuityChecks.tick(mc,output);
        if(Boolean.getBoolean("vandorlabs.savedOculusTransition"))return SavedOculusTransitionChecks.tick(mc,output);
        ticks++;mc.displayGuiScreen(stage==10||stage==12?mc.currentScreen:null);mc.gameSettings.pauseOnLostFocus=false;
        if(ticks<60)return false;
        if(stage==1||stage==10||stage==12||stage==14||stage==17){
            need(ticks-started<240,"client interaction synchronization "+clientSync);
            if(!sent&&ready(mc)&&nativeVertices!=null){
                boolean tool=stage==10||stage==12;if(tool?mc.player.getHeldItemMainhand().getItem()!=com.vandorlabs.items.ModItems.CONFIGURIZER:!mc.player.getHeldItemMainhand().isEmpty())return false;
                RayTraceResult hit=GearInteractions.trace(mc.world,mc.player.getPositionEyes(1),mc.player.getPositionEyes(1).add(mc.player.getLook(1).scale(5.5)));if(hit==null||!hit.getBlockPos().equals(source))return false;
                PacketHandler.INSTANCE.sendToServer(new MessageModularGearUse(source));sent=true;
            }
            if((stage==10||stage==12)&&sent&&mc.currentScreen instanceof com.vandorlabs.client.GuiModularGear&&mc.player.openContainer instanceof com.vandorlabs.container.ContainerModularGear&&!settingsSent){
                GearTileEntity client=root(mc.world,selected);NBTTagCompound settings=client.settings();settings.setBoolean("JoinArms",true);settings.setInteger("JoinRange",4);settings.setInteger("LoadHeight",3);settings.setBoolean("CarryLoad",false);settings.setInteger("Direction",0);settings.setBoolean("MiddleHinge",true);settings.setInteger("HingeMode",2);settings.setInteger("KinkDirection",0);settings.setBoolean("RotatePayload",false);settings.setBoolean("Deployed",true);PacketHandler.INSTANCE.sendToServer(new MessageModularGear(mc.player.openContainer.windowId,settings));settingsSent=true;
            }
        }
        if(stage<=2)clientWitness(mc);
        TileEntity client=mc.world.getTileEntity(RIGHT);if(stage<=2&&client instanceof GearTileEntity&&coreNormal!=null&&((GearTileEntity)client).payload()!=null&&!((GearTileEntity)client).payload().materialized())projection((GearTileEntity)client,"client");
        return mc.getIntegratedServer().callFromMainThread(()->{
            WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            if(stage==0){for(int x=-6;x<=-3;x++)for(int z=7;z<=10;z++)world.getChunkFromChunkCoords(x,z);actor.setGameType(GameType.CREATIVE);actor.capabilities.isFlying=true;actor.sendPlayerAbilities();actor.inventory.currentItem=0;actor.connection.sendPacket(new net.minecraft.network.play.server.SPacketHeldItemChange(0));actor.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);actor.inventoryContainer.detectAndSendChanges();snapshot(world);GearTileEntity arm=root(world,RIGHT),peer=root(world,LEFT);need(arm!=peer&&!arm.joinEnabled()&&!peer.joinEnabled()&&arm.columns().size()==1&&peer.columns().size()==1,"archive remains exact independent pair");need(arm.assembly().columnHeight()==5&&arm.mountDirection()==EnumFacing.UP&&arm.payload()!=null&&arm.payload().materialized()&&arm.payload().blocks.cells.size()==27,"exact saved Large UP/height5/27-cell native payload");System.out.println("[vandorlabs][saved-oculus] begin right="+arm.settings()+" left="+peer.settings());ArmTransactions.testFault=point->{if(point.equals("prepared"))prepared++;if(point.equals("detach")){GearTileEntity active=root(world,stage>=10?LEFT:RIGHT);if(active.payload()!=null&&!active.payload().materialized()){detached++;projection(active,"server-detach");}}};location(actor,arm);stage=1;started=ticks;return false;}
            GearTileEntity arm=root(world,stage<10?RIGHT:LEFT);minimum=Math.min(minimum,arm.progress());status="phase="+arm.progress()+" target="+arm.target()+" error="+arm.error()+" lock="+ArmTransactions.locked(world,arm.getPos());
            if(stage==1){if(!sent)return false;stage=2;started=ticks;return false;}
            if(stage==2){if(!obstacleWitness&&arm.payload()!=null&&!arm.payload().materialized()){obstacleWitness=true;obstacles(arm);}need(ticks-started<300,"exact activation settles");if(ArmTransactions.locked(world,RIGHT)||ticks-started<30)return false;if(arm.progress()!=1||!arm.target())return false;unchanged(world);need(detached>0&&prepared>0,"actual packet reached asynchronous capture and restore");need(transientPose==null,"reported transient Core pose must remain in its original world frame: "+transientPose);RayTraceResult hit=world.rayTraceBlocks(new Vec3d(-72.5,89.5,140),new Vec3d(-72.5,89.5,146),false,false,false);need(hit!=null&&states.containsKey(hit.getBlockPos()),"refused Core remains natively ray selectable");System.out.println("[vandorlabs][saved-oculus] exact-refusal PASS minPhase="+minimum+" clientEmittedSamples="+emittedSamples+" renderOwners="+minimumOwners+".."+maximumOwners+" nativeCore27/unchangedFacing/whole/selectable/supports error="+arm.error());if(!JOINED){ArmTransactions.testFault=null;finished=true;System.out.println("[vandorlabs][saved-oculus] PASS exact-refusal checks="+checks);return true;}selected=LEFT;actor.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(com.vandorlabs.items.ModItems.CONFIGURIZER));actor.inventoryContainer.detectAndSendChanges();location(actor,root(world,LEFT));stage=10;started=ticks;sent=settingsSent=false;return false;}
            if(stage==10||stage==12){if(!settingsSent)return false;GearTileEntity configured=root(world,selected);if(!configured.joinEnabled()||configured.hingeMode()!=com.vandorlabs.gear.core.ArmHinge.Mode.DOUBLE||configured.direction()!=com.vandorlabs.gear.core.GearMotion.Direction.NORTH||configured.joinRange()!=4)return false;actor.closeScreen();if(stage==10){selected=RIGHT;location(actor,root(world,RIGHT));stage=12;started=ticks;sent=settingsSent=false;return false;}stage=13;started=ticks;return false;}
            if(stage==13){need(ticks-started<180,"matching player-configured joined group forms");if(root(world,LEFT)!=root(world,RIGHT)||arm.columns().size()!=2)return false;unchanged(world);actor.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);actor.inventoryContainer.detectAndSendChanges();location(actor,arm);stage=14;started=ticks;sent=false;return false;}
            if(stage==14){if(!sent)return false;stage=15;started=ticks;return false;}
            if(stage==15){need(ticks-started<360,"joined complete Core moves "+status);if(ArmTransactions.locked(world,arm.getPos()))return false;if(ticks-started>30&&arm.progress()==1&&arm.target()&&!arm.error().isEmpty())throw new IllegalStateException("Joined full Core rejected after actual matching settings: "+arm.error());if(arm.progress()!=0)return false;need(arm.payload()!=null&&arm.payload().blocks.cells.size()==27,"joined full Core owns all27 reservations");ArmCorePayload.validate(arm.payload().blocks);need(!arm.payload().materialized(),"fractional joined endpoint retains captured ownership");int capturedOwners=0;for(VehicleStructure.Cell cell:arm.payload().blocks.cells)if(cell.state.getBlock() instanceof BlockConstructorCore)capturedOwners++;need(capturedOwners==1,"captured complete Core has one root owner");for(BlockPos at:states.keySet())need(!ArmCorePayload.machine(world.getBlockState(at)),"source Core reservation removed while captured "+at);NBTTagCompound saved=arm.writeToNBT(new NBTTagCompound());arm.readFromNBT(saved);arm.operator(actor);arm.onLoad();location(actor,arm);stage=17;started=ticks;sent=false;return false;}
            if(stage==17){if(!sent)return false;stage=18;started=ticks;return false;}
            if(stage==18){need(ticks-started<360,"joined reversal/reload settles "+status);if(ArmTransactions.locked(world,arm.getPos())||arm.progress()!=1||arm.payload()==null||!arm.payload().materialized())return false;unchanged(world);need(root(world,RIGHT)==arm&&arm.columns().size()==2,"one joined controller survives reload/reversal");ArmTransactions.testFault=null;finished=true;System.out.println("[vandorlabs][saved-oculus] PASS checks="+checks+" exactBeforeRefusal/matchingJoinC2S/Core27/realJournals/reload/reversal/sourceOwnership/supports");return true;}
            return finished;
        }).get();
    }
    private SavedOculusArmChecks(){}
}
