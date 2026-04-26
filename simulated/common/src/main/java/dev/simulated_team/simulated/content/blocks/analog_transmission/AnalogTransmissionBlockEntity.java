package dev.simulated_team.simulated.content.blocks.analog_transmission;

import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.simulated_team.simulated.data.SimLang;
import dev.simulated_team.simulated.mixin_interface.extra_kinetics.KineticBlockEntityExtension;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraBlockPos;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraKinetics;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.List;

import static net.minecraft.ChatFormatting.GOLD;

/**
 * The parent BlockEntity class. implements {@link ExtraKinetics ExtraKinetics} to allow multi-kinetic functionality
 */
public class AnalogTransmissionBlockEntity extends KineticBlockEntity implements ExtraKinetics {
    private static final float FERRARI_IDLE_RPM = 1000.0f;
    private static final float FERRARI_LIMITER_RPM = 8900.0f;
    private static final float FERRARI_SOFT_LIMITER_RPM = 8800.0f;
    private static final float FERRARI_FULL_THROTTLE_POWER = 32.0f;
    private static final float FERRARI_ENGINE_INERTIA = 0.8f;
    private static final float FERRARI_DRIVETRAIN_INERTIA = 0.15f;
    private static final float FERRARI_DRIVETRAIN_DAMPING = 6.0f;
    private static final float FERRARI_TORQUE = 400.0f;
    private static final float FERRARI_ENGINE_BRAKING = 200.0f;
    private static final float FERRARI_BRAKE_IMPULSE = 0.3f;
    private static final float FERRARI_DRIVETRAIN_COAST_DRAG = 0.08f;
    private static final float FERRARI_FINAL_DRIVE = 3.44f;
    private static final float[] FERRARI_GEAR_RATIOS = {
            3.4f, 2.36f, 1.85f, 1.47f, 1.24f, 1.07f, 0.92f, 0.78f
    };

    /**
     * The ExtraKinetic BlockEntity associated with the AnalogTransmission
     */
    private final AnalogTransmissionCogwheel extraWheel;

    private int signal = 0;
    private int ferrariEngagedGear = 0;
    private int ferrariPendingGear = 0;
    private int ferrariShiftTicks = 0;
    private float ferrariShiftRatioRatio = 0.0f;
    private boolean ferrariDownShift = false;
    private float ferrariEngineThrottle = 0.0f;
    private float ferrariEngineTheta = 0.0f;
    private float ferrariEngineOmega = 0.0f;
    private float ferrariEnginePreviousTheta = 0.0f;
    private float ferrariEnginePreviousOmega = 0.0f;
    private float ferrariDrivetrainTheta = 0.0f;
    private float ferrariDrivetrainOmega = 0.0f;
    private float ferrariDrivetrainPreviousTheta = 0.0f;
    private float ferrariDrivetrainPreviousOmega = 0.0f;


    /**
     * Set whenever the analog transmission disconnects due to overspeeding
     */
    private boolean oversaturated = false;
    boolean alreadySentEffects = false;

    public AnalogTransmissionBlockEntity(final BlockEntityType<?> typeIn, final BlockPos pos, final BlockState state) {
        super(typeIn, pos, state);

        //set our ExtraKientic BlockEntity and set the proper BlockState
        this.extraWheel = new AnalogTransmissionCogwheel(typeIn, new ExtraBlockPos(pos), state, this);
    }

