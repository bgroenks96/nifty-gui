package de.lessvoid.nifty.java2d.renderer.batch;

import java.awt.*;
import java.awt.image.BufferStrategy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import de.lessvoid.nifty.render.BlendMode;
import de.lessvoid.nifty.render.batch.spi.*;
import de.lessvoid.nifty.render.io.ImageLoader;
import de.lessvoid.nifty.render.io.ImageLoaderFactory;
import de.lessvoid.nifty.spi.render.MouseCursor;
import de.lessvoid.nifty.tools.Color;
import de.lessvoid.nifty.tools.Factory;
import de.lessvoid.nifty.tools.ObjectPool;
import de.lessvoid.nifty.tools.resourceloader.NiftyResourceLoader;

/**
 * Implementation of BatchRenderBackend for Java2D.
 * @author Brian Groenke
 * @see {@link de.lessvoid.nifty.render.batch.spi.BatchRenderBackend}
 * @see {@link de.lessvoid.nifty.render.batch.BatchRenderBackendInternal}
 */
public class Java2DBatchRenderBackend implements BatchRenderBackend {

	@Nonnull
	private static final Logger log = Logger.getLogger(Java2DBatchRenderBackend.class.getName());
	
	@Nonnull
	private static final Color ATLAS_CLEAR_COLOR = Color.WHITE;

	@Nonnull
	final ImageFactory imageFactory;
	@Nonnull
	private final BufferFactory bufferFactory;
	@Nonnull
	private final MouseCursorFactory cursorFactory;
	@Nonnull
	private final ObjectPool<Batch> batchPool;
	@Nonnull
	private final List<Batch> batches = new ArrayList<Batch>();
	@Nonnull
	private final Map<Integer, TextureImageJava2D> nonAtlasTextures = new HashMap<Integer, TextureImageJava2D>();
	@Nonnull
	private final Map<Integer, TextureAtlasJava2D> atlasTextures = new HashMap<Integer, TextureAtlasJava2D>();
	@Nonnull
	private final Map<Integer, Integer> atlasWidths = new HashMap<Integer, Integer>();
	@Nonnull
	private final Map<Integer, Integer> atlasHeights = new HashMap<Integer, Integer>();
	@Nonnull
	private final Map<String, MouseCursor> cursorCache = new HashMap<String, MouseCursor>();

	@Nonnull
	private Canvas canvas;

	@Nullable
	private Batch currentBatch;
	@Nullable
	private TextureJava2D boundTexture;
	@Nullable
	private NiftyResourceLoader resourceLoader;
	@Nullable
	private MouseCursor mouseCursor;

	private boolean shouldUseHighQualityTextures = false;
	private boolean shouldFillRemovedImagesInAtlas = false;

	public Java2DBatchRenderBackend(
			@Nonnull final Canvas awtCanvas, 
			@Nonnull final int numBuffs, 
			@Nonnull final ImageFactory imageFactory, 
			@Nonnull final BufferFactory bufferFactory, 
			@Nonnull final MouseCursorFactory cursorFactory) throws AWTException {

		initAwtCanvas(awtCanvas, numBuffs);
		this.imageFactory = imageFactory;
		this.bufferFactory = bufferFactory;
		this.cursorFactory = cursorFactory;
		batchPool = new ObjectPool<Batch>(new Factory<Batch>() {
			@Nonnull
			@Override
			public Batch createNew() {
				return new Java2DBatchInternal();
			}
		});
	}

	private void initAwtCanvas(final Canvas awtCanvas, final int numBuffs) throws AWTException {
		log.fine("initializing AWT canvas");
		this.canvas = awtCanvas;
		BufferCapabilities capabilities = new BufferCapabilities(new ImageCapabilities(true),
				new ImageCapabilities(true), BufferCapabilities.FlipContents.PRIOR);
		canvas.createBufferStrategy(numBuffs, capabilities);
	}

	@Override
	public void setResourceLoader(final NiftyResourceLoader resourceLoader) {
		log.fine("setResourceLoader()");
		this.resourceLoader = resourceLoader;
	}

	@Override
	public int getWidth() {
		return canvas.getWidth();
	}

	@Override
	public int getHeight() {
		return canvas.getHeight();
	}

	@Override
	public void beginFrame() {
		log.fine("beginFrame()");
		deleteBatches();
	}

	@Override
	public void endFrame() {
		log.fine("endFrame()");
	}

	@Override
	public void clear() {
		log.fine("clear");
		Graphics g = canvas.getBufferStrategy().getDrawGraphics();
		g.clearRect(0, 0, getWidth(), getHeight());
		g.dispose();
	}

