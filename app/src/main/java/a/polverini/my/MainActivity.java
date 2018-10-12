package a.polverini.my;

import android.app.*;
import android.os.*;
import android.widget.*;
import java.io.*;
import android.text.*;
import android.util.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import android.view.*;
import android.content.*;
import android.preference.*;
import android.text.method.*;

public class MainActivity extends Activity 
{
	public static boolean verbose = false;
	private TextHandler handler;
	private TextView text;
	private EditText edit;
	private BroadcastTask task;
	private Menu menu;

	private Map<String, String> alias = new HashMap<>();
	
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
		
		text = findViewById(R.id.text);
		text.setMovementMethod(new ScrollingMovementMethod());
		handler = new TextHandler(text);
		
		alias.put("192.168.1.3", "Alberto");
		alias.put("192.168.1.4", "Sofia");
		
		final String host = "192.168.1.255"; 
		final int port = 10000;
		
		edit = findViewById(R.id.edit);
		edit.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
		edit.setMovementMethod(new ScrollingMovementMethod());
		edit.setOnEditorActionListener(
			new EditText.OnEditorActionListener() {
				@Override
				public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
					String s = view.getText().toString();
					if(task!=null) {
						task.queue(host, port, s);
					}
					view.setText("");
					return true;          
				}
			}
		);
		
		print("MyChat 0.1.3\n");
		
		task = new BroadcastTask(host, port);
		task.execute();
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		this.menu = menu;
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		try {
			switch (item.getItemId()) {
				case R.id.pref:
					break;
				default:
					return super.onOptionsItemSelected(item);
			}
		} catch(Exception e) {
			print(e);
		}
		return true;
	}
	
	private class BroadcastTask extends AsyncTask {
		private final String TAG1 = "BroadcastTask";
		private InetAddress addr;
		private int port;
		private byte[] buffer;
		private DatagramPacket packet;
		private DatagramSocket socket;
		private Boolean running;
		private Consumer consumer;

		public BroadcastTask(final String host, final int port) {
			try {
				consumer = new Consumer();
				consumer.start();
				this.addr = InetAddress.getByName(host);
				this.port = port;
				this.buffer = new byte[1024];
				this.packet = new DatagramPacket(buffer, buffer.length);
				this.packet.setLength(buffer.length);
			} catch (Exception e) {
				print(e);
			} 
		}
		
		public class Consumer extends Thread {

			private final String TAG2 = "Consumer";
			
			private final BlockingQueue<DatagramPacket> queue;

			public Consumer() { 
				this.queue = new ArrayBlockingQueue<>(100); 
			}

			@Override
			public void run() {
				try {
					while (true) { 
						consume(queue.take()); 
					}
				} catch (InterruptedException e) { 
					print(TAG2+".run() ", e);
				}
			}

			void consume(DatagramPacket packet) {
				if(running) {
					send(packet);
				}
			}
			
			public void add(DatagramPacket packet) {
				try {
					queue.put(packet);
				} catch (InterruptedException e) {
					print(TAG2+".add() ", e);
				}
			}
			
		}
		
		@Override
		protected Object doInBackground(Object[] p1)
		{
			try {
				socket = new DatagramSocket(port, addr);
				socket.setReuseAddress(true);
				socket.setBroadcast(true);
				running = true;
				while(running) {
					while (running) {
						receive();
					}
				}
			}
			catch (Exception e) {
				print(TAG1+".doInBackground(...) ", e);
			} finally {
				running = false;
				socket.close();
			}

			return null;
		}
		
		public void send(DatagramPacket packet) 
		{
			try {
				DatagramSocket socket = new DatagramSocket();
				socket.setReuseAddress(true);
				socket.setBroadcast(true);
				socket.send(packet);
				sent(packet.getAddress().getHostAddress(), packet.getPort(), new String(packet.getData()).trim());
				socket.close();
			} catch (Exception e) {
				print(TAG1+".send() ", e);
			}
		}
		
		public void queue(final String addr, final int port, final String message) 
		{
			try {
				byte[] b = message.getBytes();
				DatagramPacket packet = new DatagramPacket(b, b.length, InetAddress.getByName(addr), port);
				consumer.add(packet);
			} catch (Exception e) {
				print(TAG1+".queue() ", e);
			}
		}
		
		public void receive() 
		{
			if (socket == null || socket.isClosed()) {
				return;
			}
			try {
				
				this.packet.setLength(buffer.length);
				this.socket.receive(packet);
				String addr = packet.getAddress().getHostAddress();
				int port = packet.getPort();
				String message = new String(packet.getData(), 0, packet.getLength());
				//String message = new String(packet.getData()).trim();
				received(addr, port, message);
			} catch (Exception e) {
				print(TAG1+".receive() ", e);
			}
		}
		
		private void received(String addr, int port, String message)
		{
			if(verbose) {
				print("received %s:%d > %s\n", addr, port, message);
			} else {
				switch(message) {
					case "clear":
						command(message);
						break;
					default:
						break;
					
				}
				if(alias.containsKey(addr)) {
					print("%s: ",alias.get(addr));
				}
				print("%s\n", message);
			}
		}
		
		private void sent(String addr, int port, String message)
		{
			if(verbose) {
				print("sent %s:%d < %s\n", addr, port, message);
			}
		}
		
	}
	
	public static class TextHandler extends Handler {

		private TextView text;

		public TextHandler(TextView text) {
			super(Looper.getMainLooper());
			this.text = text;
		}

		@Override
		public void handleMessage(Message message) {
			switch(message.what) {
				case 0:
					if(message.obj instanceof String) {
						String[] args = ((String)message.obj).split(" ");
						switch (args[0]) {
							case "clear":
								text.setText("");
								break;
							default:
								break;
						}
					}
					break;
				case 1:
					if(message.obj instanceof String) {
						text.append((String)message.obj);
						Layout layout = text.getLayout();
						if(layout!=null)  {
							int top = layout.getLineTop(text.getLineCount());
							int bottom = text.getBottom();
							if(top>bottom) {
								text.scrollTo(0, top-bottom);
							}
						}
					}
					break;
				default:
					break;
			}
		}
	}
	
	public void command(String s) {
		handler.obtainMessage(0, s).sendToTarget();
	}

	public void print(String fmt, Object... args) {
		String s = String.format(fmt, args);
		handler.obtainMessage(1, s).sendToTarget();
	}

	public void print(String s, Exception e) {
		print("%s %s %s\n", s, e.getClass().getSimpleName(), e.getMessage());
		if(verbose) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			pw.close();
			print("%s\n", sw.getBuffer().toString());
		}
	}
	
	public void print(Exception e) {
		print("", e);
	}
	
}