    /**
     * Required override, as we need our ExtraKinetic BlockEntity to tick
     */
    @Override
    public void tick() {
        final int bestNeighborSignal = this.getLevel().getBestNeighborSignal(this.getBlockPos());

        if (!this.getLevel().isClientSide) {
            if (bestNeighborSignal != this.signal) {
                //detach our own network, and our ExtraKinetic's
                this.detachKinetics();
                this.extraWheel.detachKinetics();

                //Remove the sources
                this.removeSource();
                this.extraWheel.removeSource();

                this.signal = bestNeighborSignal;
                this.getLevel().setBlockAndUpdate(this.getBlockPos(), this.getBlockState().setValue(AnalogTransmissionBlock.POWERED, this.signal > 0));

                //Depending on if we are connected to the ExtraKinetic BlockEntity, or vise versa, we need to attach kinetics accordingly
                if (((KineticBlockEntityExtension) this).simulated$getConnectedToExtraKinetics()) {//Attach ours, then ExtraKientic's
                    this.attachKinetics();
                    this.extraWheel.attachKinetics();
                } else { //Attach ExtraKinetic's, then ours
                    this.extraWheel.attachKinetics();
                    this.attachKinetics();
                }
            }
        } else if (this.oversaturated) {
            if (!this.alreadySentEffects) {
                this.alreadySentEffects = true;
                this.effects.triggerOverStressedEffect();
            }
        } else {
            this.alreadySentEffects = false;
        }

        this.updateFerrariEngineDebugState();
        this.extraWheel.tick();
        super.tick();
    }

    @VisibleForTesting
    public float getRotationModifier() {
        return 1 - (this.signal + 1) / 16f;
    }

    public int getSignal() {
        return this.signal;
    }

    public int getFerrariEngineGear() {
        return Mth.clamp(this.signal - 7, 0, 8);
    }

    public int getFerrariEngagedGear() {
        return this.ferrariEngagedGear;
    }

    public int getFerrariAudioGear() {
        return Mth.clamp(this.ferrariEngagedGear, 0, 6);
    }

    public float getFerrariEnginePowerInput() {
        final float shaftSpeed = this.getSpeed();
        final float cogSpeed = this.extraWheel.getSpeed();

        return Math.abs(cogSpeed) > Math.abs(shaftSpeed) ? cogSpeed : shaftSpeed;
    }

    public float getFerrariThrottle() {
        return Mth.clamp(this.getFerrariEnginePowerInput() / FERRARI_FULL_THROTTLE_POWER, 0.0f, 1.0f);
    }

    public float getFerrariBrake() {
        return Mth.clamp(-this.getFerrariEnginePowerInput() / FERRARI_FULL_THROTTLE_POWER, 0.0f, 1.0f);
    }

    public float getFerrariAudioThrottle() {
        return this.ferrariEngineThrottle;
    }

    public float getFerrariEngineRpm() {
        final float rpm = this.ferrariEngineOmegaToRpm(this.ferrariEngineOmega);
        if (rpm < 0.01f) {
            return 0.0f;
        }

        return rpm;
    }

    public float getFerrariEngineTheta() {
        return this.ferrariEngineTheta;
    }

    public float getFerrariEngineOmega() {
        return this.ferrariEngineOmega;
    }

    public float getFerrariEnginePreviousOmega() {
        return this.ferrariEnginePreviousOmega;
    }

    public float getFerrariDrivetrainTheta() {
        return this.ferrariDrivetrainTheta;
    }

    public float getFerrariDrivetrainOmega() {
        return this.ferrariDrivetrainOmega;
    }

    public float getFerrariDrivetrainPreviousOmega() {
        return this.ferrariDrivetrainPreviousOmega;
    }

    private void updateFerrariEngineDebugState() {
        this.updateFerrariGearshift();

        final float powerInput = this.getFerrariEnginePowerInput();
        this.ferrariEngineThrottle = this.ferrariDownShift ? Math.max(this.getFerrariThrottle(), 0.8f) : this.getFerrariThrottle();
        final float brake = this.getFerrariBrake();

        if (brake > 0.0f) {
            this.ferrariDrivetrainOmega = Math.max(0.0f, this.ferrariDrivetrainOmega - FERRARI_BRAKE_IMPULSE * brake);
        }
        if (this.ferrariEngineThrottle < 0.01f && brake < 0.01f) {
            this.ferrariDrivetrainOmega *= 1.0f - FERRARI_DRIVETRAIN_COAST_DRAG;
        }

        final float dt = 1.0f / 20.0f;
        final int subSteps = 20;
        final float h = dt / subSteps;

        for (int i = 0; i < subSteps; i++) {
            this.integrateFerrariEngine(h);
            this.integrateFerrariDrivetrain(h);

            this.solveFerrariEnginePosition(h);
            this.solveFerrariDrivetrainPosition(h);

            this.updateFerrariEngineVelocity(h);
            this.updateFerrariDrivetrainVelocity(h);

            this.solveFerrariEngineVelocity(h);
            this.solveFerrariDrivetrainVelocity(h);
        }

        if (Math.abs(powerInput) < 0.01f && this.ferrariEngineOmega < 0.01f) {
            this.ferrariEngineOmega = 0.0f;
        }
    }

