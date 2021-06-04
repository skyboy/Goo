package com.xeno.goo.jei;

import com.ldtteam.aequivaleo.api.compound.CompoundInstance;
import com.ldtteam.aequivaleo.api.compound.container.ICompoundContainer;
import com.xeno.goo.aequivaleo.Equivalencies;
import com.xeno.goo.aequivaleo.GooEntry;
import com.xeno.goo.library.CrucibleRecipe;
import com.xeno.goo.library.CrucibleRecipes;
import com.xeno.goo.library.MixerRecipe;
import com.xeno.goo.library.MixerRecipes;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.util.LinkedHashList;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocus.Mode;
import mezz.jei.api.recipe.advanced.IRecipeManagerPlugin;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class GooRecipeManager implements IRecipeManagerPlugin {

	private static final GooConversionWrapper INVALID_CONVERSION_WRAPPER = new GooConversionWrapper();
	private static Map<ICompoundContainer<?>, Set<CompoundInstance>> aequivaleoCache = new HashMap<>();
	private static LinkedHashList<GooConversionWrapper> conversionWrappers = new LinkedHashList<>();
	public static GooRecipeManager instance = new GooRecipeManager();

	@Override
	public <V> List<ResourceLocation> getRecipeCategoryUids(IFocus<V> focus) {
		List<ResourceLocation> validUids = new ArrayList<>();
		if (getRecipes(category(GooifierRecipeCategory.UID), focus).size() > 0) {
			validUids.add(GooifierRecipeCategory.UID);
		}
		if (getRecipes(category(SolidifierRecipeCategory.UID), focus).size() > 0) {
			validUids.add(SolidifierRecipeCategory.UID);
		}
		if (getRecipes(category(CrucibleRecipeCategory.UID), focus).size() > 0) {
			validUids.add(CrucibleRecipeCategory.UID);
		}
		if (getRecipes(category(MixerRecipeCategory.UID), focus).size() > 0) {
			validUids.add(MixerRecipeCategory.UID);
		}
		return validUids;
	}

	@Override
	public <T, V> List<T> getRecipes(IRecipeCategory<T> recipeCategory, IFocus<V> focus) {
		if (recipeCategory.getUid().equals(GooifierRecipeCategory.UID)) {
			return gooifierRecipes(focus);
		}

		if (recipeCategory.getUid().equals(SolidifierRecipeCategory.UID)) {
			return solidifierRecipes(focus);
		}

		if (recipeCategory.getUid().equals(CrucibleRecipeCategory.UID)) {
			return crucibleRecipes(focus);
		}

		if (recipeCategory.getUid().equals(MixerRecipeCategory.UID)) {
			return mixerRecipes(focus);
		}

		return Collections.emptyList();
	}

	private <T, V> List<T> mixerRecipes(IFocus<V> focus) {
		List<T> mixerMatches = new ArrayList<>();
		if (focus.getValue() instanceof GooIngredient) {
			if (focus.getMode().equals(Mode.INPUT)) {
				GooIngredient stack = ((GooIngredient) focus.getValue());
				mixerMatches.addAll((List<T>) getRecipes(category(MixerRecipeCategory.UID))
						.parallelStream().filter(r -> Arrays.stream(((JeiMixerRecipe) r).inputs()).parallel().anyMatch(m -> m.fluidKey().equals(stack.fluidKey())))
						.collect(Collectors.toList())
				);
			} else if (focus.getMode().equals(Mode.OUTPUT)) {
				GooIngredient stack = ((GooIngredient) focus.getValue());
				mixerMatches.addAll((List<T>) getRecipes(category(MixerRecipeCategory.UID))
						.parallelStream().filter(r -> ((JeiMixerRecipe) r).output().fluidKey().equals(stack.fluidKey()))
						.collect(Collectors.toList())
				);
			}
		}
		return mixerMatches;
	}

	private <T, V> List<T> crucibleRecipes(IFocus<V> focus) {
		List<T> crucibleMatches = new ArrayList<>();
		if (focus.getValue() instanceof GooIngredient) {
			if (focus.getMode().equals(Mode.INPUT)) {
				GooIngredient stack = ((GooIngredient) focus.getValue());
				crucibleMatches.addAll((List<T>) getRecipes(category(CrucibleRecipeCategory.UID))
						.parallelStream()
						.filter(r -> ((JeiCrucibleRecipe) r).input().fluidKey().equals(stack.fluidKey()))
						.collect(Collectors.toList())
				);
			} else if (focus.getMode().equals(Mode.OUTPUT)) {
				GooIngredient stack = ((GooIngredient) focus.getValue());
				crucibleMatches.addAll((List<T>) getRecipes(category(CrucibleRecipeCategory.UID))
						.parallelStream().filter(r -> ((JeiCrucibleRecipe) r).output().fluidKey().equals(stack.fluidKey()))
						.collect(Collectors.toList())
				);
			}
		}
		return crucibleMatches;
	}

	private IRecipeCategory category(ResourceLocation uid) {
		return GooJeiPlugin.cachedJeiRunTime.getRecipeManager().getRecipeCategory(uid);
	}

	@NotNull
	private <T, V> List<T> solidifierRecipes(IFocus<V> focus) {

		List<T> solidifierMatches = new ArrayList<>();
		if (focus.getMode().equals(Mode.INPUT)) {
			if (focus.getValue() instanceof GooIngredient) {
				GooIngredient stack = ((GooIngredient) focus.getValue());
				solidifierMatches.addAll((List<T>) getJeiSolidifierRecipes()
						.parallelStream().filter(k -> k.inputs().parallelStream().anyMatch(v -> v.fluidKey().equals(stack.fluidKey())))
						.collect(Collectors.toList()));
			}
		} else if (focus.getMode().equals(Mode.OUTPUT)) {
			if (focus.getValue() instanceof ItemStack) {
				ItemStack stack = ((ItemStack) focus.getValue());
				solidifierMatches.addAll((List<T>) getJeiSolidifierRecipes()
						.parallelStream().filter(k -> k.output().isItemEqual(stack))
						.collect(Collectors.toList()));
			}
			if (focus.getValue() instanceof Item) {
				Item stack = ((Item) focus.getValue());
				solidifierMatches.addAll((List<T>) getJeiSolidifierRecipes()
						.parallelStream().filter(k -> k.output().getItem().equals(stack))
						.collect(Collectors.toList()));
			}
		}
		solidifierMatches.sort((a, b) -> SolidifierRecipe.sortByInputFocus(a, b, focus));
		return solidifierMatches;
	}

	@NotNull
	private <T, V> List<T> gooifierRecipes(IFocus<V> focus) {

		List<T> gooifierMatches = new ArrayList<>();
		if (focus.getMode().equals(Mode.INPUT)) {
			if (focus.getValue() instanceof ItemStack) {
				ItemStack stack = ((ItemStack) focus.getValue());
				gooifierMatches.addAll((List<T>) getJeiGooifierRecipes()
						.parallelStream().filter(k -> k.input().isItemEqual(stack))
						.collect(Collectors.toList()));
			}
		} else if (focus.getMode().equals(Mode.OUTPUT)) {
			if (focus.getValue() instanceof GooIngredient) {
				GooIngredient stack = ((GooIngredient) focus.getValue());
				gooifierMatches.addAll((List<T>) getJeiGooifierRecipes()
						.parallelStream().filter(k -> k.outputs().parallelStream().anyMatch(v -> v.fluidKey().equals(stack.fluidKey())))
						.collect(Collectors.toList()));
			}
		}
		gooifierMatches.sort((a, b) -> GooifierRecipe.sortByOutputFocus(a, b, focus));
		return gooifierMatches;
	}

	@Override
	public <T> List<T> getRecipes(IRecipeCategory<T> recipeCategory) {
		if (recipeCategory.getUid().equals(SolidifierRecipeCategory.UID)) {
			return (List<T>)getJeiSolidifierRecipes();
		}
		if (recipeCategory.getUid().equals(GooifierRecipeCategory.UID)) {
			return (List<T>)getJeiGooifierRecipes();
		}
		if (recipeCategory.getUid().equals(CrucibleRecipeCategory.UID)) {
			return convertCrucibleRecipesToJeiFormat(CrucibleRecipes.recipes());
		}
		if (recipeCategory.getUid().equals(MixerRecipeCategory.UID)) {
			return convertMixerRecipesToJeiFormat(MixerRecipes.recipes());
		}
		return new ArrayList<>();
	}

	private List<GooifierRecipe> getJeiGooifierRecipes() {
		initConversionWrappers();

		return validGooifierRecipes();
	}

	private List<GooifierRecipe> validGooifierRecipes() {
		return conversionWrappers.parallelStream().filter(c -> c != INVALID_CONVERSION_WRAPPER).map(GooConversionWrapper::toGooifierRecipe).collect(Collectors.toList());
	}

	private boolean shouldTryReseedingConversionWrappers() {
		return conversionWrappers.isEmpty() || conversionWrappers.size() < aequivaleoCache().size();
	}

	private void seedConversionWrappers() {
		conversionWrappers.addAll(convertToConversionWrappers(aequivaleoCache()));
	}

	private Collection<? extends GooConversionWrapper> convertToConversionWrappers(Map<ICompoundContainer<?>, Set<CompoundInstance>> aequivaleoCache) {
		return aequivaleoCache.entrySet().parallelStream().map((e) -> conversionWrapper(e.getKey(), e.getValue())).collect(Collectors.toList());
	}

	private List<SolidifierRecipe> getJeiSolidifierRecipes() {
		initConversionWrappers();

		return validSolidifierRecipes();
	}

	private void initConversionWrappers() {
		if (shouldTryReseedingConversionWrappers()) {
			seedConversionWrappers();
		}
	}

	private List<SolidifierRecipe> validSolidifierRecipes() {
		return conversionWrappers.parallelStream().filter(c -> c != INVALID_CONVERSION_WRAPPER).map(GooConversionWrapper::toSolidifierRecipe).collect(Collectors.toList());
	}

	private Map<ICompoundContainer<?>, Set<CompoundInstance>> aequivaleoCache() {
		if (aequivaleoCache.isEmpty()) {
			Equivalencies.cache(worldKey()).getAllDataOf(Registry.GOO_GROUP.get()).entrySet().parallelStream().forEach((e) -> {
					if (e.getKey().getContents() instanceof ItemStack) {
						aequivaleoCache.put(e.getKey(), e.getValue());
					}
				}
			);
		}
		return aequivaleoCache;
	}

	private GooConversionWrapper conversionWrapper(ICompoundContainer<?> k, Set<CompoundInstance> v) {
		return new GooConversionWrapper(((ItemStack)k.getContents()).getItem(), new GooEntry(worldKey(), ((ItemStack)k.getContents()).getItem(), v));
	}

	private <T> List<T> convertMixerRecipesToJeiFormat(List<MixerRecipe> recipes) {
			List<T> result = new ArrayList<>();
			recipes.forEach(r -> result.add((T) convertMixerRecipeToJei(r)));
			return result;
	}

	private Object convertMixerRecipeToJei(MixerRecipe r) {
		return new JeiMixerRecipe(
				new GooIngredient(r.inputs().get(0).getAmount(), r.inputs().get(0).getFluid().getRegistryName()),
				new GooIngredient(r.inputs().get(1).getAmount(), r.inputs().get(1).getFluid().getRegistryName()),
				new GooIngredient(r.output().getAmount(), r.output().getFluid().getRegistryName())
		);
	}

	private <T> List<T> convertCrucibleRecipesToJeiFormat(List<CrucibleRecipe> recipes) {
		List<T> result = new ArrayList<>();
		recipes.forEach(r -> result.add((T) convertCrucibleRecipeToJei(r)));
		return result;
	}

	private JeiCrucibleRecipe convertCrucibleRecipeToJei(CrucibleRecipe r) {
		return new JeiCrucibleRecipe(
				new GooIngredient(r.input().getAmount(), r.input().getFluid().getRegistryName()),
				new GooIngredient(r.output().getAmount(), r.output().getFluid().getRegistryName())
		);
	}

	private RegistryKey<World> worldKey() {
		return Minecraft.getInstance().world.getDimensionKey();
	}
}