package com.vandorlabs.animation;

/** Commands and observations are deliberately independent of any world time. */
public final class RampPartAnimationChecks {
    private static int checks;
    private static void equal(double expected,double actual,String label){checks++;if(Math.abs(expected-actual)>1e-10)throw new AssertionError(label+" expected="+expected+" actual="+actual);}
    private static void sample(RampPartAnimation animation,long revision,boolean moving,double observed,double target,boolean belt,double distance,double velocity,double now){animation.sample(revision,moving,observed,target,16,belt,distance,velocity,now);}
    public static void main(String[] args){
        RampPartAnimation a=new RampPartAnimation();sample(a,1,true,.25,1,false,0,0,100);equal(.25,a.pose(),"first observation");
        sample(a,1,true,.25,1,false,0,0,104);equal(.5,a.pose(),"duplicate does not restart");
        sample(a,0,true,0,0,false,0,0,106);equal(.625,a.pose(),"old opposite command ignored while advancing");
        sample(a,2,true,.2,0,false,0,0,106);equal(.625,a.pose(),"reversal continuous despite delayed observation");
        sample(a,2,true,.2,0,false,0,0,110);equal(.375,a.pose(),"reverse rate");
        sample(a,2,true,.2,0,false,0,0,109);equal(.375,a.pose(),"clock rewind ignored");
        sample(a,2,true,.2,0,false,0,0,200);equal(0,a.pose(),"lag reaches endpoint");
        sample(a,3,false,.4,.4,false,0,0,200);equal(.4,a.pose(),"new stationary correction");
        sample(a,3,true,0,1,false,0,0,220);equal(.4,a.pose(),"old fields in duplicate cannot restart");
        sample(a,4,true,.4,1,false,0,0,220);sample(a,4,true,.99,1,false,0,0,224);equal(.65,a.pose(),"new observation in same command does not reset cosmetic progress");
        sample(a,5,false,1,1,false,0,0,224);equal(1,a.pose(),"authoritative final endpoint");sample(a,4,true,.4,0,false,0,0,230);equal(1,a.pose(),"old moving command after final endpoint");
        RampPartAnimation b=new RampPartAnimation();sample(b,7,false,1,1,true,2,.05,0);sample(b,7,false,1,1,true,999,-.05,4);equal(2.2,b.distance(),"belt duplicate phase and velocity ignored");
        sample(b,8,false,1,1,true,1,-.025,4);equal(2.2,b.distance(),"running reversal continuous");
        sample(b,8,false,1,1,true,1,-.025,8);equal(2.1,b.distance(),"reversed belt rate");
        sample(b,6,false,1,1,true,0,.05,10);equal(2.05,b.distance(),"old belt command ignored");
        sample(b,9,false,1,1,true,1.9,0,10);equal(1.9,b.distance(),"stop authoritative processed phase");
        sample(b,8,false,1,1,true,3,.05,11);equal(1.9,b.distance(),"old running revision after late processed stop");
        sample(b,9,false,1,1,true,100,.05,100);equal(1.9,b.distance(),"repeated stopped observation stays still");
        sample(b,10,false,1,1,true,1.9,.0125,100);sample(b,10,false,1,1,true,1.9,.0125,108);equal(2,b.distance(),"restart speed");
        sample(b,11,false,1,1,false,0,0,108);equal(0,b.distance(),"disabled belt phase");
        VisualClock clock=new VisualClock();equal(0,clock.sample(1000000000L,false),"clock starts");equal(2,clock.sample(1100000000L,false),"two visual ticks");equal(2,clock.sample(2000000000L,true),"pause");equal(2,clock.sample(3000000000L,false),"resume without jump");equal(3,clock.sample(3050000000L,false),"resumed progress");
        RampPartAnimation lag=new RampPartAnimation();for(int i=0;i<=320;i++){sample(lag,1,true,0,1,false,0,0,i/20D);equal(Math.min(1,i/320D),lag.pose(),"low TPS independent visual sample "+i);}
        com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();for(int i=0;i<20000;i++)sample(b,11,false,1,1,false,0,0,108);long before=bean.getThreadAllocatedBytes(thread);for(int i=0;i<100000;i++)sample(b,11,false,1,1,false,0,0,108);long bytes=bean.getThreadAllocatedBytes(thread)-before;equal(0,bytes,"warmed scalar sampling allocation");
        System.out.println("Ramp part animation PASS checks="+checks+" samples=100000 allocated="+bytes);
    }
}
