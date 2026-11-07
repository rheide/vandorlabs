package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Opt-in actual-world preparation/emission measurement; does not estimate hardware FPS. */
public final class DenseGlassCacheChecks {
    private static final String[] IDS={"programmable_glass","programmable_diagonal_glass","programmable_diagonal_porthole"};
    private static final BlockPos BASE=new BlockPos(6144,180,6144);
    private static final BlockPos[][] POS=new BlockPos[3][512];
    private static int stage,ticks;
    private DenseGlassCacheChecks() { }
    public static boolean tick(Minecraft mc,File output)throws Exception {
        if(stage==0){
            mc.displayGuiScreen(null);mc.gameSettings.pauseOnLostFocus=false;
            mc.getIntegratedServer().addScheduledTask(()->{
                WorldServer world=mc.getIntegratedServer().getWorld(0);
                for(int role=0;role<IDS.length;role++){
                    Block block=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs",IDS[role]));
                    if(block==null||block==net.minecraft.init.Blocks.AIR)throw new AssertionError("Missing canonical "+IDS[role]);
                    for(int i=0;i<512;i++){
                        BlockPos pos=BASE.add((role==1?30:0)+(i&7)*3,((i>>6)&7)*3,(role==2?30:0)+((i>>3)&7)*3);
                        POS[role][i]=pos;world.setBlockState(pos,block.getDefaultState(),3);
                        if(world.getTileEntity(pos)==null)throw new AssertionError("Missing real owner "+pos);
                    }
                }
                EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
                actor.capabilities.allowFlying=true;actor.capabilities.isFlying=true;actor.sendPlayerAbilities();
                actor.connection.setPlayerLocation(BASE.getX()+24,110,BASE.getZ()+24,0,0);
            }).get();stage=1;return false;
        }
        if(++ticks>600)throw new AssertionError("Dense glass client owners not synchronized stage="+stage+" position="+mc.player.getPositionVector());
        if(stage==1){
            for(BlockPos[] role:POS)for(BlockPos pos:role)if(mc.world.getTileEntity(pos)==null)return false;
            if(ticks<40)return false;
            DiagonalWallMeshCache.clear();
            for(int role=0;role<IDS.length;role++)measure(mc,role);
            System.out.println("[vandorlabs][reprolab] utility26-focused PASS dense-glass-cache-only");stage=2;return true;
        }
        return true;
    }
    private static StaticSurfaceMesh mesh(Minecraft mc,int role,int index){
        TileEntity owner=mc.world.getTileEntity(POS[role][index]);
        if(role==0)return GlassFrameMesh.get((TileEntityProgrammableGlass)owner);
        TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)owner;
        IBlockState state=mc.world.getBlockState(tile.getPos());
        TextureAtlasSprite sprite=mc.getTextureMapBlocks().getAtlasSprite("minecraft:blocks/glass");
        PortholeHex.Slice slice=role==2?TEAnimatedScreenSelector.portholeGroup(tile,state).slice(tile.getPos()):null;
        return DiagonalWallMeshCache.get(tile,state,sprite,sprite,slice);
    }
    private static void measure(Minecraft mc,int role){
        Set<StaticSurfaceMesh> unique=Collections.newSetFromMap(new IdentityHashMap<StaticSurfaceMesh,Boolean>());
        int vertices=0;for(int i=0;i<512;i++){StaticSurfaceMesh mesh=mesh(mc,role,i);unique.add(mesh);vertices+=mesh.vertexCount();}
        long retained=0;for(StaticSurfaceMesh mesh:unique)retained+=mesh.cacheRetainedBytes();
        for(int pass=0;pass<8;pass++)for(int i=0;i<512;i++)mesh(mc,role,i);
        com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId(),before=bean.getThreadAllocatedBytes(thread);
        for(int pass=0;pass<4;pass++)for(int i=0;i<512;i++)mesh(mc,role,i);
        long lookupBytes=bean.getThreadAllocatedBytes(thread)-before;
        BufferBuilder buffer=new BufferBuilder(Math.max(2097152,vertices*32));
        for(int pass=0;pass<2;pass++){buffer.begin(7,DefaultVertexFormats.BLOCK);for(int i=0;i<512;i++)mesh(mc,role,i).draw(buffer,240,240);buffer.finishDrawing();buffer.reset();}
        before=bean.getThreadAllocatedBytes(thread);buffer.begin(7,DefaultVertexFormats.BLOCK);
        for(int i=0;i<512;i++)mesh(mc,role,i).draw(buffer,240,240);
        int emitted=buffer.getVertexCount();buffer.finishDrawing();long emitBytes=bean.getThreadAllocatedBytes(thread)-before;buffer.reset();
        if(emitted!=vertices)throw new AssertionError("Dense emitted vertex mismatch "+IDS[role]);
        System.out.println("[vandorlabs][dense-glass] role="+IDS[role]+" owners=512 uniqueMeshes="+unique.size()+" retainedBytes="+retained+" warmLookup2048Bytes="+lookupBytes+" emittedVertices="+emitted+" lookupAndEmissionBytes="+emitBytes);
        if(Boolean.getBoolean("vandorlabs.denseGlassRequireShared")&&unique.size()!=1)throw new AssertionError("Equivalent owners must share immutable geometry "+IDS[role]);
    }
}
