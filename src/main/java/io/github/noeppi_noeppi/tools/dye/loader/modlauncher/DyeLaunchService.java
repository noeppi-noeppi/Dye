package io.github.noeppi_noeppi.tools.dye.loader.modlauncher;

import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.NamedPath;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import io.github.noeppi_noeppi.tools.dye.loader.DyeLoader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.util.EnumSet;

public class DyeLaunchService implements ILaunchPluginService {

    private static final EnumSet<Phase> TRANSFORM = EnumSet.of(Phase.AFTER);
    private static final EnumSet<Phase> DISCARD = EnumSet.noneOf(Phase.class);
    
    private final DyeLoader loader;

    public DyeLaunchService() {
        this.loader = new DyeLoader();
    }

    @Override
    public String name() {
        return "dye";
    }

    public DyeLoader getLoader() {
        return loader;
    }

    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean empty) {
        String cls = classType.getInternalName();
        if (cls.startsWith("java/") || cls.startsWith("javax/") || cls.startsWith("scala/")
                || cls.startsWith("cpw/mods/modlauncher/") || cls.startsWith("com/sun/")
                || cls.startsWith("sun/")|| cls.startsWith("jdk/internal/")) {
            return DISCARD;
        } else {
            return TRANSFORM;
        }
    }

    @Override
    public boolean processClass(Phase phase, ClassNode cls, Type classType) {
        return this.loader.transform(cls);
    }

    @Override
    public void initializeLaunch(ITransformerLoader loader, NamedPath[] specialPaths) {
        if (Launcher.INSTANCE == null) throw new IllegalStateException("No launcher");
        IModuleLayerManager manager = Launcher.INSTANCE.environment().findModuleLayerManager().orElse(null);
        if (manager == null) throw new IllegalStateException("Can't initialise dye, module layer manager not found");
        ModuleLayer gameLayer = manager.getLayer(IModuleLayerManager.Layer.GAME).orElse(null);
        if (gameLayer == null) throw new IllegalStateException("Can't initialise dye, game layer not ready");
        this.loader.from(gameLayer);
        this.loader.printInfo();
    }
}
