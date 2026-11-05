package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.BlockProgrammableWall;
import java.io.*;
import java.lang.reflect.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Bounded actual legacy sloped body/pane records; no native projection code is used. */
public final class DiagonalApertureReferenceExport {
    private static TextureAtlasSprite sprite(String id,int x){TextureAtlasSprite s=new TextureAtlasSprite(id){};s.setIconWidth(16);s.setIconHeight(16);s.initSprite(32,16,x,0,false);return s;}
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();TextureAtlasSprite wall=sprite("diagonal_aperture",0),metal=sprite("diagonal_inner",16);PanelApertureReferenceExport.initialize(wall,metal);
        Field vertices=StaticSurfaceMesh.class.getDeclaredField("vertices");vertices.setAccessible(true);Constructor<BlockProgrammableWall.Corner> ctor=BlockProgrammableWall.Corner.class.getDeclaredConstructor(Boolean.class,Boolean.class,boolean.class,boolean.class);ctor.setAccessible(true);
        File out=new File(System.getProperty("vandorlabs.diagonalApertureMeshReference","build/test-artifacts/diagonal-aperture-mesh-forge.jsonl"));out.getAbsoluteFile().getParentFile().mkdirs();int records=0;Gson gson=new Gson();
        try(PrintWriter writer=new PrintWriter(out,"UTF-8")){
            for(int slope=0;slope<3;slope++)for(int inverted=0;inverted<2;inverted++)for(int profile=0;profile<3;profile++)for(int shape=0;shape<4;shape++)for(int flips=0;flips<4;flips++){
                int col=0,row=0,cols=2,rows=3;int lc=(flips&1)!=0?1:0,lr=(flips&2)!=0?2:0;PortholeHex.Slice slice=new PortholeHex(cols,rows,shape,0).slice(lc,lr);
                double lower=profile==1?4:Double.NaN,upper=profile==1?12:Double.NaN;double[] bounds=profile==2?new double[]{0,1,0,16,15,16}:null;BlockProgrammableWall.Corner corner=profile==2?ctor.newInstance(Boolean.TRUE,Boolean.FALSE,true,false):null;
                DiagonalPortholeMesh projection=new DiagonalPortholeMesh(slope,inverted!=0,lower,upper,bounds,corner);
                StaticSurfaceMesh body=PanelApertureReferenceExport.body(slice,false,true,false,true,4,metal,projection);StaticSurfaceMesh.Capture cap=StaticSurfaceMesh.capture();TEAnimatedScreenSelector.drawPortholeGlassGeometry(cap,slice,projection);StaticSurfaceMesh pane=cap.finish();
                for(int mode=0;mode<3;mode++){
                    GlassSurfaceGroups.Layout layout=new GlassSurfaceGroups.Layout(col,row,cols,rows,(flips&1)!=0,(flips&2)!=0);JsonObject r=new JsonObject();r.addProperty("slope",slope);r.addProperty("inverted",inverted);r.addProperty("profile",profile);r.addProperty("shape",shape);r.addProperty("flips",flips);r.addProperty("mode",mode);
                    r.add("body",gson.toJsonTree(vertices.get(mode==0?body:GlassSurfaceMapping.map(body,wall,layout,false,0,0,mode))));r.add("pane",gson.toJsonTree(vertices.get(GlassSurfaceMapping.map(pane,wall,layout,true,2D/16,4D/16,mode))));writer.println(gson.toJson(r));records++;
                }
            }
        }
        if(records!=864)throw new AssertionError(records);System.out.println("Diagonal aperture actual Forge reference PASS records="+records);
    }
}
