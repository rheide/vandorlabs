package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.canopy.CanopyMesh;
import com.vandorlabs.canopy.LargeCanopySettings;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.screen.VehicleInstrument;
import com.vandorlabs.tiles.TileEntityCanopy;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketHeldItemChange;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import javax.imageio.ImageIO;
import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.Future;

/** Real Configurizer packet, independent pane choices, saved item/world data and world-frame captures. */
final class CanopyDisplayRuntimeChecks {
    private static final String[] KINDS={"canopy","long_canopy","front_canopy_glass","large_front_canopy_glass","regular_canopy_glass"};
    private static final VehicleInstrument[] CHOICES=VehicleInstrument.values();
    private static final BlockPos FIRST=new BlockPos(168,106,194);
    private static int variant,stage,ticks;
    private static Future<?> pending;
    private static Vec3d eye;
    private static float yaw,pitch;

    private CanopyDisplayRuntimeChecks() {}

    static boolean tick(Minecraft mc,File output)throws Exception{
        if(variant>=KINDS.length)return true;
        if(mc.player==null||mc.world==null||mc.getIntegratedServer()==null)return false;
        require(++ticks<400,"timeout, screen="+mc.currentScreen+" eye="+mc.player.getPositionEyes(1)+" held="+mc.player.getHeldItemMainhand());
        if(pending!=null){
            if(!pending.isDone())return false;
            pending.get();pending=null;
        }
        if(stage==0){
            prepare(mc);next(1);return false;
        }
        TileEntityCanopy tile=TileEntityCanopy.at(mc.world,position());
        if(tile==null)return false;
        if(stage==1){
            if(ticks<12||!mc.playerController.isInCreativeMode()||mc.player.inventory.currentItem!=0
                    ||mc.player.getHeldItemMainhand().getItem()!=ModItems.CONFIGURIZER)return false;
            aim(mc,tile,false);next(2);return false;
        }
        if(stage==2&&aligned(mc)){
            Vec3d start=mc.player.getPositionEyes(1);
            RayTraceResult hit=tile.trace(start,start.add(mc.player.getLook(1).scale(5)));
            require(hit!=null,"camera points at actual pane");
            mc.playerController.processRightClickBlock(mc.player,mc.world,hit.getBlockPos(),hit.sideHit,hit.hitVec,EnumHand.MAIN_HAND);
            next(3);return false;
        }
        if(stage==3&&mc.currentScreen instanceof GuiLargeCanopy){
            GuiLargeCanopy gui=(GuiLargeCanopy)mc.currentScreen;
            require("Main".equals(button(gui,90).displayString)&&!button(gui,90).enabled,"dedicated editor defaults to Main");
            choose(gui,main());
            click(gui,93);
            require("Side".equals(button(gui,93).displayString)&&!button(gui,93).enabled,"independent Side tab");
            choose(gui,side());
            next(4);return false;
        }
        if(stage==4){
            if(tile.large.material!=main().choice||tile.large.material(true)!=side().choice)return false;
            require(ticks>0,"client edit synchronized");
            pending=mc.getIntegratedServer().addScheduledTask(()->verify(mc));
            shot(mc,output,"side-picker");
            mc.player.closeScreen();mc.gameSettings.hideGUI=true;
            next(5);return false;
        }
        if(stage==5){
            aim(mc,tile,false);next(6);return false;
        }
        if(stage==6&&ticks>18&&aligned(mc)&&CanopyDisplayRenderer.captured(tile,main(),false)){
            shot(mc,output,"main");aim(mc,tile,true);next(7);return false;
        }
        if(stage==7&&ticks>18&&aligned(mc)&&CanopyDisplayRenderer.captured(tile,side(),true)){
            shot(mc,output,"side");
            variant++;
            if(variant==KINDS.length){
                mc.gameSettings.hideGUI=false;
                System.out.println("[vandorlabs][reprolab] canopy-displays PASS real Configurizer, independent Main/Side, Status and four instruments, saved panes and world captures");
                return true;
            }
            next(0);
        }
        return false;
    }

    private static BlockPos position(){return FIRST.east(variant*16);}
    private static VehicleInstrument main(){return CHOICES[variant];}
    private static VehicleInstrument side(){return CHOICES[(variant+2)%CHOICES.length];}
    private static void next(int value){stage=value;ticks=0;}
    private static void require(boolean ok,String message){
        if(!ok)throw new IllegalStateException("Canopy display "+KINDS[variant]+" stage="+stage+" "+message);
    }

    private static EntityPlayerMP player(Minecraft mc){
        return mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
    }

