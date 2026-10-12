package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityCanopy;
import com.vandorlabs.vehicle.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Exercise captured canopy motion without running any local-world tile updates. */
final class VehicleCanopyMotionRuntimeChecks {
    private static void require(boolean value,String text){if(!value)throw new IllegalStateException("vehicle canopy: "+text);}
    static void run(World world,VehicleStructure snapshot,BlockPos hit){
        EntityGroundVehicle craft=new EntityGroundVehicle(world);
        craft.install(snapshot);craft.setPosition(480,150,480);
        world.getChunkFromChunkCoords(480 >> 4,480 >> 4);
        require(world.spawnEntity(craft),"fixture spawn in loaded chunk");
        try{
            TileEntityCanopy owner=((TileEntityCanopy)craft.view.getTileEntity(hit)).owner();
            VehicleWorld originalView=craft.view;
            VehicleCollision closed=craft.collision;
            boolean selectable=false;
            for(com.vandorlabs.canopy.CanopyMesh.Face face:owner.faces()){
                // Authored polygons may begin with collinear trim vertices. Try every
                // nondegenerate fan triangle and both sides; the seat can occlude
                // the first face without making the remaining shell unselectable.
                for(int triangle=1;triangle<face.vertices.length-1 && !selectable;triangle++){
                    net.minecraft.util.math.Vec3d a=com.vandorlabs.canopy.CanopyMesh.rotate(face.vertices[0],owner.facing()).add(new net.minecraft.util.math.Vec3d(owner.getPos()));
                    net.minecraft.util.math.Vec3d b=com.vandorlabs.canopy.CanopyMesh.rotate(face.vertices[triangle],owner.facing()).add(new net.minecraft.util.math.Vec3d(owner.getPos()));
                    net.minecraft.util.math.Vec3d c=com.vandorlabs.canopy.CanopyMesh.rotate(face.vertices[triangle+1],owner.facing()).add(new net.minecraft.util.math.Vec3d(owner.getPos()));
                    net.minecraft.util.math.Vec3d normal=b.subtract(a).crossProduct(c.subtract(a));
                    if(normal.lengthSquared()<1e-12)continue;
                    normal=normal.normalize();
                    net.minecraft.util.math.Vec3d center=a.add(b).add(c).scale(1D/3);
                    for(int side:new int[]{1,-1}){
                        VehicleSelection.Hit selected=VehicleSelection.local(craft.structure,craft.view,
                                center.add(normal.scale(2*side)),center.subtract(normal.scale(.05*side)),16);
                        if(selected!=null && selected.action==VehicleSelection.Action.CANOPY){selectable=true;break;}
                    }
                }
                if(selectable)break;
            }
            require(selectable,"actual nondegenerate shell surface has canopy interaction: "+owner.block().kind+" width="+owner.width());
            require(VehicleCanopies.toggle(craft,hit),"captured canopy toggles");
            for(int tick=0;tick<5;tick++)VehicleCanopies.tick(craft);
            require(owner.progress==5/16F && owner.targetOpen,"opening progresses");
            require(craft.view==originalView && craft.view.getTileEntity(owner.getPos())==owner,"live view and interpolation tile retained");
            require(craft.collision!=closed,"moving shell rebuilds collision");
            require(!craft.collision.boxes.equals(closed.boxes),"posed collision differs from closed shell");
            VehicleStructure mid=VehicleStructure.read(craft.structure.write());
            EntityGroundVehicle late=new EntityGroundVehicle(world);late.install(mid);
            TileEntityCanopy resumed=((TileEntityCanopy)late.view.getTileEntity(hit)).owner();
            require(resumed.progress==owner.progress && resumed.targetOpen,"save and late tracker retain current and target pose");
            require(VehicleCanopies.toggle(craft,hit),"mid-animation reversal");
            for(int tick=0;tick<5;tick++)VehicleCanopies.tick(craft);
            require(owner.progress==0 && !owner.targetOpen,"reversal closes in five ticks");
            require(VehicleCanopies.toggle(craft,hit),"reopen");
            for(int tick=0;tick<16;tick++)VehicleCanopies.tick(craft);
            require(owner.progress==1 && owner.previous==15/16F,"full animation preserves final interpolation");
            VehicleCollision settled=craft.collision;
            VehicleCanopies.tick(craft);
            require(craft.collision==settled,"settled canopy has no collision rebuild");
            require(owner.previous==1,"settled interpolation catches up");
            net.minecraft.nbt.NBTTagCompound parked=craft.structure.at(owner.getPos()).tileData();
            require(parked.getBoolean("Open") && parked.getFloat("Progress")==1,"parking snapshot retains open state");
            byte[] invalid=java.nio.ByteBuffer.allocate(13).putLong(owner.getPos().toLong()).put((byte)1).putFloat(Float.NaN).array();
            require(!VehicleCanopies.apply(craft,invalid) && !VehicleCanopies.apply(craft,new byte[0]),"malformed packets rejected");
            java.util.List<VehicleStructure.Cell> broken=new java.util.ArrayList<>(snapshot.cells);
            BlockPos missing=((TileEntityCanopy)late.view.getTileEntity(hit)).owner().cells().get(1);
            broken.removeIf(cell->cell.pos.equals(missing));
            late.install(new VehicleStructure(broken,snapshot.seat,snapshot.facing,snapshot.gearCount));
            require(!VehicleCanopies.toggle(late,hit),"incomplete captured canopy rejects toggle");
            late.setDead();
        }finally{craft.setDead();}
    }
}
