package de.lessvoid.nifty.java2d.renderer.batch;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;

import javax.annotation.Nonnull;

import de.lessvoid.nifty.render.batch.spi.BatchRenderBackend;
import de.lessvoid.nifty.render.batch.spi.BatchRenderBackend.Image;

/**
 *
 * @author Brian Groenke
 */
interface TextureJava2D {

	int getID();
	int getWidth();
	int getHeight();
	/**
	 * Writes the given {@link BatchRenderBackend.Image} data to this
	 * Java2D texture at the given location. This method will allocate internal
	 * image resources if they have not already been initialized.
	 * @param image
	 * @param x
	 * @param y
	 */
	void writeImageToTexture(@Nonnull Image image, int x, int y);
	/**
	 * Draws this {@link TextureJava2D} to the given {@link Graphics2D}.
	 * Note that this method <b>will only</b> call <code>drawImage</code> on
	 * <code>drawGraphics</code>; it will not be disposed or modified in any
	 * other way.
	 * @param drawGraphics the Graphics2D instance to draw into
	 * @param destX destination coords
	 * @param destY
	 * @param destWidth
	 * @param destHeight
	 * @param srcX source (this texture) coords
	 * @param srcY
	 * @param srcWidth
	 * @param srcHeight
	 */
	void drawTexture(
			@Nonnull Graphics2D drawGraphics,
			int destX,
			int destY,
			int destWidth,
			int destHeight,
			int srcX,
			int srcY,
			int srcWidth,
			int srcHeight);
	/**
	 * Sets the acceleration priority on the backing Java2D {@link BufferedImage}/{@link VolatileImage}.
	 * @param priority
	 * @see {@link BufferedImage.setAccelerationPriority}
	 */
	void setAccelerationPriority(float priority);
	/**
	 * @return true if this TextureJava2D is configured to invert the Y value of incoming source coordinates.
	 */
	boolean invertsYAxis();
	/**
	 * Clears this TextureJava2D with the given {@link java.awt.Color}. This method will allocate internal
	 * image resources if they have not already been initialized.
	 * @param clearColor the color that should be painted over the image to clear it
	 */
	void clear(@Nonnull Color clearColor);
	/**
	 * Releases cached resources currently being held by this TextureJava2D's backing
	 * image buffers and sets internal references to null. While it is generally expected
	 * (and recommended) that the TextureJava2D object will be ignored and discarded after
	 * calling this method, a call to {@link #writeImageToTexture(Image, int, int)} or {@link #clear(Color)}
	 * will allow for internal resources to be reallocated for further use.
	 */
	void dispose();
}