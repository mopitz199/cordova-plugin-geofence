package com.cowbell.cordova.geofence;

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import java.util.Random;

class TalanaOpenHelper extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 2;
    private static final String DATABASE_NAME = "talana";
    private static final String DICTIONARY_TABLE_NAME = "dictionary";
    private static final String KEY_WORD = "key";
    private static final String KEY_DEFINITION = "value";
    private static final String DICTIONARY_TABLE_CREATE =
            "CREATE TABLE " + DICTIONARY_TABLE_NAME + " (" +
                    KEY_WORD + " TEXT, " +
                    KEY_DEFINITION + " TEXT);";


    TalanaOpenHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }


    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(DICTIONARY_TABLE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // No hay operaciones
    }
}

public class ReceiveTransitionsIntentService extends IntentService {
    protected static final String GeofenceTransitionIntent = "com.cowbell.cordova.geofence.TRANSITION";
    protected BeepHelper beepHelper;
    protected GeoNotificationNotifier notifier;
    protected GeoNotificationStore store;

    /**
     * Sets an identifier for the service
     */
    public ReceiveTransitionsIntentService() {
        super("ReceiveTransitionsIntentService");
        beepHelper = new BeepHelper();
        store = new GeoNotificationStore(this);
        Logger.setLogger(new Logger(GeofencePlugin.TAG, this, false));
    }

    /**
     * Handles incoming intents
     *
     * @param intent
     *            The Intent sent by Location Services. This Intent is provided
     *            to Location Services (inside a PendingIntent) when you call
     *            addGeofences()
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        Logger logger = Logger.getLogger();
        logger.log(Log.DEBUG, "ReceiveTransitionsIntentService - onHandleIntent");
        Intent broadcastIntent = new Intent(GeofenceTransitionIntent);
        notifier = new GeoNotificationNotifier(
                (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE),
                this
        );

        // TODO: refactor this, too long
        // First check for errors
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        if (geofencingEvent.hasError()) {
            // Get the error code with a static method
            int errorCode = geofencingEvent.getErrorCode();
            String error = "Location Services error: " + Integer.toString(errorCode);
            // Log the error
            logger.log(Log.ERROR, error);
            broadcastIntent.putExtra("error", error);
        } else {
            // Get the type of transition (entry or exit)
            int transitionType = geofencingEvent.getGeofenceTransition();
            if ((transitionType == Geofence.GEOFENCE_TRANSITION_ENTER)
                    || (transitionType == Geofence.GEOFENCE_TRANSITION_EXIT)) {
                logger.log(Log.DEBUG, "Geofence transition detected");
                List<Geofence> triggerList = geofencingEvent.getTriggeringGeofences();
                List<GeoNotification> geoNotifications = new ArrayList<GeoNotification>();
                for (Geofence fence : triggerList) {
                    String fenceId = fence.getRequestId();
                    GeoNotification geoNotification = store
                            .getGeoNotification(fenceId);

                    if (geoNotification != null) {
                        if (geoNotification.notification != null) {
                            Calendar cal = Calendar.getInstance();
                            SimpleDateFormat sdf = new SimpleDateFormat("d");


                            TalanaOpenHelper helper = new TalanaOpenHelper(this);
                            //SQLiteDatabase db = helper.getWritableDatabase();


                            /*ContentValues values = new ContentValues();
                            values.put("key", "nombre");
                            values.put("value", "maximiliano");

                            long newRowId = db.insert("dictionary", null, values);*/

                            SQLiteDatabase db = helper.getReadableDatabase();
                            String[] projection = {
                                    "key",
                                    "value"
                            };

                            // Filter results WHERE "title" = 'My Title'
                            String selection = "key" + " = ?";
                            String[] selectionArgs = { "notificationDay" };

                            String sortOrder =
                                    "key" + " DESC";

                            Cursor c = db.query(
                                    "dictionary",                     // The table to query
                                    projection,                               // The columns to return
                                    selection,                                // The columns for the WHERE clause
                                    selectionArgs,                            // The values for the WHERE clause
                                    null,                                     // don't group the rows
                                    null,                                     // don't filter by row groups
                                    sortOrder                                 // The sort order
                            );

                            if(c.moveToFirst()){
                                String key = c.getString(0);
                                String value = c.getString(1);

                                int notificatedDay = Integer.parseInt(value);
                                int currentDay = Integer.parseInt(sdf.format(cal.getTime()));

                                if(notificatedDay==0 || notificatedDay!=currentDay){
                                    notifier.notify(geoNotification.notification);
                                }
                            }else{
                                notifier.notify(geoNotification.notification);
                            }
                        }
                        geoNotification.transitionType = transitionType;
                        geoNotifications.add(geoNotification);
                    }
                }

                if (geoNotifications.size() > 0) {
                    broadcastIntent.putExtra("transitionData", Gson.get().toJson(geoNotifications));
                    GeofencePlugin.onTransitionReceived(geoNotifications);
                }
            } else {
                String error = "Geofence transition error: " + transitionType;
                logger.log(Log.ERROR, error);
                broadcastIntent.putExtra("error", error);
            }
        }
        sendBroadcast(broadcastIntent);
    }
}
