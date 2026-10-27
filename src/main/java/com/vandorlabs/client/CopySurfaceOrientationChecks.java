package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.copier.CopyControllerTile;
import com.vandorlabs.surface.*;
import com.vandorlabs.tiles.*;
import java.io.File;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;

/** Complete pad editing on every surface normal and horizontal controller facing. */
final class CopySurfaceOrientationChecks {
    private static final BlockPos BASE=new BlockPos(1920,120,128);
    private static int fixture,stage,ticks;
    private static boolean complete;
    private static BlockPos removed;
    private static List<BlockPos> remaining;
    private static SquareLayout stable;
    private static EnumFacing normal(){return EnumFacing.values()[fixture/4];}
    private static EnumFacing front(){return EnumFacing.HORIZONTALS[fixture%4];}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("copy-surface orientation="+normal()+" front="+front()+" stage="+stage+": "+why);}
    static boolean tick(Minecraft mc,File output)throws Exception{
        if(complete)return true;
        if(fixture==24){
            mc.getIntegratedServer().callFromMainThread(()->{com.vandorlabs.surface.SurfaceRuntimeChecks.run(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0));return null;}).get();
            SurfaceClientChecks.run(mc,output);
            System.out.println("[vandorlabs] copy-surfaces PASS saved root/member mining 5-to-7 growth mismatch seed/controller removal 24 orientation lifecycles 72 actual opaque faces settled topology shared surface regressions");complete=true;return true;
        }
        mc.displayGuiScreen(null);
        mc.getIntegratedServer().callFromMainThread(()->{step(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0));return null;}).get();
        if(stage==3){
            for(BlockPos p:remaining){TileEntityTurntableSurface tile=(TileEntityTurntableSurface)mc.world.getTileEntity(p);require(tile!=null,"client received remaining tile");java.awt.image.BufferedImage frame=SurfaceClientChecks.damagedFace(mc,tile,normal());int ink=0;for(int color:frame.getRGB(0,0,64,64,null,0,64))if((color&0xffffff)!=0x203040)ink++;require(ink>1800,"remaining face is opaque pixels="+ink);}
            stage=4;ticks=0;
        }
        return false;
    }
    private static void step(WorldServer world,EntityPlayerMP player){
        require(++ticks<240,"bounded topology update");
        if(stage==0){
            for(BlockPos p:BlockPos.getAllInBox(BASE.add(-6,-6,-6),BASE.add(6,6,6))){world.removeTileEntity(p);world.setBlockToAir(p);}
            player.setGameType(GameType.CREATIVE);player.capabilities.isFlying=true;player.sendPlayerAbilities();player.connection.setPlayerLocation(BASE.getX()+5,BASE.getY()+5,BASE.getZ()+5,0,0);
            world.setBlockState(BASE,Block.getBlockFromName("vandorlabs:copy_controller").getDefaultState().withProperty(BlockVandorDirectional.FACING,front()),3);
            CopyControllerTile copier=(CopyControllerTile)world.getTileEntity(BASE);copier.actor=player.getUniqueID();WorldActionAuthorization.authorize(copier,player);build(world,player,2);stage=1;ticks=0;return;
        }
        CopyControllerTile copier=(CopyControllerTile)world.getTileEntity(BASE);
        if(stage==1){if(ticks<20)return;require(copier.input!=null&&copier.output!=null&&copier.input.size==2&&copier.output.size==2,"initial pair automatically claimed: "+copier.error);removed=copier.output.root;remaining=new ArrayList<>(copier.output.cells);remaining.remove(removed);require(player.interactionManager.tryHarvestBlock(removed),"mine drawing root");stage=2;ticks=0;return;}
        if(stage==2){if(ticks<20)return;require(copier.input==null&&copier.output==null,"incomplete pair releases claims");for(BlockPos p:remaining){TileEntityTurntableSurface tile=(TileEntityTurntableSurface)world.getTileEntity(p);require(tile.appearanceLayout().size==1&&!tile.locked(),"remaining faces render independently");}stage=3;return;}
        if(stage==4){place(world,player,removed);build(world,player,3);stage=5;ticks=0;return;}
        if(stage==5){if(ticks<20)return;require(copier.input!=null&&copier.output!=null&&copier.input.size==3&&copier.output.size==3,"expanded pair automatically claimed: "+copier.error);stable=copier.input;stage=6;ticks=0;return;}
        if(stage==6){if(ticks<20)return;require(copier.input==stable,"idle topology settles");System.out.println("[vandorlabs] copy-surface orientation PASS fixture="+fixture+" normal="+normal()+" front="+front());fixture++;stage=ticks=0;}
    }
    private static void build(WorldServer world,EntityPlayerMP player,int size){
        com.vandorlabs.turntable.TurntableGeometry.Face basis=com.vandorlabs.turntable.TurntableGeometry.Face.valueOf(normal().name());EnumFacing right=front().rotateYCCW();
        for(int sign:new int[]{-1,1}){
            EnumFacing u=EnumFacing.getFacingFromVector(basis.ux,basis.uy,basis.uz),v=EnumFacing.getFacingFromVector(basis.vx,basis.vy,basis.vz);
            if(dot(u,right)*sign<0)u=u.getOpposite();if(dot(v,right)*sign<0)v=v.getOpposite();BlockPos seed=BASE.offset(right,sign);
            for(int x=0;x<size;x++)for(int y=0;y<size;y++){BlockPos p=seed.offset(u,x).offset(v,y);if(!(world.getBlockState(p).getBlock() instanceof BlockTurntableSurface))place(world,player,p);}
        }
    }
    private static int dot(EnumFacing a,EnumFacing b){return a.getFrontOffsetX()*b.getFrontOffsetX()+a.getFrontOffsetY()*b.getFrontOffsetY()+a.getFrontOffsetZ()*b.getFrontOffsetZ();}
    private static void place(WorldServer world,EntityPlayerMP player,BlockPos p){BlockPos support=p.offset(normal().getOpposite());if(world.isAirBlock(support))world.setBlockState(support,Blocks.STONE.getDefaultState(),3);player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(Block.getBlockFromName("vandorlabs:turntable_surface")));require(player.getHeldItemMainhand().onItemUse(player,world,support,EnumHand.MAIN_HAND,normal(),.5F,.5F,.5F)==EnumActionResult.SUCCESS,"normal surface placement "+p);player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);}
    private CopySurfaceOrientationChecks(){}
}
