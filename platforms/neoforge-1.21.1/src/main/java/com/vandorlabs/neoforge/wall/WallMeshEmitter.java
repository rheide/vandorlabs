package com.vandorlabs.neoforge.wall;

import com.vandorlabs.client.PortholeGeometryCache;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.render.ControlMount.Point;
import com.vandorlabs.render.FlatPanelMesh;
import com.vandorlabs.render.FlatPanelPlacement;
import com.vandorlabs.render.FlatWallMesh;
import com.vandorlabs.render.FlatWallTopology;
import com.vandorlabs.render.PanelTextureMapping;
import com.vandorlabs.render.PanelMeshTransform;
import com.vandorlabs.render.PortholeSlice;
import com.vandorlabs.render.ScreenHousingMesh;
import com.vandorlabs.render.WallGroupPolicy;
import com.vandorlabs.tiles.GlassTextures;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/** Platform material selection and transforms around shared authored panel meshes. */
final class WallMeshEmitter {
    private record Material(ResourceLocation texture,double usedU,double usedV,String logical){double aspect(){return usedU/usedV;}}
    private record Raw(Material material,int color,double[][] vertices,float opacity,boolean depthWrite){Raw(Material material,int color,double[][] vertices){this(material,color,vertices,(color>>>24)/255F,true);}}
    private static Material finish(int choice){var entry=ModernFinishCatalog.entry(choice);String logical=entry.canonical()>=0&&entry.canonical()<com.vandorlabs.tiles.HousingFinishCatalog.legacyCount()?com.vandorlabs.tiles.HousingFinishCatalog.texture(entry.canonical()):entry.texture();return new Material(ResourceLocation.parse(entry.texture()),entry.u(),entry.v(),logical);}
    private static Material fixed(String texture){return new Material(WallModel.texture(texture),1,1,texture.replace("vandorlabs:blocks/",""));}
    private static PanelTextureMapping.Layout layout(WallGroupPolicy.Layout value){return new PanelTextureMapping.Layout(value.column,value.row,value.columns,value.rows);}
    static WallRenderSnapshot build(WallClientSnapshots.Source own,WallClientSnapshots.Group opening,WallClientSnapshots.Group main,Function<BlockPos,WallClientSnapshots.Source> sample){
        var settings=own.settings();var pose=own.pose();Material wall=finish(settings.main),metal=own.profile().porthole()&&settings.side>=0?finish(settings.side):fixed("vandorlabs:blocks/programmable_glass/metal_side");
        PortholeSlice slice=null;if(own.profile().porthole()){var l=opening.layout();int border=pose.full()?1:0;var outline=PortholeGeometryCache.outline(l.columns,l.rows,settings.appearance.shape,border);slice=PortholeGeometryCache.slice(l.columns,l.rows,settings.appearance.shape,border,l.column,l.row,outline);}
        if(FlatWallTopology.bar(pose.corner()))return bar(own,opening,wall,metal,slice,sample);
        List<Raw> opaque=opaque(own,wall,metal,slice,sample),glass=glass(own,opening,slice);
        if(settings.layout!=0)opaque=map(opaque,wall,layout(main.layout()),0,0,settings.layout);
        double width=pose.corner()!=null&&own.profile().porthole()?(pose.corner().right()-pose.corner().left())/16:1,left=pose.corner()!=null&&own.profile().porthole()?pose.corner().left():0;
        List<WallRenderSnapshot.Face> out=new ArrayList<>(),panes=new ArrayList<>();
        for(var raw:opaque)out.add(orient(raw,pose,left,width,own.profile().porthole()||pose.corner()==null));for(var raw:glass)panes.add(orient(raw,pose,left,width,own.profile().porthole()||pose.corner()==null));
        if(pose.corner()!=null&&own.profile().porthole()){
            double near=FlatPanelPlacement.near(pose.depth());if(pose.corner().frontArm!=null)arm(out,wall,metal,pose,pose.corner().frontArm,0,pose.corner().frontArm+4,near);
            if(pose.corner().backArm!=null)arm(out,wall,metal,pose,pose.corner().backArm,near+4,pose.corner().backArm+4,16);
        }
        return new WallRenderSnapshot(out,panes);
    }
    private static List<Raw> opaque(WallClientSnapshots.Source own,Material wall,Material metal,PortholeSlice opening,Function<BlockPos,WallClientSnapshots.Source> sample){
        List<Raw> result=new ArrayList<>();var pose=own.pose();if(pose.corner()!=null&&!own.profile().porthole()){
            for(var quad:FlatWallMesh.build(FlatPanelPlacement.near(pose.depth()),pose.corner()).quads)result.add(raw(quad.metal?metal:wall,-1,quad.reverse,quad.vertices,false));
        }else{
            Direction right=pose.facing().getClockWise();BlockPos pos=own.entity().getBlockPos();
            for(var quad:FlatPanelMesh.opaque(opening,pose.full()?16:4,compatible(own,sample.apply(pos.below())),compatible(own,sample.apply(pos.above())),compatible(own,sample.apply(pos.relative(right.getOpposite()))),compatible(own,sample.apply(pos.relative(right)))))result.add(raw(quad.side?metal:wall,-1,quad.reverse,quad.vertices,false));
        }result.removeIf(java.util.Objects::isNull);return result;
    }
    private static boolean compatible(WallClientSnapshots.Source own,WallClientSnapshots.Source other){return other!=null&&WallGroupPolicy.aperture(own.surface(),other.surface());}
    private static List<Raw> glass(WallClientSnapshots.Source own,WallClientSnapshots.Group opening,PortholeSlice slice){
        if(slice==null)return List.of();var a=own.settings().appearance;Material material=a.material<0?fixed(GlassTextures.texture(a.shade,1,a.tint)):finish(a.material);
        var group=a.material<0?new PanelTextureMapping.Layout(0,0,1,1):layout(opening.layout());int extra=own.pose().full()?1:0;double bx=a.material<0?0:(2+extra)/16D,by=a.material<0?0:(4+extra)/16D;int mode=a.material<0?0:a.textureLayout();
        float opacity=com.vandorlabs.persistence.WallAppearance.opacity(a.material,a.tint);int alpha=Math.round(255*opacity);List<Raw> source=new ArrayList<>();for(var quad:FlatPanelMesh.glass(slice)){Raw value=raw(material,alpha<<24|0xFFFFFF,quad.reverse,quad.vertices,true);if(value!=null)source.add(new Raw(material,value.color,value.vertices,opacity,a.material>=0&&opacity==1));}
        source.removeIf(java.util.Objects::isNull);return map(source,material,group,bx,by,mode);
    }
    private static Raw raw(Material material,int color,boolean reverse,double[][] source,boolean normalized){
        double[] a=source[0],b=source[1],c=source[2],d=source[3];double ux=c[0]-a[0],uy=c[1]-a[1],uz=c[2]-a[2],vx=d[0]-b[0],vy=d[1]-b[1],vz=d[2]-b[2];double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx,len=Math.sqrt(nx*nx+ny*ny+nz*nz),sign=reverse?-1:1;
        if(len<1E-10)return null;double[][] result=new double[4][8];for(int i=0;i<4;i++){int index=reverse?(4-i)&3:i;var point=source[index];for(int j=0;j<5;j++)result[i][j]=(float)(point[j]/(j>=3&&!normalized?16:1));result[i][5]=(float)(sign*nx/len);result[i][6]=(float)(sign*ny/len);result[i][7]=(float)(sign*nz/len);}return new Raw(material,color,result);
    }
    private static List<Raw> map(List<Raw> source,Material selected,PanelTextureMapping.Layout layout,double bx,double by,int mode){
        List<Raw> result=new ArrayList<>();for(var face:source){if(!face.material.logical.equals(selected.logical)){result.add(face);continue;}
            if(mode==0){double[][] quad=new double[4][];for(int i=0;i<4;i++){quad[i]=face.vertices[i].clone();quad[i][3]=PanelTextureMapping.stretchU(quad[i][3],layout,bx);quad[i][4]=PanelTextureMapping.stretchV(quad[i][4],layout,by);}result.add(new Raw(face.material,face.color,quad,face.opacity,face.depthWrite));}
            else{var mapped=PanelTextureMapping.map(face.vertices,layout,bx,by,mode,selected.aspect());if(mapped==null)result.add(face);else for(var quad:mapped)result.add(new Raw(face.material,face.color,quad,face.opacity,face.depthWrite));}
        }return result;
    }
    private static WallRenderSnapshot.Face orient(Raw raw,WallGeometry.Pose pose,double left,double width,boolean centered){
        List<WallRenderSnapshot.Vertex> vertices=new ArrayList<>();for(var v:raw.vertices){double x=left+v[0]*width,z=pose.full()?(v[2]-6)*4:v[2]+(centered?FlatPanelPlacement.near(pose.depth())-6:0);Point p=FlatPanelPlacement.rotate(new Point(x/16,v[1]/16,z/16),pose.facing().get3DDataValue());vertices.add(new WallRenderSnapshot.Vertex(p.x,p.y,p.z,v[3]*raw.material.usedU,v[4]*raw.material.usedV));}
        double nx=raw.vertices[0][5]/width,ny=raw.vertices[0][6],nz=raw.vertices[0][7]/(pose.full()?4:1);Point a=FlatPanelPlacement.rotate(new Point(nx,ny,nz),pose.facing().get3DDataValue()),b=FlatPanelPlacement.rotate(new Point(0,0,0),pose.facing().get3DDataValue());a=a.subtract(b);double length=Math.sqrt(a.dot(a));return new WallRenderSnapshot.Face(vertices,raw.material.texture,raw.color,(float)(a.x/length),(float)(a.y/length),(float)(a.z/length),raw.opacity,raw.depthWrite);
    }
    private static void arm(List<WallRenderSnapshot.Face> out,Material wall,Material metal,WallGeometry.Pose pose,double x0,double z0,double x1,double z1){
        var mesh=ScreenHousingMesh.box(x0,0,z0,x1,16,z1).sideLayout(false);for(int face=0;face<mesh.quads.length;face++){if(face==4)continue;var q=mesh.quads[face];double[][] points=new double[4][5];for(int i=0;i<4;i++){var p=q.vertices[i];points[i]=new double[]{p.x,p.y,p.z,p.u,p.v};}Raw value=raw(face==2||face==3?wall:metal,-1,true,points,false);if(value!=null)out.add(orient(value,new WallGeometry.Pose(pose.facing(),pose.depth(),false,new FlatWallTopology.Corner(null,null,null,null,false,false)),0,1,false));}
    }
    private static float[] packed(Raw face){float[] result=new float[32];for(int i=0;i<4;i++)for(int j=0;j<8;j++)result[i*8+j]=(float)face.vertices[i][j];return result;}
    private static List<Raw> unpack(Material material,int color,float opacity,boolean depthWrite,float[] data){List<Raw> result=new ArrayList<>();for(int offset=0;offset<data.length;offset+=32){double[][] vertices=new double[4][8];for(int i=0;i<4;i++)for(int j=0;j<8;j++)vertices[i][j]=data[offset+i*8+j];result.add(new Raw(material,color,vertices,opacity,depthWrite));}return result;}
    private static void part(List<WallRenderSnapshot.Face> out,List<Raw> source,WallGeometry.Pose pose,int turns,double depth,double left,double width,double barNear,boolean outside){
        for(var raw:source){float[] transformed=PanelMeshTransform.transform(packed(raw),turns,depth,left,width),clipped=PanelMeshTransform.box(transformed,barNear,-1,-1,barNear+4,17,17,outside);for(var face:unpack(raw.material,raw.color,raw.opacity,raw.depthWrite,clipped))out.add(orient(face,pose,0,1,false));}
    }
    private static WallRenderSnapshot bar(WallClientSnapshots.Source own,WallClientSnapshots.Group opening,Material wall,Material metal,PortholeSlice slice,Function<BlockPos,WallClientSnapshots.Source> sample){
        var pose=own.pose();var c=pose.corner();double barNear=c.frontArm,near=FlatPanelPlacement.near(pose.depth());List<Raw> base=opaque(own,wall,metal,slice,sample),pane=glass(own,opening,slice);
        double depth=own.profile().porthole()?near-6:0,left=own.profile().porthole()?c.left():0,width=own.profile().porthole()?(c.right()-c.left())/16:1;
        List<Raw> across=new ArrayList<>();PortholeSlice single=null;
        if(own.profile().porthole()){var outline=PortholeGeometryCache.outline(1,1,own.settings().appearance.shape,0);single=PortholeGeometryCache.slice(1,1,own.settings().appearance.shape,0,0,0,outline);}
        for(var q:FlatPanelMesh.bar(single))across.add(raw(q.side?metal:wall,-1,q.reverse,q.vertices,false));
        across.removeIf(java.util.Objects::isNull);List<WallRenderSnapshot.Face> opaque=new ArrayList<>(),glass=new ArrayList<>();
        part(opaque,base,pose,0,depth,left,width,barNear,true);part(glass,pane,pose,0,depth,left,width,barNear,true);
        part(opaque,across,pose,3,barNear-6,0,1,barNear,false);part(glass,glass(own,opening,single),pose,3,barNear-6,0,1,barNear,false);
        double cut=c.straightLeft?barNear:barNear+4;
        for(var raw:base){float[] transformed=PanelMeshTransform.transform(packed(raw),0,depth,left,width);for(var cap:unpack(metal,-1,1,true,PanelMeshTransform.end(transformed,cut,near,c.straightLeft)))opaque.add(orient(cap,pose,0,1,false));}
        return new WallRenderSnapshot(opaque,glass);
    }
}
