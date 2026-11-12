package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockLargeProgrammableDoor;
import com.vandorlabs.blocks.BlockProgrammableDiagonalGlass;
import com.vandorlabs.blocks.BlockVandorDoor;
import com.vandorlabs.segmented.SegmentedDoorTile;
import com.vandorlabs.tiles.GlassTextures;
import com.vandorlabs.tiles.ScreenHousingTextures;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.tiles.TileEntityCanopy;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import com.vandorlabs.tiles.TileEntityProgrammableLight;
import com.vandorlabs.tiles.TileEntityProgrammableTrapdoor;
import com.vandorlabs.tiles.TileEntityProgrammableTrigger;
import com.vandorlabs.tiles.TileEntitySpaceDoor;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;

/** HUD names use the same catalog labels as the texture picker. */
public final class ProgrammableTooltip {
    private ProgrammableTooltip() { }

    public static void append(IBlockAccess world, BlockPos pos, List<String> tooltip) {
        TileEntity tile = appearanceTile(world, pos);
        if (tile == null) return;
        String texture = primaryTexture(tile);
        if (texture != null && !texture.isEmpty()) {
            tooltip.add(I18n.format("tooltip.vandorlabs.texture", texture));
        }
        if (tile instanceof TileEntitySpaceDoor) {
            motion(tooltip, ((TileEntitySpaceDoor) tile).isSliding());
        } else if (tile instanceof TileEntityProgrammableTrapdoor) {
            motion(tooltip, ((TileEntityProgrammableTrapdoor) tile).isSliding());
        } else if (tile instanceof SegmentedDoorTile) {
            motion(tooltip, true);
        }
    }

    /** Upper halves and all nine Large Door cells display their owner's settings. */
    static TileEntity appearanceTile(IBlockAccess world, BlockPos pos) {
        if (world == null || pos == null || !loaded(world, pos)) return null;
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof BlockLargeProgrammableDoor) {
            return ((BlockLargeProgrammableDoor) state.getBlock()).root(world, pos);
        }
        if (state.getBlock() instanceof BlockVandorDoor
                && state.getValue(BlockVandorDoor.HALF) == BlockDoor.EnumDoorHalf.UPPER) {
            BlockPos lower = pos.down();
            if (!loaded(world, lower) || world.getBlockState(lower).getBlock() != state.getBlock()) return null;
            return world.getTileEntity(lower);
        }
        return world.getTileEntity(pos);
    }

    private static boolean loaded(IBlockAccess world, BlockPos pos) {
        return !(world instanceof World) || ((World) world).isBlockLoaded(pos);
    }

    static String primaryTexture(TileEntity tile) {
        if (tile instanceof TileEntitySpaceDoor) {
            TileEntitySpaceDoor door = (TileEntitySpaceDoor) tile;
            int choice = door.getFaceTexture();
            return HousingTextureList.name(choice >= 0 ? choice
                    : ScreenHousingTextures.doorIndex(door.getDesign(), door.getDetail()));
        }
        if (tile instanceof TileEntityProgrammableTrapdoor) {
            return HousingTextureList.name(((TileEntityProgrammableTrapdoor) tile).getHousingTexture());
        }
        if (tile instanceof TileEntityProgrammableGlass) {
            TileEntityProgrammableGlass glass = (TileEntityProgrammableGlass) tile;
            return glassName(glass.getMaterial(), glass.getShade());
        }
        if (tile instanceof TileEntityCanopy) {
            TileEntityCanopy canopy = ((TileEntityCanopy) tile).owner();
            return glassName(canopy.large.material, canopy.large.shade);
        }
        if (tile instanceof SegmentedDoorTile) {
            int choice = ((SegmentedDoorTile) tile).effectiveTexture();
            return choice >= 0 ? HousingTextureList.name(choice)
                    : I18n.format("tooltip.vandorlabs.segmented_original");
        }
        if (!(tile instanceof TileEntityAnimatedScreenSelector)) return null;
        TileEntityAnimatedScreenSelector panel = (TileEntityAnimatedScreenSelector) tile;
        if (tile instanceof TileEntityProgrammableLight) {
            TileEntityProgrammableLight light = (TileEntityProgrammableLight) tile;
            int off = light.getOffFaceTexture();
            return HousingTextureList.name(off >= 0 && (!light.isOn() || light.getLightLevel() == 0)
                    ? off : light.getFaceTexture());
        }
        if (tile instanceof TileEntityProgrammableTrigger) {
            return HousingTextureList.name(((TileEntityProgrammableTrigger) tile).getVisibleTexture());
        }
        if (tile.getBlockType() instanceof BlockProgrammableDiagonalGlass) {
            return glassName(panel.getGlassMaterial(), panel.getGlassShade());
        }
        if (TileEntityAnimatedScreenSelector.supportsActivation(tile.getBlockType())) {
            int choice = panel.primaryArtworkTexture();
            if (choice == TileEntityAnimatedScreenSelector.REDSTONE_SURFACE)
                return I18n.format("tooltip.vandorlabs.redstone_display");
            if (choice == TileEntityAnimatedScreenSelector.CHIP_SURFACE)
                return I18n.format("tooltip.vandorlabs.chip_display");
            if (choice == TileEntityAnimatedScreenSelector.VEHICLE_SURFACE)
                return I18n.format("tooltip.vandorlabs.vehicle_display");
            return HousingTextureList.name(choice);
        }
        return HousingTextureList.name(panel.getHousingTexture());
    }

    private static String glassName(int material, int shade) {
        return material >= 0 ? HousingTextureList.name(material) : GlassTextures.LABELS[GlassTextures.clamp(shade)];
    }

    private static void motion(List<String> tooltip, boolean sliding) {
        tooltip.add(I18n.format("tooltip.vandorlabs.movement",
                I18n.format(sliding ? "tooltip.vandorlabs.sliding" : "tooltip.vandorlabs.rotating")));
    }
}
