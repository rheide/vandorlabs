package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.BlockLandingGear;
import com.vandorlabs.gear.GearBlock;
import com.vandorlabs.gear.GearTileEntity;
import com.vandorlabs.physics.OrientedBounds;
import com.vandorlabs.physics.RigidTransform;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.Map;

/** Pitched hull collision. The normal Taxi geometry still handles contact and ground roll. */
final class VehicleFlightMotion {
    enum Result { MOVED, OBSTRUCTED, UNLOADED }

    private final EntityGroundVehicle craft;
    private VehicleCollision previousCollision;
    private VehicleWorld previousView;
    private final java.util.List<Map.Entry<Integer,VehicleCollision>> hull=new java.util.ArrayList<>();
    private Map<Integer,VehicleCollision> wheels;
    private double radius;

    VehicleFlightMotion(EntityGroundVehicle craft) { this.craft = craft; }

    static boolean landingGear(VehicleWorld view, VehicleStructure.Cell cell) {
        Block block = cell.state.getBlock();
        if (!(block instanceof BlockLandingGear)) return false;
        if (!(block instanceof GearBlock)) return true;
        GearBlock.Kind kind = ((GearBlock) block).kind();
        if (kind == GearBlock.Kind.WHEEL || kind == GearBlock.Kind.TRACK || kind == GearBlock.Kind.SKI || kind == GearBlock.Kind.PLATE) return true;
        TileEntity tile = view.getTileEntity(cell.pos);
        GearTileEntity controller = tile instanceof GearTileEntity ? ((GearTileEntity) tile).loadedController() : null;
        return controller != null && controller.isLandingAssembly() && controller.payload() == null;
    }

    private void prepare() {
        if (previousCollision == craft.collision && previousView == craft.view) return;
        wheels=VehicleCollision.flightHull(craft,true);
        hull.clear();
        hull.addAll(VehicleCollision.flightHull(craft,false).entrySet());
        hull.addAll(wheels.entrySet());
        previousCollision = craft.collision;
        previousView = craft.view;
        AxisAlignedBB box = craft.collision.bounds;
        radius = Math.sqrt(craft.structure.bounds.maxX * craft.structure.bounds.maxX / 4 +
                box.maxY * box.maxY + craft.structure.bounds.maxZ * craft.structure.bounds.maxZ / 4);
        if (craft.articulation.active()) radius = Math.max(radius, craft.articulation.renderRadius());
    }

    AxisAlignedBB bounds(VehiclePose pose) {
        prepare();
        double px = craft.structure.bounds.maxX / 2, pz = craft.structure.bounds.maxZ / 2;
        if (!craft.articulation.active()) return pose.world(craft.collision.bounds,px,pz);
        AxisAlignedBB combined = null;
        RigidTransform root = pose.transform(px,pz);
        for (Map.Entry<Integer,VehicleCollision> body : hull) {
            AxisAlignedBB box = bounds(body.getValue().bounds,root.then(craft.articulation.physicalBody(body.getKey())));
            combined = combined == null ? box : combined.union(box);
        }
        return combined == null ? pose.world(craft.collision.bounds,px,pz) : combined;
    }

    Result move(double dx, double dy, double dz, float yaw, float pitch, float bank) {
        prepare();
        VehiclePose from = craft.flightPose();
        VehiclePose to = new VehiclePose(from.x + dx, from.y + dy, from.z + dz, yaw, pitch, bank, from.facing);
        AxisAlignedBB envelope = bounds(from).union(bounds(to)).grow(.2);
        if (!loaded(envelope)) return Result.UNLOADED;
        double angular = Math.abs(MathHelper.wrapDegrees(yaw - from.yaw)) + Math.abs(pitch - from.pitch) + Math.abs(MathHelper.wrapDegrees(bank - from.bank));
        int steps = Math.max(1, (int) Math.ceil(Math.toRadians(angular) * radius / .12));
        VehiclePose accepted = from;
        double ceiling = craft.world.provider.getDimension()==0?Double.POSITIVE_INFINITY:craft.world.getActualHeight();
        try {
            for (Map.Entry<Integer,VehicleCollision> body : hull) body.getValue().begin(craft.world,craft);
            for (int step = 1; step <= steps; step++) {
                VehiclePose next = from.interpolate(to,(float) step / steps);
                AxisAlignedBB occupied = bounds(next);
                if (occupied.maxY >= ceiling) return Result.UNLOADED;
                if (occupied.minY < 0 || blocked(accepted,next)) {
                    craft.applyFlightPose(accepted);
                    return Result.OBSTRUCTED;
                }
                accepted = next;
            }
        } finally {
            for (Map.Entry<Integer,VehicleCollision> body : hull) body.getValue().end();
        }
        craft.applyFlightPose(to);
        return Result.MOVED;
    }

