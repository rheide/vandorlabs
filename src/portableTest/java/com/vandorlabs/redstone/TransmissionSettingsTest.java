package com.vandorlabs.redstone;

import java.util.Arrays;

/** Independent saved-value and malformed-input goldens, with no host or tag adapter. */
public final class TransmissionSettingsTest {
    private static void need(boolean value,String detail){if(!value)throw new AssertionError(detail);}
    public static void main(String[] args){
        int cases=0;int[] ranges={4,8,16,32,64,128,256};
        need(Arrays.equals(TransmissionSettings.RANGES,ranges),"range choices/order");
        for(int range=-2;range<=512;range++){
            boolean expected=false;for(int allowed:ranges)if(allowed==range)expected=true;
            need(TransmissionSettings.validRange(range)==expected,"allowed range "+range);
            for(boolean supported:new boolean[]{false,true}){
                need(TransmissionSettings.readRange(false,range,supported)==(supported?16:-1),"absent range ignores numeric value");
                need(TransmissionSettings.readRange(true,range,supported)==(expected?range:16),"typed range fallback");cases+=2;
            }
        }
        for(boolean typed:new boolean[]{false,true})for(boolean saved:new boolean[]{false,true})for(boolean fallback:new boolean[]{false,true}){
            need(TransmissionSettings.readLocal(typed,saved,fallback)==(typed?saved:fallback),"typed Local/default");cases++;
        }
        ChannelList fallback=ChannelList.of(73);
        need(TransmissionSettings.readChannels(false,null,fallback)==fallback,"absent array retains exact fallback");
        need(TransmissionSettings.readChannels(true,new int[0],fallback)==ChannelList.EMPTY,"explicit empty disconnects");
        int[] values={9,0,3,9};int[] before=values.clone();
        need(TransmissionSettings.readChannels(true,values,fallback).equals(ChannelList.of(3,9))&&Arrays.equals(values,before),"sorted/deduplicated without mutating input");
        need(TransmissionSettings.readChannels(true,new int[]{-1},fallback)==ChannelList.EMPTY,"negative array is empty configured output");
        need(TransmissionSettings.readChannels(true,new int[65],fallback)==ChannelList.EMPTY,"raw over-limit array rejected before deduplication");
        int[] maximum=new int[64];for(int i=0;i<64;i++)maximum[i]=i+1;
        need(TransmissionSettings.readChannels(true,maximum,fallback).size()==64,"maximum channel count");
        need(TransmissionSettings.defaultChannels(true,fallback)==fallback&&TransmissionSettings.defaultChannels(false,fallback)==ChannelList.EMPTY,"output/receiver defaults");
        need(!TransmissionSettings.validConfiguration(null,16)&&!TransmissionSettings.validConfiguration(fallback,0)&&TransmissionSettings.validConfiguration(ChannelList.EMPTY,16),"configure rejects null/invalid range and accepts clearing");
        int previous=TransmissionSettings.RANGES[0];try{TransmissionSettings.RANGES[0]=5;need(TransmissionSettings.validRange(5)&&!TransmissionSettings.validRange(4),"public array semantics");}finally{TransmissionSettings.RANGES[0]=previous;}
        need("VandorTransmission".equals(TransmissionSettings.SETTINGS)&&"Channels".equals(TransmissionSettings.CHANNELS)&&"Range".equals(TransmissionSettings.RANGE)&&"Local".equals(TransmissionSettings.LOCAL),"saved schema names");
        System.out.println("Transmission settings PASS cases="+(cases+10)+" typed defaults bounds explicit clearing ownership public-range API");
    }
}
