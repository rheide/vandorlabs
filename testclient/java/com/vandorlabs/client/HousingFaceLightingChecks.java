package com.vandorlabs.client;
import com.vandorlabs.blocks.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import java.util.*;
/** A bright side neighbour must not overwrite the visible face's vanilla light sample. */
final class HousingFaceLightingChecks {
 static void run(){
  final BlockPos pos=new BlockPos(8,100,8);NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true){public int getCombinedLight(BlockPos p,int minimum){int level=p.equals(pos.east())?15:p.equals(pos.north())?8:p.equals(pos.up())?12:6;return (Math.max(level,minimum)<<4)|(5<<20);}};
  TextureAtlasSprite sprite=new TextureAtlasSprite("face_light_fixture"){};sprite.setIconWidth(16);sprite.setIconHeight(16);sprite.initSprite(16,16,0,0,false);
  IBakedModel delegate=new IBakedModel(){public List<BakedQuad> getQuads(IBlockState s,EnumFacing f,long seed){return Collections.emptyList();}public boolean isAmbientOcclusion(){return false;}public boolean isGui3d(){return true;}public boolean isBuiltInRenderer(){return false;}public TextureAtlasSprite getParticleTexture(){return sprite;}public ItemCameraTransforms getItemCameraTransforms(){return ItemCameraTransforms.DEFAULT;}public ItemOverrideList getOverrides(){return ItemOverrideList.NONE;}};
  int cases=0;
  for(boolean slab:new boolean[]{false,true}){
   Block block=slab?new BlockProgrammableSlab():new BlockProgrammableBlock();ProgrammableHousingModel model=new ProgrammableHousingModel(delegate,slab,false,name->sprite);
   for(IBlockState state:block.getBlockState().getValidStates()){
    world.setBlockState(pos,state,2);IBlockState extended=block.getExtendedState(state,world,pos);
    for(EnumFacing face:EnumFacing.values()){
     BlockPos sample=pos.offset(face);int expected=net.minecraft.init.Blocks.STONE.getDefaultState().getPackedLightmapCoords(world,sample),actual=block.getPackedLightmapCoords(extended,world,sample);
     if(expected!=actual)throw new AssertionError("side light bleeds into visible face: slab="+slab+" sample="+face+" expected="+expected+" actual="+actual);cases++;
    }
    for(BakedQuad quad:model.getQuads(extended,null,0)){
     int[] d=quad.getVertexData();int stride=d.length/4;int axis=quad.getFace().getAxis()==EnumFacing.Axis.X?0:quad.getFace().getAxis()==EnumFacing.Axis.Y?1:2;float coordinate=Float.intBitsToFloat(d[axis]);for(int i=1;i<4;i++)if(Math.abs(Float.intBitsToFloat(d[i*stride+axis])-coordinate)>1e-6)throw new AssertionError("declared face differs from emitted geometry normal");
    }
   }
  }
  System.out.println("housing-face-lighting PASS cases="+cases+" vanilla positional samples bright-side negative control real face normals all facings slab halves");
 }
}
