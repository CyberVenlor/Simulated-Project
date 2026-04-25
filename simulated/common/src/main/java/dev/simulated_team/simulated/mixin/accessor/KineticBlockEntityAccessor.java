package dev.simulated_team.simulated.mixin.accessor;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KineticBlockEntity.class)
public interface KineticBlockEntityAccessor {

    @Accessor("capacity")
    float simulated$getCapacity();

    @Accessor("stress")
    float simulated$getStress();

    @Accessor("lastStressApplied")
    float simulated$getLastStressApplied();

    @Accessor("lastCapacityProvided")
    float simulated$getLastCapacityProvided();
}
