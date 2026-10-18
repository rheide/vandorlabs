package com.vandorlabs.client;

import com.vandorlabs.gear.*;
import com.vandorlabs.gear.core.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** Actual connected assemblies, native collision, save links and Hover restoration. */
final class ModularGearRuntimeChecks {
    static GearTileEntity build(WorldServer world,BlockPos root,boolean ski,boolean swing,EnumFacing facing,int height,int width,int depth){
        GearBlock strut=swing?GearRegistry.GEAR_SWING_ARM:GearRegistry.GEAR_EXTENDER,foot=ski?GearRegistry.LANDING_SKI:GearRegistry.LANDING_PLATE;
        for(int x=0;x<width;x++)for(int z=0;z<depth;z++)world.setBlockState(root.add(x,-height,z),foot.getDefaultState().withProperty(GearBlock.FACING,facing));
        for(int x:new int[]{0,width-1}){world.setBlockState(root.add(x,1,0),Blocks.IRON_BLOCK.getDefaultState());for(int y=0;y<height;y++)world.setBlockState(root.add(x,-y,0),strut.getDefaultState().withProperty(GearBlock.FACING,facing));}
        GearTileEntity tile=(GearTileEntity)world.getTileEntity(root);require(tile.rebuild(),"formation: "+tile.error());return tile.loadedController();
    }
    static void run(WorldServer world,EntityPlayerMP player)throws Exception{
        BlockPos placement=new BlockPos(475,215,180);world.setBlockState(placement,GearRegistry.LANDING_PLATE.getDefaultState());
        require(GearRegistry.LANDING_PLATE.collisionRayTrace(world.getBlockState(placement),world,placement,new Vec3d(475.5,217,180.5),new Vec3d(475.5,214,180.5))!=null,"unformed plate can be selected");
        ItemStack held=player.getHeldItemMainhand();try{player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,new ItemStack(GearRegistry.GEAR_EXTENDER));
            require(!GearRegistry.LANDING_PLATE.onBlockActivated(world,placement,world.getBlockState(placement),player,net.minecraft.util.EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.1F,.5F),"plate permits strut placement");
            require(player.getHeldItemMainhand().onItemUse(player,world,placement,net.minecraft.util.EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.1F,.5F)==net.minecraft.util.EnumActionResult.SUCCESS&&world.getBlockState(placement.up()).getBlock()==GearRegistry.GEAR_EXTENDER,"actual extender item placement on plate");
            require(GearRegistry.GEAR_EXTENDER.collisionRayTrace(world.getBlockState(placement.up()),world,placement.up(),new Vec3d(475.5,218,180.5),new Vec3d(475.5,214,180.5))!=null,"unformed extender can be selected");
        }finally{player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,held);world.setBlockToAir(placement.up());world.setBlockToAir(placement);}
        recipes(world);
        NBTTagCompound unformed=new GearTileEntity().writeToNBT(new NBTTagCompound()),cycled=unformed.copy();for(int i=0;i<4;i++)GearData.rotate(cycled,1);require(unformed.equals(cycled),"unformed gear rotation preserves absent fields");
        BlockPos base=new BlockPos(390,205,205);Vec3d playerBefore=player.getPositionVector();
        try{for(boolean ski:new boolean[]{false,true})for(boolean swing:new boolean[]{false,true})for(EnumFacing facing:EnumFacing.HORIZONTALS){
            clear(world,base);int height=swing?1:3;GearTileEntity root=build(world,base,ski,swing,facing,height,3,2);
            require(root.columns().size()==2&&root.assembly().feet().size()==6,"shared group with two struts and six feet");
            for(BlockPos p:root.members())require(((GearTileEntity)world.getTileEntity(p)).loadedController()==root,"one owner for every part");
            require(root.getWorld().getBlockState(root.getPos()).getValue(GearBlock.ASSEMBLED),"formed state retains controller tile");
            GearTileEntity copy=(GearTileEntity)TileEntity.create(world,root.writeToNBT(new NBTTagCompound()));require(copy.columns().equals(root.columns())&&copy.assembly().feet().equals(root.assembly().feet()),"group NBT");
            NBTTagCompound tag=root.writeToNBT(new NBTTagCompound()),rotated=tag.copy();for(int t=0;t<4;t++)GearData.rotate(rotated,1);require(tag.equals(rotated),"cardinal group NBT round trip");
            require(!root.collisionBoxes().isEmpty(),"assembled collision");
            root.target(false);for(int tick=0;tick<32;tick++)root.update();require(root.progress()==0,"complete retraction "+ski+" "+swing+" "+facing+" progress="+root.progress());
            if(swing){GearMotion.Pose pose=GearMotion.pose(root.mode(),root.direction(),1,root.progress());GearMotion.Vec a=pose.transformFootVertex(0,-1,0),b=pose.transformFootVertex(2,-1,1);require(Math.abs(a.y-b.y)<1e-9,"shared feet remain level");}
            root.target(true);for(int tick=0;tick<32;tick++)root.update();require(root.fullyDeployed(),"complete deployment");
            BlockPos obstruction=root.getPos().add(1,-height+1,1);if(swing)switch(root.direction()){case NORTH:obstruction=root.getPos().add(1,-1,-1);break;case SOUTH:obstruction=root.getPos().add(1,-1,2);break;case EAST:obstruction=root.getPos().add(3,-1,1);break;case WEST:obstruction=root.getPos().add(-1,-1,1);break;default:break;}world.setBlockState(obstruction,Blocks.STONE.getDefaultState());root.target(false);for(int tick=0;tick<32;tick++)root.update();require(root.progress()>0,"obstruction prevents complete stroke");world.setBlockToAir(obstruction);for(int tick=0;tick<32;tick++)root.update();require(root.progress()==0,"motion resumes after obstacle removed");
            root.target(true);for(int tick=0;tick<32;tick++)root.update();
            NBTTagCompound settings=root.settings();settings.setInteger("Trigger",2);root.configure(settings);root.input();require(root.target(),"OFF trigger deploys unpowered");settings.setInteger("Trigger",1);root.configure(settings);root.input();require(!root.target(),"ON trigger stows unpowered");settings.setInteger("Trigger",0);settings.setBoolean("Deployed",true);settings.setString("Channels","65001,65002");root.configure(settings);
            ItemStack picked=root.getWorld().getBlockState(root.getPos()).getBlock().getPickBlock(world.getBlockState(root.getPos()),null,world,root.getPos(),player);require(picked.hasTagCompound()&&picked.getTagCompound().getCompoundTag("ModularGearSettings").getString("Channels").contains("65002"),"configured pick item");
            root.releaseAssembly();for(BlockPos p:BlockPos.getAllInBox(base.add(0,-height,0),base.add(2,0,1)))if(world.getTileEntity(p) instanceof GearTileEntity)require(!world.getBlockState(p).getValue(GearBlock.ASSEMBLED),"release leaves ordinary parts");
        }
        roundTrip(world,player);
        }finally{clear(world,base);player.setPositionAndUpdate(playerBefore.x,playerBefore.y,playerBefore.z);}
        System.out.println("[vandorlabs][reprolab] modular-gear PASS plate/ski straight/swing four facings shared struts level feet motion obstruction persistence items channels and Hover round trip");
    }
    private static void roundTrip(WorldServer world,EntityPlayerMP player)throws Exception{
        BlockPos base=new BlockPos(430,140,190),seat=base.add(1,2,1);EntityGroundVehicle craft=null;
        try{
            clear(world,base);for(BlockPos p:BlockPos.getAllInBox(base.add(-4,-4,-4),base.add(6,-4,6)))world.setBlockState(p,Blocks.STONE.getDefaultState());
            for(int x=0;x<3;x++)for(int z=0;z<3;z++){world.setBlockState(base.add(x,1,z),Blocks.IRON_BLOCK.getDefaultState());world.setBlockState(base.add(x,-1,z),GearRegistry.LANDING_PLATE.getDefaultState());}
            for(BlockPos p:new BlockPos[]{base,base.east(2),base.south(2)})world.setBlockState(p,GearRegistry.GEAR_EXTENDER.getDefaultState());GearTileEntity gear=(GearTileEntity)world.getTileEntity(base);require(gear.rebuild()&&gear.columns().size()==3,"three-strut vehicle group");
            NBTTagCompound settings=gear.settings();settings.setString("Channels","65003");gear.configure(settings);
            Block pilot=Block.getBlockFromName("vandorlabs:pilot_seat");world.setBlockState(seat,pilot.getDefaultState());pilot.onBlockPlacedBy(world,seat,world.getBlockState(seat),player,new ItemStack(pilot));
            world.setBlockState(base.add(0,2,0),Block.getBlockFromName("vandorlabs:impulse_engine").getDefaultState());for(int x=0;x<3;x++)world.setBlockState(base.add(x,2,2),Block.getBlockFromName("vandorlabs:antigravity_plate").getDefaultState());
            VehicleScan scan=new VehicleScan(world,seat);for(int tick=0;tick<200&&!scan.step(128);tick++);require(scan.error==null&&scan.result!=null,"vehicle scan "+scan.error);require(scan.result.gearCount==3&&scan.result.hasNonWheeledGear,"count shared control as three physical mounting supports");
            player.setPositionAndUpdate(base.getX()-2,base.getY()+2,base.getZ()-2);craft=VehicleTransfer.assemble(world,scan.result,scan.origin,player);require(craft.hover,"modular gear enters Hover");craft.rotationYaw=90;
            GearTileEntity captured=null;for(TileEntity raw:craft.view.tiles.values())if(raw instanceof GearTileEntity&&((GearTileEntity)raw).isController())captured=(GearTileEntity)raw;
            require(captured!=null&&craft.collisionCache.bypass().isEmpty(),"native gear participates in cached vehicle geometry");captured.target(false);
            for(int tick=0;tick<300&&captured.progress()>0;tick++){ModularGearVehicles.tick(craft);Thread.sleep(3);}require(captured.progress()==0,"captured group retracts");captured.target(true);
            for(int tick=0;tick<300&&!captured.fullyDeployed();tick++){ModularGearVehicles.tick(craft);Thread.sleep(3);}require(captured.fullyDeployed(),"captured group deploys");
            VehicleTransfer.park(world,craft,scan.origin,player);craft=null;
            GearTileEntity found=null;for(BlockPos p:BlockPos.getAllInBox(base.add(-1,-1,-1),base.add(4,1,4)))if(world.getTileEntity(p) instanceof GearTileEntity&&((GearTileEntity)world.getTileEntity(p)).isController())found=(GearTileEntity)world.getTileEntity(p);
            require(found!=null&&found.columns().size()==3&&found.assembly().feet().size()==9,"rotated group restored");require(found.rebuild(),"restored group rebuild");GearTileEntity actual=found.loadedController();require(actual!=null&&actual.getRedstoneChannels().contains(65003),"canonical controller change retains settings");
        }finally{if(craft!=null&&!craft.isDead)VehicleTransfer.park(world,craft,new BlockPos(craft.posX,craft.posY,craft.posZ),player);clear(world,base);}
    }
    static void fixture(WorldServer world){for(int i=0;i<3;i++){BlockPos base=new BlockPos(390+i*10,32,120);clear(world,base);build(world,base,i==1,i==2,EnumFacing.NORTH,i==2?1:3,3,2);}}
    static void pose(WorldServer world,boolean stowed){for(int i=0;i<3;i++){TileEntity raw=world.getTileEntity(new BlockPos(390+i*10,32,120));if(raw instanceof GearTileEntity){GearTileEntity root=((GearTileEntity)raw).loadedController();if(root!=null){root.target(!stowed);root.captureProgress(stowed?0:1);root.sync();}}}}
    private static void clear(WorldServer world,BlockPos base){for(BlockPos p:BlockPos.getAllInBox(base.add(-5,-6,-5),base.add(7,4,6)))if(!world.isAirBlock(p))world.setBlockToAir(p);}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("modular gear: "+message);}
    private static void recipes(WorldServer world){
        for(String id:new String[]{"landing_plate","landing_ski","gear_extender","gear_swing_arm","turntable_controller","turntable_surface","programmable_diagonal_half_console"}){
            net.minecraft.item.crafting.IRecipe recipe=net.minecraft.item.crafting.CraftingManager.REGISTRY.getObject(new net.minecraft.util.ResourceLocation("vandorlabs:"+id));require(recipe instanceof net.minecraftforge.common.crafting.IShapedRecipe,"registered shaped recipe "+id);
            net.minecraftforge.common.crafting.IShapedRecipe shape=(net.minecraftforge.common.crafting.IShapedRecipe)recipe;net.minecraft.inventory.InventoryCrafting grid=new net.minecraft.inventory.InventoryCrafting(new net.minecraft.inventory.Container(){public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player){return true;}},3,3);
            for(int y=0;y<shape.getRecipeHeight();y++)for(int x=0;x<shape.getRecipeWidth();x++){ItemStack[] choices=recipe.getIngredients().get(x+y*shape.getRecipeWidth()).getMatchingStacks();if(choices.length>0)grid.setInventorySlotContents(x+y*3,choices[0].copy());}
            ItemStack actual=net.minecraft.item.crafting.CraftingManager.findMatchingResult(grid,world),expected=recipe.getRecipeOutput();require(ItemStack.areItemsEqual(actual,expected)&&actual.getCount()==expected.getCount(),"recipe resolves to its intended output without a collision: "+id);
        }
    }
}
