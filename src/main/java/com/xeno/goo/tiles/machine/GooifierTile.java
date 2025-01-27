package com.xeno.goo.tiles.machine;

import com.xeno.goo.GooMod;
import com.xeno.goo.aequivaleo.Equivalencies;
import com.xeno.goo.aequivaleo.GooEntry;
import com.xeno.goo.aequivaleo.GooValue;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.FluidHandlerHelper;
import com.xeno.goo.tiles.base.FluidHandlerInteractionAbstraction;
import net.minecraft.block.BlockState;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class GooifierTile extends FluidHandlerInteractionAbstraction implements ITickableTileEntity, ISidedInventory
{
    private Map<String, Double> fluidBuffer;
    private NonNullList<ItemStack> slots = NonNullList.withSize(5, ItemStack.EMPTY);
    private boolean isDoingStuff;
    public GooifierTile() {
        super(Registry.GOOIFIER_TILE.get());
        fluidBuffer = new TreeMap<>();
        isDoingStuff = false;
    }

    @Override
    public void tick() {
        if (world == null) {
            return;
        }

        if (world.isRemote()) {
            return;
        }

        if (getBlockState().get(BlockStateProperties.POWERED)) {
            return;
        }

        // buffered output means we have work left from our last item destruction where a fluidstack can be generated (>= 1f of any fluid)
        if (hasBufferedOutput()) {
            tryDistributingFluid();
        }

        // to make production seamless, return only if we are still pumping out goo. If we ran out of work, resume melting items.
        if (!hasBufferedOutput()) {
            for (ItemStack s : slots) {
                if (s.isEmpty()) {
                    continue;
                }
                GooEntry mapping = getEntryForItem(s);
                if (mapping == null) {
                    continue;
                }
                bufferOutput(mapping);
                s.setCount(s.getCount() - 1);
                break;
            }
        }
    }

    private GooEntry getEntryForItem(ItemStack e)
    {
        // String key = Objects.requireNonNull(s.getItem().getRegistryName()).toString();
        if (world == null) {
            return GooEntry.UNKNOWN;
        }

        GooEntry mapping = Equivalencies.getEntry(world, e.getItem());

        if (mapping.isUnusable()) {
            return GooEntry.UNKNOWN;
        }

        List<FluidStack> itemHandlerContents = FluidHandlerHelper.contentsOfItemStack(e);
        List<FluidStack> tileHandlerContents = FluidHandlerHelper.contentsOfTileStack(e);
        if (!itemHandlerContents.isEmpty()) {
            return mapping.addGooContentsToMapping(itemHandlerContents);
        } else if (!tileHandlerContents.isEmpty()) {
            return mapping.addGooContentsToMapping(tileHandlerContents);
        } else if (GooMod.config.canDamagedItemsBeGooified()) { // this is here to prevent "damage" containers from falsely reporting their damage values as durability
            // you may not melt down items that are damageable *and damaged*. Sorry not sorry
            if (e.isDamageable() && e.isDamaged()) {
                return mapping.scale((e.getMaxDamage() * 1d - e.getDamage()) / e.getMaxDamage());
            }
        } else {
            return GooEntry.UNKNOWN;
        }

        return mapping;
    }

    public double getTotalGoo() {
        double[] total = {0d};
        fluidBuffer.forEach((k, v) -> total[0] += v);
        return total[0];
    }

    public Map<String, Double> fluidBuffer() {
        return fluidBuffer;
    }

    private class DistributionState {
        boolean isFirstPass;
        int workRemaining;
        int workLastCycle;

        private DistributionState (boolean isFirstPass, int workRemaining, int workLastCycle) {
            this.isFirstPass = isFirstPass;
            this.workRemaining = workRemaining;
            this.workLastCycle = workLastCycle;
        }

        private void setNextPass() {
            this.isFirstPass = false;
        }

        private void addWork(int work) {
            workRemaining -= work;
            workLastCycle += work;
        }
    }
    private void tryDistributingFluid()
    {
        for(Direction d : getValidDirections()) {
            DistributionState[] state = {new DistributionState(true, GooMod.config.gooProcessingRate(), 0)};
            LazyOptional<IFluidHandler> cap = fluidHandlerInDirection(d);
            cap.ifPresent((c) -> state[0] = doDistribution(c, state[0]));
        }
    }

    private DistributionState doDistribution(IFluidHandler cap, DistributionState state)
    {
        while(state.workRemaining > 0 && (state.workLastCycle > 0 || state.isFirstPass)) {
            state.setNextPass();
            state.workLastCycle = 0;
            for (Map.Entry<String, Double> fluidInBuffer : fluidBuffer.entrySet()) {
                if (fluidInBuffer.getValue() < 1f) {
                    continue;
                }

                Fluid f = Registry.getFluid(fluidInBuffer.getKey());
                if (f == null) {
                    continue;
                }
                FluidStack s = new FluidStack(f, Math.min(state.workRemaining, (int) Math.floor(fluidInBuffer.getValue())));
                int fillResult = cap.fill(s, IFluidHandler.FluidAction.SIMULATE);
                if (fillResult > 0) {
                    fillResult = cap.fill(s, IFluidHandler.FluidAction.EXECUTE);
                } else {
                    continue;
                }
                state.addWork(fillResult);
                fluidBuffer.put(fluidInBuffer.getKey(), fluidInBuffer.getValue() - fillResult);

                if (state.workRemaining <= 0) break; // fastpath
            }
        }
        return state;
    }

    public static final Map<Direction, Direction[]> CACHED_DIRECTIONS = new HashMap<>();
    private Direction[] getValidDirections()
    {
        if (!CACHED_DIRECTIONS.containsKey(facing())) {
            CACHED_DIRECTIONS.put(facing(), new Direction[]{ Direction.UP,
                    (this.facing().getAxis() == Direction.Axis.Z ? Direction.EAST : Direction.SOUTH),
                    (this.facing().getAxis() == Direction.Axis.Z ? Direction.WEST : Direction.NORTH)
            });
        }
        return CACHED_DIRECTIONS.get(facing());
    }

    private Direction facing() {
        return getBlockState().get(BlockStateProperties.HORIZONTAL_FACING);
    }

    private void bufferOutput(GooEntry mapping)
    {
        for(GooValue v : mapping.values()) {
            String key = v.getFluidResourceLocation();
            if (fluidBuffer.containsKey(key)) {
                fluidBuffer.put(key, fluidBuffer.get(key) + v.amount());
            } else {
                fluidBuffer.put(key, v.amount());
            }
        }
    }

    private boolean hasBufferedOutput()
    {
        return fluidBuffer.size() > 0 && fluidBuffer.entrySet().stream().anyMatch(b -> b.getValue() >= 1d);
    }

    @Override
    public int[] getSlotsForFace(Direction side)
    {
        Direction facing = getBlockState().get(BlockStateProperties.HORIZONTAL_FACING);
        if (side == facing.getOpposite()) {
            return new int[]{0, 1, 2, 3, 4};
        }
        return new int[0];
    }

    @Override
    public boolean canInsertItem(int index, ItemStack itemStackIn, Direction direction)
    {
        Direction facing = getBlockState().get(BlockStateProperties.HORIZONTAL_FACING);
        if (direction != facing.getOpposite()) {
            return false;
        }

        if (itemStackIn.isDamageable() && itemStackIn.isDamaged()) {
            return false;
        }

        if (getEntryForItem(itemStackIn) == null) {
            return false;
        }

        for(ItemStack s : slots) {
            if (s.isEmpty()) {
                return true;
            }
            if (!s.equals(itemStackIn, false)) {
                continue;
            }
            if (s.getMaxStackSize() > s.getCount()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canExtractItem(int i, ItemStack stack, Direction direction)
    {
        if (direction == Direction.DOWN) {
            return slots.get(i).equals(stack, false);
        }

        return false;
    }

    @Override
    public int getSizeInventory()
    {
        return 5;
    }

    @Override
    public boolean isEmpty()
    {
        for(ItemStack s : slots) {
            if (!s.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getStackInSlot(int index)
    {
        return slots.get(index);
    }

    @Override
    public ItemStack decrStackSize(int index, int count)
    {
        if (slots.get(index).isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (slots.get(index).getCount() <= count) {
            return removeStackFromSlot(index);
        }

        slots.get(index).setCount(slots.get(index).getCount() - count);
        ItemStack result = slots.get(index).copy();
        result.setCount(count);
        return result;

    }

    @Override
    public ItemStack removeStackFromSlot(int index)
    {
        ItemStack result = slots.get(index).copy();
        slots.set(index, ItemStack.EMPTY);
        return result;
    }

    @Override
    public void setInventorySlotContents(int i, ItemStack stack)
    {
        slots.set(i, stack);
    }

    @Override
    public boolean isUsableByPlayer(PlayerEntity player)
    {
        return false;
    }

    @Override
    public void clear()
    {
        // ???
    }

    @Override
    public CompoundNBT getUpdateTag()
    {
        return this.write(new CompoundNBT());
    }

    private CompoundNBT serializeGoo()  {
        CompoundNBT tag = new CompoundNBT();
        tag.putInt("count", fluidBuffer.size());
        int index = 0;
        for(Map.Entry<String, Double> e : fluidBuffer.entrySet()) {
            CompoundNBT gooTag = new CompoundNBT();
            gooTag.putString("key", e.getKey());
            gooTag.putDouble("value", e.getValue());
            tag.put("goo" + index, gooTag);
            index++;
        }
        return tag;
    }

    private CompoundNBT serializeItems()
    {
        CompoundNBT itemTag = new CompoundNBT();
        ItemStackHelper.saveAllItems(itemTag, slots);
        return itemTag;
    }

    private void deserializeGoo(CompoundNBT tag) {
        int size = tag.getInt("count");
        for(int i = 0; i < size; i++) {
            CompoundNBT gooTag = tag.getCompound("goo" + i);
            String key = gooTag.getString("key");
            double value = gooTag.getDouble("value");
            fluidBuffer.put(key, value);
        }
    }

    private void deserializeItems(CompoundNBT tag)
    {
        CompoundNBT itemTag = tag.getCompound("items");
        this.slots = NonNullList.withSize(this.getSizeInventory(), ItemStack.EMPTY);
        ItemStackHelper.loadAllItems(itemTag, this.slots);
    }

    @Override
    public CompoundNBT write(CompoundNBT tag)
    {
        tag.put("items", serializeItems());
        tag.put("goo", serializeGoo());
        tag.putBoolean("is_doing_stuff", isDoingStuff);
        return super.write(tag);
    }

    @Override
    public void read(BlockState state, CompoundNBT tag)
    {
        super.read(state, tag);
        deserializeItems(tag);
        deserializeGoo(tag);
        isDoingStuff = tag.getBoolean("is_doing_stuff");
    }

    public void spewItems()
    {
        for (ItemStack s : slots) {
            ItemEntity itemEntity = new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), s);
            itemEntity.setDefaultPickupDelay();
            world.addEntity(itemEntity);
        }
    }
}
