package com.vandorlabs.gear;

import com.vandorlabs.tiles.TileEntityTurntableSurface;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/** Runtime appearance links only; never change payload ownership or snapshot NBT. */
public final class ArmSurfaceActivity {
    private static final Map<World,Map<BlockPos,GearTileEntity>> cells=new IdentityHashMap<>();
    private static final Map<GearTileEntity,Registration> registrations=new IdentityHashMap<>();
    private static final class Registration {
        final World world;final ArmPayload payload;final Object assembly;final BlockPos origin;final Set<BlockPos> positions;
        Registration(GearTileEntity arm,boolean discover){world=arm.getWorld();payload=arm.payload();assembly=arm.assembly();origin=payload==null?arm.getPos():payload.materializedOrigin(arm);positions=discover?ArmPayload.attachedSurfaceCells(arm):payload==null?Collections.emptySet():payload.placedSurfaceCells(arm);}
    }
    public static synchronized void refresh(GearTileEntity arm){refresh(arm,false);}
    public static synchronized void discover(GearTileEntity arm){if(arm.getWorld()!=null&&arm.getWorld().isBlockLoaded(arm.getPos())&&arm.getWorld().getTileEntity(arm.getPos())==arm)refresh(arm,true);}
    private static void refresh(GearTileEntity arm,boolean discover){
        ArmPayload payload=arm.payload();Registration old=registrations.get(arm);
        boolean nativeCells=arm.getWorld()!=null&&!(arm.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld)&&!arm.isInvalid()&&!arm.isFixedMount()&&arm.isController()&&(payload==null?arm.progress()==1:payload.materialized());
        if(!discover&&old!=null&&nativeCells&&old.assembly==arm.assembly()&&old.world==arm.getWorld()&&old.payload==payload&&old.origin.equals(payload==null?arm.getPos():payload.materializedOrigin(arm)))return;
        remove(arm);
        if(nativeCells){Registration next=new Registration(arm,discover);registrations.put(arm,next);Map<BlockPos,GearTileEntity> map=cells.computeIfAbsent(next.world,w->new HashMap<>());for(BlockPos p:next.positions)map.put(p,arm);}
    }
    public static synchronized void surfaceChanged(World world,Collection<BlockPos> positions){
        if(world==null||world instanceof com.vandorlabs.vehicle.VehicleWorld)return;
        Set<GearTileEntity> affected=Collections.newSetFromMap(new IdentityHashMap<>());Map<BlockPos,GearTileEntity> map=cells.get(world);Set<BlockPos> touching=new HashSet<>();
        for(BlockPos pos:positions){touching.add(pos);for(net.minecraft.util.EnumFacing face:net.minecraft.util.EnumFacing.values())touching.add(pos.offset(face));}
        if(map!=null)for(BlockPos pos:touching){GearTileEntity arm=map.get(pos);if(arm!=null)affected.add(arm);}
        for(GearTileEntity arm:GearGeometry.roots(world))if(arm.isController()){
            ArmPayload payload=arm.payload();
            if(payload!=null){if(payload.materialized())for(BlockPos at:payload.placedCells(arm))if(touching.contains(at)){affected.add(arm);break;}continue;}
            if(arm.progress()!=1)continue;
            int minX=Integer.MAX_VALUE,minZ=minX,maxX=Integer.MIN_VALUE,maxZ=maxX;
            for(BlockPos column:arm.columns()){BlockPos local=ArmOrientation.inverse(arm.mountDirection(),column);minX=Math.min(minX,local.getX());maxX=Math.max(maxX,local.getX());minZ=Math.min(minZ,local.getZ());maxZ=Math.max(maxZ,local.getZ());}
            for(BlockPos at:touching){BlockPos local=ArmOrientation.inverse(arm.mountDirection(),at.subtract(arm.getPos()));if(local.getY()==-arm.assembly().columnHeight()&&local.getX()>=minX&&local.getX()<=maxX&&local.getZ()>=minZ&&local.getZ()<=maxZ){affected.add(arm);break;}}
        }
        for(GearTileEntity arm:affected)discover(arm);
    }
    public static synchronized void remove(GearTileEntity arm){Registration old=registrations.remove(arm);if(old==null)return;Map<BlockPos,GearTileEntity> map=cells.get(old.world);if(map!=null){for(BlockPos p:old.positions)map.remove(p,arm);if(map.isEmpty())cells.remove(old.world);}}
    public static synchronized void unload(World world){cells.remove(world);registrations.entrySet().removeIf(e->e.getValue().world==world);}
    public static synchronized Boolean activity(TileEntityTurntableSurface surface){
        GearTileEntity arm=surface.carriedArm;
        if(arm==null){Map<BlockPos,GearTileEntity> map=cells.get(surface.getWorld());arm=map==null?null:map.get(surface.getPos());}
        if(arm==null||arm.isInvalid())return null;
        return arm.surfaceActive();
    }
    private ArmSurfaceActivity(){}
}
