package com.vandorlabs.persistence;

/** Portable scalar state for a ramp controller; coordinates and block states stay in adapters. */
public final class RampControllerData {
    public final int startOffset,endOffset,treadPixels;
    public final int savedVersion,drop,segments,duration,length,minAlong,facing,direction,redstoneChannel;
    public final String status;
    public final boolean top,activateOnPower,slow,elevator,error,open,moving;
    public final boolean hasDirection,latched,signalKnown,recoveryPending,channelSignal;
    public final double startPose,low,high;
    public final long startTick,lastStepTick;

    public RampControllerData(int savedVersion,int drop,int segments,String status,
            boolean top,boolean activateOnPower,boolean slow,boolean elevator,boolean error,
            boolean open,boolean moving,double startPose,long startTick,long lastStepTick,
            int duration,int length,int minAlong,int facing,boolean hasDirection,int direction,
            double low,double high,boolean latched,boolean signalKnown,boolean recoveryPending,
            int redstoneChannel,boolean channelSignal) {
        this(savedVersion,drop,segments,status,top,activateOnPower,slow,elevator,error,
                open,moving,startPose,startTick,lastStepTick,duration,length,minAlong,facing,
                hasDirection,direction,low,high,latched,signalKnown,recoveryPending,
                redstoneChannel,channelSignal,0,top?-Math.max(1,Math.min(16,drop)):Math.max(1,Math.min(16,drop)));
    }

    public RampControllerData(int savedVersion,int drop,int segments,String status,
            boolean top,boolean activateOnPower,boolean slow,boolean elevator,boolean error,
            boolean open,boolean moving,double startPose,long startTick,long lastStepTick,
            int duration,int length,int minAlong,int facing,boolean hasDirection,int direction,
            double low,double high,boolean latched,boolean signalKnown,boolean recoveryPending,
            int redstoneChannel,boolean channelSignal,int startOffset,int endOffset) {
        this(savedVersion,drop,segments,status,top,activateOnPower,slow,elevator,error,open,moving,startPose,startTick,lastStepTick,duration,length,minAlong,facing,hasDirection,direction,low,high,latched,signalKnown,recoveryPending,redstoneChannel,channelSignal,startOffset,endOffset,segments==8?2:8);
    }

    public RampControllerData(int savedVersion,int drop,int segments,String status,
            boolean top,boolean activateOnPower,boolean slow,boolean elevator,boolean error,
            boolean open,boolean moving,double startPose,long startTick,long lastStepTick,
            int duration,int length,int minAlong,int facing,boolean hasDirection,int direction,
            double low,double high,boolean latched,boolean signalKnown,boolean recoveryPending,
            int redstoneChannel,boolean channelSignal,int startOffset,int endOffset,int treadPixels) {
        this.treadPixels=Math.max(1,Math.min(16,treadPixels));
        this.startOffset=Math.max(-8,Math.min(8,startOffset));
        this.endOffset=Math.max(-16,Math.min(16,endOffset));
        this.savedVersion=savedVersion;
        this.drop=Math.max(1,Math.min(16,drop));
        this.segments=(16+this.treadPixels-1)/this.treadPixels;
        this.status=status==null?"":status;
        this.top=top;
        this.activateOnPower=activateOnPower;
        this.slow=slow; this.elevator=elevator; this.error=error;
        this.open=open; this.moving=moving;
        this.startPose=Double.isFinite(startPose)?Math.max(0,Math.min(1,startPose)):0;
        this.startTick=startTick; this.lastStepTick=lastStepTick;
        this.duration=Math.max(1,duration); this.length=Math.max(1,length);
        this.minAlong=minAlong; this.facing=facing;
        this.hasDirection=hasDirection; this.direction=direction;
        this.low=low; this.high=high;
        this.latched=latched; this.signalKnown=signalKnown;
        this.recoveryPending=recoveryPending;
        this.redstoneChannel=Math.max(0,redstoneChannel);
        this.channelSignal=channelSignal;
    }

