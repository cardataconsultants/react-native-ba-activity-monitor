package com.reactnativebaactivitymonitor;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.modules.core.PermissionAwareActivity;
import com.facebook.react.modules.core.PermissionListener;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.tasks.Task;
import java.util.ArrayList;
import java.util.List;

@ReactModule(name = BaActivityMonitorModule.NAME)
public class BaActivityMonitorModule extends ReactContextBaseJavaModule implements PermissionListener {

    private final static String TAG = "BaActivityModule";
    public static final String NAME = "BaActivityMonitor";

    private TransitionsReceiver mTransitionsReceiver = new TransitionsReceiver();
    private PendingIntent mActivityTransitionsPendingIntent;
    private static final int PERMISSION_REQUEST_ACTIVITY_RECOGNITION = 18923671;
    private boolean activityTrackingEnabled;

    private boolean runningQOrLater =
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q;
    private final String TRANSITIONS_RECEIVER_ACTION =
        "BA_ACTIVITY_MONITOR_TRANSITIONS_RECEIVER_ACTION";

    private final String GRANTED = "granted";
    private final String DENIED = "denied";
    private final String BLOCKED = "blocked";

    private Request mPermissionRequest;


    public BaActivityMonitorModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    @NonNull
    public String getName() {
        return NAME;
    }

    private void addFullTransition(List<ActivityTransition> transitions, int activity) {
        transitions.add(
                new ActivityTransition.Builder()
                .setActivityType(activity)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build());

        transitions.add(
                new ActivityTransition.Builder()
                .setActivityType(activity)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build());
    }

    public boolean isAllowedToTrackActivities() {
        if (runningQOrLater) {
            return PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
                    getReactApplicationContext().getCurrentActivity(),
                    Manifest.permission.ACTIVITY_RECOGNITION
            );
        } else {
            return true;
        }
    }

    private void startTracking(Promise promise) {
        List<ActivityTransition> transitions = new ArrayList<>();

        addFullTransition(transitions, DetectedActivity.IN_VEHICLE);
        addFullTransition(transitions, DetectedActivity.WALKING);
        addFullTransition(transitions, DetectedActivity.RUNNING);
        addFullTransition(transitions, DetectedActivity.STILL);
        addFullTransition(transitions, DetectedActivity.ON_BICYCLE);
        addFullTransition(transitions, DetectedActivity.ON_FOOT);

        ActivityTransitionRequest request = new ActivityTransitionRequest(transitions);

        getReactApplicationContext().getCurrentActivity().registerReceiver(mTransitionsReceiver, new IntentFilter(TRANSITIONS_RECEIVER_ACTION));
        mActivityTransitionsPendingIntent = PendingIntent.getBroadcast(getReactApplicationContext().getCurrentActivity(), 0, new Intent(TRANSITIONS_RECEIVER_ACTION), 0);

        Task<Void> task = ActivityRecognition.getClient(getReactApplicationContext().getCurrentActivity())
          .requestActivityTransitionUpdates(request, mActivityTransitionsPendingIntent);

        task.addOnSuccessListener(
          result -> {
              activityTrackingEnabled = true;
              promise.resolve(true);
          });

        task.addOnFailureListener(
          e -> {
              activityTrackingEnabled = false;
              promise.reject(e);
          });
    }

    @ReactMethod
    public void askPermissionAndroid(Promise promise) {
      String permission = Manifest.permission.ACTIVITY_RECOGNITION;
      PermissionAwareActivity activity = getPermissionAwareActivity();
      boolean[] rationaleStatuses = new boolean[1];
      rationaleStatuses[0] = activity.shouldShowRequestPermissionRationale(permission);

      mPermissionRequest = new Request(
        rationaleStatuses,
        new Callback() {
          @SuppressLint("ApplySharedPref")
          @Override
          public void invoke(Object... args) {
            int[] results = (int[]) args[0];

            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
              promise.resolve(GRANTED);
            } else {
              PermissionAwareActivity activity = (PermissionAwareActivity) args[1];
              boolean[] rationaleStatuses = (boolean[]) args[2];

              if (rationaleStatuses[0] &&
                !activity.shouldShowRequestPermissionRationale(permission)) {
                promise.resolve(BLOCKED);
              } else {
                promise.resolve(DENIED);
              }
            }
          }
        });

      activity.requestPermissions(new String[]{permission}, PERMISSION_REQUEST_ACTIVITY_RECOGNITION, this);
    }

    @ReactMethod
    public void start(Promise promise) {
        if(activityTrackingEnabled) {
          return;
        }

        if (!isAllowedToTrackActivities()) {
            startTracking(promise);
            promise.resolve(true);
        } else {
            askPermissionAndroid(promise);
        }
    }

    @ReactMethod
    public void stop() {
      if(!activityTrackingEnabled) {
        return;
      }

      getReactApplicationContext().getCurrentActivity().unregisterReceiver(mTransitionsReceiver);
    }


    private PermissionAwareActivity getPermissionAwareActivity() {
      Activity activity = getCurrentActivity();
      if (activity == null) {
        throw new IllegalStateException(
          "Tried to use permissions API while not attached to an Activity.");
      } else if (!(activity instanceof PermissionAwareActivity)) {
        throw new IllegalStateException(
          "Tried to use permissions API but the host Activity doesn't implement PermissionAwareActivity.");
      }
      return (PermissionAwareActivity) activity;
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
      if(requestCode != PERMISSION_REQUEST_ACTIVITY_RECOGNITION) {
        return false;
      }
      mPermissionRequest.callback.invoke(grantResults, getPermissionAwareActivity(), mPermissionRequest.rationaleStatuses);
      mPermissionRequest = null;
      return true;
    }

    private class TransitionsReceiver extends BroadcastReceiver {

          @Override
          public void onReceive(Context context, Intent intent) {
              Log.d(TAG, "onReceive(): " + intent);

              if (!TextUtils.equals(TRANSITIONS_RECEIVER_ACTION, intent.getAction())) {

                  Log.e(TAG, "Received an unsupported action in TransitionsReceiver: action = " +
                          intent.getAction());
                  return;
              }


          }
      }

    private class Request {

      public boolean[] rationaleStatuses;
      public Callback callback;

      public Request(boolean[] rationaleStatuses, Callback callback) {
        this.rationaleStatuses = rationaleStatuses;
        this.callback = callback;
      }
    }

}
