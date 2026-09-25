package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockBridgeChair;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.tiles.TileEntityProgrammableChair;
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
        String[] roles={"command","companion","operator","conference","mess_hall"};
        BlockPos pos = new BlockPos(18, 4, 18);
        for (int style = 0; style < roles.length; style++) {
          for (int height = 0; height < 3; height++) {
            String role = roles[style];
            BlockBridgeChair chair = (BlockBridgeChair) ModBlocks.PROGRAMMABLE_CHAIR;
            world.setBlockToAir(pos.up());
            world.setBlockToAir(pos);
            IBlockState lower = chair.getDefaultState()
                    .withProperty(BlockBridgeChair.FACING, EnumFacing.SOUTH)
                    .withProperty(BlockBridgeChair.UPPER, false);
            world.setBlockState(pos, lower, 2);
            world.setBlockState(pos.up(), lower.withProperty(
                    BlockBridgeChair.UPPER, true), 2);
            TileEntityProgrammableChair tile = (TileEntityProgrammableChair) world.getTileEntity(pos);
            tile.setStyle(style);
            tile.setHeight(height);
            require(chair.getActualState(lower, world, pos).getValue(BlockBridgeChair.STYLE)
                            == BlockBridgeChair.Style.byIndex(style)
                            && chair.getActualState(lower, world, pos).getValue(BlockBridgeChair.HEIGHT)
                            == BlockBridgeChair.Height.byIndex(height),
                    role + " model selection was not applied");
            TileEntityProgrammableChair restored = new TileEntityProgrammableChair();
            restored.readFromNBT(tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));
            require(restored.getStyle() == style && restored.getHeight() == height,
                    role + " height was not saved");
            net.minecraft.item.ItemStack copied = chair.getPickBlock(lower, null,
                    world, pos, player);
            require(copied.getSubCompound("BlockEntityTag") != null
                            && copied.getSubCompound("BlockEntityTag").getInteger("ChairStyle") == style
                            && copied.getSubCompound("BlockEntityTag").getInteger("ChairHeight") == height,
                    role + " middle-click copy lost style");
            require(chair.onBlockActivated(world, pos, lower, player,
                    EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 0.5F, 0.5F),
                    role + " activation was not handled");
            require(player.getRidingEntity() instanceof EntityChairSeat,
                    role + " did not mount player on seat entity");
            EntityChairSeat seat = (EntityChairSeat) player.getRidingEntity();
            seat.updatePassenger(player);
            double seatPixels=role.equals("mess_hall")?10
                    :role.equals("companion") || role.equals("conference")?11:12;
            require(Math.abs(player.posY - (pos.getY() + (seatPixels + (height - 1) * 2) / 16.0D
                            - EntityChairSeat.RIDER_PELVIS_OFFSET)) < 0.01D,
                    role + " rider pelvis does not match model cushion marker");
            player.dismountRidingEntity();
            world.setBlockToAir(pos);
            require(seat.isDead, "breaking " + role + " chair did not clean up seat entity");
            world.setBlockToAir(pos.up());
          }
        }
        System.out.println("[vandorlabs][reprolab] chair-runtime PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(
                "chair runtime check: " + message);
    }
}
