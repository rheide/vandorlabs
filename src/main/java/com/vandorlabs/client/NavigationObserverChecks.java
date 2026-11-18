package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.navigation.*;
import com.vandorlabs.persistence.*;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Real formation, saved-data and reservation teardown checks for ceiling-mounted observers. */
final class NavigationObserverChecks {
    private static void need(boolean ok,String why){if(!ok)throw new IllegalStateException("Navigation observers: "+why);}
    static void visual(net.minecraft.client.Minecraft mc){
        BlockPos at=new BlockPos(166,90,166);net.minecraft.block.state.IBlockState before=mc.world.getBlockState(at);
        try{for(String id:new String[]{"standalone_dish","standalone_antenna"})for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int width:id.equals("standalone_dish")?new int[]{3,4,5,6,16}:new int[]{2,3,4,8})for(boolean inverted:new boolean[]{false,true}){
            BlockConstructorCore block=(BlockConstructorCore)Block.getBlockFromName("vandorlabs:"+id+"_core");mc.world.setBlockState(at,block.getDefaultState().withProperty(BlockVandorDirectional.FACING,facing).withProperty(BlockConstructorCore.FORMED,true),2);TileEntityConstructor tile=(TileEntityConstructor)mc.world.getTileEntity(at);NBTTagCompound tag=tile.writeToNBT(new NBTTagCompound());tag.setInteger(ObserverScale.WIDTH_TAG,width);tag.setBoolean("ObserverInverted",inverted);tag.setBoolean("NavigationScreenOn",false);tile.readFromNBT(tag);
            net.minecraft.client.renderer.BufferBuilder buffer=new net.minecraft.client.renderer.BufferBuilder(65536);buffer.begin(7,net.minecraft.client.renderer.vertex.DefaultVertexFormats.BLOCK);new com.vandorlabs.client.constructors.TEConstructor().renderTileEntityFast(tile,0,0,0,0,-1,1,buffer);buffer.finishDrawing();need(buffer.getVertexCount()>0,"actual inverted renderer emits "+id);
            java.nio.ByteBuffer bytes=buffer.getByteBuffer();int stride=net.minecraft.client.renderer.vertex.DefaultVertexFormats.BLOCK.getNextOffset();double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY,minX=min,maxX=max,minZ=min,maxZ=max;BlockPos origin=tile.renderOriginOffset();for(int v=0;v<buffer.getVertexCount();v++){float x=bytes.getFloat(v*stride),y=bytes.getFloat(v*stride+4),z=bytes.getFloat(v*stride+8);minX=Math.min(minX,x);maxX=Math.max(maxX,x);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);need(x>=origin.getX()-.001&&x<=origin.getX()+width+.001&&z>=origin.getZ()-.001&&z<=origin.getZ()+width+.001,"actual mesh stays inside horizontal reservations");min=Math.min(min,y);max=Math.max(max,y);need(y>=(inverted?1-tile.structureHeight():0)-.001&&y<=(inverted?1:tile.structureHeight())+.001,"inverted emitted vertex stays below top core");}need(inverted?min<-.25&&max<=1.001:min>=-.001&&max>.25,"actual body extends from core "+id);need(maxX-minX>=width*.93&&maxZ-minZ>=width*.93,"model fills horizontal footprint "+id);
        }}finally{mc.world.setBlockState(at,before,2);}
        ConstructorRenderLifetimeChecks.run(mc);
        System.out.println("[vandorlabs] navigation-observers-render PASS actual TESR scaled chipless observers all facings upright/inverted actual vertex bounds");
    }
    static void run(WorldServer world){
        for(BlockPos at:BlockPos.getAllInBox(new BlockPos(150,74,150),new BlockPos(182,106,182)))world.setBlockToAir(at);
        for(String id:new String[]{"standalone_dish","standalone_antenna"})for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})for(int width:id.equals("standalone_dish")?new int[]{3,4,5,6,16}:new int[]{2,3,4,8}){
            BlockConstructorCore block=(BlockConstructorCore)Block.getBlockFromName("vandorlabs:"+id+"_core");BlockPos root=new BlockPos(166,90,166);

            world.setBlockState(root,block.getDefaultState().withProperty(BlockVandorDirectional.FACING,facing),2);TileEntityConstructor tile=(TileEntityConstructor)world.getTileEntity(root);
            Block matter=Block.getBlockFromName("vandorlabs:programmable_matter_block");
            int height=ObserverScale.height(id,width);com.vandorlabs.machines.CoreLayout shape=new com.vandorlabs.machines.CoreLayout(width,height,width,facing.getIndex(),0,0);
            for(int x=0;x<width;x++)for(int y=0;y<height;y++)for(int z=0;z<width;z++)if(shape.frame(x,y,z)){
                BlockPos at=root.add(shape.x(x,z),inverted?-y:y,shape.z(x,z));if(!at.equals(root))world.setBlockState(at,matter.getDefaultState(),2);
            }
            need(tile.assemble(),id+facing+inverted+" forms "+tile.assemblyProblem());need(tile.inverted()==inverted,"detect core-at-top orientation");need(tile.structureWidth()==width&&tile.structureHeight()==height&&tile.layout().cells()==width*width*height,"detected proportional size");need(tile.mount()==null&&!tile.accepts(new net.minecraft.item.ItemStack(com.vandorlabs.items.ModItems.DATA_CHIP)),"no mount and no chip insertion");
            com.vandorlabs.redstone.TransmitterSlots.configure(tile,com.vandorlabs.redstone.ChannelList.of(51),16,false);need(tile.getRedstoneChannels().isEmpty(),"transmitter does not replace receiving selection");tile.setRedstoneChannels(com.vandorlabs.redstone.ChannelList.of(52));com.vandorlabs.redstone.TransmitterSlots.configure(tile,com.vandorlabs.redstone.ChannelList.of(53),32,false);need(tile.getRedstoneChannels().equals(com.vandorlabs.redstone.ChannelList.of(52)),"receiving and transmitting remain independent");
            NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());tile.readFromNBT(saved);need(tile.inverted()==inverted&&tile.structureWidth()==width,"orientation/size round trip");need(saved.hasKey(ObserverScale.WIDTH_TAG)==(width!=block.width),"legacy base footprint omits new tag");
            NBTTagCompound invalid=saved.copy();invalid.setInteger(ObserverScale.WIDTH_TAG,17);tile.readFromNBT(invalid);need(!tile.formed()&&!tile.assemble()&&tile.writeToNBT(new NBTTagCompound()).equals(invalid),"invalid size inert and original snapshot preserved");tile.readFromNBT(saved);com.vandorlabs.gear.NavigationArmChecks.snapshot(tile);
            for(int y:new int[]{64,96,128,200})need(NavigationRange.radius(id.equals("standalone_dish")?NavigationRange.SURVEY_BASE:NavigationRange.SENTINEL_BASE,y,tile.observerScale())==Math.round((id.equals("standalone_dish")?NavigationRange.SURVEY_BASE:NavigationRange.SENTINEL_BASE)*(Math.min(1,y/128D)+Math.min(1,tile.observerScale()-1)*Math.max(0,Math.min(1,(y-64)/64D)))),"range endpoints and size cap");
            for(int x=0;x<width;x++)for(int y=0;y<height;y++)for(int z=0;z<width;z++){BlockPos at=tile.cell(x,y,z);need(at.getY()==root.getY()+(inverted?-y:y),"reservation vertical coordinates");need(world.getTileEntity(at) instanceof TileEntityConstructorPart||at.equals(root),"whole footprint reserved");}
            if(width==ObserverScale.maximumWidth(id)){
                net.minecraft.entity.player.EntityPlayer actor=world.playerEntities.get(0);net.minecraft.util.math.Vec3d before=actor.getPositionVector();BlockPos far=tile.cell(width-1,height-1,width-1);
                try{actor.setPosition(far.getX()+.5,far.getY()+1,far.getZ()+.5);need(actor.getDistanceSq(root)>64,"far member is beyond original core reach");need(com.vandorlabs.items.MovingDeviceConfiguration.nearOwnedPart(actor,tile)&&com.vandorlabs.items.ConfigurationAccess.canKeepDialogOpen(actor,world,root),"dialog authorized beside actual distant owned reservation");actor.setPosition(far.getX()+40,far.getY()+1,far.getZ()+40);need(!com.vandorlabs.items.MovingDeviceConfiguration.nearOwnedPart(actor,tile)&&!com.vandorlabs.items.ConfigurationAccess.canKeepDialogOpen(actor,world,root),"remote actor outside entire footprint refused");}
                finally{actor.setPosition(before.x,before.y,before.z);}
            }
            int radius=NavigationSources.radius(tile);need(radius==NavigationRange.radius(id.equals("standalone_dish")?NavigationRange.SURVEY_BASE:NavigationRange.SENTINEL_BASE,90,tile.observerScale()),"height-dependent live radius");
            tile.dismantle();for(int x=0;x<width;x++)for(int y=0;y<height;y++)for(int z=0;z<width;z++){BlockPos at=tile.cell(x,y,z);if(!at.equals(root))need(world.getBlockState(at).getBlock()==(tile.frame(x,y,z)?matter:net.minecraft.init.Blocks.AIR),"exact frame restored");}
            for(int x=0;x<width;x++)for(int y=0;y<height;y++)for(int z=0;z<width;z++){BlockPos at=tile.cell(x,y,z);if(!at.equals(root))world.setBlockToAir(at);}world.setBlockToAir(root);
        }
        // A wider Array must also grow taller; a visually similar short frame cannot form.
        {BlockPos root=new BlockPos(166,90,166);BlockConstructorCore block=(BlockConstructorCore)Block.getBlockFromName("vandorlabs:standalone_antenna_core");world.setBlockState(root,block.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.NORTH),2);TileEntityConstructor tile=(TileEntityConstructor)world.getTileEntity(root);com.vandorlabs.machines.CoreLayout wrong=new com.vandorlabs.machines.CoreLayout(3,5,3,EnumFacing.NORTH.getIndex(),0,0);Block matter=Block.getBlockFromName("vandorlabs:programmable_matter_block");for(int x=0;x<3;x++)for(int y=0;y<5;y++)for(int z=0;z<3;z++)if(wrong.frame(x,y,z)&&!(x==0&&y==0&&z==0))world.setBlockState(root.add(x,y,z),matter.getDefaultState(),2);need(!tile.assemble(),"nonproportional Array refused");for(int x=0;x<3;x++)for(int y=0;y<5;y++)for(int z=0;z<3;z++)if(!(x==0&&y==0&&z==0))world.setBlockToAir(root.add(x,y,z));world.setBlockToAir(root);}
        for(String old:new String[]{"survey_dish","sentinel_array"}){
            String name="vandorlabs:"+old+"_core",replacement=RenamedBlocks.canonical(name);need(!name.equals(replacement),"retired ID alias");
            need(LegacyBlockStates.decode(name+"[facing=north,formed=true]").getBlock()==Block.getBlockFromName(replacement),"previous block-state codec remap");
            NBTTagCompound item=new NBTTagCompound();item.setString("id",name);item.setByte("Count",(byte)1);RenamedBlocks.migrateItems(item);need(item.getString("id").equals(replacement),"old inventory item migration");
            NBTTagCompound state=new NBTTagCompound();state.setString("Name",name);need(RenamedBlocks.stateTag(state).getString("Name").equals(replacement)&&state.getString("Name").equals(name),"snapshot migration retains original data");
        }
        System.out.println("[vandorlabs] navigation-observers PASS formations=72 proportional widths through max upright/inverted all facings saved/yaw snapshots distant owned-part access exact teardown chipless oldIDs/items/states");
    }
    private NavigationObserverChecks(){}
}
