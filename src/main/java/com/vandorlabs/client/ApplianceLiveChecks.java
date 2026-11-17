package com.vandorlabs.client;

import com.vandorlabs.appliance.ApplianceCatalog;
import com.vandorlabs.items.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.io.File;
import java.util.*;

/** Actual creative enumeration, Survival packets, per-face state, slabs and authored item models. */
final class ApplianceLiveChecks {
    private static final BlockPos ROOT=new BlockPos(40,90,40);
    private static int stage,ticks,kind,checks;
    private static void need(boolean value,String why){checks++;if(!value)throw new IllegalStateException("Appliances stage="+stage+" kind="+kind+": "+why);}
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static void server(Minecraft mc,Runnable work)throws Exception{mc.getIntegratedServer().addScheduledTask(work).get();}
    private static ItemStack item(int index){return index<4?AppliancePresets.stack(index):new ItemStack(Block.getBlockFromName("vandorlabs:"+ApplianceCatalog.IDS[index]));}
    private static Block block(int index){return index<4?(ApplianceCatalog.slab(index)?ModBlocks.PROGRAMMABLE_SLAB:ModBlocks.PROGRAMMABLE_BLOCK):Block.getBlockFromName("vandorlabs:"+ApplianceCatalog.IDS[index]);}
    private static void placed(WorldServer world,BlockPos at,int index,EnumFacing facing,boolean upper){
        IBlockState state=world.getBlockState(at);need(state.getBlock()==block(index),"actual block identity "+ApplianceCatalog.IDS[index]);
        if(index<4){
            TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(at);need(tile!=null&&tile.getFaceTextures().enabled&&Arrays.equals(tile.getFaceTextures().choices(),AppliancePresets.faces(index)),"six saved local face materials");need(tile.getMainTextureLayout()==0,"full face artwork stretched once per face");need(state.getValue(BlockAnimatedScreenSelector.FACING)==facing,"player-facing material orientation");
            if(index<2){need(state.getValue(BlockProgrammableSlab.HALF)==(upper?BlockSlab.EnumBlockHalf.TOP:BlockSlab.EnumBlockHalf.BOTTOM),"top/bottom slab placement");AxisAlignedBB bounds=state.getBoundingBox(world,at);need(bounds.maxY-bounds.minY==.5,"actual half-height collision");}
            NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());int[] edited=tile.getFaceTextures().choices();edited[2]=edited[3];tile.setFaceTextures(new com.vandorlabs.tiles.FaceTextures(true,edited));need(tile.getFaceTextures().choice(2)==edited[3]&&world.getBlockState(at).getBlock()==block(index),"ordinary programmable face editing");tile.readFromNBT(saved);need(Arrays.equals(tile.getFaceTextures().choices(),AppliancePresets.faces(index)),"save round trip");net.minecraft.util.NonNullList<ItemStack> drops=net.minecraft.util.NonNullList.create();state.getBlock().getDrops(drops,world,at,state,0);need(drops.size()==1&&Block.getBlockFromItem(drops.get(0).getItem())==block(index),"ordinary programmable drop");need(AppliancePresets.matching(drops.get(0))==index,"mined appearance retains six faces and inventory preview");
        }else{need(world.getTileEntity(at) instanceof TileEntityChipAppliance,"standalone functional appliance tile");need(state.getValue(BlockVandorDirectional.FACING)==facing,"standalone facing");}
    }
    private static void matrix(EntityPlayerMP p){
        WorldServer world=p.getServerWorld();BlockPos root=ROOT.east(12);Vec3d old=p.getPositionVector();float yaw=p.rotationYaw;
        try{for(int index=0;index<6;index++)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean upper:index<2?new boolean[]{false,true}:new boolean[]{false}){
            for(BlockPos at:BlockPos.getAllInBox(root.add(-1,-1,-1),root.add(1,1,1)))world.setBlockToAir(at);
            p.rotationYaw=facing.getOpposite().getHorizontalAngle();p.setPosition(root.getX()+.5,root.getY()+2,root.getZ()-2);ItemStack stack=item(index);stack.setCount(2);p.setHeldItem(EnumHand.MAIN_HAND,stack);
            BlockPos support=root.add(0,upper?1:-1,0);world.setBlockState(support,Blocks.STONE.getDefaultState(),2);need(stack.onItemUse(p,world,support,EnumHand.MAIN_HAND,upper?EnumFacing.DOWN:EnumFacing.UP,.5F,upper?0:1,.5F)==EnumActionResult.SUCCESS,"native ItemBlock placement");need(stack.getCount()==1,"Survival consumes one");placed(world,root,index,facing,upper);
        }}finally{p.setPosition(old.x,old.y,old.z);p.rotationYaw=yaw;}
        for(int index=0;index<4;index++){need(!Block.REGISTRY.containsKey(new ResourceLocation("vandorlabs",ApplianceCatalog.IDS[index])),"no AC block registration");need(!net.minecraft.item.Item.REGISTRY.containsKey(new ResourceLocation("vandorlabs",ApplianceCatalog.IDS[index])),"no AC item registration");}
        System.out.println("[vandorlabs] appliances-placement PASS checks="+checks+" all facings top/bottom slabs standard registry identity per-face data drops persistence standalone decorations");
    }
    static boolean tick(Minecraft mc,File output)throws Exception{
        if(stage==4)return ChipApplianceLiveChecks.tick(mc,output);
        if(++ticks>700)throw new IllegalStateException("Appliance timeout stage="+stage+" kind="+kind+" mode="+mc.player.isCreative()+" held="+mc.player.getHeldItemMainhand());
        if(stage==0){mc.playerController.setGameType(GameType.SURVIVAL);mc.player.inventory.currentItem=0;mc.getConnection().sendPacket(new net.minecraft.network.play.client.CPacketHeldItemChange(0));server(mc,()->{EntityPlayerMP p=player(mc);p.setGameType(GameType.SURVIVAL);p.capabilities.allowFlying=true;p.capabilities.isFlying=true;p.sendPlayerAbilities();p.inventory.clear();p.inventory.currentItem=0;matrix(p);});stage=1;ticks=0;return false;}
        if(stage==1){server(mc,()->{EntityPlayerMP p=player(mc);for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-2,-1,-3),ROOT.add(2,2,2)))p.world.setBlockToAir(at);p.world.setBlockState(ROOT.down(),Blocks.STONE.getDefaultState(),2);ItemStack stack=item(kind);stack.setCount(2);p.setHeldItem(EnumHand.MAIN_HAND,stack);p.connection.sendPacket(new net.minecraft.network.play.server.SPacketHeldItemChange(0));p.inventoryContainer.detectAndSendChanges();p.connection.setPlayerLocation(ROOT.getX()+.5,ROOT.getY()+1,ROOT.getZ()-2,0,40);});stage=2;ticks=0;return false;}
        if(stage==2){if(ticks<20||mc.playerController.getCurrentGameType()!=GameType.SURVIVAL||mc.player.inventory.currentItem!=0||mc.player.getHeldItemMainhand().getCount()!=2||mc.player.getDistanceSq(ROOT.getX()+.5,ROOT.getY()+1,ROOT.getZ()-2)>.05)return false;mc.playerController.processRightClickBlock(mc.player,mc.world,ROOT.down(),EnumFacing.UP,new Vec3d(ROOT).addVector(.5,0,.5),EnumHand.MAIN_HAND);stage=3;ticks=0;return false;}
        if(stage==3){if(ticks<20||mc.world.getBlockState(ROOT).getBlock()!=block(kind))return false;server(mc,()->{EntityPlayerMP p=player(mc);need(p.getHeldItemMainhand().getCount()==1,"actual Survival packet consumes one");placed(p.getServerWorld(),ROOT,kind,EnumFacing.NORTH,false);});if(++kind<6){stage=1;ticks=0;return false;}models(mc,output);stage=4;System.out.println("[vandorlabs] appliances PASS checks="+checks+" creative names/identities actual Survival packets per-face model sprites padded icons and mutable programmable AC");return false;}
        return false;
    }
    private static void models(Minecraft mc,File output)throws Exception{
        for(int i=0;i<6;i++){
            ItemStack stack=item(i);need(stack.getDisplayName().equals(ApplianceCatalog.LABELS[i]),"localized creative entry "+stack.getDisplayName());
            net.minecraft.util.NonNullList<ItemStack> menu=net.minecraft.util.NonNullList.create();stack.getItem().getSubItems(com.vandorlabs.VandorLabs.VANDOR_LABS_TAB,menu);final int index=i;need(menu.stream().anyMatch(s->iMatch(s,index)),"creative variant enumerated");
            net.minecraft.client.renderer.block.model.IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(stack,mc.world,mc.player);Set<String> textures=new HashSet<>();for(int f=-1;f<6;f++)for(net.minecraft.client.renderer.block.model.BakedQuad quad:model.getQuads(null,f<0?null:EnumFacing.getFront(f),0))textures.add(quad.getSprite().getIconName());for(int f=0;f<6;f++)need(textures.contains("vandorlabs:blocks/"+ApplianceCatalog.texture(i,f)),"authored inventory face "+i+" "+f+" "+textures);
        }
        need(new ItemStack(Block.getBlockFromName("vandorlabs:standalone_dish_core")).getDisplayName().equals("Survey Dish Core"),"plain Survey Dish Core name");need(new ItemStack(Block.getBlockFromName("vandorlabs:standalone_antenna_core")).getDisplayName().equals("Sentinel Array Core"),"plain Sentinel Array Core name");
        ApplianceVisualChecks.run(mc,output);
    }
    private static boolean iMatch(ItemStack stack,int index){return index<4?AppliancePresets.matching(stack)==index:stack.getItem()==net.minecraft.item.Item.getItemFromBlock(block(index));}
}
