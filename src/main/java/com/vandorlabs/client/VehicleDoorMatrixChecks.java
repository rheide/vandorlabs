package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.BlockDoor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Production door grouping/collision/NBT across every motion, frame, depth and heading. */
final class VehicleDoorMatrixChecks {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("vehicle door matrix: "+message);}
    static void run(WorldServer world) {
        EntityGroundVehicle craft=new EntityGroundVehicle(world);int cases=0;
        BlockPos root=new BlockPos(4,1,4);world.getChunkFromChunkCoords(10,10);
        try {
            for(int kind=0;kind<3;kind++)for(EnumFacing face:EnumFacing.HORIZONTALS)for(int framed=0;framed<2;framed++)for(int depth=0;depth<3;depth++)for(int motion=0;motion<(kind==2?5:4);motion++) {
                List<VehicleStructure.Cell> cells=new ArrayList<>();cells.add(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PILOT_SEAT.getDefaultState(),null));
                net.minecraft.block.Block block=net.minecraft.block.Block.getBlockFromName("vandorlabs:"+(kind==2?"large_programmable_door":"programmable_door"));
                List<BlockPos> group=new ArrayList<>();
                int width=kind==2?3:kind==1?2:1,height=kind==2?3:2;
                for(int x=0;x<width;x++)for(int y=0;y<height;y++) {
                    BlockPos at=root.offset(face.rotateYCCW(),x).up(y);group.add(at);
                    TileEntitySpaceDoor tile=kind==2?new TileEntityLargeProgrammableDoor():new TileEntitySpaceDoor();tile.setPos(at);
                    tile.configure(kind==2?23:2,1,framed==1,motion==0?0:motion-1,false,motion!=0,depth%2==0,com.vandorlabs.persistence.SpaceDoorData.TRIGGER_DISABLED,depth!=2);
                    tile.setPlacementDepth(depth);tile.setFaceTexture(depth==2?CustomBlockMaterials.choice(new net.minecraft.item.ItemStack(net.minecraft.init.Blocks.GOLD_BLOCK)):14);tile.setTileTexture(depth==1);
                    if(tile instanceof TileEntityLargeProgrammableDoor)((TileEntityLargeProgrammableDoor)tile).assign(root);
                    net.minecraft.block.state.IBlockState state=block.getDefaultState().withProperty(BlockDoor.FACING,face);
                    if(kind!=2)state=state.withProperty(BlockDoor.HALF,y==0?BlockDoor.EnumDoorHalf.LOWER:BlockDoor.EnumDoorHalf.UPPER).withProperty(BlockDoor.HINGE,x==0?BlockDoor.EnumHingePosition.LEFT:BlockDoor.EnumHingePosition.RIGHT);
                    cells.add(new VehicleStructure.Cell(at,state,tile.writeToNBT(new NBTTagCompound())));
                }
                VehicleStructure snapshot=new VehicleStructure(cells,BlockPos.ORIGIN,EnumFacing.NORTH,1);craft.install(snapshot);
                if(cases==0){craft.setPosition(160,150,160);require(world.spawnEntity(craft),"matrix fixture spawn");}
                require(VehicleDoors.toggle(craft,root),"complete door group opens kind="+kind+" motion="+motion);
                for(BlockPos at:group)require(craft.structure.at(at).state.getValue(BlockDoor.OPEN),"all group cells open");
                Vec3d start=new Vec3d(root).addVector(.5,1,-3),end=start.addVector(0,0,6);
                // Block selection callbacks must remain usable after the leaf moves.
                for(BlockPos at:group)craft.view.getBlockState(at).collisionRayTrace(craft.view,at,start,end);
                require(VehicleDoors.toggle(craft,root),"complete door group closes");
                require(craft.structure.write().equals(snapshot.write()),"full block states, appearance and private NBT preserved");
                for(VehicleCollision.OrientedBox box:craft.collision.pose(45))require(Double.isFinite(box.broad.minX),"rotated collision is finite");
                cases++;
            }
        }finally{craft.setDead();}
        System.out.println("[vandorlabs][reprolab] vehicle-door-matrix PASS cases="+cases+" normal-paired-3x3 all-motions frames depths facings custom-materials full-NBT");
    }
}
