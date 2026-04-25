package dev.simulated_team.simulated.mixin.accessor;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ArmBlockEntity.class)
public interface ArmBlockEntityAccessor {

    @Accessor("inputs")
    List<ArmInteractionPoint> simulated$getInputs();

    @Accessor("outputs")
    List<ArmInteractionPoint> simulated$getOutputs();

    @Accessor("heldItem")
    ItemStack simulated$getHeldItem();

    @Accessor("phase")
    ArmBlockEntity.Phase simulated$getPhase();

    @Accessor("goggles")
    boolean simulated$getGoggles();

    @Accessor("selectionMode")
    ScrollOptionBehaviour<ArmBlockEntity.SelectionMode> simulated$getSelectionMode();

    @Accessor("lastInputIndex")
    int simulated$getLastInputIndex();

    @Accessor("lastOutputIndex")
    int simulated$getLastOutputIndex();

    @Accessor("redstoneLocked")
    boolean simulated$getRedstoneLocked();
}
