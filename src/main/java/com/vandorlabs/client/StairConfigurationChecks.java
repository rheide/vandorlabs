package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.items.*;
import com.vandorlabs.tiles.*;
import java.util.*;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

final class StairConfigurationChecks {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("stair configuration: "+message);}
    static void server(WorldServer world,EntityPlayerMP player){
        BlockPos pos=new BlockPos(300,40,146);BlockProgrammableStairs block=(BlockProgrammableStairs)ModBlocks.PROGRAMMABLE_STAIRS;int cases=0;
        try{
            for(EnumFacing facing:EnumFacing.HORIZONTALS)for(BlockStairs.EnumHalf half:BlockStairs.EnumHalf.values())for(int tread:new int[]{1,2,4,8,16})for(int roll=0;roll<3;roll++){
                IBlockState state=block.getDefaultState().withProperty(BlockStairs.FACING,facing).withProperty(BlockStairs.HALF,half);world.setBlockState(pos,state,2);
                TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);tile.setStairGeometry(tread,roll);
                List<AxisAlignedBB> boxes=new ArrayList<>();block.addCollisionBoxToList(state,world,pos,new AxisAlignedBB(pos),boxes,null,false);double solid=0;
                for(AxisAlignedBB box:boxes){solid+=(box.maxX-box.minX)*(box.maxY-box.minY)*(box.maxZ-box.minZ);require(new AxisAlignedBB(pos).union(box).equals(new AxisAlignedBB(pos)),"collision contained");}
                require(Math.abs(solid-(1+tread/16D)/2)<1e-7,"solid stair volume "+tread+"/"+roll);
                TileEntityAnimatedScreenSelector saved=(TileEntityAnimatedScreenSelector)net.minecraft.tileentity.TileEntity.create(world,tile.writeToNBT(new NBTTagCompound()));require(saved.stairGeometry()==tile.stairGeometry(),"saved geometry");
                NBTTagCompound settings=ProgrammableSettings.capture(world,pos);ItemStack configured=ProgrammableSettings.applyToItem(new ItemStack(block),settings);
                require(configured.getSubCompound("BlockEntityTag").getInteger("StairTread")==tread&&configured.getSubCompound("BlockEntityTag").getInteger("StairRoll")==roll,"configured item copy");
                tile.setStairGeometry(8,0);require(ProgrammableSettings.apply(world,pos,settings,player)&&tile.stairTread()==tread&&tile.stairRoll()==roll,"Duplifier geometry");
                RayTraceResult ray=world.rayTraceBlocks(new Vec3d(pos).addVector(.5,2,.5),new Vec3d(pos).addVector(.5,-1,.5));require(ray!=null&&ray.getBlockPos().equals(pos),"selection ray");
                if(tread==1){double water=0;for(WaterloggedParts.Cell cell:WaterloggedParts.volume(world,pos).cells){AxisAlignedBB b=cell.box;water+=(b.maxX-b.minX)*(b.maxY-b.minY)*(b.maxZ-b.minZ);}require(Math.abs(water+solid-1)<1e-6,"water geometry follows stairs");}
                for(EnumFacing face:EnumFacing.values()){
                    boolean covered=ProgrammableStairGeometry.solidFace(block.getActualState(state,world,pos),tile.stairGeometry(),face);
                    require(block.doesSideBlockRendering(state,world,pos,face)==covered,"neighbor face culling follows occupied geometry "+facing+"/"+half+"/"+tread+"/"+roll+"/"+face);
                    BlockPos neighbor=pos.offset(face);world.setBlockState(neighbor,Blocks.STONE.getDefaultState(),2);
                    require(Blocks.STONE.getDefaultState().shouldSideBeRendered(world,neighbor,face.getOpposite())==!covered,"actual neighboring stone face remains visible through empty stair space "+face+"/"+roll);
                    world.setBlockToAir(neighbor);
                }
                cases++;
            }
            for(EnumFacing side:EnumFacing.HORIZONTALS){
                IBlockState state=block.getStateForPlacement(world,pos,side,.5F,.5F,.5F,0,player);world.setBlockToAir(pos);
                ItemStack stack=new ItemStack(block);require(((net.minecraft.item.ItemBlock)stack.getItem()).placeBlockAt(stack,player,world,pos,side,.5F,.5F,.5F,state),"side placement");
                require(((TileEntityAnimatedScreenSelector)world.getTileEntity(pos)).stairRoll()==0,"wall placement remains normal stairs "+side);
            }
            TileEntityAnimatedScreenSelector legacy=new TileEntityAnimatedScreenSelector();legacy.readFromNBT(new NBTTagCompound());require(legacy.stairGeometry()==8,"legacy geometry unchanged");
        }finally{world.setBlockToAir(pos);}
        System.out.println("[vandorlabs][reprolab] stair-configuration PASS cases="+cases+" five step sizes three saved rolls four facings both halves collision selection persistence copied items water volume normal wall placement neighbor face visibility");
    }
    static void client(Minecraft mc)throws Exception{
        BlockPos pos=new BlockPos(300,40,146);int cases=0;
        try{for(int tread:new int[]{1,2,4,8,16})for(int roll=0;roll<3;roll++)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(BlockStairs.EnumHalf half:BlockStairs.EnumHalf.values())for(BlockStairs.EnumShape corner:BlockStairs.EnumShape.values()){
            IBlockState clean=ModBlocks.PROGRAMMABLE_STAIRS.getDefaultState().withProperty(BlockStairs.FACING,facing).withProperty(BlockStairs.HALF,half).withProperty(BlockStairs.SHAPE,corner);
            mc.world.setBlockState(pos,clean,2);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)mc.world.getTileEntity(pos);tile.setStairGeometry(tread,roll);
            IBlockState state=ModBlocks.PROGRAMMABLE_STAIRS.getExtendedState(clean,mc.world,pos);IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(clean);
            List<BakedQuad> quads=new ArrayList<>(model.getQuads(state,null,0));for(EnumFacing face:EnumFacing.values())quads.addAll(model.getQuads(state,face,0));require(!quads.isEmpty(),"rendered geometry");
            double volume=0;for(BakedQuad quad:quads){int[] data=quad.getVertexData();int stride=data.length/4;Vec3d[] p=new Vec3d[4];for(int i=0;i<4;i++)p[i]=new Vec3d(Float.intBitsToFloat(data[i*stride]),Float.intBitsToFloat(data[i*stride+1]),Float.intBitsToFloat(data[i*stride+2]));
                for(int i=1;i<3;i++)volume+=p[0].dotProduct(p[i].crossProduct(p[i+1]))/6;
                require(!quad.getSprite().getIconName().contains("missingno"),"texture present: "+quad.getSprite().getIconName()+" model="+model.getClass().getName());
            }
            if(roll!=0){
                net.minecraft.client.renderer.texture.TextureAtlasSprite sprite=mc.getTextureMapBlocks().getAtlasSprite("minecraft:blocks/planks_oak");
                List<BakedQuad> normal=ProgrammableStairMesh.quads(clean,tread,sprite),mounted=ProgrammableStairMesh.quads(clean,tread|(roll<<5),sprite);
                require(normal.size()==mounted.size(),"rolled surface count");
                for(int q=0;q<normal.size();q++){
                    BakedQuad before=normal.get(q),after=mounted.get(q);
                    require(ProgrammableStairGeometry.unrolledFace(clean,roll,after.getFace())==before.getFace(),"face texture follows mount");
                    for(int v=0;v<4;v++)for(int uv=4;uv<=5;uv++)require(before.getVertexData()[v*7+uv]==after.getVertexData()[v*7+uv],"UV rotates with mounted surface "+facing+"/"+half+"/"+roll);
                }
            }
            double expected=0;int count=16/tread;for(int x=0;x<count;x++)for(int z=0;z<count;z++)expected+=ProgrammableStairGeometry.height(x,z,count,corner)/(count*count);
            require(Math.abs(volume-expected)<1e-6,"rendered mesh volume matches collision "+tread+"/"+roll+" actual="+volume);cases++;
        }}finally{mc.world.setBlockToAir(pos);}
        mountedTextures(mc,pos);
        System.out.println("[vandorlabs][reprolab] stair-mesh PASS variants="+cases+" outward winding closed surface collision volume parity textures");
    }
    private static void mountedTextures(Minecraft mc,BlockPos pos){
        try{for(EnumFacing facing:EnumFacing.HORIZONTALS)for(BlockStairs.EnumHalf half:BlockStairs.EnumHalf.values())for(int layout=0;layout<3;layout++){
            IBlockState clean=ModBlocks.PROGRAMMABLE_STAIRS.getDefaultState().withProperty(BlockStairs.FACING,facing).withProperty(BlockStairs.HALF,half);
            mc.world.setBlockState(pos,clean,2);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)mc.world.getTileEntity(pos);
            tile.setFaceTextures(new FaceTextures(true,new int[]{1,2,3,4,5,6}));tile.setMainTextureLayout(layout);tile.setStairGeometry(4,0);
            IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(clean);
            List<BakedQuad> reference=model.getQuads(ModBlocks.PROGRAMMABLE_STAIRS.getExtendedState(clean,mc.world,pos),null,0);
            for(int roll=1;roll<=2;roll++){
                tile.setStairGeometry(4,roll);List<BakedQuad> mounted=model.getQuads(ModBlocks.PROGRAMMABLE_STAIRS.getExtendedState(clean,mc.world,pos),null,0);
                require(reference.size()==mounted.size(),"textured mounted surface count");
                for(int q=0;q<reference.size();q++){
                    BakedQuad before=reference.get(q),after=mounted.get(q);require(before.getSprite()==after.getSprite(),"configured face material rotates with stair");
                    int stride=before.getVertexData().length/4;
                    for(int v=0;v<4;v++)for(int uv=4;uv<=5;uv++)require(before.getVertexData()[v*stride+uv]==after.getVertexData()[v*stride+uv],"final textured UV follows rotation in layout "+layout);
                }
            }
        }}finally{mc.world.setBlockToAir(pos);}
    }

}