    private void updateFerrariGearshift() {
        final int commandGear = this.getFerrariEngineGear();
        if (commandGear != this.ferrariPendingGear) {
            final float prevRatio = this.getFerrariGearRatio(this.ferrariEngagedGear);
            final float nextRatio = this.getFerrariGearRatio(commandGear);
            final float ratioRatio = prevRatio > 0.0f ? nextRatio / prevRatio : 0.0f;

            this.ferrariPendingGear = commandGear;
            this.ferrariShiftTicks = 1;
            this.ferrariEngagedGear = 0;
            this.ferrariShiftRatioRatio = ratioRatio;
            this.ferrariDownShift = ratioRatio > 1.0f;
            return;
        }

        if (this.ferrariShiftTicks <= 0) {
            return;
        }

        this.ferrariShiftTicks--;
        if (this.ferrariShiftTicks > 0) {
            return;
        }

        this.ferrariEngagedGear = this.ferrariPendingGear;
        this.ferrariDownShift = false;
        if (this.ferrariEngagedGear > 0) {
            this.ferrariDrivetrainOmega *= this.ferrariShiftRatioRatio;
        }
    }

    private void integrateFerrariEngine(final float dt) {
        float throttle = this.ferrariEngineThrottle;
        final float rpm = this.ferrariEngineOmegaToRpm(this.ferrariEngineOmega);
        if (rpm >= FERRARI_SOFT_LIMITER_RPM) {
            final float limiterRatio = Mth.clamp((rpm - FERRARI_SOFT_LIMITER_RPM) / (FERRARI_LIMITER_RPM - FERRARI_SOFT_LIMITER_RPM), 0.0f, 1.0f);
            throttle *= Math.pow(1.0f - limiterRatio, 0.05f);
        }
        if (rpm >= FERRARI_LIMITER_RPM) {
            throttle = 0.0f;
        }

        float idleTorque = 0.0f;
        if (throttle < 0.1f && rpm < FERRARI_IDLE_RPM * 1.5f) {
            final float idleRatio = Mth.clamp((rpm - FERRARI_IDLE_RPM * 0.9f) / (FERRARI_IDLE_RPM * 0.1f), 0.0f, 1.0f);
            idleTorque = (1.0f - idleRatio) * FERRARI_ENGINE_BRAKING * 10.0f;
        }

        final float throttleTorque = (float) Math.pow(throttle, 1.2f) * FERRARI_TORQUE;
        final float engineBrakeTorque = (float) Math.pow(1.0f - throttle, 1.2f) * FERRARI_ENGINE_BRAKING;
        final float torque = throttleTorque - engineBrakeTorque + idleTorque;

        this.ferrariEnginePreviousTheta = this.ferrariEngineTheta;
        this.ferrariEngineOmega += torque / FERRARI_ENGINE_INERTIA * dt;
        this.ferrariEngineTheta += this.ferrariEngineOmega * dt;
    }

    private void integrateFerrariDrivetrain(final float dt) {
        this.ferrariDrivetrainPreviousTheta = this.ferrariDrivetrainTheta;
        this.ferrariDrivetrainTheta += this.ferrariDrivetrainOmega * dt;
    }

    private void solveFerrariEnginePosition(final float h) {
        if (this.ferrariEngagedGear == 0) {
            return;
        }

        final float compliance = Math.max(0.0006f - 0.00015f * this.ferrariEngagedGear, 0.00007f);
        final float correction = this.ferrariDrivetrainTheta - this.ferrariEngineTheta;
        this.ferrariEngineTheta += this.getFerrariEngineCorrection(correction, h, compliance) * Math.signum(correction);
    }