	@Override
	public MouseCursor createMouseCursor(@Nonnull final String filename, final int hotspotX,
			final int hotspotY) throws IOException {
    log.fine("createMouseCursor");
    return existsCursor(filename) ? getCursor(filename) : createCursor(filename, hotspotX, hotspotY);
	}

	@Override
	public void enableMouseCursor(final MouseCursor mouseCursor) {
		log.fine("enableMouseCursor");
		this.mouseCursor = mouseCursor;
		mouseCursor.enable();
	}

	@Override
	public void disableMouseCursor() {
		if (mouseCursor != null) {
			log.fine("disableMouseCursor");
			mouseCursor.disable();
		}
	}

	@Override
	public int createTextureAtlas(final int atlasWidth, final int atlasHeight) {
		log.fine("createTextureAtlas");
		return createTextureAtlasInternal(atlasWidth, atlasHeight);
	}

	@Override
	public void clearTextureAtlas(final int atlasTextureId) {
		log.fine("clearTextureAtlas");
		bindTexture(atlasTextureId);
		if (boundTexture != null && boundTexture.getID() == atlasTextureId) {
			boundTexture.clear(niftyColorToAwt(ATLAS_CLEAR_COLOR));
		} else {
			log.warning("failed to bind atlas texture (id="+atlasTextureId+") - atlas not cleared");
		}
	}

	@Nonnull
	@Override
	public Image loadImage(@Nonnull final String filename) {
		log.fine("loadImage {1}");
		return createImageFromFile(filename);
	}

	@Nullable
	@Override
	public Image loadImage(@Nonnull final ByteBuffer imageData, final int imageWidth, final int imageHeight) {
		log.fine("loadImage {2}");
		return imageFactory.create(imageData, imageWidth, imageHeight);
	}

	@Override
	public void addImageToAtlas(final Image image, final int atlasX, final int atlasY,
			final int atlasTextureId) {
		log.fine("addImageToAtlas");
		bindTexture(atlasTextureId);
		if (boundTexture == null) {
			log.warning("failed to bind atlas texture (id="+atlasTextureId+") - image cannot be added");
			return;
		}
		boundTexture.writeImageToTexture(image, atlasX, atlasY);
	}

	@Override
	public int createNonAtlasTexture(final Image image) {
		log.fine("createNonAtlasTexture");
		int texID = createTextureInternal(image.getWidth(), image.getHeight());
		nonAtlasTextures.get(texID).writeImageToTexture(image, 0, 0);
		return texID;
	}

	@Override
	public void deleteNonAtlasTexture(final int textureId) {
		TextureJava2D tex2d = nonAtlasTextures.get(textureId);
		tex2d.dispose();
		nonAtlasTextures.remove(textureId);
	}

	@Override
	public boolean existsNonAtlasTexture(final int textureId) {
		return nonAtlasTextures.containsKey(textureId);
	}

	@Override
	public void addQuad(
			final float x, 
			final float y, 
			final float width, 
			final float height,
			@Nonnull final Color color1, 
			@Nonnull final Color color2, 
			@Nonnull final Color color3, 
			@Nonnull final Color color4, 
			final float textureX,
			final float textureY, 
			final float textureWidth, 
			final float textureHeight, 
			final int textureId) {
		log.fine("addQuad [texId=" + textureId+"]");
		updateCurrentBatch(textureId);
		addQuadToCurrentBatch(
				x,
				y,
				width,
				height,
				color1,
				color2,
				color3,
				color4,
				textureX,
				textureY,
				textureWidth,
				textureHeight);
	}

	@Override
	public void beginBatch(final BlendMode blendMode, final int textureId) {
		log.fine("beginBatch");
    currentBatch = createNewBatch();
    addBatch(currentBatch);
    currentBatch.begin(blendMode, textureId);
	}

	@Override
	public int render() {
		log.fine("render");
		BufferStrategy bs = canvas.getBufferStrategy();
		renderBatches();
		bs.show();
		return getTotalBatchesRendered();
	}

	@Override
	public void removeImageFromAtlas(final Image image, final int atlasX, final int atlasY,
			int imageWidth, int imageHeight, int atlasTextureId) {
    if (! shouldFillRemovedImagesInAtlas) {
      return;
    }
    log.fine("removeImageFromAtlas");
    bindTexture(atlasTextureId);
    ByteBuffer blankBuffer = bufferFactory.createNativeOrderedByteBuffer(imageWidth * imageHeight);
    Image blankImage = imageFactory.create(blankBuffer, imageWidth, imageHeight);
    if (boundTexture != null && boundTexture.getID() == atlasTextureId) {
    	boundTexture.writeImageToTexture(blankImage, 0, 0);
    } else {
    	log.warning("failed to remove image from atlas (id="+atlasTextureId+") - atlas not found");
    }
	}

