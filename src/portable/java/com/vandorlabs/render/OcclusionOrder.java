package com.vandorlabs.render;

import java.util.*;

/** Preserve every acyclic occlusion constraint, even when other surfaces form cycles. */
public final class OcclusionOrder {
    public static final class Result {
        public final int[] order,component;
        public final int cyclicComponents;
        private Result(int[] order,int[] component,int cycles){this.order=order;this.component=component;cyclicComponents=cycles;}
    }
    public static Result sort(BitSet[] edges){
        int n=edges.length;boolean[] seen=new boolean[n];int[] finish=new int[n],stack=new int[n],cursor=new int[n];int finished=0;
        // Iterative DFS keeps large scenes independent of the JVM call-stack size.
        for(int root=0;root<n;root++)if(!seen[root]){
            int size=1;stack[0]=root;seen[root]=true;
            while(size>0){int at=stack[size-1],next=edges[at].nextSetBit(cursor[size-1]);
                if(next<0){finish[finished++]=at;size--;continue;}
                if(next>=n)throw new IllegalArgumentException("Occlusion edge outside scene");
                cursor[size-1]=next+1;if(!seen[next]){seen[next]=true;stack[size]=next;cursor[size]=0;size++;}
            }
            cursor[0]=0;
        }
        BitSet[] reverse=new BitSet[n];for(int i=0;i<n;i++)reverse[i]=new BitSet();
        for(int i=0;i<n;i++)for(int j=edges[i].nextSetBit(0);j>=0;j=edges[i].nextSetBit(j+1))reverse[j].set(i);
        int[] component=new int[n];Arrays.fill(component,-1);List<List<Integer>> members=new ArrayList<>();
        for(int f=n-1;f>=0;f--){int root=finish[f];if(component[root]>=0)continue;int id=members.size(),size=1;List<Integer> group=new ArrayList<>();stack[0]=root;component[root]=id;
            while(size>0){int at=stack[--size];group.add(at);for(int j=reverse[at].nextSetBit(0);j>=0;j=reverse[at].nextSetBit(j+1))if(component[j]<0){component[j]=id;stack[size++]=j;}}
            Collections.sort(group);members.add(group);
        }
        int count=members.size(),cycles=0;BitSet[] dag=new BitSet[count];int[] incoming=new int[count];
        for(int i=0;i<count;i++){dag[i]=new BitSet();if(members.get(i).size()>1)cycles++;}
        for(int i=0;i<n;i++)for(int j=edges[i].nextSetBit(0);j>=0;j=edges[i].nextSetBit(j+1)){int a=component[i],b=component[j];if(a!=b&&!dag[a].get(b)){dag[a].set(b);incoming[b]++;}}
        PriorityQueue<Integer> ready=new PriorityQueue<>((a,b)->Integer.compare(members.get(a).get(0),members.get(b).get(0)));for(int i=0;i<count;i++)if(incoming[i]==0)ready.add(i);
        int[] order=new int[n];int used=0;
        while(!ready.isEmpty()){int c=ready.remove();for(int member:members.get(c))order[used++]=member;for(int j=dag[c].nextSetBit(0);j>=0;j=dag[c].nextSetBit(j+1))if(--incoming[j]==0)ready.add(j);}
        if(used!=n)throw new IllegalStateException("Condensed occlusion graph must be acyclic");
        return new Result(order,component,cycles);
    }
    private OcclusionOrder(){}
}
