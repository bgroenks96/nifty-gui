package de.lessvoid.nifty.java2d.renderer.batch;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.imageio.ImageIO;

import de.lessvoid.nifty.render.batch.spi.BatchRenderBackend.ByteBufferedImage;

public class Java2DImage extends ByteBufferedImage {
	
	private static final Logger log = Logger.getLogger(Java2DImage.class.getName());
	
	@Nonnull
	private BufferedImage javaImg;
	
	public Java2DImage(@Nonnull final ByteBuffer buffer, final int width, final int height) {
		super(buffer, width, height);
		loadBufferedImage(buffer, width, height);
	}

	@Override
	public int getWidth() {
		return super.getWidth();
	}

	@Override
	public int getHeight() {
		return super.getHeight();
	}
	
	@Nonnull
	public BufferedImage getBufferedImage() {
		return javaImg;
	}

	private void loadBufferedImage(ByteBuffer buffer, int width, int height) {
		try {
			BufferedImage buffImg = ImageIO.read(new ByteBufferInputStream(buffer));
			javaImg = new BufferedImage(width, height, buffImg.getType());
			Graphics2D g2d = javaImg.createGraphics();
			g2d.drawImage(buffImg, 0, 0, null);
			g2d.dispose();
		} catch (IOException e) {
			log.warning("failed to load BufferedImage instance from ByteBuffer: " + e.toString());
		}
	}
	
	class ByteBufferInputStream extends InputStream {
		
		final ByteBuffer buffRead;
		
		ByteBufferInputStream(ByteBuffer buffRead) {
			this.buffRead = buffRead;
		}

		@Override
		public int read() throws IOException {
			return buffRead.get();
		}
		
		public void reset() {
			buffRead.rewind();
		}
	}
}
