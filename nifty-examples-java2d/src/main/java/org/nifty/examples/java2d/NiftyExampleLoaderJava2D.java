package org.nifty.examples.java2d;

import java.awt.Canvas;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import org.nifty.examples.java2d.Java2DNiftyRunner.Callback;

import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.examples.NiftyExample;
import de.lessvoid.nifty.examples.console.ConsoleDemoStartScreen;

/**
 * Loads & runs any {@link de.lessvoid.nifty.examples.NiftyExample} using Nifty and/or Java2D.
 *
 * @author Aaron Mahan &lt;aaron@forerunnergames.com&gt;
 * @author Brian Groenke
 */
public class NiftyExampleLoaderJava2D {
  @Nonnull
  private static final Logger log = Logger.getLogger(NiftyExampleLoaderJava2D.class.getName());

  /**
   * This is the standard, simple way to run a {@link de.lessvoid.nifty.examples.NiftyExample}. Just instantiate your
   * example and pass it along with your main method's arguments to this method. This method will use Java2D to run your
   * example, automatically creating & initializing a AWT application for you before running the example.
   *
   * @param example The {@link de.lessvoid.nifty.examples.NiftyExample} to run.
   * @param args The arguments from your application's main method.
   */
  public static void runWithJava2D(@Nonnull final NiftyExample example, @Nonnull final String... args) {
    runWithJava2D(new Callback() {
      @Override
      public void init(@Nonnull Nifty nifty, Canvas awtCanvas) {
        runWithNifty(example, nifty);
      }
    }, args);
  }

  /**
   *
   * @see #runWithNifty(de.lessvoid.nifty.examples.NiftyExample, de.lessvoid.nifty.Nifty)
   *
   * @param callback Your custom {@link Java2DNiftyRunner.lessvoid.nifty.examples.jogl.JOGLNiftyRunner.Callback} implementation.
   * @param args The arguments from your application's main method.
   */
  public static void runWithJava2D(@Nonnull final Callback callback, @Nonnull final String... args) {
    try {
      Java2DNiftyRunner.run(args, callback);
    } catch (Exception e) {
      log.log(Level.SEVERE, "Unable to run Nifty example!", e);
    }
  }

  /**
   * Directly run a {@link de.lessvoid.nifty.examples.NiftyExample} without AWT. Since you must have a valid Nifty
   * instance, this is useful inside of a custom {@link Java2DNiftyRunner.lessvoid.nifty.examples.java2d.Java2DNiftyRunner.Callback} in
   * conjunction with {@link #runWithJava2D(Java2DNiftyRunner.lessvoid.nifty.examples.java2d.Java2DNiftyRunner.Callback, String...)}, since
   * your custom Callback method will receive a valid Nifty instance. For an example (no pun intended), see
   * {@link de.lessvoid.nifty.examples.java2d.defaultcontrols.ControlsDemoMain}. This method will use Nifty to run your
   * example, without creating or initializing an AWT application first. It is up to you to create & initialize your
   * own AWT application, either by using
   * {@link #runWithJava2D(Java2DNiftyRunner.lessvoid.nifty.examples.java2d.Java2DNiftyRunner.Callback, String...)} or by writing your own
   * custom AWT application code.
   *
   * @see #runWithJava2D(Java2DNiftyRunner.lessvoid.nifty.examples.java2d.Java2DNiftyRunner.Callback, String...)
   *
   * @param example The {@link de.lessvoid.nifty.examples.NiftyExample} to run.
   * @param nifty The Nifty instance to use to run this example.
   */
  public static void runWithNifty(@Nonnull final NiftyExample example, @Nonnull final Nifty nifty) {
    try {
      example.prepareStart(nifty);
      if (example.getMainXML() != null) {
        nifty.fromXml(example.getMainXML(), example.getStartScreen());
      } else {
        nifty.gotoScreen(example.getStartScreen());
      }
    } catch (Exception e) {
      log.log(Level.SEVERE, "Unable to run Nifty example!", e);
    }
  }

  public static void main(String[] args) {
    runWithJava2D(new ConsoleDemoStartScreen());
  }
}
