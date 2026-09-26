package com.vandorlabs.items;

import com.vandorlabs.blocks.BlockProgrammableBlock;
import com.vandorlabs.blocks.BlockProgrammableSlab;
import com.vandorlabs.blocks.BlockProgrammableWall;
import com.vandorlabs.blocks.BlockPropulsionLight;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.persistence.SpaceDoorData;
import com.vandorlabs.redstone.RedstoneChannelMember;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.tiles.TileEntityProgrammableChair;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import com.vandorlabs.tiles.TileEntityProgrammableLight;
import com.vandorlabs.tiles.TileEntityProgrammableTrigger;
import com.vandorlabs.tiles.TileEntityRampController;
import com.vandorlabs.tiles.TileEntityRedstoneChannel;
import com.vandorlabs.tiles.TileEntityRedstoneLight;
import com.vandorlabs.tiles.TileEntitySpaceDoor;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Semantic setting names shared by the duplifier's capture and apply paths. */
public final class ProgrammableSettings {
    public static final String WALL_TEXTURE = "wall_texture";
    public static final String PRIMARY_TEXTURE = "primary_texture";
    public static final String PRIMARY_KIND = "primary_kind";
    public static final String CHANNEL = "redstone_channel";
    public static final String TRIGGER = "redstone_trigger";
    public static final String JOIN = "join";
    public static final String ACTIVE = "active";
    public static final String PARTICLES = "particles";
    public static final String PROPULSION_SHAPE = "propulsion_shape";
    public static final String PORTHOLE_SHAPE = "porthole_shape";
    public static final String GLASS_SHADE = "glass_shade";
    public static final String GLASS_SIZE = "glass_size";
    public static final String SLAB_TILE_SIDES = "slab_tile_sides";
    public static final String DIAGONAL_FULL_WIDTH = "diagonal_full_width";
    public static final String TRIGGER_ON_TEXTURE = "trigger_on_texture";
    public static final String DISPLAY_MODE = "display_mode";
    public static final String ANIMATION_SPEED = "animation_speed";
    public static final String FRAMED = "framed";
    public static final String INPUT_PANEL = "input_panel";
    public static final String SECONDARY_INPUT_PANEL = "secondary_input_panel";
    public static final String SMALL_INPUT = "small_input";
    public static final String WALL_POSITION = "wall_position";
    public static final String LIGHT_LEVEL = "light_level";
    public static final String CHAIR_STYLE = "chair_style";
    public static final String CHAIR_HEIGHT = "chair_height";
    public static final String DOOR_DESIGN = "door_design";
    public static final String DOOR_DETAIL = "door_detail";
    public static final String DOOR_SLIDE_DIRECTION = "door_slide_direction";
    public static final String DOOR_MIDDLE = "door_middle";
    public static final String DOOR_SLIDING = "door_sliding";
    public static final String DOOR_HINGES = "door_hinges";
    public static final String DOOR_PANEL = "door_panel";
    public static final String DOOR_DEPTH = "door_depth";
    public static final String SWITCH_ROTATION = "switch_rotation";
    public static final String RAMP_START = "ramp_start";
    public static final String RAMP_END = "ramp_end";
    public static final String RAMP_TREAD_PIXELS = "ramp_tread_pixels";
    public static final String RAMP_SPEED = "ramp_speed";
    public static final String RAMP_LIFT = "ramp_lift";
    public static final String RAMP_EXTEND = "ramp_extend";
    public static final String RAMP_DIRECTION = "ramp_direction";
    public static final String RAMP_TRAVEL = "ramp_travel";

    private ProgrammableSettings() { }

    private static boolean isPorthole(Block block) {
        return block instanceof BlockProgrammableWall
                && ((BlockProgrammableWall) block).getShape()
                == BlockProgrammableWall.Shape.PORTHOLE;
    }

    private static boolean isDisplay(Block block) {
        return !(block instanceof BlockProgrammableWall)
                && !(block instanceof BlockProgrammableBlock)
                && !(block instanceof BlockProgrammableSlab);
    }

