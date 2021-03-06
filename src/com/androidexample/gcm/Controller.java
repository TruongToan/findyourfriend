package com.androidexample.gcm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.PowerManager;
import android.util.Log;

import com.google.android.gcm.GCMRegistrar;
import com.google.android.gms.maps.model.LatLng;

public class Controller extends Application {

	private final int MAX_ATTEMPTS = 5;
	private final int BACKOFF_MILLI_SECONDS = 2000;
	private final Random random = new Random();

	public List<LatLng> getLocationHistory(final Context context) {
		Log.i(Config.TAG, "get location history");
		String serverUrl = Config.GET_LOCATION_HISTORY_SERVER_URL;
		Map<String, String> params = new HashMap<String, String>();

		String jsonResponse = tryPostWithJsonResult(context, serverUrl, params);

		if (!jsonResponse.equals("")) {
			// parse json response
			List<LatLng> fs = new ArrayList<LatLng>();

			try {
				JSONObject json = new JSONObject(jsonResponse);
				JSONArray ls = json.getJSONArray("items");

				ls.toString().replace("[", "");
				ls.toString().replace("]", "");

				for (int i = 0; i < ls.length(); ++i) {
					JSONObject o = ls.getJSONObject(i);
					fs.add(new LatLng(o.getDouble("latitute"),
							o.getDouble("longtitute")));
				}
			} catch (Exception e) {
				Log.d(Config.TAG, "Parsed json error: " + e.getMessage());
			}
			return fs;
		} else {
			// Send Broadcast to Show message on screen
			displayMessageOnScreen(context, "get location history fail!");
			return null;
		}
	}

	public List<Friend> getFriendsName(final Context context) {

		Log.i(Config.TAG, "get friends name");
		String serverUrl = Config.GET_FRIENDS_SERVER_URL;
		Map<String, String> params = new HashMap<String, String>();

		String jsonResponse = tryPostWithJsonResult(context, serverUrl, params);

		if (!jsonResponse.equals("")) {
			// parse json response
			List<Friend> fs = new ArrayList<Friend>();

			try {
				JSONObject json = new JSONObject(jsonResponse);
				JSONArray ls = json.getJSONArray("items");

				ls.toString().replace("[", "");
				ls.toString().replace("]", "");

				for (int i = 0; i < ls.length(); ++i) {
					JSONObject o = ls.getJSONObject(i);
					fs.add(new Friend(o.getString("name"),
							o.getString("email"), o.getString("gcm_regid")));
				}
			} catch (Exception e) {
				Log.d(Config.TAG, "Parsed json error: " + e.getMessage());
			}
			return fs;

		} else {
			// Send Broadcast to Show message on screen
			displayMessageOnScreen(context, "get friend list fail!");
			return null;
		}
	}

	void sendLocation(final Context context, final double lat,
			final double lng, final String regId) {
		Log.i(Config.TAG, "send location: " + lat + " # " + lng);
		String serverUrl = Config.SEND_LOCATION_SERVER_URL;

		Map<String, String> params = new HashMap<String, String>();
		params.put("latitute", String.valueOf(lat));
		params.put("longtitute", String.valueOf(lng));
		params.put("regId", regId);

		tryPostNoResult(context, serverUrl, params);
	}

	// Send message
	public void sendMessage(final Context context, final String regIdFrom,
			final String regIdTo, String message) {
		Log.i(Config.TAG, "send message from " + regIdFrom + " to " + regIdTo);
		String serverUrl = Config.SEND_MESSAGE_SERVER_URL;

		Map<String, String> params = new HashMap<String, String>();
		params.put("regIdFrom", regIdFrom);
		params.put("regIdTo", regIdTo);
		params.put("message", message);

		tryPostNoResult(context, serverUrl, params);
	}

	// Register this account with the server.
	void register(final Context context, String name, String email,
			final String regId) {

		Log.i(Config.TAG, "registering device (regId = " + regId + ")");

		String serverUrl = Config.YOUR_SERVER_URL;

		Map<String, String> params = new HashMap<String, String>();
		params.put("regId", regId);
		params.put("name", name);
		params.put("email", email);

		long backoff = BACKOFF_MILLI_SECONDS + random.nextInt(1000);

		// Once GCM returns a registration id, we need to register on our server
		// As the server might be down, we will retry it a couple
		// times.
		for (int i = 1; i <= MAX_ATTEMPTS; i++) {

			Log.d(Config.TAG, "Attempt #" + i + " to register");

			try {
				// Send Broadcast to Show message on screen
				displayMessageOnScreen(context, context.getString(
						R.string.server_registering, i, MAX_ATTEMPTS));

				// Post registration values to web server
				post(serverUrl, params);

				GCMRegistrar.setRegisteredOnServer(context, true);

				// Send Broadcast to Show message on screen
				String message = context.getString(R.string.server_registered);
				displayMessageOnScreen(context, message);

				return;
			} catch (IOException e) {

				// Here we are simplifying and retrying on any error; in a real
				// application, it should retry only on unrecoverable errors
				// (like HTTP error code 503).

				Log.e(Config.TAG, "Failed to register on attempt " + i + ":"
						+ e);

				if (i == MAX_ATTEMPTS) {
					break;
				}
				try {

					Log.d(Config.TAG, "Sleeping for " + backoff
							+ " ms before retry");
					Thread.sleep(backoff);

				} catch (InterruptedException e1) {
					// Activity finished before we complete - exit.
					Log.d(Config.TAG,
							"Thread interrupted: abort remaining retries!");
					Thread.currentThread().interrupt();
					return;
				}

				// increase backoff exponentially
				backoff *= 2;
			}
		}

		String message = context.getString(R.string.server_register_error,
				MAX_ATTEMPTS);

		// Send Broadcast to Show message on screen
		displayMessageOnScreen(context, message);
	}

