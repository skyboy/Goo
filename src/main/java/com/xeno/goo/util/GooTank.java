package com.xeno.goo.util;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import java.util.function.IntSupplier;

public class GooTank extends IGooTank {

	@Nonnull
	protected FluidStack tank = FluidStack.EMPTY;

	public GooTank(IntSupplier capacity) {

		super(capacity, false);
	}

	@Override
	protected void readFromNBTInternal(CompoundNBT nbt) {

		// we save and load with an array for inter-op with the multi-tanks
		tank = FluidStack.loadFluidStackFromNBT(nbt.getList("Tanks", NBT.TAG_COMPOUND).getCompound(0));
	}

	@Override
	protected void writeToNBTInternal(CompoundNBT nbt) {

		// we save and load with an array for inter-op with the multi-tanks
		ListNBT val = new ListNBT();
		val.add(tank.writeToNBT(new CompoundNBT()));
		nbt.put("Tanks", val);
	}

	@Override
	public void readFromPacket(PacketBuffer buf) {

		// we save and load with an array for inter-op with the multi-tanks
		buf.readVarInt();
		tank = readFluidStack(buf);
	}

	@Override
	public void writeToPacket(PacketBuffer buf) {

		// we save and load with an array for inter-op with the multi-tanks
		buf.writeVarInt(1);
		writeFluidStack(buf, tank);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getTanks() {

		return 1;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isEmpty() {

		return tank.isEmpty();
	}

	@Override
	public int getTotalContents() {

		return tank.getAmount();
	}

	@Override
	public int getTotalCapacity() {

		return capacity.getAsInt();
	}

	/**
	 * {@inheritDoc}
	 */
	@Nonnull
	@Override
	public FluidStack getFluidInTankInternal(int index) {

		if (index != 0)
			throw new IndexOutOfBoundsException("Index (" + index + ") is out of bounds (1)");
		return tank;
	}

	@Override
	public void empty() {
		tank.setAmount(0);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getTankCapacity(int tank) {

		getFluidInTankInternal(tank);
		return capacity.getAsInt();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int fill(FluidStack resource, FluidAction action) {

		final int tankAmt;
		{
			final FluidStack tank = getFluidInTankInternal(0);
			tankAmt = tank.getAmount();
			if (resource == null || resource.isEmpty() || (tankAmt != 0 && !resource.isFluidEqual(tank)) || !filter.test(resource))
				return 0;
		}

		final int accept = Math.min(resource.getAmount(), capacity.getAsInt() - tankAmt);
		if (accept > 0 && action.execute()) {

			final FluidStack tank = this.tank;
			if (tank.isEmpty())
				this.tank = new FluidStack(resource.getRawFluid(), accept, resource.getTag());
			else
				tank.grow(accept);
			onChange();
		}
		return accept;
	}

	/**
	 * {@inheritDoc}
	 */
	@Nonnull
	@Override
	public FluidStack drain(FluidStack resource, FluidAction action) {

		if (resource == null || resource.isEmpty() || !resource.isFluidEqual(tank))
			return FluidStack.EMPTY;

		return drain(resource.getAmount(), action);
	}

	/**
	 * {@inheritDoc}
	 */
	@Nonnull
	@Override
	public FluidStack drain(int maxDrain, FluidAction action) {

		final int tankAmt;
		{
			final FluidStack tank = getFluidInTankInternal(0);
			tankAmt = tank.getAmount();
			if (tankAmt <= 0)
				return FluidStack.EMPTY;
		}

		final int accept = Math.min(maxDrain, tankAmt);
		if (accept > 0 && action.execute()) {
			tank.shrink(accept);
			onChange();
		}

		return new FluidStack(tank.getRawFluid(), accept, tank.getTag());
	}
}