    private void solveFerrariDrivetrainPosition(final float h) {
        final float correction = this.ferrariEngineTheta - this.ferrariDrivetrainTheta;
        this.ferrariDrivetrainTheta += this.getFerrariDrivetrainCorrection(correction, h, 0.01f) * Math.signum(correction);
    }

    private void updateFerrariEngineVelocity(final float h) {
        this.ferrariEnginePreviousOmega = this.ferrariEngineOmega;
        this.ferrariEngineOmega = (this.ferrariEngineTheta - this.ferrariEnginePreviousTheta) / h;
    }

    private void updateFerrariDrivetrainVelocity(final float h) {
        this.ferrariDrivetrainPreviousOmega = this.ferrariDrivetrainOmega;
        this.ferrariDrivetrainOmega = (this.ferrariDrivetrainTheta - this.ferrariDrivetrainPreviousTheta) / h;
    }

    private void solveFerrariEngineVelocity(final float h) {
        float damping = 12.0f;
        if (this.ferrariEngagedGear > 3) {
            damping = 9.0f;
        }

        this.ferrariEngineOmega += (this.ferrariDrivetrainOmega - this.ferrariEngineOmega) * damping * h;
    }

    private void solveFerrariDrivetrainVelocity(final float h) {
        float damping = FERRARI_DRIVETRAIN_DAMPING;
        if (this.ferrariEngagedGear > 3) {
            damping = FERRARI_DRIVETRAIN_DAMPING * 0.75f;
        }

        this.ferrariDrivetrainOmega += (this.ferrariEngineOmega - this.ferrariDrivetrainOmega) * damping * h;
    }

    private float getFerrariEngineCorrection(final float correction, final float h, final float compliance) {
        final float w = correction * correction / FERRARI_ENGINE_INERTIA;
        final float lambda = -correction / (w + compliance / h / h);
        return correction * -lambda;
    }

    private float getFerrariDrivetrainCorrection(final float correction, final float h, final float compliance) {
        final float w = correction * correction / FERRARI_DRIVETRAIN_INERTIA;
        final float lambda = -correction / (w + compliance / h / h);
        return correction * -lambda;
    }

    private float getFerrariGearRatio(final int gear) {
        if (gear <= 0) {
            return 0.0f;
        }

        final int index = Mth.clamp(gear, 1, FERRARI_GEAR_RATIOS.length) - 1;
        return FERRARI_GEAR_RATIOS[index];
    }

    private float getFerrariTotalGearRatio(final int gear) {
        return this.getFerrariGearRatio(gear) * FERRARI_FINAL_DRIVE;
    }

    private float ferrariEngineOmegaToRpm(final float omega) {
        return 60.0f * omega / 2.0f * (float) Math.PI;
    }

    /**
     * This propagateRotationTo handles both the AnalogTransmission's modifier towards the ExtraKientic BlockEntity, and vise versa
     */
    @Override
    public float propagateRotationTo(final KineticBlockEntity target, final BlockState stateFrom, final BlockState stateTo, final BlockPos diff, final boolean connectedViaAxes, final boolean connectedViaCogs) {
        float gatheredRotationModifier = 0;
        if (this.signal != 15) {
            if (target == this.extraWheel) { //reduce speed
                gatheredRotationModifier = this.signal == 0 ? 1 : this.getRotationModifier();
                if (this.oversaturated) {
                    return 0;
                }
            } else if (target == this) { //increase speed
                gatheredRotationModifier = this.signal == 0 ? 1 : (1 / this.getRotationModifier());

                if (Math.abs(this.extraWheel.getTheoreticalSpeed() * gatheredRotationModifier) > AllConfigs.server().kinetics.maxRotationSpeed.get()) {
                    this.oversaturated = true;
                    return 0;
                } else {
                    this.oversaturated = false;
                }
            }
        } else {
            this.oversaturated = false;
        }

        return gatheredRotationModifier;
    }

