package org.nifty.examples.java2d;

import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.io.InputStream;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import paulscode.sound.SoundSystemException;
import paulscode.sound.libraries.LibraryJavaSound;
import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.examples.LoggerShortFormat;
import de.lessvoid.nifty.java2d.input.InputSystemAwtImpl;
import de.lessvoid.nifty.java2d.renderer.GraphicsWrapper;
import de.lessvoid.nifty.java2d.renderer.RenderDeviceJava2dImpl;
import de.lessvoid.nifty.java2d.renderer.batch.Java2DBatchRenderBackendFactory;
import de.lessvoid.nifty.nulldevice.NullSoundDevice;
import de.lessvoid.nifty.render.batch.BatchRenderDevice;
import de.lessvoid.nifty.sound.paulssoundsystem.PaulsSoundsystemSoundDevice;
import de.lessvoid.nifty.spi.render.RenderDevice;
import de.lessvoid.nifty.spi.sound.SoundDevice;
import de.lessvoid.nifty.spi.time.TimeProvider;
import de.lessvoid.nifty.spi.time.impl.AccurateTimeProvider;

/**
 * Takes care of Java2D initialization.
 *
 * @author void
 */
public class Java2DNiftyRunner {

  private static final Logger log = Logger.getLogger(Java2DNiftyRunner.class.getName());
  private static final int FPS = 60;
  private static final int CANVAS_WIDTH = 1024;
  private static final int CANVAS_HEIGHT = 768;
  private static TimeProvider timeProvider = new AccurateTimeProvider();

  @Nonnull
  private static Mode mode = Mode.Batch;

  @Nullable
  private Nifty nifty;
  private JFrame window;
  private Canvas awtCanvas;

  public static void run(@Nonnull final String[] args, final Callback callback) throws Exception {
    InputStream input = null;
    try {
      input = LoggerShortFormat.class.getClassLoader().getResourceAsStream("logging.properties");
      LogManager.getLogManager().readConfiguration(input);
    } finally {
      if (input != null) {
        input.close();
      }
    }

    if (args.length == 1) {
      mode = Mode.findMode(args[0]);
    }

    System.out.println("using mode " + mode.getName());

    Java2DNiftyRunner runner = new Java2DNiftyRunner("Nifty Runner AWT/Java2D", callback);
    runner.init();
  }

  private Java2DNiftyRunner(String displayTitle, Callback callback) {
    window = new JFrame(displayTitle);
    awtCanvas = new Canvas();
    awtCanvas.setPreferredSize(new Dimension(CANVAS_WIDTH, CANVAS_HEIGHT));
    window.add(awtCanvas);
    window.pack();
    window.setLocationRelativeTo(null);
    window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    RenderDevice renderDevice;
    switch (mode) {
    case Batch:
      renderDevice = new BatchRenderDevice(Java2DBatchRenderBackendFactory.create(awtCanvas, 2));
      break;
    case Old:
    default:
      renderDevice = new RenderDeviceJava2dImpl(new GraphicsWrapper() {

        @Override
        public Graphics2D getGraphics2d() {
          return (Graphics2D) awtCanvas.getGraphics();
        }

        @Override
        public int getHeight() {
          return awtCanvas.getHeight();
        }

        @Override
        public int getWidth() {
          return awtCanvas.getWidth();
        }

      });
    }

    SoundDevice soundDevice;
    try {
      soundDevice = new PaulsSoundsystemSoundDevice(LibraryJavaSound.class);
    } catch (SoundSystemException e) {
      soundDevice = new NullSoundDevice();
    }

    nifty = new Nifty(renderDevice, soundDevice, new InputSystemAwtImpl(), timeProvider);
    callback.init(nifty, awtCanvas);
  }

  private void init() {
    RenderLoop loop = new RenderLoop();
    loop.setTargetFPS(FPS);
    loop.setTargetTPS(30);
    new Thread(loop).start();
  }

  private void update(long lastUpdateTime) {
    nifty.update();
  }

  private void render(float interpolation) {
    nifty.render(true);
  }

  /**
   * Runnable type for managing the Nifty render loop.
   *
   * Borrowed and modified from The 2DX Project (github.com/bgroenks96/2DX-GL).
   * See com.snap2d.gl.RenderControl.RenderLoop for original version.
   *
   * @author Brian Groenke
   */
  private class RenderLoop implements Runnable {

