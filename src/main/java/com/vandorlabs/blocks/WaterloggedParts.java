package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import java.util.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Source water occupies only the empty half-cells of programmable slabs/stairs. */
@Mod.EventBusSubscriber(modid="vandorlabs")
public final class WaterloggedParts {
    private WaterloggedParts(){}
    public static boolean supports(IBlockState state){return state.getBlock() instanceof BlockProgrammableSlab || state.getBlock() instanceof BlockProgrammableStairs;}
    public static boolean wet(IBlockAccess world,BlockPos pos){TileEntity tile=world.getTileEntity(pos);return tile instanceof TileEntityAnimatedScreenSelector && ((TileEntityAnimatedScreenSelector)tile).isWaterlogged() && supports(world.getBlockState(pos));}
    public static List<AxisAlignedBB> gaps(IBlockAccess access,BlockPos pos){
        if(!(access instanceof World))return Collections.emptyList();World world=(World)access;
        IBlockState state=world.getBlockState(pos).getActualState(world,pos);List<AxisAlignedBB> solid=new ArrayList<>(),water=new ArrayList<>();
        state.addCollisionBoxToList(world,pos,new AxisAlignedBB(pos),solid,null,false);
        for(int x=0;x<2;x++)for(int y=0;y<2;y++)for(int z=0;z<2;z++){
            AxisAlignedBB cell=new AxisAlignedBB(x*.5,y*.5,z*.5,(x+1)*.5,(y+1)*.5,(z+1)*.5).offset(pos);boolean occupied=false;
            for(AxisAlignedBB box:solid)if(box.intersects(cell.shrink(.00001))){occupied=true;break;}
            if(!occupied)water.add(cell);
        }return water;
    }
    public static Boolean intersects(World world,BlockPos pos,AxisAlignedBB box,Material material){
        if(material!=Material.WATER || !wet(world,pos))return null;
        for(AxisAlignedBB gap:gaps(world,pos))if(gap.intersects(box))return true;return false;
    }
    public static Boolean entity(IBlockAccess world,BlockPos pos,Entity entity,double height,Material material,boolean head){
        if(material!=Material.WATER || !wet(world,pos))return null;
        for(AxisAlignedBB gap:gaps(world,pos))if(head?gap.contains(new Vec3d(entity.posX,height,entity.posZ)):gap.intersects(entity.getEntityBoundingBox()))return true;return false;
    }
    @SubscribeEvent public static void placed(net.minecraftforge.event.world.BlockEvent.PlaceEvent event){
        if(event.getWorld().isRemote || !supports(event.getPlacedBlock()) || event.getBlockSnapshot().getReplacedBlock().getMaterial()!=Material.WATER)return;
        TileEntity tile=event.getWorld().getTileEntity(event.getPos());if(tile instanceof TileEntityAnimatedScreenSelector)((TileEntityAnimatedScreenSelector)tile).setWaterlogged(true);
    }
    @SubscribeEvent public static void bucket(PlayerInteractEvent.RightClickBlock event){
        World world=event.getWorld();BlockPos pos=event.getPos();if(!supports(world.getBlockState(pos)))return;
        EntityPlayer player=event.getEntityPlayer();ItemStack held=player.getHeldItem(event.getHand());boolean fill=held.getItem()==Items.WATER_BUCKET,drain=held.getItem()==Items.BUCKET;
        if(!fill && !drain || fill==wet(world,pos))return;
        if(player.isSpectator() || !world.isBlockModifiable(player,pos) || !player.canPlayerEdit(pos,event.getFace(),held))return;
        event.setCanceled(true);event.setCancellationResult(EnumActionResult.SUCCESS);
        if(world.isRemote)return;
        TileEntity raw=world.getTileEntity(pos);if(!(raw instanceof TileEntityAnimatedScreenSelector))return;
        ((TileEntityAnimatedScreenSelector)raw).setWaterlogged(fill);
        if(!player.capabilities.isCreativeMode){
            held.shrink(1);ItemStack result=new ItemStack(fill?Items.BUCKET:Items.WATER_BUCKET);
            if(held.isEmpty())player.setHeldItem(event.getHand(),result);else if(!player.inventory.addItemStackToInventory(result))player.dropItem(result,false);
        }
        world.playSound(null,pos,fill?net.minecraft.init.SoundEvents.ITEM_BUCKET_EMPTY:net.minecraft.init.SoundEvents.ITEM_BUCKET_FILL,SoundCategory.BLOCKS,1,1);
    }
}
