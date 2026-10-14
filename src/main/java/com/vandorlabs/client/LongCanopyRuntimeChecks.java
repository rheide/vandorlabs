package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.canopy.*;
import com.vandorlabs.tiles.TileEntityCanopy;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Full depth shells, wide assembly links and item bounds in the live client world. */
final class LongCanopyRuntimeChecks {
    private static final BlockPos BASE=new BlockPos(8,110,8);
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("long canopy: "+message);}
    static void check(World world){
        for(String id:new String[]{"canopy","long_canopy"}) {
            BlockCanopy block=(BlockCanopy)Block.getBlockFromName("vandorlabs:"+id);
            require(block!=null,"registered "+id);
            for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int motion=0;motion<2;motion++) {
                for(BlockPos pos:BlockPos.getAllInBox(BASE.add(-12,-5,-12),BASE.add(12,8,12)))world.setBlockToAir(pos);
                IBlockState state=block.getDefaultState().withProperty(BlockCanopy.FACING,facing);
                ItemStack item=new ItemStack(block);NBTTagCompound settings=new NBTTagCompound();settings.setInteger("CanopyMotion",motion);item.setTagCompound(settings);
                // Two separate groups merge when the missing center constituent is placed.
                for(int column:new int[]{0,1,2,3,5,6,7,8,4,9}) {
                    BlockPos pos=BASE.offset(facing.rotateY(),column);world.setBlockState(pos,state,2);block.onBlockPlacedBy(world,pos,state,null,item);
                }
                TileEntityCanopy owner=TileEntityCanopy.at(world,BASE);
                require(owner.draws() && owner.width()==9,"nine-wide merge");
                require(TileEntityCanopy.at(world,BASE.offset(facing.rotateY(),9)).width()==1,"tenth remains separate");
                for(int column=0;column<9;column++)for(BlockPos cell:block.cells(BASE.offset(facing.rotateY(),column),facing))require(TileEntityCanopy.at(world,cell).owner()==owner,"all occupied cells resolve owner");
                NBTTagCompound saved=owner.writeToNBT(new NBTTagCompound());TileEntityCanopy restored=new TileEntityCanopy();restored.setWorld(world);restored.readFromNBT(saved);require(restored.width()==9,"NBT keeps nine-wide join");
                for(int angle=0;angle<(block.length==3?4:3);angle++) {
                    for(int column=0;column<10;column++)for(BlockPos cell:block.cells(BASE.offset(facing.rotateY(),column),facing))TileEntityCanopy.at(world,cell).large.angle=angle;
                    for(double progress:new double[]{0,.5,1}) {
                        List<CanopyMesh.Face> faces=owner.faces(progress);require(!faces.isEmpty(),"posed geometry");
                        for(CanopyMesh.Face face:faces)for(Vec3d vertex:face.vertices)require(Double.isFinite(vertex.x) && Double.isFinite(vertex.y) && Double.isFinite(vertex.z),"finite geometry");
                    }
                    AxisAlignedBB bounds=owner.selectionBounds();double depth=facing.getAxis()==EnumFacing.Axis.Z?bounds.maxZ-bounds.minZ:bounds.maxX-bounds.minX;
                    require(depth>=block.length-.01,"full footprint selection depth");
                    icon(id,angle);
                }
                owner.toggle();for(int tick=0;tick<16;tick++)owner.update();require(owner.progress==1,"shared open");
                owner.toggle();for(int tick=0;tick<16;tick++)owner.update();require(owner.progress==0,"shared close");
                world.setBlockToAir(BASE.offset(facing.rotateY(),4).offset(facing.getOpposite(),block.length-1));
                require(TileEntityCanopy.at(world,BASE).width()==4,"left survivors rejoin after rear follower removal");
                require(TileEntityCanopy.at(world,BASE.offset(facing.rotateY(),5)).width()==5,"right survivors rejoin including tenth");
            }
        }
        for(BlockPos pos:BlockPos.getAllInBox(BASE.add(-12,-5,-12),BASE.add(12,8,12)))world.setBlockToAir(pos);
        System.out.println("[vandorlabs][reprolab] long-canopy PASS depths=2,3 widths=9 facings=4 motions=2 all-shapes NBT split follower-removal padded-icons");
    }
    private static void icon(String id,int angle){
        LargeCanopySettings settings=new LargeCanopySettings();settings.angle=angle;
        CanopyModels.Icon model=new CanopyModels.Icon(id,settings);
        double minX=100,minY=100,maxX=-100,maxY=-100;
        for(net.minecraft.client.renderer.block.model.BakedQuad quad:model.getQuads(null,null,0)) {
            int[] data=quad.getVertexData();int stride=data.length/4;
            for(int i=0;i<4;i++){Vec3d v=ControlItemPadding.project(new Vec3d(Float.intBitsToFloat(data[i*stride]),Float.intBitsToFloat(data[i*stride+1]),Float.intBitsToFloat(data[i*stride+2])),model.getItemCameraTransforms().gui);v=v.addVector(model.getItemCameraTransforms().gui.translation.x,model.getItemCameraTransforms().gui.translation.y,0);minX=Math.min(minX,v.x);maxX=Math.max(maxX,v.x);minY=Math.min(minY,v.y);maxY=Math.max(maxY,v.y);}
        }
        require(minX>=-.38 && maxX<=.38 && minY>=-.38 && maxY<=.38,"padding after GUI rotation for "+id+" angle "+angle);
    }
    private LongCanopyRuntimeChecks(){}
}
