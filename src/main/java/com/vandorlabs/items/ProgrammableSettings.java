package com.vandorlabs.items;

import com.vandorlabs.blocks.BlockProgrammableBlock;
import com.vandorlabs.blocks.BlockProgrammableSlab;
import com.vandorlabs.blocks.BlockProgrammableWall;
import com.vandorlabs.blocks.BlockPropulsionLight;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.persistence.SpaceDoorData;
import com.vandorlabs.redstone.RedstoneChannelMember;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.ChannelData;
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
    public static final String TRAPDOOR_SLIDE_OVER_SURFACE="trapdoor_slide_over_surface";
    public static final String TRAPDOOR_SLIDE_INTO_WALL="trapdoor_slide_into_wall";
    public static final String TRAPDOOR_COVER_FACING="trapdoor_cover_facing";
    public static final String TRAPDOOR_TILE_TEXTURE="trapdoor_tile_texture";
    public static final String TRAPDOOR_COVER="trapdoor_cover";
    public static final String TRAPDOOR_POSITION = "trapdoor_position";
    public static final String DIAGONAL_GEOMETRY = "diagonal_geometry";
    public static final String FACE_TEXTURES = "face_textures";
    public static final String WALL_TEXTURE = "wall_texture";
    public static final String PRIMARY_TEXTURE = "primary_texture";
    public static final String PRIMARY_KIND = "primary_kind";
    public static final String REDSTONE_ROWS="redstone_rows";
    public static final String CHANNEL = "redstone_channel";
    public static final String TRIGGER = "redstone_trigger";
    public static final String JOIN = "join";
    public static final String ACTIVE = "active";
    public static final String PARTICLES = "particles";
    public static final String PROPULSION_SHAPE = "propulsion_shape";
    public static final String PORTHOLE_SHAPE = "porthole_shape";
    public static final String GLASS_SHADE = "glass_shade";
    public static final String GLASS_SIZE = "glass_size";
    public static final String SIDE_TEXTURE="side_texture";
    public static final String SLAB_TILE_SIDES = "slab_tile_sides";
    public static final String DIAGONAL_FULL_WIDTH = "diagonal_full_width";
    public static final String TRIGGER_ON_TEXTURE = "trigger_on_texture";
    public static final String PRIMARY_SURFACE="primary_surface", SECONDARY_SURFACE="secondary_surface";
    public static final String DISPLAY_MODE = "display_mode";
    public static final String ANIMATION_SPEED = "animation_speed";
    public static final String FRAMED = "framed";
    public static final String INPUT_PANEL = "input_panel";
    public static final String SECONDARY_INPUT_PANEL = "secondary_input_panel";
    public static final String SMALL_INPUT = "small_input";
    public static final String WALL_POSITION = "wall_position";
    public static final String LIGHT_FACE_TEXTURE="light_face_texture";
    public static final String LIGHT_LEVEL = "light_level";
    public static final String CHAIR_STYLE = "chair_style";
    public static final String CHAIR_HEIGHT = "chair_height";
    public static final String DOOR_TILE_TEXTURE="door_tile_texture";
    public static final String DOOR_FACE_TEXTURE="door_face_texture";
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
    public static final String RAMP_START_HALF = "ramp_start_half";
    public static final String RAMP_END_HALF = "ramp_end_half";
    public static final String RAMP_TREAD_PIXELS = "ramp_tread_pixels";
    public static final String RAMP_INTERPOLATION="ramp_interpolation";
    public static final String RAMP_SPEED = "ramp_speed";
    public static final String RAMP_LIFT = "ramp_lift";
    public static final String RAMP_EXTEND = "ramp_extend";
    public static final String RAMP_DIRECTION = "ramp_direction";
    public static final String RAMP_TRAVEL = "ramp_travel";
    public static final String RAMP_MATCH_TEXTURES = "ramp_match_textures";
    public static final String GEAR_SIZE = "gear_size", GEAR_LENGTH = "gear_length", GEAR_MODE = "gear_mode";

    private ProgrammableSettings() { }

    private static boolean isPorthole(Block block) {
        return block instanceof BlockProgrammableWall
                && ((BlockProgrammableWall) block).isPortholeShape();
    }

    private static boolean isDisplay(Block block) {
        return !(block instanceof BlockProgrammableWall)
                && !(block instanceof BlockProgrammableBlock)
                && !((block instanceof BlockProgrammableSlab || block instanceof com.vandorlabs.blocks.BlockProgrammableStairs));
    }

    private static boolean validScreen(String id) {
        for (ModBlocks.ScreenOption option : ModBlocks.SCREEN_OPTIONS)
            if (option.bareId.equals(id) || option.framedId.equals(id)) return true;
        return false;
    }

    private static int number(NBTTagCompound tag, String key, int fallback) {
        return tag.hasKey(key, 3) ? tag.getInteger(key) : fallback;
    }

    private static ChannelList channels(NBTTagCompound tag,RedstoneChannelMember member) {
        if(tag.hasKey(CHANNEL,11))return ChannelData.read(tag,CHANNEL,member.getRedstoneChannels());
        return tag.hasKey(CHANNEL,3)?ChannelList.of(Math.max(0,tag.getInteger(CHANNEL))):member.getRedstoneChannels();
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
        if(tile instanceof TileEntityAnimatedScreenSelector && com.vandorlabs.blocks.RedstoneScreenInteractions.supports(block))
            out.setTag(REDSTONE_ROWS,((TileEntityAnimatedScreenSelector)tile).redstoneConfiguration());
        if (tile instanceof RedstoneChannelMember) {
            ChannelList channels=((RedstoneChannelMember)tile).getRedstoneChannels();
            if(channels.size()<=1)out.setInteger(CHANNEL,channels.first());
            else out.setIntArray(CHANNEL,channels.toArray());
        }
        if (tile instanceof com.vandorlabs.tiles.TileEntityProgrammableTrapdoor) {
            com.vandorlabs.tiles.TileEntityProgrammableTrapdoor hatch=(com.vandorlabs.tiles.TileEntityProgrammableTrapdoor)tile;
            out.setInteger(WALL_TEXTURE,hatch.getHousingTexture());
            if(hatch instanceof com.vandorlabs.tiles.TileEntityProgrammableDiagonalTrapdoor){
                NBTTagCompound geometry=new NBTTagCompound();geometry.setInteger("mode",hatch.getPosition());out.setTag(DIAGONAL_GEOMETRY,geometry);
            } else out.setInteger(TRAPDOOR_POSITION,hatch.getPosition());
            if(hatch.canOffsetClosedLeaf())out.setInteger(TRAPDOOR_COVER_FACING,hatch.coverFacing().getHorizontalIndex());
            out.setBoolean(TRAPDOOR_SLIDE_OVER_SURFACE,hatch.isSlideOverSurface());out.setBoolean(TRAPDOOR_TILE_TEXTURE,hatch.isTileTexture());out.setBoolean(TRAPDOOR_COVER,hatch.isCover());out.setBoolean(DOOR_SLIDING,hatch.isSliding());if(hatch instanceof com.vandorlabs.tiles.TileEntityProgrammableDiagonalTrapdoor)out.setBoolean(TRAPDOOR_SLIDE_INTO_WALL,hatch.isSlideIntoWall());out.setInteger(TRIGGER,hatch.getTrigger());
        } else if (tile instanceof TileEntityProgrammableLight) {
            TileEntityProgrammableLight light = (TileEntityProgrammableLight) tile;
            out.setInteger(WALL_TEXTURE, light.getHousingTexture());
            out.setString(PRIMARY_KIND, "light");
            out.setInteger(PRIMARY_TEXTURE, light.getTexture());
            out.setInteger(LIGHT_FACE_TEXTURE,light.getFaceTexture());
            out.setInteger(LIGHT_LEVEL, light.getLightLevel());
            out.setBoolean(SMALL_INPUT,light.isSmallInput());out.setBoolean(SLAB_TILE_SIDES,light.isSlabTileSides());
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
            if(TileEntityAnimatedScreenSelector.supportsSideTexture(block))out.setInteger(SIDE_TEXTURE,screen.getSideTexture());
            if (block == ModBlocks.PROGRAMMABLE_STORAGE || block == ModBlocks.PROGRAMMABLE_BLOCK || block == ModBlocks.PROGRAMMABLE_SLAB || block == ModBlocks.PROGRAMMABLE_STAIRS) {
                NBTTagCompound faces = new NBTTagCompound();
                faces.setBoolean("enabled", screen.getFaceTextures().enabled);
                if (screen.getFaceTextures().enabled)
                    faces.setIntArray("choices", screen.getFaceTextures().choices());
                out.setTag(FACE_TEXTURES, faces);
            }
            if (isDisplay(block)) {
                out.setInteger(PRIMARY_SURFACE,screen.getSurfaceTexture(0));out.setInteger(SECONDARY_SURFACE,screen.getSurfaceTexture(1));
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
            if (block instanceof BlockProgrammableSlab || block instanceof com.vandorlabs.blocks.BlockProgrammableStairs || isDisplay(block))
                out.setBoolean(SLAB_TILE_SIDES, isDisplay(block)?screen.isSurfaceTileSides():screen.isSlabTileSides());
            if (block instanceof BlockProgrammableWall
                    && ((BlockProgrammableWall) block).isDiagonalShape())
            {
                out.setBoolean(DIAGONAL_FULL_WIDTH, screen.isDiagonalFullWidth());
                NBTTagCompound geometry=new NBTTagCompound();
                geometry.setInteger("mode",screen.isDiagonalHalfHeight()?2:screen.isDiagonalFullWidth()?1:0);
                geometry.setInteger("fill",screen.getDiagonalFill());
                out.setTag(DIAGONAL_GEOMETRY,geometry);
            }
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
            out.setInteger(PARTICLES,propulsion.getParticleLevel());
            if (!fixture.familyId().isEmpty()) out.setInteger(PROPULSION_SHAPE, fixture.shape());
        } else if (tile instanceof TileEntityProgrammableGlass) {
            TileEntityProgrammableGlass glass = (TileEntityProgrammableGlass) tile;
            out.setBoolean(JOIN, glass.isJoin());
            out.setInteger(GLASS_SHADE, glass.getShade());
            out.setInteger(GLASS_SIZE, glass.getSize());
        } else if (tile instanceof TileEntitySpaceDoor) {
            TileEntitySpaceDoor door = (TileEntitySpaceDoor) tile;
            out.setInteger(TRIGGER, door.getTrigger());
            out.setBoolean(DOOR_TILE_TEXTURE,door.isTileTexture());out.setInteger(DOOR_DESIGN, door.getDesign());out.setInteger(DOOR_FACE_TEXTURE,door.getFaceTexture());
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
            if (chair instanceof com.vandorlabs.tiles.TileEntityConnectedSeat)
                out.setBoolean(JOIN,((com.vandorlabs.tiles.TileEntityConnectedSeat)chair).isJoin());
            else out.setInteger(CHAIR_STYLE, chair.getStyle());
            out.setInteger(CHAIR_HEIGHT, chair.getHeight());
        } else if (tile instanceof TileEntityRedstoneChannel) {
            TileEntityRedstoneChannel switchTile = (TileEntityRedstoneChannel) tile;
            out.setBoolean(ACTIVE, switchTile.isLocalOn());
            out.setInteger(SWITCH_ROTATION, switchTile.getMountRotation());
        } else if (tile instanceof com.vandorlabs.tiles.TileEntityLandingGear) {
            com.vandorlabs.tiles.TileEntityLandingGear gear=(com.vandorlabs.tiles.TileEntityLandingGear)tile;
            out.setInteger(GEAR_SIZE,gear.getSize());out.setInteger(GEAR_LENGTH,gear.getExtensionPixels());out.setInteger(GEAR_MODE,gear.getMode());
        } else if (tile instanceof TileEntityRampController) {
            TileEntityRampController ramp = (TileEntityRampController) tile;
            out.setInteger(TRIGGER, ramp.activateOnPower
                    ? SpaceDoorData.TRIGGER_REDSTONE_ON : SpaceDoorData.TRIGGER_REDSTONE_OFF);
            out.setInteger(RAMP_START, ramp.startOffset);
            out.setInteger(RAMP_END, ramp.endOffset());
            out.setInteger(RAMP_START_HALF, ramp.startHalfSteps());
            out.setInteger(RAMP_END_HALF, ramp.endHalfSteps());
            out.setInteger(RAMP_TREAD_PIXELS, ramp.treadPixels);
            out.setInteger(RAMP_SPEED, ramp.speed);
            out.setInteger(RAMP_INTERPOLATION,ramp.interpolation);
            out.setBoolean(RAMP_LIFT, ramp.elevator);
            out.setBoolean(RAMP_EXTEND, ramp.extendSegments);
            out.setInteger(RAMP_DIRECTION, ramp.rampDirection().getIndex());
            out.setInteger(RAMP_TRAVEL, ramp.travelAxis);
            out.setBoolean(RAMP_MATCH_TEXTURES, ramp.matchTextures);
        }
        return out.hasNoTags() ? null : out;
    }

    /** Configure an isolated tile so crafting never mutates the world or its input stacks. */
    public static net.minecraft.item.ItemStack applyToItem(net.minecraft.item.ItemStack input, NBTTagCompound values) {
        if (!(input.getItem() instanceof net.minecraft.item.ItemBlock)) return net.minecraft.item.ItemStack.EMPTY;
        Block block = ((net.minecraft.item.ItemBlock)input.getItem()).getBlock();
        if (block.getRegistryName() == null || !block.getRegistryName().getResourcePath().startsWith("programmable_") && !(block instanceof com.vandorlabs.blocks.BlockLargeProgrammableDoor))
            return net.minecraft.item.ItemStack.EMPTY;
        TileEntity tile = block.createTileEntity(null, block.getStateFromMeta(input.getMetadata()));
        if (tile == null) return net.minecraft.item.ItemStack.EMPTY;
        NBTTagCompound existing = input.getSubCompound("BlockEntityTag");
        if (existing != null) tile.readFromNBT(existing.copy());
        if (tile instanceof TileEntitySpaceDoor && input.getSubCompound("SpaceDoorSettings") != null)
            ((TileEntitySpaceDoor)tile).applyItemSettings(input.getSubCompound("SpaceDoorSettings"));
        if (tile instanceof TileEntityProgrammableGlass && input.getSubCompound("ProgrammableGlassSettings") != null) {
            NBTTagCompound glass = input.getSubCompound("ProgrammableGlassSettings");
            ((TileEntityProgrammableGlass)tile).setSize(glass.getInteger("Size"));
            ((TileEntityProgrammableGlass)tile).setShade(glass.getInteger("Shade"));
            ((TileEntityProgrammableGlass)tile).setJoin(!glass.hasKey("Join") || glass.getBoolean("Join"));
        }
        boolean applied = applyToTile(null, BlockPos.ORIGIN, values, null, tile, block);
        if (!applied && !(tile instanceof TileEntityRampController)) return net.minecraft.item.ItemStack.EMPTY;
        net.minecraft.item.ItemStack output = input.copy(); output.setCount(1);
        NBTTagCompound data = tile.writeToNBT(new NBTTagCompound());
        data.removeTag("x"); data.removeTag("y"); data.removeTag("z"); data.removeTag("id");
        if (tile instanceof com.vandorlabs.tiles.TileEntityProgrammableStorage) data.removeTag("Items");
        output.setTagInfo("BlockEntityTag", data);
        if (tile instanceof TileEntitySpaceDoor) output.setTagInfo("SpaceDoorSettings", ((TileEntitySpaceDoor)tile).itemSettings());
        if (tile instanceof TileEntityProgrammableGlass) {
            TileEntityProgrammableGlass glass = (TileEntityProgrammableGlass)tile;
            NBTTagCompound settings = new NBTTagCompound();
            settings.setInteger("Size", glass.getSize());settings.setInteger("Shade",glass.getShade());settings.setBoolean("Join",glass.isJoin());
            output.setTagInfo("ProgrammableGlassSettings",settings);
        }
        if (tile instanceof TileEntityRampController) output.setTagInfo("CopiedRampSettings", values.copy());
        return output;
    }

    /** Apply only settings supported by the target; placement orientation is untouched. */
    public static boolean apply(World world, BlockPos pos, NBTTagCompound values) {
        return apply(world, pos, values, null);
    }

    public static boolean apply(World world, BlockPos pos, NBTTagCompound values,
            EntityPlayer player) {
        TileEntity tile = world.getTileEntity(pos);
        Block block = world.getBlockState(pos).getBlock();
        return applyToTile(world, pos, values, player, tile, block);
    }

    private static boolean applyToTile(World world, BlockPos pos, NBTTagCompound values,
            EntityPlayer player, TileEntity tile, Block block) {
        if (tile == null || values == null || values.hasNoTags()) return false;
        boolean applicable = false;
        if (tile instanceof com.vandorlabs.tiles.TileEntityProgrammableTrapdoor) {
            com.vandorlabs.tiles.TileEntityProgrammableTrapdoor hatch=(com.vandorlabs.tiles.TileEntityProgrammableTrapdoor)tile;
            boolean diagonal=hatch instanceof com.vandorlabs.tiles.TileEntityProgrammableDiagonalTrapdoor;
            applicable=values.hasKey(TRAPDOOR_COVER_FACING,3) || values.hasKey(TRAPDOOR_TILE_TEXTURE,1) || values.hasKey(TRAPDOOR_COVER,1) || values.hasKey(WALL_TEXTURE,3) || (diagonal?values.hasKey(DIAGONAL_GEOMETRY,10):values.hasKey(TRAPDOOR_POSITION,3))
                    || values.hasKey(DOOR_SLIDING,1) || values.hasKey(TRIGGER,3) || values.hasKey(CHANNEL);
            if(applicable) {
                java.util.List<com.vandorlabs.tiles.TileEntityProgrammableTrapdoor> leaves=world==null
                        ?java.util.Collections.singletonList(hatch):hatch.group();
                if(player!=null)for(com.vandorlabs.tiles.TileEntityProgrammableTrapdoor leaf:leaves)
                    if(!player.canPlayerEdit(leaf.getPos(),EnumFacing.UP,player.getHeldItemMainhand())
                            || !world.isBlockModifiable(player,leaf.getPos()))return false;
                com.vandorlabs.tiles.TileEntityProgrammableTrapdoor.configureGroup(leaves,()->{
                for(com.vandorlabs.tiles.TileEntityProgrammableTrapdoor leaf:leaves){
                    if(values.hasKey(TRAPDOOR_TILE_TEXTURE,1))leaf.setTileTexture(values.getBoolean(TRAPDOOR_TILE_TEXTURE));
                    if(values.hasKey(TRAPDOOR_COVER,1))leaf.setCover(values.getBoolean(TRAPDOOR_COVER));
                    if(values.hasKey(TRAPDOOR_COVER_FACING,3))leaf.setHingeFacing(EnumFacing.getHorizontal(values.getInteger(TRAPDOOR_COVER_FACING)));
                    if(!diagonal && values.hasKey(DOOR_SLIDING,1) && values.hasKey(TRAPDOOR_SLIDE_OVER_SURFACE,1))leaf.setSlideOverSurface(values.getBoolean(TRAPDOOR_SLIDE_OVER_SURFACE));
                    if(diagonal && values.hasKey(DOOR_SLIDING,1) && values.hasKey(TRAPDOOR_SLIDE_INTO_WALL,1))leaf.setSlideIntoWall(values.getBoolean(TRAPDOOR_SLIDE_INTO_WALL));
                    leaf.configure(
                        number(values,WALL_TEXTURE,leaf.getHousingTexture()),diagonal?(values.hasKey(DIAGONAL_GEOMETRY,10)?Math.max(0,Math.min(2,values.getCompoundTag(DIAGONAL_GEOMETRY).getInteger("mode"))):leaf.getPosition()):number(values,TRAPDOOR_POSITION,leaf.getPosition()),
                        flag(values,DOOR_SLIDING,leaf.isSliding()),number(values,TRIGGER,leaf.getTrigger()),
                        channels(values,leaf));
                }
                });
            }
        } else if (tile instanceof TileEntityRedstoneLight && block instanceof BlockPropulsionLight) {
            BlockPropulsionLight fixture = (BlockPropulsionLight) block;
            if (world != null && values.hasKey(PROPULSION_SHAPE, 3) && !fixture.familyId().isEmpty()) {
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
            if (values.hasKey(ACTIVE, 1) || values.hasKey(PARTICLES, 1) || values.hasKey(PARTICLES,3)) {
                ChannelList channel = propulsion.getRedstoneChannels();
                propulsion.setRedstoneChannel(0);
                boolean active = flag(values, ACTIVE, propulsion.getManualMode() != 0);
                int particles=values.hasKey(PARTICLES,3)?Math.max(0,Math.min(3,values.getInteger(PARTICLES))):values.hasKey(PARTICLES,1)?values.getBoolean(PARTICLES)?1:0:propulsion.getParticleLevel();
                propulsion.setManualMode(active?particles>0?2:1:0,true);
                propulsion.setParticleLevel(particles);
                propulsion.setRedstoneChannels(channel);
                applicable = true;
            }
        } else if (tile instanceof TileEntityProgrammableLight) {
            TileEntityProgrammableLight light = (TileEntityProgrammableLight) tile;
            int trigger = number(values, TRIGGER, light.getTrigger());
            if (!SpaceDoorData.validTrigger(trigger)) trigger = light.getTrigger();
            if(values.hasKey(LIGHT_FACE_TEXTURE,3))light.setFaceTexture(values.getInteger(LIGHT_FACE_TEXTURE));
            int primary = light.getTexture();
            if ("light".equals(values.getString(PRIMARY_KIND))
                    && values.hasKey(PRIMARY_TEXTURE, 3))
                primary = values.getInteger(PRIMARY_TEXTURE);
            if(values.hasKey(SMALL_INPUT,1))light.setSmallInput(values.getBoolean(SMALL_INPUT));
            if(values.hasKey(SLAB_TILE_SIDES,1))light.setSlabTileSides(values.getBoolean(SLAB_TILE_SIDES));
            light.configure(primary, number(values, LIGHT_LEVEL, light.getLightLevel()),
                    flag(values, JOIN, light.isJoin()),
                    channels(values,light),
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
                        channels(values,trigger));
                applicable = true;
            }
        } else if (tile instanceof TileEntityAnimatedScreenSelector) {
            TileEntityAnimatedScreenSelector screen = (TileEntityAnimatedScreenSelector) tile;
            long before=screen.getSettingsRevision();
            if (values.hasKey(WALL_TEXTURE, 3)) {
                screen.setHousingTexture(values.getInteger(WALL_TEXTURE));
                applicable = true;
            }
            if(TileEntityAnimatedScreenSelector.supportsSideTexture(block) && values.hasKey(SIDE_TEXTURE,3)){screen.setSideTexture(values.getInteger(SIDE_TEXTURE));applicable=true;}
            if ((block == ModBlocks.PROGRAMMABLE_STORAGE || block == ModBlocks.PROGRAMMABLE_BLOCK || block == ModBlocks.PROGRAMMABLE_SLAB || block == ModBlocks.PROGRAMMABLE_STAIRS)
                    && values.hasKey(FACE_TEXTURES, 10)) {
                NBTTagCompound faces = values.getCompoundTag(FACE_TEXTURES);
                boolean enabled = faces.getBoolean("enabled");
                screen.setFaceTextures(new com.vandorlabs.tiles.FaceTextures(enabled,
                        enabled ? faces.getIntArray("choices") : screen.getFaceTextures().choices()));
                applicable = true;
            }
            if (isDisplay(block)) {
                if(values.hasKey(PRIMARY_SURFACE,3)){screen.setSurfaceTexture(0,values.getInteger(PRIMARY_SURFACE));applicable=true;}
                if(values.hasKey(SECONDARY_SURFACE,3)){screen.setSurfaceTexture(1,values.getInteger(SECONDARY_SURFACE));applicable=true;}
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
            if ((block instanceof BlockProgrammableSlab || block instanceof com.vandorlabs.blocks.BlockProgrammableStairs || isDisplay(block)) && values.hasKey(SLAB_TILE_SIDES, 1)) {
                if(isDisplay(block))screen.setSurfaceTileSides(values.getBoolean(SLAB_TILE_SIDES));else screen.setSlabTileSides(values.getBoolean(SLAB_TILE_SIDES)); applicable = true;
            }
            if (block instanceof BlockProgrammableWall
                    && ((BlockProgrammableWall) block).isDiagonalShape()
                    && values.hasKey(DIAGONAL_FULL_WIDTH, 1) && !values.hasKey(DIAGONAL_GEOMETRY,10)) {
                screen.setDiagonalFullWidth(values.getBoolean(DIAGONAL_FULL_WIDTH));
                applicable = true;
            }
            if (block instanceof BlockProgrammableWall && ((BlockProgrammableWall)block).isDiagonalShape()
                    && values.hasKey(DIAGONAL_GEOMETRY,10)) {
                NBTTagCompound geometry=values.getCompoundTag(DIAGONAL_GEOMETRY);
                int mode=Math.max(0,Math.min(2,geometry.getInteger("mode")));
                screen.setDiagonalGeometry(mode,
                        isPorthole(block)?0:geometry.getInteger("fill"));
                applicable=true;
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
            if (applicable && world != null && screen.getSettingsRevision()!=before) {
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
            if(values.hasKey(DOOR_TILE_TEXTURE,1)){door.setTileTexture(values.getBoolean(DOOR_TILE_TEXTURE));applicable=true;}
            if(values.hasKey(DOOR_FACE_TEXTURE,3)){door.setFaceTexture(values.getInteger(DOOR_FACE_TEXTURE));applicable=true;}
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
            if (chair instanceof com.vandorlabs.tiles.TileEntityConnectedSeat && values.hasKey(JOIN,1)) {
                ((com.vandorlabs.tiles.TileEntityConnectedSeat)chair).setJoin(values.getBoolean(JOIN));applicable=true;
            }
            if (!(chair instanceof com.vandorlabs.tiles.TileEntityConnectedSeat) && values.hasKey(CHAIR_STYLE, 3)) {
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
        } else if (tile instanceof com.vandorlabs.tiles.TileEntityLandingGear) {
            com.vandorlabs.tiles.TileEntityLandingGear gear=(com.vandorlabs.tiles.TileEntityLandingGear)tile;
            if(values.hasKey(GEAR_SIZE)||values.hasKey(GEAR_LENGTH)||values.hasKey(GEAR_MODE)) {
                int size=number(values,GEAR_SIZE,gear.getSize()),length=number(values,GEAR_LENGTH,gear.getExtensionPixels()),mode=number(values,GEAR_MODE,gear.getMode());
                if(world!=null) applicable=gear.configure(mode,channels(values,gear),length,size);
                else if(size>=0&&size<=2&&length>=0&&length<=64&&length%8==0&&mode>=0&&mode<=2) {
                    NBTTagCompound tag=gear.writeToNBT(new NBTTagCompound());
                    tag.setInteger("GearSize",size);tag.setInteger("ExtensionPixels",length);tag.setInteger("RedstoneMode",mode);
                    gear.readFromNBT(tag);applicable=true;
                }
            }
        } else if (tile instanceof TileEntityRampController && player != null) {
            TileEntityRampController ramp = (TileEntityRampController) tile;
            if (values.hasKey(RAMP_START) || values.hasKey(RAMP_END)
                    || values.hasKey(RAMP_START_HALF) || values.hasKey(RAMP_END_HALF)
                    || values.hasKey(RAMP_INTERPOLATION) || values.hasKey(RAMP_TREAD_PIXELS) || values.hasKey(RAMP_SPEED)
                    || values.hasKey(RAMP_LIFT) || values.hasKey(RAMP_EXTEND)
                    || values.hasKey(RAMP_DIRECTION) || values.hasKey(RAMP_TRAVEL) || values.hasKey(RAMP_MATCH_TEXTURES)
                    || (values.hasKey(TRIGGER, 3)
                    && (values.getInteger(TRIGGER) == SpaceDoorData.TRIGGER_REDSTONE_ON
                    || values.getInteger(TRIGGER) == SpaceDoorData.TRIGGER_REDSTONE_OFF))) {
                EnumFacing direction = EnumFacing.getFront(number(values,
                        RAMP_DIRECTION, ramp.rampDirection().getIndex()));
                int trigger = number(values, TRIGGER, ramp.activateOnPower
                        ? SpaceDoorData.TRIGGER_REDSTONE_ON : SpaceDoorData.TRIGGER_REDSTONE_OFF);
                applicable = ramp.configureHalfOffsets(player,
                        number(values, RAMP_START_HALF,
                                number(values, RAMP_START, ramp.startOffset)*2),
                        number(values, RAMP_END_HALF,
                                number(values, RAMP_END, ramp.endOffset())*2),
                        number(values, RAMP_TREAD_PIXELS, ramp.treadPixels),
                        trigger == SpaceDoorData.TRIGGER_REDSTONE_ON,
                        number(values, RAMP_SPEED, ramp.speed) == 2,
                        flag(values, RAMP_LIFT, ramp.elevator), direction,
                        number(values, RAMP_TRAVEL, ramp.travelAxis),
                        flag(values, RAMP_EXTEND, ramp.extendSegments),
                        number(values, RAMP_SPEED, ramp.speed),
                        flag(values, RAMP_MATCH_TEXTURES, ramp.matchTextures),
                        player.getHeldItemMainhand().getItem() == ModItems.DUPLIFIER
                                && DuplifierApplyOptions.connected(player.getHeldItemMainhand()),
                        number(values,RAMP_INTERPOLATION,ramp.interpolation));
            }
        }
        if(tile instanceof TileEntityAnimatedScreenSelector && com.vandorlabs.blocks.RedstoneScreenInteractions.supports(block) && values.hasKey(REDSTONE_ROWS,10))
            applicable|=((TileEntityAnimatedScreenSelector)tile).applyRedstoneConfiguration(values.getCompoundTag(REDSTONE_ROWS));
        if (tile instanceof RedstoneChannelMember && values.hasKey(CHANNEL)) {
            ChannelList before=((RedstoneChannelMember)tile).getRedstoneChannels();
            ((RedstoneChannelMember) tile).setRedstoneChannels(channels(values,(RedstoneChannelMember)tile));
            if (world != null && tile instanceof TileEntityAnimatedScreenSelector
                    && !before.equals(((RedstoneChannelMember)tile).getRedstoneChannels())) {
                net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
                world.notifyBlockUpdate(pos, state, state, 3);
            }
            applicable = true;
        }
        return applicable;
    }
}
