package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.rack.*;
import com.vandorlabs.tiles.TileEntityAutopilot;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.registry.*;

/** Actual chunk publication boundary; both ordinary installers fail before the retained owner is saved. */
public final class AutopilotMigrationFailureChecks {
    private static final BlockPos AT=new BlockPos(3,80,4);private static int checks;
    private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static final class World extends NonRenderingChecks.MemoryWorld {
        final Chunk chunk=new Chunk(this,0,0);int attempts,failures;boolean failRemoval;
        World(int failures){super(false);this.failures=failures;}
        @Override public Chunk getChunkFromChunkCoords(int x,int z){return chunk;}
        @Override public IBlockState getBlockState(BlockPos at){return chunk==null?net.minecraft.init.Blocks.AIR.getDefaultState():chunk.getBlockState(at);}
        @Override public TileEntity getTileEntity(BlockPos at){return chunk.getTileEntityMap().get(at);}
        @Override public void removeTileEntity(BlockPos at){if(failRemoval)throw new IllegalStateException("injected removal failure");TileEntity old=chunk.getTileEntityMap().remove(at);if(old!=null)old.invalidate();}
        @Override public void setTileEntity(BlockPos at,TileEntity tile){if(++attempts<=failures)throw new IllegalStateException("injected tile publication failure "+attempts);tile.setWorld(this);tile.setPos(at);tile.validate();chunk.getTileEntityMap().put(at.toImmutable(),tile);}
    }
    public static void main(String[] args){
        net.minecraft.init.Bootstrap.register();GameRegistry.registerTileEntity(TileEntityAutopilot.class,new ResourceLocation("vandorlabs:autopilot"));
        BlockAutopilot block=new BlockAutopilot(false);ForgeRegistries.BLOCKS.register(block);ForgeRegistries.ITEMS.register(new ItemBlock(block).setRegistryName(block.getRegistryName()));
        IBlockState state=block.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.WEST);
        TileEntityAutopilot old=new TileEntityAutopilot();old.setPos(AT);NBTTagCompound raw=old.writeToNBT(new NBTTagCompound());raw.setString("FutureOwnField","exact recovery");NBTTagCompound caps=new NBTTagCompound();caps.setIntArray("absent:provider",new int[]{37,4,19});raw.setTag("ForgeCaps",caps);
        for(int failures:new int[]{2,3}){
            World world=new World(failures);Exception initial;
            com.vandorlabs.tiles.TileEntityModuleRack partial=new com.vandorlabs.tiles.TileEntityModuleRack();need(partial.insertModule(0,new ItemStack(com.vandorlabs.items.ModItems.BLANK_MODULE),false),"partial target owns one module before rollback");partial.setWorld(world);partial.setPos(AT);world.chunk.getTileEntityMap().put(AT,partial);world.loadedTileEntityList.add(partial);world.failRemoval=true;
            try{world.setTileEntity(AT,new TileEntityAutopilot());throw new AssertionError("first install must fail");}catch(IllegalStateException failure){initial=failure;}
            LegacyAutopilotMigration.rollbackAfterFailure(world,AT,state,raw,()->world.setTileEntity(AT,old),initial);
            need(world.attempts==3,"failed install+rollback+checked fallback attempted");need(world.getBlockState(AT)==state,"old block/facing restored directly");
            need(partial.isInvalid()&&!world.loadedTileEntityList.contains(partial)&&partial.removeModule(0).isEmpty(),"displaced target invalidated/retired despite removal exception, no orphan extraction");
            TileEntity recovery=world.getTileEntity(AT);need(recovery instanceof LegacyAutopilotRecovery,"one inert recovery owner");need(world.chunk.getTileEntityMap().size()==1,"exactly one physical tile");need(recovery.writeToNBT(new NBTTagCompound()).equals(raw),"complete old NBT/caps saved exactly");
            need(!recovery.hasCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY,null),"no active provider on recovery");
            TileEntity loaded=TileEntity.create(world,recovery.writeToNBT(new NBTTagCompound()));need(loaded instanceof TileEntityAutopilot&&loaded.writeToNBT(new NBTTagCompound()).equals(raw),"canonical old owner reloads exact preimage for deferred retry");
            net.minecraft.util.NonNullList<ItemStack> drops=net.minecraft.util.NonNullList.create();block.getDrops(drops,world,AT,state,0);need(drops.size()==1&&drops.get(0).getCount()==1,"one configured recovery carrier");
            NBTTagCompound expected=raw.copy();for(String key:new String[]{"id","x","y","z"})expected.removeTag(key);need(drops.get(0).getSubCompound("BlockEntityTag").equals(expected),"carrier retains raw resources/capabilities");
            need(raw.getCompoundTag("ForgeCaps").equals(caps),"original retained reference never mutated");
        }
        System.out.println("Autopilot migration failure PASS checks="+checks+" double install failure, failed fallback setter, exact inert chunk owner/canonical reload/single carrier");
    }
}
