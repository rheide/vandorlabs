package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockTurntableSurface;
import com.vandorlabs.copier.CopyControllerTile;
import com.vandorlabs.surface.SquareLayout;
import com.vandorlabs.tiles.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;

/** Saved-world edits through normal mining/placement, world ticks and actual client TESR pixels. */
final class CopySurfaceLiveChecks {
    private static final BlockPos CONTROL=new BlockPos(-80,83,179);
    private static int fixture,stage,ticks;
    private static boolean clicked;
    private static BlockPos removed;
    private static List<BlockPos> original;
    private static NBTTagCompound pickedAppearance;
    private static SquareLayout stableInput,stableOutput;
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("copy-surfaces fixture="+fixture+" stage="+stage+" tick="+ticks+": "+why);}
    static boolean tick(Minecraft mc,File output)throws Exception{
        if(fixture>=2)return CopySurfaceOrientationChecks.tick(mc,output);
        mc.displayGuiScreen(null);
        if(stage==1&&ticks>20&&!clicked&&mc.playerController.getCurrentGameType()==GameType.CREATIVE&&mc.player.inventory.currentItem==0&&mc.player.getHeldItemMainhand().isEmpty()
                &&mc.player.getDistanceSq(removed.getX()+.5,86,removed.getZ()+.5)<.05&&mc.world.getBlockState(removed).getBlock() instanceof BlockTurntableSurface){
            require(mc.objectMouseOver!=null&&removed.equals(mc.objectMouseOver.getBlockPos()),"real ray selects saved surface");
            mc.playerController.clickBlock(removed,EnumFacing.UP);clicked=true;
        }
        boolean done=mc.getIntegratedServer().callFromMainThread(()->step(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0))).get();
        if(stage==5&&ticks>=20){
            TileEntity raw=mc.world.getTileEntity(removed);require(raw instanceof TileEntityTurntableSurface,"picked placement reached client");TileEntityTurntableSurface placed=(TileEntityTurntableSurface)raw;
            require(placed.surfaceOn.equals(pickedAppearance.getString("SurfaceOn"))&&placed.surfaceOff.equals(pickedAppearance.getString("SurfaceOff"))&&placed.textureLayout==pickedAppearance.getInteger("SurfaceLayout"),"picked appearance reaches client without another edit");
        }
        if(stage==3){
            int frames=0;
            for(BlockPos p:original)if(!p.equals(removed)){
                require(mc.world.getTileEntity(p) instanceof TileEntityTurntableSurface,"remaining surface reached client");
                TileEntityTurntableSurface tile=(TileEntityTurntableSurface)mc.world.getTileEntity(p);
                BufferedImage frame=SurfaceClientChecks.damagedFace(mc,tile,EnumFacing.UP);int visible=0;
                for(int color:frame.getRGB(0,0,64,64,null,0,64))if((color&0xffffff)!=0x203040)visible++;
                if(frames++==0)javax.imageio.ImageIO.write(frame,"png",new File(output,"shot_copy_surface_removed_"+fixture+".png"));
                require(visible>1800,"remaining top is opaque after "+(fixture==0?"root":"member")+" mining at "+p+" visible="+visible);
                require(tile.appearanceLayout().size==1&&tile.appearanceLayout().root.equals(p),"irregular pad renders each remaining block");
            }
            System.out.println("[vandorlabs] copy-surface removal PASS fixture="+fixture+" actual-TESR-frames="+frames);stage=4;ticks=0;
        }
        return false;
    }
    private static boolean step(WorldServer world,EntityPlayerMP player)throws Exception{
        require(++ticks<300,"bounded world/client synchronization");
        if(stage==0){
            for(BlockPos p:BlockPos.getAllInBox(new BlockPos(-85,82,171),new BlockPos(-75,87,187))){world.removeTileEntity(p);world.setBlockToAir(p);}
            NBTTagCompound data;try(InputStream stream=Files.newInputStream(Paths.get(System.getProperty("vandorlabs.copySurfaceFixture")))){data=CompressedStreamTools.readCompressed(stream);}
            NBTTagList cells=data.getTagList("Cells",10);
            for(int i=0;i<cells.tagCount();i++){NBTTagCompound cell=cells.getCompoundTagAt(i);BlockPos p=new BlockPos(cell.getInteger("X"),cell.getInteger("Y"),cell.getInteger("Z"));Block block=Block.getBlockFromName(cell.getString("Name"));require(block!=null,"saved registry entry");world.setBlockState(p,block.getStateFromMeta(cell.getInteger("Meta")),2);if(cell.hasKey("Tile",10)){world.removeTileEntity(p);world.setTileEntity(p,TileEntity.create(world,cell.getCompoundTag("Tile")));}}
            CopyControllerTile copier=(CopyControllerTile)world.getTileEntity(CONTROL);copier.actor=player.getUniqueID();WorldActionAuthorization.authorize(copier,player);
            require(copier.input.size==5&&copier.output.size==5,"exact saved five-block squares");original=new ArrayList<>(copier.output.cells);removed=fixture==0?copier.output.root:new BlockPos(-81,83,180);
            player.setGameType(GameType.CREATIVE);player.capabilities.isFlying=true;player.sendPlayerAbilities();player.inventory.currentItem=0;player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);player.inventoryContainer.detectAndSendChanges();player.connection.setPlayerLocation(removed.getX()+.5,86,removed.getZ()+.5,0,90);
            stage=1;ticks=0;return false;
        }
        CopyControllerTile copier=(CopyControllerTile)world.getTileEntity(CONTROL);
        if(stage==1){if(!clicked||!world.isAirBlock(removed))return false;stage=2;ticks=0;return false;}
        if(stage==2){if(ticks<30)return false;stage=3;return false;}
        if(stage==4){require(copier.input==null&&copier.output==null,"invalid pad releases stale copy layout");BlockPos source=original.stream().filter(p->!p.equals(removed)).findFirst().get();Block block=world.getBlockState(source).getBlock();ItemStack picked=block.getPickBlock(world.getBlockState(source),null,world,source,player);pickedAppearance=picked.getSubCompound("ProgrammableSurface").copy();place(world,player,removed,picked);stage=5;ticks=0;return false;}
        if(stage==5){if(ticks<30)return false;require(copier.input!=null&&copier.output!=null&&copier.input.size==5&&copier.output.size==5,"replacement automatically reforms saved pair: "+copier.error);grow(world,player,172,178);stage=6;ticks=0;return false;}
        if(stage==6){if(ticks<30)return false;require(copier.input==null&&copier.output==null&&!copier.error.isEmpty(),"unequal squares cannot keep old copy area");grow(world,player,180,186);stage=7;ticks=0;return false;}
        if(stage==7){if(ticks<30)return false;require(copier.input!=null&&copier.output!=null&&copier.input.size==7&&copier.output.size==7,"both expanded squares discovered automatically: "+copier.error);for(SquareLayout area:new SquareLayout[]{copier.input,copier.output})for(BlockPos p:area.cells)require(((TileEntityTurntableSurface)world.getTileEntity(p)).claimedOwner()==copier,"all expanded cells share ownership");stableInput=copier.input;stableOutput=copier.output;stage=11;ticks=0;return false;}
        if(stage==11){if(ticks<30)return false;require(copier.input==stableInput&&copier.output==stableOutput,"settled pads do not continually rescan");require(player.interactionManager.tryHarvestBlock(new BlockPos(-80,83,180)),"remove controller-adjacent seed");stage=8;ticks=0;return false;}
        if(stage==8){if(ticks<30)return false;require(copier.input==null&&copier.output==null,"missing seed invalidates copy area");place(world,player,new BlockPos(-80,83,180));stage=9;ticks=0;return false;}
        if(stage==9){if(ticks<30)return false;require(copier.input!=null&&copier.output!=null&&copier.output.size==7,"seed replacement restores expanded pair");require(player.interactionManager.tryHarvestBlock(CONTROL),"remove idle controller");stage=10;ticks=0;return false;}
        if(stage==10){if(ticks<30)return false;for(BlockPos p:original){TileEntityTurntableSurface tile=(TileEntityTurntableSurface)world.getTileEntity(p);require(tile.claimedOwner()==null&&!tile.locked()&&tile.appearanceLayout().size==7,"controller removal leaves ordinary visible complete surface");}System.out.println("[vandorlabs] copy-surface lifecycle PASS fixture="+fixture);if(++fixture==2)return true;stage=ticks=0;clicked=false;return false;}
        return false;
    }
    private static void grow(WorldServer world,EntityPlayerMP player,int z0,int z1){for(int x=-83;x<=-77;x++)for(int z=z0;z<=z1;z++){BlockPos p=new BlockPos(x,83,z);if(!(world.getBlockState(p).getBlock() instanceof BlockTurntableSurface)){if(!world.isAirBlock(p))require(player.interactionManager.tryHarvestBlock(p),"remove existing border before expansion");place(world,player,p);}}}
    private static void place(WorldServer world,EntityPlayerMP player,BlockPos p){place(world,player,p,new ItemStack(Block.getBlockFromName("vandorlabs:turntable_surface")));}
    private static void place(WorldServer world,EntityPlayerMP player,BlockPos p,ItemStack stack){if(world.isAirBlock(p.down()))world.setBlockState(p.down(),Blocks.STONE.getDefaultState(),3);player.setHeldItem(EnumHand.MAIN_HAND,stack);require(player.getHeldItemMainhand().onItemUse(player,world,p.down(),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,1,.5F)==EnumActionResult.SUCCESS,"ordinary surface placement "+p);player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);}
    private CopySurfaceLiveChecks(){}
}
