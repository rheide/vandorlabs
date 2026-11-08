package com.vandorlabs.physics;

import java.util.Arrays;
import java.util.Objects;

/** Local contact projection with free body rotations. Not terrain, joint-limit or ownership admission. */
public final class CouplerPoseConstraint {
 public static final double EPSILON=1e-7;
 public static final int MAX_ITERATIONS=32,MAX_EVALUATIONS=8192;
 public static final long DEFAULT_WORK_OPS=8_000_000L,MAX_WORK_OPS=64_000_000L;
 public enum Failure {FRAME,UNSAT,BUDGET}
 public static final class Result {
  public final Failure failure;public final int iterations,evaluations,modelEvaluations;public final long workOps;public final double error;
  private final double[] phases,rotations,positions;
  private Result(Failure f,Workspace w,int iteration,double error,boolean accepted){failure=f;iterations=iteration;evaluations=w==null?0:w.evaluations;modelEvaluations=w==null?0:w.modelEvaluations;workOps=w==null?0:w.ops;this.error=error;phases=accepted?w.phase.clone():EMPTY;rotations=accepted?w.rotation.clone():EMPTY;positions=accepted?w.position.clone():EMPTY;}
  public boolean accepted(){return failure==null;}public int arms(){return phases.length;}public int bodies(){return positions.length/3;}public double phase(int arm){return phases[arm];}public double progress(int arm){return phase(arm);}
  public double rotation(int body,int row,int column){if(row<0||row>2||column<0||column>2)throw new IndexOutOfBoundsException();return rotations[body*9+row*3+column];}
  public double translation(int body,int axis){if(axis<0||axis>2)throw new IndexOutOfBoundsException();return positions[body*3+axis];}
  public RigidTransform transform(int body){double[] r=new double[9];System.arraycopy(rotations,body*9,r,0,9);return new RigidTransform(r,positions[body*3],positions[body*3+1],positions[body*3+2]);}
 }
 private static final double[] EMPTY=new double[0];
 /** Serialized caller-owned workspace. No model, terrain, entity or world is retained. */
 public static final class Workspace {
  private final CouplerGraphConstraint.Graph graph;private final int arms,rows,capacity;private final long limit;
  private final int[] traversal,parent,edge,nonTree,kind,index;private final boolean connected;
  private final double[] phase,min,max,trialPhase,modelInput,rotation,trialRotation,backup,points,tempPoints,trialPoints,delta,position,residual,low,high,trialResidual,jacobian,matrix,step;
  private int columns,evaluations,modelEvaluations;private long ops;private boolean busy,budget;
  public Workspace(CouplerGraphConstraint.Graph graph,int arms){this(graph,arms,DEFAULT_WORK_OPS);}
  public Workspace(CouplerGraphConstraint.Graph graph,int arms,long maxWorkOps){
   this.graph=Objects.requireNonNull(graph);if(arms<0||arms>CouplerGraphConstraint.MAX_ARMS||maxWorkOps<1||maxWorkOps>MAX_WORK_OPS)throw new IllegalArgumentException("Pose workspace bounds");this.arms=arms;limit=maxWorkOps;
   int bodies=graph.bodies,links=graph.links();parent=new int[bodies];edge=new int[bodies];traversal=new int[bodies];Arrays.fill(parent,-1);boolean[] tree=new boolean[links];parent[graph.root]=graph.root;traversal[0]=graph.root;int head=0,tail=1;
   while(head<tail){int body=traversal[head++];for(int i=0;i<links;i++){CouplerGraphConstraint.Link l=graph.link(i);int other=l.first==body?l.second:l.second==body?l.first:-1;if(other>=0&&parent[other]<0){parent[other]=body;edge[other]=i;tree[i]=true;traversal[tail++]=other;}}}
   connected=tail==bodies;int count=0;for(boolean b:tree)if(!b)count++;nonTree=new int[count];for(int i=0,j=0;i<links;i++)if(!tree[i])nonTree[j++]=i;rows=count*3;capacity=arms+3*(bodies-1);kind=new int[capacity];index=new int[capacity];
   phase=new double[arms];min=new double[arms];max=new double[arms];trialPhase=new double[arms];modelInput=new double[arms];rotation=new double[bodies*9];trialRotation=new double[bodies*9];backup=new double[9];points=new double[links*6];tempPoints=new double[links*6];trialPoints=new double[links*6];delta=new double[links*3];position=new double[bodies*3];residual=new double[rows];low=new double[rows];high=new double[rows];trialResidual=new double[rows];jacobian=new double[rows*capacity];matrix=new double[capacity*(capacity+1)];step=new double[capacity];
  }
  public long workOps(){return ops;}public int evaluations(){return evaluations;}public int modelEvaluations(){return modelEvaluations;}public long workLimit(){return limit;}
  /** Workspace arrays/overheads; excludes the immutable caller-owned Graph and returned Results. */
  public long retainedBytes(){return 1024L+4L*(traversal.length+parent.length+edge.length+nonTree.length+kind.length+index.length)+8L*(phase.length+min.length+max.length+trialPhase.length+modelInput.length+rotation.length+trialRotation.length+backup.length+points.length+tempPoints.length+trialPoints.length+delta.length+position.length+residual.length+low.length+high.length+trialResidual.length+jacobian.length+matrix.length+step.length);}
 }
 public static Result solve(CouplerGraphConstraint.Model model,CouplerGraphConstraint.Graph graph,CouplerArmConstraint.Stroke[] strokes,boolean[] driven,RigidTransform[] rotationPrior,Workspace w){
  if(w==null)return new Result(Failure.FRAME,null,0,Double.NaN,false);if(w.busy)throw new IllegalStateException("Recursive pose solve");w.busy=true;
  try{
   w.ops=0;w.evaluations=0;w.modelEvaluations=0;w.budget=false;w.columns=0;
   if(model==null||graph!=w.graph||strokes==null||strokes.length!=w.arms||driven==null||driven.length!=w.arms||rotationPrior==null||rotationPrior.length!=graph.bodies||!w.connected)return fail(w,Failure.FRAME,0,Double.NaN);
   if(!spend(w,256L+128L*graph.bodies+16L*w.arms))return fail(w,Failure.BUDGET,0,Double.NaN);
   for(int body=0;body<graph.bodies;body++){RigidTransform r=rotationPrior[body];if(!proper(r))return fail(w,Failure.FRAME,0,Double.NaN);System.arraycopy(r.r,0,w.rotation,body*9,9);if(body!=graph.root)for(int axis=0;axis<3;axis++){w.kind[w.columns]=1+axis;w.index[w.columns++]=body;}}
   Arrays.fill(w.rotation,graph.root*9,graph.root*9+9,0);for(int axis=0;axis<3;axis++)w.rotation[graph.root*9+axis*3+axis]=1;
   for(int arm=0;arm<w.arms;arm++){CouplerArmConstraint.Stroke s=strokes[arm];if(s==null||!finite(s.min)||!finite(s.max)||!finite(s.requested)||s.min<0||s.max>1||s.min>s.max||s.requested<s.min||s.requested>s.max)return fail(w,Failure.FRAME,0,Double.NaN);w.phase[arm]=s.requested;w.min[arm]=driven[arm]?s.requested:s.min;w.max[arm]=driven[arm]?s.requested:s.max;if(w.min[arm]!=w.max[arm]){w.kind[w.columns]=0;w.index[w.columns++]=arm;}}
   if(!model(model,w,w.phase,w.points)||!evaluate(w,w.rotation,w.points,w.residual))return fail(w,w.budget?Failure.BUDGET:Failure.FRAME,0,Double.NaN);
   double error=error(w.residual);double damping=1e-8;int iteration=0;
   for(;iteration<MAX_ITERATIONS&&error>EPSILON;iteration++){
    if(w.columns==0)return fail(w,Failure.UNSAT,iteration,error);
    // Refuse a dense pass before filling its normal equations. No truncated solution is accepted.
    long normalWork=4L*w.rows*w.columns*(w.columns+1)+4L*w.columns*w.columns*w.columns;
    if(normalWork>w.limit-w.ops)return fail(w,Failure.BUDGET,iteration,error);
    for(int column=0;column<w.columns;column++){
     int index=w.index[column],kind=w.kind[column];double denominator;
     if(kind==0){double old=w.phase[index],lo=Math.max(w.min[index],old-1e-5),hi=Math.min(w.max[index],old+1e-5);denominator=hi-lo;w.phase[index]=lo;boolean ok=model(model,w,w.phase,w.tempPoints)&&evaluate(w,w.rotation,w.tempPoints,w.low);w.phase[index]=hi;ok=ok&&model(model,w,w.phase,w.tempPoints)&&evaluate(w,w.rotation,w.tempPoints,w.high);w.phase[index]=old;if(!ok)return fail(w,w.budget?Failure.BUDGET:Failure.FRAME,iteration,error);}
     else{int at=index*9;System.arraycopy(w.rotation,at,w.backup,0,9);rotate(w.rotation,at,w.backup,0,kind-1,-1e-5);boolean ok=evaluate(w,w.rotation,w.points,w.low);rotate(w.rotation,at,w.backup,0,kind-1,1e-5);ok=ok&&evaluate(w,w.rotation,w.points,w.high);System.arraycopy(w.backup,0,w.rotation,at,9);denominator=2e-5;if(!ok)return fail(w,w.budget?Failure.BUDGET:Failure.FRAME,iteration,error);}
     if(!spend(w,6L*w.rows))return fail(w,Failure.BUDGET,iteration,error);for(int row=0;row<w.rows;row++)w.jacobian[row*w.capacity+column]=(w.high[row]-w.low[row])/denominator;
    }
    if(!spend(w,normalWork))return fail(w,Failure.BUDGET,iteration,error);
    int n=w.columns,stride=n+1;for(int a=0;a<n;a++){double rhs=0;for(int row=0;row<w.rows;row++)rhs-=w.jacobian[row*w.capacity+a]*w.residual[row];for(int b=0;b<n;b++){double value=a==b?damping:0;for(int row=0;row<w.rows;row++)value+=w.jacobian[row*w.capacity+a]*w.jacobian[row*w.capacity+b];w.matrix[a*stride+b]=value;}w.matrix[a*stride+n]=rhs;}
    if(!linear(w)){damping*=100;continue;}
    boolean improved=false;for(double scale=1;scale>=1D/256;scale*=.5){
     if(!spend(w,256L*graph.bodies+16L*w.arms+16L*n))return fail(w,Failure.BUDGET,iteration,error);
     System.arraycopy(w.phase,0,w.trialPhase,0,w.arms);System.arraycopy(w.rotation,0,w.trialRotation,0,w.rotation.length);boolean changedPhase=false;
     for(int col=0;col<n;col++)if(w.kind[col]==0){int arm=w.index[col];w.trialPhase[arm]=Math.max(w.min[arm],Math.min(w.max[arm],w.phase[arm]+scale*w.step[col]));changedPhase|=w.trialPhase[arm]!=w.phase[arm];}
     for(int body=0,col=0;body<graph.bodies;body++)if(body!=graph.root){double x=w.step[col++],y=w.step[col++],z=w.step[col++],length=Math.sqrt(x*x+y*y+z*z),factor=scale*(length>.35?.35/length:1);if(!exp(w.trialRotation,body*9,w.rotation,body*9,x*factor,y*factor,z*factor))return fail(w,Failure.FRAME,iteration,error);}
     double[] p=w.points;if(changedPhase){if(!model(model,w,w.trialPhase,w.trialPoints))return fail(w,w.budget?Failure.BUDGET:Failure.FRAME,iteration,error);p=w.trialPoints;}
     if(!evaluate(w,w.trialRotation,p,w.trialResidual))return fail(w,w.budget?Failure.BUDGET:Failure.FRAME,iteration,error);double next=error(w.trialResidual);
     if(next<error){System.arraycopy(w.trialPhase,0,w.phase,0,w.arms);System.arraycopy(w.trialRotation,0,w.rotation,0,w.rotation.length);if(changedPhase)System.arraycopy(w.trialPoints,0,w.points,0,w.points.length);System.arraycopy(w.trialResidual,0,w.residual,0,w.rows);error=next;improved=true;damping=Math.max(1e-12,damping*.1);break;}
    }
    if(!improved)damping*=100;
   }
   if(error>EPSILON)return fail(w,Failure.UNSAT,iteration,error);
   // Recompute every tree/parallel/loop contact, not only the independent residual basis.
   if(!evaluate(w,w.rotation,w.points,w.residual))return fail(w,w.budget?Failure.BUDGET:Failure.FRAME,iteration,error);if(!spend(w,12L*graph.links()))return fail(w,Failure.BUDGET,iteration,error);double all=0;
   for(int link=0;link<graph.links();link++){CouplerGraphConstraint.Link l=graph.link(link);double sum=0;for(int axis=0;axis<3;axis++){double v=w.position[l.second*3+axis]-w.position[l.first*3+axis]-w.delta[link*3+axis];sum+=v*v;}all=Math.max(all,Math.sqrt(sum));}
   if(all>EPSILON)return fail(w,Failure.UNSAT,iteration,all);return new Result(null,w,iteration,all,true);
  }finally{w.busy=false;}
 }
 private static boolean spend(Workspace w,long cost){if(cost<0||cost>w.limit-w.ops){w.budget=true;return false;}w.ops+=cost;return true;}
 // Invocation/point-processing charges are bounded here; arbitrary callback internals remain the caller's responsibility.
 private static boolean model(CouplerGraphConstraint.Model model,Workspace w,double[] phase,double[] out){if(!spend(w,128L+16L*w.arms+32L*w.graph.links()))return false;System.arraycopy(phase,0,w.modelInput,0,phase.length);Arrays.fill(out,Double.NaN);w.modelEvaluations++;if(!model.evaluate(w.modelInput,out))return false;for(int i=0;i<phase.length;i++)if(Double.doubleToLongBits(w.modelInput[i])!=Double.doubleToLongBits(phase[i]))return false;for(double x:out)if(!finite(x)||Math.abs(x)>1_000_000)return false;return true;}
 private static boolean evaluate(Workspace w,double[] rotation,double[] points,double[] residual){
  if(w.evaluations>=MAX_EVALUATIONS){w.budget=true;return false;}if(!spend(w,128L+64L*w.graph.links()+16L*w.graph.bodies+16L*w.nonTree.length))return false;w.evaluations++;
  for(int i=0;i<w.graph.links();i++){CouplerGraphConstraint.Link l=w.graph.link(i);int at=i*6;for(int axis=0;axis<3;axis++){int a=l.first*9+axis*3,b=l.second*9+axis*3;w.delta[i*3+axis]=rotation[a]*points[at]+rotation[a+1]*points[at+1]+rotation[a+2]*points[at+2]-rotation[b]*points[at+3]-rotation[b+1]*points[at+4]-rotation[b+2]*points[at+5];}}
  int root=w.graph.root;w.position[root*3]=w.position[root*3+1]=w.position[root*3+2]=0;
  for(int i=1;i<w.traversal.length;i++){int body=w.traversal[i],parent=w.parent[body],edge=w.edge[body];double sign=w.graph.link(edge).first==parent?1:-1;for(int axis=0;axis<3;axis++){double value=w.position[parent*3+axis]+sign*w.delta[edge*3+axis];if(!finite(value)||Math.abs(value)>768_000_000)return false;w.position[body*3+axis]=value;}}
  for(int i=0;i<w.nonTree.length;i++){int edge=w.nonTree[i];CouplerGraphConstraint.Link l=w.graph.link(edge);for(int axis=0;axis<3;axis++)residual[i*3+axis]=w.position[l.second*3+axis]-w.position[l.first*3+axis]-w.delta[edge*3+axis];}return true;
 }
 /** Left SO(3) perturbation around a world/construction-body axis. */
 private static void rotate(double[] out,int at,double[] source,int from,int axis,double angle){double c=Math.cos(angle),s=Math.sin(angle);int a=(axis+1)%3,b=(axis+2)%3;for(int col=0;col<3;col++){out[at+axis*3+col]=source[from+axis*3+col];out[at+a*3+col]=c*source[from+a*3+col]-s*source[from+b*3+col];out[at+b*3+col]=s*source[from+a*3+col]+c*source[from+b*3+col];}}
 private static boolean exp(double[] out,int at,double[] source,int from,double x,double y,double z){double theta=Math.sqrt(x*x+y*y+z*z);if(!finite(theta))return false;double a,b;if(theta<1e-8){double q=theta*theta;a=1-q/6;b=.5-q/24;}else{a=Math.sin(theta)/theta;b=(1-Math.cos(theta))/(theta*theta);}double xx=x*x,yy=y*y,zz=z*z,xy=x*y,xz=x*z,yz=y*z;
  double r00=1-b*(yy+zz),r01=b*xy-a*z,r02=b*xz+a*y,r10=b*xy+a*z,r11=1-b*(xx+zz),r12=b*yz-a*x,r20=b*xz-a*y,r21=b*yz+a*x,r22=1-b*(xx+yy);
  for(int col=0;col<3;col++){double u=source[from+col],v=source[from+3+col],w=source[from+6+col];out[at+col]=r00*u+r01*v+r02*w;out[at+3+col]=r10*u+r11*v+r12*w;out[at+6+col]=r20*u+r21*v+r22*w;}return true;
 }
 private static boolean linear(Workspace w){int n=w.columns,stride=n+1;for(int at=0;at<n;at++){int pivot=at;for(int row=at+1;row<n;row++)if(Math.abs(w.matrix[row*stride+at])>Math.abs(w.matrix[pivot*stride+at]))pivot=row;if(Math.abs(w.matrix[pivot*stride+at])<1e-20)return false;if(pivot!=at)for(int col=at;col<=n;col++){double v=w.matrix[at*stride+col];w.matrix[at*stride+col]=w.matrix[pivot*stride+col];w.matrix[pivot*stride+col]=v;}double d=w.matrix[at*stride+at];for(int col=at;col<=n;col++)w.matrix[at*stride+col]/=d;for(int row=0;row<n;row++)if(row!=at){double f=w.matrix[row*stride+at];for(int col=at;col<=n;col++)w.matrix[row*stride+col]-=f*w.matrix[at*stride+col];}}for(int i=0;i<n;i++){w.step[i]=w.matrix[i*stride+n];if(!finite(w.step[i]))return false;}return true;}
 private static double error(double[] values){double error=0;for(int i=0;i<values.length;i+=3)error=Math.max(error,Math.sqrt(values[i]*values[i]+values[i+1]*values[i+1]+values[i+2]*values[i+2]));return error;}
 private static boolean proper(RigidTransform t){if(t==null||!finite(t.x)||!finite(t.y)||!finite(t.z))return false;double[] m=t.r;for(double x:m)if(!finite(x))return false;for(int a=0;a<3;a++)for(int b=a;b<3;b++)if(Math.abs(m[a]*m[b]+m[3+a]*m[3+b]+m[6+a]*m[6+b]-(a==b?1:0))>1e-8)return false;return Math.abs(m[0]*(m[4]*m[8]-m[5]*m[7])-m[1]*(m[3]*m[8]-m[5]*m[6])+m[2]*(m[3]*m[7]-m[4]*m[6])-1)<1e-8;}
 private static boolean finite(double x){return Double.isFinite(x);}
 private static Result fail(Workspace w,Failure f,int iteration,double error){return new Result(f,w,iteration,error,false);}
 private CouplerPoseConstraint(){}
}
