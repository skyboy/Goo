package com.xeno.goo.tiles.machine;

import com.xeno.goo.GooMod;
import com.xeno.goo.aequivaleo.EntryHelper;
import com.xeno.goo.aequivaleo.Equivalencies;
import com.xeno.goo.aequivaleo.GooEntry;
import com.xeno.goo.aequivaleo.GooValue;
import com.xeno.goo.blocks.BlocksRegistry;
import com.xeno.goo.network.ChangeItemTargetPacket;
import com.xeno.goo.network.Networking;
import com.xeno.goo.network.SolidifierPoppedPacket;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.FluidHandlerHelper;
import net.minecraft.block.BlockState;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import java.text.NumberFormat;
import java.util.*;

import static net.minecraft.item.ItemStack.EMPTY;

public class SolidifierTile extends TileEntity implements ITickableTileEntity, ChangeItemTargetPacket.IChangeItemTargetReceiver
{
    private static final int HALF_SECOND_TICKS = 10;
    private static final int ONE_SECOND_TICKS = 20;
    // the item the solidifier currently "targets" is what it tries to make more of
    private Item target;
    private ItemStack targetStack;

    // when switching targets, a safety mechanism is designed to prevent accidental swaps
    // this is the stack the machine will transition to if the player confirms their input.
    private Item newTarget;
    private ItemStack newTargetStack;

    // timer that counts down after a change of target request. Failing to confirm the change reverts the selection.
    private int changeTargetTimer;

    // default timer span of 5 seconds should be plenty of time to swap an input?
    private static final int CHANGE_TARGET_TIMER_DURATION = 100;

    // the internal buffer gets filled when the machine is in the process of solidifying an item
    private Map<String, Double> fluidBuffer;
    private ItemEntity lastItem;

    public SolidifierTile() {
        super(Registry.SOLIDIFIER_TILE.get());
        target = Items.AIR;
        targetStack = EMPTY;
        newTarget = Items.AIR;
        newTargetStack = EMPTY;
        changeTargetTimer = 0;
        lastItem = null;
        fluidBuffer = new HashMap<>();
    }

    public Map<String, Double> fluidBuffer() {
        return fluidBuffer;
    }

    @Override
    public void tick() {
        handleTargetChangingCountdown();
        if (world == null) {
            return;
        }
        
        if (world.isRemote()) {
            return;
        }

        if (getBlockState().get(BlockStateProperties.POWERED)) {
            return;
        }

        if (lastItem != null) {
            if (lastItem.isAlive()) {
                return;
            }
            lastItem = null;
        }

        resolveTargetChangingCountdown();

        if (hasValidTarget()) {
            handleSolidifying();
        }
    }

    private void handleTargetChangingCountdown()
    {
        if (changeTargetTimer > 0) {
            changeTargetTimer--;
        }
    }

    private void resolveTargetChangingCountdown()
    {
        if (changeTargetTimer <= 0) {
            newTarget = Items.AIR;
            newTargetStack = EMPTY;
            sendTargetUpdate();
        }
    }

    private boolean hasValidTarget()
    {
        if (targetStack.isEmpty()) {
            return false;
        }
        return isValidTarget(target);
    }

    private void handleSolidifying()
    {
        GooEntry mapping = getItemEntry(target);
        if (mapping == null || mapping.isUnusable()) {
            return;
        }

        if (needsToDrainSources(mapping)) {
            tryDrainingSources(mapping);
        }

        if (hasBufferedEnough(mapping)) {
            depleteBufferByEntry(mapping);
            produceItem();
        }
    }

    private void produceItem()
    {
        if (world == null) {
            return;
        }

        ItemStack stack = targetStack.copy();

        if (!canPushStackForward(stack).isEmpty()) {
            spitStack(world, stack);
        }
    }

    private ItemStack canPushStackForward(ItemStack stack)
    {
        if (world == null) {
            return stack;
        }
        BlockPos pos = this.pos.offset(this.getHorizontalFacing());
        TileEntity tile = world.getTileEntity(pos);
        if (tile == null) {
            return stack;
        }
        LazyOptional<IItemHandler> lazy = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, this.getHorizontalFacing().getOpposite());
        if (!lazy.isPresent()) {
            return stack;
        }
        IItemHandler cap = lazy.orElseThrow(() -> new RuntimeException("Handler missing, this shouldn't have happened."));

