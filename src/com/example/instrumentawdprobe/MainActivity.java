package com.example.instrumentawdprobe;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Presentation;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.ServiceConnection;
import android.content.res.AssetFileDescriptor;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.hardware.display.DisplayManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.Settings;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.URL;
import java.util.Arrays;
import java.util.Locale;
import java.security.SecureRandom;

/** Read-only probe for instrument Display 2 and the stock AWD energy-flow signals. */
public final class MainActivity extends Activity {
    private static final String TAG = "InstrumentAwdProbe";
    private static final int AMAP_INSTRUMENT_DISPLAY_ID = 2;
    public static final String ACTION_SHOW_AWD =
            "com.example.instrumentawdprobe.action.SHOW_AWD";
    public static final String ACTION_HIDE_AWD =
            "com.example.instrumentawdprobe.action.HIDE_AWD";
    private static final String PREFS = "awd_display";
    private static final String PREF_ENABLED = "enabled";
    private static final String PREF_AUTOSTART = "autostart";
    private static final String PREF_CAMERA_AUDIO = "camera_audio";
    private static final String PREF_CAMERA_HUD = "camera_hud";
    private static final String PREF_CAMERA_GPS_GUARD = "camera_gps_guard";
    private static final String PREF_PHONE_GPS_ENABLED = "phone_gps_enabled";
    private static final String PREF_PHONE_GPS_TOKEN = "phone_gps_token";
    private static final String PREF_CAMERA_WARNING_MODE = "camera_warning_mode";
    private static final String PREF_CAMERA_SOUND = "camera_sound";
    private static final String PREF_IDLE_MODE = "idle_mode";
    private static final String PREF_CLUSTER_MANEUVER_POSITION =
            "cluster_maneuver_position";
    private static final String PREF_NAVIGATION_HUD = "navigation_hud";
    private static final String PREF_INSTRUMENT_TBT = "instrument_tbt";
    private static final String PREF_MEDIA_BRIDGE_AUTO_PLAY = "media_bridge_auto_play";
    private static final String PREF_MEDIA_BRIDGE_ROUTE_CARD = "media_bridge_route_card";
    private static final String PREF_MEDIA_BRIDGE_NAV_CONTROLS =
            "media_bridge_nav_controls";
    private static final String PREF_CAMERA_ETAG = "camera_etag";
    private static final String PREF_CAMERA_LAST_MODIFIED = "camera_last_modified";
    private static final int CAMERA_DISTANCE_CITY_METERS = 400;
    private static final int CAMERA_DISTANCE_FAST_METERS = 600;
    private static final float CAMERA_FAST_SPEED_KMH = 80f;
    private static final int REQUEST_LOCATION_PERMISSION = 1001;
    private static final long CAMERA_LOCATION_STALE_MS = 10000L;
    private static final long CAMERA_LOCATION_WATCHDOG_MS = 2000L;
    private static final long PHONE_GPS_STALE_MS = 6000L;
    private static final String CAMERA_DATABASE_ASSET = "hud_speed.txt";
    private static final String CAMERA_DATABASE_FILE = "hud_speed.txt";
    private static final String CAMERA_DATABASE_URL =
            "https://dwn.jcartools.ru/arad/h_ru.zip";
    private static final String CAMERA_DATABASE_ZIP_ENTRY = "PocketGisPlus.txt";
    private static final long MAX_CAMERA_ZIP_BYTES = 10L * 1024L * 1024L;
    private static final long MAX_CAMERA_TEXT_BYTES = 25L * 1024L * 1024L;
    private static final int MIN_PRIMARY_CAMERA_COUNT = 50000;
    private static final String[] CAMERA_SOUND_NAMES = {
            "Мягкий аккорд", "Плавная мелодия", "Мягкий короткий сигнал",
            "ICQ — Oh-oh", "Эпическое уведомление", "Портал — гул",
            "Древние слова — шёпот"
    };
    private static final String[] CAMERA_SOUND_ASSETS = {
            null, null, null,
            "camera_sounds/icq-oh-oh.mp3",
            "camera_sounds/epic-contact.mp3",
            "camera_sounds/portal-hum.mp3",
            "camera_sounds/ancient-whisper.mp3"
    };
    private static final float[] CAMERA_SOUND_VOLUMES = {
            1f, 1f, 1f, 0.42f, 0.68f, 0.34f, 0.68f
    };
    private static final String[] CAMERA_WARNING_MODE_NAMES = {
            "Всегда", "Только при превышении скорости"
    };
    private static final int CAMERA_WARNING_ALWAYS = 0;
    private static final int CAMERA_WARNING_OVERSPEED = 1;
    private static final float OVERSPEED_ACTIVATION_MARGIN_KMH = 2f;
    private static final String[] IDLE_MODE_NAMES = {
            "Полный привод", "Заглушка", "Антирадар", "Маршрут"
    };
    // Preserve the stored values used by previous versions while presenting the
    // four owner-facing modes in their requested order.
    private static final int[] IDLE_MODE_VALUES = {1, 0, 2, 3};
    private static final int IDLE_MODE_BLANK = 0;
    private static final int IDLE_MODE_AWD = 1;
    private static final int IDLE_MODE_RADAR = 2;
    private static final int IDLE_MODE_ROUTE = 3;
    private static final String[] CLUSTER_MANEUVER_POSITION_NAMES = {
            "Сверху справа, над скоростью", "Сверху слева", "Слева ниже", "Снизу справа"
    };
    private static final int CLUSTER_MANEUVER_POSITION_UPPER_INNER = 0;

    private static final String IPK_PACKAGE = "com.neusoft.ipkservice";
    private static final String IPK_SERVICE =
            "com.neusoft.ipkservice.cluster.EcologicalDataService";
    private static final String IPK_ACTION = "android.service.IPK.EcologicalDataService";
    private static final String IPK_DESCRIPTOR =
            "com.neusoft.ConnectServiceApi.IPKService.IIPKService";
    private static final String IPK_NAVI_CALLBACK_DESCRIPTOR =
            "com.neusoft.ConnectServiceApi.IPKService.IIPKChangeNaviCacllback";
    private static final String IPK_KEY_CALLBACK_DESCRIPTOR =
            "com.neusoft.ConnectServiceApi.IPKService.IIPKKeyNotify";
    private static final int TRANSACTION_UPDATE_NAVI_INFO = 3;
    private static final int TRANSACTION_UPDATE_TBT_NAVI_INFO = 6;
    private static final int TRANSACTION_REGISTER_NAVI_CALLBACK = 7;
    private static final int TRANSACTION_UNREGISTER_NAVI_CALLBACK = 8;
    private static final int TRANSACTION_REGISTER_IPK_KEY_CALLBACK = 13;
    private static final int TRANSACTION_UNREGISTER_IPK_KEY_CALLBACK = 14;
    private static final int TRANSACTION_ON_CHANGE_NAVI = 1;
    private static final int TRANSACTION_ON_IPK_KEY_PRESSED = 1;
    private static final int TRANSACTION_ON_IPK_KEY_RELEASED = 2;
    private static final int IPK_REQUEST_MINI = 1;
    private static final int IPK_REQUEST_FULL_SCREEN = 2;
    // This firmware uses mapMode=3/state=3 to make the stock navigation pane
    // transparent while keeping its MINI slot active for our overlay.
    private static final int IPK_WIDGET_MAP_MODE = 3;
    private static final String YANDEX_NAVI_PACKAGE = "ru.yandex.yandexnavi";
    private static final String YANDEX_MAPS_PACKAGE = "ru.yandex.yandexmaps";
    private static final String YANDEX_CLUSTER_ACTIVITY =
            "ru.yandex.yandexmaps.app.ClusterMapActivity";
    private static final String YANDEX_CLUSTER_ACTION =
            "ru.yandex.yandexnavi.action.CLUSTER_MAP";
    private static final String YANDEX_MANEUVER_ACTION = "Y.maneuver.guide";
    private static final String YANDEX_MANEUVER_VISIBILITY_ACTION = "Y.maneuver.vis";
    private static final String YANDEX_NAV_MAP_ACTION = "Y.nav.map";
    private static final String YANDEX_CLUSTER_LAYOUT_ACTION = "Y.lay.guide";
    private static final String TELENAV_ACTION = "com.telenav.app.START";
    private static final String TELENAV_EXTRA_HUD_ENABLED = "hudEnabled";
    private static final String TELENAV_EXTRA_PANEL_TBT_ENABLED = "panelTbtEnabled";
    private static final String TELENAV_EXTRA_PANEL_TBT_SUPPRESSED =
            "panelTbtSuppressed";
    private static final String TELENAV_EXTRA_PANEL_TBT_STATE_MANAGED_EXTERNALLY =
            "panelTbtStateManagedExternally";
    private static final ComponentName TELENAV_RECEIVER = new ComponentName(
            "com.telenav.app.arp", "com.telenav.app.receiver.BootReceiver");
    private static final String MEDIA_BRIDGE_PACKAGE = "ru.exeed.yandexmediabridge";
    private static final ComponentName MEDIA_BRIDGE_CONTROL_RECEIVER = new ComponentName(
            MEDIA_BRIDGE_PACKAGE,
            "ru.exeed.yandexmediabridge.BridgeControlReceiver");
    private static final String MEDIA_BRIDGE_ACTION_START =
            "ru.exeed.yandexmediabridge.action.START";
    private static final String MEDIA_BRIDGE_ACTION_SET_AUTO_PLAY =
            "ru.exeed.yandexmediabridge.action.SET_AUTO_PLAY";
    private static final String MEDIA_BRIDGE_EXTRA_AUTO_PLAY =
            "ru.exeed.yandexmediabridge.extra.AUTO_PLAY_ENABLED";
    private static final String MEDIA_BRIDGE_ACTION_SET_ROUTE_CARD =
            "ru.exeed.yandexmediabridge.action.SET_LAUNCHER_ROUTE_CARD";
    private static final String MEDIA_BRIDGE_ACTION_SET_NAV_CONTROLS =
            "ru.exeed.yandexmediabridge.action.SET_NAVIGATOR_CORNER_CONTROLS";
    private static final String MEDIA_BRIDGE_EXTRA_ROUTE_CARD =
            "ru.exeed.yandexmediabridge.extra.LAUNCHER_ROUTE_CARD_ENABLED";
    private static final String MEDIA_BRIDGE_EXTRA_NAV_CONTROLS =
            "ru.exeed.yandexmediabridge.extra.NAVIGATOR_CORNER_CONTROLS_ENABLED";
    private static final String NEUSOFT_NAVI_STATE_ACTION =
            "AUTONAVI_STANDARD_BROADCAST_SEND";
    private static final String NEUSOFT_NAVI_KEY_TYPE = "KEY_TYPE";
    private static final String NEUSOFT_NAVI_EXTRA_STATE = "EXTRA_STATE";
    private static final int NEUSOFT_NAVI_TYPE = 10019;
    private static final int NEUSOFT_NAVI_START = 8;
    private static final int NEUSOFT_NAVI_STOP = 9;
    private static final int STEERING_SILENT_SAMPLE_RATE = 48000;
    private static final long STEERING_LONG_PRESS_MS = 1200L;
    private static final boolean ENABLE_STEERING_MEDIA_SHORTCUT = false;
    private static final int RPC_HARD_KEY_EVENT = 1296;
    private static final int INSTRUMENT_MODE_KEY_CODE = 76;

    private static final int PROP_ESTIMATED_COUPLING_TORQUE = 560992876;
    private static final int PROP_ENGINE_WHEEL_TORQUE_RATIO = 560992877;
    private static final int PROP_MEAN_EFFECTIVE_TORQUE = 560992878;
    private static final int PROP_HUD_DISTANCE_TO_DESTINATION = 560992986;
    private static final int PROP_HUD_DISTANCE_TO_JUNCTION = 560992987;
    private static final int PROP_HUD_NAVIGATION_TEXT = 560992988;
    private static final int PROP_ENGINE_STATE = 557847175;
    private static final int PROP_LEVER_MODE = 557847156;
    private static final int[] AWD_PROPERTIES = {
            PROP_ESTIMATED_COUPLING_TORQUE,
            PROP_ENGINE_WHEEL_TORQUE_RATIO,
            PROP_MEAN_EFFECTIVE_TORQUE,
            PROP_ENGINE_STATE,
            PROP_LEVER_MODE
    };

    private TextView logView;
    private TextView controlStatusView;
    private Button enableButton;
    private Button disableButton;
    private Spinner idleModeSpinner;
    private Spinner clusterManeuverPositionSpinner;
    private Switch cameraAudioCheck;
    private Switch cameraHudCheck;
    private Switch cameraGpsGuardCheck;
    private Switch phoneGpsCheck;
    private TextView phoneGpsStatusView;
    private Switch autostartCheck;
    private Switch mediaBridgeAutoPlayCheck;
    private Switch mediaBridgeRouteCardCheck;
    private Switch mediaBridgeNavigationControlsCheck;
    private Switch navigationHudCheck;
    private Switch instrumentTbtCheck;
    private TextView mediaBridgeStatusView;
    private Spinner cameraSoundSpinner;
    private Spinner cameraWarningModeSpinner;
    private TextView cameraDistanceView;
    private TextView cameraDatabaseView;
    private TextView batteryInfoView;
    private Button cameraUpdateButton;
    private AwdPresentation presentation;
    private WindowManager overlayWindowManager;
    private View overlayRoot;
    private FrameLayout overlayContentRoot;
    private AwdView overlayView;
    private InstrumentMapSurfaceView overlayMapView;
    private WindowManager miniRouteGuidanceWindowManager;
    private MiniRouteGuidanceView overlayRouteGuidanceView;
    private WindowManager clusterGuidanceWindowManager;
    private ClusterGuidanceView clusterGuidanceView;
    private Object car;
    private Object vendorManager;
    private Object vendorCallback;
    private IBinder ipkBinder;
    private boolean ipkBound;
    private boolean ipkBindingRequested;
    private boolean ipkNaviCallbackRegistered;
    private boolean ipkKeyCallbackRegistered;
    private int pendingNavigationMode = -1;
    private volatile SpeedCameraIndex cameraIndex;
    private LocationManager locationManager;
    private Location lastLocation;
    private Location courseAnchorLocation;
    private long lastLocationUpdateElapsedMs;
    private long lastLocationGuardLogElapsedMs;
    private long lastPhoneLocationElapsedMs;
    private boolean phoneGpsWasPrimary;
    private final LocationGuard locationGuard = new LocationGuard();
    private boolean locationReceiverRegistered;
    private boolean yandexGuidanceReceiverRegistered;
    private String yandexNextRoadName = "";
    private String yandexManeuverResourceId = "";
    private int yandexManeuverDistanceMeters;
    private int yandexRouteDistanceMeters;
    private int yandexRouteTimeSeconds;
    private boolean yandexManeuverVisible;
    private String yandexRoutePoints = "";
    private double yandexNavigationLatitude = Double.NaN;
    private double yandexNavigationLongitude = Double.NaN;
    private float yandexNavigationHeading = Float.NaN;
    private long lastYandexPositionUpdateElapsedMs;
    private long lastYandexNavigationUpdateElapsedMs;
    private int hudNavigationTextGeneration;
    private long lastCameraScanLogMs;
    private SpeedCamera activeCamera;
    private SpeedCamera lastPassedCamera;
    private float activeMinimumDistance = Float.MAX_VALUE;
    private int activeCameraDistanceBucket = -1;
    private AudioManager alertAudioManager;
    private AudioFocusRequest alertFocusRequest;
    private AudioTrack alertAudioTrack;
    private MediaPlayer alertMediaPlayer;
    private boolean demoAlertActive;
    private boolean hudCameraActive;
    private Boolean hudOutputAvailable;
    private int lastHudCameraDistance = -1;
    private long lastHudCameraUpdateMs;
    private int hudDistanceOverrideGeneration;

