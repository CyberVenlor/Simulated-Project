package dev.simulated_team.simulated.mixin.create_assembly;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlock;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import dev.simulated_team.simulated.mixin.accessor.ArmBlockEntityAccessor;
import dev.simulated_team.simulated.mixin.accessor.KineticBlockEntityAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ArmBlock.class)
public class ArmBlockMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void simulated$printDebugInfo(final ItemStack stack,
                                          final BlockState state,
                                          final Level level,
                                          final BlockPos pos,
                                          final Player player,
                                          final InteractionHand hand,
                                          final BlockHitResult hitResult,
                                          final CallbackInfoReturnable<ItemInteractionResult> cir) {
        if (!player.isShiftKeyDown() || !stack.isEmpty()) {
            return;
        }

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            final var blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof final ArmBlockEntity armBlockEntity) {
                simulated$printArmDebug(serverPlayer, armBlockEntity, state);
            } else {
                serverPlayer.sendSystemMessage(Component.literal("[Arm Debug] block entity missing at " + pos));
            }
        }

        cir.setReturnValue(ItemInteractionResult.SUCCESS);
    }

    private static void simulated$printArmDebug(final ServerPlayer player,
                                                final ArmBlockEntity armBlockEntity,
                                                final BlockState state) {
        final ArmBlockEntityAccessor armAccessor = (ArmBlockEntityAccessor) armBlockEntity;
        final KineticBlockEntityAccessor kineticAccessor = (KineticBlockEntityAccessor) armBlockEntity;
        final ScrollOptionBehaviour<ArmBlockEntity.SelectionMode> selectionMode = armAccessor.simulated$getSelectionMode();
        final List<ArmInteractionPoint> inputs = armAccessor.simulated$getInputs();
        final List<ArmInteractionPoint> outputs = armAccessor.simulated$getOutputs();

        player.sendSystemMessage(Component.literal("[Arm Debug] pos=%s state=%s level=%s dim=%s sublevel=%s"
                .formatted(armBlockEntity.getBlockPos(),
                        state.getBlock().builtInRegistryHolder().key().location(),
                        levelName(armBlockEntity.getLevel()),
                        dimensionName(armBlockEntity.getLevel()),
                        isSubLevelName(armBlockEntity.getLevel()))));
        player.sendSystemMessage(Component.literal("[Arm Debug] phase=%s selection=%s held=%s redstoneLocked=%s goggles=%s"
                .formatted(armAccessor.simulated$getPhase(),
                        selectionMode == null ? "null" : selectionMode.get(),
                        itemName(armAccessor.simulated$getHeldItem()),
                        armAccessor.simulated$getRedstoneLocked(),
                        armAccessor.simulated$getGoggles())));
        player.sendSystemMessage(Component.literal("[Arm Debug] speed=%s overstressed=%s network=%s source=%s stress=%s capacity=%s applied=%s provided=%s"
                .formatted(formatFloat(armBlockEntity.getSpeed()),
                        armBlockEntity.isOverStressed(),
                        armBlockEntity.hasNetwork() ? armBlockEntity.network : "none",
                        armBlockEntity.hasSource() ? armBlockEntity.source : "none",
                        formatFloat(kineticAccessor.simulated$getStress()),
                        formatFloat(kineticAccessor.simulated$getCapacity()),
                        formatFloat(kineticAccessor.simulated$getLastStressApplied()),
                        formatFloat(kineticAccessor.simulated$getLastCapacityProvided()))));
        player.sendSystemMessage(Component.literal("[Arm Debug] inputs=%s lastInput=%s outputs=%s lastOutput=%s"
                .formatted(inputs.size(),
                        armAccessor.simulated$getLastInputIndex(),
                        outputs.size(),
                        armAccessor.simulated$getLastOutputIndex())));

        simulated$printPoints(player, "in", inputs);
        simulated$printPoints(player, "out", outputs);
    }

    private static void simulated$printPoints(final ServerPlayer player,
                                              final String prefix,
                                              final List<ArmInteractionPoint> points) {
        for (int i = 0; i < points.size(); i++) {
            final ArmInteractionPoint point = points.get(i);
            final Level pointLevel = point.getLevel();
            final BlockPos pointPos = point.getPos();
            player.sendSystemMessage(Component.literal("[Arm Debug] %s[%s] pos=%s mode=%s valid=%s block=%s level=%s dim=%s type=%s"
                    .formatted(prefix,
                            i,
                            pointPos,
                            point.getMode(),
                            point.isValid(),
                            blockName(pointLevel, pointPos),
                            levelName(pointLevel),
                            dimensionName(pointLevel),
                            point.getType().getClass().getSimpleName())));
        }
    }

    private static String itemName(final ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }

        return "%s x%s".formatted(stack.getItem().builtInRegistryHolder().key().location(), stack.getCount());
    }

    private static String blockName(final Level level, final BlockPos pos) {
        if (level == null) {
            return "null-level";
        }

        return level.getBlockState(pos).getBlock().builtInRegistryHolder().key().location().toString();
    }

    private static String levelName(final Level level) {
        return level == null ? "null" : level.getClass().getName();
    }

    private static String dimensionName(final Level level) {
        if (level == null) {
            return "null";
        }

        final ResourceKey<Level> dimension = level.dimension();
        return dimension == null ? "null" : dimension.location().toString();
    }

    private static boolean isSubLevelName(final Level level) {
        return level != null && level.getClass().getName().contains("SubLevel");
    }

    private static String formatFloat(final float value) {
        return String.format("%.3f", value);
    }
}
