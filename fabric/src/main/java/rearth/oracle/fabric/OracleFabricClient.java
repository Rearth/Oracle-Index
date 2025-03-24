package rearth.oracle.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.CustomValue;
import rearth.oracle.OracleClient;

import java.util.*;

public final class OracleFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        OracleClient.init();
        OracleClient.setMainFolder(FabricLoader.getInstance().getConfigDir().toString());

        Collection<ModContainer> mods = FabricLoader.getInstance().getAllMods();
        HashMap<String, String> ids_urls = new HashMap<>();
        mods.forEach( mod -> {
            if(mod.getMetadata().getId().startsWith("fabric")){
                return;
            }
            //TODO tell people to put their github
            // url in the file
            //TODO verify this works
            String url;
            if(mod.getMetadata().getContact().get("sources").isPresent()){
                url = mod.getMetadata().getContact().get("sources").get();
            }else{
                url = "-";
            }
            ids_urls.put(mod.getMetadata().getId(), url);
        });
        OracleClient.setModIdAndUrls(ids_urls);
    }
}
