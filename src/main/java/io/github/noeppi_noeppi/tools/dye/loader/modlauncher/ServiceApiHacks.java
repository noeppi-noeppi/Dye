package io.github.noeppi_noeppi.tools.dye.loader.modlauncher;

import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServiceApiHacks {

    private static final Logger LOGGER = LogManager.getLogger(ServiceApiHacks.class);

    public static void registerFromServiceLayer(Class<? extends ILaunchPluginService> cls) {
        try {
            if (Launcher.INSTANCE == null) throw new IllegalStateException("No launcher");
            Field launchPluginsField = Launcher.class.getDeclaredField("launchPlugins");
            launchPluginsField.setAccessible(true);
            LaunchPluginHandler launchPlugins = (LaunchPluginHandler) launchPluginsField.get(Launcher.INSTANCE);
            Field pluginsField = LaunchPluginHandler.class.getDeclaredField("plugins");
            pluginsField.setAccessible(true);
            //noinspection unchecked
            Map<String, ILaunchPluginService> plugins = (Map<String, ILaunchPluginService>) pluginsField.get(launchPlugins);
            if (plugins.values().stream().noneMatch(p -> p.getClass() == cls)) {
                LOGGER.info("Dye: Adding launch plugin service after module init");
                
                ILaunchPluginService service = cls.getConstructor().newInstance();
                plugins.put(service.name(), service);
                
                List<Map<String, String>> mods = Launcher.INSTANCE.environment().getProperty(IEnvironment.Keys.MODLIST.get()).orElse(null);
                if (mods != null) {
                    Map<String, String> mod = new HashMap<>();
                    mod.put("name", service.name());
                    mod.put("type", "PLUGINSERVICE");
                    String fileName = cls.getProtectionDomain().getCodeSource().getLocation().getFile();
                    mod.put("file", fileName.substring(fileName.lastIndexOf('/')));
                    mods.add(mod);
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Dye: Failed to add launch plugin service");
        }
    }
}
