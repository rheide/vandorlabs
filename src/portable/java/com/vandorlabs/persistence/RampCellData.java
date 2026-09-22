package com.vandorlabs.persistence;

/** Portable scalar state for one moving ramp cell; block-state NBT stays in adapters. */
public final class RampCellData {
    public final int startOffset,endOffset,treadPixels;
    public final int sourceY;
    public final int row;
    public final int length;
    public final int drop;
    public final int segments;
    public final int duration;
    public final double low;
    public final double high;
    public final boolean top;
    public final boolean elevator;
    public final boolean open;
    public final boolean moving;
    public final double startPose;
    public final long startTick;

    public RampCellData(int sourceY, int row, int length, int drop, int segments,
            int duration, double low, double high, boolean top, boolean elevator,
            boolean open, boolean moving, double startPose, long startTick) {
        this(sourceY,row,length,drop,segments,duration,low,high,top,elevator,open,moving,startPose,startTick,0,top?-Math.max(1,Math.min(16,drop)):Math.max(1,Math.min(16,drop)));
    }

    public RampCellData(int sourceY, int row, int length, int drop, int segments,
            int duration, double low, double high, boolean top, boolean elevator,
            boolean open, boolean moving, double startPose, long startTick,int startOffset,int endOffset) {
        this(sourceY,row,length,drop,segments,duration,low,high,top,elevator,open,moving,startPose,startTick,startOffset,endOffset,segments==8?2:8);
    }

    public RampCellData(int sourceY, int row, int length, int drop, int segments,
            int duration, double low, double high, boolean top, boolean elevator,
            boolean open, boolean moving, double startPose, long startTick,int startOffset,int endOffset,int treadPixels) {
        this.treadPixels=Math.max(1,Math.min(16,treadPixels));
        this.startOffset=Math.max(-8,Math.min(8,startOffset));
        this.endOffset=Math.max(-16,Math.min(16,endOffset));
        this.sourceY = sourceY;
        this.length = Math.max(1, Math.min(128, length));
        this.row = Math.max(0, Math.min(this.length - 1, row));
        this.drop = Math.max(1, Math.min(16, drop));
        this.segments = (16+this.treadPixels-1)/this.treadPixels;
        this.duration = Math.max(1, duration);
        this.low = low;
        this.high = high;
        this.top = top;
        this.elevator = elevator;
        this.open = open;
        this.moving = moving;
        this.startPose = Double.isFinite(startPose)
                ? Math.max(0, Math.min(1, startPose)) : 0;
        this.startTick = startTick;
    }

    public void write(PrimitiveData data) {
        data.putInt(SaveSchema.Ramp.TREAD_PIXELS,treadPixels);
        data.putInt(SaveSchema.Ramp.START_OFFSET,startOffset);
        data.putInt(SaveSchema.Ramp.END_OFFSET,endOffset);
        data.putInt(SaveSchema.DATA_VERSION, SaveSchema.Ramp.CELL_VERSION);
        data.putInt(SaveSchema.Ramp.SOURCE_Y, sourceY);
        data.putInt(SaveSchema.Ramp.ROW, row);
        data.putInt(SaveSchema.Ramp.LENGTH, length);
        data.putInt(SaveSchema.Ramp.DROP, drop);
        data.putInt(SaveSchema.Ramp.SEGMENTS, segments);
        data.putInt(SaveSchema.Ramp.DURATION, duration);
        data.putDouble(SaveSchema.Ramp.LOW, low);
        data.putDouble(SaveSchema.Ramp.HIGH, high);
        data.putBoolean(SaveSchema.Ramp.TOP, top);
        data.putBoolean(SaveSchema.Ramp.ELEVATOR, elevator);
        data.putBoolean(SaveSchema.Ramp.OPEN, open);
        data.putBoolean(SaveSchema.Ramp.MOVING, moving);
        data.putDouble(SaveSchema.Ramp.START_POSE, startPose);
        data.putLong(SaveSchema.Ramp.START_TICK, startTick);
    }

    public static RampCellData read(PrimitiveData data) {
        return new RampCellData(data.getInt(SaveSchema.Ramp.SOURCE_Y),
                data.getInt(SaveSchema.Ramp.ROW), data.getInt(SaveSchema.Ramp.LENGTH),
                data.getInt(SaveSchema.Ramp.DROP), data.getInt(SaveSchema.Ramp.SEGMENTS),
                data.getInt(SaveSchema.Ramp.DURATION), data.getDouble(SaveSchema.Ramp.LOW),
                data.getDouble(SaveSchema.Ramp.HIGH),
                !data.contains(SaveSchema.Ramp.TOP) || data.getBoolean(SaveSchema.Ramp.TOP),
                data.getBoolean(SaveSchema.Ramp.ELEVATOR),
                data.getBoolean(SaveSchema.Ramp.OPEN),
                data.getBoolean(SaveSchema.Ramp.MOVING),
                data.getDouble(SaveSchema.Ramp.START_POSE),
                data.getLong(SaveSchema.Ramp.START_TICK),
                data.getInt(SaveSchema.Ramp.START_OFFSET),
                data.contains(SaveSchema.Ramp.END_OFFSET)?data.getInt(SaveSchema.Ramp.END_OFFSET)
                        :(!data.contains(SaveSchema.Ramp.TOP)||data.getBoolean(SaveSchema.Ramp.TOP)?-1:1)*Math.max(1,Math.min(16,data.getInt(SaveSchema.Ramp.DROP))),
                data.contains(SaveSchema.Ramp.TREAD_PIXELS)?data.getInt(SaveSchema.Ramp.TREAD_PIXELS)
                        :(data.getInt(SaveSchema.Ramp.SEGMENTS)==8?2:8));
    }
}
