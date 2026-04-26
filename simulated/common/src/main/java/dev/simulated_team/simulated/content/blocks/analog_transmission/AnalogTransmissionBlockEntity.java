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
    /**
     * The ExtraKinetic BlockEntity associated with the AnalogTransmission
     */
    private final AnalogTransmissionCogwheel extraWheel;

    private final FerrariEngine ferrariEngine = new FerrariEngine();
    private final FerrariDrivetrain ferrariDrivetrain = new FerrariDrivetrain();

    private int signal = 0;
    private int ferrariCommandGear = 0;
    private int ferrariShiftTicks = -1;
    private double ferrariShiftRatioRatio = 0.0;
    private int ferrariShiftTargetGear = 0;
    private double ferrariTimeMs = 0.0;


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
        return this.ferrariDrivetrain.gear;
    }

    public int getFerrariAudioGear() {
        return Mth.clamp(this.ferrariDrivetrain.gear, 0, 6);
    }

    public float getFerrariEnginePowerInput() {
        final float shaftSpeed = this.getSpeed();
        final float cogSpeed = this.extraWheel.getSpeed();

        return Math.abs(cogSpeed) > Math.abs(shaftSpeed) ? cogSpeed : shaftSpeed;
    }

    public float getFerrariThrottle() {
        return this.getFerrariEnginePowerInput() > 0.01f ? 1.0f : 0.0f;
    }

    public float getFerrariBrake() {
        return this.getFerrariEnginePowerInput() < -0.01f ? 1.0f : 0.0f;
    }

    public float getFerrariAudioThrottle() {
        return (float) this.ferrariEngine.throttle;
    }

    public float getFerrariEngineRpm() {
        final float rpm = (float) this.ferrariEngine.rpm;
        if (rpm < 0.01f) {
            return 0.0f;
        }

        return rpm;
    }

    public float getFerrariEngineTheta() {
        return (float) this.ferrariEngine.theta;
    }

    public float getFerrariEngineOmega() {
        return (float) this.ferrariEngine.omega;
    }

    public float getFerrariEnginePreviousOmega() {
        return (float) this.ferrariEngine.prevOmega;
    }

    public float getFerrariDrivetrainTheta() {
        return (float) this.ferrariDrivetrain.theta;
    }

    public float getFerrariDrivetrainOmega() {
        return (float) this.ferrariDrivetrain.omega;
    }

    public float getFerrariDrivetrainPreviousOmega() {
        return (float) this.ferrariDrivetrain.prevOmega;
    }

    private void updateFerrariEngineDebugState() {
        final int commandGear = this.getFerrariEngineGear();
        if (commandGear != this.ferrariCommandGear) {
            this.ferrariCommandGear = commandGear;
            this.ferrariDrivetrain.changeGear(commandGear);
        }

        if (this.ferrariShiftTicks >= 0) {
            this.ferrariShiftTicks--;
            if (this.ferrariShiftTicks < 0) {
                this.ferrariDrivetrain.omega = this.ferrariDrivetrain.omega * this.ferrariShiftRatioRatio;
                this.ferrariDrivetrain.gear = (int) clamp(this.ferrariShiftTargetGear, 0, this.ferrariDrivetrain.gears.length);
                this.ferrariDrivetrain.downShift = false;
            }
        }

        if (this.ferrariDrivetrain.downShift) {
            this.ferrariEngine.throttle = 0.8;
        } else {
            if (this.getFerrariEnginePowerInput() > 0.01f) {
                this.ferrariEngine.throttle = clamp(this.ferrariEngine.throttle + 0.2, 0.0, 1.0);
            } else {
                this.ferrariEngine.throttle = clamp(this.ferrariEngine.throttle - 0.2, 0.0, 1.0);
            }
        }

        if (this.getFerrariEnginePowerInput() < -0.01f) {
            this.ferrariDrivetrain.omega -= 0.3;
        }

        final double dt = 1.0 / 20.0;
        final int subSteps = 20;
        final double h = dt / subSteps;
        final double loadInertia = this.ferrariGetLoadInertia() * 0.00;

        for (int i = 0; i < subSteps; i++) {
            this.ferrariEngine.integrate(loadInertia, this.ferrariTimeMs + dt * i, h);
            this.ferrariDrivetrain.integrate(h);
            this.ferrariEngine.solvePos(this.ferrariDrivetrain, h);
            this.ferrariDrivetrain.solvePos(this.ferrariEngine, h);
            this.ferrariEngine.update(h);
            this.ferrariDrivetrain.update(h);
            this.ferrariEngine.solveVel(this.ferrariDrivetrain, h);
            this.ferrariDrivetrain.solveVel(this.ferrariEngine, h);
        }

        this.ferrariTimeMs += dt * 1000.0;
    }

    private double ferrariGetLoadInertia() {
        if (this.ferrariDrivetrain.gear == 0)
            return 0;

        final double gearRatio = this.ferrariDrivetrain.getGearRatio();
        final double totalGearRatio = this.ferrariDrivetrain.getTotalGearRatio();
        final double vehicleInertia = 500 * Math.pow(0.250, 2);
        final double wheelsInertia = 4 * 12.0 * Math.pow(0.250, 2);

        final double i1 = vehicleInertia / Math.pow(totalGearRatio, 2);
        final double i2 = wheelsInertia / Math.pow(totalGearRatio, 2);
        final double i3 = this.ferrariDrivetrain.inertia / Math.pow(gearRatio, 2);
        return i1 + i2 + i3;
    }

    private static double clamp(final double value, final double min, final double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double ratio(final double value, final double start, final double end) {
        return clamp((value - start) / (end - start), 0.0, 1.0);
    }

    private class FerrariEngine {
        double idle = 1000;
        double limiter = 8900;
        double softLimiter = 8800;
        double rpm = 0;
        double inertia = 0.8;
        double limiterMs = 0;
        double limiterDelay = 100;
        double lastLimiter = 0;
        double torque = 400;
        double engineBraking = 200;
        double throttle = 0;
        double theta = 0;
        double alpha = 0;
        double omega = 0;
        double prevTheta = 0;
        double prevOmega = 0;
        double dTheta = 0;
        double omegaMax = 2 * Math.PI * this.limiter / 60;

        void integrate(final double loadInertia, final double time, final double dt) {
            if (this.rpm >= this.softLimiter) {
                final double ratio = ratio(this.rpm, this.softLimiter, this.limiter);
                this.throttle *= Math.pow(1 - ratio, 0.05);
            }
            if (this.rpm >= this.limiter)
                this.lastLimiter = time;
            if (time - this.lastLimiter >= this.limiterMs) {
                final double t = time - this.lastLimiter;
                final double ratio = ratio(t, 0, this.limiterDelay);
                this.throttle *= ratio;
            } else {
                this.throttle = 0.0;
            }

            double idleTorque = 0;
            if (this.throttle < 0.1 && this.rpm < this.idle * 1.5) {
                final double idleRatio = ratio(this.rpm, this.idle * 0.9, this.idle);
                idleTorque = (1 - idleRatio) * this.engineBraking * 10;
            }

            final double t1 = Math.pow(this.throttle, 1.2) * this.torque;
            final double t2 = Math.pow(1 - this.throttle, 1.2) * this.engineBraking;
            final double torque = t1 - t2 + idleTorque;
            final double inertia = loadInertia + this.inertia;
            final double dAlpha = torque / inertia;

            this.prevTheta = this.theta;
            this.omega += dAlpha * dt;
            this.theta += this.omega * dt;
            this.dTheta = this.omega * dt;
            this.rpm = (60 * this.omega) / 2 * Math.PI;
        }

        void update(final double h) {
            this.prevOmega = this.omega;
            final double dTheta = (this.theta - this.prevTheta) / h;
            this.omega = dTheta;
        }

        void solvePos(final FerrariDrivetrain drivetrain, final double h) {
            if (drivetrain.gear == 0)
                return;
            final double compliance = Math.max(0.0006 - 0.00015 * drivetrain.gear, 0.00007);
            final double correction = drivetrain.theta - this.theta;
            final double correction1 = this.getCorrection(correction, h, compliance);
            this.theta += correction1 * Math.signum(correction);
        }

        void solveVel(final FerrariDrivetrain drivetrain, final double h) {
            double damping = 12;
            if (drivetrain.gear > 3)
                damping = 9;

            this.omega += (drivetrain.omega - this.omega) * damping * h;
        }

        double getCorrection(final double correction, final double h, final double compliance) {
            final double w = correction * correction * 1 / this.inertia;
            final double lambda = -correction / (w + compliance / h / h);
            return correction * -lambda;
        }
    }

    private class FerrariDrivetrain {
        int gear = 0;
        double clutch = 1.0;
        boolean downShift = false;
        final double[] gears = {3.4, 2.36, 1.85, 1.47, 1.24, 1.07, 0.92, 0.78};
        double finalDrive = 3.44;
        double theta = 0;
        double omega = 0;
        double prevTheta = 0;
        double prevOmega = 0;
        double thetaWheel = 0;
        double omegaWheel = 0;
        double inertia = 0.1 + 0.05;
        double damping = 6;
        double compliance = 0.01;
        double shiftTime = 50;

        void integrate(final double dt) {
            this.clutch = clamp(this.clutch, 0, 1);
            this.prevTheta = this.theta;
            this.theta += this.omega * dt;
        }

        void update(final double h) {
            this.prevOmega = this.omega;
            final double dTheta = (this.theta - this.prevTheta) / h;
            this.omega = dTheta;
        }

        void solvePos(final FerrariEngine engine, final double h) {
            final double correction = engine.theta - this.theta;
            final double correction1 = this.getCorrection(correction, h, this.compliance);
            this.theta += correction1 * Math.signum(correction);
        }

        void solveVel(final FerrariEngine engine, final double h) {
            double damping = this.damping;
            if (this.gear > 3)
                damping = this.damping * 0.75;

            this.omega += (engine.omega - this.omega) * damping * h;
        }

        double getCorrection(final double correction, final double h, final double compliance) {
            final double w = correction * correction * 1 / this.inertia;
            final double lambda = -correction / (w + compliance / h / h);
            return correction * -lambda;
        }

        double getFinalDriveRatio() {
            return this.finalDrive;
        }

        double getGearRatio() {
            return this.getGearRatio(this.gear);
        }

        double getGearRatio(final int gear) {
            final int clampedGear = (int) clamp(gear, 0, this.gears.length);
            return clampedGear > 0 ? this.gears[clampedGear - 1] : 0;
        }

        double getTotalGearRatio() {
            return this.getGearRatio() * this.getFinalDriveRatio();
        }

        void changeGear(final int gear) {
            final double prevRatio = this.getGearRatio(this.gear);
            final double nextRatio = this.getGearRatio(gear);
            final double ratioRatio = prevRatio > 0 ? nextRatio / prevRatio : 0;

            if (ratioRatio == 1)
                return;

            this.gear = 0;

            if (ratioRatio > 1)
                this.downShift = true;

            ferrariShiftTicks = Math.max(1, (int) Math.ceil(this.shiftTime / 50.0));
            ferrariShiftRatioRatio = ratioRatio;
            ferrariShiftTargetGear = gear;
        }
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
