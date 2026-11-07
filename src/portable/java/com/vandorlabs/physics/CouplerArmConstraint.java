package com.vandorlabs.physics;

/** Bounded stroke fitting for an explicit requested two-body orientation. No world or ownership effects. */
public final class CouplerArmConstraint {
    public static final int MAX_PAIRS=64,MAX_ARMS=64,MAX_ITERATIONS=24,MAX_EVALUATIONS=1+MAX_ITERATIONS*(2*MAX_ARMS+8);
    private static final double EPS=1e-7;
    /** Fill six scalars per pair: parent XYZ, child XYZ in their respective body frames. */
    public interface Model {boolean evaluate(double[] progress,double[] pairs);}
    public static final class Stroke {
        public final double min,max,requested;
        public Stroke(double min,double max,double requested){this.min=min;this.max=max;this.requested=requested;}
    }
    public enum Failure {COUNT,FRAME,STROKE,MODEL,UNSATISFIED}
    public static final class Result {
        public final RigidTransform transform;public final Failure failure;public final int iterations;public final double error;
        private final double[] progress;
        private Result(RigidTransform transform,Failure failure,double[] progress,int iterations,double error){this.transform=transform;this.failure=failure;this.progress=progress;this.iterations=iterations;this.error=error;}
        public boolean accepted(){return transform!=null;}
        public int arms(){return progress.length;}
        public double progress(int arm){return progress[arm];}
    }
    /** Reusable bounded scratch; contains no model/world references. One owner must serialize its use. */
    public static final class Workspace {
        private final int pairs,arms,rows;
        private int evaluations;
        private final double[] values,trialValues,samples,other,residual,trialResidual,derivatives,matrix,step,progress,trial;
        public Workspace(int pairs,int arms){
            if(pairs<1||pairs>MAX_PAIRS||arms<0||arms>MAX_ARMS)throw new IllegalArgumentException("Coupler fit exceeds bounded dimensions");this.pairs=pairs;this.arms=arms;rows=pairs*3;
            values=new double[pairs*6];trialValues=new double[pairs*6];samples=new double[pairs*6];other=new double[pairs*6];residual=new double[rows];trialResidual=new double[rows];derivatives=new double[rows*arms];matrix=new double[arms*(arms+1)];step=new double[arms];progress=new double[arms];trial=new double[arms];
        }
        public int evaluations(){return evaluations;}
        public long retainedBytes(){return 256L+8L*(values.length+trialValues.length+samples.length+other.length+residual.length+trialResidual.length+derivatives.length+matrix.length+step.length+progress.length+trial.length);}
    }
    public static Result solve(Model model,Stroke[] strokes,RigidTransform desired,Workspace w){
        if(model==null||w==null||strokes==null||strokes.length!=w.arms)return refused(Failure.COUNT,0,Double.NaN);
        w.evaluations=0;
        if(!rotation(desired))return refused(Failure.FRAME,0,Double.NaN);
        for(int i=0;i<strokes.length;i++){Stroke s=strokes[i];if(s==null||!Double.isFinite(s.min)||!Double.isFinite(s.max)||!Double.isFinite(s.requested)||s.min<0||s.max>1||s.min>s.max||s.requested<s.min||s.requested>s.max)return refused(Failure.STROKE,0,Double.NaN);w.progress[i]=s.requested;}
        if(!evaluate(model,w,w.progress,w.values))return refused(Failure.MODEL,0,Double.NaN);
        double error=residual(w.values,desired.r,w.residual,w.pairs);int iteration=0;
        for(;iteration<MAX_ITERATIONS&&error>EPS;iteration++){
            if(w.arms==0)break;
            for(int arm=0;arm<w.arms;arm++){
                double old=w.progress[arm],lo=Math.max(strokes[arm].min,old-1e-5),hi=Math.min(strokes[arm].max,old+1e-5);
                if(hi==lo){for(int row=0;row<w.rows;row++)w.derivatives[row*w.arms+arm]=0;continue;}
                w.progress[arm]=lo;boolean valid=evaluate(model,w,w.progress,w.samples);w.progress[arm]=hi;valid&=evaluate(model,w,w.progress,w.other);w.progress[arm]=old;if(!valid)return refused(Failure.MODEL,iteration,error);
                double ax=0,ay=0,az=0;double[] r=desired.r;
                for(int pair=0;pair<w.pairs;pair++){int at=pair*6;double x=(w.other[at]-w.samples[at])-(r[0]*(w.other[at+3]-w.samples[at+3])+r[1]*(w.other[at+4]-w.samples[at+4])+r[2]*(w.other[at+5]-w.samples[at+5])),y=(w.other[at+1]-w.samples[at+1])-(r[3]*(w.other[at+3]-w.samples[at+3])+r[4]*(w.other[at+4]-w.samples[at+4])+r[5]*(w.other[at+5]-w.samples[at+5])),z=(w.other[at+2]-w.samples[at+2])-(r[6]*(w.other[at+3]-w.samples[at+3])+r[7]*(w.other[at+4]-w.samples[at+4])+r[8]*(w.other[at+5]-w.samples[at+5]));int row=pair*3;w.derivatives[row*w.arms+arm]=x;w.derivatives[(row+1)*w.arms+arm]=y;w.derivatives[(row+2)*w.arms+arm]=z;ax+=x;ay+=y;az+=z;}
                ax/=w.pairs;ay/=w.pairs;az/=w.pairs;for(int pair=0;pair<w.pairs;pair++){int row=pair*3;w.derivatives[row*w.arms+arm]=(w.derivatives[row*w.arms+arm]-ax)/(hi-lo);w.derivatives[(row+1)*w.arms+arm]=(w.derivatives[(row+1)*w.arms+arm]-ay)/(hi-lo);w.derivatives[(row+2)*w.arms+arm]=(w.derivatives[(row+2)*w.arms+arm]-az)/(hi-lo);}
            }
            residual(w.values,desired.r,w.residual,w.pairs);
            for(int a=0;a<w.arms;a++){double rhs=0;for(int row=0;row<w.rows;row++)rhs-=w.derivatives[row*w.arms+a]*w.residual[row];for(int b=0;b<w.arms;b++){double v=a==b?1e-10:0;for(int row=0;row<w.rows;row++)v+=w.derivatives[row*w.arms+a]*w.derivatives[row*w.arms+b];w.matrix[a*(w.arms+1)+b]=v;}w.matrix[a*(w.arms+1)+w.arms]=rhs;}
            if(!linear(w))break;boolean improved=false;
            for(double scale=1;scale>=1.0/128;scale*=.5){for(int arm=0;arm<w.arms;arm++)w.trial[arm]=Math.max(strokes[arm].min,Math.min(strokes[arm].max,w.progress[arm]+scale*w.step[arm]));if(!evaluate(model,w,w.trial,w.trialValues))continue;double next=residual(w.trialValues,desired.r,w.trialResidual,w.pairs);if(next<error){System.arraycopy(w.trial,0,w.progress,0,w.arms);System.arraycopy(w.trialValues,0,w.values,0,w.values.length);System.arraycopy(w.trialResidual,0,w.residual,0,w.rows);error=next;improved=true;break;}}
            if(!improved)break;
        }
        if(error>EPS)return refused(Failure.UNSATISFIED,iteration,error);
        double x=0,y=0,z=0;double[] r=desired.r;for(int pair=0;pair<w.pairs;pair++){int at=pair*6;x+=w.values[at]-r[0]*w.values[at+3]-r[1]*w.values[at+4]-r[2]*w.values[at+5];y+=w.values[at+1]-r[3]*w.values[at+3]-r[4]*w.values[at+4]-r[5]*w.values[at+5];z+=w.values[at+2]-r[6]*w.values[at+3]-r[7]*w.values[at+4]-r[8]*w.values[at+5];}
        return new Result(new RigidTransform(r,x/w.pairs,y/w.pairs,z/w.pairs),null,w.progress.clone(),iteration,error);
    }
    private static boolean evaluate(Model model,Workspace w,double[] parameters,double[] out){w.evaluations++;for(int i=0;i<out.length;i++)out[i]=Double.NaN;if(!model.evaluate(parameters,out))return false;for(double value:out)if(!Double.isFinite(value)||Math.abs(value)>1000000)return false;return true;}
    private static double residual(double[] points,double[] r,double[] out,int pairs){double x=0,y=0,z=0;for(int pair=0;pair<pairs;pair++){int at=pair*6,row=pair*3;x+=(out[row]=points[at]-r[0]*points[at+3]-r[1]*points[at+4]-r[2]*points[at+5]);y+=(out[row+1]=points[at+1]-r[3]*points[at+3]-r[4]*points[at+4]-r[5]*points[at+5]);z+=(out[row+2]=points[at+2]-r[6]*points[at+3]-r[7]*points[at+4]-r[8]*points[at+5]);}x/=pairs;y/=pairs;z/=pairs;double error=0;for(int pair=0;pair<pairs;pair++){int row=pair*3;out[row]-=x;out[row+1]-=y;out[row+2]-=z;error=Math.max(error,Math.sqrt(out[row]*out[row]+out[row+1]*out[row+1]+out[row+2]*out[row+2]));}return error;}
    private static boolean linear(Workspace w){int n=w.arms,stride=n+1;for(int at=0;at<n;at++){int pivot=at;for(int row=at+1;row<n;row++)if(Math.abs(w.matrix[row*stride+at])>Math.abs(w.matrix[pivot*stride+at]))pivot=row;if(Math.abs(w.matrix[pivot*stride+at])<1e-18)return false;if(pivot!=at)for(int col=at;col<=n;col++){double v=w.matrix[at*stride+col];w.matrix[at*stride+col]=w.matrix[pivot*stride+col];w.matrix[pivot*stride+col]=v;}double v=w.matrix[at*stride+at];for(int col=at;col<=n;col++)w.matrix[at*stride+col]/=v;for(int row=0;row<n;row++)if(row!=at){double f=w.matrix[row*stride+at];for(int col=at;col<=n;col++)w.matrix[row*stride+col]-=f*w.matrix[at*stride+col];}}for(int i=0;i<n;i++){w.step[i]=w.matrix[i*stride+n];if(!Double.isFinite(w.step[i]))return false;}return true;}
    private static boolean rotation(RigidTransform t){if(t==null)return false;double[] m=t.r;for(double v:m)if(!Double.isFinite(v))return false;for(int a=0;a<3;a++)for(int b=a;b<3;b++)if(Math.abs(m[a]*m[b]+m[3+a]*m[3+b]+m[6+a]*m[6+b]-(a==b?1:0))>1e-8)return false;return Math.abs(m[0]*(m[4]*m[8]-m[5]*m[7])-m[1]*(m[3]*m[8]-m[5]*m[6])+m[2]*(m[3]*m[7]-m[4]*m[6])-1)<1e-8;}
    private static Result refused(Failure failure,int iterations,double error){return new Result(null,failure,new double[0],iterations,error);}
    private CouplerArmConstraint(){}
}
