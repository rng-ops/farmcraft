package com.farmcraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.IPlantable;

/**
 * Enhanced farmland block with fertilizer effects
 */
public class FertilizedFarmlandBlock extends Block {

    public static final IntegerProperty MOISTURE = FarmBlock.MOISTURE;
    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 15.0D, 16.0D);

    public enum FertilizerType {
        STONE_DUST("Stone Dust", 1.25f, 1.0f, 1.0f, "Faster growth"),
        CALCIUM_MIX("Calcium Mix", 1.0f, 1.25f, 1.0f, "Increased yield"),
        MINERAL_BLEND("Mineral Blend", 1.0f, 1.0f, 1.5f, "Longer effect duration"),
        GRAVEL_GRIT("Gravel Grit", 1.0f, 1.0f, 1.25f, "Stronger effects"),
        ENHANCED_STONE("Enhanced Stone", 1.5f, 1.25f, 1.0f, "Much faster growth + yield"),
        ENHANCED_MINERAL("Enhanced Mineral", 1.25f, 1.25f, 1.75f, "Balanced bonuses + duration"),
        SUPERIOR_BLEND("Superior Blend", 1.75f, 1.5f, 2.0f, "All bonuses greatly enhanced");

        private final String name;
        private final float growthSpeedMultiplier;
        private final float yieldMultiplier;
        private final float effectMultiplier;
        private final String effectDescription;

        FertilizerType(String name, float growthSpeedMultiplier, float yieldMultiplier,
                float effectMultiplier, String effectDescription) {
            this.name = name;
            this.growthSpeedMultiplier = growthSpeedMultiplier;
            this.yieldMultiplier = yieldMultiplier;
            this.effectMultiplier = effectMultiplier;
            this.effectDescription = effectDescription;
        }

        public String getName() {
            return name;
        }

        public float getGrowthSpeedMultiplier() {
            return growthSpeedMultiplier;
        }

        public float getYieldMultiplier() {
            return yieldMultiplier;
        }

        public float getEffectMultiplier() {
            return effectMultiplier;
        }

        public String getEffectDescription() {
            return effectDescription;
        }
    }

    private final FertilizerType fertilizerType;

    public FertilizedFarmlandBlock(Properties properties, FertilizerType fertilizerType) {
        super(properties);
        this.fertilizerType = fertilizerType;
        this.registerDefaultState(this.stateDefinition.any().setValue(MOISTURE, 0));
    }

    public FertilizerType getFertilizerType() {
        return fertilizerType;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MOISTURE);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return !this.defaultBlockState().canSurvive(context.getLevel(), context.getClickedPos())
                ? null
                : super.getStateForPlacement(context);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState above = level.getBlockState(pos.above());
        return !above.isSolid() || above.getBlock() instanceof CropBlock;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int moisture = state.getValue(MOISTURE);

        if (!isNearWater(level, pos) && !level.isRainingAt(pos.above())) {
            if (moisture > 0) {
                level.setBlock(pos, state.setValue(MOISTURE, moisture - 1), 2);
            }
        } else if (moisture < 7) {
            level.setBlock(pos, state.setValue(MOISTURE, 7), 2);
        }

        // Boost crop growth based on fertilizer type
        BlockState cropState = level.getBlockState(pos.above());
        if (cropState.getBlock() instanceof CropBlock crop) {
            float growthChance = fertilizerType.getGrowthSpeedMultiplier() - 1.0f;

            if (random.nextFloat() < growthChance) {
                // Extra growth tick
                crop.randomTick(cropState, level, pos.above(), random);
            }
        }
    }

    private boolean isNearWater(LevelReader level, BlockPos pos) {
        for (BlockPos nearPos : BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 1, 4))) {
            if (level.getFluidState(nearPos).isSource()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        // Fertilized farmland is more resistant to trampling
        if (fallDistance > 1.5f) {
            super.fallOn(level, state, pos, entity, fallDistance);
        }
    }

    public boolean canSustainPlant(BlockState state, BlockGetter world, BlockPos pos,
            Direction facing, IPlantable plantable) {
        return facing == Direction.UP;
    }

    public boolean isFertile(BlockGetter world, BlockPos pos) {
        return true;
    }
}