    private static boolean validScreen(String id) {
        for (ModBlocks.ScreenOption option : ModBlocks.SCREEN_OPTIONS)
            if (option.bareId.equals(id) || option.framedId.equals(id)) return true;
        return false;
    }

    private static int number(NBTTagCompound tag, String key, int fallback) {
        return tag.hasKey(key, 3) ? tag.getInteger(key) : fallback;
    }

    private static boolean flag(NBTTagCompound tag, String key, boolean fallback) {
        return tag.hasKey(key, 1) ? tag.getBoolean(key) : fallback;
    }

    private static String word(NBTTagCompound tag, String key, String fallback) {
        return tag.hasKey(key, 8) ? tag.getString(key) : fallback;
    }

    public static NBTTagCompound capture(World world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        Block block = world.getBlockState(pos).getBlock();
        if (tile == null) return null;
        NBTTagCompound out = new NBTTagCompound();
        if (tile instanceof RedstoneChannelMember)
            out.setInteger(CHANNEL, ((RedstoneChannelMember) tile).getRedstoneChannel());
        if (tile instanceof TileEntityProgrammableLight) {
            TileEntityProgrammableLight light = (TileEntityProgrammableLight) tile;
            out.setInteger(WALL_TEXTURE, light.getHousingTexture());
            out.setString(PRIMARY_KIND, "light");
            out.setInteger(PRIMARY_TEXTURE, light.getTexture());
            out.setInteger(LIGHT_LEVEL, light.getLightLevel());
            out.setInteger(TRIGGER, light.getTrigger());
            out.setBoolean(JOIN, light.isJoin());
            out.setBoolean(ACTIVE, light.isManualOn());
        } else if (tile instanceof TileEntityProgrammableTrigger) {
            TileEntityProgrammableTrigger trigger = (TileEntityProgrammableTrigger) tile;
            out.setInteger(WALL_TEXTURE, trigger.getHousingTexture());
            out.setInteger(TRIGGER_ON_TEXTURE, trigger.getOnTexture());
        } else if (tile instanceof TileEntityAnimatedScreenSelector) {
            TileEntityAnimatedScreenSelector screen = (TileEntityAnimatedScreenSelector) tile;
            out.setInteger(WALL_TEXTURE, screen.getHousingTexture());
            if (isDisplay(block)) {
                out.setString(PRIMARY_KIND, "screen");
                out.setString(PRIMARY_TEXTURE, screen.getSelectedScreen());
                out.setInteger(TRIGGER, screen.isRedstoneEnabled()
                        ? SpaceDoorData.TRIGGER_REDSTONE_ON
                        : SpaceDoorData.TRIGGER_DISABLED);
                out.setInteger(DISPLAY_MODE, screen.getDisplayMode());
                out.setInteger(ANIMATION_SPEED, screen.getAnimationSpeedIndex());
                out.setBoolean(FRAMED, screen.isFramed());
                out.setString(INPUT_PANEL, screen.getInputPanel());
                out.setString(SECONDARY_INPUT_PANEL, screen.getSecondaryInputPanel());
                out.setBoolean(SMALL_INPUT, screen.isSmallInput());
                out.setInteger(WALL_POSITION, screen.getWallPosition(1));
            }
            if (block instanceof BlockProgrammableSlab)
                out.setBoolean(SLAB_TILE_SIDES, screen.isSlabTileSides());
            if (block instanceof BlockProgrammableWall
                    && ((BlockProgrammableWall) block).getShape()
                    == BlockProgrammableWall.Shape.DIAGONAL)
                out.setBoolean(DIAGONAL_FULL_WIDTH, screen.isDiagonalFullWidth());
            if (isPorthole(block)) {
                out.setBoolean(JOIN, screen.isJoinPortholes());
                out.setInteger(PORTHOLE_SHAPE, screen.getPortholeShape());
                out.setInteger(GLASS_SHADE, screen.getGlassShade());
            }
        } else if (tile instanceof TileEntityRedstoneLight && block instanceof BlockPropulsionLight) {
            TileEntityRedstoneLight propulsion = (TileEntityRedstoneLight) tile;
            BlockPropulsionLight fixture = (BlockPropulsionLight) block;
            out.setInteger(WALL_TEXTURE, propulsion.getSideTexture());
            out.setBoolean(JOIN, propulsion.isJoin());
            out.setBoolean(ACTIVE, propulsion.getManualMode() != 0);
            out.setBoolean(PARTICLES, propulsion.isParticleStreamSelected());
            if (!fixture.familyId().isEmpty()) out.setInteger(PROPULSION_SHAPE, fixture.shape());
        } else if (tile instanceof TileEntityProgrammableGlass) {
            TileEntityProgrammableGlass glass = (TileEntityProgrammableGlass) tile;
            out.setBoolean(JOIN, glass.isJoin());
            out.setInteger(GLASS_SHADE, glass.getShade());
            out.setInteger(GLASS_SIZE, glass.getSize());
        } else if (tile instanceof TileEntitySpaceDoor) {
            TileEntitySpaceDoor door = (TileEntitySpaceDoor) tile;
            out.setInteger(TRIGGER, door.getTrigger());
            out.setInteger(DOOR_DESIGN, door.getDesign());
            out.setInteger(DOOR_DETAIL, door.getDetail());
            out.setBoolean(FRAMED, door.isFramed());
            out.setInteger(DOOR_SLIDE_DIRECTION, door.getSlideDirection());
            out.setBoolean(DOOR_MIDDLE, door.isMiddle());
            out.setBoolean(DOOR_SLIDING, door.isSliding());
            out.setBoolean(DOOR_HINGES, door.hasHinges());
            out.setBoolean(DOOR_PANEL, door.hasPanel());
            out.setInteger(DOOR_DEPTH, door.getPlacementDepth());
        } else if (tile instanceof TileEntityProgrammableChair) {
            TileEntityProgrammableChair chair = (TileEntityProgrammableChair) tile;
            out.setInteger(CHAIR_STYLE, chair.getStyle());
            out.setInteger(CHAIR_HEIGHT, chair.getHeight());
        } else if (tile instanceof TileEntityRedstoneChannel) {
            TileEntityRedstoneChannel switchTile = (TileEntityRedstoneChannel) tile;
            out.setBoolean(ACTIVE, switchTile.isLocalOn());
            out.setInteger(SWITCH_ROTATION, switchTile.getMountRotation());
        } else if (tile instanceof TileEntityRampController) {
            TileEntityRampController ramp = (TileEntityRampController) tile;
            out.setInteger(TRIGGER, ramp.activateOnPower
                    ? SpaceDoorData.TRIGGER_REDSTONE_ON : SpaceDoorData.TRIGGER_REDSTONE_OFF);
            out.setInteger(RAMP_START, ramp.startOffset);
            out.setInteger(RAMP_END, ramp.endOffset());
            out.setInteger(RAMP_TREAD_PIXELS, ramp.treadPixels);
            out.setInteger(RAMP_SPEED, ramp.speed);
            out.setBoolean(RAMP_LIFT, ramp.elevator);
            out.setBoolean(RAMP_EXTEND, ramp.extendSegments);
            out.setInteger(RAMP_DIRECTION, ramp.rampDirection().getIndex());
            out.setInteger(RAMP_TRAVEL, ramp.travelAxis);
        }
        return out.hasNoTags() ? null : out;
    }

