package de.lessvoid.nifty.examples.lwjgl.console;

import de.lessvoid.nifty.examples.console.ConsoleDemoStartScreen;
import de.lessvoid.nifty.examples.lwjgl.NiftyExampleLoaderLWJGL;

import javax.annotation.Nonnull;

/**
 * @author Aaron Mahan &lt;aaron@forerunnergames.com&gt;
 */
public final class ConsoleDemoMain {
  public static void main(@Nonnull final String... args) {
    NiftyExampleLoaderLWJGL.run (new ConsoleDemoStartScreen());
  }
}