    private int rawEstimatedCouplingTorque = -1;
    private int rawEngineWheelTorqueRatio = -1;
    private int rawMeanEffectiveTorque = -1;
    private int engineState = -1;
    private int leverMode = -1;
    private int frontPerWheel;
    private int rearPerWheel;
    private int demoFrontPerWheel = -1;
    private int demoRearPerWheel = -1;
    private boolean liveFullOverlay;
    private final Handler overlayHandler = new Handler(Looper.getMainLooper());
    private MediaSession steeringMediaSession;
    private AudioTrack steeringSilentAudioTrack;
    private long steeringButtonDownMs;
    private boolean steeringLongPressHandled;
    private boolean instrumentMapFullScreenActive;
    private boolean instrumentOverlayRestorePending;
    private static volatile Object instrumentModeRpcListener;
    private static volatile MainActivity activeInstance;
    private final IBinder ipkNaviCallback = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == IBinder.INTERFACE_TRANSACTION) {
                if (reply != null) reply.writeString(IPK_NAVI_CALLBACK_DESCRIPTOR);
                return true;
            }
            if (code == TRANSACTION_ON_CHANGE_NAVI) {
                data.enforceInterface(IPK_NAVI_CALLBACK_DESCRIPTOR);
                final int requestedMode = data.readInt();
                overlayHandler.post(new Runnable() {
                    @Override public void run() {
                        handleInstrumentNavigationRequest(requestedMode);
                    }
                });
                if (reply != null) reply.writeNoException();
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    };
    private final IBinder ipkKeyCallback = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == IBinder.INTERFACE_TRANSACTION) {
                if (reply != null) reply.writeString(IPK_KEY_CALLBACK_DESCRIPTOR);
                return true;
            }
            if (code == TRANSACTION_ON_IPK_KEY_PRESSED
                    || code == TRANSACTION_ON_IPK_KEY_RELEASED) {
                data.enforceInterface(IPK_KEY_CALLBACK_DESCRIPTOR);
                final int keyCode = data.readInt();
                final boolean pressed = code == TRANSACTION_ON_IPK_KEY_PRESSED;
                overlayHandler.post(new Runnable() {
                    @Override public void run() {
                        append("IPK instrument key " + keyCode + " "
                                + (pressed ? "pressed" : "released"));
                    }
                });
                if (reply != null) reply.writeNoException();
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    };
    private final Runnable overlayTimeout = new Runnable() {
        @Override public void run() { dismissInstrumentOverlay(); }
    };
    private final Runnable showPersistentOverlay = new Runnable() {
        @Override public void run() {
            instrumentOverlayRestorePending = false;
            if (InstrumentOverlayLifecyclePolicy.shouldRestore(
                    isAwdEnabled(), instrumentMapFullScreenActive,
                    isInstrumentOverlayAttached(), false)) {
                showFullInstrumentOverlay();
            }
        }
    };
    private final Runnable clearDemoAlert = new Runnable() {
        @Override public void run() {
            demoAlertActive = false;
            clearActiveCamera();
        }
    };
    private final Runnable releaseAlertAudio = new Runnable() {
        @Override public void run() { releaseAlertSound(); }
    };
    private final AudioManager.OnAudioFocusChangeListener alertFocusListener =
            new AudioManager.OnAudioFocusChangeListener() {
                @Override public void onAudioFocusChange(int focusChange) {
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                        releaseAlertSound();
                    }
                }
            };

    private final LocationListener cameraLocationListener = new LocationListener() {
        @Override public void onLocationChanged(Location location) {
            handleCameraLocation(location);
        }

        @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
        @Override public void onProviderEnabled(String provider) { }
        @Override public void onProviderDisabled(String provider) { }
    };

    private final BroadcastReceiver locationUpdateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null
                    || !CameraLocationService.ACTION_LOCATION_UPDATE.equals(intent.getAction())) return;
            Location location = intent.getParcelableExtra(CameraLocationService.EXTRA_LOCATION);
            handleCameraLocation(location);
        }
    };
    private final BroadcastReceiver yandexGuidanceReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            long nowElapsedMs = SystemClock.elapsedRealtime();
            if (NavigatorRouteFreshnessPolicy.isExpired(hasYandexNavigationState(),
                    lastYandexNavigationUpdateElapsedMs, nowElapsedMs)) {
                clearYandexNavigationState("Stale Navigator route cleared before new update");
            }
            lastYandexNavigationUpdateElapsedMs = nowElapsedMs;
            if (YANDEX_MANEUVER_VISIBILITY_ACTION.equals(action)) {
                yandexManeuverVisible = intent.getBooleanExtra("Visible", false);
                updateNavigationGuidanceViews();
                return;
            }
            if (YANDEX_NAV_MAP_ACTION.equals(action)) {
                String type = stringExtra(intent, "type");
                if ("route".equals(type)) {
                    if (intent.getBooleanExtra("clear", false)) {
                        yandexRoutePoints = "";
                        if (overlayMapView != null) overlayMapView.clearNavigatorRoute();
                    } else if (intent.hasExtra("points")) {
                        yandexRoutePoints = stringExtra(intent, "points");
                        if (overlayMapView != null) {
                            overlayMapView.updateNavigatorRoute(yandexRoutePoints);
                        }
                    }
                } else if ("position".equals(type)
                        && intent.hasExtra("lat") && intent.hasExtra("lon")) {
                    double latitude = intent.getDoubleExtra("lat", Double.NaN);
                    double longitude = intent.getDoubleExtra("lon", Double.NaN);
                    Object heading = intent.getExtras() == null
                            ? null : intent.getExtras().get("heading");
                    float parsedHeading;
                    try {
                        parsedHeading = heading == null
                                ? Float.NaN : Float.parseFloat(String.valueOf(heading));
                    } catch (NumberFormatException ignored) {
                        parsedHeading = Float.NaN;
                    }
                    if (isValidMapPosition(latitude, longitude)) {
                        yandexNavigationLatitude = latitude;
                        yandexNavigationLongitude = longitude;
                        yandexNavigationHeading = parsedHeading;
                        lastYandexPositionUpdateElapsedMs = nowElapsedMs;
                    }
                    if (overlayMapView != null
                            && isValidMapPosition(latitude, longitude)) {
                        overlayMapView.updateNavigatorPosition(yandexNavigationLatitude,
                                yandexNavigationLongitude, yandexNavigationHeading);
                    }
                }
                if (intent.getBooleanExtra("clear", false)) {
                    clearYandexNavigationState("Navigator route cleared");
                    return;
                } else {
                    if (intent.hasExtra("distanceToFinish")) {
                        yandexRouteDistanceMeters = Math.max(0,
                                (int) Math.round(intent.getDoubleExtra("distanceToFinish", 0d)));
                    }
                    if (intent.hasExtra("timeToFinish")) {
                        yandexRouteTimeSeconds = Math.max(0,
                                (int) Math.round(intent.getDoubleExtra("timeToFinish", 0d)));
                    }
                }
                updateNavigationGuidanceViews();
                return;
            }
            if (!YANDEX_MANEUVER_ACTION.equals(action)) return;
            if (intent.hasExtra("resourceId")) {
                yandexManeuverResourceId = stringExtra(intent, "resourceId");
            }
            if (intent.hasExtra("nextRoadName")) {
                yandexNextRoadName = stringExtra(intent, "nextRoadName");
            }
            if (intent.hasExtra("distance") || intent.hasExtra("unit")) {
                yandexManeuverDistanceMeters = parseYandexDistanceMeters(
                        stringExtra(intent, "distance"), stringExtra(intent, "unit"));
                yandexManeuverVisible = true;
            }
            if (intent.hasExtra("distanceleft")) {
                yandexRouteDistanceMeters = parseYandexDistanceMeters(
                        stringExtra(intent, "distanceleft"), "m");
            }
            if (intent.hasExtra("resourceId") || intent.hasExtra("nextRoadName")
                    || intent.hasExtra("distance")) {
                Log.i(TAG, "Yandex guidance received for instrument overlay; "
                        + "persistent HUD forwarding is owned by FakeTeleNav");
            }
            updateNavigationGuidanceViews();
        }
    };
    private final Runnable cameraLocationWatchdog = new Runnable() {
        @Override public void run() {
            if (!isAwdEnabled()) return;
            long now = SystemClock.elapsedRealtime();
            if (lastLocationUpdateElapsedMs > 0L
                    && now - lastLocationUpdateElapsedMs > CAMERA_LOCATION_STALE_MS) {
                if (activeCamera != null) {
                    append("Camera alert cleared: location updates are stale");
                    clearActiveCamera();
                }
                lastLocation = null;
                courseAnchorLocation = null;
                lastLocationUpdateElapsedMs = 0L;
            }
            overlayHandler.postDelayed(this, CAMERA_LOCATION_WATCHDOG_MS);
        }
    };
    private final Runnable navigatorRouteFreshnessWatchdog = new Runnable() {
        @Override public void run() {
            if (NavigatorRouteFreshnessPolicy.isExpired(hasYandexNavigationState(),
                    lastYandexNavigationUpdateElapsedMs, SystemClock.elapsedRealtime())) {
                clearYandexNavigationState("Navigator route updates timed out");
            }
            overlayHandler.postDelayed(this,
                    NavigatorRouteFreshnessPolicy.WATCHDOG_INTERVAL_MS);
        }
    };

    private final ServiceConnection ipkConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            ipkBinder = service;
            ipkBound = true;
            ipkBindingRequested = false;
            append("IPKService подключён: " + name.flattenToShortString());
            registerIpkNaviCallback();
            registerIpkKeyCallback();
            flushPendingNavigationMode();
            syncFakeTeleNavNavigationOutputs();
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            ipkBinder = null;
            ipkBound = false;
            ipkBindingRequested = false;
            ipkNaviCallbackRegistered = false;
            ipkKeyCallbackRegistered = false;
            append("IPKService отключён");
        }
    };

    private final ServiceConnection carConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            append("CarService подключён: " + name.flattenToShortString());
            subscribeToAwdProperties();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            vendorManager = null;
            vendorCallback = null;
            append("CarService отключён");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activeInstance = this;
        demoFrontPerWheel = getIntent().getIntExtra("demo_front", -1);
        demoRearPerWheel = getIntent().getIntExtra("demo_rear", -1);
        liveFullOverlay = isAwdEnabled();
        registerReceiver(locationUpdateReceiver,
                new IntentFilter(CameraLocationService.ACTION_LOCATION_UPDATE));
        locationReceiverRegistered = true;
        IntentFilter guidanceFilter = new IntentFilter();
        guidanceFilter.addAction(YANDEX_MANEUVER_ACTION);
        guidanceFilter.addAction(YANDEX_MANEUVER_VISIBILITY_ACTION);
        guidanceFilter.addAction(YANDEX_NAV_MAP_ACTION);
        registerReceiver(yandexGuidanceReceiver, guidanceFilter);
        yandexGuidanceReceiverRegistered = true;
        overlayHandler.removeCallbacks(navigatorRouteFreshnessWatchdog);
        overlayHandler.postDelayed(navigatorRouteFreshnessWatchdog,
                NavigatorRouteFreshnessPolicy.WATCHDOG_INTERVAL_MS);
        buildControlUi();
        registerInstrumentModeRpcProbe();
        refreshBatteryInfo();
        loadCameraDatabaseAsync(false);
        handleControlIntent(getIntent());
        if (getIntent().getBooleanExtra("boot_autostart", false)) {
            overlayHandler.postDelayed(new Runnable() {
                @Override public void run() { moveTaskToBack(true); }
            }, 1800L);
        }
    }

    /**
     * The cluster mode button is not exposed through Android KeyEvent or the
     * IPK key callback on this firmware.  It is present on the shared vehicle
     * RPC hard-key stream as opcode 1296, key 76.  Keep this build passive:
     * log both actions first, then enable behavior only after an in-car test.
     */
    private void registerInstrumentModeRpcProbe() {
        if (instrumentModeRpcListener != null) {
            Log.i(TAG, "Instrument mode RPC probe already registered");
            return;
        }
        try {
            final Class<?> managerClass = Class.forName("alfusos.rpc.RpcManager");
            final Class<?> listenerClass =
                    Class.forName("alfusos.rpc.RpcManager$OnRpcListener");
            final Object manager = managerClass.getMethod("getInstance").invoke(null);
            ClassLoader loader = listenerClass.getClassLoader();
            if (loader == null) loader = MainActivity.class.getClassLoader();
            final Object listener = Proxy.newProxyInstance(loader,
                    new Class<?>[]{listenerClass}, new InvocationHandler() {
                @Override public Object invoke(Object proxy, Method method, Object[] args) {
                    if ("onCallback".equals(method.getName()) && args != null
                            && args.length >= 2 && args[0] instanceof Integer
                            && args[1] instanceof byte[]) {
                        final int opcode = (Integer) args[0];
                        final byte[] packet = (byte[]) args[1];
                        if (opcode == RPC_HARD_KEY_EVENT && packet.length >= 2) {
                            final int keyCode = packet[0] & 0xff;
                            final int action = packet[1] & 0xff;
                            if (keyCode == INSTRUMENT_MODE_KEY_CODE) {
                                Log.i(TAG, "Instrument mode RPC key=" + keyCode
                                        + " action=" + action
                                        + " packet=" + Arrays.toString(packet));
                                if (action == 2) {
                                    final MainActivity activity = activeInstance;
                                    if (activity != null) {
                                        activity.overlayHandler.post(new Runnable() {
                                            @Override public void run() {
                                                activity.toggleInstrumentMapFromModeKey();
                                            }
                                        });
                                    }
                                }
                            }
                        }
                        return null;
                    }
                    if ("toString".equals(method.getName())) {
                        return "InstrumentModeRpcListener";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return args != null && args.length == 1 && proxy == args[0];
                    }
                    return null;
                }
            });
            managerClass.getMethod("registerListener", int.class, byte.class, listenerClass)
                    .invoke(manager, RPC_HARD_KEY_EVENT, (byte) -1, listener);
            instrumentModeRpcListener = listener;
            Log.i(TAG, "Instrument mode RPC probe registered: opcode="
                    + RPC_HARD_KEY_EVENT + " key=" + INSTRUMENT_MODE_KEY_CODE);
        } catch (Throwable error) {
            Log.w(TAG, "Instrument mode RPC probe registration failed", error);
        }
    }

    private void buildControlUi() {
        int pad = dp(32);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.rgb(242, 241, 238));

        Window window = getWindow();
        if (window != null) {
            window.setStatusBarColor(Color.rgb(28, 29, 28));
            window.setNavigationBarColor(Color.rgb(28, 29, 28));
        }

        TextView title = new TextView(this);
        title.setText("Дорожный ассистент");
        title.setTextColor(Color.rgb(31, 32, 31));
        title.setTextSize(32f);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(18), 0, dp(8));
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView notice = new TextView(this);
        notice.setText("Камеры, карта и визуализация полного привода");
        notice.setTextColor(Color.rgb(104, 103, 98));
        notice.setTextSize(17f);
        notice.setGravity(Gravity.CENTER);
        notice.setPadding(0, 0, 0, dp(28));
        root.addView(notice, matchWrap());

        controlStatusView = new TextView(this);
        controlStatusView.setTextSize(20f);
        controlStatusView.setGravity(Gravity.CENTER);
        controlStatusView.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.setMargins(0, 0, 0, dp(24));
        root.addView(controlStatusView, statusParams);

        enableButton = createControlButton("Включить на приборной панели",
                Color.rgb(217, 154, 85), Color.WHITE);
        enableButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { enableAwdDisplay(); }
        });
        root.addView(enableButton, controlButtonParams());

        disableButton = createControlButton("Выключить",
                Color.rgb(82, 81, 77), Color.WHITE);
        disableButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { disableAwdDisplay(); }
        });
        root.addView(disableButton, controlButtonParams());

        batteryInfoView = new TextView(this);
        batteryInfoView.setText("12 В: чтение…");
        batteryInfoView.setTextSize(18f);
        batteryInfoView.setTextColor(Color.rgb(52, 52, 50));
        batteryInfoView.setGravity(Gravity.CENTER);
        batteryInfoView.setPadding(0, dp(4), 0, dp(8));
        root.addView(batteryInfoView, matchWrap());

        Button batteryRefreshButton = createControlButton("Обновить данные батареи",
                Color.rgb(116, 107, 95), Color.WHITE);
        batteryRefreshButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { refreshBatteryInfo(); }
        });
        root.addView(batteryRefreshButton, controlButtonParams());

        final SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        autostartCheck = new Switch(this);
        autostartCheck.setText("Автозапуск при включении автомобиля");
        autostartCheck.setTextSize(21f);
        autostartCheck.setTextColor(Color.rgb(52, 52, 50));
        autostartCheck.setPadding(0, dp(4), 0, dp(8));
        autostartCheck.setChecked(preferences.getBoolean(PREF_AUTOSTART, false));
        autostartCheck.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putBoolean(PREF_AUTOSTART, autostartCheck.isChecked()).apply();
            }
        });
        addSettingsCheckBox(root, autostartCheck);

        TextView homeScreenTitle = new TextView(this);
        homeScreenTitle.setText("Главный экран и Яндекс Музыка");
        homeScreenTitle.setTextColor(Color.rgb(31, 32, 31));
        homeScreenTitle.setTextSize(26f);
        homeScreenTitle.setPadding(0, dp(18), 0, dp(8));
        root.addView(homeScreenTitle, matchWrap());

        mediaBridgeStatusView = new TextView(this);
        mediaBridgeStatusView.setText(isMediaBridgeInstalled()
                ? "Media Bridge установлен; отдельная иконка скрыта"
                : "Media Bridge не установлен");
        mediaBridgeStatusView.setTextSize(18f);
        mediaBridgeStatusView.setTextColor(Color.rgb(104, 103, 98));
        mediaBridgeStatusView.setPadding(0, 0, 0, dp(6));
        root.addView(mediaBridgeStatusView, matchWrap());

        mediaBridgeAutoPlayCheck = new Switch(this);
        mediaBridgeAutoPlayCheck.setText("Запускать Яндекс Музыку после загрузки");
        mediaBridgeAutoPlayCheck.setTextSize(18f);
        mediaBridgeAutoPlayCheck.setTextColor(Color.rgb(52, 52, 50));
        mediaBridgeAutoPlayCheck.setChecked(preferences.getBoolean(
                PREF_MEDIA_BRIDGE_AUTO_PLAY, true));
        mediaBridgeAutoPlayCheck.setEnabled(isMediaBridgeInstalled());
        mediaBridgeAutoPlayCheck.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                boolean enabled = mediaBridgeAutoPlayCheck.isChecked();
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putBoolean(PREF_MEDIA_BRIDGE_AUTO_PLAY, enabled).apply();
                sendMediaBridgeControl(MEDIA_BRIDGE_ACTION_SET_AUTO_PLAY, enabled);
            }
        });
        addSettingsCheckBox(root, mediaBridgeAutoPlayCheck);

        mediaBridgeRouteCardCheck = new Switch(this);
        mediaBridgeRouteCardCheck.setText("Показывать активный маршрут на главном экране");
        mediaBridgeRouteCardCheck.setTextSize(18f);
        mediaBridgeRouteCardCheck.setTextColor(Color.rgb(52, 52, 50));
        mediaBridgeRouteCardCheck.setChecked(preferences.getBoolean(
                PREF_MEDIA_BRIDGE_ROUTE_CARD, true));
        mediaBridgeRouteCardCheck.setEnabled(isMediaBridgeInstalled());
        mediaBridgeRouteCardCheck.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                boolean enabled = mediaBridgeRouteCardCheck.isChecked();
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putBoolean(PREF_MEDIA_BRIDGE_ROUTE_CARD, enabled).apply();
                sendMediaBridgeControl(MEDIA_BRIDGE_ACTION_SET_ROUTE_CARD, enabled);
            }
        });
        addSettingsCheckBox(root, mediaBridgeRouteCardCheck);

        mediaBridgeNavigationControlsCheck = new Switch(this);
        mediaBridgeNavigationControlsCheck.setText(
                "Углы «Назад» и «Домой» поверх Навигатора (вместо JCarTools)");
        mediaBridgeNavigationControlsCheck.setTextSize(18f);
        mediaBridgeNavigationControlsCheck.setTextColor(Color.rgb(52, 52, 50));
        mediaBridgeNavigationControlsCheck.setChecked(preferences.getBoolean(
                PREF_MEDIA_BRIDGE_NAV_CONTROLS, false));
        mediaBridgeNavigationControlsCheck.setEnabled(isMediaBridgeInstalled());
        mediaBridgeNavigationControlsCheck.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                boolean enabled = mediaBridgeNavigationControlsCheck.isChecked();
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putBoolean(PREF_MEDIA_BRIDGE_NAV_CONTROLS, enabled).apply();
                sendMediaBridgeControl(MEDIA_BRIDGE_ACTION_SET_NAV_CONTROLS, enabled);
            }
        });
        addSettingsCheckBox(root, mediaBridgeNavigationControlsCheck);

        TextView mediaBridgeServiceNote = new TextView(this);
        mediaBridgeServiceNote.setText(
                "Служебное восстановление (обычно не требуется после установки)");
        mediaBridgeServiceNote.setTextSize(18f);
        mediaBridgeServiceNote.setTextColor(Color.rgb(104, 103, 98));
        mediaBridgeServiceNote.setPadding(0, dp(14), 0, dp(4));
        root.addView(mediaBridgeServiceNote, matchWrap());

        Button mediaBridgeStartButton = createControlButton(
                "Перезапустить фоновый Media Bridge",
                Color.rgb(116, 107, 95), Color.WHITE);
        mediaBridgeStartButton.setEnabled(isMediaBridgeInstalled());
        mediaBridgeStartButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                sendMediaBridgeControl(MEDIA_BRIDGE_ACTION_START, null);
            }
        });
        root.addView(mediaBridgeStartButton, controlButtonParams());

        Button notificationAccessButton = createControlButton(
                "Доступ к уведомлениям (управление музыкой)",
                Color.rgb(116, 107, 95), Color.WHITE);
        notificationAccessButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                openSystemSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            }
        });
        root.addView(notificationAccessButton, controlButtonParams());

        Button accessibilityButton = createControlButton(
                "Спецвозможности (маршрут и кнопки Навигатора)",
                Color.rgb(116, 107, 95), Color.WHITE);
        accessibilityButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                openSystemSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            }
        });
        root.addView(accessibilityButton, controlButtonParams());

        if (isMediaBridgeInstalled()) {
            sendMediaBridgeControl(MEDIA_BRIDGE_ACTION_SET_AUTO_PLAY,
                    mediaBridgeAutoPlayCheck.isChecked());
            sendMediaBridgeControl(MEDIA_BRIDGE_ACTION_SET_ROUTE_CARD,
                    mediaBridgeRouteCardCheck.isChecked());
            sendMediaBridgeControl(MEDIA_BRIDGE_ACTION_SET_NAV_CONTROLS,
                    mediaBridgeNavigationControlsCheck.isChecked());
        }

        TextView idleModeLabel = new TextView(this);
        idleModeLabel.setText("Режим малого виджета приборной панели");
        idleModeLabel.setTextSize(21f);
        idleModeLabel.setTextColor(Color.rgb(52, 52, 50));
        idleModeLabel.setPadding(0, dp(12), 0, dp(4));
        root.addView(idleModeLabel, matchWrap());

        idleModeSpinner = new Spinner(this);
        ArrayAdapter<String> idleModeAdapter = createSettingsSpinnerAdapter(IDLE_MODE_NAMES);
        idleModeSpinner.setAdapter(idleModeAdapter);
        idleModeSpinner.setSelection(idleModeSelectionForValue(
                preferences.getInt(PREF_IDLE_MODE, IDLE_MODE_AWD)));
        idleModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view,
                                                  int position, long id) {
                int selectedMode = IDLE_MODE_VALUES[Math.max(0,
                        Math.min(IDLE_MODE_VALUES.length - 1, position))];
                int storedMode = getSharedPreferences(PREFS, MODE_PRIVATE)
                        .getInt(PREF_IDLE_MODE, IDLE_MODE_AWD);
                // Spinner dispatches its current selection while the settings
                // screen is being built.  That is not a mode change and must
                // not refresh the already running instrument widget.
                if (!InstrumentOverlayLifecyclePolicy.isModeChange(
                        storedMode, selectedMode)) return;
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putInt(PREF_IDLE_MODE, selectedMode).apply();
                updateOverlayIdleMode();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        styleSettingsSpinner(idleModeSpinner);
        root.addView(idleModeSpinner, settingsControlParams());

        navigationHudCheck = new Switch(this);
        navigationHudCheck.setText("Манёвры навигатора на HUD");
        navigationHudCheck.setTextSize(18f);
        navigationHudCheck.setTextColor(Color.rgb(52, 52, 50));
        navigationHudCheck.setChecked(preferences.getBoolean(PREF_NAVIGATION_HUD, true));
        navigationHudCheck.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putBoolean(PREF_NAVIGATION_HUD, navigationHudCheck.isChecked()).apply();
                syncFakeTeleNavNavigationOutputs();
            }
        });
        addSettingsCheckBox(root, navigationHudCheck);

        instrumentTbtCheck = new Switch(this);
        instrumentTbtCheck.setText("Манёвры на приборной панели");
        instrumentTbtCheck.setTextSize(18f);
        instrumentTbtCheck.setTextColor(Color.rgb(52, 52, 50));
        instrumentTbtCheck.setChecked(preferences.getBoolean(PREF_INSTRUMENT_TBT, true));
        instrumentTbtCheck.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putBoolean(PREF_INSTRUMENT_TBT, instrumentTbtCheck.isChecked()).apply();
                syncFakeTeleNavNavigationOutputs();
            }
        });
        addSettingsCheckBox(root, instrumentTbtCheck);

        TextView clusterManeuverPositionLabel = new TextView(this);
        clusterManeuverPositionLabel.setText("Карточка манёвра в полноэкранной карте");
        clusterManeuverPositionLabel.setTextSize(21f);
        clusterManeuverPositionLabel.setTextColor(Color.rgb(52, 52, 50));
        clusterManeuverPositionLabel.setPadding(0, dp(10), 0, dp(4));
        root.addView(clusterManeuverPositionLabel, matchWrap());

        clusterManeuverPositionSpinner = new Spinner(this);
        ArrayAdapter<String> clusterPositionAdapter =
                createSettingsSpinnerAdapter(CLUSTER_MANEUVER_POSITION_NAMES);
        clusterManeuverPositionSpinner.setAdapter(clusterPositionAdapter);
        clusterManeuverPositionSpinner.setSelection(getClusterManeuverPosition());
        clusterManeuverPositionSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view,
                                                  int position, long id) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putInt(PREF_CLUSTER_MANEUVER_POSITION, position).apply();
                updateClusterGuidanceOverlay();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        styleSettingsSpinner(clusterManeuverPositionSpinner);
        root.addView(clusterManeuverPositionSpinner, settingsControlParams());

        TextView cameraTitle = new TextView(this);
        cameraTitle.setText("Предупреждения о камерах");
        cameraTitle.setTextColor(Color.rgb(31, 32, 31));
        cameraTitle.setTextSize(26f);
        cameraTitle.setPadding(0, dp(18), 0, dp(8));
        root.addView(cameraTitle, matchWrap());

        cameraAudioCheck = new Switch(this);
        cameraAudioCheck.setText("Звуковое предупреждение");
        cameraAudioCheck.setTextSize(18f);
        cameraAudioCheck.setTextColor(Color.rgb(52, 52, 50));
        cameraAudioCheck.setChecked(preferences.getBoolean(PREF_CAMERA_AUDIO, true));
        cameraAudioCheck.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putBoolean(PREF_CAMERA_AUDIO, cameraAudioCheck.isChecked()).apply();
                if (!cameraAudioCheck.isChecked()) releaseAlertSound();
            }
        });
        addSettingsCheckBox(root, cameraAudioCheck);

        cameraHudCheck = new Switch(this);
        final boolean hudAvailable = isHudOutputAvailable();
        if (!hudAvailable) {
            preferences.edit().putBoolean(PREF_CAMERA_HUD, false).apply();
        }
        cameraHudCheck.setText(hudAvailable
                ? "Предупреждения о камерах на HUD"
                : "HUD отключён в этой сборке");
        cameraHudCheck.setTextSize(18f);
        cameraHudCheck.setTextColor(Color.rgb(52, 52, 50));
        cameraHudCheck.setChecked(hudAvailable
                && preferences.getBoolean(PREF_CAMERA_HUD, false));
        cameraHudCheck.setEnabled(hudAvailable);
        cameraHudCheck.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                boolean enabled = cameraHudCheck.isChecked();
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putBoolean(PREF_CAMERA_HUD, enabled).apply();
                if (!enabled) clearCameraHudAlert();
                else if (activeCamera != null && overlayView != null) {
                    sendCameraHudAlert(activeCamera,
                            Math.round(overlayView.alertDistanceMeters));
                }
            }
        });
        addSettingsCheckBox(root, cameraHudCheck);

        cameraGpsGuardCheck = new Switch(this);
        cameraGpsGuardCheck.setText("Защита GPS от скачков (пробный режим)");
        cameraGpsGuardCheck.setTextSize(18f);
        cameraGpsGuardCheck.setTextColor(Color.rgb(52, 52, 50));
        cameraGpsGuardCheck.setChecked(preferences.getBoolean(
                PREF_CAMERA_GPS_GUARD, false));
        cameraGpsGuardCheck.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                boolean enabled = cameraGpsGuardCheck.isChecked();
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putBoolean(PREF_CAMERA_GPS_GUARD, enabled).apply();
                locationGuard.reset();
                lastLocationGuardLogElapsedMs = 0L;
                append(enabled
                        ? "GPS Guard включён: скачки и невалидное время будут отбрасываться"
                        : "GPS Guard выключен: используется исходный поток GPS");
            }
        });
        addSettingsCheckBox(root, cameraGpsGuardCheck);

        phoneGpsCheck = new Switch(this);
        phoneGpsCheck.setText("Использовать GPS телефона по Wi‑Fi");
        phoneGpsCheck.setChecked(preferences.getBoolean(PREF_PHONE_GPS_ENABLED, false));
        phoneGpsCheck.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                boolean enabled = phoneGpsCheck.isChecked();
                if (enabled) ensurePhoneGpsPairingToken(false);
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putBoolean(PREF_PHONE_GPS_ENABLED, enabled).apply();
                resetPhoneGpsSelection();
                refreshPhoneGpsSource();
                updatePhoneGpsStatus();
                append(enabled
                        ? "GPS телефона включён: ожидание пакетов по Wi‑Fi"
                        : "GPS телефона выключен: используется GPS автомобиля");
            }
        });
        addSettingsCheckBox(root, phoneGpsCheck);

        phoneGpsStatusView = new TextView(this);
        phoneGpsStatusView.setTextSize(16f);
        phoneGpsStatusView.setTextColor(Color.rgb(82, 79, 74));
        phoneGpsStatusView.setPadding(dp(18), 0, dp(18), dp(8));
        root.addView(phoneGpsStatusView, matchWrap());
        Button renewPhoneGpsCode = createControlButton("Новый код для телефона",
                Color.rgb(105, 103, 98), Color.WHITE);
        renewPhoneGpsCode.setTextSize(18f);
        renewPhoneGpsCode.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                String code = ensurePhoneGpsPairingToken(true);
                resetPhoneGpsSelection();
                refreshPhoneGpsSource();
                updatePhoneGpsStatus();
                append("Создан новый код GPS телефона: " + code);
            }
        });
        root.addView(renewPhoneGpsCode, matchWrap());
        updatePhoneGpsStatus();

        TextView warningModeLabel = new TextView(this);
        warningModeLabel.setText("Когда предупреждать о скоростных камерах");
        warningModeLabel.setTextSize(21f);
        warningModeLabel.setTextColor(Color.rgb(52, 52, 50));
        warningModeLabel.setPadding(0, dp(10), 0, dp(4));
        root.addView(warningModeLabel, matchWrap());

        cameraWarningModeSpinner = new Spinner(this);
        ArrayAdapter<String> warningModeAdapter =
                createSettingsSpinnerAdapter(CAMERA_WARNING_MODE_NAMES);
        cameraWarningModeSpinner.setAdapter(warningModeAdapter);
        cameraWarningModeSpinner.setSelection(Math.max(0, Math.min(
                CAMERA_WARNING_MODE_NAMES.length - 1,
                preferences.getInt(PREF_CAMERA_WARNING_MODE, CAMERA_WARNING_ALWAYS))));
        cameraWarningModeSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view,
                                                  int position, long id) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putInt(PREF_CAMERA_WARNING_MODE, position).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        styleSettingsSpinner(cameraWarningModeSpinner);
        root.addView(cameraWarningModeSpinner, settingsControlParams());

        TextView cameraSoundLabel = new TextView(this);
        cameraSoundLabel.setText("Звук предупреждения");
        cameraSoundLabel.setTextSize(21f);
        cameraSoundLabel.setTextColor(Color.rgb(52, 52, 50));
        cameraSoundLabel.setPadding(0, dp(10), 0, dp(4));
        root.addView(cameraSoundLabel, matchWrap());

        cameraSoundSpinner = new Spinner(this);
        ArrayAdapter<String> soundAdapter = createSettingsSpinnerAdapter(CAMERA_SOUND_NAMES);
        cameraSoundSpinner.setAdapter(soundAdapter);
        cameraSoundSpinner.setSelection(Math.max(0, Math.min(
                CAMERA_SOUND_NAMES.length - 1,
                preferences.getInt(PREF_CAMERA_SOUND, 0))));
        cameraSoundSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view,
                                                  int position, long id) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putInt(PREF_CAMERA_SOUND, position).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        styleSettingsSpinner(cameraSoundSpinner);
        root.addView(cameraSoundSpinner, settingsControlParams());

        cameraDistanceView = new TextView(this);
        cameraDistanceView.setTextSize(18f);
        cameraDistanceView.setTextColor(Color.rgb(52, 52, 50));
        root.addView(cameraDistanceView, matchWrap());

        updateDistanceLabel();

        cameraDatabaseView = new TextView(this);
        cameraDatabaseView.setText("База камер: загрузка…");
        cameraDatabaseView.setTextSize(16f);
        cameraDatabaseView.setTextColor(Color.rgb(104, 103, 98));
        cameraDatabaseView.setPadding(0, dp(8), 0, dp(8));
        root.addView(cameraDatabaseView, matchWrap());

        cameraUpdateButton = createControlButton("Обновить базу камер",
                Color.rgb(116, 107, 95), Color.WHITE);
        cameraUpdateButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { updateCameraDatabaseAsync(); }
        });
        root.addView(cameraUpdateButton, controlButtonParams());

        Button cameraTestButton = createControlButton("Проверить предупреждение",
                Color.rgb(196, 126, 75), Color.WHITE);
        cameraTestButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showDemoCameraAlert(); }
        });
        root.addView(cameraTestButton, controlButtonParams());

        logView = new TextView(this);
        logView.setVisibility(View.GONE);
        root.addView(logView, new LinearLayout.LayoutParams(1, 1));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root);
        setContentView(scroll);
        updateControlUi(isAwdEnabled(), null);
    }

    private Button createControlButton(String text, int background, int foreground) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(22f);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundTintList(ColorStateList.valueOf(background));
        button.setMinHeight(dp(76));
        return button;
    }

    private void addSettingsCheckBox(LinearLayout root, Switch checkBox) {
        checkBox.setTextSize(21f);
        checkBox.setTextColor(Color.rgb(52, 52, 50));
        checkBox.setMinHeight(dp(68));
        checkBox.setGravity(Gravity.CENTER_VERTICAL);
        checkBox.setPadding(dp(10), dp(8), dp(18), dp(8));
        checkBox.setShowText(false);
        checkBox.setSwitchMinWidth(dp(64));
        checkBox.setThumbTintList(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{-android.R.attr.state_enabled},
                        new int[]{}
                },
                new int[]{Color.WHITE, Color.rgb(210, 207, 201), Color.WHITE}));
        checkBox.setTrackTintList(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{-android.R.attr.state_enabled},
                        new int[]{}
                },
                new int[]{
                        Color.rgb(205, 137, 70),
                        Color.rgb(174, 171, 164),
                        Color.rgb(91, 89, 84)
                }));

        // The row spans the safe settings column, but only the switch and its
        // caption are clickable. Swiping the empty part can therefore scroll
        // without toggling an option by accident.
        FrameLayout row = new FrameLayout(this);
        row.setClickable(false);
        FrameLayout.LayoutParams optionParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.CENTER_VERTICAL);
        row.addView(checkBox, optionParams);
        LinearLayout.LayoutParams rowParams = settingsControlParams();
        rowParams.height = LinearLayout.LayoutParams.WRAP_CONTENT;
        root.addView(row, rowParams);
    }

    private ArrayAdapter<String> createSettingsSpinnerAdapter(String[] values) {
        return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, values) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                styleSpinnerText(view, false);
                return view;
            }

            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                styleSpinnerText(view, true);
                return view;
            }
        };
    }

    private void styleSpinnerText(TextView view, boolean dropDown) {
        view.setTextSize(21f);
        view.setTextColor(Color.rgb(42, 42, 40));
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setMinHeight(dp(dropDown ? 72 : 68));
        view.setPadding(dp(18), dp(12), dp(18), dp(12));
    }

    private void styleSettingsSpinner(Spinner spinner) {
        spinner.setMinimumHeight(dp(72));
        spinner.setPadding(dp(4), dp(2), dp(4), dp(2));
        spinner.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(116, 107, 95)));
    }

    private int idleModeSelectionForValue(int storedValue) {
        for (int index = 0; index < IDLE_MODE_VALUES.length; index++) {
            if (IDLE_MODE_VALUES[index] == storedValue) return index;
        }
        return 0;
    }

    private boolean isMediaBridgeInstalled() {
        try {
            getPackageManager().getPackageInfo(MEDIA_BRIDGE_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private void sendMediaBridgeControl(String action, Boolean enabledValue) {
        if (!isMediaBridgeInstalled()) {
            if (mediaBridgeStatusView != null) {
                mediaBridgeStatusView.setText("Media Bridge не установлен");
            }
            return;
        }
        Intent intent = new Intent(action);
        intent.setComponent(MEDIA_BRIDGE_CONTROL_RECEIVER);
        if (enabledValue != null) {
            if (MEDIA_BRIDGE_ACTION_SET_ROUTE_CARD.equals(action)) {
                intent.putExtra(MEDIA_BRIDGE_EXTRA_ROUTE_CARD,
                        enabledValue.booleanValue());
            } else if (MEDIA_BRIDGE_ACTION_SET_NAV_CONTROLS.equals(action)) {
                intent.putExtra(MEDIA_BRIDGE_EXTRA_NAV_CONTROLS,
                        enabledValue.booleanValue());
            } else {
                intent.putExtra(MEDIA_BRIDGE_EXTRA_AUTO_PLAY,
                        enabledValue.booleanValue());
            }
        }
        try {
            sendBroadcast(intent);
            if (mediaBridgeStatusView != null) {
                mediaBridgeStatusView.setText("Media Bridge активирован из Дорожного ассистента");
            }
        } catch (RuntimeException error) {
            Log.e(TAG, "Media Bridge control failed", error);
            if (mediaBridgeStatusView != null) {
                mediaBridgeStatusView.setText("Не удалось запустить Media Bridge");
            }
        }
    }

    private void openSystemSettings(String action) {
        try {
            startActivity(new Intent(action));
        } catch (RuntimeException error) {
            Log.e(TAG, "Cannot open system settings: " + action, error);
        }
    }

    private void refreshBatteryInfo() {
        if (batteryInfoView == null) return;
        batteryInfoView.setText("12 В: чтение…");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final BatteryRpcReader.Result result = BatteryRpcReader.read();
                    Log.i(TAG, "BATTERY_RPC OK " + result.diagnosticText());
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (batteryInfoView != null) {
                                batteryInfoView.setText(result.displayText());
                            }
                        }
                    });
                } catch (final Throwable error) {
                    Log.e(TAG, "BATTERY_RPC ERROR", error);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (batteryInfoView != null) {
                                Throwable cause = error;
                                while (cause.getCause() != null) cause = cause.getCause();
                                batteryInfoView.setText("12 В: недоступно ("
                                        + cause.getClass().getSimpleName() + ")");
                            }
                        }
                    });
                }
            }
        }, "BatteryRpcReader").start();
    }

    private LinearLayout.LayoutParams controlButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                settingsControlWidth(), dp(82));
        params.setMargins(0, 0, 0, dp(14));
        return params;
    }

    private LinearLayout.LayoutParams settingsControlParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                settingsControlWidth(), LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(12));
        return params;
    }

    private int settingsControlWidth() {
        int available = Math.max(dp(320),
                getResources().getDisplayMetrics().widthPixels - dp(96));
        return Math.min(available, dp(1100));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleControlIntent(intent);
    }

    private void handleControlIntent(Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (intent != null && intent.hasExtra("camera_hud_enabled")) {
            boolean enabled = isHudOutputAvailable()
                    && intent.getBooleanExtra("camera_hud_enabled", false);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean(PREF_CAMERA_HUD, enabled).apply();
            if (cameraHudCheck != null) cameraHudCheck.setChecked(enabled);
            if (!enabled) clearCameraHudAlert();
        }
        if (intent != null && intent.hasExtra("camera_warning_mode")) {
            int mode = Math.max(CAMERA_WARNING_ALWAYS, Math.min(
                    CAMERA_WARNING_OVERSPEED,
                    intent.getIntExtra("camera_warning_mode", CAMERA_WARNING_ALWAYS)));
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putInt(PREF_CAMERA_WARNING_MODE, mode).apply();
            if (cameraWarningModeSpinner != null) cameraWarningModeSpinner.setSelection(mode);
        }
        if (ACTION_SHOW_AWD.equals(action)
                || (intent != null && intent.getBooleanExtra("live_full_overlay", false))) {
            enableAwdDisplay();
        } else if (ACTION_HIDE_AWD.equals(action)) {
            disableAwdDisplay();
        } else if (isAwdEnabled()) {
            enableAwdDisplay();
        } else {
            updateControlUi(false, null);
        }
        if (intent != null && intent.getBooleanExtra("test_camera_alert", false)) {
            showDemoCameraAlert();
        }
    }

    private void enableAwdDisplay() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().putBoolean(PREF_ENABLED, true).apply();
        liveFullOverlay = true;
        boolean needsInstrumentRestore = InstrumentOverlayLifecyclePolicy.shouldRestore(
                true, instrumentMapFullScreenActive, isInstrumentOverlayAttached(),
                instrumentOverlayRestorePending);
        updateControlUi(true, needsInstrumentRestore ? "Включаем…" : "Включено");
        // These recovery operations are idempotent. Keep the car callbacks
        // alive after a stock-service restart, but do not re-send transaction
        // 3 or recreate the MapKit surface just because Settings was opened.
        connectToCarService();
        startCameraMonitoring();
        updateSteeringInputShortcut();
        ensureIpkBound();
        if (!needsInstrumentRestore) {
            Log.i(TAG, "AWD display already active or restore pending; "
                    + "settings opened without cluster refresh");
            return;
        }
        overlayHandler.removeCallbacks(showPersistentOverlay);
        instrumentOverlayRestorePending = true;
        requestNavigationMode(true);
        overlayHandler.postDelayed(showPersistentOverlay, 900L);
    }

    private void disableAwdDisplay() {
        boolean wasFullScreen = instrumentMapFullScreenActive;
        instrumentMapFullScreenActive = false;
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().putBoolean(PREF_ENABLED, false).apply();
        liveFullOverlay = false;
        overlayHandler.removeCallbacks(showPersistentOverlay);
        instrumentOverlayRestorePending = false;
        dismissInstrumentOverlay();
        dismissPresentation();
        disconnectFromCarService();
        stopCameraMonitoring();
        demoAlertActive = false;
        overlayHandler.removeCallbacks(clearDemoAlert);
        clearActiveCamera();
        releaseSteeringInputShortcut();
        requestNavigationMode(false);
        if (wasFullScreen) sendNeusoftNavigationState(false);
        syncFakeTeleNavNavigationOutputs(wasFullScreen);
        updateControlUi(false, "Выключено");
    }

    private boolean isAwdEnabled() {
        return getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(PREF_ENABLED, false);
    }

    private int getWarningDistanceMeters(Location location) {
        float speedKmh = location != null && location.hasSpeed()
                ? Math.max(0f, location.getSpeed() * 3.6f) : 0f;
        return speedKmh >= CAMERA_FAST_SPEED_KMH
                ? CAMERA_DISTANCE_FAST_METERS : CAMERA_DISTANCE_CITY_METERS;
    }

    private void updateDistanceLabel() {
        if (cameraDistanceView != null) {
            cameraDistanceView.setText(
                    "Дистанция: 400 м до 80 км/ч, 600 м при 80 км/ч и выше");
        }
    }

    private void updateControlUi(boolean enabled, String message) {
        if (controlStatusView == null) return;
        controlStatusView.setText(message != null
                ? message
                : enabled ? "Включено" : "Выключено");
        controlStatusView.setTextColor(enabled
                ? Color.rgb(132, 91, 48)
                : Color.rgb(92, 91, 87));
        if (enableButton != null) enableButton.setEnabled(!enabled);
        if (disableButton != null) disableButton.setEnabled(enabled);
    }

    private void requestNavigationMode(boolean enabled) {
        pendingNavigationMode = enabled ? 1 : 0;
        if (ipkBound && ipkBinder != null) {
            flushPendingNavigationMode();
            return;
        }
        ensureIpkBound();
    }

    /** Binds the stock service without queuing a navigation-state change. */
    private void ensureIpkBound() {
        if (ipkBound || ipkBinder != null || ipkBindingRequested) return;
        Intent intent = new Intent(IPK_ACTION);
        intent.setComponent(new ComponentName(IPK_PACKAGE, IPK_SERVICE));
        try {
            boolean requested = bindService(intent, ipkConnection, Context.BIND_AUTO_CREATE);
            ipkBindingRequested = requested;
            if (!requested) {
                setControlError("Не удалось подключиться к службе приборной панели");
            }
        } catch (Throwable error) {
            appendFailure("IPKService bind failed", error);
            setControlError("Служба приборной панели недоступна");
        }
    }

    private void flushPendingNavigationMode() {
        if (pendingNavigationMode < 0 || ipkBinder == null) return;
        boolean enabled = pendingNavigationMode == 1;
        if (sendNavigationStatus(enabled ? IPK_WIDGET_MAP_MODE : 0, enabled ? 3 : 5)) {
            pendingNavigationMode = -1;
            append(enabled ? "Transparent MINI navigation enabled" : "Navigation state restored");
        }
    }

    private boolean sendNavigationStatus(int mapMode, int state) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(IPK_DESCRIPTOR);
            data.writeInt(1);
            data.writeInt(state);
            data.writeInt(mapMode);
            boolean handled = ipkBinder.transact(
                    TRANSACTION_UPDATE_NAVI_INFO, data, reply, 0);
            if (!handled) return false;
            reply.readException();
            return true;
        } catch (SecurityException error) {
            appendFailure("IPKService access denied", error);
            setControlError("Нет доступа к приборной панели");
            return false;
        } catch (RemoteException error) {
            appendFailure("IPKService transaction failed", error);
            setControlError("Ошибка связи с приборной панелью");
            return false;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private static String stringExtra(Intent intent, String key) {
        try {
            String value = intent.getStringExtra(key);
            return value == null ? "" : value;
        } catch (Throwable ignored) {
            Object value = intent.getExtras() == null ? null : intent.getExtras().get(key);
            return value == null ? "" : String.valueOf(value);
        }
    }

    private static int parseYandexDistanceMeters(String distance, String unit) {
        String source = distance == null ? "" : distance.trim().toLowerCase(Locale.US);
        String normalizedUnit = unit == null ? "" : unit.trim().toLowerCase(Locale.US);
        String number = source.replace(',', '.').replaceAll("[^0-9.]", "");
        if (number.length() == 0) return 0;
        try {
            double value = Double.parseDouble(number);
            if (normalizedUnit.contains("km") || normalizedUnit.contains("км")
                    || source.contains("km") || source.contains("км")) {
                value *= 1000.0d;
            }
            return Math.max(0, (int) Math.round(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private boolean shouldEnableFakeTeleNavInstrumentTbt() {
        return getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(PREF_INSTRUMENT_TBT, true);
    }

    private void syncFakeTeleNavNavigationOutputs() {
        syncFakeTeleNavNavigationOutputs(instrumentMapFullScreenActive);
    }

    private void syncFakeTeleNavNavigationOutputs(boolean panelStateManagedExternally) {
        try {
            boolean hudEnabled = getSharedPreferences(PREFS, MODE_PRIVATE)
                    .getBoolean(PREF_NAVIGATION_HUD, true);
            boolean instrumentEnabled = shouldEnableFakeTeleNavInstrumentTbt();
            boolean instrumentSuppressed = instrumentMapFullScreenActive;
            Intent control = new Intent(TELENAV_ACTION).setComponent(TELENAV_RECEIVER);
            control.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES
                    | Intent.FLAG_RECEIVER_FOREGROUND);
            control.putExtra(TELENAV_EXTRA_HUD_ENABLED, hudEnabled);
            control.putExtra(TELENAV_EXTRA_PANEL_TBT_ENABLED, instrumentEnabled);
            control.putExtra(TELENAV_EXTRA_PANEL_TBT_SUPPRESSED, instrumentSuppressed);
            control.putExtra(TELENAV_EXTRA_PANEL_TBT_STATE_MANAGED_EXTERNALLY,
                    panelStateManagedExternally);
            sendBroadcast(control);
            Log.i(TAG, "FakeTeleNav outputs hud=" + hudEnabled
                    + " instrument=" + instrumentEnabled
                    + " suppressed=" + instrumentSuppressed
                    + " externalState=" + panelStateManagedExternally
                    + " routeWidget=" + shouldShowRouteIdle());
        } catch (Throwable error) {
            ipkBindingRequested = false;
            Log.w(TAG, "FakeTeleNav output sync failed", error);
        }
    }

    private boolean registerIpkNaviCallback() {
        if (ipkBinder == null || ipkNaviCallbackRegistered) return ipkNaviCallbackRegistered;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(IPK_DESCRIPTOR);
            data.writeStrongBinder(ipkNaviCallback);
            boolean handled = ipkBinder.transact(
                    TRANSACTION_REGISTER_NAVI_CALLBACK, data, reply, 0);
            if (!handled) return false;
            reply.readException();
            ipkNaviCallbackRegistered = true;
            append("IPK navigation callback зарегистрирован");
            return true;
        } catch (Throwable error) {
            appendFailure("IPK callback registration failed", error);
            return false;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private void unregisterIpkNaviCallback() {
        if (ipkBinder == null || !ipkNaviCallbackRegistered) return;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(IPK_DESCRIPTOR);
            data.writeStrongBinder(ipkNaviCallback);
            if (ipkBinder.transact(TRANSACTION_UNREGISTER_NAVI_CALLBACK, data, reply, 0)) {
                reply.readException();
            }
        } catch (Throwable error) {
            Log.w(TAG, "IPK callback unregister failed", error);
        } finally {
            ipkNaviCallbackRegistered = false;
            reply.recycle();
            data.recycle();
        }
    }

    private boolean registerIpkKeyCallback() {
        if (ipkBinder == null || ipkKeyCallbackRegistered) return ipkKeyCallbackRegistered;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(IPK_DESCRIPTOR);
            data.writeStrongBinder(ipkKeyCallback);
            boolean handled = ipkBinder.transact(
                    TRANSACTION_REGISTER_IPK_KEY_CALLBACK, data, reply, 0);
            if (!handled) return false;
            reply.readException();
            ipkKeyCallbackRegistered = true;
            append("IPK instrument-key probe registered");
            return true;
        } catch (Throwable error) {
            appendFailure("IPK instrument-key probe registration failed", error);
            return false;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private void unregisterIpkKeyCallback() {
        if (ipkBinder == null || !ipkKeyCallbackRegistered) return;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(IPK_DESCRIPTOR);
            data.writeStrongBinder(ipkKeyCallback);
            if (ipkBinder.transact(
                    TRANSACTION_UNREGISTER_IPK_KEY_CALLBACK, data, reply, 0)) {
                reply.readException();
            }
        } catch (Throwable error) {
            Log.w(TAG, "IPK instrument-key probe unregister failed", error);
        } finally {
            ipkKeyCallbackRegistered = false;
            reply.recycle();
            data.recycle();
        }
    }

    private void handleInstrumentNavigationRequest(int requestedMode) {
        append("IPK onChangeNavi(" + requestedMode + ")");
        if (!isAwdEnabled() || !shouldShowRouteIdle()) {
            append("Steering map launch ignored: instrument mode is not Route");
            return;
        }
        if (requestedMode == IPK_REQUEST_FULL_SCREEN) {
            launchYandexMapsOnInstrumentDisplay();
        } else if (requestedMode == IPK_REQUEST_MINI) {
            returnInstrumentMapToMini("IPK instrument request");
        }
    }

    private void launchYandexMapsOnInstrumentDisplay() {
        Display display = findInstrumentDisplay();
        if (display == null) {
            append("Yandex Navi launch failed: instrument display not found");
            return;
        }
        Intent maps = createYandexInstrumentIntent();
        if (maps == null) {
            append("Yandex Navi launch failed: compatible package is not installed");
            return;
        }
        maps.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            instrumentMapFullScreenActive = true;
            syncFakeTeleNavNavigationOutputs();
            hideInstrumentOverlayForFullScreen();
            sendNavigationStatus(2, 2);
            ActivityOptions options = ActivityOptions.makeBasic()
                    .setLaunchDisplayId(display.getDisplayId());
            startActivity(maps, options.toBundle());
            append("Yandex Navi cluster launched on displayId=" + display.getDisplayId());
            overlayHandler.postDelayed(new Runnable() {
                @Override public void run() {
                    applyYandexClusterLayout();
                    showClusterGuidanceOverlay();
                }
            }, 700L);
            // Maps can become the foreground task without taking ownership of our media
            // shortcut. Refresh our silent, focus-free playback after the task switch so
            // the second long press can bring the instrument panel back to MINI mode.
            overlayHandler.postDelayed(new Runnable() {
                @Override public void run() { refreshSteeringMediaPriority(); }
            }, 500L);
        } catch (Throwable error) {
            appendFailure("Yandex Navi cluster launch failed", error);
            returnInstrumentMapToMini("Yandex Navi launch recovery");
        }
    }

    private Intent createYandexInstrumentIntent() {
        try {
            getPackageManager().getPackageInfo(YANDEX_NAVI_PACKAGE, 0);
            return new Intent(YANDEX_CLUSTER_ACTION)
                    .setComponent(new ComponentName(
                            YANDEX_NAVI_PACKAGE, YANDEX_CLUSTER_ACTIVITY));
        } catch (PackageManager.NameNotFoundException ignored) {
            Log.w(TAG, "Yandex Navigator package is not installed");
        }
        return null;
    }

    private void applyYandexClusterLayout() {
        Intent layout = new Intent(YANDEX_CLUSTER_LAYOUT_ACTION)
                .setPackage(YANDEX_NAVI_PACKAGE);
        // b22 applies these values inside its cluster process. Keep route progress on
        // the left and move the speed group away from the instrument centre opening.
        layout.putExtra("area", 2);
        layout.putExtra("GS_leftMargin", 1760);
        layout.putExtra("GS_topMargin", 38);
        layout.putExtra("FC_leftMargin", 24);
        layout.putExtra("FC_bottomMargin", 18);
        sendBroadcast(layout);
        Log.i(TAG, "Yandex cluster layout profile sent");
    }

    private boolean isSteeringMapShortcutEnabled() {
        return ENABLE_STEERING_MEDIA_SHORTCUT && isAwdEnabled() && shouldShowRouteIdle();
    }

    private void toggleInstrumentMapFromModeKey() {
        if (instrumentMapFullScreenActive) {
            returnInstrumentMapToMini("Instrument mode key long press");
        } else if (isAwdEnabled() && shouldShowRouteIdle()) {
            append("Instrument mode key long press: launching Yandex Maps");
            launchYandexMapsOnInstrumentDisplay();
        } else {
            append("Instrument mode key long press ignored: widget or Map mode is disabled");
        }
    }

    private void toggleInstrumentMapFromSteering() {
        if (instrumentMapFullScreenActive) {
            returnInstrumentMapToMini("Steering O long press");
        } else if (isSteeringMapShortcutEnabled()) {
            launchYandexMapsOnInstrumentDisplay();
        }
    }

    private void returnInstrumentMapToMini(String source) {
        instrumentMapFullScreenActive = false;
        dismissClusterGuidanceOverlay();
        append(source + ": returning instrument panel to MINI mode");
        // Reproduce the verified off/on protocol sequence without destroying the
        // MapKit view.  Recreating the secondary-display SurfaceView after full
        // Yandex Maps left it attached but no longer producing fresh frames.
        sendNeusoftNavigationState(false);
        sendNavigationStatus(0, 5);
        overlayHandler.postDelayed(new Runnable() {
            @Override public void run() {
                sendNavigationStatus(IPK_WIDGET_MAP_MODE, 3);
                restoreInstrumentOverlayVisibility();
                syncFakeTeleNavNavigationOutputs(true);
                refreshLauncherRouteCard();
                append("Instrument MINI overlay restored without MapKit restart");
            }
        }, 500L);
    }

    private void refreshLauncherRouteCard() {
        boolean enabled = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(PREF_MEDIA_BRIDGE_ROUTE_CARD, true);
        sendMediaBridgeControl(MEDIA_BRIDGE_ACTION_SET_ROUTE_CARD, enabled);
    }

    private void hideInstrumentOverlayForFullScreen() {
        if (overlayRoot != null) {
            overlayRoot.setVisibility(View.INVISIBLE);
            append("Instrument MINI overlay hidden; MapKit kept alive");
        }
        if (overlayRouteGuidanceView != null) {
            overlayRouteGuidanceView.setVisibility(View.INVISIBLE);
        }
    }

    private void restoreInstrumentOverlayVisibility() {
        dismissClusterGuidanceOverlay();
        if (!isAwdEnabled()) return;
        if (overlayRoot == null) {
            showFullInstrumentOverlay();
            return;
        }
        overlayRoot.setVisibility(View.VISIBLE);
        overlayRoot.invalidate();
        if (overlayMapView != null) overlayMapView.invalidate();
        if (overlayRouteGuidanceView != null) {
            overlayRouteGuidanceView.setVisibility(View.VISIBLE);
            overlayRouteGuidanceView.invalidate();
        }
    }

    private void showClusterGuidanceOverlay() {
        if (!instrumentMapFullScreenActive) return;
        Display display = findInstrumentDisplay();
        if (display == null) return;
        if (clusterGuidanceView != null) {
            updateClusterGuidanceOverlay();
            return;
        }
        Context displayContext = createDisplayContext(display);
        clusterGuidanceWindowManager =
                (WindowManager) displayContext.getSystemService(Context.WINDOW_SERVICE);
        if (clusterGuidanceWindowManager == null) return;
        clusterGuidanceView = new ClusterGuidanceView(displayContext);
        int requestedWindowType = Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
                : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                requestedWindowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.setTitle("Yandex cluster guidance");
        try {
            clusterGuidanceWindowManager.addView(clusterGuidanceView, params);
            updateClusterGuidanceOverlay();
            Log.i(TAG, "Cluster guidance overlay attached to displayId="
                    + display.getDisplayId());
        } catch (Throwable error) {
            Log.w(TAG, "Cluster guidance overlay attach failed", error);
            clusterGuidanceView = null;
            clusterGuidanceWindowManager = null;
        }
    }

    private void dismissClusterGuidanceOverlay() {
        if (clusterGuidanceView != null && clusterGuidanceWindowManager != null) {
            try {
                clusterGuidanceWindowManager.removeViewImmediate(clusterGuidanceView);
            } catch (Throwable error) {
                Log.w(TAG, "Cluster guidance overlay removal failed", error);
            }
        }
        clusterGuidanceView = null;
        clusterGuidanceWindowManager = null;
    }

    private void updateNavigationGuidanceViews() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            overlayHandler.post(new Runnable() {
                @Override public void run() { updateNavigationGuidanceViews(); }
            });
            return;
        }
        if (overlayRouteGuidanceView != null) {
            overlayRouteGuidanceView.update(yandexManeuverResourceId, yandexNextRoadName,
                    yandexManeuverDistanceMeters, yandexRouteDistanceMeters,
                    yandexRouteTimeSeconds, yandexManeuverVisible,
                    !yandexRoutePoints.isEmpty());
        }
        updateClusterGuidanceOverlay();
    }

    private static boolean isValidMapPosition(double latitude, double longitude) {
        return !Double.isNaN(latitude) && !Double.isInfinite(latitude)
                && !Double.isNaN(longitude) && !Double.isInfinite(longitude)
                && latitude >= -90d && latitude <= 90d
                && longitude >= -180d && longitude <= 180d;
    }

    private void updateInstrumentMapFromAcceptedLocation(Location location, long nowElapsedMs) {
        if (overlayMapView == null || location == null
                || !InstrumentCameraPolicy.shouldUseFallbackPosition(
                lastYandexPositionUpdateElapsedMs, nowElapsedMs)) {
            return;
        }
        float heading = location.hasBearing() ? location.getBearing() : Float.NaN;
        overlayMapView.updateFallbackPosition(
                location.getLatitude(), location.getLongitude(), heading);
    }

    private void updateInstrumentMapFromBestPosition() {
        if (overlayMapView == null) return;
        long nowElapsedMs = SystemClock.elapsedRealtime();
        if (!InstrumentCameraPolicy.shouldUseFallbackPosition(
                lastYandexPositionUpdateElapsedMs, nowElapsedMs)
                && isValidMapPosition(yandexNavigationLatitude, yandexNavigationLongitude)) {
            overlayMapView.updateNavigatorPosition(yandexNavigationLatitude,
                    yandexNavigationLongitude, yandexNavigationHeading);
            return;
        }
        updateInstrumentMapFromAcceptedLocation(lastLocation, nowElapsedMs);
    }

    private boolean hasYandexNavigationState() {
        return !yandexRoutePoints.isEmpty()
                || !yandexManeuverResourceId.isEmpty()
                || !yandexNextRoadName.isEmpty()
                || yandexManeuverDistanceMeters > 0
                || yandexRouteDistanceMeters > 0
                || yandexRouteTimeSeconds > 0
                || yandexManeuverVisible;
    }

    private void clearYandexNavigationState(String reason) {
        yandexNextRoadName = "";
        yandexManeuverResourceId = "";
        yandexManeuverDistanceMeters = 0;
        yandexRouteDistanceMeters = 0;
        yandexRouteTimeSeconds = 0;
        yandexManeuverVisible = false;
        yandexRoutePoints = "";
        yandexNavigationLatitude = Double.NaN;
        yandexNavigationLongitude = Double.NaN;
        yandexNavigationHeading = Float.NaN;
        lastYandexPositionUpdateElapsedMs = 0L;
        lastYandexNavigationUpdateElapsedMs = 0L;
        if (overlayMapView != null) overlayMapView.clearNavigatorRoute();
        updateInstrumentMapFromAcceptedLocation(lastLocation, SystemClock.elapsedRealtime());
        updateNavigationGuidanceViews();
        Log.i(TAG, reason);
    }

    private void updateClusterGuidanceOverlay() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            overlayHandler.post(new Runnable() {
                @Override public void run() { updateClusterGuidanceOverlay(); }
            });
            return;
        }
        if (!instrumentMapFullScreenActive) return;
        if (clusterGuidanceView == null) {
            showClusterGuidanceOverlay();
            return;
        }
        clusterGuidanceView.update(yandexManeuverResourceId, yandexNextRoadName,
                yandexManeuverDistanceMeters, yandexRouteDistanceMeters,
                yandexRouteTimeSeconds, yandexManeuverVisible,
                getClusterManeuverPosition());
    }

    private int getClusterManeuverPosition() {
        int position = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(
                PREF_CLUSTER_MANEUVER_POSITION,
                CLUSTER_MANEUVER_POSITION_UPPER_INNER);
        return Math.max(0, Math.min(CLUSTER_MANEUVER_POSITION_NAMES.length - 1, position));
    }

    private void updateSteeringInputShortcut() {
        if (!isSteeringMapShortcutEnabled()) {
            releaseSteeringInputShortcut();
            return;
        }
        if (steeringMediaSession == null) {
            steeringMediaSession = new MediaSession(this, "InstrumentAwdSteering");
            steeringMediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                    | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
            steeringMediaSession.setCallback(new MediaSession.Callback() {
                @Override public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
                    KeyEvent event = mediaButtonIntent == null ? null
                            : mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                    if (event == null || (event.getKeyCode() != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                            && event.getKeyCode() != KeyEvent.KEYCODE_HEADSETHOOK)) {
                        return super.onMediaButtonEvent(mediaButtonIntent);
                    }
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        if (event.getRepeatCount() == 0) {
                            steeringButtonDownMs = event.getEventTime();
                            steeringLongPressHandled = false;
                        } else if (!steeringLongPressHandled
                                && steeringButtonDownMs > 0L
                                && event.getEventTime() - steeringButtonDownMs
                                >= STEERING_LONG_PRESS_MS) {
                            steeringLongPressHandled = true;
                            append("Steering O long press captured; toggling instrument map");
                            toggleInstrumentMapFromSteering();
                        }
                    } else if (event.getAction() == KeyEvent.ACTION_UP) {
                        long heldMs = steeringButtonDownMs > 0L
                                ? event.getEventTime() - steeringButtonDownMs : 0L;
                        if (!steeringLongPressHandled && heldMs >= STEERING_LONG_PRESS_MS) {
                            steeringLongPressHandled = true;
                            append("Steering O long press captured (" + heldMs
                                    + " ms); toggling instrument map");
                            toggleInstrumentMapFromSteering();
                        } else if (!steeringLongPressHandled) {
                            append("Steering O short press (" + heldMs
                                    + " ms) left to stock music control");
                        }
                        steeringButtonDownMs = 0L;
                        steeringLongPressHandled = false;
                    }
                    return true;
                }
            }, overlayHandler);
            steeringMediaSession.setPlaybackState(new PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY_PAUSE
                            | PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE)
                    .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
                    .build());
        }
        steeringMediaSession.setActive(true);
        startSteeringSilentPlayback();
    }

    private void refreshSteeringMediaPriority() {
        if (!instrumentMapFullScreenActive || !isAwdEnabled()
                || steeringMediaSession == null) return;
        try {
            steeringMediaSession.setActive(false);
            steeringMediaSession.setActive(true);
        } catch (Throwable error) {
            Log.w(TAG, "Steering MediaSession priority refresh failed", error);
        }
        releaseSteeringSilentPlayback();
        startSteeringSilentPlayback();
    }

    private void startSteeringSilentPlayback() {
        if (steeringSilentAudioTrack != null
                && steeringSilentAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            return;
        }
        releaseSteeringSilentPlayback();
        try {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            AudioFormat format = new AudioFormat.Builder()
                    .setSampleRate(STEERING_SILENT_SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build();
            int minimumBuffer = AudioTrack.getMinBufferSize(STEERING_SILENT_SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            if (minimumBuffer <= 0) {
                throw new IllegalStateException("invalid silent AudioTrack buffer=" + minimumBuffer);
            }
            final AudioTrack track = new AudioTrack(attributes, format, minimumBuffer,
                    AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                track.release();
                throw new IllegalStateException("silent AudioTrack initialization failed");
            }
            track.setVolume(0f);
            steeringSilentAudioTrack = track;
            track.play();
            final byte[] silence = new byte[minimumBuffer];
            new Thread(new Runnable() {
                @Override public void run() {
                    while (steeringSilentAudioTrack == track) {
                        int written = track.write(
                                silence, 0, silence.length, AudioTrack.WRITE_BLOCKING);
                        if (written <= 0) break;
                    }
                }
            }, "SteeringSilentAudio").start();
            append("Steering media priority active with silent playback (no audio focus)");
        } catch (Throwable error) {
            appendFailure("Steering silent playback failed", error);
        }
    }

    private void releaseSteeringSilentPlayback() {
        if (steeringSilentAudioTrack == null) return;
        AudioTrack track = steeringSilentAudioTrack;
        steeringSilentAudioTrack = null;
        try { track.stop(); }
        catch (Throwable error) { Log.w(TAG, "Steering silent AudioTrack stop failed", error); }
        try { track.release(); }
        catch (Throwable error) { Log.w(TAG, "Steering silent AudioTrack release failed", error); }
    }

    private void releaseSteeringInputShortcut() {
        steeringButtonDownMs = 0L;
        steeringLongPressHandled = false;
        releaseSteeringSilentPlayback();
        if (steeringMediaSession != null) {
            try { steeringMediaSession.setActive(false); }
            catch (Throwable error) { Log.w(TAG, "Steering MediaSession stop failed", error); }
            try { steeringMediaSession.release(); }
            catch (Throwable error) { Log.w(TAG, "Steering MediaSession release failed", error); }
            steeringMediaSession = null;
        }
        append("Steering media shortcut released");
    }

    private void sendNeusoftNavigationState(boolean started) {
        Intent state = new Intent(NEUSOFT_NAVI_STATE_ACTION);
        state.putExtra(NEUSOFT_NAVI_KEY_TYPE, NEUSOFT_NAVI_TYPE);
        state.putExtra(NEUSOFT_NAVI_EXTRA_STATE,
                started ? NEUSOFT_NAVI_START : NEUSOFT_NAVI_STOP);
        sendBroadcast(state);
        append("Neusoft navigation state="
                + (started ? NEUSOFT_NAVI_START : NEUSOFT_NAVI_STOP));
    }

    private void setControlError(String message) {
        if (controlStatusView == null) return;
        controlStatusView.setText(message);
        controlStatusView.setTextColor(Color.rgb(166, 58, 51));
    }

    private File cameraDatabaseFile() {
        return new File(getFilesDir(), CAMERA_DATABASE_FILE);
    }

    private File cameraDatabaseBackupFile() {
        return new File(getFilesDir(), CAMERA_DATABASE_FILE + ".bak");
    }

    private void loadCameraDatabaseAsync(final boolean updated) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                if (cameraDatabaseView != null) {
                    cameraDatabaseView.setText(updated
                            ? "База камер: проверка обновления…"
                            : "База камер: загрузка…");
                }
            }
        });
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    File local = cameraDatabaseFile();
                    File backup = cameraDatabaseBackupFile();
                    if (!local.isFile() && backup.isFile() && !backup.renameTo(local)) {
                        Log.w(TAG, "Camera database backup could not be restored");
                    }
                    SpeedCameraIndex candidate;
                    String candidateSource;
                    if (local.isFile()) {
                        try {
                            candidate = readCameraDatabase(new FileInputStream(local));
                            candidateSource = formatCameraSource(candidate, "HUD Speed");
                        } catch (Throwable localError) {
                            Log.e(TAG, "Local camera database is invalid", localError);
                            if (backup.isFile()) {
                                try {
                                    candidate = readCameraDatabase(new FileInputStream(backup));
                                    candidateSource = formatCameraSource(candidate,
                                            "HUD Speed · резервная копия");
                                    if (local.delete() && !backup.renameTo(local)) {
                                        Log.w(TAG, "Camera database backup could not replace invalid local file");
                                    }
                                } catch (Throwable backupError) {
                                    Log.e(TAG, "Camera database backup is invalid; using asset",
                                            backupError);
                                    candidate = readCameraDatabase(
                                            getAssets().open(CAMERA_DATABASE_ASSET));
                                    candidateSource = formatCameraSource(candidate,
                                            "HUD Speed · встроенная");
                                }
                            } else {
                                candidate = readCameraDatabase(
                                        getAssets().open(CAMERA_DATABASE_ASSET));
                                candidateSource = formatCameraSource(candidate,
                                        "HUD Speed · встроенная");
                            }
                        }
                    } else {
                        candidate = readCameraDatabase(
                                getAssets().open(CAMERA_DATABASE_ASSET));
                        candidateSource = formatCameraSource(candidate,
                                "HUD Speed · встроенная");
                    }
                    final SpeedCameraIndex loaded = candidate;
                    final String source = candidateSource;
                    cameraIndex = loaded;
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (cameraDatabaseView != null) {
                                cameraDatabaseView.setText("База камер: "
                                        + loaded.size() + " объектов · " + source
                                        + (loaded.repairedRows() > 0
                                        ? " · исправлено строк: " + loaded.repairedRows() : ""));
                            }
                            if (cameraUpdateButton != null) cameraUpdateButton.setEnabled(true);
                        }
                    });
                } catch (final Throwable error) {
                    Log.e(TAG, "Camera database load failed", error);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (cameraDatabaseView != null) {
                                cameraDatabaseView.setText("Ошибка базы камер: "
                                        + error.getMessage());
                            }
                            if (cameraUpdateButton != null) cameraUpdateButton.setEnabled(true);
                        }
                    });
                }
            }
        }, "SpeedCameraLoad").start();
    }

    private SpeedCameraIndex readCameraDatabase(InputStream input) throws IOException {
        try {
            return SpeedCameraIndex.read(input);
        } finally {
            try { input.close(); } catch (IOException ignored) { }
        }
    }

    private String formatCameraSource(SpeedCameraIndex index, String prefix) {
        String date = index.databaseDate();
        return date == null ? prefix : prefix + " " + date;
    }

    private void updateCameraDatabaseAsync() {
        if (cameraUpdateButton != null) cameraUpdateButton.setEnabled(false);
        if (cameraDatabaseView != null) cameraDatabaseView.setText("База камер: скачивание…");
        new Thread(new Runnable() {
            @Override public void run() {
                File temporary = new File(getFilesDir(), CAMERA_DATABASE_FILE + ".tmp");
                HttpURLConnection connection = null;
                try {
                    if (temporary.exists() && !temporary.delete()) {
                        throw new IOException("Не удалось очистить временный файл базы");
                    }
                    connection = (HttpURLConnection) new URL(CAMERA_DATABASE_URL).openConnection();
                    connection.setConnectTimeout(20000);
                    connection.setReadTimeout(90000);
                    connection.setRequestProperty("User-Agent", cameraUpdateUserAgent());
                    SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
                    String etag = preferences.getString(PREF_CAMERA_ETAG, null);
                    String lastModified = preferences.getString(
                            PREF_CAMERA_LAST_MODIFIED, null);
                    boolean conditionalRequest = hasUsableDownloadedCameraDatabase(
                            cameraDatabaseFile());
                    if (conditionalRequest) {
                        if (etag != null && etag.length() > 0) {
                            connection.setRequestProperty("If-None-Match", etag);
                        }
                        if (lastModified != null && lastModified.length() > 0) {
                            connection.setRequestProperty("If-Modified-Since", lastModified);
                        }
                    }
                    connection.connect();
                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                        if (!hasUsableDownloadedCameraDatabase(cameraDatabaseFile())) {
                            preferences.edit()
                                    .remove(PREF_CAMERA_ETAG)
                                    .remove(PREF_CAMERA_LAST_MODIFIED)
                                    .apply();
                            throw new IOException("Сервер вернул 304, но локальная база недоступна");
                        }
                        loadCameraDatabaseAsync(true);
                        return;
                    }
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        throw new IOException("HTTP " + responseCode);
                    }
                    int contentLength = connection.getContentLength();
                    if (contentLength > MAX_CAMERA_ZIP_BYTES) {
                        throw new IOException("Архив базы слишком большой: " + contentLength);
                    }
                    extractHudDatabase(connection.getInputStream(), temporary);

                    SpeedCameraIndex validated;
                    try (InputStream input = new FileInputStream(temporary)) {
                        validated = SpeedCameraIndex.read(input);
                    }
                    if (validated.size() < MIN_PRIMARY_CAMERA_COUNT) {
                        throw new IOException("Слишком мало записей: " + validated.size());
                    }
                    SpeedCameraIndex active = cameraIndex;
                    if (active != null && active.databaseDate() != null
                            && validated.databaseDate() != null
                            && validated.databaseDate().compareTo(active.databaseDate()) < 0) {
                        throw new IOException("Сервер вернул устаревшую базу "
                                + validated.databaseDate());
                    }
                    replaceCameraDatabase(temporary);
                    SharedPreferences.Editor editor = preferences.edit();
                    String responseEtag = connection.getHeaderField("ETag");
                    String responseModified = connection.getHeaderField("Last-Modified");
                    if (responseEtag == null) editor.remove(PREF_CAMERA_ETAG);
                    else editor.putString(PREF_CAMERA_ETAG, responseEtag);
                    if (responseModified == null) editor.remove(PREF_CAMERA_LAST_MODIFIED);
                    else editor.putString(PREF_CAMERA_LAST_MODIFIED, responseModified);
                    editor.apply();
                    loadCameraDatabaseAsync(true);
                } catch (final Throwable error) {
                    Log.e(TAG, "Camera database update failed", error);
                    if (temporary.exists()) temporary.delete();
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (cameraDatabaseView != null) {
                                SpeedCameraIndex active = cameraIndex;
                                cameraDatabaseView.setText("Обновление не удалось: "
                                        + cameraUpdateErrorText(error)
                                        + (active == null ? "" : " · действует база: "
                                        + active.size() + " объектов"));
                            }
                            if (cameraUpdateButton != null) cameraUpdateButton.setEnabled(true);
                        }
                    });
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
        }, "SpeedCameraUpdate").start();
    }

    private void extractHudDatabase(InputStream networkInput, File target)
            throws IOException {
        CameraDatabaseUpdate.extractExactEntry(networkInput, target,
                CAMERA_DATABASE_ZIP_ENTRY, MAX_CAMERA_ZIP_BYTES, MAX_CAMERA_TEXT_BYTES);
    }

    private boolean hasUsableDownloadedCameraDatabase(File file) {
        if (file == null || !file.isFile()) return false;
        try (InputStream input = new FileInputStream(file)) {
            return SpeedCameraIndex.read(input).size() >= MIN_PRIMARY_CAMERA_COUNT;
        } catch (Throwable error) {
            Log.w(TAG, "Downloaded camera database is unavailable for conditional update", error);
            return false;
        }
    }

    private String cameraUpdateUserAgent() {
        try {
            String version = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
            if (version != null && version.length() > 0) {
                return "EXEED-Road-Assistant/" + version;
            }
        } catch (Throwable error) {
            Log.w(TAG, "Could not read app version for camera update", error);
        }
        return "EXEED-Road-Assistant/unknown";
    }

    private String cameraUpdateErrorText(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        String message = cause.getMessage();
        if (cause instanceof UnknownHostException
                || cause instanceof SocketTimeoutException
                || (message != null && message.toLowerCase(java.util.Locale.US)
                .contains("timeout"))) {
            return "нет соединения с интернетом";
        }
        return message == null || message.length() == 0
                ? cause.getClass().getSimpleName() : message;
    }

    private void replaceCameraDatabase(File temporary) throws IOException {
        CameraDatabaseUpdate.replaceKeepingBackup(temporary,
                cameraDatabaseFile(), cameraDatabaseBackupFile());
    }

    private void startCameraMonitoring() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQUEST_LOCATION_PERMISSION);
            return;
        }
        Intent serviceIntent = new Intent(this, CameraLocationService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        overlayHandler.removeCallbacks(cameraLocationWatchdog);
        overlayHandler.removeCallbacks(navigatorRouteFreshnessWatchdog);
        overlayHandler.postDelayed(cameraLocationWatchdog, CAMERA_LOCATION_WATCHDOG_MS);
        overlayHandler.postDelayed(navigatorRouteFreshnessWatchdog,
                NavigatorRouteFreshnessPolicy.WATCHDOG_INTERVAL_MS);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            append("LocationManager недоступен");
            return;
        }
        try {
            // The foreground service is the sole location source. Keep this legacy
            // listener disabled to avoid processing every GPS sample twice.
            if (false && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        1000L, 2f, cameraLocationListener);
            }
            if (false && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                        2000L, 5f, cameraLocationListener);
            }
            append("Мониторинг камер запущен");
        } catch (SecurityException error) {
            appendFailure("Нет доступа к геопозиции", error);
        }
    }

    private boolean isPhoneGpsEnabled() {
        return getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(PREF_PHONE_GPS_ENABLED, false);
    }

    private String ensurePhoneGpsPairingToken(boolean renew) {
        String current = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(PREF_PHONE_GPS_TOKEN, "");
        if (!renew && current != null && current.length() >= 12) return current;
        byte[] value = new byte[12];
        new SecureRandom().nextBytes(value);
        StringBuilder token = new StringBuilder(24);
        for (byte one : value) token.append(String.format(Locale.US, "%02x", one & 0xff));
        String result = token.toString();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(PREF_PHONE_GPS_TOKEN, result).apply();
        return result;
    }

    private void refreshPhoneGpsSource() {
        Intent serviceIntent = new Intent(this, CameraLocationService.class)
                .setAction(CameraLocationService.ACTION_REFRESH_PHONE_GPS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent);
        else startService(serviceIntent);
    }

    private void resetPhoneGpsSelection() {
        lastPhoneLocationElapsedMs = 0L;
        phoneGpsWasPrimary = false;
        locationGuard.reset();
        courseAnchorLocation = null;
    }

    private void updatePhoneGpsStatus() {
        if (phoneGpsStatusView == null) return;
        if (!isPhoneGpsEnabled()) {
            phoneGpsStatusView.setText("Телефон: выключен; GPS автомобиля используется всегда.");
            return;
        }
        long ageMs = SystemClock.elapsedRealtime() - lastPhoneLocationElapsedMs;
        if (lastPhoneLocationElapsedMs > 0L && ageMs <= PHONE_GPS_STALE_MS) {
            phoneGpsStatusView.setText("Телефон: GPS принимается; при потере вернётся GPS автомобиля.");
            return;
        }
        phoneGpsStatusView.setText("Телефон: ожидание Wi‑Fi. Код: "
                + ensurePhoneGpsPairingToken(false));
    }

    private void stopCameraMonitoring() {
        // onDestroy may be called when the control Activity is merely replaced or
        // reclaimed. Keep the foreground monitor alive while the feature is enabled.
        if (!isAwdEnabled()) {
            stopService(new Intent(this, CameraLocationService.class));
        }
        overlayHandler.removeCallbacks(cameraLocationWatchdog);
        if (locationManager != null
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            try { locationManager.removeUpdates(cameraLocationListener); }
            catch (Throwable error) { Log.w(TAG, "Location listener removal failed", error); }
        }
        locationManager = null;
        lastLocation = null;
        courseAnchorLocation = null;
        lastLocationUpdateElapsedMs = 0L;
        resetPhoneGpsSelection();
        lastLocationGuardLogElapsedMs = 0L;
        locationGuard.reset();
        lastCameraScanLogMs = 0L;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && isAwdEnabled()) {
            startCameraMonitoring();
        }
    }

    private void handleCameraLocation(Location location) {
        SpeedCameraIndex index = cameraIndex;
        if (location != null) {
            final boolean phoneSample = "phone-gps".equals(location.getProvider());
            final long nowElapsedMs = SystemClock.elapsedRealtime();
            if (phoneSample) {
                if (!isPhoneGpsEnabled()) return;
                if (!phoneGpsWasPrimary) {
                    locationGuard.reset();
                    courseAnchorLocation = null;
                    phoneGpsWasPrimary = true;
                    append("Камеры: источник GPS переключён на телефон");
                }
                lastPhoneLocationElapsedMs = nowElapsedMs;
                updatePhoneGpsStatus();
            } else if (isPhoneGpsEnabled() && lastPhoneLocationElapsedMs > 0L
                    && nowElapsedMs - lastPhoneLocationElapsedMs <= PHONE_GPS_STALE_MS) {
                // A fresh phone fix is primary. The service still receives vehicle fixes so
                // fallback is immediate when the Wi-Fi link goes away.
                return;
            } else if (phoneGpsWasPrimary) {
                locationGuard.reset();
                courseAnchorLocation = null;
                phoneGpsWasPrimary = false;
                updatePhoneGpsStatus();
                append("Камеры: связь с GPS телефона потеряна, используется GPS автомобиля");
            }
            if (isGpsGuardEnabled()) {
                LocationGuard.Result guardResult = locationGuard.filter(location);
                if (!guardResult.isAccepted()) {
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastLocationGuardLogElapsedMs >= 5000L) {
                        lastLocationGuardLogElapsedMs = now;
                        append("GPS Guard: точка отброшена (" + guardResult.reason + ")");
                    }
                    return;
                }
                location = guardResult.location;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                    && location.getElapsedRealtimeNanos() > 0L) {
                long ageMs = (SystemClock.elapsedRealtimeNanos()
                        - location.getElapsedRealtimeNanos()) / 1000000L;
                if (ageMs > CAMERA_LOCATION_STALE_MS) {
                    Log.w(TAG, "Ignoring stale location: age=" + ageMs + " ms");
                    return;
                }
            }
            if (location.hasAccuracy() && location.getAccuracy() > 500f) {
                Log.w(TAG, "Ignoring inaccurate location: accuracy="
                        + location.getAccuracy() + " m");
                return;
            }
            lastLocationUpdateElapsedMs = nowElapsedMs;
            updateInstrumentMapFromAcceptedLocation(location, nowElapsedMs);
        }
        // A demo camera alert suppresses only the real radar lookup. It must not
        // freeze the shared location stream that keeps the instrument map centred.
        if (demoAlertActive) {
            lastLocation = location == null ? null : new Location(location);
            return;
        }
        if (!isAwdEnabled() || index == null || location == null) {
            lastLocation = location;
            return;
        }
        boolean hasCourse = location.hasBearing() && location.getSpeed() > 1.2f;
        float course = hasCourse ? location.getBearing() : 0f;
        boolean gpsSample = LocationManager.GPS_PROVIDER.equals(location.getProvider())
                || "phone-gps".equals(location.getProvider());
        boolean moving = !location.hasSpeed() || location.getSpeed() > 0.8f;
        if (!hasCourse && gpsSample && moving && courseAnchorLocation != null
                && (LocationManager.GPS_PROVIDER.equals(courseAnchorLocation.getProvider())
                || "phone-gps".equals(courseAnchorLocation.getProvider()))) {
            float anchorDistance = courseAnchorLocation.distanceTo(location);
            if (anchorDistance >= 6f) {
                course = courseAnchorLocation.bearingTo(location);
                hasCourse = true;
            }
        }
        boolean anchorIsGpsSample = courseAnchorLocation != null
                && (LocationManager.GPS_PROVIDER.equals(courseAnchorLocation.getProvider())
                || "phone-gps".equals(courseAnchorLocation.getProvider()));
        if (gpsSample && (courseAnchorLocation == null || hasCourse || !anchorIsGpsSample)) {
            courseAnchorLocation = new Location(location);
        }
        lastLocation = new Location(location);
        int warningDistance = getWarningDistanceMeters(location);

        if (lastPassedCamera != null) {
            float passedDistance = SpeedCameraIndex.distanceMeters(
                    location.getLatitude(), location.getLongitude(),
                    lastPassedCamera.latitude, lastPassedCamera.longitude);
            if (passedDistance > warningDistance + 250f) lastPassedCamera = null;
        }

        if (activeCamera != null) {
            int activeWarningDistance = activeCamera.effectiveWarningDistance(warningDistance);
            float distance = SpeedCameraIndex.distanceMeters(
                    location.getLatitude(), location.getLongitude(),
                    activeCamera.latitude, activeCamera.longitude);
            activeMinimumDistance = Math.min(activeMinimumDistance, distance);
            float bearingToCamera = SpeedCameraIndex.bearingDegrees(
                    location.getLatitude(), location.getLongitude(),
                    activeCamera.latitude, activeCamera.longitude);
            boolean behind = hasCourse
                    && SpeedCameraIndex.angleDifference(course, bearingToCamera) > 105f;
            boolean passed = SpeedCameraIndex.hasPassedCamera(
                    activeMinimumDistance, distance, behind);
            if (passed) {
                append("Camera passed: id=" + activeCamera.id
                        + " minimum=" + Math.round(activeMinimumDistance)
                        + " m current=" + Math.round(distance) + " m");
                lastPassedCamera = activeCamera;
                clearActiveCamera();
                return;
            }
            // Keep tracking a camera briefly after it moves behind us. Otherwise
            // matchesTravelPath() clears it first and a nearby duplicate can be
            // acquired immediately without recording the physical camera as passed.
            boolean justBehindCamera = behind && activeMinimumDistance < 170f && distance <= 30f;
            boolean stillOnTravelPath = !hasCourse || justBehindCamera
                    || SpeedCameraIndex.matchesTravelPath(
                    activeCamera, course, bearingToCamera, distance);
            if (!stillOnTravelPath) {
                append("Camera alert cleared outside travel path: id=" + activeCamera.id
                        + " course=" + Math.round(course)
                        + " bearing=" + Math.round(bearingToCamera));
                clearActiveCamera();
                return;
            }
            if (!shouldShowSpeedCamera(activeCamera, location, true)) {
                append("Camera alert cleared at legal speed: id=" + activeCamera.id
                        + " limit=" + activeCamera.speed
                        + " current=" + Math.round(location.getSpeed() * 3.6f));
                clearActiveCamera();
                return;
            }
            if (distance <= activeWarningDistance + 100f) {
                int bucket = Math.max(0, (int) distance / 100);
                if (bucket != activeCameraDistanceBucket) {
                    activeCameraDistanceBucket = bucket;
                    append("Camera distance: id=" + activeCamera.id
                            + " " + Math.round(distance) + " m");
                }
                showCameraAlert(activeCamera, distance);
                return;
            }
            append("Camera alert cleared outside radius: id=" + activeCamera.id);
            clearActiveCamera();
        }

        // A camera warning is a route-corridor decision, never a radius-only
        // decision. Wait for a reliable course before acquiring a camera.
        SpeedCameraIndex.Match match = hasCourse ? index.findNearest(
                location, course, true, warningDistance) : null;
        if (match != null && (lastPassedCamera == null
                || !SpeedCameraIndex.areSameWarningZone(
                match.camera, lastPassedCamera))
                && shouldShowSpeedCamera(match.camera, location, false)) {
            activeCamera = match.camera;
            activeMinimumDistance = match.distanceMeters;
            activeCameraDistanceBucket = Math.max(0,
                    (int) match.distanceMeters / 100);
            append("Camera alert: id=" + activeCamera.id
                    + " type=" + activeCamera.type
                    + " speed=" + activeCamera.speed
                    + " radius=" + activeCamera.effectiveWarningDistance(warningDistance)
                    + " angle=" + activeCamera.effectiveAngleTolerance()
                    + " distance=" + Math.round(match.distanceMeters)
                    + " m course=" + Math.round(course));
            showCameraAlert(activeCamera, match.distanceMeters);
            playCameraAlert();
        } else if (match == null) {
            long now = SystemClock.uptimeMillis();
            if (now - lastCameraScanLogMs >= 10000L) {
                lastCameraScanLogMs = now;
                append(String.format(java.util.Locale.US,
                        "Camera scan: lat=%.6f lon=%.6f speed=%.1f course=%s accuracy=%.0f",
                        location.getLatitude(), location.getLongitude(),
                        location.hasSpeed() ? location.getSpeed() * 3.6f : -1f,
                        hasCourse ? String.format(java.util.Locale.US, "%.0f", course) : "none",
                        location.hasAccuracy() ? location.getAccuracy() : -1f));
            }
        }
    }

    private boolean isGpsGuardEnabled() {
        return getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(PREF_CAMERA_GPS_GUARD, false);
    }

    private boolean shouldShowSpeedCamera(SpeedCamera camera, Location location,
                                          boolean alertAlreadyActive) {
        int mode = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(
                PREF_CAMERA_WARNING_MODE, CAMERA_WARNING_ALWAYS);
        if (mode != CAMERA_WARNING_OVERSPEED || camera == null || camera.speed <= 0) {
            return true;
        }
        if (location == null || !location.hasSpeed()) return false;
        float speedKmh = Math.max(0f, location.getSpeed() * 3.6f);
        float threshold = alertAlreadyActive
                ? camera.speed : camera.speed + OVERSPEED_ACTIVATION_MARGIN_KMH;
        return speedKmh > threshold;
    }

    private void showCameraAlert(SpeedCamera camera, float distanceMeters) {
        if (overlayRouteGuidanceView != null) {
            overlayRouteGuidanceView.updateCameraAlert(camera, distanceMeters);
        }
        if (overlayView != null) {
            overlayView.updateCameraAlert(camera, distanceMeters);
        }
        if (overlayMapView != null) {
            overlayMapView.showCamera(camera);
        }
        sendCameraHudAlert(camera, Math.max(0, Math.round(distanceMeters)));
    }

    private void clearActiveCamera() {
        clearCameraHudAlert();
        activeCamera = null;
        activeMinimumDistance = Float.MAX_VALUE;
        activeCameraDistanceBucket = -1;
        if (overlayView != null) overlayView.clearCameraAlert();
        if (overlayMapView != null) overlayMapView.clearCamera();
        if (overlayRouteGuidanceView != null) {
            overlayRouteGuidanceView.clearCameraAlert();
        }
    }

    private void sendCameraHudAlert(SpeedCamera camera, int distanceMeters) {
        if (!isHudOutputAvailable()
                || !getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(PREF_CAMERA_HUD, false)) return;
        long now = SystemClock.elapsedRealtime();
        if (hudCameraActive
                && Math.abs(distanceMeters - lastHudCameraDistance) < 5
                && now - lastHudCameraUpdateMs < 2000L) return;
        Intent hud = new Intent("com.telenav.app.START");
        hud.setComponent(new ComponentName(
                "com.telenav.app.arp", "com.telenav.app.receiver.BootReceiver"));
        hud.putExtra("mode", "LEGACY");
        String hudText = camera == null ? "Камера" : camera.hudLabel();
        hud.putExtra("street", hudText);
        // Physically verified on TXL2: TeleNav maneuver 23 is translated by
        // ExtraService to raw HUD status 31 (toll booth), used as camera icon.
        hud.putExtra("turn", 23);
        hud.putExtra("distance", distanceMeters);
        // Treat the camera as the temporary destination so the finish distance is meaningful.
        hud.putExtra("destDistance", distanceMeters);
        hud.putExtra("intervalMs", 1000L);
        hud.putExtra("source", "camera");
        sendBroadcast(hud);
        hudCameraActive = true;
        lastHudCameraDistance = distanceMeters;
        lastHudCameraUpdateMs = now;
        scheduleHudMetricDistanceOverride(distanceMeters);
        Log.i(TAG, "HUD camera alert: icon=toll_booth telenav=23 raw=31 text='" + hudText
                + "' distance=" + distanceMeters + " m");
    }

    private void clearCameraHudAlert() {
        if (!hudCameraActive) return;
        hudDistanceOverrideGeneration++;
        Intent stop = new Intent("com.telenav.app.START");
        stop.setComponent(new ComponentName(
                "com.telenav.app.arp", "com.telenav.app.receiver.BootReceiver"));
        stop.putExtra("mode", "STOP");
        stop.putExtra("source", "camera");
        sendBroadcast(stop);
        hudCameraActive = false;
        lastHudCameraDistance = -1;
        lastHudCameraUpdateMs = 0L;
        Log.i(TAG, "HUD camera alert cleared");
        // FakeTeleNav owns the persistent Navigator state and restores the latest
        // maneuver after processing this camera-specific STOP command.
    }

    private boolean isHudOutputAvailable() {
        if (hudOutputAvailable != null) return hudOutputAvailable.booleanValue();
        try {
            InputStream marker = getAssets().open("hud_output_disabled");
            marker.close();
            hudOutputAvailable = Boolean.FALSE;
        } catch (IOException markerMissing) {
            hudOutputAvailable = Boolean.TRUE;
        }
        return hudOutputAvailable.booleanValue();
    }

    private void scheduleHudMetricDistanceOverride(final int distanceMeters) {
        final int generation = ++hudDistanceOverrideGeneration;
        // ExtraService writes unit=0 because its TeleNav adapter discards formattedDistance.
        // Rewrite after its callback using the documented HUD unit code 1 (metres).
        long[] delaysMs = {120L, 420L};
        for (final long delayMs : delaysMs) {
            overlayHandler.postDelayed(new Runnable() {
                @Override public void run() {
                    if (!hudCameraActive || generation != hudDistanceOverrideGeneration) return;
                    writeHudMetricDistances(distanceMeters);
                }
            }, delayMs);
        }
    }

    private void writeHudMetricDistances(int distanceMeters) {
        Object manager = vendorManager;
        if (manager == null) {
            Log.w(TAG, "HUD metric distance override skipped: vendor_extension is not ready");
            return;
        }
        byte[] frame = HudDistanceEncoder.encodeMeters(distanceMeters);
        try {
            Method setProperty = manager.getClass().getMethod(
                    "setProperty", Class.class, int.class, int.class, Object.class);
            setProperty.invoke(manager, byte[].class,
                    PROP_HUD_DISTANCE_TO_DESTINATION, 0, frame.clone());
            setProperty.invoke(manager, byte[].class,
                    PROP_HUD_DISTANCE_TO_JUNCTION, 0, frame.clone());
            Log.d(TAG, "HUD metric distances written: " + distanceMeters + " m");
        } catch (Throwable error) {
            Log.w(TAG, "HUD metric distance override failed", error);
        }
    }

    private void scheduleHudNavigationTextRewrite() {
        if (!isHudOutputAvailable() || !yandexManeuverVisible
                || yandexNextRoadName.trim().isEmpty()) return;
        final int generation = ++hudNavigationTextGeneration;
        long[] delaysMs = {180L, 520L};
        for (final long delayMs : delaysMs) {
            overlayHandler.postDelayed(new Runnable() {
                @Override public void run() {
                    if (generation != hudNavigationTextGeneration || hudCameraActive
                            || !yandexManeuverVisible) return;
                    writeHudNavigationText(yandexNextRoadName);
                }
            }, delayMs);
        }
    }

    private void writeHudNavigationText(String text) {
        Object manager = vendorManager;
        if (manager == null) {
            Log.w(TAG, "HUD navigation text rewrite skipped: vendor_extension is not ready");
            return;
        }
        try {
            Method setProperty = manager.getClass().getMethod(
                    "setProperty", Class.class, int.class, int.class, Object.class);
            byte[][] frames = HudNavigationTextEncoder.encode(text);
            for (byte[] frame : frames) {
                setProperty.invoke(manager, byte[].class,
                        PROP_HUD_NAVIGATION_TEXT, 0, frame);
            }
            Log.i(TAG, "HUD navigation text rewritten: '" + text
                    + "' frames=" + frames.length);
        } catch (Throwable error) {
            Log.w(TAG, "HUD navigation text rewrite failed", error);
        }
    }

    private void playCameraAlert() {
        if (!getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(PREF_CAMERA_AUDIO, true)) return;
        releaseAlertSound();
        try {
            alertAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            int focusResult = requestAlertAudioFocus(attributes);

            final int sampleRate = 48000;
            int soundStyle = Math.max(0, Math.min(CAMERA_SOUND_NAMES.length - 1,
                    getSharedPreferences(PREFS, MODE_PRIVATE)
                            .getInt(PREF_CAMERA_SOUND, 0)));
            if (CAMERA_SOUND_ASSETS[soundStyle] != null) {
                playPackagedCameraAlert(soundStyle, attributes, focusResult);
                return;
            }
            byte[] pcm = AlertSoundGenerator.createPcm(sampleRate, soundStyle);
            AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build();
            int minimumBuffer = AudioTrack.getMinBufferSize(sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            if (minimumBuffer <= 0) {
                throw new IllegalStateException("Invalid AudioTrack buffer: " + minimumBuffer);
            }
            alertAudioTrack = new AudioTrack(attributes, format, minimumBuffer,
                    AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
            if (alertAudioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                throw new IllegalStateException("AudioTrack initialization failed");
            }
            alertAudioTrack.setVolume(1f);
            alertAudioTrack.play();
            append("Navigation alert sound started: focus=" + focusResult
                    + " buffer=" + minimumBuffer
                    + " style=" + CAMERA_SOUND_NAMES[soundStyle]);
            final AudioTrack activeTrack = alertAudioTrack;
            final byte[] activePcm = pcm;
            new Thread(new Runnable() {
                @Override public void run() {
                    int offset = 0;
                    try {
                        while (offset < activePcm.length && activeTrack == alertAudioTrack) {
                            int written = activeTrack.write(activePcm, offset,
                                    activePcm.length - offset, AudioTrack.WRITE_BLOCKING);
                            if (written <= 0) {
                                throw new IOException("AudioTrack stream write failed: " + written);
                            }
                            offset += written;
                        }
                        append("Navigation alert PCM written: " + offset + " bytes");
                    } catch (Throwable error) {
                        appendFailure("Navigation alert streaming failed", error);
                    }
                }
            }, "CameraAlertAudio").start();
            overlayHandler.postDelayed(releaseAlertAudio, 1650L);
        } catch (Throwable error) {
            appendFailure("Navigation alert sound failed", error);
            releaseAlertSound();
        }
    }

    private void playPackagedCameraAlert(final int soundStyle,
                                         AudioAttributes attributes,
                                         int focusResult) throws IOException {
        final MediaPlayer player = new MediaPlayer();
        alertMediaPlayer = player;
        try (AssetFileDescriptor asset = getAssets().openFd(
                CAMERA_SOUND_ASSETS[soundStyle])) {
            player.setAudioAttributes(attributes);
            player.setDataSource(asset.getFileDescriptor(),
                    asset.getStartOffset(), asset.getLength());
            float volume = CAMERA_SOUND_VOLUMES[soundStyle];
            player.setVolume(volume, volume);
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer completed) {
                    if (alertMediaPlayer == completed) releaseAlertSound();
                }
            });
            player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer failed, int what, int extra) {
                    Log.e(TAG, "Packaged camera alert failed: what=" + what
                            + " extra=" + extra);
                    if (alertMediaPlayer == failed) releaseAlertSound();
                    return true;
                }
            });
            player.prepare();
            int durationMs = Math.max(1, player.getDuration());
            player.start();
            append("Navigation alert asset started: focus=" + focusResult
                    + " duration=" + durationMs
                    + " volume=" + volume
                    + " style=" + CAMERA_SOUND_NAMES[soundStyle]);
            overlayHandler.postDelayed(releaseAlertAudio,
                    Math.max(1000L, durationMs + 300L));
        } catch (IOException error) {
            try { player.release(); } catch (Throwable ignored) { }
            if (alertMediaPlayer == player) alertMediaPlayer = null;
            throw error;
        } catch (RuntimeException error) {
            try { player.release(); } catch (Throwable ignored) { }
            if (alertMediaPlayer == player) alertMediaPlayer = null;
            throw error;
        }
    }

    private int requestAlertAudioFocus(AudioAttributes attributes) {
        if (alertAudioManager == null) return AudioManager.AUDIOFOCUS_REQUEST_FAILED;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            alertFocusRequest = new AudioFocusRequest.Builder(
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(alertFocusListener)
                    .build();
            return alertAudioManager.requestAudioFocus(alertFocusRequest);
        }
        return alertAudioManager.requestAudioFocus(alertFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
    }

    private void releaseAlertSound() {
        overlayHandler.removeCallbacks(releaseAlertAudio);
        if (alertAudioTrack != null) {
            try { alertAudioTrack.stop(); } catch (Throwable ignored) { }
            try { alertAudioTrack.release(); } catch (Throwable ignored) { }
            alertAudioTrack = null;
        }
        if (alertMediaPlayer != null) {
            try { alertMediaPlayer.stop(); } catch (Throwable ignored) { }
            try { alertMediaPlayer.release(); } catch (Throwable ignored) { }
            alertMediaPlayer = null;
        }
        if (alertAudioManager != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        && alertFocusRequest != null) {
                    alertAudioManager.abandonAudioFocusRequest(alertFocusRequest);
                } else {
                    alertAudioManager.abandonAudioFocus(alertFocusListener);
                }
            } catch (Throwable error) {
                Log.w(TAG, "Audio focus release failed", error);
            }
        }
        alertFocusRequest = null;
        alertAudioManager = null;
    }

    private void showDemoCameraAlert() {
        if (!isAwdEnabled()) enableAwdDisplay();
        demoAlertActive = true;
        activeCamera = createDemoCameraAhead();
        activeMinimumDistance = 350f;
        activeCameraDistanceBucket = 3;
        overlayHandler.removeCallbacks(clearDemoAlert);
        overlayHandler.postDelayed(new Runnable() {
            @Override public void run() {
                if (!demoAlertActive) return;
                showCameraAlert(activeCamera, 350f);
                playCameraAlert();
            }
        }, 1200L);
        overlayHandler.postDelayed(clearDemoAlert, 10000L);
    }

    private SpeedCamera createDemoCameraAhead() {
        double latitude = lastLocation == null ? 47.2357 : lastLocation.getLatitude();
        double longitude = lastLocation == null ? 39.7015 : lastLocation.getLongitude();
        double bearing = lastLocation != null && lastLocation.hasBearing()
                ? Math.toRadians(lastLocation.getBearing()) : 0.0;
        double angularDistance = 140.0 / 6371000.0;
        double latitudeRadians = Math.toRadians(latitude);
        double longitudeRadians = Math.toRadians(longitude);
        double targetLatitude = Math.asin(
                Math.sin(latitudeRadians) * Math.cos(angularDistance)
                        + Math.cos(latitudeRadians) * Math.sin(angularDistance)
                        * Math.cos(bearing));
        double targetLongitude = longitudeRadians + Math.atan2(
                Math.sin(bearing) * Math.sin(angularDistance) * Math.cos(latitudeRadians),
                Math.cos(angularDistance)
                        - Math.sin(latitudeRadians) * Math.sin(targetLatitude));
        return new SpeedCamera(-1,
                Math.toDegrees(targetLongitude), Math.toDegrees(targetLatitude),
                1, 60, 1, (int) Math.round(Math.toDegrees(bearing)));
    }

    public static final class ControlReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            Intent activity = new Intent(context, MainActivity.class);
            activity.setAction(intent == null ? null : intent.getAction());
            activity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(activity);
        }
    }

    public static final class BootReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
            boolean autostart = context.getSharedPreferences(PREFS, MODE_PRIVATE)
                    .getBoolean(PREF_AUTOSTART, false);
            if (!autostart) {
                Log.i(TAG, "Boot completed; autostart is disabled");
                return;
            }
            Intent activity = new Intent(context, MainActivity.class);
            activity.setAction(ACTION_SHOW_AWD);
            activity.putExtra("boot_autostart", true);
            activity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(activity);
            Log.i(TAG, "Boot completed; road assistant autostart requested");
        }
    }

    private void enumerateDisplays() {
        DisplayManager manager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        Display[] displays = manager == null ? new Display[0] : manager.getDisplays();
        append("Найдено дисплеев: " + displays.length);
        for (Display display : displays) {
            append("  id=" + display.getDisplayId()
                    + " name=" + display.getName()
                    + " size=" + display.getMode().getPhysicalWidth()
                    + "x" + display.getMode().getPhysicalHeight()
                    + " flags=0x" + Integer.toHexString(display.getFlags())
                    + " state=" + display.getState());
        }
    }

    private Display findInstrumentDisplay() {
        DisplayManager manager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        if (manager == null) return null;
        Display fallback = null;
        for (Display display : manager.getDisplays()) {
            if (display.getDisplayId() == AMAP_INSTRUMENT_DISPLAY_ID) return display;
            if (display.getDisplayId() != Display.DEFAULT_DISPLAY
                    && (display.getFlags() & Display.FLAG_PRESENTATION) != 0
                    && fallback == null) {
                fallback = display;
            }
        }
        return fallback;
    }

    private void showInstrumentPresentation() {
        Display display = findInstrumentDisplay();
        if (display == null) {
            append("Внешний presentation-дисплей не найден; ничего не показано");
            return;
        }
        dismissPresentation();
        try {
            presentation = new AwdPresentation(this, display);
            presentation.show();
            Window window = presentation.getWindow();
            if (window != null) {
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT);
            }
            presentation.update(frontPerWheel, rearPerWheel, signalSummary());
            presentation.setRadarIdle(shouldShowRadarIdle());
            presentation.setBlankIdle(shouldShowBlankIdle());
            append("Presentation показан на displayId=" + display.getDisplayId()
                    + " (AMAP ожидает ID 2; разрешён fallback) обычным типом окна");
        } catch (Throwable error) {
            presentation = null;
            appendFailure("Не удалось показать Presentation", error);
        }
    }

    private void dismissPresentation() {
        if (presentation != null) {
            presentation.dismiss();
            presentation = null;
            append("Presentation закрыт");
        }
    }

    private void showInstrumentOverlay() {
        showInstrumentOverlay(false);
    }

    private void showFullInstrumentOverlay() {
        showInstrumentOverlay(true);
    }

    private void showInstrumentOverlay(boolean fullScreen) {
        if (isInstrumentOverlayAttached()) {
            updateControlUi(true, "Включено");
            Log.i(TAG, "Instrument overlay already attached; keeping current MapKit surface");
            return;
        }
        Display display = findInstrumentDisplay();
        if (display == null) {
            append("External instrument display not found; overlay not created");
            setControlError("Дисплей приборной панели не найден");
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            append("SYSTEM_ALERT_WINDOW app-op is not allowed; overlay not created");
            setControlError("Нет разрешения на показ виджета");
            return;
        }
        dismissInstrumentOverlay();
        try {
            Context displayContext = createDisplayContext(display);
            overlayWindowManager = (WindowManager) displayContext.getSystemService(Context.WINDOW_SERVICE);
            if (overlayWindowManager == null) throw new IllegalStateException("display WindowManager is null");

            overlayView = new AwdView(displayContext);
            overlayView.update(displayFrontPerWheel(), displayRearPerWheel(), signalSummary());
            overlayView.setRadarIdle(shouldShowRadarIdle());
            overlayView.setBlankIdle(shouldShowBlankIdle());
            if (activeCamera != null && (demoAlertActive || lastLocation != null)) {
                float distance = demoAlertActive
                        ? activeMinimumDistance
                        : SpeedCameraIndex.distanceMeters(
                                lastLocation.getLatitude(), lastLocation.getLongitude(),
                                activeCamera.latitude, activeCamera.longitude);
                overlayView.updateCameraAlert(activeCamera, distance);
            }

            overlayContentRoot = new FrameLayout(displayContext);
            overlayContentRoot.setBackgroundColor(Color.TRANSPARENT);
            overlayContentRoot.addView(overlayView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            updateInstrumentMapMode();

            if (fullScreen) {
                FrameLayout root = new FrameLayout(displayContext);
                root.setBackgroundColor(Color.TRANSPARENT);
                FrameLayout.LayoutParams miniMapParams = new FrameLayout.LayoutParams(
                        637, 637, Gravity.TOP | Gravity.RIGHT);
                root.addView(overlayContentRoot, miniMapParams);
                overlayRoot = root;
            } else {
                overlayRoot = overlayContentRoot;
            }

            int requestedWindowType = shouldShowRouteIdle()
                    ? WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
                    : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    fullScreen ? 1920 : 637, fullScreen ? 720 : 637,
                    requestedWindowType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    android.graphics.PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | (fullScreen ? Gravity.LEFT : Gravity.RIGHT);
            params.x = 0;
            params.y = 0;
            params.setTitle("InstrumentAwdOverlay");
            try {
                overlayWindowManager.addView(overlayRoot, params);
            } catch (RuntimeException systemOverlayDenied) {
                if (requestedWindowType != WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY) {
                    throw systemOverlayDenied;
                }
                Log.w(TAG, "TYPE_SYSTEM_OVERLAY denied; falling back to APPLICATION_OVERLAY",
                        systemOverlayDenied);
                try {
                    overlayWindowManager.removeViewImmediate(overlayRoot);
                } catch (RuntimeException cleanupError) {
                    Log.d(TAG, "Rejected SYSTEM_OVERLAY cleanup was unnecessary", cleanupError);
                }
                params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
                overlayWindowManager.addView(overlayRoot, params);
                append("Instrument map: SYSTEM_OVERLAY denied, APPLICATION_OVERLAY fallback used");
            }
            attachMiniRouteGuidanceOverlayIfNeeded();
            overlayHandler.removeCallbacks(overlayTimeout);
            if (!liveFullOverlay) {
                overlayHandler.postDelayed(overlayTimeout, fullScreen ? 8000L : 15000L);
            }
            append("Instrument overlay added: displayId=" + display.getDisplayId()
                    + (fullScreen
                    ? " root=(0,0) 1920x720; content child=(1283,0) 637x637; "
                    + (liveFullOverlay ? "live until explicit stop" : "automatic removal in 8 seconds")
                    : " rect=(1283,0) 637x637; automatic removal in 15 seconds"));
            updateControlUi(true, "Включено");
        } catch (Throwable error) {
            dismissInstrumentOverlay();
            appendFailure("Failed to add AWD overlay", error);
            setControlError("Не удалось показать виджет");
        }
    }

    private void dismissInstrumentOverlay() {
        overlayHandler.removeCallbacks(overlayTimeout);
        dismissMiniRouteGuidanceOverlay();
        if (overlayMapView != null && overlayContentRoot != null) {
            try { overlayContentRoot.removeView(overlayMapView); }
            catch (Throwable error) { Log.w(TAG, "Map surface removal failed", error); }
        }
        overlayMapView = null;
        if (overlayRoot != null && overlayWindowManager != null) {
            try {
                overlayWindowManager.removeView(overlayRoot);
                append("AWD overlay removed");
            } catch (Throwable error) {
                Log.w(TAG, "Overlay removal failed", error);
            }
        }
        overlayRoot = null;
        overlayContentRoot = null;
        overlayView = null;
        overlayWindowManager = null;
    }

    private void connectToCarService() {
        if (car != null) {
            append("Запрос к CarService уже создан");
            return;
        }
        try {
            Class<?> carClass = Class.forName("android.car.Car");
            Method createCar = carClass.getMethod("createCar", Context.class, ServiceConnection.class);
            car = createCar.invoke(null, this, carConnection);
            carClass.getMethod("connect").invoke(car);
            append("Подключение к CarService запрошено…");
        } catch (Throwable error) {
            car = null;
            appendFailure("Не удалось создать android.car.Car", error);
        }
    }

    private void disconnectFromCarService() {
        if (car != null) {
            try {
                car.getClass().getMethod("disconnect").invoke(car);
            } catch (Throwable error) {
                Log.w(TAG, "Car disconnect failed", error);
            }
        }
        car = null;
        vendorManager = null;
        vendorCallback = null;
    }

    private void subscribeToAwdProperties() {
        try {
            Method getCarManager = car.getClass().getMethod("getCarManager", String.class);
            vendorManager = getCarManager.invoke(car, "vendor_extension");
            if (vendorManager == null) throw new IllegalStateException("vendor_extension manager is null");

            final Class<?> callbackClass = Class.forName(
                    "android.car.hardware.CarVendorExtensionManager$CarVendorExtensionCallback");
            vendorCallback = Proxy.newProxyInstance(
                    callbackClass.getClassLoader(),
                    new Class<?>[]{callbackClass},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) {
                            if ("hashCode".equals(method.getName())
                                    && method.getParameterTypes().length == 0) {
                                return Integer.valueOf(System.identityHashCode(proxy));
                            } else if ("equals".equals(method.getName())
                                    && args != null && args.length == 1) {
                                return Boolean.valueOf(proxy == args[0]);
                            } else if ("toString".equals(method.getName())
                                    && method.getParameterTypes().length == 0) {
                                return "InstrumentAwdProbe.CarVendorExtensionCallback";
                            } else if ("onChangeEvent".equals(method.getName())
                                    && args != null && args.length > 0 && args[0] != null) {
                                handleCarPropertyValue(args[0]);
                            } else if ("onErrorEvent".equals(method.getName())) {
                                append("Car property error: " + Arrays.toString(args));
                            }
                            return null;
                        }
                    });

            Method register = vendorManager.getClass().getMethod(
                    "registerCallback", callbackClass, int.class);
            for (int propertyId : AWD_PROPERTIES) {
                try {
                    register.invoke(vendorManager, vendorCallback, propertyId);
                    append("Подписка OK: " + propertyName(propertyId) + " (" + propertyId + ")");
                    readInitialProperty(propertyId);
                } catch (Throwable error) {
                    appendFailure("Подписка отклонена для " + propertyId, error);
                }
            }
        } catch (Throwable error) {
            appendFailure("Не удалось открыть vendor_extension", error);
        }
    }

    private void readInitialProperty(int propertyId) {
        try {
            Class<?> valueClass = isByteProperty(propertyId) ? byte[].class : Integer.class;
            Method getProperty = vendorManager.getClass().getMethod(
                    "getProperty", Class.class, int.class, int.class);
            Object value = getProperty.invoke(vendorManager, valueClass, propertyId, 0);
            acceptProperty(propertyId, value);
        } catch (Throwable error) {
            appendFailure("Начальное чтение не удалось для " + propertyId, error);
        }
    }

    private void handleCarPropertyValue(Object propertyValue) {
        try {
            int propertyId = ((Integer) propertyValue.getClass()
                    .getMethod("getPropertyId").invoke(propertyValue)).intValue();
            Object value = propertyValue.getClass().getMethod("getValue").invoke(propertyValue);
            acceptProperty(propertyId, value);
        } catch (Throwable error) {
            appendFailure("Ошибка разбора CarPropertyValue", error);
        }
    }

    private void acceptProperty(final int propertyId, final Object value) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                if (propertyId == PROP_ESTIMATED_COUPLING_TORQUE) {
                    rawEstimatedCouplingTorque = decodeLittleEndianU16(value);
                } else if (propertyId == PROP_ENGINE_WHEEL_TORQUE_RATIO) {
                    rawEngineWheelTorqueRatio = decodeLittleEndianU16(value);
                } else if (propertyId == PROP_MEAN_EFFECTIVE_TORQUE) {
                    rawMeanEffectiveTorque = decodeLittleEndianU16(value);
                } else if (propertyId == PROP_ENGINE_STATE && value instanceof Number) {
                    engineState = ((Number) value).intValue();
                } else if (propertyId == PROP_LEVER_MODE && value instanceof Number) {
                    leverMode = ((Number) value).intValue();
                }
                calculateStockEnergyFlow();
                append(propertyName(propertyId) + " = " + valueText(value)
                        + " → перед " + (frontPerWheel * 2) + "% / зад " + (rearPerWheel * 2) + "%");
                if (presentation != null) {
                    presentation.update(frontPerWheel, rearPerWheel, signalSummary());
                    presentation.setRadarIdle(shouldShowRadarIdle());
                    presentation.setBlankIdle(shouldShowBlankIdle());
                }
                if (overlayView != null) {
                    overlayView.update(displayFrontPerWheel(), displayRearPerWheel(), signalSummary());
                    overlayView.setRadarIdle(shouldShowRadarIdle());
                    overlayView.setBlankIdle(shouldShowBlankIdle());
                }
            }
        });
    }

    private void calculateStockEnergyFlow() {
        float staticEngineTorque = rawMeanEffectiveTorque < 0
                ? 0f : rawMeanEffectiveTorque * 0.5f - 1000f;
        float estimatedCouplingTorque = rawEstimatedCouplingTorque < 0
                ? 0f : rawEstimatedCouplingTorque * 0.25f;
        float ratio = rawEngineWheelTorqueRatio < 0
                ? 0f : rawEngineWheelTorqueRatio * 0.01f;

        frontPerWheel = 0;
        rearPerWheel = 0;
        if (engineState != 1 || leverMode == 1 || leverMode == 3) return;
        boolean drivingGear = leverMode == 2 || (leverMode >= 4 && leverMode <= 30);
        if (!drivingGear || staticEngineTorque <= 0f || ratio <= 0f) return;

        float stockValue = estimatedCouplingTorque * 2.47f
                / (staticEngineTorque * ratio) * 50f;
        int rounded = Math.round(stockValue);
        if (rounded >= 0 && rounded <= 50) {
            // EstimatedCouplingTorque describes the rear coupling load.
            // Keep the stock calculation, but map its result to the rear axle.
            rearPerWheel = rounded;
            frontPerWheel = 50 - rounded;
        } else if (rounded > 50) {
            frontPerWheel = 0;
            rearPerWheel = 50;
        }
    }

    private int decodeLittleEndianU16(Object value) {
        if (!(value instanceof byte[])) return -1;
        byte[] bytes = (byte[]) value;
        if (bytes.length < 2) return -1;
        return (bytes[0] & 0xff) | ((bytes[1] & 0xff) << 8);
    }

    private boolean isByteProperty(int propertyId) {
        return propertyId == PROP_ESTIMATED_COUPLING_TORQUE
                || propertyId == PROP_ENGINE_WHEEL_TORQUE_RATIO
                || propertyId == PROP_MEAN_EFFECTIVE_TORQUE;
    }

    private String propertyName(int propertyId) {
        switch (propertyId) {
            case PROP_ESTIMATED_COUPLING_TORQUE: return "EstimatedCouplingTorque";
            case PROP_ENGINE_WHEEL_TORQUE_RATIO: return "EngWhlTrqRatio";
            case PROP_MEAN_EFFECTIVE_TORQUE: return "MeanEffectiveTorque";
            case PROP_ENGINE_STATE: return "ENGINE_STATE";
            case PROP_LEVER_MODE: return "STAT_LEVER_MODE";
            default: return "property";
        }
    }

    private String signalSummary() {
        return "raw: coupling=" + rawEstimatedCouplingTorque
                + " ratio=" + rawEngineWheelTorqueRatio
                + " torque=" + rawMeanEffectiveTorque
                + " engine=" + engineState
                + " lever=" + leverMode;
    }

    private int displayFrontPerWheel() {
        return demoFrontPerWheel >= 0 ? demoFrontPerWheel : frontPerWheel;
    }

    private int displayRearPerWheel() {
        return demoRearPerWheel >= 0 ? demoRearPerWheel : rearPerWheel;
    }

    private boolean hasAwdSignalData() {
        return demoFrontPerWheel >= 0 || demoRearPerWheel >= 0
                || rawEstimatedCouplingTorque >= 0
                || rawEngineWheelTorqueRatio >= 0
                || rawMeanEffectiveTorque >= 0;
    }

    private boolean shouldShowRadarIdle() {
        int mode = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(PREF_IDLE_MODE, IDLE_MODE_AWD);
        if (mode == IDLE_MODE_RADAR) return true;
        return false;
    }

    private boolean shouldShowBlankIdle() {
        return getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(PREF_IDLE_MODE, IDLE_MODE_AWD) == IDLE_MODE_BLANK;
    }

    private boolean shouldShowRouteIdle() {
        return getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(PREF_IDLE_MODE, IDLE_MODE_AWD) == IDLE_MODE_ROUTE;
    }

    private void updateOverlayIdleMode() {
        if (instrumentMapFullScreenActive && !shouldShowRouteIdle()) {
            returnInstrumentMapToMini("Instrument widget mode changed");
            return;
        }
        boolean radarIdle = shouldShowRadarIdle();
        if (overlayView != null) overlayView.setRadarIdle(radarIdle);
        if (overlayView != null) overlayView.setBlankIdle(shouldShowBlankIdle());
        if (presentation != null) presentation.setRadarIdle(radarIdle);
        if (presentation != null) presentation.setBlankIdle(shouldShowBlankIdle());
        updateInstrumentMapMode();
        syncFakeTeleNavNavigationOutputs();
        updateSteeringInputShortcut();
    }

    private void updateInstrumentMapMode() {
        if (overlayContentRoot == null || overlayView == null) return;
        boolean mapRequested = shouldShowRouteIdle();
        boolean mapEnabled = mapRequested && RoadAssistantApplication.hasMapKitApiKey();
        overlayView.setMapIdle(mapEnabled);
        if (mapEnabled && overlayMapView == null) {
            overlayMapView = new InstrumentMapSurfaceView(overlayContentRoot.getContext());
            // Keep the map centred on the original instrument card while giving
            // it substantially more room in both directions.
            FrameLayout.LayoutParams mapParams = new FrameLayout.LayoutParams(637, 500);
            mapParams.leftMargin = 0;
            mapParams.topMargin = 0;
            overlayContentRoot.addView(overlayMapView, 0, mapParams);
            if (activeCamera != null) overlayMapView.showCamera(activeCamera);
            if (!yandexRoutePoints.isEmpty()) {
                overlayMapView.updateNavigatorRoute(yandexRoutePoints);
            }
            updateInstrumentMapFromBestPosition();
            overlayRouteGuidanceView = new MiniRouteGuidanceView(
                    overlayContentRoot.getContext());
            overlayRouteGuidanceView.update(yandexManeuverResourceId, yandexNextRoadName,
                    yandexManeuverDistanceMeters, yandexRouteDistanceMeters,
                    yandexRouteTimeSeconds, yandexManeuverVisible,
                    !yandexRoutePoints.isEmpty());
            attachMiniRouteGuidanceOverlayIfNeeded();
            append("Instrument mode: active route map 637x500 with guidance chrome");
        } else if (!mapEnabled && overlayMapView != null) {
            dismissMiniRouteGuidanceOverlay();
            overlayContentRoot.removeView(overlayMapView);
            overlayMapView = null;
        }
        if (mapRequested && !mapEnabled) {
            append("Instrument map is unavailable: MAPKIT_API_KEY is missing");
        }
    }

    private boolean isInstrumentOverlayAttached() {
        return overlayRoot != null && overlayRoot.isAttachedToWindow();
    }

    private void attachMiniRouteGuidanceOverlayIfNeeded() {
        if (overlayRouteGuidanceView == null || miniRouteGuidanceWindowManager != null
                || overlayRoot == null || !overlayRoot.isAttachedToWindow()) return;
        Display display = findInstrumentDisplay();
        if (display == null) return;
        Context displayContext = createDisplayContext(display);
        miniRouteGuidanceWindowManager =
                (WindowManager) displayContext.getSystemService(Context.WINDOW_SERVICE);
        if (miniRouteGuidanceWindowManager == null) return;
        int requestedWindowType = WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                637, 500, requestedWindowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.RIGHT;
        params.x = 0;
        params.y = 0;
        params.setTitle("Instrument route guidance");
        try {
            miniRouteGuidanceWindowManager.addView(overlayRouteGuidanceView, params);
            if (activeCamera != null) {
                float distance = demoAlertActive
                        ? activeMinimumDistance
                        : lastLocation == null ? 0f : SpeedCameraIndex.distanceMeters(
                                lastLocation.getLatitude(), lastLocation.getLongitude(),
                                activeCamera.latitude, activeCamera.longitude);
                overlayRouteGuidanceView.updateCameraAlert(activeCamera, distance);
            }
            if (instrumentMapFullScreenActive) {
                overlayRouteGuidanceView.setVisibility(View.INVISIBLE);
            }
            Log.i(TAG, "Mini route guidance overlay attached to displayId="
                    + display.getDisplayId());
        } catch (RuntimeException systemOverlayDenied) {
            Log.w(TAG, "Mini route SYSTEM_OVERLAY denied; trying APPLICATION_OVERLAY",
                    systemOverlayDenied);
            try {
                params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
                miniRouteGuidanceWindowManager.addView(overlayRouteGuidanceView, params);
                if (activeCamera != null) {
                    float distance = demoAlertActive
                            ? activeMinimumDistance
                            : lastLocation == null ? 0f : SpeedCameraIndex.distanceMeters(
                                    lastLocation.getLatitude(), lastLocation.getLongitude(),
                                    activeCamera.latitude, activeCamera.longitude);
                    overlayRouteGuidanceView.updateCameraAlert(activeCamera, distance);
                }
                if (instrumentMapFullScreenActive) {
                    overlayRouteGuidanceView.setVisibility(View.INVISIBLE);
                }
                Log.i(TAG, "Mini route guidance overlay attached with fallback to displayId="
                        + display.getDisplayId());
            } catch (Throwable fallbackError) {
                Log.w(TAG, "Mini route guidance overlay attach failed", fallbackError);
                miniRouteGuidanceWindowManager = null;
            }
        }
    }

    private void dismissMiniRouteGuidanceOverlay() {
        if (overlayRouteGuidanceView != null && miniRouteGuidanceWindowManager != null) {
            try {
                miniRouteGuidanceWindowManager.removeViewImmediate(overlayRouteGuidanceView);
            } catch (Throwable error) {
                Log.w(TAG, "Mini route guidance overlay removal failed", error);
            }
        }
        overlayRouteGuidanceView = null;
        miniRouteGuidanceWindowManager = null;
    }

    private String valueText(Object value) {
        return value instanceof byte[] ? Arrays.toString((byte[]) value) : String.valueOf(value);
    }

    private void appendFailure(String prefix, Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        append(prefix + ": " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
        Log.e(TAG, prefix, error);
    }

    private void append(final String text) {
        if (logView == null) return;
        if (Thread.currentThread() != getMainLooper().getThread()) {
            runOnUiThread(new Runnable() { @Override public void run() { append(text); } });
            return;
        }
        logView.append(text + "\n");
        Log.i(TAG, text);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onBackPressed() {
        if (isAwdEnabled()) {
            moveTaskToBack(true);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (activeInstance == this) activeInstance = null;
        overlayHandler.removeCallbacks(showPersistentOverlay);
        overlayHandler.removeCallbacks(clearDemoAlert);
        overlayHandler.removeCallbacks(cameraLocationWatchdog);
        overlayHandler.removeCallbacks(navigatorRouteFreshnessWatchdog);
        stopCameraMonitoring();
        releaseAlertSound();
        releaseSteeringInputShortcut();
        dismissClusterGuidanceOverlay();
        dismissInstrumentOverlay();
        dismissPresentation();
        disconnectFromCarService();
        if (ipkBound) {
            try {
                unregisterIpkKeyCallback();
                unregisterIpkNaviCallback();
                unbindService(ipkConnection);
            } catch (Throwable error) {
                Log.w(TAG, "IPKService unbind failed", error);
            }
        }
        ipkBound = false;
        ipkBindingRequested = false;
        ipkBinder = null;
        ipkKeyCallbackRegistered = false;
        if (locationReceiverRegistered) {
            try { unregisterReceiver(locationUpdateReceiver); }
            catch (Throwable error) { Log.w(TAG, "Location receiver removal failed", error); }
            locationReceiverRegistered = false;
        }
        if (yandexGuidanceReceiverRegistered) {
            try { unregisterReceiver(yandexGuidanceReceiver); }
            catch (Throwable error) { Log.w(TAG, "Yandex guidance receiver removal failed", error); }
            yandexGuidanceReceiverRegistered = false;
        }
        super.onDestroy();
    }

    /** Compact route chrome drawn above the MapKit view in the small cluster slot. */
    private static final class MiniRouteGuidanceView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final Path arrow = new Path();
        private String maneuverResourceId = "";
        private String nextRoadName = "";
        private int maneuverDistanceMeters;
        private int routeDistanceMeters;
        private int routeTimeSeconds;
        private boolean maneuverVisible;
        private boolean routeAvailable;
        private SpeedCamera alertCamera;
        private float alertDistanceMeters;

        MiniRouteGuidanceView(Context context) {
            super(context);
            setBackgroundColor(Color.TRANSPARENT);
            setClickable(false);
        }

        void update(String resourceId, String roadName, int maneuverDistance,
                    int routeDistance, int routeTime, boolean visible,
                    boolean hasRouteGeometry) {
            maneuverResourceId = resourceId == null ? "" : resourceId;
            nextRoadName = roadName == null ? "" : roadName;
            maneuverDistanceMeters = Math.max(0, maneuverDistance);
            routeDistanceMeters = Math.max(0, routeDistance);
            routeTimeSeconds = Math.max(0, routeTime);
            maneuverVisible = visible;
            routeAvailable = hasRouteGeometry || visible
                    || routeDistanceMeters > 0 || routeTimeSeconds > 0;
            invalidate();
        }

        void updateCameraAlert(SpeedCamera camera, float distanceMeters) {
            alertCamera = camera;
            alertDistanceMeters = Math.max(0f, distanceMeters);
            invalidate();
        }

        void clearCameraAlert() {
            alertCamera = null;
            alertDistanceMeters = 0f;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float sx = getWidth() / 637f;
            float sy = getHeight() / 500f;
            float scale = Math.max(0.65f, Math.min(sx, sy));
            if (alertCamera != null) {
                drawCameraAlert(canvas, scale);
                return;
            }
            if (!routeAvailable) {
                drawNoRoute(canvas, scale);
                return;
            }
            if (maneuverVisible && (!maneuverResourceId.isEmpty()
                    || maneuverDistanceMeters > 0 || !nextRoadName.isEmpty())) {
                drawManeuver(canvas, scale);
            }
            if (routeDistanceMeters > 0 || routeTimeSeconds > 0) {
                drawProgress(canvas, scale);
            }
        }

        private void drawCameraAlert(Canvas canvas, float scale) {
            float left = 86f * scale;
            float top = 118f * scale;
            float width = 465f * scale;
            float height = 230f * scale;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(244, 9, 10, 9));
            canvas.drawRoundRect(new RectF(left, top, left + width, top + height),
                    28f * scale, 28f * scale, paint);

            float signX = left + 112f * scale;
            float signY = top + 93f * scale;
            paint.setColor(Color.WHITE);
            canvas.drawCircle(signX, signY, 58f * scale, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(10f * scale);
            paint.setColor(Color.rgb(218, 69, 54));
            canvas.drawCircle(signX, signY, 53f * scale, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(22, 22, 21));
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize((alertCamera.speed >= 100 ? 39f : 46f) * scale);
            canvas.drawText(alertCamera.speed > 0
                            ? String.valueOf(alertCamera.speed) : "!",
                    signX, signY + 16f * scale, paint);

            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSize(31f * scale);
            paint.setColor(Color.WHITE);
            canvas.drawText("Камера", left + 205f * scale,
                    top + 78f * scale, paint);
            paint.setTextSize(39f * scale);
            canvas.drawText(ClusterGuidanceView.formatDistance(
                            Math.max(0, Math.round(alertDistanceMeters))),
                    left + 205f * scale, top + 129f * scale, paint);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            paint.setTextSize(21f * scale);
            paint.setColor(Color.argb(220, 255, 255, 255));
            String detail = alertCamera.speed > 0
                    ? "Ограничение " + alertCamera.speed + " км/ч"
                    : "Контроль движения";
            canvas.drawText(detail, left + 42f * scale,
                    top + 197f * scale, paint);
        }

        private void drawNoRoute(Canvas canvas, float scale) {
            float left = 86f * scale;
            float top = 202f * scale;
            float width = 465f * scale;
            float height = 74f * scale;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(220, 25, 27, 31));
            canvas.drawRoundRect(new RectF(left, top, left + width, top + height),
                    20f * scale, 20f * scale, paint);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(25f * scale);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(Color.WHITE);
            canvas.drawText("Маршрут не построен", left + width / 2f,
                    top + 47f * scale, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }

        private void drawManeuver(Canvas canvas, float scale) {
            float left = 16f * scale;
            float top = 16f * scale;
            float width = 430f * scale;
            float height = nextRoadName.isEmpty() ? 98f * scale : 126f * scale;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(242, 17, 105, 224));
            canvas.drawRoundRect(new RectF(left, top, left + width, top + height),
                    20f * scale, 20f * scale, paint);

            drawArrow(canvas, left + 54f * scale, top + 48f * scale, scale);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(35f * scale);
            paint.setColor(Color.WHITE);
            canvas.drawText(ClusterGuidanceView.formatDistance(maneuverDistanceMeters),
                    left + 104f * scale, top + 61f * scale, paint);
            if (!nextRoadName.isEmpty()) {
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
                paint.setTextSize(22f * scale);
                paint.setColor(Color.argb(230, 255, 255, 255));
                canvas.drawText(ClusterGuidanceView.ellipsize(nextRoadName, 28),
                        left + 20f * scale, top + 105f * scale, paint);
            }
        }

        private void drawProgress(Canvas canvas, float scale) {
            float left = 16f * scale;
            float top = 418f * scale;
            float width = 520f * scale;
            float height = 64f * scale;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(230, 25, 27, 31));
            canvas.drawRoundRect(new RectF(left, top, left + width, top + height),
                    17f * scale, 17f * scale, paint);
            String distance = routeDistanceMeters > 0
                    ? ClusterGuidanceView.formatDistance(routeDistanceMeters) : "—";
            String duration = routeTimeSeconds > 0
                    ? ClusterGuidanceView.formatDuration(routeTimeSeconds) : "—";
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(24f * scale);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setColor(Color.WHITE);
            canvas.drawText(distance + "   " + duration,
                    left + 20f * scale, top + 41f * scale, paint);
            if (routeTimeSeconds > 0) {
                long arrivalMs = System.currentTimeMillis() + routeTimeSeconds * 1000L;
                java.text.SimpleDateFormat formatter =
                        new java.text.SimpleDateFormat("HH:mm", Locale.getDefault());
                paint.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(formatter.format(new java.util.Date(arrivalMs)),
                        left + width - 20f * scale, top + 41f * scale, paint);
            }
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setColor(Color.rgb(54, 196, 95));
            canvas.drawRoundRect(new RectF(left + 12f * scale,
                            top + height - 6f * scale, left + width - 12f * scale,
                            top + height - 2f * scale),
                    2f * scale, 2f * scale, paint);
        }

        private void drawArrow(Canvas canvas, float cx, float cy, float scale) {
            String id = maneuverResourceId.toLowerCase(Locale.US).replace('-', '_');
            boolean left = id.contains("left");
            boolean right = id.contains("right");
            boolean uturn = id.contains("uturn") || id.contains("u_turn");
            float direction = right ? 1f : left ? -1f : 0f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(8f * scale);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(Color.WHITE);
            arrow.reset();
            if (uturn) {
                if (direction == 0f) direction = -1f;
                arrow.moveTo(cx, cy + 25f * scale);
                arrow.lineTo(cx, cy - 7f * scale);
                arrow.quadTo(cx, cy - 27f * scale,
                        cx + direction * 25f * scale, cy - 27f * scale);
                arrow.lineTo(cx + direction * 34f * scale, cy - 27f * scale);
                canvas.drawPath(arrow, paint);
                drawArrowHead(canvas, cx + direction * 34f * scale,
                        cy - 27f * scale, direction, 0f, scale);
            } else if (direction != 0f) {
                arrow.moveTo(cx, cy + 27f * scale);
                arrow.lineTo(cx, cy - 3f * scale);
                arrow.lineTo(cx + direction * 28f * scale, cy - 28f * scale);
                canvas.drawPath(arrow, paint);
                drawArrowHead(canvas, cx + direction * 28f * scale,
                        cy - 28f * scale, direction, -1f, scale);
            } else {
                canvas.drawLine(cx, cy + 27f * scale, cx, cy - 29f * scale, paint);
                drawArrowHead(canvas, cx, cy - 29f * scale, 0f, -1f, scale);
            }
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawArrowHead(Canvas canvas, float x, float y,
                                   float dx, float dy, float scale) {
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length < 0.1f) return;
            dx /= length;
            dy /= length;
            float px = -dy;
            float py = dx;
            float back = 17f * scale;
            float wing = 10f * scale;
            arrow.reset();
            arrow.moveTo(x, y);
            arrow.lineTo(x - dx * back + px * wing, y - dy * back + py * wing);
            arrow.moveTo(x, y);
            arrow.lineTo(x - dx * back - px * wing, y - dy * back - py * wing);
            canvas.drawPath(arrow, paint);
        }
    }

    /** Transparent route chrome drawn above Yandex ClusterMapActivity only. */
    private static final class ClusterGuidanceView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path arrow = new Path();
        private String maneuverResourceId = "";
        private String nextRoadName = "";
        private int maneuverDistanceMeters;
        private int routeDistanceMeters;
        private int routeTimeSeconds;
        private boolean maneuverVisible;
        private int maneuverPosition;

        ClusterGuidanceView(Context context) {
            super(context);
            setBackgroundColor(Color.TRANSPARENT);
        }

        void update(String resourceId, String roadName, int maneuverDistance,
                    int routeDistance, int routeTime, boolean visible,
                    int position) {
            maneuverResourceId = resourceId == null ? "" : resourceId;
            nextRoadName = roadName == null ? "" : roadName;
            maneuverDistanceMeters = Math.max(0, maneuverDistance);
            routeDistanceMeters = Math.max(0, routeDistance);
            routeTimeSeconds = Math.max(0, routeTime);
            maneuverVisible = visible;
            maneuverPosition = position;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float sx = getWidth() / 1920f;
            float sy = getHeight() / 720f;
            float scale = Math.max(0.75f, Math.min(sx, sy));
            if (maneuverVisible && (!maneuverResourceId.isEmpty()
                    || maneuverDistanceMeters > 0)) {
                drawManeuverCard(canvas, scale);
            }
            if (routeDistanceMeters > 0 || routeTimeSeconds > 0) {
                drawRouteProgress(canvas, scale);
            }
        }

        private void drawManeuverCard(Canvas canvas, float scale) {
            float x;
            float y;
            switch (maneuverPosition) {
                case 1:
                    x = 24f * scale;
                    y = 26f * scale;
                    break;
                case 2:
                    x = 24f * scale;
                    y = 210f * scale;
                    break;
                case 3:
                    x = 1488f * scale;
                    y = 530f * scale;
                    break;
                case CLUSTER_MANEUVER_POSITION_UPPER_INNER:
                default:
                    // Keep the card clear of the transmission/temperature strip and
                    // move it towards the speed-limit area requested by the owner.
                    x = 1310f * scale;
                    y = 90f * scale;
                    break;
            }
            float width = 400f * scale;
            float height = nextRoadName.isEmpty() ? 124f * scale : 154f * scale;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(238, 17, 105, 224));
            canvas.drawRoundRect(new RectF(x, y, x + width, y + height),
                    22f * scale, 22f * scale, paint);

            drawManeuverArrow(canvas, x + 58f * scale, y + 58f * scale, scale);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(43f * scale);
            paint.setColor(Color.WHITE);
            canvas.drawText(formatDistance(maneuverDistanceMeters),
                    x + 112f * scale, y + 72f * scale, paint);
            if (!nextRoadName.isEmpty()) {
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
                paint.setTextSize(25f * scale);
                paint.setColor(Color.argb(225, 255, 255, 255));
                String road = ellipsize(nextRoadName, 23);
                canvas.drawText(road, x + 24f * scale, y + 126f * scale, paint);
            }
        }

        private void drawManeuverArrow(Canvas canvas, float cx, float cy, float scale) {
            String id = maneuverResourceId.toLowerCase(Locale.US).replace('-', '_');
            boolean left = id.contains("left");
            boolean right = id.contains("right");
            boolean uturn = id.contains("uturn") || id.contains("u_turn");
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(10f * scale);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(Color.WHITE);
            arrow.reset();
            if (uturn) {
                float direction = right ? 1f : -1f;
                arrow.moveTo(cx, cy + 31f * scale);
                arrow.lineTo(cx, cy - 9f * scale);
                arrow.quadTo(cx, cy - 34f * scale,
                        cx + direction * 28f * scale, cy - 34f * scale);
                arrow.lineTo(cx + direction * 42f * scale, cy - 34f * scale);
                canvas.drawPath(arrow, paint);
                drawArrowHead(canvas, cx + direction * 42f * scale,
                        cy - 34f * scale, direction, 0f, scale);
            } else if (left || right) {
                float direction = right ? 1f : -1f;
                arrow.moveTo(cx, cy + 34f * scale);
                arrow.lineTo(cx, cy - 4f * scale);
                arrow.lineTo(cx + direction * 34f * scale, cy - 34f * scale);
                canvas.drawPath(arrow, paint);
                drawArrowHead(canvas, cx + direction * 34f * scale,
                        cy - 34f * scale, direction, -1f, scale);
            } else {
                canvas.drawLine(cx, cy + 34f * scale, cx, cy - 36f * scale, paint);
                drawArrowHead(canvas, cx, cy - 36f * scale, 0f, -1f, scale);
            }
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawArrowHead(Canvas canvas, float x, float y,
                                   float dx, float dy, float scale) {
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length < 0.1f) return;
            dx /= length;
            dy /= length;
            float px = -dy;
            float py = dx;
            float back = 22f * scale;
            float wing = 13f * scale;
            arrow.reset();
            arrow.moveTo(x, y);
            arrow.lineTo(x - dx * back + px * wing, y - dy * back + py * wing);
            arrow.moveTo(x, y);
            arrow.lineTo(x - dx * back - px * wing, y - dy * back - py * wing);
            canvas.drawPath(arrow, paint);
        }

        private void drawRouteProgress(Canvas canvas, float scale) {
            float width = 530f * scale;
            float height = 69f * scale;
            // Keep the summary centred horizontally. The previous 79.5% anchor
            // moved it too far right and placed it over the coolant/fuel area.
            float x = (getWidth() - width) * 0.5f;
            float y = 590f * scale;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(220, 25, 27, 31));
            canvas.drawRoundRect(new RectF(x, y, x + width, y + height),
                    18f * scale, 18f * scale, paint);

            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(29f * scale);
            paint.setColor(Color.WHITE);
            String distance = routeDistanceMeters > 0
                    ? formatDistance(routeDistanceMeters) : "—";
            String duration = routeTimeSeconds > 0
                    ? formatDuration(routeTimeSeconds) : "—";
            canvas.drawText(distance + "   " + duration,
                    x + 28f * scale, y + 44f * scale, paint);

            if (routeTimeSeconds > 0) {
                long arrivalMs = System.currentTimeMillis() + routeTimeSeconds * 1000L;
                java.text.SimpleDateFormat timeFormat =
                        new java.text.SimpleDateFormat("HH:mm", Locale.getDefault());
                String eta = timeFormat.format(new java.util.Date(arrivalMs));
                paint.setTextAlign(Paint.Align.RIGHT);
                paint.setColor(Color.argb(220, 255, 255, 255));
                canvas.drawText(eta, x + width - 26f * scale, y + 44f * scale, paint);
                paint.setTextAlign(Paint.Align.LEFT);
            }
            paint.setColor(Color.rgb(54, 196, 95));
            canvas.drawRoundRect(new RectF(x + 15f * scale, y + height - 7f * scale,
                    x + width - 15f * scale, y + height - 3f * scale),
                    2f * scale, 2f * scale, paint);
        }

        private static String formatDistance(int meters) {
            if (meters >= 10000) return Math.round(meters / 1000f) + " км";
            if (meters >= 1000) {
                return String.format(Locale.getDefault(), "%.1f км", meters / 1000f);
            }
            return meters + " м";
        }

        private static String formatDuration(int seconds) {
            int minutes = Math.max(1, (int) Math.ceil(seconds / 60d));
            if (minutes < 60) return minutes + " мин";
            int hours = minutes / 60;
            int rest = minutes % 60;
            return rest == 0 ? hours + " ч" : hours + " ч " + rest + " мин";
        }

        private static String ellipsize(String value, int maxChars) {
            if (value == null || value.length() <= maxChars) return value == null ? "" : value;
            return value.substring(0, Math.max(1, maxChars - 1)) + "…";
        }
    }

    private static final class AwdPresentation extends Presentation {
        private AwdView awdView;

        AwdPresentation(Context context, Display display) {
            super(context, display);
        }

        @Override protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            awdView = new AwdView(getContext());
            setContentView(awdView);
        }

        void update(int front, int rear, String details) {
            if (awdView != null) awdView.update(front, rear, details);
        }

        void setRadarIdle(boolean radarIdle) {
            if (awdView != null) awdView.setRadarIdle(radarIdle);
        }

        void setBlankIdle(boolean blankIdle) {
            if (awdView != null) awdView.setBlankIdle(blankIdle);
        }
    }

    private static final class AwdView extends View {
        private static final long ALERT_FADE_IN_MS = 380L;
        private static final long ALERT_FADE_OUT_MS = 520L;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float targetFrontAxle;
        private float targetRearAxle;
        private float displayedFrontAxle;
        private float displayedRearAxle;
        private long lastFrameTimeMs;
        private String details = "ожидание сигналов";
        private SpeedCamera alertCamera;
        private float alertDistanceMeters;
        private long alertFadeInStartedAtMs;
        private long alertFadeOutStartedAtMs;
        private boolean radarIdle;
        private boolean mapIdle;
        private boolean blankIdle;

        AwdView(Context context) {
            super(context);
            setBackgroundColor(Color.TRANSPARENT);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        void update(int front, int rear, String details) {
            targetFrontAxle = Math.max(0f, Math.min(100f, front * 2f));
            targetRearAxle = Math.max(0f, Math.min(100f, rear * 2f));
            this.details = details;
            postInvalidateOnAnimation();
        }

        void setRadarIdle(boolean radarIdle) {
            if (this.radarIdle == radarIdle) return;
            this.radarIdle = radarIdle;
            lastFrameTimeMs = 0L;
            postInvalidateOnAnimation();
        }

        void setMapIdle(boolean mapIdle) {
            if (this.mapIdle == mapIdle) return;
            this.mapIdle = mapIdle;
            lastFrameTimeMs = 0L;
            postInvalidateOnAnimation();
        }

        void setBlankIdle(boolean blankIdle) {
            if (this.blankIdle == blankIdle) return;
            this.blankIdle = blankIdle;
            lastFrameTimeMs = 0L;
            postInvalidateOnAnimation();
        }

        void updateCameraAlert(SpeedCamera camera, float distanceMeters) {
            boolean startFadeIn = alertCamera == null
                    || alertCamera.id != camera.id
                    || alertFadeOutStartedAtMs > 0L;
            alertCamera = camera;
            alertDistanceMeters = Math.max(0f, distanceMeters);
            if (startFadeIn) alertFadeInStartedAtMs = SystemClock.uptimeMillis();
            alertFadeOutStartedAtMs = 0L;
            postInvalidateOnAnimation();
        }

        void clearCameraAlert() {
            if (alertCamera == null || alertFadeOutStartedAtMs > 0L) return;
            alertFadeOutStartedAtMs = SystemClock.uptimeMillis();
            postInvalidateOnAnimation();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            long frameTimeMs = SystemClock.uptimeMillis();
            float deltaSeconds = lastFrameTimeMs == 0L
                    ? 1f / 60f
                    : Math.min(0.05f, (frameTimeMs - lastFrameTimeMs) / 1000f);
            lastFrameTimeMs = frameTimeMs;
            displayedFrontAxle = smoothLoad(displayedFrontAxle, targetFrontAxle, deltaSeconds);
            displayedRearAxle = smoothLoad(displayedRearAxle, targetRearAxle, deltaSeconds);
            int frontAxle = Math.round(displayedFrontAxle);
            int rearAxle = Math.round(displayedRearAxle);
            float scale = Math.max(0.75f, Math.min(w, h) / 637f);

            RectF panel = new RectF(w * 0.405f, h * 0.13f, w * 0.84f, h * 0.61f);
            if ((mapIdle || blankIdle) && alertCamera == null) return;
            // Keep the normal AWD visualization open over the stock cluster background.
            // Camera and radar states retain a dark panel for text/icon readability.
            if (alertCamera != null || radarIdle) {
                paint.clearShadowLayer();
                paint.setShader(null);
                paint.setAlpha(255);
                paint.setPathEffect(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(238, 9, 10, 9));
                canvas.drawRoundRect(panel, w * 0.04f, w * 0.04f, paint);
            }

            if (alertCamera != null) {
                float alertAlpha;
                boolean transitionRunning;
                if (alertFadeOutStartedAtMs > 0L) {
                    float progress = Math.min(1f,
                            (frameTimeMs - alertFadeOutStartedAtMs) / (float) ALERT_FADE_OUT_MS);
                    alertAlpha = 1f - smoothStep(progress);
                    transitionRunning = progress < 1f;
                    if (!transitionRunning) {
                        alertCamera = null;
                        alertDistanceMeters = 0f;
                        alertFadeInStartedAtMs = 0L;
                        alertFadeOutStartedAtMs = 0L;
                        lastFrameTimeMs = 0L;
                        postInvalidateOnAnimation();
                    }
                } else {
                    float progress = alertFadeInStartedAtMs <= 0L ? 1f : Math.min(1f,
                            (frameTimeMs - alertFadeInStartedAtMs) / (float) ALERT_FADE_IN_MS);
                    alertAlpha = smoothStep(progress);
                    transitionRunning = progress < 1f;
                }

                if (alertCamera != null && alertAlpha > 0f) {
                    int layerAlpha = Math.max(0, Math.min(255,
                            Math.round(alertAlpha * 255f)));
                    float transitionScale = 0.975f + alertAlpha * 0.025f;
                    canvas.saveLayerAlpha(panel, layerAlpha);
                    canvas.scale(transitionScale, transitionScale,
                            panel.centerX(), panel.centerY());
                    drawCameraAlert(canvas, panel, alertCamera,
                            alertDistanceMeters, scale);
                    canvas.restore();
                    if (transitionRunning) postInvalidateOnAnimation();
                    return;
                }
            }

            if (radarIdle) {
                drawRadarIdle(canvas, panel, frameTimeMs, scale);
                postInvalidateOnAnimation();
                return;
            }

            // A universal perspective diagram: the front axle is narrower and
            // smaller, while the rear axle is wider and visually closer.
            float frontY = panel.top + panel.height() * 0.27f;
            float rearY = panel.top + panel.height() * 0.72f;
            float frontLeft = panel.left + panel.width() * 0.255f;
            float frontRight = panel.left + panel.width() * 0.745f;
            float rearLeft = panel.left + panel.width() * 0.146f;
            float rearRight = panel.left + panel.width() * 0.854f;
            float frontCenterX = (frontLeft + frontRight) / 2f;
            float rearCenterX = (rearLeft + rearRight) / 2f;
            float frontGrow = 1f + loadFactor(frontAxle) * 0.14f;
            float rearGrow = 1f + loadFactor(rearAxle) * 0.14f;
            float frontLineInset = (8f * frontGrow + 3f) * scale;
            float rearLineInset = (15f * rearGrow + 3f) * scale;

            drawDriveLine(canvas, frontCenterX, frontY,
                    rearCenterX, rearY, Math.max(frontAxle, rearAxle), scale);

            drawWheelGlow(canvas, frontLeft, frontY, 8f, 26f,
                    frontAxle, scale);
            drawWheelGlow(canvas, frontRight, frontY, 8f, 26f,
                    frontAxle, scale);
            drawWheelGlow(canvas, rearLeft, rearY, 15f, 44f,
                    rearAxle, scale);
            drawWheelGlow(canvas, rearRight, rearY, 15f, 44f,
                    rearAxle, scale);

            drawAxleLine(canvas, frontLeft + frontLineInset, frontY,
                    frontRight - frontLineInset, frontY,
                    frontAxle, scale);
            drawAxleLine(canvas, rearLeft + rearLineInset, rearY,
                    rearRight - rearLineInset, rearY,
                    rearAxle, scale);

            drawAxlePercent(canvas, frontCenterX, frontY, frontAxle, scale);
            drawAxlePercent(canvas, rearCenterX, rearY, rearAxle, scale);

            paint.clearShadowLayer();
            paint.setPathEffect(null);
            postInvalidateOnAnimation();
        }

        private void drawRadarIdle(Canvas canvas, RectF panel, long frameTimeMs,
                                   float scale) {
            float cx = panel.centerX();
            float cy = panel.centerY();
            float radius = Math.min(panel.width(), panel.height()) * 0.34f;
            int warmWhite = Color.rgb(224, 214, 199);
            int copper = Color.rgb(196, 126, 75);

            canvas.save();
            canvas.clipRect(panel);
            paint.clearShadowLayer();
            paint.setShader(null);
            paint.setPathEffect(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);

            paint.setStrokeWidth(1.2f * scale);
            paint.setColor(warmWhite);
            paint.setAlpha(38);
            for (int ring = 1; ring <= 3; ring++) {
                canvas.drawCircle(cx, cy, radius * ring / 3f, paint);
            }
            canvas.drawLine(cx - radius, cy, cx + radius, cy, paint);
            canvas.drawLine(cx, cy - radius, cx, cy + radius, paint);

            float sweepAngle = (frameTimeMs % 8000L) * 360f / 8000f - 90f;
            for (int trail = 8; trail >= 0; trail--) {
                float angle = (float) Math.toRadians(sweepAngle - trail * 3.5f);
                float endX = cx + (float) Math.cos(angle) * radius;
                float endY = cy + (float) Math.sin(angle) * radius;
                paint.setColor(copper);
                paint.setAlpha(18 + (8 - trail) * 16);
                paint.setStrokeWidth((1.2f + (8 - trail) * 0.16f) * scale);
                if (trail == 0) {
                    paint.setShadowLayer(7f * scale, 0f, 0f,
                            Color.argb(150, 196, 126, 75));
                }
                canvas.drawLine(cx, cy, endX, endY, paint);
                paint.clearShadowLayer();
            }

            float pulse = 0.5f + 0.5f * (float) Math.sin(frameTimeMs / 520f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(copper);
            paint.setAlpha(190);
            paint.setShadowLayer((3f + pulse * 3f) * scale, 0f, 0f, copper);
            canvas.drawCircle(cx, cy, (3.8f + pulse * 0.8f) * scale, paint);
            paint.clearShadowLayer();
            paint.setAlpha(255);
            paint.setStrokeCap(Paint.Cap.BUTT);
            canvas.restore();
        }

        private void drawCameraAlert(Canvas canvas, RectF panel, SpeedCamera camera,
                                     float distanceMeters, float scale) {
            float cx = panel.centerX();
            float iconCy = panel.top + panel.height() * 0.48f;
            paint.clearShadowLayer();
            paint.setShader(null);
            paint.setPathEffect(null);
            paint.setAlpha(255);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));

            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(16f * scale);
            paint.setColor(Color.rgb(224, 214, 199));
            canvas.drawText(camera.typeLabel(), cx,
                    panel.top + 30f * scale, paint);

            if (camera.type == 3) {
                drawTrafficLight(canvas, cx, iconCy, scale);
            } else if (camera.type == 6) {
                drawRailCrossing(canvas, cx, iconCy, scale);
            } else if (camera.speed > 0) {
                drawSpeedLimit(canvas, cx, iconCy, camera.speed, scale);
            } else {
                drawCameraIcon(canvas, cx, iconCy, scale);
            }

            String distance = formatDistance(distanceMeters);
            paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            paint.setTextSize(34f * scale);
            paint.setColor(Color.rgb(235, 230, 219));
            canvas.drawText(distance, cx, panel.bottom - 30f * scale, paint);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTypeface(Typeface.DEFAULT);
        }

        private void drawSpeedLimit(Canvas canvas, float cx, float cy,
                                    int speed, float scale) {
            float radius = 68f * scale;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(245, 242, 234));
            paint.setShadowLayer(8f * scale, 0f, 0f, Color.argb(150, 0, 0, 0));
            canvas.drawCircle(cx, cy, radius, paint);
            paint.clearShadowLayer();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(10f * scale);
            paint.setColor(Color.rgb(202, 70, 58));
            canvas.drawCircle(cx, cy, radius - 5f * scale, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(28, 29, 28));
            paint.setTextSize((speed >= 100 ? 48f : 56f) * scale);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            Paint.FontMetrics metrics = paint.getFontMetrics();
            float baseline = cy - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(String.valueOf(speed), cx, baseline, paint);
        }

        private void drawTrafficLight(Canvas canvas, float cx, float cy, float scale) {
            RectF body = new RectF(cx - 34f * scale, cy - 78f * scale,
                    cx + 34f * scale, cy + 78f * scale);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(40, 42, 41));
            canvas.drawRoundRect(body, 14f * scale, 14f * scale, paint);
            float radius = 18f * scale;
            paint.setColor(Color.rgb(217, 69, 57));
            canvas.drawCircle(cx, cy - 48f * scale, radius, paint);
            paint.setColor(Color.rgb(220, 159, 67));
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setColor(Color.rgb(71, 166, 91));
            canvas.drawCircle(cx, cy + 48f * scale, radius, paint);
        }

        private void drawRailCrossing(Canvas canvas, float cx, float cy, float scale) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(14f * scale);
            paint.setColor(Color.rgb(235, 230, 219));
            canvas.drawLine(cx - 58f * scale, cy - 58f * scale,
                    cx + 58f * scale, cy + 58f * scale, paint);
            canvas.drawLine(cx + 58f * scale, cy - 58f * scale,
                    cx - 58f * scale, cy + 58f * scale, paint);
            paint.setStrokeWidth(5f * scale);
            paint.setColor(Color.rgb(196, 126, 75));
            canvas.drawLine(cx - 45f * scale, cy - 45f * scale,
                    cx + 45f * scale, cy + 45f * scale, paint);
            canvas.drawLine(cx + 45f * scale, cy - 45f * scale,
                    cx - 45f * scale, cy + 45f * scale, paint);
        }

        private void drawCameraIcon(Canvas canvas, float cx, float cy, float scale) {
            RectF body = new RectF(cx - 72f * scale, cy - 45f * scale,
                    cx + 52f * scale, cy + 45f * scale);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(224, 214, 199));
            canvas.drawRoundRect(body, 14f * scale, 14f * scale, paint);
            paint.setColor(Color.rgb(28, 29, 28));
            canvas.drawCircle(cx - 10f * scale, cy, 28f * scale, paint);
            paint.setColor(Color.rgb(196, 126, 75));
            canvas.drawCircle(cx - 10f * scale, cy, 15f * scale, paint);
            Path lens = new Path();
            lens.moveTo(cx + 52f * scale, cy - 25f * scale);
            lens.lineTo(cx + 84f * scale, cy - 42f * scale);
            lens.lineTo(cx + 84f * scale, cy + 42f * scale);
            lens.lineTo(cx + 52f * scale, cy + 25f * scale);
            lens.close();
            paint.setColor(Color.rgb(224, 214, 199));
            canvas.drawPath(lens, paint);
        }

        private String formatDistance(float distanceMeters) {
            if (distanceMeters >= 1000f) {
                return String.format(java.util.Locale.US, "%.1f км", distanceMeters / 1000f);
            }
            int rounded = Math.max(10, Math.round(distanceMeters / 10f) * 10);
            return rounded + " м";
        }

        private float smoothLoad(float current, float target, float deltaSeconds) {
            float timeConstant = target > current ? 0.22f : 0.42f;
            float blend = 1f - (float) Math.exp(-deltaSeconds / timeConstant);
            float result = current + (target - current) * blend;
            return Math.abs(target - result) < 0.03f ? target : result;
        }

        private float smoothStep(float value) {
            float clamped = Math.max(0f, Math.min(1f, value));
            return clamped * clamped * (3f - 2f * clamped);
        }

        private void drawDriveLine(Canvas canvas, float x1, float y1, float x2, float y2,
                                   int load, float scale) {
            int color = warmDriveColor(load);
            float intensity = loadFactor(load);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setPathEffect(null);
            paint.setShader(new LinearGradient(x1, y1, x2, y2,
                    warmDriveColor(load), wheelDarkColor(load), Shader.TileMode.CLAMP));
            paint.setStrokeWidth((2.4f + intensity * 1.2f) * scale);
            paint.setColor(Color.WHITE);
            paint.setAlpha(Math.round(42f + intensity * 213f));
            paint.setShadowLayer((1.5f + intensity * 2.5f) * scale, 0f, 0f, color);
            canvas.drawLine(x1, y1, x2, y2, paint);
            paint.setShader(null);
            paint.setAlpha(255);
            paint.clearShadowLayer();
        }

        private void drawAxleLine(Canvas canvas, float x1, float y1, float x2, float y2,
                                  int load, float scale) {
            int color = warmDriveColor(load);
            float intensity = loadFactor(load);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setPathEffect(null);
            paint.setShader(new LinearGradient(x1, y1, x2, y2,
                    new int[]{wheelDarkColor(load), wheelLightColor(load), wheelDarkColor(load)},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            paint.setStrokeWidth((3.2f + intensity * 2.6f) * scale);
            paint.setColor(Color.WHITE);
            paint.setAlpha(255);
            paint.setShadowLayer((1.8f + intensity * 2.5f) * scale, 0f, 0f, color);
            canvas.drawLine(x1, y1, x2, y2, paint);
            paint.setShader(null);
            paint.setAlpha(255);
            paint.clearShadowLayer();
        }

        private void drawAxleFlares(Canvas canvas, float left, float right, float y,
                                    int load, float scale) {
            float intensity = loadFactor(load);
            float depth = (9f + load * 0.055f) * scale;
            float halfHeight = (4.5f + load * 0.055f) * scale;
            int color = warmDriveColor(load);
            Path flares = new Path();
            flares.moveTo(left + depth, y);
            flares.lineTo(left, y - halfHeight);
            flares.lineTo(left, y + halfHeight);
            flares.close();
            flares.moveTo(right - depth, y);
            flares.lineTo(right, y - halfHeight);
            flares.lineTo(right, y + halfHeight);
            flares.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            paint.setAlpha(255);
            paint.setShadowLayer((1.5f + intensity * 2f) * scale, 0f, 0f, color);
            canvas.drawPath(flares, paint);
            paint.clearShadowLayer();
        }

        private void drawNode(Canvas canvas, float cx, float cy, int load, float scale) {
            float half = (4.5f + load * 0.015f) * scale;
            int color = warmDriveColor(load);
            float intensity = loadFactor(load);
            paint.setStyle(Paint.Style.FILL);
            paint.setPathEffect(null);
            paint.setColor(color);
            paint.setAlpha(255);
            paint.setShadowLayer((1.5f + intensity * 2f) * scale, 0f, 0f, color);
            canvas.drawRoundRect(new RectF(cx - half, cy - half, cx + half, cy + half),
                    2f * scale, 2f * scale, paint);
            paint.clearShadowLayer();
        }

        private void drawAxlePercent(Canvas canvas, float cx, float cy,
                                     int load, float scale) {
            String label = load + "%";
            paint.clearShadowLayer();
            paint.setShader(null);
            paint.setPathEffect(null);
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            paint.setTextSize(32f * scale);
            float textWidth = paint.measureText(label);
            float horizontalPadding = 14f * scale;
            float halfHeight = 24f * scale;
            RectF background = new RectF(
                    cx - textWidth / 2f - horizontalPadding,
                    cy - halfHeight,
                    cx + textWidth / 2f + horizontalPadding,
                    cy + halfHeight);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(13, 14, 13));
            paint.setAlpha(255);
            canvas.drawRoundRect(background, 14f * scale, 14f * scale, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f * scale);
            paint.setColor(Color.rgb(59, 56, 51));
            canvas.drawRoundRect(background, 14f * scale, 14f * scale, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(warmDriveColor(load));
            Paint.FontMetrics metrics = paint.getFontMetrics();
            float baseline = cy - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(label, cx - textWidth / 2f, baseline, paint);
            paint.setTypeface(Typeface.DEFAULT);
        }

        private void drawWheelGlow(Canvas canvas, float cx, float cy,
                                   float halfWidth, float halfHeight,
                                   int load, float scale) {
            float intensity = loadFactor(load);
            float grow = 1f + intensity * 0.14f;
            halfWidth *= grow;
            halfHeight *= grow;
            RectF wheel = new RectF(
                    cx - halfWidth * scale, cy - halfHeight * scale,
                    cx + halfWidth * scale, cy + halfHeight * scale);

            paint.setPathEffect(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(
                    wheel.left, wheel.top, wheel.right, wheel.bottom,
                    new int[]{wheelLightColor(load), warmDriveColor(load), wheelDarkColor(load)},
                    new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP));
            paint.setColor(Color.WHITE);
            paint.setAlpha(255);
            paint.setShadowLayer((1.8f + intensity * 3f) * scale,
                    0f, 0f, warmDriveColor(load));
            canvas.drawRoundRect(wheel, halfWidth * 0.7f * scale,
                    halfWidth * 0.7f * scale, paint);

            paint.clearShadowLayer();
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth((1.4f + intensity * 1.1f) * scale);
            paint.setColor(wheelDarkColor(load));
            paint.setAlpha(255);
            canvas.drawRoundRect(wheel, halfWidth * 0.7f * scale,
                    halfWidth * 0.7f * scale, paint);
        }

        private void drawAxleFlow(Canvas canvas, float left, float center, float right, float y,
                                  int load, float phase, float scale) {
            if (load < 4) return;
            int count = Math.max(1, Math.min(4, (load + 19) / 24));
            float leftSpan = center - left;
            float rightSpan = right - center;
            for (int i = 0; i < count; i++) {
                float p = (phase + i / (float) count) % 1f;
                drawChevron(canvas, center - p * leftSpan, y, false, load, scale);
                drawChevron(canvas, center + p * rightSpan, y, true, load, scale);
            }
        }

        private void drawShaftFlow(Canvas canvas, float x, float top, float bottom,
                                   int load, float phase, float scale) {
            if (load < 4) return;
            int count = Math.max(1, Math.min(3, (load + 24) / 30));
            float span = bottom - top;
            for (int i = 0; i < count; i++) {
                float p = (phase + i / (float) count) % 1f;
                drawDownChevron(canvas, x, top + p * span, load, scale);
            }
        }

        private void drawChevron(Canvas canvas, float x, float y, boolean right,
                                 int load, float scale) {
            float d = 5.2f * scale;
            Path path = new Path();
            if (right) {
                path.moveTo(x - d, y - d);
                path.lineTo(x, y);
                path.lineTo(x - d, y + d);
            } else {
                path.moveTo(x + d, y - d);
                path.lineTo(x, y);
                path.lineTo(x + d, y + d);
            }
            drawAxleFlowPath(canvas, path, load, scale);
        }

        private void drawDownChevron(Canvas canvas, float x, float y, int load, float scale) {
            float d = 4.2f * scale;
            Path path = new Path();
            path.moveTo(x - d, y - d);
            path.lineTo(x, y);
            path.lineTo(x + d, y - d);
            drawFlowPath(canvas, path, load, scale);
        }

        private void drawFlowPath(Canvas canvas, Path path, int load, float scale) {
            int color = warmDriveColor(load);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth((1.5f + load * 0.006f) * scale);
            paint.setColor(color);
            paint.setAlpha(230);
            paint.setShadowLayer((2f + load * 0.035f) * scale, 0f, 0f, color);
            canvas.drawPath(path, paint);
            paint.clearShadowLayer();
            paint.setAlpha(255);
        }

        private void drawAxleFlowPath(Canvas canvas, Path path, int load, float scale) {
            int color = warmDriveColor(load);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth((2.1f + load * 0.01f) * scale);
            paint.setColor(color);
            paint.setAlpha(230);
            paint.setShadowLayer((3f + load * 0.045f) * scale, 0f, 0f, color);
            canvas.drawPath(path, paint);
            paint.clearShadowLayer();
            paint.setAlpha(255);
        }

        private int warmDriveColor(int load) {
            float amount = loadFactor(load);
            int red = Math.round(224f + (221f - 224f) * amount);
            int green = Math.round(220f + (146f - 220f) * amount);
            int blue = Math.round(212f + (104f - 212f) * amount);
            return Color.rgb(red, green, blue);
        }

        private int wheelLightColor(int load) {
            float amount = loadFactor(load);
            return Color.rgb(
                    Math.round(247f + (246f - 247f) * amount),
                    Math.round(244f + (190f - 244f) * amount),
                    Math.round(238f + (153f - 238f) * amount));
        }

        private int wheelDarkColor(int load) {
            float amount = loadFactor(load);
            return Color.rgb(
                    Math.round(139f + (139f - 139f) * amount),
                    Math.round(135f + (75f - 135f) * amount),
                    Math.round(128f + (48f - 128f) * amount));
        }

        private float loadFactor(int load) {
            float normalized = Math.max(0f, Math.min(1f, load / 100f));
            return (float) Math.pow(normalized, 1.45);
        }

        private int withAlpha(int color, int alpha) {
            return Color.argb(Math.max(0, Math.min(255, alpha)),
                    Color.red(color), Color.green(color), Color.blue(color));
        }
    }
}
