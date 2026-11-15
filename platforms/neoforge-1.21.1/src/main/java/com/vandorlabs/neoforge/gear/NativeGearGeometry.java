package com.vandorlabs.neoforge.gear;

import com.google.gson.*;
import com.vandorlabs.gear.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.phys.*;

/** Same authored cuboids and swept rotation plane used by Source; no client classes enter collision. */
public final class NativeGearGeometry {
    private static final Map<String,List<AABB>> MODELS=new java.util.concurrent.ConcurrentHashMap<>();
    public static List<AABB> model(String name,boolean sliced){String key=name+(sliced?"/sliced":"");return MODELS.computeIfAbsent(key,ignored->load(name,sliced?8:1));}
    private static JsonArray elements(String name,int depth){if(depth>16)throw new IllegalArgumentException("Cyclic arm model");String path=name.contains(":")?name.split(":",2)[1]:"block/gear/"+name;String domain=name.contains(":")?name.split(":",2)[0]:"vandorlabs";
        try(InputStream input=NativeGearGeometry.class.getResourceAsStream("/assets/"+domain+"/models/"+path+".json")){if(input==null)throw new IllegalArgumentException("Missing arm geometry "+name);var root=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();return root.has("elements")?root.getAsJsonArray("elements"):root.has("parent")?elements(root.get("parent").getAsString(),depth+1):new JsonArray();}catch(IOException failure){throw new IllegalStateException("Cannot read arm geometry",failure);}}
    private static AABB raw(JsonArray from,JsonArray to){return new AABB(from.get(0).getAsDouble()/16,from.get(1).getAsDouble()/16,from.get(2).getAsDouble()/16,to.get(0).getAsDouble()/16,to.get(1).getAsDouble()/16,to.get(2).getAsDouble()/16);}
    private static List<AABB> load(String name,int slices){List<AABB> result=new ArrayList<>();for(var raw:elements(name,0)){var element=raw.getAsJsonObject();var box=raw(element.getAsJsonArray("from"),element.getAsJsonArray("to"));if(element.has("rotation")){double step=(box.maxY-box.minY)/slices;for(int n=0;n<slices;n++)result.add(rotated(new AABB(box.minX,box.minY+n*step,box.minZ,box.maxX,box.minY+(n+1)*step,box.maxZ),element.getAsJsonObject("rotation")));}else result.add(box);}return List.copyOf(result);}
    private interface Point {Vec3 apply(double x,double y,double z);}
    private static AABB transform(AABB box,Point transform){double lx=Double.POSITIVE_INFINITY,ly=lx,lz=lx,hx=Double.NEGATIVE_INFINITY,hy=hx,hz=hx;for(int n=0;n<8;n++){var p=transform.apply((n&1)==0?box.minX:box.maxX,(n&2)==0?box.minY:box.maxY,(n&4)==0?box.minZ:box.maxZ);lx=Math.min(lx,p.x);ly=Math.min(ly,p.y);lz=Math.min(lz,p.z);hx=Math.max(hx,p.x);hy=Math.max(hy,p.y);hz=Math.max(hz,p.z);}return new AABB(lx,ly,lz,hx,hy,hz);}
    private static AABB rotated(AABB box,JsonObject rotation){var o=rotation.getAsJsonArray("origin");double ox=o.get(0).getAsDouble()/16,oy=o.get(1).getAsDouble()/16,oz=o.get(2).getAsDouble()/16,a=Math.toRadians(rotation.get("angle").getAsDouble()),c=Math.cos(a),s=Math.sin(a);String axis=rotation.get("axis").getAsString();return transform(box,(px,py,pz)->{double x=px-ox,y=py-oy,z=pz-oz;return switch(axis){case "x"->new Vec3(x+ox,y*c-z*s+oy,y*s+z*c+oz);case "y"->new Vec3(x*c+z*s+ox,y+oy,-x*s+z*c+oz);default->new Vec3(x*c-y*s+ox,x*s+y*c+oy,z+oz);};});}
    public static AABB yaw(AABB box,int turns){return transform(box,(x,y,z)->{double xx=x-.5,zz=z-.5;return switch(Math.floorMod(turns,4)){case 1->new Vec3(.5-zz,y,.5+xx);case 2->new Vec3(.5-xx,y,.5-zz);case 3->new Vec3(.5+zz,y,.5-xx);default->new Vec3(x,y,z);};});}
    private static AABB stretch(AABB box,double height){double factor=(height+.25)/1.25;return new AABB(box.minX,GearMotion.PIVOT_Y+(box.minY-GearMotion.PIVOT_Y)*factor,box.minZ,box.maxX,GearMotion.PIVOT_Y+(box.maxY-GearMotion.PIVOT_Y)*factor,box.maxZ);}
    private static AABB posed(AABB box,GearMotion.Pose pose,boolean foot,int sx,int sz){return transform(box,(x,y,z)->{var p=foot?pose.transformFootVertex(.5+(x-.5)*sx,y,.5+(z-.5)*sz):pose.transformRigidVertex(.5+(x-.5)*sx,y,.5+(z-.5)*sz);return new Vec3(p.x,p.y,p.z);});}
    private static AABB upper(AABB box,GearMotion.Pose pose,int sx,int sz,double dx,double dz){return transform(box,(x,y,z)->{var p=pose.transformUpperLinkVertex(.5+(x-.5)*sx+dx,y,.5+(z-.5)*sz+dz);return new Vec3(p.x,p.y,p.z);});}
    public static List<AABB> mechanismBoxes(NativeGearEntity owner,double progress){return boxes(owner,progress,false);}
    public static List<AABB> boxes(NativeGearEntity owner,double progress){return boxes(owner,progress,true);}
    private static List<AABB> boxes(NativeGearEntity owner,double progress,boolean payload){var assembly=owner.assembly();if(assembly==null)return List.of();var pose=owner.pose(progress);List<AABB> result=new ArrayList<>();int width=owner.definition().size;boolean swing=owner.definition().kind==NativeGearDefinition.Kind.ROTATING,fixed=owner.definition().kind==NativeGearDefinition.Kind.MOUNT;
        for(BlockPos column:owner.columns()){var local=NativeGearOrientation.inverse(owner.mount(),column);result.add(new AABB(0,14/16D,0,1,1,1).move(local));result.add(new AABB(.5-5/16D*width,fixed?0:6/16D,.5-5/16D*width,.5+5/16D*width,14/16D,.5+5/16D*width).move(local));
            if(swing){
                int sx=pose.direction().axisX()!=0?width:1,sz=pose.direction().axisZ()!=0?width:1;double height=assembly.columnHeight()-owner.socketOffset();
                if(owner.settings().hingeMode()==ArmHinge.Mode.DOUBLE||owner.settings().kinkDirection()!=owner.settings().direction()){
                    for(AABB box:model("swing_linkage_upper_body",true))result.add(upper(stretch(yaw(box,owner.settings().kinkDirection()),height),pose,sx,sz,0,0).move(local));
                    var fold=GearMotion.Direction.values()[owner.settings().direction()];var kink=GearMotion.Direction.values()[owner.settings().kinkDirection()];
                    for(AABB box:model("swing_linkage_elbow",true))result.add(upper(stretch(yaw(box,owner.settings().direction()),height),pose,sx,sz,ArmHinge.kinkOffsetX(fold,kink,width),ArmHinge.kinkOffsetZ(fold,kink,width)).move(local));
                    for(AABB box:model("swing_linkage_lower",true))result.add(posed(stretch(yaw(box,owner.settings().kinkDirection()),height),pose,false,sx,sz).move(local));
                }else{
                    if(owner.settings().middleHinge())for(AABB box:model("swing_linkage_upper",true))result.add(posed(stretch(yaw(box,owner.settings().direction()),height),owner.poseAtAngle(0),false,sx,sz).move(local));
                    for(AABB box:model(owner.settings().middleHinge()?"swing_linkage_lower":"swing_linkage",true))result.add(posed(stretch(yaw(box,owner.settings().direction()),height),pose,false,sx,sz).move(local));
                }
            }
            else result.add(posed(new AABB(5/16D,pose.rodBottomY()+owner.socketOffset(),5/16D,11/16D,pose.rodTopY(),11/16D),pose,false,width,width).move(local));
            AABB socket=assembly.kind()==GearTopology.FootKind.NONE&&!swing?new AABB(4.25/16D,3/16D,4.25/16D,11.75/16D,5/16D,11.75/16D):new AABB(.25,3/16D,.3125,.75,7/16D,.6875);if(assembly.kind()!=GearTopology.FootKind.NONE||!fixed)result.add(posed(socket.move(0,-assembly.columnHeight()+owner.socketOffset()-(assembly.kind()==GearTopology.FootKind.TRACK?1/64D:0),0),pose,true,width,width).move(local));
        }
        if(assembly.kind()==GearTopology.FootKind.TRACK){var tracks=owner.trackDescriptor();if(tracks==null)throw new IllegalStateException("Track descriptor unavailable");for(var foot:tracks.feet()){var cell=foot.cell();for(var box:com.vandorlabs.neoforge.gear.track.NativeTrackGeometry.boxes(tracks.style().ordinal(),owner.settings().footFacing(),foot.appearance()))result.add(posed(box.move(cell.x,cell.y,cell.z),pose,true,1,1));}}
        else for(var foot:assembly.feet())if(assembly.kind()!=GearTopology.FootKind.NONE){int turns=Math.floorMod(owner.settings().footFacing()+2,4);if(assembly.kind()==GearTopology.FootKind.WHEEL){if(!owner.wheelRoot(foot))continue;AABB wheel=model(owner.definition().size==2?"wheel_large":"wheel",false).stream().reduce(AABB::minmax).orElseThrow();result.add(posed(yaw(wheel,turns).move(foot.x,foot.y,foot.z),pose,true,1,1));}else if(assembly.kind()==GearTopology.FootKind.PLATE)result.add(posed(new AABB(0,0,0,1,3/16D,1).move(foot.x,foot.y,foot.z),pose,true,1,1));else {result.add(posed(yaw(new AABB(.25,0,0,.75,5/16D,1),turns).move(foot.x,foot.y,foot.z),pose,true,1,1));result.add(posed(yaw(new AABB(0,3/16D,.375,1,5/16D,.625),turns).move(foot.x,foot.y,foot.z),pose,true,1,1));}}
        var worldBoxes=new ArrayList<AABB>(result.stream().map(box->{var world=box.move(owner.getBlockPos());return owner.mount()==Direction.DOWN?world:NativeGearOrientation.box(owner.mount(),world.move(-owner.getBlockPos().getX(),-owner.getBlockPos().getY(),-owner.getBlockPos().getZ())).move(owner.getBlockPos());}).toList());if(payload&&owner.payload()!=null)worldBoxes.addAll(owner.payload().boxes(owner,progress));else if(payload&&owner.payloadView()!=null)worldBoxes.addAll(viewBoxes(owner,progress));return List.copyOf(worldBoxes);
    }
    private static List<AABB> viewBoxes(NativeGearEntity owner,double progress){var view=owner.payloadView();var transform=NativeGearPayload.relativeTransform(owner,progress,view.reference());var root=owner.getBlockPos();var result=new ArrayList<AABB>();for(var cell:view.cells())for(var box:cell.boxes()){double lx=Double.POSITIVE_INFINITY,ly=lx,lz=lx,hx=Double.NEGATIVE_INFINITY,hy=hx,hz=hx;for(int n=0;n<8;n++){var point=transform.point(cell.source().getX()+((n&1)==0?box.minX:box.maxX),cell.source().getY()+((n&2)==0?box.minY:box.maxY),cell.source().getZ()+((n&4)==0?box.minZ:box.maxZ));lx=Math.min(lx,point.x);ly=Math.min(ly,point.y);lz=Math.min(lz,point.z);hx=Math.max(hx,point.x);hy=Math.max(hy,point.y);hz=Math.max(hz,point.z);}result.add(new AABB(lx+root.getX(),ly+root.getY(),lz+root.getZ(),hx+root.getX(),hy+root.getY(),hz+root.getZ()));}return result;}
    public static AABB swept(NativeGearEntity owner,AABB before,AABB after,double a,double b){var union=before.minmax(after);if(owner.definition().kind!=NativeGearDefinition.Kind.ROTATING)return union.deflate(.0001);double padding=owner.settings().hingeMode()==ArmHinge.Mode.DOUBLE?doublePadding(owner,union,a,b):(owner.assembly().columnHeight()+18)*(1-Math.cos(Math.toRadians(Math.abs(owner.pose(b).angleDegrees()-owner.pose(a).angleDegrees())/2)));var axis=NativeGearOrientation.vector(owner.mount(),owner.pose(a).direction().axisX(),0,owner.pose(a).direction().axisZ());return union.inflate(Math.abs(axis.x)>.5?0:padding,Math.abs(axis.y)>.5?0:padding,Math.abs(axis.z)>.5?0:padding).deflate(.0001);}
    private static double doublePadding(NativeGearEntity owner,AABB box,double a,double b){
        var pivot=NativeGearOrientation.point(owner.mount(),GearMotion.PIVOT_X,GearMotion.PIVOT_Y,GearMotion.PIVOT_Z).add(Vec3.atLowerCornerOf(owner.getBlockPos()));
        var axis=NativeGearOrientation.vector(owner.mount(),owner.pose(a).direction().axisX(),0,owner.pose(a).direction().axisZ());
        double x=Math.max(Math.abs(box.minX-pivot.x),Math.abs(box.maxX-pivot.x)),y=Math.max(Math.abs(box.minY-pivot.y),Math.abs(box.maxY-pivot.y)),z=Math.max(Math.abs(box.minZ-pivot.z),Math.abs(box.maxZ-pivot.z));
        double radius=axis.x!=0?Math.hypot(y,z):axis.y!=0?Math.hypot(x,z):Math.hypot(x,y),radians=Math.toRadians(Math.abs(owner.pose(b).angleDegrees()-owner.pose(a).angleDegrees()));
        return (radius+1.25*(owner.assembly().columnHeight()+2))*radians*radians/8;
    }
    private NativeGearGeometry(){}
}
