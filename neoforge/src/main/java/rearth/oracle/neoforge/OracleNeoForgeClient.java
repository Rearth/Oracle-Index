package rearth.oracle.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;

import net.neoforged.fml.loading.FMLConfig;
import rearth.oracle.Oracle;
import rearth.oracle.OracleClient;

import java.util.HashMap;

@Mod(value = Oracle.MOD_ID, dist = Dist.CLIENT)
public final class OracleNeoForgeClient {
    public OracleNeoForgeClient() {
        // Run our common setup.
        OracleClient.init();
        OracleClient.setMainDownloadsFolder(FMLConfig.defaultConfigPath());
        HashMap<String, String> ids_urls = new HashMap<>();

        ModList.get().getMods().forEach( mod -> {
            String url = "-";
            if(mod.getModURL().isPresent()){
                url = mod.getModURL().get().toString();
            }
            //TODO tell people to put their github link inside neoforge.mods.toml "modUrl" thingy
            ids_urls.put(mod.getModId(), url);
        });
        OracleClient.setModIdAndUrls(ids_urls);
    }
}
