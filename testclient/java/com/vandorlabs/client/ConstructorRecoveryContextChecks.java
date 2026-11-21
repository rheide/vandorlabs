package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.copier.PhysicalTileFactory;
import com.vandorlabs.machines.BuilderRecipeCatalog;
import com.vandorlabs.tiles.TileEntityConstructor;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Recovery prepares tile NBT before the journal restores the captured native block state. */
public final class ConstructorRecoveryContextChecks {
    private static final BlockPos POS=new BlockPos(8,80,8);
    private static int checks;
    private static sun.misc.Unsafe unsafe;
    private static final class RecoveryWorld extends WorldServer {
        IBlockState state;TileEntity tile;
        private RecoveryWorld(){super(null,null,null,0,null);}
        @Override public IBlockState getBlockState(BlockPos at){return state;}
        @Override public TileEntity getTileEntity(BlockPos at){return tile;}
        @Override public boolean isBlockLoaded(BlockPos at){return true;}
        @Override public boolean isBlockLoaded(BlockPos at,boolean allowEmpty){return true;}
        @Override public net.minecraft.server.MinecraftServer getMinecraftServer(){return null;}
    }
    private static void need(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    private static RecoveryWorld world(IBlockState state)throws Exception{
        RecoveryWorld world=(RecoveryWorld)unsafe.allocateInstance(RecoveryWorld.class);world.state=state;return world;
    }
    private static TileEntityConstructor recover(IBlockState state,NBTTagCompound before,String description)throws Exception{
        RecoveryWorld world=world(net.minecraft.init.Blocks.AIR.getDefaultState());NBTTagCompound original=before.copy();
        TileEntityConstructor restored=(TileEntityConstructor)PhysicalTileFactory.create(world,state,before);
        NBTTagCompound after=restored.writeToNBT(new NBTTagCompound());
        need(before.equals(after),"Actual unpublished recovery factory preserves "+description+" while target is AIR: expected="+before+" actual="+after);
        MovingBlockSupport.requireExactTileData(new VehicleStructure.Cell(POS,state,before),restored);
        need(before.equals(original),"Recovery leaves the saved preimage untouched: "+description);
        need(world.state.getBlock()==net.minecraft.init.Blocks.AIR&&world.tile==null,"Recovery comparison does not publish a native block or tile: "+description);
        world.state=state;world.tile=restored;
        need(before.equals(restored.writeToNBT(new NBTTagCompound())),"Installing the target state retains the same exact receipt: "+description);
        return restored;
    }
    private static void allCores()throws Exception{
        for(FrameMachineDesign design:FrameMachineDesign.all()){
            BlockConstructorCore block=new BlockConstructorCore(design);
            IBlockState state=block.getDefaultState().withProperty(BlockConstructorCore.FORMED,true);
            RecoveryWorld world=world(state);
            TileEntityConstructor original=new TileEntityConstructor();original.setWorld(world);original.setPos(POS);world.tile=original;
            NBTTagCompound before=original.writeToNBT(new NBTTagCompound());
            need(before.hasKey("Synthesis",10),"Current release fixture has an explicit runtime: "+design.design);
            recover(state,before,design.design);
        }
    }
    /** Synthesis builders take any Programmable Block finish; the Assembly Press takes its ingot. */
    private static ItemStack input(String machine){
        if("assembly_press".equals(machine))return new ItemStack(item(BuilderRecipeCatalog.input(machine)));
        net.minecraft.block.Block block=com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_BLOCK;
        if(Item.getItemFromBlock(block)==net.minecraft.init.Items.AIR){
            if(!net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.containsValue(block))net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(block);
            net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.register(new net.minecraft.item.ItemBlock(block).setRegistryName(block.getRegistryName()));
        }
        return new ItemStack(block);
    }
    private static Item item(String name){
        ResourceLocation id=new ResourceLocation("vandorlabs",name);
        if(!Item.REGISTRY.containsKey(id))net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.register(new Item().setRegistryName(id));
        return Item.REGISTRY.getObject(id);
    }
    private static void builders()throws Exception{
        for(String machine:com.vandorlabs.machines.SynthesisRuntime.DESIGNS)for(boolean emptyIds:new boolean[]{false,true}){
            String recipe=BuilderRecipeCatalog.defaults(machine).get(1);item(recipe);
            BlockConstructorCore block=new BlockConstructorCore(FrameMachineDesign.get(machine));
            IBlockState state=block.getDefaultState().withProperty(BlockConstructorCore.FORMED,true);
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);world.time=1;
            TileEntityConstructor tile=new TileEntityConstructor();tile.setWorld(world);tile.setPos(POS);world.states.put(POS,state);world.tiles.put(POS,tile);
            NBTTagCompound initial=tile.writeToNBT(new NBTTagCompound());
            if(emptyIds){initial.getCompoundTag("Synthesis").setString("SelectedRecipe","");initial.getCompoundTag("Synthesis").setString("BuildingRecipe","");}
            tile.readFromNBT(initial);tile.refreshInputs();
            need(tile.synthesisEnabled(),"Installed builder is ready: "+machine);
            tile.selectDesign(1);
            need(tile.selectedDesign()==1&&tile.writeToNBT(new NBTTagCompound()).getCompoundTag("Synthesis").getString("SelectedRecipe").equals(recipe),"Real recipe selection replaces a saved default: "+machine+" empty="+emptyIds);
            tile.synthesisInventory().setStackInSlot(0,input(machine));
            tile.update();
            need(tile.processing()&&tile.synthesisInventory().getStackInSlot(0).isEmpty(),"Real builder start owns exactly one input: "+machine+" empty="+emptyIds);
            NBTTagCompound running=tile.writeToNBT(new NBTTagCompound());
            need(running.getCompoundTag("Synthesis").getString("BuildingRecipe").equals(recipe),"Starting a job replaces the old building recipe: "+machine+" empty="+emptyIds);
            TileEntityConstructor restored=recover(state,running,machine+" active empty="+emptyIds);
            need(restored.processing()&&restored.selectedDesign()==1&&restored.buildDesign()==1&&restored.synthesisInventory().getStackInSlot(0).isEmpty(),"Recovered job retains its selected/building IDs and consumed input: "+machine);
            need(running.equals(restored.writeToNBT(new NBTTagCompound())),"Resolving valid builder IDs leaves its exact receipt unchanged: "+machine);
        }
    }
    public static void main(String[] args)throws Exception{
        java.lang.reflect.Field environment=net.minecraftforge.fml.relauncher.CoreModManager.class.getDeclaredField("deobfuscatedEnvironment");environment.setAccessible(true);environment.setBoolean(null,true);
        net.minecraft.init.Bootstrap.register();
        com.vandorlabs.VandorLabs.logger=org.apache.logging.log4j.LogManager.getLogger("constructor-recovery");
        com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy();
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityConstructor.class,new ResourceLocation("vandorlabs:constructor"));
        java.lang.reflect.Field singleton=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");singleton.setAccessible(true);unsafe=(sun.misc.Unsafe)singleton.get(null);
        allCores();builders();
        System.out.println("Constructor recovery context PASS checks="+checks+" actual PhysicalTileFactory, every Core design, unpublished AIR target, real builder selection/start/reload");
    }
    private ConstructorRecoveryContextChecks(){}
}