    public void write(PrimitiveData data) {
        data.putInt(SaveSchema.Ramp.TREAD_PIXELS,treadPixels);
        data.putInt(SaveSchema.Ramp.START_OFFSET,startOffset);
        data.putInt(SaveSchema.Ramp.END_OFFSET,endOffset);
        data.putInt(SaveSchema.Ramp.CONTROLLER_VERSION_KEY,SaveSchema.Ramp.CONTROLLER_VERSION);
        data.putInt(SaveSchema.Ramp.DROP,drop); data.putInt(SaveSchema.Ramp.SEGMENTS,segments);
        data.putString(SaveSchema.Ramp.STATUS,status); data.putBoolean(SaveSchema.Ramp.TOP,top);
        data.putBoolean(SaveSchema.Ramp.ACTIVATE_ON_POWER,activateOnPower);
        data.putBoolean(SaveSchema.Ramp.SLOW,slow); data.putBoolean(SaveSchema.Ramp.ELEVATOR,elevator);
        data.putBoolean(SaveSchema.Ramp.ERROR,error); data.putBoolean(SaveSchema.Ramp.OPEN,open);
        data.putBoolean(SaveSchema.Ramp.MOVING,moving); data.putDouble(SaveSchema.Ramp.START_POSE,startPose);
        data.putLong(SaveSchema.Ramp.START_TICK,startTick); data.putLong(SaveSchema.Ramp.LAST_STEP_TICK,lastStepTick);
        data.putInt(SaveSchema.Ramp.DURATION,duration); data.putInt(SaveSchema.Ramp.LENGTH,length);
        data.putInt(SaveSchema.Ramp.MIN_ALONG,minAlong); data.putInt(SaveSchema.Ramp.FACING,facing);
        if (hasDirection) data.putInt(SaveSchema.Ramp.DIRECTION,direction);
        data.putDouble(SaveSchema.Ramp.LOW,low); data.putDouble(SaveSchema.Ramp.HIGH,high);
        data.putBoolean(SaveSchema.Ramp.LATCHED,latched); data.putBoolean(SaveSchema.Ramp.SIGNAL_KNOWN,signalKnown);
        data.putBoolean(SaveSchema.Ramp.RECOVERY_PENDING,recoveryPending);
        data.putInt(SaveSchema.Redstone.CHANNEL,redstoneChannel);
        data.putBoolean(SaveSchema.Redstone.SIGNAL,channelSignal);
    }

    public static RampControllerData read(PrimitiveData data) {
        int version=data.getInt(SaveSchema.Ramp.CONTROLLER_VERSION_KEY);
        boolean legacy=version<2;
        return new RampControllerData(version,data.getInt(SaveSchema.Ramp.DROP),
                data.getInt(SaveSchema.Ramp.SEGMENTS),data.getString(SaveSchema.Ramp.STATUS),
                legacy||data.getBoolean(SaveSchema.Ramp.TOP),
                legacy||data.getBoolean(SaveSchema.Ramp.ACTIVATE_ON_POWER),
                data.getBoolean(SaveSchema.Ramp.SLOW),data.getBoolean(SaveSchema.Ramp.ELEVATOR),
                data.getBoolean(SaveSchema.Ramp.ERROR),data.getBoolean(SaveSchema.Ramp.OPEN),
                data.getBoolean(SaveSchema.Ramp.MOVING),data.getDouble(SaveSchema.Ramp.START_POSE),
                data.getLong(SaveSchema.Ramp.START_TICK),data.getLong(SaveSchema.Ramp.LAST_STEP_TICK),
                data.getInt(SaveSchema.Ramp.DURATION),data.getInt(SaveSchema.Ramp.LENGTH),
                data.getInt(SaveSchema.Ramp.MIN_ALONG),data.getInt(SaveSchema.Ramp.FACING),
                data.contains(SaveSchema.Ramp.DIRECTION),data.getInt(SaveSchema.Ramp.DIRECTION),
                data.getDouble(SaveSchema.Ramp.LOW),data.getDouble(SaveSchema.Ramp.HIGH),
                data.getBoolean(SaveSchema.Ramp.LATCHED),data.getBoolean(SaveSchema.Ramp.SIGNAL_KNOWN),
                data.getBoolean(SaveSchema.Ramp.RECOVERY_PENDING),
                data.getInt(SaveSchema.Redstone.CHANNEL),data.getBoolean(SaveSchema.Redstone.SIGNAL),
                data.getInt(SaveSchema.Ramp.START_OFFSET),
                data.contains(SaveSchema.Ramp.END_OFFSET)?data.getInt(SaveSchema.Ramp.END_OFFSET)
                        :(legacy||data.getBoolean(SaveSchema.Ramp.TOP)?-1:1)*Math.max(1,Math.min(16,data.getInt(SaveSchema.Ramp.DROP))),
                data.contains(SaveSchema.Ramp.TREAD_PIXELS)?data.getInt(SaveSchema.Ramp.TREAD_PIXELS)
                        :(data.getInt(SaveSchema.Ramp.SEGMENTS)==8?2:8));
    }
}
