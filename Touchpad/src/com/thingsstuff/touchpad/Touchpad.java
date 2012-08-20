/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thingsstuff.touchpad;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.thingsstuff.touchpad.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ToggleButton;

public class Touchpad extends Activity {
	static final private String LOG_TAG = "Touchpad";
	
	static final private int CANCEL_ID = Menu.FIRST;

	// Options menu.
	static final private int CONNECT_ID = CANCEL_ID + 1;
	static final private int DISCONNECT_ID = CONNECT_ID + 1;
	static final private int ADD_FAVORITE_ID = DISCONNECT_ID + 1;
	static final private int PREFERENCES_ID = ADD_FAVORITE_ID + 1;
	static final private int EXIT_ID = PREFERENCES_ID + 1;

	// Context (server) menu.
	static final private int FAVORITES_ID = CANCEL_ID + 1;
	static final private int FIND_SERVERS_ID = FAVORITES_ID + 1;
	static final private int SERVER_CUSTOM_ID = FIND_SERVERS_ID + 1;
	static final private int SERVER_FOUND_ID = Menu.FIRST;
	static final private int SERVER_FAVORITE_ID = Menu.FIRST + 1;
	
	static final protected int KeepAlive = 2000;
	static final private int DefaultPort = 2999;
	static final private int MaxServers = 9;

	// Current preferences.
	protected short Port;
	protected float Sensitivity;
	protected int MultitouchMode;
	protected int Timeout;
	protected boolean EnableScrollBar;
	protected int ScrollBarWidth;
	protected boolean EnableSystem;

	// State.
	protected Handler timer = new Handler();
	protected Socket server = null;
	protected ImageView touchpad;
	protected View mousebuttons;
	protected View keyboard, modifiers;
	protected View media, browser;
	protected ToggleButton[] button = { null, null };
	protected ToggleButton key_shift, key_ctrl, key_alt;
	
	public Touchpad() {
	}

	// Activity lifecycle.
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Inflate our UI from its XML layout description.
		setContentView(R.layout.touchpad);

		// Set touchpad events.
		touchpad = (ImageView) findViewById(R.id.touchpad);
		touchpad.setOnTouchListener(mTouchListener);
		
		// Get button containers.
		LinearLayout buttons = (LinearLayout) findViewById(R.id.buttons);

		// Set keyboard events.
		keyboard = (View) buttons.findViewById(R.id.keyboard);
		keyboard.setOnClickListener(mKeyboardListener);
		keyboard.setOnKeyListener(mKeyListener);

		// Keyboard modifiers.
		modifiers = (View) buttons.findViewById(R.id.modifiers);
		
