package de.lessvoid.nifty.java2d.renderer.batch;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.annotation.Nonnull;

import de.lessvoid.nifty.render.batch.spi.BufferFactory;

public class Java2DBufferFactory implements BufferFactory {
	@Nonnull
	@Override
	public ByteBuffer createNativeOrderedByteBuffer(final int numBytes) {
		return ByteBuffer.allocateDirect(numBytes).order(ByteOrder.nativeOrder());
	}

	@Nonnull
	@Override
	public FloatBuffer createNativeOrderedFloatBuffer(final int numFloats) {
		return ByteBuffer.allocateDirect(numFloats).order(ByteOrder.nativeOrder()).asFloatBuffer();
	}

	@Nonnull
	@Override
	public IntBuffer createNativeOrderedIntBuffer(final int numInts) {
		return ByteBuffer.allocateDirect(numInts).order(ByteOrder.nativeOrder()).asIntBuffer();
	}
}
