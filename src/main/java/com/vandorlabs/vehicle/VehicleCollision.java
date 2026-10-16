package com.vandorlabs.vehicle;

import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Cached local collision boxes, coalesced along each axis to reduce hull cost. */
public final class VehicleCollision {
    public final List<AxisAlignedBB> boxes;
    public final AxisAlignedBB bounds;
    private final List<Cluster> clusters=new ArrayList<>();
    private static final boolean PROFILE=VehicleDiagnostics.ENABLED||Boolean.getBoolean("vandorlabs.checkVehiclePerformance")||Boolean.getBoolean("vandorlabs.checkVehicleAnimation");
    private final long collectionNanos,mergeNanos,indexNanos,deduplicationNanos,axis0Nanos,axis1Nanos,axis2Nanos;
    private final int rawBoxes,uniqueBoxes,firstPassBoxes,secondPassBoxes;
    private final boolean cached,cacheable;
    public String buildProfile(){return "raw-boxes="+rawBoxes+" unique-boxes="+uniqueBoxes+" first-pass="+firstPassBoxes+" second-pass="+secondPassBoxes+" merged-boxes="+boxes.size()+" clusters="+clusters.size()+" collect-us="+collectionNanos/1000+" merge-us="+mergeNanos/1000+" index-us="+indexNanos/1000+" dedup-us="+deduplicationNanos/1000+" axis0-us="+axis0Nanos/1000+" axis1-us="+axis1Nanos/1000+" axis2-us="+axis2Nanos/1000+" cached="+cached;}
    private static final class Cluster {
        final List<AxisAlignedBB> boxes=new ArrayList<>();AxisAlignedBB bounds;
        void add(AxisAlignedBB box){boxes.add(box);bounds=bounds==null?box:bounds.union(box);}
    }
    /** Exact coordinate bits avoid formatting thousands of doubles during animation. */
    private static final class FaceKey {
        final long a,b,c,d;
        FaceKey(double a,double b,double c,double d){this.a=Double.doubleToLongBits(a);this.b=Double.doubleToLongBits(b);this.c=Double.doubleToLongBits(c);this.d=Double.doubleToLongBits(d);}
        @Override public int hashCode(){long hash=a^Long.rotateLeft(b,13)^Long.rotateLeft(c,29)^Long.rotateLeft(d,47);hash=(hash^(hash>>>33))*0xff51afd7ed558ccdL;hash=(hash^(hash>>>33))*0xc4ceb9fe1a85ec53L;return (int)(hash^(hash>>>32));}
        @Override public boolean equals(Object other){if(!(other instanceof FaceKey))return false;FaceKey k=(FaceKey)other;return a==k.a&&b==k.b&&c==k.c&&d==k.d;}
    }
    /** Immutable captured coordinates; worker preparation never touches a World or tile. */
    static final class Raw {
        final List<AxisAlignedBB> boxes;final double pivotX,pivotZ;final long collectionNanos;final String diagnosticSide;final boolean complete;
        Raw(List<AxisAlignedBB> boxes,double x,double z,long nanos,String side,boolean complete){this.boxes=Collections.unmodifiableList(boxes);pivotX=x;pivotZ=z;collectionNanos=nanos;diagnosticSide=side;this.complete=complete;}
    }
    /** Immutable geometry only. Mutable heading/terrain caches belong to each wrapper. */
    static final class Geometry {
        final List<AxisAlignedBB> boxes;final List<Cluster> clusters;final AxisAlignedBB bounds;final double pivotX,pivotZ;final boolean cacheable;
        Geometry(VehicleCollision collision){boxes=collision.boxes;clusters=Collections.unmodifiableList(new ArrayList<>(collision.clusters));bounds=collision.bounds;pivotX=collision.pivotX;pivotZ=collision.pivotZ;cacheable=collision.cacheable;}
    }
    Geometry geometry(){return new Geometry(this);}
    VehicleCollision(Geometry geometry){
        boxes=geometry.boxes;clusters.addAll(geometry.clusters);bounds=geometry.bounds;pivotX=geometry.pivotX;pivotZ=geometry.pivotZ;
        rawBoxes=uniqueBoxes=firstPassBoxes=secondPassBoxes=boxes.size();collectionNanos=mergeNanos=indexNanos=deduplicationNanos=axis0Nanos=axis1Nanos=axis2Nanos=0;cached=true;cacheable=geometry.cacheable;
    }
    public VehicleCollision(VehicleWorld view) {this(capture(view),false);}
    private VehicleCollision(VehicleWorld view,boolean reference) {this(capture(view),reference);}
    static Raw capture(VehicleWorld view){
        long profileStart=PROFILE?System.nanoTime():0;
        long diagnostic=view.craft==null?0:VehicleAnimationDiagnostics.start(view.craft);
        String diagnosticSide=diagnostic==0?null:view.parent.isRemote?"client/":"server/";
        List<AxisAlignedBB> all=new ArrayList<>();
        Set<BlockPos> canopyOwners=new HashSet<>();boolean complete=true;
        AxisAlignedBB query=view.structure.bounds.grow(16);
        for(VehicleStructure.Cell c:view.structure.cells) {
            List<AxisAlignedBB> component=new ArrayList<>();
            try {
                if(c.state.getBlock() instanceof com.vandorlabs.blocks.BlockCanopy) {
                    net.minecraft.tileentity.TileEntity raw=view.getTileEntity(c.pos);
                    if(raw instanceof com.vandorlabs.tiles.TileEntityCanopy) {
                        com.vandorlabs.tiles.TileEntityCanopy owner=((com.vandorlabs.tiles.TileEntityCanopy)raw).owner();
                        // Owner boxes already use VehicleWorld coordinates, including shell displacement.
                        if(owner.draws() && canopyOwners.add(owner.getPos())){
                            long ownerStart=diagnostic==0?0:System.nanoTime();List<AxisAlignedBB> ownerBoxes=owner.boxes();
                            if(diagnostic!=0){String name=diagnosticSide+"collision-owner/"+owner.block().kind+"@"+owner.getPos();VehicleAnimationDiagnostics.value(name+"/collect",System.nanoTime()-ownerStart);VehicleAnimationDiagnostics.value(name+"/raw-boxes-count",ownerBoxes.size());}
                            component.addAll(ownerBoxes);
                        }
                    }
                } else if(c.state.getBlock() instanceof com.vandorlabs.blocks.BlockTelescopicLandingGear)
                    component.addAll(((com.vandorlabs.blocks.BlockTelescopicLandingGear)c.state.getBlock()).vehicleBoxes(view,c.pos));
                else c.state.addCollisionBoxToList(view,c.pos,query,component,null,false);
                all.addAll(component);
            }
            catch(RuntimeException | LinkageError error){
                complete=false;VehicleCompatibility.warn(c.state.getBlock(),"collision",error);
                all.add(new AxisAlignedBB(c.pos));
            }
        }
        if(all.isEmpty())throw new IllegalArgumentException("The craft has no collision geometry.");
        return new Raw(all,view.structure.bounds.maxX/2,view.structure.bounds.maxZ/2,PROFILE?System.nanoTime()-profileStart:0,diagnostic==0?null:diagnosticSide,complete);
    }
    static VehicleCollision prepare(Raw raw){return new VehicleCollision(raw,false);}
    /** Copied face coordinates and ordinary boxes, captured on the world thread. */
    static final class DeferredRaw {
        final List<DeferredPart> parts;final double pivotX,pivotZ;
        DeferredRaw(List<DeferredPart> parts,double x,double z){this.parts=Collections.unmodifiableList(parts);pivotX=x;pivotZ=z;}
    }
    private static final class DeferredPart {
        final List<AxisAlignedBB> boxes;final List<com.vandorlabs.canopy.CanopyMesh.Face> faces;
        final boolean window;final net.minecraft.util.EnumFacing facing;final BlockPos pos;final int width;
        DeferredPart(List<AxisAlignedBB> boxes){this.boxes=Collections.unmodifiableList(new ArrayList<>(boxes));faces=null;window=false;facing=null;pos=null;width=0;}
        DeferredPart(com.vandorlabs.tiles.TileEntityCanopy owner){
            boxes=null;window=owner.resizable();facing=owner.facing();pos=owner.getPos().toImmutable();width=owner.width();
            List<com.vandorlabs.canopy.CanopyMesh.Face> copied=new ArrayList<>();
            for(com.vandorlabs.canopy.CanopyMesh.Face original:owner.faces()){
                com.vandorlabs.canopy.CanopyMesh.Face copy=new com.vandorlabs.canopy.CanopyMesh.Face();
                copy.vertices=original.vertices.clone();copy.group=window?"":original.group;copy.glazing=original.glazing;copy.baseMaterial=original.baseMaterial;copied.add(copy);
            }
            faces=Collections.unmodifiableList(copied);
        }
    }
    static DeferredRaw defer(VehicleWorld view,int maximum){
        List<DeferredPart> parts=new ArrayList<>();Set<BlockPos> owners=new HashSet<>();AxisAlignedBB query=view.structure.bounds.grow(16);long bound=0;
        for(VehicleStructure.Cell cell:view.structure.cells){
            List<AxisAlignedBB> boxes=new ArrayList<>();DeferredPart captured;
            try{
                if(cell.state.getBlock() instanceof com.vandorlabs.blocks.BlockCanopy){
                    net.minecraft.tileentity.TileEntity raw=view.getTileEntity(cell.pos);if(!(raw instanceof com.vandorlabs.tiles.TileEntityCanopy))continue;
                    com.vandorlabs.tiles.TileEntityCanopy owner=((com.vandorlabs.tiles.TileEntityCanopy)raw).owner();if(!owner.draws()||!owners.add(owner.getPos()))continue;
                    captured=new DeferredPart(owner);
                    for(com.vandorlabs.canopy.CanopyMesh.Face face:captured.faces){
                        if(captured.window&&!face.glazing&&!"glass".equals(face.baseMaterial))continue;
                        bound+=face.vertices.length;
                    }
                }else{
                    if(cell.state.getBlock() instanceof com.vandorlabs.blocks.BlockTelescopicLandingGear)boxes.addAll(((com.vandorlabs.blocks.BlockTelescopicLandingGear)cell.state.getBlock()).vehicleBoxes(view,cell.pos));
                    else cell.state.addCollisionBoxToList(view,cell.pos,query,boxes,null,false);
                    for(AxisAlignedBB box:boxes)if(box.getClass()!=AxisAlignedBB.class)return null;
                    bound+=boxes.size();captured=new DeferredPart(boxes);
                }
            }catch(RuntimeException|LinkageError failure){return null;}
            if(bound>maximum)return null;parts.add(captured);
        }
        return new DeferredRaw(parts,view.structure.bounds.maxX/2,view.structure.bounds.maxZ/2);
    }
    static Geometry prepare(DeferredRaw deferred,int maximum){
        List<AxisAlignedBB> all=new ArrayList<>();
        for(DeferredPart part:deferred.parts){
            if(part.boxes!=null)all.addAll(part.boxes);
            else{
                Set<AxisAlignedBB> rails=new HashSet<>();
                for(com.vandorlabs.canopy.CanopyMesh.Face face:part.faces){
                    if(part.window&&!face.glazing&&!"glass".equals(face.baseMaterial))continue;
                    all.addAll(com.vandorlabs.canopy.CanopyMesh.collision(Collections.singletonList(face),"posed",part.width,0,part.facing,part.pos));
                    if(all.size()+rails.size()>maximum)throw new IllegalArgumentException("Deferred collision exceeds its coordinate budget.");
                    if(part.window)for(int i=0;i<face.vertices.length;i++){
                        Vec3d a=com.vandorlabs.canopy.CanopyMesh.rotate(face.vertices[i],part.facing),b=com.vandorlabs.canopy.CanopyMesh.rotate(face.vertices[(i+1)%face.vertices.length],part.facing);
                        int steps=Math.max(1,(int)Math.ceil(a.distanceTo(b)*8));
                        for(int step=0;step<steps;step++){Vec3d from=a.add(b.subtract(a).scale(step/(double)steps)),to=a.add(b.subtract(a).scale((step+1D)/steps));rails.add(new AxisAlignedBB(from,to).grow(1D/16+.002).offset(part.pos));if(all.size()+rails.size()>maximum)throw new IllegalArgumentException("Deferred rails exceed their coordinate budget.");}
                    }
                }
                all.addAll(rails);
            }
            if(all.size()>maximum)throw new IllegalArgumentException("Deferred collision exceeds its coordinate budget.");
        }
        return prepare(new Raw(all,deferred.pivotX,deferred.pivotZ,0,null,true)).geometry();
    }
    private VehicleCollision(Raw raw,boolean reference){
        List<AxisAlignedBB> all=raw.boxes;pivotX=raw.pivotX;pivotZ=raw.pivotZ;collectionNanos=raw.collectionNanos;cached=false;cacheable=raw.complete;
        long diagnostic=raw.diagnosticSide==null?0:1;String diagnosticSide=raw.diagnosticSide;PrimitiveProfile primitiveProfile=diagnostic==0?null:new PrimitiveProfile();
        if(all.isEmpty())throw new IllegalArgumentException("The craft has no collision geometry.");
        rawBoxes=all.size();long collected=PROFILE?System.nanoTime():0;
        // Equal adjacent full faces merge without filling empty interiors.
        all=reference?new ArrayList<>(new LinkedHashSet<>(all)):deduplicate(all,primitiveProfile);uniqueBoxes=all.size();
        long deduped=PROFILE?System.nanoTime():0;deduplicationNanos=deduped-collected;
        all=reference?legacyMerge(all,0):merge(all,0,primitiveProfile);firstPassBoxes=all.size();long first=PROFILE?System.nanoTime():0;axis0Nanos=first-deduped;
        all=reference?legacyMerge(all,1):merge(all,1,primitiveProfile);secondPassBoxes=all.size();long second=PROFILE?System.nanoTime():0;axis1Nanos=second-first;
        all=reference?legacyMerge(all,2):merge(all,2,primitiveProfile);
        long merged=PROFILE?System.nanoTime():0;axis2Nanos=merged-second;mergeNanos=merged-collected;
        if(diagnostic!=0){VehicleAnimationDiagnostics.value(diagnosticSide+"collision-dedup",deduplicationNanos);VehicleAnimationDiagnostics.value(diagnosticSide+"collision-axis0",axis0Nanos);VehicleAnimationDiagnostics.value(diagnosticSide+"collision-axis1",axis1Nanos);VehicleAnimationDiagnostics.value(diagnosticSide+"collision-axis2",axis2Nanos);
            for(int pass=0;pass<4;pass++){String name=diagnosticSide+"collision-table/"+(pass==0?"dedup":"axis"+(pass-1));VehicleAnimationDiagnostics.value(name+"/extra-probes-count",primitiveProfile.probes[pass]);VehicleAnimationDiagnostics.value(name+"/max-probes-count",primitiveProfile.maximum[pass]);VehicleAnimationDiagnostics.value(name+"/groups-count",primitiveProfile.groups[pass]);}
        }
        if(all.size()>8192)throw new IllegalArgumentException("Craft collision geometry exceeds 8192 boxes.");
        boxes=Collections.unmodifiableList(all);
        AxisAlignedBB bound=all.get(0);for(AxisAlignedBB box:all)bound=bound.union(box);bounds=bound;
        // Keep every original box, but amortize terrain queries over nearby surfaces.
        // Four-block clusters avoid querying the empty interior of a large hollow hull.
        Map<BlockPos,Cluster> grouped=new LinkedHashMap<>();
        for(AxisAlignedBB box:all){BlockPos key=new BlockPos(MathHelper.floor((box.minX+box.maxX)/2)>>2,MathHelper.floor((box.minY+box.maxY)/2)>>2,MathHelper.floor((box.minZ+box.maxZ)/2)>>2);grouped.computeIfAbsent(key,k->new Cluster()).add(box);}
        clusters.addAll(grouped.values());indexNanos=PROFILE?System.nanoTime()-merged:0;
    }
    private static final class PrimitiveProfile {final long[] probes=new long[4];final int[] maximum=new int[4],groups=new int[4];void sample(int pass,int count){probes[pass]+=count;maximum[pass]=Math.max(maximum[pass],count);}}
    private static int tableSize(int entries){int size=2;while(size<entries*2L){if(size>=1<<29)throw new IllegalArgumentException("Too many raw collision boxes.");size<<=1;}return size;}
    private static long mix(long hash){hash=(hash^(hash>>>33))*0xff51afd7ed558ccdL;hash=(hash^(hash>>>33))*0xc4ceb9fe1a85ec53L;return hash^(hash>>>33);}
    private static long bits(double value){return Double.doubleToLongBits(value);}
    /** AxisAlignedBB.equals uses Double.compare: canonical NaNs compare equal, signed zeros differ. */
    private static List<AxisAlignedBB> deduplicate(List<AxisAlignedBB> input){return deduplicate(input,null);}
    private static List<AxisAlignedBB> deduplicate(List<AxisAlignedBB> input,PrimitiveProfile profile){
        // An adapter may supply a subclass with its own equality contract.
        for(AxisAlignedBB box:input)if(box.getClass()!=AxisAlignedBB.class)return new ArrayList<>(new LinkedHashSet<>(input));
        int[] table=new int[tableSize(input.size())];int mask=table.length-1;
        List<AxisAlignedBB> output=new ArrayList<>(input.size());
        for(AxisAlignedBB box:input){
            long hash=bits(box.minX)^Long.rotateLeft(bits(box.minY),11)^Long.rotateLeft(bits(box.minZ),23)^Long.rotateLeft(bits(box.maxX),37)^Long.rotateLeft(bits(box.maxY),43)^Long.rotateLeft(bits(box.maxZ),53);
            int slot=(int)mix(hash)&mask,probes=0;
            while(table[slot]!=0&&!box.equals(output.get(table[slot]-1))){slot=(slot+1)&mask;probes++;}
            if(profile!=null)profile.sample(0,probes);
            if(table[slot]==0){output.add(box);table[slot]=output.size();}
        }
        if(profile!=null)profile.groups[0]=output.size();
        return output;
    }
    private static final Comparator<AxisAlignedBB> X_ORDER=Comparator.comparingDouble(b->b.minX),Y_ORDER=Comparator.comparingDouble(b->b.minY),Z_ORDER=Comparator.comparingDouble(b->b.minZ);
    /** First-seen groups and stable sorts exactly match the former LinkedHashMap pipeline. */
    private static List<AxisAlignedBB> merge(List<AxisAlignedBB> input,int axis){return merge(input,axis,null);}
    private static List<AxisAlignedBB> merge(List<AxisAlignedBB> input,int axis,PrimitiveProfile profile){
        int size=input.size(),groups=0;int[] table=new int[tableSize(size)],representative=new int[size],groupOf=new int[size],counts=new int[size];int mask=table.length-1;
        for(int i=0;i<size;i++){
            AxisAlignedBB box=input.get(i);
            long a=bits(axis==0?box.minY:box.minX),b=bits(axis==0?box.maxY:box.maxX),c=bits(axis==2?box.minY:box.minZ),d=bits(axis==2?box.maxY:box.maxZ);
            int slot=(int)mix(a^Long.rotateLeft(b,13)^Long.rotateLeft(c,29)^Long.rotateLeft(d,47))&mask,probes=0;
            while(table[slot]!=0){AxisAlignedBB other=input.get(representative[table[slot]-1]);if(a==bits(axis==0?other.minY:other.minX)&&b==bits(axis==0?other.maxY:other.maxX)&&c==bits(axis==2?other.minY:other.minZ)&&d==bits(axis==2?other.maxY:other.maxZ))break;slot=(slot+1)&mask;probes++;}
            if(profile!=null)profile.sample(axis+1,probes);
            int group=table[slot]-1;if(group<0){group=groups++;representative[group]=i;table[slot]=group+1;}groupOf[i]=group;counts[group]++;
        }
        if(profile!=null)profile.groups[axis+1]=groups;
        int[] starts=new int[groups+1];for(int group=0;group<groups;group++)starts[group+1]=starts[group]+counts[group];
        int[] write=starts.clone();AxisAlignedBB[] ordered=new AxisAlignedBB[size];for(int i=0;i<size;i++)ordered[write[groupOf[i]]++]=input.get(i);
        Comparator<AxisAlignedBB> comparator=axis==0?X_ORDER:axis==1?Y_ORDER:Z_ORDER;List<AxisAlignedBB> output=new ArrayList<>(size);
        for(int group=0;group<groups;group++){
            int from=starts[group],to=starts[group+1],count=to-from;
            if(count==1){output.add(ordered[from]);continue;}
            if(count<=8){
                // The same Double.compare ordering, stable for equal starts (including signed zero).
                for(int i=from+1;i<to;i++){
                    AxisAlignedBB box=ordered[i];double start=axis==0?box.minX:axis==1?box.minY:box.minZ;int j=i;
                    while(j>from&&Double.compare(start,axis==0?ordered[j-1].minX:axis==1?ordered[j-1].minY:ordered[j-1].minZ)<0){ordered[j]=ordered[j-1];j--;}
                    ordered[j]=box;
                }
            }else Arrays.sort(ordered,from,to,comparator);
            AxisAlignedBB current=ordered[from];
            for(int i=from+1;i<to;i++){
                AxisAlignedBB box=ordered[i];double start=axis==0?box.minX:axis==1?box.minY:box.minZ,end=axis==0?current.maxX:axis==1?current.maxY:current.maxZ;
                if(start<=end+1e-8){
                    // Math.min/max also define union's NaN and signed-zero behavior. Reuse only exact bits.
                    if(current.getClass()!=AxisAlignedBB.class||bits(current.minX)!=bits(Math.min(current.minX,box.minX))||bits(current.minY)!=bits(Math.min(current.minY,box.minY))||bits(current.minZ)!=bits(Math.min(current.minZ,box.minZ))
                            ||bits(current.maxX)!=bits(Math.max(current.maxX,box.maxX))||bits(current.maxY)!=bits(Math.max(current.maxY,box.maxY))||bits(current.maxZ)!=bits(Math.max(current.maxZ,box.maxZ)))current=current.union(box);
                }else{output.add(current);current=box;}
            }
            output.add(current);
        }
        return output;
    }
    private static List<AxisAlignedBB> legacyMerge(List<AxisAlignedBB> input,int axis) {
        Map<FaceKey,List<AxisAlignedBB>> groups=new LinkedHashMap<>();
        for(AxisAlignedBB b:input){FaceKey key=axis==0?new FaceKey(b.minY,b.maxY,b.minZ,b.maxZ):axis==1?new FaceKey(b.minX,b.maxX,b.minZ,b.maxZ):new FaceKey(b.minX,b.maxX,b.minY,b.maxY);groups.computeIfAbsent(key,k->new ArrayList<>()).add(b);}
        List<AxisAlignedBB> output=new ArrayList<>();
        for(List<AxisAlignedBB> group:groups.values()) {
            group.sort(Comparator.comparingDouble(b->axis==0?b.minX:axis==1?b.minY:b.minZ));
            AxisAlignedBB current=null;
            for(AxisAlignedBB b:group) {
                double start=axis==0?b.minX:axis==1?b.minY:b.minZ, end=current==null?Double.NEGATIVE_INFINITY:axis==0?current.maxX:axis==1?current.maxY:current.maxZ;
                if(current!=null && start<=end+1e-8)current=current.union(b);
                else {if(current!=null)output.add(current);current=b;}
            }
            if(current!=null)output.add(current);
        }
        return output;
    }
    private float posedYaw=Float.NaN;
    private List<OrientedBox> posed;
    private AxisAlignedBB posedBounds;
    private final LinkedHashMap<Float,Posed> recentPoses=new LinkedHashMap<Float,Posed>(4,.75F,true);
    private static final class Posed {final List<OrientedBox> boxes;final AxisAlignedBB bounds;Posed(List<OrientedBox> boxes,AxisAlignedBB bounds){this.boxes=boxes;this.bounds=bounds;}}
    private double pivotX,pivotZ;
    public List<OrientedBox> pose(float yaw) {
        if(posed!=null && yaw==posedYaw)return posed;
        Posed cached=recentPoses.get(yaw);
        if(cached!=null){posedYaw=yaw;posed=cached.boxes;posedBounds=cached.bounds;return posed;}
        posedYaw=yaw;posed=new ArrayList<>();posedBounds=null;
        double radians=Math.toRadians(yaw),cos=Math.cos(radians),sin=Math.sin(radians);
        for(AxisAlignedBB box:boxes) {
            OrientedBox b=new OrientedBox(box,pivotX,pivotZ,cos,sin);posed.add(b);
            posedBounds=posedBounds==null?b.broad:posedBounds.union(b.broad);
        }
        recentPoses.put(yaw,new Posed(posed,posedBounds));
        if(recentPoses.size()>3)recentPoses.remove(recentPoses.keySet().iterator().next());
        return posed;
    }
    private float boundedYaw=Float.NaN;private AxisAlignedBB bounded;
    public AxisAlignedBB bounds(float yaw){
        if(posed!=null&&yaw==posedYaw)return posedBounds;
        if(bounded!=null&&yaw==boundedYaw)return bounded;
        double radians=Math.toRadians(yaw),cos=Math.cos(radians),sin=Math.sin(radians);
        double minX=Double.POSITIVE_INFINITY,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxZ=maxX;
        for(AxisAlignedBB box:boxes){
            double x=(box.minX+box.maxX)/2-pivotX,z=(box.minZ+box.maxZ)/2-pivotZ,cx=pivotX+cos*x-sin*z,cz=pivotZ+sin*x+cos*z;
            double hx=(box.maxX-box.minX)/2,hz=(box.maxZ-box.minZ)/2,rx=Math.abs(cos)*hx+Math.abs(sin)*hz,rz=Math.abs(sin)*hx+Math.abs(cos)*hz;
            minX=Math.min(minX,cx-rx);maxX=Math.max(maxX,cx+rx);minZ=Math.min(minZ,cz-rz);maxZ=Math.max(maxZ,cz+rz);
        }
        boundedYaw=yaw;return bounded=new AxisAlignedBB(minX,bounds.minY,minZ,maxX,bounds.maxY,maxZ);
    }
    public boolean loaded(World world,double x,double y,double z,double dx,double dy,double dz){return loaded(world,x,y,z,dx,dy,dz,0);}
    public boolean loaded(World world,double x,double y,double z,double dx,double dy,double dz,float yaw) {
        AxisAlignedBB b=bounds(yaw).offset(x,y,z).expand(dx,dy,dz).grow(1);
        if(b.minY<0 || b.maxY>=world.getHeight())return false;
        if(!world.getWorldBorder().contains(new BlockPos(b.minX,b.minY,b.minZ)) || !world.getWorldBorder().contains(new BlockPos(b.maxX,b.maxY,b.maxZ)))return false;
        for(int cx=MathHelper.floor(b.minX)>>4;cx<=MathHelper.floor(b.maxX)>>4;cx++)
            for(int cz=MathHelper.floor(b.minZ)>>4;cz<=MathHelper.floor(b.maxZ)>>4;cz++)
                if(!world.isBlockLoaded(new BlockPos(cx*16,64,cz*16)))return false;
        return true;
    }
    private Terrain terrain,replayTerrain;
    public int terrainQueries,candidateBoxes;
    /** Synchronous prediction only: world obstacles cannot tick between replayed inputs. */
    public void withTerrainCache(World world,net.minecraft.entity.Entity entity,Runnable replay){
        Terrain previous=replayTerrain,active=terrain;replayTerrain=new Terrain(world,entity);
        try{replay.run();}finally{replayTerrain=previous;terrain=active;}
    }
    public void begin(World world,net.minecraft.entity.Entity entity){terrain=replayTerrain!=null&&replayTerrain.world==world&&replayTerrain.entity==entity?replayTerrain:new Terrain(world,entity);terrainQueries=candidateBoxes=0;}
    public void end(){terrain=null;}
    private final class Terrain {
        final World world;final net.minecraft.entity.Entity entity;
        final Map<BlockPos,List<AxisAlignedBB>> sections=new HashMap<>();
        Terrain(World world,net.minecraft.entity.Entity entity){this.world=world;this.entity=entity;}
        Collection<AxisAlignedBB> obstacles(AxisAlignedBB swept) {
            Set<AxisAlignedBB> found=new HashSet<>();
            // Query only eight-block sections touched by actual hull sweeps, never a hollow hull's full volume.
            for(int x=MathHelper.floor(swept.minX)>>3;x<=MathHelper.floor(swept.maxX)>>3;x++)
                for(int y=MathHelper.floor(swept.minY)>>3;y<=MathHelper.floor(swept.maxY)>>3;y++)
                    for(int z=MathHelper.floor(swept.minZ)>>3;z<=MathHelper.floor(swept.maxZ)>>3;z++) {
                        BlockPos key=new BlockPos(x,y,z);
                        List<AxisAlignedBB> candidates=sections.get(key);
                        if(candidates==null) {
                            candidates=world.getCollisionBoxes(entity,new AxisAlignedBB(x*8,y*8,z*8,x*8+8,y*8+8,z*8+8).grow(1e-6));
                            sections.put(key,candidates);terrainQueries++;candidateBoxes+=candidates.size();
                        }
                        for(AxisAlignedBB box:candidates)if(box.intersects(swept))found.add(box);
                    }
            return found;
        }
    }
    private Collection<AxisAlignedBB> obstacles(World world,net.minecraft.entity.Entity entity,AxisAlignedBB swept) {
        if(boxes.size()<=8){terrainQueries++;List<AxisAlignedBB> result=world.getCollisionBoxes(entity,swept);candidateBoxes+=result.size();return result;}
        return (terrain==null?new Terrain(world,entity):terrain).obstacles(swept);
    }
    public float turn(World world,net.minecraft.entity.Entity entity,double x,double y,double z,float from,float change) {
        if(boxes.size()<=32)return legacyTurn(world,entity,x,y,z,from,change);
        if(Math.abs(change)<1e-6)return from;
        double radius=Math.hypot(bounds.maxX-pivotX,bounds.maxZ-pivotZ);
        int steps=Math.max(1,(int)Math.ceil(Math.abs(Math.toRadians(change))*radius/.12));float accepted=from;
        for(int i=1;i<=steps;i++){
            float next=from+change*i/steps;if(!loaded(world,x,y,z,0,0,0,next))break;
            double radians=Math.toRadians(next),cos=Math.cos(radians),sin=Math.sin(radians);boolean blocked=false;
            for(Cluster cluster:clusters){
                AxisAlignedBB sweep=new OrientedBox(cluster.bounds,pivotX,pivotZ,cos,sin).broad.offset(x,y,z).shrink(1e-6);
                Collection<AxisAlignedBB> obstacles=obstacles(world,entity,sweep);if(obstacles.isEmpty())continue;
                for(AxisAlignedBB local:cluster.boxes){OrientedBox box=new OrientedBox(local,pivotX,pivotZ,cos,sin);AxisAlignedBB query=box.broad.offset(x,y,z).shrink(1e-6);
                    for(AxisAlignedBB obstacle:obstacles)if(query.intersects(obstacle)&&box.intersects(obstacle.offset(-x,-y,-z))){blocked=true;break;}
                    if(blocked)break;
                }if(blocked)break;
            }
            if(blocked)break;accepted=next;
        }return accepted;
    }
    private float legacyTurn(World world,net.minecraft.entity.Entity entity,double x,double y,double z,float from,float change) {
        if(Math.abs(change)<1e-6)return from;
        double radius=Math.hypot(bounds.maxX-pivotX,bounds.maxZ-pivotZ);
        int steps=Math.max(1,(int)Math.ceil(Math.abs(Math.toRadians(change))*radius/.12));
        float accepted=from;
        for(int i=1;i<=steps;i++) {
            float next=from+change*i/steps;
            if(!loaded(world,x,y,z,0,0,0,next))break;
            boolean blocked=false;
            for(OrientedBox box:pose(next)) {
                for(AxisAlignedBB obstacle:obstacles(world,entity,box.broad.offset(x,y,z).shrink(1e-6)))
                    if(box.intersects(obstacle.offset(-x,-y,-z))){blocked=true;break;}
                if(blocked)break;
            }
            if(blocked)break;accepted=next;
        }
        return accepted;
    }
    public double clip(World world,net.minecraft.entity.Entity entity,double x,double y,double z,double amount,int axis){return clip(world,entity,x,y,z,amount,axis,0);}
    public double clip(World world,net.minecraft.entity.Entity entity,double x,double y,double z,double amount,int axis,float yaw) {
        if(boxes.size()<=32)return legacyClip(world,entity,x,y,z,amount,axis,yaw);
        if(amount==0)return 0;double result=amount,radians=Math.toRadians(yaw),cos=Math.cos(radians),sin=Math.sin(radians);
        for(Cluster cluster:clusters){
            AxisAlignedBB swept=new OrientedBox(cluster.bounds,pivotX,pivotZ,cos,sin).broad.offset(x,y,z).expand(axis==0?amount:0,axis==1?amount:0,axis==2?amount:0);
            Collection<AxisAlignedBB> obstacles=obstacles(world,entity,swept);if(obstacles.isEmpty())continue;
            for(AxisAlignedBB local:cluster.boxes){OrientedBox box=new OrientedBox(local,pivotX,pivotZ,cos,sin);AxisAlignedBB query=box.broad.offset(x,y,z).expand(axis==0?amount:0,axis==1?amount:0,axis==2?amount:0);
                for(AxisAlignedBB obstacle:obstacles)if(query.intersects(obstacle))result=box.clip(obstacle.offset(-x,-y,-z),result,axis);
            }
        }return result;
    }
    private double legacyClip(World world,net.minecraft.entity.Entity entity,double x,double y,double z,double amount,int axis,float yaw) {
        if(amount==0)return 0;
        double result=amount;
        for(OrientedBox box:pose(yaw)) {
            AxisAlignedBB swept=box.broad.offset(x,y,z).expand(axis==0?amount:0,axis==1?amount:0,axis==2?amount:0);
            for(AxisAlignedBB obstacle:obstacles(world,entity,swept))
                result=box.clip(obstacle.offset(-x,-y,-z),result,axis);
        }
        return result;
    }
    /** Horizontal OBB versus terrain AABB; four separating axes preserve rotated hollow hulls. */
    public static final class OrientedBox {
        public final AxisAlignedBB broad;
        final double cx,cz,hx,hz,cos,sin,minY,maxY;
        OrientedBox(AxisAlignedBB b,double px,double pz,double c,double s) {
            hx=(b.maxX-b.minX)/2;hz=(b.maxZ-b.minZ)/2;cos=c;sin=s;minY=b.minY;maxY=b.maxY;
            double x=(b.minX+b.maxX)/2-px,z=(b.minZ+b.maxZ)/2-pz;
            cx=px+c*x-s*z;cz=pz+s*x+c*z;
            double rx=Math.abs(c)*hx+Math.abs(s)*hz,rz=Math.abs(s)*hx+Math.abs(c)*hz;
            broad=new AxisAlignedBB(cx-rx,minY,cz-rz,cx+rx,maxY,cz+rz);
        }
        private double radius(double x,double z){return hx*Math.abs(cos*x+sin*z)+hz*Math.abs(-sin*x+cos*z);}
        private boolean horizontal(AxisAlignedBB b) {
            double bx=(b.minX+b.maxX)/2,bz=(b.minZ+b.maxZ)/2,bhx=(b.maxX-b.minX)/2,bhz=(b.maxZ-b.minZ)/2;
            for(int n=0;n<4;n++) {
                double ax=n==0?1:n==1?0:n==2?cos:-sin,az=n==0?0:n==1?1:n==2?sin:cos;
                if(Math.abs((cx-bx)*ax+(cz-bz)*az)>=radius(ax,az)+bhx*Math.abs(ax)+bhz*Math.abs(az)-1e-7)return false;
            }
            return true;
        }
        public boolean intersects(AxisAlignedBB b){return maxY>b.minY+1e-7 && minY<b.maxY-1e-7 && horizontal(b);}
        double clip(AxisAlignedBB b,double amount,int axis) {
            if(axis==1) {
                if(!horizontal(b))return amount;
                if(amount>0 && maxY<=b.minY+1e-7)return Math.min(amount,Math.max(0,b.minY-maxY));
                if(amount<0 && minY>=b.maxY-1e-7)return Math.max(amount,Math.min(0,b.maxY-minY));
                return amount;
            }
            if(maxY<=b.minY+1e-7 || minY>=b.maxY-1e-7)return amount;
            double bx=(b.minX+b.maxX)/2,bz=(b.minZ+b.maxZ)/2,bhx=(b.maxX-b.minX)/2,bhz=(b.maxZ-b.minZ)/2;
            double enter=Double.NEGATIVE_INFINITY,exit=Double.POSITIVE_INFINITY;
            for(int n=0;n<4;n++) {
                double ax=n==0?1:n==1?0:n==2?cos:-sin,az=n==0?0:n==1?1:n==2?sin:cos;
                double centre=(cx-bx)*ax+(cz-bz)*az,extent=radius(ax,az)+bhx*Math.abs(ax)+bhz*Math.abs(az);
                double velocity=amount*(axis==0?ax:az);
                if(Math.abs(velocity)<1e-12){if(Math.abs(centre)>=extent-1e-7)return amount;continue;}
                double t1=(-extent-centre)/velocity,t2=(extent-centre)/velocity;
                enter=Math.max(enter,Math.min(t1,t2));exit=Math.min(exit,Math.max(t1,t2));
                if(enter>exit)return amount;
            }
            if(exit<=1e-8 || enter>1 || enter< -1e-6)return amount;
            return amount*Math.max(0,enter-1e-7/Math.max(1e-6,Math.abs(amount)));
        }
    }
}
