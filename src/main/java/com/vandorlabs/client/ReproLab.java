package com.vandorlabs.client;

import com.vandorlabs.VandorLabs;
import com.vandorlabs.blocks.BlockAnimatedScreenSelector;
import com.vandorlabs.blocks.BlockVandorDirectional;
import com.vandorlabs.blocks.BlockVandorDoor;
import com.vandorlabs.blocks.BlockBridgeChair;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Headless rendering lab for Vandor Labs (enabled ONLY by the system property
 * {@code vandorlabs.reprolab}, set by testclient/run.sh).
 * <p>
 * Boots an integrated superflat world, builds a floating stone platform,
 * places all programmable-display shapes, an {@code engineering_screen}
 * control, and an animated door at known coordinates; runs state contracts;
 * captures deterministic views; writes a manifest; and shuts down. The
 * pixel-analysis script verifies housing, every animation/input option,
 * six-way wedge placement, and intermediate door motion.
 */
@SideOnly(Side.CLIENT)
public class ReproLab {

    /** Platform sits one block below the test blocks. */
    private static final int Y = 21;
    private static final BlockPos PLATFORM0 = new BlockPos(-10, Y - 1, -10);
    private static final BlockPos PLATFORM1 = new BlockPos(10, Y - 1, 10);

    private static final BlockPos SELECTOR = new BlockPos(0, Y, 0);
    private static final BlockPos CONTROL = new BlockPos(5, Y, 0);
    private static final BlockPos CONSOLE = new BlockPos(-5, Y, 0);
    private static final BlockPos DIAGONAL = new BlockPos(0, Y, 5);
    private static final BlockPos DIAGONAL_UP = new BlockPos(-5, Y, 5);
    private static final BlockPos DIAGONAL_DOWN = new BlockPos(5, Y, 5);
    private static final BlockPos WALL_DISPLAY = new BlockPos(-2, Y + 10, 2);
    private static final BlockPos WIDE_LEFT = new BlockPos(-9, Y, 7);
    private static final BlockPos WIDE_RIGHT = new BlockPos(-8, Y, 7);
    private static final BlockPos INPUT_WALL = new BlockPos(-8, Y, 2);
    private static final BlockPos INPUT_KEYBOARD = new BlockPos(-6, Y, 2);
    private static final BlockPos HALF_CONSOLE = new BlockPos(8, Y, 2);
    private static final BlockPos CRUISER_GRID = new BlockPos(2, Y + 2, 8);
    private static final BlockPos FULL_INPUT_WALL = new BlockPos(3, Y, 2);
    private static final BlockPos FULL_INPUT_FLOOR = new BlockPos(7, Y, 2);
    private static final BlockPos CHAIR_COMMAND = new BlockPos(-3, Y + 4, 4);
    private static final BlockPos CHAIR_COMPANION = new BlockPos(0, Y + 4, 4);
    private static final BlockPos CHAIR_OPERATOR = new BlockPos(3, Y + 4, 4);
    private static final BlockPos CHAIR_CONFERENCE = new BlockPos(-6, Y + 4, 4);
    private static final BlockPos CHAIR_MESS_HALL = new BlockPos(6, Y + 4, 4);
    private static final BlockPos MATERIAL_GRID = new BlockPos(-8, Y + 6, 8);
    private static final BlockPos CEILING_THRUSTER = new BlockPos(10, Y + 8, 17);
    private static final BlockPos FLOOR_THRUSTER = new BlockPos(12, Y + 8, 8);
    private static final BlockPos WALL_THRUSTER = new BlockPos(12, Y + 8, 5);

    private static final float SIDE_DIST = 3.0F;
    private static final int CAPTURE_SETTLE_TICKS = 8;
    private static final int GALLERY_X = 160;
    private static final int GALLERY_Y = 4;
    private static final int GUI_SETTLE_TICKS = 12;

    private static final class Shot {
        final String name;
        final double x, y, z;
        final float yaw, pitch;
        final int animationFrame;
        final String inputPanel;

        Shot(String name, double x, double y, double z, float yaw, float pitch) {
            this(name, x, y, z, yaw, pitch, -1, null);
        }

        Shot(String name, double x, double y, double z, float yaw, float pitch,
                int animationFrame) {
            this(name, x, y, z, yaw, pitch, animationFrame, null);
        }

        Shot(String name, double x, double y, double z, float yaw, float pitch,
                String inputPanel) {
            this(name, x, y, z, yaw, pitch, -1, inputPanel);
        }

        private Shot(String name, double x, double y, double z, float yaw, float pitch,
                int animationFrame, String inputPanel) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.animationFrame = animationFrame;
            this.inputPanel = inputPanel;
        }

