package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.BlockVandorSwitch;
import com.vandorlabs.tiles.TileEntityRedstoneChannel;
import java.io.File;
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
    private static final BlockPos SWITCH = new BlockPos(0,2,2);
    private static final int CHANNEL = 24791;
    private static int stage, ticks;
    private static boolean wasDebug;
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
            require(Math.abs(clientCraft.posX-clientCraft.cruise.x())<.5
                    && Math.abs(clientCraft.posZ-clientCraft.cruise.z())<.5, "client craft is at the route coordinates");
            require(Math.abs(mc.player.posX-clientCraft.posX)<2 && Math.abs(mc.player.posZ-clientCraft.posZ)<2,
                    "rider and craft share the projected world");
            mc.gameSettings.thirdPersonView=0;
            wasDebug=mc.gameSettings.showDebugInfo;
            mc.gameSettings.showDebugInfo=true;
            aim(mc,clientCraft);
            stage=1;
            return false;
        }
        if (stage == 1) {
            String xyz = new VanillaDebug(mc).coordinates();
            require(xyz != null, "stock F3 XYZ row is present");
            String[] values=xyz.substring(5).split("/");
            require(values.length==3 && Math.abs(Double.parseDouble(values[0].trim())-mc.player.posX)<.002
                    && Math.abs(Double.parseDouble(values[2].trim())-mc.player.posZ)<.002, "F3 follows live client X/Z " + xyz);
            System.out.println("[vandorlabs][coordinate-probe] " + xyz + " flightAltitude=" + clientCraft.flightAltitude());
            ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),
                    "png",new File(output,"cruise-geographic-f3.png"));
            mc.gameSettings.showDebugInfo=wasDebug;
            require(isTarget(mc.player,clientCraft), "corrected client ray points at the carried rocker");
            stage=2;
        }
        if (stage == 2) {
            if(serverCheck==null){
                serverCheck=mc.getIntegratedServer().callFromMainThread(()->{
                    EntityPlayerMP pilot=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                    return pilot.getRidingEntity()==serverCraft && pilot.getHeldItemMainhand().isEmpty()
                            && isTarget(pilot,serverCraft);
                });
                return false;
            }
            if(!serverCheck.isDone())return false;
            boolean ready=serverCheck.get();
            serverCheck=null;
            if(!ready){aim(mc,clientCraft);return false;}
            require(!powered(clientCraft),"rocker starts off");
            MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickEmpty(mc.player,EnumHand.MAIN_HAND));
            stage=3;
            return false;
        }
        if(!powered(clientCraft))return false;
        if(serverCheck==null){serverCheck=mc.getIntegratedServer().callFromMainThread(()->powered(serverCraft));return false;}
        if(!serverCheck.isDone())return false;
        require(serverCheck.get(),"real interaction toggles the authoritative carried rocker");
        ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),
                "png",new File(output,"cruise-geographic-carried-control.png"));
        System.out.println("[vandorlabs][coordinate-probe] geographic HUD + real carried switch PASS");
        return true;
    }

    private static boolean powered(EntityGroundVehicle craft) {
        return craft.view.getBlockState(SWITCH).getValue(BlockVandorSwitch.ON);
    }

    private static boolean isTarget(EntityPlayer player, EntityGroundVehicle craft) {
        VehicleInteraction.Target hit=VehicleInteraction.pointed(player);
        return hit!=null && hit.craft==craft && hit.hit.cell.pos.equals(SWITCH) && VehicleInteraction.supported(player,hit);
    }

    private static void aim(Minecraft mc,EntityGroundVehicle craft) {
        AxisAlignedBB box=craft.view.getBlockState(SWITCH).getBoundingBox(craft.view,SWITCH);
        Vec3d local=new Vec3d(SWITCH).addVector((box.minX+box.maxX)/2,(box.minY+box.maxY)/2,(box.minZ+box.maxZ)/2);
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

    private static final class VanillaDebug extends GuiOverlayDebug {
        VanillaDebug(Minecraft mc) {super(mc);}
        String coordinates() {
            for (String line : getDebugInfoLeft()) if (line.startsWith("XYZ: ")) return line;
            return null;
        }
    }
}
