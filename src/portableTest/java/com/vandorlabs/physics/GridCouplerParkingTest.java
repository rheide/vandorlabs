package com.vandorlabs.physics;

import java.util.*;
import java.lang.management.ManagementFactory;
import static com.vandorlabs.physics.GridCouplerParking.*;

/** Independent full-assignment graph oracle; the production solver uses partial weighted union-find. */
public final class GridCouplerParkingTest {
 private static int checks;private static void require(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static Point p(long x,int facing){return new Point(x,0,0,facing);}
 private static Port port(int body,int var,Point...points){return new Port(body,var,points);}
 private static Problem problem(int bodies,int root,int[] counts,Port[] ports,Link...links){return new Problem(bodies,root,counts,Arrays.asList(ports),Arrays.asList(links));}
 private static Status finish(Search s,int slice){Status status;do{status=s.step(slice,Long.MAX_VALUE);}while(status==Status.PENDING);return status;}
 private static void refuses(Runnable action,String why){try{action.run();}catch(IllegalArgumentException expected){checks++;return;}throw new AssertionError(why);}
 private static long[][] oracle(Problem p,int[] options){
  long[][] t=new long[p.bodies][3];boolean[] seen=new boolean[p.bodies];int[] queue=new int[p.bodies];
  // Root component first; other components use their lowest numbered body.
  for(int pass=-1;pass<p.bodies;pass++){int start=pass<0?p.root:pass;if(seen[start])continue;seen[start]=true;int head=0,tail=0;queue[tail++]=start;
   while(head<tail){int body=queue[head++];for(int n=0;n<p.links();n++){Link l=p.link(n);Port a=p.port(l.firstPort),b=p.port(l.secondPort);if(a.body!=body&&b.body!=body)continue;Point pa=a.choice(a.variable<0?0:options[a.variable]),pb=b.choice(b.variable<0?0:options[b.variable]);if((pa.facing^1)!=pb.facing)return null;long[] d={pa.x-pb.x,pa.y-pb.y,pa.z-pb.z};for(long v:d)if(v%SCALE!=0)return null;
    int other=a.body==body?b.body:a.body;long sign=a.body==body?1:-1;for(int k=0;k<3;k++){long value=t[body][k]+sign*d[k]/SCALE;if(seen[other]&&t[other][k]!=value)return null;if(!seen[other])t[other][k]=value;}if(!seen[other]){seen[other]=true;queue[tail++]=other;}
   }}
  }return t;
 }
 private static boolean brute(Problem p,int[] options,int variable){if(variable==options.length)return oracle(p,options)!=null;for(int i=0;i<p.options(variable);i++){options[variable]=i;if(brute(p,options,variable+1))return true;}return false;}
 private static void compare(Problem p){boolean possible=brute(p,new int[p.variables()],0);Search s=new Search(p,1000000);Status status=finish(s,1);require((status==Status.SOLVED)==possible,"oracle existence");if(possible){Result r=s.result();int[] opts=new int[p.variables()];for(int i=0;i<opts.length;i++)opts[i]=r.option(i);long[][] expected=oracle(p,opts);require(expected!=null,"accepted assignment oracle");for(int b=0;b<p.bodies;b++)for(int k=0;k<3;k++)require(expected[b][k]==r.translation(b,k),"anchored translations");require(s.step(1,Long.MAX_VALUE)==status,"terminal stable");}}
 private static void examples(){
  compare(problem(3,1,new int[0],new Port[]{port(0,-1,p(2048,0)),port(1,-1,p(0,1))},new Link(0,1)));
  compare(problem(2,0,new int[]{2},new Port[]{port(0,0,p(0,0),p(1024,0)),port(1,-1,p(0,1)),port(0,-1,p(1024,0)),port(1,-1,p(0,1))},new Link(0,1),new Link(2,3)));
  compare(problem(3,0,new int[]{2},new Port[]{port(0,0,p(0,0),p(1024,0)),port(1,-1,p(0,1)),port(1,-1,p(1024,0)),port(2,-1,p(0,1)),port(2,-1,p(0,0)),port(0,-1,p(2048,1))},new Link(0,1),new Link(2,3),new Link(4,5)));
  compare(problem(2,0,new int[0],new Port[]{port(0,-1,p(1,0)),port(1,-1,p(0,1))},new Link(0,1)));
  compare(problem(1,0,new int[0],new Port[]{port(0,-1,p(0,0)),port(0,-1,p(0,0))},new Link(0,1)));
  for(int facing=0;facing<6;facing++)compare(problem(2,0,new int[0],new Port[]{port(0,-1,new Point(0,0,0,facing)),port(1,-1,new Point(1024,2048,-3072,facing^1))},new Link(0,1)));
  Search empty=new Search(problem(2,1,new int[0],new Port[0]),1);require(finish(empty,1)==Status.SOLVED&&empty.result().translation(0,0)==0,"empty disconnected canonical anchor");
 }
 private static void random(){Random random=new Random(729031);for(int test=0;test<4000;test++){int bodies=1+random.nextInt(5),vars=random.nextInt(4);int[] counts=new int[vars];for(int i=0;i<vars;i++)counts[i]=1+random.nextInt(3);int pairs=random.nextInt(7);Port[] ports=new Port[pairs*2];Link[] links=new Link[pairs];for(int j=0;j<ports.length;j++){int var=vars==0||random.nextBoolean()?-1:random.nextInt(vars);Point[] choices=new Point[var<0?1:counts[var]];for(int k=0;k<choices.length;k++)choices[k]=new Point((random.nextInt(5)-2)*1024L+(random.nextInt(8)==0?1:0),(random.nextInt(3)-1)*1024L,(random.nextInt(3)-1)*1024L,random.nextInt(6));ports[j]=port(random.nextInt(bodies),var,choices);}for(int j=0;j<pairs;j++)links[j]=new Link(j*2,j*2+1);compare(problem(bodies,random.nextInt(bodies),counts,ports,links));}}
 private static void planted(){
  Random random=new Random(729032);for(int test=0;test<1000;test++){int bodies=2+random.nextInt(4),vars=1+random.nextInt(3),pairs=2+random.nextInt(7);int[] counts=new int[vars],solution=new int[vars];for(int v=0;v<vars;v++){counts[v]=2+random.nextInt(2);solution[v]=random.nextInt(counts[v]);}long[][] shift=new long[bodies][3];for(long[] row:shift)for(int k=0;k<3;k++)row[k]=random.nextInt(7)-3;Port[] ports=new Port[pairs*2];Link[] links=new Link[pairs];
   for(int link=0;link<pairs;link++){int face=random.nextInt(6);long[] contact={random.nextInt(8)*1024L,random.nextInt(8)*1024L,random.nextInt(8)*1024L};for(int end=0;end<2;end++){int body=random.nextInt(bodies),var=random.nextInt(vars);Point[] choices=new Point[counts[var]];for(int choice=0;choice<choices.length;choice++)choices[choice]=choice==solution[var]?new Point(contact[0]-shift[body][0]*1024,contact[1]-shift[body][1]*1024,contact[2]-shift[body][2]*1024,face^end):new Point(random.nextInt(10)*1024L,random.nextInt(10)*1024L,random.nextInt(10)*1024L,random.nextInt(6));ports[link*2+end]=port(body,var,choices);}links[link]=new Link(link*2,link*2+1);}
   Problem p=problem(bodies,random.nextInt(bodies),counts,ports,links);require(oracle(p,solution)!=null,"planted contact assignment");compare(p);
  }
 }
 private static void resumable(){
  Problem p=problem(1,0,new int[]{3,2},new Port[0]);final int[] calls={0};Search s=new Search(p,new Validator(){public boolean accept(View v){calls[0]++;return v.option(0)==2&&v.option(1)==1;}},100);
  require(s.step(10,System.nanoTime()-1)==Status.PENDING&&s.trials()==0,"expired deadline yields without trial");require(s.step(1,Long.MAX_VALUE)==Status.PENDING&&s.trials()==1,"single trial yield");require(finish(s,1)==Status.SOLVED&&calls[0]==6,"validator visits complete assignments");Result r=s.result();require(r.option(0)==2&&r.option(1)==1,"validator selected result");
  Search limited=new Search(p,new Validator(){public boolean accept(View v){return false;}},2);require(finish(limited,10)==Status.LIMIT&&limited.trials()==2,"global limit explicit");
  Search exhausted=new Search(problem(1,0,new int[]{1},new Port[0]),new Validator(){public boolean accept(View v){return false;}},1);require(finish(exhausted,1)==Status.EXHAUSTED,"last trial exhaustive distinct from limit");
  final Search[] recursive=new Search[1];recursive[0]=new Search(p,new Validator(){public boolean accept(View v){try{recursive[0].step(1,Long.MAX_VALUE);throw new AssertionError("recursive allowed");}catch(IllegalStateException expected){return true;}}},100);require(finish(recursive[0],100)==Status.SOLVED,"borrowed view nonreentrant");
 }
 private static void bounds(){
  refuses(new Runnable(){public void run(){new Point(Long.MIN_VALUE,0,0,0);}},"long coordinate bound");refuses(new Runnable(){public void run(){new Point(0,0,0,6);}},"facing bound");
  refuses(new Runnable(){public void run(){problem(129,0,new int[0],new Port[0]);}},"body bound");refuses(new Runnable(){public void run(){problem(1,0,new int[]{4097},new Port[0]);}},"option bound");refuses(new Runnable(){public void run(){problem(1,0,new int[]{2},new Port[]{port(0,0,p(0,0))});}},"domain match");
  Point[] points={p(1024,0)};Port port=port(0,-1,points);points[0]=p(999,1);int[] counts={1};List<Port> ports=new ArrayList<Port>();ports.add(port);Problem p=new Problem(1,0,counts,ports,Collections.<Link>emptyList());counts[0]=2;ports.clear();Search s=new Search(p,10);require(finish(s,1)==Status.SOLVED&&p.port(0).choice(0).x==1024&&s.result().variables()==1,"immutable input snapshots");
  Port[] chain=new Port[254];Link[] links=new Link[127];for(int i=0;i<127;i++){chain[i*2]=port(i,-1,p(MAX_COORDINATE,0));chain[i*2+1]=port(i+1,-1,p(-MAX_COORDINATE,1));links[i]=new Link(i*2,i*2+1);}Search large=new Search(problem(128,127,new int[0],chain,links),1);require(finish(large,1)==Status.SOLVED&&large.result().translation(0,0)==-127*64000002L,"largest weighted path exact long arithmetic");
  final Point[] many=new Point[4096];Arrays.fill(many,p(0,0));final List<Port> excessive=new ArrayList<Port>();for(int i=0;i<17;i++)excessive.add(port(0,0,many));refuses(new Runnable(){public void run(){new Problem(1,0,new int[]{4096},excessive,Collections.<Link>emptyList());}},"aggregate points bound");
 }
 private static void allocation(){
  java.lang.management.ThreadMXBean base=ManagementFactory.getThreadMXBean();require(base instanceof com.sun.management.ThreadMXBean,"allocation counter available");com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)base;bean.setThreadAllocatedMemoryEnabled(true);
  Search s=new Search(problem(1,0,new int[]{4096,4096},new Port[0]),new Validator(){public boolean accept(View v){return false;}},10000000);for(int i=0;i<50000;i++)s.step(1,Long.MAX_VALUE);long id=Thread.currentThread().getId(),before=bean.getThreadAllocatedBytes(id);for(int i=0;i<10000;i++)s.step(1,Long.MAX_VALUE);long bytes=bean.getThreadAllocatedBytes(id)-before;require(bytes==0,"warm trials allocate zero bytes: "+bytes);System.out.println("Grid parking warm10000 trials allocatedBytes="+bytes);
 }
 public static void main(String[] args){examples();random();planted();resumable();bounds();allocation();System.out.println("Grid coupler parking PASS checks="+checks+" randomProblems=5000");}
}
