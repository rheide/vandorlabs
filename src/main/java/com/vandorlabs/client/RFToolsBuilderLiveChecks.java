package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.tiles.TileEntityCanopy;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Real installed RFTools Builder: direct placement and powered automatic Shape Card fill. */
final class RFToolsBuilderLiveChecks {
    private static final BlockPos BUILDER=new BlockPos(560,100,400),TARGET=BUILDER.east(4);
    private static int ticks,stage;
    private static Block canopy;
    private static java.util.List<BlockPos> filled=new java.util.ArrayList<>();
    private static int placedAt;
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("RFTools Builder: "+why);}
    static boolean tick(Minecraft mc,java.io.File output)throws Exception{
        mc.displayGuiScreen(null);mc.gameSettings.pauseOnLostFocus=false;if(++ticks<(stage<2?40:1))return false;
        if(stage==0){mc.getIntegratedServer().addScheduledTask(()->{try{
            WorldServer world=mc.getIntegratedServer().getWorld(0);Block builder=Block.getBlockFromName("rftools:builder");canopy=Block.getBlockFromName("vandorlabs:regular_canopy_glass");require(builder!=Blocks.AIR&&canopy instanceof BlockCanopy,"installed Builder and Canopy Glass registry");
            for(BlockPos at:BlockPos.getAllInBox(BUILDER.add(-1,0,-1),BUILDER.add(12,3,7))){world.getChunkFromBlockCoords(at);world.setBlockToAir(at);}
            world.setBlockState(BUILDER,builder.getDefaultState(),3);world.setBlockState(BUILDER.up(),Blocks.CHEST.getDefaultState(),3);TileEntityChest supply=(TileEntityChest)world.getTileEntity(BUILDER.up());supply.setInventorySlotContents(0,new ItemStack(canopy,32));Object machine=world.getTileEntity(BUILDER);
            java.lang.reflect.Method build=machine.getClass().getMethod("buildBlock",int.class,BlockPos.class,net.minecraft.block.state.IBlockState.class,net.minecraft.block.state.IBlockState.class);
            for(int x=0;x<3;x++)for(int z=0;z<3;z++){BlockPos at=TARGET.add(x,0,z);build.invoke(machine,0,at,Blocks.AIR.getDefaultState(),null);TileEntityCanopy tile=TileEntityCanopy.at(world,at);require(world.getBlockState(at).getBlock()==canopy&&tile!=null,"native buildBlock did not place supplied Canopy Glass");}
            require(supply.getStackInSlot(0).getCount()==23,"exactly nine supply blocks consumed");
        }catch(Exception e){throw new IllegalStateException(e);}}).get();stage++;ticks=0;return false;}
        if(stage==1){mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=mc.getIntegratedServer().getWorld(0);for(int x=0;x<3;x++)for(int z=0;z<3;z++){BlockPos at=TARGET.add(x,0,z);TileEntityCanopy tile=TileEntityCanopy.at(world,at);require(world.getBlockState(at).getBlock()==canopy&&tile!=null,"Canopy Glass disappeared during native world ticks at "+at);require(at.equals(tile.anchor),"native root ownership changed at "+at);}}).get();mc.getIntegratedServer().addScheduledTask(()->{try{WorldServer world=mc.getIntegratedServer().getWorld(0);for(BlockPos at:BlockPos.getAllInBox(BUILDER.add(2,0,-3),BUILDER.add(13,8,6)))world.setBlockToAir(at);TileEntityChest supply=(TileEntityChest)world.getTileEntity(BUILDER.up());supply.setInventorySlotContents(0,new ItemStack(canopy,32));
                net.minecraft.entity.player.EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);player.setGameType(net.minecraft.world.GameType.CREATIVE);player.capabilities.allowFlying=true;player.capabilities.isFlying=true;player.sendPlayerAbilities();player.connection.setPlayerLocation(BUILDER.getX()+6,BUILDER.getY()+5,BUILDER.getZ()+8,180,30);
                ItemStack card=new ItemStack(net.minecraft.item.Item.getByNameOrId("rftools:shape_card"));require(!card.isEmpty(),"native shape card item");Class<?> type=Class.forName("mcjty.rftools.items.builder.ShapeCardItem"),shape=Class.forName("mcjty.rftools.shapes.Shape");type.getMethod("setShape",ItemStack.class,shape,boolean.class).invoke(null,card,shape.getField("SHAPE_BOX").get(null),true);type.getMethod("setDimension",ItemStack.class,int.class,int.class,int.class).invoke(null,card,3,3,3);type.getMethod("setOffset",ItemStack.class,int.class,int.class,int.class).invoke(null,card,8,3,0);Object machine=world.getTileEntity(BUILDER);((net.minecraft.inventory.IInventory)machine).setInventorySlotContents(0,card);machine.getClass().getMethod("modifyEnergyStored",long.class).invoke(machine,1000000L);machine.getClass().getMethod("setRSModeInt",int.class).invoke(machine,2);machine.getClass().getMethod("setPowerInput",int.class).invoke(machine,0);world.setBlockState(BUILDER.west(),Blocks.REDSTONE_BLOCK.getDefaultState(),3);machine.getClass().getMethod("setPowerInput",int.class).invoke(machine,15);
                System.out.println("[vandorlabs][reprolab] rftools-builder automatic Shape Card="+card.getTagCompound());
            }catch(Exception e){throw new IllegalStateException(e);}}).get();stage=2;ticks=0;return false;}
        if(stage==2){int count=mc.getIntegratedServer().callFromMainThread(()->{WorldServer world=mc.getIntegratedServer().getWorld(0);filled.clear();for(BlockPos at:BlockPos.getAllInBox(BUILDER.add(2,0,-3),BUILDER.add(13,8,6)))if(world.getBlockState(at).getBlock()==canopy)filled.add(at.toImmutable());return filled.size();}).get();require(ticks<600,"automatic solid-box fill timed out with "+count+" blocks");if(count<27)return false;require(count==27,"solid box filled wrong volume");stage=3;placedAt=ticks;return false;}
        if(stage==3){if(ticks-placedAt<60)return false;mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=mc.getIntegratedServer().getWorld(0);for(BlockPos at:filled)require(world.getBlockState(at).getBlock()==canopy&&TileEntityCanopy.at(world,at)!=null,"automatic Builder lost block/tile after placement at "+at);for(BlockPos at:filled){TileEntityCanopy tile=TileEntityCanopy.at(world,at);require(at.equals(tile.anchor)&&tile.hasSavedFootprint(),"automatic placement ownership missing at "+at);}TileEntityChest supply=(TileEntityChest)world.getTileEntity(BUILDER.up());require(supply.getStackInSlot(0).getCount()==5,"automatic Builder must consume exactly27 supplies");}).get();int mesh=0;for(BlockPos at:filled){require(mc.world.getBlockState(at).getBlock()==canopy&&TileEntityCanopy.at(mc.world,at)!=null,"client lost Canopy Glass at "+at);if(TileEntityCanopy.at(mc.world,at).chunkMesh!=null)mesh++;}require(mesh>0,"native glass chunk meshes never published on client");javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new java.io.File(output,"rftools-builder-canopy-solid-fill.png"));System.out.println("[vandorlabs][reprolab] rftools-builder PASS installed7.72 native-buildBlock9 automatic-powered-ShapeCard-solid-box27 exact-supply consumption persistent-after60-ticks client-blocks27 visible-meshes="+mesh);return true;}
        return false;
    }
    private RFToolsBuilderLiveChecks(){}
}
