package com.vandorlabs.segmented;

public final class SegmentedGateContextTest {
    private static volatile int sink;
    private static int checks;private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    @SuppressWarnings("deprecation")
    public static void main(String[] args){
        int[] all={0,0,0};SegmentedGateContext.Scratch scratch=new SegmentedGateContext.Scratch();
        for(int direction=0;direction<4;direction++)for(int rotation=0;rotation<4;rotation++){
            SegmentedGateContext closed=new SegmentedGateContext(3,3,1,direction,false,0,true,3,all,all,all);need(closed.mask(1,0,rotation,scratch)==15,"closed plane all four neighbours");
            SegmentedGateContext straight=new SegmentedGateContext(3,3,1,direction,true,0,true,3,all,all,all);for(double progress:new double[]{0,.01,.25,.5,.9,1})need(straight.mask(1,progress,rotation,scratch)==15,"straight moving cells retain continuous plane");
            int turning=closed.mask(1,.5,rotation,scratch);need(turning==(direction<2?3:12),"turning sections join only along compatible plane");
            SegmentedGateContext unknown=new SegmentedGateContext(3,3,1,direction,false,0,false,3,all,all,all);need(unknown.mask(1,.5,rotation,scratch)==0&&unknown.mask(1,0,rotation,scratch)==15,"unknown moving identity cannot extend posts");
        }
        for(int direction=0;direction<4;direction++)for(int rotation=0;rotation<4;rotation++){
            SegmentedGateContext peers=new SegmentedGateContext(3,3,1,direction,true,0,true,3,3,all,all,all);
            need((peers.mask(1,.5,.25,.5,rotation,scratch)&1)==0,"independent left pose cannot join a separated plane");
            need((peers.mask(1,.5,.5,.75,rotation,scratch)&2)==0,"independent right pose cannot join a separated plane");
            need(peers.mask(1,.5,Double.NaN,Double.NaN,rotation,scratch)==12,"retired peer clocks cannot contribute horizontal joins");
            SegmentedGateContext unrelated=new SegmentedGateContext(3,3,1,direction,true,0,true,3,0,all,all,all);
            need(unrelated.mask(1,0,0,0,rotation,scratch)==15,"independent drivers can join coincident closed endpoints");
            need(unrelated.mask(1,.5,.5,.5,rotation,scratch)==12,"coincident moving sections require a shared motion driver");
            peers.prepare(.5,.25,.75,rotation,scratch);for(int row=0;row<3;row++)need(peers.preparedMask(row,scratch)==peers.mask(row,.5,.25,.75,rotation,scratch),"independent peer poses survive batched preparation");
        }
        SegmentedGateContext mixed=new SegmentedGateContext(3,3,1,0,false,0,true,3,new int[]{0,0,1},new int[]{0,1,0},all);need(mixed.mask(1,0,0,scratch)==10,"mixed styles refuse individual neighbour edges");all[1]=2;need(mixed.kind(1)==0,"prepared styles are owned immutable copies");
        mixed.prepare(.5,0,scratch);for(int row=0;row<3;row++)need(mixed.preparedMask(row,scratch)==mixed.mask(row,.5,0,scratch),"batched immutable neighbour masks");
        boolean badPose=false,staleMask=false;try{mixed.prepare(Double.NaN,0,scratch);}catch(IllegalArgumentException expected){badPose=true;}try{mixed.preparedMask(1,scratch);}catch(IllegalStateException expected){staleMask=true;}need(badPose&&staleMask,"failed preparation cannot expose previous row masks");
        int profiles=0;double[] pose=new double[6],frame=new double[9],point=new double[6],origin=new double[6];float[] vertex=new float[8];
        for(int direction=0;direction<4;direction++)for(boolean straight:new boolean[]{false,true})for(double depth:new double[]{-13D/32,0,13D/32})for(double progress:new double[]{0,.01,.1,.25,.5,.75,1})for(int rotation=0;rotation<4;rotation++)for(int column=0;column<4;column++)for(int row=0;row<3;row++){
            SegmentedDoorGeometry.panel(row,3,column,4,progress,direction,straight,pose);pose[2]+=depth;int offset=column%3-1;SegmentedGateContext.frame(pose,offset,rotation,frame);double x=rotation==0?offset:rotation==2?-offset:0,z=rotation==1?-offset:rotation==3?offset:0;vertex[0]=vertex[1]=vertex[2]=0;SegmentedPanelTransform.vertex(vertex,0,pose,rotation,x,0,z,origin);for(int axis=0;axis<3;axis++)need(Math.abs(frame[axis]-origin[axis])<1e-12,"prepared origin uses released section transform");for(int basis=0;basis<2;basis++){vertex[0]=basis==0?16:0;vertex[1]=basis==1?16:0;SegmentedPanelTransform.vertex(vertex,0,pose,rotation,x,0,z,point);for(int axis=0;axis<3;axis++)need(Math.abs(frame[3+basis*3+axis]-(point[axis]-origin[axis]))<1e-12,"prepared basis uses released section transform");}profiles++;
        }
        double[][] frames={{0,0,0,1,0,0,0,1,0},{-1,0,0,1,0,0,0,1,0},{1,0,0,1,0,0,0,1,0},{0,-1,0,1,0,0,0,1,0},{0,1,0,1,0,0,0,1,0}};int[] styles={0,0,0,0,0};need(SegmentedGateJoin.mask(styles,15,frames)==15,"prepared scalar join parity");frames[1][0]=Double.NaN;need(SegmentedGateJoin.mask(styles,15,frames)==14,"nonfinite neighbour refused");frames[1][0]=-1;frames[1][3]=0;need(SegmentedGateJoin.mask(styles,15,frames)==14,"nonunit neighbour refused");
        SegmentedGatePreparedCache<Object,SegmentedGateContext> cache=new SegmentedGatePreparedCache<>(4096,4);int emptyBytes=cache.retainedBytes();Object owner=new Object(),revision=new Object();cache.put(owner,10,70,20,revision,mixed,mixed.retainedBytes());need(cache.get(owner,10,70,20,revision)==mixed,"prepared cache identity");need(cache.get(new Object(),10,70,20,revision)==null&&cache.get(owner,10,70,20,new Object())==null,"stale owner/revision refuse");cache.changed(10,68,20);need(cache.get(owner,10,70,20,revision)==null,"lower cell locally invalidates root");cache.put(owner,10,70,20,revision,mixed,mixed.retainedBytes());cache.changed(11,68,20);need(cache.get(owner,10,70,20,revision)==null,"direct neighbour invalidates root");cache.put(owner,10,70,20,revision,mixed,mixed.retainedBytes());cache.changed(12,68,20);need(cache.get(owner,10,70,20,revision)==mixed,"unrelated columns retain prepared context");for(int i=0;i<16;i++)cache.put(owner,i,70,20,revision,mixed,mixed.retainedBytes());need(cache.size()==4&&cache.retainedBytes()<=4096&&cache.get(owner,0,70,20,revision)==null,"bounded LRU context eviction");cache.clear();need(cache.size()==0&&cache.retainedBytes()==emptyBytes,"world/reload cache retirement");cache.put(owner,10,70,20,revision,mixed,mixed.retainedBytes());
        com.sun.management.ThreadMXBean meter=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();meter.getThreadAllocatedBytes(thread);meter.getThreadAllocatedBytes(thread);for(int i=0;i<50000;i++)sink=cache.get(owner,10,70,20,revision).mask(1,(i%10000)/10000D,i&3,scratch);long before=meter.getThreadAllocatedBytes(thread);for(int i=0;i<10000;i++)sink=cache.get(owner,10,70,20,revision).mask(1,(i%10000)/10000D,i&3,scratch);long allocated=meter.getThreadAllocatedBytes(thread)-before;need(allocated==0,"warm context/cache query allocation "+allocated);
        System.out.println("Segmented gate prepared context PASS profiles="+profiles+" checks="+checks+" warm10000Bytes="+allocated);
    }
}