		key_shift = (ToggleButton) modifiers.findViewById(R.id.key_shift);
		key_shift.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton button, boolean checked) {
				if(checked)	sendKeyDown((short)KeyEvent.KEYCODE_SHIFT_LEFT, (short) 0);
				else		sendKeyUp((short)KeyEvent.KEYCODE_SHIFT_LEFT, (short) 0);
			}
		});
		
		key_ctrl = (ToggleButton) modifiers.findViewById(R.id.key_ctrl);
		key_ctrl.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton button, boolean checked) {
				// CTRL key is not in this SDK version...
				if(checked)	sendKeyDown((short)113, (short) 0);
				else		sendKeyUp((short)113, (short) 0);
			}
		});

		key_alt = (ToggleButton) modifiers.findViewById(R.id.key_alt);
		key_alt.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton button, boolean checked) {
				if(checked)	sendKeyDown((short)KeyEvent.KEYCODE_ALT_LEFT, (short) 0);
				else		sendKeyUp((short)KeyEvent.KEYCODE_ALT_LEFT, (short) 0);
			}
		});
		
				
		// Set mouse button events.
		mousebuttons = (LinearLayout) findViewById(R.id.mousebuttons);
		
		button[0] = (ToggleButton) mousebuttons.findViewById(R.id.button0);
		button[0].setOnCheckedChangeListener(mButton0ToggleListener);
		button[0].setOnLongClickListener(mButton0ClickListener);

		button[1] = (ToggleButton) mousebuttons.findViewById(R.id.button1);
		button[1].setOnCheckedChangeListener(mButton1ToggleListener);
		button[1].setOnLongClickListener(mButton1ClickListener);


		// Set media button events.
		media = (LinearLayout) findViewById(R.id.media);

		View prevtrack = media.findViewById(R.id.prevtrack);
		prevtrack.setOnClickListener(new OnClickListener() {
			public void onClick(View v) { sendKeyPress((short) KeyEvent.KEYCODE_MEDIA_PREVIOUS, (short) 0); }
		});
		View playpause = media.findViewById(R.id.playpause);
		playpause.setOnClickListener(new OnClickListener() {
			public void onClick(View v) { sendKeyPress((short) KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, (short) 0); }
		});
		View nexttrack = media.findViewById(R.id.nexttrack);
		nexttrack.setOnClickListener(new OnClickListener() {
			public void onClick(View v) { sendKeyPress((short) KeyEvent.KEYCODE_MEDIA_NEXT, (short) 0); }
		});
		View stop = media.findViewById(R.id.stop);
		stop.setOnClickListener(new OnClickListener() {
			public void onClick(View v) { sendKeyPress((short) KeyEvent.KEYCODE_MEDIA_STOP, (short) 0); }
		});
		View volumemute = media.findViewById(R.id.volumemute);
		volumemute.setOnClickListener(new OnClickListener() {
			public void onClick(View v) { sendKeyPress((short) KeyEvent.KEYCODE_MUTE, (short) 0); }
		});
		View volumedown = media.findViewById(R.id.volumedown);
		volumedown.setOnClickListener(new OnClickListener() {
			public void onClick(View arg0) {
				for(int i = 0; i < 3; ++i)
					sendKeyPress((short) KeyEvent.KEYCODE_VOLUME_DOWN, (short) 0);
			}
		});
		volumedown.setOnLongClickListener(new OnLongClickListener() {
			public boolean onLongClick(View v) { 
				sendKeyPress((short) KeyEvent.KEYCODE_VOLUME_DOWN, (short) 0); 
				return true; 
			}
		});
		View volumeup = media.findViewById(R.id.volumeup);
		volumeup.setOnClickListener(new OnClickListener() {
			public void onClick(View arg0) {
				for(int i = 0; i < 3; ++i)
					sendKeyPress((short) KeyEvent.KEYCODE_VOLUME_UP, (short) 0);
			}
		});
		volumeup.setOnLongClickListener(new OnLongClickListener() {
			public boolean onLongClick(View v) { 
				sendKeyPress((short) KeyEvent.KEYCODE_VOLUME_UP, (short) 0); 
				return true;
			}
		});

		
		// Set browser button events.
		browser = (LinearLayout) findViewById(R.id.browser);
		
		View browsehome = browser.findViewById(R.id.browsehome);
		browsehome.setOnClickListener(new OnClickListener() {
			public void onClick(View v) { sendKeyPress((short) KeyEvent.KEYCODE_HOME, (short) 0); }
		});
		View browseforward = browser.findViewById(R.id.browseforward);
		browseforward.setOnClickListener(new OnClickListener() {
			public void onClick(View v) { sendKeyPress((short) 125, (short) 0); }
		});
		View browseback = browser.findViewById(R.id.browseback);
		browseback.setOnClickListener(new OnClickListener() {
			public void onClick(View v) { sendKeyPress((short) KeyEvent.KEYCODE_BACK, (short) 0); }
		});
		
		// Set up preferences.
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

		// If there is no server to reconnect, set the background to bad.
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		String to = preferences.getString("Server", null);
		if (to == null)
			touchpad.setImageResource(R.drawable.background_bad);
	}
	@Override
	protected void onResume() {
		super.onResume();

		// Restore settings.
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

		try { Port = (short) Integer.parseInt(preferences.getString("Port", Integer.toString(DefaultPort))); } catch(NumberFormatException ex) { Port = DefaultPort; }
		Sensitivity = (float) preferences.getInt("Sensitivity", 50) / 25.0f + 0.1f;
		try { MultitouchMode = Integer.parseInt(preferences.getString("MultitouchMode", "0")); } catch(NumberFormatException ex) { MultitouchMode = 0; }
		Timeout = preferences.getInt("Timeout", 500) + 1;
		EnableScrollBar = preferences.getBoolean("EnableScrollBar", preferences.getBoolean("EnableScroll", true));
		ScrollBarWidth = preferences.getInt("ScrollBarWidth", 20);
		EnableSystem = preferences.getBoolean("EnableSystem", true);

		boolean EnableMouseButtons = preferences.getBoolean("EnableMouseButtons", false);
		boolean EnableModifiers = preferences.getBoolean("EnableModifiers", false);
		int Toolbar = 0;
		try { Toolbar = Integer.parseInt(preferences.getString("Toolbar", "0")); } catch(NumberFormatException ex) { }
		
		// Show/hide the mouse buttons.
		if(EnableMouseButtons) mousebuttons.setVisibility(View.VISIBLE);
		else mousebuttons.setVisibility(View.GONE);
				
		// Show/hide the modifier keys.
		if(EnableModifiers) modifiers.setVisibility(View.VISIBLE);
		else modifiers.setVisibility(View.GONE);
		
		// Show/hide media toolbar.
		if(Toolbar == 1) media.setVisibility(View.VISIBLE);
		else media.setVisibility(View.GONE);

		// Show/hide browser toolbar.
		if(Toolbar == 2) browser.setVisibility(View.VISIBLE);
		else browser.setVisibility(View.GONE);
		
		timer.postDelayed(mKeepAliveListener, KeepAlive);
	}
	@Override
	protected void onPause() {
		timer.removeCallbacks(mKeepAliveListener);
		disconnect(true);
		super.onPause();
	}
		
	// Mouse actions.
	protected class Action {
		protected float downX, downY;
		protected float oldX, oldY;
		protected int pointerId;
		protected long downTime;
		protected boolean moving;
		
		public boolean isClick(MotionEvent e) {
			ViewConfiguration vc = ViewConfiguration.get(touchpad.getContext());

			int index = e.findPointerIndex(pointerId);
			return	Math.abs(e.getX(index) - downX) < vc.getScaledTouchSlop() && 
					Math.abs(e.getY(index) - downY) < vc.getScaledTouchSlop() && 
					e.getEventTime() - downTime < vc.getTapTimeout();
		}
		
		public boolean onDown(MotionEvent e) {
			pointerId = e.getPointerId(0);
			int index = e.findPointerIndex(pointerId);
			oldX = downX = e.getX(index);
			oldY = downY = e.getY(index);
			downTime = e.getEventTime();
			moving = false;
			return true;
		}
		public boolean onUp(MotionEvent e) { 
			if (isClick(e))
				onClick();
			return true;
		}
		
		public boolean acceptMove(MotionEvent e) {
			return true;
		}
		
		public boolean onMove(MotionEvent e) { 
			if(!acceptMove(e))
				return false;
				
			int index = e.findPointerIndex(pointerId);

			float X = e.getX(index);
			float Y = e.getY(index);
			if(moving)
				onMoveDelta((X - oldX) * Sensitivity, (Y - oldY) * Sensitivity);
			else
				moving = true;
			oldX = X;
			oldY = Y;
			return true;
		}
		public boolean cancel(MotionEvent e) {
			return false;
		}
		
		public void onMoveDelta(float dx, float dy) { }
		public void onClick() { }
	};
	protected class MoveAction extends Action {
		public void onMoveDelta(float dx, float dy) { sendMove(dx, dy); }
		public void onClick() {
			if (button[0].isChecked()) 
				button[0].toggle();
			sendClick(0);
		}
		
		public boolean cancel(MotionEvent e) {
			return true;
		}
	};
	protected class ScrollAction extends Action {
		protected long time;

		public boolean onDown(MotionEvent e) {
			time = e.getEventTime();
			return super.onDown(e);
		}
		
		public boolean acceptMove(MotionEvent e) {
			if(e.getEventTime() + 200 < time)
				return false;
			time = e.getEventTime();
			return true;
		}
		public void onMoveDelta(float dx, float dy) { sendScroll(-2.0f * dy); }
	};
	protected class ScrollAction2 extends ScrollAction {
		public void onMoveDelta(float dx, float dy) { sendScroll2(dx, -2.0f * dy); }
		public void onClick() {
			if (button[1].isChecked()) 
				button[1].toggle();
			sendClick(1);
		}
	};
	protected class DragAction extends Action {
		protected boolean drag = false;
		
		public boolean onMove(MotionEvent e) {
			if (!drag && !isClick(e)) {
				sendDown(0);
				drag = true;
			}	
			return super.onMove(e);
		}
		public boolean onUp(MotionEvent e) { 
			if(drag) 
				sendUp(0); 
			return super.onUp(e); 
		}

		public void onMoveDelta(float dx, float dy) { sendMove(dx, dy); }
		public void onClick() { sendClick(1); }
	}
	
	// Mouse listeners.
	OnTouchListener mTouchListener = new OnTouchListener() {
		protected Action action = null;

		public boolean onTouch(View v, MotionEvent e) {
			switch(e.getAction() & MotionEvent.ACTION_MASK) {
			case MotionEvent.ACTION_POINTER_DOWN:
				if(action != null)
					if(!action.cancel(e))
						return true;
			case MotionEvent.ACTION_DOWN:
				action = null;
				// If this is a multitouch action, check the multitouch mode.
				if(e.getPointerCount() >= 2) {
					switch(MultitouchMode) {
					case 1: action = new DragAction(); break;
					case 2: action = new ScrollAction2(); break;
					}
				}
				
				// If the action is still null, check if it is a scroll action.
				if(action == null && EnableScrollBar && ((e.getEdgeFlags() & MotionEvent.EDGE_RIGHT) != 0 || e.getX() > v.getWidth() - ScrollBarWidth))
					action = new ScrollAction();
				
				// If the action is still null, it is a plain move action.
				if(action == null)
					action = new MoveAction();

				if(action != null)
					return action.onDown(e);
				return true;
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_POINTER_UP:
				if(action != null)
					action.onUp(e);
				action = null;
				return true;
			case MotionEvent.ACTION_MOVE:
				if(action != null)
					return action.onMove(e);
				return true;
			default:
				return false;
			}
		}
	};
	OnCheckedChangeListener mButton0ToggleListener = new OnCheckedChangeListener() {
		public void onCheckedChanged(CompoundButton cb, boolean on) {
			if (on) sendDown(0);
			else sendUp(0);
		}
	};
	OnLongClickListener mButton0ClickListener = new OnLongClickListener() {
		public boolean onLongClick(View arg0) {
			if (button[0].isChecked()) 
				button[0].toggle();
			sendClick(0);
			sendClick(0);
			return true;
		}
	};
	OnCheckedChangeListener mButton1ToggleListener = new OnCheckedChangeListener() {
		public void onCheckedChanged(CompoundButton cb, boolean on) {
			if (on) sendDown(1);
			else sendUp(1);
		}
	};
	OnLongClickListener mButton1ClickListener = new OnLongClickListener() {
		public boolean onLongClick(View arg0) {
			if (button[1].isChecked()) 
				button[1].toggle();
			sendClick(1);
			return true;
		}
	};
	
	// Keyboard listener.
	OnClickListener mKeyboardListener = new OnClickListener() {
		public void onClick(View v) {
			InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.showSoftInput(keyboard, InputMethodManager.SHOW_FORCED);
		}
	};
	OnKeyListener mKeyListener = new OnKeyListener() {
		public boolean onKey(View v, int keyCode, KeyEvent event) {
			if(event.getAction() == KeyEvent.ACTION_DOWN)
				return onKeyDown(keyCode, event);
			if(event.getAction() == KeyEvent.ACTION_MULTIPLE)
				return onKeyMultiple(keyCode, event.getRepeatCount(), event);
			if(event.getAction() == KeyEvent.ACTION_UP)
				return onKeyUp(keyCode, event);
			return false;
		}
	};
	
	// Timer listener.
	Runnable mKeepAliveListener = new Runnable() {
		public void run() {
			sendNull();
			timer.postDelayed(this, KeepAlive);
		}
	};

	// Keyboard events.
	boolean ignoreKeyEvent(KeyEvent event) {
		if(event.isSystem() && !EnableSystem)
			return true;
		
		// Don't handle menu or home buttons.
		switch (event.getKeyCode()) {
		case KeyEvent.KEYCODE_MENU:
		case KeyEvent.KEYCODE_HOME:
		case KeyEvent.KEYCODE_POWER:
			return true;
		}
		return false;
	}
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if(ignoreKeyEvent(event))
			return super.onKeyDown(keyCode, event);
		
		int c = event.getUnicodeChar();
		if (c == 0 || Character.isISOControl(c) || key_shift.isChecked() || key_ctrl.isChecked() || key_alt.isChecked())
			sendKeyPress((short) event.getKeyCode(), (short) event.getMetaState());
		else
			sendChar((char) c);
		return true;
	}
	@Override
	public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
		if(keyCode == KeyEvent.KEYCODE_UNKNOWN) {
			String s = event.getCharacters();
			for(int i = 0; i < s.length(); ++i)
				sendChar(s.charAt((i)));
		} else {
			for(int i = 0; i < repeatCount; ++i)
				onKeyDown(keyCode, event);
		}
		return true;
	}
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if(ignoreKeyEvent(event))
			return super.onKeyUp(keyCode,  event);
		
		return true;
	}
	
	// Connection management.
	protected boolean isConnected() {
		reconnect();
		return server != null;
	}
	
	protected void reconnect() {
		if(server == null) {
			// Reconnect to default server.
			SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
			String to = preferences.getString("Server", null);
			int password = preferences.getInt("Password", 0);
			if (to != null)
				connect(to, password, true);
		}
	}
	
	protected boolean connect(String to) { return connect(to, 0); }
	protected boolean connect(String to, boolean reconnect) { return connect(to, 0, reconnect); }
	protected boolean connect(final String to, int password) { return connect(to, password, false); }
	protected boolean connect(final String to, int password, boolean reconnect) {
		disconnect(reconnect);
		
		server = new Socket();

		try {
			// Connect to server.
			String[] addr = to.split("\\:");

			int port = Port;
			if (addr.length > 1)
				port = Short.parseShort(addr[addr.length - 1]);

			server.connect(new InetSocketAddress(InetAddress.getByName(addr[0]), port), Timeout);
			server.setSoTimeout(Timeout);
			server.setTcpNoDelay(true);

			// Send connection packet.
			sendConnect(password, reconnect);

			// Get the response.
			byte[] response = new byte[5];
			if (server.getInputStream().read(response) != 5)
				throw new Exception(getString(R.string.error_connect));

			// If not connected...
			if (response[0] != 0x00) {
				disconnect(false);

				// If the reason is other than a bad password, throw error.
				if (response[0] != 0x01 || response[1] != 0x01)
					throw new Exception(getString(R.string.error_connect));

				// If the user already specified a password, throw password
				// error.
				if (reconnect || password != 0)
					throw new Exception(getString(R.string.error_password));


				// Prompt user for password and retry connect.
				AlertDialog.Builder alert = new AlertDialog.Builder(this);

				final EditText pw = new EditText(this);
				pw.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
				pw.setTransformationMethod(new PasswordTransformationMethod());

				alert.setTitle(R.string.password_title);
				alert.setMessage(R.string.password_message);
				alert.setView(pw);

				alert.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int whichButton) {
							connect(to, Hash(pw.getText().toString()), false);
						}
					});

				alert.setNegativeButton(R.string.cancel, null);

				alert.show();
				return isConnected();
			}

			touchpad.setImageResource(R.drawable.background);

			if(!reconnect) {
				// Store this server as the default.
				SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
				editor.putString("Server", to);
				editor.putInt("Password", password);
				editor.commit();
			}

			Log.i(LOG_TAG, "Connected to " + to);
			
			return true;
		} 
		catch (Exception e) {
			Log.e(LOG_TAG, "Failed to connect to " + to, e);
			
			disconnect(false);

			if(!reconnect)
				showErrorDialog(getString(R.string.error_connecting) + " " + to, e);
			
			return false;
		}
	}

	protected void disconnect() { disconnect(false); }
	protected void disconnect(boolean reconnect) {
		// Clear default server.
		if(!reconnect) {
			SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
			editor.remove("Server");
			editor.remove("Password");
			editor.commit();
			
			Log.i(LOG_TAG, "Cleared connection info.");
		}

		sendDisconnect(reconnect);
		try {
			server.close();
		} catch (Exception e) { }
		server = null;
		if(!reconnect)
			touchpad.setImageResource(R.drawable.background_bad);
	}
	
	// Context menu.
	protected void findServers(Menu menu) throws Exception {
		// Broadcast ping to look for servers.
		DatagramSocket beacon = new DatagramSocket(null);
		beacon.setBroadcast(true);
		beacon.setSoTimeout(Timeout);

		InetAddress broadcast = getBroadcastAddress();

		byte[] buffer = new byte[] { 0x02, 0x00, 0x00, 0x00, 0x00 };
		beacon.send(new DatagramPacket(buffer, 5, broadcast, Port));
		if(Port != DefaultPort)
			beacon.send(new DatagramPacket(buffer, 5, broadcast, DefaultPort));

		try {
			// Add each ack to the menu.
			for (int i = 0; i < MaxServers; ++i) {
				byte[] port = new byte[5];
				DatagramPacket ack = new DatagramPacket(port, 5);
				beacon.receive(ack);

				ByteBuffer parser = ByteBuffer.wrap(port);

				if (parser.get() == 0x03)
				{
					String addr = ack.getAddress().toString().substring(1) + ":" + parser.getShort();
					menu.add(SERVER_FOUND_ID, 0, 0, addr).setShortcut((char) (i + '1'), (char)(i + 'a'));
				}
			}
		} catch (SocketTimeoutException e) { }
	}
	
	protected int findFavorite(String server) {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		for(int i = 0; i < MaxServers; ++i) {
			String addr = preferences.getString("Favorite" + i, null);
			if( (addr == null && server == null) || 
				(addr != null && server != null && addr.equals(server)))
				return i;
		}
		return -1;		
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		menu.clear();
		menu.setHeaderTitle(R.string.servers);

		//menu.addSubMenu(0, FAVORITES_ID, 0, R.string.favoriteservers).setHeaderTitle(R.string.servers);
		menu.addSubMenu(0, FIND_SERVERS_ID, 1, R.string.findservers).setHeaderTitle(R.string.servers);
		menu.add(0, SERVER_CUSTOM_ID, 2, R.string.customserver).setShortcut('1', 'c');
		menu.add(0, CANCEL_ID, 3, R.string.cancel).setShortcut('2', 'x');
	}
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		if (item.getGroupId() == SERVER_FOUND_ID) {
			// Connect to menu item server.
			connect(item.getTitle().toString(), 0);
			return true;
		} else if (item.getGroupId() == SERVER_FAVORITE_ID) {
			// Connect to menu item server.
			int slot = item.getItemId();
			
			SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
			int password = preferences.getInt("Password" + slot, 0);
			if(!connect(item.getTitle().toString(), password)) {
				SharedPreferences.Editor editor = preferences.edit();
				editor.remove("Favorite" + slot);
				editor.remove("Password" + slot);
				editor.commit();
			}
			return true;
		} else if (item.getItemId() == SERVER_CUSTOM_ID) {
			// Prompt user for server to connect to.
			AlertDialog.Builder alert = new AlertDialog.Builder(this);

			final EditText to = new EditText(this);
			to.setInputType(InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT);

			alert.setTitle(R.string.customserver_title);
			alert.setMessage(R.string.customserver_message);
			alert.setView(to);

			alert.setPositiveButton(R.string.ok_connect,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int whichButton) {
							connect(to.getText().toString());
						}
					});

			alert.setNegativeButton(R.string.cancel,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int whichButton) { }
					});

			alert.show();

			return true;
		} else if (item.getItemId() == FIND_SERVERS_ID) {
			try {
				SubMenu servers = item.getSubMenu();
				findServers(servers);
				if(servers.size() == 0)
					showErrorDialog(getString(R.string.error_noservers));
					
			} catch(Exception e) {
				showErrorDialog(getString(R.string.error), e);				
			}
			return true;
		} else if (item.getItemId() == FAVORITES_ID) {
			SubMenu servers = item.getSubMenu();
			
			SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
			for(int i = 0; i < MaxServers; ++i) {
				String server = preferences.getString("Favorite" + i, null);
				int password = preferences.getInt("Password" + i, 0);
				if (server != null)
					servers.add(SERVER_FAVORITE_ID, password, 0, server).setShortcut((char) (i + '1'), (char) (i + 'a'));			
			}
			
			if(servers.size() == 0)
				showErrorDialog(getString(R.string.error_nofavorites));
			return true;
		} else {
			return super.onContextItemSelected(item);
		}
	}

	// Options menu.
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();		
		//if(!isConnected())
			menu.add(0, CONNECT_ID, 0, R.string.connect).setShortcut('0', 'c');
		//else
			menu.add(0, DISCONNECT_ID, 1, R.string.disconnect).setShortcut('0', 'd');
		//menu.add(0, ADD_FAVORITE_ID, 2, R.string.addfavorite).setShortcut('1', 'f').setEnabled(isConnected());
		menu.add(0, PREFERENCES_ID, 3, R.string.preferences).setShortcut('2', 'p');
		menu.add(0, EXIT_ID, 4, R.string.exit).setShortcut('3', 'q');
		return true;
	}
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case CONNECT_ID:
			registerForContextMenu(touchpad);
			openContextMenu(touchpad);
			unregisterForContextMenu(touchpad);
			return true;
		case DISCONNECT_ID:
			disconnect();
			return true;
		case ADD_FAVORITE_ID:
			SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
			String server = preferences.getString("Server", null);
			int password = preferences.getInt("Password", 0);
			
			if(server != null) {
				int slot = findFavorite(server);
				if(slot == -1)
					slot = findFavorite(null);
				if(slot != -1) {
					SharedPreferences.Editor editor = preferences.edit();
					editor.putString("Favorite" + slot, server);
					editor.putInt("Password" + slot, password);
					editor.commit();
				}
			}
			return true;
		case PREFERENCES_ID:
			startActivity(new Intent(this, Preferences.class));
			return true;
		case EXIT_ID:
			super.onBackPressed();
			return true;
		}

		return super.onOptionsItemSelected(item);
	}
	
	// Send packet.
	void sendPacket(byte[] buffer) {
		sendPacket(buffer, true, true);
	}
	void sendPacket(byte[] buffer, boolean allowConnect, boolean allowDisconnect) {
		try {
			if(server == null && allowConnect) 
				reconnect();
			if(server != null)
				server.getOutputStream().write(buffer);
		} catch (Exception e) {
			Log.e(LOG_TAG, "Failed to send packet " + buffer[0], e);
			if(allowDisconnect)
				disconnect();
		}
	}
	
	// Mouse packets.
	protected void sendMove(float dx, float dy) {
		byte[] buffer = new byte[5];
		ByteBuffer writer = ByteBuffer.wrap(buffer);
		writer.order(ByteOrder.BIG_ENDIAN);

		// Move packet.
		writer.put((byte) 0x11);
		writer.put(floatToByte(dx));
		writer.put(floatToByte(dy));

		sendPacket(buffer);
	}
	protected void sendDown(int button) {
		byte[] buffer = new byte[5];
		ByteBuffer writer = ByteBuffer.wrap(buffer);
		writer.order(ByteOrder.BIG_ENDIAN);

		// Down packet.
		writer.put((byte) 0x12);
		writer.put((byte) button);

		sendPacket(buffer);
	}
	protected void sendUp(int button) {
		byte[] buffer = new byte[5];
		ByteBuffer writer = ByteBuffer.wrap(buffer);
		writer.order(ByteOrder.BIG_ENDIAN);

		// Down packet.
		writer.put((byte) 0x13);
		writer.put((byte) button);

		sendPacket(buffer);
	}
	protected void sendClick(int button) {
		sendDown(button);
		sendUp(button);
	}
	protected void sendScroll(float d) {
		byte[] buffer = new byte[5];
		ByteBuffer writer = ByteBuffer.wrap(buffer);
		writer.order(ByteOrder.BIG_ENDIAN);

		// Move packet.
		writer.put((byte) 0x16);
		writer.put(floatToByte(d));

		sendPacket(buffer);
	}
	protected void sendScroll2(float dx, float dy) {
		byte[] buffer = new byte[5];
		ByteBuffer writer = ByteBuffer.wrap(buffer);
		writer.order(ByteOrder.BIG_ENDIAN);

		// Move packet.
		writer.put((byte) 0x17);
		writer.put(floatToByte(dx));
		writer.put(floatToByte(dy));

		sendPacket(buffer);
	}

	// Keyboard packets.
	protected void sendKey(byte control, short code, short flags) {
		byte[] buffer = new byte[5];
		ByteBuffer writer = ByteBuffer.wrap(buffer);
		writer.order(ByteOrder.BIG_ENDIAN);

		// Move packet.
		writer.put(control);
		writer.putShort(code);
		writer.putShort(flags);

		sendPacket(buffer);
	}
	protected void sendKeyPress(short code, short flags) { sendKey((byte) 0x21, code, flags); }
	protected void sendKeyDown(short code, short flags) { sendKey((byte) 0x22, code, flags); }
	protected void sendKeyUp(short code, short flags) { sendKey((byte) 0x23, code, flags); }
	protected void sendChar(char code) {
		byte[] buffer = new byte[5];
		ByteBuffer writer = ByteBuffer.wrap(buffer);
		writer.order(ByteOrder.BIG_ENDIAN);

		// Move packet.
		writer.put((byte) 0x20);
		writer.putChar(code);

		sendPacket(buffer);
	}

	// Connection packets.
	protected void sendConnect(int password, boolean silent) {
		byte[] buffer = new byte[5];
		ByteBuffer writer = ByteBuffer.wrap(buffer);
		writer.order(ByteOrder.BIG_ENDIAN);

		// Move packet.
		if(silent)
			writer.put((byte) 0x05);
		else
			writer.put((byte) 0x00);
		writer.putInt(password);

		sendPacket(buffer, false, true);
	}
	protected void sendDisconnect(boolean silent) {
		byte[] buffer = new byte[5];
		ByteBuffer writer = ByteBuffer.wrap(buffer);
		writer.order(ByteOrder.BIG_ENDIAN);

		// Move packet.
		if(silent)
			writer.put((byte) 0x04);
		else
			writer.put((byte) 0x01);

		sendPacket(buffer, false, false);
	}

	// Keep alive packet.
	protected int nullCount = 0;
	protected void sendNull() {
		byte[] buffer = new byte[5];
		ByteBuffer writer = ByteBuffer.wrap(buffer);
		writer.order(ByteOrder.BIG_ENDIAN);
		
		writer.put((byte) 0xFF);
		writer.putInt(nullCount);
		nullCount++;

		sendPacket(buffer);
	}
	
	// Show error dialog
	protected void showErrorDialog(String message) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.error);
		builder.setMessage(message);

		builder.setPositiveButton(R.string.ok, null);
		
		AlertDialog dlg = builder.create();
		dlg.show();
	}
	
	protected void showErrorDialog(String message, Throwable e) {
		if (e.getMessage() != null)
			showErrorDialog(message + ": " + e.getMessage());
		else
			showErrorDialog(message + ": " + e.toString());
	}
	
	// Get broadcast address for LAN.
	protected InetAddress getBroadcastAddress() throws IOException {
		WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		DhcpInfo dhcp = wifi.getDhcpInfo();
		// handle null somehow

		int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
		byte[] quads = new byte[4];
		for (int k = 0; k < 4; k++)
			quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
		return InetAddress.getByAddress(quads);
	}
	
	// Clamp float to byte.
	protected static byte floatToByte(float x) {
		if(x < -128.0f)
			x = -128.0f;
		if(x > 127.0f)
			x = 127.0f;
		return (byte) x;
	}

	// Password hash. 
	protected static int Hash(String s) {
		int hash = 5381;

		for (int i = 0; i < s.length(); ++i)
			hash = hash * 33 + s.charAt(i);

		return hash;
	}
}
