import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.Arrays;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;

import komposten.leapjna.LeapC;
import komposten.leapjna.leapc.data.LEAP_CONNECTION;
import komposten.leapjna.leapc.data.LEAP_CONNECTION_INFO;
import komposten.leapjna.leapc.data.LEAP_CONNECTION_MESSAGE;
import komposten.leapjna.leapc.data.LEAP_DEVICE;
import komposten.leapjna.leapc.data.LEAP_DEVICE_INFO;
import komposten.leapjna.leapc.data.LEAP_DEVICE_REF;
import komposten.leapjna.leapc.data.LEAP_DIGIT;
import komposten.leapjna.leapc.data.LEAP_HAND;
import komposten.leapjna.leapc.data.LEAP_IMAGE;
import komposten.leapjna.leapc.data.LEAP_POINT_MAPPING;
import komposten.leapjna.leapc.data.LEAP_VARIANT;
import komposten.leapjna.leapc.data.LEAP_VECTOR;
import komposten.leapjna.leapc.enums.eLeapEventType;
import komposten.leapjna.leapc.enums.eLeapImageFormat;
import komposten.leapjna.leapc.enums.eLeapPolicyFlag;
import komposten.leapjna.leapc.enums.eLeapRS;
import komposten.leapjna.leapc.events.LEAP_CONFIG_CHANGE_EVENT;
import komposten.leapjna.leapc.events.LEAP_CONFIG_RESPONSE_EVENT;
import komposten.leapjna.leapc.events.LEAP_DEVICE_EVENT;
import komposten.leapjna.leapc.events.LEAP_DEVICE_STATUS_CHANGE_EVENT;
import komposten.leapjna.leapc.events.LEAP_DROPPED_FRAME_EVENT;
import komposten.leapjna.leapc.events.LEAP_HEAD_POSE_EVENT;
import komposten.leapjna.leapc.events.LEAP_IMAGE_EVENT;
import komposten.leapjna.leapc.events.LEAP_LOG_EVENT;
import komposten.leapjna.leapc.events.LEAP_LOG_EVENTS;
import komposten.leapjna.leapc.events.LEAP_POINT_MAPPING_CHANGE_EVENT;
import komposten.leapjna.leapc.events.LEAP_TRACKING_EVENT;
import komposten.leapjna.leapc.util.ArrayByReference;

/*FIXME Check for memory leaks!
 *   We're currently releasing the native memory from the tracking events after
 *   Passing the data to the render panel.
 *
 *   Should probably mention that this might be needed in the docs for 
 *   LEAP_CONNECTION_MESSAGE. But verify that it is needed first!
 *   
 *   Might not be enough with just data.clear(). I still see the same pattern
 *   of occasionally climbing memory over long periods of times (15+ minutes).
 */


public class LeapTestGui extends JFrame
{
	private static final int FRAME_RATE = 60;
	private static final float FRAME_TIME = 1000f / FRAME_RATE;
	private RenderPanel renderPanel;
	private Thread leapJnaThread;

