package de.lessvoid.nifty.java2d.renderer.batch;

import java.awt.AWTException;
import java.awt.Canvas;

import javax.annotation.Nonnull;

import de.lessvoid.nifty.render.batch.spi.BatchRenderBackend;

/**
 * @author Aaron Mahan &lt;aaron@forerunnergames.com&gt;
 */
public class Java2DBatchRenderBackendFactory {
  @Nonnull
  public static BatchRenderBackend create(Canvas awtCanvas, int numBuffers) {
    try {
      return new Java2DBatchRenderBackend(awtCanvas,
                                          numBuffers,
                                          new Java2DImageFactory(),
                                          new Java2DBufferFactory(),
                                          new Java2DMouseCursorFactory(awtCanvas));
    } catch (AWTException e) {
      e.printStackTrace();
      return null;
    }
  }
}
