package com.vandorlabs.copier.core;

import com.vandorlabs.redstone.*;

/** Stable Source settings and activation edge rule, shared with the native controller. */
public final class CopySettings {
    public final int height,increment,activation;
    public final ChannelList channels;
    public final boolean chip;
    public CopySettings(int height,int increment,int activation,ChannelList channels,boolean chip){
        if(height<1||height>16||!ChannelRange.validIncrement(increment)||activation<0||activation>2||channels==null)throw new IllegalArgumentException("Invalid copy settings");
        this.height=height;this.increment=increment;this.activation=activation;this.channels=channels;this.chip=chip;
    }
    public boolean active(boolean signal){return activation==1?signal:activation==2&&!signal;}
    public boolean changesEdgeBank(CopySettings before){return activation!=before.activation||!channels.equals(before.channels);}
    @Override public boolean equals(Object value){if(!(value instanceof CopySettings))return false;CopySettings other=(CopySettings)value;return height==other.height&&increment==other.increment&&activation==other.activation&&chip==other.chip&&channels.equals(other.channels);}
    @Override public int hashCode(){return ((((height*31+increment)*31+activation)*31+channels.hashCode())*31)+(chip?1:0);}
}
