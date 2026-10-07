package com.vandorlabs.client;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.blocks.*;
import com.vandorlabs.redstone.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import java.io.File;
import java.lang.reflect.*;
import javax.imageio.ImageIO;

/** Opt-in real dialog -> network -> server -> reopened dialog regression. */
final class ChannelListGuiChecks {
    private static final int[] IDS={2,5,0,0,9,1,3,8,2,2,2,2,2,2,2,2};
    private static final String[] METHODS={"submit","send","send","sendUpdate","send","submit","sendUpdate","submit","submit","submit","submit","submit","submit","submit","submit","submit"};
    private static final ChannelList EXPECTED=ChannelList.of(14861,14862,14863);
    private static final BlockPos POS=new BlockPos(8,80,8);
    private static int index,stage,ticks;
    private static com.google.common.util.concurrent.ListenableFuture<?> pending;
    static void tick(Minecraft mc,File output) {
        if(stage==9)return;
        if(stage==0 && index==0 && Boolean.getBoolean("vandorlabs.controlIconsOnly")){checkControlModels(mc);index=9;}
        if(stage>=6){captureControls(mc,output);return;}
        try {
            if(++ticks>400)throw new IllegalStateException("channel GUI timeout index="+index+" stage="+stage);
            if(pending!=null){if(!pending.isDone())return;pending.get();pending=null;}
            if(stage==0){
                pending=mc.getIntegratedServer().addScheduledTask(()->{
                    EntityPlayerMP owner=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
                    owner.closeScreen();owner.capabilities.isCreativeMode=true;owner.capabilities.isFlying=true;owner.sendPlayerAbilities();
                    owner.setPositionAndUpdate(POS.getX()+.5,POS.getY(),POS.getZ()-2);
                    for(BlockPos p:BlockPos.getAllInBox(POS.add(-2,-2,-2),POS.add(2,3,2)))owner.world.setBlockToAir(p);
                    owner.world.setBlockState(POS.down(),Blocks.STONE.getDefaultState(),3);
                    Block block=index==14?Block.getBlockFromName("vandorlabs:industrial_power_lever"):index==15?Block.getBlockFromName("vandorlabs:rocker_switch"):index==13?Block.getBlockFromName("vandorlabs:small_power_lever"):index>=9 && index<13?Block.REGISTRY.getObject(new ResourceLocation("vandorlabs",SignalControlRuntimeChecks.IDS[index-9])):index==0?Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:rocker_switch")):
                        index==1?ModBlocks.PROGRAMMABLE_LIGHT:index==2?ModBlocks.PROGRAMMABLE_TRIGGER_BLOCK:
                        index==3?ModBlocks.ANIMATED_SCREEN_SELECTOR:index==4?ModBlocks.PROGRAMMABLE_TRAPDOOR:
                        index==5?ModBlocks.PROGRAMMABLE_RAMP:index==8?Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:ion_drive")):Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:"+(index==6?"programmable_door":"landing_gear")));
                    owner.world.setBlockState(POS,block instanceof BlockVandorSwitch?block.getDefaultState().withProperty(BlockVandorSwitch.FACING,net.minecraft.util.EnumFacing.UP):block instanceof BlockIndustrialLever?block.getDefaultState().withProperty(BlockIndustrialLever.FLOOR,true):block.getDefaultState(),3);
                    if(block instanceof BlockVandorDoor)owner.world.setBlockState(POS.up(),block.getDefaultState().withProperty(BlockVandorDoor.HALF,BlockDoor.EnumDoorHalf.UPPER),3);
                    if(!(owner.world.getTileEntity(POS) instanceof RedstoneChannelMember))throw new IllegalStateException("missing GUI fixture "+index);
                });stage=1;ticks=0;return;
            }
            if(stage==1 && ticks>20){if(index==0)checkControlModels(mc);open(mc);stage=2;ticks=0;return;}
            if(stage==2 && ticks>15 && mc.currentScreen!=null){
                GuiTextField field=field(mc);
                field.setText("14861,");require(ChannelFields.parse(field)==null,"incomplete list accepted");
                field.setText("14863, 14861, 14862, 14861");require(EXPECTED.equals(ChannelFields.parse(field)),"GUI rejects comma list");
                if(index==1)for(int i=0;i<3;i++)((GuiProgrammableLight)mc.currentScreen).actionPerformed(new net.minecraft.client.gui.GuiButton(102,0,0,"Signal brightness"));
                if(index==2){((GuiProgrammableTrigger)mc.currentScreen).actionPerformed(new net.minecraft.client.gui.GuiButton(101,0,0,"Signal bands"));((GuiProgrammableTrigger)mc.currentScreen).actionPerformed(new net.minecraft.client.gui.GuiButton(102,0,0,"Exact level"));}
                if(index==8){((GuiRedstoneChannel)mc.currentScreen).actionPerformed(new net.minecraft.client.gui.GuiButton(5,0,0,"Signal brightness"));((GuiRedstoneChannel)mc.currentScreen).actionPerformed(new net.minecraft.client.gui.GuiButton(6,0,0,"Threshold"));}
                if(index>=9){for(int id:new int[]{9,9,9,10,10,10,11})((GuiRedstoneChannel)mc.currentScreen).actionPerformed(new net.minecraft.client.gui.GuiButton(id,0,0,"Mount"));}
                if(index>=9 && index<13){((GuiRedstoneChannel)mc.currentScreen).actionPerformed(new net.minecraft.client.gui.GuiButton(7,0,0,"Low"));((GuiRedstoneChannel)mc.currentScreen).actionPerformed(new net.minecraft.client.gui.GuiButton(8,0,0,"High"));}
                if(index>=9 && index<13 && index!=10)((GuiRedstoneChannel)mc.currentScreen).actionPerformed(new net.minecraft.client.gui.GuiButton(13,0,0,"Type"));
                if(index==13 || index==14)((GuiRedstoneChannel)mc.currentScreen).actionPerformed(new net.minecraft.client.gui.GuiButton(12,0,0,"Size"));
                Method send=mc.currentScreen.getClass().getDeclaredMethod(METHODS[index]);send.setAccessible(true);send.invoke(mc.currentScreen);
                stage=3;ticks=0;return;
            }
            if(stage==3 && ticks>20){
                pending=mc.getIntegratedServer().addScheduledTask(()->{
                    RedstoneChannelMember member=(RedstoneChannelMember)mc.getIntegratedServer().getEntityWorld().getTileEntity(POS);
                    if(index>=9 && index<13){com.vandorlabs.tiles.TileEntitySignalControl control=(com.vandorlabs.tiles.TileEntitySignalControl)member;require(control.getLowLimit()==6 && control.getHighLimit()==8,"control limits packet");if(index!=10)require(control.getControlType()==(index==9?1:index==11?2:0),"type packet");require(control.getBaseHeight()==3 && control.getBaseTilt()==3 && control.getTiltDirection()==1,"base height/tilt packet");}
                    if(index>=13){com.vandorlabs.tiles.TileEntityRedstoneChannel control=(com.vandorlabs.tiles.TileEntityRedstoneChannel)member;require(control.getBaseHeight()==3 && control.getBaseTilt()==3 && control.getTiltDirection()==1,"binary mount packet");}
                    if(index==14)require(((com.vandorlabs.tiles.TileEntityRedstoneChannel)member).getPowerLeverSize()==0,"Industrial size packet");
                    if(index==13)require(((com.vandorlabs.tiles.TileEntityRedstoneChannel)member).getPowerLeverSize()==1,"size packet did not apply");
                    require(member!=null && EXPECTED.equals(member.getRedstoneChannels()),"server lost submitted list index="+index);
                    if(index==1)require(((com.vandorlabs.tiles.TileEntityProgrammableLight)member).isSignalBrightness(),"light brightness packet");
                    if(index==2)require(((com.vandorlabs.tiles.TileEntityProgrammableTrigger)member).isLevelStates() && ((com.vandorlabs.tiles.TileEntityProgrammableTrigger)member).getExactLevel()==0,"trigger levels packet");
                    if(index==8)require(((com.vandorlabs.tiles.TileEntityRedstoneLight)member).isSignalBrightness() && ((com.vandorlabs.tiles.TileEntityRedstoneLight)member).getParticleThreshold()==9,"propulsion levels packet");
                });mc.player.closeScreen();stage=4;ticks=0;return;
            }
            if(stage==4 && ticks>10){open(mc);stage=5;ticks=0;return;}
            if(stage==5 && ticks>20 && mc.currentScreen!=null){
                if(index==1 || index==8){Field signal=mc.currentScreen.getClass().getDeclaredField("signalBrightness");signal.setAccessible(true);require(signal.getBoolean(mc.currentScreen),"signal brightness lost on reopen");}
                if(index==2){Field exact=mc.currentScreen.getClass().getDeclaredField("exact");exact.setAccessible(true);require(exact.getInt(mc.currentScreen)==0,"exact signal lost on reopen");}
                if(index>=9 && index<13){for(String key:new String[]{"lowLimit","highLimit"}){Field limit=mc.currentScreen.getClass().getDeclaredField(key);limit.setAccessible(true);require(limit.getInt(mc.currentScreen)==(key.equals("lowLimit")?6:8),"control limits lost on reopen");}}
                if(index>=9)for(String key:new String[]{"baseHeight","baseTilt","tiltDirection"}){Field f=mc.currentScreen.getClass().getDeclaredField(key);f.setAccessible(true);require(f.getInt(mc.currentScreen)==(key.equals("baseHeight")?3:key.equals("baseTilt")?3:1),"mount settings lost on reopen");}
                if(index>=9 && index<13 && index!=10){Field type=mc.currentScreen.getClass().getDeclaredField("controlType");type.setAccessible(true);require(type.getInt(mc.currentScreen)==(index==9?1:index==11?2:0),"type lost on reopen");}
                if(index==13){Field size=mc.currentScreen.getClass().getDeclaredField("powerLeverSize");size.setAccessible(true);require(size.getInt(mc.currentScreen)==1,"size lost on reopen");}
                require(EXPECTED.toString().equals(field(mc).getText()),"reopened GUI lost list index="+index);
                ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_channels_"+index+".png"));
                System.out.println("[vandorlabs][reprolab] channel-gui PASS "+mc.currentScreen.getClass().getSimpleName());
                mc.player.closeScreen();index++;stage=0;ticks=0;
                if(index==IDS.length){stage=6;System.out.println("[vandorlabs][reprolab] channel-gui-runtime PASS");}
            }
        }catch(Exception e){throw new IllegalStateException("channel dialog regression index="+index+" stage="+stage,e);}
    }
    private static void checkControlModels(Minecraft mc){
        for(String id:SignalControlRuntimeChecks.IDS){
            Block block=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs",id));
            for(net.minecraft.util.EnumFacing facing:net.minecraft.util.EnumFacing.values())for(int rotation=0;rotation<4;rotation++){
                java.util.Set<String> signatures=new java.util.HashSet<>();
                for(int level=0;level<4;level++){
                    net.minecraft.block.state.IBlockState state=block.getDefaultState().withProperty(BlockVandorSwitch.FACING,facing).withProperty(BlockVandorSwitch.ROTATION,rotation).withProperty(BlockSignalControl.LEVEL,level);
                    net.minecraft.client.renderer.block.model.IBakedModel model=mc.getBlockRendererDispatcher().getBlockModelShapes().getModelForState(state);
                    require(model!=mc.getBlockRendererDispatcher().getBlockModelShapes().getModelManager().getMissingModel(),"missing control model "+id);
                    StringBuilder signature=new StringBuilder();int count=0;
                    for(net.minecraft.util.EnumFacing side: new net.minecraft.util.EnumFacing[]{null,net.minecraft.util.EnumFacing.UP,net.minecraft.util.EnumFacing.DOWN,net.minecraft.util.EnumFacing.NORTH,net.minecraft.util.EnumFacing.SOUTH,net.minecraft.util.EnumFacing.EAST,net.minecraft.util.EnumFacing.WEST})
                        for(net.minecraft.client.renderer.block.model.BakedQuad quad:model.getQuads(state,side,0)){count++;signature.append(quad.getSprite().getIconName()).append(java.util.Arrays.hashCode(quad.getVertexData()));}
                    if(((BlockSignalControl)block).controlKind().equals("wall_slider"))WallSliderModelChecks.check(model,state,facing,level);
                    require(count>0,"empty control model "+id);signatures.add(signature.toString());
                }
                require(signatures.size()==4,"detent artwork repeated "+id+" "+facing+" "+rotation);
            }
        }
        SignalControlMountChecks.checkModels(mc);
        TwinPowerLeverChecks.checkModels(mc);
        RockerSwitchChecks.checkModels(mc);
        MountedBinaryControlChecks.checkModels(mc);
        System.out.println("[vandorlabs][reprolab] signal-control-models PASS every detent, mount and rotation; slider attachment and grip/panel alignment");
    }
    private static int visualIndex,visualStep,visualMount,visualRotation,iconPage,mountPreview,powerPreview,binaryMountPreview;
    private static final net.minecraft.util.EnumFacing[] VISUAL_MOUNTS={net.minecraft.util.EnumFacing.NORTH,net.minecraft.util.EnumFacing.SOUTH,net.minecraft.util.EnumFacing.EAST,net.minecraft.util.EnumFacing.WEST,net.minecraft.util.EnumFacing.UP,net.minecraft.util.EnumFacing.DOWN};
    private static void captureControls(Minecraft mc,File output){
        try{
            if(pending!=null){if(!pending.isDone())return;pending.get();pending=null;}
            if(stage==6){
                mc.player.closeScreen();mc.gameSettings.hideGUI=false;mc.gameSettings.clouds=0;mc.player.inventory.currentItem=8;
                for(int slot=0;slot<9;slot++)mc.player.inventory.setInventorySlotContents(slot,net.minecraft.item.ItemStack.EMPTY);
                pending=mc.getIntegratedServer().addScheduledTask(()->{
                    EntityPlayerMP owner=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
                    owner.capabilities.isCreativeMode=true;owner.capabilities.isFlying=true;owner.sendPlayerAbilities();
                    owner.connection.setPlayerLocation(8.5,90,4,0,-30);
                });
                mc.player.rotationYaw=0;mc.player.rotationPitch=-30;stage=7;ticks=0;return;
            }
            if(stage==7 && ++ticks>20){
                capture(mc,output,"controls_hotbar_empty");
                for(int slot=0;slot<SignalControlRuntimeChecks.IDS.length;slot++)mc.player.inventory.setInventorySlotContents(slot,new net.minecraft.item.ItemStack(Block.REGISTRY.getObject(new ResourceLocation("vandorlabs",SignalControlRuntimeChecks.IDS[slot]))));
                for(int i=0;i<2;i++){net.minecraft.item.ItemStack lever=new net.minecraft.item.ItemStack(Block.getBlockFromName("vandorlabs:small_power_lever"));net.minecraft.nbt.NBTTagCompound size=new net.minecraft.nbt.NBTTagCompound();size.setInteger("PowerLeverSize",i);lever.setTagInfo("RedstoneChannelSettings",size);mc.player.inventory.setInventorySlotContents(4+i,lever);}
                mc.player.inventory.setInventorySlotContents(6,new net.minecraft.item.ItemStack(Block.getBlockFromName("vandorlabs:rocker_switch")));
                stage=8;ticks=0;return;
            }
            if(stage==8 && ++ticks>20){capture(mc,output,"controls_hotbar");stage=12;ticks=0;return;}
            if(stage==12){
                mc.player.inventory.setInventorySlotContents(8,net.minecraft.item.ItemStack.EMPTY);
                for(int slot=0;slot<8;slot++){
                    int variant=iconPage*8+slot,kind=variant/64,value=variant%64;
                    net.minecraft.item.ItemStack stack=new net.minecraft.item.ItemStack(Block.getBlockFromName("vandorlabs:"+(kind<3?"thruster_lever":kind==3?"wall_slider":kind<6?"small_power_lever":kind<8?"industrial_power_lever":"rocker_switch")));
                    net.minecraft.nbt.NBTTagCompound tag=new net.minecraft.nbt.NBTTagCompound();tag.setInteger("ControlType",kind<3?kind:0);if(kind>=4 && kind<8)tag.setInteger("PowerLeverSize",kind%2);tag.setInteger("ControlMountVersion",2);tag.setInteger("BaseHeight",value%4);tag.setInteger("BaseTilt",value/4%4);tag.setInteger("TiltDirection",value/16);stack.setTagInfo("RedstoneChannelSettings",tag);mc.player.inventory.setInventorySlotContents(slot,stack);
                }
                stage=13;ticks=0;return;
            }
            if(stage==13 && ++ticks>15){capture(mc,output,"controls_mount_icons_"+iconPage);iconPage++;if(iconPage==72 && Boolean.getBoolean("vandorlabs.controlIconsOnly")){stage=14;System.out.println("[vandorlabs][reprolab] signal-control-icons PASS all 576 configured icons");}else stage=iconPage==72?10:12;ticks=0;return;}
            if(stage==10){
                pending=mc.getIntegratedServer().addScheduledTask(()->{
                    net.minecraft.world.World world=mc.getIntegratedServer().getWorld(0);
                    world.setBlockToAir(POS);
                    for(net.minecraft.util.EnumFacing side:net.minecraft.util.EnumFacing.values())world.setBlockToAir(POS.offset(side));
                    net.minecraft.util.EnumFacing mount=VISUAL_MOUNTS[visualMount];
                    world.setBlockState(POS.offset(mount.getOpposite()),Blocks.STONE.getDefaultState(),3);
                    Block block=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs",SignalControlRuntimeChecks.IDS[visualIndex]));
                    EntityPlayerMP owner=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());owner.setSneaking(false);
                    boolean flat=mount.getAxis()==net.minecraft.util.EnumFacing.Axis.Y;
                    net.minecraft.util.EnumFacing look=flat?net.minecraft.util.EnumFacing.getHorizontal((visualRotation+(mount==net.minecraft.util.EnumFacing.DOWN?2:0))&3):mount.getOpposite();
                    double eyeY=POS.getY()+(flat?(mount==net.minecraft.util.EnumFacing.UP?2.5:-1.5):.5);
                    owner.connection.setPlayerLocation(POS.getX()+.5-look.getFrontOffsetX()*2.5,eyeY-owner.getEyeHeight(),POS.getZ()+.5-look.getFrontOffsetZ()*2.5,look.getHorizontalAngle(),flat?(mount==net.minecraft.util.EnumFacing.UP?45:-45):0);
                    net.minecraft.item.ItemStack stack=new net.minecraft.item.ItemStack(block);
                    net.minecraft.block.state.IBlockState placed=block.getStateForPlacement(world,POS,mount,.5F,.5F,.5F,0,owner,net.minecraft.util.EnumHand.MAIN_HAND);
                    require(((net.minecraft.item.ItemBlock)stack.getItem()).placeBlockAt(stack,owner,world,POS,mount,.5F,.5F,.5F,placed),"real item placement failed");
                    require(world.getBlockState(POS).getValue(BlockVandorSwitch.FACING)==mount,"placement does not match clicked support face");
                    com.vandorlabs.tiles.TileEntitySignalControl control=(com.vandorlabs.tiles.TileEntitySignalControl)world.getTileEntity(POS);if(((BlockSignalControl)block).hasAdjustableBase())control.configureMount(visualStep%3,visualStep==3?3:0,0);control.setRedstoneChannels(ChannelList.of(16901));
                    if(flat)require(control.getMountRotation()==visualRotation,"placement rotation does not follow player facing");
                    for(int click=0;click<(visualStep==0?4:visualStep);click++)block.onBlockActivated(world,POS,world.getBlockState(POS),owner,net.minecraft.util.EnumHand.MAIN_HAND,mount,.5F,.5F,.5F);
                    require(control.getStep()==visualStep && RedstoneChannels.level(world,16901)==new int[]{0,5,10,15}[visualStep],"live click detent");
                });stage=11;ticks=0;return;
            }
            if(stage==11 && ++ticks>30){
                net.minecraft.util.EnumFacing mount=VISUAL_MOUNTS[visualMount];
                require(mc.objectMouseOver!=null && POS.equals(mc.objectMouseOver.getBlockPos()),"placed control selection misses mounted geometry");
                String suffix=mount==net.minecraft.util.EnumFacing.NORTH?"":mount==net.minecraft.util.EnumFacing.UP?"_floor":mount==net.minecraft.util.EnumFacing.DOWN?"_ceiling":"_"+mount.getName();
                if(visualRotation>0)suffix+="_r"+visualRotation;
                capture(mc,output,"controls_"+visualIndex+"_"+visualStep+suffix);
                visualStep++;
                if(visualStep==4){
                    visualStep=0;
                    if(visualIndex>0 && mount.getAxis()==net.minecraft.util.EnumFacing.Axis.Y && visualRotation<3)visualRotation++;
                    else{visualRotation=0;visualMount++;}
                    if(visualIndex==0 && visualMount==1)visualMount=4;
                    if(visualMount==VISUAL_MOUNTS.length){visualMount=0;visualIndex++;}
                }
                if(visualIndex==SignalControlRuntimeChecks.IDS.length){stage=14;System.out.println("[vandorlabs][reprolab] signal-control-visuals PASS four icons, actual item placement and 156 mounted poses");}else stage=10;
                ticks=0;
            }
            if(stage==14){
                mc.gameSettings.hideGUI=true;
                pending=mc.getIntegratedServer().addScheduledTask(()->{
                    net.minecraft.world.World world=mc.getIntegratedServer().getWorld(0);
                    for(BlockPos at:BlockPos.getAllInBox(POS.add(-4,-4,-4),POS.add(4,6,4)))world.setBlockToAir(at);
                    for(net.minecraft.entity.item.EntityItem drop:world.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class,new net.minecraft.util.math.AxisAlignedBB(POS).grow(12)))drop.setDead();
                    world.setBlockState(POS.down(),Blocks.STONE.getDefaultState(),3);
                    Block block=Block.getBlockFromName("vandorlabs:"+new String[]{"thruster_lever","airliner_throttle","fighter_throttle"}[mountPreview/8]);
                    world.setBlockState(POS,block.getDefaultState().withProperty(BlockVandorSwitch.FACING,net.minecraft.util.EnumFacing.UP),3);
                    com.vandorlabs.tiles.TileEntitySignalControl tile=(com.vandorlabs.tiles.TileEntitySignalControl)world.getTileEntity(POS);tile.setMountRotation(0);int setting=mountPreview%8;tile.configureMount(setting<4?setting:setting==7?3:0,setting<4?0:setting==7?3:setting-3,0);tile.setStep(0);
                    EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,net.minecraft.item.ItemStack.EMPTY);
                    player.connection.setPlayerLocation(POS.getX()-2,POS.getY()+2.2-player.getEyeHeight(),POS.getZ()-2.6,-38.9F,27F);
                });stage=15;ticks=0;return;
            }
            if(stage==15 && ++ticks>25){
                capture(mc,output,"controls_mount_"+new String[]{"thruster_lever","airliner_throttle","fighter_throttle"}[mountPreview/8]+"_"+new String[]{"standard","raised_2px","raised_4px","raised_6px","tilted_15","tilted_30","tilted_45","raised_6px_tilted_45"}[mountPreview%8]);
                if(++mountPreview<24)stage=14;else{stage=16;System.out.println("[vandorlabs][reprolab] signal-control-mount-gallery PASS shots=24");}ticks=0;return;
            }
            if(stage==16){
                pending=mc.getIntegratedServer().addScheduledTask(()->{
                    net.minecraft.world.World world=mc.getIntegratedServer().getWorld(0);
                    for(BlockPos at:BlockPos.getAllInBox(POS.add(-4,-4,-4),POS.add(4,6,4)))world.setBlockToAir(at);
                    for(net.minecraft.entity.item.EntityItem drop:world.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class,new net.minecraft.util.math.AxisAlignedBB(POS).grow(12)))drop.setDead();
                    Block block=Block.getBlockFromName("vandorlabs:"+new String[]{"small_power_lever","small_power_lever","rocker_switch"}[powerPreview]);
                    for(int i=0;i<2;i++){
                        BlockPos at=POS.east(i);world.setBlockState(at.south(),Blocks.STONEBRICK.getDefaultState(),3);
                        world.setBlockState(at,powerPreview==2?block.getDefaultState().withProperty(BlockVandorSwitch.FACING,net.minecraft.util.EnumFacing.NORTH).withProperty(BlockVandorSwitch.ON,i==0):block.getDefaultState().withProperty(BlockIndustrialLever.FACING,net.minecraft.util.EnumFacing.NORTH).withProperty(BlockIndustrialLever.POWERED,i==0),3);
                    }
                    if(powerPreview<2)for(int i=0;i<2;i++)((com.vandorlabs.tiles.TileEntityRedstoneChannel)world.getTileEntity(POS.east(i))).setPowerLeverSize(powerPreview);
                    EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
                    player.connection.setPlayerLocation(POS.getX()+1,POS.getY()+.58-player.getEyeHeight(),POS.getZ()-2.1,0,8);
                });stage=17;ticks=0;return;
            }
            if(stage==17 && ++ticks>25){
                capture(mc,output,"gallery_close_control_"+new String[]{"small_power_lever","large_power_lever","rocker_switch"}[powerPreview]);
                if(++powerPreview<3)stage=16;else{stage=18;System.out.println("[vandorlabs][reprolab] binary-control-gallery PASS shots=3");}ticks=0;return;
            }
            if(stage==18){
                pending=mc.getIntegratedServer().addScheduledTask(()->{
                    net.minecraft.world.World world=mc.getIntegratedServer().getWorld(0);
                    for(BlockPos at:BlockPos.getAllInBox(POS.add(-4,-4,-4),POS.add(4,6,4)))world.setBlockToAir(at);
                    for(net.minecraft.entity.item.EntityItem drop:world.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class,new net.minecraft.util.math.AxisAlignedBB(POS).grow(12)))drop.setDead();
                    int kind=binaryMountPreview/2;Block block=Block.getBlockFromName("vandorlabs:"+new String[]{"small_power_lever","small_power_lever","industrial_power_lever","industrial_power_lever","rocker_switch","wall_slider"}[kind]);
                    world.setBlockState(POS.down(),Blocks.STONEBRICK.getDefaultState(),3);
                    world.setBlockState(POS,block instanceof BlockIndustrialLever?block.getDefaultState().withProperty(BlockIndustrialLever.FLOOR,true):block.getDefaultState().withProperty(BlockVandorSwitch.FACING,net.minecraft.util.EnumFacing.UP),3);
                    com.vandorlabs.tiles.TileEntityRedstoneChannel tile=(com.vandorlabs.tiles.TileEntityRedstoneChannel)world.getTileEntity(POS);tile.configureMount(3,binaryMountPreview%2==0?0:3,0);if(kind<4)tile.setPowerLeverSize(kind%2);
                    EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());player.connection.setPlayerLocation(POS.getX()+(kind==5?2:-2),POS.getY()+2.2-player.getEyeHeight(),POS.getZ()+(kind==5?2.6:-2.6),kind==5?141.1F:-38.9F,27F);
                });stage=19;ticks=0;return;
            }
            if(stage==19 && ++ticks>25){
                capture(mc,output,"controls_mount_"+new String[]{"small_power_lever","large_power_lever","compact_power_lever","industrial_power_lever","rocker_switch","wall_slider"}[binaryMountPreview/2]+(binaryMountPreview%2==0?"_raised_6px":"_raised_6px_tilted_45"));
                if(++binaryMountPreview<12)stage=18;else{stage=9;System.out.println("[vandorlabs][reprolab] binary-control-mount-gallery PASS shots=12");mc.shutdown();}ticks=0;return;
            }
        }catch(Exception e){throw new IllegalStateException("control visual regression",e);}
    }
    private static void capture(Minecraft mc,File output,String name)throws java.io.IOException{ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_"+name+".png"));}
    private static GuiTextField field(Minecraft mc)throws ReflectiveOperationException {
        Field field=mc.currentScreen.getClass().getDeclaredField(index==7?"channel":"channelField");field.setAccessible(true);return (GuiTextField)field.get(mc.currentScreen);
    }
    private static void open(Minecraft mc){pending=mc.getIntegratedServer().addScheduledTask(()->{
        EntityPlayerMP owner=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
        owner.openGui(VandorLabs.instance,IDS[index],owner.world,POS.getX(),POS.getY(),POS.getZ());
    });}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private ChannelListGuiChecks(){}
}
