package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockAnimatedScreenSelector;
import com.vandorlabs.blocks.BlockDisplayArm;
import com.vandorlabs.container.ContainerAnimatedScreenSelector;
import com.vandorlabs.display.DisplayArmGeometry;
import com.vandorlabs.display.DisplayArmSettings;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.tiles.TileEntityDisplayArm;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Real placement, settings widgets, packet publication and rendered presence in one small scene. */
final class DisplayArmDialogChecks {
    private static final BlockPos ORIGIN = new BlockPos(8, 100, 8);
    private static final BlockPos DIALOG = position(EnumFacing.UP);
    private static final DisplayArmSettings EDITED =
            new DisplayArmSettings(true, true, 12, 8, 16, 45, -30, 15);
    private static final Field BUTTONS = net.minecraftforge.fml.relauncher.ReflectionHelper.findField(
            GuiScreen.class, "buttonList", "field_146292_n");
    private static int stage, ticks, checks, wait;
    private static volatile boolean serverDone, published;
    private static volatile Throwable failure;
    private static LiveGallery gallery;

    private DisplayArmDialogChecks() { }

    private static BlockPos position(EnumFacing face) {
        return ORIGIN.add((face.getIndex() % 3) * 4, 0, (face.getIndex() / 3) * 6);
    }

    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new IllegalStateException("display-arm " + message);
    }

    private interface ServerStep { void run(WorldServer world, EntityPlayerMP player); }

    private static void server(Minecraft mc, ServerStep step) {
        serverDone = false;
        mc.getIntegratedServer().addScheduledTask(() -> {
            try {
                step.run(mc.getIntegratedServer().getWorld(0),
                        mc.getIntegratedServer().getPlayerList().getPlayers().get(0));
                serverDone = true;
            } catch (RuntimeException | LinkageError error) { failure = error; }
        });
    }

    static boolean tick(Minecraft mc, File output) throws Exception {
        if (++ticks > 1800) throw new IllegalStateException("display-arm timed out stage=" + stage
                + " serverDone=" + serverDone + " screen=" + mc.currentScreen
                + " held=" + mc.player.getHeldItemMainhand());
        if (failure != null) throw new IllegalStateException("display-arm server fixture", failure);
        mc.gameSettings.pauseOnLostFocus = false;
        switch (stage) {
            case 0:
                if (ticks < 40) return false;
                server(mc, DisplayArmDialogChecks::build);
                stage = 1;
                return false;
            case 1:
                if (!serverDone) return false;
                for (EnumFacing face : EnumFacing.values()) {
                    if (!(mc.world.getTileEntity(position(face)) instanceof TileEntityDisplayArm)) return false;
                    TileEntityDisplayArm tile = (TileEntityDisplayArm) mc.world.getTileEntity(position(face));
                    if (tile.getMountFace() != face) return false;
                }
                server(mc, (world, player) -> prepareOpen(world, player, false));
                stage = 2;
                return false;
            case 2:
                if (!serverDone || !readyForClick(mc, false)) return false;
                click(mc);
                stage = 20;
                return false;
            case 20:
                if (!serverDone || !(mc.currentScreen instanceof GuiAnimatedScreenSelector)) return false;
                require(((ContainerAnimatedScreenSelector) mc.player.openContainer).getTileEntity()
                        == mc.world.getTileEntity(DIALOG), "ordinary right-click opens the owner dialog");
                verify(mc, (TileEntityDisplayArm) mc.world.getTileEntity(DIALOG));
                wait = 0;
                stage = 3;
                return false;
            case 3:
                if (++wait % 10 != 0) return false;
                if (!published) {
                    server(mc, (world, player) -> published = EDITED.equals(
                            ((TileEntityDisplayArm) world.getTileEntity(DIALOG)).getArmSettings()));
                    return false;
                }
                if (!serverDone) return false;
                mc.player.closeScreen();
                server(mc, (world, player) -> prepareOpen(world, player, true));
                stage = 4;
                wait = 0;
                return false;
            case 4:
                if (!serverDone || !readyForClick(mc, true)) return false;
                click(mc);
                stage = 40;
                return false;
            case 40:
                if (!serverDone || !(mc.currentScreen instanceof GuiAnimatedScreenSelector)) return false;
                require(page((GuiAnimatedScreenSelector) mc.currentScreen) == 11,
                        "survival Configurizer opens the same Arm page");
                if (++wait < 12) return false;
                ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth, mc.displayHeight,
                        mc.getFramebuffer()), "png", new File(output, "display-arm-dialog.png"));
                mc.player.closeScreen();
                server(mc, DisplayArmDialogChecks::prepareGallery);
                stage = 5;
                return false;
            case 5:
                if (!serverDone) return false;
                if (gallery == null) {
                    gallery = new LiveGallery("display-arm", ORIGIN);
                    for (EnumFacing face : EnumFacing.values()) {
                        BlockPos at = position(face).subtract(ORIGIN);
                        gallery.shot(face.getName(), at.getX() - 3.3, -.35,
                                at.getZ() - 1.7, -62, 12);
                    }
                    gallery.shot("visible", 4 - 3.3, -.35, -1.7, -62, 12);
                }
                if (!gallery.tick(mc, output)) return false;
                gallery = null;
                server(mc, (world, player) -> world.setBlockToAir(DIALOG));
                stage = 6;
                return false;
            case 6:
                if (!serverDone) return false;
                if (gallery == null) gallery = new LiveGallery("display-arm", ORIGIN)
                        .shot("removed", 4 - 3.3, -.35, -1.7, -62, 12);
                if (!gallery.tick(mc, output)) return false;
                renderedPresence(output);
                System.out.println("[vandorlabs][reprolab] display-arm PASS checks=" + checks
                        + " six clicked mounts, independent west-facing pose, settings widgets,"
                        + " geometry packets, ordinary/Configurizer dialogs, rendered variants");
                return true;
            default: return false;
        }
    }

    private static void build(WorldServer world, EntityPlayerMP player) {
        player.setGameType(GameType.CREATIVE);
        player.capabilities.isFlying = true;
        player.sendPlayerAbilities();
        world.setWorldTime(6000);
        world.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
        world.getGameRules().setOrCreateGameRule("doWeatherCycle", "false");
        for (BlockPos at : BlockPos.getAllInBox(ORIGIN.add(-4, -3, -4), ORIGIN.add(12, 3, 10)))
            world.setBlockState(at, at.getY() == ORIGIN.getY() - 3
                    ? Blocks.QUARTZ_BLOCK.getDefaultState() : Blocks.AIR.getDefaultState(), 2);
        Block block = Block.getBlockFromName("vandorlabs:programmable_display_arm");
        require(block instanceof BlockDisplayArm, "one display arm block is registered");
        DisplayArmGeometry.Pose reference = null;
        for (EnumFacing face : EnumFacing.values()) {
            BlockPos at = position(face), support = at.offset(face.getOpposite());
            world.setBlockState(support, Blocks.IRON_BLOCK.getDefaultState(), 3);
            player.rotationYaw = -90; // Facing east places the display facing west.
            float x = face == EnumFacing.EAST ? 1 : face == EnumFacing.WEST ? 0 : .5F;
            float y = face == EnumFacing.UP ? 1 : face == EnumFacing.DOWN ? 0 : .5F;
            float z = face == EnumFacing.SOUTH ? 1 : face == EnumFacing.NORTH ? 0 : .5F;
            require(LiveGallery.place(world, player, new ItemStack(block), support, face, x, y, z),
                    "real item placement against " + face);
            require(world.getTileEntity(at) instanceof TileEntityDisplayArm, "owner at clicked face " + face);
            TileEntityDisplayArm tile = (TileEntityDisplayArm) world.getTileEntity(at);
            require(tile.getMountFace() == face, "clicked support is persisted " + face);
            require(tile.getScreenFacing() == EnumFacing.WEST, "screen facing is independent of " + face);
            if (reference == null) reference = tile.geometry().display;
            else require(tile.geometry().display.center.subtract(reference.center).length() < 1e-9
                    && tile.geometry().display.xAxis.subtract(reference.xAxis).length() < 1e-9,
                    "all mounts produce the same display pose");
            tile.setDisplayMode(1);
        }
        player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
        player.connection.setPlayerLocation(DIALOG.getX() - 2, DIALOG.getY(), DIALOG.getZ() + .5, -90, 20);
        System.out.println("[vandorlabs][reprolab] display-arm-placement PASS mounts=6 facing=west");
    }

    private static void prepareOpen(WorldServer world, EntityPlayerMP player, boolean configurizer) {
        player.closeScreen();
        player.setGameType(configurizer ? GameType.SURVIVAL : GameType.CREATIVE);
        player.capabilities.allowFlying = true;
        player.capabilities.isFlying = true;
        player.sendPlayerAbilities();
        player.inventory.currentItem = 0;
        player.setHeldItem(EnumHand.MAIN_HAND, configurizer ? new ItemStack(ModItems.CONFIGURIZER) : ItemStack.EMPTY);
        player.inventoryContainer.detectAndSendChanges();
        player.connection.setPlayerLocation(DIALOG.getX() - 2, DIALOG.getY(), DIALOG.getZ() + .5, -90, 20);
        player.setSneaking(false);
    }

    private static boolean readyForClick(Minecraft mc, boolean configurizer) {
        return mc.playerController.getCurrentGameType() == (configurizer ? GameType.SURVIVAL : GameType.CREATIVE)
                && mc.player.inventory.currentItem == 0
                && (configurizer ? mc.player.getHeldItemMainhand().getItem() == ModItems.CONFIGURIZER
                        : mc.player.getHeldItemMainhand().isEmpty())
                && mc.player.getDistanceSq(DIALOG.getX() - 2, DIALOG.getY(), DIALOG.getZ() + .5) < .025;
    }

    private static void click(Minecraft mc) {
        require(mc.playerController.processRightClickBlock(mc.player, mc.world, DIALOG,
                EnumFacing.WEST, new Vec3d(DIALOG).addVector(.5, .1, .5), EnumHand.MAIN_HAND)
                == EnumActionResult.SUCCESS, "actual synchronized right-click packet");
    }

    /** Actual widgets at a small and a wide logical resolution, including activation-expanded tabs. */
    static void verify(Minecraft mc, TileEntityDisplayArm tile) throws Exception {
        NBTTagCompound original = tile.writeToNBT(new NBTTagCompound());
        for (int mode = 0; mode < 3; mode++) for (int[] size : new int[][]{{320, 240}, {640, 360}}) {
            tile.configureActivation(mode, 0, -1, 1, 1);
            GuiAnimatedScreenSelector gui = new GuiAnimatedScreenSelector(mc.player.inventory, tile);
            mc.displayGuiScreen(gui);
            gui.setWorldAndResolution(mc, size[0], size[1]);
            require(page(gui) == 11 && !button(gui, 199).enabled, "Arm is the initial page");
            rectangles(gui);
            GuiButton direction = button(gui, DisplayArmPane.FACING);
            EnumFacing before = tile.getScreenFacing();
            gui.mouseClicked(direction.x + 2, direction.y + 2, 1);
            require(tile.getScreenFacing() == before.rotateYCCW(), "right-click cycles facing backwards");
            gui.mouseClicked(direction.x + 2, direction.y + 2, 0);
            require(tile.getScreenFacing() == before, "left-click returns the next facing");
            boolean half = tile.getArmSettings().half;
            gui.actionPerformed(button(gui, DisplayArmPane.SIZE));
            require(tile.getArmSettings().half != half, "size toggle previews immediately");
            gui.actionPerformed(button(gui, DisplayArmPane.SIZE));
            String savedInput = tile.getInputPanel();
            gui.actionPerformed(button(gui, DisplayArmPane.INPUT));
            gui.actionPerformed(button(gui, DisplayArmPane.INPUT));
            require(savedInput.equals(tile.getInputPanel()), "hidden input keeps its artwork");
            DisplayArmPane pane = pane(gui);
            GuiTextField[] fields = fields(pane);
            fields[0].setText("33");
            gui.actionPerformed(button(gui, mode == 0 ? 90 : 650));
            require(page(gui) == 11, "out-of-range numeric entry cannot leave the page");
            fields[0].setText("-32");
            require(pane.commit() && tile.getArmSettings().right == -32, "exact signed pixel entry");
            gui.actionPerformed(button(gui, DisplayArmPane.RESET));
            require(tile.getArmSettings().right == DisplayArmSettings.DEFAULT.right
                    && tile.getArmSettings().forward == DisplayArmSettings.DEFAULT.forward,
                    "Reset uses the safe default pose");
            gui.actionPerformed(button(gui, mode == 0 ? 90 : 650));
            require(page(gui) == 0 && button(gui, 199).visible, "Arm remains reachable from display state tabs");
            gui.actionPerformed(button(gui, 199));
            require(page(gui) == 11, "Arm tab returns from display state tabs");
        }
        tile.readFromNBT(original);
        GuiAnimatedScreenSelector gui = new GuiAnimatedScreenSelector(mc.player.inventory, tile);
        mc.displayGuiScreen(gui);
        if (!tile.getArmSettings().half) gui.actionPerformed(button(gui, DisplayArmPane.SIZE));
        if (!tile.getArmSettings().input) gui.actionPerformed(button(gui, DisplayArmPane.INPUT));
        int[] values = {EDITED.right, EDITED.height, EDITED.forward, EDITED.yaw, EDITED.pitch, EDITED.roll};
        GuiTextField[] fields = fields(pane(gui));
        for (int index = 0; index < values.length; index++) fields[index].setText(Integer.toString(values[index]));
        require(pane(gui).commit() && EDITED.equals(tile.getArmSettings()), "all geometry fields preview together");
        System.out.println("[vandorlabs][reprolab] display-arm-dialog PASS sizes=2 activationModes=3");
    }

    @SuppressWarnings("unchecked")
    private static List<GuiButton> buttons(GuiAnimatedScreenSelector gui) throws Exception {
        return (List<GuiButton>) BUTTONS.get(gui);
    }

    private static GuiButton button(GuiAnimatedScreenSelector gui, int id) throws Exception {
        for (GuiButton button : buttons(gui)) if (button.id == id) return button;
        throw new IllegalStateException("display-arm missing widget " + id);
    }

    private static int page(GuiAnimatedScreenSelector gui) throws Exception {
        Field field = GuiAnimatedScreenSelector.class.getDeclaredField("textureTab");
        field.setAccessible(true);
        return field.getInt(gui);
    }

    private static DisplayArmPane pane(GuiAnimatedScreenSelector gui) throws Exception {
        Field field = GuiAnimatedScreenSelector.class.getDeclaredField("armPane");
        field.setAccessible(true);
        return (DisplayArmPane) field.get(gui);
    }

    private static GuiTextField[] fields(DisplayArmPane pane) throws Exception {
        Field field = DisplayArmPane.class.getDeclaredField("fields");
        field.setAccessible(true);
        return (GuiTextField[]) field.get(pane);
    }

    private static void rectangles(GuiAnimatedScreenSelector gui) throws Exception {
        List<java.awt.Rectangle> occupied = new ArrayList<>();
        for (GuiButton button : buttons(gui)) if (button.visible) {
            require(Minecraft.getMinecraft().fontRenderer.getStringWidth(button.displayString) <= button.width - 4,
                    "widget label fits id=" + button.id + " label=" + button.displayString);
            require(button.x >= gui.getGuiLeft() && button.y >= gui.getGuiTop()
                    && button.x + button.width <= gui.getGuiLeft() + gui.getXSize()
                    && button.y + button.height <= gui.getGuiTop() + gui.getYSize(),
                    "widget inside panel id=" + button.id);
            java.awt.Rectangle rectangle = new java.awt.Rectangle(button.x, button.y, button.width, button.height);
            for (java.awt.Rectangle other : occupied) require(!rectangle.intersects(other),
                    "widgets do not overlap id=" + button.id);
            occupied.add(rectangle);
        }
        for (GuiTextField field : fields(pane(gui))) {
            java.awt.Rectangle rectangle = new java.awt.Rectangle(field.x, field.y, field.width, field.height);
            for (java.awt.Rectangle other : occupied) require(!rectangle.intersects(other),
                    "numeric fields do not overlap buttons or each other");
            occupied.add(rectangle);
        }
    }

    private static void prepareGallery(WorldServer world, EntityPlayerMP player) {
        player.setGameType(GameType.CREATIVE);
        player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
        for (EnumFacing face : EnumFacing.values()) {
            TileEntityDisplayArm tile = (TileEntityDisplayArm) world.getTileEntity(position(face));
            int index = face.getIndex();
            tile.setArmSettings(index == 4 ? new DisplayArmSettings(false, true, 4, 3, 8, 35, -20, 15)
                    : new DisplayArmSettings((index & 1) != 0, (index & 2) != 0, 0, 0, 4, 0, 0, 0));
            tile.configureActivation(0, 0, -1, 1, 1);
            tile.setDisplayMode(1);
        }
    }

    private static void renderedPresence(File output) throws Exception {
        BufferedImage before = ImageIO.read(new File(output, "display-arm-visible.png"));
        BufferedImage after = ImageIO.read(new File(output, "display-arm-removed.png"));
        int changed = 0, total = 0;
        for (int y = before.getHeight() / 4; y < before.getHeight() * 3 / 4; y++)
            for (int x = before.getWidth() / 4; x < before.getWidth() * 3 / 4; x++) {
                int a = before.getRGB(x, y), b = after.getRGB(x, y);
                if (Math.abs((a >> 16 & 255) - (b >> 16 & 255))
                        + Math.abs((a >> 8 & 255) - (b >> 8 & 255))
                        + Math.abs((a & 255) - (b & 255)) > 40) changed++;
                total++;
            }
        require(changed > total / 500, "screen and arm render: removal changes " + changed + "/" + total + " pixels");
    }
}
