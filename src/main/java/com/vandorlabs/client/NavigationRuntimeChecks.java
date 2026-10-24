package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.container.ContainerTokenSlot;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.navigation.*;
import com.vandorlabs.network.MessageNavigationTable;
import com.vandorlabs.redstone.*;
import com.vandorlabs.tiles.*;
import io.netty.buffer.*;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** Real loaded-world input gates, joined ownership, persistence and bounded wire format. */
final class NavigationRuntimeChecks {
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("Navigation display: "+why);}
    private static TileEntityNavigationTable table(WorldServer world,BlockPos pos){return (TileEntityNavigationTable)world.getTileEntity(pos);}
    static int artwork(){for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if(ScreenTextureList.artwork(i,false))return ScreenHousingTextures.choiceAt(i);throw new IllegalStateException("No screen artwork");}
    static void run(WorldServer world){
        BlockPos base=new BlockPos(128,110,128);for(int x=7;x<=9;x++)for(int z=7;z<=9;z++)world.getChunkFromChunkCoords(x,z);
        int cases=0,texture=artwork();
        for(String kind:new String[]{"flat","angled"})for(EnumFacing facing:EnumFacing.HORIZONTALS){
            BlockNavigationTable block=(BlockNavigationTable)Block.getBlockFromName("vandorlabs:navigation_table_"+kind);
            int depth=kind.equals("flat")?2:1;
            for(int x=0;x<2;x++)for(int z=0;z<depth;z++){
                Vec3d local=MachineMountSurface.rotate(new Vec3d(x+.5,0,z+.5),facing,2,depth);BlockPos at=base.add((int)Math.floor(local.x),0,(int)Math.floor(local.z));
                world.setBlockState(at,block.getDefaultState().withProperty(BlockVandorDirectional.FACING,z==0?facing:facing.getOpposite()),2);
            }
            NavigationAssemblies.rebuild(world,base);TileEntityNavigationTable root=table(world,base).reader();require(root!=null&&root.hasMount(),"mixed-facing rectangle forms");
            java.util.List<BlockPos> members=new java.util.ArrayList<>();for(BlockPos at:BlockPos.getAllInBox(base,base.add(1,0,1)))if(world.getTileEntity(at) instanceof TileEntityNavigationTable)members.add(at.toImmutable());
            BlockPos powered=members.get(members.size()-1);
            require(root.screenSettings().texture==-1,"new assembly defaults black");
            MachineMountSurface mount=root.mount();require(Math.abs(mount.width-6D/16)<1e-9&&mount.width==mount.height,"standalone mount proportions");
            double top=kind.equals("flat")?14D/16:8D/16;
            require(Math.abs(mount.center.y-root.getPos().getY()+mount.height/2-(top-.5/16))<1e-9,"half-pixel clearance below front top");
            for(int bits=0;bits<16;bits++){
                boolean r=(bits&1)!=0,s=(bits&2)!=0,p=(bits&4)!=0,c=(bits&8)!=0;
                root.configureDisplay(new NavigationScreenSettings(texture,true,1,r,s),false,1<<4,0,ChannelList.of(64521));
                root.setInventorySlotContents(0,c?new ItemStack(ModItems.DATA_CHIP,1,4):ItemStack.EMPTY);
                world.setBlockState(powered.down(),p?Blocks.REDSTONE_BLOCK.getDefaultState():Blocks.AIR.getDefaultState(),3);
                require(root.screenOn()==NavigationScreenPolicy.visible(r,s,p,c),"physical power/chip truth table "+kind+facing+bits);
                for(BlockPos at:members){TileEntityNavigationTable member=table(world,at);require(member.screenSettings().texture==texture&&member.reader()==root,"configuration and owner shared");
                    require(!block.canProvidePower(world.getBlockState(at))&&!member.hasLocalRedstoneSignal()&&member.localSignalLevel(64521)==0&&member.getOutputLevel()==0,"no virtual output");
                    for(EnumFacing side:EnumFacing.values())require(block.getWeakPower(world.getBlockState(at),world,at,side)==0&&block.getStrongPower(world.getBlockState(at),world,at,side)==0,"no physical output");
                }
                cases++;
            }
            world.setBlockToAir(powered.down());root.refreshInputs();root.removeStackFromSlot(0);root.setInventorySlotContents(0,new ItemStack(ModItems.DATA_CHIP,1,3));root.configureDisplay(new NavigationScreenSettings(texture,false,2,false,true),false,1<<4,0,ChannelList.of(64521));require(!root.screenOn(),"wrong company remains dark");
            root.setInventorySlotContents(0,new ItemStack(ModItems.DATA_CHIP,1,4));require(root.screenOn(),"matching chip reveals display without power");
            root.configureDisplay(new NavigationScreenSettings(texture,true,0,true,true),false,1<<4,0,ChannelList.of(64521));
            BlockPos sourcePos=base.add(6,0,0);world.setBlockState(sourcePos.south(),Blocks.STONE.getDefaultState(),2);world.setBlockState(sourcePos,Block.getBlockFromName("vandorlabs:industrial_power_lever").getDefaultState(),2);TileEntityRedstoneChannel source=(TileEntityRedstoneChannel)world.getTileEntity(sourcePos);source.setRedstoneChannels(ChannelList.of(64521));RedstoneChannels.register(source);RedstoneChannels.register(root);source.setLocalOn(true);
            require(root.screenOn(),"ordinary channel source powers secured display");source.setLocalOn(false);require(!root.screenOn(),"channel source off darkens display");world.setBlockToAir(sourcePos);
            NBTTagCompound saved=root.writeToNBT(new NBTTagCompound());TileEntityNavigationTable restored=new TileEntityNavigationTable();restored.readFromNBT(saved);require(restored.screenSettings().texture==texture&&restored.screenSettings().redstone&&restored.screenSettings().security&&restored.screenSettings().speed==0&&!restored.isEmpty(),"settings and real chip persist");require(restored.getOutputLevel()==0,"legacy saved reader output suppressed");
            carried(world,base,members);
            root.configureDisplay(new NavigationScreenSettings(texture,false,2,false,true),false,1<<4,0,ChannelList.of(64521));root.removeStackFromSlot(0);require(!root.screenOn(),"removing chip closes secured display");
            BlockPos survivor=members.get(members.size()-1);for(BlockPos at:members)if(!at.equals(survivor))world.setBlockToAir(at);
            NavigationAssemblies.rebuild(world,survivor);TileEntityNavigationTable split=table(world,survivor);
            require(split.hasMount()&&split.screenSettings().texture==texture&&split.screenSettings().security&&split.getRedstoneChannels().contains(64521)&&!split.screenOn(),"split retains artwork, security and channels without cloning a chip");
            for(BlockPos at:BlockPos.getAllInBox(base.add(-1,-1,-1),base.add(7,1,3)))world.setBlockToAir(at);
        }
        resizeEmpty(world,base,texture);wire(texture);
        System.out.println("[vandorlabs] navigation-display-inputs PASS cases="+cases+" mixed-facing native mount dimensions top clearance gates wrong/right chip physical/channel power zero outputs persistence bounded packets");
    }
    private static void resizeEmpty(WorldServer world,BlockPos base,int texture){
        BlockNavigationTable block=(BlockNavigationTable)Block.getBlockFromName("vandorlabs:navigation_table_flat");
        world.setBlockState(base.east(),block.getDefaultState(),2);NavigationAssemblies.rebuild(world,base.east());
        TileEntityNavigationTable configured=table(world,base.east());configured.configureDisplay(new NavigationScreenSettings(texture,false,2,false,true),false,16,0,ChannelList.of(64521));
        world.setBlockState(base,block.getDefaultState(),2);NavigationAssemblies.rebuild(world,base);
        TileEntityNavigationTable owner=table(world,base).reader();require(owner.getPos().equals(base)&&owner.screenSettings().texture==texture&&owner.screenSettings().security&&owner.getRedstoneChannels().contains(64521)&&owner.isEmpty(),"configured empty reader survives ownership change from one to two modules");
        world.setBlockToAir(base);world.setBlockToAir(base.east());
    }
    private static void carried(WorldServer world,BlockPos base,java.util.List<BlockPos> members){
        java.util.List<com.vandorlabs.vehicle.VehicleStructure.Cell> cells=new java.util.ArrayList<>();
        for(BlockPos at:members){BlockPos local=at.subtract(base);NBTTagCompound tag=table(world,at).writeToNBT(new NBTTagCompound());tag.setInteger("x",local.getX());tag.setInteger("y",local.getY());tag.setInteger("z",local.getZ());cells.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(local,world.getBlockState(at),tag));}
        com.vandorlabs.vehicle.VehicleStructure snapshot=com.vandorlabs.vehicle.VehicleStructure.display(cells);
        com.vandorlabs.vehicle.VehicleWorld view=new com.vandorlabs.vehicle.VehicleWorld(world,snapshot);
        for(int turns=0;turns<4;turns++){
            com.vandorlabs.vehicle.VehicleStructure rotated=com.vandorlabs.vehicle.VehicleRotation.rotate(snapshot,view,turns);
            com.vandorlabs.vehicle.VehicleWorld carried=new com.vandorlabs.vehicle.VehicleWorld(world,rotated);
            TileEntityNavigationTable root=null;
            for(net.minecraft.tileentity.TileEntity raw:carried.tiles.values()){TileEntityNavigationTable tile=(TileEntityNavigationTable)raw;if(tile.hasMount())root=tile;}
            require(root!=null&&!root.isEmpty(),"rotated snapshot retains the single physical chip");
            root.configureDisplay(new NavigationScreenSettings(root.screenSettings().texture,true,1,true,true),false,16,0,ChannelList.EMPTY);
            require(!root.screenOn(),"carried input starts off");
            TileEntityNavigationTable input=(TileEntityNavigationTable)carried.tiles.values().iterator().next();input.setChannelSignal(true);
            require(root.screenOn(),"server authority uses VehicleWorld parent");
            for(net.minecraft.tileentity.TileEntity raw:carried.tiles.values()){
                TileEntityNavigationTable tile=(TileEntityNavigationTable)raw;require(tile.reader()==root&&tile.screenOn(),"rotated owner/origin and member synchronization");
                NavigationLayout.DisplayPatch patch=NavigationLayout.displayPatch(((BlockNavigationTable)tile.getBlockType()).kind,NavigationLayout.Facing.valueOf(carried.getBlockState(tile.getPos()).getValue(BlockVandorDirectional.FACING).name()),new NavigationLayout.Cell(tile.getPos().getX(),tile.getPos().getY(),tile.getPos().getZ()),tile.connections(),NavigationLayout.Facing.valueOf(tile.displayFacing().name()),new NavigationLayout.Cell(tile.origin().getX(),tile.origin().getY(),tile.origin().getZ()),tile.assemblyWidth(),tile.assemblyDepth());
                for(int i=0;i<4;i++)require(patch.u[i]>=-1e-8&&patch.u[i]<=1+1e-8&&patch.v[i]>=-1e-8&&patch.v[i]<=1+1e-8,"rotated image bounds");
                require(tile.localSignalLevel(64521)==0,"carried display never publishes power");
            }
            input.setChannelSignal(false);require(!root.screenOn(),"carried channel off closes display");
        }
    }
    private static void wire(int texture){
        for(int count:new int[]{0,1,64}){
            int[] channels=new int[count];for(int i=0;i<count;i++)channels[i]=i+1;
            MessageNavigationTable original=new MessageNavigationTable(123,new NavigationScreenSettings(texture,true,2,true,true),16,0,ChannelList.of(channels));ByteBuf buffer=Unpooled.buffer();ByteBuf roundtrip=Unpooled.buffer();
            try{original.toBytes(buffer);byte[] before=new byte[buffer.readableBytes()];buffer.getBytes(0,before);MessageNavigationTable decoded=new MessageNavigationTable();decoded.fromBytes(buffer);decoded.toBytes(roundtrip);byte[] after=new byte[roundtrip.readableBytes()];roundtrip.readBytes(after);require(java.util.Arrays.equals(before,after),"packet exact round trip "+count);}finally{buffer.release();roundtrip.release();}
        }
        ByteBuf invalid=Unpooled.buffer();try{new MessageNavigationTable(0,NavigationScreenSettings.DEFAULT,0,0,ChannelList.EMPTY).toBytes(invalid);invalid.setByte(14,65);try{new MessageNavigationTable().fromBytes(invalid);throw new IllegalStateException("accepted unbounded channel count");}catch(IllegalArgumentException expected){}}finally{invalid.release();}
    }
}
