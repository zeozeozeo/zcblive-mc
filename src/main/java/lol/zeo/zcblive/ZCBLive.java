package lol.zeo.zcblive;

import net.fabricmc.api.ModInitializer;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class ZCBLive implements ModInitializer {
	public static final String MOD_ID = "zcb-live";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.debug("Initializing {}", MOD_ID);
	}
}
