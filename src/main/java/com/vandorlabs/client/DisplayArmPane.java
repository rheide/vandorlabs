package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockAnimatedScreenSelector;
import com.vandorlabs.display.DisplayArmSettings;
import com.vandorlabs.network.MessageDisplayArm;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.tiles.TileEntityDisplayArm;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.client.config.GuiSlider;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.util.List;

/** Screen-relative pose controls; mounting is captured by placement, never by these sliders. */
@SideOnly(Side.CLIENT)
final class DisplayArmPane {
    static final int SIZE = 710, INPUT = 711, FACING = 712, RESET = 713;
    private static final EnumFacing[] DIRECTIONS = {
            EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST
    };
    private static final String[] LABELS = {"Right", "Height", "Forward", "Yaw", "Tilt", "Roll"};
    private static final int[] MINIMUM = {-32, -32, -32, -180, -90, -180};
    private static final int[] MAXIMUM = {32, 32, 32, 180, 90, 180};

    private final TileEntityDisplayArm tile;
    private final GuiTextField[] fields = new GuiTextField[6];
    private final GuiSlider[] sliders = new GuiSlider[6];
    private DisplayArmSettings settings;
    private EnumFacing facing;
    private ProgrammableDialogLayout layout;
    private GuiButton sizeButton, inputButton, facingButton;
    private int columnWidth, controlsTop, rowGap;

    DisplayArmPane(TileEntityDisplayArm tile) {
        this.tile = tile;
    }

    void init(ProgrammableDialogLayout layout, List<GuiButton> buttons) {
        this.layout = layout;
        settings = tile.getArmSettings();
        facing = tile.getWorld().getBlockState(tile.getPos()).getValue(BlockAnimatedScreenSelector.FACING);
        if (facing.getAxis().isVertical()) facing = EnumFacing.NORTH;
        int left = layout.left + 12, top = layout.top + 54;
        int choiceWidth = (layout.width - 28) / 3;
        sizeButton = new GuiButton(SIZE, left, top, choiceWidth - 2, 20, "");
        inputButton = new GuiButton(INPUT, left + choiceWidth, top, choiceWidth - 2, 20, "");
        facingButton = new GuiButton(FACING, left + choiceWidth * 2, top,
                layout.width - 24 - choiceWidth * 2, 20, "");
        buttons.add(sizeButton);
        buttons.add(inputButton);
        buttons.add(facingButton);
        columnWidth = (layout.width - 32) / 2;
        controlsTop = layout.top + 93;
        rowGap = Math.max(20, Math.min(25, (layout.doneY - controlsTop - 14) / 3));
        for (int index = 0; index < fields.length; index++) {
            final int setting = index;
            int x = left + (index / 3) * (columnWidth + 8);
            int y = controlsTop + (index % 3) * rowGap;
            int fieldWidth = 34, sliderWidth = columnWidth - fieldWidth - 4;
            fields[index] = new GuiTextField(730 + index, Minecraft.getMinecraft().fontRenderer,
                    x + sliderWidth + 4, y + 1, fieldWidth, 18);
            fields[index].setMaxStringLength(4);
            fields[index].setValidator(value -> value.matches("-?[0-9]*"));
            fields[index].setText(Integer.toString(value(index)));
            sliders[index] = new GuiSlider(720 + index, x, y, sliderWidth, 20, "", "",
                    MINIMUM[index], MAXIMUM[index], value(index), false, true, slider -> {
                        int next = slider.getValueInt();
                        slider.setValue(next);
                        slider.displayString = label(setting, next);
                        fields[setting].setText(Integer.toString(next));
                        fields[setting].setTextColor(0xE0E0E0);
                        if (next != value(setting)) {
                            replace(setting, next);
                            send();
                        }
                    });
            sliders[index].displayString = label(index, value(index));
            buttons.add(sliders[index]);
        }
        buttons.add(new GuiButton(RESET, left, layout.doneY,
                Math.max(60, layout.controlsX - left - 8), 20, "Reset pose"));
        refreshChoices();
    }

    private void refreshChoices() {
        sizeButton.displayString = settings.half ? "Half height" : "Full height";
        inputButton.displayString = "Input: " + (settings.input ? "On" : "Off");
        facingButton.displayString = "Faces: " + title(facing);
    }

