package com.vandorlabs.physics;

import java.util.List;
import java.util.Objects;

/**
 * Bounded finite-domain contacts; no world, entity or ownership admission is performed here.
 * Coordinates are fixed-point; translations are whole blocks. Contact normals must oppose.
 * The requested root is anchored at zero; each disconnected component anchors its lowest body.
 * Search is single-threaded. LIMIT never claims exhaustive failure, and deadline/slice yields
 * preserve every untried option. Validator work is caller-owned and must itself be bounded.
 */
public final class GridCouplerParking {
 public static final int SCALE=1024,MAX_VARIABLES=64,MAX_BODIES=128,MAX_PORTS=512,MAX_LINKS=512,MAX_OPTIONS=4096,MAX_POINTS=65536;
 public static final long MAX_COORDINATE=32000001L*SCALE,MAX_TRANSLATION=MAX_BODIES*64000002L;
 public enum Status { PENDING,SOLVED,EXHAUSTED,LIMIT }
 public interface View { int bodies();int variables();int option(int variable);long translation(int body,int axis); }
 /** The view is borrowed only during this call; do not retain it or mutate the Search recursively. */
 public interface Validator { boolean accept(View view); }
 public static final class Point {
  public final long x,y,z;public final int facing;
  public Point(long x,long y,long z,int facing){if(x < -MAX_COORDINATE||x>MAX_COORDINATE||y < -MAX_COORDINATE||y>MAX_COORDINATE||z < -MAX_COORDINATE||z>MAX_COORDINATE||facing<0||facing>5)throw new IllegalArgumentException("Point bounds");this.x=x;this.y=y;this.z=z;this.facing=facing;}
 }
 public static final class Port {
  public final int body,variable;private final Point[] choices;
  public Port(int body,int variable,Point[] choices){this.body=body;this.variable=variable;Objects.requireNonNull(choices);if(choices.length<1||choices.length>MAX_OPTIONS)throw new IllegalArgumentException("Port options");this.choices=choices.clone();for(Point p:this.choices)Objects.requireNonNull(p);}
  public int options(){return choices.length;}public Point choice(int option){return choices[option];}
 }
 public static final class Link {
  public final int firstPort,secondPort;
  public Link(int firstPort,int secondPort){this.firstPort=firstPort;this.secondPort=secondPort;}
 }
 public static final class Problem {
  public final int bodies,root;private final int[] counts;private final Port[] ports;private final Link[] links;
  public Problem(int bodies,int root,int[] optionCounts,List<Port> ports,List<Link> links){
   Objects.requireNonNull(optionCounts);Objects.requireNonNull(ports);Objects.requireNonNull(links);
   if(bodies<1||bodies>MAX_BODIES||root<0||root>=bodies||optionCounts.length>MAX_VARIABLES||ports.size()>MAX_PORTS||links.size()>MAX_LINKS)throw new IllegalArgumentException("Problem bounds");
   this.bodies=bodies;this.root=root;counts=optionCounts.clone();for(int n:counts)if(n<1||n>MAX_OPTIONS)throw new IllegalArgumentException("Domain bounds");
   this.ports=ports.toArray(new Port[ports.size()]);this.links=links.toArray(new Link[links.size()]);int points=0;
   for(Port p:this.ports){Objects.requireNonNull(p);if(p.body<0||p.body>=bodies||p.variable < -1||p.variable>=counts.length||p.choices.length!=(p.variable<0?1:counts[p.variable]))throw new IllegalArgumentException("Port domain/body");points+=p.choices.length;if(points>MAX_POINTS)throw new IllegalArgumentException("Point budget");}
   for(Link l:this.links){Objects.requireNonNull(l);if(l.firstPort<0||l.firstPort>=this.ports.length||l.secondPort<0||l.secondPort>=this.ports.length)throw new IllegalArgumentException("Link bounds");}
  }
  public int variables(){return counts.length;}public int options(int variable){return counts[variable];}public int ports(){return ports.length;}public Port port(int index){return ports[index];}public int links(){return links.length;}public Link link(int index){return links[index];}
 }
 public static final class Result implements View {
  private final int[] options;private final long[] translations;
  private Result(int[] options,long[] translations){this.options=options.clone();this.translations=translations.clone();}
  public int bodies(){return translations.length/3;}public int variables(){return options.length;}public int option(int variable){return options[variable];}public long translation(int body,int axis){check(body,axis,bodies());return translations[body*3+axis];}
 }
 public static final class Search {
  private final Problem problem;private final Validator validator;private final long maxTrials;private long trials;private Status status=Status.PENDING;private Result result;private int depth;private boolean stepping;
  private final int[] options,order,next,parent,size,anchor;private final long[] potential,translations;private final View view;
  public Search(Problem problem,Validator validator,long maxTrials){
   this.problem=Objects.requireNonNull(problem);this.validator=validator;if(maxTrials<1)throw new IllegalArgumentException("Trial budget");this.maxTrials=maxTrials;
   options=new int[problem.variables()];order=new int[options.length];next=new int[options.length];for(int i=0;i<options.length;i++){options[i]=-1;order[i]=i;}
   // Stable fewest-domain ordering, then most incident contacts; shared variables stay shared.
   int[] degree=new int[options.length];for(Link l:problem.links){int a=problem.ports[l.firstPort].variable,b=problem.ports[l.secondPort].variable;if(a>=0)degree[a]++;if(b>=0&&b!=a)degree[b]++;}
   for(int i=1;i<order.length;i++){int v=order[i],j=i;while(j>0&&(problem.counts[v]<problem.counts[order[j-1]]||problem.counts[v]==problem.counts[order[j-1]]&&degree[v]>degree[order[j-1]])){order[j]=order[j-1];j--;}order[j]=v;}
   parent=new int[problem.bodies];size=new int[parent.length];anchor=new int[parent.length];potential=new long[parent.length*3];translations=new long[potential.length];
   view=new View(){public int bodies(){return Search.this.problem.bodies;}public int variables(){return options.length;}public int option(int variable){return options[variable];}public long translation(int body,int axis){check(body,axis,bodies());return translations[body*3+axis];}};
  }
  public Search(Problem problem,long maxTrials){this(problem,null,maxTrials);}
  public long trials(){return trials;}public Status status(){return status;}public Result result(){if(status!=Status.SOLVED)throw new IllegalStateException("No accepted result");return result;}
  /** maxTrialsThisSlice bounds attempted options, not complete Cartesian assignments. */
  public Status step(int maxTrialsThisSlice,long deadlineNanos){
   if(maxTrialsThisSlice<1)throw new IllegalArgumentException("Slice budget");if(stepping)throw new IllegalStateException("Recursive search");if(status!=Status.PENDING)return status;stepping=true;
   try{int used=0;while(true){
    if(options.length!=0){while(depth>=0&&next[depth]>=problem.counts[order[depth]]){options[order[depth]]=-1;next[depth]=0;depth--;}if(depth<0)return status=Status.EXHAUSTED;}
    else if(trials!=0)return status=Status.EXHAUSTED;
    if(trials>=maxTrials)return status=Status.LIMIT;
    if(used>=maxTrialsThisSlice||deadlineNanos!=Long.MAX_VALUE&&System.nanoTime()-deadlineNanos>=0)return status;
    trials++;used++;if(options.length!=0)options[order[depth]]=next[depth]++;
    if(!consistent())continue;
    if(options.length!=0&&depth+1<options.length){depth++;continue;}
    publishTranslations();if(validator==null||validator.accept(view)){result=new Result(options,translations);return status=Status.SOLVED;}
   }}finally{stepping=false;}
  }
  // Rebuild bounded weighted union-find per partial trial: no undo-log/trial allocation.
  private boolean consistent(){
   for(int i=0;i<parent.length;i++){parent[i]=i;size[i]=1;potential[i*3]=potential[i*3+1]=potential[i*3+2]=0;}
   for(Link l:problem.links){Port a=problem.ports[l.firstPort],b=problem.ports[l.secondPort];int ai=a.variable<0?0:options[a.variable],bi=b.variable<0?0:options[b.variable];if(ai<0||bi<0)continue;Point pa=a.choices[ai],pb=b.choices[bi];if((pa.facing^1)!=pb.facing)return false;
    int ra=find(a.body),rb=find(b.body);long dx=pa.x-pb.x,dy=pa.y-pb.y,dz=pa.z-pb.z;if(dx%SCALE!=0||dy%SCALE!=0||dz%SCALE!=0)return false;
    dx=dx/SCALE+weight(a.body,0)-weight(b.body,0);dy=dy/SCALE+weight(a.body,1)-weight(b.body,1);dz=dz/SCALE+weight(a.body,2)-weight(b.body,2);
    if(ra==rb){if(dx!=0||dy!=0||dz!=0)return false;}else if(size[ra]>=size[rb]){parent[rb]=ra;potential[rb*3]=dx;potential[rb*3+1]=dy;potential[rb*3+2]=dz;size[ra]+=size[rb];}else{parent[ra]=rb;potential[ra*3]=-dx;potential[ra*3+1]=-dy;potential[ra*3+2]=-dz;size[rb]+=size[ra];}
   }return true;
  }
  private int find(int body){while(parent[body]!=body)body=parent[body];return body;}
  private long weight(int body,int axis){long n=0;while(parent[body]!=body){n+=potential[body*3+axis];body=parent[body];}return n;}
  private void publishTranslations(){
   for(int i=0;i<anchor.length;i++)anchor[i]=-1;for(int i=0;i<parent.length;i++){int r=find(i);if(anchor[r]<0)anchor[r]=i;}anchor[find(problem.root)]=problem.root;
   for(int i=0;i<parent.length;i++)for(int axis=0;axis<3;axis++){long value=weight(i,axis)-weight(anchor[find(i)],axis);if(value < -MAX_TRANSLATION||value>MAX_TRANSLATION)throw new IllegalStateException("Translation bound");translations[i*3+axis]=value;}
  }
 }
 private static void check(int body,int axis,int bodies){if(body<0||body>=bodies||axis<0||axis>2)throw new IndexOutOfBoundsException("Body/axis");}
 private GridCouplerParking(){}
}
