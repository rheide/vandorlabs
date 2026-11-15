package com.vandorlabs.ramp;

/** Independent Source formula oracle plus pending/committed lifecycle boundaries. */
public final class RampLifecycleTest {
    private static int checks;
    private static void need(boolean value){checks++;if(!value)throw new AssertionError("Ramp lifecycle "+checks);}
    public static void main(String[] args){
        for(int p=-1;p<=16;p++)for(int r=-1;r<=16;r++)for(boolean on:new boolean[]{false,true}){
            int level=Math.max(Math.max(0,Math.min(15,p)),Math.max(0,Math.min(15,r)));
            need(RampLifecycle.signalTarget(true,false,on,p,r)==(on?level:15-level)/15D);
            need(RampLifecycle.signalTarget(false,false,on,p,r)==((level>0)==on?1:0));
            need(RampLifecycle.signalTarget(true,true,on,p,r)==((level>0)==on?1:0));
        }
        for(int a=-32;a<=32;a++)for(int b=-32;b<=32;b++)for(int length=1;length<=16;length++)for(int speed=0;speed<3;speed++)
            need(RampLifecycle.duration(length,a,b,speed)==ControllerPlatform.duration(length,(int)Math.ceil(Math.abs(b/2D-a/2D)),speed));
        for(int duration=1;duration<=128;duration++){
            varTimeline(duration);
        }
        need(RampLifecycle.work(false,false,false,false,false)==0);
        need(RampLifecycle.work(false,true,false,false,false)==RampLifecycle.OWNERSHIP);
        need(RampLifecycle.work(true,true,true,true,true)==31);
        boolean bad=false;try{new RampLifecycle.Timeline(0,0,0,0,Double.NaN,0,1,false);}catch(IllegalArgumentException expected){bad=true;}need(bad);
        System.out.println("RampLifecycle PASS "+checks);
    }
    private static void varTimeline(int duration){
        RampLifecycle.Timeline t=new RampLifecycle.Timeline(0,0,0,0,0,0,duration,false).request(1,100,duration);
        for(int n=0;n<=duration;n++){
            double actual=t.sample(100+n,0);need(actual==Math.min(1,n/(double)duration));
            RampLifecycle.Timeline committed=t.committed(100+n,actual);
            need(committed.observed==actual);need(committed.lastTick==100+n);
            need(committed.moving==(n<duration));
            RampLifecycle.Timeline reverse=committed.request(0,100+n,duration);
            need(reverse.from==actual&&reverse.observed==actual&&reverse.sequence==t.sequence+1);
            need(reverse.sample(100+n+1,0)==Math.max(0,actual-1D/duration));
            RampLifecycle.Timeline stopped=committed.stopped(100+n);
            need(stopped.sample(Long.MAX_VALUE,.5)==actual&&!stopped.moving);
        }
    }
}
