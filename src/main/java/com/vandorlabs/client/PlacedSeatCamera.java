package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.entity.EntityChairSeat;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.VehicleScan;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;
import java.lang.ref.WeakReference;

/** A parked craft uses the same orbit distance as its captured counterpart. */
final class PlacedSeatCamera {
    private static WeakReference<World> world=new WeakReference<>(null);
    private static UUID mount;private static BlockPos seat;private static boolean dirty=true,validated;private static int supportCount;
    private static final Map<BlockPos,Block> cells=new HashMap<>();
    private static final Set<BlockPos> canopies=new HashSet<>();
    private static AxisAlignedBB observed;private static double distance=6,lastVanilla,lastArm;private static String discovery="not discovered";private static int blockCallbacks,bulkCallbacks,relevantBulk;private static String lastBulk="none";
    private static final WorldRenderListener listener=new WorldRenderListener(){
        @Override public void notifyBlockUpdate(World w,BlockPos p,IBlockState before,IBlockState after,int flags){blockCallbacks++;if(before!=after&&observed!=null&&observed.grow(1).intersects(new AxisAlignedBB(p)))dirty=true;}
        @Override public void markBlockRangeForRenderUpdate(int x1,int y1,int z1,int x2,int y2,int z2){
            bulkCallbacks++;if(observed!=null&&observed.grow(1).intersects(new AxisAlignedBB(x1,y1,z1,x2+1D,y2+1D,z2+1D))){relevantBulk++;lastBulk=x1+","+y1+","+z1+".."+x2+","+y2+","+z2;}
            // Client chunks are installed empty before their payload is applied.
            // Bulk chunk arrival retries only an unvalidated scope; ordinary
            // animation/light render marks never trigger discovery.
            if(!validated&&observed!=null&&x2-x1>=15&&y2-y1>=15&&z2-z1>=15&&observed.grow(1).intersects(new AxisAlignedBB(x1,y1,z1,x2+1D,y2+1D,z2+1D)))dirty=true;
        }
    };
    private PlacedSeatCamera(){}
    private static BlockPos seat(Minecraft mc){
        if(mc.player==null||mc.world==null||!(mc.player.getRidingEntity() instanceof EntityChairSeat))return null;
        EntityChairSeat chair=(EntityChairSeat)mc.player.getRidingEntity();BlockPos p=chair.getChairPos()==null?new BlockPos(chair.posX,chair.posY,chair.posZ):chair.getChairPos();
        return mc.world.getBlockState(p).getBlock() instanceof BlockPilotSeat?p:null;
    }
    static void tick(Minecraft mc){
        BlockPos p=seat(mc);World old=world.get();
        if(p==null){if(old!=null)old.removeEventListener(listener);world=new WeakReference<>(null);mount=null;seat=null;validated=false;supportCount=0;cells.clear();canopies.clear();observed=null;return;}
        UUID id=mc.player.getRidingEntity().getUniqueID();
        if(old!=mc.world){if(old!=null)old.removeEventListener(listener);world=new WeakReference<>(mc.world);mc.world.addEventListener(listener);dirty=true;}
        if(!id.equals(mount)||!p.equals(seat)){mount=id;seat=p;dirty=true;distance=6;blockCallbacks=bulkCallbacks=relevantBulk=0;lastBulk="none";}
        if(dirty){dirty=false;discover(mc.world,p);}
    }
    private static void add(World w,BlockPos p,Map<BlockPos,Block> result){if(w.isBlockLoaded(p)&&!w.isAirBlock(p))result.put(p.toImmutable(),w.getBlockState(p).getBlock());}
    private static void canopy(World w,TileEntityCanopy t,Map<BlockPos,Block> result,Set<BlockPos> owners){TileEntityCanopy root=t.owner();owners.add(root.getPos());for(BlockPos p:root.cells())add(w,p,result);}
    private static void discover(World w,BlockPos p){
        cells.clear();canopies.clear();Map<BlockPos,Block> found=new HashMap<>();Set<BlockPos> owners=new HashSet<>(),seen=new HashSet<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();queue.add(p);int supports=0,controllers=0,limit=Math.min(4096,com.vandorlabs.config.BlockAvailability.vehicleMaxBlocks());boolean complete=true;AxisAlignedBB bounds=new AxisAlignedBB(p);
        while(!queue.isEmpty()){
            BlockPos at=queue.removeFirst();if(!seen.add(at))continue;if(seen.size()>limit*7||!w.isBlockLoaded(at)){complete=false;break;}
            IBlockState state=w.getBlockState(at);Block b=state.getBlock();if(b.isAir(state,w,at))continue;
            if(state.getMaterial().isLiquid()||state.getBlockHardness(w,at)<0||found.size()>=limit){complete=false;break;}
            bounds=bounds.union(new AxisAlignedBB(at));if(bounds.maxX-bounds.minX>64||bounds.maxY-bounds.minY>64||bounds.maxZ-bounds.minZ>64){complete=false;break;}
            found.put(at.toImmutable(),b);if(b instanceof BlockVehicleController)controllers++;
            net.minecraft.tileentity.TileEntity raw=w.getTileEntity(at);
            if(raw instanceof TileEntityCanopy)canopy(w,(TileEntityCanopy)raw,found,owners);
            if(raw instanceof TileEntityRampController)for(BlockPos cell:((TileEntityRampController)raw).vehicleCells())add(w,cell,found);
            if(b instanceof BlockLandingGear){boolean root=!(b instanceof BlockTelescopicLandingGear)||!state.getValue(BlockTelescopicLandingGear.LOWER);if(b instanceof BlockWheel)root=state.getValue(BlockWheel.PART)==0;if(root)supports++;continue;}
            if(b instanceof BlockControlledRamp)continue;
            for(EnumFacing face:EnumFacing.values())if(VehicleScan.touches(w,at,state,face))queue.add(at.offset(face));
        }
        observed=bounds;discovery="complete="+complete+", controllers="+controllers+", supports="+supports+", found="+found.size()+", seen="+seen.size()+", bounds="+bounds;
        supportCount=supports;validated=complete&&controllers==1&&supports>=3;
        if(validated){cells.putAll(found);canopies.addAll(owners);distance=Math.min(96,Math.max(6,Math.hypot(bounds.maxX-bounds.minX,bounds.maxZ-bounds.minZ)*.8+4));}
        else {
            add(w,p,cells);add(w,p.up(),cells);
            net.minecraft.tileentity.TileEntity top=w.getTileEntity(p.up());if(top instanceof TileEntityCanopy)canopy(w,(TileEntityCanopy)top,cells,canopies);
        }
    }
    private static boolean ignored(World w,BlockPos p,IBlockState state){
        if(cells.get(p)==state.getBlock())return true;
        if(state.getBlock() instanceof BlockCanopy){net.minecraft.tileentity.TileEntity tile=w.getTileEntity(p);return tile instanceof TileEntityCanopy&&canopies.contains(((TileEntityCanopy)tile).owner().getPos());}return false;
    }
    static boolean wheel(net.minecraftforge.client.event.MouseEvent event){Minecraft mc=Minecraft.getMinecraft();if(event.getDwheel()==0||mc.currentScreen!=null||mc.gameSettings.thirdPersonView==0||seat(mc)==null)return false;tick(mc);distance=MathHelper.clamp(distance+(event.getDwheel()<0?2:-2),4,96);event.setCanceled(true);return true;}
    static boolean camera(net.minecraftforge.client.event.EntityViewRenderEvent.CameraSetup event){
        Minecraft mc=Minecraft.getMinecraft();if(mc.gameSettings.thirdPersonView==0||mc.gameSettings.debugCamEnable||seat(mc)==null)return false;if(mount==null||world.get()!=mc.world)tick(mc);
        float partial=(float)event.getRenderPartialTicks();Vec3d eye=new Vec3d(mc.player.prevPosX+(mc.player.posX-mc.player.prevPosX)*partial,mc.player.prevPosY+(mc.player.posY-mc.player.prevPosY)*partial+mc.player.getEyeHeight(),mc.player.prevPosZ+(mc.player.posZ-mc.player.prevPosZ)*partial);
        boolean front=mc.gameSettings.thirdPersonView==2;Vec3d away=Vec3d.fromPitchYaw(mc.player.rotationPitch,mc.player.rotationYaw).scale(front?1:-1);
        double vanilla=vanillaArm(mc,eye,front),arm=distance;
        for(int n=0;n<8;n++){Vec3d start=eye.addVector((n&1)==0?-.1:.1,(n&2)==0?-.1:.1,(n&4)==0?-.1:.1);RayTraceResult hit=trace(mc.world,start,start.add(away.scale(distance)));if(hit!=null)arm=Math.min(arm,Math.max(0,start.distanceTo(hit.hitVec)-.1));}
        lastVanilla=vanilla;lastArm=arm;GlStateManager.translate(0,0,(front?1:-1)*(arm-vanilla));return true;
    }
    static boolean hasCraftScope(){return validated;}
    static int supportCount(){return supportCount;}
    static boolean owns(BlockPos pos){return cells.containsKey(pos);}
    static String diagnostic(Minecraft mc){net.minecraft.entity.Entity ride=mc.player.getRidingEntity();return "mount="+(ride==null?"none":ride.getClass().getSimpleName()+"@"+ride.getPositionVector()+" bbox="+ride.getEntityBoundingBox())+", resolvedSeat="+seat(mc)+", cachedSeat="+seat+", dirty="+dirty+", ignored="+cells.size()+", desired="+distance+", vanilla="+lastVanilla+", arm="+lastArm+", debugCam="+mc.gameSettings.debugCamEnable+", callbacks="+blockCallbacks+"/"+bulkCallbacks+" relevantBulk="+relevantBulk+" lastBulk="+lastBulk+", "+discovery;}
    /** Match vanilla's eight rays, including its diagonal end-X offset. */
    private static double vanillaArm(Minecraft mc,Vec3d eye,boolean front){
        float yaw=mc.player.rotationYaw,pitch=mc.player.rotationPitch+(front?180:0);double x=-MathHelper.sin(yaw*.017453292F)*MathHelper.cos(pitch*.017453292F)*4,z=MathHelper.cos(yaw*.017453292F)*MathHelper.cos(pitch*.017453292F)*4,y=-MathHelper.sin(pitch*.017453292F)*4,d=4;
        for(int n=0;n<8;n++){double a=((n&1)*2-1)*.1F,b=(((n>>1)&1)*2-1)*.1F,c=(((n>>2)&1)*2-1)*.1F;RayTraceResult hit=mc.world.rayTraceBlocks(eye.addVector(a,b,c),eye.addVector(-x+a+c,-y+b,-z+c));if(hit!=null)d=Math.min(d,eye.distanceTo(hit.hitVec));}return d;
    }
    /** Voxel traversal keeps foreign solids inside the hull bounds as real occluders. */
    private static RayTraceResult trace(World w,Vec3d start,Vec3d end){
        Vec3d delta=end.subtract(start);int x=MathHelper.floor(start.x),y=MathHelper.floor(start.y),z=MathHelper.floor(start.z);double tx=next(start.x,delta.x,x),ty=next(start.y,delta.y,y),tz=next(start.z,delta.z,z);RayTraceResult best=null;double entry=0;
        for(int steps=0;steps<512;steps++){
            BlockPos p=new BlockPos(x,y,z);if(!w.isBlockLoaded(p))return best!=null?best:new RayTraceResult(start.add(delta.scale(entry)),EnumFacing.UP,p);
            IBlockState state=w.getBlockState(p);if(!ignored(w,p,state)&&state.getBlock().canCollideCheck(state,false)){RayTraceResult hit=state.collisionRayTrace(w,p,start,end);if(hit!=null&&(best==null||start.squareDistanceTo(hit.hitVec)<start.squareDistanceTo(best.hitVec)))best=hit;}
            double t=Math.min(tx,Math.min(ty,tz));if(t>1||!Double.isFinite(t))break;entry=Math.max(0,t);
            if(tx<=t){x+=delta.x>0?1:-1;tx+=Math.abs(1/delta.x);}if(ty<=t){y+=delta.y>0?1:-1;ty+=Math.abs(1/delta.y);}if(tz<=t){z+=delta.z>0?1:-1;tz+=Math.abs(1/delta.z);}
        }return best;
    }
    private static double next(double start,double delta,int cell){return delta==0?Double.POSITIVE_INFINITY:((delta>0?cell+1:cell)-start)/delta;}
}
