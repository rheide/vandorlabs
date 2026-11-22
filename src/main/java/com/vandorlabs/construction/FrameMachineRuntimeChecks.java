package com.vandorlabs.construction;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Independent world-space frames: every core corner, frame axis and placed facing. */
public final class FrameMachineRuntimeChecks {
    private static String scenario;
    public static void run(World world,net.minecraft.entity.player.EntityPlayer player){
        Block matter=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:programmable_block"));
        checkLegacyPalette(world,matter);
        BlockPos minimum=new BlockPos(150,225,150);int count=0;
        for(FrameMachineDesign spec:FrameMachineDesign.all())for(boolean turned:new boolean[]{false,true})for(int corner=0;corner<4;corner++)for(EnumFacing facing:EnumFacing.HORIZONTALS){
            String id=spec.design;BlockConstructorCore block=(BlockConstructorCore)Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:"+spec.block));
            scenario=id+" facing="+facing+" corner="+corner+" turned="+turned;
            int width=turned?block.depth:block.width,depth=turned?block.width:block.depth;
            BlockPos root=minimum.add((corner&1)==0?0:width-1,0,(corner&2)==0?0:depth-1);
            java.util.Map<BlockPos,Boolean> original=new java.util.HashMap<>();
            try{
                world.setBlockState(root,block.getDefaultState().withProperty(BlockVandorDirectional.FACING,facing));
                TileEntityConstructor tile=(TileEntityConstructor)world.getTileEntity(root);
                for(int y=0;y<block.height;y++)for(int x=0;x<width;x++)for(int z=0;z<depth;z++){
                    BlockPos p=minimum.add(x,y,z);if(p.equals(root))continue;
                    boolean frame=y==0 || (x==0 || x==width-1) && (z==0 || z==depth-1) || y==block.height-1 && (x==0 || x==width-1 || z==0 || z==depth-1);
                    original.put(p,frame);world.setBlockState(p,frame?matter.getDefaultState():Blocks.AIR.getDefaultState());
                }
                if(width>2&&depth>2){BlockPos middle=minimum.add(1,1,1);world.setBlockState(middle,Blocks.DIRT.getDefaultState());
                require(!tile.assemble(),"occupied interior rejects assembly "+id+facing);
                require(tile.assemblyProblem().contains("Interior must be air"),"actionable obstruction diagnostic");world.setBlockToAir(middle);}
                BlockPos missing=original.entrySet().stream().filter(e->e.getValue()).map(e->e.getKey()).findFirst().get();world.setBlockToAir(missing);
                require(!tile.assemble(),"missing base rejects assembly");world.setBlockState(missing,matter.getDefaultState());
                TileEntityAnimatedScreenSelector selectedFrame=(TileEntityAnimatedScreenSelector)world.getTileEntity(missing);
                selectedFrame.setHousingTexture(0);
                require(tile.assemble() && tile.formed(),"frame assembles "+id+facing+" corner="+corner+" turned="+turned);
                net.minecraft.block.state.IBlockState formed=world.getBlockState(root);
                require(!formed.isOpaqueCube()&&!formed.isFullCube()&&formed.getLightOpacity()==0,"formed core cannot hide neighboring terrain through model cutouts");
                for(EnumFacing side:EnumFacing.values())require(!block.doesSideBlockRendering(formed,world,root,side),"formed core leaves adjacent faces visible "+side);
                for(BlockPos p:original.keySet())require(world.getTileEntity(p) instanceof TileEntityConstructorPart && ((TileEntityConstructorPart)world.getTileEntity(p)).owner().equals(root),"owned world-space footprint");
                require(root.add(tile.renderOriginOffset()).equals(minimum),"render origin "+scenario);
                AxisAlignedBB bounds=tile.getRenderBoundingBox();
                require(bounds.minX==minimum.getX()-.5 && bounds.minY==minimum.getY()-.5
                        && bounds.minZ==minimum.getZ()-.5 && bounds.maxX==minimum.getX()+width+.5
                        && bounds.maxY==minimum.getY()+block.height+.5 && bounds.maxZ==minimum.getZ()+depth+.5,
                        "padded rotated render bounds "+scenario);
                TileEntityConstructor reloaded=new TileEntityConstructor();reloaded.setWorld(world);reloaded.readFromNBT(tile.writeToNBT(new NBTTagCompound()));
                require(reloaded.pivot().equals(tile.pivot()) && reloaded.cell(0,0,0).equals(tile.cell(0,0,0)),"saved corner offset");
                TileEntityConstructor client=new TileEntityConstructor();client.setWorld(world);client.setPos(root);client.readFromNBT(tile.getUpdateTag());
                require(client.pivot().equals(tile.pivot()),"client update corner offset");
                tile.dismantle();require(!tile.formed()&&world.getBlockState(root).isOpaqueCube(),"manual dismantling restores the solid core model");checkRestored(world,original,matter);
                require(((TileEntityAnimatedScreenSelector)world.getTileEntity(missing)).getHousingTexture()==0,
                        "mixed finish restored through formation and dismantling");
                checkCosmeticTicks(tile,spec);require(!tile.formed(),"dismantled frame never automatically reforms");
                require(tile.assemble(),"reassembly from non-default corner");require(world.getTileEntity(root)==tile,"core tile identity after reassembly");
                net.minecraft.block.state.IBlockState selected=world.getBlockState(missing);selected.getBlock().removedByPlayer(selected,world,missing,player,false);require(world.isAirBlock(missing)&&!tile.formed(),"mining a reserved part dismantles and removes the clicked cell");checkCosmeticTicks(tile,spec);require(world.isAirBlock(missing)&&!tile.formed(),"mined frame stays dismantled");
                world.setBlockState(missing,matter.getDefaultState());require(tile.assemble(),"explicit repair and reassembly");world.setBlockToAir(root);checkRestored(world,original,matter);count++;
            }finally{
                world.setBlockToAir(root);for(BlockPos p:original.keySet())world.setBlockToAir(p);
            }
        }
        System.out.println("[vandorlabs][reprolab] constructors PASS "+count+" independent frames all corners axes facings diagnostics persistence sync dismantling bounds");
    }
    public static void runPlate(World world){
        BlockPos root=new BlockPos(0,110,0);
        BlockConstructorCore block=(BlockConstructorCore)Block.getBlockFromName("vandorlabs:assembly_press_core");
        FrameMachineDesign design=block.specification();
        world.setBlockState(root,block.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.NORTH));
        TileEntityConstructor press=(TileEntityConstructor)world.getTileEntity(root);
        for(int x=0;x<design.width;x++)for(int y=0;y<design.height;y++)for(int z=0;z<design.depth;z++){
            BlockPos at=press.cell(x,y,z);
            if(!at.equals(root)&&press.frame(x,y,z))world.setBlockState(at,ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState());
        }
        BlockPos corner=press.cell(design.width-1,0,design.depth-1);
        net.minecraft.block.state.IBlockState frame=ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState()
                .withProperty(BlockProgrammableBlock.FACING,EnumFacing.WEST);
        world.setBlockState(corner,frame);
        ((TileEntityAnimatedScreenSelector)world.getTileEntity(corner)).setHousingTexture(0);
        require(press.assemble(),"Forge Plate check forms a complete mixed-finish Press");
        checkPlate(world,press);
        press.dismantle();
        require(world.getBlockState(corner)==frame
                        &&((TileEntityAnimatedScreenSelector)world.getTileEntity(corner)).getHousingTexture()==0,
                "dismantling restores the exact non-default frame facing and finish");
        System.out.println("[vandorlabs][reprolab] programmable plate PASS Forge Press/no Catalog/8000RF/four armor recipes/mixed frame");
    }
    private static void checkPlate(World world,TileEntityConstructor press){
        net.minecraft.item.Item plate=com.vandorlabs.items.ModItems.PROGRAMMABLE_PLATE;
        net.minecraft.item.Item ingot=com.vandorlabs.items.ModItems.PROGRAMMABLE_MATTER_INGOT;
        net.minecraftforge.energy.IEnergyStorage power=world.getTileEntity(press.synthesisPowerPort())
                .getCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY,EnumFacing.DOWN);
        int recipe=press.builderRecipeIndex("programmable_plate"),spent=0;
        require(recipe>=0&&power!=null&&press.getStackInSlot(0).isEmpty(),"Plate available from the formed Press without a Catalog Chip");
        press.configureSynthesisRedstone(0,com.vandorlabs.redstone.ChannelList.EMPTY);
        press.selectDesign(recipe);
        press.synthesisInventory().setStackInSlot(0,new net.minecraft.item.ItemStack(ingot));
        long initialTime=world.getTotalWorldTime();
        try{
            for(int tick=0;tick<450&&press.synthesisInventory().getStackInSlot(1).isEmpty();tick++){
                world.getWorldInfo().setWorldTotalTime(initialTime+tick+1);
                spent+=power.receiveEnergy(1000,false);
                press.update();
            }
        }finally{world.getWorldInfo().setWorldTotalTime(initialTime);}
        require(press.synthesisInventory().getStackInSlot(0).isEmpty()&&!press.processing()&&spent==8000,
                "Forge Press consumes exactly one ingot and 8,000 RF");
        net.minecraft.item.ItemStack output=press.synthesisInventory().extractItem(1,1,false);
        require(output.getItem()==plate&&output.getCount()==1,"Forge Press produces one Programmable Plate");
        String[] pieces={"helmet","chestplate","leggings","boots"};
        String[] patterns={
                "###"+"# #"+"   ",
                "# #"+"###"+"###",
                "###"+"# #"+"# #",
                "# #"+"# #"+"   "
        };
        for(int piece=0;piece<pieces.length;piece++){
            net.minecraft.item.crafting.IRecipe armor=net.minecraftforge.fml.common.registry.ForgeRegistries.RECIPES
                    .getValue(new ResourceLocation("vandorlabs","programmable_"+pieces[piece]));
            net.minecraft.inventory.InventoryCrafting grid=new net.minecraft.inventory.InventoryCrafting(new net.minecraft.inventory.Container(){
                @Override public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player){return true;}
            },3,3);
            String shape=patterns[piece];
            for(int cell=0;cell<9;cell++)if(shape.charAt(cell)=='#')grid.setInventorySlotContents(cell,output.copy());
            require(armor!=null&&armor.matches(grid,world)&&!armor.getCraftingResult(grid).isEmpty(),"Plate crafts "+pieces[piece]);
            for(int cell=0;cell<9;cell++)if(shape.charAt(cell)=='#')grid.setInventorySlotContents(cell,new net.minecraft.item.ItemStack(ingot));
            require(!armor.matches(grid,world),"raw ingots do not craft "+pieces[piece]);
        }
    }
    private static void checkLegacyPalette(World world,Block programmable){
        String old=com.vandorlabs.persistence.RenamedBlocks.OLD_MATTER;
        require(!net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.containsKey(new ResourceLocation(old))
                &&!net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.containsKey(new ResourceLocation(old)),
                "retired Programmable Matter Block is absent from both registries");
        com.vandorlabs.vehicle.VehicleStructure template=com.vandorlabs.vehicle.VehicleStructure.display(
                java.util.Collections.singletonList(new com.vandorlabs.vehicle.VehicleStructure.Cell(
                        BlockPos.ORIGIN,Blocks.IRON_BLOCK.getDefaultState(),null)));
        NBTTagCompound saved=template.write();
        NBTTagCompound palette=saved.getTagList("Palette",10).getCompoundTagAt(0);
        palette.setString("Name",old);
        require(!palette.hasKey("Properties"),"released Matter state carried no properties");
        com.vandorlabs.vehicle.VehicleStructure migrated=com.vandorlabs.vehicle.VehicleStructure.readDisplay(saved);
        require(migrated.at(BlockPos.ORIGIN).state.getBlock()==programmable&&migrated.at(BlockPos.ORIGIN).tileData()!=null,
                "old tile-less frame acquires a programmable tile after snapshot migration");
        com.vandorlabs.vehicle.VehicleWorld view=new com.vandorlabs.vehicle.VehicleWorld(world,migrated);
        require(((TileEntityAnimatedScreenSelector)view.getTileEntity(BlockPos.ORIGIN)).getHousingTexture()
                ==ScreenHousingTextures.DEFAULT_PROGRAMMABLE_BLOCK,"old frame retains Matter texture in captured world");
        require(palette.getString("Name").equals(old),"validated snapshot does not mutate its saved source");
        NBTTagCompound properties=new NBTTagCompound();properties.setString("facing","north");palette.setTag("Properties",properties);
        boolean refused=false;
        try{com.vandorlabs.vehicle.VehicleStructure.readDisplay(saved);}catch(IllegalArgumentException invalid){refused=true;}
        require(refused,"old Matter palette rejects unrecognized properties");
        NBTTagCompound item=new NBTTagCompound();item.setString("id",old);item.setByte("Count",(byte)17);
        com.vandorlabs.persistence.RenamedBlocks.migrateItems(item);
        require(item.getString("id").equals(programmable.getRegistryName().toString())&&item.getByte("Count")==17,
                "old inventory frame item migrates and keeps its stack count");
    }
    private static void checkCosmeticTicks(TileEntityConstructor tile,FrameMachineDesign spec){
        boolean drive=spec.design.equals("longburn_drive")||spec.design.equals("meridian_core")||spec.design.equals("oculus_drive");
        require(tile.isDrive()==drive,"cosmetic drive classification remains limited to three drives");
        NBTTagCompound before=tile.writeToNBT(new NBTTagCompound());
        for(int tick=0;tick<20;tick++)tile.update();
        require(before.equals(tile.writeToNBT(new NBTTagCompound())),"constructor ticks never simulate server gameplay or alter dismantled snapshots "+spec.design);
    }
    private static void checkRestored(World world,java.util.Map<BlockPos,Boolean> original,Block matter){for(java.util.Map.Entry<BlockPos,Boolean> entry:original.entrySet())require(entry.getValue()?world.getBlockState(entry.getKey()).getBlock()==matter:world.isAirBlock(entry.getKey()),"restore "+scenario+" at "+entry.getKey()+" expected="+(entry.getValue()?"matter":"air")+" actual="+world.getBlockState(entry.getKey()));}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
