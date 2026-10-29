package com.vandorlabs.gear;

import com.vandorlabs.gear.core.*;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.event.world.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import java.util.*;

/** The animated pose is shared by collision and rendering; source cells remain reserved blocks. */
@Mod.EventBusSubscriber(modid="vandorlabs")
public final class GearGeometry {
    private static final Map<World,Set<GearTileEntity>> ROOTS=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<World,SpatialIndex> INDICES=Collections.synchronizedMap(new WeakHashMap<>());
    private static final class SpatialIndex {
        final Map<Long,Set<GearTileEntity>> chunks=new HashMap<>();
        final Map<GearTileEntity,Set<Long>> membership=new IdentityHashMap<>();
        void remove(GearTileEntity gear){Set<Long> keys=membership.remove(gear);if(keys!=null)for(long key:keys){Set<GearTileEntity> members=chunks.get(key);members.remove(gear);if(members.isEmpty())chunks.remove(key);}}
        void put(GearTileEntity gear){remove(gear);AxisAlignedBB b=gear.getRenderBoundingBox();Set<Long> keys=new HashSet<>();
            for(int x=MathHelper.floor(b.minX)>>4;x<=(MathHelper.floor(b.maxX)>>4);x++)for(int z=MathHelper.floor(b.minZ)>>4;z<=(MathHelper.floor(b.maxZ)>>4);z++){
                long key=((long)x<<32)^(z&0xffffffffL);keys.add(key);chunks.computeIfAbsent(key,k->Collections.newSetFromMap(new IdentityHashMap<>())).add(gear);
            }membership.put(gear,keys);
        }
        Set<GearTileEntity> near(AxisAlignedBB b){int x0=MathHelper.floor(b.minX)>>4,x1=MathHelper.floor(b.maxX)>>4,z0=MathHelper.floor(b.minZ)>>4,z1=MathHelper.floor(b.maxZ)>>4;
            if((long)(x1-x0+1)*(z1-z0+1)>256)return new HashSet<>(membership.keySet());
            Set<GearTileEntity> result=Collections.newSetFromMap(new IdentityHashMap<>());
            for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){Set<GearTileEntity> members=chunks.get(((long)x<<32)^(z&0xffffffffL));if(members!=null)result.addAll(members);}return result;
        }
    }
    private static final Map<String,List<AxisAlignedBB>> MODELS=new java.util.concurrent.ConcurrentHashMap<>();
    private static final ThreadLocal<GearTileEntity> EXCLUDED=new ThreadLocal<>();
    private static final class PreflightSources {
        final World world;final Set<AxisAlignedBB> boxes;
        PreflightSources(World world,Set<AxisAlignedBB> boxes){this.world=world;this.boxes=boxes;}
    }
    private static final ThreadLocal<PreflightSources> PREFLIGHT_SOURCES=new ThreadLocal<>();
    /** Test a complete configuration sweep without removing its native attachment. */
    static boolean startClear(GearTileEntity root,ArmPayload nativePayload){
        Set<AxisAlignedBB> sources=new HashSet<>();
        if(nativePayload!=null&&nativePayload.materialized())for(BlockPos at:nativePayload.placedCells(root)){
            if(!GearAssembly.loaded(root.getWorld(),at))return false;
            List<AxisAlignedBB> boxes=new ArrayList<>();net.minecraft.block.state.IBlockState state=root.getWorld().getBlockState(at);
            state.addCollisionBoxToList(root.getWorld(),at,new AxisAlignedBB(at),boxes,null,false);sources.addAll(boxes);
        }
        PreflightSources previous=PREFLIGHT_SOURCES.get();PREFLIGHT_SOURCES.set(new PreflightSources(root.getWorld(),sources));
        try{for(int step=0;step<GearTileEntity.ANIMATION_TICKS;step++)
            if(!canMove(root,1-step/(double)GearTileEntity.ANIMATION_TICKS,1-(step+1)/(double)GearTileEntity.ANIMATION_TICKS))return false;
            return true;
        }finally{if(previous==null)PREFLIGHT_SOURCES.remove();else PREFLIGHT_SOURCES.set(previous);root.riderPlan=null;}
    }
    public static boolean excluded(GearTileEntity root){return EXCLUDED.get()==root;}
    public static List<AxisAlignedBB> foreign(World world,GearTileEntity root,AxisAlignedBB box){EXCLUDED.set(root);try{return world.getCollisionBoxes(null,box);}finally{EXCLUDED.remove();}}
    public static void register(GearTileEntity tile){World world=tile.getWorld();if(world==null||world instanceof com.vandorlabs.vehicle.VehicleWorld||!world.isBlockLoaded(tile.getPos(),false))return;Set<GearTileEntity> roots=ROOTS.computeIfAbsent(world,k->Collections.newSetFromMap(new IdentityHashMap<>()));SpatialIndex index=INDICES.computeIfAbsent(world,k->new SpatialIndex());if(tile.isController()||!looseWheelBoxes(tile).isEmpty()){roots.add(tile);index.put(tile);}else{roots.remove(tile);index.remove(tile);}}
    public static void remove(GearTileEntity tile){Set<GearTileEntity> roots=ROOTS.get(tile.getWorld());if(roots!=null)roots.remove(tile);SpatialIndex index=INDICES.get(tile.getWorld());if(index!=null)index.remove(tile);}
    @SubscribeEvent public static void unload(WorldEvent.Unload event){ROOTS.remove(event.getWorld());INDICES.remove(event.getWorld());}
    public static java.util.List<GearTileEntity> roots(World world){Set<GearTileEntity> found=ROOTS.get(world);return found==null?Collections.emptyList():new ArrayList<>(found);}
    /** A full-size loose wheel reaches below its four reserved source cells. */
    public static List<AxisAlignedBB> looseWheelBoxes(GearTileEntity tile){
        World world=tile.getWorld();BlockPos pos=tile.getPos();
        if(world==null||tile.isInvalid()||!world.isBlockLoaded(pos))return Collections.emptyList();
        net.minecraft.block.state.IBlockState state=world.getBlockState(pos);
        if(!(state.getBlock() instanceof LargeGearBlock)||((GearBlock)state.getBlock()).kind()!=GearBlock.Kind.WHEEL
            ||!((GearBlock)state.getBlock()).componentRoot(pos,state).equals(pos))return Collections.emptyList();
        GearTileEntity owner=tile.loadedController();if(owner!=null && owner.isOperational())return Collections.emptyList();
        int turns=GearCollision.quarterTurns(state.getValue(GearBlock.FACING));List<AxisAlignedBB> result=new ArrayList<>();
        for(AxisAlignedBB box:MODELS.computeIfAbsent("wheel_large",GearBlock::modelParts))result.add(yaw(box,turns).offset(pos));
        return result;
    }
    @SubscribeEvent public static void collisions(GetCollisionBoxesEvent event){PreflightSources sources=PREFLIGHT_SOURCES.get();if(sources!=null&&sources.world==event.getWorld())event.getCollisionBoxesList().removeIf(sources.boxes::contains);if(event.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld)return;SpatialIndex index=INDICES.get(event.getWorld());if(index==null)return;Set<GearTileEntity> roots=index.near(event.getAabb());
        for(GearTileEntity root:roots)if(!root.isInvalid()&&root.getWorld().isBlockLoaded(root.getPos())&&root.getWorld().getTileEntity(root.getPos())==root)
            for(AxisAlignedBB box:looseWheelBoxes(root))if(box.intersects(event.getAabb()))event.getCollisionBoxesList().add(box);
        for(GearTileEntity root:roots)if(root!=EXCLUDED.get()&&!root.isInvalid()&&root.isOperational()&&root.getWorld().isBlockLoaded(root.getPos())&&root.getWorld().getTileEntity(root.getPos())==root&&root.getRenderBoundingBox().intersects(event.getAabb()))for(AxisAlignedBB original:root.collisionBoxes()){AxisAlignedBB box=com.vandorlabs.motion.PlatformRiders.supportCollision(original,event.getEntity(),root.progress()>0&&root.progress()<1);if(box!=null&&box.getAverageEdgeLength()>0&&box.intersects(event.getAabb()))event.getCollisionBoxesList().add(box);}
    }
    private static GearMotion.Bounds bounds(AxisAlignedBB box){return new GearMotion.Bounds(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ);}
    private static AxisAlignedBB box(GearMotion.Bounds b){return new AxisAlignedBB(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);}
    private static void add(List<AxisAlignedBB> out,AxisAlignedBB local,GearMotion.Pose pose,boolean foot,BlockPos root){out.add(box(foot?pose.transformFootBounds(bounds(local)):pose.transformRigidBounds(bounds(local))).offset(root));}
    private static AxisAlignedBB yaw(AxisAlignedBB b,int turns){return com.vandorlabs.blocks.PanelPlacement.rotateFromNorth(b,new EnumFacing[]{EnumFacing.NORTH,EnumFacing.EAST,EnumFacing.SOUTH,EnumFacing.WEST}[turns&3]);}
    public static List<AxisAlignedBB> boxes(GearTileEntity tile,double progress){
        return tile.boxesAt(progress);
    }
    static List<AxisAlignedBB> computeBoxes(GearTileEntity tile,double progress){return computeBoxes(tile,progress,true);}
    private static List<AxisAlignedBB> computeBoxes(GearTileEntity tile,double progress,boolean payload){
        if(tile.assembly()==null)return Collections.emptyList();GearTopology.Assembly group=tile.assembly();GearMotion.Pose pose=tile.pose(progress);List<AxisAlignedBB> out=new ArrayList<>();BlockPos root=tile.getPos();
        for(BlockPos column:tile.columns()){
            BlockPos sourceColumn=root.add(column);if(!(tile.getWorld().getBlockState(sourceColumn).getBlock() instanceof GearBlock))continue;BlockPos at=root.add(ArmOrientation.inverse(tile.mountDirection(),column));int width=tile.armSize();double shift=(width-1)*.5;
            out.add(new AxisAlignedBB(0,14/16D,0,1,1,1).offset(tile.armShiftX(column),0,tile.armShiftZ(column)).offset(at));out.add(new AxisAlignedBB(.5-5/16D*width,tile.isFixedMount()?0:6/16D,.5-5/16D*width,.5+5/16D*width,14/16D,.5+5/16D*width).offset(tile.armShiftX(column),0,tile.armShiftZ(column)).offset(at));
            if(tile.isSwingArmController())for(AxisAlignedBB arm:armParts())addArm(out,stretch(yaw(arm,tile.direction().ordinal()),group.columnHeight()-tile.footMountOffset()),pose,false,at,tile.direction().axisX()!=0?width:1,tile.direction().axisZ()!=0?width:1,tile.armShiftX(column),tile.armShiftZ(column));
            else addArm(out,new AxisAlignedBB(5/16D,pose.rodBottomY()+tile.footMountOffset(),5/16D,11/16D,pose.rodTopY(),11/16D),pose,false,at,width,width,tile.armShiftX(column),tile.armShiftZ(column));
            AxisAlignedBB socket=group.kind()==GearTopology.FootKind.NONE&&!tile.isSwingArmController()?new AxisAlignedBB(4.25/16D,3/16D,4.25/16D,11.75/16D,5/16D,11.75/16D):new AxisAlignedBB(.25,3/16D,.3125,.75,7/16D,.6875);
            if(group.kind()!=GearTopology.FootKind.NONE||!tile.isFixedMount())addArm(out,socket.offset(0,-group.columnHeight()+tile.footMountOffset()-(group.kind()==GearTopology.FootKind.TRACK&&tile.isSwingArmController()?1/64D:0),0),pose,true,at,width,width,tile.armShiftX(column),tile.armShiftZ(column));
        }
        int turns=tile.footFacing()==EnumFacing.EAST?1:tile.footFacing()==EnumFacing.SOUTH?2:tile.footFacing()==EnumFacing.WEST?3:0;
        if(group.kind()!=GearTopology.FootKind.NONE)for(GearTopology.Cell foot:group.feet()){
            if(group.kind()==GearTopology.FootKind.WHEEL){BlockPos source=tile.localCell(foot);net.minecraft.block.state.IBlockState state=tile.getWorld().getBlockState(source);GearBlock block=state.getBlock() instanceof GearBlock?(GearBlock)state.getBlock():null;AxisAlignedBB wheel=block!=null&&block.kind()==GearBlock.Kind.WHEEL&&block.componentRoot(source,state).equals(source)?yaw(MODELS.computeIfAbsent(block.wheelModel(),GearBlock::modelParts).stream().reduce(AxisAlignedBB::union).get(),turns):new AxisAlignedBB(0,0,0,0,0,0);AxisAlignedBB posed=box(pose.transformFootBounds(bounds(wheel.offset(foot.x,foot.y,foot.z))));
                if(block!=null&&block.kind()==GearBlock.Kind.WHEEL&&block.componentRoot(source,state).equals(source)){AxisAlignedBB connector=yaw(MODELS.computeIfAbsent(block.wheelModel()+"_connector",GearBlock::modelParts).stream().reduce(AxisAlignedBB::union).get(),turns).offset(foot.x,foot.y,foot.z);posed=posed.union(box(pose.transformConnectorBounds(bounds(connector),(connector.minX+connector.maxX)/2,(connector.minY+connector.maxY)/2,(connector.minZ+connector.maxZ)/2)));}
                out.add(posed.offset(root));}
            else if(group.kind()==GearTopology.FootKind.TRACK){BlockPos source=tile.localCell(foot);com.vandorlabs.gear.track.TrackAppearance appearance=com.vandorlabs.gear.track.TrackGeometry.onArm(com.vandorlabs.gear.track.TrackBlock.appearance(tile.getWorld(),source));AxisAlignedBB nativeTrack=com.vandorlabs.gear.track.TrackGeometry.bounds(com.vandorlabs.gear.track.TrackBlock.style(tile.getWorld(),source),tile.footFacing(),appearance);add(out,nativeTrack.offset(foot.x,foot.y,foot.z),pose,true,root);}
            else if(group.kind()==GearTopology.FootKind.PLATE)add(out,new AxisAlignedBB(0,0,0,1,3/16D,1).offset(foot.x,foot.y,foot.z),pose,true,root);
            else{add(out,yaw(new AxisAlignedBB(.25,0,0,.75,5/16D,1),turns).offset(foot.x,foot.y,foot.z),pose,true,root);add(out,yaw(new AxisAlignedBB(0,3/16D,.375,1,5/16D,.625),turns).offset(foot.x,foot.y,foot.z),pose,true,root);}
        }
        if(tile.mountDirection()!=EnumFacing.DOWN){
            List<AxisAlignedBB> oriented=new ArrayList<>();for(AxisAlignedBB b:out)oriented.add(ArmOrientation.box(tile.mountDirection(),b.offset(-root.getX(),-root.getY(),-root.getZ())).offset(root));out=oriented;
        }
        if(payload&&tile.payload()!=null)try{out.addAll(tile.payload().boxes(tile,progress));}catch(RuntimeException | LinkageError failure){tile.quarantinePayload();return Collections.emptyList();}
        return Collections.unmodifiableList(out);
    }
    private static List<AxisAlignedBB> armParts(){return MODELS.computeIfAbsent("swing_linkage_sliced",name->GearBlock.movingArmParts());}
    private static AxisAlignedBB stretch(AxisAlignedBB b,double height){double factor=(height+.25)/1.25;return new AxisAlignedBB(b.minX,GearMotion.PIVOT_Y+(b.minY-GearMotion.PIVOT_Y)*factor,b.minZ,b.maxX,GearMotion.PIVOT_Y+(b.maxY-GearMotion.PIVOT_Y)*factor,b.maxZ);}
    private static void addArm(List<AxisAlignedBB> out,AxisAlignedBB local,GearMotion.Pose pose,boolean foot,BlockPos at,int scaleX,int scaleZ,double shiftX,double shiftZ){AxisAlignedBB scaled=new AxisAlignedBB(.5+(local.minX-.5)*scaleX,local.minY,.5+(local.minZ-.5)*scaleZ,.5+(local.maxX-.5)*scaleX,local.maxY,.5+(local.maxZ-.5)*scaleZ);List<AxisAlignedBB> one=new ArrayList<>();add(one,scaled,pose,foot,at);out.add(one.get(0).offset(shiftX,0,shiftZ));}
    public static boolean canMove(GearTileEntity root,double before,double next){
        World world=root.getWorld();List<AxisAlignedBB> old=boxes(root,before),after=boxes(root,next);if(old.size()!=after.size())return false;
        MovePlan riders=plan(world,old,after,null);
        for(Entity rider:riders.riders)if(com.vandorlabs.motion.PlatformRiders.vehicle(rider)&&!JoinedArms.acceptsVehicle(root,rider,before))return false;
        EXCLUDED.set(root);
        try(com.vandorlabs.motion.PlatformRiders.CollisionScope scope=new com.vandorlabs.motion.PlatformRiders.CollisionScope(riders.riders)){for(int i=0;i<after.size();i++){
            AxisAlignedBB swept=swept(root,i,old.get(i),after.get(i),before,next);
            if(old.get(i).equals(after.get(i))||after.get(i).getAverageEdgeLength()==0)continue;
            if(!world.isAreaLoaded(new BlockPos(swept.minX,swept.minY,swept.minZ),new BlockPos(swept.maxX,swept.maxY,swept.maxZ),false))return false;
            List<AxisAlignedBB> obstacles=world.getCollisionBoxes(null,swept);
            if(!obstacles.isEmpty()){
                // Only payloads meeting an endpoint face may graze that face during
                // rotation. Linkage and unrelated obstacles keep the strict tolerance.
                if(!root.isSwingArmController()||root.isLandingAssembly())return false;
                List<AxisAlignedBB> endpoint=boxes(root,root.target()?1:0);
                for(AxisAlignedBB obstacle:obstacles){
                    int payloadStart=after.size()-(root.payload()==null?0:root.payload().collisionBoxCount(root));
                    AxisAlignedBB finalBox=endpoint.get(i),sourceBox=boxes(root,root.target()?0:1).get(i);
                    double tolerance=i>=payloadStart&&root.rotatePayload()&&(finalBox.grow(.0002).intersects(obstacle)||sourceBox.grow(.0002).intersects(obstacle))?1/4D:1/64D;
                    if(penetration(swept,obstacle)>tolerance)return false;
                    if(!shallowStroke(root,i,after.size(),obstacle,before,tolerance))return false;
                    for(AxisAlignedBB end:endpoint)if(end.shrink(.0001).intersects(obstacle))return false;
                }
            }
        }for(Entity rider:riders.riders){Vec3d move=riders.moves.get(rider);if(!com.vandorlabs.motion.PlatformRiders.clear(rider,move,world.getCollisionBoxes(rider,rider.getEntityBoundingBox().expand(move.x,move.y,move.z).grow(1e-7))))return false;}root.riderPlan=riders;return true;}finally{EXCLUDED.remove();}
    }
    private static double penetration(AxisAlignedBB a,AxisAlignedBB b){return Math.min(Math.min(a.maxX,b.maxX)-Math.max(a.minX,b.minX),Math.min(Math.min(a.maxY,b.maxY)-Math.max(a.minY,b.minY),Math.min(a.maxZ,b.maxZ)-Math.max(a.minZ,b.minZ)));}
    /** A harmless first sliver must not hide a deeper obstruction later in this same stroke. */
    private static boolean shallowStroke(GearTileEntity root,int index,int boxes,AxisAlignedBB obstacle,double start,double tolerance){
        double end=root.target()?1:0;int payloadStart=boxes-(root.payload()==null?0:root.payload().collisionBoxCount(root));
        for(int step=0;step<GearTileEntity.ANIMATION_TICKS;step++){
            double a=start+(end-start)*step/GearTileEntity.ANIMATION_TICKS,b=start+(end-start)*(step+1)/GearTileEntity.ANIMATION_TICKS;AxisAlignedBB sweep;
            if(index>=payloadStart)sweep=root.payload().sweptBox(root,index-payloadStart,a,b);
            else {AxisAlignedBB first=computeBoxes(root,a,false).get(index),last=computeBoxes(root,b,false).get(index);double angle=Math.abs(root.pose(b).angleDegrees()-root.pose(a).angleDegrees());sweep=rotationEnvelope(root,first.union(last),(root.assembly().columnHeight()+18)*(1-Math.cos(Math.toRadians(angle/2)))).shrink(.0001);}
            if(sweep.intersects(obstacle)&&penetration(sweep,obstacle)>tolerance)return false;
        }
        return true;
    }
    static final class MovePlan {
        final Map<Entity,Vec3d> moves=new IdentityHashMap<>();
        final Set<Entity> riders=Collections.newSetFromMap(new IdentityHashMap<>());
        void apply(){for(Map.Entry<Entity,Vec3d> entry:moves.entrySet()){
            Entity entity=entry.getKey();Vec3d move=entry.getValue();if(entity.isDead)continue;
            if(riders.contains(entity)){com.vandorlabs.motion.PlatformRiders.move(entity,move.x,move.y,move.z);continue;}
            if(entity instanceof net.minecraft.entity.player.EntityPlayerMP)((net.minecraft.entity.player.EntityPlayerMP)entity).connection.setPlayerLocation(entity.posX+move.x,entity.posY+move.y,entity.posZ+move.z,entity.rotationYaw,entity.rotationPitch);
            else entity.setPositionAndUpdate(entity.posX+move.x,entity.posY+move.y,entity.posZ+move.z);
            entity.fallDistance=0;entity.velocityChanged=true;
        }}
    }
    /** One entity query per stroke step, independent of platform block count. */
    private static MovePlan plan(World world,List<AxisAlignedBB> before,List<AxisAlignedBB> after,Entity vehicle){
        MovePlan result=new MovePlan();if(world.isRemote||after.isEmpty())return result;
        AxisAlignedBB area=before.get(0).union(after.get(0));for(int i=1;i<after.size();i++)area=area.union(before.get(i)).union(after.get(i));
        List<Entity> candidates=world.getEntitiesWithinAABBExcludingEntity(vehicle,area.grow(.001,.5,.001));
        Map<Entity,Double> highest=new IdentityHashMap<>();
        for(int i=0;i<after.size();i++){
            AxisAlignedBB old=before.get(i),next=after.get(i);if(old.equals(next)||next.getAverageEdgeLength()==0)continue;
            for(Entity entity:candidates){
                if(entity.isDead||entity.noClip||vehicle!=null&&entity.getLowestRidingEntity()==vehicle)continue;
                AxisAlignedBB body=entity.getEntityBoundingBox();boolean standing=Math.abs(body.minY-old.maxY)<(entity instanceof net.minecraft.entity.player.EntityPlayer?.5:.15)&&body.maxX>old.minX&&body.minX<old.maxX&&body.maxZ>old.minZ&&body.minZ<old.maxZ;
                double dx=(next.minX+next.maxX-old.minX-old.maxX)/2,dz=(next.minZ+next.maxZ-old.minZ-old.maxZ)/2;
                if(standing){if(!com.vandorlabs.motion.PlatformRiders.eligible(entity))continue;Double prior=highest.get(entity);if(prior!=null&&prior>=old.maxY)continue;highest.put(entity,old.maxY);result.riders.add(entity);result.moves.put(entity,new Vec3d(dx,next.maxY-body.minY,dz));continue;}
                if(result.riders.contains(entity)||!body.intersects(old.union(next)))continue;
                double dy=(next.minY+next.maxY-old.minY-old.maxY)/2;AxisAlignedBB shifted=body.offset(dx,dy,dz);
                if(shifted.intersects(next)){
                    if(Math.abs(dy)>=Math.abs(dx)&&Math.abs(dy)>=Math.abs(dz))dy+=dy>=0?next.maxY-shifted.minY+.001:next.minY-shifted.maxY-.001;
                    else if(Math.abs(dx)>=Math.abs(dz))dx+=dx>=0?next.maxX-shifted.minX+.001:next.minX-shifted.maxX-.001;
                    else dz+=dz>=0?next.maxZ-shifted.minZ+.001:next.minZ-shifted.maxZ-.001;
                }
                Vec3d move=new Vec3d(dx,dy,dz),previous=result.moves.get(entity);if(previous==null||move.lengthSquared()>previous.lengthSquared())result.moves.put(entity,move);
            }
        }
        return result;
    }
    public static void pushEntities(World world,List<AxisAlignedBB> before,List<AxisAlignedBB> after,Entity vehicle){plan(world,before,after,vehicle).apply();}
    static void pushEntities(GearTileEntity arm){MovePlan ready=arm.riderPlan;arm.riderPlan=null;if(ready!=null)ready.apply();}
    public static AxisAlignedBB swept(GearTileEntity root,int index,AxisAlignedBB old,AxisAlignedBB next,double before,double after){
        if(root.payload()!=null&&!root.payload().materialized()&&root.isSwingArmController()){
            int start=boxes(root,before).size()-root.payload().collisionBoxCount(root);
            if(index>=start)return root.payload().sweptBox(root,index-start,before,after);
        }
        if(!root.isLandingAssembly()){double angle=Math.abs(root.pose(after).angleDegrees()-root.pose(before).angleDegrees());return rotationEnvelope(root,old.union(next),(root.assembly().columnHeight()+18)*(1-Math.cos(Math.toRadians(angle/2)))).shrink(.0001);}
        int pieces=root.isSwingArmController()?armParts().size()+3:4,feetStart=root.columns().size()*pieces;
        boolean translated=root.mode()==GearMotion.Mode.EXTEND||root.mode()==GearMotion.Mode.ROTATE_LEVEL&&(index>=feetStart||index%pieces==pieces-1);
        double angle=Math.abs(root.pose(after).angleDegrees()-root.pose(before).angleDegrees());
        double padding=translated?0:(root.assembly().columnHeight()+2)*(1-Math.cos(Math.toRadians(angle/2)));
        AxisAlignedBB envelope=old.union(next);
        if(root.mode()==GearMotion.Mode.ROTATE_LEVEL&&root.assembly().kind()==GearTopology.FootKind.WHEEL&&index>=feetStart){
            GearTopology.Cell foot=root.assembly().feet().get(index-feetStart);BlockPos source=root.localCell(foot);
            net.minecraft.block.state.IBlockState state=root.getWorld().getBlockState(source);
            if(state.getBlock() instanceof GearBlock&&((GearBlock)state.getBlock()).componentRoot(source,state).equals(source)){
                GearBlock wheel=(GearBlock)state.getBlock();int turns=GearCollision.quarterTurns(root.footFacing());
                AxisAlignedBB connector=yaw(MODELS.computeIfAbsent(wheel.wheelModel()+"_connector",GearBlock::modelParts).stream().reduce(AxisAlignedBB::union).get(),turns).offset(foot.x,foot.y,foot.z);
                double cx=(connector.minX+connector.maxX)/2,cy=(connector.minY+connector.maxY)/2,cz=(connector.minZ+connector.maxZ)/2;
                AxisAlignedBB first=box(root.pose(before).transformConnectorBounds(bounds(connector),cx,cy,cz)),last=box(root.pose(after).transformConnectorBounds(bounds(connector),cx,cy,cz));
                double curve=(root.assembly().columnHeight()+2)*(1-Math.cos(Math.toRadians(angle/2)));
                envelope=envelope.union(first.union(last).grow(curve).offset(root.getPos()));
            }
        }
        // Level feet only translate monotonically along this quarter arc. Their
        // endpoint union is exact; rigid bars need curvature only in their plane.
        return rotationEnvelope(root,envelope,padding).shrink(.0001);
    }
    private static AxisAlignedBB rotationEnvelope(GearTileEntity root,AxisAlignedBB envelope,double padding){
        // Rotation never changes coordinates along the hinge axis. Inflating
        // that axis turns face contact with neighbouring blocks into a collision.
        Vec3d axis=ArmOrientation.vector(root.mountDirection(),root.direction().axisX(),0,root.direction().axisZ());
        return envelope.grow(axis.x==0?padding:0,axis.y==0?padding:0,axis.z==0?padding:0);
    }

}
