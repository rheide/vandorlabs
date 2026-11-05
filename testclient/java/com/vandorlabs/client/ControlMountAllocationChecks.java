package com.vandorlabs.client;

import com.vandorlabs.blocks.SignalControlMount;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.util.*;
import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.ThreadMXBean;
import sun.misc.Unsafe;

/** Focused warmed VM allocation/layout evidence; timings do not establish client FPS. */
public final class ControlMountAllocationChecks {
    private static volatile Object sink;private static volatile double scalar;
    private static final AxisAlignedBB BOX=new AxisAlignedBB(.11330098,.0625,.9375,.88669902,.87565048,1);
    private static final EnumFacing[] FACES=EnumFacing.values();
    private static final Vec3d[] POINTS=new Vec3d[32];
    private static final SignalControlMount MOUNT=new SignalControlMount(EnumFacing.NORTH,3,3,3,2,BOX);
    static{for(int i=0;i<POINTS.length;i++)POINTS[i]=new Vec3d(.11330098+i/64D,.87565048-i/64D,.30394670+i/128D);}
    private static void loop(int kind,int count){
        if(kind==0)for(int i=0;i<count;i++)sink=MOUNT.transform(POINTS[i&31]);
        else if(kind==1)for(int i=0;i<count;i++)sink=MOUNT.project(POINTS[i&31]);
        else if(kind==2)for(int i=0;i<count;i++)scalar=MOUNT.depth(POINTS[i&31]);
        else if(kind==3)for(int i=0;i<count;i++)sink=MOUNT.bounds(BOX);
        else for(int i=0;i<count;i++)sink=MOUNT.wedgeBoxes(BOX);
    }
    private static long shallow(Unsafe u,Object value,int alignment)throws Exception{
        long size=u.objectFieldOffset(Header.class.getDeclaredField("one"));
        for(Class<?> type=value.getClass();type!=null;type=type.getSuperclass())for(Field field:type.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())){Class<?> t=field.getType();int bytes=t==long.class||t==double.class?8:t==int.class||t==float.class?4:t==short.class||t==char.class?2:t==byte.class||t==boolean.class?1:u.arrayIndexScale(Object[].class);size=Math.max(size,u.objectFieldOffset(field)+bytes);}
        return (size+alignment-1)/alignment*alignment;
    }
    private static long retained(Unsafe u,Object value,int alignment,Set<Object> seen)throws Exception{
        if(value==null||!seen.add(value)||value.getClass().getName().contains("$$Lambda$"))return 0;
        long bytes=shallow(u,value,alignment);
        for(Class<?> type=value.getClass();type!=null;type=type.getSuperclass())for(Field field:type.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())&&!field.getType().isPrimitive())bytes+=retained(u,u.getObject(value,u.objectFieldOffset(field)),alignment,seen);
        return bytes;
    }
    private static final class Header{byte one;}
    public static void main(String[] ignored)throws Exception{
        ThreadMXBean bean=(ThreadMXBean)ManagementFactory.getThreadMXBean();if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("VM allocation counter unavailable");bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
        String[] names={"transform","project","depth","bounds","wedges"};int[] counts={500000,500000,500000,20000,2000};
        for(int kind=0;kind<names.length;kind++){
            for(int warm=0;warm<4;warm++)loop(kind,Math.min(100000,counts[kind]));long best=Long.MAX_VALUE,nanos=Long.MAX_VALUE;
            for(int sample=0;sample<5;sample++){long bytes=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();loop(kind,counts[kind]);long time=System.nanoTime()-start,allocated=bean.getThreadAllocatedBytes(thread)-bytes;best=Math.min(best,allocated);nanos=Math.min(nanos,time);}
            System.out.println("Mount allocation "+names[kind]+" bytes/op="+(best/(double)counts[kind])+" ns/op="+(nanos/(double)counts[kind]));
        }
        SignalControlMount[] poses=new SignalControlMount[50000];for(int warm=0;warm<50000;warm++)sink=new SignalControlMount(FACES[warm%6],warm&3,warm&3,(warm>>2)&3,(warm>>4)&3,BOX);
        long bytes=bean.getThreadAllocatedBytes(thread);for(int i=0;i<poses.length;i++)poses[i]=new SignalControlMount(FACES[i%6],i&3,i&3,(i>>2)&3,(i>>4)&3,BOX);long allocated=bean.getThreadAllocatedBytes(thread)-bytes;sink=poses;
        Field field=Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);Unsafe u=(Unsafe)field.get(null);int alignment=Integer.parseInt(ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class).getVMOption("ObjectAlignmentInBytes").getValue());
        long exclusive=retained(u,poses[0],alignment,Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>()));
        System.out.println("Mount allocation constructor bytes/pose="+(allocated/(double)poses.length)+" exclusive-retained-by-VM-field-layout="+exclusive+" alignment="+alignment+" shared static factories excluded");
    }
}
