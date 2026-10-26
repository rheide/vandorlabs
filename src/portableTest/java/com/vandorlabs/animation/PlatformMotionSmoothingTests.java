package com.vandorlabs.animation;
public final class PlatformMotionSmoothingTests {
    private static void near(double a,double b,String label){if(Math.abs(a-b)>1e-9)throw new AssertionError(label+": "+a+" / "+b);}
    public static void main(String[] args){int samples=0;
        for(double interval:new double[]{1,2,3,4,12}){
            PlatformMotionSmoothing motion=new PlatformMotionSmoothing(interval>4?12:4);double position=0;
            for(int n=0;n<48;n++){
                double now=n*interval,delta=n<24?.08:-.08,before=position+motion.y(now);position+=delta;motion.accept(0,delta,0,now);near(position+motion.y(now),before,"packet arrival is continuous");
                double previous=position+motion.y(now);for(int frame=1;frame<=16;frame++){double value=position+motion.y(now+interval*frame/16);if(delta>0&&value<previous-1e-9||delta<0&&value>previous+1e-9)throw new AssertionError("visual reversal overshoot");previous=value;samples++;}
                near(position+motion.y(now+interval),position,"settles at authoritative support");
            }
            motion.accept(.2,.1,-.2,100);double x=motion.x(100);motion.accept(.1,.2,.1,100);near(motion.x(100),x-.1,"same-frame packet burst continuity");motion.clear();near(motion.y(100),0,"unload clears correction");
        }
        PlatformMotionSmoothing batch=new PlatformMotionSmoothing(),individual=new PlatformMotionSmoothing();batch.accept(.1,0,0,0);individual.accept(.1,0,0,0);batch.accept(.2,0,0,4);individual.accept(.1,0,0,4);individual.accept(.1,0,0,4);
        for(int i=0;i<=16;i++)near(batch.x(4+i/4D),individual.x(4+i/4D),"batched platform poses match individual rider corrections");
        VisualClock clock=new VisualClock();clock.sample(0,false);double before=clock.sample(50000000,false);near(clock.sample(1000000000,true),before,"pause freezes smoothing time");near(clock.sample(2000000000,false),before,"resume does not jump");
        System.out.println("platform-smoothing PASS samples="+samples+" 20 10 5 TPS one/three-tick packet cadence reversals bursts pause reset continuity");
    }
}
