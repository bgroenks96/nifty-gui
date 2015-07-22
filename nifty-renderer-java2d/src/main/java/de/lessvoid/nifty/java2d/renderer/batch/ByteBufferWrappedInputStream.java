package de.lessvoid.nifty.java2d.renderer.batch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Wraps an InputStream and acts as an intermediary buffer by simultaneously writing
 * all data returned by read calls to a pre-allocated ByteBuffer. The internal buffer
 * will be reallocated as needed to expand.
 * @author Brian Groenke
 *
 */
class ByteBufferWrappedInputStream extends InputStream {

  private static final int PREALLOC = 8192, REALLOC = 2048;

  ByteBuffer buffer;
  InputStream in;

  ByteBufferWrappedInputStream(InputStream toWrap) {
    this.in = toWrap;
    this.buffer = ByteBuffer.allocateDirect(PREALLOC);
  }

  @Override
  public int read() throws IOException {
    checkBuffer(1);
    int next = in.read();
    buffer.put((byte) next);
    return next;
  }

  @Override
  public int read(byte[] buff) throws IOException {
    checkBuffer(buff.length);
    buffer.put(buff);
    return in.read(buff);
  }

  /**
   * Creates a copy of the internal data buffer as-is and returns it.
   * @return
   */
  public ByteBuffer copyBuffer() {
    buffer.flip();
    ByteBuffer resultBuffer = ByteBuffer.allocate(buffer.limit());
    resultBuffer.put(buffer);
    resultBuffer.flip();
    buffer.limit(buffer.capacity());
    return resultBuffer;
  }

  /**
   * Creates a slice of the internal data buffer as-is and returns it.
   * This method uses Buffer.slice to avoid copying data. However, it should
   * be noted that maintaining references to the returned slice will adversely
   * keep the entire internal buffer allocated in system memory. You should use
   * {@link #copyBuffer()} if the returned ByteBuffer reference will be held
   * for any significant period of time.
   * @return
   */
  public ByteBuffer sliceBuffer() {
    int pos = buffer.position();
    buffer.flip();
    ByteBuffer slice = buffer.slice();
    buffer.limit(buffer.capacity());
    buffer.position(pos);
    return slice;
  }

  private void checkBuffer(int nextRead) {
    if (buffer.remaining() <= nextRead) {
      reallocBuffer();
    }
  }

  private void reallocBuffer() {
    ByteBuffer curr = buffer;
    curr.flip();
    buffer = ByteBuffer.allocateDirect(curr.capacity() + REALLOC);
    buffer.put(curr);
    curr.clear();
  }
}
