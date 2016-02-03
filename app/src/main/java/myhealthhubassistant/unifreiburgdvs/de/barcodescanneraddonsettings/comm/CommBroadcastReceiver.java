package myhealthhubassistant.unifreiburgdvs.de.barcodescanneraddonsettings.comm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import myhealthhubassistant.unifreiburgdvs.de.barcodescanneraddonsettings.utils.Constants;

import de.tudarmstadt.dvs.myhealthassistant.myhealthhub.events.AbstractChannel;
import de.tudarmstadt.dvs.myhealthassistant.myhealthhub.events.Event;
import de.tudarmstadt.dvs.myhealthassistant.myhealthhub.events.management.JSONDataExchange;
import de.tudarmstadt.dvs.myhealthassistant.myhealthhub.events.management.ManagementEvent;


public class CommBroadcastReceiver extends BroadcastReceiver {
	private static final String TAG = CommBroadcastReceiver.class
			.getSimpleName();
	private int evtCounter = 0;
	private final Context context;
	private final JSONResult jResult;

	public CommBroadcastReceiver(Context context, JSONResult r) {
		this.context = context;
		this.jResult = r;
	}

	// Other Activity must implement this to get the entries delivered from myHealthHub
	public static abstract class JSONResult {
		public abstract void gotResult(JSONArray arrayR) throws IOException, JSONException;
	}

	/**
	 * Send GET-Request over to myHealthHub to get all encoded JSONObject of
	 * scanned codes
	 */
	public void getJSONEntryList() {
		// send GetRequest over to myHealthHub through management channel
		evtCounter++;
		JSONObject jEncodedData = new JSONObject();

		try {
			// request to store encoded data to db
			jEncodedData.putOpt(JSONDataExchange.JSON_REQUEST,
					JSONDataExchange.JSON_GET);

			JSONDataExchange eData = new JSONDataExchange(TAG + evtCounter,
					getTimestamp(), TAG, context.getPackageName(),
					JSONDataExchange.EVENT_TYPE, jEncodedData.toString());

			// Publishes a management event to myHealthHub
			publishEvent(eData, AbstractChannel.MANAGEMENT);

		} catch (JSONException e) {
			e.printStackTrace();
		}
	}
	/**
	 * Publishes an event on a specific myHealthHub channel.
	 * 
	 * @param event
	 *            that shall be published.
	 * @param channel
	 *            on which the event shall be published.
	 */
	private void publishEvent(Event event, String channel) {
		Intent i = new Intent();
		// add event
		i.putExtra(Event.PARCELABLE_EXTRA_EVENT_TYPE, event.getEventType());
		i.putExtra(Event.PARCELABLE_EXTRA_EVENT, event);

		// set channel as Management
		i.setAction(channel);

		// set receiver package
		i.setPackage("de.tudarmstadt.dvs.myhealthassistant.myhealthhub");

		// sent intent
		context.sendBroadcast(i);
	}

	@Override
	public void onReceive(Context arg0, Intent intent) {
		if (intent == null)
			return;

		Event evt = intent.getParcelableExtra(Event.PARCELABLE_EXTRA_EVENT);
		// String type = evt.getEventType();
		// Log.e(TAG, type);
		// JSONDataExchange
		if (evt.getEventType().equals(ManagementEvent.JSON_DATA_EXCHANGE)) {
			Log.e(TAG, "JSON Data Exchange event from: " + evt.getProducerID());

			String jsonDataString = ((JSONDataExchange) evt)
					.getJSONEncodedData();

			try {
				JSONObject jsonData = new JSONObject(jsonDataString);
				String json_request = jsonData.optString(
						JSONDataExchange.JSON_REQUEST, "null");
				JSONArray jObjArray = jsonData
						.optJSONArray(Constants.JSON_REQUEST_CONTENT_ARRAY);

				if (json_request.equalsIgnoreCase(JSONDataExchange.JSON_GET)) {
					// received jsonEncodeData from myHealthHub
					if (jObjArray != null) {
						try {
							jResult.gotResult(jObjArray);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Returns the current time
	 * 
	 * @return timestamp
	 */
	private String getTimestamp() {
		return (String) android.text.format.DateFormat.format(
				"yyyy-MM-dd\nkk:mm:ss", new java.util.Date());
	}
}
