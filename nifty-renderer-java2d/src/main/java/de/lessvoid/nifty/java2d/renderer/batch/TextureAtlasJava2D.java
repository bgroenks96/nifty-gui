package de.lessvoid.nifty.java2d.renderer.batch;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import de.lessvoid.nifty.render.batch.spi.BatchRenderBackend.Image;
import de.lessvoid.nifty.render.batch.spi.ImageFactory;

/**
 * Implementation of TextureJava2D for atlas textures. TextureAtlasJava2D writes
 * image data to a {@link java.awt.image.VolatileImage} that is fully hardware
 * accelerated (unless priority is set to 0 or system VRAM is full).  The backing
 * VolatileImage is hardware accelerated both in render-to and render-from operations,
 * so TextureAtlasJava2D should provide the maximum possible performance for storing
 * multiple sub-images in one large texture object. VolatileImage data, however, may
 * become invalid or be flushed from video memory at any given time; in order to prevent
 * loss of atlas data, TextureAtlasJava2D creates and stores a {@link java.awt.image.BufferedImage}
 * snapshot of the atlas after each write operation. This snapshot is then used to restore
 * the contents of the VolatileImage in the case that its data is lost or invalidated.
 * @author Brian Groenke
 *
 */
class TextureAtlasJava2D implements TextureJava2D {

	private static final Logger log = Logger.getLogger(TextureAtlasJava2D.class.getName());

	private static volatile int texIdTick = 0xfff;

	/**
	 *
	 */
	private final int id, width, height;

	@Nonnull
	private final ImageFactory imageFactory;

	private float priority;

	@Nullable
	private VolatileImage texVI;
	@Nullable
	private BufferedImage snapshot;

	/**
	 * @param width width of the new texture
	 * @param height height of the new texture
	 * @param priority acceleration priority value
	 */
	TextureAtlasJava2D(
			@Nonnull final ImageFactory imageFactory,
			final int width,
			final int height,
			final float priority) {
		id = texIdTick++; // assign id and increment texIdTick by 1
		this.imageFactory = imageFactory;
		this.width = width;
		this.height = height;
		this.priority = priority;
		initTextureVI(width, height);
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
		Java2DImage j2dImage = (Java2DImage) imageFactory.create(imageFactory.asByteBuffer(img), img.getWidth(), img.getHeight());
		if (texVI == null) {
			initTextureVI(width, height);
		}
		validate();
		writeImageDataToSurface(j2dImage.getBufferedImage(), x, y);
		updateSnapshot();
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
		validate();
		drawGraphics.drawImage(texVI, destX, destY, destWidth, destHeight, srcX, srcY, srcWidth, srcHeight, null);
	}

	@Override
	public void setAccelerationPriority(final float priority) {
		if (priority >= 0 && priority <= 1)
			this.priority = priority;
	}

	@Override
	public void clear(@Nonnull final Color color) {
		if (texVI == null) {
			initTextureVI(width, height);
		}
		Graphics2D g2d = texVI.createGraphics();
		g2d.setColor(color);
		g2d.fillRect(0, 0, width, height);
		g2d.dispose();
		updateSnapshot();
	}

	@Override
	public void dispose() {
		texVI.flush();
		snapshot.flush();
		texVI = null;
		snapshot = null;
	}

	private void validate() {
		if (texVI == null) return;

		GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
		if (device == null) throw new RuntimeException("TextureJava2D: environment returned null GraphicsDevice");
		do {
			int check = texVI.validate(device.getDefaultConfiguration());
			switch(check) {
			case VolatileImage.IMAGE_INCOMPATIBLE:
				log.fine("texture (id="+id+") -> IMAGE_INCOMPATIBLE - re-initializing image buffer");
				initTextureVI(width, height);
			case VolatileImage.IMAGE_RESTORED:
				log.fine("texture (id="+id+") -> IMAGE_RESTORED - reloading texture data from snapshot");
				if (snapshot != null) writeImageDataToSurface(snapshot, 0, 0);
				break;
			default:
				// nothing needs to be done
			}
		} while (texVI.contentsLost());
	}

	private void initTextureVI(final int width, final int height) {
		if (texVI != null) {
			texVI.flush();
		}
		texVI = getImageFactoryInternal().createNativeVolatileImage(width, height);
		texVI.setAccelerationPriority(priority);
	}

	private void writeImageDataToSurface(@Nonnull final BufferedImage img, final int x, final int y) {
		Graphics2D g2d = texVI.createGraphics();
		g2d.drawImage(img, x, y, null);
		g2d.dispose();
	}

	private Java2DImageFactory getImageFactoryInternal() {
		if (imageFactory instanceof Java2DImageFactory) {
			return (Java2DImageFactory) imageFactory;
		} else {
			return new Java2DImageFactory();
		}
	}

	/*
	 * We use a java.util.concurrent.ExecutorService to handle updating the BufferedImage snapshot.
	 * This saves the renderer thread from having to consume valuable frame time to update the
	 * snapshot; there should not be any synchronization issues since getSnapshot() is a read-only operation.
	 * A thread pool of size n=1 should be used to ensure that update requests are executed asynchronously.
	 */

	private final ExecutorService snapshotThreadPool = Executors.newFixedThreadPool(1);

	private void updateSnapshot() {
		final Runnable syncUpdateSnapshot = new Runnable() {
			@Override
			public void run() {
				if (snapshot != null) {
					snapshot.flush();
				}
				snapshot = texVI.getSnapshot();
				snapshot.setAccelerationPriority(0);
			}
		};
		snapshotThreadPool.execute(syncUpdateSnapshot);
	}
}
