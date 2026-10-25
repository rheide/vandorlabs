package com.vandorlabs.dynmap;

import com.google.gson.*;
import com.vandorlabs.blocks.PanelPlane;
import com.vandorlabs.render.ConnectedSquare;
import java.io.*;
import java.util.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;
import org.dynmap.renderer.*;

/** Maps the native clipped cells using the same bounded square partition as gameplay. */
public final class HexThrusterRenderer extends CustomRenderer {
    private static final String[] FIELDS={"PropulsionJoin","ParticleLevel","ParticleStream","SignalBrightness","ParticleThreshold","ChannelLevel","RedstoneInverted"};
    private static final JsonObject DATA=load();
    private static JsonObject load(){try(InputStream in=HexThrusterRenderer.class.getResourceAsStream("/assets/vandorlabs/data/dynmap_hex_thrusters.json")){if(in==null)throw new IOException("Missing hexagonal map geometry");return new JsonParser().parse(new InputStreamReader(in,"UTF-8")).getAsJsonObject();}catch(IOException e){throw new IllegalStateException(e);}}
    @Override public String[] getTileEntityFieldsNeeded(){return FIELDS.clone();}
    @Override protected int getMaximumTextureCount(){return DATA.getAsJsonArray("textures").size();}
    private static Object field(MapDataContext c,String name,int x,int y,int z){return x==0&&y==0&&z==0?c.getBlockTileEntityField(name):c.getBlockTileEntityFieldAt(name,x,y,z);}
    private static int integer(Object value,int fallback){return value instanceof Number?((Number)value).intValue():value instanceof Boolean?((Boolean)value?1:0):fallback;}
    private static boolean join(MapDataContext c,int x,int y,int z){return integer(field(c,"PropulsionJoin",x,y,z),1)!=0;}
    private static String facing(DynmapBlockState state){for(String name:new String[]{"north","east","south","west","up","down"})if(state.isStateMatch("facing",name))return name;return "north";}
    private static String mode(MapDataContext c,DynmapBlockState state,int x,int y,int z){if(!state.isStateMatch("powered","true"))return "off";int particles=integer(field(c,"ParticleLevel",x,y,z),integer(field(c,"ParticleStream",x,y,z),state.isStateMatch("particles","true")?1:0));boolean proportional=integer(field(c,"SignalBrightness",x,y,z),0)!=0;int level=integer(field(c,"ChannelLevel",x,y,z),0);if(integer(field(c,"RedstoneInverted",x,y,z),0)!=0)level=15-level;boolean stream=particles>0&&(!proportional||level>=integer(field(c,"ParticleThreshold",x,y,z),8));return stream?"stream":"on";}
    @Override public RenderPatch[] getRenderPatchList(MapDataContext context){
        DynmapBlockState self=context.getBlockType();String face=facing(self),mode=mode(context,self,0,0,0);PanelPlane plane=PanelPlane.of(EnumFacing.byName(face));
        ConnectedSquare.Part part=join(context,0,0,0)?ConnectedSquare.findPartitioned(3,(u,v)->{int x=plane.right.getFrontOffsetX()*u+plane.up.getFrontOffsetX()*v,y=plane.right.getFrontOffsetY()*u+plane.up.getFrontOffsetY()*v,z=plane.right.getFrontOffsetZ()*u+plane.up.getFrontOffsetZ()*v;DynmapBlockState next=x==0&&y==0&&z==0?self:context.getBlockTypeAt(x,y,z);return next!=null&&self.blockName.equals(next.blockName)&&face.equals(facing(next))&&mode.equals(mode(context,next,x,y,z))&&join(context,x,y,z);}):ConnectedSquare.Part.SINGLE;
        String selected=part.isSingle()?"single":part.size==3?"s3_x"+part.x+"_y"+part.y:part.y==0?(part.x==0?"bottom_left":"bottom_right"):(part.x==0?"top_left":"top_right");
        String id=self.blockName.substring(self.blockName.indexOf(':')+1);JsonArray faces=DATA.getAsJsonObject("states").getAsJsonArray(id+"/"+face+"/"+mode+"/"+selected);if(faces==null)return new RenderPatch[0];List<RenderPatch> patches=new ArrayList<>();
        for(JsonElement entry:faces){JsonArray polygon=entry.getAsJsonArray();List<MapSurface.Vertex> vertices=new ArrayList<>();for(int i=1;i<polygon.size();i++){JsonArray v=polygon.get(i).getAsJsonArray();vertices.add(new MapSurface.Vertex(new Vec3d(v.get(0).getAsDouble(),v.get(1).getAsDouble(),v.get(2).getAsDouble()),v.get(3).getAsDouble(),v.get(4).getAsDouble()));}for(int i=1;i<vertices.size()-1;i++)MapSurface.emit(context,patches,Arrays.asList(vertices.get(0),vertices.get(i),vertices.get(i+1)),polygon.get(0).getAsInt());}return patches.toArray(new RenderPatch[0]);
    }
}
