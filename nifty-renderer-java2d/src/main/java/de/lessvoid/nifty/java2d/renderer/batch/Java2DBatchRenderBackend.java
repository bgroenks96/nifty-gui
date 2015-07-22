package de.lessvoid.nifty.java2d.renderer.batch;

import java.awt.AWTException;
import java.awt.BufferCapabilities;
import java.awt.Canvas;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.ImageCapabilities;
import java.awt.RenderingHints;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.io.IOException;
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
import de.lessvoid.nifty.render.batch.spi.Batch;
import de.lessvoid.nifty.render.batch.spi.BatchRenderBackend;
import de.lessvoid.nifty.render.batch.spi.BufferFactory;
import de.lessvoid.nifty.render.batch.spi.ImageFactory;
import de.lessvoid.nifty.render.batch.spi.MouseCursorFactory;
import de.lessvoid.nifty.render.io.ImageLoader;
import de.lessvoid.nifty.render.io.ImageLoaderFactory;
import de.lessvoid.nifty.spi.render.MouseCursor;
import de.lessvoid.nifty.tools.Color;
import de.lessvoid.nifty.tools.Factory;
import de.lessvoid.nifty.tools.ObjectPool;
import de.lessvoid.nifty.tools.resourceloader.NiftyResourceLoader;

