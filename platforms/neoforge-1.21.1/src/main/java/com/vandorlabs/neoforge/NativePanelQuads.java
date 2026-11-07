package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.shape.NativePanelLayout;
import com.vandorlabs.render.*;
import java.util.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;

/** Native records from the current shared polygon, vertex, frame and repeated-UV kernels. */
final class NativePanelQuads {
    record Material(ProgrammableModel.MaterialSnapshot surface,List<ProgrammableModel.MaterialSnapshot> edges,boolean authoredOutline){
        Material(ProgrammableModel.MaterialSnapshot surface){this(surface,List.of());}
        Material(ProgrammableModel.MaterialSnapshot surface,List<ProgrammableModel.MaterialSnapshot> edges){this(surface,edges,false);}
        Material{edges=List.copyOf(edges);if(!edges.isEmpty()&&edges.size()!=4)throw new IllegalArgumentException("Four directional frame edges required");}
        Material mapped(double[][] points){if(edges.isEmpty())return this;int edge=FrameEdgeMapping.edge(points);FrameEdgeMapping.map(edge,points);return new Material(edges.get(edge));}
    }
    private static final class Context {
        DiagonalPortholeProjection projection;
        final Direction facing;final Material main;final int mode;final NativePanelLayout layout;
        final boolean raw;final double borderX,borderY,zOffset,imageAspect,zScale;final int color;
        final List<BakedQuad> result;final boolean capture;private float[] captured;private int capturedUsed;private final List<Material> capturedMaterials;final float[] source=new float[32],mapped=new float[32];int used,mappedUsed;Material current;
        Context(Direction facing,Material main,int mode,NativePanelLayout layout){this(facing,main,mode,layout,false,0,0,0,1,0);}
        Context(Direction facing,Material main,int mode,NativePanelLayout layout,boolean raw,double borderX,double borderY,double zOffset,float opacity,double imageAspect){this(facing,main,mode,layout,raw,borderX,borderY,zOffset,opacity,imageAspect,false);}
        Context(Direction facing,Material main,int mode,NativePanelLayout layout,boolean raw,double borderX,double borderY,double zOffset,float opacity,double imageAspect,boolean capture){this(facing,main,mode,layout,raw,borderX,borderY,zOffset,opacity,imageAspect,capture,1);}
        Context(Direction facing,Material main,int mode,NativePanelLayout layout,boolean raw,double borderX,double borderY,double zOffset,float opacity,double imageAspect,boolean capture,double zScale){this.zScale=zScale;this.capture=capture;result=capture?null:new ArrayList<>();captured=capture?new float[256]:null;capturedMaterials=capture?new ArrayList<>():List.of();this.facing=facing;this.main=main;this.mode=mode;this.layout=Objects.requireNonNull(layout);this.raw=raw;this.borderX=borderX;this.borderY=borderY;this.zOffset=zOffset;this.imageAspect=imageAspect;color=((int)(opacity*255)<<24)|0xFFFFFF;}
        void source(Material material,double x,double y,double z,double u,double v,float nx,float ny,float nz){
            put(source,used++,x,y,z,u,v,nx,ny,nz);if(used!=4)return;used=0;
            var texture=main.surface;var sprite=texture.sprite();
            if(mode==0||material.surface.sprite()!=sprite){
                if(raw){double width=layout.columns()-2*borderX,height=layout.rows()-2*borderY;for(int i=0;i<4;i++){int at=i*8;double mappedU=source[at+3],mappedV=source[at+4];if(layout.flipU())mappedU=1-mappedU;if(layout.flipV())mappedV=1-mappedV;source[at+3]=NativeHousingQuads.atlas(texture,PanelTextureMapping.stretch(mappedU,layout.column(),borderX,width)*16,true);source[at+4]=NativeHousingQuads.atlas(texture,PanelTextureMapping.stretch(mappedV,layout.rows()-1-layout.row(),borderY,height)*16,false);}}
                append(source,material);return;
            }
            float u0=sprite.getU0(),v0=sprite.getV0(),u1=u0+(sprite.getU1()-u0)*texture.usedU(),v1=v0+(sprite.getV1()-v0)*texture.usedV();
            PanelTextureMesh.map(MAPPED,this,material,source,u0,u1,v0,v1,imageAspect>0?imageAspect:texture.usedU()/texture.usedV(),layout.column(),layout.row(),layout.columns(),layout.rows(),layout.flipU(),layout.flipV(),raw,borderX,borderY,mode);
        }
        void mapped(Material material,double x,double y,double z,double u,double v,float nx,float ny,float nz){put(mapped,mappedUsed++,x,y,z,u,v,nx,ny,nz);if(mappedUsed==4){mappedUsed=0;append(mapped,material);}}
        void append(float[] points,Material material){
            if(capture){if(capturedUsed+32>captured.length)captured=Arrays.copyOf(captured,Math.max(captured.length*2,capturedUsed+32));System.arraycopy(points,0,captured,capturedUsed,32);capturedUsed+=32;capturedMaterials.add(material);return;}
            int[] data=new int[32];int turns=facing==Direction.WEST?1:facing==Direction.SOUTH?2:facing==Direction.EAST?3:0;
            int a=(byte)(int)(points[5]*127),b=(byte)(int)(points[6]*127),c=(byte)(int)(points[7]*127),nx=turns==1?c:turns==2?-a:turns==3?-c:a,nz=turns==1?-a:turns==2?-c:turns==3?a:c,normal=(nx&255)|((b&255)<<8)|((nz&255)<<16);
            Direction direction=direction(nx/127F,b/127F,nz/127F);
            for(int v=0;v<4;v++){int at=v*8;double x=points[at]/16D,z=(points[at+2]*zScale+zOffset)/16D;data[at]=Float.floatToRawIntBits((float)(turns==1?z:turns==2?1-x:turns==3?1-z:x));data[at+1]=Float.floatToRawIntBits(points[at+1]/16);data[at+2]=Float.floatToRawIntBits((float)(turns==1?1-x:turns==2?1-z:turns==3?x:z));data[at+3]=color;data[at+4]=Float.floatToRawIntBits(points[at+3]);data[at+5]=Float.floatToRawIntBits(points[at+4]);data[at+7]=normal;}
            result.add(new BakedQuad(data,-1,direction,material.surface.sprite(),false,false));int[] back=new int[32];for(int v=0;v<4;v++)System.arraycopy(data,((4-v)&3)*8,back,v*8,8);result.add(new BakedQuad(back,-1,direction,material.surface.sprite(),false,false));
        }
        Mesh captured(){if(!capture||used!=0||mappedUsed!=0)throw new IllegalStateException("Incomplete captured panel quad");return new Mesh(Arrays.copyOf(captured,capturedUsed),List.copyOf(capturedMaterials));}
        List<BakedQuad> finish(){if(used!=0||mappedUsed!=0)throw new IllegalStateException("Incomplete native panel quad");return List.copyOf(result);}
    }
    private static void put(float[] data,int vertex,double x,double y,double z,double u,double v,float nx,float ny,float nz){int at=vertex*8;data[at]=(float)x;data[at+1]=(float)y;data[at+2]=(float)z;data[at+3]=(float)u;data[at+4]=(float)v;data[at+5]=nx;data[at+6]=ny;data[at+7]=nz;}
    private static Direction direction(float x,float y,float z){Direction result=Direction.NORTH;float best=Float.MIN_VALUE;for(Direction direction:Direction.values()){float score=x*direction.getStepX()+y*direction.getStepY()+z*direction.getStepZ();if(score>best){best=score;result=direction;}}return result;}
    private static final PanelVertexEmitter.RawSink<Context> RAW=(context,x,y,z,u,v,nx,ny,nz)->context.source(context.current,x,y,z,u,v,nx,ny,nz);
    private static final PanelVertexEmitter.PixelSink<Context,Material> PIXEL=(context,material,x,y,z,u,v,nx,ny,nz)->context.source(material,x,y,z,NativeHousingQuads.atlas(material.surface,u,true),NativeHousingQuads.atlas(material.surface,v,false),nx,ny,nz);
    private static final PanelTextureMesh.Sink<Context,Material> MAPPED=new PanelTextureMesh.Sink<>(){
        public void atlas(Context context,Material material,double x,double y,double z,double u,double v,float nx,float ny,float nz){context.mapped(material,x,y,z,u,v,nx,ny,nz);}
        public void pixel(Context context,Material material,double x,double y,double z,double u,double v,float nx,float ny,float nz){context.mapped(material,x,y,z,NativeHousingQuads.atlas(context.main.surface,u,true),NativeHousingQuads.atlas(context.main.surface,v,false),nx,ny,nz);}
    };
    private static final PanelMeshEmitter.Sink<Context,Material> EMITTER=new PanelMeshEmitter.Sink<>(){
        public void quad(Context context,Material material,boolean reverse,double[] a,double[] b,double[] c,double[] d){
            material=material.mapped(new double[][]{a,b,c,d});context.current=material;var texture=material.surface;
            PanelVertexEmitter.raw(RAW,context,reverse,a[0],a[1],a[2],NativeHousingQuads.atlas(texture,a[3],true),NativeHousingQuads.atlas(texture,a[4],false),b[0],b[1],b[2],NativeHousingQuads.atlas(texture,b[3],true),NativeHousingQuads.atlas(texture,b[4],false),c[0],c[1],c[2],NativeHousingQuads.atlas(texture,c[3],true),NativeHousingQuads.atlas(texture,c[4],false),d[0],d[1],d[2],NativeHousingQuads.atlas(texture,d[3],true),NativeHousingQuads.atlas(texture,d[4],false));
        }
        public void normal(Context context,Material material,int sign,double halfOffset,double[] clip,double[] a,double[] b,double[] c,double[] d){material=material.mapped(new double[][]{a,b,c,d});PanelVertexEmitter.normal(PIXEL,context,material,sign,halfOffset,clip,a,b,c,d);}
    };
    static List<BakedQuad> flat(Direction facing,double near,PanelCornerGeometry.FlatCorner corner,Material wall,Material metal,int mode,NativePanelLayout group){Context context=new Context(facing,wall,mode,group);PanelMeshEmitter.flat(EMITTER,context,wall,metal,near,corner);return context.finish();}
    static List<BakedQuad> diagonal(Direction facing,boolean inverted,PanelCornerGeometry.Corner corner,double span,int fill,double low,double high,double halfOffset,double lower,double upper,boolean hideLower,boolean hideUpper,double[] clip,Material wall,Material metal,int mode,NativePanelLayout group){Context context=new Context(facing,wall,mode,group);PanelMeshEmitter.diagonal(EMITTER,context,wall,metal,inverted,corner,span,fill,low,high,halfOffset,lower,upper,hideLower,hideUpper,clip);return context.finish();}
    private static Mesh diagonalSource(com.vandorlabs.neoforge.shape.NativeDiagonalWallGeometry.Pose pose,Material wall,Material outer){
        boolean half=pose.mode()==2;var join=pose.join();Context capture=new Context(pose.facing(),wall,0,NativePanelLayout.UNIT,false,0,0,0,1,0,true);
        PanelMeshEmitter.diagonal(EMITTER,capture,wall,outer,pose.inverted(),join==null?null:join.corner(),pose.mode()==1?16:8,pose.fill(),half&&pose.inverted()?-8:0,half&&pose.inverted()?8:16,half?(pose.inverted()?8:0):Double.NaN,pose.lowerEnd()*16,pose.upperEnd()*16,pose.hideLower(),pose.hideUpper(),pose.clip().scaled(16));
        return capture.captured();
    }
    static List<BakedQuad> diagonalArms(com.vandorlabs.neoforge.shape.NativeDiagonalWallGeometry.Pose pose,Material wall,Material outer,int mode,NativePanelLayout group){
        Mesh source=diagonalSource(pose,wall,outer);Context target=new Context(pose.facing(),wall,mode,group);
        for(int q=0;q<source.vertices.length;q+=32)if(PanelMeshTransform.diagonalArm(source.vertices,q,pose.mode(),pose.inverted(),pose.lowerEnd(),pose.upperEnd())){
            Material material=source.materials.get(q/32);for(int v=0;v<4;v++){int p=q+v*8;target.source(material,source.vertices[p],source.vertices[p+1],source.vertices[p+2],source.vertices[p+3],source.vertices[p+4],source.vertices[p+5],source.vertices[p+6],source.vertices[p+7]);}
        }
        return target.finish();
    }
    private static float[] diagonalGlassSource(com.vandorlabs.neoforge.shape.NativeDiagonalWallGeometry.Pose pose,Material glass,int hideMask){
        if((hideMask&~63)!=0)throw new IllegalArgumentException("Glass boundary mask");float[] source=diagonalSource(pose,glass,glass).vertices;
        if((hideMask&1)!=0)source=PanelMeshTransform.withoutPlane(source,0,0);if((hideMask&2)!=0)source=PanelMeshTransform.withoutPlane(source,0,16);
        if((hideMask&4)!=0)source=PanelMeshTransform.withoutPlane(source,2,0);if((hideMask&8)!=0)source=PanelMeshTransform.withoutPlane(source,2,16);return source;
    }
    static List<BakedQuad> diagonalGlassBody(com.vandorlabs.neoforge.shape.NativeDiagonalWallGeometry.Pose pose,Material glass,int mode,NativePanelLayout group,int hideMask){return diagonalGlassBody(pose,glass,mode,group,hideMask,1,0);}
    static List<BakedQuad> diagonalGlassBody(com.vandorlabs.neoforge.shape.NativeDiagonalWallGeometry.Pose pose,Material glass,int mode,NativePanelLayout group,int hideMask,float opacity,double aspect){
        float[] source=diagonalGlassSource(pose,glass,hideMask);Context target=new Context(pose.facing(),glass,mode,group,false,0,0,0,opacity,aspect);
        for(int p=0;p<source.length;p+=8)target.source(glass,source[p],source[p+1],source[p+2],source[p+3],source[p+4],source[p+5],source[p+6],source[p+7]);return target.finish();
    }
    private static double unAtlas(Material material,float value,boolean u){var sprite=material.surface.sprite();float low=u?sprite.getU0():sprite.getV0(),high=u?sprite.getU1():sprite.getV1(),used=u?material.surface.usedU():material.surface.usedV();float contentHigh=low+(high-low)*used;return (value-low)/(contentHigh-low)*16;}
    private static final PanelOutlineFrame.Textures<Material> FRAME_TEXTURES=new PanelOutlineFrame.Textures<>(){
        public Material edge(Material original,double[] a,double[] b){return original.edges.isEmpty()?original:new Material(original.edges.get(FrameEdgeMapping.edge(a,b)));}
        public boolean authored(Material original,Material edge){return original.authoredOutline||edge.surface.sprite()!=original.surface.sprite()||original.surface.sprite().contents().name().getPath().contains("/frames/");}
        public Material map(Material material,double[][] points){return material.mapped(points);}
        public double unU(Material material,float u){return unAtlas(material,u,true);}
        public double unV(Material material,float v){return unAtlas(material,v,false);}
    };
    static List<BakedQuad> diagonalGlassFrame(com.vandorlabs.neoforge.shape.NativeDiagonalWallGeometry.Pose pose,Material glass,Material rail,Material inner,Material outer,int hideMask){
        float[] source=diagonalGlassSource(pose,glass,hideMask);Context target=new Context(pose.facing(),rail,0,NativePanelLayout.UNIT);
        PanelOutlineFrame.emit(CAP,target,FRAME_TEXTURES,source,rail,glass,inner,outer,(hideMask&1)!=0,(hideMask&2)!=0,(hideMask&4)!=0,(hideMask&8)!=0,(hideMask&16)!=0,(hideMask&32)!=0,pose.mode()==2?2:1,false);return target.finish();
    }
    private static final PanelApertureEmitter.Sink<Context,Material,Void> APERTURE=(context,material,ignored,reverse,a,b,c,d)->{if(context.projection==null)EMITTER.quad(context,material,reverse,a,b,c,d);else DiagonalPortholeProjection.quad(EMITTER,context,material,context.projection,reverse,a,b,c,d);};
    static List<BakedQuad> aperture(Direction facing,double near,NativePanelSlice opening,
            Material wall,Material metal,Material outer,int mode,NativePanelLayout group,
            boolean hideLeft,boolean hideRight,boolean hideLower,boolean hideUpper){return aperture(facing,near,opening,wall,metal,outer,mode,group,hideLeft,hideRight,hideLower,hideUpper,4);}
    static List<BakedQuad> aperture(Direction facing,double near,NativePanelSlice opening,
            Material wall,Material metal,Material outer,int mode,NativePanelLayout group,
            boolean hideLeft,boolean hideRight,boolean hideLower,boolean hideUpper,double depthUv){return aperture(facing,near,1,opening,wall,metal,outer,mode,group,hideLeft,hideRight,hideLower,hideUpper,depthUv);}
    static List<BakedQuad> aperture(Direction facing,double near,double zScale,NativePanelSlice opening,
            Material wall,Material metal,Material outer,int mode,NativePanelLayout group,
            boolean hideLeft,boolean hideRight,boolean hideLower,boolean hideUpper,double depthUv){
        Context context=new Context(facing,wall,mode,group,false,0,0,near-6*zScale,1,0,false,zScale);
        aperture(context,opening,wall,metal,outer,hideLeft,hideRight,hideLower,hideUpper,depthUv);
        return context.finish();
    }
    static List<BakedQuad> diagonalAperture(Direction facing,DiagonalPortholeProjection projection,NativePanelSlice opening,
            Material wall,Material metal,Material outer,int mode,NativePanelLayout group,
            boolean hideLeft,boolean hideRight,boolean hideLower,boolean hideUpper,double depthUv){
        Context context=new Context(facing,wall,mode,group);context.projection=Objects.requireNonNull(projection);
        aperture(context,opening,wall,metal,outer,hideLeft,hideRight,hideLower,hideUpper,depthUv);return context.finish();
    }
    private static void aperture(Context context,NativePanelSlice opening,Material wall,Material metal,Material outer,boolean hideLeft,boolean hideRight,boolean hideLower,boolean hideUpper,double depthUv){
        for(int q=0;q<opening.frameCount();q++)PanelApertureEmitter.panelQuad(APERTURE,context,wall,null,opening.frame(q,0),opening.frame(q,1),opening.frame(q,2),opening.frame(q,3),opening.frame(q,4),opening.frame(q,5),opening.frame(q,6),opening.frame(q,7));
        for(int e=0;e<opening.edgeCount();e++)PanelApertureEmitter.rimSegment(APERTURE,context,metal,null,opening.edge(e,0),opening.edge(e,1),opening.edge(e,2),opening.edge(e,3),depthUv);
        for(int y:new int[]{0,16}){
            if(y==0?hideLower:hideUpper)continue;double low=opening.opening(1,y==0?0:1,0),high=opening.opening(1,y==0?0:1,1);
            if(Double.isNaN(low))PanelApertureEmitter.topRim(APERTURE,context,outer,null,y,0,16,depthUv);
            else{PanelApertureEmitter.topRim(APERTURE,context,outer,null,y,0,low,depthUv);PanelApertureEmitter.topRim(APERTURE,context,outer,null,y,high,16,depthUv);}
        }
        for(int x:new int[]{0,16}){
            if(x==0?hideLeft:hideRight)continue;double low=opening.opening(0,x==0?0:1,0),high=opening.opening(0,x==0?0:1,1);
            if(Double.isNaN(low))PanelApertureEmitter.sideRim(APERTURE,context,outer,null,x,0,16,depthUv);
            else{PanelApertureEmitter.sideRim(APERTURE,context,outer,null,x,0,low,depthUv);PanelApertureEmitter.sideRim(APERTURE,context,outer,null,x,high,16,depthUv);}
        }
    }
    static List<BakedQuad> pane(Direction facing,double near,NativePanelSlice opening,
            Material glass,int mode,NativePanelLayout group,double borderX,double borderY,float opacity){return pane(facing,near,opening,glass,mode,group,borderX,borderY,opacity,0);}
    static List<BakedQuad> pane(Direction facing,double near,NativePanelSlice opening,
            Material glass,int mode,NativePanelLayout group,double borderX,double borderY,float opacity,double imageAspect){return pane(facing,near,1,opening,glass,mode,group,borderX,borderY,opacity,imageAspect);}
    static List<BakedQuad> pane(Direction facing,double near,double zScale,NativePanelSlice opening,
            Material glass,int mode,NativePanelLayout group,double borderX,double borderY,float opacity,double imageAspect){
        Context context=new Context(facing,glass,mode,group,true,borderX,borderY,near-6*zScale,opacity,imageAspect,false,zScale);context.current=glass;
        pane(context,opening);
        return context.finish();
    }
    static List<BakedQuad> diagonalPane(Direction facing,DiagonalPortholeProjection projection,NativePanelSlice opening,
            Material glass,int mode,NativePanelLayout group,double borderX,double borderY,float opacity,double imageAspect){
        Context context=new Context(facing,glass,mode,group,true,borderX,borderY,0,opacity,imageAspect);context.projection=Objects.requireNonNull(projection);context.current=glass;
        pane(context,opening);return context.finish();
    }
    private static final PanelQuadSink<Context,Material> RAW_PROJECTED=(context,material,reverse,a,b,c,d)->PanelVertexEmitter.raw(RAW,context,reverse,a[0],a[1],a[2],a[3],a[4],b[0],b[1],b[2],b[3],b[4],c[0],c[1],c[2],c[3],c[4],d[0],d[1],d[2],d[3],d[4]);
    private static void pane(Context context,NativePanelSlice opening){
        for(int depth:new int[]{7,9})for(int q=0;q<opening.glassCount();q++){
            if(context.projection!=null){double[][] vertices=new double[4][5];for(int v=0;v<4;v++){double x=opening.glass(q,v*2),y=opening.glass(q,v*2+1);vertices[v]=new double[]{x,y,depth,x/16,1-y/16};}DiagonalPortholeProjection.quad(RAW_PROJECTED,context,context.current,context.projection,depth==7,vertices);continue;}
            PanelVertexEmitter.raw(RAW,context,depth==7,opening.glass(q,0),opening.glass(q,1),depth,opening.glass(q,0)/16,1-opening.glass(q,1)/16,opening.glass(q,2),opening.glass(q,3),depth,opening.glass(q,2)/16,1-opening.glass(q,3)/16,opening.glass(q,4),opening.glass(q,5),depth,opening.glass(q,4)/16,1-opening.glass(q,5)/16,opening.glass(q,6),opening.glass(q,7),depth,opening.glass(q,6)/16,1-opening.glass(q,7)/16);
        }
    }
    private record Mesh(float[] vertices,List<Material> materials){}
    private static final PanelTextureMesh.Sink<Context,Material> CAP=new PanelTextureMesh.Sink<>(){
        public void atlas(Context context,Material material,double x,double y,double z,double u,double v,float nx,float ny,float nz){context.source(material,x,y,z,u,v,nx,ny,nz);}
        public void pixel(Context context,Material material,double x,double y,double z,double u,double v,float nx,float ny,float nz){context.source(material,x,y,z,NativeHousingQuads.atlas(material.surface,u,true),NativeHousingQuads.atlas(material.surface,v,false),nx,ny,nz);}
    };
    private static void part(Context target,Mesh source,int turns,double depth,double left,double width,double barNear,boolean outside){
        float[] points=PanelMeshTransform.transform(source.vertices,turns,depth,left,width);double[] bounds={barNear,barNear+4,-1,17,-1,17};
        for(int q=0;q<source.materials.size();q++){target.current=source.materials.get(q);PanelMeshTransform.box(RAW,target,points,q*32,q*32+32,bounds,outside);}
    }
    static List<BakedQuad> barBody(com.vandorlabs.neoforge.shape.NativeFlatBarOwner owner,com.vandorlabs.neoforge.shape.NativeFlatWallGeometry.Geometry geometry,
            com.vandorlabs.neoforge.shape.NativePortholeLayout aperture,Material wall,Material interior,Material exterior){
        Context stem=new Context(Direction.NORTH,wall,0,NativePanelLayout.UNIT,false,0,0,0,1,0,true),bar=new Context(Direction.NORTH,wall,0,NativePanelLayout.UNIT,false,0,0,0,1,0,true);
        if(aperture==null){PanelMeshEmitter.flat(EMITTER,stem,wall,interior,owner.panelNear(),geometry.join().corner());PanelMeshEmitter.flat(EMITTER,bar,wall,exterior,6,null);}
        else{aperture(stem,NativePanelSlice.get(aperture),wall,interior,exterior,aperture.hideLeft(),aperture.hideRight(),aperture.hideLower(),aperture.hideUpper(),4);aperture(bar,NativePanelSlice.get(com.vandorlabs.neoforge.shape.NativePortholeLayout.unit(aperture.shape())),wall,interior,exterior,false,false,false,false,4);}
        Mesh stemMesh=stem.captured(),barMesh=bar.captured();Context target=new Context(owner.facing(),wall,0,NativePanelLayout.UNIT);
        double depth=aperture==null?0:owner.panelNear()-6,left=aperture==null?0:owner.stemLeft(),width=aperture==null?1:owner.stemWidth();
        part(target,stemMesh,0,depth,left,width,owner.barNear(),true);
        PanelMeshTransform.end(CAP,target,interior,PanelMeshTransform.transform(stemMesh.vertices,0,depth,left,width),owner.stemCut(),owner.panelNear(),owner.leftTail());
        part(target,barMesh,3,owner.barNear()-6,0,1,owner.barNear(),false);return target.finish();
    }
    static List<BakedQuad> barPane(com.vandorlabs.neoforge.shape.NativeFlatBarOwner owner,com.vandorlabs.neoforge.shape.NativePortholeLayout aperture,
            Material glass,int mode,NativePanelLayout group,double borderX,double borderY,float opacity,double imageAspect){
        Context stem=new Context(Direction.NORTH,glass,mode,group,true,borderX,borderY,0,opacity,imageAspect,true),bar=new Context(Direction.NORTH,glass,mode,group,true,borderX,borderY,0,opacity,imageAspect,true);stem.current=glass;bar.current=glass;
        pane(stem,NativePanelSlice.get(aperture));pane(bar,NativePanelSlice.get(com.vandorlabs.neoforge.shape.NativePortholeLayout.unit(aperture.shape())));
        Context target=new Context(owner.facing(),glass,0,NativePanelLayout.UNIT,false,0,0,0,opacity,0);
        part(target,stem.captured(),0,owner.panelNear()-6,owner.stemLeft(),owner.stemWidth(),owner.barNear(),true);part(target,bar.captured(),3,owner.barNear()-6,0,1,owner.barNear(),false);return target.finish();
    }
    private static final PanelBoxEmitter.Sink<Context,Material> BOX=(context,material,x0,y0,z0,x1,y1,z1,x2,y2,z2,x3,y3,z3)->EMITTER.quad(context,material,true,new double[]{x0,y0,z0,0,0},new double[]{x1,y1,z1,16,0},new double[]{x2,y2,z2,16,16},new double[]{x3,y3,z3,0,16});
    private static void draw(Context target,Mesh source,int turns,double depth,double left,double width){
        float[] points=PanelMeshTransform.transform(source.vertices,turns,depth,left,width);
        for(int q=0;q<source.materials.size();q++){target.current=source.materials.get(q);for(int v=0;v<4;v++){int at=q*32+v*8;RAW.vertex(target,points[at],points[at+1],points[at+2],points[at+3],points[at+4],points[at+5],points[at+6],points[at+7]);}}
    }
    /** Non-T joined window center; arm faces retain their own immediate full-face artwork policy. */
    static List<BakedQuad> bentBody(com.vandorlabs.neoforge.shape.NativeFlatWallGeometry.Geometry geometry,com.vandorlabs.neoforge.shape.NativePortholeLayout aperture,
            Material wall,Material interior,Material exterior,int mode){
        if(geometry.join()==null)return aperture(geometry.facing(),PanelDepthPolicy.start(geometry.depth()),NativePanelSlice.get(aperture),wall,interior,exterior,mode,geometry.layout(),aperture.hideLeft(),aperture.hideRight(),aperture.hideLower(),aperture.hideUpper());
        var corner=geometry.join().corner();double near=PanelDepthPolicy.start(geometry.depth());
        Context center=new Context(Direction.NORTH,wall,mode,geometry.layout(),false,0,0,0,1,0,true);aperture(center,NativePanelSlice.get(aperture),wall,interior,exterior,aperture.hideLeft(),aperture.hideRight(),aperture.hideLower(),aperture.hideUpper(),4);
        Context target=new Context(geometry.facing(),wall,0,NativePanelLayout.UNIT);draw(target,center.captured(),0,near-6,corner.left(),(corner.right()-corner.left())/16);
        if(corner.frontArm!=null)PanelBoxEmitter.box(BOX,target,wall,interior,false,corner.frontArm,0,0,corner.frontArm+4,16,near,false);
        if(corner.backArm!=null)PanelBoxEmitter.box(BOX,target,wall,interior,false,corner.backArm,0,near+4,corner.backArm+4,16,16,false);
        return target.finish();
    }
    static List<BakedQuad> bentPane(com.vandorlabs.neoforge.shape.NativeFlatWallGeometry.Geometry geometry,com.vandorlabs.neoforge.shape.NativePortholeLayout aperture,
            Material glass,int mode,NativePanelLayout group,double borderX,double borderY,float opacity,double imageAspect){
        if(geometry.join()==null)return pane(geometry.facing(),PanelDepthPolicy.start(geometry.depth()),NativePanelSlice.get(aperture),glass,mode,group,borderX,borderY,opacity,imageAspect);
        var corner=geometry.join().corner();Context center=new Context(Direction.NORTH,glass,mode,group,true,borderX,borderY,0,opacity,imageAspect,true);center.current=glass;pane(center,NativePanelSlice.get(aperture));
        Context target=new Context(geometry.facing(),glass,0,NativePanelLayout.UNIT,false,0,0,0,opacity,0);draw(target,center.captured(),0,PanelDepthPolicy.offset(geometry.depth()),corner.left(),(corner.right()-corner.left())/16);return target.finish();
    }
    /** Ordinary glass has a distinct two-surface insert and saved-layout contract. */
    static List<BakedQuad> flatGlassBuiltin(Direction facing,int depth,Material glass,float opacity){
        Context target=new Context(facing,glass,0,NativePanelLayout.UNIT,false,0,0,PanelDepthPolicy.offset(depth),opacity,0);
        PanelTextureMesh.Sink<Context,Material> sink=new PanelTextureMesh.Sink<>(){public void atlas(Context c,Material m,double x,double y,double z,double u,double v,float nx,float ny,float nz){throw new IllegalStateException("Builtin pane pixel UV");}public void pixel(Context c,Material m,double x,double y,double z,double u,double v,float nx,float ny,float nz){c.source(m,x*16,y*16,z*16,NativeHousingQuads.atlas(m.surface,u,true),NativeHousingQuads.atlas(m.surface,v,false),nx,ny,nz);}};
        FlatGlassSurface.builtin(sink,target,glass,false);FlatGlassSurface.builtin(sink,target,glass,true);return target.finish();
    }
    static List<BakedQuad> flatGlassReplacement(Direction facing,int depth,Material glass,int mode,NativePanelLayout group,float opacity,double aspect){
        Context target=new Context(facing,glass,mode,group,true,0,0,PanelDepthPolicy.offset(depth),opacity,aspect);float[] source=FlatGlassSurface.materialPlane();
        for(int at=0;at<source.length;at+=8)target.source(glass,source[at],source[at+1],source[at+2],source[at+3],source[at+4],source[at+5],source[at+6],source[at+7]);return target.finish();
    }
    private static Mesh flatGlassSource(double near,PanelCornerGeometry.FlatCorner corner,Material glass){Context capture=new Context(Direction.NORTH,glass,0,NativePanelLayout.UNIT,false,0,0,0,1,0,true);PanelMeshEmitter.flat(EMITTER,capture,glass,glass,near,corner);return capture.captured();}
    static List<BakedQuad> flatGlassCornerPane(Direction facing,double near,PanelCornerGeometry.FlatCorner corner,Material glass,int mode,NativePanelLayout group,float opacity,double aspect,boolean replacement){
        float[] source=flatGlassSource(near,corner,glass).vertices;Context target=new Context(facing,glass,replacement?mode:0,replacement?group:NativePanelLayout.UNIT,false,0,0,0,opacity,aspect);
        for(int at=0;at<source.length;at+=8){double u=source[at+3],v=source[at+4];if(replacement&&mode==0){u=unAtlas(glass,(float)u,true)/16;v=unAtlas(glass,(float)v,false)/16;if(group.flipU())u=1-u;if(group.flipV())v=1-v;u=NativeHousingQuads.atlas(glass.surface,PanelTextureMapping.stretch(u,group.column(),0,group.columns())*16,true);v=NativeHousingQuads.atlas(glass.surface,PanelTextureMapping.stretch(v,group.rows()-1-group.row(),0,group.rows())*16,false);}target.source(glass,source[at],source[at+1],source[at+2],u,v,source[at+5],source[at+6],source[at+7]);}return target.finish();
    }
    static List<BakedQuad> flatGlassCornerFrame(Direction facing,double near,PanelCornerGeometry.FlatCorner corner,Material pane,Material rail,Material inner,Material outer){
        float[] source=flatGlassSource(near,corner,pane).vertices;Context target=new Context(facing,rail,0,NativePanelLayout.UNIT);PanelOutlineFrame.emit(CAP,target,FRAME_TEXTURES,source,rail,pane,inner,outer,false,false,false,false,false,false,1,true);return target.finish();
    }
    /** Released T bars use the center glass appearance on both pieces, including its original near coordinate. */
    static List<BakedQuad> flatGlassBarPart(Direction facing,double near,PanelCornerGeometry.FlatCorner corner,double barNear,int role,boolean frame,Material glass,Material rail,Material inner,Material outer,int mode,NativePanelLayout group,float opacity,double aspect,boolean replacement){
        Mesh original=flatGlassSource(near,role==0?corner:null,glass);Context capture=new Context(Direction.NORTH,frame?rail:glass,replacement&&!frame?mode:0,replacement&&!frame?group:NativePanelLayout.UNIT,false,0,0,0,1,aspect,true);
        if(frame)PanelOutlineFrame.emit(CAP,capture,FRAME_TEXTURES,original.vertices,rail,glass,inner,outer,false,false,false,false,false,false,1,true);
        else for(int at=0;at<original.vertices.length;at+=8){float[] points=original.vertices;double u=points[at+3],v=points[at+4];if(replacement&&mode==0){u=unAtlas(glass,(float)u,true)/16;v=unAtlas(glass,(float)v,false)/16;if(group.flipU())u=1-u;if(group.flipV())v=1-v;u=NativeHousingQuads.atlas(glass.surface,PanelTextureMapping.stretch(u,group.column(),0,group.columns())*16,true);v=NativeHousingQuads.atlas(glass.surface,PanelTextureMapping.stretch(v,group.rows()-1-group.row(),0,group.rows())*16,false);}capture.source(glass,points[at],points[at+1],points[at+2],u,v,points[at+5],points[at+6],points[at+7]);}
        Mesh mesh=capture.captured();Context target=new Context(facing,frame?rail:glass,0,NativePanelLayout.UNIT,false,0,0,0,frame?1:opacity,0);float[] points=PanelMeshTransform.transform(mesh.vertices,role==0?0:3,role==0?0:barNear-near,0,1);double padding=frame?.02:0;double[] bounds={barNear-padding,barNear+4+padding,-1,17,-1,17};for(int q=0;q<mesh.materials.size();q++){target.current=mesh.materials.get(q);PanelMeshTransform.box(RAW,target,points,q*32,q*32+32,bounds,role==0);}return target.finish();
    }
    static List<BakedQuad> flatGlassSelectedFrame(Direction facing,int depth,int mask,boolean selected,boolean border,double[] borders,double aspect,Material rail,Material inner,Material outer,Material artwork){
        Context target=new Context(facing,rail,0,NativePanelLayout.UNIT,false,0,0,PanelDepthPolicy.offset(depth),1,0);
        FlatGlassFrame.emit((context,material,edge,reverse,a,b,c,d)->{if(edge)EMITTER.quad(context,material,reverse,a,b,c,d);else{context.current=material;var texture=material.surface;PanelVertexEmitter.raw(RAW,context,reverse,a[0],a[1],a[2],NativeHousingQuads.atlas(texture,a[3],true),NativeHousingQuads.atlas(texture,a[4],false),b[0],b[1],b[2],NativeHousingQuads.atlas(texture,b[3],true),NativeHousingQuads.atlas(texture,b[4],false),c[0],c[1],c[2],NativeHousingQuads.atlas(texture,c[3],true),NativeHousingQuads.atlas(texture,c[4],false),d[0],d[1],d[2],NativeHousingQuads.atlas(texture,d[3],true),NativeHousingQuads.atlas(texture,d[4],false));}},target,mask,selected,border,borders,aspect,rail,inner,outer,artwork);return target.finish();
    }
    private NativePanelQuads(){}
}
