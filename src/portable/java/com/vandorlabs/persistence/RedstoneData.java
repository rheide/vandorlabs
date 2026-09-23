package com.vandorlabs.persistence;

/** Stable primitive save codecs for the three redstone-channel state shapes. */
public final class RedstoneData {
    private RedstoneData() { }

    private static void beginWrite(PrimitiveData data) {
        data.putInt(SaveSchema.DATA_VERSION, SaveSchema.Redstone.VERSION);
    }

    public static final class Member {
        public final int channel;
        public final boolean signal;
        public Member(int channel, boolean signal) {
            this.channel = Math.max(0, channel);
            this.signal = signal;
        }
        public void write(PrimitiveData data) {
            beginWrite(data);
            data.putInt(SaveSchema.Redstone.CHANNEL, channel);
            data.putBoolean(SaveSchema.Redstone.SIGNAL, signal);
        }
        public static Member read(PrimitiveData data) {
            return new Member(data.getInt(SaveSchema.Redstone.CHANNEL),
                    data.getBoolean(SaveSchema.Redstone.SIGNAL));
        }
    }

    public static final class Source {
        public final int channel;
        public final boolean localOn;
        public final boolean initialized;
        public final int mountRotation;
        public Source(int channel, boolean localOn, boolean initialized) {
            this(channel,localOn,initialized,0);
        }
        public Source(int channel, boolean localOn, boolean initialized, int mountRotation) {
            this.channel = Math.max(0, channel);
            this.localOn = localOn;
            this.initialized = initialized;
            this.mountRotation = Math.floorMod(mountRotation,4);
        }
        public void write(PrimitiveData data) {
            beginWrite(data);
            data.putInt(SaveSchema.Redstone.CHANNEL, channel);
            data.putBoolean(SaveSchema.Redstone.LOCAL_ON, localOn);
            data.putBoolean(SaveSchema.Redstone.CHANNEL_INITIALIZED, initialized);
            data.putInt(SaveSchema.Redstone.MOUNT_ROTATION, mountRotation);
        }
        public static Source read(PrimitiveData data) {
            return new Source(data.getInt(SaveSchema.Redstone.CHANNEL),
                    data.getBoolean(SaveSchema.Redstone.LOCAL_ON),
                    data.getBoolean(SaveSchema.Redstone.CHANNEL_INITIALIZED),
                    data.getInt(SaveSchema.Redstone.MOUNT_ROTATION));
        }
    }

    public static final class Light {
        public final int channel;
        public final boolean signal;
        public final boolean manualOn;
        public final boolean particleStream;
        public final boolean initialized;
        public Light(int channel, boolean signal, boolean manualOn,
                boolean particleStream, boolean initialized) {
            this.channel = Math.max(0, channel);
            this.signal = signal;
            this.manualOn = manualOn;
            this.particleStream = particleStream;
            this.initialized = initialized;
        }
        public void write(PrimitiveData data) {
            beginWrite(data);
            data.putInt(SaveSchema.Redstone.CHANNEL, channel);
            data.putBoolean(SaveSchema.Redstone.SIGNAL, signal);
            data.putBoolean(SaveSchema.Redstone.MANUAL_ON, manualOn);
            data.putBoolean(SaveSchema.Redstone.PARTICLE_STREAM, particleStream);
            data.putBoolean(SaveSchema.Redstone.LIGHT_INITIALIZED, initialized);
        }
        public static Light read(PrimitiveData data) {
            return new Light(data.getInt(SaveSchema.Redstone.CHANNEL),
                    data.getBoolean(SaveSchema.Redstone.SIGNAL),
                    data.getBoolean(SaveSchema.Redstone.MANUAL_ON),
                    data.getBoolean(SaveSchema.Redstone.PARTICLE_STREAM),
                    data.getBoolean(SaveSchema.Redstone.LIGHT_INITIALIZED));
        }
    }
}
