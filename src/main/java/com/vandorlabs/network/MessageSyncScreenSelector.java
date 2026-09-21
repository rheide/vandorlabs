package com.vandorlabs.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public class MessageSyncScreenSelector implements IMessage {

    private BlockPos pos;
    private String selectedScreen;
    private boolean redstoneEnabled;
    private int displayMode;
    private boolean framed;
    private int animationSpeedIndex;
    private String inputPanel;
    private String secondaryInputPanel;
    private boolean smallInput;
    private int redstoneChannel;

    public MessageSyncScreenSelector() {}

    public MessageSyncScreenSelector(BlockPos pos, String selectedScreen, boolean redstoneEnabled,
            int displayMode, boolean framed, int animationSpeedIndex, String inputPanel) {
        this(pos, selectedScreen, redstoneEnabled, displayMode, framed,
                animationSpeedIndex, inputPanel, inputPanel, false, 0);
    }

    public MessageSyncScreenSelector(BlockPos pos, String selectedScreen, boolean redstoneEnabled,
            int displayMode, boolean framed, int animationSpeedIndex, String inputPanel,
            String secondaryInputPanel) {
        this(pos, selectedScreen, redstoneEnabled, displayMode, framed,
                animationSpeedIndex, inputPanel, secondaryInputPanel, false, 0);
    }

    public MessageSyncScreenSelector(BlockPos pos, String selectedScreen, boolean redstoneEnabled,
            int displayMode, boolean framed, int animationSpeedIndex, String inputPanel,
            String secondaryInputPanel, boolean smallInput) {
        this(pos, selectedScreen, redstoneEnabled, displayMode, framed, animationSpeedIndex,
                inputPanel, secondaryInputPanel, smallInput, 0);
    }

    public MessageSyncScreenSelector(BlockPos pos, String selectedScreen, boolean redstoneEnabled,
            int displayMode, boolean framed, int animationSpeedIndex, String inputPanel,
            String secondaryInputPanel, boolean smallInput, int redstoneChannel) {
        this.pos = pos;
        this.selectedScreen = selectedScreen;
        this.redstoneEnabled = redstoneEnabled;
        this.displayMode = displayMode;
        this.framed = framed;
        this.animationSpeedIndex = animationSpeedIndex;
        this.inputPanel = inputPanel;
        this.secondaryInputPanel = secondaryInputPanel;
        this.smallInput = smallInput;
        this.redstoneChannel = redstoneChannel;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        selectedScreen = readString(buf);
        redstoneEnabled = buf.readBoolean();
        displayMode = buf.readInt();
        framed = buf.readBoolean();
        animationSpeedIndex = buf.readInt();
        inputPanel = readString(buf);
        secondaryInputPanel = buf.readableBytes() > 0 ? readString(buf) : inputPanel;
        smallInput = buf.readableBytes() > 0 && buf.readBoolean();
        redstoneChannel = buf.readableBytes() >= 4 ? buf.readInt() : 0;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        writeString(buf, selectedScreen);
        buf.writeBoolean(redstoneEnabled);
        buf.writeInt(displayMode);
        buf.writeBoolean(framed);
        buf.writeInt(animationSpeedIndex);
        writeString(buf, inputPanel);
        writeString(buf, secondaryInputPanel);
        buf.writeBoolean(smallInput);
        buf.writeInt(redstoneChannel);
    }

    private static String readString(ByteBuf buf) {
        int len = buf.readInt();
        if (len < 0 || len > 256 || len > buf.readableBytes()) {
            throw new IllegalArgumentException("invalid selector string length " + len);
        }
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void writeString(ByteBuf buf, String value) {
        byte[] bytes = (value == null ? "" : value)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    public BlockPos getPos() {
        return pos;
    }

    public String getSelectedScreen() {
        return selectedScreen;
    }

    public boolean isRedstoneEnabled() {
        return redstoneEnabled;
    }

    public int getDisplayMode() {
        return displayMode;
    }

    public boolean isFramed() {
        return framed;
    }

    public int getAnimationSpeedIndex() {
        return animationSpeedIndex;
    }

    public String getInputPanel() {
        return inputPanel;
    }

    public String getSecondaryInputPanel() {
        return secondaryInputPanel;
    }

    public boolean isSmallInput() {
        return smallInput;
    }

    public int getRedstoneChannel() { return redstoneChannel; }
}
