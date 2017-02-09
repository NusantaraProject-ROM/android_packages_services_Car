/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.car.vms;

import android.annotation.SystemApi;
import android.car.Car;
import android.car.CarManagerBase;
import android.car.CarNotConnectedException;
import android.car.annotation.FutureFeature;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.lang.ref.WeakReference;

/**
 * API for interfacing with the VmsSubscriberService. It supports a single listener that can
 * (un)subscribe to different layers. After getting an instance of this manager, the first step
 * must be to call #setListener. After that, #subscribe and #unsubscribe methods can be invoked.
 *
 * @hide
 */

@FutureFeature
@SystemApi
public final class VmsSubscriberManager implements CarManagerBase {
    private static final boolean DBG = true;
    private static final String TAG = "VmsSubscriberManager";

    private final Handler mHandler;
    private final IVmsSubscriberService mVmsSubscriberService;
    private final IOnVmsMessageReceivedListener mIListener;
    private final Object mListenerLock = new Object();
    @GuardedBy("mListenerLock")
    private OnVmsMessageReceivedListener mListener;

    /** Interface exposed to VMS subscribers: it is a wrapper of IOnVmsMessageReceivedListener. */
    public interface OnVmsMessageReceivedListener {
        /** Called when the property is updated */
        void onVmsMessageReceived(VmsProperty message);
    }

    /**
     * Allows to asynchronously dispatch onVmsMessageReceived events.
     */
    private final static class VmsEventHandler extends Handler {
        /** Constants handled in the handler */
        private static final int ON_RECEIVE_MESSAGE_EVENT = 0;

        private final WeakReference<VmsSubscriberManager> mMgr;

        VmsEventHandler(VmsSubscriberManager mgr, Looper looper) {
            super(looper);
            mMgr = new WeakReference<>(mgr);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case ON_RECEIVE_MESSAGE_EVENT:
                    VmsSubscriberManager mgr = mMgr.get();
                    if (mgr != null) {
                        mgr.dispatchOnReceiveMessage((VmsProperty) msg.obj);
                    }
                    break;
                default:
                    Log.e(VmsSubscriberManager.TAG, "Event type not handled:  " + msg.what);
                    break;
            }
        }
    }

    public VmsSubscriberManager(IBinder service, Handler handler) {
        mVmsSubscriberService = IVmsSubscriberService.Stub.asInterface(service);
        mHandler = new VmsEventHandler(this, handler.getLooper());
        mIListener = new IOnVmsMessageReceivedListener.Stub() {
            @Override
            public void onVmsMessageReceived(VmsProperty message) throws RemoteException {
                mHandler.sendMessage(
                        mHandler.obtainMessage(VmsEventHandler.ON_RECEIVE_MESSAGE_EVENT, message));
            }
        };
    }

    /**
     * Sets the listener ({@link #mListener}) this manager is linked to. Subscriptions to the
     * {@link com.android.car.VmsSubscriberService} are done through the {@link #mIListener}.
     * Therefore, notifications from the {@link com.android.car.VmsSubscriberService} are received
     * by the {@link #mIListener} and then forwarded to the {@link #mListener}.
     *
     * @param listener subscriber listener that will handle onVmsMessageReceived events.
     * @throws IllegalStateException if the listener was already set.
     */
    public void setListener(OnVmsMessageReceivedListener listener) {
        if (DBG) {
            Log.d(TAG, "Setting listener.");
        }
        synchronized (mListenerLock) {
            if (mListener != null) {
                throw new IllegalStateException("Listener is already configured.");
            }
            mListener = listener;
        }
    }

    /**
     * Removes the listener and unsubscribes from all the layer/version.
     */
    public void clearListener() {
        synchronized (mListenerLock) {
            mListener = null;
        }
        // TODO(antoniocortes): logic to unsubscribe from all the layer/version pairs.
    }

    /**
     * Subscribes to listen to the layer/version specified.
     *
     * @param layer           the layer id to subscribe to.
     * @param version         the layer version to subscribe to.
     * @param silentSubscribe if true, the listener does not notify publishers of its existence,
     *                        it only listens passively.
     * @throws IllegalStateException if the listener was not set via {@link #setListener}.
     */
    public void subscribe(int layer, int version, boolean silentSubscribe)
            throws CarNotConnectedException {
        if (DBG) {
            Log.d(TAG, "Subscribing to layer: " + layer + ", version: " + version);
        }
        OnVmsMessageReceivedListener listener;
        synchronized (mListenerLock) {
            listener = mListener;
        }
        if (listener == null) {
            Log.w(TAG, "subscribe: listener was not set, " +
                    "setListener must be called first.");
            throw new IllegalStateException("Listener was not set.");
        }
        try {
            mVmsSubscriberService.addOnVmsMessageReceivedListener(layer, version, mIListener,
                    silentSubscribe);
        } catch (RemoteException e) {
            Log.e(TAG, "Could not connect: ", e);
            throw new CarNotConnectedException(e);
        } catch (IllegalStateException ex) {
            Car.checkCarNotConnectedExceptionFromCarService(ex);
        }
    }

    /**
     * Unsubscribes from the layer/version specified.
     *
     * @param layer   the layer id to unsubscribe from.
     * @param version the layer version to unsubscribe from.
     * @throws IllegalStateException if the listener was not set via {@link #setListener}.
     */
    public void unsubscribe(int layer, int version) {
        if (DBG) {
            Log.d(TAG, "Unsubscribing from layer: " + layer + ", version: " + version);
        }
        OnVmsMessageReceivedListener listener;
        synchronized (mListenerLock) {
            listener = mListener;
        }
        if (listener == null) {
            Log.w(TAG, "unsubscribe: listener was not set, " +
                    "setListener must be called first.");
            throw new IllegalStateException("Listener was not set.");
        }
        try {
            mVmsSubscriberService.removeOnVmsMessageReceivedListener(layer, version, mIListener);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to unregister subscriber", e);
            // ignore
        } catch (IllegalStateException ex) {
            Car.hideCarNotConnectedExceptionFromCarService(ex);
        }
    }

    private void dispatchOnReceiveMessage(VmsProperty message) {
        OnVmsMessageReceivedListener listener;
        synchronized (mListenerLock) {
            listener = mListener;
        }
        if (listener == null) {
            Log.e(TAG, "Listener died, not dispatching event.");
            return;
        }
        listener.onVmsMessageReceived(message);
    }

    /** @hide */
    @Override
    public void onCarDisconnected() {
        clearListener();
    }
}
