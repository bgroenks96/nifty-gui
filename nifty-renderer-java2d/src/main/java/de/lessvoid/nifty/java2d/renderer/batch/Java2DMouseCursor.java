package de.lessvoid.nifty.java2d.renderer.batch;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.annotation.Nonnull;

import de.lessvoid.nifty.render.io.ImageLoader;
import de.lessvoid.nifty.render.io.ImageLoaderFactory;
import de.lessvoid.nifty.spi.render.MouseCursor;
import de.lessvoid.nifty.tools.resourceloader.NiftyResourceLoader;

public class Java2DMouseCursor implements MouseCursor {

  private final Cursor cursor;

  private Component activeComponent;

  public Java2DMouseCursor(@Nonnull final String filename,
                           final int hotspotX,
                           final int hotspotY,
                           final Component awtComponent,
                           @Nonnull final NiftyResourceLoader resourceLoader) throws IOException {
    ImageLoader imageLoader = ImageLoaderFactory.createImageLoader(filename);
    InputStream imageStream = resourceLoader.getResourceAsStream(filename);
    if (imageStream == null) {
        throw new IOException("Cannot find / load mouse cursor image file: [" + filename + "].");
    }
    BufferedImage cursorImage = imageLoader.loadAsBufferedImage(imageStream);
    cursor = Toolkit.getDefaultToolkit().createCustomCursor(cursorImage, new Point(hotspotX, hotspotY), filename);
  }

  private Cursor previousCursor = Cursor.getDefaultCursor();

  @Override
  public void enable() {
    activeComponent.setCursor(cursor);
  }

  @Override
  public void disable() {
    activeComponent.setCursor(previousCursor);
  }

  @Override
  public void dispose() {
    // nothing to do here
  }

  public void setActiveComponent(@Nonnull final Component comp) {
    this.activeComponent = comp;
  }
}