/**
 * Implementation of BatchRenderBackend for Java2D.
 *
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

  /**
   * Creates a new Java2DBatchRenderBackend with the given Canvas, back buffer count, and
   * resource factories.
   * @param awtCanvas AWT canvas to draw on.
   * @param numBuffs number of render buffers (1-3; 2 = double buffered, 3 = triple buffered).
   * @param imageFactory
   * @param bufferFactory
   * @param cursorFactory
   * @throws AWTException
   */
  public Java2DBatchRenderBackend(@Nonnull final Canvas awtCanvas,
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

  private void initAwtCanvas(@Nonnull final Canvas awtCanvas, final int numBuffs) throws AWTException {
    log.fine("initializing AWT canvas");
    this.canvas = awtCanvas;
    BufferCapabilities capabilities = new BufferCapabilities(new ImageCapabilities(true), new ImageCapabilities(true),
        BufferCapabilities.FlipContents.PRIOR);
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
  public MouseCursor createMouseCursor(@Nonnull final String filename, final int hotspotX, final int hotspotY)
      throws IOException {
    log.fine("createMouseCursor");
    return existsCursor(filename) ? getCursor(filename) : createCursor(filename, hotspotX, hotspotY);
  }

  @Override
  public void enableMouseCursor(@Nonnull final MouseCursor mouseCursor) {
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
      log.warning("failed to bind atlas texture (id=" + atlasTextureId + ") - atlas not cleared");
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
  public void addImageToAtlas(@Nonnull final Image image, final int atlasX, final int atlasY, final int atlasTextureId) {
    log.fine("addImageToAtlas");
    bindTexture(atlasTextureId);
    if (boundTexture == null) {
      log.warning("failed to bind atlas texture (id=" + atlasTextureId + ") - image cannot be added");
      return;
    }
    boundTexture.writeImageToTexture(image, atlasX, atlasY);
  }

  @Override
  public int createNonAtlasTexture(@Nonnull final Image image) {
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
  public void addQuad(final float x,
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
    if (width <= 0 || height <= 0) return;
    log.fine("addQuad [texId=" + textureId + "]");
    //log.info("addQuad tex-info: " + textureX + " " + textureY + " " + textureWidth + " " + textureHeight);
    updateCurrentBatch(textureId);
    addQuadToCurrentBatch(x,
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
  public void beginBatch(@Nonnull final BlendMode blendMode, final int textureId) {
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
  public void removeImageFromAtlas(@Nonnull final Image image,
                                   final int atlasX,
                                   final int atlasY,
                                   int imageWidth,
                                   int imageHeight,
                                   int atlasTextureId) {
    if (!shouldFillRemovedImagesInAtlas) {
      return;
    }
    log.fine("removeImageFromAtlas");
    bindTexture(atlasTextureId);
    ByteBuffer blankBuffer = bufferFactory.createNativeOrderedByteBuffer(imageWidth * imageHeight);
    Image blankImage = imageFactory.create(blankBuffer, imageWidth, imageHeight);
    if (boundTexture != null && boundTexture.getID() == atlasTextureId) {
      boundTexture.writeImageToTexture(blankImage, atlasX, atlasY);
    } else {
      log.warning("failed to remove image from atlas (id=" + atlasTextureId + ") - atlas not found");
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
    TextureJava2D tex2d = (atlasTextures.containsKey(textureId)) ? atlasTextures.get(textureId) : nonAtlasTextures
        .get(textureId);
    if (tex2d == null) {
      log.warning("texture (id=" + textureId + ") not found - texture binding unchanged");
    } else {
      boundTexture = tex2d;
    }
  }

  private void deleteBatches() {
    for (Batch batch : batches) {
      batchPool.free(batch);
    }
    batches.clear();
  }

  private void addQuadToCurrentBatch(final float x,
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
    currentBatch.addQuad(x,
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
    // 0.5 priority for non-atlas textures
    TextureImageJava2D tex2d = new TextureImageJava2D(imageFactory, width, height, 0.5f, true);
    nonAtlasTextures.put(tex2d.getID(), tex2d);
    return tex2d.getID();
  }

  private int createTextureAtlasInternal(final int width, final int height) {
    // always try to keep atlas textures accelerated
    TextureAtlasJava2D tex2d = new TextureAtlasJava2D(imageFactory, width, height, 1.0f, true);
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
    ByteBufferWrappedInputStream imageStream = null;
    try {
      assert resourceLoader != null;
      imageStream = new ByteBufferWrappedInputStream(resourceLoader.getResourceAsStream(filename));
      if (imageStream != null) {
        BufferedImage buffImg = loader.loadAsBufferedImage(imageStream);
        int width = buffImg.getWidth();
        int height = buffImg.getHeight();
        return imageFactory.create(imageStream.sliceBuffer(), width, height);
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
  private MouseCursor createCursor(@Nonnull final String filename, final int hotspotX, final int hotspotY) {
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
  private java.awt.Color niftyColorToAwt(@Nonnull final Color niftyColor) {
    return new java.awt.Color(niftyColor.getRed(), niftyColor.getBlue(), niftyColor.getGreen(), niftyColor.getAlpha());
  }

  /**
   * Implementation of Batch for Java2D. A Java2D batch is limited by Java2D functionality, primarily in that it is
   * incapable of buffered rendering. This class primarily serves the purpose of filling in for the Java2D renderer
   * implementation. It should be noted that Java2DBatchInternal MUST be an inner type of Java2DBatchRenderBackend
   * because it needs to be able to access the enclosing type's BufferStrategy directly.
   *
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
      for (int i = 0; i < quads.length; i++) {
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
      g2d.scale(1,-1);
      g2d.translate(0, -canvas.getHeight());
      if (shouldUseHighQualityTextures) {
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      } else {
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
      }

      for (int i = 0; i < primitiveCount; i++) {
        BatchQuad quad = quads[i];
        // flip y-coord for AWT top-down coordinate system
        final int quadFlipY = canvas.getHeight() - quad.y - quad.height;
        if (boundTexture != null && boundTexture.getID() == textureId) {
          log.fine("batch internal: tex bound [id=" + textureId + "]");
          float xysq = (float) Math.sqrt(Math.pow(boundTexture.getWidth(), 2) + Math.pow(boundTexture.getHeight(), 2));
          log.info("addQuad tex-info: " + quad.tx + " " + quad.ty + " " + quad.twidth * xysq + " " + quad.theight * xysq);
          boundTexture.drawTexture(g2d,
                                   quad.x,
                                   quadFlipY,
                                   quad.width,
                                   quad.height,
                                   (int) Math.round(quad.tx * xysq),
                                   (int) Math.round(quad.ty * xysq),
                                   (int) Math.round(quad.twidth * xysq),
                                   (int) Math.round(quad.theight * xysq));
        } else {
          log.fine("batch internal: no tex bound");
          g2d.drawImage(quad.defaultColorImg, quad.x, quadFlipY, quad.width, quad.height, null);
        }
      }
      g2d.dispose();
    }

    @Override
    public boolean canAddQuad() {
      return primitiveCount < quads.length;
    }

    @Override
    public void addQuad(float x,
                        float y,
                        float width,
                        float height,
                        @Nonnull Color color1,
                        @Nonnull Color color2,
                        @Nonnull Color color3,
                        @Nonnull Color color4,
                        float textureX,
                        float textureY,
                        float textureWidth,
                        float textureHeight) {
      BufferedImage colorQuad = createInterpolatedColorImage(width, height, color1, color2, color3, color4);
      quads[primitiveCount++] = new BatchQuad((int) x, (int) y, (int) width, (int) height, textureX,
          textureY, textureWidth, textureHeight, colorQuad);
    }

    // utility methods for Java2DBatchInternal

    private BufferedImage createInterpolatedColorImage(float width, float height, Color c1, Color c2, Color c3, Color c4) {
      BufferedImage temp = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
      temp.setRGB(0, 0, niftyColorToAwt(c1).getRGB());
      temp.setRGB(0, 1, niftyColorToAwt(c2).getRGB());
      temp.setRGB(1, 1, niftyColorToAwt(c3).getRGB());
      temp.setRGB(1, 0, niftyColorToAwt(c4).getRGB());
      int wt = (int) Math.ceil(width), ht = (int) Math.ceil(height);
      BufferedImage img = new BufferedImage(wt, ht, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = (Graphics2D) img.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.drawImage(temp, 0, 0, wt, ht, null);
      g.dispose();
      return img;
    }
  }

  private class BatchQuad {
    int x, y, width, height;
    float tx, ty, twidth, theight;
    BufferedImage defaultColorImg;

    BatchQuad(int x,
              int y,
              int width,
              int height,
              float tx,
              float ty,
              float twidth,
              float theight,
              @Nonnull BufferedImage defaultColorImg) {
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
      this.tx = tx;
      this.ty = ty;
      this.twidth = twidth;
      this.theight = theight;
      this.defaultColorImg = defaultColorImg;
    }

    @Override
    public String toString() {
      return String.format("{%d,%d,%d,%d,%d,%d,%d,%d}", x, y, width, height, tx, ty, twidth, theight);
    }
  }
}
