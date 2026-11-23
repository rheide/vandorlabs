package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.canopy.CanopyDisplayHit;
import com.vandorlabs.canopy.CanopyMesh;
import com.vandorlabs.canopy.LargeCanopySettings;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.screen.VehicleInstrument;
import com.vandorlabs.screen.VehicleStatusLayout;
import com.vandorlabs.tiles.TileEntityCanopy;
import com.vandorlabs.tiles.TileEntityPilotSeat;
import com.vandorlabs.tiles.TileEntityVehicleController;
import com.vandorlabs.vehicle.EntityGroundVehicle;
import com.vandorlabs.vehicle.VehicleInteraction;
import com.vandorlabs.vehicle.VehicleLookup;
import com.vandorlabs.vehicle.VehicleSelection;
import com.vandorlabs.vehicle.VehicleStatus;
import com.vandorlabs.vehicle.VehicleStructure;
import com.vandorlabs.vehicle.VehicleTileData;
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
import net.minecraft.util.EnumFacing;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;

/** Real Configurizer packet, independent pane choices, saved item/world data and world-frame captures. */
final class CanopyDisplayRuntimeChecks {
    private static final String[] KINDS={"canopy","long_canopy","front_canopy_glass","large_front_canopy_glass","regular_canopy_glass","canopy"};
    private static final VehicleInstrument[] CHOICES=VehicleInstrument.values();
    private static final BlockPos FIRST=new BlockPos(168,106,194);
    private static final BlockPos CARRIED=new BlockPos(0,2,0);
    private static UUID craftId;
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
        if(stage>=10)return carried(mc,output);
        TileEntityCanopy tile=TileEntityCanopy.at(mc.world,position());
        if(tile==null)return false;
        tile=tile.owner();
        if(stage==1){
            if(joined()&&tile.width()!=2)return false;
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
            if(main()==VehicleInstrument.HUD)checkHud(tile);
            shot(mc,output,"main");aim(mc,tile,true);next(7);return false;
        }
        if(stage==7&&ticks>18&&aligned(mc)&&CanopyDisplayRenderer.captured(tile,side(),true)){
            shot(mc,output,"side");
            if(joined()){aim(mc,tile,false,true);next(8);return false;}
            variant++;next(0);return false;
        }
        if(stage==8&&ticks>18&&aligned(mc)){
            shot(mc,output,"inside-main");aim(mc,tile,true,true);next(9);return false;
        }
        if(stage==9&&ticks>18&&aligned(mc)){
            shot(mc,output,"inside-side");
            pending=mc.getIntegratedServer().addScheduledTask(()->installCarried(mc));
            next(10);return false;
        }
        return false;
    }

    private static void installCarried(Minecraft mc){
        EntityPlayerMP p=player(mc);
        WorldServer world=p.getServerWorld();
        BlockPos origin=position().east(12).south(4);
        TileEntityCanopy root=TileEntityCanopy.at(world,position()).owner();
        List<VehicleStructure.Cell> cells=new ArrayList<>();
        BlockPos offset=CARRIED.subtract(root.getPos());
        for(int column=0;column<2;column++){
            TileEntityCanopy member=TileEntityCanopy.at(world,root.getPos().offset(root.facing().rotateY(),column));
            require(member!=null&&member.owner()==root,"carry both joined native canopy members");
            for(BlockPos at:member.reservedCells()){
                TileEntityCanopy tile=TileEntityCanopy.at(world,at);
                require(tile!=null,"complete captured canopy footprint");
                NBTTagCompound tag=VehicleTileData.translated(tile.writeToNBT(new NBTTagCompound()),offset);
                cells.add(new VehicleStructure.Cell(at.add(offset),world.getBlockState(at),tag));
            }
        }
        for(BlockPos at:BlockPos.getAllInBox(origin.add(-3,-1,-3),origin.add(8,-1,8)))world.setBlockState(at,Blocks.STONE.getDefaultState(),2);
        for(int x=0;x<5;x++)for(int z=0;z<5;z++)cells.add(new VehicleStructure.Cell(new BlockPos(x,0,z),Blocks.STONE.getDefaultState(),null));
        BlockPos seatPos=new BlockPos(3,1,2),controllerPos=new BlockPos(4,1,2);
        TileEntityPilotSeat seat=new TileEntityPilotSeat();seat.setPos(seatPos);seat.setTaxiMode(false);seat.owner=p.getUniqueID();
        cells.add(new VehicleStructure.Cell(seatPos,ModBlocks.PILOT_SEAT.getDefaultState(),seat.writeToNBT(new NBTTagCompound())));
        TileEntityVehicleController controller=new TileEntityVehicleController();controller.setPos(controllerPos);controller.owner=p.getUniqueID();
        cells.add(new VehicleStructure.Cell(controllerPos,ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),controller.writeToNBT(new NBTTagCompound())));
        String[] propulsion={"antigravity_plate","repulsor_array","vertical_hover_thruster","impulse_engine"};
        for(int i=0;i<propulsion.length;i++){
            cells.add(new VehicleStructure.Cell(new BlockPos(i,1,4),Block.getBlockFromName("vandorlabs:"+propulsion[i]).getDefaultState(),null));
        }
        EntityGroundVehicle craft=new EntityGroundVehicle(world);
        craft.install(new VehicleStructure(cells,seatPos,EnumFacing.NORTH,1));
        craft.setPosition(origin.getX(),origin.getY(),origin.getZ());
        require(world.spawnEntity(craft),"spawn carried status fixture");
        craftId=craft.getUniqueID();
        p.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
        p.inventoryContainer.detectAndSendChanges();
        p.connection.setPlayerLocation(origin.getX()+.5,origin.getY()+4,origin.getZ()-2,0,30);
    }

    private static boolean carried(Minecraft mc,File output)throws Exception{
        EntityGroundVehicle craft=null;
        for(EntityGroundVehicle candidate:VehicleLookup.loaded(mc.world))if(candidate.getUniqueID().equals(craftId))craft=candidate;
        if(craft==null||craft.structure==null||craft.view==null)return false;
        if(stage==10){
            if(ticks<12||!mc.player.getHeldItemMainhand().isEmpty())return false;
            TileEntityCanopy canopy=TileEntityCanopy.at(craft.view,CARRIED);
            require(canopy!=null&&canopy.large.material==VehicleInstrument.STATUS.choice,"captured screen retains Status");
            require((VehicleStatus.availableModes(craft.view,CARRIED,mc.player)&(1<<VehicleStatusLayout.HOVER))!=0,"carried status offers Hover");
            aimMode(mc,craft,canopy,VehicleStatusLayout.HOVER);next(11);return false;
        }
        if(stage==11&&aligned(mc)){
            VehicleInteraction.Target target=VehicleInteraction.pointed(mc.player);
            require(target!=null&&target.craft==craft&&target.hit.action==VehicleSelection.Action.CANOPY,
                    "normal screen ray selects the carried canopy");
            shot(mc,output,"carried-taxi");
            mc.objectMouseOver=new RayTraceResult(RayTraceResult.Type.MISS,eye,EnumFacing.UP,BlockPos.ORIGIN);
            ReflectionHelper.findMethod(Minecraft.class,"rightClickMouse","func_147121_ag").invoke(mc);
            next(12);return false;
        }
        if(stage==12&&ticks>10&&craft.hover){
            shot(mc,output,"carried-hover");
            pending=mc.getIntegratedServer().addScheduledTask(()->{
                EntityGroundVehicle actual=(EntityGroundVehicle)player(mc).getServerWorld().getEntityFromUuid(craftId);
                require(actual!=null&&actual.hover&&!TileEntityCanopy.at(actual.view,CARRIED).targetOpen,
                        "ordinary authorized Status packet enters Hover without toggling the canopy");
            });
            next(13);return false;
        }
        if(stage==13){
            variant++;mc.gameSettings.hideGUI=false;
            System.out.println("[vandorlabs][reprolab] canopy-displays PASS real picker, independent saved panes, inside joined dials, ordinary carried Status Hover click");
            return true;
        }
        return false;
    }

    private static void aimMode(Minecraft mc,EntityGroundVehicle craft,TileEntityCanopy canopy,int mode){
        VehicleStatusLayout layout=VehicleStatusLayout.forHeight(128);
        double u=layout.centerX(mode)/128D,v=1-layout.centerY(mode)/128D;
        for(CanopyMesh.Face face:canopy.faces()){
            if(face.display!=VehicleInstrument.STATUS||face.sideGlass)continue;
            Vec3d first=face.vertices[0];
            for(int i=1;i<face.vertices.length-1;i++){
                Vec3d a=face.vertices[i].subtract(first),b=face.vertices[i+1].subtract(first);
                Vec3d normal=a.crossProduct(b).normalize();
                boolean reverse=normal.y<0;
                if(reverse)normal=normal.scale(-1);
                double x=CanopyDisplayHit.mirrored(face,i,i+1)!=reverse?1-u:u;
                double[] p=face.uv[0],q=face.uv[i],r=face.uv[i+1];
                double ax=q[0]-p[0],ay=q[1]-p[1],bx=r[0]-p[0],by=r[1]-p[1];
                double area=ax*by-ay*bx;
                if(Math.abs(area)<1e-9)continue;
                double s=((x-p[0])*by-(v-p[1])*bx)/area,t=(ax*(v-p[1])-ay*(x-p[0]))/area;
                if(s<0||t<0||s+t>1)continue;
                Vec3d point=first.add(a.scale(s)).add(b.scale(t));
                Vec3d root=new Vec3d(canopy.getPos());
                Vec3d center=craft.toWorld(CanopyMesh.rotate(point,canopy.facing()).add(root));
                Vec3d outside=craft.toWorld(CanopyMesh.rotate(point.add(normal.scale(3)),canopy.facing()).add(root));
                placeCamera(mc,center,outside);
                return;
            }
        }
        throw new IllegalStateException("Hover center is outside the joined canopy canvas");
    }

    private static BlockPos position(){return FIRST.east(variant*16);}
    private static boolean joined(){return variant==KINDS.length-1;}
    private static VehicleInstrument main(){return variant==1||variant==2?VehicleInstrument.HUD:CHOICES[variant%CHOICES.length];}
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
            if(variant==1){TileEntityCanopy roof=TileEntityCanopy.at(world,pos);roof.large.angle=2;roof.markDirty();world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);}
            if(joined()){
                require(world.setBlockState(pos.east(),block.getDefaultState(),3),"place adjacent canopy root");
                block.onBlockPlacedBy(world,pos.east(),block.getDefaultState(),p,new ItemStack(block));
            }
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

    private static void aim(Minecraft mc,TileEntityCanopy tile,boolean side){aim(mc,tile,side,false);}
    private static void aim(Minecraft mc,TileEntityCanopy tile,boolean side,boolean inside){
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
        if(inside){
            double x=tile.width()/2D;
            if(side)x=v[0].x<x?tile.width()-.28:.28;
            Vec3d interior=new Vec3d(x,.58,side?1.12:1.84);
            Vec3d focus=side?new Vec3d(v[0].x,.52,1.15):new Vec3d(x,.83,.62);
            placeCamera(mc,CanopyMesh.rotate(focus,tile.facing()).add(new Vec3d(tile.getPos())),
                    CanopyMesh.rotate(interior,tile.facing()).add(new Vec3d(tile.getPos())));
        }else placeCamera(mc,center,center.add(normal.scale(3)));
    }

    private static void placeCamera(Minecraft mc,Vec3d center,Vec3d camera){
        eye=camera;
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
        if(variant==1){
            int roofs=0,slopes=0;
            for(CanopyMesh.Face face:tile.faces())if(face.glazing){
                Vec3d normal=face.vertices[1].subtract(face.vertices[0]).crossProduct(face.vertices[2].subtract(face.vertices[0])).normalize();
                if(Math.abs(normal.y)>.999){require(face.sideGlass&&face.display==side(),"horizontal roof follows Side");roofs++;}
                if(Math.abs(normal.y)>.1 && Math.abs(normal.z)>.1){require(!face.sideGlass&&face.display==main(),"windshield follows Main");slopes++;}
            }
            require(roofs>0&&slopes>0,"Long Angled roof and windshield verified");
        }
        for(boolean side:new boolean[]{false,true}){
            VehicleInstrument expected=side?side():main();
            require(tile.faces().stream().anyMatch(face->face.sideGlass==side&&face.display==expected),"selected choice reaches exact posed pane");
        }
        List<java.util.Map<Vec3d,double[]>> edges=java.util.Arrays.asList(new java.util.HashMap<>(),new java.util.HashMap<>());
        for(CanopyMesh.Face face:tile.baseFaces()){
            if(face.display==null)continue;
            for(int i=0;i<face.vertices.length;i++){
                double[] uv=face.uv[i];
                double[] previous=edges.get(face.sideGlass?1:0).putIfAbsent(face.vertices[i],uv);
                require(previous==null||Math.abs(previous[0]-uv[0])+Math.abs(previous[1]-uv[1])<1e-7,
                        "the pane UV remains continuous at authored shared vertices");
            }
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

    private static void checkHud(TileEntityCanopy tile)throws Exception{
        Field cache=CanopyDisplayRenderer.class.getDeclaredField("DISPLAYS");cache.setAccessible(true);
        Object[] displays=(Object[])((java.util.Map<?,?>)cache.get(null)).get(tile);
        Field buffer=displays[0].getClass().getDeclaredField("frame");buffer.setAccessible(true);
        net.minecraft.client.shader.Framebuffer frame=(net.minecraft.client.shader.Framebuffer)buffer.get(displays[0]);
        int old=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
        java.nio.ByteBuffer alpha=org.lwjgl.BufferUtils.createByteBuffer(frame.framebufferWidth*frame.framebufferHeight);
        frame.bindFramebufferTexture();org.lwjgl.opengl.GL11.glGetTexImage(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,0,org.lwjgl.opengl.GL11.GL_ALPHA,org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE,alpha);
        net.minecraft.client.renderer.GlStateManager.bindTexture(old);
        int clear=0,marks=0;
        for(int i=0;i<alpha.capacity();i++){int coverage=alpha.get(i)&255;if(coverage==0)clear++;if(coverage>127)marks++;}
        require(clear>alpha.capacity()*.8 && marks>200,"HUD glass alpha coverage cleared="+clear+" marked="+marks);
        System.out.println("[vandorlabs] canopy HUD alpha PASS "+clear+" clear, "+marks+" marked");
    }

    private static void shot(Minecraft mc,File output,String face)throws Exception{
        ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),
                "png",new File(output,"shot_canopy-display-"+KINDS[variant]+(joined()?"-joined":"")+"-"+face+".png"));
    }
}