    @Override
    protected void write(final CompoundTag compound, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.write(compound, registries, clientPacket);

        compound.putInt("Signal", this.signal);
        compound.putBoolean("Oversaturated", this.oversaturated);
    }

    @Override
    protected void read(final CompoundTag compound, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.read(compound, registries, clientPacket);

        this.signal = compound.getInt("Signal");
        this.oversaturated = compound.getBoolean("Oversaturated");
    }

    @Override
    public boolean isOverStressed() {
        if (this.level.isClientSide) {
            return this.oversaturated || this.overStressed;
        }

        return super.isOverStressed();
    }

    /**
     * Accesses the ExtraKinetic BlockEntity associated with the AnalogTransmission
     */
    @Override
    public @NotNull KineticBlockEntity getExtraKinetics() {
        return this.extraWheel;
    }

    @Override
    //See javaDoc
    public boolean shouldConnectExtraKinetics() {
        return true;
    }

    @Override
    public String getExtraKineticsSaveName() {
        return "ExtraCogwheel";
    }

    @Override
    public boolean addToTooltip(final List<Component> tooltip, final boolean isPlayerSneaking) {
        if (this.oversaturated) {
            SimLang.translate("analog_transmission.too_fast")
                    .style(GOLD)
                    .forGoggles(tooltip);

            final MutableComponent component = SimLang.translate("analog_transmission.too_fast_error")
                    .component();

            final List<Component> cutString = TooltipHelper.cutTextComponent(component, FontHelper.Palette.GRAY_AND_WHITE);
            tooltip.addAll(cutString);

            return true;
        }

        return super.addToTooltip(tooltip, isPlayerSneaking);
    }

    /**
     * The ExtraKinetic BlockEntity for the AnalogTransmission. Extends KineticBlockEntity (Can be any other KBE), and implements ExtraKinetics
     */
    public static class AnalogTransmissionCogwheel extends KineticBlockEntity implements ExtraKineticsBlockEntity {

        public static final ICogWheel EXTRA_COGWHEEL_CONFIG = new ICogWheel() {
            @Override
            public boolean hasShaftTowards(final LevelReader world, final BlockPos pos, final BlockState state, final Direction face) {
                return false;
            }

            @Override
            public Direction.Axis getRotationAxis(final BlockState state) {
                return state.getValue(AnalogTransmissionBlock.AXIS);
            }
        };

        /**
         * Access to the parent BlockEntity to avoid called {@link Level#getBlockEntity(BlockPos) getBlockEntity} unnecessarily
         */
        private final KineticBlockEntity parentBlockEntity;

        /**
         * @param pos An ExtraBlockPos associated with this ExtraKinetic BlockEntity. This is needed to inform the {@link com.simibubi.create.content.kinetics.RotationPropagator} that this BlockEntity is an ExtraKinetic one.
         */
        public AnalogTransmissionCogwheel(final BlockEntityType<?> typeIn, final ExtraBlockPos pos, final BlockState state, final KineticBlockEntity parentBlockEntity) {
            super(typeIn, pos, state);
            this.parentBlockEntity = parentBlockEntity;
        }

        /**
         * We call the parent's {@link KineticBlockEntity#propagateRotationTo(KineticBlockEntity, BlockState, BlockState, BlockPos, boolean, boolean) propagateRotationTo} here for easier rotation modifier Handling.
         */
        @Override
        public float propagateRotationTo(final KineticBlockEntity target, final BlockState stateFrom, final BlockState stateTo, final BlockPos diff, final boolean connectedViaAxes, final boolean connectedViaCogs) {
            return this.parentBlockEntity.propagateRotationTo(target, stateFrom, stateTo, diff, connectedViaAxes, connectedViaCogs);
        }

        @Override
        protected boolean canPropagateDiagonally(final IRotate block, final BlockState state) {
            return true;
        }

        @Override
        public KineticBlockEntity getParentBlockEntity() {
            return this.parentBlockEntity;
        }
    }
}
