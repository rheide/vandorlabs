package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.BlockProgrammableWall;
import java.io.*;
import java.lang.invoke.*;
import java.lang.reflect.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Current non-T bent window paths, including the actual immediate arm renderer. */
public final class PanelBentReferenceExport {
    private static Field vertices;
    private static TextureAtlasSprite sprite(String name,int w,int h,int x){TextureAtlasSprite s=new TextureAtlasSprite(name){};s.setIconWidth(w);s.setIconHeight(h);s.initSprite(1024,128,x,0,false);return s;}
    private static float[] join(StaticSurfaceMesh... meshes)throws Exception{int count=0;for(StaticSurfaceMesh mesh:meshes)count+=((float[])vertices.get(mesh)).length;float[] result=new float[count];int offset=0;for(StaticSurfaceMesh mesh:meshes){float[] data=(float[])vertices.get(mesh);System.arraycopy(data,0,result,offset,data.length);offset+=data.length;}return result;}
    public static void main(String[] ignored)throws Throwable{
        net.minecraft.init.Bootstrap.register();vertices=StaticSurfaceMesh.class.getDeclaredField("vertices");vertices.setAccessible(true);Constructor<BlockProgrammableWall.FlatCorner> constructor=BlockProgrammableWall.FlatCorner.class.getDeclaredConstructor(Double.class,Double.class,Boolean.class,Boolean.class,boolean.class,boolean.class);constructor.setAccessible(true);Method method=TEAnimatedScreenSelector.class.getDeclaredMethod("renderWallBox",TextureAtlasSprite.class,TextureAtlasSprite.class,boolean.class,double.class,double.class,double.class,double.class,double.class,double.class,boolean.class);method.setAccessible(true);MethodHandle box=MethodHandles.lookup().unreflect(method);
        TextureAtlasSprite wall=sprite("bent_wall",16,16,0),plain=sprite("bent_interior",16,16,32),outer=sprite("bent_exterior",16,16,64),rail=PanelBoxEmitterChecks.frame();Gson gson=new Gson();int count=0;
        File output=new File(System.getProperty("vandorlabs.panelBentReference","build/test-artifacts/panel-bent-forge.jsonl"));output.getAbsoluteFile().getParentFile().mkdirs();
        try(PanelBoxEmitterChecks.TessellatorOverride tess=new PanelBoxEmitterChecks.TessellatorOverride(StaticSurfaceMesh.independentCapture());PrintWriter writer=new PrintWriter(output,"UTF-8")){
            Double[] arms={null,0D,6D,12D};for(int depth=0;depth<3;depth++)for(Double front:arms)for(Double back:arms)for(int tails=0;tails<4;tails++){
                if(front==null&&back==null||front!=null&&back!=null&&Math.abs(front-back)<1e-7&&tails!=0&&tails!=3)continue;boolean left=(tails&1)!=0,right=(tails&2)!=0;BlockProgrammableWall.FlatCorner corner=constructor.newInstance(front,back,front==null?null:Boolean.FALSE,back==null?null:Boolean.TRUE,left,right);double near=com.vandorlabs.render.PanelDepthPolicy.start(depth);if(corner.right()-corner.left()<=0)continue;
                for(boolean authored:new boolean[]{false,true})for(int shape:new int[]{0,3}){
                    TextureAtlasSprite metal=authored?rail:plain;int columns=2,rows=3,column=0,row=1;PortholeHex.Slice opening=new PortholeHex(columns,rows,shape).slice(column,row);PanelApertureReferenceExport.initialize(wall,metal);StaticSurfaceMesh source=PanelApertureReferenceExport.body(opening,false,true,true,true,4,outer);StaticSurfaceMesh.Capture armCapture=StaticSurfaceMesh.independentCapture();tess.cpu.active=armCapture;
                    if(front!=null)box.invokeExact(wall,metal,false,(double)front,0D,0D,front+4,16D,near,false);if(back!=null)box.invokeExact(wall,metal,false,(double)back,0D,near+4,back+4,16D,16D,false);StaticSurfaceMesh armMesh=armCapture.finish();
                    for(int mode=0;mode<3;mode++){
                        GlassSurfaceGroups.Layout mainLayout=new GlassSurfaceGroups.Layout(1,1,3,2);StaticSurfaceMesh center=mode==0?source:GlassSurfaceMapping.map(source,wall,mainLayout,false,0,0,mode);float[] body=join(center.panelTransform(0,near-6,corner.left(),(corner.right()-corner.left())/16),armMesh);
                        for(double aspect:new double[]{.5,1,2}){int w=aspect==2?32:16,h=aspect==.5?32:16;TextureAtlasSprite glass=sprite("bent_glass",w,h,96);StaticSurfaceMesh.Capture paneCapture=StaticSurfaceMesh.capture();TEAnimatedScreenSelector.drawPortholeGlassGeometry(paneCapture,opening,null);StaticSurfaceMesh raw=paneCapture.finish();GlassSurfaceGroups.Layout paneLayout=new GlassSurfaceGroups.Layout(column,row,columns,rows);StaticSurfaceMesh pane=GlassSurfaceMapping.map(raw,glass,paneLayout,true,2D/16,4D/16,mode).panelTransform(0,near-6,corner.left(),(corner.right()-corner.left())/16);
                            JsonObject record=new JsonObject();record.addProperty("depth",depth);record.add("front",gson.toJsonTree(front));record.add("back",gson.toJsonTree(back));record.addProperty("tails",tails);record.addProperty("frame",authored);record.addProperty("shape",shape);record.addProperty("mode",mode);record.addProperty("aspect",aspect);record.add("body",gson.toJsonTree(body));record.add("pane",gson.toJsonTree(vertices.get(pane)));writer.println(gson.toJson(record));count++;
                        }
                    }
                }
            }
        }
        System.out.println("Panel bent porthole actual Forge reference PASS records="+count+" all local bends/crosses, independent main/pane layouts, exact immediate arm source");
    }
}
