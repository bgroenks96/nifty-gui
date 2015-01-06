package de.lessvoid.nifty.java2d.renderer.batch;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.WeakHashMap;

import de.lessvoid.nifty.tools.Color;

public class QuadGradientPaint implements Paint {
	
	private Color c0, c1, c2, c3;
	
	public QuadGradientPaint(
			final Color c0,
			final Color c1,
			final Color c2,
			final Color c3) {
		this.c0 = c0;
		this.c1 = c1;
		this.c2 = c2;
		this.c3 = c3;
	}

	@Override
	public int getTransparency() {
		return Paint.TRANSLUCENT;
	}

	@Override
	public PaintContext createContext(ColorModel cm, Rectangle deviceBounds,
			Rectangle2D userBounds, AffineTransform xform, RenderingHints hints) {
		return new QuadGradientPaintContext(cm, deviceBounds, userBounds, xform, hints);
	}
	
	private static WeakHashMap<ColorModel, Raster> rasterCache = new WeakHashMap<ColorModel, Raster>();

	private class QuadGradientPaintContext implements PaintContext {
		
		ColorModel cm;
		Rectangle deviceBounds;
		Rectangle2D userBounds;
		
		Raster saved;

		public QuadGradientPaintContext(ColorModel cm, Rectangle deviceBounds,
				Rectangle2D userBounds, AffineTransform xform, RenderingHints hints) {
			this.cm = cm;
			this.deviceBounds = deviceBounds;
			this.userBounds = userBounds;
		}

		@Override
		public void dispose() {
			if (saved != null) {
				rasterCache.put(cm, saved);
				saved = null;
			}
		}

		@Override
		public ColorModel getColorModel() {
			return cm;
		}

		@Override
		public Raster getRaster(int x, int y, int w, int h) {
			checkRasterCache();
			if (saved == null || w < saved.getWidth() || h < saved.getHeight()) {
				saved = createRaster(w, h);
			}
			return saved;
		}
		
		private Raster createRaster(int w, int h) {
			WritableRaster raster = cm.createCompatibleWritableRaster(w, h);
			final double radius = Math.sqrt(w*w +h*h);
			float[] color = new float[4];
			for (int j=0; j < h; j++) {
				for (int i=0; i < w; i++) {
					if (!deviceBounds.contains(i, j) || !userBounds.contains(i, j)) continue;
					double distC0 = Math.max(Point.distance(i, j, 0, 0), radius);
					float ratioC0 = (float) (1 - (distC0 / radius));
					double distC1 = Math.max(Point.distance(i, j, 0, h), radius);
					float ratioC1 = (float) (1 - (distC1 / radius));
					double distC2 = Math.max(Point.distance(i, j, w, h), radius);
					float ratioC2 = (float) (1 - (distC2 / radius));
					double distC3 = Math.max(Point.distance(i, j, w, 0), radius);
					float ratioC3 = (float) (1 - (distC3 / radius));
					color[0] = (ratioC0 * c0.getRed() + ratioC1 * c1.getRed() + ratioC2 * c2.getRed() + ratioC3 * c3.getRed()) / 4;
					color[1] = (ratioC0 * c0.getGreen() + ratioC1 * c1.getGreen() + ratioC2 * c2.getGreen() + ratioC3 * c3.getGreen()) / 4;
					color[2] = (ratioC0 * c0.getBlue() + ratioC1 * c1.getBlue() + ratioC2 * c2.getBlue() + ratioC3 * c3.getBlue()) / 4;
					color[3] = (ratioC0 * c0.getAlpha() + ratioC1 * c1.getAlpha() + ratioC2 * c2.getAlpha() + ratioC3 * c3.getAlpha()) / 4;
					raster.setPixel(i, j, color);
				}
			}
			return raster;
		}
		
		private void checkRasterCache() {
			if (saved == null) {
				saved = rasterCache.get(cm);
			}
		}
	}
}
