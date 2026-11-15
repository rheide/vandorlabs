package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.items.*;
import com.vandorlabs.surface.*;
import com.vandorlabs.tiles.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameType;

/** Real Survival Duplifier packets over already-rendered horizontal aperture groups. */
final class DirectionalJoinPlayerChecks {
    private static final BlockPos BASE=new BlockPos(240,100,240),SOURCE=BASE.add(-2,0,0);
    private static final String[] FAMILIES={"programmable_porthole_wall","programmable_porthole_block","programmable_glass"};
    private static final Set<BlockPos> DIRTY=new HashSet<>();
    private static int stage,age,family,settled,checks;
    private static WorldRenderListener listener;
    private static EntityPlayerMP actor(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());}
    private static void server(Minecraft mc,Runnable task)throws Exception{mc.getIntegratedServer().addScheduledTask(task).get(15,TimeUnit.SECONDS);}
    private static void need(boolean valid,String detail){checks++;if(!valid)throw new IllegalStateException("Directional Duplifier family="+FAMILIES[family]+" stage="+stage+" age="+age+": "+detail);}
    private static void next(int value){stage=value;age=settled=0;}
    private static BlockPos at(int cell){return BASE.east(cell);}
    private static TextureJoinOwner owner(Minecraft mc,BlockPos pos){TileEntity raw=mc.world.getTileEntity(pos);return raw instanceof TextureJoinOwner?(TextureJoinOwner)raw:null;}
    private static boolean policies(Minecraft mc,int direction){for(int i=0;i<3;i++)if(owner(mc,at(i))==null||owner(mc,at(i)).textureJoin().direction!=direction)return false;return true;}
    private static boolean synchronizedPlayer(Minecraft mc,boolean sneak)throws Exception {
        if(mc.playerController.getCurrentGameType()!=GameType.SURVIVAL||mc.player.inventory.currentItem!=0||mc.player.getHeldItemMainhand().getItem()!=ModItems.DUPLIFIER||!mc.player.getHeldItemOffhand().isEmpty()||mc.player.isSneaking()!=sneak)return false;
        final boolean[] valid={false};server(mc,()->{EntityPlayerMP p=actor(mc);valid[0]=p.interactionManager.getGameType()==GameType.SURVIVAL&&p.inventory.currentItem==0&&p.getHeldItemMainhand().getItem()==ModItems.DUPLIFIER&&p.getHeldItemOffhand().isEmpty()&&p.isSneaking()==sneak&&p.getPositionVector().squareDistanceTo(mc.player.getPositionVector())<.04;});return valid[0];
    }
    private static void sneak(Minecraft mc,boolean value){KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(),value);}
    private static void use(Minecraft mc,BlockPos target){
        double z=target.getZ()+(family==1?1:.625);Vec3d hit=new Vec3d(target.getX()+.12,target.getY()+.12,z),eye=mc.player.getPositionEyes(1),delta=hit.subtract(eye);
        mc.player.rotationYaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));mc.player.rotationPitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)));
        mc.entityRenderer.getMouseOver(1);
        RayTraceResult picked=mc.objectMouseOver;
        need(picked!=null&&picked.typeOfHit==RayTraceResult.Type.BLOCK&&target.equals(picked.getBlockPos()),"fresh vanilla pick target="+target+" actual="+picked);
        need(mc.playerController.processRightClickBlock(mc.player,mc.world,target,picked.sideHit,picked.hitVec,EnumHand.MAIN_HAND)==EnumActionResult.SUCCESS,"real Duplifier use packet");
    }
    private static void setup(Minecraft mc)throws Exception {
        mc.playerController.setGameType(GameType.SURVIVAL);mc.player.inventory.currentItem=0;sneak(mc,false);
        server(mc,()->{
            EntityPlayerMP p=actor(mc);p.interactionManager.setGameType(GameType.SURVIVAL);p.capabilities.isFlying=false;p.capabilities.allowFlying=false;p.sendPlayerAbilities();p.inventory.clear();p.inventory.currentItem=0;
            ItemStack tool=new ItemStack(ModItems.DUPLIFIER);DuplifierApplyOptions.setConnected(tool,true);DuplifierApplyOptions.setRespectJoins(tool,true);p.setHeldItem(EnumHand.MAIN_HAND,tool);p.setHeldItem(EnumHand.OFF_HAND,ItemStack.EMPTY);p.inventoryContainer.detectAndSendChanges();
            for(int x=-4;x<=5;x++)for(int z=-1;z<=5;z++){p.world.setBlockState(BASE.add(x,-1,z),Blocks.STONE.getDefaultState(),3);for(int y=0;y<3;y++)p.world.setBlockToAir(BASE.add(x,y,z));}
            Block block=Block.getBlockFromName("vandorlabs:"+FAMILIES[family]);
            for(int i=-1;i<3;i++) {BlockPos pos=i<0?SOURCE:at(i);IBlockState state=block.getDefaultState();p.world.setBlockState(pos,state,3);((TextureJoinOwner)p.world.getTileEntity(pos)).setTextureJoin(new TextureJoinSettings(i<0?4:3,0));}
            p.connection.setPlayerLocation(SOURCE.getX()+.5,BASE.getY(),BASE.getZ()+3,180,0);
        });
        if(listener==null){listener=new WorldRenderListener(){@Override public void markBlockRangeForRenderUpdate(int x0,int y0,int z0,int x1,int y1,int z1){if(stage!=5)return;for(int i=0;i<3;i++){BlockPos pos=at(i);if(pos.getX()>=Math.min(x0,x1)&&pos.getX()<=Math.max(x0,x1)&&pos.getY()>=Math.min(y0,y1)&&pos.getY()<=Math.max(y0,y1)&&pos.getZ()>=Math.min(z0,z1)&&pos.getZ()<=Math.max(z0,z1)&&owner(mc,pos)!=null&&owner(mc,pos).textureJoin().direction==4)DIRTY.add(pos);}}};mc.world.addEventListener(listener);}
    }
    static boolean tick(Minecraft mc,File output)throws Exception {
        mc.gameSettings.pauseOnLostFocus=false;mc.gameSettings.renderDistanceChunks=6;
        if(++age%80==0)System.out.println("[vandorlabs][reprolab] directional-duplifier waiting family="+family+" stage="+stage+" age="+age+" mode="+mc.playerController.getCurrentGameType()+" held="+mc.player.getHeldItemMainhand()+" position="+mc.player.getPositionVector()+" sneak="+mc.player.isSneaking());
        need(age<240,"bounded packet/state wait mode="+mc.playerController.getCurrentGameType()+" held="+mc.player.getHeldItemMainhand()+" position="+mc.player.getPositionVector());
        if(stage==0){DirectionalJoinRegressionChecks.run(mc);setup(mc);next(1);return false;}
        if(stage==1){if(!policies(mc,3)||owner(mc,SOURCE)==null||owner(mc,SOURCE).textureJoin().direction!=4||!synchronizedPlayer(mc,false))return false;warm(mc,true);sneak(mc,true);next(2);return false;}
        if(stage==2){if(!synchronizedPlayer(mc,true))return false;use(mc,SOURCE);next(3);return false;}
        if(stage==3){NBTTagCompound copied=mc.player.getHeldItemMainhand().getSubCompound(ItemDuplifier.SETTINGS_TAG);if(copied==null||copied.getCompoundTag("texture_join_policy").getInteger("Direction")!=4)return false;sneak(mc,false);server(mc,()->actor(mc).connection.setPlayerLocation(BASE.getX()+1.5,BASE.getY(),BASE.getZ()+3,180,0));next(4);return false;}
        if(stage==4){if(!synchronizedPlayer(mc,false))return false;warm(mc,true);DIRTY.clear();next(5);use(mc,BASE);return false;}
        if(stage==5){if(!policies(mc,4))return false;if(++settled<20)return false;server(mc,()->{for(int i=0;i<3;i++)need(((TextureJoinOwner)actor(mc).world.getTileEntity(at(i))).textureJoin().direction==4,"actual server copied policy applied to each original horizontal member");});need(DIRTY.size()==3,"each client member dirtied after new policy, got="+DIRTY);warm(mc,false);mc.gameSettings.hideGUI=true;mc.ingameGUI.getChatGUI().clearChatMessages(true);mc.getToastGui().clear();next(6);return false;}
        if(stage==6){if(++settled<3)return false;mc.ingameGUI.getChatGUI().clearChatMessages(true);mc.getToastGui().clear();boolean saved=mc.gameSettings.hideGUI;mc.gameSettings.hideGUI=true;ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"directional_join_"+FAMILIES[family]+".png"));mc.gameSettings.hideGUI=saved;System.out.println("[vandorlabs][reprolab] directional-duplifier PASS "+FAMILIES[family]+" real Survival shiftcapture/apply original3 Horizontal→NorthSouth postpacket dirty/unit emitted geometry");if(++family==FAMILIES.length){mc.world.removeEventListener(listener);System.out.println("[vandorlabs][reprolab] directional-join-player PASS checks="+checks);return true;}setup(mc);next(1);}
        return false;
    }
    private static void warm(Minecraft mc,boolean joined){
        for(int i=0;i<3;i++) {
            TileEntity tile=mc.world.getTileEntity(at(i));IBlockState state=mc.world.getBlockState(at(i));
            if(tile instanceof TileEntityAnimatedScreenSelector){
                TileEntityAnimatedScreenSelector p=(TileEntityAnimatedScreenSelector)tile;TEAnimatedScreenSelector.PortholeGroup group=TEAnimatedScreenSelector.portholeGroup(p,state);need(group.columns==(joined?3:1)&&group.rows==1,"warm aperture extent cell="+i+" got="+group.columns+"x"+group.rows);
                PortholeRetainedGeometry.Surfaces mesh=PortholeRetainedGeometry.capture(p,state);if(!joined){BlockPos local=new BlockPos(8,8,8);TileEntityAnimatedScreenSelector unit=new TileEntityAnimatedScreenSelector();unit.setPos(local);unit.setTextureJoin(new TextureJoinSettings(0,0));com.vandorlabs.vehicle.VehicleWorld view=new com.vandorlabs.vehicle.VehicleWorld(mc.world,com.vandorlabs.vehicle.VehicleStructure.display(Collections.singletonList(new com.vandorlabs.vehicle.VehicleStructure.Cell(local,state,unit.writeToNBT(new NBTTagCompound())))));try{PortholeRetainedGeometry.Surfaces oracle=PortholeRetainedGeometry.capture((TileEntityAnimatedScreenSelector)view.getTileEntity(local),state);need(mesh.housing.sameGeometry(oracle.housing)&&mesh.pane.sameGeometry(oracle.pane),"post-Duplifier actual emitted aperture/rims are individual cell="+i);}finally{PortholeGroupCache.unload(view);GlassSurfaceGroups.unload(view);}}
            }else {TileEntityProgrammableGlass g=(TileEntityProgrammableGlass)tile;GlassSurfaceGroups.Layout layout=GlassSurfaceGroups.frameLayout(g);need(layout.columns==(joined?3:1)&&layout.rows==1,"warm Glass frame extent");IBlockState actual=state.getBlock().getActualState(state,mc.world,at(i));need(actual.getValue(BlockGlassWall.LEFT)==(!joined||i==0)&&actual.getValue(BlockGlassWall.RIGHT)==(!joined||i==2),"post-policy authored Glass frame boundary");}
        }
    }
    private DirectionalJoinPlayerChecks() { }
}
