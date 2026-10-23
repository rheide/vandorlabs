package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import java.util.*;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.EnumSkyBlock;

/** Compare generated texture coordinates and recess light sampling with vanilla stairs. */
final class StairAppearanceChecks {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("stair appearance: "+message);}
    static void run(Minecraft mc,BlockPos pos){
        int samples=0;
        try{
            for(EnumFacing facing:EnumFacing.HORIZONTALS)for(BlockStairs.EnumHalf half:BlockStairs.EnumHalf.values())for(BlockStairs.EnumShape shape:BlockStairs.EnumShape.values()){
                IBlockState nativeState=Blocks.OAK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING,facing).withProperty(BlockStairs.HALF,half).withProperty(BlockStairs.SHAPE,shape);
                IBakedModel nativeModel=mc.getBlockRendererDispatcher().getModelForState(nativeState);List<BakedQuad> nativeQuads=new ArrayList<>(nativeModel.getQuads(nativeState,null,0));
                for(EnumFacing face:EnumFacing.values())nativeQuads.addAll(nativeModel.getQuads(nativeState,face,0));
                IBlockState state=ModBlocks.PROGRAMMABLE_STAIRS.getDefaultState().withProperty(BlockStairs.FACING,facing).withProperty(BlockStairs.HALF,half).withProperty(BlockStairs.SHAPE,shape);
                for(BakedQuad generated:ProgrammableStairMesh.quads(state,8,nativeModel.getParticleTexture())){
                    Vec3d point=point(generated,0).add(point(generated,1)).add(point(generated,2)).add(point(generated,3)).scale(.25);
                    BakedQuad reference=null;
                    for(BakedQuad quad:nativeQuads)if(quad.getFace()==generated.getFace()&&contains(quad,point)){reference=quad;break;}
                    require(reference!=null,"generated face covered by vanilla stair surface "+facing+"/"+half+"/"+shape+"/"+generated.getFace());
                    for(int uv=4;uv<=5;uv++){
                        double delta=Math.abs(uv(generated,point,uv)-uv(reference,point,uv));
                        require(delta<.02||Math.abs(delta-16)<.02,"vanilla UV parity "+facing+"/"+half+"/"+shape+"/"+generated.getFace()+" uv="+uv+" delta="+delta);
                    }samples++;
                }
                // Force a dark owner cell beside lit air: vanilla samples the lit neighbors.
                mc.world.setBlockState(pos,nativeState,2);
                for(EnumFacing face:EnumFacing.values())mc.world.setBlockToAir(pos.offset(face));
                for(EnumFacing face:EnumFacing.values()){mc.world.setLightFor(EnumSkyBlock.SKY,pos.offset(face),15);mc.world.setLightFor(EnumSkyBlock.BLOCK,pos.offset(face),11);}
                mc.world.setLightFor(EnumSkyBlock.SKY,pos,0);mc.world.setLightFor(EnumSkyBlock.BLOCK,pos,0);
                int expected=nativeState.getPackedLightmapCoords(mc.world,pos);
                mc.world.setBlockState(pos,state,2);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)mc.world.getTileEntity(pos);
                require(tile.getMainTextureLayout()==1,"new stairs default to Tile");
                for(int roll=0;roll<3;roll++){
                    tile.setStairGeometry(8,roll);mc.world.setLightFor(EnumSkyBlock.SKY,pos,0);mc.world.setLightFor(EnumSkyBlock.BLOCK,pos,0);
                    require(state.getPackedLightmapCoords(mc.world,pos)==expected,"recess light matches vanilla for mount "+roll);
                }
                tile.setMainTextureLayout(0);tile.readFromNBT(tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));require(tile.getMainTextureLayout()==0,"saved Fit remains Fit");
            }
            lighting(mc,pos);
        }finally{mc.world.setBlockToAir(pos);}
        System.out.println("[vandorlabs][reprolab] stair-appearance PASS vanilla UV samples="+samples+" recess light all mounts, Tile default and saved Fit");
    }
    private static void lighting(Minecraft mc,BlockPos pos){
        int oldAo=mc.gameSettings.ambientOcclusion;
        try{for(int ao:new int[]{0,2}){
            mc.gameSettings.ambientOcclusion=ao;
            IBlockState stone=Blocks.STONE.getDefaultState();mc.world.setBlockState(pos,stone,2);light(mc,pos);
            java.util.Map<EnumFacing,int[]> reference=render(mc,stone,pos);
            mc.world.setBlockState(pos,Blocks.OAK_STAIRS.getDefaultState(),2);light(mc,pos);mc.world.setBlockState(pos.down(),stone,2);
            int[] beneathVanilla=render(mc,stone,pos.down()).get(EnumFacing.UP);
            for(EnumFacing facing:EnumFacing.HORIZONTALS)for(BlockStairs.EnumHalf half:BlockStairs.EnumHalf.values())for(int roll=0;roll<3;roll++){
                IBlockState state=ModBlocks.PROGRAMMABLE_STAIRS.getDefaultState().withProperty(BlockStairs.FACING,facing).withProperty(BlockStairs.HALF,half);
                mc.world.setBlockState(pos,state,2);((TileEntityAnimatedScreenSelector)mc.world.getTileEntity(pos)).setStairGeometry(4,roll);light(mc,pos);
                java.util.Map<EnumFacing,int[]> actual=render(mc,state,pos);
                for(EnumFacing face:EnumFacing.values())require(java.util.Arrays.equals(actual.get(face),reference.get(face)),"rendered recess brightness/diffuse shading matches lit vanilla face "+ao+"/"+facing+"/"+half+"/"+roll+"/"+face+" actual="+java.util.Arrays.toString(actual.get(face))+" expected="+java.util.Arrays.toString(reference.get(face)));
                mc.world.setBlockState(pos.down(),stone,2);
                require(java.util.Arrays.equals(render(mc,stone,pos.down()).get(EnumFacing.UP),beneathVanilla),"block under empty stair region must not darken compared with vanilla "+ao+"/"+facing+"/"+half+"/"+roll);
            }
        }}finally{mc.gameSettings.ambientOcclusion=oldAo;}
    }
    private static void light(Minecraft mc,BlockPos pos){
        for(BlockPos cell:BlockPos.getAllInBox(pos.add(-2,-2,-2),pos.add(2,2,2))){if(!cell.equals(pos))mc.world.setBlockToAir(cell);mc.world.setLightFor(EnumSkyBlock.SKY,cell,15);mc.world.setLightFor(EnumSkyBlock.BLOCK,cell,11);}
        mc.world.setLightFor(EnumSkyBlock.SKY,pos,0);mc.world.setLightFor(EnumSkyBlock.BLOCK,pos,0);
    }
    private static java.util.Map<EnumFacing,int[]> render(Minecraft mc,IBlockState state,BlockPos pos){
        net.minecraft.client.renderer.BufferBuilder buffer=new net.minecraft.client.renderer.BufferBuilder(65536);buffer.begin(7,net.minecraft.client.renderer.vertex.DefaultVertexFormats.BLOCK);
        mc.getBlockRendererDispatcher().getBlockModelRenderer().renderModel(mc.world,mc.getBlockRendererDispatcher().getModelForState(state),state.getBlock().getExtendedState(state,mc.world,pos),pos,buffer,false);buffer.finishDrawing();
        java.util.Map<EnumFacing,int[]> result=new java.util.EnumMap<>(EnumFacing.class);java.nio.ByteBuffer data=buffer.getByteBuffer();int stride=buffer.getVertexFormat().getNextOffset(),color=buffer.getVertexFormat().getColorOffset(),light=buffer.getVertexFormat().getUvOffsetById(1);
        for(int i=0;i<buffer.getVertexCount();i+=4){Vec3d[] v=new Vec3d[3];for(int j=0;j<3;j++){int offset=(i+j)*stride;v[j]=new Vec3d(data.getFloat(offset),data.getFloat(offset+4),data.getFloat(offset+8));}Vec3d n=v[1].subtract(v[0]).crossProduct(v[2].subtract(v[0]));EnumFacing face=EnumFacing.getFacingFromVector((float)n.x,(float)n.y,(float)n.z);
            for(int j=0;j<4;j++){int offset=(i+j)*stride;int[] values={data.getInt(offset+color),data.getInt(offset+light)};int[] previous=result.put(face,values);require(previous==null||java.util.Arrays.equals(previous,values),"uniform lit air must not darken individual stair patches "+face);}
        }buffer.reset();return result;
    }
    private static Vec3d point(BakedQuad q,int v){int[] d=q.getVertexData();int a=v*d.length/4;return new Vec3d(Float.intBitsToFloat(d[a]),Float.intBitsToFloat(d[a+1]),Float.intBitsToFloat(d[a+2]));}
    private static boolean contains(BakedQuad q,Vec3d p){Vec3d a=point(q,0),u=point(q,1).subtract(a),v=point(q,3).subtract(a),d=p.subtract(a);double x=d.dotProduct(u)/u.lengthSquared(),y=d.dotProduct(v)/v.lengthSquared();return Math.abs(d.dotProduct(u.crossProduct(v)))<1e-7&&x>=-1e-7&&x<=1+1e-7&&y>=-1e-7&&y<=1+1e-7;}
    private static double uv(BakedQuad q,Vec3d p,int uv){Vec3d a=point(q,0),u=point(q,1).subtract(a),v=point(q,3).subtract(a),d=p.subtract(a);int[] data=q.getVertexData();int stride=data.length/4;double origin=Float.intBitsToFloat(data[uv]),value=origin+d.dotProduct(u)/u.lengthSquared()*(Float.intBitsToFloat(data[stride+uv])-origin)+d.dotProduct(v)/v.lengthSquared()*(Float.intBitsToFloat(data[3*stride+uv])-origin);return 16*(value-(uv==4?q.getSprite().getMinU():q.getSprite().getMinV()))/(uv==4?q.getSprite().getMaxU()-q.getSprite().getMinU():q.getSprite().getMaxV()-q.getSprite().getMinV());}
}
