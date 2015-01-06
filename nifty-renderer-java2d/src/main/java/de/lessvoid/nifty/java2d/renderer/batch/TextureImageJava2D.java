package de.lessvoid.nifty.java2d.renderer.batch;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import de.lessvoid.nifty.render.batch.spi.BatchRenderBackend.Image;
import de.lessvoid.nifty.render.batch.spi.ImageFactory;

/**
 * Implementation of TextureJava2D for single-image (non-atlas) textures.
 * TextureImageJava2D writes image data to a {@link java.awt.image.BufferedImage}
 * that can be hardware accelerated depending on the acceleration priority set.
 * BufferedImage is backed internally  in Java2D by OpenGL/D3D hardware (if accelerated)
 * textures but handles render-to operations in software, so it is best used in this
 * case for textures that are non-atlas'd but will be read from in future hardware draw
 * operations.
 * 
 * @author Brian Groenke
 */
class TextureImageJava2D implements TextureJava2D {
	
	private static volatile int idTick = 0x1f;
	
	@Nonnull
	private final ImageFactory imageFactory;
	private final int id, width, height;
	
	private float priority;
	
	@Nullable
	private BufferedImage texImg;
	@Nullable
	private Java2DImage imgRef;
	
	/**
	 * @param imageFactory
	 * @param width
	 * @param height
	 * @param priority 0 < priority < 1 where 0 is never hardware accelerated and 1 is max priority
	 */
	TextureImageJava2D(@Nonnull final ImageFactory imageFactory, final int width, final int height, float priority) {
		this.id = idTick++;
		this.width = width;
		this.height = height;
		this.priority = priority;
		this.imageFactory = imageFactory;
		initTextureImage(width, height);
	}

	@Override
	public int getID() {
		return id;
	}
	
	@Override
	public int getWidth() {
		return width;
	}

	@Override
	public int getHeight() {
		return height;
	}

	@Override
	public void writeImageToTexture(@Nonnull final Image img, final int x, final int y) {
		imgRef = (Java2DImage) imageFactory.create(imageFactory.asByteBuffer(img), img.getWidth(), img.getHeight());
		if (texImg == null) {
			initTextureImage(width, height);
		}
		Graphics2D g2d = texImg.createGraphics();
		g2d.drawImage(imgRef.getBufferedImage(), x, y, null);
		g2d.dispose();
	}
	
	@Override
	public void drawTexture(
			@Nonnull final Graphics2D drawGraphics, 
			final int destX, 
			final int destY,
			final int destWidth, 
			final int destHeight, 
			final int srcX, 
			final int srcY, 
			final int srcWidth,
			final int srcHeight) {
		drawGraphics.drawImage(texImg, destX, destY, destWidth, destHeight, srcX, srcY, srcWidth, srcHeight, null);
	}
	
	@Override
	public void setAccelerationPriority(final float priority) {
		this.priority = priority;
	}
	
	@Override
	public void clear(@Nonnull final Color clearColor) {
		
	}
	
	@Override
	public void dispose() {
		texImg.flush();
		texImg = null;
		imgRef = null;
	}
	
	private void initTextureImage(final int width, final int height) {
		texImg = getImageFactoryInternal().createNativeBufferedImage(width, height);
		texImg.setAccelerationPriority(priority);
	}
	
	private Java2DImageFactory getImageFactoryInternal() {
		if (imageFactory instanceof Java2DImageFactory)
			return (Java2DImageFactory) imageFactory;
		else
			return new Java2DImageFactory();
	}
}
