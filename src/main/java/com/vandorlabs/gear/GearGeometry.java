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
    @SubscribeEvent public static void collisions(GetCollisionBoxesEvent event){if(event.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld)return;Set<GearTileEntity> roots=ROOTS.get(event.getWorld());if(roots==null)return;
        for(GearTileEntity root:new ArrayList<>(roots))if(root!=EXCLUDED.get()&&!root.isInvalid()&&root.isOperational()&&root.getWorld().isBlockLoaded(root.getPos())&&root.getWorld().getTileEntity(root.getPos())==root&&root.getRenderBoundingBox().intersects(event.getAabb()))for(AxisAlignedBB box:root.collisionBoxes())if(box.intersects(event.getAabb()))event.getCollisionBoxesList().add(box);
    }
    private static GearMotion.Bounds bounds(AxisAlignedBB box){return new GearMotion.Bounds(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ);}
    private static AxisAlignedBB box(GearMotion.Bounds b){return new AxisAlignedBB(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);}
    private static void add(List<AxisAlignedBB> out,AxisAlignedBB local,GearMotion.Pose pose,boolean foot,BlockPos root){out.add(box(foot?pose.transformFootBounds(bounds(local)):pose.transformRigidBounds(bounds(local))).offset(root));}
    private static AxisAlignedBB yaw(AxisAlignedBB b,int turns){return com.vandorlabs.blocks.PanelPlacement.rotateFromNorth(b,new EnumFacing[]{EnumFacing.NORTH,EnumFacing.EAST,EnumFacing.SOUTH,EnumFacing.WEST}[turns&3]);}
    public static List<AxisAlignedBB> boxes(GearTileEntity tile,double progress){
        if(tile.assembly()==null)return Collections.emptyList();GearTopology.Assembly group=tile.assembly();GearMotion.Pose pose=GearMotion.pose(tile.mode(),tile.direction(),group.columnHeight(),progress);List<AxisAlignedBB> out=new ArrayList<>();BlockPos root=tile.getPos();
        for(BlockPos column:tile.columns()){
            BlockPos at=root.add(column);
            out.add(new AxisAlignedBB(0,14/16D,0,1,1,1).offset(at));out.add(new AxisAlignedBB(3/16D,6/16D,3/16D,13/16D,14/16D,13/16D).offset(at));
            if(tile.isSwingArmController())for(AxisAlignedBB arm:MODELS.computeIfAbsent("swing_linkage",GearBlock::modelParts))add(out,yaw(arm,tile.direction().ordinal()),pose,false,at);
            else add(out,new AxisAlignedBB(5/16D,pose.rodBottomY(),5/16D,11/16D,pose.rodTopY(),11/16D),pose,false,at);
            add(out,new AxisAlignedBB(.25,-group.columnHeight()+3/16D,.3125,.75,-group.columnHeight()+7/16D,.6875),pose,true,at);
        }
        int turns=tile.footFacing()==EnumFacing.EAST?1:tile.footFacing()==EnumFacing.SOUTH?2:tile.footFacing()==EnumFacing.WEST?3:0;
        for(GearTopology.Cell foot:group.feet()){
            if(group.kind()==GearTopology.FootKind.PLATE)add(out,new AxisAlignedBB(0,0,0,1,3/16D,1).offset(foot.x,foot.y,foot.z),pose,true,root);
            else{add(out,yaw(new AxisAlignedBB(.25,0,0,.75,5/16D,1),turns).offset(foot.x,foot.y,foot.z),pose,true,root);add(out,yaw(new AxisAlignedBB(0,3/16D,.375,1,5/16D,.625),turns).offset(foot.x,foot.y,foot.z),pose,true,root);}
        }
        return Collections.unmodifiableList(out);
    }
    public static boolean canMove(GearTileEntity root,double before,double next){
        World world=root.getWorld();List<AxisAlignedBB> old=boxes(root,before),after=boxes(root,next);if(old.size()!=after.size())return false;
        double degrees=Math.abs(GearMotion.pose(root.mode(),root.direction(),root.assembly().columnHeight(),next).angleDegrees()-GearMotion.pose(root.mode(),root.direction(),root.assembly().columnHeight(),before).angleDegrees());
        double padding=(root.assembly().columnHeight()+20)*(1-Math.cos(Math.toRadians(degrees/2)));
        EXCLUDED.set(root);
        try{for(int i=0;i<after.size();i++){
            int feetStart=after.size()-root.assembly().feet().size()*(root.assembly().kind()==GearTopology.FootKind.SKI?2:1);
            AxisAlignedBB swept=old.get(i).union(after.get(i)).grow(padding,i>=feetStart?0:padding,padding).shrink(.0001);
            if(old.get(i).equals(after.get(i)))continue;
            if(!world.isAreaLoaded(new BlockPos(swept.minX,swept.minY,swept.minZ),new BlockPos(swept.maxX,swept.maxY,swept.maxZ),false)||!world.getCollisionBoxes(null,swept).isEmpty())return false;
            for(Entity entity:world.getEntitiesWithinAABBExcludingEntity(null,swept))if(!entity.isDead&&entity.canBeCollidedWith()&&entity.getEntityBoundingBox().intersects(swept))return false;
        }return true;}finally{EXCLUDED.remove();}
    }
}