    /** Separate a tilted aircraft from its contact without rotating it through the terrain. */
    boolean retreat() {
        VehiclePose pose = craft.flightPose();
        double angle = Math.toRadians(pose.yaw), cos = Math.cos(angle), sin = Math.sin(angle);
        double dx = pose.facing.getFrontOffsetX() * cos - pose.facing.getFrontOffsetZ() * sin;
        double dz = pose.facing.getFrontOffsetX() * sin + pose.facing.getFrontOffsetZ() * cos;
        return move(-dx*.30,0,-dz*.30,pose.yaw,pose.pitch,pose.bank)==Result.MOVED
                || move(-dx*.24,.12,-dz*.24,pose.yaw,pose.pitch,pose.bank)==Result.MOVED
                || move(0,.16,0,pose.yaw,pose.pitch,pose.bank)==Result.MOVED;
    }

    private boolean blocked(VehiclePose from, VehiclePose to) {
        double dx=to.x-from.x,dy=to.y-from.y,dz=to.z-from.z;
        boolean turned=from.yaw!=to.yaw||from.pitch!=to.pitch||from.bank!=to.bank;
        if(!turned)return sweeps(from,dx,dy,dz);
        if((dx!=0||dy!=0||dz!=0)&&sweeps(from,dx,dy,dz))return true;
        return sweeps(new VehiclePose(from.x,from.y,from.z,to.yaw,to.pitch,to.bank,to.facing),dx,dy,dz);
    }

    private boolean sweeps(VehiclePose pose,double dx,double dy,double dz){
        return sweeps(hull,pose,dx,dy,dz);
    }

    private boolean sweeps(Iterable<Map.Entry<Integer,VehicleCollision>> bodies,VehiclePose pose,double dx,double dy,double dz){
        RigidTransform root = pose.transform(craft.structure.bounds.maxX/2,craft.structure.bounds.maxZ/2);
        for (Map.Entry<Integer,VehicleCollision> body : bodies) {
            RigidTransform transform = root.then(craft.articulation.physicalBody(body.getKey()));
            if (body.getValue().overlapsFlight(craft.world,craft,transform,dx,dy,dz)) return true;
        }
        return false;
    }

    /** Settle a shallow wheels-first approach, while requiring clearance for the whole leveled hull. */
    boolean touchdown(double dx,double dy,double dz) {
        VehiclePose from=craft.flightPose();
        if(dy>=0||Math.abs(from.pitch)>18||Math.abs(from.bank)>12||wheels.isEmpty()||
                !VehicleLandingGear.extended(craft)||!VehicleSkiTraction.allowed(craft))return false;
        VehiclePose level=new VehiclePose(from.x,from.y,from.z,from.yaw,0,0,from.facing);
        double reach=Math.min(3,.12-dy+Math.toRadians(Math.abs(from.pitch)+Math.abs(from.bank))*radius);
        if(!loaded(bounds(from).union(bounds(level).expand(dx,-reach,dz)).grow(.2)))return false;
        if(sweeps(hull,level,dx,0,dz))return false; // A wall ahead cannot become runway contact.
        double drop=craft.collision.clip(craft.world,craft,level.x,level.y,level.z,-reach,1,level.yaw);
        if(drop<=-reach+1e-6||!sweeps(wheels.entrySet(),level,0,drop-.002,0))return false;
        int steps=Math.max(1,(int)Math.ceil(Math.toRadians(Math.abs(from.pitch)+Math.abs(from.bank))*radius/.12));
        for(int i=1;i<=steps;i++)if(sweeps(from.interpolate(level,(float)i/steps),0,0,0))return false;
        craft.applyFlightPose(new VehiclePose(level.x,level.y+drop,level.z,level.yaw,0,0,level.facing));
        return true;
    }

    private boolean loaded(AxisAlignedBB area) {
        if (!craft.world.getWorldBorder().contains(new BlockPos(area.minX,area.minY,area.minZ)) ||
                !craft.world.getWorldBorder().contains(new BlockPos(area.maxX,area.maxY,area.maxZ))) return false;
        for (int x = MathHelper.floor(area.minX) >> 4; x <= MathHelper.floor(area.maxX) >> 4; x++)
            for (int z = MathHelper.floor(area.minZ) >> 4; z <= MathHelper.floor(area.maxZ) >> 4; z++)
                if (!craft.world.isBlockLoaded(new BlockPos(x * 16,64,z * 16),false)) return false;
        return true;
    }

    double ceiling(float yaw,float pitch,float bank) {
        prepare();
        if(craft.world.provider.getDimension()==0)return com.vandorlabs.physics.FlightEnvelope.MAX_ALTITUDE;
        return craft.world.getActualHeight() - (bounds(new VehiclePose(craft.posX,craft.posY,craft.posZ,yaw,pitch,bank,craft.structure.facing)).maxY - craft.posY) - 1;
    }

    static OrientedBounds oriented(AxisAlignedBB box, RigidTransform transform) {
        return new OrientedBounds(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ,transform);
    }

    static AxisAlignedBB bounds(AxisAlignedBB box, RigidTransform transform) {
        OrientedBounds oriented = oriented(box,transform);
        return new AxisAlignedBB(oriented.minX,oriented.minY,oriented.minZ,oriented.maxX,oriented.maxY,oriented.maxZ);
    }
}
