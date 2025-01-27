package com.xeno.goo.aequivaleo;

import com.ldtteam.aequivaleo.api.plugin.AequivaleoPlugin;
import com.ldtteam.aequivaleo.api.plugin.AequivaleoPlugin.Instance;
import com.ldtteam.aequivaleo.api.plugin.IAequivaleoPlugin;
import com.xeno.goo.events.TargetingHandler;
import jei.GooRecipeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@AequivaleoPlugin
public class GooAequivaleoPlugin implements IAequivaleoPlugin {

    @Instance
    public static final GooAequivaleoPlugin INSTANCE = new GooAequivaleoPlugin();

	@Override
	public String getId() {

		return "Goo";
	}

	@Override
	public void onReloadStartedFor(final ServerWorld world) {

		Equivalencies.resetFurnaceProducts(world);
	}

    @Override
    @OnlyIn(Dist.CLIENT)
    public void onDataSynced(RegistryKey<World> worldRegistryKey) {

		// TODO: this is part of a nicer unavailable gooification display
		// TODO: this is a very simple first-pass
		TargetingHandler.isGooReady = true;

		GooRecipeManager.instance.seedRecipes(worldRegistryKey);
    }

}