	public LeapTestGui()
	{
		buildUi();
		addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_ENTER && leapJnaThread == null)
				{
					leapJnaThread = new Thread(LeapTestGui.this::startLoop, "LeapJna Thread");
					leapJnaThread.start();
				}
				else if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
				{
					terminate();
				}
			}
		});

		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent e)
			{
				terminate();
			}
		});
	}


	private void buildUi()
	{
		setTitle("LeapJna - 2D visualiser");
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		setSize(500, 500);
		setLocationRelativeTo(null);

		renderPanel = new RenderPanel();
		setContentPane(renderPanel);

		setVisible(true);
	}


	private void startLoop()
	{
		printHeader("Creating connection");
		renderPanel.setStage(RenderPanel.Stage.Connecting);
		LEAP_CONNECTION leapConnection = new LEAP_CONNECTION();
		eLeapRS result = LeapC.INSTANCE.LeapCreateConnection(null, leapConnection);

		printStatus(leapConnection);
		if (result == eLeapRS.Success)
		{
			printHeader("Opening connection");
			result = LeapC.INSTANCE.LeapOpenConnection(leapConnection.handle);
			printStatus(leapConnection);

			if (result == eLeapRS.Success)
			{
				renderPanel.setStage(RenderPanel.Stage.Running);
				printHeader("Polling connection");

				// doInterpolateLoop(leapConnection);
				doPollLoop(leapConnection);
			}
		}
	}


	private void doInterpolateLoop(LEAP_CONNECTION leapConnection)
	{
		double timer = 0;
		long lastTime = System.nanoTime();
		double frameTimer = 0;
		int framerate = 0;

		while (true)
		{
			// Actively poll the connection to keep up to date with the frames.
			// This is required for interpolation to work.
			LEAP_CONNECTION_MESSAGE message = new LEAP_CONNECTION_MESSAGE();
			LeapC.INSTANCE.LeapPollConnection(leapConnection.handle, 30, message);

			long currentTime = System.nanoTime();
			double deltaTime = (currentTime - lastTime) / 1E6;
			timer += deltaTime;
			frameTimer += deltaTime;
			lastTime = currentTime;

			LongByReference pFrameSize = new LongByReference();

			// Get an interpolated frame at a certain rate.
			if (timer > FRAME_TIME)
			{
				framerate++;
				timer -= FRAME_TIME;

				long timestamp = LeapC.INSTANCE.LeapGetNow();
				eLeapRS frameSizeResult = LeapC.INSTANCE.LeapGetFrameSize(leapConnection.handle,
						timestamp, pFrameSize);

				if (frameSizeResult == eLeapRS.Success)
				{
					LEAP_TRACKING_EVENT pEvent = new LEAP_TRACKING_EVENT(
							(int) pFrameSize.getValue());
					eLeapRS frameResult = LeapC.INSTANCE.LeapInterpolateFrame(leapConnection.handle,
							timestamp, pEvent, pFrameSize.getValue());

					if (frameResult == eLeapRS.Success)
					{
						renderPanel.setFrameData(pEvent);
					}
					else
					{
						System.out.println("Failed to interpolate frame: " + frameResult);
					}
				}
				else
				{
					System.out.println("Failed to get frame size: " + frameSizeResult);
				}
			}

			if (frameTimer > 1000)
			{
				frameTimer -= 1000;
				renderPanel.setFramerate(framerate);
				framerate = 0;
			}

			if (Thread.interrupted())
			{
				break;
			}
		}
	}


	private void doPollLoop(LEAP_CONNECTION leapConnection)
	{
		double trackingTimer = 0;
		double imageTimer = 0;
		long lastTime = System.nanoTime();
		double frameTimer = 0;
		int framerate = 0;

		boolean firstIteration = true;
		LongByReference pRequestID = new LongByReference();

		while (true)
		{
			LEAP_CONNECTION_MESSAGE message = new LEAP_CONNECTION_MESSAGE();
			LeapC.INSTANCE.LeapPollConnection(leapConnection.handle, 30, message);

			if (firstIteration)
			{
				LeapC.INSTANCE.LeapSetPolicyFlags(leapConnection.handle,
						eLeapPolicyFlag.createMask(eLeapPolicyFlag.AllowPauseResume,
								eLeapPolicyFlag.Images, eLeapPolicyFlag.MapPoints),
						0);

				LeapC.INSTANCE.LeapRequestConfigValue(leapConnection.handle, "images_mode",
						pRequestID);
				System.out.println("Images mode get request: " + pRequestID.getValue());
				LeapC.INSTANCE.LeapSaveConfigValue(leapConnection.handle, "images_mode",
						new LEAP_VARIANT(2), pRequestID);
				System.out.println("Images mode change request: " + pRequestID.getValue());

				LongByReference pSize = new LongByReference();
				eLeapRS result = LeapC.INSTANCE.LeapGetPointMappingSize(leapConnection.handle,
						pSize);
				if (result == eLeapRS.Success)
				{
					System.out.println("Point mapping size: " + pSize.getValue());
					LEAP_POINT_MAPPING pointMapping = new LEAP_POINT_MAPPING(
							(int) pSize.getValue());
					result = LeapC.INSTANCE.LeapGetPointMapping(leapConnection.handle, pointMapping,
							pSize);

					if (result == eLeapRS.Success)
					{
						System.out.format("Point mapping: Frame %d at time %d with %d points:%n",
								pointMapping.frame_id, pointMapping.timestamp, pointMapping.nPoints);
						System.out.print("  Points:");
						for (LEAP_VECTOR point : pointMapping.getPoints())
							System.out.format(" [%.02f, %.02f, %.02f]", point.x, point.y, point.z);
						System.out.print("  IDs:");
						for (int id : pointMapping.getIds())
							System.out.format(" %d", id);
					}
					else
					{
						System.out.println("Failed to retrieve point mapping: " + result);
					}
				}
				else
				{
					System.out.println("Failed to retrieve point mapping size " + result);
				}
			}

			long currentTime = System.nanoTime();
			double deltaTime = (currentTime - lastTime) / 1E6;
			trackingTimer += deltaTime;
			imageTimer += deltaTime;
			frameTimer += deltaTime;
			lastTime = currentTime;

			if (message.type == eLeapEventType.Connection.value)
			{
				System.out.println("Connection flags: " + message.getConnectionEvent().flags);
			}
			else if (message.type == eLeapEventType.ConnectionLost.value)
			{
				System.out.println("Connection lost!");
			}
			else if (message.type == eLeapEventType.Device.value)
			{
				LEAP_DEVICE_EVENT deviceEvent = message.getDeviceEvent();
				System.out.format("Device detected: %d | %s (%x)%n", deviceEvent.device.id,
						Arrays.toString(deviceEvent.getStatus()), deviceEvent.status);

				IntByReference pnArray = new IntByReference();
				LeapC.INSTANCE.LeapGetDeviceList(leapConnection.handle, null, pnArray);
				System.out.println("Device count: " + pnArray.getValue());

				ArrayByReference<LEAP_DEVICE_REF> pArray = ArrayByReference
						.empty(LEAP_DEVICE_REF.class, pnArray.getValue());
				LeapC.INSTANCE.LeapGetDeviceList(leapConnection.handle, pArray, pnArray);

				System.out.println(
						Arrays.toString(pArray.getValues(new LEAP_DEVICE_REF[pnArray.getValue()])));

				LEAP_DEVICE phDevice = new LEAP_DEVICE();
				eLeapRS result = LeapC.INSTANCE.LeapOpenDevice(deviceEvent.device, phDevice);

				if (result == eLeapRS.Success)
				{
					LEAP_DEVICE_INFO info = new LEAP_DEVICE_INFO();
					result = LeapC.INSTANCE.LeapGetDeviceInfo(phDevice.handle, info);

					if (result == eLeapRS.InsufficientBuffer || result == eLeapRS.Success)
					{
						info.allocateSerialBuffer(info.serial_length);
						result = LeapC.INSTANCE.LeapGetDeviceInfo(phDevice.handle, info);

						if (result == eLeapRS.Success)
						{
							System.out.format("Device info for device %d:%n", deviceEvent.device.id);
							System.out.format("  Status: %s%n", Arrays.toString(info.getStatus()));
							System.out.format("  Caps: %s%n", Arrays.toString(info.getCapabilities()));
							System.out.format("  PID: %s (%d)%n",
									LeapC.INSTANCE.LeapDevicePIDToString(info.pid), info.pid);
							System.out.format("  Baseline: %d �m%n", info.baseline);
							System.out.format("  Serial: %s%n", info.serial);
							System.out.format("  FoV: %.02f�x%.02f� (HxV)%n",
									Math.toDegrees(info.h_fov), Math.toDegrees(info.v_fov));
							System.out.format("  Range: %d �m%n", info.range);

							LeapC.INSTANCE.LeapCloseDevice(phDevice.handle);
						}
						else
						{
							System.out.println("Failed to read device info: " + result);
						}
					}
					else
					{
						System.out
								.println("Failed to read device info to get serial length: " + result);
					}
				}
				else
				{
					System.out.println("Failed to open device: " + result);
				}
			}
			else if (message.type == eLeapEventType.DeviceStatusChange.value)
			{
				LEAP_DEVICE_STATUS_CHANGE_EVENT deviceEvent = message
						.getDeviceStatusChangeEvent();
				System.out.format("Device changed: %d | From %s (%x) to %s (%x)%n",
						deviceEvent.device.id, Arrays.toString(deviceEvent.getLastStatus()),
						deviceEvent.last_status, Arrays.toString(deviceEvent.getStatus()),
						deviceEvent.status);
			}
			else if (message.type == eLeapEventType.Policy.value)
			{
				System.out.println(
						"Policies: " + Arrays.toString(message.getPolicyEvent().getCurrentPolicy()));
			}
			else if (message.type == eLeapEventType.LogEvent.value)
			{
				LEAP_LOG_EVENT logEvent = message.getLogEvent();
				System.out.println(logEvent.getSeverity() + ": " + logEvent.message);
			}
			else if (message.type == eLeapEventType.LogEvents.value)
			{
				LEAP_LOG_EVENTS logEvents = message.getLogEvents();

				System.out.println("Multiple log events: " + logEvents.nEvents);
				for (LEAP_LOG_EVENT logEvent : logEvents.getEvents())
				{
					System.out.println(logEvent.getSeverity() + ": " + logEvent.message);
				}
			}
			else if (message.type == eLeapEventType.DeviceFailure.value)
			{
				System.out.println(message.getDeviceFailureEvent().status);
			}
			else if (message.type == eLeapEventType.ConfigResponse.value)
			{
				LEAP_CONFIG_RESPONSE_EVENT responseEvent = message.getConfigResponseEvent();
				System.out.println("Config response: " + responseEvent.requestID + " | "
						+ responseEvent.value.getValue() + " (" + responseEvent.value.getType()
						+ ")");
			}
			else if (message.type == eLeapEventType.ConfigChange.value)
			{
				LEAP_CONFIG_CHANGE_EVENT changeEvent = message.getConfigChangeEvent();
				System.out.println(
						"Config change: " + changeEvent.requestID + " | " + changeEvent.value);

				if (changeEvent.requestID == pRequestID.getValue())
				{
					LeapC.INSTANCE.LeapRequestConfigValue(leapConnection.handle, "images_mode",
							pRequestID);
					System.out.println("Images mode get request: " + pRequestID.getValue());
				}
			}
			else if (message.type == eLeapEventType.DroppedFrame.value)
			{
				LEAP_DROPPED_FRAME_EVENT droppedEvent = message.getDroppedFrameEvent();
				System.out.format("Dropped frame: %d (%s)%n", droppedEvent.frame_id,
						droppedEvent.getType());
			}
			else if (message.type == eLeapEventType.HeadPose.value)
			{
				LEAP_HEAD_POSE_EVENT headEvent = message.getHeadPoseEvent();
				System.out.format("Head pose: %d, %s, %s%n", headEvent.timestamp,
						Arrays.toString(headEvent.head_position.asArray()),
						Arrays.toString(headEvent.head_orientation.asArray()));
			}
			else if (message.type == eLeapEventType.PointMappingChange.value)
			{
				LEAP_POINT_MAPPING_CHANGE_EVENT mappingEvent = message
						.getPointMappingChangeEvent();
				System.out.format("Point mapping change: Frame %d at time %d, %d points%n",
						mappingEvent.frame_id, mappingEvent.timestamp, mappingEvent.nPoints);
			}

			if (trackingTimer > FRAME_TIME && message.type == eLeapEventType.Tracking.value)
			{
				renderPanel.setFrameData(message.getTrackingEvent());

				trackingTimer = 0;
				framerate++;
			}

			if (imageTimer > FRAME_TIME && message.type == eLeapEventType.Image.value)
			{
				renderPanel.setImageData(message.getImageEvent());

				imageTimer = 0;
			}

			if (frameTimer > 1000)
			{
				frameTimer -= 1000;
				renderPanel.setFramerate(framerate);
				framerate = 0;
			}


			if (Thread.interrupted())
			{
				break;
			}

			firstIteration = false;
		}

		System.out.println("Closing connection!");
		LeapC.INSTANCE.LeapCloseConnection(leapConnection.handle);
		printStatus(leapConnection);
		LeapC.INSTANCE.LeapDestroyConnection(leapConnection.handle);
	}


	private eLeapRS printStatus(LEAP_CONNECTION leapConnection)
	{
		printHeader("Connection status");

		LEAP_CONNECTION_INFO connectionStatus = new LEAP_CONNECTION_INFO();
		eLeapRS result = LeapC.INSTANCE.LeapGetConnectionInfo(leapConnection.handle,
				connectionStatus);

		if (result == eLeapRS.Success)
		{
			System.out.format("Status: %s%n", connectionStatus.getStatus());
		}
		else
		{
			System.out.format("Failed to get status: %s%n", result);
		}
		return result;
	}


	private void printHeader(String text)
	{
		System.out.println();
		System.out.println("===" + text + "===");

		try
		{
			Thread.sleep(250);
		}
		catch (InterruptedException e)
		{
		}
	}


	private void terminate()
	{
		if (leapJnaThread != null)
		{
			leapJnaThread.interrupt();
		}

		setVisible(false);
		dispose();
	}


	public static void main(String[] args)
	{
		new LeapTestGui();
	}
}



