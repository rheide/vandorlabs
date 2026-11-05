package com.vandorlabs.client;

import com.google.gson.*;
import java.io.*;
import java.lang.reflect.*;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Optional actual legacy mesh/UV reference for the independent native CPU adapter. */
public final class PanelApertureReferenceExport {
    private static Method panel,top,side,rim;private static Field vertices;
    private static TextureAtlasSprite wall,metal;
    private static TextureAtlasSprite sprite(String id,int offset){TextureAtlasSprite s=new TextureAtlasSprite(id){};s.setIconWidth(16);s.setIconHeight(16);s.initSprite(32,16,offset,0,false);return s;}
    private static Method method(String name,int count)throws Exception{Class<?>[] parameters=new Class<?>[3+count];parameters[0]=BufferBuilder.class;parameters[1]=TextureAtlasSprite.class;parameters[2]=DiagonalPortholeMesh.class;java.util.Arrays.fill(parameters,3,parameters.length,double.class);Method result=TEAnimatedScreenSelector.class.getDeclaredMethod(name,parameters);result.setAccessible(true);return result;}
    static StaticSurfaceMesh body(PortholeHex.Slice slice,boolean left,boolean right,boolean lower,boolean upper,double depthUv,TextureAtlasSprite outer)throws Exception{
        StaticSurfaceMesh.Capture capture=StaticSurfaceMesh.capture();
        for(double[] q:slice.frameQuads)panel.invoke(null,capture,wall,null,q[0],q[1],q[2],q[3],q[4],q[5],q[6],q[7]);
        for(double[] e:slice.hexEdges)rim.invoke(null,capture,metal,null,e[0],e[1],e[2],e[3],depthUv);
        for(int y:new int[]{0,16}){if(y==0?lower:upper)continue;double[] cut=slice.edgeOpening(1,y);if(cut==null)top.invoke(null,capture,outer,null,(double)y,0D,16D,depthUv);else{top.invoke(null,capture,outer,null,(double)y,0D,cut[0],depthUv);top.invoke(null,capture,outer,null,(double)y,cut[1],16D,depthUv);}}
        for(int x:new int[]{0,16}){if(x==0?left:right)continue;double[] cut=slice.edgeOpening(0,x);if(cut==null)side.invoke(null,capture,outer,null,(double)x,0D,16D,depthUv);else{side.invoke(null,capture,outer,null,(double)x,0D,cut[0],depthUv);side.invoke(null,capture,outer,null,(double)x,cut[1],16D,depthUv);}}
        return capture.finish();
    }
    static void initialize(TextureAtlasSprite body,TextureAtlasSprite inner)throws Exception{panel=method("panelQuad",8);top=method("topRim",4);side=method("sideRim",4);rim=method("rimSegment",5);wall=body;metal=inner;}
    public static void main(String[] ignored)throws Exception{
        net.minecraft.init.Bootstrap.register();panel=method("panelQuad",8);top=method("topRim",4);side=method("sideRim",4);rim=method("rimSegment",5);vertices=StaticSurfaceMesh.class.getDeclaredField("vertices");vertices.setAccessible(true);wall=sprite("aperture_reference",0);metal=sprite("aperture_reference_metal",16);
        File output=new File(System.getProperty("vandorlabs.panelApertureReference","build/test-artifacts/panel-aperture-forge.jsonl"));output.getAbsoluteFile().getParentFile().mkdirs();int records=0;Gson gson=new Gson();
        try(PrintWriter writer=new PrintWriter(output,"UTF-8")){
            for(int columns:new int[]{1,2,3})for(int rows:new int[]{1,2,3})for(int shape=0;shape<4;shape++)for(int border=0;border<2;border++)for(int row=0;row<rows;row++)for(int column=0;column<columns;column++){
                PortholeHex.Slice slice=new PortholeHex(columns,rows,shape,border).slice(column,row);boolean left=column>0,right=column<columns-1,lower=row>0,upper=row<rows-1;StaticSurfaceMesh[] sources={body(slice,left,right,lower,upper,4,metal),body(slice,left,right,lower,upper,16,metal)};StaticSurfaceMesh.Capture capture=StaticSurfaceMesh.capture();TEAnimatedScreenSelector.drawPortholeGlassGeometry(capture,slice,null);StaticSurfaceMesh pane=capture.finish();
                for(int depthIndex=0;depthIndex<2;depthIndex++)for(int mode=0;mode<3;mode++)for(int flips=0;flips<4;flips++){
                    GlassSurfaceGroups.Layout layout=new GlassSurfaceGroups.Layout(column,row,columns,rows,(flips&1)!=0,(flips&2)!=0);StaticSurfaceMesh source=sources[depthIndex];JsonObject record=new JsonObject();record.addProperty("depthUv",depthIndex==0?4:16);record.addProperty("columns",columns);record.addProperty("rows",rows);record.addProperty("column",column);record.addProperty("row",row);record.addProperty("shape",shape);record.addProperty("border",border);record.addProperty("mode",mode);record.addProperty("flips",flips);record.add("body",gson.toJsonTree(vertices.get(mode==0?source:GlassSurfaceMapping.map(source,wall,layout,false,0,0,mode))));record.add("pane",gson.toJsonTree(vertices.get(GlassSurfaceMapping.map(pane,wall,layout,true,(2+border)/16D,(4+border)/16D,mode))));writer.println(gson.toJson(record));records++;
                }
            }
        }
        System.out.println("Panel aperture actual Forge reference PASS records="+records+" independently captured body/pane atlasUVs and current group mapping");
    }
}
