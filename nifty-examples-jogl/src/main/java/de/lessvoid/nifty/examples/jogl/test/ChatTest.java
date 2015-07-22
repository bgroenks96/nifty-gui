package de.lessvoid.nifty.examples.jogl.test;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.jogamp.newt.event.WindowAdapter;
import com.jogamp.newt.event.WindowEvent;
import com.jogamp.newt.opengl.GLWindow;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.util.FPSAnimator;

import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.NiftyEventSubscriber;
import de.lessvoid.nifty.controls.Chat;
import de.lessvoid.nifty.controls.ChatTextSendEvent;
import de.lessvoid.nifty.nulldevice.NullSoundDevice;
import de.lessvoid.nifty.render.batch.BatchRenderConfiguration;
import de.lessvoid.nifty.render.batch.BatchRenderDevice;
import de.lessvoid.nifty.renderer.jogl.input.JoglInputSystem;
import de.lessvoid.nifty.renderer.jogl.render.JoglBatchRenderBackendCoreProfileFactory;
import de.lessvoid.nifty.screen.Screen;
import de.lessvoid.nifty.screen.ScreenController;
import de.lessvoid.nifty.spi.time.impl.AccurateTimeProvider;


public class ChatTest implements ScreenController, GLEventListener {

	private static final Logger log = Logger.getLogger(ChatTest.class.getName());

	public static void main(String[] args) {
		Logger.getLogger("de.lessvoid").setLevel(Level.ALL);
		new ChatTest().start();
	}
	private Nifty nifty;
	private Screen screen;

	private GLWindow newtWin;

	public void start() {
		System.out.println(Logger.getLogger("de.lessvoid").getLevel());

		GLProfile glp = GLProfile.getMaxProgrammable(true);
		newtWin = GLWindow.create(new GLCapabilities(glp));
		newtWin.addGLEventListener(this);
		final FPSAnimator anim = new FPSAnimator(newtWin, 30);
		newtWin.addWindowListener(new WindowAdapter() {
			@Override
			public void windowDestroyNotify(WindowEvent e) {
				anim.stop();
			}
		});
		newtWin.setSize(800, 600);
		newtWin.setVisible(true);

		anim.start();

		Logger.getLogger("de.lessvoid").setLevel(Level.ALL);
	}

	public void bind(Nifty nifty, Screen screen) {
		System.out.println("bind(" + screen.getScreenId() + ")");
		this.screen = screen;
		final Chat chat = screen.findNiftyControl("chat1", Chat.class);
		chat.addPlayer("Cris", null);
		chat.update();
	}

	public void onStartScreen() {

		System.out.println("onStartScreen");


	}

	public void onEndScreen() {
		System.out.println("onEndScreen");
	}

	public void quit(){
		nifty.gotoScreen("end");
	}

	@NiftyEventSubscriber(id="chat1")
	public final void onSendText(final String id, final ChatTextSendEvent event) {
		final Chat chat = screen.findNiftyControl(id, Chat.class);
		log.info("submitting chat event to control: " + event.getText());
		chat.receivedChatLine(event.getText(), null, null);
		chat.update();
	}

	@Override
	public void init(GLAutoDrawable drawable) {
		JoglInputSystem input = new JoglInputSystem(newtWin);
		BatchRenderDevice render = new BatchRenderDevice(JoglBatchRenderBackendCoreProfileFactory.create(newtWin), new BatchRenderConfiguration());
		nifty = new Nifty(render, new NullSoundDevice(),
				input, new AccurateTimeProvider());
		nifty.fromXml("test.xml", "GScreen0", this);
	}

	@Override
	public void dispose(GLAutoDrawable drawable) {

	}

	@Override
	public void display(GLAutoDrawable drawable) {
		if (!nifty.update())
			nifty.render(true);
	}

	@Override
	public void reshape(GLAutoDrawable drawable, int x, int y, int width,
			int height) {

	}
}