        String cam() {
            return x + "\t" + y + "\t" + z + "\t" + yaw + "\t" + pitch;
        }
    }

    /** World origin for view naming. */
    private static final double BCY = Y + 0.5D;

    private static final List<Shot> SHOTS = new ArrayList<>();

    static {
        // Selector: front (north face), west side, top, NW diagonal, back.
        // Shot Y is player-feet Y.  Subtract the normal 1.62-block eye
        // height so a zero-pitch view is centered on the block.
        double eyeLevelFeet = BCY - 1.62D;
        for (int group=0;group<4;group++) {
            for (String pose : new String[]{"closed","opening_mid","open"})
                SHOTS.add(new Shot("controller_"+group+"_"+pose,36+group*8,28,10,-135,30));
        }
        SHOTS.add(new Shot("selector_front", SELECTOR.getX() + 0.5D, eyeLevelFeet,
                SELECTOR.getZ() + 0.5D - SIDE_DIST, 0.0F, 0.0F));
        for (int frame = 0; frame < 6; frame++) {
            SHOTS.add(new Shot("selector_anim_" + frame, SELECTOR.getX() + 0.5D,
                    eyeLevelFeet, SELECTOR.getZ() + 0.5D - SIDE_DIST,
                    0.0F, 0.0F, frame));
        }
        SHOTS.add(new Shot("selector_west", SELECTOR.getX() + 0.5D - SIDE_DIST, eyeLevelFeet,
                SELECTOR.getZ() + 0.5D, 270.0F, 0.0F));
        SHOTS.add(new Shot("selector_top", SELECTOR.getX() + 0.5D, BCY + 3.0D,
                SELECTOR.getZ() + 0.5D, 0.0F, 90.0F));
        SHOTS.add(new Shot("selector_diag", SELECTOR.getX() + 0.5D - SIDE_DIST, eyeLevelFeet,
                SELECTOR.getZ() + 0.5D - SIDE_DIST, 315.0F, 0.0F));
        SHOTS.add(new Shot("selector_back", SELECTOR.getX() + 0.5D, eyeLevelFeet,
                SELECTOR.getZ() + 0.5D + SIDE_DIST, 180.0F, 0.0F));
        // Control block (engineering screen) equivalent views.
        SHOTS.add(new Shot("control_front", CONTROL.getX() + 0.5D, eyeLevelFeet,
                CONTROL.getZ() + 0.5D - SIDE_DIST, 0.0F, 0.0F));
        SHOTS.add(new Shot("control_west", CONTROL.getX() + 0.5D - SIDE_DIST, eyeLevelFeet,
                CONTROL.getZ() + 0.5D, 270.0F, 0.0F));
        SHOTS.add(new Shot("control_top", CONTROL.getX() + 0.5D, BCY + 3.0D,
                CONTROL.getZ() + 0.5D, 0.0F, 90.0F));
        SHOTS.add(new Shot("control_back", CONTROL.getX() + 0.5D, eyeLevelFeet,
                CONTROL.getZ() + 0.5D + SIDE_DIST, 180.0F, 0.0F));
        double consoleFeet = Y + 1.2D - 1.62D;
        SHOTS.add(new Shot("console_front", CONSOLE.getX() + 0.5D, consoleFeet,
                CONSOLE.getZ() + 0.5D - SIDE_DIST, 0.0F, 12.0F));
        for (String input : TileEntityAnimatedScreenSelector.INPUT_PANELS) {
            SHOTS.add(new Shot("console_input_" + input, CONSOLE.getX() + 0.5D,
                    consoleFeet, CONSOLE.getZ() + 0.5D - SIDE_DIST,
                    0.0F, 12.0F, input));
        }
        SHOTS.add(new Shot("console_diag", CONSOLE.getX() + 0.5D - SIDE_DIST,
                consoleFeet, CONSOLE.getZ() + 0.5D - SIDE_DIST, 315.0F, 12.0F));
        SHOTS.add(new Shot("console_top", CONSOLE.getX() + 0.5D, BCY + 3.0D,
                CONSOLE.getZ() + 0.5D, 0.0F, 90.0F));
        SHOTS.add(new Shot("diagonal_front", DIAGONAL.getX() + 0.5D, eyeLevelFeet,
                DIAGONAL.getZ() + 0.5D - SIDE_DIST, 0.0F, 0.0F));
        SHOTS.add(new Shot("diagonal_side", DIAGONAL.getX() + 0.5D - SIDE_DIST,
                eyeLevelFeet, DIAGONAL.getZ() + 0.5D, 270.0F, 0.0F));
        SHOTS.add(new Shot("diagonal_up", DIAGONAL_UP.getX() + 0.5D, eyeLevelFeet,
                DIAGONAL_UP.getZ() + 0.5D - SIDE_DIST, 0.0F, 0.0F));
        SHOTS.add(new Shot("diagonal_down", DIAGONAL_DOWN.getX() + 0.5D, eyeLevelFeet,
                DIAGONAL_DOWN.getZ() + 0.5D - SIDE_DIST, 0.0F, 0.0F));
        SHOTS.add(new Shot("diagonal_down_oblique", DIAGONAL_DOWN.getX() + 1.5D, eyeLevelFeet,
                DIAGONAL_DOWN.getZ() + 0.5D - SIDE_DIST, 20.0F, 0.0F));
        SHOTS.add(new Shot("diagonal_down_side", DIAGONAL_DOWN.getX() + 0.5D - SIDE_DIST,
                eyeLevelFeet, DIAGONAL_DOWN.getZ() + 0.5D, 270.0F, 0.0F));
        SHOTS.add(new Shot("diagonal_down_close_low", DIAGONAL_DOWN.getX() + 0.5D,
                Y + .45D - 1.62D, DIAGONAL_DOWN.getZ() - 0.65D, 0.0F, 0.0F));
        SHOTS.add(new Shot("diagonal_down_close_high", DIAGONAL_DOWN.getX() + 0.5D,
                Y + .65D - 1.62D, DIAGONAL_DOWN.getZ() - 0.65D, 0.0F, 5.0F));
        SHOTS.add(new Shot("programmable_walls", 0.5D,
                Y + 10.6D - 1.62D, -2.0D, 0.0F, 2.0F));
        SHOTS.add(new Shot("programmable_wall_side", -4.5D,
                Y + 10.7D - 1.62D, -0.5D, -45.0F, 3.0F));
        SHOTS.add(new Shot("programmable_porthole", -0.5D,
                Y + 10.5D - 1.62D, 0.2D, 18.0F, 0.0F));
        SHOTS.add(new Shot("programmable_porthole_joined", -0.5D,
                Y + 10.5D - 1.62D, -0.9D, 0.0F, 0.0F));
        SHOTS.add(new Shot("programmable_porthole_vertical", WALL_DISPLAY.getX() + 13.5D,
                Y + 10.95D - 1.62D, -0.9D, 0.0F, 0.0F));
        SHOTS.add(new Shot("programmable_porthole_single", WALL_DISPLAY.getX() + 14.5D,
                Y + 10.5D - 1.62D, -0.9D, 0.0F, 0.0F));
        SHOTS.add(new Shot("programmable_porthole_incomplete", WALL_DISPLAY.getX() + 23D,
                Y + 11D - 1.62D, -1.2D, 0.0F, 0.0F));
        SHOTS.add(new Shot("programmable_porthole_large_rim", WALL_DISPLAY.getX() + 28.5D,
                Y + 11.5D - 1.62D, -1.5D, 15.0F, 0.0F));
        SHOTS.add(new Shot("programmable_corner", WALL_DISPLAY.getX() + 6.5D,
                Y + 10.5D - 1.62D, -0.7D, 0.0F, 0.0F));
        SHOTS.add(new Shot("programmable_corner_side", WALL_DISPLAY.getX() + 5.1D,
                Y + 10.6D - 1.62D, -0.4D, -35.0F, 4.0F));
        SHOTS.add(new Shot("programmable_corner_turn", WALL_DISPLAY.getX() + 12.4D,
                Y + 11.1D - 1.62D, -0.1D, 42.0F, 20.0F));
        SHOTS.add(new Shot("programmable_corner_three_back", WALL_DISPLAY.getX() + 10.5D,
                Y + 10.7D - 1.62D, 5.0D, 180.0F, 4.0F));
        SHOTS.add(new Shot("programmable_corner_three_top", WALL_DISPLAY.getX() + 18.0D,
                Y + 13.5D - 1.62D, WALL_DISPLAY.getZ() + 1.0D, 0.0F, 90.0F));
        SHOTS.add(new Shot("programmable_corner_t_top", WALL_DISPLAY.getX() + 33.5D,
                Y + 14D - 1.62D, WALL_DISPLAY.getZ() + 1D, 0.0F, 90.0F));
        SHOTS.add(new Shot("programmable_corner_pair_top", WALL_DISPLAY.getX() + 38.5D,
                Y + 13.5D - 1.62D, WALL_DISPLAY.getZ() + 1D, 0.0F, 90.0F));
        SHOTS.add(new Shot("programmable_depth_top", WALL_DISPLAY.getX() + 45.5D,
                Y + 14D - 1.62D, WALL_DISPLAY.getZ() + 1.5D, 0.0F, 90.0F));
        SHOTS.add(new Shot("programmable_flat_corners_top", WALL_DISPLAY.getX() + 55D,
                Y + 14D - 1.62D, WALL_DISPLAY.getZ() + 0.5D, 0.0F, 90.0F));
        SHOTS.add(new Shot("programmable_flat_three_top", WALL_DISPLAY.getX() + 110.5D,
                Y + 14D - 1.62D, WALL_DISPLAY.getZ() + 0.5D, 0.0F, 90.0F));
        SHOTS.add(new Shot("programmable_flat_four_top", WALL_DISPLAY.getX() + 116.5D,
                Y + 14D - 1.62D, WALL_DISPLAY.getZ() + 0.5D, 0.0F, 90.0F));
        SHOTS.add(new Shot("programmable_flat_edge_top", WALL_DISPLAY.getX() + 122.5D,
                Y + 14D - 1.62D, WALL_DISPLAY.getZ() + 0.5D, 0.0F, 90.0F));
        SHOTS.add(new Shot("wide_ship_pair", -8.0D, eyeLevelFeet,
                4.0D, 0.0F, 0.0F));
        SHOTS.add(new Shot("input_wall", INPUT_WALL.getX() + 0.5D,
                Y + 0.75D - 1.62D, INPUT_WALL.getZ() - 2.5D, 0.0F, 0.0F));
        SHOTS.add(new Shot("input_keyboard", INPUT_KEYBOARD.getX() + 0.5D,
                Y + 1.15D - 1.62D, INPUT_KEYBOARD.getZ() - 2.5D, 0.0F, 14.0F));
        SHOTS.add(new Shot("input_surface_off", INPUT_KEYBOARD.getX() + 0.5D,
                Y + 1.15D - 1.62D, INPUT_KEYBOARD.getZ() - 2.5D, 0.0F, 14.0F));
        SHOTS.add(new Shot("input_surface_static", INPUT_KEYBOARD.getX() + 0.5D,
                Y + 1.15D - 1.62D, INPUT_KEYBOARD.getZ() - 2.5D, 0.0F, 14.0F));
        for (int frame = 0; frame < 6; frame++) {
            SHOTS.add(new Shot("input_surface_anim_" + frame,
                    INPUT_KEYBOARD.getX() + 0.5D, Y + 1.15D - 1.62D,
                    INPUT_KEYBOARD.getZ() - 2.5D, 0.0F, 14.0F, frame));
        }
        SHOTS.add(new Shot("half_console", HALF_CONSOLE.getX() + 0.5D,
                Y + 0.8D - 1.62D, HALF_CONSOLE.getZ() - 2.5D, 0.0F, 10.0F));
        SHOTS.add(new Shot("cruiser_grid", CRUISER_GRID.getX() + 1.5D,
                CRUISER_GRID.getY() + 1.0D - 1.62D,
                CRUISER_GRID.getZ() - 3.5D, 0.0F, 0.0F));
        SHOTS.add(new Shot("full_input_wall", FULL_INPUT_WALL.getX() + 0.5D,
                eyeLevelFeet, FULL_INPUT_WALL.getZ() - 2.5D, 0.0F, 0.0F));
        SHOTS.add(new Shot("full_input_floor", FULL_INPUT_FLOOR.getX() + 0.5D,
                Y + 1.25D - 1.62D, FULL_INPUT_FLOOR.getZ() - 2.5D,
                0.0F, 18.0F));
        SHOTS.add(new Shot("bridge_chairs", 0.0D, Y + 4.15D,
                -3.0D, 0.0F, 0.0F));
        SHOTS.add(new Shot("material_grid", -6.5D, Y + 5.9D,
                3.5D, 0.0F, 0.0F));
        SHOTS.add(new Shot("thruster_ceiling", 13.0D, Y - 3.0D,
                15.0D, 0.0F, -90.0F));
        SHOTS.add(new Shot("thruster_floor", 13.0D, Y + 13.0D,
                9.0D, 0.0F, 90.0F));
        SHOTS.add(new Shot("thruster_wall", 13.0D, Y + 7.38D,
                1.0D, 0.0F, 0.0F));
        double galleryFeet = GALLERY_Y + 1.0D - 1.62D;
        SHOTS.add(new Shot("gallery_programmable_displays", GALLERY_X,
                galleryFeet + 0.7D, -25.0D, 0.0F, 4.0F));
        SHOTS.add(new Shot("gallery_programmable_inputs", GALLERY_X,
                galleryFeet + 0.7D, -24.0D, 0.0F, 5.0F));
        SHOTS.add(new Shot("gallery_programmable_full_inputs", GALLERY_X,
                galleryFeet + 0.7D, -23.0D, 0.0F, 7.0F));
        SHOTS.add(new Shot("gallery_display_table", GALLERY_X,
                galleryFeet + 0.5D, -24.0D, 0.0F, 8.0F));
        SHOTS.add(new Shot("gallery_propulsion", GALLERY_X, galleryFeet + 2.0D,
                -32.0D, 0.0F, 5.0F));
        SHOTS.add(new Shot("gallery_connected_thruster", GALLERY_X,
                galleryFeet + 1.0D, -26.0D, 0.0F, 0.0F));
        SHOTS.add(new Shot("gallery_lighting_controls", GALLERY_X,
                galleryFeet + 1.5D, -28.0D, 0.0F, 4.0F));
        SHOTS.add(new Shot("gallery_joined_lights", GALLERY_X,
                galleryFeet + 1.5D, -28.0D, 0.0F, 4.0F));
        SHOTS.add(new Shot("gallery_programmable_slabs", GALLERY_X,
                galleryFeet + 0.5D, -27.0D, 0.0F, 5.0F));
        SHOTS.add(new Shot("gallery_structure", GALLERY_X, galleryFeet + 2.0D,
                -30.0D, 0.0F, 4.0F));
        for (String motion : new String[]{"sliding", "rotating"}) {
            for (String trim : new String[]{"bare", "framed"}) {
                for (String pose : new String[]{"closed", "open"}) {
                    SHOTS.add(new Shot("gallery_space_" + motion + "_" + trim + "_" + pose,
                            GALLERY_X, galleryFeet, -31.0D, 0, 0));
                }
            }
        }
        SHOTS.add(new Shot("gallery_space_glass", GALLERY_X, galleryFeet, -23.0D, 0, 0));
        SHOTS.add(new Shot("gallery_round_portholes", GALLERY_X, galleryFeet + 2, -27, 0, 0));
        for (String hinges:new String[]{"on","off"}) SHOTS.add(new Shot("gallery_space_config_hinges_"+hinges,
                GALLERY_X-2.5D,galleryFeet+.4D,-21,-36,5));
        SHOTS.add(new Shot("gallery_space_panel_sliding_edge",GALLERY_X-2.0D,
                galleryFeet+.3D,-20.5D,-32,4));
        SHOTS.add(new Shot("gallery_space_panel_rotating_edge",GALLERY_X+2.0D,
                galleryFeet+.3D,-20.5D,32,4));
        for (String depth : new String[]{"middle","far"}) {
            SHOTS.add(new Shot("gallery_space_panel_sliding_"+depth,GALLERY_X-2.0D,
                    galleryFeet+.3D,-20.5D,-32,4));
            SHOTS.add(new Shot("gallery_space_panel_rotating_"+depth,GALLERY_X+2.0D,
                    galleryFeet+.3D,-20.5D,32,4));
        }
        // Stand inside the opening and inspect both jambs obliquely from both
        // depth edges. Missing step shoulders expose the magenta backing wall.
        for (String side : new String[]{"east","west"}) for (int edge : new int[]{-1,1}) {
            float yaw=(float)(90+edge*Math.toDegrees(Math.atan(.1/.4375)));
            SHOTS.add(new Shot("gallery_space_jamb_"+side+"_"+(edge<0?"front":"back"),
                    GALLERY_X+.5,galleryFeet,-18+(16-13.24)/16+edge*.1,
                    side.equals("east")?-yaw:yaw,0));
        }
        for (String pose : new String[]{"closed","open"}) {
            SHOTS.add(new Shot("gallery_space_hinges_"+pose,
                    GALLERY_X-2.5D,galleryFeet+.4D,-21,-36,5));
        }
        SHOTS.add(new Shot("gallery_chairs", GALLERY_X, galleryFeet + 0.4D,
                -27.0D, 0.0F, 4.0F));
        for (String direction : new String[] {"up", "down"}) {
            for (String tread : new String[] {"smooth", "stairs"}) {
                boolean down = direction.equals("down");
                SHOTS.add(new Shot("gallery_ramp_" + direction + "_" + tread,
                        GALLERY_X + 5.0D, galleryFeet + 7.0D,
                        down ? -10.0D : -29.0D, down ? 145.0F : 45.0F,
                        down ? 40.0F : 35.0F));
            }
        }
    }

    private final File outDir;
    private final boolean enabled;

    private int state = 0; // 0 menu, 1 wait, 2 build, 3 shots, 4-7 GUIs, 8 hotbar, 9 done
    private int tick = 0;
    private int holdTicks = 0;
    private int shotIndex = 0;
    private boolean worldSpawned = false;

    public ReproLab() {
        String prop = System.getProperty("vandorlabs.reprolab");
        enabled = prop != null && !prop.isEmpty();
        outDir = enabled ? new File(prop) : null;
        if (enabled) {
            //noinspection ResultOfMethodCallIgnored
            outDir.mkdirs();
        }
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !enabled) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        // Xvfb has no window manager, so the client window never reports
        // focus.  Without this, vanilla reopens the pause menu every tick.
        mc.gameSettings.pauseOnLostFocus = false;
        if (mc.world == null) {
            // Main menu. Wait for the menu to be usable, then create the world.
            tick++;
            if (!worldSpawned && tick > 60 && mc.getIntegratedServer() == null) {
                worldSpawned = true;
                WorldSettings settings = new WorldSettings(12345L,
                        GameType.CREATIVE, false, false, WorldType.FLAT);
                // Every run gets a fresh save. Reusing one after registry ids
                // are added/retired lets Forge remap stale chunk data into
                // current fixtures and made otherwise deterministic renders
                // intermittently fail.
                String saveName = "repro-" + outDir.getName().replaceAll(
                        "[^A-Za-z0-9._-]", "_");
                mc.launchIntegratedServer(saveName, "Vandor Labs render test", settings);
                mc.setIngameFocus();
                System.out.println("[vandorlabs][reprolab] launching integrated world");
            }
            return;
        }

        if (mc.player == null) {
            return;
        }
        // launchIntegratedServer can install its post-load pause screen one
        // tick after the world first becomes non-null.  Keep the lab in the
        // actual game view instead of accidentally photographing that GUI.
        if (state < 4 && mc.currentScreen != null) {
            mc.displayGuiScreen(null);
            mc.setIngameFocus();
        }

        switch (state) {
            case 0:
                // Entered the world.
                state = 1;
                tick = 0;
                mc.displayGuiScreen(null);
                mc.setIngameFocus();
                mc.gameSettings.hideGUI = true;
                mc.gameSettings.thirdPersonView = 0;
                mc.gameSettings.renderDistanceChunks = 6;
                mc.getTutorial().setStep(TutorialSteps.NONE);
                EntityPlayer p = mc.player;
                p.capabilities.isFlying = true;
                System.out.println("[vandorlabs][reprolab] world loaded");
                break;
            case 1:
                // Let chunks generate.
                if (++tick > 80) {
                    build();
                    tick = 0;
                    state = 2;
                    holdTicks = 20;
                }
                break;
            case 2:
                if (--holdTicks > 0) {
                    break;
                }
                beginShot(mc, SHOTS.get(shotIndex), true);
                state = 3;
                holdTicks = CAPTURE_SETTLE_TICKS;
                break;
            case 3:
                if (--holdTicks > 0) {
                    break;
                }
                Shot s = SHOTS.get(shotIndex);
                save(mc, s);
                shotIndex++;
                if (shotIndex < SHOTS.size()) {
                    Shot next = SHOTS.get(shotIndex);
                    beginShot(mc, next, false);
                    holdTicks = next.name.endsWith("opening_mid") ? 4
                            : next.name.startsWith("gallery_") ? 60
                            : CAPTURE_SETTLE_TICKS;
                } else {
                    writeManifest();
                    beginShot(mc, new Shot("gui_return", CONSOLE.getX() + 0.5D,
                            Y + 1.0D - 1.62D, CONSOLE.getZ() - 2.5D,
                            0.0F, -90.0F), false);
                    state = 11;
                    holdTicks = 40;
                }
                break;
            case 11:
                if (--holdTicks > 0) break;
                rebuildGuiFixture(mc, CONSOLE, ModBlocks.PROGRAMMABLE_CONSOLE);
                TileEntity raw = mc.world.getTileEntity(CONSOLE);
                if (!(raw instanceof TileEntityAnimatedScreenSelector)) {
                    throw new IllegalStateException("console tile unavailable for GUI shot");
                }
                mc.gameSettings.hideGUI = false;
                mc.displayGuiScreen(new GuiAnimatedScreenSelector(
                        mc.player.inventory, (TileEntityAnimatedScreenSelector) raw));
                System.out.println("[vandorlabs][reprolab] console selector GUI opened");
                state = 4;
                holdTicks = GUI_SETTLE_TICKS;
                break;
            case 4:
                if (--holdTicks > 0) {
                    break;
                }
                saveNamed(mc, "console_gui");
                rebuildGuiFixture(mc, HALF_CONSOLE, ModBlocks.PROGRAMMABLE_HALF_CONSOLE);
                TileEntity halfRaw = mc.world.getTileEntity(HALF_CONSOLE);
                if (!(halfRaw instanceof TileEntityAnimatedScreenSelector)) {
                    throw new IllegalStateException("half-console tile unavailable for GUI shot");
                }
                mc.displayGuiScreen(new GuiProgrammableHalfConsole(mc.player.inventory,
                        (TileEntityAnimatedScreenSelector) halfRaw));
                state = 5;
                holdTicks = GUI_SETTLE_TICKS;
                break;
            case 5:
                if (--holdTicks > 0) {
                    break;
                }
                saveNamed(mc, "half_console_gui");
                rebuildGuiFixture(mc, INPUT_WALL, ModBlocks.PROGRAMMABLE_INPUT);
                TileEntity inputRaw = mc.world.getTileEntity(INPUT_WALL);
                if (!(inputRaw instanceof TileEntityAnimatedScreenSelector)) {
                    throw new IllegalStateException("input tile unavailable for GUI shot");
                }
                mc.displayGuiScreen(new GuiProgrammableInput(mc.player.inventory,
                        (TileEntityAnimatedScreenSelector) inputRaw));
                state = 6;
                holdTicks = GUI_SETTLE_TICKS;
                break;
            case 6:
                if (--holdTicks > 0) break;
                saveNamed(mc, "input_gui");
                rebuildGuiFixture(mc, FULL_INPUT_WALL, ModBlocks.PROGRAMMABLE_FULL_INPUT);
                TileEntity fullRaw = mc.world.getTileEntity(FULL_INPUT_WALL);
                if (!(fullRaw instanceof TileEntityAnimatedScreenSelector)) {
                    throw new IllegalStateException("full-input tile unavailable for GUI shot");
                }
                mc.displayGuiScreen(new GuiAnimatedScreenSelector(mc.player.inventory,
                        (TileEntityAnimatedScreenSelector) fullRaw));
                state = 7;
                holdTicks = GUI_SETTLE_TICKS;
                break;
            case 7:
                if (--holdTicks > 0) break;
                saveNamed(mc, "full_input_gui");
                BlockPos porthole = WALL_DISPLAY.east();
                rebuildGuiFixture(mc, porthole, ModBlocks.PROGRAMMABLE_PORTHOLE_WALL);
                TileEntity wallRaw = mc.world.getTileEntity(porthole);
                if (!(wallRaw instanceof TileEntityAnimatedScreenSelector)) {
                    throw new IllegalStateException("wall tile unavailable for GUI shot");
                }
                mc.displayGuiScreen(new GuiProgrammableWall(mc.player.inventory,
                        (TileEntityAnimatedScreenSelector) wallRaw));
                state = 15;
                holdTicks = GUI_SETTLE_TICKS;
                break;
            case 15:
                if (--holdTicks > 0) break;
                saveNamed(mc, "programmable_wall_gui");
                rebuildGuiFixture(mc, CONTROL, ModBlocks.PROGRAMMABLE_BLOCK);
                TileEntity blockRaw = mc.world.getTileEntity(CONTROL);
                if (!(blockRaw instanceof TileEntityAnimatedScreenSelector))
                    throw new IllegalStateException("programmable block tile unavailable");
                mc.displayGuiScreen(new GuiProgrammableWall(mc.player.inventory,
                        (TileEntityAnimatedScreenSelector) blockRaw));
                state = 16;
                holdTicks = GUI_SETTLE_TICKS;
                break;
            case 16:
                if (--holdTicks > 0) break;
                saveNamed(mc, "programmable_block_gui");
                prepareItemHotbar(mc);
                state = 8;
                holdTicks = GUI_SETTLE_TICKS;
                break;
            case 8:
                if (--holdTicks > 0) break;
                saveNamed(mc, "item_hotbar");
                ItemStack doorIcon = new ItemStack(block("programmable_door"));
                net.minecraft.nbt.NBTTagCompound doorSettings =
                        new net.minecraft.nbt.NBTTagCompound();
                new com.vandorlabs.persistence.SpaceDoorData(7, 2, false, 0)
                        .write(new com.vandorlabs.persistence.NbtPrimitiveData(doorSettings));
                doorIcon.setTagInfo("SpaceDoorSettings", doorSettings);
                mc.player.inventory.setInventorySlotContents(4, doorIcon);
                state = 17;
                holdTicks = GUI_SETTLE_TICKS;
                break;
            case 17:
                if (--holdTicks > 0) break;
                saveNamed(mc, "door_item_hotbar");
                prepareWedgeHotbar(mc);
                state=18;
                holdTicks=GUI_SETTLE_TICKS;
                break;
            case 18:
                if (--holdTicks > 0) break;
                saveNamed(mc, "wedge_item_hotbar");
                TileEntity controllerRaw=mc.world.getTileEntity(ControllerRuntimeChecks.FIXTURE);
                if (!(controllerRaw instanceof com.vandorlabs.tiles.TileEntityRampController))
                    throw new IllegalStateException("controller GUI fixture missing");
                mc.displayGuiScreen(new GuiRampController((com.vandorlabs.tiles.TileEntityRampController)controllerRaw));
                state=10;
                holdTicks=GUI_SETTLE_TICKS;
                break;
            case 10:
                if (--holdTicks > 0) break;
                saveNamed(mc,"ramp_controller_gui");
                BlockPos doorGui=CONSOLE.add(0,0,3);
                // Represent an already-loaded door before the tool opens its GUI.
                placeDoor(mc.world,doorGui,"programmable_door",false);
                ((com.vandorlabs.tiles.TileEntitySpaceDoor)mc.world.getTileEntity(doorGui))
                        .configure(7,1,true,0,true,false,false);
                mc.displayGuiScreen(null);
                mc.getIntegratedServer().addScheduledTask(() -> {
                    World serverWorld = mc.getIntegratedServer().getWorld(0);
                    placeDoor(serverWorld,doorGui,"programmable_door",false);
                    ((com.vandorlabs.tiles.TileEntitySpaceDoor)serverWorld.getTileEntity(doorGui))
                            .configure(7,1,true,0,true,false,false);
                    EntityPlayerMP serverPlayer = mc.getIntegratedServer().getPlayerList()
                            .getPlayerByUsername(mc.player.getName());
                    serverPlayer.setPositionAndUpdate(doorGui.getX()+.5,doorGui.getY(),doorGui.getZ()-2);
                    serverPlayer.capabilities.isCreativeMode = false;
                    serverPlayer.setHeldItem(EnumHand.MAIN_HAND,
                            new ItemStack(com.vandorlabs.items.ModItems.CONFIGURIZER));
                    com.vandorlabs.items.ItemConfigurizer.onRightClickBlock(
                            new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(
                                    serverPlayer,EnumHand.MAIN_HAND,doorGui.up(),EnumFacing.NORTH,
                                    new net.minecraft.util.math.Vec3d(.5,.5,.5)));
                });
                state=23;
                holdTicks=GUI_SETTLE_TICKS;
                break;
            case 23:
                if (--holdTicks > 0) break;
                if (!(mc.currentScreen instanceof GuiSpaceDoor))
                    throw new IllegalStateException("Configurizer upper-door GUI did not open");
                GuiSpaceDoor doorScreen = (GuiSpaceDoor) mc.currentScreen;
                for (int i=0;i<5;i++)
                    doorScreen.actionPerformed(new net.minecraft.client.gui.GuiButton(11,0,0,"Size"));
                for (int i=0;i<3;i++)
                    doorScreen.actionPerformed(new net.minecraft.client.gui.GuiButton(12,0,0,"Frame"));
                state=12;
                holdTicks=GUI_SETTLE_TICKS;
                break;
            case 12:
                if (--holdTicks > 0) break;
                com.vandorlabs.tiles.TileEntitySpaceDoor editedDoor =
                        (com.vandorlabs.tiles.TileEntitySpaceDoor)mc.getIntegratedServer().getWorld(0)
                                .getTileEntity(CONSOLE.add(0,0,3));
                if (editedDoor.getDesign()!=7 || editedDoor.getDetail()!=0 || editedDoor.isFramed())
                    throw new IllegalStateException("Configurizer frame/size changes reset or stopped updating door");
                saveNamed(mc,"space_door_gui");
                mc.displayGuiScreen(null);
                BlockPos glassGui=CONSOLE.add(3,0,3);
                mc.world.setBlockState(glassGui,block("programmable_glass").getDefaultState(),3);
                mc.getIntegratedServer().addScheduledTask(() -> {
                    World serverWorld=mc.getIntegratedServer().getWorld(0);
                    serverWorld.setBlockState(glassGui,block("programmable_glass").getDefaultState(),3);
                    EntityPlayerMP serverPlayer=mc.getIntegratedServer().getPlayerList()
                            .getPlayerByUsername(mc.player.getName());
                    serverPlayer.openGui(com.vandorlabs.VandorLabs.instance,
                            com.vandorlabs.GuiHandler.GUI_PROGRAMMABLE_GLASS,serverWorld,
                            glassGui.getX(),glassGui.getY(),glassGui.getZ());
                });
                state=13;
                holdTicks=20;
                break;
            case 13:
                if (--holdTicks > 0) break;
                if (!(mc.currentScreen instanceof GuiProgrammableGlass))
                    throw new IllegalStateException("glass GUI did not open");
                GuiProgrammableGlass glassScreen=(GuiProgrammableGlass)mc.currentScreen;
                try {
                    glassScreen.actionPerformed(new net.minecraft.client.gui.GuiButton(0,0,0,"Size"));
                    glassScreen.actionPerformed(new net.minecraft.client.gui.GuiButton(1,0,0,"Glass"));
                    glassScreen.actionPerformed(new net.minecraft.client.gui.GuiButton(3,0,0,"Join"));
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException("glass GUI button failed",exception);
                }
                state=14;
                holdTicks=20;
                break;
            case 14:
                if (--holdTicks > 0) break;
                if (!(mc.currentScreen instanceof GuiProgrammableGlass))
                    throw new IllegalStateException("glass GUI closed after selection");
                BlockPos configuredGlass=CONSOLE.add(3,0,3);
                World glassWorld=mc.getIntegratedServer().getWorld(0);
                if (glassWorld.getBlockState(configuredGlass).getValue(
                        com.vandorlabs.blocks.BlockProgrammableGlass.SIZE)!=2
                        || ((com.vandorlabs.tiles.TileEntityProgrammableGlass)
                        glassWorld.getTileEntity(configuredGlass)).getShade()!=1
                        || ((com.vandorlabs.tiles.TileEntityProgrammableGlass)
                        glassWorld.getTileEntity(configuredGlass)).isJoin())
                    throw new IllegalStateException("glass GUI selections were not applied");
                saveNamed(mc,"programmable_glass_gui");
                mc.displayGuiScreen(null);
                BlockPos lightGui=CONSOLE.add(4,0,3);
                mc.world.setBlockState(lightGui,ModBlocks.PROGRAMMABLE_LIGHT.getDefaultState(),3);
                TileEntity lightRaw=mc.world.getTileEntity(lightGui);
                if (!(lightRaw instanceof com.vandorlabs.tiles.TileEntityProgrammableLight))
                    throw new IllegalStateException("light GUI fixture missing");
                mc.displayGuiScreen(new GuiProgrammableLight(mc.player.inventory,
                        (com.vandorlabs.tiles.TileEntityProgrammableLight)lightRaw));
                state=19;
                holdTicks=GUI_SETTLE_TICKS;
                break;
            case 19:
                if (--holdTicks > 0) break;
                saveNamed(mc,"programmable_light_gui");
                mc.displayGuiScreen(null);
                BlockPos triggerGui=CONSOLE.add(5,0,3);
                mc.world.setBlockState(triggerGui,ModBlocks.PROGRAMMABLE_TRIGGER_BLOCK.getDefaultState(),3);
                com.vandorlabs.tiles.TileEntityProgrammableTrigger triggerTile=
                        (com.vandorlabs.tiles.TileEntityProgrammableTrigger)mc.world.getTileEntity(triggerGui);
                java.util.List<String> finishes=java.util.Arrays.asList(
                        com.vandorlabs.tiles.ScreenHousingTextures.IDS);
                triggerTile.configure(finishes.indexOf("matter_red"),finishes.indexOf("matter_cyan"),0);
                mc.displayGuiScreen(new GuiProgrammableTrigger(mc.player.inventory,triggerTile));
                state=20;
                holdTicks=GUI_SETTLE_TICKS;
                break;
            case 20:
                if (--holdTicks > 0) break;
                saveNamed(mc,"programmable_trigger_gui");
                mc.displayGuiScreen(null);
                for (int slot = 0; slot < 9; slot++) {
                    ItemStack chair = new ItemStack(ModBlocks.PROGRAMMABLE_CHAIR);
                    net.minecraft.nbt.NBTTagCompound settings = new net.minecraft.nbt.NBTTagCompound();
                    settings.setInteger("ChairStyle", slot % 5);
                    settings.setInteger("ChairHeight", slot < 5 ? 2 : slot < 7 ? 0 : 1);
                    chair.setTagInfo("BlockEntityTag", settings);
                    mc.player.inventory.setInventorySlotContents(slot, chair);
                }
                mc.player.inventory.currentItem = 0;
                state = 21;
                holdTicks = GUI_SETTLE_TICKS;
                break;
            case 21:
                if (--holdTicks > 0) break;
                saveNamed(mc,"chair_item_hotbar_and_hand");
                BlockPos thrusterGui = CONSOLE.add(6, 0, 3);
                mc.world.setBlockState(thrusterGui, block("ion_drive").getDefaultState(), 3);
                mc.displayGuiScreen(new GuiRedstoneChannel(
                        (com.vandorlabs.tiles.TileEntityRedstoneLight) mc.world.getTileEntity(thrusterGui)));
                state = 22;
                holdTicks = GUI_SETTLE_TICKS;
                break;
            case 22:
                if (--holdTicks > 0) break;
                saveNamed(mc,"thruster_gui");
                System.out.println("[vandorlabs][reprolab] all shots taken, shutting down");
                state = 9;
                holdTicks = 10;
                break;
            case 9:
                if (--holdTicks > 0) break;
                mc.shutdown();
                break;
            default:
                break;
        }
    }

    private static void prepareItemHotbar(Minecraft mc) {
        mc.displayGuiScreen(null);
        mc.gameSettings.hideGUI = false;
        Block[] icons = {
            ModBlocks.ANIMATED_SCREEN_SELECTOR,
            ModBlocks.PROGRAMMABLE_CONSOLE,
            ModBlocks.PROGRAMMABLE_DIAGONAL_SCREEN,
            ModBlocks.PROGRAMMABLE_INPUT,
            ModBlocks.PROGRAMMABLE_FULL_INPUT,
            ModBlocks.PROGRAMMABLE_HALF_CONSOLE,
            Block.REGISTRY.getObject(new ResourceLocation("vandorlabs", "industrial_power_lever")),
            Block.REGISTRY.getObject(new ResourceLocation("vandorlabs", "push_button")),
            Block.REGISTRY.getObject(new ResourceLocation("vandorlabs", "rocker_switch"))
        };
        for (int i = 0; i < icons.length; i++) {
            if (icons[i] == null) {
                throw new IllegalStateException("hotbar fixture block " + i + " is unavailable");
            }
            mc.player.inventory.setInventorySlotContents(i, new ItemStack(icons[i]));
        }
        mc.player.inventory.currentItem = 0;
        System.out.println("[vandorlabs][reprolab] representative item hotbar prepared");
    }

    private static void prepareWedgeHotbar(Minecraft mc) {
        String[] ids={"rocket_thruster_wedge","ion_drive_wedge",
                "plasma_vent_wedge","impulse_engine_wedge"};
        for (int slot=0;slot<9;slot++)
            mc.player.inventory.setInventorySlotContents(slot,ItemStack.EMPTY);
        for (int slot=0;slot<ids.length;slot++)
            mc.player.inventory.setInventorySlotContents(slot,new ItemStack(block(ids[slot])));
        mc.player.inventory.currentItem=8;
    }

    private void build() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getIntegratedServer() == null) {
            throw new IllegalStateException("render lab requires an integrated server");
        }
        World world = mc.getIntegratedServer().getWorld(0);
        int y = PLATFORM0.getY();
        BlockPos.getAllInBox(PLATFORM0, PLATFORM1).forEach(pos -> {
            world.setBlockState(pos, Blocks.STONE.getDefaultState(), 2);
        });
        for (int z = -10; z <= 10; z++) {
            for (int x = -10; x <= 10; x++) {
                world.setBlockToAir(new BlockPos(x, y + 1, z));
                world.setBlockToAir(new BlockPos(x, y + 2, z));
                world.setBlockToAir(new BlockPos(x, y + 3, z));
            }
        }
        BlockPos.getAllInBox(new BlockPos(-6, Y + 3, -9),
                new BlockPos(5, Y + 3, -7)).forEach(pos ->
                        world.setBlockState(pos, Blocks.STONE.getDefaultState(), 2));
        place(world, SELECTOR, ModBlocks.ANIMATED_SCREEN_SELECTOR,
                BlockAnimatedScreenSelector.FACING, EnumFacing.NORTH);
        place(world, CONTROL, ModBlocks.PROGRAMMABLE_BLOCK,
                BlockAnimatedScreenSelector.FACING, EnumFacing.NORTH);
        place(world, CONSOLE, ModBlocks.PROGRAMMABLE_CONSOLE,
                BlockAnimatedScreenSelector.FACING, EnumFacing.NORTH);
        place(world, DIAGONAL, ModBlocks.PROGRAMMABLE_DIAGONAL_SCREEN,
                BlockAnimatedScreenSelector.FACING, EnumFacing.NORTH);
        placeDiagonal(world, DIAGONAL_UP, false);
        placeDiagonal(world, DIAGONAL_DOWN, true);
        for (int dx = -1; dx <= 19; dx++) {
            world.setBlockState(WALL_DISPLAY.add(dx, -1, 0),
                    Blocks.STONE.getDefaultState(), 2);
        }
        world.setBlockState(WALL_DISPLAY.add(-1, 0, 0),
                Blocks.COBBLESTONE_WALL.getDefaultState(), 2);
        for (int depth = 0; depth < 3; depth++) {
            for (int row : new int[] {0, 2})
                world.setBlockState(WALL_DISPLAY.add(44 + depth, -1, row),
                        Blocks.STONE.getDefaultState(), 2);
            BlockPos wallPos = WALL_DISPLAY.add(44 + depth, 0, 0);
            world.setBlockState(wallPos, ModBlocks.PROGRAMMABLE_WALL.getDefaultState()
                    .withProperty(com.vandorlabs.blocks.BlockProgrammableWall.FACING,
                            EnumFacing.NORTH)
                    .withProperty(com.vandorlabs.blocks.BlockProgrammableWall.DEPTH, depth), 2);
            BlockPos glassPos = WALL_DISPLAY.add(44 + depth, 0, 2);
            world.setBlockState(glassPos, block("programmable_glass").getDefaultState()
                    .withProperty(com.vandorlabs.blocks.BlockProgrammableGlass.DEPTH, depth), 2);
        }
        IBlockState plainNorth = ModBlocks.PROGRAMMABLE_WALL.getDefaultState()
                .withProperty(com.vandorlabs.blocks.BlockProgrammableWall.FACING,
                        EnumFacing.NORTH);
        IBlockState plainEast = plainNorth.withProperty(
                com.vandorlabs.blocks.BlockProgrammableWall.FACING, EnumFacing.EAST);
        for (int dx : new int[] {52, 53, 56, 57})
            world.setBlockState(WALL_DISPLAY.add(dx, 0, 0),
                    dx == 52 || dx == 56 ? plainNorth : plainEast, 2);
        for (int dz : new int[] {-1, 1})
            world.setBlockState(WALL_DISPLAY.add(57, 0, dz), plainEast, 2);
        IBlockState plainSouth = plainNorth.withProperty(
                com.vandorlabs.blocks.BlockProgrammableWall.FACING, EnumFacing.SOUTH);
        IBlockState plainWest = plainNorth.withProperty(
                com.vandorlabs.blocks.BlockProgrammableWall.FACING, EnumFacing.WEST);
        for (int center : new int[] {110, 116}) {
            world.setBlockState(WALL_DISPLAY.add(center - 1, 0, 0), plainNorth, 2);
            world.setBlockState(WALL_DISPLAY.add(center, 0, 0), plainEast, 2);
            world.setBlockState(WALL_DISPLAY.add(center + 1, 0, 0), plainSouth, 2);
        }
        world.setBlockState(WALL_DISPLAY.add(110, 0, -1), plainWest, 2);
        for (int dz : new int[] {-1, 1})
            world.setBlockState(WALL_DISPLAY.add(116, 0, dz), plainWest, 2);
        world.setBlockState(WALL_DISPLAY.add(121, 0, 0), plainSouth.withProperty(
                com.vandorlabs.blocks.BlockProgrammableWall.DEPTH, 1), 2);
        world.setBlockState(WALL_DISPLAY.add(122, 0, 0), plainEast.withProperty(
                com.vandorlabs.blocks.BlockProgrammableWall.DEPTH, 1), 2);
        world.setBlockState(WALL_DISPLAY.add(122, 0, -1), plainWest.withProperty(
                com.vandorlabs.blocks.BlockProgrammableWall.DEPTH, 2), 2);
        for (int i = 0; i < 4; i++) {
            Block block = i == 0 ? ModBlocks.PROGRAMMABLE_WALL
                    : i == 1 ? ModBlocks.PROGRAMMABLE_PORTHOLE_WALL
                    : ModBlocks.PROGRAMMABLE_DIAGONAL_WALL;
            BlockPos wallPos = WALL_DISPLAY.add(i < 2 ? i : i + 1, 0, 0);
            IBlockState wallState = block.getDefaultState().withProperty(
                    com.vandorlabs.blocks.BlockProgrammableWall.FACING,
                    EnumFacing.NORTH);
            if (i == 3) wallState = wallState.withProperty(
                    com.vandorlabs.blocks.BlockProgrammableWall.INVERTED, true);
            world.setBlockState(wallPos, wallState, 2);
            TileEntity tile = world.getTileEntity(wallPos);
            if (tile instanceof TileEntityAnimatedScreenSelector) {
                ((TileEntityAnimatedScreenSelector) tile).setHousingTexture(i);
                world.notifyBlockUpdate(wallPos, wallState, wallState, 3);
            }
        }
        BlockPos joined = WALL_DISPLAY.add(2, 0, 0);
        IBlockState joinedState = ModBlocks.PROGRAMMABLE_PORTHOLE_WALL
                .getDefaultState().withProperty(
                        com.vandorlabs.blocks.BlockProgrammableWall.FACING,
                        EnumFacing.NORTH);
        world.setBlockState(joined, joinedState, 2);
        ((TileEntityAnimatedScreenSelector) world.getTileEntity(WALL_DISPLAY.east()))
                .setJoinPortholes(true);
        TileEntityAnimatedScreenSelector joinedTile =
                (TileEntityAnimatedScreenSelector) world.getTileEntity(joined);
        joinedTile.setJoinPortholes(true);
        joinedTile.setGlassShade(1);
        joinedTile.setHousingTexture(2);
        for (int dx = 1; dx <= 2; dx++) {
            BlockPos upperPorthole = WALL_DISPLAY.add(dx, 1, 0);
            world.setBlockState(upperPorthole, joinedState, 2);
            TileEntityAnimatedScreenSelector upperTile =
                    (TileEntityAnimatedScreenSelector) world.getTileEntity(upperPorthole);
            upperTile.setJoinPortholes(true);
            upperTile.setGlassShade(1);
            upperTile.setHousingTexture(dx);
        }
        for (int offset = 6; offset <= 7; offset++) {
            BlockPos cornerPos = WALL_DISPLAY.add(offset, 0, 0);
            IBlockState cornerState = ModBlocks.PROGRAMMABLE_DIAGONAL_WALL
                    .getDefaultState().withProperty(
                            com.vandorlabs.blocks.BlockProgrammableWall.FACING,
                            EnumFacing.NORTH).withProperty(
                            com.vandorlabs.blocks.BlockProgrammableWall.INVERTED,
                            offset == 7);
            world.setBlockState(cornerPos, cornerState, 2);
            ((TileEntityAnimatedScreenSelector) world.getTileEntity(cornerPos))
                    .setHousingTexture(offset == 6 ? 1 : 2);
            BlockPos turnPos = cornerPos.north();
            world.setBlockState(turnPos, cornerState.withProperty(
                    com.vandorlabs.blocks.BlockProgrammableWall.FACING,
                    EnumFacing.EAST), 2);
            ((TileEntityAnimatedScreenSelector) world.getTileEntity(turnPos))
                    .setHousingTexture(offset == 6 ? 1 : 2);
        }
        BlockPos cornerTurn = WALL_DISPLAY.add(10, 0, 0);
        IBlockState turnState = ModBlocks.PROGRAMMABLE_DIAGONAL_WALL
                .getDefaultState().withProperty(
                        com.vandorlabs.blocks.BlockProgrammableWall.FACING,
                        EnumFacing.NORTH);
        world.setBlockState(cornerTurn, turnState, 2);
        ((TileEntityAnimatedScreenSelector) world.getTileEntity(cornerTurn))
                .setHousingTexture(1);
        world.setBlockState(cornerTurn.north(), turnState.withProperty(
                com.vandorlabs.blocks.BlockProgrammableWall.FACING,
                EnumFacing.WEST), 2);
        world.setBlockState(cornerTurn.south(), turnState.withProperty(
                com.vandorlabs.blocks.BlockProgrammableWall.FACING,
                EnumFacing.EAST), 2);
        ((TileEntityAnimatedScreenSelector) world.getTileEntity(cornerTurn.south()))
                .setHousingTexture(2);
        BlockPos threeCorner = WALL_DISPLAY.add(18, 0, 0);
        world.setBlockState(threeCorner, turnState, 2);
        world.setBlockState(threeCorner.west(), turnState, 2);
        world.setBlockState(threeCorner.south(), turnState.withProperty(
                com.vandorlabs.blocks.BlockProgrammableWall.FACING,
                EnumFacing.WEST), 2);
        world.setBlockState(threeCorner.south().down(),
                Blocks.STONE.getDefaultState(), 2);
        for (int offset : new int[] {33, 38}) {
            BlockPos center = WALL_DISPLAY.add(offset, 0, 0);
            world.setBlockState(center, turnState, 2);
            world.setBlockState(center.south(), turnState.withProperty(
                    com.vandorlabs.blocks.BlockProgrammableWall.FACING, EnumFacing.EAST), 2);
            if (offset == 33) {
                world.setBlockState(center.west(), turnState, 2);
                world.setBlockState(center.east(), turnState, 2);
            }
        }
        for (int dy = 0; dy <= 1; dy++) {
            BlockPos verticalPos = WALL_DISPLAY.add(13, dy, 0);
            world.setBlockState(verticalPos, joinedState, 2);
            TileEntityAnimatedScreenSelector verticalTile =
                    (TileEntityAnimatedScreenSelector) world.getTileEntity(verticalPos);
            verticalTile.setJoinPortholes(true);
            verticalTile.setHousingTexture(1);
        }
        BlockPos singlePos = WALL_DISPLAY.add(14, 0, 0);
        world.setBlockState(singlePos, joinedState, 2);
        ((TileEntityAnimatedScreenSelector) world.getTileEntity(singlePos))
                .setHousingTexture(2);
        for (int dx = 22; dx <= 28; dx++) {
            if (dx == 24 || dx == 25) continue;
            int rows = dx < 26 ? (dx == 22 ? 2 : 1) : 3;
            for (int dy = 0; dy < rows; dy++) {
                BlockPos panePos = WALL_DISPLAY.add(dx, dy, 0);
                world.setBlockState(panePos, joinedState, 2);
                TileEntityAnimatedScreenSelector pane =
                        (TileEntityAnimatedScreenSelector) world.getTileEntity(panePos);
                pane.setJoinPortholes(true);
                pane.setHousingTexture(1);
            }
        }
        world.setBlockState(DIAGONAL_DOWN.up(),Blocks.STONE.getDefaultState(),2);
        IBlockState inputWall = ModBlocks.PROGRAMMABLE_INPUT.getDefaultState()
                .withProperty(com.vandorlabs.blocks.BlockProgrammableInput.FACING,
                        EnumFacing.NORTH)
                .withProperty(com.vandorlabs.blocks.BlockProgrammableInput.KEYBOARD, false)
                .withProperty(com.vandorlabs.blocks.BlockProgrammableInput.UPPER, true);
        world.setBlockState(INPUT_WALL, inputWall, 2);
        configureInputs(world, INPUT_WALL, 2, 2);
        setInputWallPosition(world, INPUT_WALL, 1);
        setInputSmall(world, INPUT_WALL, true);
        IBlockState inputKeyboard = inputWall
                .withProperty(com.vandorlabs.blocks.BlockProgrammableInput.KEYBOARD, true)
                .withProperty(com.vandorlabs.blocks.BlockProgrammableInput.UPPER, false);
        world.setBlockState(INPUT_KEYBOARD, inputKeyboard, 2);
        configureInputs(world, INPUT_KEYBOARD, 3, 3);
        setInputSmall(world, INPUT_KEYBOARD, true);
        place(world, HALF_CONSOLE, ModBlocks.PROGRAMMABLE_HALF_CONSOLE,
                BlockAnimatedScreenSelector.FACING, EnumFacing.NORTH);
        configureInputs(world, HALF_CONSOLE, 4, 14);
        IBlockState fullWall = ModBlocks.PROGRAMMABLE_FULL_INPUT.getDefaultState()
                .withProperty(com.vandorlabs.blocks.BlockProgrammableInput.FACING,
                        EnumFacing.NORTH)
                .withProperty(com.vandorlabs.blocks.BlockProgrammableInput.KEYBOARD, false)
                .withProperty(com.vandorlabs.blocks.BlockProgrammableInput.UPPER, false);
        IBlockState fullFloor = fullWall.withProperty(
                com.vandorlabs.blocks.BlockProgrammableInput.KEYBOARD, true);
        EntityPlayerMP serverPlayer = mc.getIntegratedServer().getPlayerList()
                .getPlayerByUsername(mc.player.getName());
        if (serverPlayer == null) {
            throw new IllegalStateException("render lab server player unavailable");
        }
        try {
            mc.getIntegratedServer().addScheduledTask(() -> {
                DoorRuntimeChecks.run(world, serverPlayer);
                DisplayTableRuntimeChecks.run(world, serverPlayer);
                ChairRuntimeChecks.run(world, serverPlayer);
                CopyCompatibilityRuntimeChecks.run(world, serverPlayer);
                RedstoneChannelRuntimeChecks.run(serverPlayer.world, serverPlayer);
                ControllerRuntimeChecks.run(world, serverPlayer);
                ControllerRuntimeChecks.buildFixture(world, serverPlayer);
            }).get();
        } catch (Exception exception) {
            throw new IllegalStateException("Controller server-thread contracts failed",exception);
        }
        ScreenRuntimeChecks.run(serverPlayer);
        MaterialRuntimeChecks.run(serverPlayer);
        ItemRuntimeChecks.run(serverPlayer);
        // Rebuild programmable fixtures after destructive runtime contracts.
        // Keeping render targets downstream from test mutations also avoids
        // integrated-server/client ordering races in a reused world.
        place(world, CONSOLE, ModBlocks.PROGRAMMABLE_CONSOLE,
                BlockAnimatedScreenSelector.FACING, EnumFacing.NORTH);
        world.setBlockState(INPUT_WALL, inputWall, 2);
        configureInputs(world, INPUT_WALL, 2, 2);
        setInputWallPosition(world, INPUT_WALL, 1);
        setInputSmall(world, INPUT_WALL, true);
        world.setBlockState(INPUT_KEYBOARD, inputKeyboard, 2);
        configureInputs(world, INPUT_KEYBOARD, 3, 3);
        setInputSmall(world, INPUT_KEYBOARD, true);
        place(world, HALF_CONSOLE, ModBlocks.PROGRAMMABLE_HALF_CONSOLE,
                BlockAnimatedScreenSelector.FACING, EnumFacing.NORTH);
        configureInputs(world, HALF_CONSOLE, 4, 14);
        world.setBlockState(FULL_INPUT_WALL, fullWall, 2);
        configureScreen(world, FULL_INPUT_WALL, "engineering_screen");
        world.setBlockState(FULL_INPUT_FLOOR, fullFloor, 2);
        configureScreen(world, FULL_INPUT_FLOOR, "engineering_screen");
        // Runtime door checks use nearby temporary positions. Clear their
        // whole camera corridor (including upper door halves/TESRs), then
        // build the adjacent pair so no intermittent fixture can occlude it.
        BlockPos.getAllInBox(new BlockPos(-10, Y, 5),
                new BlockPos(-6, Y + 2, 8)).forEach(world::setBlockToAir);
        place(world, WIDE_LEFT, ModBlocks.ANIMATED_SCREEN_SELECTOR,
                BlockAnimatedScreenSelector.FACING, EnumFacing.NORTH);
        place(world, WIDE_RIGHT, ModBlocks.ANIMATED_SCREEN_SELECTOR,
                BlockAnimatedScreenSelector.FACING, EnumFacing.NORTH);
        configureScreen(world, WIDE_LEFT, "ship_rounded_wide_1_left");
        configureScreen(world, WIDE_RIGHT, "ship_rounded_wide_1_right");
        String[] columns = {"left", "center", "right"};
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 3; column++) {
                BlockPos pos = CRUISER_GRID.add(column, 1 - row, 0);
                place(world, pos, ModBlocks.ANIMATED_SCREEN_SELECTOR,
                        BlockAnimatedScreenSelector.FACING, EnumFacing.NORTH);
                configureScreen(world, pos, "cruiser_three_views_"
                        + (row == 0 ? "top_" : "bottom_") + columns[column]);
            }
        }
        placeChair(world, CHAIR_COMMAND, 0);
        placeChair(world, CHAIR_COMPANION, 1);
        placeChair(world, CHAIR_OPERATOR, 2);
        placeChair(world, CHAIR_CONFERENCE, 3);
        placeChair(world, CHAIR_MESS_HALL, 4);
        String[] materials = {"stitched_padding", "seamed_padding",
                "ribbed_padding", "cushion_padding",
                "cyan_light_strip", "border_light",
                "amber_light_strip",
                                "wall_pipes", "framed_wall_pipes"};
        for (int index = 0; index < materials.length; index++) {
            Block material = Block.REGISTRY.getObject(
                    new ResourceLocation("vandorlabs", materials[index]));
            world.setBlockState(MATERIAL_GRID.add(index % 4, 3 - index / 4, 0),
                    material.getDefaultState(), 2);
        }
        String[] hullFloors = {"light_alloy_hull", "dark_gunmetal_hull",
                "midnight_matte_hull", "midnight_satin_hull",
                "bluegray", "burgundy",
                "metal_floor"};
        for (int index = 0; index < hullFloors.length; index++) {
            String id = hullFloors[index];
            int finish = java.util.Arrays.asList(
                    com.vandorlabs.tiles.ScreenHousingTextures.IDS).indexOf(id);
            Block hull = finish >= 0 ? ModBlocks.PROGRAMMABLE_BLOCK
                    : Block.REGISTRY.getObject(new ResourceLocation("vandorlabs", id));
            BlockPos at = MATERIAL_GRID.add(4 + index % 2, 3 - index / 2, 0);
            world.setBlockState(at, hull.getDefaultState(), 2);
            if (finish >= 0) ((TileEntityAnimatedScreenSelector)
                    world.getTileEntity(at)).setHousingTexture(finish);
        }
        Block ceilingThruster = Block.REGISTRY.getObject(
                new ResourceLocation("vandorlabs", "ion_drive"));
        String[] ceilingFamilies = {"rocket_thruster", "ion_drive",
                "plasma_vent", "impulse_engine"};
        for (int family = 0; family < ceilingFamilies.length; family++) {
            Block familyBlock = Block.REGISTRY.getObject(new ResourceLocation(
                    "vandorlabs", ceilingFamilies[family]));
            BlockPos familyAnchor = CEILING_THRUSTER.east((family % 2) * 4)
                    .north((family / 2) * 4);
            for (int x = 0; x < 2; x++) {
                for (int localY = 0; localY < 2; localY++) {
                    BlockPos member = familyAnchor.east(x).north(localY);
                    world.setBlockState(member, familyBlock.getDefaultState()
                            .withProperty(com.vandorlabs.blocks.BlockPropulsionLight.FACING,
                                    EnumFacing.DOWN), 2);
                    TileEntity streamTile = world.getTileEntity(member);
                    if (streamTile instanceof com.vandorlabs.tiles.TileEntityRedstoneLight)
                        ((com.vandorlabs.tiles.TileEntityRedstoneLight) streamTile)
                                .setParticleStreamSelected(true);
                }
            }
        }
        for (int x = 0; x < 2; x++) {
            for (int localY = 0; localY < 2; localY++) {
                world.setBlockState(FLOOR_THRUSTER.east(x).south(localY),
                        ceilingThruster.getDefaultState().withProperty(
                                com.vandorlabs.blocks.BlockPropulsionLight.FACING,
                                EnumFacing.UP), 2);
                world.setBlockState(WALL_THRUSTER.east(x).up(localY),
                        ceilingThruster.getDefaultState().withProperty(
                                com.vandorlabs.blocks.BlockPropulsionLight.FACING,
                                EnumFacing.NORTH), 2);
            }
        }
        System.out.println("[vandorlabs][reprolab] platform built");
    }

    private static void placeChair(World world, BlockPos pos, int style) {
        Block raw = ModBlocks.PROGRAMMABLE_CHAIR;
        IBlockState lower = raw.getDefaultState()
                .withProperty(BlockBridgeChair.FACING, EnumFacing.NORTH)
                .withProperty(BlockBridgeChair.UPPER, false);
        world.setBlockState(pos, lower, 2);
        world.setBlockState(pos.up(), lower.withProperty(BlockBridgeChair.UPPER, true), 2);
        ((com.vandorlabs.tiles.TileEntityProgrammableChair)
                world.getTileEntity(pos)).setStyle(style);
    }

    private static void buildGalleryStage(World world, String shot) {
        world.getGameRules().setOrCreateGameRule("doMobSpawning", "false");
        // Documentation lives in its own empty chunk so the ordinary compact
        // regression fixtures never appear behind the catalog.
        for (Entity entity : new ArrayList<Entity>(world.loadedEntityList)) {
            if (!(entity instanceof EntityPlayer)) entity.setDead();
        }
        BlockPos.getAllInBox(new BlockPos(GALLERY_X - 14, GALLERY_Y, -22),
                new BlockPos(GALLERY_X + 14, GALLERY_Y + 9, -14))
                .forEach(world::setBlockToAir);
        BlockPos.getAllInBox(new BlockPos(GALLERY_X - 14, GALLERY_Y - 1, -22),
                new BlockPos(GALLERY_X + 14, GALLERY_Y - 1, -14))
                .forEach(pos -> world.setBlockState(pos,
                        Blocks.GRASS.getDefaultState(), 2));
        if (shot.equals("gallery_programmable_displays")) {
            place(world, new BlockPos(GALLERY_X - 6, GALLERY_Y, -18),
                    ModBlocks.ANIMATED_SCREEN_SELECTOR,
                    BlockAnimatedScreenSelector.FACING, EnumFacing.NORTH);
            place(world, new BlockPos(GALLERY_X - 2, GALLERY_Y, -18),
                    ModBlocks.PROGRAMMABLE_CONSOLE,
                    BlockAnimatedScreenSelector.FACING, EnumFacing.NORTH);
            placeDiagonal(world, new BlockPos(GALLERY_X + 2, GALLERY_Y, -18), false);
            placeDiagonal(world, new BlockPos(GALLERY_X + 6, GALLERY_Y, -18), true);
        } else if (shot.equals("gallery_programmable_inputs")) {
            placeGalleryInputs(world, false);
        } else if (shot.equals("gallery_programmable_full_inputs")) {
            placeGalleryInputs(world, true);
        } else if (shot.equals("gallery_display_table")) {
            com.vandorlabs.blocks.BlockIndustrialTable table =
                    (com.vandorlabs.blocks.BlockIndustrialTable) block("industrial_table");
            IBlockState state = table.getDefaultState().withProperty(
                    com.vandorlabs.blocks.BlockIndustrialTable.FACING, EnumFacing.NORTH);
            for (int x = -2; x <= 0; x++)
                world.setBlockState(new BlockPos(GALLERY_X + x, GALLERY_Y, -18), state, 3);
            world.setBlockState(new BlockPos(GALLERY_X + 3, GALLERY_Y + 2, -18),
                    state.withProperty(com.vandorlabs.blocks.BlockIndustrialTable.UPSIDE_DOWN, true), 3);
        } else if (shot.equals("gallery_propulsion")) {
            String[] hexes = {"rocket_thruster_hexagonal", "ion_drive_hexagonal",
                    "plasma_vent_hexagonal", "impulse_engine_hexagonal"};
            String[] triangles = {"rocket_thruster_wedge", "ion_drive_wedge",
                    "plasma_vent_wedge", "impulse_engine_wedge"};
            String[] corners = {"rocket_thruster_wedge",
                    "rocket_thruster_wedge_bottom_right",
                    "rocket_thruster_wedge_top_left",
                    "rocket_thruster_wedge_top_right"};
            placePropulsionRow(world, hexes, GALLERY_Y + 5, true);
            placePropulsionRow(world, triangles, GALLERY_Y + 3, false);
            placePropulsionRow(world, corners, GALLERY_Y + 1, false);
            String[] hover = {"antigravity_plate", "repulsor_array",
                    "vertical_hover_thruster"};
            for (int i = 0; i < hover.length; i++) {
                Block block = block(hover[i]);
                world.setBlockState(new BlockPos(GALLERY_X - 3 + i * 3,
                                GALLERY_Y, -18),
                        block.getDefaultState().withProperty(
                                com.vandorlabs.blocks.BlockPropulsionLight.FACING,
                                EnumFacing.NORTH), 2);
            }
        } else if (shot.equals("gallery_connected_thruster")) {
            Block thruster = block("ion_drive");
            for (int x = -1; x <= 1; x++) {
                for (int y = 0; y < 3; y++) {
                    BlockPos pos = new BlockPos(GALLERY_X + x, GALLERY_Y + y, -18);
                    world.setBlockState(pos, thruster.getDefaultState().withProperty(
                            com.vandorlabs.blocks.BlockPropulsionLight.FACING,
                            EnumFacing.NORTH), 2);
                    TileEntity tile = world.getTileEntity(pos);
                    if (tile instanceof com.vandorlabs.tiles.TileEntityRedstoneLight)
                        ((com.vandorlabs.tiles.TileEntityRedstoneLight) tile)
                                .setParticleStreamSelected(true);
                }
            }
        } else if (shot.equals("gallery_lighting_controls")) {
            for (int i = 0; i < com.vandorlabs.tiles.ProgrammableLightTextures.IDS.length; i++) {
                int x = GALLERY_X - 9 + i * 2;
                BlockPos on = new BlockPos(x, GALLERY_Y + 3, -18);
                BlockPos off = new BlockPos(x, GALLERY_Y + 1, -18);
                world.setBlockState(on, ModBlocks.PROGRAMMABLE_LIGHT.getDefaultState(), 2);
                world.setBlockState(off, ModBlocks.PROGRAMMABLE_LIGHT.getDefaultState(), 2);
                ((com.vandorlabs.tiles.TileEntityProgrammableLight) world.getTileEntity(on))
                        .configure(i, 15);
                com.vandorlabs.tiles.TileEntityProgrammableLight dark =
                        (com.vandorlabs.tiles.TileEntityProgrammableLight) world.getTileEntity(off);
                dark.configure(i, 15);
                dark.setOn(false);
            }
            String[] controls = {"push_button", "rocker_switch",
                    "industrial_power_lever", "compact_power_lever"};
            for (int i = 0; i < controls.length; i++) {
                Block control = block(controls[i]);
                int x = GALLERY_X + 2 + i * 2;
                IBlockState off = control.getDefaultState();
                IBlockState on = off;
                if (control instanceof com.vandorlabs.blocks.BlockVandorSwitch) {
                    off = off.withProperty(com.vandorlabs.blocks.BlockVandorSwitch.FACING,
                                    EnumFacing.NORTH)
                            .withProperty(com.vandorlabs.blocks.BlockVandorSwitch.ON, false);
                    on = off.withProperty(com.vandorlabs.blocks.BlockVandorSwitch.ON, true);
                } else if (control instanceof com.vandorlabs.blocks.BlockIndustrialLever) {
                    off = off.withProperty(net.minecraft.block.BlockHorizontal.FACING,
                                    EnumFacing.NORTH)
                            .withProperty(com.vandorlabs.blocks.BlockIndustrialLever.POWERED,
                                    false);
                    on = off.withProperty(
                            com.vandorlabs.blocks.BlockIndustrialLever.POWERED, true);
                }
                world.setBlockState(new BlockPos(x, GALLERY_Y + 3, -18), on, 2);
                world.setBlockState(new BlockPos(x, GALLERY_Y + 1, -18), off, 2);
            }
        } else if (shot.equals("gallery_joined_lights")) {
            for (int style = 0; style < 2; style++) {
                for (int dx = 0; dx < 3; dx++)
                    for (int dy = 0; dy < 2; dy++) {
                        BlockPos at = new BlockPos(GALLERY_X - 6 + style * 8 + dx,
                                GALLERY_Y + 1 + dy, -18);
                        world.setBlockState(at, ModBlocks.PROGRAMMABLE_LIGHT.getDefaultState(), 2);
                        ((com.vandorlabs.tiles.TileEntityProgrammableLight)
                                world.getTileEntity(at)).configure(style == 0 ? 0 : 2,
                                style == 0 ? 15 : 9, true, 0);
                    }
            }
        } else if (shot.equals("gallery_programmable_slabs")) {
            for (int i = 0; i < 6; i++) {
                boolean top = i >= 3;
                BlockPos at = new BlockPos(GALLERY_X - 6 + (i % 3) * 4,
                        GALLERY_Y + (top ? 3 : 1), -18);
                world.setBlockState(at, ModBlocks.PROGRAMMABLE_SLAB.getDefaultState()
                        .withProperty(com.vandorlabs.blocks.BlockProgrammableSlab.HALF,
                                top ? net.minecraft.block.BlockSlab.EnumBlockHalf.TOP
                                        : net.minecraft.block.BlockSlab.EnumBlockHalf.BOTTOM), 2);
                ((TileEntityAnimatedScreenSelector) world.getTileEntity(at))
                        .setHousingTexture(i == 2 || i == 5
                                ? com.vandorlabs.tiles.ScreenHousingTextures.IDS.length - 2
                                : i % 3);
            }
        } else if (shot.equals("gallery_structure")) {
            String[] ids = {"dark_wall_panel", "light_wall_panel",
                    "bolted_wall_plate", "ribbed_wall", "wall_vent", "border_base_left",
                    "border_base_right", "border_corner_left", "border_corner_right",
                    "border_light", "border_light_vertical", "framed_wall_pipes",
                    "wall_pipes", "stitched_padding", "seamed_padding",
                    "ribbed_padding", "cushion_padding", "border_light",
                    "matter", "light_alloy_hull",
                    "dark_gunmetal_hull", "midnight_matte_hull", "midnight_satin_hull",
                    "bluegray", "burgundy",
                    "metal_floor", "clear_cockpit_glass", "pale_cyan_cockpit_glass",
                    "smoked_cockpit_glass"};
            for (int i = 0; i < ids.length; i++) {
                int finish = java.util.Arrays.asList(
                        com.vandorlabs.tiles.ScreenHousingTextures.IDS).indexOf(ids[i]);
                Block block = finish >= 0 ? ModBlocks.PROGRAMMABLE_BLOCK : block(ids[i]);
                IBlockState state = block.getDefaultState();
                if (state.getProperties().containsKey(BlockVandorDirectional.FACING))
                    state = state.withProperty(BlockVandorDirectional.FACING,
                            EnumFacing.NORTH);
                BlockPos location = new BlockPos(GALLERY_X - 7 + (i % 8) * 2,
                        GALLERY_Y + 6 - (i / 8) * 2, -18);
                world.setBlockState(location, state, 2);
                if (finish >= 0) ((TileEntityAnimatedScreenSelector)
                        world.getTileEntity(location)).setHousingTexture(finish);
            }
        } else if (shot.equals("gallery_space_glass")) {
            for (int x=0; x<3; x++) for (int y=0; y<3; y++) {
                world.setBlockState(new BlockPos(GALLERY_X-1+x,GALLERY_Y+y,-18),
                        block("programmable_glass").getDefaultState().withProperty(
                                com.vandorlabs.blocks.BlockProgrammableGlass.SIZE,x),3);
                ((com.vandorlabs.tiles.TileEntityProgrammableGlass)world.getTileEntity(
                        new BlockPos(GALLERY_X-1+x,GALLERY_Y+y,-18))).setShade(y);
            }
        } else if (shot.equals("gallery_round_portholes")) {
            int[] starts = {-6, -3, 1};
            for (int size = 1; size <= 3; size++)
                for (int dx = 0; dx < size; dx++) for (int dy = 0; dy < size; dy++) {
                    BlockPos at = new BlockPos(GALLERY_X + starts[size - 1] + dx,
                            GALLERY_Y + 1 + dy, -18);
                    world.setBlockState(at, ModBlocks.PROGRAMMABLE_PORTHOLE_WALL.getDefaultState()
                            .withProperty(com.vandorlabs.blocks.BlockProgrammableWall.FACING,
                                    EnumFacing.NORTH), 2);
                    TileEntityAnimatedScreenSelector tile =
                            (TileEntityAnimatedScreenSelector)world.getTileEntity(at);
                    tile.setPortholeShape(PortholeHex.ROUND);
                    tile.setJoinPortholes(true);
                    tile.setGlassShade(2);
                    tile.setHousingTexture(java.util.Arrays.asList(
                            com.vandorlabs.tiles.ScreenHousingTextures.IDS).indexOf("light_alloy_hull"));
                }
        } else if (shot.startsWith("gallery_space_config_hinges_")) {
            for (int i=0;i<2;i++) {
                BlockPos p=new BlockPos(GALLERY_X-1+i,GALLERY_Y,-18);
                placeDoor(world,p,"programmable_door",true);
                setDoorHinge(world,p,i==1?BlockDoor.EnumHingePosition.RIGHT:BlockDoor.EnumHingePosition.LEFT);
                ((com.vandorlabs.tiles.TileEntitySpaceDoor)world.getTileEntity(p))
                        .configure(2,1,true,0,false,false,shot.endsWith("_on"));
            }
        } else if (shot.startsWith("gallery_space_panel_")) {
            BlockPos p=new BlockPos(GALLERY_X,GALLERY_Y,-18);
            placeDoor(world,p,"programmable_door",false);
            for (BlockPos part:new BlockPos[]{p,p.up()}) {
                IBlockState state=world.getBlockState(part);
                world.setBlockState(part,state.withProperty(BlockVandorDoor.FACING,EnumFacing.SOUTH),2);
            }
            setDoorHinge(world,p,BlockDoor.EnumHingePosition.RIGHT);
            boolean sliding=shot.contains("sliding");
            ((com.vandorlabs.tiles.TileEntitySpaceDoor)world.getTileEntity(p))
                    .configure(2,1,true,0,false,sliding,true);
            ((com.vandorlabs.tiles.TileEntitySpaceDoor)world.getTileEntity(p))
                    .setPlacementDepth(shot.endsWith("_far")?2:shot.endsWith("_middle")?0:1);
        } else if (shot.startsWith("gallery_space_jamb_")) {
            placeDoor(world,new BlockPos(GALLERY_X,GALLERY_Y,-18),"space_standard_rotating_door_framed",true);
            IBlockState backing=Blocks.CONCRETE.getDefaultState().withProperty(
                    net.minecraft.block.BlockColored.COLOR,net.minecraft.item.EnumDyeColor.MAGENTA);
            for (int side : new int[]{-1,1}) for (int y=0;y<2;y++) for (int z=-19;z<=-17;z++)
                world.setBlockState(new BlockPos(GALLERY_X+side,GALLERY_Y+y,z),backing,2);
        } else if (shot.startsWith("gallery_space_hinges_")) {
            for (int i=0;i<3;i++) {
                BlockPos p=new BlockPos(GALLERY_X-1+(i==2?3:i),GALLERY_Y,-18);
                placeDoor(world,p,i==2?"space_standard_rotating_door_bare":"space_standard_rotating_door_framed",
                        shot.endsWith("_open"));
                setDoorHinge(world,p,i==1?BlockDoor.EnumHingePosition.RIGHT:BlockDoor.EnumHingePosition.LEFT);
            }
        } else if (shot.startsWith("gallery_space_")) {
            String[] bits=shot.split("_");
            int index=0;
            for (String family : new String[]{"observation","airlock","standard","security","reactor"}) {
                String id="space_"+(family.equals("reactor")?"reactor_service":family)
                        +"_"+bits[2]+"_door_"+bits[3];
                int x=GALLERY_X-11+index++*5;
                for (int column : new int[]{0,1,3}) {
                    BlockPos p=new BlockPos(x+column,GALLERY_Y,-18);
                    placeDoor(world,p,id,shot.endsWith("_open"));
                    setDoorHinge(world,p,column==1 ? BlockDoor.EnumHingePosition.RIGHT
                            : BlockDoor.EnumHingePosition.LEFT);
                }
            }
        } else if (shot.equals("gallery_chairs")) {
            for (int i = 0; i < 5; i++)
                placeChair(world, new BlockPos(GALLERY_X - 6 + i * 3,
                        GALLERY_Y, -18), i);
        } else if (shot.startsWith("gallery_ramp_") && !world.isRemote) {
            EntityPlayerMP player = null;
            for (EntityPlayer candidate : world.playerEntities) {
                if (candidate instanceof EntityPlayerMP) {
                    player = (EntityPlayerMP) candidate;
                    break;
                }
            }
            if (player == null) throw new IllegalStateException("gallery ramp player unavailable");
            boolean upper = shot.contains("_down_");
            boolean smooth = shot.endsWith("_smooth");
            ControllerRuntimeChecks.buildGalleryFixture(world, player,
                    new BlockPos(GALLERY_X, upper ? GALLERY_Y + 3 : GALLERY_Y, -21),
                    upper, smooth);
        }
    }

    private static void placeGalleryInputs(World world, boolean fullOnly) {
        IBlockState wall = ModBlocks.PROGRAMMABLE_INPUT.getDefaultState()
                .withProperty(com.vandorlabs.blocks.BlockProgrammableInput.FACING,
                        EnumFacing.NORTH)
                .withProperty(com.vandorlabs.blocks.BlockProgrammableInput.KEYBOARD, false)
                .withProperty(com.vandorlabs.blocks.BlockProgrammableInput.UPPER, true);
        if (!fullOnly) {
            BlockPos wallPos = new BlockPos(GALLERY_X - 4, GALLERY_Y, -18);
            world.setBlockState(wallPos, wall, 2);
            configureInputs(world, wallPos, 2, 2);
            setInputWallPosition(world, wallPos, 1);
            setInputSmall(world, wallPos, true);

            BlockPos keyboardPos = new BlockPos(GALLERY_X, GALLERY_Y, -18);
            world.setBlockState(keyboardPos, wall
                    .withProperty(com.vandorlabs.blocks.BlockProgrammableInput.KEYBOARD, true)
                    .withProperty(com.vandorlabs.blocks.BlockProgrammableInput.UPPER, false), 2);
            configureInputs(world, keyboardPos, 3, 3);
            setInputSmall(world, keyboardPos, true);

            BlockPos halfPos = new BlockPos(GALLERY_X + 4, GALLERY_Y, -18);
            place(world, halfPos, ModBlocks.PROGRAMMABLE_HALF_CONSOLE,
                    BlockAnimatedScreenSelector.FACING, EnumFacing.NORTH);
            configureInputs(world, halfPos, 4, 14);
            return;
        }

        IBlockState full = ModBlocks.PROGRAMMABLE_FULL_INPUT.getDefaultState()
                .withProperty(com.vandorlabs.blocks.BlockProgrammableInput.FACING,
                        EnumFacing.NORTH)
                .withProperty(com.vandorlabs.blocks.BlockProgrammableInput.UPPER, false);
        BlockPos fullWall = new BlockPos(GALLERY_X - 2, GALLERY_Y, -18);
        world.setBlockState(fullWall, full.withProperty(
                com.vandorlabs.blocks.BlockProgrammableInput.KEYBOARD, false), 2);
        configureScreen(world, fullWall, "engineering_screen");
        BlockPos fullFloor = new BlockPos(GALLERY_X + 2, GALLERY_Y, -18);
        world.setBlockState(fullFloor, full.withProperty(
                com.vandorlabs.blocks.BlockProgrammableInput.KEYBOARD, true), 2);
        configureScreen(world, fullFloor, "engineering_screen");
    }

    private static Block block(String id) {
        Block result = Block.REGISTRY.getObject(new ResourceLocation("vandorlabs", id));
        if (result == null || result == Blocks.AIR)
            throw new IllegalStateException("gallery block unavailable: " + id);
        return result;
    }

    private static void placePropulsionRow(World world, String[] ids, int y,
            boolean particles) {
        for (int i = 0; i < ids.length; i++) {
            BlockPos pos = new BlockPos(GALLERY_X - 6 + i * 4, y, -18);
            Block block = block(ids[i]);
            world.setBlockState(pos, block.getDefaultState().withProperty(
                    com.vandorlabs.blocks.BlockPropulsionLight.FACING,
                    EnumFacing.NORTH), 2);
            TileEntity tile = world.getTileEntity(pos);
            if (particles && tile instanceof com.vandorlabs.tiles.TileEntityRedstoneLight)
                ((com.vandorlabs.tiles.TileEntityRedstoneLight) tile)
                        .setParticleStreamSelected(true);
        }
    }

    private static void place(World world, BlockPos pos, Block block,
            IProperty<EnumFacing> facingProp, EnumFacing facing) {
        if (block == null) {
            System.out.println("[vandorlabs][reprolab] null block at " + pos);
            return;
        }
        IBlockState state = block.getDefaultState();
        if (facingProp != null && state.getProperties().containsKey(facingProp)) {
            state = state.withProperty(facingProp, facing);
        }
        world.setBlockState(pos, state, 2);
        if (block instanceof BlockAnimatedScreenSelector) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntityAnimatedScreenSelector) {
                TileEntityAnimatedScreenSelector sel = (TileEntityAnimatedScreenSelector) te;
                sel.setSelectedScreen("engineering_screen");
                // Deterministic art makes pixel assertions independent of
                // the animation frame reached on a slow CI host.
                sel.setDisplayMode(TileEntityAnimatedScreenSelector.MODE_STATIC);
                sel.setFramed(true);
                world.notifyBlockUpdate(pos, state, state, 3);
                System.out.println("[vandorlabs][reprolab] selector tile configured at " + pos);
            }
        }
    }

    private static void placeDiagonal(World world, BlockPos pos, boolean inverted) {
        IBlockState state = ModBlocks.PROGRAMMABLE_DIAGONAL_SCREEN.getDefaultState()
                .withProperty(com.vandorlabs.blocks.BlockProgrammableDiagonalScreen.FACING,
                        EnumFacing.NORTH)
                .withProperty(com.vandorlabs.blocks.BlockProgrammableDiagonalScreen.INVERTED,
                        inverted);
        world.setBlockState(pos, state, 2);
        TileEntity raw = world.getTileEntity(pos);
        if (raw instanceof TileEntityAnimatedScreenSelector) {
            TileEntityAnimatedScreenSelector selector =
                    (TileEntityAnimatedScreenSelector) raw;
            selector.setSelectedScreen("engineering_screen");
            selector.setDisplayMode(TileEntityAnimatedScreenSelector.MODE_STATIC);
            selector.setFramed(true);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    private static void configureInputs(World world, BlockPos pos, int front, int rear) {
        TileEntity raw = world.getTileEntity(pos);
        if (raw instanceof TileEntityAnimatedScreenSelector) {
            TileEntityAnimatedScreenSelector te = (TileEntityAnimatedScreenSelector) raw;
            te.setInputPanel(TileEntityAnimatedScreenSelector.INPUT_PANELS[front]);
            te.setSecondaryInputPanel(TileEntityAnimatedScreenSelector.INPUT_PANELS[rear]);
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    private static void setInputWallPosition(World world, BlockPos pos, int wallPosition) {
        TileEntity raw = world.getTileEntity(pos);
        if (raw instanceof TileEntityAnimatedScreenSelector) {
            TileEntityAnimatedScreenSelector te = (TileEntityAnimatedScreenSelector) raw;
            te.setWallPosition(wallPosition);
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    private static void setInputSmall(World world, BlockPos pos, boolean small) {
        TileEntity raw = world.getTileEntity(pos);
        if (raw instanceof TileEntityAnimatedScreenSelector) {
            TileEntityAnimatedScreenSelector te = (TileEntityAnimatedScreenSelector) raw;
            te.setSmallInput(small);
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    private static void rebuildGuiFixture(Minecraft mc, BlockPos pos, Block block) {
        World server = mc.getIntegratedServer().getWorld(0);
        server.setBlockState(pos, block.getDefaultState(), 2);
        mc.world.setBlockState(pos, block.getDefaultState(), 2);
    }

    private static void beginShot(Minecraft mc, Shot s, boolean first) {
        EntityPlayer p = mc.player;
        EntityPlayerMP serverPlayer = mc.getIntegratedServer().getPlayerList()
                .getPlayerByUsername(p.getName());
        if (serverPlayer != null) {
            serverPlayer.connection.setPlayerLocation(
                    s.x, s.y, s.z, s.yaw, s.pitch);
        }
        p.setLocationAndAngles(s.x, s.y, s.z, s.yaw, s.pitch);
        p.setPositionAndUpdate(s.x, s.y, s.z);
        p.motionX = 0.0D;
        p.motionY = 0.0D;
        p.motionZ = 0.0D;
        p.onGround = true;
        p.setAir(300);
        mc.setRenderViewEntity(p);
        if (s.name.startsWith("gallery_")) {
            mc.getIntegratedServer().addScheduledTask(() ->
                    buildGalleryStage(mc.getIntegratedServer().getWorld(0), s.name));
            buildGalleryStage(mc.world, s.name);
        }
        if (s.name.equals("wide_ship_pair")) {
            // Runtime checks mutate nearby blocks. In a reused integrated
            // world their client updates can arrive after platform setup, so
            // restore this fixture on both sides immediately before capture.
            rebuildWidePair(mc.getIntegratedServer().getWorld(0));
            rebuildWidePair(mc.world);
        }
        if (s.animationFrame >= 0) {
            // Arrange for the requested play-order slot after the fixed
            // camera settle. Engineering has six authored frames.
            int phase = Math.floorMod(s.animationFrame, 6);
            long startTime = 120000L + phase * 20L - CAPTURE_SETTLE_TICKS;
            mc.world.setWorldTime(startTime);
            World serverWorld = mc.getIntegratedServer().getWorld(0);
            serverWorld.setWorldTime(startTime);
            configureAnimation(mc.world.getTileEntity(SELECTOR));
            configureAnimation(serverWorld.getTileEntity(SELECTOR));
        }
        if (s.inputPanel != null) {
            configureInput(mc.world.getTileEntity(CONSOLE), s.inputPanel);
            configureInput(mc.getIntegratedServer().getWorld(0).getTileEntity(CONSOLE),
                    s.inputPanel);
        }
        if (s.name.startsWith("input_surface_")) {
            int mode = s.name.equals("input_surface_off")
                    ? TileEntityAnimatedScreenSelector.MODE_OFF
                    : (s.name.equals("input_surface_static")
                            ? TileEntityAnimatedScreenSelector.MODE_STATIC
                            : TileEntityAnimatedScreenSelector.MODE_ANIMATED);
            World serverWorld = mc.getIntegratedServer().getWorld(0);
            configureInputMode(mc.world.getTileEntity(INPUT_KEYBOARD), mode);
            configureInputMode(serverWorld.getTileEntity(INPUT_KEYBOARD), mode);
        }
        if (s.name.startsWith("controller_")) {
            World sw=mc.getIntegratedServer().getWorld(0);
            int group=Integer.parseInt(s.name.split("_")[1]);
            BlockPos root=ControllerRuntimeChecks.FIXTURE.add(group*8,0,0);
            com.vandorlabs.tiles.TileEntityRampController controller=(com.vandorlabs.tiles.TileEntityRampController)sw.getTileEntity(root);
            if (s.name.endsWith("opening_mid")) {
                sw.setBlockState(root.north(),Blocks.REDSTONE_BLOCK.getDefaultState(),3);
                controller.updatePower();
                ControllerRuntimeChecks.elapsed(controller,controller.durationTicks()/2);
            } else if (s.name.endsWith("_open")) ControllerRuntimeChecks.elapsed(controller,controller.durationTicks()+1);
        }
        System.out.println("[vandorlabs][reprolab] " + (first ? "first " : "") + "shot "
                + s.name + " cam=" + s.cam());
    }

    private static void rebuildWidePair(World world) {
        BlockPos.getAllInBox(new BlockPos(-10, Y, 5),
                new BlockPos(-6, Y + 2, 8)).forEach(world::setBlockToAir);
        place(world, WIDE_LEFT, ModBlocks.ANIMATED_SCREEN_SELECTOR,
                BlockAnimatedScreenSelector.FACING, EnumFacing.NORTH);
        place(world, WIDE_RIGHT, ModBlocks.ANIMATED_SCREEN_SELECTOR,
                BlockAnimatedScreenSelector.FACING, EnumFacing.NORTH);
        configureScreen(world, WIDE_LEFT, "ship_rounded_wide_1_left");
        configureScreen(world, WIDE_RIGHT, "ship_rounded_wide_1_right");
    }

    private static void configureAnimation(TileEntity raw) {
        if (raw instanceof TileEntityAnimatedScreenSelector) {
            TileEntityAnimatedScreenSelector selector =
                    (TileEntityAnimatedScreenSelector) raw;
            selector.setDisplayMode(TileEntityAnimatedScreenSelector.MODE_ANIMATED);
            selector.setAnimationSpeedIndex(0);
        }
    }

    private static void configureInputMode(TileEntity raw, int mode) {
        if (raw instanceof TileEntityAnimatedScreenSelector) {
            TileEntityAnimatedScreenSelector te = (TileEntityAnimatedScreenSelector) raw;
            te.setInputPanel("analysis_framed_gas_giant");
            te.setDisplayMode(mode);
            te.setAnimationSpeedIndex(0);
            if (te.getWorld() != null) {
                IBlockState state = te.getWorld().getBlockState(te.getPos());
                te.getWorld().notifyBlockUpdate(te.getPos(), state, state, 3);
            }
        }
    }

    private static void configureInput(TileEntity raw, String inputPanel) {
        if (raw instanceof TileEntityAnimatedScreenSelector) {
            ((TileEntityAnimatedScreenSelector) raw).setInputPanel(inputPanel);
        }
    }

    private static void configureScreen(World world, BlockPos pos, String screenId) {
        TileEntity raw = world.getTileEntity(pos);
        if (raw instanceof TileEntityAnimatedScreenSelector) {
            TileEntityAnimatedScreenSelector selector =
                    (TileEntityAnimatedScreenSelector) raw;
            selector.setSelectedScreen(screenId);
            selector.setDisplayMode(TileEntityAnimatedScreenSelector.MODE_STATIC);
            selector.setFramed(false);
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    private static void placeDoor(World world, BlockPos pos, String id, boolean open) {
        Block block = Block.REGISTRY.getObject(
                new ResourceLocation("vandorlabs", id));
        if (!(block instanceof BlockVandorDoor)) {
            throw new IllegalStateException("render lab door unavailable: " + id);
        }
        IBlockState lower = block.getDefaultState()
                .withProperty(BlockVandorDoor.FACING, EnumFacing.NORTH)
                .withProperty(BlockVandorDoor.HALF, BlockDoor.EnumDoorHalf.LOWER)
                .withProperty(BlockVandorDoor.HINGE, BlockDoor.EnumHingePosition.LEFT)
                .withProperty(BlockVandorDoor.OPEN, open);
        world.setBlockState(pos, lower, 2);
        world.setBlockState(pos.up(), lower.withProperty(BlockVandorDoor.HALF,
                BlockDoor.EnumDoorHalf.UPPER), 2);
    }

    private static void setDoorHinge(World world, BlockPos pos,
            BlockDoor.EnumHingePosition hinge) {
        for (BlockPos part : new BlockPos[] {pos, pos.up()}) {
            IBlockState state = world.getBlockState(part);
            if (state.getBlock() instanceof BlockVandorDoor) {
                world.setBlockState(part, state.withProperty(BlockVandorDoor.HINGE, hinge), 2);
            }
        }
    }

    private static void setDoorOpen(World world, BlockPos lower, boolean open) {
        for (BlockPos pos : new BlockPos[] { lower, lower.up() }) {
            IBlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof BlockVandorDoor) {
                world.setBlockState(pos, state.withProperty(BlockVandorDoor.OPEN, open), 2);
            }
        }
    }

    private void save(Minecraft mc, Shot s) {
        saveNamed(mc, s.name);
    }

    private void saveNamed(Minecraft mc, String name) {
        try {
            BufferedImage img = ScreenShotHelper.createScreenshot(
                    mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            File f = new File(outDir, "shot_" + name + ".png");
            ImageIO.write(img, "png", f);
            System.out.println("[vandorlabs][reprolab] wrote " + f.getName());
        } catch (Exception e) {
            System.err.println("[vandorlabs][reprolab] save failed for " + name + ": " + e);
        }
    }

    private void writeManifest() {
        try {
            StringBuilder sb = new StringBuilder(
                    "name\tposX\tposY\tposZ\tcamX\tcamY\tcamZ\tyaw\tpitch\n");
            for (int i = 0; i < SHOTS.size(); i++) {
                Shot s = SHOTS.get(i);
                BlockPos b = blockForShot(s.name);
                sb.append(s.name).append('\t').append(b.getX()).append('\t')
                        .append(b.getY()).append('\t').append(b.getZ()).append('\t')
                        .append(s.cam()).append('\n');
            }
            java.nio.file.Files.write(
                    new File(outDir, "manifest.tsv").toPath(), sb.toString().getBytes("UTF-8"));
        } catch (Exception e) {
            System.err.println("[vandorlabs][reprolab] manifest failed: " + e);
        }
    }

    private static BlockPos blockForShot(String name) {
        if (name.equals("programmable_walls") || name.equals("programmable_wall_side")
                || name.startsWith("programmable_porthole")
                || name.equals("programmable_porthole_joined")
                || name.equals("programmable_porthole_vertical")
                || name.equals("programmable_porthole_single")
                || name.startsWith("programmable_corner"))
            return WALL_DISPLAY;
        if (name.startsWith("wide_ship")) return WIDE_LEFT;
        if (name.equals("input_wall")) return INPUT_WALL;
        if (name.equals("input_keyboard")) return INPUT_KEYBOARD;
        if (name.startsWith("input_surface_")) return INPUT_KEYBOARD;
        if (name.startsWith("half_console")) return HALF_CONSOLE;
        if (name.startsWith("cruiser_grid")) return CRUISER_GRID;
        if (name.equals("full_input_wall")) return FULL_INPUT_WALL;
        if (name.equals("full_input_floor")) return FULL_INPUT_FLOOR;
        if (name.startsWith("control")) return CONTROL;
        if (name.startsWith("console")) return CONSOLE;
        if (name.equals("diagonal_up")) return DIAGONAL_UP;
        if (name.startsWith("diagonal_down")) return DIAGONAL_DOWN;
        if (name.startsWith("diagonal")) return DIAGONAL;
        if (name.startsWith("bridge_chairs")) return CHAIR_COMPANION;
        if (name.startsWith("material_grid")) return MATERIAL_GRID;
        return SELECTOR;
    }
}
