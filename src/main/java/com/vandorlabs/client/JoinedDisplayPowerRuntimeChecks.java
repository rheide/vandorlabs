package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.redstone.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockPressurePlateWeighted;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Real local redstone at every strength, followed by client-observed joined On/Off. */
final class JoinedDisplayPowerRuntimeChecks {
    private static final BlockPos BASE=new BlockPos(620,100,620);
    private static int stage,ticks;
    private static void require(boolean value,String why){if(!value)throw new IllegalStateException("joined display power stage="+stage+": "+why);}
    private static void server(Minecraft mc,Runnable run)throws Exception{mc.getIntegratedServer().addScheduledTask(run).get();}
    static boolean tick(Minecraft mc)throws Exception{
        require(++ticks<600,"client/native power update timeout");
        if(stage==0){server(mc,()->{
            WorldServer world=mc.getIntegratedServer().getWorld(0);matrix(world);
            for(BlockPos p:BlockPos.getAllInBox(BASE.add(-3,-2,-3),BASE.add(4,4,3)))world.setBlockToAir(p);
            for(int x=0;x<2;x++)for(int y=0;y<2;y++){BlockPos p=BASE.add(x,y,0);world.setBlockState(p,ModBlocks.ANIMATED_SCREEN_SELECTOR.getDefaultState(),3);configure((TileEntityAnimatedScreenSelector)world.getTileEntity(p));}
            world.setBlockState(BASE.west(),Blocks.REDSTONE_BLOCK.getDefaultState(),3);
            net.minecraft.entity.player.EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);player.capabilities.isFlying=true;player.sendPlayerAbilities();player.connection.setPlayerLocation(620.5,102,624,180,10);
        });stage=1;return false;}
        int expected=stage==2?0:15;
        for(int x=0;x<2;x++)for(int y=0;y<2;y++){
            net.minecraft.tileentity.TileEntity raw=mc.world.getTileEntity(BASE.add(x,y,0));if(!(raw instanceof TileEntityAnimatedScreenSelector))return false;
            TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)raw;if(tile.activationMode()!=2||tile.getSignalLevel()!=expected)return false;
            GlassSurfaceGroups.Layout layout=ScreenSurfaceGroups.layout(tile);require(layout.columns==2&&layout.rows==2,"local activation must preserve 2x2 joining");
        }
        if(stage==1){server(mc,()->mc.getIntegratedServer().getWorld(0).setBlockToAir(BASE.west()));stage=2;return false;}
        if(stage==2){server(mc,()->mc.getIntegratedServer().getWorld(0).setBlockState(BASE.add(2,1,0),Blocks.REDSTONE_BLOCK.getDefaultState(),3));stage=3;return false;}
        System.out.println("[vandorlabs][reprolab] joined-display-power PASS seven families 16 local levels shared geometry bank isolation native 2x2 client On Off opposite corner no channel feedback");return true;
    }
    private static void configure(TileEntityAnimatedScreenSelector tile){for(int band=0;band<4;band++)require(tile.configureActivation(2,band,band,1,1),"activation fixture configured");}
    private static void matrix(WorldServer world){
        Block[] blocks={ModBlocks.ANIMATED_SCREEN_SELECTOR,ModBlocks.PROGRAMMABLE_FULL_INPUT,ModBlocks.PROGRAMMABLE_DIAGONAL_SCREEN,ModBlocks.PROGRAMMABLE_INPUT,ModBlocks.PROGRAMMABLE_CONSOLE,ModBlocks.PROGRAMMABLE_HALF_CONSOLE,Block.getBlockFromName("vandorlabs:programmable_diagonal_half_console")};
        BlockPos origin=BASE.add(20,0,0);
        for(Block block:blocks){
            for(BlockPos p:BlockPos.getAllInBox(origin.add(-4,-3,-4),origin.add(5,5,5)))world.setBlockToAir(p);
            world.setBlockState(origin,block.getDefaultState(),2);TileEntityAnimatedScreenSelector first=(TileEntityAnimatedScreenSelector)world.getTileEntity(origin);configure(first);
            DisplayJoinGeometry.Basis basis=DisplayJoinGeometry.basis(first,world.getBlockState(origin));List<TileEntityAnimatedScreenSelector> tiles=new ArrayList<>();tiles.add(first);
            for(int x=0;x<2;x++)for(int y=0;y<2;y++)if(x!=0||y!=0){BlockPos p=origin.add(basis.steps[0].getX()*x+basis.steps[2].getX()*y,basis.steps[0].getY()*x+basis.steps[2].getY()*y,basis.steps[0].getZ()*x+basis.steps[2].getZ()*y);world.setBlockState(p,block.getDefaultState(),2);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(p);configure(tile);tiles.add(tile);}
            BlockPos source=origin.subtract(basis.steps[0]);world.setBlockState(source.down(),Blocks.STONE.getDefaultState(),2);world.setBlockState(source,Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.getDefaultState(),2);
            for(int level=0;level<=15;level++){
                world.setBlockState(source,Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.getDefaultState().withProperty(BlockPressurePlateWeighted.POWER,level),2);first.localInputChanged();
                require(LoadedRedstonePower.level(world,origin)==level,"native source level "+block+" level="+level);
                for(TileEntityAnimatedScreenSelector tile:tiles){int expected=tile==first||DisplayJoinGeometry.supportsJoining(first)?level:0;require(tile.getSignalLevel()==expected,"joined strength "+block+" level="+level+" at="+tile.getPos()+" actual="+tile.getSignalLevel());require(tile.transmittedChannels().isEmpty(),"joined receiver must not repeat channels");}
            }
            if(DisplayJoinGeometry.supportsJoining(first)){
                TileEntityAnimatedScreenSelector last=tiles.get(3);last.configureActivation(2,3,7,1,1);
                require(last.getSignalLevel()==0,"different state bank must not receive the neighbouring group's local power");
                last.configureActivation(2,3,3,1,1);require(last.getSignalLevel()==15,"matching settings rejoin immediately");
            }
        }
    }
    private JoinedDisplayPowerRuntimeChecks(){}
}