        int slots = cap.getSlots();
        for (int i = 0; i < slots; i++) {
            ItemStack simulated = cap.insertItem(i, stack, true);
            if (!simulated.equals(stack, false)) {
                stack = cap.insertItem(i, stack, false);
            }
        }
        return stack;
    }

    private ItemStack spitStack(World world, ItemStack stack)
    {
        Vector3d nozzleLocation = getNozzleLocation();
        ItemEntity itemEntity = new ItemEntity(world, nozzleLocation.getX(), nozzleLocation.getY(), nozzleLocation.getZ(), stack);
        Vector3d spitVector = getSpitVector();

        if (world == null) {
            return stack;
        }
        if (stack.isEmpty()) {
            return stack;
        }
        itemEntity.setMotion(spitVector.getX(), spitVector.getY(), spitVector.getZ());
        itemEntity.setDefaultPickupDelay();
        world.addEntity(itemEntity);
        Networking.sendToClientsAround(new SolidifierPoppedPacket(this.getWorld().getDimensionKey(), getSpitVector(), getNozzleLocation()), Objects.requireNonNull(Objects.requireNonNull(world.getServer()).getWorld(world.getDimensionKey())), pos );
        lastItem = itemEntity;
        return EMPTY;
    }

    private Vector3d getNozzleLocation()
    {
        Direction direction = this.getBlockState().get(BlockStateProperties.HORIZONTAL_FACING);
        double d0 = pos.getX() + 0.5D + 0.7D * (double)direction.getXOffset();
        double d1 = pos.getY()+ 0.2D;
        double d2 = pos.getZ() + 0.5D + 0.7D * (double)direction.getZOffset();
        return new Vector3d(d0, d1, d2);
    }

    private Vector3d getSpitVector()
    {
        return new Vector3d(
                getHorizontalFacing().getDirectionVec().getX() * 0.05F,
                0F,
                getHorizontalFacing().getDirectionVec().getZ() * 0.05F);
    }

    private void depleteBufferByEntry(GooEntry mapping)
    {
        for(GooValue v : mapping.values()) {
            String key = v.getFluidResourceLocation();
            fluidBuffer.put(key, fluidBuffer.get(key) - v.amount());
        }
    }

    private boolean hasBufferedEnough(GooEntry mapping)
    {
        return mapping.values().stream().noneMatch(v -> !fluidBuffer.containsKey(v.getFluidResourceLocation()) || fluidBuffer.get(v.getFluidResourceLocation()) < v.amount());
    }

    private void tryDrainingSources(GooEntry mapping)
    {
        if (world == null) {
            return;
        }
        for(Direction d : getValidDirections()) {
            int[] workLeftThisGasket = {GooMod.config.gooProcessingRate()};
            LazyOptional<IFluidHandler> cap = FluidHandlerHelper.capabilityOfNeighbor(this, d);
            cap.ifPresent((c) ->
                    {
                        for (GooValue v : mapping.values()) {
                            workLeftThisGasket[0] = tryDrainingFluid(workLeftThisGasket[0], c, v);
                        }
                    }
            );
        }
    }

    private int tryDrainingFluid(int workLeftThisGasket, IFluidHandler cap, GooValue v)
    {
        if (workLeftThisGasket == 0) {
            return 0;
        }
        // have to ceiling here; fluid stacks are integers but values can be partial.
        // when we're short a partial unit, we just grab a full unit.
        double absentFluid = getAbsentFluid(v.getFluidResourceLocation(), v.amount());
        int maxDrain = (int)Math.min(Math.ceil(absentFluid), workLeftThisGasket);

        FluidStack drainTarget = getDrainTarget(v, maxDrain);
        if (drainTarget.isEmpty()) {
            return workLeftThisGasket;
        }

        // simulate
        if (cap.drain(drainTarget, IFluidHandler.FluidAction.SIMULATE).isEmpty()) {
            return workLeftThisGasket;
        }

        FluidStack result = cap.drain(drainTarget, IFluidHandler.FluidAction.EXECUTE);
        workLeftThisGasket -= result.getAmount();

        if (fluidBuffer.containsKey(v.getFluidResourceLocation())) {
            fluidBuffer.put(v.getFluidResourceLocation(), fluidBuffer.get(v.getFluidResourceLocation()) + result.getAmount());
        } else {
            fluidBuffer.put(v.getFluidResourceLocation(), (double)result.getAmount());
        }
        return workLeftThisGasket;
    }

    private FluidStack getDrainTarget(GooValue v, int maxDrain)
    {
        Fluid f = Registry.getFluid(v.getFluidResourceLocation());
        if (f == null) {
            return FluidStack.EMPTY;
        }
        return new FluidStack(f, Math.min(maxDrain, (int)Math.ceil(v.amount())));
    }

    private double getAbsentFluid(String fluidResourceLocation, double fluidAmount)
    {
        if (!fluidBuffer.containsKey(fluidResourceLocation)) {
            return fluidAmount;
        }

        return fluidAmount - fluidBuffer.get(fluidResourceLocation);
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

    private boolean needsToDrainSources(GooEntry mapping)
    {
        return mapping.values().stream().anyMatch(v -> !fluidBuffer.containsKey(v.getFluidResourceLocation()) || fluidBuffer.get(v.getFluidResourceLocation()) < v.amount());
    }

    private GooEntry getItemEntry(Item item)
    {
        if (world == null) {
            return GooEntry.UNKNOWN;
        }
        return Equivalencies.getEntry(world, item);
    }

    public Direction getHorizontalFacing()
    {
        return getBlockState().get(BlockStateProperties.HORIZONTAL_FACING);
    }

    public ItemStack getDisplayedItem()
    {
        return targetStack;
    }

    public void changeTargetItem(Item item) {
        // air is special, it means we're disabling the machine, essentially.
        // skip our returns if we're setting the target to nothing.
        if (!item.equals(Items.AIR)) {
            if (!isValidTarget(item)) {
                return;
            }
        }
        if (isEmptyTarget() || isChangingTargetValid(item)) {
            changeTarget(item);
        } else if (!item.equals(target)) {
            enterTargetSwapMode(item);
        }
    }

    private boolean isValidTarget(Item item)
    {
        return !getItemEntry(item).deniesSolidification();
    }

    private void enterTargetSwapMode(Item item)
    {
        changeTargetTimer = CHANGE_TARGET_TIMER_DURATION;
        newTarget = item;
        newTargetStack = EntryHelper.getSingleton(item);

        sendTargetUpdate();
    }

    private void sendTargetUpdate()
    {
        if (world == null || world.isRemote()) {
            return;
        }

        Networking.sendToClientsAround(new ChangeItemTargetPacket(world.getDimensionKey(), pos, targetStack, newTargetStack, changeTargetTimer), Objects.requireNonNull(Objects.requireNonNull(world.getServer()).getWorld(world.getDimensionKey())), pos);
    }

    private void changeTarget(Item item)
    {
        changeTargetTimer = 0;
        target = item;
        targetStack = EntryHelper.getSingleton(item);
        newTarget = Items.AIR;
        newTargetStack = EMPTY;

        sendTargetUpdate();
    }

    private boolean isChangingTargetValid(Item item)
    {
        return changeTargetTimer > 0 && item.equals(newTarget);
    }

    private boolean isEmptyTarget()
    {
        return target.equals(Items.AIR);
    }

    @Override
    public CompoundNBT write(CompoundNBT tag)
    {
        tag.put("goo", serializeGoo());
        tag.put("items", serializeItems());
        return super.write(tag);
    }

    @Override
    public void read(BlockState state, CompoundNBT tag)
    {
        super.read(state, tag);
        deserializeGoo(tag);
        deserializeItems(tag);
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

    private void deserializeGoo(CompoundNBT tag) {
        int size = tag.getInt("count");
        for(int i = 0; i < size; i++) {
            CompoundNBT gooTag = tag.getCompound("goo" + i);
            String key = gooTag.getString("key");
            double value = gooTag.getDouble("value");
            fluidBuffer.put(key, value);
        }
    }

    private CompoundNBT serializeItems()
    {
        CompoundNBT itemTag = new CompoundNBT();
        NonNullList<ItemStack> targetStackList = NonNullList.withSize(2, EMPTY);
        targetStackList.set(0, targetStack);
        targetStackList.set(1, newTargetStack);
        ItemStackHelper.saveAllItems(itemTag, targetStackList);
        itemTag.putInt("change_target_timer", this.changeTargetTimer);
        return itemTag;
    }

    private void deserializeItems(CompoundNBT tag)
    {
        CompoundNBT itemTag = tag.getCompound("items");
        NonNullList<ItemStack> targetStackList = NonNullList.withSize(2, EMPTY);
        ItemStackHelper.loadAllItems(itemTag, targetStackList);
        this.targetStack = targetStackList.get(0);
        this.target = this.targetStack.getItem();
        this.newTargetStack = targetStackList.get(1);
        this.newTarget = this.newTargetStack.getItem();
        this.changeTargetTimer = itemTag.getInt("change_target_timer");
    }

    public static void addInformation(ItemStack stack, List<ITextComponent> tooltip)
    {
        CompoundNBT stackTag = stack.getTag();
        if (stackTag == null) {
            return;
        }

        if (!stackTag.contains("BlockEntityTag")) {
            return;
        }

        CompoundNBT bulbTag = stackTag.getCompound("BlockEntityTag");

        if (bulbTag.contains("items")) {
            CompoundNBT gooTag = bulbTag.getCompound("items");
            NonNullList<ItemStack> targetStacks = NonNullList.withSize(2, EMPTY);
            ItemStackHelper.loadAllItems(gooTag, targetStacks);
            ItemStack tagTargetStack = targetStacks.get(0);

            if (!tagTargetStack.isEmpty()) {
                tooltip.add(new TranslationTextComponent("tooltip.goo.solidifying_target_preface").appendSibling(new TranslationTextComponent(tagTargetStack.getTranslationKey())));
            }
        }
    }

    @Override
    public void updateItemTarget(ItemStack target, ItemStack newTarget, int changeTargetTimer)
    {
        this.target = target.getItem();
        this.targetStack = target;
        this.newTarget = newTarget.getItem();
        this.newTargetStack = newTarget;
        this.changeTargetTimer = changeTargetTimer;
    }

    public boolean shouldFlashTargetItem()
    {
        // we may as well send the renderer a signal that it shouldn't render the item targeted, because there's nothing
        if (targetStack.isEmpty()) {
            return true;
        }

        if (world == null) {
            return false;
        }

        // half second intervals
        return changeTargetTimer > 0 && changeTargetTimer % ONE_SECOND_TICKS > HALF_SECOND_TICKS;
    }
}