    private static String title(EnumFacing direction) {
        String name = direction.getName();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String label(int index, int value) {
        return LABELS[index] + ": " + value + (index < 3 ? " px" : "\u00b0");
    }

    private int value(int index) {
        switch (index) {
            case 0: return settings.right;
            case 1: return settings.height;
            case 2: return settings.forward;
            case 3: return settings.yaw;
            case 4: return settings.pitch;
            default: return settings.roll;
        }
    }

    private void replace(int index, int value) {
        settings = new DisplayArmSettings(settings.half, settings.input,
                index == 0 ? value : settings.right,
                index == 1 ? value : settings.height,
                index == 2 ? value : settings.forward,
                index == 3 ? value : settings.yaw,
                index == 4 ? value : settings.pitch,
                index == 5 ? value : settings.roll);
    }

    private void send() {
        tile.setArmSettings(settings);
        IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        if (state.getValue(BlockAnimatedScreenSelector.FACING) != facing) {
            tile.getWorld().setBlockState(tile.getPos(),
                    state.withProperty(BlockAnimatedScreenSelector.FACING, facing), 2);
        }
        PacketHandler.INSTANCE.sendToServer(new MessageDisplayArm(tile.getPos(), facing, settings));
    }

    boolean action(GuiButton button) {
        if (button.id >= 720 && button.id <= 725) return true;
        if (button.id == SIZE) {
            settings = new DisplayArmSettings(!settings.half, settings.input,
                    settings.right, settings.height, settings.forward,
                    settings.yaw, settings.pitch, settings.roll);
        } else if (button.id == INPUT) {
            settings = new DisplayArmSettings(settings.half, !settings.input,
                    settings.right, settings.height, settings.forward,
                    settings.yaw, settings.pitch, settings.roll);
        } else if (button.id == FACING) {
            int index = 0;
            while (DIRECTIONS[index] != facing && index < DIRECTIONS.length - 1) index++;
            facing = DIRECTIONS[GuiOptionCycle.next(index, DIRECTIONS.length)];
        } else if (button.id == RESET) {
            DisplayArmSettings defaults = DisplayArmSettings.DEFAULT;
            settings = new DisplayArmSettings(settings.half, settings.input,
                    defaults.right, defaults.height, defaults.forward,
                    defaults.yaw, defaults.pitch, defaults.roll);
            for (int index = 0; index < fields.length; index++) {
                fields[index].setText(Integer.toString(value(index)));
                fields[index].setTextColor(0xE0E0E0);
                sliders[index].setValue(value(index));
                sliders[index].displayString = label(index, value(index));
            }
        } else return false;
        refreshChoices();
        send();
        return true;
    }

    void click(int x, int y, int mouseButton) {
        for (GuiTextField field : fields) field.mouseClicked(x, y, mouseButton);
    }

    private Integer parsed(int index) {
        try {
            int value = Integer.parseInt(fields[index].getText());
            return value >= MINIMUM[index] && value <= MAXIMUM[index] ? value : null;
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    boolean commit() {
        boolean valid = true;
        boolean changed = false;
        for (int index = 0; index < fields.length; index++) {
            Integer next = parsed(index);
            fields[index].setTextColor(next == null ? 0xFF7777 : 0xE0E0E0);
            if (next == null) valid = false;
        }
        if (!valid) return false;
        for (int index = 0; index < fields.length; index++) {
            int next = parsed(index);
            changed |= next != value(index);
            replace(index, next);
            sliders[index].setValue(next);
            sliders[index].displayString = label(index, next);
        }
        if (changed) send();
        return true;
    }

    boolean key(char typed, int key) {
        int focus = -1;
        for (int index = 0; index < fields.length; index++) if (fields[index].isFocused()) focus = index;
        if (key == Keyboard.KEY_TAB) {
            int step = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT) ? -1 : 1;
            if (focus >= 0) fields[focus].setFocused(false);
            int next = focus < 0 ? step < 0 ? fields.length - 1 : 0
                    : (focus + step + fields.length) % fields.length;
            fields[next].setFocused(true);
            return true;
        }
        if (focus < 0 || key == Keyboard.KEY_ESCAPE) return false;
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            if (commit()) fields[focus].setFocused(false);
            return true;
        }
        fields[focus].textboxKeyTyped(typed, key);
        Integer next = parsed(focus);
        fields[focus].setTextColor(next == null ? 0xFF7777 : 0xE0E0E0);
        if (next != null) {
            sliders[focus].setValue(next);
            sliders[focus].displayString = label(focus, next);
            if (next != value(focus)) {
                replace(focus, next);
                send();
            }
        }
        return true;
    }

    void draw() {
        net.minecraft.client.gui.FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        font.drawString("Position (pixels)", layout.left + 12, layout.top + 81, 0xDAE8F0);
        font.drawString("Rotation (degrees)", layout.left + 20 + columnWidth, layout.top + 81, 0xDAE8F0);
        int hintY = controlsTop + rowGap * 2 + 28;
        if (hintY + 20 < layout.doneY) {
            font.drawString("Offsets follow facing; right is viewer's right.",
                    layout.left + 12, hintY, 0xAFC1CD);
            String mount = tile.getMountFace() == EnumFacing.UP ? "top of support"
                    : tile.getMountFace() == EnumFacing.DOWN ? "underside of support"
                    : tile.getMountFace().getName() + " side of support";
            boolean valid = true;
            for (int index = 0; index < fields.length; index++) if (parsed(index) == null) valid = false;
            font.drawString(valid ? "Mount: " + mount
                    : "Ranges: \u00b132 px; tilt \u00b190\u00b0; yaw/roll \u00b1180\u00b0",
                    layout.left + 12, hintY + 11, valid ? 0xAFC1CD : 0xFF7777);
        }
    }

    void drawFields() {
        for (GuiTextField field : fields) field.drawTextBox();
    }

    void tick() {
        for (GuiTextField field : fields) field.updateCursorCounter();
    }
}
