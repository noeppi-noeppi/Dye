package io.github.noeppi_noeppi.tools.dye.loader.modlauncher;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

// Only use of this class is to get called as early as possible to
// register a launch plugin service without access to the boot layer.
public class DyeTransformService implements ITransformationService {
    
    public DyeTransformService() {
        ServiceApiHacks.registerFromServiceLayer(DyeLaunchService.class);
    }
    
    @Override
    public String name() {
        return "dye";
    }

    @Override
    public void initialize(IEnvironment env) {
        //
    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {
        //
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List<ITransformer> transformers() {
        return List.of();
    }
}