	@Override
	public void useHighQualityTextures(final boolean shouldUseHighQualityTextures) {
		log.fine("useHighQualityTextures");
		this.shouldUseHighQualityTextures = shouldUseHighQualityTextures;
	}

	@Override
	public void fillRemovedImagesInAtlas(final boolean shouldFill) {
		log.fine("fillRemovedImageInAtlas");
		this.shouldFillRemovedImagesInAtlas = shouldFill;
	}

	public void setCanvas(@Nonnull final Canvas awtCanvas, final int numBuffs) throws AWTException {
		log.fine("setCanvas");
		initAwtCanvas(awtCanvas, numBuffs);
	}

	public Canvas getCanvas() {
		return canvas;
	}

	private void bindTexture(final int textureId) {
		if (textureId <= 0) {
			log.fine("textureId <= 0 (unbind)");
			boundTexture = null;
			return;
		}
		log.fine("bind texture " + textureId);
		TextureJava2D tex2d = (atlasTextures.containsKey(textureId)) ? atlasTextures.get(textureId) : nonAtlasTextures.get(textureId);
		if (tex2d == null)
			log.warning("texture (id=" + textureId + ") not found - texture binding unchanged");
		else
			boundTexture = tex2d;
	}

	private void deleteBatches() {
		for (Batch batch : batches) {
			batchPool.free(batch);
		}
		batches.clear();
	}

	private void addQuadToCurrentBatch(
			final float x,
			final float y,
			final float width,
			final float height,
			@Nonnull final Color color1,
			@Nonnull final Color color2,
			@Nonnull final Color color3,
			@Nonnull final Color color4,
			final float textureX,
			final float textureY,
			final float textureWidth,
			final float textureHeight) {
		assert currentBatch != null;
		currentBatch.addQuad(
				x,
				y,
				width,
				height,
				color1,
				color2,
				color3,
				color4,
				textureX,
				textureY,
				textureWidth,
				textureHeight);
	}

	private void updateCurrentBatch(final int textureId) {
		if (shouldBeginBatch()) {
			beginBatch(getCurrentBlendMode(), textureId);
		}
	}

	@Nonnull
	private BlendMode getCurrentBlendMode() {
		assert currentBatch != null;
		return currentBatch.getBlendMode();
	}

	private boolean shouldBeginBatch() {
		assert currentBatch != null;
		return !currentBatch.canAddQuad();
	}

	@Nonnull
	private Batch createNewBatch() {
		return batchPool.allocate();
	}

	private void addBatch(@Nonnull final Batch batch) {
		batches.add(batch);
	}

	private void renderBatches() {
		for (Batch batch : batches) {
			batch.render();
		}
	}

	private int createTextureInternal(final int width, final int height) {
		TextureImageJava2D tex2d = new TextureImageJava2D(imageFactory, width, height, 0.5f); // 0.5 priority for non-atlas textures
		nonAtlasTextures.put(tex2d.getID(), tex2d);
		return tex2d.getID();
	}

	private int createTextureAtlasInternal(final int width, final int height) {
		TextureAtlasJava2D tex2d = new TextureAtlasJava2D(imageFactory, width, height, 1.0f); // always try to keep atlases accelerated
		saveAtlasSize(tex2d.getID(), width, height);
		atlasTextures.put(tex2d.getID(), tex2d);
		return tex2d.getID();
	}

	private void saveAtlasSize(final int atlasTextureId, final int atlasWidth, final int atlasHeight) {
		atlasWidths.put(atlasTextureId, atlasWidth);
		atlasHeights.put(atlasTextureId, atlasHeight);
	}

	@Nonnull
	private Image createImageFromFile(@Nonnull final String filename) {
		ImageLoader loader = ImageLoaderFactory.createImageLoader(filename);
		InputStream imageStream = null;
		try {
			assert resourceLoader != null;
			imageStream = resourceLoader.getResourceAsStream(filename);
			if (imageStream != null) {
				ByteBuffer image = loader.loadAsByteBufferRGBA(imageStream);
				image.rewind();
				int width = loader.getImageWidth();
				int height = loader.getImageHeight();
				return imageFactory.create(image, width, height);
			}
		} catch (Exception e) {
			log.log(Level.WARNING, "Could not load image from file: [" + filename + "]", e);
		} finally {
			if (imageStream != null) {
				try {
					imageStream.close();
				} catch (IOException ignored) {
				}
			}
		}
		return imageFactory.create(null, 0, 0);
	}

