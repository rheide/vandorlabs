package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.BlockLandingGear;
import com.vandorlabs.blocks.BlockNonWheeledLandingGear;
import com.vandorlabs.blocks.BlockPropulsionLight;
import com.vandorlabs.blocks.BlockTelescopicLandingGear;
import com.vandorlabs.blocks.BlockWheel;
import com.vandorlabs.gear.GearBlock;
import com.vandorlabs.gear.GearGeometry;
import com.vandorlabs.gear.GearTileEntity;
import com.vandorlabs.gear.core.GearTopology;
import com.vandorlabs.gear.track.TrackBlock;
import com.vandorlabs.gear.track.TrackTile;
import com.vandorlabs.tiles.TileEntityLandingGear;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Cheap parked-screen check of the known supports in its cached connected component. */
final class VehicleStatusSupport {
    private final Set<BlockPos> feet = new HashSet<>(), tracks = new HashSet<>(), skis = new HashSet<>(), roots = new HashSet<>();
    private boolean blocked, nonWheeled, otherFeet, skisOnIce = true;

    boolean ready() { return !blocked && (feet.size() >= 3 || tracks.size() >= 2 || skis.size() >= 2); }
    boolean taxi() { return !nonWheeled || !otherFeet && skisOnIce; }
    void unavailable() { blocked = true; }

    void observe(World world, BlockPos pos, IBlockState state, TileEntity tile) {
        Block block = state.getBlock();
        if (block instanceof GearBlock) {
            GearTileEntity root = tile instanceof GearTileEntity ? ((GearTileEntity)tile).loadedController() : null;
            if (root == null && tile instanceof TrackTile) {
                standaloneTrack(world, pos, (TrackTile)tile);
                return;
            }
            if (root == null || !root.isOperational()) { blocked = true; return; }
            if (!roots.add(root.getPos())) return;
            if (root.hasFailedArmSnapshot() || com.vandorlabs.gear.ArmTransactions.locked(world, root.getPos())) { blocked = true; return; }
            if (root.foldedWheelSupport(root.progress())) for (GearTopology.Cell foot : root.assembly().feet()) {
                BlockPos at = root.localCell(foot);
                if (!world.isBlockLoaded(at)) { blocked = true; continue; }
                IBlockState wheel = world.getBlockState(at);
                if (wheel.getBlock() instanceof GearBlock && ((GearBlock)wheel.getBlock()).componentRoot(world,at,wheel).equals(at)
                        && GearGeometry.landingContact(root,at) != null) { feet.add(at); otherFeet = true; }
            }
            if (!root.isLandingAssembly() || root.mountDirection() != EnumFacing.DOWN) return;
            if (!root.landingDeployed()) { blocked = true; return; }
            GearTopology.FootKind kind = root.assembly().kind();
            for (BlockPos column : root.columns()) {
                BlockPos at = root.getPos().add(column);
                feet.add(at);
                if (kind == GearTopology.FootKind.SKI) skis.add(at);
            }
            if (kind == GearTopology.FootKind.SKI) {
                nonWheeled = true;
                for (GearTopology.Cell foot : root.assembly().feet())
                    skisOnIce &= VehicleSkiTraction.parkedRunnerOnIce(world,root.localCell(foot),.02);
            }
            if (kind == GearTopology.FootKind.PLATE) { nonWheeled = true; otherFeet = true; }
            if (kind == GearTopology.FootKind.WHEEL) otherFeet = true;
            for (BlockPos member : root.members()) if (world.isBlockLoaded(member) && world.getBlockState(member).getBlock() instanceof TrackBlock) tracks.add(member);
        } else if (block instanceof BlockWheel) {
            BlockWheel wheel = (BlockWheel)block;
            BlockPos root = wheel.root(pos,state);
            if (!roots.add(root)) return;
            int part = 0;
            for (BlockPos member : wheel.members(root,state)) {
                if (!world.isBlockLoaded(member)) { blocked = true; return; }
                IBlockState cell = world.getBlockState(member);
                if (cell.getBlock()!=block || cell.getValue(BlockWheel.PART)!=part++
                        || cell.getValue(BlockWheel.FACING)!=state.getValue(BlockWheel.FACING)) { blocked = true; return; }
            }
            feet.add(root); otherFeet = true;
        } else if (block instanceof BlockTelescopicLandingGear) {
            BlockPos mount = block instanceof BlockNonWheeledLandingGear ? ((BlockNonWheeledLandingGear)block).connectedRoot(world,pos) : pos;
            TileEntityLandingGear gear = ((BlockTelescopicLandingGear)block).root(world,mount);
            if (gear == null) { blocked = true; return; }
            BlockPos root = gear.getPos();
            if (!roots.add(root)) return;
            float target = world.getBlockState(root).getValue(BlockTelescopicLandingGear.EXTENDED) ? gear.getExtensionPixels()/16F : 0;
            if (Math.abs(gear.progress-target) >= .001) { blocked = true; return; }
            feet.add(root);
            if (block instanceof BlockNonWheeledLandingGear && ((BlockNonWheeledLandingGear)block).ski)
                runner(world,root,.02-gear.progress);
            else { otherFeet = true; nonWheeled |= BlockNonWheeledLandingGear.forbidsTaxi(block); }
        } else if (block instanceof BlockLandingGear) {
            feet.add(pos); otherFeet = true;
        } else if (block instanceof BlockPropulsionLight && state.getValue(BlockPropulsionLight.FACING)==EnumFacing.DOWN) {
            feet.add(pos);
        }
    }

    private void standaloneTrack(World world, BlockPos pos, TrackTile track) {
        tracks.add(pos); feet.add(pos);
        BlockPos leader = track.leader();
        if (leader == null || !roots.add(leader)) return;
        if (!world.isBlockLoaded(leader) || !(world.getTileEntity(leader) instanceof TrackTile)) { blocked = true; return; }
        for (BlockPos member : ((TrackTile)world.getTileEntity(leader)).trackMembers()) {
            if (!world.isBlockLoaded(member) || world.getBlockState(member).getBlock()!=track.getBlockType()) { blocked = true; return; }
            tracks.add(member); feet.add(member);
        }
    }

    private void runner(World world, BlockPos pos, double offset) {
        skis.add(pos);
        nonWheeled = true;
        skisOnIce &= VehicleSkiTraction.parkedRunnerOnIce(world,pos,offset);
    }
}
