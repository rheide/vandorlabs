package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.items.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.turntable.TurntableGeometry;
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
        BlockAvailabilityChecks.run();
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
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityProgrammableGlass.class,new net.minecraft.util.ResourceLocation("minecraft:vandorlabs_data_check_glass"));
        if(args.length>0 && args[0].equals("diagonal-joins")){DiagonalJoinMatrixChecks.run();return;}
        DiagonalSurfaceChecks.run();
        placement();
        contiguous();
        rampClearance();
        turntables();
        TrapdoorChecks.run();
        DiagonalTrapdoorChecks.run();
        DiagonalAlignmentChecks.run();
        DiagonalJoinMatrixChecks.run();
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
        net.minecraftforge.registries.ForgeRegistry<Block> controlBlocks=(net.minecraftforge.registries.ForgeRegistry<Block>)net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS;
        net.minecraftforge.registries.ForgeRegistry<Item> controlItems=(net.minecraftforge.registries.ForgeRegistry<Item>)net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS;
        boolean blocksLocked=controlBlocks.isLocked(),itemsLocked=controlItems.isLocked();
        controlBlocks.unfreeze();controlItems.unfreeze();
        for(String id:SignalControlRuntimeChecks.IDS){
            BlockSignalControl block=new BlockSignalControl("vandorlabs:"+id,id,32);
            net.minecraft.util.ResourceLocation key=new net.minecraft.util.ResourceLocation("vandorlabs",id);
            controlBlocks.register(block);
            ItemBlock item=new ItemBlock(block);
            item.setRegistryName(key);controlItems.register(item);
            try {
                java.lang.reflect.Field map=Item.class.getDeclaredField("BLOCK_TO_ITEM");map.setAccessible(true);
                ((java.util.Map<Block,Item>)map.get(null)).put(block,item);
            } catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot register fixture block item",e);}
        }
        if(blocksLocked)controlBlocks.freeze();if(itemsLocked)controlItems.freeze();
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

    private static void turntables() {
        ModBlocks.TURNTABLE=new BlockTurntable();
        ModBlocks.TURNTABLE_CONTROLLER=new BlockTurntableController();

        // A floor 3x3 platform turns the 3x3 volume resting on top of it.
        MemoryWorld world=new MemoryWorld(false);
        BlockPos control=new BlockPos(10,100,10);
        controllerAt(world,control);
        List<BlockPos> floor=floorPlatform(control.north());
        layPlatform(world,floor,EnumFacing.UP);
        for(BlockPos cell:floor)world.setBlockState(cell.up(),Blocks.STONE.getDefaultState(),2);
        TileEntityTurntableController controller=(TileEntityTurntableController)world.getTileEntity(control);
        controller.height=1;
        controller.speed=90;
        controller.rotationDirection=TurntableGeometry.CLOCKWISE;

        require(controller.scan(),"3x3 floor turntable rejected: "+controller.status);
        require(controller.isValidTurntable()&&controller.size()==3,"3x3 floor platform size");
        require(controller.axis()==TurntableGeometry.AXIS_Y,"floor turntable turns about Y");
        require(controller.request(true),"3x3 floor turntable failed to engage");
        require(controller.isCaptured()&&controller.isActive(),"floor turntable did not engage");
        require(controller.payloadSize()==9,"floor turntable payload was "+controller.payloadSize());
        for(BlockPos cell:floor)require(isAir(world,cell.up()),"captured floor block left in the world");
        world.time+=20;
        require(Math.abs(controller.angle(0F)-270D)<1e-9,"clockwise angle was "+controller.angle(0F));
        require(Math.abs(controller.angle(.5F)-267.75D)<1e-9,"frame fraction ignored");
        require(controller.request(false),"3x3 floor turntable failed to release");
        require(!controller.isActive()&&!controller.isCaptured()&&controller.payloadSize()==0,
                "floor turntable left engaged");
        for(BlockPos cell:floor)require(world.getBlockState(cell.up()).getBlock()==Blocks.STONE,
                "floor payload block not restored");
        controller.rotationDirection=TurntableGeometry.COUNTER_CLOCKWISE;
        require(controller.request(true),"counter-clockwise turntable failed to engage");
        world.time+=20;
        require(Math.abs(controller.angle(0F)-90D)<1e-9,"counter-clockwise angle was "+controller.angle(0F));
        controller.request(false);
        world.clear();

        // A rectangle and a hollow square are rejected with a readable message.
        MemoryWorld rectangle=new MemoryWorld(false);
        BlockPos rectangleControl=new BlockPos(10,100,10);
        controllerAt(rectangle,rectangleControl);
        List<BlockPos> wide=new ArrayList<>();
        for(int dx=-1;dx<=1;dx++)for(int dz=0;dz<4;dz++)wide.add(rectangleControl.north().add(dx,0,-dz));
        layPlatform(rectangle,wide,EnumFacing.UP);
        TileEntityTurntableController rectangleTile=(TileEntityTurntableController)rectangle.getTileEntity(rectangleControl);
        require(!rectangleTile.scan(),"3x4 platform accepted");
        require(rectangleTile.error&&rectangleTile.status.contains("square"),
                "3x4 platform message was "+rectangleTile.status);
        rectangle.clear();

        MemoryWorld hollow=new MemoryWorld(false);
        BlockPos hollowControl=new BlockPos(10,100,10);
        controllerAt(hollow,hollowControl);
        List<BlockPos> shell=floorPlatform(hollowControl.north());
        shell.remove(hollowControl.north().add(0,0,-1));
        layPlatform(hollow,shell,EnumFacing.UP);
        TileEntityTurntableController hollowTile=(TileEntityTurntableController)hollow.getTileEntity(hollowControl);
        require(!hollowTile.scan(),"hollow 3x3 platform accepted");
        require(hollowTile.error&&hollowTile.status.contains("filled"),"hollow platform message was "+hollowTile.status);
        hollow.clear();

        // A ceiling platform points down, and a wall platform turns about Z.
        MemoryWorld ceiling=new MemoryWorld(false);
        BlockPos ceilingControl=new BlockPos(10,100,10);
        controllerAt(ceiling,ceilingControl);
        List<BlockPos> ceilingCells=floorPlatform(ceilingControl.north());
        layPlatform(ceiling,ceilingCells,EnumFacing.DOWN);
        for(BlockPos cell:ceilingCells)ceiling.setBlockState(cell.down(),Blocks.STONE.getDefaultState(),2);
        TileEntityTurntableController ceilingTile=(TileEntityTurntableController)ceiling.getTileEntity(ceilingControl);
        ceilingTile.height=1;
        require(ceilingTile.request(true),"ceiling turntable failed to engage");
        require(ceilingTile.axis()==TurntableGeometry.AXIS_Y,"ceiling turntable axis was "+ceilingTile.axis());
        require(ceilingTile.payloadSize()==9,"ceiling payload was "+ceilingTile.payloadSize());
        for(BlockPos cell:ceilingCells)require(isAir(ceiling,cell.down()),"captured ceiling block left in the world");
        ceiling.clear();

        MemoryWorld wall=new MemoryWorld(false);
        BlockPos wallControl=new BlockPos(10,100,10);
        controllerAt(wall,wallControl);
        List<BlockPos> wallCells=new ArrayList<>();
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)wallCells.add(wallControl.north().add(dx,dy,0));
        layPlatform(wall,wallCells,EnumFacing.NORTH);
        for(BlockPos cell:wallCells)wall.setBlockState(cell.north(),Blocks.STONE.getDefaultState(),2);
        TileEntityTurntableController wallTile=(TileEntityTurntableController)wall.getTileEntity(wallControl);
        wallTile.height=1;
        require(wallTile.request(true),"wall turntable failed to engage");
        require(wallTile.axis()==TurntableGeometry.AXIS_Z,"wall turntable axis was "+wallTile.axis());
        require(wallTile.payloadSize()==9,"wall payload was "+wallTile.payloadSize());
        for(BlockPos cell:wallCells)require(isAir(wall,cell.north()),"captured wall block left in the world");
        wall.clear();

        // Losing a platform block drops the captured volume and flags the error.
        MemoryWorld removal=new MemoryWorld(false);
        BlockPos removalControl=new BlockPos(10,100,10);
        controllerAt(removal,removalControl);
        List<BlockPos> removalCells=floorPlatform(removalControl.north());
        layPlatform(removal,removalCells,EnumFacing.UP);
        for(BlockPos cell:removalCells)removal.setBlockState(cell.up(),Blocks.STONE.getDefaultState(),2);
        TileEntityTurntableController removalTile=(TileEntityTurntableController)removal.getTileEntity(removalControl);
        removalTile.height=1;
        require(removalTile.request(true),"removal turntable failed to engage");
        BlockPos removedPart=removalCells.get(0);
        removal.setBlockToAir(removedPart);
        removalTile.onPartRemoved(removedPart);
        require(!removalTile.isActive()&&!removalTile.isCaptured(),"removal left the turntable engaged");
        require(removalTile.error&&removalTile.status.contains("removed"),"removal status was "+removalTile.status);
        require(!removalTile.isValidTurntable(),"removal left the platform valid");
        require(removal.loadedEntityList.size()==9,"removal dropped "+removal.loadedEntityList.size()+" items");
        removal.clear();

        System.out.println("PASS: turntable scanning, capture/restore, axis by facing and part removal");
    }

    private static void controllerAt(MemoryWorld world,BlockPos pos) {
        world.setBlockState(pos,ModBlocks.TURNTABLE_CONTROLLER.getDefaultState()
                .withProperty(BlockVandorDirectional.FACING,EnumFacing.NORTH),2);
    }

    /** The 3x3 platform square that sits against the controller's front face. */
    private static List<BlockPos> floorPlatform(BlockPos front) {
        List<BlockPos> cells=new ArrayList<>();
        for(int dx=-1;dx<=1;dx++)for(int dz=0;dz<3;dz++)cells.add(front.add(dx,0,-dz));
        return cells;
    }

    private static void layPlatform(MemoryWorld world,List<BlockPos> cells,EnumFacing facing) {
        for(BlockPos cell:cells)world.setBlockState(cell,ModBlocks.TURNTABLE.getDefaultState()
                .withProperty(BlockTurntable.FACING,facing),2);
    }

    private static boolean isAir(MemoryWorld world,BlockPos pos) {
        IBlockState state=world.getBlockState(pos);
        return state.getBlock().isAir(state,world,pos);
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
        @Override public List<net.minecraft.entity.Entity> getEntitiesWithinAABBExcludingEntity(net.minecraft.entity.Entity entity,
                net.minecraft.util.math.AxisAlignedBB box){return Collections.emptyList();}
        @Override public boolean spawnEntity(net.minecraft.entity.Entity entity){loadedEntityList.add(entity);return true;}
        @Override public BlockPos getSpawnPoint(){return new BlockPos(0,64,0);}
        @Override protected net.minecraft.world.chunk.IChunkProvider createChunkProvider(){
            return new net.minecraft.world.chunk.IChunkProvider(){
                public net.minecraft.world.chunk.Chunk getLoadedChunk(int x,int z){
                    if(!isChunkLoaded(x,z,true))return null;
                    return new net.minecraft.world.chunk.Chunk(MemoryWorld.this,x,z){
                        @Override public Map<BlockPos,TileEntity> getTileEntityMap(){return tiles;}
                    };
                }
                public net.minecraft.world.chunk.Chunk provideChunk(int x,int z){return getLoadedChunk(x,z);}
                public boolean tick(){return false;}
                public String makeString(){return "MemoryWorld";}
                public boolean isChunkGeneratedAt(int x,int z){return isChunkLoaded(x,z,true);}
            };
        }
        @Override public net.minecraft.world.chunk.IChunkProvider getChunkProvider(){return createChunkProvider();}
        @Override protected boolean isChunkLoaded(int x,int z,boolean empty){return !chunkLimit || x==0&&z==0;}
        private void loaded(BlockPos p){if(!isBlockLoaded(p))throw new IllegalStateException("read unloaded chunk "+p);}
        @Override public IBlockState getBlockState(BlockPos p){stateReads++;loaded(p);return states.getOrDefault(p,Blocks.AIR.getDefaultState());}
        @Override public void removeTileEntity(BlockPos p){TileEntity tile=tiles.remove(p);if(tile!=null)tile.invalidate();}
        @Override public void playEvent(net.minecraft.entity.player.EntityPlayer player,int event,BlockPos p,int data){}
        @Override public TileEntity getTileEntity(BlockPos p){loaded(p);return tiles.get(p);}
        @Override public boolean setBlockState(BlockPos p,IBlockState state,int flags) {
            loaded(p);IBlockState before=states.put(p.toImmutable(),state);
            TileEntity previous=tiles.get(p);
            if(before!=null && previous!=null && !previous.shouldRefresh(this,p,before,state))return true;
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
