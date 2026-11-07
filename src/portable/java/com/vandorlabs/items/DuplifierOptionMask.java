package com.vandorlabs.items;

import java.util.Arrays;

/** Append-only Copifier choices, independent of the platform's item and NBT representation. */
public final class DuplifierOptionMask {
    private final int count, wordCount;
    private final long lowMask;

    public DuplifierOptionMask(int count) {
        if(count<1)throw new IllegalArgumentException("Empty Copifier option catalog");
        this.count=count;wordCount=(count+63)/64;lowMask=count>=64?-1L:(1L<<count)-1;
    }
    public long lowMask(){return lowMask;}
    private void trim(long[] words){int tail=count%64;if(tail!=0)words[wordCount-1]&=(1L<<tail)-1;}
    public long[] legacyWords(long low){long[] words=new long[wordCount];Arrays.fill(words,-1L);words[0]=low;trim(words);return words;}
    public boolean valid(long[] words){if(words==null||words.length!=wordCount)return false;long[] clean=words.clone();trim(clean);return Arrays.equals(words,clean);}
    /** The caller checks exact tag types. Null stored words mean no correctly typed multiword tag. */
    public long[] read(boolean hasLow,long low,int known,int[] stored){
        if(!hasLow)return legacyWords(lowMask);
        known=Math.max(0,Math.min(count,known));long[] words=new long[wordCount];words[0]=low;
        if(stored!=null)for(int i=0;i<wordCount&&i*2+1<stored.length;i++)words[i]=(stored[i*2]&0xffffffffL)|((long)stored[i*2+1]<<32);
        for(int i=known;i<count;i++)words[i/64]|=1L<<(i%64);
        trim(words);return words;
    }
    public int[] encode(long[] words){
        if(!valid(words))throw new IllegalArgumentException("Invalid Duplifier option mask");
        int[] stored=new int[wordCount*2];for(int i=0;i<wordCount;i++){stored[i*2]=(int)words[i];stored[i*2+1]=(int)(words[i]>>>32);}return stored;
    }
    /** Original one-word callers cannot disable options appended to later words. */
    public boolean enabled(long low,int index){return index>=64&&index<count||index>=0&&index<64&&(low&(1L<<index))!=0;}
    public boolean enabled(long[] words,int index){return index>=0&&index<count&&index/64<words.length&&(words[index/64]&(1L<<(index%64)))!=0;}
}
