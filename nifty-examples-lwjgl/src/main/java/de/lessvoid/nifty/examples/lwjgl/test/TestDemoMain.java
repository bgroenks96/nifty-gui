package de.lessvoid.nifty.examples.lwjgl.test;

import de.lessvoid.nifty.examples.lwjgl.NiftyExampleLoaderLWJGL;
import de.lessvoid.nifty.examples.test.TestScreen;

/**
 * @author Aaron Mahan &lt;aaron@forerunnergames.com&gt;
 */
public class TestDemoMain {
  public static void main(final String[] args) {
    NiftyExampleLoaderLWJGL.run(new TestScreen());
  }
}
