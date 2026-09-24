package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockVoxelWall;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Forge runtime checks for all voxel wall models and stepped collision. */
final class ThinWallRuntimeChecks {
    private ThinWallRuntimeChecks() {}
    static void run(World world, EntityPlayer player) {
        String[] shapes={"regular","porthole","bottom_diagonal","top_diagonal"};
        for (String shape:shapes) for (String suffix:new String[]{"","_bordered"}) {
            String id="voxel_wall_"+shape+suffix;
            Block raw=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs",id));
            require(raw instanceof BlockVoxelWall,id+" registration");
            BlockVoxelWall wall=(BlockVoxelWall)raw;
            ItemStack item=new ItemStack(wall);
            require(Minecraft.getMinecraft().getRenderItem().getItemModelMesher().getItemModel(item)
                    !=Minecraft.getMinecraft().getRenderItem().getItemModelMesher()
                    .getModelManager().getMissingModel(),id+" item model");
            IBlockState placed=wall.getStateForPlacement(world,BlockPos.ORIGIN,EnumFacing.UP,
                    .5F,.5F,.5F,0,player,EnumHand.MAIN_HAND);
            require(wall.getStateFromMeta(wall.getMetaFromState(placed)).equals(placed),
                    id+" facing metadata");
            List<AxisAlignedBB> boxes=new ArrayList<>();
            wall.addCollisionBoxToList(wall.getDefaultState(),world,BlockPos.ORIGIN,
                    new AxisAlignedBB(0,0,0,1,1,1),boxes,null,false);
            require(boxes.size()==(id.contains("diagonal")?9:1),id+" collision bands");
            if (id.contains("diagonal"))
                require(boxes.stream().allMatch(box->box.maxZ<=.875),id+" rear space filled");
        }
        System.out.println("[vandorlabs][reprolab] thin-wall-runtime PASS");
    }
    private static void require(boolean condition,String message) {
        if (!condition) throw new IllegalStateException("thin wall: "+message);
    }
}
