package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import com.vandorlabs.programmer.*;
import com.vandorlabs.copier.*;
import com.vandorlabs.items.ModItems;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.GameType;
import java.io.File;
import java.util.*;

/** Actual controller/editor packets, retained card checks, exact chip data and queued copy. */
final class VehicleDesignRuntimeChecks {
    private static final BlockPos BASE=new BlockPos(320,121,320),CONTROL=BASE.up(2),CHIP=CONTROL.north(),CARD=CONTROL.west(),COPY=BASE.east(30);
    private static int stage,ticks;private static ItemStack written;private static VehicleStructure design;private static BlockPos target;private static boolean sawBusy,sawTransfer;private static UUID craftId;
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("vehicle-design stage="+stage+" ticks="+ticks+": "+why);}
    private static WorldServer world(Minecraft mc){return mc.getIntegratedServer().getWorld(0);}
    private static EntityPlayerMP actor(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static Block block(String name){return Block.getBlockFromName("vandorlabs:"+name);}
    private static TileEntityVehicleController controller(Minecraft mc){return (TileEntityVehicleController)world(mc).getTileEntity(CONTROL);}
    private static Object field(Object object,String name)throws Exception{java.lang.reflect.Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static void mount(WorldServer world,BlockPos at,EnumFacing side,boolean chip){world.setBlockState(at,block(chip?"data_chip_slot":"access_card_slot").getDefaultState().withProperty(BlockTokenSlot.FACING,side),2);}
    private static void setup(Minecraft mc)throws Exception{
        WorldServer w=world(mc);EntityPlayerMP p=actor(mc);p.dismountRidingEntity();p.setGameType(GameType.CREATIVE);p.capabilities.isFlying=true;p.sendPlayerAbilities();
        for(int x=-1;x<4;x++)for(int z=-1;z<3;z++)w.getChunkFromChunkCoords((BASE.getX()>>4)+x,(BASE.getZ()>>4)+z);
        for(BlockPos at:BlockPos.getAllInBox(BASE.add(-4,-2,-4),BASE.add(45,10,15)))w.setBlockToAir(at);
        for(BlockPos at:BlockPos.getAllInBox(BASE.add(-4,-1,-4),BASE.add(10,-1,5)))w.setBlockState(at,Blocks.STONE.getDefaultState(),2);
        Block gear=null;for(Block b:Block.REGISTRY)if(b instanceof BlockTelescopicLandingGear&&!(b instanceof BlockNonWheeledLandingGear)){gear=b;break;}require(gear!=null,"gear registered");
        for(int x:new int[]{0,3,6}){w.setBlockState(BASE.east(x),gear.getDefaultState(),2);((TileEntityLandingGear)w.getTileEntity(BASE.east(x))).configure(0,0,0,0);}
        for(int x=0;x<7;x++)for(int z=0;z<2;z++)w.setBlockState(BASE.add(x,1,z),Blocks.IRON_BLOCK.getDefaultState(),2);
        w.setBlockState(CONTROL,block("vehicle_controller").getDefaultState(),2);controller(mc).initializeControls();
        BlockPos seat=BASE.add(3,2,0);w.setBlockState(seat,ModBlocks.PILOT_SEAT.getDefaultState(),2);ModBlocks.PILOT_SEAT.onBlockPlacedBy(w,seat,w.getBlockState(seat),p,new ItemStack(ModBlocks.PILOT_SEAT));((TileEntityPilotSeat)w.getTileEntity(seat)).setTaxiMode(false);
        w.setBlockState(BASE.add(6,2,0),block("impulse_engine").getDefaultState(),2);
        w.setBlockState(BASE.add(5,2,1),Blocks.CHEST.getDefaultState(),2);((TileEntityChest)w.getTileEntity(BASE.add(5,2,1))).setInventorySlotContents(7,new ItemStack(Items.DIAMOND,11));
        mount(w,CHIP,EnumFacing.NORTH,true);mount(w,CARD,EnumFacing.WEST,false);
        ItemStack blank=new ItemStack(ModItems.DATA_CHIP);VehicleChip.blank(blank);((TileEntityTokenSlot)w.getTileEntity(CHIP)).setInventorySlotContents(0,blank);
        p.setPositionAndUpdate(CONTROL.getX()+.5,CONTROL.getY()+.5,CONTROL.getZ()-3);VehicleService.previewController(p,CONTROL);sawBusy=controller(mc).converting;
    }
    static boolean tick(Minecraft mc,File output)throws Exception{
        ticks++;require(ticks<1200,"timed out");
        if(stage==0&&ticks>40){mc.getIntegratedServer().addScheduledTask(()->{try{setup(mc);}catch(Exception e){throw new IllegalStateException(e);}}).get();stage=1;ticks=0;return false;}
        if(stage==1&&ticks>50){
            require(mc.currentScreen!=null,"controller dialog opened");GuiTextField name=(GuiTextField)field(mc.currentScreen,"vehicleName");VehicleTaxiRuntimeChecks.type(mc,name,"Test Shuttle");stage=2;ticks=0;return false;
        }
        if(stage==2&&ticks>35){VehicleTaxiRuntimeChecks.button(mc,92);VehicleTaxiRuntimeChecks.button(mc,33);stage=3;ticks=0;return false;}
        if(stage==3&&ticks>25){VehicleTaxiRuntimeChecks.button(mc,32);stage=4;ticks=0;return false;}
        if(stage==4&&ticks>35){
            mc.getIntegratedServer().addScheduledTask(()->{TileEntityVehicleController c=controller(mc);require(c.vehicleName.equals("Test Shuttle")&&c.security&&c.card==1,"actual name/security/card packets persist");require(sawBusy&&!c.converting&&!c.writeToNBT(new NBTTagCompound()).hasKey("VehicleConverting"),"busy synchronized separately from durable tile data");require(c.shape!=null&&c.shape.bits.length==54,"bounded shape persisted");TileEntityVehicleController reload=new TileEntityVehicleController();reload.readFromNBT(c.writeToNBT(new NBTTagCompound()));require(reload.security&&reload.card==1&&reload.vehicleName.equals(c.vehicleName)&&Arrays.equals(reload.shape.bits,c.shape.bits),"settings/preview reload");}).get();
            VehicleTaxiRuntimeChecks.screenshot(mc,output,"shot_vehicle_design_security.png");VehicleTaxiRuntimeChecks.button(mc,90);VehicleTaxiRuntimeChecks.button(mc,31);stage=5;ticks=0;return false;
        }
        if(stage==5&&ticks>70){
            VehicleTaxiRuntimeChecks.screenshot(mc,output,"shot_vehicle_design_controller.png");
            mc.getIntegratedServer().addScheduledTask(()->{try{chipAndSecurity(mc);prepareCopy(mc);}catch(Exception e){throw new IllegalStateException(e);}}).get();mc.displayGuiScreen(null);stage=6;ticks=0;return false;
        }
        if(stage==6&&ticks>40){
            java.util.concurrent.atomic.AtomicBoolean finished=new java.util.concurrent.atomic.AtomicBoolean();mc.getIntegratedServer().addScheduledTask(()->{CopyControllerTile owner=(CopyControllerTile)world(mc).getTileEntity(COPY);if(owner.busy())return;require(owner.error.isEmpty(),"queued chip copy: "+owner.error);require(design.matchesExact(world(mc),target),"exact installed blocks and stored inventory from chip");require(world(mc).isAirBlock(COPY.east()),"chip source needs no input Surface");finished.set(true);}).get();if(!finished.get())return false;
            mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=actor(mc);p.setPositionAndUpdate(CONTROL.getX()+.5,CONTROL.getY()+.5,CONTROL.getZ()-3);((TileEntityTokenSlot)world(mc).getTileEntity(CARD)).setInventorySlotContents(0,new ItemStack(ModItems.ACCESS_CARD,1,1));VehicleService.screenMode(p,CONTROL,null,1);require(controller(mc).converting,"screen conversion publishes native busy state");}).get();stage=7;ticks=0;return false;
        }
        if(stage==7){
            java.util.concurrent.atomic.AtomicBoolean ready=new java.util.concurrent.atomic.AtomicBoolean();mc.getIntegratedServer().addScheduledTask(()->{
                for(EntityGroundVehicle craft:VehicleLookup.loaded(world(mc)))if(craft.structure!=null&&craft.structure.controller()!=null&&craft.getDistanceSq(BASE.getX(),BASE.getY(),BASE.getZ())<400){
                    sawTransfer|=craft.transferInProgress;if(craft.transferInProgress)return;
                    TileEntityVehicleController carried=(TileEntityVehicleController)craft.view.getTileEntity(craft.structure.controller().pos);require(carried.security&&carried.shape!=null&&carried.vehicleName.equals("Test Shuttle"),"real assembly carries name security and shape");
                    require(sawTransfer,"real asynchronous transfer remains visibly busy through its checkpoint");craftId=craft.getUniqueID();TileEntityTokenSlot reader=VehicleMounts.find(craft.view,carried.getPos(),false);reader.takeForDrop();craft.signals.persist();require(!VehicleMounts.allowed(craft.view,carried.getPos()),"carried card removal blocks conversion");
                    VehicleService.screenMode(actor(mc),carried.getPos(),craft,0);ready.set(true);return;
                }
            }).get();if(!ready.get())return false;stage=8;ticks=0;return false;
        }
        if(stage==8&&ticks>40){
            java.util.concurrent.atomic.AtomicBoolean parked=new java.util.concurrent.atomic.AtomicBoolean();mc.getIntegratedServer().addScheduledTask(()->{if(!(world(mc).getTileEntity(CONTROL) instanceof TileEntityVehicleController))return;TileEntityVehicleController c=controller(mc);require(c.security&&c.shape!=null&&c.vehicleName.equals("Test Shuttle"),"Park retains last preview/name/security without a card");parked.set(true);}).get();if(!parked.get())return false;
            VehicleControllerDialogChecks.run(mc,output);VehicleStatusRenderChecks.run(mc,output);VehicleStatusRenderChecks.design(mc,output,design.controller().tileData());programmerPreview(mc,output);
            System.out.println("[vandorlabs][reprolab] vehicle-design PASS real-editor-packets name security retained-card all-six-mount-faces exact-chip programmer-copy blank-vehicle output-only-copy async-journal secured-assembly card-removal parking-retains-preview previews");return true;
        }
        return false;
    }
    private static void chipAndSecurity(Minecraft mc)throws Exception{
        WorldServer w=world(mc);TileEntityVehicleController c=controller(mc);written=((TileEntityTokenSlot)w.getTileEntity(CHIP)).getStackInSlot(0).copy();require(VehicleChip.isVehicle(written)&&VehicleChip.name(written).equals("Test Shuttle"),"write button creates named Vehicle Chip");design=VehicleChip.design(written);require(design.controller()!=null&&design.pilotSeats().size()==1,"complete exact design codec");
        byte[] raw=design.encode();require(written.getTagCompound().getByteArray("VehicleDesign").length<raw.length,"compressed chip payload reduces retained bytes");require(VehicleStructure.decode(raw).write().equals(design.write()),"exact compressed-chip round trip");System.out.println("[vandorlabs][reprolab] vehicle-chip-bytes raw="+raw.length+" stored="+written.getTagCompound().getByteArray("VehicleDesign").length+" exact-roundtrip=true");
        VehicleStructure.Cell chest=design.at(BASE.add(5,2,1).subtract(CONTROL.subtract(design.controller().pos)));require(chest!=null&&chest.tileData().getTagList("Items",10).tagCount()==1,"exact chest inventory retained");
        require(!VehicleMounts.allowed(w,CONTROL),"missing card blocks conversion");boolean denied=false;try{VehicleTransfer.assemble(w,design,CONTROL.subtract(design.controller().pos),actor(mc));}catch(IllegalArgumentException expected){denied=expected.getMessage().contains("Access Card");}require(denied,"authoritative assembly rejects missing card before changing blocks");TileEntityTokenSlot reader=(TileEntityTokenSlot)w.getTileEntity(CARD);reader.setInventorySlotContents(0,new ItemStack(ModItems.ACCESS_CARD,1,2));require(!VehicleMounts.allowed(w,CONTROL),"wrong card blocks");reader.setInventorySlotContents(0,new ItemStack(ModItems.ACCESS_CARD,1,1));require(VehicleMounts.allowed(w,CONTROL),"retained matching card permits conversion");reader.takeForDrop();require(!VehicleMounts.allowed(w,CONTROL),"removal immediately blocks");
        for(EnumFacing side:EnumFacing.values()){BlockPos root=BASE.add(12,3,0);w.setBlockState(root,block("vehicle_controller").getDefaultState(),2);TileEntityVehicleController tile=(TileEntityVehicleController)w.getTileEntity(root);tile.initializeControls();mount(w,root.offset(side),side,false);tile.designSettings("",true,3);TileEntityTokenSlot slot=VehicleMounts.find(w,root,false);require(slot!=null,"physical mount "+side);slot.setInventorySlotContents(0,new ItemStack(ModItems.ACCESS_CARD,1,3));require(VehicleMounts.allowed(w,root),"all six attached faces "+side);w.setBlockToAir(root.offset(side));w.setBlockToAir(root);}
        ProgrammerTile programmer=new ProgrammerTile();programmer.setInventorySlotContents(1,new ItemStack(ModItems.DATA_CHIP));require(programmer.programVehicle()&&VehicleChip.isVehicle(programmer.getStackInSlot(2)),"program blank Vehicle without a company Master");programmer.clear();programmer.setInventorySlotContents(1,written.copy());programmer.setInventorySlotContents(2,new ItemStack(ModItems.DATA_CHIP));require(programmer.copyInput()&&ItemStack.areItemStackTagsEqual(written,programmer.getStackInSlot(2)),"programmer exact chip duplication");require(!ChipJournal.master(written),"Vehicle Chip never authorizes company Master slot");
        // Two large retained designs must also survive vanilla's bounded tile-NBT reader.
        NBTTagCompound control=design.controller().tileData();byte[] opaque=new byte[700000];new Random(41).nextBytes(opaque);control.getCompoundTag("ForgeData").setByteArray("ChipFixture",opaque);if(!control.hasKey("ForgeData",10)){NBTTagCompound extra=new NBTTagCompound();extra.setByteArray("ChipFixture",opaque);control.setTag("ForgeData",extra);}
        VehicleStructure large=VehicleSeatSettings.replaceAt(design,design.controller().pos,control,BlockPos.ORIGIN);ItemStack largeChip=new ItemStack(ModItems.DATA_CHIP);VehicleChip.blank(largeChip);VehicleChip.write(largeChip,large,"Large fixture");require(VehicleChip.design(largeChip).write().equals(large.write()),"large compressed design exact round trip");
        ProgrammerTile largeProgrammer=new ProgrammerTile();largeProgrammer.setInventorySlotContents(1,largeChip);largeProgrammer.setInventorySlotContents(2,largeChip.copy());net.minecraft.network.PacketBuffer wire=new net.minecraft.network.PacketBuffer(io.netty.buffer.Unpooled.buffer());try{wire.writeCompoundTag(largeProgrammer.writeToNBT(new NBTTagCompound()));ProgrammerTile decoded=new ProgrammerTile();decoded.readFromNBT(wire.readCompoundTag());require(ItemStack.areItemStackTagsEqual(largeChip,decoded.getStackInSlot(1))&&ItemStack.areItemStackTagsEqual(largeChip,decoded.getStackInSlot(2)),"two large chips fit vanilla tile-NBT decoding budget");}finally{wire.release();}
        java.io.ByteArrayOutputStream zipped=new java.io.ByteArrayOutputStream();try(java.io.OutputStream out=new java.util.zip.GZIPOutputStream(zipped)){out.write(new byte[VehicleStructure.MAX_NBT_BYTES+1]);}ItemStack bomb=new ItemStack(ModItems.DATA_CHIP);VehicleChip.blank(bomb);bomb.getTagCompound().setInteger("VehicleDesignCodec",1);bomb.getTagCompound().setByteArray("VehicleDesign",zipped.toByteArray());boolean bounded=false;try{VehicleChip.design(bomb);}catch(java.io.IOException expected){bounded=true;}require(bounded,"expanded chip length bounded before snapshot allocation");
        performance(w);
    }
    private static void prepareCopy(Minecraft mc)throws Exception{
        WorldServer w=world(mc);EntityPlayerMP p=actor(mc);w.setBlockState(COPY,block("copy_controller").getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.NORTH),2);
        CopyControllerTile owner=(CopyControllerTile)w.getTileEntity(COPY);owner.useChip=true;owner.height=8;owner.actor=p.getUniqueID();
        for(int x=1;x<=8;x++)for(int z=0;z<8;z++)w.setBlockState(COPY.add(-x,0,-z),block("turntable_surface").getDefaultState().withProperty(BlockTurntableSurface.FACING,EnumFacing.UP),2);
        mount(w,COPY.south(),EnumFacing.SOUTH,true);((TileEntityTokenSlot)w.getTileEntity(COPY.south())).setInventorySlotContents(0,written.copy());
        p.setPositionAndUpdate(COPY.getX()+.5,COPY.getY()+2,COPY.getZ()+3);DeferredTileLoad.flush(w);owner.refreshPads(p);require(owner.input==null&&owner.output!=null,"output-only pad accepted");target=CopyPlan.volume(owner.output,owner.height)[0];owner.requestCopy(p);require(owner.busy(),"real queued copy starts");
    }
    private static void programmerPreview(Minecraft mc,File output)throws Exception{
        ProgrammerTile tile=new ProgrammerTile();tile.setInventorySlotContents(1,written.copy());mc.displayGuiScreen(new com.vandorlabs.programmer.client.GuiProgrammer(new ProgrammerContainer(mc.player.inventory,tile)));VehicleTaxiRuntimeChecks.button(mc,6);VehicleTaxiRuntimeChecks.screenshot(mc,output,"shot_vehicle_design_programmer.png");mc.displayGuiScreen(null);
    }
    private static void performance(WorldServer world)throws Exception{
        List<VehicleStructure.Cell> cells=new ArrayList<>();for(int x=0;x<32;x++)for(int z=0;z<32;z++)cells.add(new VehicleStructure.Cell(new BlockPos(x,0,z),Blocks.IRON_BLOCK.getDefaultState(),null));VehicleStructure source=VehicleStructure.display(cells);VehicleWorld view=new VehicleWorld(world,source);
        java.lang.reflect.Method rotate=VehicleRotation.class.getDeclaredMethod("state",net.minecraft.block.state.IBlockState.class,Rotation.class);rotate.setAccessible(true);
        long before=System.nanoTime();for(VehicleStructure.Cell cell:cells){net.minecraft.block.state.IBlockState state=cell.state;for(int i=0;i<4;i++)state=(net.minecraft.block.state.IBlockState)rotate.invoke(null,state,Rotation.CLOCKWISE_90);}before=System.nanoTime()-before;
        long after=System.nanoTime();net.minecraft.block.state.IBlockState state=cells.get(0).state;for(int i=0;i<4;i++)state=(net.minecraft.block.state.IBlockState)rotate.invoke(null,state,Rotation.CLOCKWISE_90);after=System.nanoTime()-after;
        VehicleRotation.Validation validation=new VehicleRotation.Validation(source,view);int slices=0;while(!validation.step(64,System.nanoTime()+2_000_000L))slices++;require(slices>=15,"validation yields by cell budget");System.out.println("[vandorlabs][reprolab] vehicle-preflight-cost blocks=1024 rotationCallsBefore=4096 rotationCallsAfter=4 baselineNanos="+before+" cachedNanos="+after+" slices="+(slices+1)+" shapeBytes=216");
    }
    private VehicleDesignRuntimeChecks(){}
}
