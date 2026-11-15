package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockModuleRack;
import com.vandorlabs.container.ContainerModuleRack;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.network.MessageModuleRack;
import com.vandorlabs.network.PacketHandler;
import com.vandorlabs.redstone.*;
import com.vandorlabs.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameType;
import org.lwjgl.input.Keyboard;
import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import javax.imageio.ImageIO;

/** One real Rack dialog journey. Slot inventories remain authoritative and are never a GUI payload. */
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
final class ModuleRackSettingsLiveChecks {
    private static int stage, wait, checks, inserted, previousWindow;
    private static long incarnation;
    private static final int[] LEVELS = {0,4,8,11,15};
    private static BlockPos at() { return ModuleRackLiveChecks.placedRackPosition(); }
    private static EntityPlayerMP actor(Minecraft mc) { return ModuleRackLiveChecks.player(mc); }
    private static TileEntityModuleRack rack(net.minecraft.world.World world) {
        return world.getTileEntity(at()) instanceof TileEntityModuleRack ? (TileEntityModuleRack)world.getTileEntity(at()) : null;
    }
    private static void need(boolean ok, String why) {
        checks++; if (!ok) throw new IllegalStateException("Rack settings stage="+stage+" wait="+wait+": "+why);
    }
    private static void next(int value) { stage=value; wait=0; }
    private static void click(GuiModuleRack gui,int id,int mouse) throws Exception {
        need(gui!=null,"dedicated Rack dialog remains open");
        GuiButton button=gui.control(id); need(button!=null&&button.enabled,"enabled control "+id);
        gui.mouseClicked(button.x+button.width/2,button.y+10,mouse);
    }
    private static void use(Minecraft mc,double u,double v) {
        need(mc.playerController.processRightClickBlock(mc.player,mc.world,at(),EnumFacing.SOUTH,
                new Vec3d(at().getX()+u,at().getY()+v,at().getZ()+1),EnumHand.MAIN_HAND)==EnumActionResult.SUCCESS,
                "real Rack interaction packet");
    }
    private static Object field(HousingTextureList list,String name) throws Exception {
        Field value=HousingTextureList.class.getDeclaredField(name);value.setAccessible(true);return value.get(list);
    }
    private static void held(Minecraft mc,ItemStack item) throws Exception {
        ModuleRackLiveChecks.server(mc,()->{
            EntityPlayerMP player=actor(mc);player.inventory.currentItem=0;player.setHeldItem(EnumHand.MAIN_HAND,item);
            player.setHeldItem(EnumHand.OFF_HAND,ItemStack.EMPTY);player.inventoryContainer.detectAndSendChanges();
        });
    }
    private static void shot(Minecraft mc,File directory,String name) throws Exception {
        ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),
                "png",new File(directory,"module_rack_dialog_"+name+".png"));
    }
    private static void confirmOutput(Minecraft mc,int count,boolean local) throws Exception {
        ModuleRackLiveChecks.server(mc,()->{
            EntityPlayerMP player=actor(mc);TileEntityModuleRack owned=rack(player.world);
            need(owned!=null&&owned.output.level()==LEVELS[count],"proportional installed module count "+count);
            need(RedstoneChannels.level(player.world,at(),73)==LEVELS[count],"standard ranged channel73 output");
            for(EnumFacing side:EnumFacing.values()) {
                net.minecraft.block.state.IBlockState state=player.world.getBlockState(at());
                need(state.getBlock().getWeakPower(state,player.world,at(),side)==(local?LEVELS[count]:0),"local output gate "+side);
                need(state.getBlock().getStrongPower(state,player.world,at(),side)==(local?LEVELS[count]:0),"support conduction gate "+side);
            }
        });
    }
    @SuppressWarnings("unchecked") static boolean tick(Minecraft mc,File directory) throws Exception {
        TileEntityModuleRack current=rack(mc.world);
        if(++wait%80==0)System.out.println("[vandorlabs][reprolab] Rack settings waiting stage="+stage+" age="+wait
                +" mode="+mc.playerController.getCurrentGameType()+" slot="+mc.player.inventory.currentItem
                +" held="+mc.player.getHeldItemMainhand()+" pos="+mc.player.getPositionVector()+" screen="+mc.currentScreen);
        need(wait<240,"bounded stage mode="+mc.playerController.getCurrentGameType()+" held="+mc.player.getHeldItemMainhand()
                +" position="+mc.player.getPositionVector()+" screen="+mc.currentScreen);
        mc.gameSettings.pauseOnLostFocus=false;mc.ingameGUI.getChatGUI().clearChatMessages(true);
        GuiModuleRack gui=mc.currentScreen instanceof GuiModuleRack?(GuiModuleRack)mc.currentScreen:null;
        if(stage==0){
            if(current==null||!mc.player.getHeldItemMainhand().isEmpty()||mc.playerController.getCurrentGameType()!=GameType.SURVIVAL)return false;
            ModuleRackAppearanceChecks.verify(mc);shot(mc,directory,"original_housing");use(mc,.5,.98);next(1);
        }else if(stage==1){
            if(gui==null)return false;
            need(current.getHousingTexture()==-1&&!current.emitsRedstone(),"old Rack defaults remain original artwork/output off");
            previousWindow=gui.inventorySlots.windowId;
            HousingTextureList list=gui.materialPicker();int x=(Integer)field(list,"x"),y=(Integer)field(list,"y");
            need(list.searchClick(x+4,y-HousingTextureList.ROW_HEIGHT+6,0),"standard Housing search focus");
            for(char ch:HousingTextureList.name(0).toCharArray())need(list.searchKey(ch,0),"standard Housing search typing");
            List<Integer> rows=(List<Integer>)field(list,"rows");int row=rows.indexOf(0);need(row>=0,"known general material in Housing picker");
            int count=(Integer)field(list,"count");if(row>=count)list.wheel(x+8,y+6,-120*(row-count+1));
            row-=(Integer)field(list,"scroll");gui.mouseClicked(x+24,y+row*HousingTextureList.ROW_HEIGHT+11,0);
            need(list.selected()==0,"actual Housing row click");next(2);
        }else if(stage==2){
            if(current.getHousingTexture()!=0)return false;
            ModuleRackLiveChecks.server(mc,()->need(rack(actor(mc).world).getHousingTexture()==0,"Housing C2S acknowledged"));
            net.minecraft.block.state.IBlockState state=mc.world.getBlockState(at());
            net.minecraft.block.state.IBlockState extended=state.getBlock().getExtendedState(state,mc.world,at());
            net.minecraft.client.renderer.block.model.IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(state);
            List<net.minecraft.client.renderer.block.model.BakedQuad> quads=model.getQuads(extended,null,0);
            java.util.ArrayList<net.minecraft.client.renderer.block.model.BakedQuad> all=new java.util.ArrayList<>(quads);
            for(EnumFacing side:EnumFacing.values())all.addAll(model.getQuads(extended,side,0));
            need(all.stream().anyMatch(q->q.getSprite().getIconName().equals(ScreenHousingTextures.texture(0))),"actual exterior emitted shell uses selected atlas material");
            need(model.getQuads(extended,null,0)==quads,"steady Housing reuses its prepared quads");
            shot(mc,directory,"housing");click(gui,101,0);next(3);
        }else if(stage==3){click(gui,0,0);next(4);
        }else if(stage==4){if(!current.emitsRedstone())return false;click(gui,0,1);next(5);
        }else if(stage==5){if(current.emitsRedstone())return false;click(gui,0,0);next(6);
        }else if(stage==6){
            if(!current.emitsRedstone()||InlineTransmitterWidget.channelsField(gui)==null)return false;
            net.minecraft.client.gui.GuiTextField channels=InlineTransmitterWidget.channelsField(gui);channels.setFocused(true);channels.setText("73");
            need(InlineTransmitterWidget.keyInput(gui,'\n',Keyboard.KEY_RETURN),"standard transmit channel editor");
            List<GuiButton> controls=InlineTransmitterWidget.controls(gui);need(controls.size()==2,"standard range/local controls");
            GuiButton range=controls.get(0);int x=range.x+4+(int)Math.round((range.width-8)*4.0/6);
            need(InlineTransmitterWidget.mouseInput(gui,x,range.y+10,0),"actual range slider input");range.mouseReleased(x,range.y+10);
            GuiButton local=controls.get(1);need(InlineTransmitterWidget.mouseInput(gui,local.x+20,local.y+10,1),"previous-right local output toggle");next(7);
        }else if(stage==7){
            if(!current.output.getRedstoneChannels().equals(ChannelList.of(73))||current.output.transmissionRange()!=64
                    ||TransmitterSlots.localRedstone(current))return false;
            confirmOutput(mc,0,false);shot(mc,directory,"redstone");click(gui,1,0);
            ItemStack modules=new ItemStack(ModItems.BLANK_MODULE,4);NBTTagCompound secret=new NBTTagCompound();secret.setString("PrivateModuleTest","not an appearance field");modules.setTagCompound(secret);
            held(mc,modules);next(8);
        }else if(stage==8){
            if(!mc.player.getHeldItemMainhand().isItemEqual(new ItemStack(ModItems.BLANK_MODULE))||mc.player.getHeldItemMainhand().getCount()!=4)return false;
            inserted=0;use(mc,7/16.0,13.25/16.0);next(9);
        }else if(stage==9){
            if(current.getOccupiedMask()!=(1<<(inserted+1))-1||mc.player.getHeldItemMainhand().getCount()!=3-inserted)return false;
            confirmOutput(mc,inserted+1,false);
            if(++inserted<4){use(mc,7/16.0,com.vandorlabs.rack.ModuleRackGeometry.slotCenterY(inserted)/16.0);wait=0;return false;}
            incarnation=current.getIncarnation();
            ModuleRackLiveChecks.server(mc,()->{
                EntityPlayerMP player=actor(mc);TileEntityModuleRack before=rack(player.world);NBTTagCompound saved=before.writeToNBT(new NBTTagCompound());
                TileEntity replacement=com.vandorlabs.copier.PhysicalTileFactory.create(player.getServerWorld(),player.world.getBlockState(at()),saved);
                need(replacement instanceof TileEntityModuleRack,"physical Rack reload factory");player.world.setTileEntity(at(),replacement);
                need(before.removeModule(0).isEmpty()&&before.drainModules().isEmpty(),"stale Rack object cannot duplicate reloaded modules");
                player.world.notifyBlockUpdate(at(),player.world.getBlockState(at()),player.world.getBlockState(at()),2);
            });held(mc,new ItemStack(ModItems.CONFIGURIZER));next(10);
        }else if(stage==10){
            if(current==null||current.getIncarnation()==incarnation||mc.player.getHeldItemMainhand().getItem()!=ModItems.CONFIGURIZER)return false;
            need(current.getOccupiedMask()==15&&current.getHousingTexture()==0&&current.emitsRedstone(),"same-position reload retains public state and four owned modules");
            final boolean[] registered={false};
            ModuleRackLiveChecks.server(mc,()->registered[0]=RedstoneChannels.level(actor(mc).world,at(),73)==15);
            if(!registered[0])return false;
            confirmOutput(mc,4,false);use(mc,.5,.98);next(11);
        }else if(stage==11){
            if(gui==null||gui.inventorySlots.windowId==previousWindow)return false;
            ModuleRackLiveChecks.server(mc,()->{
                EntityPlayerMP player=actor(mc);TileEntityModuleRack owned=rack(player.world);
                need(player.openContainer instanceof ContainerModuleRack&&((ContainerModuleRack)player.openContainer).tile==owned,"Configurator uses same dedicated dialog and installed owner");
                boolean allowed=player.capabilities.allowEdit;player.capabilities.allowEdit=false;
                try{need(!MessageModuleRack.apply(player,new MessageModuleRack(player.openContainer.windowId,at(),-1,false)),"server edit permission refusal");}
                finally{player.capabilities.allowEdit=allowed;}
                need(!owned.getUpdateTag().hasKey("Items")&&!owned.getUpdateTag().hasKey("ForgeCaps")&&!owned.getUpdateTag().toString().contains("PrivateModuleTest"),"actual public update excludes module and capability payloads");
            });
            PacketHandler.INSTANCE.sendToServer(new MessageModuleRack(previousWindow,at(),-1,false));next(12);
        }else if(stage==12){
            if(wait<8)return false;need(current.getHousingTexture()==0&&current.emitsRedstone(),"real stale-window C2S refused");
            click(gui,101,0);next(13);
        }else if(stage==13){
            List<GuiButton> controls=InlineTransmitterWidget.controls(gui);if(controls.size()!=2)return false;
            GuiButton local=controls.get(1);need(InlineTransmitterWidget.mouseInput(gui,local.x+20,local.y+10,0),"local output enable");next(14);
        }else if(stage==14){
            if(!TransmitterSlots.localRedstone(current))return false;confirmOutput(mc,4,true);click(gui,0,1);next(15);
        }else if(stage==15){
            if(current.emitsRedstone())return false;
            ModuleRackLiveChecks.server(mc,()->{
                EntityPlayerMP player=actor(mc);TileEntityModuleRack owned=rack(player.world);
                need(owned.getOccupiedMask()==15&&owned.output.level()==0&&RedstoneChannels.level(player.world,at(),73)==0,"emission off clears both ranged and physical output");
                for(EnumFacing side:EnumFacing.values())need(player.world.getBlockState(at()).getBlock().getWeakPower(player.world.getBlockState(at()),player.world,at(),side)==0,"off gates every local side");
                int modules=0;for(int slot=0;slot<4;slot++)modules+=owned.getStackInSlot(slot).getCount();
                need(modules==4,"exactly four usable modules after configuration/reload");
            });click(gui,1,0);
            System.out.println("[vandorlabs][reprolab] module-rack PASS settings checks="+checks+" ordinary/Configurator/Housing/C2S/previous/transmission/proportional/reload/privateItems/stale/permissions");return true;
        }
        return false;
    }
}
