package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.neoforge.seating.NativeSeatBindings;
import com.vandorlabs.neoforge.seating.NativeSeatBlock;
import com.vandorlabs.neoforge.seating.NativeSeatKind;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class NativeVehicleCouplerChecks {
    public static void checkScan(ServerLevel world, BlockPos head, boolean paired, BiConsumer<Boolean, String> check) {
        var controller = head.west(2);
        var pilot = controller.north();
        var seat = NativeSeatBindings.BLOCKS.get(NativeSeatKind.PILOT).defaultBlockState()
                .setValue(NativeSeatBlock.FACING, Direction.NORTH);
        try {
            world.setBlock(controller, NativeVehicleBindings.CONTROLLER.defaultBlockState(), Block.UPDATE_ALL);
            world.setBlock(pilot, seat, Block.UPDATE_ALL);
            world.setBlock(pilot.above(), seat.setValue(NativeSeatBlock.UPPER, true), Block.UPDATE_ALL);
            var scan = NativeVehicleScan.scan(world, controller);
            var cells = scan.physical().cells();
            check.accept(cells.size() == (paired ? 7 : 5)
                            && cells.stream().anyMatch(cell -> cell.pos.equals(head.east(2))) == paired,
                    paired ? "matching engaged heads capture both mounted segments and their exact tile snapshots"
                            : "an open head keeps the neighboring segment outside the vehicle scan");
        } finally {
            for (var pos : new BlockPos[]{pilot.above(), pilot, controller})
                world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private NativeVehicleCouplerChecks() {}
}
