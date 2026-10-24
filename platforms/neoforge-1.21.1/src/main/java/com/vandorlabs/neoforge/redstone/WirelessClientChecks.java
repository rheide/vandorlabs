package com.vandorlabs.neoforge.redstone;

import com.vandorlabs.neoforge.ModernClientRuntimeChecks;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Opt-in actual baked endpoints and native dialog input/roundtrip checks. */
public final class WirelessClientChecks {
    private static final BlockPos TX = new BlockPos(10, 80, 0), RX = new BlockPos(14, 80, 0);
    private static int stage, started, expectedOn, expectedOff;
    private static String originalChannels;
    private static int originalOn, originalOff;
    private static int glyphScale, glyphLevel;
    private static final com.google.gson.JsonObject glyphMetadata = new com.google.gson.JsonObject();
    private WirelessClientChecks() { }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException("Wireless client: " + message); }
    private static Object field(Object object, String name) {
        try { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private static List<AbstractSliderButton> sliders(WirelessScreen screen) {
        List<AbstractSliderButton> result = new ArrayList<>();
        for (var child : screen.children()) if (child instanceof AbstractSliderButton slider) result.add(slider);
        return result;
    }
    private static void drag(WirelessScreen screen, AbstractSliderButton slider, int level) {
        double x = slider.getX() + 4 + (slider.getWidth() - 8) * level / 15D, y = slider.getY() + 10;
        require(screen.mouseClicked(x, y, 0), "level slider click");
        screen.mouseDragged(x, y, 0, 0, 0); screen.mouseReleased(x, y, 0);
    }
    private static String glyphName(String mode) { return "modern-wireless-on" + glyphLevel + "-scale" + glyphScale + "-" + mode + ".png"; }
    private static boolean captured(Minecraft mc, String mode) { return ModernClientRuntimeChecks.captured(glyphName(mode)); }
    private static void glyphMetadata(Minecraft mc, WirelessScreen screen) {
        AbstractSliderButton slider = sliders(screen).get(0); int textWidth = mc.font.width(slider.getMessage());
        com.google.gson.JsonObject data = new com.google.gson.JsonObject();
        data.addProperty("screen_width", screen.width); data.addProperty("screen_height", screen.height);
        int labelX = slider.getX() + slider.getWidth() / 2 - textWidth / 2;
        data.addProperty("label_x", labelX); data.addProperty("label_width", textWidth);
        data.addProperty("glyph_x", labelX + mc.font.width("On level: "));
        data.addProperty("glyph_y", slider.getY() + (slider.getHeight() - mc.font.lineHeight) / 2);
        data.addProperty("glyph_width", mc.font.width(Integer.toString(glyphLevel))); data.addProperty("glyph_height", mc.font.lineHeight);
        glyphMetadata.add("level" + glyphLevel + "-scale" + glyphScale, data);
        try {
            java.nio.file.Files.writeString(new java.io.File(mc.gameDirectory, "screenshots/modern-wireless-glyphs.json").toPath(), glyphMetadata.toString());
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
    }
    private static void move(Minecraft mc, BlockPos pos) {
        mc.player.closeContainer(); mc.player.setShiftKeyDown(false);
        mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
        mc.player.setPos(pos.getX() + .5, 81, -3); mc.player.setYRot(0); mc.player.setXRot(31);
        mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(pos.getX() + .5, 81, -3, true));
    }
    private static void open(Minecraft mc, BlockPos pos) {
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0, -.5), Direction.NORTH, pos, false));
    }
    private static void geometry(Minecraft mc, BlockPos pos, String name) {
        var state = mc.level.getBlockState(pos); var model = mc.getBlockRenderer().getBlockModel(state); int count = 0;
        for (Direction face : Direction.values()) {
            var quads = model.getQuads(state, face, RandomSource.create(0), ModelData.EMPTY, null);
            require(quads.size() == 1, name + " one authored square per cube face " + face);
            for (var quad : quads) {
                count++; String sprite = quad.getSprite().contents().name().toString();
                require(sprite.startsWith("vandorlabs:block/" + name + "/") && !sprite.contains("missingno"), name + " missing native sprite " + sprite);
                if (face == state.getValue(WirelessBlock.FACING)) require(sprite.endsWith("/port"), name + " port artwork must match wired facing");
                if (face == state.getValue(WirelessBlock.FACING).getOpposite()) require(sprite.endsWith("/type"), name + " type artwork opposite port");
                int[] vertices = quad.getVertices(); int stride = vertices.length / 4;
                for (int vertex = 0; vertex < 4; vertex++) for (int axis = 0; axis < 3; axis++) {
                    float value = Float.intBitsToFloat(vertices[vertex * stride + axis]);
                    require(Float.isFinite(value) && (Math.abs(value) < .00001F || Math.abs(value - 1) < .00001F), name + " native cube vertex boundaries");
                }
            }
        }
        require(count == 6, name + " six rendered faces");
        var item = name.equals("redstone_transmitter") ? WirelessContent.TRANSMITTER_ITEM.get() : WirelessContent.RECEIVER_ITEM.get();
        var itemModel = mc.getItemRenderer().getModel(item.getDefaultInstance(), mc.level, mc.player, 0);
        var scale = itemModel.getTransforms().getTransform(net.minecraft.world.item.ItemDisplayContext.GUI).scale;
        require(scale.x() == .5F && scale.y() == .5F && scale.z() == .5F, name + " authored padded inventory scale");
    }
    public static boolean tick(Minecraft mc, int tick) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return false;
        require(tick - started < 400, "native menu roundtrip timed out at stage " + stage);
        if (stage == 0) {
            for (int level : new int[]{8, 9}) for (int scale : new int[]{2, 3}) for (String mode : new String[]{"actual", "reference", "negative", "vanilla"}) {
                java.io.File previous = new java.io.File(mc.gameDirectory, "screenshots/modern-wireless-on" + level + "-scale" + scale + "-" + mode + ".png");
                if (previous.exists()) require(previous.delete(), "remove owned stale glyph capture");
            }
            mc.player.closeContainer(); mc.player.setShiftKeyDown(false);
            mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
            mc.player.setPos(12.5, 80, -5.5); mc.player.setDeltaMovement(Vec3.ZERO); mc.player.setYRot(0); mc.player.setXRot(11);
            mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(12.5, 80, -5.5, true));
            geometry(mc, TX, "redstone_transmitter"); geometry(mc, RX, "redstone_receiver");
            stage = 90; started = tick; return false;
        }
        if (stage == 90) {
            if (tick - started < 20) return false;
            require(Math.abs(mc.player.getX() - 12.5) < .05 && Math.abs(mc.player.getZ() + 5.5) < .05, "wide endpoint capture camera position");
            ModernClientRuntimeChecks.capture("modern-wireless-world.png"); stage = 91; started = tick; return false;
        }
        if (stage == 91) { if (tick - started < 10) return false; move(mc, RX); stage = 1; started = tick; return false; }
        if (stage == 1) { if (tick - started < 10) return false; open(mc, RX); stage = 2; started = tick; return false; }
        if (stage == 2) {
            if (!(mc.screen instanceof WirelessScreen screen)) return false;
            EditBox channels = (EditBox) field(screen, "channels"); Button signal = (Button) field(screen, "signalButton"), port = (Button) field(screen, "faceButton");
            require(signal.getMessage().getString().equals("Signal: Digital") && port.getMessage().getString().equals("Output port: up"), "receiver initial saved labels");
            require(sliders(screen).size() == 2 && signal.getY() == channels.getY() + 28 && port.getY() == signal.getY() + 26, "actual receiver field order");
            originalChannels = channels.getValue(); originalOn = (Integer) field(screen, "on"); originalOff = (Integer) field(screen, "off");
            int face = (Integer) field(screen, "face");
            screen.mouseClicked(port.getX() + 3, port.getY() + 3, 1);
            require((Integer) field(screen, "face") == Math.floorMod(face - 1, 6), "right click previous six-face port");
            port = (Button) field(screen, "faceButton"); screen.mouseClicked(port.getX() + 3, port.getY() + 3, 0);
            require((Integer) field(screen, "face") == face, "left/right port symmetry");
            signal = (Button) field(screen, "signalButton"); screen.mouseClicked(signal.getX() + 3, signal.getY() + 3, 1);
            require((Boolean) field(screen, "analog") && !sliders(screen).get(0).active, "analog disables digital on slider");
            signal = (Button) field(screen, "signalButton"); screen.mouseClicked(signal.getX() + 3, signal.getY() + 3, 0);
            require(!(Boolean) field(screen, "analog") && sliders(screen).get(0).active, "digital restores on slider");
            channels = (EditBox) field(screen, "channels"); channels.setValue("");
            screen.mouseClicked(channels.getX() + 4, channels.getY() + 4, 0);
            for (char digit : "101, 102".toCharArray()) require(screen.charTyped(digit, 0), "actual channel-field typing");
            require(channels.getValue().equals("101, 102"), "channel text retained");
            screen.charTyped('x', 0); require(channels.getValue().equals("101, 102"), "channel filter rejects non-digits");
            drag(screen, sliders(screen).get(0), 6); drag(screen, sliders(screen).get(1), 3);
            expectedOn = (Integer) field(screen, "on"); expectedOff = (Integer) field(screen, "off");
            require(expectedOn == 6 && expectedOff == 3, "native slider levels"); stage = 3; started = tick; return false;
        }
        if (stage == 3) {
            WirelessBlockEntity tile = (WirelessBlockEntity) mc.level.getBlockEntity(RX);
            if (tile == null || tile.onLevel() != expectedOn || tile.offLevel() != expectedOff || !tile.getRedstoneChannels().equals(com.vandorlabs.redstone.ChannelList.of(101, 102))) return false;
            require(mc.screen instanceof WirelessScreen, "receiver menu retained during server roundtrip");
            WirelessScreen screen = (WirelessScreen) mc.screen;
            ((EditBox) field(screen, "channels")).setValue(originalChannels);
            drag(screen, sliders(screen).get(0), originalOn); drag(screen, sliders(screen).get(1), originalOff);
            stage = 4; started = tick; return false;
        }
        if (stage == 4) {
            WirelessBlockEntity tile = (WirelessBlockEntity) mc.level.getBlockEntity(RX);
            if (tile.onLevel() != originalOn || tile.offLevel() != originalOff || !tile.getRedstoneChannels().toString().equals(originalChannels)) return false;
            ModernClientRuntimeChecks.capture("modern-wireless-receiver-dialog.png"); stage = 40; started = tick; return false;
        }
        if (stage == 40) {
            if (tick - started < 15) return false;
            glyphScale = 2; glyphLevel = 8; mc.options.guiScale().set(glyphScale); mc.resizeDisplay();
            WirelessScreen screen = (WirelessScreen) mc.screen;
            drag(screen, sliders(screen).get(0), glyphLevel); drag(screen, sliders(screen).get(1), 3);
            ((EditBox) field(screen, "channels")).setFocused(false); screen.setFocused(null);
            stage = 41; started = tick; return false;
        }
        if (stage == 41) {
            WirelessBlockEntity tile = (WirelessBlockEntity) mc.level.getBlockEntity(RX);
            if (tick - started < 5 || tile.onLevel() != glyphLevel || tile.offLevel() != 3) return false;
            WirelessScreen screen = (WirelessScreen) mc.screen;
            require(sliders(screen).get(0).getMessage().getString().equals("On level: " + glyphLevel), "normalized actual slider label");
            WirelessScreen.glyphCaptureMode = 0; glyphMetadata(mc, screen); ModernClientRuntimeChecks.capture(glyphName("actual"));
            stage = 42; started = tick; return false;
        }
        if (stage == 42) {
            if (!captured(mc, "actual")) return false;
            WirelessScreen.glyphCaptureMode = 1; ModernClientRuntimeChecks.capture(glyphName("reference")); stage = 43; started = tick; return false;
        }
        if (stage == 43) {
            if (!captured(mc, "reference")) return false;
            WirelessScreen.glyphCaptureMode = 2; ModernClientRuntimeChecks.capture(glyphName("negative")); stage = 44; started = tick; return false;
        }
        if (stage == 44) {
            if (!captured(mc, "negative")) return false;
            WirelessScreen.glyphCaptureMode = 3; ModernClientRuntimeChecks.capture(glyphName("vanilla")); stage = 46; started = tick; return false;
        }
        if (stage == 46) {
            if (!captured(mc, "vanilla")) return false;
            WirelessScreen.glyphCaptureMode = 0;
            if (glyphScale == 2) {
                glyphScale = 3; mc.options.guiScale().set(glyphScale); mc.resizeDisplay();
                ((EditBox) field(mc.screen, "channels")).setFocused(false); mc.screen.setFocused(null);
                stage = 41; started = tick; return false;
            }
            if (glyphLevel == 8) {
                glyphLevel = 9; glyphScale = 2; mc.options.guiScale().set(glyphScale); mc.resizeDisplay();
                WirelessScreen screen = (WirelessScreen) mc.screen; drag(screen, sliders(screen).get(0), glyphLevel);
                ((EditBox) field(screen, "channels")).setFocused(false); screen.setFocused(null);
                stage = 41; started = tick; return false;
            }
            WirelessScreen screen = (WirelessScreen) mc.screen;
            drag(screen, sliders(screen).get(0), originalOn); drag(screen, sliders(screen).get(1), originalOff);
            mc.options.guiScale().set(2); mc.resizeDisplay(); stage = 45; started = tick; return false;
        }
        if (stage == 45) {
            WirelessBlockEntity tile = (WirelessBlockEntity) mc.level.getBlockEntity(RX);
            if (tile.onLevel() != originalOn || tile.offLevel() != originalOff) return false;
            System.out.println("[vandorlabs][modern] wireless-glyph-captures PASS normalized On 8/9 at GUI scales 2/3, displaced-thumb references and wrong-order negative controls");
            stage = 5; started = tick; return false;
        }
        if (stage == 5) { if (tick - started < 15) return false; move(mc, TX); stage = 6; started = tick; return false; }
        if (stage == 6) { if (tick - started < 10) return false; open(mc, TX); stage = 7; started = tick; return false; }
        if (stage == 7) {
            if (!(mc.screen instanceof WirelessScreen screen)) return false;
            require(sliders(screen).isEmpty(), "transmitter must omit receiver level sliders");
            Button signal = (Button) field(screen, "signalButton"), port = (Button) field(screen, "faceButton");
            require(signal.getMessage().getString().equals("Signal: Analog") && port.getMessage().getString().equals("Input port: west"), "transmitter saved labels/options");
            screen.mouseClicked(signal.getX() + 3, signal.getY() + 3, 1); require(!(Boolean) field(screen, "analog"), "transmitter digital cycle");
            signal = (Button) field(screen, "signalButton"); screen.mouseClicked(signal.getX() + 3, signal.getY() + 3, 0);
            ModernClientRuntimeChecks.capture("modern-wireless-transmitter-dialog.png"); stage = 8; started = tick; return false;
        }
        if (stage == 8) {
            if (tick - started < 15 || !((WirelessBlockEntity) mc.level.getBlockEntity(TX)).analog()) return false;
            mc.player.closeContainer();
            System.out.println("[vandorlabs][modern] wireless-client PASS actual native six-face model/sprites, port orientation, padded inventory scale, both dialogs, reverse cycling, slider drag and exact server settings roundtrip");
            stage = 9; return true;
        }
        return stage == 9;
    }
}
