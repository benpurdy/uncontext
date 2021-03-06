package uncontext;

import java.lang.reflect.*;

import processing.core.*;
import processing.data.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.handshake.ServerHandshake;
import java.nio.channels.UnresolvedAddressException;

public class Uncontext {

	private final String 	BASE_URL = "uncontext.com";
	private final int 		PORT = 80;
	private final String	DATA_METHOD_NAME = "uncontext";
	
	private WebSocketClient cc;
	private PApplet parent;
	private boolean connected = false;
	private Streams currentStream;
	private Method dataMethod;
	
	/**
	 * Constructor, this should be used in the setup() method of the sketch. 
	 * The data stream will connect automatically.
	 * <p>
	 * Typical usage:
	 * <p>
	 * unctx = new Uncontext(this, "literature");
	 *
	 * @param parent The parent applet.
	 * @param streamName A valid uncontext stream name.
	 */
	public Uncontext(PApplet parent, String streamName) {
		this.parent = parent;
		this.parent.registerMethod("dispose", this);

		if(streamName == null){
			parent.println("Uncontext: Error, stream name is null.");
			return;
		}

		boolean streamExists = false;
		for(Streams s : Streams.values()) {
			if( s.getName().equals(streamName.toLowerCase()) ) {
				currentStream = s;
				streamExists = true;
				break;
			}
		}

		if(streamExists){
			this.attachEvents();
			if(this.dataMethod != null) {
				this.startStream();
			}
		} else {
			parent.println("Uncontext: There are no streams named '" + streamName + "', please use one of the following.");
			this.list();
		}
	}

	/**
	* Print the list of available streams in the debug console.
	*/
	public static void list() {
		System.out.println("------------------- Uncontext Data Streams ----------------------");
		for(Streams s : Streams.values()) {
			String nameColumn = String.format("%1$-15s", s.getName() + ":");
			System.out.println(nameColumn + s.getSignatureExample());
		}
		System.out.println("-----------------------------------------------------------------");
	}

	/**
	 * Stop the stream, if it's running.
	 */
	public void stop() {
		if(connected) {
			connected = false;
			cc.close();
		}
	}

	/**
	 * Start the stream, if it isn't running.
	 */
	public void start(){
		if(!connected){
			startStream();
		}
	}

	/**
	 * Check to see if the stream is currently connected.
	 *
	 * @return True if the stream is connected, False if it isn't.
	 */
	public boolean isConnected() {
		return connected;
	}

	/**
	 * Clean things up, stop the stream. This is called automatically when the 
	 * sketch stops.
	 */
	public void dispose() {
		this.stop();
	}

	//////////////////////////////////

	/**
	 * Use reflection to find and validate the data handler method in the 
	 * sketch based on the enum of available streams.
	 */
	private void attachEvents() {
		
		boolean methodFound = false;
		try { 
			dataMethod = parent.getClass().getMethod(DATA_METHOD_NAME, currentStream.getMethodParameters()); 
			methodFound = true;
		} catch (NoSuchMethodException e){
			// ignore missing methods, just means they didn't specify one or 
			// the signatire is wrong. Both cases will be caught later and error 
			// messages will be displayed.
		} catch (Exception e) { 
			System.err.println("Uncontext: Error while iterating through potential data handlers.");
		}

		if(!methodFound) {

			// A data handler method matching one of the streams was not found 
			// so check to see if any method name matches the data handler name.
			// This could indicate that they have simply made a mistake in the 
			// method signature.

			Method[] allMethods = parent.getClass().getMethods();
			boolean methodExists = false;
			for(int i = 0; i < allMethods.length; i++){
				if(DATA_METHOD_NAME.equals(allMethods[i].getName())){
					methodExists = true;
					break;
				}
			}

			if(methodExists) {
				parent.println("Uncontext: The " + DATA_METHOD_NAME + "() method for '" + currentStream.getName() + "' must accept the following parameters:");
				parent.println(currentStream.getSignatureExample());
			} else {
				parent.println("Uncontext: No method named '" + DATA_METHOD_NAME + "' found in the sketch, add a method like this:");
				parent.println(currentStream.getSignatureExample());
			} 

		}
	}

	/**
	 * Start the websocket stream.
	 */
	private void startStream() {
		try{
			cc = new WebSocketClient( new java.net.URI( this.generateURL(currentStream) ), new Draft_17() ) {
				@Override
				public void onMessage( String message ) {
					// If "stop" is called, data may still come in before the 
					// stream shuts down, so check the connected value and 
					// don't handle messages if connected is false.
					if(connected) {
						handleData(message);
					}
				}

				@Override
				public void onOpen( ServerHandshake handshake ) {
					parent.println("Uncontext: Connected to data stream '" + currentStream.getName() + "'");
					connected = true;
				}

				@Override
				public void onClose( int code, String reason, boolean remote ) {
					if(connected) {
						System.err.println( "Uncontext: Connection closed. Code: " + code + ", Reason:" + reason);
					}
				}

				@Override
				public void onError( Exception ex ) {
					if( ex instanceof UnresolvedAddressException) {
						System.err.println("Uncontext: Unable to resolve network address, please check your internet connection.");
					} else {
						System.err.println("Uncontext: An error occured.");
						System.err.println(ex);	
					}
					this.close();
				}
			};
			

		    cc.connect();

		} catch( URISyntaxException ex ){
			// This should never happen, URI generation is not exposed.
		}
	}

	/**
	 * Data handler for JSON messages from the stream.
	 */
	private void handleData(String message){
		
		JSONObject data = null;

		try {
			data = JSONObject.parse(message);
		} catch (Exception e) {
			System.err.println("Uncontext: Error - unable to parse uncontext data for stream '" + currentStream.getName() + "'.");
			System.err.println("Exception: " + e);
		}

		if(data != null) {
			try { 
				dataMethod.invoke( parent, currentStream.getMethodValues(data) );
			}
			catch (IllegalAccessException e) {
			}
			catch (IllegalArgumentException e) {
			}
			catch (InvocationTargetException e) {
			}
		}
	}

	/**
	 * Helper function to generate websocket URLs
	 */
	private String generateURL(Streams stream) {
		return "ws://" + stream.getName() + "." + BASE_URL + ":" + PORT;
	}
}