class RenderPanel extends JPanel
{
	public enum Stage
	{
		Startup, Connecting, Running;
	}

	private Stage stage = Stage.Startup;
	private LEAP_TRACKING_EVENT data;
	private LEAP_IMAGE image;
	private BufferedImage texture;
	private int framerate;

	public void setStage(Stage stage)
	{
		this.stage = stage;
		repaint();
	}


	public void setFrameData(LEAP_TRACKING_EVENT data)
	{
		this.data = data;
		repaint();
		data.clear();
	}


	public void setImageData(LEAP_IMAGE_EVENT data)
	{
		this.image = data.image[0];
		createTexture(image);
		SwingUtilities.invokeLater(this::repaint);
		data.clear();
	}


	private void createTexture(LEAP_IMAGE image)
	{
		boolean newTexture = (texture == null || texture.getWidth() != image.properties.width
				|| texture.getHeight() != image.properties.height);

		byte[] imageData = image.getData();

		if (image.properties.getFormat() == eLeapImageFormat.IR)
		{
			if (newTexture)
			{
				texture = new BufferedImage(image.properties.width, image.properties.height,
						BufferedImage.TYPE_BYTE_GRAY);
			}

			byte[] textureData = ((DataBufferByte) texture.getRaster().getDataBuffer())
					.getData();
			System.arraycopy(imageData, 0, textureData, 0, imageData.length);
		}
	}


