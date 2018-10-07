package a.polverini.my;

import android.app.*;
import android.os.*;
import android.widget.*;
import java.io.*;
import android.text.*;
import android.util.*;
import java.net.*;

public class MainActivity extends Activity 
{
	public static boolean verbose = false;
	private TextHandler handler;
	private TextView text;
	private EditText edit;
	private BroadcastTask task;
	
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
		
		text = findViewById(R.id.text);
		handler = new TextHandler(text);
		
		final String host = "127.0.0.255";
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
			
		print("MyChat 0.1.0\n");
		task = new BroadcastTask(host, port);
		task.execute();
    }
	
	private class BroadcastTask extends AsyncTask {
		private InetAddress addr;
		private int port;
		private byte[] buffer;
		private DatagramPacket packet;
		private DatagramSocket socket;
		private Boolean running;
		
		public BroadcastTask(final String host, final int port) {
			try {
				this.addr = InetAddress.getByName(host);
				this.port = port;
				this.buffer = new byte[1024];
				this.packet = new DatagramPacket(buffer, buffer.length);
				this.packet.setLength(buffer.length);
			} catch (Exception e) {
				print(e);
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
				print(e);
			} finally {
				running = false;
				socket.close();
			}

			return null;
		}
		
		public void send(final String addr, final int port, final String message) 
		{
			try {
				byte[] b = message.getBytes();
				DatagramPacket packet = new DatagramPacket(b, b.length);
				packet.setAddress(InetAddress.getByName(addr));
				packet.setPort(port);
				DatagramSocket socket = new DatagramSocket();
				socket.setBroadcast(true);
				socket.send(packet);
				sent(packet.getAddress().getHostAddress(), packet.getPort(), new String(packet.getData()).trim());
			} catch (Exception e) {
				print(e);
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
				print(e);
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

	public void print(Exception e) {
		print("%s %s\n", e.getClass().getSimpleName(), e.getMessage());
		if(verbose) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			pw.close();
			print("%s\n", sw.getBuffer().toString());
		}
	}
	
}
