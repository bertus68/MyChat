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

public class MainActivity extends Activity 
{
	public static boolean verbose = false;
	private TextHandler handler;
	private TextView text;
	private EditText edit;
	private BroadcastTask task;

	private Menu menu;

	private SharedPreferences preferences;
	
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
		
		preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext()); 
		preferences.registerOnSharedPreferenceChangeListener(new SharedPreferences.OnSharedPreferenceChangeListener() {
				@Override
				public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
					updatePreference(preferences, key);
				}
			});
		
		
		text = findViewById(R.id.text);
		handler = new TextHandler(text);
		
		final String host = "192.168.1.255";
		final int port = 10000;
		
		edit = findViewById(R.id.edit);
		edit.addTextChangedListener(new TextWatcher() {

				@Override
				public void beforeTextChanged(CharSequence p1, int p2, int p3, int p4)
				{
					
				}

				String tosend="";
				@Override
				public void onTextChanged(CharSequence p1, int p2, int p3, int p4)
				{
					String s = p1.toString().substring(p2);
					switch(s) {
						case "\n":
							if(task!=null) {
								task.send(host, port, tosend);
							}
							tosend="";
							edit.setText("");
							break;
						default:
							tosend=s;
							break;
					}
					
				}

				@Override
				public void afterTextChanged(Editable editable)
				{
					
				}

			});
			
		print("MyChat 0.1.1\n");
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
					startActivity(new Intent(this, MyPreferencesActivity.class));
					break;
				default:
					return super.onOptionsItemSelected(item);
			}
		} catch(Exception e) {
			print(e);
		}
		return true;
	}

	public static class MyPreferencesActivity extends PreferenceActivity {

		@Override
		protected void onCreate(Bundle savedInstanceState) { 
			super.onCreate(savedInstanceState); 
			getFragmentManager().beginTransaction().replace(android.R.id.content, new MyPreferenceFragment()).commit(); 
		} 

		public static class MyPreferenceFragment extends PreferenceFragment { 
			@Override 
			public void onCreate(final Bundle savedInstanceState) { 
				super.onCreate(savedInstanceState); 
				addPreferencesFromResource(R.xml.preferences); 
			} 
		} 
	}
	
	private void updatePreference(SharedPreferences preferences, String key) {
		try {
			switch(key) {
				case "addr":
					print("%s = %s\n",key,preferences.getString(key, ""));
					break;
				case "port":
					print("%s = %s\n",key,preferences.getString(key, ""));
					break;
				default:
					print("unexpected preference %s\n",key);
					break;
			}
		} catch (Exception e) {
			print(e);
		}
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
				this.addr = InetAddress.getByName("255.255.255.255");
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
					print("queued\n");
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
				DatagramSocket socket = new DatagramSocket(10001);
				socket.setReuseAddress(true);
				socket.setBroadcast(true);
				socket.send(packet);
				sent(packet.getAddress().getHostAddress(), packet.getPort(), new String(packet.getData()).trim());
				Thread.sleep(1000);
				socket.close();
			} catch (Exception e) {
				print(TAG1+".send(2) ", e);
			}
		}
		
		public void send(final String addr, final int port, final String message) 
		{
			try {
				byte[] b = message.getBytes();
				DatagramPacket packet = new DatagramPacket(b, b.length, InetAddress.getByName("255.255.255.255"), port);
				consumer.add(packet);
			} catch (Exception e) {
				print(TAG1+".send(1) ", e);
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
				String message = new String(packet.getData()).trim();
				received(addr, port, message);
			} catch (Exception e) {
				print(TAG1+".receive() ", e);
			}
		}
		
		private void received(String addr, int port, String message)
		{
			print("received %s:%d > %s\n", addr, port, message);
		}
		
		private void sent(String addr, int port, String message)
		{
			print("sent %s:%d < %s\n", addr, port, message);
		}
		
	}
	
	public static class TextHandler extends Handler {

		private TextView view;

		public TextHandler(TextView view) {
			super(Looper.getMainLooper());
			this.view = view;
		}

		@Override
		public void handleMessage(Message message) {
			switch(message.what) {
				case 0:
					if(message.obj instanceof String) {
						String[] args = ((String)message.obj).split(" ");
						switch (args[0]) {
							case "clear":
								view.setText("");
								break;
							default:
								break;
						}
					}
					break;
				case 1:
					if(message.obj instanceof String) {
						view.append((String)message.obj);
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
