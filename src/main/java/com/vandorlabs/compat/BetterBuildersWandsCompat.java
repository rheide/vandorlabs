package com.vandorlabs.compat;

import com.vandorlabs.VandorLabs;
import com.vandorlabs.blocks.BlockBridgeChair;
import com.vandorlabs.blocks.BlockVandorDoor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Better Builder's Wands 0.11.1 places the picked {@code IBlockState} directly,
 * but deliberately does not copy tile-entity NBT. It also derives that state
 * from the picked item damage, which discards placement-only properties such
 * as facing. The wand records its exact destinations in
 * {@code bbw.lastPlaced}; after its right-click completes we restore the
 * source state and Vandor Labs tile data at those destinations.
 *
 * This integration has no link-time dependency on BBW. Servers without that
 * mod never create a pending copy and pay only the event-filter cost.
 */
public final class BetterBuildersWandsCompat {

    public static final BetterBuildersWandsCompat INSTANCE =
            new BetterBuildersWandsCompat();

    private static final String BBW_MODID = "betterbuilderswands";
    private static final Map<UUID, PendingCopy> PENDING = new HashMap<>();

    private BetterBuildersWandsCompat() {}

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        World world = event.getWorld();
        if (world.isRemote || !isBbwWand(event.getItemStack())) {
            return;
        }
        IBlockState clicked = world.getBlockState(event.getPos());
        ResourceLocation id = clicked.getBlock().getRegistryName();
        if (id == null || !VandorLabs.MODID.equals(id.getResourceDomain())) {
            return;
        }
        PENDING.put(event.getEntityPlayer().getUniqueID(),
                PendingCopy.capture(world, event.getPos(), clicked,
                        event.getHand(), getLastPlaced(event.getItemStack())));
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote) {
            return;
        }
        finishPending(event.player);
    }

    /** Visible for the live integration test; normal play uses player ticks. */
    public static boolean finishPending(EntityPlayer player) {
        PendingCopy pending = PENDING.get(player.getUniqueID());
        if (pending == null) {
            return false;
        }
        ItemStack wand = player.getHeldItem(pending.hand);
        int[] destinations = getLastPlaced(wand);
        if (destinations.length == 0) {
            if (--pending.ticksLeft <= 0) {
                PENDING.remove(player.getUniqueID());
            }
            return false;
        }
        boolean copied = pending.apply(player.world, destinations);
        PENDING.remove(player.getUniqueID());
        return copied;
    }

    private static boolean isBbwWand(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem().getRegistryName() == null) {
            return false;
        }
        return BBW_MODID.equals(stack.getItem().getRegistryName()
                .getResourceDomain());
    }

    private static int[] getLastPlaced(ItemStack wand) {
        if (!isBbwWand(wand) || !wand.hasTagCompound()) {
            return new int[0];
        }
        NBTTagCompound root = wand.getTagCompound();
        if (!root.hasKey("bbw", 10)) {
            return new int[0];
        }
        NBTTagCompound bbw = root.getCompoundTag("bbw");
        return bbw.hasKey("lastPlaced", 11)
                ? bbw.getIntArray("lastPlaced") : new int[0];
    }

    private static final class Part {
        final BlockPos offset;
        final IBlockState state;
        final NBTTagCompound tileNbt;

        Part(World world, BlockPos root, BlockPos pos) {
            offset = pos.subtract(root);
            state = world.getBlockState(pos);
            TileEntity tile = world.getTileEntity(pos);
            tileNbt = tile == null ? null
                    : tile.writeToNBT(new NBTTagCompound());
        }
    }

    private static final class PendingCopy {
        final EnumHand hand;
        final BlockPos clickedOffset;
        final Block block;
        final List<Part> parts;
        final int[] previousDestinations;
        final boolean[] previousDestinationWasSource;
        int ticksLeft = 4;

        private PendingCopy(EnumHand hand, BlockPos clickedOffset, Block block,
                List<Part> parts, int[] previousDestinations,
                boolean[] previousDestinationWasSource) {
            this.hand = hand;
            this.clickedOffset = clickedOffset;
            this.block = block;
            this.parts = parts;
            this.previousDestinations = previousDestinations;
            this.previousDestinationWasSource = previousDestinationWasSource;
        }

        static PendingCopy capture(World world, BlockPos clickedPos,
                IBlockState clicked, EnumHand hand, int[] previousDestinations) {
            BlockPos root = clickedPos;
            List<BlockPos> positions = new ArrayList<>();
            if (clicked.getBlock() instanceof BlockVandorDoor) {
                if (clicked.getValue(BlockVandorDoor.HALF)
                        == BlockDoor.EnumDoorHalf.UPPER) {
                    root = clickedPos.down();
                }
                if (world.getBlockState(root).getBlock() == clicked.getBlock()
                        && world.getBlockState(root.up()).getBlock()
                        == clicked.getBlock()) {
                    positions.add(root);
                    positions.add(root.up());
                }
            } else if (clicked.getBlock() instanceof BlockBridgeChair) {
                if (clicked.getValue(BlockBridgeChair.UPPER)) {
                    root = clickedPos.down();
                }
                if (world.getBlockState(root).getBlock() == clicked.getBlock()
                        && world.getBlockState(root.up()).getBlock()
                        == clicked.getBlock()) {
                    positions.add(root);
                    positions.add(root.up());
                }
            }
            if (positions.isEmpty()) {
                positions.add(clickedPos);
                root = clickedPos;
            }
            List<Part> parts = new ArrayList<>();
            for (BlockPos pos : positions) {
                parts.add(new Part(world, root, pos));
            }
            boolean[] occupied = new boolean[previousDestinations.length / 3];
            for (int i = 0; i < occupied.length; i++) {
                BlockPos oldTarget = new BlockPos(previousDestinations[i * 3],
                        previousDestinations[i * 3 + 1],
                        previousDestinations[i * 3 + 2]);
                occupied[i] = world.getBlockState(oldTarget).getBlock()
                        == clicked.getBlock();
            }
            return new PendingCopy(hand, clickedPos.subtract(root),
                    clicked.getBlock(), parts, previousDestinations.clone(),
                    occupied);
        }

        boolean apply(World world, int[] packedPositions) {
            // A failed/cancelled right-click leaves BBW's old journal intact.
            // Do not mistake that stale journal for this operation. Reusing
            // the same coordinates is still supported when they were empty
            // (or otherwise not the source block) at click time.
            if (Arrays.equals(previousDestinations, packedPositions)) {
                boolean newlyPlaced = false;
                for (int i = 0; i < previousDestinationWasSource.length; i++) {
                    BlockPos target = new BlockPos(packedPositions[i * 3],
                            packedPositions[i * 3 + 1],
                            packedPositions[i * 3 + 2]);
                    if (!previousDestinationWasSource[i]
                            && world.getBlockState(target).getBlock() == block) {
                        newlyPlaced = true;
                        break;
                    }
                }
                if (!newlyPlaced) {
                    return false;
                }
            }
            boolean copied = false;
            for (int i = 0; i + 2 < packedPositions.length; i += 3) {
                BlockPos wandTarget = new BlockPos(packedPositions[i],
                        packedPositions[i + 1], packedPositions[i + 2]);
                // BBW may have discarded properties, but it must at least
                // have placed the same Vandor Labs block before we touch it.
                if (world.getBlockState(wandTarget).getBlock() != block) {
                    continue;
                }
                BlockPos targetRoot = wandTarget.subtract(clickedOffset);
                for (Part part : parts) {
                    BlockPos target = targetRoot.add(part.offset);
                    world.setBlockState(target, part.state, 2);
                    copyTileNbt(world, target, part.tileNbt);
                }
                copied = true;
            }
            return copied;
        }
    }

    private static void copyTileNbt(World world, BlockPos target,
            NBTTagCompound source) {
        if (source == null) {
            return;
        }
        TileEntity tile = world.getTileEntity(target);
        if (tile == null) {
            return;
        }
        NBTTagCompound copy = source.copy();
        copy.setInteger("x", target.getX());
        copy.setInteger("y", target.getY());
        copy.setInteger("z", target.getZ());
        tile.readFromNBT(copy);
        tile.markDirty();
        IBlockState state = world.getBlockState(target);
        world.notifyBlockUpdate(target, state, state, 3);
    }
}
