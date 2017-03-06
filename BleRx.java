package com.blue.androiddemo.blesdk;

import android.content.Context;
import android.util.Log;

import com.polidea.rxandroidble.RxBleClient;
import com.polidea.rxandroidble.RxBleConnection;
import com.polidea.rxandroidble.RxBleScanResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import rx.Emitter;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;

/**
 * Created by jb on 17/3/2.
 */

public class BleRx {


    private static final String TAG = "BleRx";
    private static BleRx instance;
    private final RxBleClient rxBleClient;
    private Context context;
    private int autoTime = -1;
    private RxBleConnection rxBleConnection;
    private Map<String, List<Emitter<byte[]>>> notifyMaps;
    private Emitter<BleState> stateEmitter;
    private BleState bleState = BleState.DISCONNECT;
    private Subscription connectSub;
    private Subscription autoSub;
    private Subscription stateSub;
    private List<Subscription> notifySubList;

    private BleRx(Context context) {
        this.context = context;
        rxBleClient = RxBleClient.create(context);
        notifyMaps = new HashMap<>();
        notifySubList = new ArrayList<>();
    }

    public static void init(Context context) {
        if (instance == null) {
            instance = new BleRx(context);
        }
    }

    public static BleRx getInstance() {

        synchronized (instance) {
            return instance;
        }
    }

    public Observable<RxBleScanResult> scan() {
        return rxBleClient
                .scanBleDevices()
                .observeOn(AndroidSchedulers.mainThread());
    }

    public void connect(String mac) {
        connect(mac, -1);
    }

    public void connect(String mac, int time) {
        unsubscribe();
        autoTime = time;
        connectSub = rxBleClient.getBleDevice(mac)
                .establishConnection(context, false)
                .subscribe(rxBleConnection -> {
                    this.rxBleConnection = rxBleConnection;
                    setNotifyAll();
                }, e -> {
                    Log.e(TAG, e.toString());
                });
        if (autoTime != -1) {
            autoSub = Observable.timer(autoTime, TimeUnit.SECONDS).subscribe(s -> {
                if (bleState != BleState.CONNECTED) {
                    bleState = BleState.DISCONNECT;
                    stateSub.unsubscribe();
                    connectSub.unsubscribe();
                    sendState(bleState);
                }
            }, e -> Log.e(TAG, e.toString()));
        }
        stateSub = rxBleClient.getBleDevice(mac)
                .observeConnectionStateChanges()
                .subscribe(s -> {
                    if (s.toString().equals("RxBleConnectionState{DISCONNECTED}")) {
                        bleState = BleState.DISCONNECT;
                        for (Subscription subscription : notifySubList) {
                            subscription.unsubscribe();
                        }
                        notifySubList.clear();
                        if (autoTime != -1) {
                            new Thread(() -> {
                                connect(mac, time);
                            }).start();
                        }
                    } else if (s.toString().equals("RxBleConnectionState{CONNECTING}")) {
                        bleState = BleState.CONNECTTING;
                    } else if (s.toString().equals("RxBleConnectionState{CONNECTED}")) {
                        bleState = BleState.CONNECTED;
                    }
                    sendState(bleState);
                }, e -> Log.e(TAG, e.toString()));
    }

    public void disConnect() {
        unsubscribe();
    }

    public Observable<Integer> readRssi() {
        return rxBleConnection.readRssi();
    }

    public Observable<BleState> bleState() {
        return Observable.create((Action1<Emitter<BleState>>) emitter -> {
            stateEmitter = null;
            stateEmitter = emitter;
        }, Emitter.BackpressureMode.BUFFER)
                .observeOn(AndroidSchedulers.mainThread());
    }

    private void sendState(BleState s) {
        stateEmitter.onNext(s);
    }

    public void write(UUID uuid, byte[] data) {
        rxBleConnection
                .writeCharacteristic(uuid, data)
                .subscribe();
    }

    public void write(String uuid, byte[] data) {
        write(UUID.fromString(uuid), data);
    }

    public Observable<byte[]> read(UUID uuid) {
        return rxBleConnection
                .readCharacteristic(uuid)
                .observeOn(AndroidSchedulers.mainThread());
    }

    public Observable<byte[]> read(String uuid) {
        return read(UUID.fromString(uuid));
    }


    private void setNotifyAll() {
        rxBleConnection
                .discoverServices()
                .map(m -> m.getBluetoothGattServices())
                .flatMap(f -> Observable.from(f))
                .flatMap(h -> Observable.from(h.getCharacteristics()))
                .filter(f ->
                        ((f.getProperties() & 0x10) > 0 &&
                                f.getDescriptors() != null &&
                                f.getDescriptors().size() > 0
                        )
                )
                .subscribe(s -> {
                    notifySubList.add(rxBleConnection
                            .setupNotification(s.getUuid())
                            .flatMap(b -> b)
                            .subscribe(data -> {
                                Observable
                                        .just(notifyMaps)
                                        .map(maps -> maps.entrySet())
                                        .flatMap(set -> Observable.from(set))
                                        .filter(entry -> entry.getKey().equals(s.getUuid().toString()))
                                        .flatMap(entry -> Observable.from(entry.getValue()))
                                        .subscribe(emitter -> emitter.onNext(data));
                            }, e -> Log.e(TAG, e.toString())));
                }, e -> Log.e(TAG, e.toString()));
    }

    public Observable<byte[]> notifyData(String uuid) {
        return Observable.create((Action1<Emitter<byte[]>>) emitter -> {
            List<Emitter<byte[]>> emitters = notifyMaps.get(uuid);
            if (emitters == null) {
                emitters = new ArrayList<Emitter<byte[]>>();
            }
            List<Emitter<byte[]>> finalEmitters = emitters;
            emitter.setCancellation(() -> {
                finalEmitters.remove(emitter);
            });
            emitters.add(emitter);
            notifyMaps.put(uuid, emitters);
        }, Emitter.BackpressureMode.BUFFER)
                .observeOn(AndroidSchedulers.mainThread());
    }

    public Observable<byte[]> notifyData(UUID uuid) {
        return notifyData(uuid.toString());
    }

    private void unsubscribe() {
        autoTime = -1;
        if (connectSub != null && !connectSub.isUnsubscribed())
            connectSub.unsubscribe();
        if (autoSub != null && !autoSub.isUnsubscribed())
            autoSub.unsubscribe();
        if (stateSub != null && !stateSub.isUnsubscribed())
            stateSub.unsubscribe();
    }


    public static enum BleState {
        CONNECTED, DISCONNECT, CONNECTTING;
    }

}
