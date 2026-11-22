package com.vandorlabs.vehicle;

import com.vandorlabs.gear.GearTileEntity;
import java.io.FileInputStream;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Conversion and ordinary grid parking on one vertical and two folded wheel arms. */
public final class FoldedWheelChecks {
    public static void run(WorldServer world, EntityPlayerMP actor, String fixture) throws Exception {
        BlockPos base = new BlockPos(620, 115, 320);
        NBTTagList cells;
        try (FileInputStream in = new FileInputStream(fixture)) {
            cells = CompressedStreamTools.readCompressed(in).getTagList("cells", 10);
        }
        for (BlockPos p : BlockPos.getAllInBox(base.add(-4,-1,-4),base.add(16,-1,16)))
            world.setBlockState(p, Blocks.CONCRETE.getDefaultState(), 2);
        for (int i=0; i<cells.tagCount(); i++) {
            NBTTagCompound cell=cells.getCompoundTagAt(i);
            BlockPos at=base.add(cell.getInteger("x"), cell.getInteger("y"), cell.getInteger("z"));
            Block block=Block.getBlockFromName(cell.getString("block"));
            world.setBlockState(at, block.getStateFromMeta(cell.getInteger("meta")), 2);
            NBTTagCompound tile=cell.getCompoundTag("tile");
            if (!tile.hasNoTags()) {
                tile.setInteger("x",at.getX()); tile.setInteger("y",at.getY()); tile.setInteger("z",at.getZ());
                world.getTileEntity(at).readFromNBT(tile);
            }
        }
        roundTrip(world,actor,base,3);
        // Copy the vertical column onto two additional mounts, preserving each
        // member's relative owner offset just as a newly built extender does.
        for (int z : new int[]{4,8}) {
            for (int y=0;y<3;y++) {
                BlockPos source=base.add(8,y,6), target=base.add(3,y,z);
                world.setBlockState(target,world.getBlockState(source),2);
                NBTTagCompound data=world.getTileEntity(source).writeToNBT(new NBTTagCompound());
                data.setInteger("x",target.getX()); data.setInteger("y",target.getY()); data.setInteger("z",target.getZ());
                world.getTileEntity(target).readFromNBT(data);
            }
            for(int x=0;x<=3;x++)world.setBlockState(base.add(x,3,z),Blocks.IRON_BLOCK.getDefaultState(),2);
        }
        roundTrip(world,actor,base,5);
    }

    private static void roundTrip(WorldServer world,EntityPlayerMP actor,BlockPos base,int supports) throws Exception {
        BlockPos controller=base.add(7,3,5);
        VehicleScan scan = new VehicleScan(world,controller);
        while (!scan.step(256)) {}
        if(scan.result==null || scan.supportCount()!=supports)
            throw new IllegalStateException("Folded-wheel runway scan: "+scan.error+" supports="+scan.supportCount());
        EntityGroundVehicle craft=VehicleTransfer.assemble(world,scan.result,scan.origin,actor);
        if(craft.hover || new VehicleSteering().profile(craft.view).wheels!=supports)
            throw new IllegalStateException("Expected "+supports+" steering wheels in Taxi");
        VehicleRotation.Plan plan=VehicleRotation.plan(craft);
        BlockPos obstacle=base.add(0,0,2);
        world.setBlockState(obstacle,Blocks.STONE.getDefaultState(),2);
        try {
            VehicleService.validatePreparedParking(actor,craft,plan.structure,plan.origin);
            throw new IllegalStateException("Parking accepted an obstacle through a folded tyre");
        } catch(IllegalArgumentException expected) {
            if(!expected.getMessage().contains("obstacle"))throw expected;
        } finally {world.setBlockToAir(obstacle);}
        VehicleService.validatePreparedParking(actor,craft,plan.structure,plan.origin);
        VehicleTransfer.park(world,craft,plan.origin,actor,false);
        if(!(world.getTileEntity(base.add(0,2,2)) instanceof GearTileEntity))
            throw new IllegalStateException("Folded arm missing after ordinary Park");
        System.out.println("[vandorlabs] folded-wheel runway PASS supports="+supports+" Taxi steering, obstruction refusal and ordinary Park");
    }
    private FoldedWheelChecks() {}
}