	// Unregister this account/device pair within the server.
	void unregister(final Context context, final String regId) {

		Log.i(Config.TAG, "unregistering device (regId = " + regId + ")");

		String serverUrl = Config.YOUR_SERVER_URL + "/unregister";
		Map<String, String> params = new HashMap<String, String>();
		params.put("regId", regId);

		try {
			post(serverUrl, params);
			GCMRegistrar.setRegisteredOnServer(context, false);
			String message = context.getString(R.string.server_unregistered);
			displayMessageOnScreen(context, message);
		} catch (IOException e) {

			// At this point the device is unregistered from GCM, but still
			// registered in the our server.
			// We could try to unregister again, but it is not necessary:
			// if the server tries to send a message to the device, it will get
			// a "NotRegistered" error message and should unregister the device.

			String message = context.getString(
					R.string.server_unregister_error, e.getMessage());
			displayMessageOnScreen(context, message);
		}
	}

	// Issue a POST request to the server.
	private static void post(String endpoint, Map<String, String> params)
			throws IOException {

		URL url;
		try {

			url = new URL(endpoint);

		} catch (MalformedURLException e) {
			throw new IllegalArgumentException("invalid url: " + endpoint);
		}

		StringBuilder bodyBuilder = new StringBuilder();
		Iterator<Entry<String, String>> iterator = params.entrySet().iterator();

		// constructs the POST body using the parameters
		while (iterator.hasNext()) {
			Entry<String, String> param = iterator.next();
			bodyBuilder.append(param.getKey()).append('=')
					.append(param.getValue());
			if (iterator.hasNext()) {
				bodyBuilder.append('&');
			}
		}

		String body = bodyBuilder.toString();

		Log.v(Config.TAG, "Posting '" + body + "' to " + url);

		byte[] bytes = body.getBytes();

		HttpURLConnection conn = null;
		try {

			Log.e("URL", "> " + url);

			conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setUseCaches(false);
			conn.setFixedLengthStreamingMode(bytes.length);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type",
					"application/x-www-form-urlencoded;charset=UTF-8");
			// post the request
			OutputStream out = conn.getOutputStream();
			out.write(bytes);
			out.close();

			// handle the response
			int status = conn.getResponseCode();

			// If response is not success
			if (status != 200) {

				throw new IOException("Post failed with error code " + status);
			}
		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

	// Issue a POST request to the server.
	private static String post_response(String endpoint,
			Map<String, String> params) throws IOException {

		URL url;
		try {

			url = new URL(endpoint);

		} catch (MalformedURLException e) {
			throw new IllegalArgumentException("invalid url: " + endpoint);
		}

		StringBuilder bodyBuilder = new StringBuilder();
		Iterator<Entry<String, String>> iterator = params.entrySet().iterator();

		// constructs the POST body using the parameters
		while (iterator.hasNext()) {
			Entry<String, String> param = iterator.next();
			bodyBuilder.append(param.getKey()).append('=')
					.append(param.getValue());
			if (iterator.hasNext()) {
				bodyBuilder.append('&');
			}
		}

		String body = bodyBuilder.toString();
		String parsedString = "";

		Log.v(Config.TAG, "Posting '" + body + "' to " + url);

		byte[] bytes = body.getBytes();

		HttpURLConnection conn = null;
		try {

			Log.e("URL", "> " + url);
			conn = (HttpURLConnection) url.openConnection();
			conn.setAllowUserInteraction(false);
			conn.setInstanceFollowRedirects(true);
			conn.setRequestMethod("POST");
			conn.connect();

			// post the request
			OutputStream out = conn.getOutputStream();
			out.write(bytes);
			out.close();

			// post response
			InputStream is = conn.getInputStream();
			parsedString = convertinputStreamToString(is);

			// handle the response
			int status = conn.getResponseCode();

			// If response is not success
			if (status != 200) {

				throw new IOException("Post failed with error code " + status);
			}
		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}

		return parsedString;
	}

	private String tryPostWithJsonResult(Context context, String serverUrl,
			Map<String, String> params) {
		long backoff = BACKOFF_MILLI_SECONDS + random.nextInt(1000);
		String jsonResponse = "";
		// Once GCM returns a registration id, we need to register on our server
		// As the server might be down, we will retry it a couple
		// times.
		for (int i = 1; i <= MAX_ATTEMPTS; i++) {

			Log.d(Config.TAG, "Attempt #" + i + " to register");

			try {
				// Post registration values to web server
				jsonResponse = post_response(serverUrl, params);

				Log.d("@@@@@@@@@@@@@", "Friends: " + jsonResponse);

				// Send Broadcast to Show message on screen
				displayMessageOnScreen(context, "request friend list!");

				break;
			} catch (IOException e) {

				// Here we are simplifying and retrying on any error; in a real
				// application, it should retry only on unrecoverable errors
				// (like HTTP error code 503).

				Log.e(Config.TAG, "Failed to send on attempt " + i + ":" + e);

				if (i == MAX_ATTEMPTS) {
					break;
				}
				try {

					Log.d(Config.TAG, "Sleeping for " + backoff
							+ " ms before retry");
					Thread.sleep(backoff);

				} catch (InterruptedException e1) {
					// Activity finished before we complete - exit.
					Log.d(Config.TAG,
							"Thread interrupted: abort remaining retries!");
					Thread.currentThread().interrupt();
					return "";
				}

				// increase backoff exponentially
				backoff *= 2;
			}
		}

		return jsonResponse;
	}

	private void tryPostNoResult(Context context, String serverUrl,
			Map<String, String> params) {
		long backoff = BACKOFF_MILLI_SECONDS + random.nextInt(1000);

		// Once GCM returns a registration id, we need to register on our server
		// As the server might be down, we will retry it a couple
		// times.
		for (int i = 1; i <= MAX_ATTEMPTS; i++) {

			Log.d(Config.TAG, "Attempt #" + i + " to register");

			try {
				// Post registration values to web server
				post(serverUrl, params);

				// Send Broadcast to Show message on screen
				// displayMessageOnScreen(context, "location is sent!");

				return;
			} catch (IOException e) {

				// Here we are simplifying and retrying on any error; in a real
				// application, it should retry only on unrecoverable errors
				// (like HTTP error code 503).

				Log.e(Config.TAG, "Failed to send on attempt " + i + ":" + e);

				if (i == MAX_ATTEMPTS) {
					break;
				}
				try {

					Log.d(Config.TAG, "Sleeping for " + backoff
							+ " ms before retry");
					Thread.sleep(backoff);

				} catch (InterruptedException e1) {
					// Activity finished before we complete - exit.
					Log.d(Config.TAG,
							"Thread interrupted: abort remaining retries!");
					Thread.currentThread().interrupt();
					return;
				}

				// increase backoff exponentially
				backoff *= 2;
			}
		}

		// Send Broadcast to Show message on screen
		displayMessageOnScreen(context, "sent error!");
	}

	public static String convertinputStreamToString(InputStream ists)
			throws IOException {
		if (ists != null) {
			StringBuilder sb = new StringBuilder();
			String line;

			try {
				BufferedReader r1 = new BufferedReader(new InputStreamReader(
						ists, "UTF-8"));
				while ((line = r1.readLine()) != null) {
					sb.append(line).append("\n");
				}
			} finally {
				ists.close();
			}
			return sb.toString();
		} else {
			return "";
		}
	}

	// Checking for all possible internet providers
	public boolean isConnectingToInternet() {

		ConnectivityManager connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		if (connectivity != null) {
			NetworkInfo[] info = connectivity.getAllNetworkInfo();
			if (info != null)
				for (int i = 0; i < info.length; i++)
					if (info[i].getState() == NetworkInfo.State.CONNECTED) {
						return true;
					}

		}
		return false;
	}

	// Notifies UI to display a message.
	void displayMessageOnScreen(Context context, String message) {

		Intent intent = new Intent(Config.DISPLAY_MESSAGE_ACTION);
		intent.putExtra(Config.EXTRA_MESSAGE, message);

		// Send Broadcast to Broadcast receiver with message
		context.sendBroadcast(intent);

	}

	// Function to display simple Alert Dialog
	public void showAlertDialog(Context context, String title, String message,
			Boolean status) {
		AlertDialog alertDialog = new AlertDialog.Builder(context).create();

		// Set Dialog Title
		alertDialog.setTitle(title);

		// Set Dialog Message
		alertDialog.setMessage(message);

		if (status != null)
			// Set alert dialog icon
			alertDialog
					.setIcon((status) ? R.drawable.success : R.drawable.fail);

		// Set OK Button
		alertDialog.setButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {

			}
		});

		// Show Alert Message
		alertDialog.show();
	}

	private PowerManager.WakeLock wakeLock;

	public void acquireWakeLock(Context context) {
		if (wakeLock != null)
			wakeLock.release();

		PowerManager pm = (PowerManager) context
				.getSystemService(Context.POWER_SERVICE);

		wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK
				| PowerManager.ACQUIRE_CAUSES_WAKEUP
				| PowerManager.ON_AFTER_RELEASE, "WakeLock");

		wakeLock.acquire();
	}

	public void releaseWakeLock() {
		if (wakeLock != null)
			wakeLock.release();
		wakeLock = null;
	}

}
