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
    private static final Map<String,List<AxisAlignedBB>> MODELS=new java.util.concurrent.ConcurrentHashMap<>();
    private static final ThreadLocal<GearTileEntity> EXCLUDED=new ThreadLocal<>();
    public static boolean excluded(GearTileEntity root){return EXCLUDED.get()==root;}
    public static List<AxisAlignedBB> foreign(World world,GearTileEntity root,AxisAlignedBB box){EXCLUDED.set(root);try{return world.getCollisionBoxes(null,box);}finally{EXCLUDED.remove();}}
    public static void register(GearTileEntity tile){World world=tile.getWorld();if(world==null||world instanceof com.vandorlabs.vehicle.VehicleWorld)return;Set<GearTileEntity> roots=ROOTS.computeIfAbsent(world,k->Collections.newSetFromMap(new IdentityHashMap<>()));if(tile.isController())roots.add(tile);else roots.remove(tile);}
    public static void remove(GearTileEntity tile){Set<GearTileEntity> roots=ROOTS.get(tile.getWorld());if(roots!=null)roots.remove(tile);}
    @SubscribeEvent public static void unload(WorldEvent.Unload event){ROOTS.remove(event.getWorld());}
    public static java.util.List<GearTileEntity> roots(World world){Set<GearTileEntity> found=ROOTS.get(world);return found==null?Collections.emptyList():new ArrayList<>(found);}
    @SubscribeEvent public static void collisions(GetCollisionBoxesEvent event){if(event.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld)return;Set<GearTileEntity> roots=ROOTS.get(event.getWorld());if(roots==null)return;
        for(GearTileEntity root:new ArrayList<>(roots))if(root!=EXCLUDED.get()&&!root.isInvalid()&&root.isOperational()&&root.getWorld().isBlockLoaded(root.getPos())&&root.getWorld().getTileEntity(root.getPos())==root&&root.getRenderBoundingBox().intersects(event.getAabb()))for(AxisAlignedBB box:root.collisionBoxes())if(box.getAverageEdgeLength()>0&&box.intersects(event.getAabb()))event.getCollisionBoxesList().add(box);
    }
    private static GearMotion.Bounds bounds(AxisAlignedBB box){return new GearMotion.Bounds(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ);}
    private static AxisAlignedBB box(GearMotion.Bounds b){return new AxisAlignedBB(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);}
    private static void add(List<AxisAlignedBB> out,AxisAlignedBB local,GearMotion.Pose pose,boolean foot,BlockPos root){out.add(box(foot?pose.transformFootBounds(bounds(local)):pose.transformRigidBounds(bounds(local))).offset(root));}
    private static AxisAlignedBB yaw(AxisAlignedBB b,int turns){return com.vandorlabs.blocks.PanelPlacement.rotateFromNorth(b,new EnumFacing[]{EnumFacing.NORTH,EnumFacing.EAST,EnumFacing.SOUTH,EnumFacing.WEST}[turns&3]);}
    public static List<AxisAlignedBB> boxes(GearTileEntity tile,double progress){
        if(tile.assembly()==null)return Collections.emptyList();GearTopology.Assembly group=tile.assembly();GearMotion.Pose pose=GearMotion.pose(tile.mode(),tile.direction(),group.columnHeight(),progress);List<AxisAlignedBB> out=new ArrayList<>();BlockPos root=tile.getPos();
        for(BlockPos column:tile.columns()){
            BlockPos at=root.add(column);int width=tile.armWidth();double shift=(width-1)*.5;
            out.add(new AxisAlignedBB(.5-width*.5,14/16D,.5-width*.5,.5+width*.5,1,.5+width*.5).offset(tile.armShiftX(),0,tile.armShiftZ()).offset(at));out.add(new AxisAlignedBB(.5-5/16D*width,6/16D,.5-5/16D*width,.5+5/16D*width,14/16D,.5+5/16D*width).offset(tile.armShiftX(),0,tile.armShiftZ()).offset(at));
            if(tile.isSwingArmController())for(AxisAlignedBB arm:armParts())addArm(out,stretch(yaw(arm,tile.direction().ordinal()),group.columnHeight()),pose,false,at,tile.direction().axisX()!=0?width:1,tile.direction().axisZ()!=0?width:1,tile.armShiftX(),tile.armShiftZ());
            else addArm(out,new AxisAlignedBB(5/16D,pose.rodBottomY(),5/16D,11/16D,pose.rodTopY(),11/16D),pose,false,at,width,width,tile.armShiftX(),tile.armShiftZ());
            addArm(out,new AxisAlignedBB(.25,-group.columnHeight()+3/16D,.3125,.75,-group.columnHeight()+7/16D,.6875),pose,true,at,width,width,tile.armShiftX(),tile.armShiftZ());
        }
        int turns=tile.footFacing()==EnumFacing.EAST?1:tile.footFacing()==EnumFacing.SOUTH?2:tile.footFacing()==EnumFacing.WEST?3:0;
        for(GearTopology.Cell foot:group.feet()){
            if(group.kind()==GearTopology.FootKind.WHEEL){BlockPos source=root.add(foot.x,foot.y,foot.z);net.minecraft.block.state.IBlockState state=tile.getWorld().getBlockState(source);GearBlock block=(GearBlock)state.getBlock();AxisAlignedBB wheel=block.componentRoot(source,state).equals(source)?yaw(MODELS.computeIfAbsent(block.wheelModel(),GearBlock::modelParts).stream().reduce(AxisAlignedBB::union).get(),turns):new AxisAlignedBB(0,0,0,0,0,0);add(out,wheel.offset(foot.x,foot.y,foot.z),pose,true,root);}
            else if(group.kind()==GearTopology.FootKind.PLATE)add(out,new AxisAlignedBB(0,0,0,1,3/16D,1).offset(foot.x,foot.y,foot.z),pose,true,root);
            else{add(out,yaw(new AxisAlignedBB(.25,0,0,.75,5/16D,1),turns).offset(foot.x,foot.y,foot.z),pose,true,root);add(out,yaw(new AxisAlignedBB(0,3/16D,.375,1,5/16D,.625),turns).offset(foot.x,foot.y,foot.z),pose,true,root);}
        }
        return Collections.unmodifiableList(out);
    }
    private static List<AxisAlignedBB> armParts(){return MODELS.computeIfAbsent("swing_linkage_sliced",name->GearBlock.movingArmParts());}
    private static AxisAlignedBB stretch(AxisAlignedBB b,int height){double factor=(height+.25)/1.25;return new AxisAlignedBB(b.minX,GearMotion.PIVOT_Y+(b.minY-GearMotion.PIVOT_Y)*factor,b.minZ,b.maxX,GearMotion.PIVOT_Y+(b.maxY-GearMotion.PIVOT_Y)*factor,b.maxZ);}
    private static void addArm(List<AxisAlignedBB> out,AxisAlignedBB local,GearMotion.Pose pose,boolean foot,BlockPos at,int scaleX,int scaleZ,double shiftX,double shiftZ){AxisAlignedBB scaled=new AxisAlignedBB(.5+(local.minX-.5)*scaleX,local.minY,.5+(local.minZ-.5)*scaleZ,.5+(local.maxX-.5)*scaleX,local.maxY,.5+(local.maxZ-.5)*scaleZ);List<AxisAlignedBB> one=new ArrayList<>();add(one,scaled,pose,foot,at);out.add(one.get(0).offset(shiftX,0,shiftZ));}
    public static boolean canMove(GearTileEntity root,double before,double next){
        World world=root.getWorld();List<AxisAlignedBB> old=boxes(root,before),after=boxes(root,next);if(old.size()!=after.size())return false;
        EXCLUDED.set(root);
        try{for(int i=0;i<after.size();i++){
            AxisAlignedBB swept=swept(root,i,old.get(i),after.get(i),before,next);
            if(old.get(i).equals(after.get(i))||after.get(i).getAverageEdgeLength()==0)continue;
            if(!world.isAreaLoaded(new BlockPos(swept.minX,swept.minY,swept.minZ),new BlockPos(swept.maxX,swept.maxY,swept.maxZ),false)||!world.getCollisionBoxes(null,swept).isEmpty())return false;
            for(Entity entity:world.getEntitiesWithinAABBExcludingEntity(null,swept))if(!entity.isDead&&entity.canBeCollidedWith()&&entity.getEntityBoundingBox().intersects(swept))return false;
        }return true;}finally{EXCLUDED.remove();}
    }
    public static AxisAlignedBB swept(GearTileEntity root,int index,AxisAlignedBB old,AxisAlignedBB next,double before,double after){
        int pieces=root.isSwingArmController()?armParts().size()+3:4,feetStart=root.columns().size()*pieces;
        boolean translated=root.mode()==GearMotion.Mode.EXTEND||root.mode()==GearMotion.Mode.ROTATE_LEVEL&&(index>=feetStart||index%pieces==pieces-1);
        double angle=Math.abs(GearMotion.pose(root.mode(),root.direction(),root.assembly().columnHeight(),after).angleDegrees()-GearMotion.pose(root.mode(),root.direction(),root.assembly().columnHeight(),before).angleDegrees());
        double padding=translated?0:(root.assembly().columnHeight()+2)*(1-Math.cos(Math.toRadians(angle/2)));
        // Level feet only translate monotonically along this quarter arc. Their
        // endpoint union is exact; rigid bars need curvature only in their plane.
        return old.union(next).grow(root.direction().axisX()==0?padding:0,padding,root.direction().axisZ()==0?padding:0).shrink(.0001);
    }
}
