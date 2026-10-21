package com.vandorlabs.cockpit;

import com.vandorlabs.tiles.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.util.*;

/** Shared placement and per-cell collision for seated cockpits and the yoke peripheral. */
public final class CockpitAssembly {
    public static TileEntityCockpit root(IBlockAccess world,BlockPos pos){
        TileEntity t=world.getTileEntity(pos);
        if(t instanceof TileEntityCockpit)return (TileEntityCockpit)t;
        if(t instanceof TileEntityCockpitPart){BlockPos owner=((TileEntityCockpitPart)t).anchor;if(world instanceof World && !((World)world).isBlockLoaded(owner))return null;TileEntity r=world.getTileEntity(owner);return r instanceof TileEntityCockpit?(TileEntityCockpit)r:null;}
        return null;
    }
    public static List<AxisAlignedBB> boxes(IBlockAccess world,BlockPos pos){return boxes(world,pos,false);}
    private static List<AxisAlignedBB> boxes(IBlockAccess world,BlockPos pos,boolean collision){
        TileEntityCockpit root=root(world,pos);if(root==null)return Collections.emptyList();List<AxisAlignedBB> result=new ArrayList<>();AxisAlignedBB cell=new AxisAlignedBB(pos);
        for(AxisAlignedBB authored:root.data().boxes){AxisAlignedBB shape=root.posedBox(authored);if(collision&&world instanceof com.vandorlabs.vehicle.VehicleWorld&&root.data().moving.getOrDefault(authored,0)==1&&(root.seatSlide.target()||root.seatSlide.moving(((World)world).getTotalWorldTime())))shape=authored.union(authored.offset(0,0,1));if(collision&&world instanceof com.vandorlabs.vehicle.VehicleWorld&&root.data().moving.getOrDefault(authored,0)==2&&root.screenLift.moving(((World)world).getTotalWorldTime()))shape=authored.union(authored.offset(0,-.75,0));AxisAlignedBB b=CockpitData.rotate(shape,root.facing()).offset(root.getPos());if(!b.intersects(cell))continue;result.add(b.intersect(cell).offset(-pos.getX(),-pos.getY(),-pos.getZ()));}return result;
    }
    public static AxisAlignedBB bounds(IBlockAccess world,BlockPos pos){AxisAlignedBB found=null;for(AxisAlignedBB b:boxes(world,pos))found=found==null?b:found.union(b);return found==null?new AxisAlignedBB(0,0,0,0,0,0):found;}
    public static void collision(IBlockAccess world,BlockPos pos,AxisAlignedBB query,List<AxisAlignedBB> out){for(AxisAlignedBB b:boxes(world,pos,true)){b=b.offset(pos);if(b.intersects(query))out.add(b);}}
    public static RayTraceResult ray(World world,BlockPos pos,Vec3d start,Vec3d end){RayTraceResult nearest=null;TileEntityCockpit owner=root(world,pos);if(owner!=null){RayTraceResult control=owner.controlRay(start.subtract(new Vec3d(owner.getPos())),end.subtract(new Vec3d(owner.getPos())));if(control!=null){Vec3d point=control.hitVec.add(new Vec3d(owner.getPos()));if(new AxisAlignedBB(pos).grow(1e-7).contains(point))nearest=new RayTraceResult(point,control.sideHit,pos);}}for(AxisAlignedBB b:boxes(world,pos)){RayTraceResult h=b.offset(pos).calculateIntercept(start,end);if(h!=null && (nearest==null || start.squareDistanceTo(h.hitVec)<start.squareDistanceTo(nearest.hitVec)))nearest=new RayTraceResult(h.hitVec,h.sideHit,pos);}return nearest;}
    public static boolean available(World world,BlockPos root,EnumFacing facing,String id,EntityPlayer player,net.minecraft.item.ItemStack held){
        for(BlockPos p:CockpitData.get(id).cells(root,facing))if(p.getY()<0 || p.getY()>=world.getHeight() || !world.isBlockLoaded(p)
                || !world.getBlockState(p).getBlock().isReplaceable(world,p)
                || player!=null && (!world.isBlockModifiable(player,p) || !player.canPlayerEdit(p,EnumFacing.UP,held)))return false;
        return true;
    }
    public static void install(World world,BlockPos pos,EntityPlayer owner){
        TileEntityCockpit tile=(TileEntityCockpit)world.getTileEntity(pos);if(tile==null)return;
        tile.owner=owner==null?null:owner.getUniqueID();
        for(BlockPos cell:tile.data().cells(pos,tile.facing()))if(!cell.equals(pos)){
            world.setBlockState(cell,net.minecraft.block.Block.getBlockFromName("vandorlabs:cockpit_part").getDefaultState(),2);
            ((TileEntityCockpitPart)world.getTileEntity(cell)).anchor=pos.toImmutable();world.getTileEntity(cell).markDirty();
            world.notifyBlockUpdate(cell,world.getBlockState(cell),world.getBlockState(cell),3);
        }
        tile.changed();
    }
    private static final Map<World,Set<BlockPos>> REMOVING=new WeakHashMap<>();
    public static void remove(World world,BlockPos pos){
        remove(world,pos,null);
    }
    public static void remove(World world,BlockPos pos,EnumFacing removedFacing){
        if(world.isRemote || com.vandorlabs.vehicle.VehicleTransfer.isMutating(world))return;TileEntityCockpit tile=root(world,pos);if(tile==null)return;
        BlockPos root=tile.getPos();Set<BlockPos> active=REMOVING.computeIfAbsent(world,w->new HashSet<>());if(active.contains(root))return;
        if(!root.equals(pos)){world.destroyBlock(root,true);return;}
        active.add(root);
        try{
            for(BlockPos cell:com.vandorlabs.cockpit.CockpitReservations.allCells(tile,removedFacing==null?tile.facing():removedFacing))if(!cell.equals(root) && world.isBlockLoaded(cell)){
                TileEntity t=world.getTileEntity(cell);if(t instanceof TileEntityCockpitPart && ((TileEntityCockpitPart)t).anchor.equals(root))world.setBlockToAir(cell);
            }
        }finally{active.remove(root);if(active.isEmpty())REMOVING.remove(world);}
    }
    public static boolean activate(World world,BlockPos pos,EntityPlayer player,EnumHand hand){
        if(hand!=EnumHand.MAIN_HAND)return false;TileEntityCockpit root=root(world,pos);if(root==null)return false;
        if(world.isRemote)return true;
        if(player.isSneaking() || player.getHeldItemMainhand().getItem()==com.vandorlabs.items.ModItems.CONFIGURIZER){com.vandorlabs.container.ContainerCockpit.open(player,root);return true;}
        Vec3d eye=player.getPositionEyes(1).subtract(new Vec3d(root.getPos())),end=eye.add(Vec3d.fromPitchYaw(player.rotationPitch,player.rotationYaw).scale(8));
        if(root.use(eye,end,player))return true;
        if(root.data().seat==null){com.vandorlabs.container.ContainerCockpit.open(player,root);return true;}
        for(com.vandorlabs.entity.EntityChairSeat mount:world.getEntitiesWithinAABB(com.vandorlabs.entity.EntityChairSeat.class,new AxisAlignedBB(root.getPos()).grow(2)))if(root.getPos().equals(mount.getChairPos()) && !mount.isDead){if(mount.getPassengers().isEmpty() && player.startRiding(mount,true))com.vandorlabs.vehicle.VehicleService.taxiAssembly((net.minecraft.entity.player.EntityPlayerMP)player,root.getPos());return true;}
        com.vandorlabs.entity.EntityChairSeat mount=new com.vandorlabs.entity.EntityChairSeat(world,root.getPos(),root.seatHeight());Vec3d seat=CockpitData.rotate(root.seatLocation(),root.facing());mount.setPosition(root.getPos().getX()+seat.x,mount.posY,root.getPos().getZ()+seat.z);mount.rotationYaw=root.facing().getHorizontalAngle();
        if(world.spawnEntity(mount)){if(!player.startRiding(mount,true))mount.setDead();else com.vandorlabs.vehicle.VehicleService.taxiAssembly((net.minecraft.entity.player.EntityPlayerMP)player,root.getPos());}return true;
    }
    private CockpitAssembly(){}
}
