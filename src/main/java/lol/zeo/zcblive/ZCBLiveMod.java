package lol.zeo.zcblive;

import lol.zeo.zcblive.client.ZcbForgeClient;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
    modid = ZCBLiveMod.MOD_ID,
    name = ZCBLiveMod.MOD_NAME,
    version = ZCBLiveMod.VERSION
)
public final class ZCBLiveMod {
    public static final String MOD_ID = "zcblive";
    public static final String MOD_NAME = "ZCB Live";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private ZcbForgeClient forgeClient;

    @EventHandler
    public void onInit(FMLInitializationEvent event) {
        if (!event.getSide().isClient()) {
            return;
        }

        forgeClient = new ZcbForgeClient();
        forgeClient.initialize();
        LOGGER.info("Initialized {} for Forge {}", MOD_NAME, event.getSide());
    }
}
