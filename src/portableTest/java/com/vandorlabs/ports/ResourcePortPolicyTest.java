package com.vandorlabs.ports;

import java.util.Arrays;

public final class ResourcePortPolicyTest {
    private static int checks;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static void main(String[] arguments){
        for(int side=0;side<6;side++){ResourcePortSettings settings=ResourcePortSettings.placed(side);for(int other=0;other<6;other++)need(settings.face(other)==(other==side?ResourcePortSettings.Face.INPUT:other==(side^1)?ResourcePortSettings.Face.OUTPUT:ResourcePortSettings.Face.DISABLED),"six-axis player/opposite defaults");need(!settings.pullInputs&&settings.throughputText().isEmpty(),"passive unlimited default");ResourcePortSettings.Face[] copy=settings.faces();copy[side]=ResourcePortSettings.Face.DISABLED;need(settings.face(side)==ResourcePortSettings.Face.INPUT,"immutable face bank");}
        for(String text:new String[]{"-1","0","+1","1.5","1e6","9223372036854775808","12x"})need(ResourcePortSettings.parseThroughput(text)==null,"refused invalid editor rate "+text);
        need(ResourcePortSettings.parseThroughput("")==0L&&ResourcePortSettings.parseThroughput("  ")==0L,"blank unlimited");need(ResourcePortSettings.parseThroughput("9223372036854775807")==Long.MAX_VALUE,"long max exact");
        long[] capacities=new long[6],grants=new long[6],total=new long[6];Arrays.fill(capacities,Long.MAX_VALUE);
        for(int tick=0;tick<60;tick++){PortOutputBalance.allocate(1,63,tick%6,capacities,grants);for(int side=0;side<6;side++)total[side]+=grants[side];}for(long value:total)need(value==10,"one item tick remainder fairness");
        capacities=new long[]{1,100,100,0,0,0};PortOutputBalance.allocate(101,7,0,capacities,grants);need(Arrays.equals(grants,new long[]{1,50,50,0,0,0}),"saturated output budget redistributed");
        for(int mask=0;mask<64;mask++)for(int start=0;start<6;start++)for(long budget:new long[]{0,1,5,17,Long.MAX_VALUE}){
            Arrays.fill(capacities,Long.MAX_VALUE);PortOutputBalance.allocate(budget,mask,start,capacities,grants);long left=budget,min=Long.MAX_VALUE,max=0;for(int side=0;side<6;side++){need(grants[side]>=0&&grants[side]<=left,"no aggregate overflow or over-budget transfer");left-=grants[side];if((mask&(1<<side))==0)need(grants[side]==0,"disabled output gets nothing");else{min=Math.min(min,grants[side]);max=Math.max(max,grants[side]);}}if(mask!=0){need(left==0,"accepting outputs consume available budget");need(max-min<=1,"equal output shares including long max");}
        }
        System.out.println("Resource port policy PASS checks="+checks);
    }
}
