package com.vandorlabs.render;

/** Check physical aspect ratios and geometry independently of Minecraft's renderer. */
public final class TextureLayoutModeTest {
    public static void main(String[] args){
        require(TextureLayoutMode.fit(2,1,.5,2),"matching rectangle must fit");
        require(!TextureLayoutMode.fit(2,1,.5,1),"square must tile on slab side");
        require(TextureLayoutMode.fit(0,1,.5,1),"unrestricted stretch");
        ScreenHousingMesh slab=ScreenHousingMesh.slab(false,true);
        ScreenHousingMesh matching=slab.textureLayout(2,2),square=slab.textureLayout(2,1);
        close(span(matching.quads[2],false),16,"matching artwork fills slab height");
        close(span(square.quads[2],false),8,"square artwork retains slab density");
        close(span(ScreenHousingMesh.cube().textureLayout(1,2).quads[0],true),8,"wide artwork repeats over two blocks");
        close(span(ScreenHousingMesh.cube().textureLayout(1,.5).quads[0],false),8,"tall artwork repeats over two blocks");
        close(span(ScreenHousingMesh.cube().textureLayout(0,2).quads[0],true),16,"stretch fills width regardless of image aspect");
        ScreenHousingMesh[] meshes={ScreenHousingMesh.cube(),slab,ScreenHousingMesh.slab(true,true),ScreenHousingMesh.console(),ScreenHousingMesh.halfConsole(),ScreenHousingMesh.diagonal(false),ScreenHousingMesh.diagonal(true),ScreenHousingMesh.box(2,0,5,14,3,15)};
        for(ScreenHousingMesh mesh:meshes)for(int mode=0;mode<3;mode++)for(double aspect:new double[]{.5,1,2,3}){
            ScreenHousingMesh transformed=mesh.textureLayout(mode,aspect);
            faces(mesh.quads,transformed.quads);faces(mesh.triangles,transformed.triangles);
            require(transformed==mesh.textureLayout(mode,aspect),"stable layout cache");
        }
        ScreenHousingMesh top=ScreenHousingMesh.slab(true,true).textureLayout(2,1);
        close(top.quads[2].vertices[0].v,0,"upper slab keeps top half phase");
        close(square.quads[2].vertices[0].v,8,"lower slab keeps bottom half phase");
        require(ScreenHousingMesh.cube().boxFaces(63 & ~(1<<2)).quads.length==5,"masked light housing");
        System.out.println("PASS: three texture layouts, rectangular repeat density, slab aspect preservation, unchanged geometry/normals, atlas bounds and cached variants");
    }
    private static double span(ScreenHousingMesh.Face face,boolean u){double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;for(ScreenHousingMesh.Vertex v:face.vertices){double value=u?v.u:v.v;min=Math.min(min,value);max=Math.max(max,value);}return max-min;}
    private static void faces(ScreenHousingMesh.Face[] source,ScreenHousingMesh.Face[] mapped){
        require(source.length==mapped.length,"face count changed");for(int i=0;i<source.length;i++){
            close(source[i].nx,mapped[i].nx,"normal X");close(source[i].ny,mapped[i].ny,"normal Y");close(source[i].nz,mapped[i].nz,"normal Z");
            for(int j=0;j<source[i].vertices.length;j++){ScreenHousingMesh.Vertex a=source[i].vertices[j],b=mapped[i].vertices[j];close(a.x,b.x,"geometry X");close(a.y,b.y,"geometry Y");close(a.z,b.z,"geometry Z");require(b.u>=-1e-8 && b.u<=16+1e-8 && b.v>=-1e-8 && b.v<=16+1e-8,"UV outside sprite");}
        }
    }
    private static void close(double a,double b,String message){require(Math.abs(a-b)<1e-7,message+" "+a+" / "+b);}
    private static void require(boolean test,String message){if(!test)throw new AssertionError(message);}
}
