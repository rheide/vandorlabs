package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockBridgeChair;
import com.vandorlabs.entity.EntityChairSeat;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Live integrated-server contract for chair placement, mounting and cleanup. */
public final class ChairRuntimeChecks {
    private ChairRuntimeChecks() { }

    public static void run(World world, EntityPlayerMP player) {
        Block raw = Block.REGISTRY.getObject(new ResourceLocation(
                "vandorlabs", "bridge_chair_simple_command"));
        require(raw instanceof BlockBridgeChair, "simple chair is not registered");
        BlockBridgeChair chair = (BlockBridgeChair) raw;
        BlockPos pos = new BlockPos(18, 4, 18);
        world.setBlockToAir(pos.up());
        world.setBlockToAir(pos);
        IBlockState lower = chair.getDefaultState()
                .withProperty(BlockBridgeChair.FACING, EnumFacing.SOUTH)
                .withProperty(BlockBridgeChair.UPPER, false);
        world.setBlockState(pos, lower, 2);
        world.setBlockState(pos.up(), lower.withProperty(
                BlockBridgeChair.UPPER, true), 2);
        require(chair.onBlockActivated(world, pos, lower, player,
                EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 0.5F, 0.5F),
                "chair activation was not handled");
        require(player.getRidingEntity() instanceof EntityChairSeat,
                "chair did not mount player on seat entity");
        EntityChairSeat seat = (EntityChairSeat) player.getRidingEntity();
        seat.updatePassenger(player);
        require(Math.abs(player.posY - (pos.getY() + 10.0D / 16.0D
                        - EntityChairSeat.RIDER_PELVIS_OFFSET)) < 0.01D,
                "chair rider pelvis does not match model cushion marker");
        player.dismountRidingEntity();
        world.setBlockToAir(pos);
        require(seat.isDead, "breaking chair did not clean up seat entity");
        world.setBlockToAir(pos.up());
        System.out.println("[vandorlabs][reprolab] chair-runtime PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(
                "chair runtime check: " + message);
    }
}
