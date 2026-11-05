package com.vandorlabs.persistence;

public final class WallSettingsTest {
    private static void require(boolean ok){if(!ok)throw new AssertionError("wall saved contract");}
    public static void main(String[] args){
        MemoryPrimitiveData tag=new MemoryPrimitiveData();
        WallSettings empty=WallSettings.read(tag);
        require(!empty.fullWidth&&!empty.halfHeight&&!empty.joinPortholes&&empty.fill==0&&empty.join==0&&empty.portholeShape==0);
        int count=0;
        for(int flags=0;flags<8;flags++)for(int value:new int[]{Integer.MIN_VALUE,-1,0,1,2,3,4,11,12,Integer.MAX_VALUE}){
            boolean full=(flags&1)!=0,half=(flags&2)!=0,joined=(flags&4)!=0;
            WallSettings.write(tag,full,half,value,value,joined,value);
            require(tag.getInt(WallSettings.FILL)==value&&tag.getInt(WallSettings.SHAPE)==value);
            WallSettings read=WallSettings.read(tag);
            require(read.fullWidth==full&&read.halfHeight==half&&read.joinPortholes==joined);
            require(read.fill==(value<0?0:value>3?3:value)&&read.portholeShape==read.fill);
            require(read.join==(value<0?0:value>11?11:value));
            require(WallSettings.geometry(half,full)==(half?2:full?1:0));count++;
        }
        tag.putString(WallSettings.SHAPE,"3");require(WallSettings.read(tag).portholeShape==0);
        System.out.println("WallSettings PASS "+count+" legacy flag combinations, raw writes and bounded saved choices");
    }
}
