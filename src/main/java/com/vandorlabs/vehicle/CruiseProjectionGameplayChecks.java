package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.BlockVandorSwitch;
import com.vandorlabs.tiles.TileEntityRedstoneChannel;
import java.io.File;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import javax.imageio.ImageIO;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiOverlayDebug;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Real debug overlay and carried controls while the aircraft crosses untracked columns. */
@SideOnly(Side.CLIENT)
final class CruiseProjectionGameplayChecks {
    private static final BlockPos SWITCH = new BlockPos(0,2,2), CONTROLLER = BlockPos.ORIGIN.east();
    private static final int CHANNEL = 24791;
    private static int stage, ticks;
    private static boolean wasDebug, hudWasHidden, switchInitiallyOn;
    private static Future<Boolean> serverCheck;

    private CruiseProjectionGameplayChecks() {}

    static List<VehicleStructure.Cell> cells() {
        Block block = Block.getBlockFromName("vandorlabs:rocker_switch");
        require(block instanceof BlockVandorSwitch, "registered rocker switch");
        IBlockState state = block.getDefaultState().withProperty(BlockVandorSwitch.FACING, EnumFacing.NORTH);
        TileEntityRedstoneChannel control = new TileEntityRedstoneChannel();
        control.setPos(SWITCH);
        control.setRedstoneChannel(CHANNEL);
        return Arrays.asList(new VehicleStructure.Cell(SWITCH, state, control.writeToNBT(new NBTTagCompound())),
                new VehicleStructure.Cell(SWITCH.south(), Blocks.IRON_BLOCK.getDefaultState(), null));
    }