    private static void prepare(Minecraft mc){
        BlockPos pos=position();String id=KINDS[variant];
        pending=mc.getIntegratedServer().addScheduledTask(()->{
            EntityPlayerMP p=player(mc);
            WorldServer world=p.getServerWorld();
            world.getChunkFromBlockCoords(pos);
            world.getGameRules().setOrCreateGameRule("doDaylightCycle","false");world.setWorldTime(6000);
            for(BlockPos floor:BlockPos.getAllInBox(pos.add(-6,-1,-6),pos.add(10,-1,10)))world.setBlockState(floor,Blocks.STONE.getDefaultState(),2);
            BlockCanopy block=(BlockCanopy)Block.getBlockFromName("vandorlabs:"+id);
            require(world.setBlockState(pos,block.getDefaultState(),3),"place canopy root");
            block.onBlockPlacedBy(world,pos,block.getDefaultState(),p,new ItemStack(block));
            p.setGameType(GameType.CREATIVE);p.setSneaking(false);
            p.capabilities.allowFlying=true;p.capabilities.isFlying=true;p.sendPlayerAbilities();
            p.inventory.currentItem=0;p.connection.sendPacket(new SPacketHeldItemChange(0));
            p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(ModItems.CONFIGURIZER));
            p.setHeldItem(EnumHand.OFF_HAND,ItemStack.EMPTY);p.inventoryContainer.detectAndSendChanges();
            p.connection.setPlayerLocation(pos.getX()+.5,pos.getY()+4,pos.getZ()-4,0,20);
        });
        mc.gameSettings.hideGUI=false;mc.gameSettings.fovSetting=55;
        mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
    }

    private static void aim(Minecraft mc,TileEntityCanopy tile,boolean side){
        CanopyMesh.Face selected=null;
        double biggest=0;
        for(CanopyMesh.Face face:tile.faces()){
            if(!face.glazing||face.sideGlass!=side)continue;
            Vec3d a=face.vertices[1].subtract(face.vertices[0]),b=face.vertices[2].subtract(face.vertices[0]);
            double area=a.crossProduct(b).lengthSquared();
            if(area>biggest){selected=face;biggest=area;}
        }
        require(selected!=null,"posed glass contains requested pane");
        Vec3d[] v=selected.vertices;
        Vec3d center=Vec3d.ZERO;
        for(Vec3d corner:v)center=center.add(CanopyMesh.rotate(corner,tile.facing()));
        center=center.scale(1D/v.length).add(new Vec3d(tile.getPos()));
        Vec3d a=CanopyMesh.rotate(v[0],tile.facing()),b=CanopyMesh.rotate(v[1],tile.facing()),c=CanopyMesh.rotate(v[2],tile.facing());
        Vec3d normal=b.subtract(a).crossProduct(c.subtract(a)).normalize();
        if(normal.y<-.15||Math.abs(normal.y)<.15&&normal.z>.2)normal=normal.scale(-1);
        eye=center.add(normal.scale(3));
        Vec3d look=center.subtract(eye);
        yaw=(float)Math.toDegrees(Math.atan2(-look.x,look.z));
        pitch=(float)-Math.toDegrees(Math.atan2(look.y,Math.hypot(look.x,look.z)));
        Vec3d target=eye;
        pending=mc.getIntegratedServer().addScheduledTask(()->{
            EntityPlayerMP p=player(mc);
            p.connection.setPlayerLocation(target.x,target.y-p.getEyeHeight(),target.z,yaw,pitch);
        });
    }

    private static boolean aligned(Minecraft mc){
        return ticks>5&&mc.player.getPositionEyes(1).squareDistanceTo(eye)<.01
                &&Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(mc.player.rotationYaw-yaw))<.1
                &&Math.abs(mc.player.rotationPitch-pitch)<.1;
    }

    private static void verify(Minecraft mc){
        TileEntityCanopy tile=TileEntityCanopy.at(player(mc).world,position()).owner();
        require(tile.large.material==main().choice&&tile.large.material(true)==side().choice,"both server choices saved");
        for(boolean side:new boolean[]{false,true}){
            VehicleInstrument expected=side?side():main();
            require(tile.faces().stream().anyMatch(face->face.sideGlass==side&&face.display==expected),"selected choice reaches exact posed pane");
        }
        LargeCanopySettings restored=new LargeCanopySettings();
        restored.read(tile.writeToNBT(new NBTTagCompound()));
        require(restored.key().equals(tile.large.key()),"saved world retains independent panes");
        restored.read(tile.block().configuredStack(tile).getTagCompound());
        require(restored.key().equals(tile.large.key()),"configured item retains independent panes");
    }

    private static GuiButton button(GuiLargeCanopy gui,int id){
        List<GuiButton> buttons=ReflectionHelper.getPrivateValue(GuiScreen.class,gui,"buttonList","field_146292_n");
        for(GuiButton button:buttons)if(button.id==id)return button;
        throw new IllegalStateException("Missing canopy tab "+id);
    }

    private static void click(GuiLargeCanopy gui,int id)throws Exception{
        GuiButton button=button(gui,id);
        gui.mouseClicked(button.x+button.width/2,button.y+button.height/2,0);
    }

    private static Object field(HousingTextureList list,String name)throws Exception{
        Field field=HousingTextureList.class.getDeclaredField(name);field.setAccessible(true);return field.get(list);
    }

    private static void choose(GuiLargeCanopy gui,VehicleInstrument instrument)throws Exception{
        HousingTextureList list=gui.materialPicker();
        List<?> rows=(List<?>)field(list,"rows");
        if(!rows.contains(instrument.choice))chooseRow(gui,list,rows,-100002);
        chooseRow(gui,list,rows,instrument.choice);
    }

    private static void chooseRow(GuiLargeCanopy gui,HousingTextureList list,List<?> rows,int choice)throws Exception{
        int count=(Integer)field(list,"count"),index=rows.indexOf(choice);
        require(index>=0,"picker includes row "+choice);
        int scroll=Math.max(0,index-count+1);
        Field field=HousingTextureList.class.getDeclaredField("scroll");field.setAccessible(true);field.setInt(list,scroll);
        int x=(Integer)field(list,"x"),y=(Integer)field(list,"y");
        gui.mouseClicked(x+4,y+(index-scroll)*HousingTextureList.ROW_HEIGHT+10,0);
    }

    private static void shot(Minecraft mc,File output,String face)throws Exception{
        ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),
                "png",new File(output,"shot_canopy-display-"+KINDS[variant]+"-"+face+".png"));
    }
}
