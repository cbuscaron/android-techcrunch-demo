package li.vin.techcrunchdemo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import li.vin.my.deviceservice.DeviceConnection;
import li.vin.my.deviceservice.Param;
import li.vin.my.deviceservice.Params;
import li.vin.my.deviceservice.VinliDevices;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.subscriptions.CompositeSubscription;

/**
 * Connect to a Vinli device over Bluetooth, log some basic info in a bunch of TextViews.
 * <br><br>
 * Uses <a href="https://github.com/vinli/android-bt">the Vinli Bluetooth SDK</a>, <a
 * href="https://github.com/ReactiveX/RxJava">RxJava</a>, and
 * <a href="http://jakewharton.github.io/butterknife/">Butter Knife</a>.
 */
public class TechCrunchDemoActivity extends AppCompatActivity {
  private static final String TAG = TechCrunchDemoActivity.class.getSimpleName();

  @Bind(R.id.chip_id) TextView chipId;

  @Bind(R.id.accel_x) TextView accelX;
  @Bind(R.id.accel_y) TextView accelY;
  @Bind(R.id.accel_z) TextView accelZ;

  @Bind(R.id.vin) TextView vin;
  @Bind(R.id.rpm) TextView rpm;
  @Bind(R.id.battery_voltage) TextView batteryVoltage;

  CompositeSubscription subscription;
  Observable<DeviceConnection> vinliConnectionObservable;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    ButterKnife.bind(this);
  }

  @Override
  protected void onStart() {
    super.onStart();
    initVinliConnection(false);
  }

  @Override
  protected void onStop() {
    shutdownVinliConnection();
    super.onStop();
  }

  @OnClick(R.id.revoke_device)
  public void revokeDevice() {
    // In response to user feedback, force a fresh Vinli device choice. Maybe they chose the wrong
    // device from a list of nearby devices by accident, or maybe they are just fickle.
    initVinliConnection(true);
  }

  /** Permissively clean up and put us back into the ready state for a fresh connection. */
  private void shutdownVinliConnection() {
    vinliConnectionObservable = null;
    if (subscription != null) {
      subscription.unsubscribe();
      subscription = null;
    }
  }

  /**
   * Initialize a new Bluetooth connection to a nearby Vinli device - automatically cleans up
   * any existing connections first. Optionally forces the user to make a fresh choice from a list
   * of nearby devices before initializing.
   */
  private void initVinliConnection(boolean forceDeviceChoice) {
    // Make sure we're cleaned up first.
    shutdownVinliConnection();

    if (!VinliDevices.isMyVinliInstalledAndUpdated(this)) {

      // Check to make sure My Vinli is installed - otherwise handle the error.
      // My Vinli provides the Bluetooth service, so this is not an optional step.
      VinliDevices.createMyVinliInstallRequestDialog(this).show();
    } else {

      // My Vinli is installed and ready - great! Get an observable for a Bluetooth connection.
      // This is an asynchronous operation which reaches out to my the My Vinli app and enables
      // Bluetooth if necessary, requests authorization from the user, prompts them to choose their
      // desired nearby Vinli device, and bakes them a cake.
      Observable<DeviceConnection> conn = vinliConnectionObservable =
          VinliDevices.connect(this, getString(R.string.app_client_id),
              getString(R.string.app_redirect_uri), forceDeviceChoice);

      // Keep all our rx subscriptions together in a neat bunch.
      subscription = new CompositeSubscription();

      // Grab the chip ID so we know what we're connecting to.
      subscription.add(conn.map(new Func1<DeviceConnection, String>() {
        @Override
        public String call(DeviceConnection deviceConnection) {
          return deviceConnection.chipId();
        }
      }).observeOn(AndroidSchedulers.mainThread()).subscribe(new Subscriber<String>() {
        @Override
        public void onCompleted() {
          Log.e(TAG, getString(R.string.chip_id) + " onCompleted");
        }

        @Override
        public void onError(Throwable e) {
          Log.e(TAG, getString(R.string.chip_id) + " onError " + e);
          chipId.setText(getString(R.string.chip_id) + ": error!");
        }

        @Override
        public void onNext(String val) {
          Log.e(TAG, getString(R.string.chip_id) + " onNext " + val);
          chipId.setText(getString(R.string.chip_id) + ": " + val);
        }
      }));

      // Subscribe to a bunch of basic information.
      makeParamSubscription(conn, Params.VIN, vin, getString(R.string.vin));
      makeParamSubscription(conn, Params.RPM, rpm, getString(R.string.rpm));
      makeParamSubscription(conn, Params.BATTERY_VOLTAGE, batteryVoltage,
          getString(R.string.battery_voltage));
      makeParamSubscription(conn, Params.ACCEL_X, accelX, getString(R.string.accel_x));
      makeParamSubscription(conn, Params.ACCEL_Y, accelY, getString(R.string.accel_y));
      makeParamSubscription(conn, Params.ACCEL_Z, accelZ, getString(R.string.accel_z));
    }
  }

  /** Create a subscription to update a TextView from a param provided by the Bluetooth SDK. */
  private <T> void makeParamSubscription(Observable<DeviceConnection> conn, final Param<T> param,
      final TextView view, final String label) {
    subscription.add(conn.flatMap(new Func1<DeviceConnection, Observable<T>>() {
      @Override
      public Observable<T> call(DeviceConnection deviceConnection) {
        return deviceConnection.observe(param);
      }
    }).observeOn(AndroidSchedulers.mainThread()).subscribe(new Subscriber<T>() {
      @Override
      public void onCompleted() {
        Log.e(TAG, label + " onCompleted");
      }

      @Override
      public void onError(Throwable e) {
        Log.e(TAG, label + " onError " + e);
        view.setText(label + ": error!");
      }

      @Override
      public void onNext(T val) {
        Log.e(TAG, label + " onNext " + val);
        view.setText(label + ": " + val);
      }
    }));
  }
}
