package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.redstone.*;
import com.vandorlabs.render.ScreenSurface;
import com.vandorlabs.tiles.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import java.util.*;

final class RedstoneScreenChecks {
    static void run(){
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos pos=new BlockPos(0,100,0);
        world.setBlockState(pos,ModBlocks.ANIMATED_SCREEN_SELECTOR.getDefaultState(),2);
        RedstoneScreenContents screen=((TileEntityAnimatedScreenSelector)world.getTileEntity(pos)).redstoneScreen(0);
        require(screen.configureRows(Arrays.asList("Doors","Lights","Unlinked"),Arrays.asList(ChannelList.of(41,42),ChannelList.of(42),ChannelList.EMPTY),0),"valid rows rejected");
        require(!screen.rows().get(0).active(),"new rows active");
        screen.toggleRow(0);require(RedstoneChannels.allPowered(world,ChannelList.of(41,42)),"row did not power all channels");
        require(screen.rows().get(0).active() && screen.rows().get(1).active(),"overlapping rows did not highlight");
        Receiver receiver=new Receiver();receiver.setWorld(world);receiver.setPos(pos.east());RedstoneChannels.register(receiver);int edges=receiver.edges;
        screen.configureRows(Arrays.asList("Door bank","Lights","Unlinked"),Arrays.asList(ChannelList.of(41,42),ChannelList.of(42),ChannelList.EMPTY),0);
        require(receiver.edges==edges && receiver.powered,"editing a label interrupted a powered channel");
        screen.toggleRow(1);require(RedstoneChannels.allPowered(world,ChannelList.of(41)) && !RedstoneChannels.allPowered(world,ChannelList.of(42)),"overlap bridged independent channels");
        require(!screen.rows().get(0).active() && !screen.rows().get(1).active(),"mixed channels highlighted active");
        NBTTagCompound saved=screen.writeToNBT(new NBTTagCompound());
        RedstoneScreenContents restored=new RedstoneScreenContents(new TileEntityAnimatedScreenSelector(),0);restored.readFromNBT(saved);
        require(restored.rows().size()==3 && restored.rows().get(0).channels.equals(ChannelList.of(41,42)),"rows lost in NBT");
        require(restored.rows().get(0).latchedChannels().equals(ChannelList.of(41)),"partial latch lost in NBT");
        screen.toggleRow(0);require(screen.rows().get(0).active(),"partial click did not activate every channel");
        screen.toggleRow(0);require(!RedstoneChannels.allPowered(world,ChannelList.of(41)),"row did not turn off");
        require(!screen.configureRows(Arrays.asList("Bad\nlabel"),Arrays.asList(ChannelList.of(1)),0),"control label accepted");
        screen.toggleRow(0);screen.configureRows(Collections.emptyList(),Collections.emptyList(),0);
        require(!RedstoneChannels.allPowered(world,ChannelList.of(41)),"removed row retained a power source");
        screen.configure("Bridge",Arrays.asList("Saved","Auxiliary"),Arrays.asList(ChannelList.of(41,43),ChannelList.of(45,46)),0);screen.toggleRow(0);screen.onChunkUnload();
        require(!RedstoneChannels.allPowered(world,ChannelList.of(41)),"unloaded screen retained a power source");
        BlockPos target=pos.east(3);world.setBlockState(target,ModBlocks.PROGRAMMABLE_DIAGONAL_SCREEN.getDefaultState(),2);
        RedstoneScreenContents copy=((TileEntityAnimatedScreenSelector)world.getTileEntity(target)).redstoneScreen(0);
        require(com.vandorlabs.items.ProgrammableSettings.apply(world,target,com.vandorlabs.items.ProgrammableSettings.capture(world,pos)),"Duplifier rows not applicable");
        require(copy.title().equals("Bridge") && copy.rows().size()==2 && copy.rows().get(0).label.equals("Saved") && copy.rows().get(0).channels.equals(ChannelList.of(41,43)) && copy.rows().get(1).channels.equals(ChannelList.of(45,46)),"Duplifier lost screen row configuration");
        copyMasks(world,pos,target);updates();projection();integratedSurfaces();diagonalSelection();packets();
        System.out.println("PASS: redstone-screen row toggles, ALL highlight, independent overlap, NBT, removal/unload and all 14 mounting projections");
    }
    private static void copyMasks(NonRenderingChecks.MemoryWorld world,BlockPos source,BlockPos target){
        net.minecraft.item.ItemStack tool=new net.minecraft.item.ItemStack(com.vandorlabs.items.ModItems.DUPLIFIER);
        long rowBit=1L<<(com.vandorlabs.items.DuplifierApplyOptions.OPTIONS.length-1);
        net.minecraft.nbt.NBTTagCompound root=new net.minecraft.nbt.NBTTagCompound();root.setLong(com.vandorlabs.items.DuplifierApplyOptions.TAG,0);tool.setTagCompound(root);
        require(com.vandorlabs.items.DuplifierApplyOptions.mask(tool)==rowBit,"old mask did not enable only new screen option");
        require(com.vandorlabs.items.ItemDuplifier.copyFrom(world,source,tool)!=null,"actual tool did not capture screen");
        require(com.vandorlabs.items.ItemDuplifier.applyTo(world,target,tool,null),"actual tool did not apply old-mask screen");
        RedstoneScreenContents tile=((TileEntityAnimatedScreenSelector)world.getTileEntity(target)).redstoneScreen(0);
        require(tile.title().equals("Bridge") && tile.rowConfiguration().equals((((TileEntityAnimatedScreenSelector)world.getTileEntity(source)).redstoneScreen(0)).rowConfiguration()),"actual tool lost title or rows");
        com.vandorlabs.items.DuplifierApplyOptions.setMask(tool,com.vandorlabs.items.DuplifierApplyOptions.ALL & ~rowBit);
        require((com.vandorlabs.items.DuplifierApplyOptions.mask(tool)&rowBit)==0,"deliberate screen exclusion lost");
        net.minecraft.nbt.NBTTagCompound filtered=com.vandorlabs.items.DuplifierApplyOptions.selected(com.vandorlabs.items.ProgrammableSettings.capture(world,source),com.vandorlabs.items.DuplifierApplyOptions.mask(tool));
        require(!filtered.hasKey(com.vandorlabs.items.ProgrammableSettings.REDSTONE_ROWS),"screen exclusion retained title or rows");
        RedstoneScreenContents restored=new RedstoneScreenContents(new TileEntityAnimatedScreenSelector(),0);restored.readFromNBT(tile.writeToNBT(new NBTTagCompound()));require(restored.title().equals("Bridge"),"title lost in NBT");
        require(!tile.configure("Bad\nheader",Collections.emptyList(),Collections.emptyList(),0) && tile.title().equals("Bridge"),"invalid header accepted or changed configuration");
    }
    private static void updates(){
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos pos=new BlockPos(0,100,0);
        world.setBlockState(pos,ModBlocks.ANIMATED_SCREEN_SELECTOR.getDefaultState(),2);
        RedstoneScreenContents screen=((TileEntityAnimatedScreenSelector)world.getTileEntity(pos)).redstoneScreen(0);
        screen.configureRows(Arrays.asList("Both","Second"),Arrays.asList(ChannelList.of(41,42),ChannelList.of(42)),0);
        PhysicalSource source=new PhysicalSource();source.setWorld(world);source.setPos(pos.east());RedstoneChannels.register(source);
        require(screen.rows().get(0).active() && screen.rows().get(1).active(),"physical source not highlighted");
        int before=world.updates,dirty=world.dirty;
        RedstoneChannels.latchChanged(screen.rows().get(0),true);
        require(world.updates==before && world.dirty>dirty,"unchanged highlight sent packet or failed to save latch");
        require(screen.rows().get(0).latchedChannels().equals(ChannelList.of(41,42)),"hidden latch state lost");
        source.on=false;RedstoneChannels.inputChanged(source);
        require(screen.rows().get(0).active() && world.updates==before,"physical release dropped latched power");
        screen.toggleRow(0);
        require(!screen.rows().get(0).active() && !screen.rows().get(1).active() && world.updates==before+1,"row status updates were not coalesced");
    }
    private static final class PhysicalSource extends TileEntity implements RedstoneChannelMember {
        boolean on=true;
        public TileEntity channelTile(){return this;}
        public int getRedstoneChannel(){return 41;}
        public ChannelList getRedstoneChannels(){return ChannelList.of(41,42);}
        public void setRedstoneChannel(int channel){}
        public boolean hasLocalRedstoneSignal(){return on;}
        public void setChannelSignal(boolean value){}
    }
    static Vec3d world(Vec3d point,EnumFacing facing){
        double x=point.x/16-.5,y=point.y/16-.5,z=point.z/16-.5,wx=x,wy=y,wz=z;
        switch(facing){case EAST:wx=-z;wz=x;break;case SOUTH:wx=-x;wz=-z;break;case WEST:wx=z;wz=-x;break;
            case UP:wx=-x;wy=-z;wz=-y;break;case DOWN:wy=z;wz=-y;break;default:break;}
        return new Vec3d(wx+.5,wy+.5,wz+.5);
    }
    private static void projection(){
        for(boolean diagonal:new boolean[]{false,true})for(EnumFacing facing:EnumFacing.values())for(boolean inverted:new boolean[]{false,true}){
            if(!diagonal && inverted || diagonal && facing.getAxis()==EnumFacing.Axis.Y)continue;
            IBlockState state=diagonal?ModBlocks.PROGRAMMABLE_DIAGONAL_SCREEN.getDefaultState().withProperty(BlockProgrammableDiagonalScreen.FACING,facing).withProperty(BlockProgrammableDiagonalScreen.INVERTED,inverted):ModBlocks.ANIMATED_SCREEN_SELECTOR.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,facing);
            ScreenSurface.Quad q=RedstoneScreenInteractions.surface(state);
            for(int row=0;row<8;row++)for(int test=0;test<6;test++){
                double u=test==1?50:test==4?5:test==5?123:109,v=24+12*row+(test==2?11:5);
                Vec3d center=new Vec3d(q.topRight.x+(q.topLeft.x-q.topRight.x)*u/128,q.topRight.y+(q.bottomRight.y-q.topRight.y)*v/128,q.topRight.z+(q.bottomRight.z-q.topRight.z)*v/128);
                Vec3d start=world(center.addVector(0,q.ny*16,q.nz*16),facing),end=world(center.addVector(0,-q.ny*16,-q.nz*16),facing);
                int actual=RedstoneScreenInteractions.hitRow(state,test==3?end:start,test==3?start:end,8);
                require(actual==(test<2?row:-1),"row hit projection differs: "+facing+" diagonal="+diagonal+" inverted="+inverted+" test="+test+" row="+row);
            }
        }
    }
    private static void integratedSurfaces(){
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos pos=new BlockPos(0,100,0);
        net.minecraft.block.Block halfDiagonal=new BlockDiagonalHalfConsole("programmable_diagonal_half_console_check");
        for(net.minecraft.block.Block block:new net.minecraft.block.Block[]{ModBlocks.PROGRAMMABLE_FULL_INPUT,ModBlocks.PROGRAMMABLE_INPUT,ModBlocks.PROGRAMMABLE_CONSOLE,ModBlocks.PROGRAMMABLE_HALF_CONSOLE,halfDiagonal}){
            for(IBlockState state:block.getBlockState().getValidStates())for(int small=0;small<2;small++)for(int ceiling=-1;ceiling<3;ceiling++){
                world.setBlockState(pos,state,2);TileEntityAnimatedScreenSelector owner=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);owner.setSmallInput(small!=0);owner.setCeilingMounted(ceiling>=0);if(ceiling>=0)owner.setCeilingPosition(ceiling);
                for(int slot=0;slot<2;slot++)if(RedstoneScreenInteractions.supportsSlot(block,slot)){
                    ScreenSurface.Quad q=RedstoneScreenInteractions.surface(state,owner,slot);int top=RedstoneScreenInteractions.rowTop(block,slot);
                    for(int row=0;row<8;row++){
                        double u=30/128D,v=(top+row*12+5)/128D;Vec3d center=new Vec3d(q.topRight.x+(q.topLeft.x-q.topRight.x)*u,q.topRight.y+(q.bottomRight.y-q.topRight.y)*v,q.topRight.z+(q.bottomRight.z-q.topRight.z)*v);
                        EnumFacing face=RedstoneScreenInteractions.facing(state);Vec3d start=world(center.addVector(0,q.ny*16,q.nz*16),face),end=world(center.addVector(0,-q.ny*16,-q.nz*16),face);
                        require(RedstoneScreenInteractions.hitRow(state,owner,slot,start,end,8)==row,"integrated row plane disagrees: "+block+" "+state+" slot="+slot);
                    }
                }
            }
        }
        world.setBlockState(pos,ModBlocks.PROGRAMMABLE_CONSOLE.getDefaultState(),2);TileEntityAnimatedScreenSelector owner=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);
        owner.redstoneScreen(0).configure("Screen",Arrays.asList("Door"),Arrays.asList(ChannelList.of(51)),0);
        owner.redstoneScreen(1).configure("Deck",Arrays.asList("Light"),Arrays.asList(ChannelList.of(52)),0);
        owner.redstoneScreen(0).toggleRow(0);owner.redstoneScreen(1).toggleRow(0);
        owner.setSurfaceTexture(0,-1);require(!RedstoneChannels.allPowered(world,ChannelList.of(51)) && RedstoneChannels.allPowered(world,ChannelList.of(52)),"switching artwork retained hidden sources or disabled the other surface");
        owner.setSurfaceTexture(0,TileEntityAnimatedScreenSelector.REDSTONE_SURFACE);require(owner.redstoneScreen(0).rows().get(0).label.equals("Door"),"switching artwork discarded saved rows");
        TileEntityAnimatedScreenSelector restored=new TileEntityAnimatedScreenSelector();restored.readFromNBT(owner.writeToNBT(new NBTTagCompound()));require(restored.hasRedstoneScreen(0) && restored.hasRedstoneScreen(1) && restored.redstoneScreen(1).title().equals("Deck"),"independent surfaces lost in NBT");
        owner.onChunkUnload();require(!RedstoneChannels.allPowered(world,ChannelList.of(51,52)),"integrated surfaces retained unloaded power");
    }
    private static void diagonalSelection(){
        for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean upper:new boolean[]{false,true}){
            DiagonalScreenShape shape=DiagonalScreenShape.of(facing,upper);double empty=upper?.2:.8,solid=upper?.95:.05;
            require(shape.trace(BlockPos.ORIGIN,DiagonalScreenShape.world(-.25,empty,.1,facing),DiagonalScreenShape.world(1.25,empty,.1,facing))==null,"empty diagonal space blocked picking");
            require(shape.trace(BlockPos.ORIGIN,DiagonalScreenShape.world(-.25,solid,.8,facing),DiagonalScreenShape.world(1.25,solid,.8,facing))!=null,"solid diagonal housing missed picking");
            RayTraceResult slope=shape.trace(BlockPos.ORIGIN,DiagonalScreenShape.world(.5,upper?.2:.8,.2,facing),DiagonalScreenShape.world(.5,upper?.8:.2,.8,facing));
            require(slope!=null && slope.hitVec.squareDistanceTo(new Vec3d(.5,.5,.5))<1e-12,"diagonal pick did not intersect rendered slope");
            AxisAlignedBB pocket=PanelPlacement.rotateFromNorth(new AxisAlignedBB(.2,upper?.1:.7,.05,.8,upper?.3:.9,.15),facing);
            for(AxisAlignedBB box:shape.collision)require(!box.intersects(pocket),"empty diagonal space has cube collision");
        }
    }
    private static void packets(){
        com.vandorlabs.network.MessageRedstoneScreen packet=new com.vandorlabs.network.MessageRedstoneScreen(new BlockPos(1,2,3),Arrays.asList("Doors","Lights"),Arrays.asList(ChannelList.of(3,9),ChannelList.of(9)),0);
        io.netty.buffer.ByteBuf buffer=io.netty.buffer.Unpooled.buffer();packet.toBytes(buffer);
        try{
            java.lang.reflect.Field valid=packet.getClass().getDeclaredField("valid");valid.setAccessible(true);
            for(int mode=0;mode<4;mode++){
                io.netty.buffer.ByteBuf input=buffer.copy();if(mode==1)input.setByte(12,9);if(mode==2)input.writerIndex(input.writerIndex()-1);if(mode==3)input.writeByte(0);
                com.vandorlabs.network.MessageRedstoneScreen decoded=new com.vandorlabs.network.MessageRedstoneScreen();decoded.fromBytes(input);input.release();
                require(valid.getBoolean(decoded)==(mode==0),"screen packet bound/rejection differs mode="+mode);
                if(mode==0){io.netty.buffer.ByteBuf encoded=io.netty.buffer.Unpooled.buffer();decoded.toBytes(encoded);require(io.netty.buffer.ByteBufUtil.equals(buffer,encoded),"row packet changed contents");encoded.release();}
            }
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}finally{buffer.release();}
    }
    private static final class Receiver extends TileEntity implements RedstoneChannelMember {
        int edges;boolean powered;
        public TileEntity channelTile(){return this;}
        public int getRedstoneChannel(){return 41;}
        public void setRedstoneChannel(int channel){}
        public boolean hasLocalRedstoneSignal(){return false;}
        public void setChannelSignal(boolean value){if(powered!=value){edges++;powered=value;}}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
