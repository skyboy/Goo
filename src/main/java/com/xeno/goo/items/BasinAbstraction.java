package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.overlay.RayTraceTargetSource;
import com.xeno.goo.tiles.FluidHandlerHelper;
import com.xeno.goo.tiles.GooContainerAbstraction;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.ItemFluidContainer;

import java.util.function.Supplier;

public class BasinAbstraction extends ItemFluidContainer
{
    public BasinAbstraction(int capacity)
    {
        super(new Item.Properties()
                .maxStackSize(1)
                .isBurnable()
                .group(GooMod.ITEM_GROUP), capacity);
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, CompoundNBT nbt)
    {
        return new BasinAbstractionCapability(stack, this.capacity);
    }

    @Override
    public ActionResultType onItemUseFirst(ItemStack stack, ItemUseContext context)
    {
        IFluidHandlerItem cap = FluidHandlerHelper.capability(stack);
        if (cap == null) {
            return ActionResultType.PASS;
        }

        ActionResultType result = tryBlockInteraction(cap, context);
        return result;
    }

    private ActionResultType tryBlockInteraction(IFluidHandlerItem cap, ItemUseContext context)
    {
        TileEntity t = context.getWorld().getTileEntity(context.getPos());
        if (!(t instanceof GooContainerAbstraction)) {
            return ActionResultType.PASS;
        }

        // special caller for getting the "right" capability, this is mainly for *mixers* having two caps
        IFluidHandler tileCap = ((GooContainerAbstraction)t).getCapabilityFromRayTraceResult(context.getHitVec(), context.getFace(), RayTraceTargetSource.BASIN);

        FluidStack hitFluid = ((GooContainerAbstraction) t).getGooFromTargetRayTraceResult(context.getHitVec(), context.getFace(), RayTraceTargetSource.BASIN);
        // if cap is empty try a drain.
        if (cap.getFluidInTank(0).isEmpty()) {
            return tryFillingEmptyBasin(context.getWorld(), context.getHitVec(), context.getPlayer(), cap, tileCap, hitFluid);
        }

        boolean isAltBehavior = context.getPlayer() != null && context.getPlayer().isSneaking();

        // ordinarily the basin just empties and fills exclusively in a toggle state.
        // holding [sneak] changes the behavior to try to fill the basin first.
        // the fluid we contain isn't the type hit or it is, but our receptacle is full so the intent is inverted.
        if (!isAltBehavior || !cap.getFluidInTank(0).isFluidEqual(hitFluid) || cap.getFluidInTank(0).getAmount() == cap.getTankCapacity(0)) {
            return tryFillingGooContainer(context.getWorld(), context.getHitVec(), context.getPlayer(), cap, tileCap, hitFluid);
        }

        return tryFillingBasinWithSameFluid(context.getWorld(), context.getHitVec(), context.getPlayer(), cap, tileCap, hitFluid);
    }

    private ActionResultType tryFillingGooContainer(World world, Vector3d pos, PlayerEntity player,
            IFluidHandlerItem cap, IFluidHandler tileCap, FluidStack hitFluid)
    {
        FluidStack sendingFluid = cap.getFluidInTank(0).copy();
        int amountSent = tileCap.fill(sendingFluid, IFluidHandler.FluidAction.SIMULATE);
        if (amountSent == 0) {
            return ActionResultType.PASS;
        }
        if (amountSent < sendingFluid.getAmount()) {
            sendingFluid.setAmount(amountSent);
        }
        FluidStack drainResult = cap.drain(sendingFluid, IFluidHandler.FluidAction.SIMULATE);
        if (drainResult.isEmpty()) {
            return ActionResultType.PASS;
        }
        if (!world.isRemote()) {
            tileCap.fill(cap.drain(sendingFluid, IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
        }
        if (player != null) {
            world.playSound(player, pos.x, pos.y, pos.z, SoundEvents.ITEM_BUCKET_EMPTY_LAVA,
                    SoundCategory.PLAYERS, 1.0f, world.rand.nextFloat() * 0.5f + 0.5f);
        } else {
            world.playSound(pos.x, pos.y, pos.z, SoundEvents.ITEM_BUCKET_EMPTY_LAVA,
                    SoundCategory.PLAYERS, 1.0f, world.rand.nextFloat() * 0.5f + 0.5f, false);
        }
        return ActionResultType.SUCCESS;
    }

    private ActionResultType tryFillingBasinWithSameFluid(World world, Vector3d pos, PlayerEntity player,
            IFluidHandlerItem cap, IFluidHandler tileCap, FluidStack hitFluid)
    {
        int amountRequested = cap.getTankCapacity(0) - cap.getFluidInTank(0).getAmount();
        FluidStack requestFluid = hitFluid.copy();
        requestFluid.setAmount(Math.min(requestFluid.getAmount(), amountRequested));
        FluidStack drainResult = tileCap.drain(requestFluid, IFluidHandler.FluidAction.SIMULATE);
        if (drainResult.isEmpty()) {
            return ActionResultType.PASS;
        }
        int fillResult = cap.fill(drainResult, IFluidHandler.FluidAction.SIMULATE);
        if (fillResult == 0) {
            return ActionResultType.PASS;
        }
        if (!world.isRemote()) {
            cap.fill(tileCap.drain(requestFluid, IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
        }
        if (player != null) {
            world.playSound(player, pos.x, pos.y, pos.z, SoundEvents.ITEM_BUCKET_EMPTY_LAVA,
                    SoundCategory.PLAYERS, 1.0f, world.rand.nextFloat() * 0.5f + 0.5f);
        } else {
            world.playSound(pos.x, pos.y, pos.z, SoundEvents.ITEM_BUCKET_EMPTY_LAVA,
                    SoundCategory.PLAYERS, 1.0f, world.rand.nextFloat() * 0.5f + 0.5f, false);
        }
        return ActionResultType.SUCCESS;
    }

    private ActionResultType tryFillingEmptyBasin(World world, Vector3d pos, PlayerEntity player,
            IFluidHandlerItem cap, IFluidHandler tileCap, FluidStack hitFluid)
    {
        FluidStack requestFluid = hitFluid.copy();
        if (requestFluid.getAmount() > cap.getTankCapacity(0)) {
            requestFluid.setAmount(cap.getTankCapacity(0));
        }
        FluidStack result = tileCap.drain(requestFluid, IFluidHandler.FluidAction.SIMULATE);
        if (result.isEmpty()) {
            return ActionResultType.PASS;
        }
        int fillResult = cap.fill(result, IFluidHandler.FluidAction.SIMULATE);
        if (fillResult == 0) {
            return ActionResultType.PASS;
        }

        if (!world.isRemote()) {
            cap.fill(tileCap.drain(requestFluid, IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
        }
        if (player != null) {
            world.playSound(player, pos.x, pos.y, pos.z, SoundEvents.ITEM_BUCKET_EMPTY_LAVA,
                    SoundCategory.PLAYERS, 1.0f, world.rand.nextFloat() * 0.5f + 0.5f);
        } else {
            world.playSound(pos.x, pos.y, pos.z, SoundEvents.ITEM_BUCKET_EMPTY_LAVA,
                    SoundCategory.PLAYERS, 1.0f, world.rand.nextFloat() * 0.5f + 0.5f, false);
        }
        return ActionResultType.SUCCESS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World worldIn, PlayerEntity playerIn, Hand handIn)
    {
        return ActionResult.resultPass(playerIn.getHeldItem(handIn));
    }
}