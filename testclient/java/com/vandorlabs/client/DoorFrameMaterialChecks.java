package com.vandorlabs.client;
import com.vandorlabs.tiles.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.util.EnumFacing;
final class DoorFrameMaterialChecks {
 static void run(){
  Map<String,TextureAtlasSprite> sprites=new HashMap<>();java.util.function.Function<String,TextureAtlasSprite> atlas=name->sprites.computeIfAbsent(name,k->{TextureAtlasSprite s=new TextureAtlasSprite(k){};s.setIconWidth(16);s.setIconHeight(16);s.initSprite(64,64,16,16,false);return s;});
  TextureAtlasSprite nativeFrame=atlas.apply("vandorlabs:blocks/space_doors/low/double_frame_metal"),nativeSide=atlas.apply("vandorlabs:blocks/programmable_glass/metal_side");
  int front=ScreenHousingTextures.screenIndex("frames/frames-door-window/vandor_bg"),inside=ScreenHousingTextures.screenIndex("light_alloy_hull"),cases=0;
  if(front<0||inside<0)throw new AssertionError("door material fixtures");
  for(boolean sharedSlot:new boolean[]{false,true})for(double scale:new double[]{1,1.5})for(int selected=1;selected<=3;selected++){
   List<BakedQuad> source=new ArrayList<>();for(EnumFacing face:EnumFacing.values())source.add(box(face,face.getAxis()==EnumFacing.Axis.Z||sharedSlot?nativeFrame:nativeSide));
   IBakedModel base=new IBakedModel(){public List<BakedQuad> getQuads(IBlockState s,EnumFacing side,long seed){return side==null?source:Collections.emptyList();}public boolean isAmbientOcclusion(){return false;}public boolean isGui3d(){return true;}public boolean isBuiltInRenderer(){return false;}public TextureAtlasSprite getParticleTexture(){return nativeFrame;}public ItemCameraTransforms getItemCameraTransforms(){return ItemCameraTransforms.DEFAULT;}public ItemOverrideList getOverrides(){return ItemOverrideList.NONE;}};
   FrameAppearance a=new FrameAppearance();a.front=(selected&1)!=0?front:-1;a.interior=(selected&2)!=0?inside:-1;
   IBakedModel changed=FrameDoorModel.get(base,a,scale,atlas);if(FrameDoorModel.get(base,a,scale,atlas)!=changed)throw new AssertionError("uncached frame model");
   for(BakedQuad q:changed.getQuads(null,null,0)){
    boolean broad=q.getFace().getAxis()==EnumFacing.Axis.Z;String expected=broad?(a.front>=0?ScreenHousingTextures.texture(front):nativeFrame.getIconName()):(a.interior>=0?ScreenHousingTextures.texture(inside):(sharedSlot?nativeFrame:nativeSide).getIconName());
    if(!q.getSprite().getIconName().equals(expected))throw new AssertionError("Front/Interior leaked across face axes: "+q.getFace());
    boolean replaced=broad?a.front>=0:a.interior>=0;
    if(q.getTintIndex()!=(replaced?-1:0))throw new AssertionError("Selected material retained native frame tint");
    if(broad&&a.front>=0){int[] d=q.getVertexData();int stride=d.length/4;for(int i=0;i<4;i++)for(int j=i+1;j<4;j++){
     double dx=Float.intBitsToFloat(d[i*stride])-Float.intBitsToFloat(d[j*stride]),dy=Float.intBitsToFloat(d[i*stride+1])-Float.intBitsToFloat(d[j*stride+1]);
     double du=q.getSprite().getUnInterpolatedU(Float.intBitsToFloat(d[i*stride+4]))-q.getSprite().getUnInterpolatedU(Float.intBitsToFloat(d[j*stride+4])),dv=q.getSprite().getUnInterpolatedV(Float.intBitsToFloat(d[i*stride+5]))-q.getSprite().getUnInterpolatedV(Float.intBitsToFloat(d[j*stride+5]));
     if(Math.abs(Math.abs(dx*scale)-Math.abs(du/16))>1e-5||Math.abs(Math.abs(dy*scale)-Math.abs(dv/16))>1e-5)throw new AssertionError("square material pixels stretched at scale "+scale);
    }}
   }
   if(DoorQuadPlan.prepare(changed)==null)throw new AssertionError("immutable custom frame excluded from prepared rendering");cases++;
  }
  FrameDoorModel.clear();System.out.println("door-frame-materials PASS cases="+cases+" independent faces shared-slot models square pixels normal/large prepared geometry reuse");
 }
 private static BakedQuad box(EnumFacing face,TextureAtlasSprite sprite){
  double[][] v={{0,0,.375},{1,0,.375},{0,2,.375},{1,2,.375},{0,0,.625},{1,0,.625},{0,2,.625},{1,2,.625}};int f=face==EnumFacing.DOWN?0:face==EnumFacing.UP?1:face==EnumFacing.NORTH?2:face==EnumFacing.SOUTH?3:face==EnumFacing.WEST?4:5;int[] indices=com.vandorlabs.render.TrapdoorGeometry.FACES[f];VertexFormat format=DefaultVertexFormats.ITEM;int stride=format.getIntegerSize();int[] d=new int[stride*4];for(int i=0;i<4;i++){for(int a=0;a<3;a++)d[i*stride+a]=Float.floatToRawIntBits((float)v[indices[i]][a]);d[i*stride+3]=-1;d[i*stride+4]=Float.floatToRawIntBits(sprite.getInterpolatedU((i==1||i==2)?16:0));d[i*stride+5]=Float.floatToRawIntBits(sprite.getInterpolatedV(i<2?0:16));}return new BakedQuad(d,0,face,sprite,false,format);
 }
}
