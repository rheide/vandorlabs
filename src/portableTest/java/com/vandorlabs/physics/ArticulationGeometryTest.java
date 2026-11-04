package com.vandorlabs.physics;
import java.util.*;
public final class ArticulationGeometryTest {
    private static int checks;
    private static void require(boolean ok,String why){checks++;if(!ok)throw new AssertionError("articulation: "+why);}
    private static void near(double a,double b,String why){require(Math.abs(a-b)<1e-7,why);}
    private static void parity(){
        java.util.Random random=new java.util.Random(282);for(int i=0;i<100000;i++){
            RigidTransform a=RigidTransform.translation(random.nextDouble()*4,random.nextDouble()*4,random.nextDouble()*4).then(RigidTransform.rotation(random.nextDouble(),random.nextDouble(),random.nextDouble(),random.nextDouble()*180)),b=RigidTransform.translation(random.nextDouble()*4,random.nextDouble()*4,random.nextDouble()*4).then(RigidTransform.rotation(random.nextDouble(),random.nextDouble(),random.nextDouble(),random.nextDouble()*180));
            OrientedBounds x=new OrientedBounds(-1,-.5,-2,1,.5,2,a),y=new OrientedBounds(-.75,-.25,-1,.75,.25,1,b);
            require(x.intersects(y)==new ReferenceOrientedBounds(-1,-.5,-2,1,.5,2,a).intersects(new ReferenceOrientedBounds(-.75,-.25,-1,.75,.25,1,b)),"allocation-free SAT matches previous implementation");
        }
    }
    public static void main(String[] args){parity();
        HingeGraph train=new HingeGraph(4,0,Arrays.asList(new HingeGraph.Edge(0,1,true),new HingeGraph.Edge(1,2,true),new HingeGraph.Edge(2,3,true)));require(train.bodies==4&&train.joints.size()==3,"three independent trailer joints");
        HingeGraph parallel=new HingeGraph(3,0,Arrays.asList(new HingeGraph.Edge(0,1,true),new HingeGraph.Edge(0,1,true),new HingeGraph.Edge(1,2,true)));require(parallel.bodies==2&&parallel.joints.size()==1&&parallel.body[0]==parallel.body[1],"parallel pairs lock only their own connection");
        HingeGraph cycle=new HingeGraph(4,0,Arrays.asList(new HingeGraph.Edge(0,1,true),new HingeGraph.Edge(1,2,true),new HingeGraph.Edge(2,0,true),new HingeGraph.Edge(2,3,true)));require(cycle.bodies==2&&cycle.joints.size()==1,"closed loop rigid, free tail still articulated");
        List<HingeGraph.Edge> longTrain=new ArrayList<>();for(int i=1;i<16000;i++)longTrain.add(new HingeGraph.Edge(i-1,i,true));HingeGraph bounded=new HingeGraph(16000,15999,longTrain);require(bounded.joints.size()==15999,"long train has no recursive stack limit");
        Random random=new Random(2864);
        for(int i=0;i<10000;i++){
            double yaw=random.nextDouble()*360-180,pitch=random.nextDouble()*180-90;double[] limited=RigidTransform.limitedAngles(yaw,pitch,75);RigidTransform rotation=RigidTransform.joint(2,.75,3,.6,.8,limited[0],limited[1]);
            near(rotation.point(2,.75,3)[0],2,"hinge stays fixed X");near(rotation.point(2,.75,3)[1],.75,"hinge stays fixed Y");near(rotation.point(2,.75,3)[2],3,"hinge stays fixed Z");require(rotation.angle()<=75.000001,"combined yaw/pitch cap is 75 degrees");
            double x=random.nextDouble()*10,y=random.nextDouble()*10,z=random.nextDouble()*10;double[] p=rotation.point(x,y,z),back=rotation.inverse(p[0],p[1],p[2]);near(back[0],x,"inverse X");near(back[1],y,"inverse Y");near(back[2],z,"inverse Z");
            RigidTransform parent=RigidTransform.translation(12,5,-8).then(RigidTransform.rotation(0,1,0,-43)),child=parent.then(rotation);double[] a=parent.point(2,.75,3),b=child.point(2,.75,3);for(int k=0;k<3;k++)near(a[k],b[k],"composed multi-trailer anchor equality");
            OrientedBounds box=new OrientedBounds(-1,-.5,-2,1,.5,2,rotation),other=new OrientedBounds(-.25,-.25,-.25,.25,.25,.25,RigidTransform.translation(x-5,y-5,z-5));require(box.intersects(other)==other.intersects(box),"full 15-axis SAT symmetry");
        }
        RigidTransform turn=RigidTransform.rotation(0,1,0,-45);OrientedBounds a=new OrientedBounds(-2,-.1,-.1,2,.1,.1,turn),b=new OrientedBounds(-2,-.1,-.1,2,.1,.1,RigidTransform.translation(-.4,0,.4).then(turn));require(!a.intersects(b),"overlapping broad boxes do not collide across empty diagonal space");
        OrientedBounds touching=new OrientedBounds(0,0,0,1,1,1,RigidTransform.IDENTITY),touching2=new OrientedBounds(1,0,0,2,1,1,RigidTransform.IDENTITY);require(!touching.intersects(touching2),"face contact is not penetration");require(touching.intersects(new OrientedBounds(.99,0,0,2,1,1,RigidTransform.IDENTITY)),"real penetration detected");
        System.out.println("Articulation geometry PASS checks="+checks+" bridge constraints parallel/loops independent chain combined75deg pivot/inverse full3D SAT");
    }
}
