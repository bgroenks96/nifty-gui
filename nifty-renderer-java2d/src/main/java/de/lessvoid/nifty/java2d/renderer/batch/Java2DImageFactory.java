package de.lessvoid.nifty.java2d.renderer.batch;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import java.nio.ByteBuffer;

import de.lessvoid.nifty.render.batch.spi.BatchRenderBackend;
import de.lessvoid.nifty.render.batch.spi.BatchRenderBackend.ByteBufferedImage;
import de.lessvoid.nifty.render.batch.spi.BatchRenderBackend.Image;
import de.lessvoid.nifty.render.batch.spi.ImageFactory;


public class Java2DImageFactory implements ImageFactory {

	@Override
	public Image create(ByteBuffer buffer, int imageWidth, int imageHeight) {
		return new Java2DImage(buffer, imageWidth, imageHeight);
	}

	@Override
	public ByteBuffer asByteBuffer(Image image) {
		return (image instanceof BatchRenderBackend.ByteBufferedImage) ? ((ByteBufferedImage)image).getBuffer(): null;
	}
	
	public BufferedImage createNativeBufferedImage(int width, int height) {
		return getGraphicsDevice().getDefaultConfiguration().createCompatibleImage(width, height);
	}
	
	public VolatileImage createNativeVolatileImage(int width, int height) {
		return getGraphicsDevice().getDefaultConfiguration().createCompatibleVolatileImage(width, height);
	}
	
	private GraphicsDevice getGraphicsDevice() {
		GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
		if (device == null)
			throw new RuntimeException("TextureImageJava2D: environment returned null GraphisDevice");
		return device;
	}
}
