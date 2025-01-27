package com.xeno.goo.interactions;

import com.xeno.goo.GooMod;
import com.xeno.goo.entities.GooBlob;
import com.xeno.goo.entities.GooSplat;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.network.BlobHitInteractionPacket;
import com.xeno.goo.network.BlobInteractionPacket;
import com.xeno.goo.network.Networking;
import com.xeno.goo.network.SplatInteractionPacket;
import com.xeno.goo.setup.Registry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.particles.BasicParticleType;
import net.minecraft.util.*;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class GooInteractions
{
    private static BasicParticleType particleTypeFromGoo(Fluid f)
    {
        return Registry.fallingParticleFromFluid(f);
    }

    public static void spawnParticles(GooBlob e)
    {
        if (!(e.getEntityWorld() instanceof ServerWorld)) {
            return;
        }
        // we should be able to guarantee the fluid has goo particles, so spawn a mess of them
        if (e.goo().getFluid() instanceof GooFluid) {
            spawnParticles(e, (GooFluid) e.goo().getFluid());
        }
    }

    // the difference here is that we can call this one during events where
    // the blob is being "emptied" and hang onto its fluid type.
    public static void spawnParticles(GooBlob e, GooFluid f)
    {
        if (!(e.getEntityWorld() instanceof ServerWorld)) {
            return;
        }
        // we should be able to guarantee the fluid has goo particles, so spawn a mess of them
        BasicParticleType type = particleTypeFromGoo(f);
        if (type == null) {
            return;
        }
        Vector3d spawnVec = e.getPositionVec();
        // give it a bit of randomness around the hit location
        double offX = (e.cubicSize() / 2d) * (e.getEntityWorld().rand.nextFloat() - 0.5f);
        double offZ = (e.cubicSize() / 2d) * (e.getEntityWorld().rand.nextFloat() - 0.5f);

        ((ServerWorld)e.getEntityWorld()).spawnParticle(type, spawnVec.x, spawnVec.y, spawnVec.z, (int)Math.sqrt(e.goo().getAmount()),
                offX, e.cubicSize(), offZ, 0.2d);
    }

    public static void spawnParticles(GooSplat e) {
        if (!(e.getEntityWorld() instanceof ServerWorld)) {
            return;
        }
        // we should be able to guarantee the fluid has goo particles, so spawn a mess of them
        if (e.goo().getFluid() instanceof GooFluid) {
            spawnParticles(e, (GooFluid) e.goo().getFluid());
        }
    }

    // the difference here is that we can call this one during events where
    // the blob is being "emptied" and hang onto its fluid type.
    public static void spawnParticles(GooSplat e, GooFluid f)
    {
        if (e.world.rand.nextFloat() > 0.04f) {
            return;
        }
        if (!(e.getEntityWorld() instanceof ServerWorld)) {
            return;
        }
        // we should be able to guarantee the fluid has goo particles, so spawn a mess of them
        BasicParticleType type = particleTypeFromGoo(f);
        if (type == null) {
            return;
        }

        AxisAlignedBB box = e.getBoundingBox();
        Vector3d lowerBounds = new Vector3d(box.minX, box.minY, box.minZ);
        Vector3d threshHoldMax = new Vector3d(box.maxX - box.minX, box.maxY - box.minY, box.maxZ - box.minZ);
        Vector3d spawnVec = lowerBounds.add(threshHoldMax.mul(e.world.rand.nextFloat(), e.world.rand.nextFloat(), e.world.rand.nextFloat()));
        // make sure the spawn area is offset in a way that puts the particle outside of the block side we live on
        Vector3d offsetVec = Vector3d.copy(e.sideWeLiveOn().getDirectionVec()).mul(threshHoldMax.x, threshHoldMax.y, threshHoldMax.z);

        ((ServerWorld)e.getEntityWorld()).spawnParticle(type, spawnVec.x, spawnVec.y, spawnVec.z, 1,
                offsetVec.x, offsetVec.y, offsetVec.z, 0.2d);
    }

    public static final Map<Fluid, Map<Tuple<Integer, String>, ISplatInteraction>> splatRegistry = new HashMap<>();
    public static void registerSplat(Fluid fluid, String key, ISplatInteraction interaction, ISplatInteraction condition) {
        ensureSplatMapContainsFluid(fluid);
        registerSplat(fluid, key, splatRegistry.get(fluid).size(), interaction, condition);
    }

    private static void registerSplat(Fluid fluid, String key, int rank, ISplatInteraction interaction, ISplatInteraction condition) {
        splatRegistry.get(fluid).put(new Tuple<>(rank, key), (context) -> condition.resolve(context) && (context.isFailing() || interaction.resolve(context)));
    }

    private static void ensureSplatMapContainsFluid(Fluid fluid) {
        if (!splatRegistry.containsKey(fluid)) {
            splatRegistry.put(fluid, new TreeMap<>(Comparator.comparing(Tuple::getA))); // sort by rank
        }
    }

    public static void registerBlob(Fluid f, String key, IBlobInteraction i) {
        ensureBlobMapContainsFluid(f);
        registerBlob(f, key, blobRegistry.get(f).size(), i);
    }

    public static final Map<Fluid, Map<Tuple<Integer, String>, IBlobInteraction>> blobRegistry = new HashMap<>();
    private static void registerBlob(Fluid fluid, String key, int rank, IBlobInteraction interaction) {
        blobRegistry.get(fluid).put(new Tuple<>(rank, key), interaction);
    }

    public static final Map<Fluid, Map<Tuple<Integer, String>, IBlobHitInteraction>> blobHitRegistry = new HashMap<>();
    public static void registerBlobHit(Fluid f, String key, IBlobHitInteraction i) {
        ensureBlobHitMapContainsFluid(f);
        registerBlobHit(f, key, blobHitRegistry.get(f).size(), i);
    }

    private static void registerBlobHit(Fluid f, String key, int rank, IBlobHitInteraction i) {
        blobHitRegistry.get(f).put(new Tuple<>(rank, key), i);
    }

    private static void ensureBlobHitMapContainsFluid(Fluid f) {
        if (!blobHitRegistry.containsKey(f)) {
            blobHitRegistry.put(f, new TreeMap<>(Comparator.comparing(Tuple::getA))); // sort by rank
        }
    }

    private static void ensureBlobMapContainsFluid(Fluid f) {
        if (!blobRegistry.containsKey(f)) {
            blobRegistry.put(f, new TreeMap<>(Comparator.comparing(Tuple::getA))); // sort by rank
        }
    }

    public static final Map<Fluid, IPassThroughPredicate> materialPassThroughPredicateRegistry = new HashMap<>();
    public static void registerPassThroughPredicate(Fluid fluid, IPassThroughPredicate p) {
        materialPassThroughPredicateRegistry.put(fluid, p);
    }

    public static void initialize()
    {
        Aquatic.registerInteractions();
        Chromatic.registerInteractions();
        Crystal.registerInteractions();
        Decay.registerInteractions();
        Earthen.registerInteractions();
        Energetic.registerInteractions();
        Faunal.registerInteractions();
        Floral.registerInteractions();
        Fungal.registerInteractions();
        Honey.registerInteractions();
        Logic.registerInteractions();
        Metal.registerInteractions();
        Molten.registerInteractions();
        Primordial.registerInteractions();
        Radiant.registerInteractions();
        Regal.registerInteractions();
        Slime.registerInteractions();
        Snow.registerInteractions();
        Vital.registerInteractions();
        Weird.registerInteractions();
    }


    public static void tryResolving(LivingEntity e, LivingEntity owner, GooBlob blob)
    {
        if (!e.world.isRemote()) {
            Networking.sendToClientsNearTarget(new BlobHitInteractionPacket(e, owner, blob), (ServerWorld)e.world, e.getPosition(), 32);
        }
        if (!blob.isAlive() || blob.goo().isEmpty()) {
            return;
        }
        Fluid fluid = blob.goo().getFluid();
        // no interactions registered, we don't want to crash.
        if (!blobHitRegistry.containsKey(fluid)) {
            return;
        }

        BlobHitContext context = new BlobHitContext(e, owner, blob, fluid);
        // cycle over resolvers in rank order and drain/apply when possible.
        Map<Tuple<Integer, String>, IBlobHitInteraction> map = blobHitRegistry.get(fluid);
        map.forEach((k, v) -> tryResolving(v, context.withKey(k.getB())));

    }

    /**
     * Resolves a blob hit - generally blob hits (on entities) are less complex than blob splats on blocks.
     * There's less argumentation, less configurability. The blob hits a thing and does something and dies.
     * There also isn't a "cost" of interactions. The blob hits are balanced around the defaults for amounts
     * thrown, and amounts thrown are balanced around block/splat effects primarily.
     * @param iBlobInteraction The functional interface of the resolver effect, this is what does the thing, whatever it is.
     * @param context The interaction context for a blob hit, contains the state for what the thing should affect.
     */
    private static void tryResolving(IBlobHitInteraction iBlobInteraction, BlobHitContext context)
    {
        if (!context.blob().isAlive() || context.blob().goo().isEmpty()) {
            return;
        }
        if (iBlobInteraction.resolve(context)) {
            GooInteractions.spawnParticles(context.blob());
            GooBlob.getGoo(context.blob()).empty();
            context.blob().remove();
        } else {
            context.ricochet();
        }
    }

    public static void tryResolving(BlockPos blockHitPos, GooBlob e)
    {
        if (!e.world.isRemote()) {
            Networking.sendToClientsNearTarget(new BlobInteractionPacket(blockHitPos, e), (ServerWorld)e.world, e.getPosition(), 32);
        }
        if (!e.isAlive()) {
            return;
        }
        Fluid fluid = e.goo().getFluid();
        // no interactions registered, we don't want to crash.
        if (!blobRegistry.containsKey(fluid)) {
            return;
        }
        BlobContext context = new BlobContext(blockHitPos, e);
        // cycle over resolvers in rank order and drain/apply when possible.
        Map<Tuple<Integer, String>, IBlobInteraction> map = blobRegistry.get(fluid);
        map.forEach((k, v) -> tryResolving(fluid, k, v, context));
    }

    private static void tryResolving(Fluid fluid, Tuple<Integer, String> interactionKey, IBlobInteraction iBlobInteraction, BlobContext context)
    {
        double chanceToResolve = GooMod.config.chanceOfBlobInteraction(fluid, interactionKey.getB());
        if (chanceToResolve == 0d) {
            return;
        }

        int keyCost = GooMod.config.costOfBlobInteraction(fluid, interactionKey.getB());
        if (keyCost == -1) {
            // interaction costs the entire thrown amount
            keyCost = context.blob().goo().getAmount();
        }

        boolean shouldResolve = chanceToResolve >= context.world().rand.nextDouble();
        if (!shouldResolve) {
            return;
        }

        FluidStack drained = context.fluidHandler().drain(keyCost, IFluidHandler.FluidAction.SIMULATE);
        if (drained.getAmount() < keyCost) {
            return;
        }

        boolean failureShortCircuit = GooMod.config.chanceOfBlobInteractionFailure(fluid, interactionKey.getB()) >= context.world().rand.nextDouble();
        if (failureShortCircuit || iBlobInteraction.resolve(context)) {
            boolean shouldDrain = GooMod.config.chanceOfBlobInteractionCost(fluid, interactionKey.getB()) >= context.world().rand.nextDouble();
            if (shouldDrain) {
                context.fluidHandler().drain(keyCost, IFluidHandler.FluidAction.EXECUTE);
            }
        }
    }

    public static void tryResolving(GooSplat e)
    {
        if (!e.world.isRemote()) {
            Networking.sendToClientsNearTarget(new SplatInteractionPacket(e), (ServerWorld)e.world, e.getPosition(), 32);
        }
        if (!e.isAlive()) {
            return;
        }
        Fluid fluid = e.goo().getFluid();
        // no interactions registered, we don't want to crash.
        if (!splatRegistry.containsKey(fluid)) {
            return;
        }
        SplatContext context = new SplatContext(e, fluid);
        // cycle over resolvers in rank order and drain/apply when possible.
        Map<Tuple<Integer, String>, ISplatInteraction> map = splatRegistry.get(fluid);
        map.forEach((k, v) -> tryResolving(fluid, k, v, context));
    }

    private static void tryResolving(Fluid fluid, Tuple<Integer, String> interactionKey, ISplatInteraction iSplatInteraction, SplatContext context)
    {
        double chanceToResolve = GooMod.config.chanceOfSplatInteraction(fluid, interactionKey.getB());
        if (chanceToResolve == 0d) {
            return;
        }

        int keyCost = GooMod.config.costOfSplatInteraction(fluid, interactionKey.getB());
        if (keyCost == -1) {
            // interaction costs the entire thrown amount
            keyCost = context.splat().goo().getAmount();
        }

        boolean shouldResolve = chanceToResolve >= context.world().rand.nextDouble();
        if (!shouldResolve) {
            return;
        }

        // we still need the full amount to resolve but a chance not to drain prevents it from deducting
        FluidStack drained = context.fluidHandler().drain(keyCost, IFluidHandler.FluidAction.SIMULATE);
        if (drained.getAmount() < keyCost) {
            return;
        }

        boolean failureShortCircuit = GooMod.config.chanceOfSplatInteractionFailure(fluid, interactionKey.getB()) >= context.world().rand.nextDouble();
        context.fail(failureShortCircuit);
        if (iSplatInteraction.resolve(context)) {
            boolean shouldDrain = GooMod.config.chanceOfSplatInteractionCost(fluid, interactionKey.getB()) >= context.world().rand.nextDouble();
            if (shouldDrain) {
                context.fluidHandler().drain(keyCost, IFluidHandler.FluidAction.EXECUTE);
            }

            int cooldown = GooMod.config.cooldownOfSplatInteraction(fluid, interactionKey.getB());
            if (cooldown > 0) {
                context.splat().setCooldown(cooldown);
            }
        }
    }
}
