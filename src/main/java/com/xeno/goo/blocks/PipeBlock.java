package com.xeno.goo.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.state.EnumProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.util.Direction;
import net.minecraft.util.Direction.AxisDirection;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IBlockReader;

import java.util.Locale;

public class PipeBlock extends Block {

	public static enum ConnectionState implements IStringSerializable {
		DISCONNECTED,
		CONNECTED_PLAIN,
		CONNECTED_ATTACHMENT,
		;

		byte connected = (byte) (ordinal() == 0 ? 0 : 1);
		byte attachments = (byte) (ordinal() == 2 ? 1 : 0);

		@Override
		public String getString() {

			return name().toLowerCase(Locale.ROOT);
		}

		private static final ConnectionState[] DISCONNECTED_STATES = new ConnectionState[] { DISCONNECTED },
				CONNECTED_STATES = new ConnectionState[] { CONNECTED_PLAIN, CONNECTED_ATTACHMENT },
				ATTACHMENT_STATES = new ConnectionState[] { CONNECTED_ATTACHMENT };

		public static ConnectionState[] getConnectedStates() {

			return CONNECTED_STATES;
		}

		public static ConnectionState[] getDisconnectedStates() {

			return DISCONNECTED_STATES;
		}

		public static ConnectionState[] getAttachmentStates() {

			return ATTACHMENT_STATES;
		}
	}

	public static final EnumProperty<ConnectionState>[] CONNECTIONS = new EnumProperty[] {
			EnumProperty.create("connection_down", ConnectionState.class),
			EnumProperty.create("connection_up", ConnectionState.class),
			EnumProperty.create("connection_north", ConnectionState.class),
			EnumProperty.create("connection_south", ConnectionState.class),
			EnumProperty.create("connection_west", ConnectionState.class),
			EnumProperty.create("connection_east", ConnectionState.class)
	};

	public PipeBlock() {

		super(Properties.create(Material.GLASS)
				.sound(SoundType.GLASS)
				.hardnessAndResistance(0.5f)
				.notSolid()
		);
	}

	@Override
	protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {

		builder
				.add(CONNECTIONS)
		;
	}

	private static final VoxelShape CENTER;
	private static final VoxelShape[] SIDES_SINGLE = new VoxelShape[6], SIDES_PAIRED = new VoxelShape[3];
	private static final VoxelShape[] ATTACHMENTS = new VoxelShape[6];

	static {

		final int size = 6,
				attachmentSize = 5,
				attachmentDepth = 3;

		{
			final double min = size,
					max = 16 - min;

			CENTER = Block.makeCuboidShape(min, min, min, max, max, max);

			final Vector3d start = new Vector3d(min, min, min),
					end = new Vector3d(max, max, max);

			for (Direction d : Direction.values()) {
				final Vector3d p1, p2;
				if (d.getAxisDirection() == AxisDirection.NEGATIVE) {
					p1 = start.add(min * d.getXOffset(), min * d.getYOffset(), min * d.getZOffset());
					p2 = end;
				} else {
					p1 = start;
					p2 = end.add(min * d.getXOffset(), min * d.getYOffset(), min * d.getZOffset());
				}
				SIDES_SINGLE[d.ordinal()] = Block.makeCuboidShape(p1.x, p1.y, p1.z, p2.x, p2.y, p2.z);
			}

			for (int i = 0, e = 6; i < e; ) {
				SIDES_PAIRED[i >> 1] = VoxelShapes.or(SIDES_SINGLE[i++], SIDES_SINGLE[i++]);
			}
		}
		{
			final double min = attachmentSize,
					max = 16 - min;

			final Vector3d start = new Vector3d(min, min, min),
					end = new Vector3d(max, max, max);

			for (Direction d : Direction.values()) {
				final Vector3d t1 = new Vector3d(
						min * d.getXOffset(),
						min * d.getYOffset(),
						min * d.getZOffset()
				), t2 = new Vector3d(
						max * d.getXOffset() - attachmentDepth * d.getXOffset(),
						max * d.getYOffset() - attachmentDepth * d.getYOffset(),
						max * d.getZOffset() - attachmentDepth * d.getZOffset()
				);

				final Vector3d p1, p2;
				if (d.getAxisDirection() == AxisDirection.NEGATIVE) {
					p1 = start.add(t1);
					p2 = end.add(t2);
				} else {
					p1 = start.add(t2);
					p2 = end.add(t1);
				}
				ATTACHMENTS[d.ordinal()] = Block.makeCuboidShape(p1.x, p1.y, p1.z, p2.x, p2.y, p2.z);
			}
		}
	}

	/**
	 * This method is used for the collision shape
	 */
	@Override
	@SuppressWarnings("deprecation")
	public VoxelShape getCollisionShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context) {

		if (!this.canCollide) return VoxelShapes.empty();

		// TODO: cache for BlockState -> VoxelShape

		int sides = 0, bits = 0;
		int attachmentSides = 0, attachments = 0;

		for (int i = 0, v, e = 6; i < e; ++i) {
			ConnectionState connection = state.get(CONNECTIONS[i]);

			v = connection.connected;
			sides |= (1 & v) << i;
			bits += v;

			v = connection.attachments;
			attachmentSides |= (1 & v) << i;
			attachments += v;
		}

		// region fastpath {
		if (sides == 0)
			return CENTER;

		if ((attachments == 0) & (bits <= 2))
			switch (sides) {
				case 0b000001:
				case 0b000010:
				case 0b000100:
				case 0b001000:
				case 0b010000:
				case 0b100000:
					return SIDES_SINGLE[Integer.numberOfTrailingZeros(sides)];
				case 0b000011:
				case 0b001100:
				case 0b110000:
					return SIDES_PAIRED[Integer.numberOfTrailingZeros(sides) >> 1];
			}
		// endregion fastpath }

		VoxelShape ret = CENTER;
		for (int i = 0, v, e = 6; i < e; ++i) {
			v = 1 << i;
			if ((sides & v) != 0)
				ret = VoxelShapes.or(ret, SIDES_SINGLE[i]);

			if ((attachmentSides & v) != 0)
				ret = VoxelShapes.or(ret, ATTACHMENTS[i]);
		}

		return ret;
	}

	/**
	 * This method is used for outline raytraces, highlighter edges will be drawn on this shape's borders
	 */
	@Override
	@SuppressWarnings("deprecation")
	public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context) {

		return getCollisionShape(state, worldIn, pos, context);
	}

	/**
	 * This method is used for visual raytraces, so we report what the outline shape is
	 */
	@Override
	@SuppressWarnings("deprecation")
	public VoxelShape getRayTraceShape(BlockState state, IBlockReader reader, BlockPos pos, ISelectionContext context) {

		return getShape(state, reader, pos, context);
	}

	/**
	 * This is the override shape used by the raytracer in *all* modes, it changes what face the raytracer reports was hit.
	 */
	@Override
	@SuppressWarnings("deprecation")
	public VoxelShape getRaytraceShape(BlockState state, IBlockReader worldIn, BlockPos pos) {

		return VoxelShapes.fullCube();
	}

}
