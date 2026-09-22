package com.vandorlabs.persistence;

/** Stable save-field contract shared by Minecraft-version-specific NBT adapters. */
public final class SaveSchema {
    public static final String DATA_VERSION="VandorDataVersion";
    private SaveSchema() { }

    public static final class Screen {
        public static final int VERSION=1;
        public static final String SELECTED="selectedScreen";
        public static final String REDSTONE_ENABLED="redstoneEnabled";
        public static final String DISPLAY_MODE="displayMode";
        public static final String FRAMED="framed";
        public static final String SPEED="animationSpeedIndex";
        public static final String INPUT="inputPanel";
        public static final String SECONDARY_INPUT="secondaryInputPanel";
        public static final String WALL_POSITION="wallPosition";
        public static final String SMALL_INPUT="smallInput";
        private Screen() { }
    }

    public static final class Ramp {
        public static final int CONTROLLER_VERSION=6;
        public static final int CELL_VERSION=4;
        public static final String CONTROLLER_VERSION_KEY="ControllerVersion";
        public static final String CONTROLLER="Controller",SOURCE="Source",SOURCE_STATE="SourceState",SOURCE_Y="SourceY";
        public static final String CONTROLLER_X="ControllerX",CONTROLLER_Y="ControllerY",CONTROLLER_Z="ControllerZ";
        public static final String ROW="Row",LENGTH="Length",DROP="Drop",SEGMENTS="Segments";
        public static final String DURATION="Duration",LOW="Low",HIGH="High",TOP="Top";
        public static final String ELEVATOR="Elevator",OPEN="Open",MOVING="Moving";
        public static final String TREAD_PIXELS="TreadPixels";
        public static final String START_OFFSET="StartOffset",END_OFFSET="EndOffset";
        public static final String START_POSE="StartPose",START_TICK="StartTick";
        public static final String LAST_STEP_TICK="LastStepTick",STATUS="Status";
        public static final String ACTIVATE_ON_POWER="ActivateOnPower",SLOW="Slow",ERROR="Error";
        public static final String MIN_ALONG="MinAlong",FACING="Facing",DIRECTION="RampDirection";
        public static final String LATCHED="Latched",SIGNAL_KNOWN="SignalKnown";
        public static final String RECOVERY_PENDING="RecoveryPending",ORIGINAL="Original",ORIGINAL_STATE="OriginalState",OWNER="Owner";
        public static final String OWNER_ID="OwnerId";
        public static final String SOURCES="Sources",CELLS="Cells",POSITION="Pos";
        public static final String X="X",Y="Y",Z="Z";
        private Ramp() { }
    }

    public static final class Redstone {
        public static final int VERSION=1;
        public static final String CHANNEL="RedstoneChannel";
        public static final String SIGNAL="ChannelSignal";
        public static final String LOCAL_ON="LocalOn";
        public static final String CHANNEL_INITIALIZED="ChannelInitialized";
        public static final String MANUAL_ON="ManualOn";
        public static final String PARTICLE_STREAM="ParticleStream";
        public static final String LIGHT_INITIALIZED="LightInitialized";
        private Redstone() { }
    }
}
