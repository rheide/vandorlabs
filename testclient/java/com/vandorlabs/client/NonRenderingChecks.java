package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.items.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/** Real block/tile and buffer code, with an in-memory world and no GL context. */
public final class NonRenderingChecks {
    public static void main(String[] args) {
        net.minecraft.init.Bootstrap.register();
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityAnimatedScreenSelector.class,
                new net.minecraft.util.ResourceLocation("minecraft:vandorlabs_data_check_screen"));
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityRampController.class,
                new net.minecraft.util.ResourceLocation("minecraft:vandorlabs_data_check_controller"));
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityControlledRamp.class,
                new net.minecraft.util.ResourceLocation("minecraft:vandorlabs_data_check_ramp"));
        ModBlocks.PROGRAMMABLE_RAMP = new BlockRampController();
        ModBlocks.CONTROLLED_RAMP = new BlockControlledRamp();
        ModBlocks.PROGRAMMABLE_BLOCK = new BlockProgrammableBlock();
        ModBlocks.PROGRAMMABLE_SLAB = new BlockProgrammableSlab();
        ModBlocks.PROGRAMMABLE_DIAGONAL_WALL = new BlockProgrammableWall("programmable_diagonal_wall", BlockProgrammableWall.Shape.DIAGONAL);
        DiagonalSurfaceChecks.run();
        placement();
        contiguous();
        rampClearance();
        TrapdoorChecks.run();
        DiagonalTrapdoorChecks.run();
        DiagonalAlignmentChecks.run();
        LandingGearFootprintChecks.run();
        SurfaceLayoutChecks.run();
        DoorMaterialChecks.run();
        CommonRenderChecks.run();
        TrapdoorTextureChecks.run();
        TrapdoorMeshParityChecks.run();
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityProgrammableLight.class,new net.minecraft.util.ResourceLocation("minecraft:vandorlabs_data_check_light"));
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityProgrammableTrigger.class,new net.minecraft.util.ResourceLocation("minecraft:vandorlabs_data_check_trigger"));
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityRedstoneLight.class,new net.minecraft.util.ResourceLocation("minecraft:vandorlabs_data_check_propulsion"));
        ModBlocks.PROGRAMMABLE_LIGHT=new BlockProgrammableLight();ModBlocks.PROGRAMMABLE_TRIGGER_BLOCK=new BlockProgrammableTrigger();
        SignalLevelRuntimeChecks.run(new MemoryWorld(false));
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntitySignalControl.class,new net.minecraft.util.ResourceLocation("minecraft:vandorlabs_data_check_control"));
        SignalControlRuntimeChecks.run(new MemoryWorld(false));
        ChannelReconciliationChecks.run();
        ChannelListChecks.run();
        RedstoneScreenChecks.run();
        PropulsionParticleChecks.run();
        TrapdoorAssemblyReuseChecks.run();
        HousingStateChecks.run();
        HousingModelChecks.run();
        ScreenPowerReadChecks.run();
        TrapdoorCollisionChecks.run();
        TextureNameChecks.run();
        TrapdoorRayCandidateChecks.run();
        OffsetInteractionChecks.run();
        TrapdoorPowerChecks.run();
        PropertyHashChecks.run();
        TrapdoorStateChecks.run();
        DoorQuadPlanChecks.run();
        OpaqueDoorBatchChecks.run();
        LargeDoorChecks.run();
        CargoDoorChecks.run();
        TextureTierChecks.run();
        ComponentTextureChecks.run();
        ScreenDesignChecks.run();
        XDoorChecks.run();
        TrapdoorPanelChecks.run();
        RampMaterialChecks.run();
        RampInterpolationChecks.run();
        DiagonalRayTraceChecks.run();
        TrapdoorScratchChecks.run();
        GroupCacheLifetimeChecks.run();
        TrapdoorSectionPresenceChecks.run();
        ScreenTextureLocationChecks.run();
        DiagonalChunkChecks.run();
    }

    private static void placement() {
        MemoryWorld client = new MemoryWorld(true);
        BlockPos pos = new BlockPos(10,100,10);
        TileEntityAnimatedScreenSelector configured = new TileEntityAnimatedScreenSelector();
        configured.setHousingTexture(4);
        configured.setFaceTextures(new FaceTextures(true,new int[]{1,2,3,4,5,6}));
        NBTTagCompound data=configured.writeToNBT(new NBTTagCompound());
        data.setInteger("x",-999); data.setInteger("y",-999); data.setInteger("z",-999);
        for(Block block:new Block[]{ModBlocks.PROGRAMMABLE_BLOCK,ModBlocks.PROGRAMMABLE_SLAB,
                ModBlocks.PROGRAMMABLE_WALL,ModBlocks.PROGRAMMABLE_PORTHOLE_WALL,
                ModBlocks.PROGRAMMABLE_DIAGONAL_WALL}) {
            ItemBlock item=new ItemBlock(block);
            ItemStack stack=new ItemStack(item);stack.setTagInfo("BlockEntityTag",data.copy());
            require(item.placeBlockAt(stack,null,client,pos,EnumFacing.UP,.5F,.5F,.5F,block.getDefaultState()),"placement failed");
            TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)client.getTileEntity(pos);
            require(tile.getHousingTexture()==4 && tile.getFaceTextures().equals(configured.getFaceTextures()),"default finish before server reply");
            require(tile.getPos().equals(pos),"item overwrote destination coordinates");
            if(block instanceof com.vandorlabs.blocks.BlockProgrammableWall)
                require(tile.getMaxRenderDistanceSquared()>256D*256D,"wall distance cutoff");
            else require(tile.getMaxRenderDistanceSquared()==4096,"unrelated tile cutoff changed");
            NBTTagCompound correction=tile.getUpdateTag();correction.setInteger("housingTexture",2);
            tile.onDataPacket(null,new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos,0,correction));
            require(tile.getHousingTexture()==2,"server correction lost");
            client.clear();
        }
        System.out.println("PASS: immediate client placement settings, server correction and wall distance policy");
    }

    private static void contiguous() {
        MemoryWorld world=new MemoryWorld(false);
        EntityPlayer player=new EntityPlayer(world,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"DataChecks")) {
            @Override public boolean isSpectator(){return false;}
            @Override public boolean isCreative(){return true;}
            @Override public boolean canPlayerEdit(BlockPos p,EnumFacing face,ItemStack tool){return capabilities.allowEdit;}
        };
        player.capabilities.allowEdit=true;
        ItemStack tool=new ItemStack(new Item());
        NBTTagCompound copy=new NBTTagCompound();copy.setInteger(ProgrammableSettings.WALL_TEXTURE,4);
        tool.setTagInfo(ItemDuplifier.SETTINGS_TAG,copy);
        BlockPos origin=new BlockPos(10,100,10);
        BlockPos[] chain={origin,origin.up(),origin.up().east().south(),origin.up(2).east(2).south(2)};
        for(int i=0;i<chain.length;i++)world.setBlockState(chain[i],ModBlocks.PROGRAMMABLE_SLAB.getDefaultState()
                .withProperty(BlockProgrammableSlab.HALF,i%2==0?BlockSlab.EnumBlockHalf.TOP:BlockSlab.EnumBlockHalf.BOTTOM),2);
        BlockPos separate=origin.east(6),different=chain[3].east(),full=origin.west();
        world.setBlockState(separate,ModBlocks.PROGRAMMABLE_SLAB.getDefaultState(),2);
        world.setBlockState(different,ModBlocks.PROGRAMMABLE_SLAB.getDefaultState(),2);
        ((TileEntityAnimatedScreenSelector)world.getTileEntity(different)).setHousingTexture(2);
        world.setBlockState(full,ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),2);
        player.capabilities.allowEdit=false;
        require(DuplifierConnectedApply.apply(world,origin,tool,player,EnumFacing.UP)==0,"ignored edit permissions");
        player.capabilities.allowEdit=true;
        require(DuplifierConnectedApply.apply(world,origin,tool,player,EnumFacing.UP)==chain.length,"missed vertical or corner slab contact");
        for(int i=0;i<chain.length;i++) {
            require(((TileEntityAnimatedScreenSelector)world.getTileEntity(chain[i])).getHousingTexture()==4,"missed slab texture");
            require(world.getBlockState(chain[i]).getValue(BlockProgrammableSlab.HALF)
                    ==(i%2==0?BlockSlab.EnumBlockHalf.TOP:BlockSlab.EnumBlockHalf.BOTTOM),"changed slab half");
        }
        require(((TileEntityAnimatedScreenSelector)world.getTileEntity(separate)).getHousingTexture()==0,"crossed gap");
        require(((TileEntityAnimatedScreenSelector)world.getTileEntity(different)).getHousingTexture()==2,"crossed configuration boundary");
        require(((TileEntityAnimatedScreenSelector)world.getTileEntity(full)).getHousingTexture()==0,"mixed slab and full block");
        world.clear();
        BlockPos edge=new BlockPos(15,100,15);
        world.chunkLimit=true;
        world.setBlockState(edge,ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),2);
        require(DuplifierConnectedApply.apply(world,edge,tool,player,EnumFacing.UP)==1,"loaded chunk boundary failed");
        world.chunkLimit=false;world.clear();
        for(int i=0;i<=DuplifierConnectedApply.MAX_CELLS;i++)
            world.setBlockState(origin.add(i%16,i/256,(i/16)%16),ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),2);
        require(DuplifierConnectedApply.apply(world,origin,tool,player,EnumFacing.UP)==-1,"accepted oversized region");
        for(TileEntity tile:world.tiles.values())require(((TileEntityAnimatedScreenSelector)tile).getHousingTexture()==0,"partially applied oversized region");
        System.out.println("PASS: vertical slabs, edge/corner connectivity, matching boundaries, permissions, loaded chunks and atomic size limit");
    }

    private static void rampClearance() {
        for(int mode=0;mode<3;mode++) for(boolean descending:new boolean[]{true,false}) {
            for(boolean fits:new boolean[]{true,false}) {
                MemoryWorld world=new MemoryWorld(false);
                BlockPos control=new BlockPos(10,100,10), seed=control.south();
                world.setBlockState(control,ModBlocks.PROGRAMMABLE_RAMP.getDefaultState()
                        .withProperty(BlockVandorDirectional.FACING,EnumFacing.SOUTH),2);
                TileEntityRampController controller=(TileEntityRampController)world.getTileEntity(control);
                NBTTagCompound data=controller.writeToNBT(new NBTTagCompound());
                data.setInteger(com.vandorlabs.persistence.SaveSchema.Ramp.END_OFFSET_HALF_STEPS,descending?-5:5);
                controller.readFromNBT(data);
                controller.elevator=mode==1; controller.extendSegments=mode==2; controller.speed=0;
                IBlockState slab=Blocks.STONE_SLAB.getDefaultState().withProperty(BlockSlab.HALF,
                        descending==fits ? BlockSlab.EnumBlockHalf.TOP : BlockSlab.EnumBlockHalf.BOTTOM);
                for(int row=0;row<3;row++) {
                    world.setBlockState(seed.south(row),slab,2);
                    world.setBlockState(seed.south(row).up(descending?-3:3),Blocks.STONE.getDefaultState(),2);
                }
                boolean deployed=controller.request(true);
                require(deployed==fits,"fractional slab clearance: mode="+mode+" descending="+descending
                        +" fits="+fits+" status="+controller.status);
                if(fits) {
                    for(int tick=0;tick<200 && controller.isMoving();tick++){world.time++;controller.scheduledTick();}
                    require(!controller.error && controller.isOpen(),"fractional slab movement stopped");
                    for(int row=0;row<3;row++)require(world.getBlockState(seed.south(row).up(descending?-3:3)).getBlock()==Blocks.STONE,
                            "movement overwrote touching ground or ceiling");
                    require(controller.recover(false),"slab recovery failed");
                }
                for(int row=0;row<3;row++)require(world.getBlockState(seed.south(row)).equals(slab),"slab source lost");
            }
        }
        System.out.println("PASS: 2.5-block slab clearance in Ramp, Lift and Extend; ground/ceiling contact and real obstructions");
    }

    static final class MemoryWorld extends World {
        final Map<BlockPos,IBlockState> states=new HashMap<>();
        final Map<BlockPos,TileEntity> tiles=new HashMap<>();
        boolean chunkLimit;
        long time;
        int updates,renderUpdates,dirty,lightChecks,stateReads;
        BlockPos renderMin,renderMax;
        MemoryWorld(boolean remote) {
            super(null,new net.minecraft.world.storage.WorldInfo(new NBTTagCompound()),
                    new net.minecraft.world.WorldProviderSurface(),new net.minecraft.profiler.Profiler(),remote);
        }
        void clear(){states.clear();tiles.clear();}
        @Override public long getTotalWorldTime(){return time;}
        @Override public int getCombinedLight(BlockPos p,int minimum){return ((p.getX()&15)<<20)|(Math.max(minimum,p.getZ()&15)<<4);}
        @Override public void scheduleUpdate(BlockPos p,Block block,int delay){}
        @Override public void notifyNeighborsOfStateChange(BlockPos p,Block block,boolean observers){}
        @Override public <T extends net.minecraft.entity.Entity> List<T> getEntitiesWithinAABB(Class<? extends T> type,
                net.minecraft.util.math.AxisAlignedBB box){return Collections.emptyList();}
        @Override public BlockPos getSpawnPoint(){return new BlockPos(0,64,0);}
        @Override protected net.minecraft.world.chunk.IChunkProvider createChunkProvider(){return null;}
        @Override protected boolean isChunkLoaded(int x,int z,boolean empty){return !chunkLimit || x==0&&z==0;}
        private void loaded(BlockPos p){if(!isBlockLoaded(p))throw new IllegalStateException("read unloaded chunk "+p);}
        @Override public IBlockState getBlockState(BlockPos p){stateReads++;loaded(p);return states.getOrDefault(p,Blocks.AIR.getDefaultState());}
        @Override public void removeTileEntity(BlockPos p){TileEntity tile=tiles.remove(p);if(tile!=null)tile.invalidate();}
        @Override public void playEvent(net.minecraft.entity.player.EntityPlayer player,int event,BlockPos p,int data){}
        @Override public TileEntity getTileEntity(BlockPos p){loaded(p);return tiles.get(p);}
        @Override public boolean setBlockState(BlockPos p,IBlockState state,int flags) {
            loaded(p);IBlockState before=states.put(p.toImmutable(),state);
            if(before!=null && before.getBlock()==state.getBlock() && tiles.containsKey(p))return true;
            TileEntity tile=state.getBlock().createTileEntity(this,state);
            if(tile!=null){tile.setWorld(this);tile.setPos(p.toImmutable());tiles.put(p.toImmutable(),tile);}else tiles.remove(p);
            return true;
        }
        @Override public boolean isSideSolid(BlockPos p,EnumFacing side,boolean fallback){return isBlockLoaded(p)?getBlockState(p).isSideSolid(this,p,side):fallback;}
        @Override public boolean isBlockModifiable(EntityPlayer player,BlockPos p){loaded(p);return true;}
        @Override public void notifyBlockUpdate(BlockPos p,IBlockState before,IBlockState after,int flags){loaded(p);updates++;super.notifyBlockUpdate(p,before,after,flags);}
        @Override public void markBlockRangeForRenderUpdate(BlockPos a,BlockPos b){renderUpdates++;renderMin=a.toImmutable();renderMax=b.toImmutable();super.markBlockRangeForRenderUpdate(a,b);}
        @Override public void markChunkDirty(BlockPos p,TileEntity tile){dirty++;}
        @Override public void updateComparatorOutputLevel(BlockPos p,Block block){}
        @Override public boolean checkLightFor(net.minecraft.world.EnumSkyBlock kind,BlockPos p){lightChecks++;return true;}
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
