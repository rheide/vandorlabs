package com.vandorlabs.gear;

import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.fml.relauncher.*;

@SideOnly(Side.CLIENT)
public final class ArmJoinedWallRuntimeChecks {
    private static final BlockPos ROOT=new BlockPos(-75,83,160);
    private static int stage,ticks;
    private static boolean clicked,done;
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("arm-joined-wall stage="+stage+" tick="+ticks+": "+why);}
    public static boolean tick(Minecraft mc)throws Exception{
        if(done)return ArmRotatingEndpointRuntimeChecks.tick(mc);mc.displayGuiScreen(null);
        if(stage==1&&ticks>20&&!clicked&&mc.playerController.getCurrentGameType()==GameType.CREATIVE&&mc.player.inventory.currentItem==0&&mc.player.getHeldItemMainhand().isEmpty()&&mc.player.getDistanceSq(-71.5,84,157.5)<1&&mc.world.getTileEntity(ROOT) instanceof GearTileEntity){
            mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock(ROOT,EnumFacing.EAST,EnumHand.MAIN_HAND,.5F,.5F,.5F));clicked=true;
        }
        return mc.getIntegratedServer().callFromMainThread(()->run(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0))).get();
    }
    private static boolean run(WorldServer world,EntityPlayerMP player)throws Exception{
        require(++ticks<400,"bounded motion");
        if(stage==0){
            for(BlockPos p:BlockPos.getAllInBox(new BlockPos(-78,78,154),new BlockPos(-68,91,167))){world.removeTileEntity(p);world.setBlockToAir(p);}
            Path path=Paths.get(System.getProperty("vandorlabs.armWallFixture")).resolveSibling("arm-wall-joined-2.6.nbt");
            NBTTagCompound data;try(java.io.InputStream input=Files.newInputStream(path)){data=CompressedStreamTools.readCompressed(input);}
            NBTTagList cells=data.getTagList("Cells",10);
            for(int n=0;n<cells.tagCount();n++){NBTTagCompound cell=cells.getCompoundTagAt(n);BlockPos at=new BlockPos(cell.getInteger("X"),cell.getInteger("Y"),cell.getInteger("Z"));Block block=Block.getBlockFromName(cell.getString("Name"));require(block!=null,"registered saved block");world.setBlockState(at,block.getStateFromMeta(cell.getInteger("Meta")),2);
                if(cell.hasKey("Tile",10)){world.removeTileEntity(at);world.setTileEntity(at,TileEntity.create(world,cell.getCompoundTag("Tile")));}}
            for(int y:new int[]{83,85})((GearTileEntity)world.getTileEntity(new BlockPos(-75,y,160))).operator(player);
            player.setGameType(GameType.CREATIVE);player.capabilities.isFlying=true;player.sendPlayerAbilities();player.inventory.currentItem=0;player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);player.inventoryContainer.detectAndSendChanges();player.connection.setPlayerLocation(-71.5,84,157.5,45,0);
            GearTileEntity saved=(GearTileEntity)world.getTileEntity(ROOT);
            require(saved.progress()==0&&saved.columns().size()==2&&saved.payload().blocks.cells.size()==2,"exact folded joined saved state");
            stage=1;ticks=0;return false;
        }
        GearTileEntity arm=((GearTileEntity)world.getTileEntity(ROOT)).loadedController();require(arm!=null&&arm.columns().size()==2,"joined controller survives");
        if(ArmTransactions.locked(world,ROOT))return false;
        if(stage==1){
            if(!clicked)return false;
            if(ticks>100&&arm.progress()==0&&!arm.target()){
                require(false,"saved joined extension refused: "+arm.error());
            }
            if(arm.progress()!=1||arm.payload()==null||!arm.payload().materialized())return false;
            for(int y=83;y<=85;y++)require(world.getBlockState(new BlockPos(-73,y,160)).getBlock()==net.minecraft.init.Blocks.STONE,"all current platform blocks extend");
            arm.target(false);stage=2;ticks=0;return false;
        }
        if(stage==3){
            if(ticks<60)return false;
            require(arm.progress()==0&&!arm.target()&&arm.payload()!=null&&arm.payload().materialized(),"outside obstacle still blocks the folded group safely");
            require(world.getBlockState(new BlockPos(-74,84,162)).getBlock()==net.minecraft.init.Blocks.OBSIDIAN,"obstacle is not captured");
            world.setBlockToAir(new BlockPos(-74,84,162));arm.target(true);stage=4;ticks=0;return false;
        }
        if(stage==4){if(arm.progress()!=1||arm.payload()==null||!arm.payload().materialized())return false;arm.target(false);stage=5;ticks=0;return false;}
        if(arm.progress()!=0||arm.payload()==null||!arm.payload().materialized())return false;
        for(int y=83;y<=85;y++)require(world.getBlockState(new BlockPos(-75,y,162)).getBlock()==net.minecraft.init.Blocks.STONE,"all current platform blocks retract");
        if(stage==2){world.setBlockState(new BlockPos(-74,84,162),net.minecraft.init.Blocks.OBSIDIAN.getDefaultState(),2);arm.target(true);stage=3;ticks=0;return false;}
        System.out.println("[vandorlabs] arm-joined-wall saved PASS exact joined rotating payload edited native endpoint full reversal");done=true;return false;
    }
    private ArmJoinedWallRuntimeChecks(){}
}
