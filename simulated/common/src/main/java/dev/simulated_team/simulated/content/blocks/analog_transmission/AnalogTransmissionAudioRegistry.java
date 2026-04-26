package dev.simulated_team.simulated.content.blocks.analog_transmission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class AnalogTransmissionAudioRegistry {
    private static final Map<AnalogTransmissionBlockEntity, Boolean> CLIENT_TICKING_TRANSMISSIONS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private AnalogTransmissionAudioRegistry() {
    }

    public static void markClientTicking(final AnalogTransmissionBlockEntity blockEntity) {
        if (blockEntity == null || blockEntity.isRemoved()) {
            return;
        }

        CLIENT_TICKING_TRANSMISSIONS.put(blockEntity, Boolean.TRUE);
    }

    public static List<AnalogTransmissionBlockEntity> snapshot() {
        synchronized (CLIENT_TICKING_TRANSMISSIONS) {
            CLIENT_TICKING_TRANSMISSIONS.keySet().removeIf(blockEntity ->
                    blockEntity == null || blockEntity.isRemoved() || blockEntity.getLevel() == null);
            return new ArrayList<>(CLIENT_TICKING_TRANSMISSIONS.keySet());
        }
    }

    public static void clear() {
        CLIENT_TICKING_TRANSMISSIONS.clear();
    }
}
