package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.items.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Real-world placement, joining, obstruction, copying and crafting contracts. */
final class Version12RuntimeChecks {
    static void run(World world,EntityPlayerMP player) {
        BlockPos p=new BlockPos(62,245,40);
        try {
            BlockConnectedSeat seat=(BlockConnectedSeat)block("luxury_seat");
            BlockConnectedSeat military=(BlockConnectedSeat)block("military_seat");
            for (EnumFacing facing:EnumFacing.HORIZONTALS) {
                IBlockState state=seat.getDefaultState().withProperty(BlockVandorDirectional.FACING,facing);
                EnumFacing right=facing.rotateY();
                world.setBlockState(p,state,3);seat.onBlockPlacedBy(world,p,state,player,new ItemStack(seat));
                require(world.getBlockState(p.up()).getValue(BlockBridgeChair.UPPER),"seat upper reservation");
                require(!seat.canPlaceBlockAt(world,p.down()),"seat accepted blocked headroom");
                world.setBlockState(p.offset(right),state,3);
                require(seat.getActualState(state,world,p).getValue(BlockConnectedSeat.PART)==BlockConnectedSeat.Part.LEFT,"seat left end");
                world.setBlockState(p.offset(right.getOpposite()),state,3);
                require(seat.getActualState(state,world,p).getValue(BlockConnectedSeat.PART)==BlockConnectedSeat.Part.MIDDLE,"seat middle");
                world.setBlockState(p.offset(right),military.getDefaultState(),3);
                require(seat.getActualState(state,world,p).getValue(BlockConnectedSeat.PART)==BlockConnectedSeat.Part.RIGHT,"mixed seat styles joined");
                world.setBlockToAir(p.offset(right));world.setBlockToAir(p.offset(right.getOpposite()));
                world.setBlockToAir(p);require(world.isAirBlock(p.up()),"orphan seat back");
            }
            BlockTelescopicLandingGear gear=(BlockTelescopicLandingGear)block("landing_gear_top_small_telescopic");
            world.setBlockState(p,gear.getDefaultState(),3);world.setBlockState(p.down(),Blocks.STONE.getDefaultState(),3);
            require(!gear.setExtended(world,p,true),"gear extended through stone");world.setBlockToAir(p.down());
            require(gear.setExtended(world,p,true),"gear failed clear extension");
            TileEntityLandingGear tile=(TileEntityLandingGear)world.getTileEntity(p);
            for (int i=0;i<21;i++) tile.update();
            require(tile.progress==1 && world.getBlockState(p.down()).getValue(BlockTelescopicLandingGear.LOWER),"gear extension endpoint");
            java.util.List<AxisAlignedBB> boxes=new java.util.ArrayList<>();
            gear.addCollisionBoxToList(world.getBlockState(p.down()),world,p.down(),new AxisAlignedBB(p.down()),boxes,null,false);
            require(!boxes.isEmpty(),"extended wheel has no lower collision");
            require(gear.setExtended(world,p,false),"gear retraction rejected");
            for (int i=0;i<21;i++) tile.update();
            require(world.getBlockState(p).getBlock()==gear && world.isAirBlock(p.down()),"gear removed root on retract");
            gear.setExtended(world,p,true);world.destroyBlock(p.down(),false);
            require(world.isAirBlock(p),"gear root survived wheel removal");
            BlockProgrammableWall diagonal=(BlockProgrammableWall)ModBlocks.PROGRAMMABLE_DIAGONAL_WALL;
            for (int mode=0;mode<3;mode++) for (int fill=0;fill<4;fill++) {
                world.setBlockState(p,diagonal.getDefaultState(),3);
                TileEntityAnimatedScreenSelector settings=(TileEntityAnimatedScreenSelector)world.getTileEntity(p);
                settings.setDiagonalGeometry(mode,fill);
                boxes.clear();diagonal.addCollisionBoxToList(world.getBlockState(p),world,p,new AxisAlignedBB(p),boxes,null,false);
                require(!boxes.isEmpty(),"empty diagonal collision");
                for (AxisAlignedBB box:boxes) require(box.minY>=p.getY()-1e-8 && box.maxY<=p.getY()+1+1e-8,"diagonal collision outside block");
                if (mode==2 && fill==0) for (AxisAlignedBB box:boxes) require(box.maxY<=p.getY()+.5+1e-8,"half-height exceeds half block");
                NBTTagCompound saved=settings.writeToNBT(new NBTTagCompound());
                TileEntityAnimatedScreenSelector restored=new TileEntityAnimatedScreenSelector();restored.readFromNBT(saved);
                require(restored.getDiagonalFill()==fill && restored.isDiagonalHalfHeight()==(mode==2),"diagonal saved geometry");
            }
            checkCrafting(world,p,player);
            for (Block block:Block.REGISTRY) if (block.getRegistryName()!=null && block.getRegistryName().getResourceDomain().equals("vandorlabs")
                    && block.getRegistryName().getResourcePath().startsWith("space_") && block.getRegistryName().getResourcePath().contains("door"))
                require(block.getCreativeTabToDisplayOn()==null,"legacy door remains creative: "+block.getRegistryName());
        } finally {
            for (BlockPos clear:BlockPos.getAllInBox(p.add(-2,-2,-2),p.add(2,2,2))) world.setBlockToAir(clear);
        }
        System.out.println("[vandorlabs][reprolab] version-1.2-runtime PASS");
    }
    private static void checkCrafting(World world,BlockPos p,EntityPlayerMP player) {
        world.setBlockState(p,ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),3);
        TileEntityAnimatedScreenSelector source=(TileEntityAnimatedScreenSelector)world.getTileEntity(p);
        source.setHousingTexture(3);source.setFaceTextures(new FaceTextures(true,new int[]{-1,2,4,-1,5,-1}));
        ItemStack tool=new ItemStack(ModItems.DUPLIFIER);ItemDuplifier.copyFrom(world,p,tool);
        InventoryCrafting grid=new InventoryCrafting(new Container(){public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer p){return true;}},2,2);
        RecipeCopiedSettings recipe=new RecipeCopiedSettings();
        for (Block block:new Block[]{ModBlocks.PROGRAMMABLE_BLOCK,ModBlocks.PROGRAMMABLE_SLAB,ModBlocks.PROGRAMMABLE_WALL,
                ModBlocks.PROGRAMMABLE_LIGHT,ModBlocks.PROGRAMMABLE_TRIGGER_BLOCK,
                block("programmable_diagonal_porthole"),block("programmable_diagonal_half_console")}) {
            grid.setInventorySlotContents(0,tool);grid.setInventorySlotContents(1,new ItemStack(block,64));
            ItemStack output=recipe.getCraftingResult(grid);
            // A chair has no housing setting, but can still receive the shared channel.
            require(!output.isEmpty() && output.getCount()==1,"crafting unsupported programmable item "+block);
            require(grid.getStackInSlot(1).getCount()==64,"recipe preview consumed inputs");
            require(ItemStack.areItemStacksEqual(recipe.getRemainingItems(grid).get(0),tool),"recipe lost copied tool data");
        }
        grid.setInventorySlotContents(1,new ItemStack(ModBlocks.PROGRAMMABLE_BLOCK,64));
        ItemStack output=recipe.getCraftingResult(grid);
        require(output.getSubCompound("BlockEntityTag").getBoolean("FaceTexturesEnabled"),"recipe lost enabled overrides");
        source.setFaceTextures(new FaceTextures(false,new int[]{1,1,1,1,1,1}));ItemDuplifier.copyFrom(world,p,tool);
        grid.setInventorySlotContents(1,output);
        output=recipe.getCraftingResult(grid);
        require(!output.getSubCompound("BlockEntityTag").getBoolean("FaceTexturesEnabled")
                && output.getSubCompound("BlockEntityTag").getIntArray("FaceTextures")[1]==2,"disabled recipe changed face choices");
        grid.setInventorySlotContents(2,new ItemStack(Blocks.STONE));
        require(recipe.getCraftingResult(grid).isEmpty(),"recipe accepted extra ingredient");
    }
    private static Block block(String id) { return Block.REGISTRY.getObject(new ResourceLocation("vandorlabs",id)); }
    private static void require(boolean value,String message) { if (!value) throw new IllegalStateException(message); }
}
