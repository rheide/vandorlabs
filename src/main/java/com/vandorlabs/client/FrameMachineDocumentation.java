package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.navigation.NavigationAssemblies;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Stateful build stages in a disposable world; separate from asserted release regressions. */
final class FrameMachineDocumentation {
    private static final BlockPos ROOT=new BlockPos(24,80,24);
    private static final List<FrameMachineDesign> DESIGNS=new ArrayList<>(FrameMachineDesign.all());
    private static final List<Map<String,Object>> records=new ArrayList<>();
    private static int design,step,settle;
    private static boolean started,captured,done;
    private static String pending;
    private static File output;
    private static WorldServer world(Minecraft mc){return mc.getIntegratedServer().getWorld(0);}
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayerByUsername(mc.player.getName());}
    private static String id(){return design<DESIGNS.size()?DESIGNS.get(design).design:design==DESIGNS.size()?"navigation_table_flat":"navigation_table_angled";}
    private static int steps(){return design<DESIGNS.size()?(DESIGNS.get(design).hasChip()?5:4):4;}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("machine build capture: "+why);}
    private static void prepare(Minecraft mc)throws Exception{
        FrameMachineDesign spec=design<DESIGNS.size()?DESIGNS.get(design):null;
        int width=spec==null?3:spec.width,height=spec==null?1:spec.height,depth=spec==null?(id().endsWith("flat")?2:1):spec.depth;
        mc.getIntegratedServer().addScheduledTask(()->{
            WorldServer world=world(mc);world.getGameRules().setOrCreateGameRule("doDaylightCycle","false");world.setWorldTime(6000);world.getWorldInfo().setRaining(false);
            if(step==0){
                for(BlockPos p:BlockPos.getAllInBox(ROOT.add(-2,0,-2),ROOT.add(9,8,9)))if(world.getTileEntity(p) instanceof TileEntityConstructor)world.setBlockToAir(p);
                for(BlockPos p:BlockPos.getAllInBox(ROOT.add(-2,0,-2),ROOT.add(9,8,9)))world.setBlockToAir(p);
                for(net.minecraft.entity.item.EntityItem item:world.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class,new AxisAlignedBB(ROOT.add(-3,-2,-3),ROOT.add(10,10,10))))item.setDead();
                for(BlockPos p:BlockPos.getAllInBox(ROOT.add(-8,-1,-8),ROOT.add(12,-1,12)))world.setBlockState(p,Blocks.STONE.getDefaultState(),2);
            }
            if(spec!=null){
                if(step<3)BridgeMachineChecks.frame(world,ROOT,spec,step);
                else{
                    TileEntityConstructor tile=(TileEntityConstructor)world.getTileEntity(ROOT);
                    if(step==3){tile.core().onBlockActivated(world,ROOT,world.getBlockState(ROOT),player(mc),EnumHand.MAIN_HAND,EnumFacing.NORTH,.5F,.5F,0);require(tile.formed(),"Core right-click must form "+spec.design);}
                    else{tile.setInventorySlotContents(0,new ItemStack(com.vandorlabs.items.ModItems.DATA_CHIP,1,4));require(tile.getOutputLevel()==15,"inserted chip activates "+spec.design);}
                }
            }else{
                Block block=Block.getBlockFromName("vandorlabs:"+id());
                if(step<3){
                    int count=step==0?1:3;
                    for(int x=0;x<count;x++)world.setBlockState(ROOT.east(x),block.getDefaultState(),2);
                    if(step==2&&depth==2)for(int x=0;x<3;x++)world.setBlockState(ROOT.east(x).south(),block.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.SOUTH),2);
                    if(step==1&&depth==1)world.setBlockToAir(ROOT.east(2));
                    NavigationAssemblies.rebuild(world,ROOT);
                }else{
                    TileEntityNavigationTable tile=((TileEntityNavigationTable)world.getTileEntity(ROOT)).reader();require(tile!=null,"joined table has its reader");tile.configureDisplay(new com.vandorlabs.navigation.NavigationScreenSettings(NavigationRuntimeChecks.artwork(),true,1,false,true),false,127,0,com.vandorlabs.redstone.ChannelList.EMPTY);tile.setInventorySlotContents(0,new ItemStack(com.vandorlabs.items.ModItems.DATA_CHIP,1,3));
                }
                int mounts=0;for(BlockPos p:BlockPos.getAllInBox(ROOT,ROOT.add(3,0,2)))if(world.getTileEntity(p) instanceof TileEntityNavigationTable&&((TileEntityNavigationTable)world.getTileEntity(p)).hasMount())mounts++;
                require(mounts==1,"exactly one reader in documented navigation assembly");
            }
            Vec3d target=new Vec3d(ROOT).addVector(width*.5,height*.43,depth*.5);
            Vec3d eye=target.addVector(-(width+3)*.8,height*.5+2.3,-(depth+3)*1.05);
            Vec3d delta=target.subtract(eye);float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));
            EntityPlayerMP p=player(mc);p.capabilities.allowFlying=true;p.capabilities.isFlying=true;p.sendPlayerAbilities();p.setSneaking(false);p.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);p.connection.setPlayerLocation(eye.x,eye.y-p.getEyeHeight(),eye.z,yaw,pitch);p.motionX=p.motionY=p.motionZ=0;
        }).get();
        mc.displayGuiScreen(null);mc.gameSettings.hideGUI=true;mc.gameSettings.thirdPersonView=0;mc.gameSettings.showDebugInfo=false;mc.setIngameFocus();
        settle=0;captured=false;
    }
    static boolean tick(Minecraft mc,File directory)throws Exception{
        output=directory;if(done)return true;
        if(!started){started=true;prepare(mc);return false;}
        if(captured){
            step++;if(step>=steps()){design++;step=0;}
            if(design>=DESIGNS.size()+2){
                Files.write(new File(output,"machine-build-steps.json").toPath(),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(records).getBytes(StandardCharsets.UTF_8));
                System.out.println("[vandorlabs] machine-build-docs PASS asserted stages="+records.size()+" Core assemblies="+DESIGNS.size()+" navigation layouts=2");done=true;return true;
            }
            prepare(mc);return false;
        }
        if(++settle>=30&&pending==null){
            if(design<DESIGNS.size()&&step>=3){net.minecraft.tileentity.TileEntity tile=mc.world.getTileEntity(ROOT);if(!(tile instanceof TileEntityConstructor)||!((TileEntityConstructor)tile).formed())return false;}
            pending="shot_build_"+id()+"_"+(step+1)+".png";
        }
        require(settle<600,"bounded scene synchronization");return false;
    }
    static void render(Minecraft mc){
        if(pending==null)return;
        try{
            javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,pending));
            Map<String,Object> row=new LinkedHashMap<>();row.put("design",id());row.put("step",step+1);row.put("image",pending);row.put("asserted",true);records.add(row);
            System.out.println("[vandorlabs] machine-build-step "+id()+" "+(step+1)+" captured");pending=null;captured=true;
        }catch(IOException error){throw new IllegalStateException(error);}
    }
}