    static boolean tick(Minecraft mc, File output, EntityGroundVehicle serverCraft) throws Exception {
        require(++ticks < 120, "timed out stage=" + stage + " rider=" + mc.player.getRidingEntity());
        if (!(mc.player.getRidingEntity() instanceof EntityGroundVehicle)) return false;
        EntityGroundVehicle clientCraft = (EntityGroundVehicle) mc.player.getRidingEntity();
        if (clientCraft.view == null || !clientCraft.cruise.active()) return false;
        if (stage == 0) {
            require(clientCraft.getUniqueID().equals(serverCraft.getUniqueID()), "same real mounted craft");
            // The synchronized geographic route is a BlockPos, while physical positions retain sub-block precision.
            if(Math.floor(clientCraft.posX)!=clientCraft.cruise.x()
                    || Math.floor(clientCraft.posZ)!=clientCraft.cruise.z()) return false;
            require(Math.abs(mc.player.posX-clientCraft.posX)<2 && Math.abs(mc.player.posZ-clientCraft.posZ)<2,
                    "rider and craft share the projected world");
            if(System.getenv("VANDOR_LABS_TEST_JOURNEYMAP")!=null) {
                require(com.vandorlabs.client.CruiseMapView.x(mc.player.posX)==mc.player.posX
                        &&com.vandorlabs.client.CruiseMapView.z(mc.player.posZ)==mc.player.posZ,
                        "JourneyMap retains the physical X/Z without an extra projection");
                CruiseRuntimeChecks.verifyJourneyMap(mc);
            }
            mc.gameSettings.thirdPersonView=0;
            wasDebug=mc.gameSettings.showDebugInfo;
            hudWasHidden=mc.gameSettings.hideGUI;
            mc.gameSettings.showDebugInfo=true;
            mc.gameSettings.hideGUI=false;
            aim(mc,clientCraft,SWITCH);
            stage=1;
            return false;
        }
        if (stage == 1) {
            String xyz = new VanillaDebug(mc).coordinates();
            require(xyz != null, "stock F3 XYZ row is present");
            String[] values=xyz.substring(5).split("/");
            NumberFormat coordinates=NumberFormat.getNumberInstance();
            require(values.length==3 && Math.abs(coordinates.parse(values[0].trim()).doubleValue()-mc.player.posX)<.002
                    && Math.abs(coordinates.parse(values[2].trim()).doubleValue()-mc.player.posZ)<.002,
                    "F3 follows live client X/Z " + xyz);
            System.out.println("[vandorlabs][coordinate-probe] " + xyz + " flightAltitude=" + clientCraft.flightAltitude());
            ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),
                    "png",new File(output,"cruise-geographic-f3.png"));
            mc.gameSettings.showDebugInfo=wasDebug;
            require(isTarget(mc.player,clientCraft,SWITCH), "corrected client ray points at the carried rocker");
            stage=2;
        }
        if (stage == 2 || stage == 4) {
            BlockPos target=stage==2?SWITCH:CONTROLLER;
            if(serverCheck==null){
                serverCheck=mc.getIntegratedServer().callFromMainThread(()->{
                    EntityPlayerMP pilot=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                    return pilot.getRidingEntity()==serverCraft && pilot.getHeldItemMainhand().isEmpty()
                            && isTarget(pilot,serverCraft,target);
                });
                return false;
            }
            if(!serverCheck.isDone())return false;
            boolean ready=serverCheck.get();
            serverCheck=null;
            if(!ready){aim(mc,clientCraft,target);return false;}
            require(isTarget(mc.player,clientCraft,target),"client ray aligned with the authorized server target " + target);
            if(stage==2){
                switchInitiallyOn="read".equals(System.getenv("VANDOR_LABS_CRUISE_PHASE"));
                require(powered(clientCraft)==switchInitiallyOn,"rocker retains the state expected before clicking");
            }
            MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickEmpty(mc.player,EnumHand.MAIN_HAND));
            stage++;
            return false;
        }
        if (stage == 3) {
            if(powered(clientCraft)==switchInitiallyOn)return false;
            if(serverCheck==null){serverCheck=mc.getIntegratedServer().callFromMainThread(()->powered(serverCraft));return false;}
            if(!serverCheck.isDone())return false;
            require(serverCheck.get()!=switchInitiallyOn,"real interaction toggles the authoritative carried rocker");
            serverCheck=null;
            ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),
                    "png",new File(output,"cruise-geographic-carried-control.png"));
            aim(mc,clientCraft,CONTROLLER);
            stage=4;
            return false;
        }
        if(stage==5){
            if(mc.currentScreen==null)return false;
            require(mc.currentScreen.getClass().getName().endsWith("VehicleClient$Preview"),
                    "real carried Controller use opens its protected dialog");
            stage=6;
            return false;
        }
        ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),
                "png",new File(output,"cruise-geographic-controller.png"));
        mc.displayGuiScreen(null);
        mc.gameSettings.hideGUI=hudWasHidden;
        System.out.println("[vandorlabs][coordinate-probe] geographic HUD + real carried switch and Controller PASS");
        return true;
    }

    private static boolean powered(EntityGroundVehicle craft) {
        return craft.view.getBlockState(SWITCH).getValue(BlockVandorSwitch.ON);
    }

    private static boolean isTarget(EntityPlayer player, EntityGroundVehicle craft, BlockPos target) {
        VehicleInteraction.Target hit=VehicleInteraction.pointed(player);
        return hit!=null && hit.craft==craft && hit.hit.cell.pos.equals(target) && VehicleInteraction.supported(player,hit);
    }

    private static void aim(Minecraft mc,EntityGroundVehicle craft,BlockPos target) {
        AxisAlignedBB box=craft.view.getBlockState(target).getBoundingBox(craft.view,target);
        Vec3d local=new Vec3d(target).addVector((box.minX+box.maxX)/2,(box.minY+box.maxY)/2,(box.minZ+box.maxZ)/2);
        Vec3d delta=craft.toWorld(local).subtract(mc.player.getPositionEyes(1));
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));
        mc.player.prevRotationYaw=mc.player.rotationYaw=yaw;
        mc.player.prevRotationPitch=mc.player.rotationPitch=pitch;
        mc.player.connection.sendPacket(new CPacketPlayer.Rotation(yaw,pitch,false));
    }

    private static void require(boolean valid,String detail) {
        if(!valid)throw new IllegalStateException("Projected cruise gameplay: "+detail);
    }

    @SideOnly(Side.CLIENT)
    private static final class VanillaDebug extends GuiOverlayDebug {
        VanillaDebug(Minecraft mc) {super(mc);}
        String coordinates() {
            for (String line : call()) if (line.startsWith("XYZ: ")) return line;
            return null;
        }
    }
}