    // Default values
    private final double TARGET_FPS = 60, TARGET_TIME_BETWEEN_RENDERS = 1000000000.0 / TARGET_FPS, TICK_HERTZ = 30,
        TIME_BETWEEN_UPDATES = 1000000000.0 / TICK_HERTZ, MAX_UPDATES_BEFORE_RENDER = 3;

    private final long SLEEP_WHILE_INACTIVE = 100;

    private double targetFPS = TARGET_FPS, targetTimeBetweenRenders = TARGET_TIME_BETWEEN_RENDERS,
        tickHertz = TICK_HERTZ, timeBetweenUpdates = TIME_BETWEEN_UPDATES, maxUpdates = MAX_UPDATES_BEFORE_RENDER;

    volatile int fps, tps;
    volatile boolean running, active, noUpdate, printFrames;

    @Override
    public void run() {

      Thread.currentThread().setName("java2d-render-loop");

      Thread sleeperThread = new Thread(new Runnable() {

        @Override
        public void run() {

          Thread.currentThread().setName("java2d-win32-timer");
          try {
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
              System.out.println("started windows sleeper daemon");
              Thread.sleep(Long.MAX_VALUE);
            }
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
      });
      sleeperThread.setDaemon(true);
      sleeperThread.start();

      Thread fpsThread = new Thread(new Runnable() {

        @Override
        public void run() {

          Thread.currentThread().setName("java2d-printfames");
          while (running) {
            try {
              Thread.sleep(800);
              while (!printFrames) {
                ;
              }
              String printStr = fps + " fps " + tps + " ticks";
              log.fine(printStr);
              printFrames = false;
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
          }
        }

      });
      fpsThread.setDaemon(true);
      fpsThread.start();

      double lastUpdateTime = System.nanoTime();
      double lastRenderTime = System.nanoTime();
      int lastSecondTime = (int) (lastUpdateTime / 1000000000);
      int frameCount = 0, ticks = 0;
      running = true;
      active = true;

      window.setVisible(true);

      while (running) {
        try {

          double now = System.nanoTime();
          if (active) {

            int updateCount = 0;

            while (now - lastUpdateTime > timeBetweenUpdates && updateCount < maxUpdates && !noUpdate) {

              update((long) lastUpdateTime);

              lastUpdateTime += timeBetweenUpdates;
              updateCount++;
              ticks++;
            }

            if (now - lastUpdateTime > timeBetweenUpdates && !noUpdate) {
              lastUpdateTime = now - timeBetweenUpdates;
            }

            float interpolation = Math.min(1.0f, (float) ( (now - lastUpdateTime) / timeBetweenUpdates));
            render(interpolation);
            lastRenderTime = now;
            frameCount++;

            int thisSecond = (int) (now / 1000000000);
            if (thisSecond > lastSecondTime) {
              fps = frameCount;
              tps = ticks;
              printFrames = true;
              frameCount = 0;
              ticks = 0;
              lastSecondTime = thisSecond;
            }
          }

          if (!active) {
            fps = 0;
            tps = 0;
            printFrames = true;
          }

          while (now - lastRenderTime < targetTimeBetweenRenders
              && (now - lastUpdateTime < timeBetweenUpdates || noUpdate)) {
            Thread.yield();
            now = System.nanoTime();
          }

          if (!active) {
            // preserve CPU if loop is currently is currently inactive.
            // the constant can be lowered to reduce latency when re-focusing.
            Thread.sleep(SLEEP_WHILE_INACTIVE);
          }
        } catch (Exception e) {
          System.err.println("error in rendering loop - terminating loop execution...");
          e.printStackTrace();
          running = false;
        }
      }
    }

    void setTargetFPS(final int fps) {

      if (fps < 0) {
        return;
      }
      targetFPS = fps;
      targetTimeBetweenRenders = 1000000000.0 / targetFPS;
    }

    void setTargetTPS(final int tps) {

      if (tps < 0) {
        return;
      }
      tickHertz = tps;
      timeBetweenUpdates = 1000000000.0 / tickHertz;
    }
  }

  public interface Callback {
    void init(@Nonnull Nifty nifty, @Nonnull Canvas awtCanvas);
  }

  private static enum Mode {
    Old("old"), Batch("batch");

    private final String name;

    private Mode(final String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    @Nonnull
    public static Mode findMode(final String arg) {
      for (Mode mode : Mode.values()) {
        if (mode.matches(arg)) {
          return mode;
        }
      }
      return Mode.Old;
    }

    private boolean matches(final String check) {
      return name.equals(check);
    }
  }
}
