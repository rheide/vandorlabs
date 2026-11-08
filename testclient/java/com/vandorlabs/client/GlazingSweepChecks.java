package com.vandorlabs.client;

import java.lang.reflect.*;
import java.util.*;

/** Compare the sweep cost estimator with an independent all-pairs overlap oracle. */
public final class GlazingSweepChecks {
    public static void main(String[] args)throws Exception{
        Class<?> owner=Class.forName("com.vandorlabs.client.GlazingRenderOrder");
        Class<?> draw=Class.forName(owner.getName()+"$Draw"),plane=Class.forName(owner.getName()+"$Plane");
        Constructor<?> dc=draw.getDeclaredConstructor(),pc=plane.getDeclaredConstructor(draw);
        dc.setAccessible(true);pc.setAccessible(true);
        Method count=owner.getDeclaredMethod("projectedPairs",List.class,boolean.class);count.setAccessible(true);
        String[] names={"left","right","bottom","top"};Field[] fields=new Field[4];
        for(int i=0;i<4;i++){fields[i]=plane.getDeclaredField(names[i]);fields[i].setAccessible(true);}
        Random random=new Random(1228102);int checks=0;
        for(int n=0;n<=128;n++){
            List<Object> planes=new ArrayList<>();double[][] bounds=new double[n][4];
            for(int i=0;i<n;i++){
                Object p=pc.newInstance(dc.newInstance());planes.add(p);
                for(int axis=0;axis<2;axis++){
                    double lo=random.nextInt(32),hi=lo+random.nextInt(8);
                    if(i%17==0){lo=Double.NEGATIVE_INFINITY;hi=Double.POSITIVE_INFINITY;}
                    if(i%19==0){lo=Double.POSITIVE_INFINITY;hi=Double.NEGATIVE_INFINITY;}
                    bounds[i][axis*2]=lo;bounds[i][axis*2+1]=hi;
                }
                for(int k=0;k<4;k++)fields[k].setDouble(p,bounds[i][k]);
            }
            for(int axis=0;axis<2;axis++){
                long expected=0;int k=axis*2;
                for(int i=0;i<n;i++)for(int j=i+1;j<n;j++)if(bounds[i][k]<bounds[i][k+1]&&bounds[j][k]<bounds[j][k+1]&&bounds[i][k]<bounds[j][k+1]&&bounds[j][k]<bounds[i][k+1])expected++;
                long actual=(Long)count.invoke(null,planes,axis==1);
                if(actual!=expected)throw new AssertionError("Sweep count n="+n+" axis="+axis+" expected="+expected+" actual="+actual);
                checks++;
            }
        }
        System.out.println("glazing-sweep PASS cases="+checks+" touching empty infinite random projected intervals");
    }
}