	private int getTotalBatchesRendered() {
		return batches.size();
	}

	private boolean existsCursor(@Nonnull final String filename) {
		return cursorCache.containsKey(filename);
	}

	@Nonnull
	private MouseCursor getCursor(@Nonnull final String filename) {
		assert cursorCache.containsKey(filename);
		return cursorCache.get(filename);
	}

	@Nullable
	private MouseCursor createCursor (final String filename, final int hotspotX, final int hotspotY) {
		try {
			assert resourceLoader != null;
			cursorCache.put(filename, cursorFactory.create(filename, hotspotX, hotspotY, resourceLoader));
			return cursorCache.get(filename);
		} catch (Exception e) {
			log.log(Level.WARNING, "Could not create mouse cursor [" + filename + "]", e);
			return null;
		}
	}
	
	@Nonnull
	private java.awt.Color niftyColorToAwt(final Color niftyColor) {
		return new java.awt.Color(niftyColor.getRed(), niftyColor.getBlue(), 
				niftyColor.getGreen(), niftyColor.getAlpha());
	}

	/**
	 * Implementation of Batch for Java2D. A Java2D batch is limited by Java2D functionality,
	 * primarily in that it is incapable of buffered rendering. This class primarily serves the
	 * purpose of filling in for the Java2D renderer implementation. It should be noted that
	 * Java2DBatchInternal MUST be an inner type of Java2DBatchRenderBackend because it needs
	 * to be able to access the enclosing type's BufferStrategy directly.
	 * @author Brian Groenke
	 */
	private class Java2DBatchInternal implements Batch {

		@Nonnull
		private final BatchQuad[] quads = new BatchQuad[512]; 
		@Nonnull
		private BlendMode blendMode = BlendMode.BLEND;
		private int primitiveCount;
		private int textureId;

		@Override
		public void begin(BlendMode blendMode, int textureId) {
			this.blendMode = blendMode;
			this.textureId = textureId;
			primitiveCount = 0;
			for (int i=0; i < quads.length; i++) {
				quads[i] = null;
			}
		}

		@Override
		public BlendMode getBlendMode() {
			return blendMode;
		}

		@Override
		public void render() {
			bindTexture(textureId);
			Graphics2D g2d = (Graphics2D) canvas.getBufferStrategy().getDrawGraphics();
			g2d.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
			if (shouldUseHighQualityTextures)
				g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			else
				g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

			for (int i=0; i < primitiveCount; i++) {
				BatchQuad quad = quads[i];
				g2d.setPaint(quad.quadPaint);
				g2d.fillRect(quad.x, quad.y, quad.width, quad.height);
				if (boundTexture != null && boundTexture.getID() == textureId) {
					log.fine("batch internal: tex bound [id="+textureId+"]");
					boundTexture.drawTexture(
							g2d, 
							quad.x,
							quad.y, 
							quad.width, 
							quad.height,
							quad.tx, 
							quad.ty,
							quad.twidth,
							quad.theight);
				} else
					log.fine("batch internal: no tex bound");
			}
			g2d.dispose();
		}

		@Override
		public boolean canAddQuad() {
			return primitiveCount < quads.length;
		}

		@Override
		public void addQuad(float x, float y, float width, float height,
				Color color1, Color color2, Color color3, Color color4, float textureX,
				float textureY, float textureWidth, float textureHeight) {
			QuadGradientPaint quadPaint = new QuadGradientPaint(color1, color2, color3, color4);
			quads[primitiveCount++] = new BatchQuad(
					(int)x,
					(int)y,
					(int)width,
					(int)height,
					(int)textureX,
					(int)textureY,
					(int)textureWidth,
					(int)textureHeight,
					quadPaint);
		}
	}

	private class BatchQuad {
		int x, y, width, height, tx, ty, twidth, theight;
		QuadGradientPaint quadPaint;
		BatchQuad(int x, int y, int width, int height, int tx, int ty, int twidth, int theight, QuadGradientPaint quadPaint) {
			this.x = x; this.y = y;
			this.width = width; this.height = height;
			this.tx = tx; this.ty = ty;
			this.twidth = twidth; this.theight = theight;
			this.quadPaint = quadPaint;
		}
	}
}
