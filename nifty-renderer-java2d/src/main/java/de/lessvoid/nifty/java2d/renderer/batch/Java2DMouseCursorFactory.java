package de.lessvoid.nifty.java2d.renderer.batch;

import java.awt.Component;
import java.io.IOException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import de.lessvoid.nifty.render.batch.spi.MouseCursorFactory;
import de.lessvoid.nifty.spi.render.MouseCursor;
import de.lessvoid.nifty.tools.resourceloader.NiftyResourceLoader;

/**
 * @author Aaron Mahan &lt;aaron@forerunnergames.com&gt;
 */
public class Java2DMouseCursorFactory implements MouseCursorFactory {

	private final Component activeComponent;

	public Java2DMouseCursorFactory(@Nonnull final Component activeComponent) {
		this.activeComponent = activeComponent;
	}

  @Nullable
  @Override
  public MouseCursor create(
          @Nonnull String filename,
          int hotspotX,
          int hotspotY,
          @Nonnull NiftyResourceLoader resourceLoader) throws IOException {
    return new Java2DMouseCursor(filename, hotspotX, hotspotY, activeComponent, resourceLoader);
  }
}
