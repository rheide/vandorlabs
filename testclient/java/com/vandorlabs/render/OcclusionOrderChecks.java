package com.vandorlabs.render;
import java.util.*;
/** Camera-independent graph invariants; native scenes test geometry construction. */
public final class OcclusionOrderChecks {
 private static int checks;
 public static void main(String[] args){
  BitSet[] g=graph(4);g[1].set(2);g[2].set(1);g[2].set(3);g[3].set(0);verify(g,true);
  Random r=new Random(27090);
  // Acyclic fast path must retain the prior smallest-ready-index tie break.
  for(int n=0;n<128;n++){
   g=graph(n);for(int i=0;i<n;i++)for(int j=i+1;j<n;j++)if(r.nextInt(8)==0)g[i].set(j);
   OcclusionOrder.Result result=OcclusionOrder.sort(g);
   require(result.cyclicComponents==0);
   for(int i=0;i<n;i++)require(result.order[i]==i);
   verify(g,false);
  }
  for(int n=0;n<=48;n++)for(int sample=0;sample<24;sample++){g=graph(n);for(int i=0;i<n;i++)for(int j=0;j<n;j++)if(r.nextInt(20)==0)g[i].set(j);verify(g,true);}
  for(int n:new int[]{128,129,16384}){g=graph(n);for(int i=1;i<n;i++)g[i].set(i-1);verify(g,false);g[0].set(n-1);verify(g,false);}
  System.out.println("occlusion-order PASS: "+checks+" graph invariants, cyclic constraints and large iterative traversal");
 }
 private static BitSet[] graph(int n){BitSet[] g=new BitSet[n];for(int i=0;i<n;i++)g[i]=new BitSet();return g;}
 private static void require(boolean ok){checks++;if(!ok)throw new AssertionError("Occlusion graph invariant failed");}
 private static void verify(BitSet[] g,boolean oracle){
  OcclusionOrder.Result result=OcclusionOrder.sort(g);int n=g.length;int[] pos=new int[n];Arrays.fill(pos,-1);
  for(int i=0;i<n;i++){int v=result.order[i];require(v>=0&&v<n);require(pos[v]<0);pos[v]=i;}
  for(int i=0;i<n;i++)for(int j=g[i].nextSetBit(0);j>=0;j=g[i].nextSetBit(j+1))if(result.component[i]!=result.component[j])require(pos[i]<pos[j]);
  if(!oracle)return;BitSet[] reach=graph(n);for(int i=0;i<n;i++){reach[i].or(g[i]);reach[i].set(i);}
  for(int k=0;k<n;k++)for(int i=0;i<n;i++)if(reach[i].get(k))reach[i].or(reach[k]);
  for(int i=0;i<n;i++)for(int j=0;j<n;j++)require((result.component[i]==result.component[j])==(reach[i].get(j)&&reach[j].get(i)));
 }
}
