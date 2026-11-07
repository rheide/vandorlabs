package com.vandorlabs.physics;

import java.util.*;

/** Bounded local stroke fit for explicit body orientations. Not a global feasibility proof or ownership admission. */
public final class CouplerGraphConstraint {
 public static final int MAX_BODIES=128,MAX_LINKS=512,MAX_ARMS=64,MAX_ITERATIONS=24,MAX_EVALUATIONS=1+MAX_ITERATIONS*(2*MAX_ARMS+8);
 public static final double EPSILON=1e-7;
 /** Read-only parameters; fill parent XYZ, child XYZ for each link in construction-body frames. */
 public interface Model {boolean evaluate(double[] progress,double[] pairs);}
 public enum Failure {COUNT,DISCONNECTED,FRAME,STROKE,MODEL,UNSATISFIED,BUDGET}
 public static final class Link {public final int first,second;public Link(int first,int second){this.first=first;this.second=second;}}
 public static final class Graph {
  public final int bodies,root;private final Link[] links;
  public Graph(int bodies,int root,List<Link> links){Objects.requireNonNull(links);if(bodies<1||bodies>MAX_BODIES||root<0||root>=bodies||links.size()>MAX_LINKS)throw new IllegalArgumentException("Graph bounds");this.bodies=bodies;this.root=root;this.links=links.toArray(new Link[links.size()]);for(Link l:this.links)if(l==null||l.first<0||l.second<0||l.first>=bodies||l.second>=bodies)throw new IllegalArgumentException("Link body");}
  public int links(){return links.length;}public Link link(int index){return links[index];}
 }
 public static final class Result {
  public final Failure failure;public final int iterations,evaluations;public final double error;private final double[] progress;private final RigidTransform[] transforms;
  private Result(Failure failure,int iterations,int evaluations,double error,double[] progress,RigidTransform[] transforms){this.failure=failure;this.iterations=iterations;this.evaluations=evaluations;this.error=error;this.progress=progress;this.transforms=transforms;}
  public boolean accepted(){return failure==null;}public int arms(){return progress.length;}public double progress(int arm){return progress[arm];}public int bodies(){return transforms.length;}
  /** Defensive because the legacy RigidTransform exposes its matrix array. */
  public RigidTransform transform(int body){RigidTransform t=transforms[body];return new RigidTransform(t.r,t.x,t.y,t.z);}
 }
 /** Reusable scratch, with no retained Model/world references. Calls must be serialized. */
 public static final class Workspace {
  private final Graph graph;private final int arms,rows;private final int[] traversal,parent,edge,nonTree;private final boolean connected;
  private final double[] rotations,points,delta,positions,values,trialValues,low,high,jacobian,matrix,step,progress,trial,min,max;private int evaluations;private boolean busy;
  public Workspace(Graph graph,int arms){this.graph=Objects.requireNonNull(graph);if(arms<0||arms>MAX_ARMS)throw new IllegalArgumentException("Arm bound");this.arms=arms;parent=new int[graph.bodies];edge=new int[graph.bodies];traversal=new int[graph.bodies];Arrays.fill(parent,-1);boolean[] tree=new boolean[graph.links.length];parent[graph.root]=graph.root;traversal[0]=graph.root;int head=0,tail=1;
   while(head<tail){int body=traversal[head++];for(int i=0;i<graph.links.length;i++){Link l=graph.links[i];int other=l.first==body?l.second:l.second==body?l.first:-1;if(other>=0&&parent[other]<0){parent[other]=body;edge[other]=i;tree[i]=true;traversal[tail++]=other;}}}connected=tail==graph.bodies;int count=0;for(boolean b:tree)if(!b)count++;nonTree=new int[count];for(int i=0,j=0;i<tree.length;i++)if(!tree[i])nonTree[j++]=i;rows=count*3;
   rotations=new double[graph.bodies*9];points=new double[graph.links.length*6];delta=new double[graph.links.length*3];positions=new double[graph.bodies*3];values=new double[rows];trialValues=new double[rows];low=new double[rows];high=new double[rows];jacobian=new double[rows*arms];matrix=new double[arms*(arms+1)];step=new double[arms];progress=new double[arms];trial=new double[arms];min=new double[arms];max=new double[arms];
  }
  public int evaluations(){return evaluations;}public long retainedBytes(){return 512L+4L*(traversal.length+parent.length+edge.length+nonTree.length)+8L*(rotations.length+points.length+delta.length+positions.length+values.length+trialValues.length+low.length+high.length+jacobian.length+matrix.length+step.length+progress.length+trial.length+min.length+max.length);}
 }
 public static Result solve(Model model,CouplerArmConstraint.Stroke[] strokes,RigidTransform[] desiredRotations,Workspace w){
  if(w==null||model==null||strokes==null||strokes.length!=w.arms||desiredRotations==null||desiredRotations.length!=w.graph.bodies)return refused(Failure.COUNT,0,0,Double.NaN);
  if(w.busy)throw new IllegalStateException("Recursive graph fit");w.busy=true;try{w.evaluations=0;if(!w.connected)return refused(Failure.DISCONNECTED,0,0,Double.NaN);
   for(int i=0;i<desiredRotations.length;i++){RigidTransform t=desiredRotations[i];if(!rotation(t))return refused(Failure.FRAME,0,0,Double.NaN);System.arraycopy(t.r,0,w.rotations,i*9,9);}
   for(int i=0;i<w.arms;i++){CouplerArmConstraint.Stroke s=strokes[i];if(s==null||!finite(s.min)||!finite(s.max)||!finite(s.requested)||s.min<0||s.max>1||s.min>s.max||s.requested<s.min||s.requested>s.max)return refused(Failure.STROKE,0,0,Double.NaN);w.min[i]=s.min;w.max[i]=s.max;w.progress[i]=s.requested;}
   if(!evaluate(model,w,w.progress,w.values))return refused(Failure.MODEL,0,w.evaluations,Double.NaN);double error=error(w.values);int iteration=0;
   for(;iteration<MAX_ITERATIONS&&error>EPSILON;iteration++){
    if(w.arms==0)break;
    for(int arm=0;arm<w.arms;arm++){double old=w.progress[arm],lo=Math.max(w.min[arm],old-1e-5),hi=Math.min(w.max[arm],old+1e-5);if(hi==lo){for(int row=0;row<w.rows;row++)w.jacobian[row*w.arms+arm]=0;continue;}
     w.progress[arm]=lo;boolean valid=evaluate(model,w,w.progress,w.low);w.progress[arm]=hi;valid&=evaluate(model,w,w.progress,w.high);w.progress[arm]=old;if(!valid)return refused(w.evaluations>=MAX_EVALUATIONS?Failure.BUDGET:Failure.MODEL,iteration,w.evaluations,error);for(int row=0;row<w.rows;row++)w.jacobian[row*w.arms+arm]=(w.high[row]-w.low[row])/(hi-lo);
    }
    for(int a=0;a<w.arms;a++){double rhs=0;for(int row=0;row<w.rows;row++)rhs-=w.jacobian[row*w.arms+a]*w.values[row];for(int b=0;b<w.arms;b++){double v=a==b?1e-10:0;for(int row=0;row<w.rows;row++)v+=w.jacobian[row*w.arms+a]*w.jacobian[row*w.arms+b];w.matrix[a*(w.arms+1)+b]=v;}w.matrix[a*(w.arms+1)+w.arms]=rhs;}
    if(!linear(w))break;boolean improved=false;for(double scale=1;scale>=1.0/128;scale*=.5){for(int arm=0;arm<w.arms;arm++)w.trial[arm]=Math.max(w.min[arm],Math.min(w.max[arm],w.progress[arm]+scale*w.step[arm]));if(!evaluate(model,w,w.trial,w.trialValues)){if(w.evaluations>=MAX_EVALUATIONS)return refused(Failure.BUDGET,iteration,w.evaluations,error);continue;}double next=error(w.trialValues);if(next<error){System.arraycopy(w.trial,0,w.progress,0,w.arms);System.arraycopy(w.trialValues,0,w.values,0,w.rows);error=next;improved=true;break;}}if(!improved)break;
   }
   if(error>EPSILON)return refused(Failure.UNSATISFIED,iteration,w.evaluations,error);
   RigidTransform[] transforms=new RigidTransform[w.graph.bodies];for(int body=0;body<transforms.length;body++){double[] r=new double[9];System.arraycopy(w.rotations,body*9,r,0,9);transforms[body]=new RigidTransform(r,w.positions[body*3],w.positions[body*3+1],w.positions[body*3+2]);}return new Result(null,iteration,w.evaluations,error,w.progress.clone(),transforms);
  }finally{w.busy=false;}
 }
 private static boolean evaluate(Model model,Workspace w,double[] progress,double[] residual){
  if(w.evaluations>=MAX_EVALUATIONS)return false;w.evaluations++;Arrays.fill(w.points,Double.NaN);if(!model.evaluate(progress,w.points))return false;for(double x:w.points)if(!finite(x)||Math.abs(x)>1000000)return false;
  for(int link=0;link<w.graph.links.length;link++){Link l=w.graph.links[link];for(int axis=0;axis<3;axis++){int a=l.first*9+axis*3,b=l.second*9+axis*3,at=link*6;w.delta[link*3+axis]=w.rotations[a]*w.points[at]+w.rotations[a+1]*w.points[at+1]+w.rotations[a+2]*w.points[at+2]-w.rotations[b]*w.points[at+3]-w.rotations[b+1]*w.points[at+4]-w.rotations[b+2]*w.points[at+5];}}
  int root=w.graph.root;w.positions[root*3]=w.positions[root*3+1]=w.positions[root*3+2]=0;for(int i=1;i<w.traversal.length;i++){int body=w.traversal[i],link=w.edge[body],parent=w.parent[body];double sign=w.graph.links[link].first==parent?1:-1;for(int axis=0;axis<3;axis++){double v=w.positions[parent*3+axis]+sign*w.delta[link*3+axis];if(!finite(v)||Math.abs(v)>768000000)return false;w.positions[body*3+axis]=v;}}
  for(int i=0;i<w.nonTree.length;i++){int link=w.nonTree[i];Link l=w.graph.links[link];for(int axis=0;axis<3;axis++)residual[i*3+axis]=w.positions[l.second*3+axis]-w.positions[l.first*3+axis]-w.delta[link*3+axis];}return true;
 }
 private static double error(double[] residual){double max=0;for(int i=0;i<residual.length;i+=3)max=Math.max(max,Math.sqrt(residual[i]*residual[i]+residual[i+1]*residual[i+1]+residual[i+2]*residual[i+2]));return max;}
 private static boolean linear(Workspace w){int n=w.arms,stride=n+1;for(int at=0;at<n;at++){int pivot=at;for(int row=at+1;row<n;row++)if(Math.abs(w.matrix[row*stride+at])>Math.abs(w.matrix[pivot*stride+at]))pivot=row;if(Math.abs(w.matrix[pivot*stride+at])<1e-18)return false;if(pivot!=at)for(int col=at;col<=n;col++){double v=w.matrix[at*stride+col];w.matrix[at*stride+col]=w.matrix[pivot*stride+col];w.matrix[pivot*stride+col]=v;}double v=w.matrix[at*stride+at];for(int col=at;col<=n;col++)w.matrix[at*stride+col]/=v;for(int row=0;row<n;row++)if(row!=at){double f=w.matrix[row*stride+at];for(int col=at;col<=n;col++)w.matrix[row*stride+col]-=f*w.matrix[at*stride+col];}}for(int i=0;i<n;i++){w.step[i]=w.matrix[i*stride+n];if(!finite(w.step[i]))return false;}return true;}
 private static boolean rotation(RigidTransform t){if(t==null||!finite(t.x)||!finite(t.y)||!finite(t.z))return false;double[] m=t.r;for(double v:m)if(!finite(v))return false;for(int a=0;a<3;a++)for(int b=a;b<3;b++)if(Math.abs(m[a]*m[b]+m[3+a]*m[3+b]+m[6+a]*m[6+b]-(a==b?1:0))>1e-8)return false;return Math.abs(m[0]*(m[4]*m[8]-m[5]*m[7])-m[1]*(m[3]*m[8]-m[5]*m[6])+m[2]*(m[3]*m[7]-m[4]*m[6])-1)<1e-8;}
 private static boolean finite(double v){return Double.isFinite(v);}
 private static Result refused(Failure failure,int iterations,int evaluations,double error){return new Result(failure,iterations,evaluations,error,new double[0],new RigidTransform[0]);}
 private CouplerGraphConstraint(){}
}
