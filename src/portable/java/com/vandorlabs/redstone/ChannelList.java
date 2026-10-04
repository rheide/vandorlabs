package com.vandorlabs.redstone;

import java.util.Arrays;

/** Immutable, canonical channel membership; zero means no channel. */
public final class ChannelList {
    public static final int MAX_CHANNELS=64;
    public static final int MAX_TEXT_LENGTH=MAX_CHANNELS*12;
    public static final ChannelList EMPTY=new ChannelList(new int[0]);
    private final int[] values;
    private final String text;

    private ChannelList(int[] values) {
        this.values=values;
        StringBuilder out=new StringBuilder();
        for(int value:values){if(out.length()>0)out.append(", ");out.append(value);}
        text=out.length()==0?"0":out.toString();
    }
    public static ChannelList of(int... input) {
        if(input.length>MAX_CHANNELS)throw new IllegalArgumentException("Too many redstone channels");
        int[] sorted=input.clone();Arrays.sort(sorted);int count=0;
        for(int value:sorted) {
            if(value<0)throw new IllegalArgumentException("Negative redstone channel");
            if(value>0 && (count==0 || sorted[count-1]!=value))sorted[count++]=value;
        }
        return count==0?EMPTY:new ChannelList(Arrays.copyOf(sorted,count));
    }
    /** Null denotes invalid input; empty text and zero both mean disconnected. */
    public static ChannelList parse(String text) {
        if(text==null || text.length()>MAX_TEXT_LENGTH)return null;
        if(text.trim().isEmpty())return EMPTY;
        String[] parts=text.split(",",-1);
        if(parts.length>MAX_CHANNELS)return null;
        int[] values=new int[parts.length];
        try {
            for(int i=0;i<parts.length;i++) {
                String part=parts[i].trim();
                if(part.isEmpty())return null;
                for(int c=0;c<part.length();c++)if(part.charAt(c)<'0' || part.charAt(c)>'9')return null;
                values[i]=Integer.parseInt(part);
            }
            return of(values);
        }catch(IllegalArgumentException invalid){return null;}
    }
    public int size(){return values.length;}
    public boolean isEmpty(){return values.length==0;}
    public int first(){return isEmpty()?0:values[0];}
    public int get(int index){return values[index];}
    public boolean contains(int channel){return Arrays.binarySearch(values,channel)>=0;}
    public boolean containsAll(ChannelList other) {
        for(int value:other.values)if(!contains(value))return false;
        return true;
    }
    public ChannelList intersect(ChannelList other) {
        int[] common=new int[Math.min(size(),other.size())];int count=0;
        for(int value:values)if(other.contains(value))common[count++]=value;
        return count==size()?this:count==0?EMPTY:new ChannelList(Arrays.copyOf(common,count));
    }
    public int[] toArray(){return values.clone();}
    @Override public String toString(){return text;}
    @Override public boolean equals(Object other){return other instanceof ChannelList && Arrays.equals(values,((ChannelList)other).values);}
    @Override public int hashCode(){return Arrays.hashCode(values);}
}
