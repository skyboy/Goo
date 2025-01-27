package com.xeno.goo.client.particle;

import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.xeno.goo.client.render.FluidCuboidHelper;
import com.xeno.goo.client.render.GooRenderHelper;
import com.xeno.goo.setup.Registry;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.Fluid;
import net.minecraft.particles.*;

public class GooParticle extends SpriteTexturedParticle
{

    private final Fluid fluid;
    private final boolean isChromatic;
    protected boolean brightnessThingy;
    protected GooParticle(ClientWorld world, double x, double y, double z, Fluid f, boolean shouldFling)
    {
        super(world, x, y, z);
        this.setSize(0.01F, 0.01F);
        this.particleGravity = 0.06F;
        this.fluid = f;
        if (f.getFluid().equals(Registry.ENERGETIC_GOO) || f.getFluid().equals(Registry.MOLTEN_GOO)) {
            this.brightnessThingy = true;
        }
        this.isChromatic = f.equals(Registry.CHROMATIC_GOO.get());
        this.setColor();
        if (shouldFling) {
            // random fling directions and also a bit of bounce
            double flingX = (world.rand.nextFloat() - 0.5f) * 0.2f;
            double flingZ = (world.rand.nextFloat() - 0.5f) * 0.2f;
            this.motionX = flingX;
            this.motionY = 0.2d;
            this.motionZ = flingZ;
        } else {
            this.motionY = 0.1d;
        }
    }

    private void setColor()
    {
        int tempColor = ParticleColorHelper.getColor(this.fluid, this.isChromatic);
        this.setColor(ParticleColorHelper.red(tempColor),
                ParticleColorHelper.green(tempColor),
                ParticleColorHelper.blue(tempColor));
        this.setAlphaF(0.8f);
    }

    private void updateChromatic() {
        int chromaticInt = FluidCuboidHelper.colorizeChromaticGoo();
        this.setColor(
                ParticleColorHelper.red(chromaticInt),
                ParticleColorHelper.green(chromaticInt),
                ParticleColorHelper.blue(chromaticInt));
    }

    @Override
    public void renderParticle(IVertexBuilder buffer, ActiveRenderInfo renderInfo, float partialTicks)
    {
        super.renderParticle(buffer, renderInfo, partialTicks);
    }

    public IParticleRenderType getRenderType()
    {
        return IParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public int getBrightnessForRender(float partialTick) {
        return GooRenderHelper.FULL_BRIGHT;
    }

    public void tick() {
        if (this.isChromatic) {
            this.updateChromatic();
        }
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        this.decay();
        if (!this.onGround) {
            this.motionY -= this.particleGravity;
        }
        if (!this.isExpired) {
            this.move(this.motionX, this.motionY, this.motionZ);
            this.slowDownAlot();
            if (!this.isExpired) {
                this.motionX *= 0.98F;
                this.motionY *= 0.98F;
                this.motionZ *= 0.98F;
            }
        }
    }

    protected void decay() {
        if (this.maxAge-- <= 0) {
            this.setExpired();
        }
    }

    protected void slowDownAlot() {
        // NOOP, intentional
    }

    public static class FallingGooFactory implements IParticleFactory<BasicParticleType> {
        protected final IAnimatedSprite spriteSet;
        protected final Fluid fluid;
        protected final IParticleData decaysInto;

        public FallingGooFactory(IAnimatedSprite animSprite, Fluid f, IParticleData decaysInto) {
            this.spriteSet = animSprite;
            this.fluid = f;
            this.decaysInto = decaysInto;
        }

        public Particle makeParticle(BasicParticleType typeIn, ClientWorld worldIn, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            GooParticle dripParticle = new FallingGooWhichLands(worldIn, x, y, z, this.fluid, decaysInto);
            dripParticle.selectSpriteRandomly(this.spriteSet);
            return dripParticle;
        }
    }

    static class FallingGooWhichLands extends FallingGooParticle
    {
        protected final IParticleData decaysInto;

        private FallingGooWhichLands(ClientWorld world, double x, double y, double z, Fluid f, IParticleData decaysInto) {
            super(world, x, y, z, f);
            this.decaysInto = decaysInto;
        }

        protected void slowDownAlot() {
            if (this.onGround) {
                this.setExpired();
                this.world.addParticle(this.decaysInto, this.posX, this.posY, this.posZ, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    static class FallingGooParticle extends GooParticle {
        private FallingGooParticle(ClientWorld world, double x, double y, double z, Fluid f) {
            super(world, x, y, z, f, true);
            this.maxAge = (int)(64.0D / (Math.random() * 0.8D + 0.2D));
        }

        protected void slowDownAlot() {
            if (this.onGround) {
                this.setExpired();
            }
        }
    }

    static class LandingGooParticle extends GooParticle {
        private LandingGooParticle(ClientWorld world, double x, double y, double z, Fluid f) {
            super(world, x, y, z, f, false);
            this.maxAge = (int)(16.0D / (Math.random() * 0.8D + 0.2D));
        }
    }

    public static class LandingGooFactory implements IParticleFactory<BasicParticleType> {
        protected final IAnimatedSprite spriteSet;
        protected final Fluid fluid;
        public LandingGooFactory(IAnimatedSprite animSprite, Fluid fluid) {
            this.fluid = fluid;
            this.spriteSet = animSprite;
        }

        public Particle makeParticle(BasicParticleType typeIn, ClientWorld worldIn, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            GooParticle drip = new LandingGooParticle(worldIn, x, y, z, this.fluid);
            drip.selectSpriteRandomly(this.spriteSet);
            return drip;
        }
    }
}
