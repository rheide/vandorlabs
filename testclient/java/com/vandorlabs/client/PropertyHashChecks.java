package com.vandorlabs.client;

import com.vandorlabs.blocks.CachedProperties;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.properties.*;
import net.minecraft.block.state.*;
import net.minecraft.init.Blocks;
import net.minecraft.util.*;
import java.util.*;

/** Property hashing must remain compatible with vanilla reads, transitions and serialization. */
final class PropertyHashChecks {
    static void run() {
        check(PropertyDirection.create("facing"),CachedProperties.direction("facing"));
        check(PropertyDirection.create("facing",EnumFacing.Plane.HORIZONTAL),CachedProperties.direction("facing",EnumFacing.Plane.HORIZONTAL));
        check(PropertyEnum.create("facing",EnumFacing.class),CachedProperties.enumeration("facing",EnumFacing.class));
        check(PropertyEnum.create("half",BlockDoor.EnumDoorHalf.class),CachedProperties.enumeration("half",BlockDoor.EnumDoorHalf.class));
        check(PropertyEnum.create("hinge",BlockDoor.EnumHingePosition.class),CachedProperties.enumeration("hinge",BlockDoor.EnumHingePosition.class));
        check(PropertyEnum.create("half",BlockSlab.EnumBlockHalf.class),CachedProperties.enumeration("half",BlockSlab.EnumBlockHalf.class));
        List<EnumFacing> values=new ArrayList<>(Arrays.asList(EnumFacing.NORTH,EnumFacing.SOUTH));
        PropertyEnum<EnumFacing> copied=CachedProperties.enumeration("axis_faces",EnumFacing.class,values);int hash=copied.hashCode();
        values.clear();require(copied.getAllowedValues().size()==2 && copied.hashCode()==hash,"caller changed cached property values");
        require(!CachedProperties.direction("facing").equals(CachedProperties.direction("facing",EnumFacing.Plane.HORIZONTAL)),"different allowed facings compare equal");
        System.out.println("PASS: cached enum hashes preserve vanilla equality, lookups, transitions, serialized names and copied values");
    }
    private static <T extends Comparable<T>> void check(IProperty<T> vanilla,IProperty<T> cached) {
        require(vanilla.equals(cached) && cached.equals(vanilla),"property equality differs");
        require(vanilla.hashCode()==cached.hashCode(),"property hash differs");
        require(vanilla.getName().equals(cached.getName()) && vanilla.getValueClass()==cached.getValueClass(),"property schema changed");
        BlockStateContainer oldStates=new BlockStateContainer(Blocks.STONE,vanilla),newStates=new BlockStateContainer(Blocks.STONE,cached);
        require(oldStates.getValidStates().size()==newStates.getValidStates().size(),"state count differs");
        for(T value:vanilla.getAllowedValues()) {
            String name=vanilla.getName(value);
            require(name.equals(cached.getName(value)) && vanilla.parseValue(name).equals(cached.parseValue(name)),"serialized value differs");
            IBlockState oldState=oldStates.getBaseState().withProperty(vanilla,value),newState=newStates.getBaseState().withProperty(cached,value);
            require(oldState.getValue(cached).equals(value) && newState.getValue(vanilla).equals(value),"cross-property lookup failed");
            require(oldState.getProperties().equals(newState.getProperties()),"serialized property map differs");
            for(T next:vanilla.getAllowedValues())
                require(newState.withProperty(vanilla,next)==newState.withProperty(cached,next),"vanilla transition lost canonical state");
        }
        require(vanilla.parseValue("__absent__").equals(cached.parseValue("__absent__")),"unknown values changed");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
