package dev.simulated_team.simulated.compat.naturescompass;

import com.chaosthedude.naturescompass.NaturesCompass;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.service.SimModCompatibilityService;

public class NaturesCompassCompatibility implements SimModCompatibilityService {
	@Override
	public void init() {
		registerTarget();
	}

	private static void registerTarget() {
		Simulated.getRegistrate().navTarget("natures_compass", NaturesCompassNavigationTarget::new, () -> NaturesCompass.naturesCompass);
	}

	@Override
	public String getModId() {
		return "naturescompass";
	}
}