	public void setFramerate(int framerate)
	{
		this.framerate = framerate;
	}


	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);

		Graphics2D g2d = (Graphics2D) g;
		int offsetX = getWidth() / 2;
		int offsetY = getHeight();

		g2d.setColor(Color.WHITE);
		g2d.fillRect(0, 0, getWidth(), getHeight());

		drawImage(g2d);

		g2d.setColor(Color.BLACK);
		g2d.drawString("Press ESC to exit", 10, getHeight() - 10);

		if (stage == Stage.Startup)
		{
			g2d.setColor(Color.BLACK);
			g2d.drawString("Press ENTER to start tracking", 10, 10);
		}
		else if (stage == Stage.Connecting)
		{
			g2d.setColor(Color.BLACK);
			g2d.drawString("Connecting...", 10, 10);
		}
		else if (stage == Stage.Running)
		{
			g2d.setColor(Color.BLACK);
			g2d.drawString(String.format("Drawing FPS: %d", framerate), 10, 10);

			if (data != null)
			{
				for (int i = 0; i < data.getHands().length; i++)
				{
					drawHand(data.getHands()[i], g2d, offsetX, offsetY);
				}

				g2d.setColor(Color.BLACK);
				drawTrackingInfo(g2d);
			}
		}
	}


	private void drawImage(Graphics2D g2d)
	{
		if (texture != null)
		{
			float ratio = texture.getWidth() / (float) texture.getHeight();
			int width = getWidth();
			int height = (int) (getHeight() / ratio);
			int y = (int) (getHeight() / 2f - height / 2f);

			g2d.drawImage(texture, 0, y, width, height, null);
		}
	}


	private void drawHand(LEAP_HAND hand, Graphics2D g2d, int offsetX, int offsetY)
	{
		g2d.setColor(Color.RED);
		drawPosition(hand.palm.position, 1, g2d, offsetX, offsetY);

		g2d.setColor(Color.BLUE);
		drawFinger(hand.digits.thumb, g2d, offsetX, offsetY);
		drawFinger(hand.digits.index, g2d, offsetX, offsetY);
		drawFinger(hand.digits.middle, g2d, offsetX, offsetY);
		drawFinger(hand.digits.ring, g2d, offsetX, offsetY);
		drawFinger(hand.digits.pinky, g2d, offsetX, offsetY);
	}


	private void drawFinger(LEAP_DIGIT finger, Graphics2D g2d, int offsetX, int offsetY)
	{
		g2d.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g2d.drawLine((int) finger.metacarpal.prev_joint.x + offsetX,
				(int) -finger.metacarpal.prev_joint.y + offsetY,
				(int) finger.metacarpal.next_joint.x + offsetX,
				(int) -finger.metacarpal.next_joint.y + offsetY);
		g2d.drawLine((int) finger.proximal.prev_joint.x + offsetX,
				(int) -finger.proximal.prev_joint.y + offsetY,
				(int) finger.proximal.next_joint.x + offsetX,
				(int) -finger.proximal.next_joint.y + offsetY);
		g2d.drawLine((int) finger.intermediate.prev_joint.x + offsetX,
				(int) -finger.intermediate.prev_joint.y + offsetY,
				(int) finger.intermediate.next_joint.x + offsetX,
				(int) -finger.intermediate.next_joint.y + offsetY);
		g2d.drawLine((int) finger.distal.prev_joint.x + offsetX,
				(int) -finger.distal.prev_joint.y + offsetY,
				(int) finger.distal.next_joint.x + offsetX,
				(int) -finger.distal.next_joint.y + offsetY);
		drawPosition(finger.distal.next_joint, 0.5f, g2d, offsetX, offsetY);
	}


	private void drawPosition(LEAP_VECTOR position, float scale, Graphics2D g2d,
			int offsetX, int offsetY)
	{
		int size = (int) ((position.z + 200) / 400 * 20 * scale + 5);
		g2d.fillRect((int) (position.x + offsetX - size / 2f),
				(int) (-position.y + offsetY - size / 2), size, size);
	}


	private void drawTrackingInfo(Graphics2D g2d)
	{
		g2d.drawString(String.format("Tracking FPS: %.02f", data.framerate), 10, 30);

		float y = 60;
		for (int i = 0; i < data.nHands; i++)
		{
			LEAP_HAND hand = data.getHands()[i];

			float roll = hand.palm.orientation.getRoll();
			float pitch = hand.palm.orientation.getPitch();
			float yaw = hand.palm.orientation.getYaw();
			float lineHeight = 20;

			g2d.drawString(String.format("Hand %d: %s", i, hand.getType()), 10, y);
			y += lineHeight;
			g2d.drawString(String.format("Roll (x): %.02f", Math.toDegrees(roll)), 10, y);
			y += lineHeight;
			g2d.drawString(String.format("Pitch (z): %.02f", Math.toDegrees(pitch)), 10, y);
			y += lineHeight;
			g2d.drawString(String.format("Yaw (y): %.02f", Math.toDegrees(yaw)), 10, y);
			y += lineHeight * 2;
		}
	}
}
