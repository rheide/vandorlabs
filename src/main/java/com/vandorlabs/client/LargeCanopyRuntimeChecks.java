package com.vandorlabs.client;
import com.vandorlabs.canopy.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.init.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.item.ItemStack;
import java.util.*;
/** Extreme shape, shared geometry, ownership rejection, persistence, palette and projected icon bounds. */
public final class LargeCanopyRuntimeChecks {
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException("large-canopy: "+text);}
    public static void run(EntityPlayerMP player){
        for(int width:new int[]{3,5})for(int depth:new int[]{0,1,15,16,17,79,80})for(int scale:new int[]{10,71,100}){
            LargeCanopySettings settings=new LargeCanopySettings();settings.depth=depth;settings.scale=scale;List<CanopyMesh.Face> faces=LargeCanopyGeometry.faces(width,settings);require(!faces.isEmpty(),"empty shape");
            for(CanopyMesh.Face face:faces)for(Vec3d v:face.vertices)require(Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z) && v.x>=0 && v.x<=width && v.y>=0 && v.y<=width && v.z>=0 && v.z<=Math.max(1D/64,depth/16D),"shape bounds");
            for(EnumFacing facing:EnumFacing.HORIZONTALS){List<AxisAlignedBB> boxes=CanopyMesh.collision(faces,"large_front_canopy_glass",width,0,facing,BlockPos.ORIGIN);require(!boxes.isEmpty(),"empty collision");for(AxisAlignedBB box:boxes)require(box.maxX>box.minX && box.maxY>box.minY && box.maxZ>box.minZ,"zero-volume collision");
                Vec3d start=CanopyMesh.rotate(new Vec3d(width/2D,width/2D,-1),facing),end=CanopyMesh.rotate(new Vec3d(width/2D,width/2D,6),facing);require(CanopyMesh.trace(faces,"large_front_canopy_glass",width,0,facing,BlockPos.ORIGIN,start,end)!=null,"center ray missed");}
        }
        for(int shade=0;shade<GlassTextures.IDS.length;shade++)for(int tint=0;tint<3;tint++){LargeCanopySettings settings=new LargeCanopySettings();settings.shade=shade;settings.tint=tint;NBTTagCompound tag=new NBTTagCompound();settings.write(tag);LargeCanopySettings restored=new LargeCanopySettings();restored.read(tag);require(settings.key().equals(restored.key()),"settings NBT");}
        BlockCanopy block=(BlockCanopy)com.vandorlabs.blocks.ModBlocks.BLOCKS.stream().filter(b->b.getRegistryName().getResourcePath().equals("large_front_canopy_glass_5x5")).findFirst().get();
        BlockPos pos=new BlockPos(40,20,40);for(int x=0;x<5;x++)for(int y=0;y<5;y++)for(int z=0;z<5;z++)player.world.setBlockToAir(pos.add(x,y,z));
        player.world.setBlockState(pos,block.getDefaultState(),3);block.onBlockPlacedBy(player.world,pos,player.world.getBlockState(pos),player,new ItemStack(block));TileEntityCanopy tile=TileEntityCanopy.at(player.world,pos);require(tile.cells().size()==25,"default footprint");
        TileEntityCanopy altered=TileEntityCanopy.at(player.world,pos.east());altered.anchor=pos.add(20,0,0);require(!LargeCanopyOwnership.apply(tile,player,80,10,12,2) && tile.large.depth==16,"foreign member was accepted");altered.anchor=pos;
        BlockPos obstruct=pos.add(0,0,2);player.world.setBlockState(obstruct,Blocks.IRON_BLOCK.getDefaultState(),3);require(!LargeCanopyOwnership.apply(tile,player,80,10,12,2) && tile.cells().size()==25 && tile.large.depth==16,"blocked expansion mutated assembly");player.world.setBlockToAir(obstruct);
        require(LargeCanopyOwnership.apply(tile,player,80,10,12,2) && tile.cells().size()==125,"maximum depth footprint");for(BlockPos cell:tile.cells())require(TileEntityCanopy.at(player.world,cell).owner()==tile,"member ownership");
        ItemStack item=block.configuredStack(tile);require(item.hasTagCompound() && item.getTagCompound().getInteger("CanopyDepth")==80,"configured item settings");
        require(LargeCanopyOwnership.apply(tile,player,0,100,0,0) && tile.cells().size()==25 && player.world.isAirBlock(pos.add(0,0,4)),"shrinking leaves stale cells");
        System.out.println("[vandorlabs][reprolab] large-canopy-runtime PASS shapes=42 poses=168 palette=57 resize-protection NBT configured-items");
    }
    public static void icons(Minecraft mc){
        for(net.minecraft.block.Block block:ModBlocks.BLOCKS)if(block instanceof BlockCanopy && LargeCanopySettings.supports(((BlockCanopy)block).kind))for(int depth:new int[]{0,16,80})for(int scale:new int[]{10,71,100})for(int shade=0;shade<GlassTextures.IDS.length;shade++)for(int tint=0;tint<3;tint++){
            LargeCanopySettings settings=new LargeCanopySettings();settings.depth=depth;settings.scale=scale;settings.shade=shade;settings.tint=tint;ItemStack stack=new ItemStack(block);NBTTagCompound tag=new NBTTagCompound();settings.write(tag);stack.setTagCompound(tag);IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(stack,mc.world,mc.player);ItemTransformVec3f gui=model.getItemCameraTransforms().gui;
            for(BakedQuad quad:model.getQuads(null,null,0)){int[] data=quad.getVertexData();int stride=data.length/4;for(int i=0;i<4;i++){Vec3d point=ControlItemPadding.project(new Vec3d(Float.intBitsToFloat(data[i*stride]),Float.intBitsToFloat(data[i*stride+1]),Float.intBitsToFloat(data[i*stride+2])),gui).addVector(gui.translation.x,gui.translation.y,0);require(Math.abs(point.x)<.49 && Math.abs(point.y)<.49,"icon lacks padding "+settings.key());}}
        }
        System.out.println("[vandorlabs][reprolab] large-canopy-icons PASS variants=1026 projected-padding");
    }
}