    /** Apply only settings supported by the target; placement orientation is untouched. */
    public static boolean apply(World world, BlockPos pos, NBTTagCompound values) {
        return apply(world, pos, values, null);
    }

    public static boolean apply(World world, BlockPos pos, NBTTagCompound values,
            EntityPlayer player) {
        TileEntity tile = world.getTileEntity(pos);
        Block block = world.getBlockState(pos).getBlock();
        if (tile == null || values == null || values.hasNoTags()) return false;
        boolean applicable = false;
        if (tile instanceof TileEntityRedstoneLight && block instanceof BlockPropulsionLight) {
            BlockPropulsionLight fixture = (BlockPropulsionLight) block;
            if (values.hasKey(PROPULSION_SHAPE, 3) && !fixture.familyId().isEmpty()) {
                BlockPropulsionLight.configureShape(world, pos,
                        values.getInteger(PROPULSION_SHAPE));
                tile = world.getTileEntity(pos);
                block = world.getBlockState(pos).getBlock();
                applicable = true;
            }
            TileEntityRedstoneLight propulsion = (TileEntityRedstoneLight) tile;
            if (values.hasKey(WALL_TEXTURE, 3)) {
                propulsion.setSideTexture(values.getInteger(WALL_TEXTURE));
                applicable = true;
            }
            if (values.hasKey(JOIN, 1) && ((BlockPropulsionLight) block).hasJoinMode()) {
                propulsion.setJoin(values.getBoolean(JOIN));
                applicable = true;
            }
            if (values.hasKey(ACTIVE, 1) || values.hasKey(PARTICLES, 1)) {
                int channel = propulsion.getRedstoneChannel();
                propulsion.setRedstoneChannel(0);
                boolean active = flag(values, ACTIVE, propulsion.getManualMode() != 0);
                boolean particles = flag(values, PARTICLES,
                        propulsion.isParticleStreamSelected());
                propulsion.setManualMode(active ? particles ? 2 : 1 : 0, true);
                propulsion.setRedstoneChannel(channel);
                applicable = true;
            }
        } else if (tile instanceof TileEntityProgrammableLight) {
            TileEntityProgrammableLight light = (TileEntityProgrammableLight) tile;
            int trigger = number(values, TRIGGER, light.getTrigger());
            if (!SpaceDoorData.validTrigger(trigger)) trigger = light.getTrigger();
            int primary = light.getTexture();
            if ("light".equals(values.getString(PRIMARY_KIND))
                    && values.hasKey(PRIMARY_TEXTURE, 3))
                primary = values.getInteger(PRIMARY_TEXTURE);
            light.configure(primary, number(values, LIGHT_LEVEL, light.getLightLevel()),
                    flag(values, JOIN, light.isJoin()),
                    number(values, CHANNEL, light.getRedstoneChannel()),
                    number(values, WALL_TEXTURE, light.getHousingTexture()), trigger);
            if (values.hasKey(ACTIVE, 1)) light.setOn(values.getBoolean(ACTIVE));
            applicable = values.hasKey(JOIN) || values.hasKey(CHANNEL)
                    || values.hasKey(TRIGGER) || values.hasKey(WALL_TEXTURE)
                    || values.hasKey(LIGHT_LEVEL) || values.hasKey(ACTIVE)
                    || "light".equals(values.getString(PRIMARY_KIND));
        } else if (tile instanceof TileEntityProgrammableTrigger) {
            TileEntityProgrammableTrigger trigger = (TileEntityProgrammableTrigger) tile;
            if (values.hasKey(WALL_TEXTURE) || values.hasKey(TRIGGER_ON_TEXTURE)
                    || values.hasKey(CHANNEL)) {
                trigger.configure(number(values, WALL_TEXTURE, trigger.getHousingTexture()),
                        number(values, TRIGGER_ON_TEXTURE, trigger.getOnTexture()),
                        number(values, CHANNEL, trigger.getRedstoneChannel()));
                applicable = true;
            }
        } else if (tile instanceof TileEntityAnimatedScreenSelector) {
            TileEntityAnimatedScreenSelector screen = (TileEntityAnimatedScreenSelector) tile;
            if (values.hasKey(WALL_TEXTURE, 3)) {
                screen.setHousingTexture(values.getInteger(WALL_TEXTURE));
                applicable = true;
            }
            if (isDisplay(block)) {
                if ("screen".equals(values.getString(PRIMARY_KIND))
                        && values.hasKey(PRIMARY_TEXTURE, 8)
                        && validScreen(values.getString(PRIMARY_TEXTURE))) {
                    screen.setSelectedScreen(values.getString(PRIMARY_TEXTURE));
                    applicable = true;
                }
                int trigger = number(values, TRIGGER, -1);
                if (trigger == SpaceDoorData.TRIGGER_DISABLED
                        || trigger == SpaceDoorData.TRIGGER_REDSTONE_ON) {
                    screen.setRedstoneEnabled(trigger == SpaceDoorData.TRIGGER_REDSTONE_ON);
                    applicable = true;
                }
                if (values.hasKey(DISPLAY_MODE, 3)) {
                    screen.setDisplayMode(values.getInteger(DISPLAY_MODE)); applicable = true;
                }
                if (values.hasKey(ANIMATION_SPEED, 3)) {
                    screen.setAnimationSpeedIndex(values.getInteger(ANIMATION_SPEED)); applicable = true;
                }
                if (values.hasKey(FRAMED, 1)) {
                    screen.setFramed(values.getBoolean(FRAMED)); applicable = true;
                }
                if (values.hasKey(INPUT_PANEL, 8)) {
                    screen.setInputPanel(values.getString(INPUT_PANEL)); applicable = true;
                }
                if (values.hasKey(SECONDARY_INPUT_PANEL, 8)) {
                    screen.setSecondaryInputPanel(values.getString(SECONDARY_INPUT_PANEL)); applicable = true;
                }
                if (values.hasKey(SMALL_INPUT, 1)) {
                    screen.setSmallInput(values.getBoolean(SMALL_INPUT)); applicable = true;
                }
                if (values.hasKey(WALL_POSITION, 3)) {
                    screen.setWallPosition(values.getInteger(WALL_POSITION)); applicable = true;
                }
            }
            if (block instanceof BlockProgrammableSlab && values.hasKey(SLAB_TILE_SIDES, 1)) {
                screen.setSlabTileSides(values.getBoolean(SLAB_TILE_SIDES)); applicable = true;
            }
            if (block instanceof BlockProgrammableWall
                    && ((BlockProgrammableWall) block).getShape()
                    == BlockProgrammableWall.Shape.DIAGONAL
                    && values.hasKey(DIAGONAL_FULL_WIDTH, 1)) {
                screen.setDiagonalFullWidth(values.getBoolean(DIAGONAL_FULL_WIDTH));
                applicable = true;
            }
            if (isPorthole(block)) {
                if (values.hasKey(JOIN, 1)) {
                    screen.setJoinPortholes(values.getBoolean(JOIN)); applicable = true;
                }
                if (values.hasKey(PORTHOLE_SHAPE, 3)) {
                    screen.setPortholeShape(values.getInteger(PORTHOLE_SHAPE)); applicable = true;
                }
                if (values.hasKey(GLASS_SHADE, 3)) {
                    screen.setGlassShade(values.getInteger(GLASS_SHADE)); applicable = true;
                }
            }
            if (applicable) {
                net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
                world.notifyBlockUpdate(pos, state, state, 3);
                world.checkLightFor(net.minecraft.world.EnumSkyBlock.BLOCK, pos);
            }
        } else if (tile instanceof TileEntityProgrammableGlass) {
            TileEntityProgrammableGlass glass = (TileEntityProgrammableGlass) tile;
            if (values.hasKey(JOIN, 1)) {
                glass.setJoin(values.getBoolean(JOIN)); applicable = true;
            }
            if (values.hasKey(GLASS_SHADE, 3)) {
                glass.setShade(values.getInteger(GLASS_SHADE)); applicable = true;
            }
            if (values.hasKey(GLASS_SIZE, 3)) {
                glass.setSize(values.getInteger(GLASS_SIZE)); applicable = true;
            }
        } else if (tile instanceof TileEntitySpaceDoor) {
            TileEntitySpaceDoor door = (TileEntitySpaceDoor) tile;
            int trigger = number(values, TRIGGER, door.getTrigger());
            if (!SpaceDoorData.validTrigger(trigger)) trigger = door.getTrigger();
            if (values.hasKey(TRIGGER) || values.hasKey(DOOR_DESIGN)
                    || values.hasKey(DOOR_DETAIL) || values.hasKey(FRAMED)
                    || values.hasKey(DOOR_SLIDE_DIRECTION) || values.hasKey(DOOR_MIDDLE)
                    || values.hasKey(DOOR_SLIDING) || values.hasKey(DOOR_HINGES)
                    || values.hasKey(DOOR_PANEL) || values.hasKey(DOOR_DEPTH)) {
                door.configure(number(values, DOOR_DESIGN, door.getDesign()),
                        number(values, DOOR_DETAIL, door.getDetail()),
                        flag(values, FRAMED, door.isFramed()),
                        number(values, DOOR_SLIDE_DIRECTION, door.getSlideDirection()),
                        flag(values, DOOR_MIDDLE, door.isMiddle()),
                        flag(values, DOOR_SLIDING, door.isSliding()),
                        flag(values, DOOR_HINGES, door.hasHinges()), trigger,
                        flag(values, DOOR_PANEL, door.hasPanel()));
                if (values.hasKey(DOOR_DEPTH, 3))
                    door.setPlacementDepth(values.getInteger(DOOR_DEPTH));
                applicable = true;
            }
        } else if (tile instanceof TileEntityProgrammableChair) {
            TileEntityProgrammableChair chair = (TileEntityProgrammableChair) tile;
            if (values.hasKey(CHAIR_STYLE, 3)) {
                chair.setStyle(values.getInteger(CHAIR_STYLE)); applicable = true;
            }
            if (values.hasKey(CHAIR_HEIGHT, 3)) {
                chair.setHeight(values.getInteger(CHAIR_HEIGHT)); applicable = true;
            }
        } else if (tile instanceof TileEntityRedstoneChannel) {
            TileEntityRedstoneChannel switchTile = (TileEntityRedstoneChannel) tile;
            if (values.hasKey(ACTIVE, 1)) {
                switchTile.setLocalOn(values.getBoolean(ACTIVE)); applicable = true;
            }
            if (values.hasKey(SWITCH_ROTATION, 3)) {
                switchTile.setMountRotation(values.getInteger(SWITCH_ROTATION)); applicable = true;
            }
        } else if (tile instanceof TileEntityRampController && player != null) {
            TileEntityRampController ramp = (TileEntityRampController) tile;
            if (values.hasKey(RAMP_START) || values.hasKey(RAMP_END)
                    || values.hasKey(RAMP_TREAD_PIXELS) || values.hasKey(RAMP_SPEED)
                    || values.hasKey(RAMP_LIFT) || values.hasKey(RAMP_EXTEND)
                    || values.hasKey(RAMP_DIRECTION) || values.hasKey(RAMP_TRAVEL)
                    || (values.hasKey(TRIGGER, 3)
                    && (values.getInteger(TRIGGER) == SpaceDoorData.TRIGGER_REDSTONE_ON
                    || values.getInteger(TRIGGER) == SpaceDoorData.TRIGGER_REDSTONE_OFF))) {
                EnumFacing direction = EnumFacing.getFront(number(values,
                        RAMP_DIRECTION, ramp.rampDirection().getIndex()));
                int trigger = number(values, TRIGGER, ramp.activateOnPower
                        ? SpaceDoorData.TRIGGER_REDSTONE_ON : SpaceDoorData.TRIGGER_REDSTONE_OFF);
                applicable = ramp.configureTreads(player,
                        number(values, RAMP_START, ramp.startOffset),
                        number(values, RAMP_END, ramp.endOffset()),
                        number(values, RAMP_TREAD_PIXELS, ramp.treadPixels),
                        trigger == SpaceDoorData.TRIGGER_REDSTONE_ON,
                        number(values, RAMP_SPEED, ramp.speed) == 2,
                        flag(values, RAMP_LIFT, ramp.elevator), direction,
                        number(values, RAMP_TRAVEL, ramp.travelAxis),
                        flag(values, RAMP_EXTEND, ramp.extendSegments),
                        number(values, RAMP_SPEED, ramp.speed));
            }
        }
        if (tile instanceof RedstoneChannelMember && values.hasKey(CHANNEL, 3)) {
            ((RedstoneChannelMember) tile).setRedstoneChannel(values.getInteger(CHANNEL));
            if (tile instanceof TileEntityAnimatedScreenSelector) {
                net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
                world.notifyBlockUpdate(pos, state, state, 3);
            }
            applicable = true;
        }
        return applicable;
    }
}
