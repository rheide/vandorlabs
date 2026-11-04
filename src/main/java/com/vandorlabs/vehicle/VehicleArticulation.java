package com.vandorlabs.vehicle;

import com.vandorlabs.coupler.*;
import com.vandorlabs.physics.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** Independent rigid-body poses inside one durable vehicle owner. No blocks or inventories are transferred here. */
public final class VehicleArticulation {
    public static final double MAX_ANGLE=75;
    public static final int MAX_JOINTS=VehicleStructure.MAX_BLOCKS/2;
    public static final int MAX_NEW_SEGMENTS=8;
    public static void requireAssemblyLimit(VehicleStructure structure){
        VehicleCouplerTopology topology=VehicleCouplerTopology.of(structure);if(topology==null)return;
        List<HingeGraph.Edge> edges=new ArrayList<>();for(VehicleCouplerTopology.Link link:topology.links)edges.add(new HingeGraph.Edge(link.first,link.second,link.articulation));
        if(new HingeGraph(topology.sections.size(),topology.root,edges).bodies>MAX_NEW_SEGMENTS)throw new IllegalArgumentException("Vehicles support at most 8 independently articulated segments; use rigid couplers for additional sections");
    }
    private final EntityGroundVehicle craft;
    private VehicleStructure observed;
    private long signature=Long.MIN_VALUE;
    private VehicleCouplerTopology topology;
    private HingeGraph graph;
    private Map<BlockPos,Integer> bodyAt=Collections.emptyMap();
    private List<Joint> joints=Collections.emptyList();
    private List<List<Shape>> shapes=Collections.emptyList();
    private AxisAlignedBB[] bodyBounds=new AxisAlignedBB[0];
    private VehicleCollision observedCollision;
    private double[] yaw=new double[0],pitch=new double[0],previousYaw=new double[0],previousPitch=new double[0],targetYaw=new double[0],targetPitch=new double[0];
    private int remoteTicks;
    private RigidTransform[] matrices={RigidTransform.IDENTITY},visualMatrices;
    private float visualPartial=Float.NaN;
    private long topologyId;
    private NBTTagCompound retained;
    private short[] pendingNetwork;
    private long pendingTopology;
    private Frame base;
    private RigidTransform[] collisionMatrices;private VehicleCollision collisionSource;private double collisionX,collisionY,collisionZ;private float collisionYaw;private List<AxisAlignedBB> cachedCollision=Collections.emptyList();
    private RigidTransform baseRoot;
    private final Map<BlockPos,List<AxisAlignedBB>> terrain=new HashMap<>();
    private double turnX,turnZ;
    private float turnYaw;
    private double baseX,baseY,baseZ;private float baseYaw;
    private VehicleCollision radiusSource;private long radiusTopology;private double radius;
    private Set<BlockPos> rootCells=Collections.emptySet();private double pivotX,pivotZ;
    public long topologyBuilds,shapeBuilds,poseChecks,terrainQueries;
    private static final class Shape {
        final AxisAlignedBB box;final BlockPos coupler;
        Shape(AxisAlignedBB box,BlockPos coupler){this.box=box;this.coupler=coupler;}
    }
    private static final class Joint {
        final BlockPos a,b;final int parent,child;final double px,py,pz,hx,hz;final double[] axle;
        Joint(BlockPos a,BlockPos b,int parent,int child,double[] pivot,double[] axle){this.a=a;this.b=b;this.parent=parent;this.child=child;px=pivot[0];py=pivot[1];pz=pivot[2];this.axle=axle;double x=axle[0]-px,z=axle[2]-pz,length=Math.hypot(x,z);hx=length>.01?x/length:0;hz=length>.01?z/length:1;}
    }
    private static final class Frame {
        final double[] yaw,pitch;final RigidTransform[] bodies;
        Frame(double[] yaw,double[] pitch,RigidTransform[] bodies){this.yaw=yaw;this.pitch=pitch;this.bodies=bodies;}
    }
    public VehicleArticulation(EntityGroundVehicle craft){this.craft=craft;}
    private static long mix(long h,long value){return (h^value)*1099511628211L;}
    public void bind(){
        if(craft.structure==observed)return;
        VehicleStructure structure=craft.structure;observed=structure;
        if(structure==null)return;
        boolean enabled=false;for(VehicleStructure.Cell cell:structure.couplers){TileEntity raw=craft.view.getTileEntity(cell.pos);if(raw instanceof TileCoupler&&((TileCoupler)raw).articulation()){enabled=true;break;}}
        if(!enabled){signature=Long.MIN_VALUE;topologyId=0;topology=structure.couplerTopology;graph=null;shapes=Collections.emptyList();bodyBounds=new AxisAlignedBB[0];observedCollision=null;collisionMatrices=null;cachedCollision=Collections.emptyList();joints=Collections.emptyList();bodyAt=Collections.emptyMap();yaw=pitch=previousYaw=previousPitch=targetYaw=targetPitch=new double[0];matrices=new RigidTransform[]{RigidTransform.IDENTITY};visualMatrices=null;return;}
        long key=1469598103934665603L;key=mix(key,structure.seat.toLong());key=mix(key,structure.facing.getIndex());
        if(!structure.couplers.isEmpty())for(VehicleStructure.Cell cell:structure.cells){key=mix(key,cell.pos.toLong());key=mix(key,Block.getStateId(cell.state));if(cell.state.getBlock() instanceof BlockCoupler){TileEntity raw=craft.view.getTileEntity(cell.pos);key=mix(key,raw instanceof TileCoupler&&((TileCoupler)raw).articulation()?1:0);}}
        if(key==signature){structure.couplerTopology=topology;return;}
        signature=key;structure.componentView(craft.view);structure.couplerTopology=null;
        Map<String,double[]> old=new HashMap<>();for(int i=0;i<joints.size();i++)old.put(id(joints.get(i)),new double[]{yaw[i],pitch[i]});
        if(structure.couplers.isEmpty()){topology=null;graph=null;joints=Collections.emptyList();bodyAt=Collections.emptyMap();yaw=pitch=previousYaw=previousPitch=targetYaw=targetPitch=new double[0];matrices=new RigidTransform[]{RigidTransform.IDENTITY};visualMatrices=null;return;}
        topology=VehicleCouplerTopology.of(structure);List<HingeGraph.Edge> edges=new ArrayList<>();for(VehicleCouplerTopology.Link link:topology.links)edges.add(new HingeGraph.Edge(link.first,link.second,link.articulation));
        graph=new HingeGraph(topology.sections.size(),topology.root,edges);Map<BlockPos,Integer> ids=new HashMap<>();for(Map.Entry<BlockPos,Integer> entry:topology.section.entrySet())ids.put(entry.getKey(),graph.body[entry.getValue()]);bodyAt=Collections.unmodifiableMap(ids);
        Set<BlockPos> rootSet=new HashSet<>();AxisAlignedBB rootBounds=null;for(Map.Entry<BlockPos,Integer> e:ids.entrySet())if(e.getValue()==0){rootSet.add(e.getKey());rootBounds=rootBounds==null?new AxisAlignedBB(e.getKey()):rootBounds.union(new AxisAlignedBB(e.getKey()));}rootCells=Collections.unmodifiableSet(rootSet);pivotX=(rootBounds.minX+rootBounds.maxX)/2;pivotZ=(rootBounds.minZ+rootBounds.maxZ)/2;
        topologyBuilds++;observedCollision=null;captureShapes();List<Joint> created=new ArrayList<>();long token=1469598103934665603L;
        for(HingeGraph.Joint edge:graph.joints){VehicleCouplerTopology.Link link=topology.links.get(edge.edge);BlockPos a=graph.body[link.first]==edge.parent?link.a:link.b,b=a.equals(link.a)?link.b:link.a;VehicleStructure.Cell cell=structure.at(a);EnumFacing facing=cell.state.getValue(BlockCoupler.FACING);
            double[] pivot={a.getX()+.5+facing.getFrontOffsetX()*.5,a.getY()+.5+facing.getFrontOffsetY()*.5,a.getZ()+.5+facing.getFrontOffsetZ()*.5};if(facing.getAxis()!=EnumFacing.Axis.Y)pivot[1]=a.getY()+BlockCoupler.couplingHeight(cell.state);
            AxisAlignedBB bounds=bodyBounds[edge.child];double[] axle={(bounds.minX+bounds.maxX)/2,bounds.minY,(bounds.minZ+bounds.maxZ)/2};created.add(new Joint(a,b,edge.parent,edge.child,pivot,axle));token=mix(mix(token,a.toLong()),b.toLong());
        }
        joints=Collections.unmodifiableList(created);topologyId=token;yaw=new double[joints.size()];pitch=new double[joints.size()];for(int i=0;i<joints.size();i++){double[] angle=old.get(id(joints.get(i)));if(angle!=null){yaw[i]=angle[0];pitch[i]=angle[1];}}
        matrices=build(yaw,pitch);previousYaw=yaw.clone();previousPitch=pitch.clone();targetYaw=yaw.clone();targetPitch=pitch.clone();visualMatrices=null;
        if(pendingNetwork!=null&&pendingTopology==topologyId&&pendingNetwork.length==joints.size()*2){applyNetwork(pendingNetwork);pendingNetwork=null;}
    }
    private static String id(Joint j){return Math.min(j.a.toLong(),j.b.toLong())+":"+Math.max(j.a.toLong(),j.b.toLong());}
    public boolean active(){bind();return craft.structure!=null&&!joints.isEmpty();}
    public int bodyAt(BlockPos pos){bind();if(joints.isEmpty())return 0;Integer id=bodyAt.get(pos);if(id!=null)return id;for(EnumFacing side:EnumFacing.VALUES){id=bodyAt.get(pos.offset(side));if(id!=null)return id;}return 0;}
    public Set<BlockPos> rootCells(){bind();return rootCells;}
    private void captureShapes(){
        if(graph==null||observedCollision==craft.collision)return;
        collectShapes(craft.view,craft.structure);observedCollision=craft.collision;
    }
    private void collectShapes(VehicleWorld view,VehicleStructure structure){
        shapeBuilds++;List<List<AxisAlignedBB>> merge=new ArrayList<>();List<List<Shape>> result=new ArrayList<>();for(int i=0;i<graph.bodies;i++){merge.add(new ArrayList<>());result.add(new ArrayList<>());}Set<BlockPos> canopyOwners=new HashSet<>();AxisAlignedBB query=structure.bounds.grow(16);
        for(VehicleStructure.Cell cell:structure.cells){List<AxisAlignedBB> boxes=new ArrayList<>();
            if(cell.state.getBlock() instanceof com.vandorlabs.blocks.BlockCanopy){TileEntity raw=view.getTileEntity(cell.pos);if(raw instanceof com.vandorlabs.tiles.TileEntityCanopy){com.vandorlabs.tiles.TileEntityCanopy owner=((com.vandorlabs.tiles.TileEntityCanopy)raw).owner();if(owner.draws()&&canopyOwners.add(owner.getPos()))boxes.addAll(owner.boxes());}}
            else if(cell.state.getBlock() instanceof com.vandorlabs.blocks.BlockTelescopicLandingGear)boxes.addAll(((com.vandorlabs.blocks.BlockTelescopicLandingGear)cell.state.getBlock()).vehicleBoxes(view,cell.pos));
            else cell.state.addCollisionBoxToList(view,cell.pos,query,boxes,null,false);
            int body=bodyAt.getOrDefault(cell.pos,0);for(AxisAlignedBB box:boxes)if(box.maxX-box.minX>1e-8&&box.maxY-box.minY>1e-8&&box.maxZ-box.minZ>1e-8){if(cell.state.getBlock() instanceof BlockCoupler)result.get(body).add(new Shape(box,cell.pos));else merge.get(body).add(box);}
        }
        bodyBounds=new AxisAlignedBB[graph.bodies];int total=0;
        for(int body=0;body<graph.bodies;body++){
            if(!merge.get(body).isEmpty())for(AxisAlignedBB box:VehicleCollision.prepare(new VehicleCollision.Raw(merge.get(body),structure.bounds.maxX/2,structure.bounds.maxZ/2,0,null,true)).boxes)result.get(body).add(new Shape(box,null));
            AxisAlignedBB bounds=null;for(Shape shape:result.get(body))if(shape.coupler==null)bounds=bounds==null?shape.box:bounds.union(shape.box);
            if(bounds==null)for(Map.Entry<BlockPos,Integer> entry:bodyAt.entrySet())if(entry.getValue()==body)bounds=bounds==null?new AxisAlignedBB(entry.getKey()):bounds.union(new AxisAlignedBB(entry.getKey()));bodyBounds[body]=bounds;total+=result.get(body).size();
        }
        if(total>8192)throw new IllegalArgumentException("Articulated collision exceeds 8192 boxes");shapes=result;
    }
    private RigidTransform[] build(double[] ys,double[] ps){RigidTransform[] result=new RigidTransform[graph==null?1:graph.bodies];Arrays.fill(result,RigidTransform.IDENTITY);for(int i=0;i<joints.size();i++){Joint j=joints.get(i);result[j.child]=result[j.parent].then(RigidTransform.joint(j.px,j.py,j.pz,j.hx,j.hz,ys[i],ps[i]));}return result;}
    public RigidTransform body(int id,float partial){bind();if(!active()||id==0)return RigidTransform.IDENTITY;if(!craft.world.isRemote||craft.transferInProgress||partial>=1)return matrices[id];if(visualMatrices==null||visualPartial!=partial){double[] y=new double[yaw.length],p=new double[yaw.length];for(int i=0;i<y.length;i++){y[i]=previousYaw[i]+(yaw[i]-previousYaw[i])*Math.max(0,partial);p[i]=previousPitch[i]+(pitch[i]-previousPitch[i])*Math.max(0,partial);}visualMatrices=build(y,p);visualPartial=partial;}return visualMatrices[id];}
    private RigidTransform root(double x,double y,double z,float yaw){double px=craft.structure.bounds.maxX/2,pz=craft.structure.bounds.maxZ/2;return RigidTransform.translation(x+px,y,z+pz).then(RigidTransform.rotation(0,1,0,-yaw)).then(RigidTransform.translation(-px,0,-pz));}
    private RigidTransform root(){return root(craft.posX,craft.posY,craft.posZ,craft.rotationYaw);}
    public Vec3d point(Vec3d local,BlockPos owner,float partial){double[] p=body(bodyAt(owner),partial).point(local.x,local.y,local.z);return new Vec3d(p[0],p[1],p[2]);}
    public Vec3d inverse(Vec3d posed,BlockPos owner){double[] p=body(bodyAt(owner),1).inverse(posed.x,posed.y,posed.z);return new Vec3d(p[0],p[1],p[2]);}
    public AxisAlignedBB localBounds(AxisAlignedBB box,int body,float partial){if(body==0)return box;return transformBounds(box,body(body,partial));}
    private static AxisAlignedBB transformBounds(AxisAlignedBB b,RigidTransform t){double cx=(b.minX+b.maxX)/2,cy=(b.minY+b.maxY)/2,cz=(b.minZ+b.maxZ)/2,hx=(b.maxX-b.minX)/2,hy=(b.maxY-b.minY)/2,hz=(b.maxZ-b.minZ)/2;double[] r=t.r;double x=t.x+r[0]*cx+r[1]*cy+r[2]*cz,y=t.y+r[3]*cx+r[4]*cy+r[5]*cz,z=t.z+r[6]*cx+r[7]*cy+r[8]*cz,dx=Math.abs(r[0])*hx+Math.abs(r[1])*hy+Math.abs(r[2])*hz,dy=Math.abs(r[3])*hx+Math.abs(r[4])*hy+Math.abs(r[5])*hz,dz=Math.abs(r[6])*hx+Math.abs(r[7])*hy+Math.abs(r[8])*hz;return new AxisAlignedBB(x-dx,y-dy,z-dz,x+dx,y+dy,z+dz);}
    private static AxisAlignedBB broad(OrientedBounds box){return new AxisAlignedBB(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ);}
    public AxisAlignedBB worldBounds(double x,double y,double z,float yaw){bind();captureShapes();RigidTransform root=root(x,y,z,yaw);AxisAlignedBB result=null;for(int i=0;i<bodyBounds.length;i++){AxisAlignedBB b=bodyBounds[i];RigidTransform t=root.then(matrices[i]);AxisAlignedBB next=transformBounds(b,t);for(Shape shape:shapes.get(i))if(shape.coupler!=null)next=next.union(transformBounds(shape.box,t));result=result==null?next:result.union(next);}return result;}
    /** Current physical boxes for entity collision queries; no ownership changes. */
    public List<AxisAlignedBB> collisionBoxes(){
        bind();captureShapes();if(collisionMatrices==matrices&&collisionSource==craft.collision&&collisionX==craft.posX&&collisionY==craft.posY&&collisionZ==craft.posZ&&collisionYaw==craft.rotationYaw)return cachedCollision;
        List<AxisAlignedBB> result=new ArrayList<>();RigidTransform root=root();for(int i=0;i<shapes.size();i++){RigidTransform t=root.then(matrices[i]);for(Shape shape:shapes.get(i))result.add(transformBounds(shape.box,t));}
        collisionMatrices=matrices;collisionSource=craft.collision;collisionX=craft.posX;collisionY=craft.posY;collisionZ=craft.posZ;collisionYaw=craft.rotationYaw;return cachedCollision=Collections.unmodifiableList(result);
    }
    /** Check a geometry-only change in the existing body frame before publishing it. */
    public boolean permitsPose(VehicleWorld source,Map<BlockPos,? extends Number> desired){
        if(!active()||craft.world.isRemote)return true;VehicleWorld probe=new VehicleWorld(craft.world,source.structure);
        for(Map.Entry<BlockPos,? extends Number> e:desired.entrySet()){TileEntity tile=probe.getTileEntity(e.getKey());double value=e.getValue().doubleValue();if(tile instanceof com.vandorlabs.tiles.TileEntityCanopy)((com.vandorlabs.tiles.TileEntityCanopy)tile).progress=(float)value;else if(tile instanceof com.vandorlabs.gear.GearTileEntity)((com.vandorlabs.gear.GearTileEntity)tile).captureProgress(value);else if(tile instanceof com.vandorlabs.tiles.TileEntityLandingGear)((com.vandorlabs.tiles.TileEntityLandingGear)tile).progress=(float)value;}
        return permits(probe,source.structure);
    }
    public boolean permits(VehicleWorld view,VehicleStructure structure){
        if(!active())return true;captureShapes();List<List<Shape>> old=shapes;AxisAlignedBB[] oldBounds=bodyBounds;
        try{collectShapes(view,structure);terrain.clear();return safe(new Frame(yaw,pitch,matrices),root());}finally{shapes=old;bodyBounds=oldBounds;terrain.clear();}
    }
    /** A cached reach bound encloses every intermediate hinge and root-yaw pose. */
    public double renderRadius(){
        bind();captureShapes();if(radiusSource==craft.collision&&radiusTopology==topologyId)return radius;
        double[] distance=new double[bodyBounds.length];double[][] anchor=new double[bodyBounds.length][3];anchor[0]=new double[]{pivotX,0,pivotZ};for(Joint j:joints){double[] parent=anchor[j.parent];double dx=j.px-parent[0],dy=j.py-parent[1],dz=j.pz-parent[2];distance[j.child]=distance[j.parent]+Math.sqrt(dx*dx+dy*dy+dz*dz);anchor[j.child]=new double[]{j.px,j.py,j.pz};}
        AxisAlignedBB[] visible=bodyBounds.clone();for(Map.Entry<BlockPos,Integer> cell:bodyAt.entrySet()){int body=cell.getValue();visible[body]=visible[body].union(new AxisAlignedBB(cell.getKey()));}
        double reach=0;for(int body=0;body<bodyBounds.length;body++){AxisAlignedBB b=visible[body];double[] at=anchor[body];double dx=Math.max(Math.abs(b.minX-at[0]),Math.abs(b.maxX-at[0])),dy=Math.max(Math.abs(b.minY-at[1]),Math.abs(b.maxY-at[1])),dz=Math.max(Math.abs(b.minZ-at[2]),Math.abs(b.maxZ-at[2]));reach=Math.max(reach,distance[body]+Math.sqrt(dx*dx+dy*dy+dz*dz));}
        radiusSource=craft.collision;radiusTopology=topologyId;return radius=reach+Math.hypot(pivotX-craft.structure.bounds.maxX/2,pivotZ-craft.structure.bounds.maxZ/2)+2;
    }
    public boolean aligned(){if(!active())return true;for(int i=0;i<yaw.length;i++)if(Math.abs(yaw[i])>.25||Math.abs(pitch[i])>.25)return false;return true;}
    public void requireAligned(){if(!aligned())throw new IllegalArgumentException("Straighten articulated sections on level ground before parking, coupling or detaching");}
    public boolean parkable(){
        if(!active())return true;captureShapes();
        for(int i=0;i<yaw.length;i++)if(Math.abs(yaw[i])>5||Math.abs(pitch[i])>3)return false;
        // Bound accumulated drift down a long chain, not just each individual joint.
        for(int body=1;body<bodyBounds.length;body++){AxisAlignedBB box=bodyBounds[body];if(matrices[body].angle()>7.5)return false;for(int n=0;n<8;n++){double x=(n&1)==0?box.minX:box.maxX,y=(n&2)==0?box.minY:box.maxY,z=(n&4)==0?box.minZ:box.maxZ;double[] p=matrices[body].point(x,y,z);if((p[0]-x)*(p[0]-x)+(p[1]-y)*(p[1]-y)+(p[2]-z)*(p[2]-z)>.75*.75)return false;}}
        return true;
    }
    public void requireParkable(){if(!parkable())throw new IllegalArgumentException("Straighten trailers before parking (within 5 degrees yaw, 3 degrees pitch and 0.75 block of the grid)");}
    public boolean canConfigure(BlockPos at,boolean value){if(value||!active())return true;for(int i=0;i<joints.size();i++){Joint j=joints.get(i);if(at.equals(j.a)||at.equals(j.b)){TileEntity other=craft.view.getTileEntity(at.equals(j.a)?j.b:j.a);if(other instanceof TileCoupler&&((TileCoupler)other).articulation())return true;if(Math.abs(yaw[i])>.25||Math.abs(pitch[i])>.25)return false;}}return true;}
    public boolean begin(){if(!active())return false;captureShapes();base=new Frame(yaw.clone(),pitch.clone(),matrices);baseRoot=root();baseX=craft.posX;baseY=craft.posY;baseZ=craft.posZ;baseYaw=craft.rotationYaw;terrain.clear();return true;}
    private double ground(double x,double y,double z){
        int bx=MathHelper.floor(x),bz=MathHelper.floor(z);if(!craft.world.isBlockLoaded(new BlockPos(bx,MathHelper.clamp((int)y,0,255),bz)))return Double.NaN;double best=Double.NEGATIVE_INFINITY;
        for(AxisAlignedBB box:obstacles(new AxisAlignedBB(x-.15,Math.max(0,y-3),z-.15,x+.15,Math.min(craft.world.getHeight(),y+.5),z+.15)))if(box.maxY<=y+.50001&&box.maxY>=y-3&&box.maxX>x-.15&&box.minX<x+.15&&box.maxZ>z-.15&&box.minZ<z+.15)best=Math.max(best,box.maxY);
        return best==Double.NEGATIVE_INFINITY?y-.04:best;
    }
    private List<AxisAlignedBB> obstacles(AxisAlignedBB area){Set<AxisAlignedBB> result=new HashSet<>();for(int x=MathHelper.floor(area.minX)>>3;x<=MathHelper.floor(area.maxX)>>3;x++)for(int y=MathHelper.floor(area.minY)>>3;y<=MathHelper.floor(area.maxY)>>3;y++)for(int z=MathHelper.floor(area.minZ)>>3;z<=MathHelper.floor(area.maxZ)>>3;z++){BlockPos key=new BlockPos(x,y,z);List<AxisAlignedBB> boxes=terrain.get(key);if(boxes==null){boxes=craft.world.getCollisionBoxes(craft,new AxisAlignedBB(x*8,y*8,z*8,x*8+8,y*8+8,z*8+8).grow(1e-6));terrain.put(key,boxes);terrainQueries++;}for(AxisAlignedBB b:boxes)if(b.intersects(area))result.add(b);}return new ArrayList<>(result);}
    private Frame propose(RigidTransform newRoot){
        double[] ys=base.yaw.clone(),ps=base.pitch.clone();RigidTransform[] next=build(ys,ps);
        for(int i=0;i<joints.size();i++){
            Joint j=joints.get(i);RigidTransform parent=newRoot.then(next[j.parent]);double[] pivot=parent.point(j.px,j.py,j.pz),old=baseRoot.then(base.bodies[j.child]).point(j.axle[0],j.axle[1],j.axle[2]),neutral=parent.point(j.axle[0],j.axle[1],j.axle[2]);
            double targetY=craft.hover?old[1]+(neutral[1]-old[1])*.35:ground(old[0],old[1],old[2]);if(!Double.isFinite(targetY))return null;
            double[] target=parent.inverse(old[0],targetY,old[2]);double lx=j.axle[0]-j.px,ly=j.axle[1]-j.py,lz=j.axle[2]-j.pz;
            double desiredYaw=Math.toDegrees(Math.atan2(target[2]-j.pz,target[0]-j.px)-Math.atan2(lz,lx));desiredYaw=MathHelper.wrapDegrees(desiredYaw);desiredYaw=approach(base.yaw[i],desiredYaw,4);
            double[] h=RigidTransform.rotation(0,1,0,-desiredYaw).vector(j.hx,0,j.hz),wh=parent.vector(h[0],h[1],h[2]),up=parent.vector(0,1,0);double length=Math.hypot(lx,lz),a=wh[1]*length+up[1]*ly,b=-wh[1]*ly+up[1]*length,r=Math.hypot(a,b);
            double desiredPitch=r<1e-9?base.pitch[i]:Math.toDegrees(Math.asin(MathHelper.clamp((targetY-pivot[1])/r,-1,1))-Math.atan2(a,b));desiredPitch=approach(base.pitch[i],MathHelper.wrapDegrees(desiredPitch),4);double[] limited=RigidTransform.limitedAngles(desiredYaw,desiredPitch,MAX_ANGLE);ys[i]=limited[0];ps[i]=limited[1];next[j.child]=next[j.parent].then(RigidTransform.joint(j.px,j.py,j.pz,j.hx,j.hz,ys[i],ps[i]));
        }
        Frame result=new Frame(ys,ps,next);if(safe(result,newRoot))return result;
        for(int i=joints.size()-1;i>=0;i--){double ty=ys[i],tp=ps[i];ys[i]=base.yaw[i];ps[i]=base.pitch[i];Frame old=new Frame(ys,ps,build(ys,ps));if(safe(old,newRoot)){double lo=0,hi=1;for(int n=0;n<12;n++){double t=(lo+hi)/2;ys[i]=base.yaw[i]+(ty-base.yaw[i])*t;ps[i]=base.pitch[i]+(tp-base.pitch[i])*t;Frame trial=new Frame(ys,ps,build(ys,ps));if(safe(trial,newRoot))lo=t;else hi=t;}ys[i]=base.yaw[i]+(ty-base.yaw[i])*lo;ps[i]=base.pitch[i]+(tp-base.pitch[i])*lo;return new Frame(ys,ps,build(ys,ps));}ys[i]=ty;ps[i]=tp;}
        return null;
    }
    private static double approach(double old,double target,double step){return old+MathHelper.clamp(target-old,-step,step);}
    private boolean exempt(Shape a,Shape b){if(a.coupler==null||b.coupler==null)return false;for(Joint j:joints)if(a.coupler.equals(j.a)&&b.coupler.equals(j.b)||a.coupler.equals(j.b)&&b.coupler.equals(j.a))return true;return false;}
    private boolean safe(Frame frame,RigidTransform root){
        poseChecks++;List<List<OrientedBounds>> boxes=new ArrayList<>();AxisAlignedBB[] bounds=new AxisAlignedBB[shapes.size()];
        for(int body=0;body<shapes.size();body++){
            List<OrientedBounds> posed=new ArrayList<>();RigidTransform t=root.then(frame.bodies[body]);for(Shape shape:shapes.get(body)){AxisAlignedBB b=shape.box;OrientedBounds box=new OrientedBounds(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ,t);posed.add(box);AxisAlignedBB broad=broad(box);bounds[body]=bounds[body]==null?broad:bounds[body].union(broad);}boxes.add(posed);
            AxisAlignedBB area=bounds[body];if(area==null)continue;if(area.minY<0||area.maxY>=craft.world.getHeight()||!craft.world.getWorldBorder().contains(new BlockPos(area.minX,area.minY,area.minZ))||!craft.world.getWorldBorder().contains(new BlockPos(area.maxX,area.maxY,area.maxZ)))return false;
            for(int x=MathHelper.floor(area.minX)>>4;x<=MathHelper.floor(area.maxX)>>4;x++)for(int z=MathHelper.floor(area.minZ)>>4;z<=MathHelper.floor(area.maxZ)>>4;z++)if(!craft.world.isBlockLoaded(new BlockPos(x*16,64,z*16)))return false;
            for(AxisAlignedBB obstacle:obstacles(area)){OrientedBounds terrainBox=new OrientedBounds(obstacle.minX,obstacle.minY,obstacle.minZ,obstacle.maxX,obstacle.maxY,obstacle.maxZ,RigidTransform.IDENTITY);for(OrientedBounds box:posed)if(box.intersects(terrainBox))return false;}
        }
        for(int a=0;a<shapes.size();a++)for(int b=a+1;b<shapes.size();b++)if(bounds[a]!=null&&bounds[b]!=null&&bounds[a].intersects(bounds[b]))for(int i=0;i<boxes.get(a).size();i++)for(int n=0;n<boxes.get(b).size();n++)if(!exempt(shapes.get(a).get(i),shapes.get(b).get(n))&&boxes.get(a).get(i).intersects(boxes.get(b).get(n)))return false;
        return true;
    }
    public float turn(double x,double y,double z,float from,float change){
        if(base==null)begin();turnX=turnZ=0;turnYaw=from;if(Math.abs(change)<1e-7)return from;double px=pivotX,pz=pivotZ;double[] anchor=root(x,y,z,from).point(px,0,pz);
        int steps=Math.max(1,(int)Math.ceil(Math.abs(change)/.25));float accepted=from;
        for(int i=1;i<=steps;i++){float next=from+change*i/steps;double[] moved=root(x,y,z,next).point(px,0,pz);double dx=anchor[0]-moved[0],dz=anchor[2]-moved[2];if(propose(root(x+dx,y,z+dz,next))==null)break;accepted=next;turnX=dx;turnZ=dz;}turnYaw=accepted;return accepted;
    }
    public void applyTurn(){if(active()&&craft.rotationYaw==turnYaw&&(turnX!=0||turnZ!=0)){craft.setPosition(craft.posX+turnX,craft.posY,craft.posZ+turnZ);}turnX=turnZ=0;}
    public boolean loaded(double x,double y,double z,double dx,double dy,double dz,float yaw){AxisAlignedBB b=worldBounds(x+dx,y+dy,z+dz,yaw).grow(1);if(b.minY<0||b.maxY>=craft.world.getHeight())return false;if(!craft.world.getWorldBorder().contains(new BlockPos(b.minX,b.minY,b.minZ))||!craft.world.getWorldBorder().contains(new BlockPos(b.maxX,b.maxY,b.maxZ)))return false;for(int cx=MathHelper.floor(b.minX)>>4;cx<=MathHelper.floor(b.maxX)>>4;cx++)for(int cz=MathHelper.floor(b.minZ)>>4;cz<=MathHelper.floor(b.maxZ)>>4;cz++)if(!craft.world.isBlockLoaded(new BlockPos(cx*16,64,cz*16)))return false;return true;}
    public double clip(double x,double y,double z,double amount,int axis,float yaw){
        if(base==null)begin();if(amount==0)return 0;
        double angle=Math.toRadians(yaw),cos=Math.cos(angle),sin=Math.sin(angle),px=craft.structure.bounds.maxX/2,pz=craft.structure.bounds.maxZ/2;
        for(Shape shape:shapes.get(0)){VehicleCollision.OrientedBox box=new VehicleCollision.OrientedBox(shape.box,px,pz,cos,sin);AxisAlignedBB query=box.broad.offset(x,y,z).expand(axis==0?amount:0,axis==1?amount:0,axis==2?amount:0);for(AxisAlignedBB obstacle:obstacles(query))amount=box.clip(obstacle.offset(-x,-y,-z),amount,axis);}
        if(Math.abs(amount)<1e-8)return 0;int steps=Math.max(1,(int)Math.ceil(Math.abs(amount)/.04));double accepted=0;
        for(int n=1;n<=steps;n++){double next=amount*n/steps;if(propose(root(x+(axis==0?next:0),y+(axis==1?next:0),z+(axis==2?next:0),yaw))==null){double lo=accepted,hi=next;for(int i=0;i<14;i++){double mid=(lo+hi)/2;if(propose(root(x+(axis==0?mid:0),y+(axis==1?mid:0),z+(axis==2?mid:0),yaw))!=null)lo=mid;else hi=mid;}return lo;}accepted=next;}return amount;
    }
    public void end(){
        if(base==null)return;Frame accepted=propose(root());double targetX=craft.posX,targetY=craft.posY,targetZ=craft.posZ;float targetYaw=craft.rotationYaw;
        if(accepted==null){craft.rotationYaw=baseYaw;craft.setPosition(baseX,baseY,baseZ);craft.motionX=craft.motionY=craft.motionZ=0;craft.driveSpeed=0;}
        else{
            double distance=0;RigidTransform endRoot=root();
            for(int body=0;body<bodyBounds.length;body++){AxisAlignedBB box=bodyBounds[body];RigidTransform before=baseRoot.then(base.bodies[body]),after=endRoot.then(accepted.bodies[body]);for(int n=0;n<8;n++){double x=(n&1)==0?box.minX:box.maxX,y=(n&2)==0?box.minY:box.maxY,z=(n&4)==0?box.minZ:box.maxZ;double[] p=before.point(x,y,z),q=after.point(x,y,z);distance=Math.max(distance,Math.sqrt((p[0]-q[0])*(p[0]-q[0])+(p[1]-q[1])*(p[1]-q[1])+(p[2]-q[2])*(p[2]-q[2])));}}
            int steps=Math.max(1,(int)Math.ceil(distance/.025));Frame last=base;double fraction=0;
            for(int n=1;n<=steps;n++){double t=(double)n/steps;double[] y=new double[yaw.length],p=new double[pitch.length];for(int i=0;i<y.length;i++){y[i]=base.yaw[i]+(accepted.yaw[i]-base.yaw[i])*t;p[i]=base.pitch[i]+(accepted.pitch[i]-base.pitch[i])*t;}Frame trial=new Frame(y,p,build(y,p));RigidTransform r=root(baseX+(targetX-baseX)*t,baseY+(targetY-baseY)*t,baseZ+(targetZ-baseZ)*t,baseYaw+MathHelper.wrapDegrees(targetYaw-baseYaw)*(float)t);if(!safe(trial,r))break;last=trial;fraction=t;}
            yaw=last.yaw;pitch=last.pitch;matrices=last.bodies;visualMatrices=null;craft.rotationYaw=baseYaw+MathHelper.wrapDegrees(targetYaw-baseYaw)*(float)fraction;craft.setPosition(baseX+(targetX-baseX)*fraction,baseY+(targetY-baseY)*fraction,baseZ+(targetZ-baseZ)*fraction);if(fraction<1){craft.motionX=craft.motionY=craft.motionZ=0;craft.driveSpeed=0;}
        }
        base=null;terrain.clear();
    }
    public void clientTick(){if(!active()||craft.transferInProgress)return;previousYaw=yaw.clone();previousPitch=pitch.clone();if(remoteTicks>0){for(int i=0;i<yaw.length;i++){yaw[i]+=(targetYaw[i]-yaw[i])/remoteTicks;pitch[i]+=(targetPitch[i]-pitch[i])/remoteTicks;}remoteTicks--;matrices=build(yaw,pitch);}visualMatrices=null;}
    public int jointCount(){bind();return joints.size();}
    public double jointYaw(int joint){bind();return yaw[joint];}
    public double jointPitch(int joint){bind();return pitch[joint];}
    public boolean validPose(){if(!active())return true;captureShapes();terrain.clear();try{return safe(new Frame(yaw,pitch,matrices),root());}finally{terrain.clear();}}
    public short[] network(){if(!active())return new short[0];short[] result=new short[yaw.length*2];for(int i=0;i<yaw.length;i++){result[i*2]=(short)Math.round(yaw[i]*100);result[i*2+1]=(short)Math.round(pitch[i]*100);}return result;}
    public long topologyId(){bind();return topologyId;}
    public void receive(long token,short[] data,boolean local){bind();if(!active()||token!=topologyId||data.length!=yaw.length*2){pendingNetwork=data.clone();pendingTopology=token;return;}if(local)applyNetwork(data);else{boolean changed=false;for(int i=0;i<yaw.length;i++){double[] limited=RigidTransform.limitedAngles(data[i*2]/100D,data[i*2+1]/100D,MAX_ANGLE);changed|=targetYaw[i]!=limited[0]||targetPitch[i]!=limited[1];targetYaw[i]=limited[0];targetPitch[i]=limited[1];}if(changed)remoteTicks=3;}}
    private void applyNetwork(short[] data){for(int i=0;i<yaw.length;i++){double[] angle=RigidTransform.limitedAngles(data[i*2]/100D,data[i*2+1]/100D,MAX_ANGLE);yaw[i]=angle[0];pitch[i]=angle[1];}matrices=build(yaw,pitch);visualMatrices=null;}
    public void write(NBTTagCompound tag){if(craft.structure==null&&retained!=null){tag.setTag("CouplerJointPose",retained.copy());return;}if(!active()){tag.removeTag("CouplerJointPose");return;}NBTTagCompound saved=new NBTTagCompound();saved.setInteger("Version",1);NBTTagList list=new NBTTagList();for(int i=0;i<joints.size();i++)if(Math.abs(yaw[i])+Math.abs(pitch[i])>1e-8){Joint j=joints.get(i);NBTTagCompound n=new NBTTagCompound();n.setLong("A",j.a.toLong());n.setLong("B",j.b.toLong());n.setDouble("Yaw",yaw[i]);n.setDouble("Pitch",pitch[i]);list.appendTag(n);}saved.setTag("Joints",list);if(list.tagCount()>0)tag.setTag("CouplerJointPose",saved);else tag.removeTag("CouplerJointPose");}
    public void retain(NBTTagCompound tag){retained=tag.hasKey("CouplerJointPose",10)?tag.getCompoundTag("CouplerJointPose").copy():null;}
    public void read(NBTTagCompound tag){retain(tag);bind();if(retained==null)return;if(retained.getInteger("Version")!=1)throw new IllegalArgumentException("Unknown articulated pose version");NBTTagList list=retained.getTagList("Joints",10);if(list.tagCount()>joints.size())throw new IllegalArgumentException("Articulated joint count changed");Set<String> seen=new HashSet<>();for(int n=0;n<list.tagCount();n++){NBTTagCompound data=list.getCompoundTagAt(n);BlockPos a=BlockPos.fromLong(data.getLong("A")),b=BlockPos.fromLong(data.getLong("B"));int found=-1;for(int i=0;i<joints.size();i++)if(joints.get(i).a.equals(a)&&joints.get(i).b.equals(b)||joints.get(i).a.equals(b)&&joints.get(i).b.equals(a)){found=i;break;}if(found<0||!seen.add(id(joints.get(found))))throw new IllegalArgumentException("Unknown or duplicate articulated joint");double y=data.getDouble("Yaw"),p=data.getDouble("Pitch");if(!Double.isFinite(y)||!Double.isFinite(p)||Math.abs(y)>MAX_ANGLE||Math.abs(p)>MAX_ANGLE||RigidTransform.joint(0,0,0,joints.get(found).hx,joints.get(found).hz,y,p).angle()>MAX_ANGLE+1e-6)throw new IllegalArgumentException("Invalid articulated joint angles");yaw[found]=y;pitch[found]=p;}matrices=build(yaw,pitch);previousYaw=yaw.clone();previousPitch=pitch.clone();retained=null;}
}
