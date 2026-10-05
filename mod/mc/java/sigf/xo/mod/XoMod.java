package sigf.xo.mod;

import net.fabricmc.api.ModInitializer;
import sigf.xo.Xo;

/**
 * Minecraft Crossover: Minecraft blocks and creatures live inside Los Santos and fight, build and blow up with GTA's
 * traffic, police and explosions. The scenes (house, TNT roadblock, mob war...) are in {@link Scenes}.
 */
public final class XoMod implements ModInitializer {
	@Override
	public void onInitialize() {
		Scenes.init();
	}
}
