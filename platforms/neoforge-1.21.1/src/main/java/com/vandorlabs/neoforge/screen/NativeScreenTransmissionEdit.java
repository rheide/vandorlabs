package com.vandorlabs.neoforge.screen;
import com.vandorlabs.redstone.*;
/** Shared row range/local options; the host remains a receiver and never repeats these channels. */
public record NativeScreenTransmissionEdit(ChannelList channels,int range,boolean local) {
 public NativeScreenTransmissionEdit{if(!TransmissionSettings.validConfiguration(channels,range))throw new IllegalArgumentException("Screen transmission edit");}
 public NativeScreenEntity.Transmission apply(NativeScreenEntity.Transmission old){return old.range()==range&&old.local()==local&&old.channels().equals(channels)?old:new NativeScreenEntity.Transmission(range,local,channels);}
}
