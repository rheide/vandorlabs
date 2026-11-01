package com.vandorlabs.gear;

import java.nio.file.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraftforge.fml.relauncher.*;

/** Saved horizontal Surface pad on a wall-mounted arm and four independent supports. */
@SideOnly(Side.CLIENT)
public final class SurfaceArmWorldChecks {
    private static int stage,ticks;private static boolean handoffChecked;
    private static final BlockPos ROTATING=new BlockPos(1248,73,-12126),EXTENDER=new BlockPos(1250,69,-12138);
    private static void require(boolean value,String why){if(!value)throw new IllegalStateException("surface-arm world stage="+stage+" tick="+ticks+": "+why);}
    public static boolean tick(Minecraft mc)throws Exception{
        mc.playerController.setGameType(GameType.CREATIVE);mc.displayGuiScreen(null);
        boolean clear=true;if(stage==4)for(int x=1242;x<=1244;x++)for(int z=-12127;z<=-12125;z++)if(!mc.world.isAirBlock(new BlockPos(x,73,z))||mc.world.getTileEntity(new BlockPos(x,73,z))!=null)clear=false;
        if(stage==3&&!handoffChecked){TileEntity client=mc.world.getTileEntity(ROTATING);if(client instanceof GearTileEntity){GearTileEntity root=((GearTileEntity)client).loadedController();if(root!=null&&root.progress()==1&&root.payload()!=null&&root.payload().materialized()){
            boolean ready=true;for(BlockPos p:root.payload().surfaceEndpointCells(root)){TileEntity t=mc.world.getTileEntity(p);if(!(t instanceof com.vandorlabs.tiles.TileEntityTurntableSurface)||!((com.vandorlabs.tiles.TileEntityTurntableSurface)t).appearanceReady())ready=false;}
            if(ready){com.vandorlabs.client.SurfaceArmHandoffChecks.run(root);handoffChecked=true;}
        }}}
        final boolean clientClear=clear;
        return mc.getIntegratedServer().callFromMainThread(()->run(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0),clientClear)).get();
    }
    private static void load(WorldServer world,EntityPlayerMP player,String name)throws Exception{
        Path fixtures=Paths.get(System.getProperty("vandorlabs.armWallFixture","../fixtures/arm-wall-rotating-2.6.nbt")).getParent();
        NBTTagCompound saved;try(java.io.InputStream in=Files.newInputStream(fixtures.resolve("arm-surface-"+name+"-2.7.nbt"))){saved=CompressedStreamTools.readCompressed(in);}
        NBTTagList cells=saved.getTagList("Cells",10);
        for(BlockPos p:BlockPos.getAllInBox(new BlockPos(1238,65,-12142),new BlockPos(1259,80,-12120))){world.removeTileEntity(p);world.setBlockToAir(p);}
        for(int i=0;i<cells.tagCount();i++){NBTTagCompound cell=cells.getCompoundTagAt(i);Block block=Block.getBlockFromName(cell.getString("Name"));require(block!=null,"saved block available");world.setBlockState(pos(cell),block.getStateFromMeta(cell.getInteger("Meta")),2);}
        for(int i=0;i<cells.tagCount();i++){NBTTagCompound cell=cells.getCompoundTagAt(i);if(!cell.hasKey("Tile",10))continue;BlockPos p=pos(cell);world.removeTileEntity(p);TileEntity tile=TileEntity.create(world,cell.getCompoundTag("Tile"));require(tile!=null,"saved tile available");world.setTileEntity(p,tile);tile.onLoad();if(tile instanceof GearTileEntity)((GearTileEntity)tile).operator(player);world.notifyBlockUpdate(p,world.getBlockState(p),world.getBlockState(p),3);}
        player.setGameType(GameType.CREATIVE);player.capabilities.isFlying=true;player.sendPlayerAbilities();player.inventory.currentItem=0;player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,net.minecraft.item.ItemStack.EMPTY);player.inventoryContainer.detectAndSendChanges();player.connection.setPlayerLocation(1258,76,-12125,90,20);
    }
    private static BlockPos pos(NBTTagCompound cell){return new BlockPos(cell.getInteger("X"),cell.getInteger("Y"),cell.getInteger("Z"));}
    private static GearTileEntity arm(WorldServer world,BlockPos p){return ((GearTileEntity)world.getTileEntity(p)).loadedController();}
    private static boolean run(WorldServer world,EntityPlayerMP player,boolean clientClear)throws Exception{
        require(++ticks<600,"bounded saved-world fixture");
        if(stage==0){ArmCopifierChecks.run(world,player);load(world,player,"rotating");stage=1;ticks=0;return false;}
        GearTileEntity arm=arm(world,stage<10?ROTATING:EXTENDER);require(arm!=null,"saved controller retained");
        if(ArmTransactions.locked(world,arm.getPos()))return false;
        if(stage==1){if(ticks<30)return false;ArmPayload.Endpoint endpoint=ArmPayload.endpoint(arm,1);require(endpoint!=null,"saved native endpoint");ArmPayload found=endpoint.capture(player);require(found!=null&&found.blocks.cells.size()==9,"horizontal 3x3 pad must capture all 9 Surfaces, got "+(found==null?0:found.blocks.cells.size()));arm.target(false);stage=2;ticks=0;return false;}
        if(stage==2){if(arm.progress()!=0)return false;require(arm.payload()!=null&&arm.payload().blocks.cells.size()==9,"all Surfaces move together");
            java.util.List<com.vandorlabs.vehicle.VehicleStructure.Cell> partial=new java.util.ArrayList<>();for(com.vandorlabs.vehicle.VehicleStructure.Cell cell:arm.payload().blocks.cells)if(cell.pos.getX()<2)partial.add(cell);require(partial.size()==6,"partial pad negative control");
            com.vandorlabs.vehicle.VehicleWorld view=new com.vandorlabs.vehicle.VehicleWorld(world,com.vandorlabs.vehicle.VehicleStructure.display(partial));for(TileEntity tile:view.tiles.values())if(tile instanceof com.vandorlabs.tiles.TileEntityTurntableSurface)require(((com.vandorlabs.tiles.TileEntityTurntableSurface)tile).appearanceLayout().size==1,"partial carried pad may not draw a stale 3x3 face over missing cells");for(int x=1242;x<=1244;x++)for(int z=-12127;z<=-12125;z++)require(world.isAirBlock(new BlockPos(x,73,z)),"no native Surface remains beneath the original decal");stage=4;ticks=0;return false;}
        if(stage==4){if(!clientClear)return false;arm.target(true);stage=3;ticks=0;return false;}
        if(stage==3){if(!handoffChecked||arm.progress()!=1||arm.payload()==null||!arm.payload().materialized())return false;require(arm.payload().blocks.cells.size()==9,"all 9 Surfaces return");load(world,player,"extender");stage=10;ticks=0;return false;}
        if(stage==10){if(ticks<30)return false;require(!arm.joinEnabled()&&arm.columns().size()==1,"saved four supports are independent");arm.target(false);stage=11;ticks=0;return false;}
        if(stage==11){if(ticks<70)return false;require(arm.progress()==1&&arm.target()&&arm.error().contains("Join Arms"),"independent supports refuse safely");for(int x:new int[]{1250,1254})for(int z:new int[]{-12138,-12134}){GearTileEntity support=arm(world,new BlockPos(x,69,z));NBTTagCompound settings=support.settings();settings.setBoolean("JoinArms",true);support.configure(settings);}stage=12;ticks=0;return false;}
        if(stage==12){if(ticks<30)return false;require(arm.columns().size()==4,"enabled four matching supports join");arm.target(false);stage=13;ticks=0;return false;}
        if(stage==13){if(arm.progress()!=0)return false;require(arm.payload()!=null&&arm.payload().materialized()&&arm.payload().blocks.cells.size()==25,"joined 5x5 Surface pad retracts intact");arm.target(true);stage=14;ticks=0;return false;}
        if(stage==14){if(arm.progress()!=1||arm.payload()==null||!arm.payload().materialized())return false;require(arm.payload().blocks.cells.size()==25,"joined 5x5 returns intact");System.out.println("[vandorlabs] surface-arm-world PASS saved horizontal 3x3 wall arm all 9 blocks source clearance return; four independent supports safe refusal and joined 5x5 native stroke");stage=15;return true;}
        return stage==15;
    }
    private SurfaceArmWorldChecks(){}
}
