package com.vandorlabs.ports;

import java.util.Arrays;

/** At most six water-filling passes, with no tick-time allocation and no capacity-sum overflow. */
public final class PortOutputBalance {
    private PortOutputBalance(){}
    /** Allocate a shared tick budget across accepting outputs. Rotate start each tick to share remainders. */
    public static void allocate(long budget,int outputMask,int start,long[] capacity,long[] granted){
        if(budget<0||(outputMask&~63)!=0||start<0||start>=6||capacity==null||granted==null||capacity==granted||capacity.length!=6||granted.length!=6)throw new IllegalArgumentException("Invalid resource port budget");
        for(long value:capacity)if(value<0)throw new IllegalArgumentException("Negative resource port acceptance");
        Arrays.fill(granted,0);int active=0;
        for(int side=0;side<6;side++)if((outputMask&(1<<side))!=0&&capacity[side]>0)active|=1<<side;
        while(budget>0&&active!=0){
            int count=Integer.bitCount(active);long share=budget/count;
            if(share==0){for(int offset=0;offset<6&&budget>0;offset++){int side=(start+offset)%6;if((active&(1<<side))!=0){granted[side]++;budget--;}}break;}
            for(int offset=0;offset<6;offset++){int side=(start+offset)%6;if((active&(1<<side))==0)continue;long amount=Math.min(share,capacity[side]-granted[side]);granted[side]+=amount;budget-=amount;if(granted[side]==capacity[side])active&=~(1<<side);}
        }
    }
}
