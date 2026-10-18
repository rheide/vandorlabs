package com.vandorlabs.client;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

final class TextureLayoutMeshChecks {
    static void run(){
        canopyLayouts();
        for(int imageWidth:new int[]{16,32})for(int imageHeight:new int[]{16,32})for(int width:new int[]{8,16,48})for(int height:new int[]{8,16,48}){
            TextureAtlasSprite sprite=new TextureAtlasSprite("layout_mesh"){};
            sprite.setIconWidth(imageWidth);sprite.setIconHeight(imageHeight);sprite.initSprite(256,256,64,64,false);
            StaticSurfaceMesh.Capture capture=StaticSurfaceMesh.capture();
            WorldSurface.quad(capture,sprite,false,new double[]{0,0,0,0,16},new double[]{width,0,0,16,16},new double[]{width,height,0,16,0},new double[]{0,height,0,0,0});
            StaticSurfaceMesh source=capture.finish();
            for(int mode=0;mode<3;mode++)for(boolean flip:new boolean[]{false,true}){
                StaticSurfaceMesh mapped=GlassSurfaceMapping.map(source,sprite,new GlassSurfaceGroups.Layout(0,0,1,1,flip,flip),false,0,0,mode);
                require(mapped.samplesOnly(sprite),"UV escaped atlas sprite");
                float[] data=vertices(mapped);double area=0;
                for(int q=0;q<data.length;q+=32){area+=triangle(data,q,q+8,q+16)+triangle(data,q,q+16,q+24);
                    for(int i=q;i<q+32;i+=8)require(data[i]>=0 && data[i]<=width && data[i+1]>=0 && data[i+1]<=height && data[i+2]==0,"mapping moved surface");}
                require(Math.abs(area-width*height)<.01,"repeat clipping lost or duplicated area: "+area+" expected="+width*height);
                if(mode==2){boolean fits=Math.abs((double)width/height/((double)imageWidth/imageHeight)-1)<.005;
                    StaticSurfaceMesh expected=GlassSurfaceMapping.map(source,sprite,new GlassSurfaceGroups.Layout(0,0,1,1,flip,flip),false,0,0,fits?0:1);
                    require(java.util.Arrays.equals(data,vertices(expected)) || fits && sameExtents(data,vertices(expected)),"aspect fit did not select the expected mapping");}
            }
        }
        System.out.println("PASS: square/rectangular texture layouts preserve surface area, bounds and aspect-fit policy through repeat clipping");
    }
    private static void canopyLayouts(){
        for(int mode=0;mode<3;mode++){
            com.vandorlabs.canopy.LargeCanopySettings settings=new com.vandorlabs.canopy.LargeCanopySettings();settings.textureLayout(mode);
            net.minecraft.nbt.NBTTagCompound tag=new net.minecraft.nbt.NBTTagCompound();settings.write(tag);
            com.vandorlabs.canopy.LargeCanopySettings copy=new com.vandorlabs.canopy.LargeCanopySettings();copy.read(tag);require(copy.textureLayout()==mode,"canopy mode persistence");
            for(double aspect:new double[]{.5,1,2}){
                com.vandorlabs.canopy.CanopyMesh.Face face=new com.vandorlabs.canopy.CanopyMesh.Face();
                face.vertices=new net.minecraft.util.math.Vec3d[]{new net.minecraft.util.math.Vec3d(0,0,0),new net.minecraft.util.math.Vec3d(2,0,0),new net.minecraft.util.math.Vec3d(2,1,0),new net.minecraft.util.math.Vec3d(0,1,0)};
                face.uv=new double[][]{{0,0},{1,0},{1,1},{0,1}};
                boolean fit=com.vandorlabs.canopy.CanopyMaterialUv.fits(face,mode,aspect);
                require(fit==(mode==0 || mode==2 && aspect==2),"canopy aspect fit policy");
                double[][] uv=com.vandorlabs.canopy.CanopyMaterialUv.coordinates(face,mode,aspect);
                double minU=Double.POSITIVE_INFINITY,minV=minU,maxU=-minU,maxV=maxU;for(double[] p:uv){minU=Math.min(minU,p[0]);minV=Math.min(minV,p[1]);maxU=Math.max(maxU,p[0]);maxV=Math.max(maxV,p[1]);}
                require(fit?Math.abs(maxU-minU-1)<1e-8 && Math.abs(maxV-minV-1)<1e-8:Math.abs((maxU-minU)/(maxV-minV)*aspect-2)<1e-8,"canopy native image aspect");
            }
        }
    }
    private static boolean sameExtents(float[] a,float[] b){for(int component=0;component<5;component++){float amin=Float.POSITIVE_INFINITY,amax=-amin,bmin=amin,bmax=amax;for(int i=component;i<a.length;i+=8){amin=Math.min(amin,a[i]);amax=Math.max(amax,a[i]);}for(int i=component;i<b.length;i+=8){bmin=Math.min(bmin,b[i]);bmax=Math.max(bmax,b[i]);}if(Math.abs(amin-bmin)>1e-6 || Math.abs(amax-bmax)>1e-6)return false;}return true;}
    private static float[] vertices(StaticSurfaceMesh mesh){try{java.lang.reflect.Field field=StaticSurfaceMesh.class.getDeclaredField("vertices");field.setAccessible(true);return (float[])field.get(mesh);}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}}
    private static double triangle(float[] data,int a,int b,int c){return Math.abs((data[b]-data[a])*(data[c+1]-data[a+1])-(data[b+1]-data[a+1])*(data[c]-data[a]))/2;}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
