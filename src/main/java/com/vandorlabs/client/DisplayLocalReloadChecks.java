package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.redstone.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Persist native metadata and compressed tile NBT, then run the real deferred load path. */
final class DisplayLocalReloadChecks {
    private static final BlockPos BASE=new BlockPos(656,100,656);
    private static void require(boolean value,String reason){if(!value)throw new IllegalStateException("Local display reload: "+reason);}
    static void run(WorldServer world){
        int count=0;
        Block[] families={ModBlocks.ANIMATED_SCREEN_SELECTOR,ModBlocks.PROGRAMMABLE_FULL_INPUT,ModBlocks.PROGRAMMABLE_DIAGONAL_SCREEN,ModBlocks.PROGRAMMABLE_INPUT,ModBlocks.PROGRAMMABLE_CONSOLE,ModBlocks.PROGRAMMABLE_HALF_CONSOLE,Block.getBlockFromName("vandorlabs:programmable_diagonal_half_console")};
        for(Block block:families)for(int step=1;step<=3;step++)for(boolean sourceFirst:new boolean[]{false,true}){
            scenario(world,block,step,sourceFirst,BASE);count++;
        }
        clear(world,BASE);
        System.out.println("[vandorlabs] display-local-reload PASS cases="+count+" seven families 3x3 supported joins three powered slider steps no channels metadata compressed-NBT deferred-load both tile orders");
    }
    static void reloadedScene(WorldServer world,BlockPos origin){scenario(world,ModBlocks.ANIMATED_SCREEN_SELECTOR,2,false,origin);for(BlockPos at:BlockPos.getAllInBox(origin.west(),origin.add(2,2,0)))world.notifyBlockUpdate(at,world.getBlockState(at),world.getBlockState(at),3);}
    private static void clear(WorldServer world,BlockPos origin){for(BlockPos at:BlockPos.getAllInBox(origin.add(-4,-3,-4),origin.add(5,5,5)))world.setBlockToAir(at);}
    private static void scenario(WorldServer world,Block block,int step,boolean sourceFirst,BlockPos origin){
        Block slider=Block.getBlockFromName("vandorlabs:wall_slider");
            for(BlockPos at:BlockPos.getAllInBox(origin.add(-4,-3,-4),origin.add(5,5,5)))world.setBlockToAir(at);
            world.setBlockState(origin,block.getDefaultState(),3);TileEntityAnimatedScreenSelector first=(TileEntityAnimatedScreenSelector)world.getTileEntity(origin);
            DisplayJoinGeometry.Basis basis=DisplayJoinGeometry.basis(first,world.getBlockState(origin));int width=DisplayJoinGeometry.supportsJoining(first)?3:1;
            List<BlockPos> screens=new ArrayList<>();
            for(int x=0;x<width;x++)for(int y=0;y<width;y++){
                BlockPos at=origin.add(basis.steps[0].getX()*x+basis.steps[2].getX()*y,basis.steps[0].getY()*x+basis.steps[2].getY()*y,basis.steps[0].getZ()*x+basis.steps[2].getZ()*y);
                world.setBlockState(at,block.getDefaultState(),3);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(at);for(int band=0;band<4;band++)tile.configureActivation(2,band,band,1,1);screens.add(at);
            }
            BlockPos source=origin.subtract(basis.steps[0]);world.setBlockState(source.down(),Blocks.STONE.getDefaultState(),3);world.setBlockState(source,slider.getDefaultState().withProperty(BlockVandorSwitch.FACING,EnumFacing.UP),3);DeferredTileLoad.flush(world);
            TileEntitySignalControl control=(TileEntitySignalControl)world.getTileEntity(source);require(control.getRedstoneChannels().isEmpty(),"fixture has no channel");control.setStep(step);int expected=control.levelForStep(step);
            check(world,screens,source,expected,width,block+" before reload");
            List<BlockPos> positions=new ArrayList<>(screens);if(sourceFirst)positions.add(0,source);else positions.add(source);
            Map<BlockPos,IBlockState> states=new LinkedHashMap<>();Map<BlockPos,NBTTagCompound> saved=new LinkedHashMap<>();
            for(BlockPos at:positions){IBlockState state=world.getBlockState(at);states.put(at,state.getBlock().getStateFromMeta(state.getBlock().getMetaFromState(state)));saved.put(at,persist(world.getTileEntity(at)));}
            for(BlockPos at:positions){world.getTileEntity(at).onChunkUnload();world.removeTileEntity(at);}
            for(BlockPos at:positions){world.setBlockState(at,states.get(at),2);TileEntity tile=TileEntity.create(world,saved.get(at));require(tile!=null,"saved tile factory");world.setTileEntity(at,tile);tile.onLoad();}
            DeferredTileLoad.flush(world);
            check(world,screens,source,expected,width,block+" after reload sourceFirst="+sourceFirst);    }
    private static NBTTagCompound persist(TileEntity tile){try{java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();CompressedStreamTools.writeCompressed(tile.writeToNBT(new NBTTagCompound()),bytes);return CompressedStreamTools.readCompressed(new java.io.ByteArrayInputStream(bytes.toByteArray()));}catch(java.io.IOException e){throw new IllegalStateException(e);}}
    private static void check(WorldServer world,List<BlockPos> screens,BlockPos source,int expected,int width,String context){
        int actual=world.getBlockState(source).getWeakPower(world,source,EnumFacing.EAST);require(actual==expected,context+" Wall Slider physical output="+actual+" expected="+expected);
        for(BlockPos at:screens){TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(at);require(tile.getSignalLevel()==expected,context+" display="+at+" actual="+tile.getSignalLevel()+" expected="+expected);int band=expected<=5?1:expected<=10?2:3;require(tile.activationState()==band&&tile.getSurfaceTexture(0)==band,context+" band artwork");}
    }
    private DisplayLocalReloadChecks(){}
}
