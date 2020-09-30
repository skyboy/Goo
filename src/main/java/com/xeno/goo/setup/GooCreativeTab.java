package com.xeno.goo.setup;

import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

import java.util.Comparator;

public class GooCreativeTab extends ItemGroup
{
    public GooCreativeTab(String label)
    {
        super(label);
    }

    @Override
    public ItemStack createIcon()
    {
        return new ItemStack(Registry.SOLIDIFIER.get());
    }

    public static final NonNullList<ItemStack> sortedCreativeItems = NonNullList.create();
    @Override
    public void fill(NonNullList<ItemStack> items)
    {
        if (sortedCreativeItems.size() == 0) {
            super.fill(items);
            items.sort(Comparator.comparing(i -> i.getItem().getRegistryName()));
            sortedCreativeItems.addAll(items);
        } else {
            items.addAll(sortedCreativeItems);
        }
    }
}