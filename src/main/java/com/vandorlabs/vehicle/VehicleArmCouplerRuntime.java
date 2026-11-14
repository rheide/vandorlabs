package com.vandorlabs.vehicle;

import com.vandorlabs.gear.GearTileEntity;
import com.vandorlabs.physics.*;
import java.util.*;
import net.minecraft.util.math.BlockPos;

/** Owning-thread constraint preparation. Publication stays in the existing carried motion batch. */
final class VehicleArmCouplerRuntime {
    static final class Motion {
        final RigidTransform[] bodies;
        final Map<BlockPos,Double> phases;
        final Set<BlockPos> passive;
        final int evaluations;
        Motion(RigidTransform[] bodies,Map<BlockPos,Double> phases,Set<BlockPos> passive,int evaluations){this.bodies=bodies;this.phases=phases;this.passive=passive;this.evaluations=evaluations;}
    }
    private final EntityGroundVehicle craft;
    private final VehicleCouplerTopology topology;
    private final boolean hasRigid;
    private CouplerGraphConstraint.Model coupledModel;
    private int[] rigidOrder,rigidParents,rigidEdges;
    private double[] headFrames;
    private final double[] rigidScratch=new double[9];
    private final List<BlockPos> roots=new ArrayList<>();
    private final List<BlockPos> rootLabels=Collections.unmodifiableList(roots);
    private Object[] configurations;
    private VehicleArmConstraintFrame frame;
    private VehicleGeneralPoseLayout layout;
    private CouplerGraphConstraint.Workspace workspace;
    private double[] points,visualPhases;
    private CouplerGraphConstraint.Workspace visualWorkspace;
    private CouplerGraphConstraint.Model visualModel;
    private List<BlockPos> carriers;
    private int[] angularOrder,angularParents,pairPeers;
    private boolean[] peerReversed;
    private AngularLaw[] angularLaws;
    private byte[][] neutralGrid;
    private VehicleArmConstraintFrame neutralModel;
    private long neutralMask;
    private boolean neutralResult;
    private static final class AngularLaw {final int parent,child;final double hx,hz;AngularLaw(int parent,int child,double hx,double hz){this.parent=parent;this.child=child;double length=Math.hypot(hx,hz);this.hx=length>.01?hx/length:0;this.hz=length>.01?hz/length:1;}}
    VehicleArmCouplerRuntime(EntityGroundVehicle craft,VehicleCouplerTopology topology){
        this.craft=craft;this.topology=topology;boolean rigid=false;for(VehicleCouplerTopology.Link link:topology.links)rigid|=!link.articulation&&link.headA!=null&&(link.headA.address.arm!=null||link.headB.address.arm!=null);hasRigid=rigid;
        for(VehicleCouplerTopology.Link link:topology.links)for(VehicleArmCouplers.Head head:new VehicleArmCouplers.Head[]{link.headA,link.headB})if(head!=null&&head.address.arm!=null&&!roots.contains(head.address.arm))roots.add(head.address.arm);
        Collections.sort(roots);refresh();prepareAngular();prepareRigid();
    }
    static boolean needed(VehicleCouplerTopology topology){
        if(!VehicleArmCouplers.runtime())return false;
        List<HingeGraph.Edge> edges=new ArrayList<>();for(VehicleCouplerTopology.Link link:topology.links)edges.add(new HingeGraph.Edge(link.first,link.second,link.articulation));
        HingeGraph ordinary=new HingeGraph(topology.sections.size(),topology.root,edges);
        for(VehicleCouplerTopology.Link link:topology.links)if(link.headA!=null&&(link.headA.address.arm!=null||link.headB.address.arm!=null)&&ordinary.body[link.first]==ordinary.body[link.second])return true;
        return false;
    }
    private GearTileEntity arm(int index){return (GearTileEntity)craft.view.tiles.get(roots.get(index));}
    private void refresh(){
        boolean changed=frame==null;
        if(!changed)for(int i=0;i<roots.size();i++){VehicleStructure.Cell cell=craft.structure.at(roots.get(i));if(cell==null||cell.gearConfigurationIdentity()!=configurations[i]){changed=true;break;}}
        if(!changed)return;
        frame=VehicleArmConstraintFrame.project(craft.structure,craft.view,topology,roots);
        layout=VehicleGeneralPoseLayout.project(craft.structure,craft.view,topology,frame);
        workspace=new CouplerGraphConstraint.Workspace(frame.graph(),roots.size());headFrames=new double[roots.size()*9];coupledModel=hasRigid?new CouplerGraphConstraint.DynamicModel(){public boolean evaluate(double[] phase,double[] out){return frame.model().evaluate(phase,out);}public boolean rotations(double[] phase,double[] desired,double[] out){return rigidRotations(phase,desired,out);}}:frame.model();points=new double[frame.graph().links()*6];
        neutralGrid=new byte[roots.size()][2];
        visualWorkspace=new CouplerGraphConstraint.Workspace(frame.graph(),roots.size());visualPhases=new double[roots.size()];visualModel=coupledModel;
        List<BlockPos> labels=new ArrayList<>();for(int i=0;i<layout.bodies();i++)labels.add(layout.carrier(i));carriers=Collections.unmodifiableList(labels);
        configurations=new Object[roots.size()];for(int i=0;i<roots.size();i++)configurations[i]=craft.structure.at(roots.get(i)).gearConfigurationIdentity();
    }
    boolean contains(BlockPos root){return roots.contains(root);}
    /** Stable construction-frame parent directions; native ownership, not a moving payload extent, defines the lever. */
    private void prepareAngular(){
        int count=frame.graph().bodies;List<List<Integer>> neighbours=new ArrayList<>();for(int i=0;i<count;i++)neighbours.add(new ArrayList<>());
        for(int i=0;i<frame.physicalLinks();i++){CouplerGraphConstraint.Link link=frame.graph().link(i);if(link.first==link.second)continue;if(!neighbours.get(link.first).contains(link.second))neighbours.get(link.first).add(link.second);if(!neighbours.get(link.second).contains(link.first))neighbours.get(link.second).add(link.first);}
        for(List<Integer> adjacent:neighbours)adjacent.sort((a,b)->layout.carrier(a).compareTo(layout.carrier(b)));
        angularOrder=new int[count];angularParents=new int[count];int[] depth=new int[count];Arrays.fill(angularParents,-1);int head=0,tail=1;angularOrder[0]=frame.graph().root;angularParents[frame.graph().root]=frame.graph().root;
        while(head<tail){int parent=angularOrder[head++];for(int child:neighbours.get(parent))if(angularParents[child]<0){angularParents[child]=parent;depth[child]=depth[parent]+1;angularOrder[tail++]=child;}}
        if(tail!=count)throw new IllegalArgumentException("Disconnected angular constraint bodies");
        double[] minX=new double[count],maxX=new double[count],minZ=new double[count],maxZ=new double[count];Arrays.fill(minX,Double.POSITIVE_INFINITY);Arrays.fill(minZ,Double.POSITIVE_INFINITY);Arrays.fill(maxX,Double.NEGATIVE_INFINITY);Arrays.fill(maxZ,Double.NEGATIVE_INFINITY);
        for(VehicleStructure.Cell cell:craft.structure.cells){int body=frame.sectionBody(topology.section.get(cell.pos));minX[body]=Math.min(minX[body],cell.pos.getX());maxX[body]=Math.max(maxX[body],cell.pos.getX()+1);minZ[body]=Math.min(minZ[body],cell.pos.getZ());maxZ[body]=Math.max(maxZ[body],cell.pos.getZ()+1);}
        angularLaws=new AngularLaw[frame.physicalLinks()];pairPeers=new int[angularLaws.length];peerReversed=new boolean[angularLaws.length];Arrays.fill(pairPeers,-1);
        for(int i=0;i<angularLaws.length;i++){
            CouplerGraphConstraint.Link edge=frame.graph().link(i);if(edge.first==edge.second)continue;boolean forward=depth[edge.first]<depth[edge.second]||depth[edge.first]==depth[edge.second]&&layout.carrier(edge.first).compareTo(layout.carrier(edge.second))<0;int parent=forward?edge.first:edge.second,child=forward?edge.second:edge.first;VehicleArmCouplers.Head anchor=forward?topology.links.get(i).headA:topology.links.get(i).headB;
            double px=anchor.legacyCell.getX()+.5+anchor.legacyFacing.getFrontOffsetX()*.5,pz=anchor.legacyCell.getZ()+.5+anchor.legacyFacing.getFrontOffsetZ()*.5;angularLaws[i]=new AngularLaw(parent,child,(minX[child]+maxX[child])/2-px,(minZ[child]+maxZ[child])/2-pz);
            for(int j=0;j<angularLaws.length;j++)if(j!=i){CouplerGraphConstraint.Link other=frame.graph().link(j);if(other.first==edge.first&&other.second==edge.second){pairPeers[i]=j;break;}if(other.second==edge.first&&other.first==edge.second){pairPeers[i]=j;peerReversed[i]=true;break;}}
        }
    }
    private void prepareRigid(){
        int count=frame.graph().bodies;rigidOrder=new int[count];rigidParents=new int[count];rigidEdges=new int[count];Arrays.fill(rigidParents,-1);Arrays.fill(rigidEdges,-1);int used=0;
        for(int anchor:angularOrder)if(rigidParents[anchor]<0){int head=used;rigidOrder[used++]=anchor;rigidParents[anchor]=anchor;while(head<used){int parent=rigidOrder[head++];for(int edge=0;edge<frame.physicalLinks();edge++){VehicleCouplerTopology.Link physical=topology.links.get(edge);if(physical.articulation)continue;CouplerGraphConstraint.Link link=frame.graph().link(edge);int child=link.first==parent?link.second:link.second==parent?link.first:-1;if(child>=0&&rigidParents[child]<0){rigidParents[child]=parent;rigidEdges[child]=edge;rigidOrder[used++]=child;}}}}
    }
    private double head(int physical,int side,int row,int column){int variable=frame.headArm(physical,side);return variable<0?(row==column?1:0):headFrames[variable*9+row*3+column];}
    private boolean rigidRotations(double[] phase,double[] desired,double[] out){
        if(!frame.model().rotations(phase,headFrames))return false;System.arraycopy(desired,0,out,0,desired.length);
        for(int child:rigidOrder){int parent=rigidParents[child];if(child==parent)continue;int edge=rigidEdges[child];boolean forward=frame.graph().link(edge).first==parent;int ps=forward?0:1,cs=forward?1:0;
            for(int row=0;row<3;row++)for(int col=0;col<3;col++){double value=0;for(int k=0;k<3;k++)value+=out[parent*9+row*3+k]*head(edge,ps,k,col);rigidScratch[row*3+col]=value;}
            for(int row=0;row<3;row++)for(int col=0;col<3;col++){double value=0;for(int k=0;k<3;k++)value+=rigidScratch[row*3+k]*head(edge,cs,col,k);out[child*9+row*3+col]=value;}
        }return true;
    }
    private double[] jointFrame(int physical,int parent,int child,RigidTransform[] bodies){
        boolean forward=frame.graph().link(physical).first==parent;int ps=forward?0:1,cs=forward?1:0;double[] first=new double[9],second=new double[9];
        for(int row=0;row<3;row++)for(int col=0;col<3;col++)for(int k=0;k<3;k++){first[row*3+col]+=bodies[parent].r[row*3+k]*head(physical,ps,k,col);second[row*3+col]+=bodies[child].r[row*3+k]*head(physical,cs,k,col);}return relative(first,second);
    }
    int body(int section){return frame.sectionBody(section);}
    int bodies(){return frame.graph().bodies;}
    long token(){refresh();return layout.token();}
    List<VehicleBodyPoseFrame.Body> saved(RigidTransform[] bodies){
        refresh();List<VehicleBodyPoseFrame.Body> result=new ArrayList<>();for(int n=0;n<layout.bodies();n++){int body=layout.canonicalBody(n);if(body!=layout.rootBody())result.add(new VehicleBodyPoseFrame.Body(layout.carrier(body),bodies[body]));}return result;
    }
    List<BlockPos> roots(){return rootLabels;}
    List<BlockPos> carriers(){return carriers;}
    private static boolean sameBasis(int[] basis,com.vandorlabs.gear.ArmGridMapping mapping){net.minecraft.util.EnumFacing[] axes={net.minecraft.util.EnumFacing.EAST,net.minecraft.util.EnumFacing.UP,net.minecraft.util.EnumFacing.SOUTH};for(int col=0;col<3;col++){net.minecraft.util.EnumFacing mapped=mapping.facing(axes[col]);if(basis[col]!=mapped.getFrontOffsetX()||basis[3+col]!=mapped.getFrontOffsetY()||basis[6+col]!=mapped.getFrontOffsetZ())return false;}return true;}
    /** Verified saved native grid references; this conveys no ownership or motion permission. */
    double[] visualReferences(){
        refresh();double[] result=new double[roots.size()];Arrays.fill(result,Double.NaN);for(int n=0;n<result.length;n++){GearTileEntity owner=arm(n);com.vandorlabs.gear.ArmPayload payload=owner.payload();int[] basis=payload.placedTileBasis();for(double phase:new double[]{1,0}){com.vandorlabs.gear.ArmGridMapping mapping=payload.gridMapping(owner,phase);if(mapping==null||!sameBasis(basis,mapping))continue;boolean same=true;for(int i=0;i<payload.blocks.cells.size();i++)if(!mapping.cell(payload.blocks.cells.get(i).pos).equals(payload.placedCellPosition(owner,i))){same=false;break;}if(same){result[n]=phase;break;}}}return result;
    }
    RigidTransform[] visual(long token,double[] phases,RigidTransform[] orientations){VehicleArmVisualFrame result=visualFrame(token,phases,phases,phases,orientations);return result==null?null:result.bodies;}
    VehicleArmVisualFrame visualFrame(long token,double[] wishes,double[] minimum,double[] maximum,RigidTransform[] orientations){
        refresh();if(token!=layout.token()||wishes.length!=roots.size()||minimum.length!=roots.size()||maximum.length!=roots.size())return null;
        CouplerArmConstraint.Stroke[] strokes=new CouplerArmConstraint.Stroke[roots.size()];for(int i=0;i<roots.size();i++){double lo=minimum[i],hi=maximum[i],wish=wishes[i];if(!Double.isFinite(lo)||!Double.isFinite(hi)||!Double.isFinite(wish)||lo<0||hi>1||lo>hi)return null;wish=Math.max(lo,Math.min(hi,wish));if(arm(i).payload().materialized()){double phase=arm(i).progress();strokes[i]=new CouplerArmConstraint.Stroke(phase,phase,phase);continue;}strokes[i]=arm(i).couplerPassive()?new CouplerArmConstraint.Stroke(lo,hi,wish):new CouplerArmConstraint.Stroke(wish,wish,wish);}
        CouplerGraphConstraint.Result result=CouplerGraphConstraint.solve(visualModel,strokes,orientations,visualWorkspace);
        if(!result.accepted())return null;for(int i=0;i<roots.size();i++)visualPhases[i]=result.progress(i);
        if(!frame.model().evaluate(visualPhases,points)||!frame.model().rotations(visualPhases,headFrames)||!angular(result))return null;
        RigidTransform[] bodies=new RigidTransform[result.bodies()];for(int i=0;i<bodies.length;i++)bodies[i]=result.transform(i);return new VehicleArmVisualFrame(bodies,visualPhases.clone());
    }
    /** Only a fully proved neutral grid pose can retire the optional proper-body envelope. */
    boolean neutral(RigidTransform[] bodies){
        refresh();if(bodies.length!=frame.graph().bodies)return false;for(RigidTransform body:bodies)if(distance(body,RigidTransform.IDENTITY)>1e-9)return false;
        long mask=0;for(int i=0;i<roots.size();i++){GearTileEntity owner=arm(i);double phase=owner.progress();if(phase!=0&&phase!=1)return false;int end=(int)phase;if(neutralGrid[i][end]==0)neutralGrid[i][end]=(byte)(owner.payload().gridMapping(owner,phase)==null?-1:1);if(neutralGrid[i][end]<0)return false;if(end==1)mask|=1L<<i;}
        if(neutralModel==frame&&neutralMask==mask)return neutralResult;
        RigidTransform[] identity=new RigidTransform[bodies.length];Arrays.fill(identity,RigidTransform.IDENTITY);Motion proof=solve(Collections.<BlockPos,Double>emptyMap(),identity,false);boolean accepted=proof!=null;if(accepted)for(RigidTransform body:proof.bodies)accepted&=distance(body,RigidTransform.IDENTITY)<=1e-9;
        neutralModel=frame;neutralMask=mask;neutralResult=accepted;return accepted;
    }
    VehicleArmMotionFrame motion(){
        refresh();List<VehicleArmMotionFrame.Arm> values=new ArrayList<>();for(int i=0;i<roots.size();i++){GearTileEntity arm=arm(i);values.add(new VehicleArmMotionFrame.Arm(roots.get(i),arm.motionRevision(),arm.progress(),arm.target(),arm.couplerPassive()));}return new VehicleArmMotionFrame(layout.token(),values);
    }
    boolean receiveMotion(VehicleArmMotionFrame packet,long tick){
        refresh();if(packet==null||packet.layout!=layout.token()||packet.arms.size()!=roots.size())return false;
        for(VehicleArmMotionFrame.Arm row:packet.arms){int index=roots.indexOf(row.root);if(index<0||!arm(index).canAcceptCouplerFrame(row.revision,tick,row.progress))return false;}
        com.vandorlabs.VandorLabs.proxy.vehicleArmVisualSupport(craft,rootLabels);
        for(VehicleArmMotionFrame.Arm row:packet.arms)if(!arm(roots.indexOf(row.root)).acceptCouplerFrame(row.revision,tick,row.target,row.passive,row.progress))return false;
        com.vandorlabs.VandorLabs.proxy.vehicleArmVisualSupport(craft,rootLabels);
        return true;
    }
    RigidTransform[] received(VehicleBodyPoseFrame packet){return received(packet,VehicleArmMotionFrame.NONE);}
    RigidTransform[] received(VehicleBodyPoseFrame packet,VehicleArmMotionFrame motion){
        refresh();if(!packet.present()||!packet.reset()&&(packet.layout!=layout.token()||packet.bodies.size()!=layout.bodies()-1))return null;
        RigidTransform[] result=new RigidTransform[layout.bodies()];if(packet.reset())Arrays.fill(result,RigidTransform.IDENTITY);else result[layout.rootBody()]=RigidTransform.IDENTITY;
        for(VehicleBodyPoseFrame.Body body:packet.bodies){int id=layout.bodyForCarrier(body.carrier);if(id<0||id==layout.rootBody()||result[id]!=null)return null;result[id]=body.transform();}
        Map<BlockPos,Double> phases=new LinkedHashMap<>();if(!motion.arms.isEmpty()){if(motion.layout!=layout.token()||motion.arms.size()!=roots.size())return null;for(VehicleArmMotionFrame.Arm row:motion.arms){if(!roots.contains(row.root))return null;phases.put(row.root,row.progress);}}
        Motion checked=solve(phases,result,false);if(checked==null)return null;
        for(int i=0;i<result.length;i++)if(distance(result[i],checked.bodies[i])>1e-5)return null;
        return result;
    }
    Motion solve(Map<BlockPos,? extends Number> requested,RigidTransform[] orientation,boolean passive){
        refresh();if(orientation==null||orientation.length!=frame.graph().bodies||orientation[frame.graph().root]==null||orientation[frame.graph().root].angle()>1e-7)return null;
        CouplerArmConstraint.Stroke[] strokes=new CouplerArmConstraint.Stroke[roots.size()];double[] progress=new double[roots.size()];
        for(int i=0;i<roots.size();i++){
            GearTileEntity arm=arm(i);double before=arm.progress();Number desired=requested.get(roots.get(i));double next=desired==null?before:desired.doubleValue();progress[i]=next;
            double step=1D/GearTileEntity.ANIMATION_TICKS;
            boolean free=passive&&desired==null&&!arm.parkingHeld();
            strokes[i]=free?new CouplerArmConstraint.Stroke(Math.max(0,before-step),Math.min(1,before+step),before):new CouplerArmConstraint.Stroke(next,next,next);
        }
        CouplerGraphConstraint.Result result=CouplerGraphConstraint.solve(coupledModel,strokes,orientation,workspace);if(!result.accepted())return null;
        // Smoothstep inverse error grows near an endpoint. Try ONE exact passive endpoint candidate;
        // every accepted candidate still needs the complete contact/angle solve and ordinary collision check.
        CouplerArmConstraint.Stroke[] endpoints=strokes.clone();boolean snap=false,requestedEndpoint=false;for(Number value:requested.values())requestedEndpoint|=value.doubleValue()==0||value.doubleValue()==1;
        for(int i=0;i<endpoints.length;i++){double phase=result.progress(i),end=phase<1e-3?0:phase>1-1e-3?1:phase;if(!requested.containsKey(roots.get(i))&&end!=phase&&end>=strokes[i].min-(requestedEndpoint?1e-3:1e-12)&&end<=strokes[i].max+(requestedEndpoint?1e-3:1e-12)){endpoints[i]=new CouplerArmConstraint.Stroke(end,end,end);snap=true;}}
        if(snap){CouplerGraphConstraint.Result exact=CouplerGraphConstraint.solve(coupledModel,endpoints,orientation,workspace);if(exact.accepted())result=exact;}
        for(int i=0;i<progress.length;i++)progress[i]=result.progress(i);
        if(!frame.model().evaluate(progress,points)||!frame.model().rotations(progress,headFrames)||!angular(result))return null;
        RigidTransform[] bodies=new RigidTransform[result.bodies()];for(int i=0;i<bodies.length;i++)bodies[i]=result.transform(i);
        Map<BlockPos,Double> phases=new LinkedHashMap<>();Set<BlockPos> followers=new LinkedHashSet<>();
        for(int i=0;i<roots.size();i++)if(Math.abs(result.progress(i)-arm(i).progress())>1e-12){phases.put(roots.get(i),result.progress(i));if(!requested.containsKey(roots.get(i)))followers.add(roots.get(i));}
        return new Motion(bodies,phases,followers,result.evaluations);
    }
    private boolean angular(CouplerGraphConstraint.Result result){
        RigidTransform[] bodies=new RigidTransform[result.bodies()];for(int i=0;i<bodies.length;i++)bodies[i]=result.transform(i);
        for(int i=0;i<frame.physicalLinks();i++){
            CouplerGraphConstraint.Link edge=frame.graph().link(i);if(edge.first==edge.second)continue;
            double[] r=jointFrame(i,edge.first,edge.second,bodies);
            if(!topology.links.get(i).articulation){for(int n=0;n<9;n++)if(Math.abs(r[n]-(n%4==0?1:0))>1e-7)return false;continue;}
            int other=pairPeers[i];boolean reverse=peerReversed[i];
            if(other>=0){int at=i*6,bt=other*6+(reverse?3:0),ct=other*6+(reverse?0:3);double[] a=headVector(i,0,points[bt]-points[at],points[bt+1]-points[at+1],points[bt+2]-points[at+2]),b=headVector(i,1,points[ct]-points[at+3],points[ct+1]-points[at+4],points[ct+2]-points[at+5]);if(CouplerAngularAdmission.line(r,a[0],a[1],a[2],b[0],b[1],b[2])!=CouplerAngularAdmission.Result.ADMITTED)return false;}
            else {AngularLaw law=angularLaws[i];if(CouplerAngularAdmission.ordinary(jointFrame(i,law.parent,law.child,bodies),law.hx,law.hz)!=CouplerAngularAdmission.Result.ADMITTED)return false;}
        }return true;
    }
    private double[] headVector(int edge,int side,double x,double y,double z){double[] result=new double[3];for(int col=0;col<3;col++)result[col]=head(edge,side,0,col)*x+head(edge,side,1,col)*y+head(edge,side,2,col)*z;return result;}
    /** Convert a calibrated head-relative frame into the child's construction-body frame. */
    private RigidTransform bodyFrame(int edge,int parent,RigidTransform parentFrame,double[] joint){
        boolean forward=frame.graph().link(edge).first==parent;int ps=forward?0:1,cs=forward?1:0;double[] first=new double[9],second=new double[9],result=new double[9];
        for(int row=0;row<3;row++)for(int col=0;col<3;col++)for(int k=0;k<3;k++)first[row*3+col]+=parentFrame.r[row*3+k]*head(edge,ps,k,col);
        for(int row=0;row<3;row++)for(int col=0;col<3;col++)for(int k=0;k<3;k++)second[row*3+col]+=first[row*3+k]*joint[k*3+col];
        for(int row=0;row<3;row++)for(int col=0;col<3;col++)for(int k=0;k<3;k++)result[row*3+col]+=second[row*3+k]*head(edge,cs,col,k);return new RigidTransform(result,0,0,0);
    }
    boolean canMakeRigid(int physical,RigidTransform[] bodies){refresh();if(physical<0||physical>=frame.physicalLinks()||bodies.length!=frame.graph().bodies)return false;double[] phases=new double[roots.size()];for(int i=0;i<phases.length;i++)phases[i]=arm(i).progress();if(!frame.model().rotations(phases,headFrames))return false;CouplerGraphConstraint.Link edge=frame.graph().link(physical);double[] relative=jointFrame(physical,edge.first,edge.second,bodies);for(int i=0;i<9;i++)if(Math.abs(relative[i]-(i%4==0?1:0))>1e-7)return false;return true;}
    /** Project an inertial/gravity prior onto the calibrated head manifold. */
    RigidTransform[] align(RigidTransform[] preferred){
        refresh();double[] phases=new double[roots.size()];for(int i=0;i<phases.length;i++)phases[i]=arm(i).progress();if(!frame.model().evaluate(phases,points)||!frame.model().rotations(phases,headFrames))return null;
        RigidTransform[] result=preferred.clone();
        for(int pass=1;pass<angularOrder.length;pass++){
            int child=angularOrder[pass],parent=angularParents[child],first=-1,rigid=-1;List<CouplerLineConstraint.Anchor> anchors=new ArrayList<>();
            for(int edge=0;edge<frame.physicalLinks();edge++){CouplerGraphConstraint.Link link=frame.graph().link(edge);boolean forward=link.first==parent&&link.second==child;if(!forward&&!(link.second==parent&&link.first==child))continue;if(first<0)first=edge;if(!topology.links.get(edge).articulation)rigid=edge;int at=edge*6;anchors.add(forward?new CouplerLineConstraint.Anchor(points[at],points[at+1],points[at+2],points[at+3],points[at+4],points[at+5]):new CouplerLineConstraint.Anchor(points[at+3],points[at+4],points[at+5],points[at],points[at+1],points[at+2]));}
            if(rigid>=0)result[child]=bodyFrame(rigid,parent,result[parent],RigidTransform.IDENTITY.r);
            else if(anchors.size()>1){RigidTransform prior=new RigidTransform(relative(result[parent].r,result[child].r),0,0,0);CouplerLineConstraint.Result fit=CouplerLineConstraint.solve(anchors,prior,0,180);if(!fit.accepted())return null;result[child]=new RigidTransform(result[parent].then(fit.transform).r,0,0,0);}
            else {AngularLaw law=angularLaws[first];double[] r=jointFrame(first,law.parent,law.child,result);double pitch=Math.toDegrees(Math.atan2(r[3]*law.hx+r[5]*law.hz,r[4]));RigidTransform tilt=RigidTransform.rotation(-law.hz,0,law.hx,pitch);double r0=0,r6=0;for(int k=0;k<3;k++){r0+=r[k]*tilt.r[k];r6+=r[6+k]*tilt.r[k];}double[] limited=RigidTransform.limitedAngles(Math.toDegrees(Math.atan2(r6,r0)),pitch,VehicleArticulation.MAX_ANGLE);RigidTransform constrained=RigidTransform.joint(0,0,0,law.hx,law.hz,limited[0],limited[1]);result[child]=bodyFrame(first,parent,result[parent],constrained.r);}
        }return result;
    }
    static double[] relative(double[] a,double[] b){double[] r=new double[9];for(int row=0;row<3;row++)for(int col=0;col<3;col++)for(int k=0;k<3;k++)r[row*3+col]+=a[k*3+row]*b[k*3+col];return r;}
    static double distance(RigidTransform a,RigidTransform b){double difference=Math.abs(a.x-b.x)+Math.abs(a.y-b.y)+Math.abs(a.z-b.z);for(int i=0;i<9;i++)difference+=Math.abs(a.r[i]-b.r[i]);return difference;}
    static RigidTransform interpolate(RigidTransform a,RigidTransform b,double t){return RigidTransform.interpolate(a,b,t);}
}
