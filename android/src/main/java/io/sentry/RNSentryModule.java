package io.sentry;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.ReadableType;
import com.getsentry.raven.RavenFactory;
import com.getsentry.raven.Raven;
import com.getsentry.raven.android.AndroidRavenFactory;
import com.getsentry.raven.android.event.helper.AndroidEventBuilderHelper;
import com.getsentry.raven.dsn.Dsn;
import com.getsentry.raven.event.Breadcrumb;
import com.getsentry.raven.event.Event;
import com.getsentry.raven.event.EventBuilder;
import com.getsentry.raven.context.Context;
import com.getsentry.raven.event.BreadcrumbBuilder;
import com.getsentry.raven.event.Breadcrumbs;
import com.getsentry.raven.event.UserBuilder;
import com.getsentry.raven.event.interfaces.UserInterface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class RNSentryModule extends ReactContextBaseJavaModule {

    private final ReactApplicationContext reactContext;
    final static Logger logger = Logger.getLogger("react-native-sentry");
    private volatile com.getsentry.raven.Raven raven;

    public RNSentryModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "RNSentry";
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put("nativeClientAvailable", true);
        return constants;
    }

    @ReactMethod
    public void startWithDsnString(String dsnString) {
        AndroidRavenFactory factory = new AndroidRavenFactory(this.getReactApplicationContext());
        raven = factory.createRavenInstance(new Dsn(dsnString));
        logger.info(String.format("startWithDsnString '%s'", dsnString));
    }

    @ReactMethod
    public void setLogLevel(int level) {
        logger.info("TODO: implement setLogLevel");
    }

    @ReactMethod
    public void setExtra(ReadableMap extras) {
        logger.info("TODO: implement setExtra");
    }

    @ReactMethod
    public void setTags(ReadableMap tags) {
        logger.info("TODO: implement setTags");
    }

    @ReactMethod
    public void setUser(ReadableMap user) {
        raven.getContext().setUser(
                new UserBuilder()
                        .setEmail(user.getString("email"))
                        .setId(user.getString("userID"))
                        .setUsername(user.getString("username"))
                        .build()
        );
    }

    @ReactMethod
    public void crash() {
        logger.info("TODO: implement crash");
    }

    @ReactMethod
    public void captureBreadcrumb(ReadableMap breadcrumb) {
        logger.info(String.format("captureEvent '%s'", breadcrumb));
        if (breadcrumb.hasKey("message")) {
            raven.getContext().recordBreadcrumb(
                    new BreadcrumbBuilder()
                            .setMessage(breadcrumb.getString("message"))
                            .setCategory(breadcrumb.getString("category"))
                            .setLevel(breadcrumbLevel(breadcrumb.getString("level")))
                            .build()
            );
        }
    }

    @ReactMethod
    public void captureEvent(ReadableMap event) {
        if (event.hasKey("message")) {
            AndroidEventBuilderHelper helper = new AndroidEventBuilderHelper(this.getReactApplicationContext());
            EventBuilder eventBuilder = new EventBuilder()
                    .withMessage(event.getString("message"))
                    .withLogger(event.getString("logger"))
                    .withLevel(eventLevel(event.getString("level")));

            eventBuilder.withSentryInterface(
                    new UserInterface(
                            event.getMap("user").getString("userID"),
                            event.getMap("user").getString("username"),
                            null,
                            event.getMap("user").getString("email")
                    )
            );

            helper.helpBuildingEvent(eventBuilder);

            for (Map.Entry<String, Object> entry : recursivelyDeconstructReadableMap(event.getMap("extra")).entrySet()) {
                eventBuilder.withExtra(entry.getKey(), entry.getValue());
            }

            for (Map.Entry<String, Object> entry : recursivelyDeconstructReadableMap(event.getMap("tags")).entrySet()) {
                eventBuilder.withTag(entry.getKey(), entry.getValue().toString());
            }

            Raven.capture(eventBuilder.build());
        } else {
            logger.info("event has no key message that means it is a js error");
        }
    }

    @ReactMethod
    public void clearContext() {
        raven.getContext().clear();
    }

    @ReactMethod
    public void activateStacktraceMerging(Promise promise) {
        logger.info("TODO: implement activateStacktraceMerging");
//        try {
        promise.resolve(true);
//        } catch (IllegalViewOperationException e) {
//            promise.reject(E_LAYOUT_ERROR, e);
//        }
    }

    private Breadcrumb.Level breadcrumbLevel(String level) {
        switch (level) {
            case "critical":
                return Breadcrumb.Level.CRITICAL;
            case "warning":
                return Breadcrumb.Level.WARNING;
            case "info":
                return Breadcrumb.Level.INFO;
            case "debug":
                return Breadcrumb.Level.DEBUG;
            default:
                return Breadcrumb.Level.ERROR;
        }
    }

    private Event.Level eventLevel(String level) {
        switch (level) {
            case "fatal":
                return Event.Level.FATAL;
            case "warning":
                return Event.Level.WARNING;
            case "info":
                return Event.Level.INFO;
            case "debug":
                return Event.Level.DEBUG;
            default:
                return Event.Level.ERROR;
        }
    }

    private Map<String, Object> recursivelyDeconstructReadableMap(ReadableMap readableMap) {
        ReadableMapKeySetIterator iterator = readableMap.keySetIterator();
        Map<String, Object> deconstructedMap = new HashMap<>();
        while (iterator.hasNextKey()) {
            String key = iterator.nextKey();
            ReadableType type = readableMap.getType(key);
            switch (type) {
                case Null:
                    deconstructedMap.put(key, null);
                    break;
                case Boolean:
                    deconstructedMap.put(key, readableMap.getBoolean(key));
                    break;
                case Number:
                    deconstructedMap.put(key, readableMap.getDouble(key));
                    break;
                case String:
                    deconstructedMap.put(key, readableMap.getString(key));
                    break;
                case Map:
                    deconstructedMap.put(key, recursivelyDeconstructReadableMap(readableMap.getMap(key)));
                    break;
                case Array:
                    deconstructedMap.put(key, recursivelyDeconstructReadableArray(readableMap.getArray(key)));
                    break;
                default:
                    throw new IllegalArgumentException("Could not convert object with key: " + key + ".");
            }

        }
        return deconstructedMap;
    }

    private List<Object> recursivelyDeconstructReadableArray(ReadableArray readableArray) {
        List<Object> deconstructedList = new ArrayList<>(readableArray.size());
        for (int i = 0; i < readableArray.size(); i++) {
            ReadableType indexType = readableArray.getType(i);
            switch (indexType) {
                case Null:
                    deconstructedList.add(i, null);
                    break;
                case Boolean:
                    deconstructedList.add(i, readableArray.getBoolean(i));
                    break;
                case Number:
                    deconstructedList.add(i, readableArray.getDouble(i));
                    break;
                case String:
                    deconstructedList.add(i, readableArray.getString(i));
                    break;
                case Map:
                    deconstructedList.add(i, recursivelyDeconstructReadableMap(readableArray.getMap(i)));
                    break;
                case Array:
                    deconstructedList.add(i, recursivelyDeconstructReadableArray(readableArray.getArray(i)));
                    break;
                default:
                    throw new IllegalArgumentException("Could not convert object at index " + i + ".");
            }
        }
        return deconstructedList;
    }
}
