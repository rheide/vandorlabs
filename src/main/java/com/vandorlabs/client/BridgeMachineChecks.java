package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.navigation.*;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.redstone.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import java.io.File;
import java.util.*;

/** Assert assembly and inventory behavior independently from documentation capture. */
final class BridgeMachineChecks {
    private static int ticks;
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("bridge machines: "+why);}
    static void frame(WorldServer world,BlockPos root,FrameMachineDesign spec,int stage){
        BlockConstructorCore block=(BlockConstructorCore)Block.getBlockFromName("vandorlabs:"+spec.block);
        Block matter=Block.getBlockFromName("vandorlabs:programmable_matter_block");
        if(stage==0)world.setBlockState(root,block.getDefaultState(),2);
        TileEntityConstructor tile=(TileEntityConstructor)world.getTileEntity(root);
        for(int y=0;y<spec.height;y++)for(int x=0;x<spec.width;x++)for(int z=0;z<spec.depth;z++){
            if(x==0&&y==0&&z==0)continue;
            boolean include=stage==0?y==0:spec.width==1&&spec.depth==1?y<=stage:
                    stage==1?y==0||(x==0||x==spec.width-1)&&(z==0||z==spec.depth-1):tile.frame(x,y,z);
            if(include)world.setBlockState(root.add(x,y,z),matter.getDefaultState(),2);
        }
    }
    private static void clear(WorldServer world,BlockPos root){
        // Remove controllers first so normal dismantling restores the reservations.
        for(BlockPos p:BlockPos.getAllInBox(root.add(-1,-1,-1),root.add(8,8,8)))if(world.getTileEntity(p) instanceof TileEntityConstructor)world.setBlockToAir(p);
        for(BlockPos p:BlockPos.getAllInBox(root.add(-1,0,-1),root.add(8,8,8)))world.setBlockToAir(p);
    }
    private static void chipMachines(WorldServer world,EntityPlayerMP player){
        BlockPos root=new BlockPos(80,100,80);int cases=0;ItemStack held=player.getHeldItemMainhand();boolean creative=player.capabilities.isCreativeMode;
        Vec3d before=player.getPositionVector();float yaw=player.rotationYaw,pitch=player.rotationPitch;
        try{for(FrameMachineDesign spec:FrameMachineDesign.all())if(spec.hasChip()){
            clear(world,root);frame(world,root,spec,0);frame(world,root,spec,2);TileEntityConstructor tile=(TileEntityConstructor)world.getTileEntity(root);
            require(tile.assemble(),spec.design+" chip fixture forms");tile.configureDisplay(new NavigationScreenSettings(-1,true,1,false,true),true,127,0,ChannelList.EMPTY);tile.setRedstoneChannels(ChannelList.of(64123));RedstoneChannels.register(tile);
            MachineMountSurface mount=tile.mount();Vec3d eye=mount.center.add(mount.normal.scale(2.5));Vec3d direction=mount.center.subtract(eye);
            player.setPosition(eye.x,eye.y-player.getEyeHeight(),eye.z);player.rotationYaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));player.rotationPitch=0;player.capabilities.isCreativeMode=false;
            require(mount.hit(player),"authored chip plane is reachable");
            for(int mode=0;mode<4;mode++){
                tile.configure(false,1<<4,mode);ItemStack stack=new ItemStack(ModItems.DATA_CHIP,2,4);stack.setStackDisplayName("Integrated reader fixture");player.setHeldItem(EnumHand.MAIN_HAND,stack);
                require(TokenSlotInteractions.activate(tile,player,EnumHand.MAIN_HAND),"same mount interaction accepts chip");
                require(stack.getCount()==1&&!tile.isEmpty(),"one real item is consumed/borrowed");
                if(mode>=2){tile.expire(world.getTotalWorldTime()+8);require(tile.screenOn()&&tile.getOutputLevel()==0,"borrowed credential authorizes without output");}
                else require(tile.screenOn()&&tile.getOutputLevel()==0,"retained chip authorizes without output");
                require(RedstoneChannels.level(world,64123)==0,"embedded chip never publishes channel power");
                NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());TileEntityConstructor copy=new TileEntityConstructor();copy.readFromNBT(saved);copy.setWorld(world);
                require(copy.getStackInSlot(0).getDisplayName().equals("Integrated reader fixture")&&copy.returning()==(mode>=2),"full item and pending return survive NBT");
                if(mode>=2){tile.expire(world.getTotalWorldTime()+25);require(tile.isEmpty(),"borrowed chip returns to its player");}
                else tile.takeForDrop();
                tile.configure(true,127,0);require(tile.getOutputLevel()==0,"configuration resets output");cases++;
                world.setTotalWorldTime(world.getTotalWorldTime()+30);
            }
            tile.configure(false,1<<4,0);require(!tile.authorized(new ItemStack(ModItems.DATA_CHIP,1,3)),"company filter rejects other chips");
            tile.dismantle();require(tile.getOutputLevel()==0,"dismantled core cannot emit chip output");
        }}finally{clear(world,root);player.setHeldItem(EnumHand.MAIN_HAND,held);player.capabilities.isCreativeMode=creative;player.setPosition(before.x,before.y,before.z);player.rotationYaw=yaw;player.rotationPitch=pitch;}
        System.out.println("[vandorlabs] integrated-chip-mounts PASS cases="+cases+" authored hits four behaviors channels full NBT returns filters dismantling");
    }
    private static void navigation(WorldServer world){
        BlockPos base=new BlockPos(96,100,96);int cases=0;
        for(int x=5;x<=7;x++)for(int z=5;z<=7;z++)world.getChunkFromChunkCoords(x,z);
        for(String kind:new String[]{"flat","angled"})for(EnumFacing facing:EnumFacing.HORIZONTALS){
            BlockNavigationTable block=(BlockNavigationTable)Block.getBlockFromName("vandorlabs:navigation_table_"+kind);
            int depth=kind.equals("flat")?2:1;
            for(int x=0;x<3;x++)for(int z=0;z<depth;z++){
                Vec3d p=MachineMountSurface.rotate(new Vec3d(x+.5,0,z+.5),facing,3,depth);
                world.setBlockState(base.add((int)Math.floor(p.x),0,(int)Math.floor(p.z)),block.getDefaultState().withProperty(BlockVandorDirectional.FACING,facing),2);
            }
            NavigationAssemblies.rebuild(world,base);List<TileEntityNavigationTable> tiles=new ArrayList<>();
            for(BlockPos p:BlockPos.getAllInBox(base,base.add(3,0,3)))if(world.getTileEntity(p) instanceof TileEntityNavigationTable)tiles.add((TileEntityNavigationTable)world.getTileEntity(p));
            require(tiles.stream().filter(TileEntityNavigationTable::ownsReader).count()==1,"exactly one mount in every joined facing");
            TileEntityNavigationTable owner=tiles.stream().filter(TileEntityNavigationTable::ownsReader).findFirst().get();
            owner.configureDisplay(new NavigationScreenSettings(-1,true,1,false,true),true,127,0,ChannelList.EMPTY);owner.setInventorySlotContents(0,new ItemStack(ModItems.DATA_CHIP,1,4));owner.setRedstoneChannels(ChannelList.of(64124));RedstoneChannels.register(owner);
            // Extend the front width from 3 to 5: ownership moves from X1 to X2.
            for(int x=3;x<5;x++)for(int z=0;z<depth;z++){
                EnumFacing right=facing.rotateY();BlockPos origin=base;
                // Continue relative to the original selected owner's local X1.
                BlockPos at=owner.getPos().offset(right,x-1).offset(facing.getOpposite(),z);
                world.setBlockState(at,block.getDefaultState().withProperty(BlockVandorDirectional.FACING,facing),2);
            }
            NavigationAssemblies.rebuild(world,owner.getPos());
            TileEntityNavigationTable moved=owner.reader();
            require(moved!=null&&moved!=owner&&!moved.isEmpty()&&owner.isEmpty(),"chip moves once to the new front-center module");
            require(moved.getRedstoneChannels().contains(64124),"reader configuration follows its mount");
            NBTTagCompound nbt=moved.writeToNBT(new NBTTagCompound());TileEntityNavigationTable restored=new TileEntityNavigationTable();restored.readFromNBT(nbt);
            require(restored.hasMount()&&!restored.isEmpty(),"layout and chip reload together");
            for(BlockPos p:BlockPos.getAllInBox(base.add(-6,-1,-6),base.add(8,1,8)))world.setBlockToAir(p);cases++;
        }
        System.out.println("[vandorlabs] navigation-tables PASS cases="+cases+" both kinds four facings one mount owner migration item conservation NBT");
    }
    private static void steering(WorldServer world){
        try{
            java.lang.reflect.Method advance=EntityGroundVehicle.class.getDeclaredMethod("advance",boolean.class);advance.setAccessible(true);
            VehicleStructure snapshot=new VehicleStructure(Arrays.asList(new VehicleStructure.Cell(BlockPos.ORIGIN,Blocks.STONE.getDefaultState(),null),
                    new VehicleStructure.Cell(BlockPos.ORIGIN.up(),ModBlocks.PILOT_SEAT.getDefaultState(),null),
                    new VehicleStructure.Cell(BlockPos.ORIGIN.up(2),ModBlocks.PILOT_SEAT.getDefaultState().withProperty(BlockPilotSeat.UPPER,true),null)),BlockPos.ORIGIN.up(),EnumFacing.NORTH,1);
            for(int x=0;x<=2;x++)for(int z=0;z<=2;z++)world.getChunkFromChunkCoords(x,z);
            for(int steer:new int[]{-1,1}){
                float[] yaw=new float[2];
                for(int reverse=0;reverse<2;reverse++){
                    EntityGroundVehicle craft=new EntityGroundVehicle(world);craft.install(snapshot);craft.hover=true;craft.hoverTarget=120;craft.setPosition(16,120,16);craft.driveSpeed=reverse==0?.18:-.18;craft.steering=steer;
                    craft.autopilotControls((byte)(reverse==0?1:-1),(byte)steer,false);advance.invoke(craft,true);yaw[reverse]=craft.rotationYaw;craft.setDead();
                }
                require(Math.abs(yaw[0]+yaw[1])<1e-6&&Math.abs(yaw[0])>1,"actual Hover simulation reverses left/right yaw");
            }
        }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        System.out.println("[vandorlabs] reverse-hover-steering PASS actual controller steps forward reverse left right");
    }
    private static boolean runtimeDone;
    static boolean tick(Minecraft mc,File output)throws Exception{
        if(++ticks==1){mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.getToastGui().clear();}
        if(ticks<30)return false;
        if(!runtimeDone){
        mc.getIntegratedServer().addScheduledTask(()->{
            WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUsername(mc.player.getName());
            world.getGameRules().setOrCreateGameRule("doDaylightCycle","false");world.setWorldTime(6000);
            com.vandorlabs.construction.FrameMachineRuntimeChecks.run(world,player);TokenSlotRuntimeChecks.run(world);chipMachines(world,player);navigation(world);NavigationRuntimeChecks.run(world);MachineDisplayRuntimeChecks.run(world,player);steering(world);
        }).get();
        for(FrameMachineDesign spec:FrameMachineDesign.all()){
            String path=spec.state_models!=null?spec.state_models[1]:"complete.obj";
            require(com.vandorlabs.client.constructors.UniversalConstructorModelCache.get(spec.design,path).getQuadCount()>10,"actual baked OBJ is available for "+spec.design);
        }
        com.vandorlabs.client.constructors.MachineMotionChecks.run(mc);UtilityItemRuntimeChecks.run(mc,output);ConstructorRenderLifetimeChecks.run(mc);MachineDisplayVisualChecks.run(mc,output);runtimeDone=true;
        }
        if(!NavigationClientChecks.tick(mc,output))return false;
        System.out.println("[vandorlabs] bridge-machines PASS assembly regressions embedded mount behavior joining models item padding reverse steering");return true;
    }
}
