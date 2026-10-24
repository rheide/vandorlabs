package com.vandorlabs.neoforge.screen;

import com.mojang.serialization.MapCodec;
import com.vandorlabs.render.ScreenFamilyPolicy.Kind;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/** Native state properties remain specific to each existing registered screen family. */
public final class ScreenFamilyBlocks {
    public static final BooleanProperty KEYBOARD=BooleanProperty.create("keyboard"),UPPER=BooleanProperty.create("upper"),INVERTED=BooleanProperty.create("inverted");
    private ScreenFamilyBlocks(){}
    public static String titleKey(Kind kind){return kind==Kind.CONSOLE?"gui.vandorlabs.console.title":kind==Kind.HALF_CONSOLE?"gui.vandorlabs.half_console.title":kind==Kind.HALF_INPUT||kind==Kind.DIAGONAL_HALF?"gui.vandorlabs.input.title":kind==Kind.FULL_INPUT?"gui.vandorlabs.full_input.title":kind==Kind.DIAGONAL?"gui.vandorlabs.diagonal_screen.title":"gui.vandorlabs.selector.title";}
    public static final class Console extends ProgrammableScreenBlock {
        private static final MapCodec<Console> CODEC=simpleCodec(Console::new);public Console(BlockBehaviour.Properties properties){super(properties);}
        @Override public Kind family(){return Kind.CONSOLE;}@Override protected MapCodec<? extends DirectionalBlock> codec(){return CODEC;}
    }
    public static final class HalfConsole extends ProgrammableScreenBlock {
        private static final MapCodec<HalfConsole> CODEC=simpleCodec(HalfConsole::new);public HalfConsole(BlockBehaviour.Properties properties){super(properties);}
        @Override public Kind family(){return Kind.HALF_CONSOLE;}@Override protected MapCodec<? extends DirectionalBlock> codec(){return CODEC;}
    }
    public static class HalfInput extends ProgrammableScreenBlock {
        private static final MapCodec<HalfInput> CODEC=simpleCodec(HalfInput::new);public HalfInput(BlockBehaviour.Properties properties){super(properties);}
        @Override public Kind family(){return Kind.HALF_INPUT;}@Override protected boolean inputFlags(){return true;}@Override protected DirectionProperty facingProperty(){return BlockStateProperties.HORIZONTAL_FACING;}@Override protected MapCodec<? extends DirectionalBlock> codec(){return CODEC;}
    }
    public static final class FullInput extends HalfInput {
        private static final MapCodec<FullInput> CODEC=simpleCodec(FullInput::new);public FullInput(BlockBehaviour.Properties properties){super(properties);}
        @Override public Kind family(){return Kind.FULL_INPUT;}@Override protected MapCodec<? extends DirectionalBlock> codec(){return CODEC;}
    }
    public static final class Diagonal extends ProgrammableScreenBlock {
        private static final MapCodec<Diagonal> CODEC=simpleCodec(Diagonal::new);public Diagonal(BlockBehaviour.Properties properties){super(properties);}
        @Override public Kind family(){return Kind.DIAGONAL;}@Override protected boolean invertedFlag(){return true;}@Override protected DirectionProperty facingProperty(){return BlockStateProperties.HORIZONTAL_FACING;}@Override protected MapCodec<? extends DirectionalBlock> codec(){return CODEC;}
    }
    public static final class DiagonalHalf extends ProgrammableScreenBlock {
        private static final MapCodec<DiagonalHalf> CODEC=simpleCodec(DiagonalHalf::new);public DiagonalHalf(BlockBehaviour.Properties properties){super(properties);}
        @Override public Kind family(){return Kind.DIAGONAL_HALF;}@Override protected boolean upperFlag(){return true;}@Override protected MapCodec<? extends DirectionalBlock> codec(){return CODEC;}
    }
}
