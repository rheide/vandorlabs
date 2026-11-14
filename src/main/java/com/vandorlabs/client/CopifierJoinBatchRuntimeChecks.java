package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.items.*;
import com.vandorlabs.surface.TextureJoinSettings;
import com.vandorlabs.tiles.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;

/** Real Copifier batch packets must dirty chunk meshes after the new join policy is installed. */
final class CopifierJoinBatchRuntimeChecks {
    private static final BlockPos BASE=new BlockPos(240,100,240);
    private static final List<BlockPos> CELLS=new ArrayList<>();
    private static final Set<BlockPos> DIRTY=new HashSet<>();
    private static final int[] DIRECTIONS={2,3,4,5};
    private static int phase,ticks,direction,settled;
    private static WorldRenderListener listener;
    private static EntityPlayerMP actor(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());}
    private static void server(Minecraft mc,Runnable task)throws Exception{mc.getIntegratedServer().addScheduledTask(task).get(15,TimeUnit.SECONDS);}
    static boolean tick(Minecraft mc)throws Exception {
        mc.gameSettings.pauseOnLostFocus=false;if(++ticks>1200)throw new IllegalStateException("Copifier join batch timeout phase="+phase+" direction="+direction);
        if(phase==0){
            for(int y=0;y<3;y++)for(int x=0;x<3;x++)CELLS.add(BASE.add(x,y,0));
            mc.playerController.setGameType(GameType.CREATIVE);
            server(mc,()->{setupServer(mc);});phase=1;return false;
        }
        if(phase==1){
            for(BlockPos pos:CELLS)if(!(mc.world.getTileEntity(pos) instanceof TileEntityAnimatedScreenSelector))return false;
            listener=new WorldRenderListener(){@Override public void markBlockRangeForRenderUpdate(int x0,int y0,int z0,int x1,int y1,int z1){
                if(phase!=3)return;for(BlockPos pos:CELLS)if(pos.getX()>=x0&&pos.getX()<=x1&&pos.getY()>=y0&&pos.getY()<=y1&&pos.getZ()>=z0&&pos.getZ()<=z1){net.minecraft.tileentity.TileEntity raw=mc.world.getTileEntity(pos);if(raw instanceof TileEntityAnimatedScreenSelector&&((TileEntityAnimatedScreenSelector)raw).textureJoin().direction==DIRECTIONS[direction])DIRTY.add(pos);}
            }};mc.world.addEventListener(listener);phase=2;
        }
        if(phase==2){
            for(BlockPos pos:CELLS)JoinedHousingFaces.sample((TileEntityAnimatedScreenSelector)mc.world.getTileEntity(pos),63);
            DIRTY.clear();phase=3;settled=0;
            server(mc,()->{
                EntityPlayerMP player=actor(mc);ItemStack tool=new ItemStack(ModItems.DUPLIFIER);NBTTagCompound values=new NBTTagCompound(),policy=new NBTTagCompound();policy.setInteger("Direction",DIRECTIONS[direction]);policy.setInteger("Parity",0);values.setTag("texture_join_policy",policy);tool.setTagInfo(ItemDuplifier.SETTINGS_TAG,values);DuplifierApplyOptions.setConnected(tool,true);
                int applied=DuplifierConnectedApply.apply(player.world,BASE,tool,player,EnumFacing.SOUTH);if(applied!=9)throw new IllegalStateException("Copifier batch expected nine matching blocks, got "+applied);
            });return false;
        }
        if(phase==3){
            for(BlockPos pos:CELLS)if(((TileEntityAnimatedScreenSelector)mc.world.getTileEntity(pos)).textureJoin().direction!=DIRECTIONS[direction])return false;
            if(++settled<20)return false;
            if(DIRTY.size()!=CELLS.size()){mc.world.removeEventListener(listener);Set<BlockPos> missing=new HashSet<>(CELLS);missing.removeAll(DIRTY);throw new IllegalStateException("Copifier join batch stale chunk mesh direction="+DIRECTIONS[direction]+" missing post-policy render invalidation="+missing);}
            System.out.println("[vandorlabs][reprolab] copifier-join-batch direction PASS "+DIRECTIONS[direction]+" warmed9 actualserverapply/clientpackets/post-policy-mesh-invalidation");
            if(++direction==DIRECTIONS.length){mc.world.removeEventListener(listener);System.out.println("[vandorlabs][reprolab] copifier-join-batch PASS");return true;}phase=2;
        }
        return false;
    }
    private static void setupServer(Minecraft mc){
        EntityPlayerMP player=actor(mc);player.interactionManager.setGameType(GameType.CREATIVE);player.capabilities.isFlying=true;player.sendPlayerAbilities();player.connection.setPlayerLocation(241.5,101,245.5,180,0);
        for(BlockPos pos:CELLS){player.world.setBlockState(pos,ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),3);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)player.world.getTileEntity(pos);tile.setHousingTexture(ScreenHousingTextures.screenIndex("corp/ashiro_logo"));tile.setMainTextureLayout(0);tile.setTextureJoin(TextureJoinSettings.DEFAULT);}
    }
    private CopifierJoinBatchRuntimeChecks(){}
}
