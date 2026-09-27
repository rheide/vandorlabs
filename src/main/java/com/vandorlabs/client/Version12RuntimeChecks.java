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
                TileEntityConnectedSeat settings=(TileEntityConnectedSeat)world.getTileEntity(p);
                require(settings.getHeightOffsetPixels()==1,"default seat leg height");
                settings.setJoin(false);require(seat.getActualState(state,world,p).getValue(BlockConnectedSeat.PART)==BlockConnectedSeat.Part.SINGLE,"seat Join Off");
                settings.setJoin(true);
                for(int h=0;h<3;h++){
                    settings.setHeight(h);require(settings.getHeightOffsetPixels()==2*h-1,"seat height offset");
                    require(seat.seatHeight(world,p)==(8+2*h)/16D,"seat rider height");
                    require(seat.getBoundingBox(world.getBlockState(p.up()),world,p.up()).maxY==.5+(2*h-1)/16D,"seat collision height");
                    require((seat.getActualState(state,world,p).getValue(BlockConnectedSeat.PART)==BlockConnectedSeat.Part.MIDDLE)==(h==1),"different seat heights joined");
                    TileEntityConnectedSeat restored=new TileEntityConnectedSeat();restored.readFromNBT(settings.writeToNBT(new NBTTagCompound()));
                    require(restored.getHeight()==h&&restored.isJoin(),"seat saved settings");
                }
                settings.setHeight(1);
                world.setBlockState(p.offset(right),military.getDefaultState(),3);
                require(seat.getActualState(state,world,p).getValue(BlockConnectedSeat.PART)==BlockConnectedSeat.Part.RIGHT,"mixed seat styles joined");
                world.setBlockToAir(p.offset(right));world.setBlockToAir(p.offset(right.getOpposite()));
                world.setBlockToAir(p);require(world.isAirBlock(p.up()),"orphan seat back");
            }
            java.util.List<AxisAlignedBB> boxes=new java.util.ArrayList<>();
            for(String name:new String[]{"small_landing_gear","large_landing_gear"}) {
                BlockTelescopicLandingGear gear=(BlockTelescopicLandingGear)block(name);
                world.setBlockState(p,gear.getDefaultState(),3);
                TileEntityLandingGear tile=(TileEntityLandingGear)world.getTileEntity(p);
                NBTTagCompound itemSettings=tile.writeToNBT(new NBTTagCompound());
                itemSettings.setInteger("RedstoneMode",2);itemSettings.setInteger("ExtensionPixels",0);
                tile.readFromNBT(itemSettings);gear.onBlockPlacedBy(world,p,world.getBlockState(p),player,new ItemStack(gear));
                require(world.getBlockState(p).getValue(BlockTelescopicLandingGear.EXTENDED),"placed Off-mode gear ignored saved settings");
                gear.setExtended(world,p,false);
                require(tile.configure(0,0,64),"gear configuration");
                world.setBlockState(p.down(4),Blocks.STONE.getDefaultState(),3);
                require(!gear.setExtended(world,p,true),"gear extended through stone");
                require(world.isAirBlock(p.down()),"blocked gear left partial reservations");world.setBlockToAir(p.down(4));
                require(gear.setExtended(world,p,true),"gear failed clear extension");
                require(world.getTileEntity(p)==tile,"extension replaced gear tile");
                for(int i=0;i<81;i++)tile.update();
                require(tile.progress==4,"four block endpoint");
                for(int i=1;i<=4;i++) {
                    require(gear.root(world,p.down(i))==tile,"gear reservation owner");
                    NBTTagCompound movedTag=world.getTileEntity(p.down(i)).writeToNBT(new NBTTagCompound());
                    movedTag.setInteger("x",p.getX()+10);
                    TileEntityLandingGear moved=new TileEntityLandingGear();moved.readFromNBT(movedTag);
                    require(moved.owner().equals(p.east(10)),"copied gear reservation retained old owner position");
                    boxes.clear();gear.addCollisionBoxToList(world.getBlockState(p.down(i)),world,p.down(i),new AxisAlignedBB(p.down(i)),boxes,null,false);
                    require(!boxes.isEmpty(),"gear lacks piston/wheel collision");
                }
                ItemStack picked=gear.getPickBlock(world.getBlockState(p.down(4)),null,world,p.down(4),player);
                require(picked.getSubCompound("BlockEntityTag").getInteger("ExtensionPixels")==64,"gear lower pick settings");
                require(gear.setExtended(world,p,false),"gear retraction rejected");
                require(world.getTileEntity(p)==tile,"retraction replaced gear tile");
                tile.update();require(tile.progress>3.9F&&tile.progress<4,"retraction did not animate");
                for(int i=0;i<81;i++)tile.update();
                require(world.getBlockState(p).getBlock()==gear,"retraction removed root");
                for(int i=1;i<=4;i++)require(world.isAirBlock(p.down(i)),"retraction left reservation");
                require(tile.configure(0,0,8),"fractional extension settings");gear.setExtended(world,p,true);
                for(int i=0;i<11;i++)tile.update();require(tile.progress==.5F,"half block endpoint");
                require(tile.configure(0,0,0),"zero extension settings");for(int i=0;i<11;i++)tile.update();
                require(tile.progress==0&&world.isAirBlock(p.down()),"zero extension reservation");
                require(tile.configure(2,0,16),"inverse redstone settings");
                require(world.getBlockState(p).getValue(BlockTelescopicLandingGear.EXTENDED),"Off mode without power");
                world.setBlockState(p.east(),Blocks.REDSTONE_BLOCK.getDefaultState(),3);tile.inputChanged();
                require(!world.getBlockState(p).getValue(BlockTelescopicLandingGear.EXTENDED),"Off mode with power");
                require(tile.configure(1,731,16),"On mode settings");
                require(world.getBlockState(p).getValue(BlockTelescopicLandingGear.EXTENDED),"On mode with power");
                TileEntityLandingGear restored=new TileEntityLandingGear();restored.readFromNBT(tile.writeToNBT(new NBTTagCompound()));
                require(restored.getMode()==1&&restored.getRedstoneChannel()==731&&restored.getExtensionPixels()==16,"gear saved settings");
                require(!tile.configure(3,0,65),"gear accepted invalid settings");
                BlockPos remote=p.add(2,0,0);
                world.setBlockState(remote,gear.getDefaultState(),3);
                TileEntityLandingGear linked=(TileEntityLandingGear)world.getTileEntity(remote);
                require(linked.configure(1,731,16),"linked gear configuration");
                require(world.getBlockState(remote).getValue(BlockTelescopicLandingGear.EXTENDED),"gear did not receive remote channel signal");
                world.setBlockToAir(p.east());tile.inputChanged();
                require(!world.getBlockState(remote).getValue(BlockTelescopicLandingGear.EXTENDED),"gear channel remained powered");
                world.setBlockToAir(remote);

                tile.configure(0,0,16);world.setBlockToAir(p.east());
                gear.setExtended(world,p,true);world.destroyBlock(p.down(),false);
                require(world.isAirBlock(p),"gear root survived wheel removal");
            }
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
            BlockProgrammableWall porthole=(BlockProgrammableWall)block("programmable_diagonal_porthole");
            for (int shape=0;shape<4;shape++) {
                world.setBlockState(p,porthole.getDefaultState(),3);
                world.setBlockState(p.east(),porthole.getDefaultState(),3);
                TileEntityAnimatedScreenSelector a=(TileEntityAnimatedScreenSelector)world.getTileEntity(p);
                TileEntityAnimatedScreenSelector b=(TileEntityAnimatedScreenSelector)world.getTileEntity(p.east());
                a.setPortholeShape(shape);b.setPortholeShape(shape);
                require(TEAnimatedScreenSelector.portholeGroup(a,world.getBlockState(p)).columns==2,"diagonal portholes did not join");
                b.setDiagonalFullWidth(true);
                require(TEAnimatedScreenSelector.portholeGroup(a,world.getBlockState(p)).columns==1,"different-width portholes joined");
                b.setDiagonalFullWidth(false);a.setJoinPortholes(false);
                require(TEAnimatedScreenSelector.portholeGroup(a,world.getBlockState(p)).columns==1,"disabled porthole join");
                world.setBlockToAir(p.east());world.setBlockToAir(p);
            }
            Block half=block("programmable_diagonal_half_console");
            for (boolean upper:new boolean[]{false,true}) {
                world.setBlockState(p.south(),ModBlocks.PROGRAMMABLE_SLAB.getDefaultState().withProperty(BlockProgrammableSlab.HALF,
                        upper?net.minecraft.block.BlockSlab.EnumBlockHalf.TOP:net.minecraft.block.BlockSlab.EnumBlockHalf.BOTTOM),3);
                IBlockState placed=half.getStateForPlacement(world,p,EnumFacing.NORTH,.5F,upper?.2F:.8F,.5F,0,player);
                require(placed.getValue(BlockDiagonalHalfConsole.UPPER)==upper,"half console mismatched support half");
                AxisAlignedBB bounds=half.getBoundingBox(placed,world,p);
                require(bounds.maxY-bounds.minY==.5 && bounds.maxZ-bounds.minZ==.5,"half console dimensions");
                world.setBlockToAir(p.south());
            }
            checkCrafting(world,p,player);
            for (Block block:Block.REGISTRY) if (block.getRegistryName()!=null && block.getRegistryName().getResourceDomain().equals("vandorlabs")
                    && block.getRegistryName().getResourcePath().startsWith("space_") && block.getRegistryName().getResourcePath().contains("door"))
                require(block.getCreativeTabToDisplayOn()==null,"legacy door remains creative: "+block.getRegistryName());
        } finally {
            for (BlockPos clear:BlockPos.getAllInBox(p.add(-2,-4,-2),p.add(2,2,2))) world.setBlockToAir(clear);
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
