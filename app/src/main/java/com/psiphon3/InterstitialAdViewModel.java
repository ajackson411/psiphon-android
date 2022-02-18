package com.psiphon3;

import android.app.Activity;
import android.app.Application;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import com.freestar.android.ads.AdRequest;
import com.freestar.android.ads.InterstitialAd;
import com.freestar.android.ads.InterstitialAdListener;
import com.jakewharton.rxrelay2.BehaviorRelay;

import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

public class InterstitialAdViewModel extends AndroidViewModel implements LifecycleObserver {
    private WeakReference<Activity> activityWeakReference;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private final BehaviorRelay<ConsumableEvent<Object>> startSignalRelay = BehaviorRelay.create();
    private final Observable<PsiphonAdManager.InterstitialResult> interstitialResultObservable;
    private InterstitialAd interstitialAd;
    private Disposable preLoadInterstitialDisposable;
    private Disposable startUpInterstitialDisposable;
    private WeakReference<TextView> countDownTextViewWeakReference;
    private int countdownSeconds = 10;

    public InterstitialAdViewModel(@NonNull Application app) {
        super(app);
        this.interstitialResultObservable = PsiphonAdManager.SdkInitializer.getFreeStar(app)
                .andThen(Observable.<PsiphonAdManager.InterstitialResult>create(emitter -> {
                    Activity mainActivity = activityWeakReference.get();
                    if (mainActivity == null) {
                        if (!emitter.isDisposed()) {
                            emitter.onError(new RuntimeException("Freestar failed to create interstitial: activity is null"));
                        }
                        return;
                    }
                    interstitialAd = new InterstitialAd(mainActivity, new InterstitialAdListener() {

                        @Override
                        public void onInterstitialLoaded(String placement) {
                            if (!emitter.isDisposed()) {
                                if (interstitialAd.isReady()) {
                                    emitter.onNext(PsiphonAdManager.InterstitialResult.Freestar.create(interstitialAd,
                                            PsiphonAdManager.InterstitialResult.State.READY));
                                } else {
                                    emitter.onError(new RuntimeException("Freestar interstitial loaded but the interstitial is not ready"));
                                }
                            }
                        }

                        @Override
                        public void onInterstitialFailed(String placement, int errorCode) {
                            if (!emitter.isDisposed()) {
                                emitter.onError(new RuntimeException("Freestar interstitial failed with error code: " + errorCode));
                            }
                        }

                        @Override
                        public void onInterstitialShown(String placement) {
                            if (!emitter.isDisposed()) {
                                emitter.onNext(PsiphonAdManager.InterstitialResult.Freestar.create(interstitialAd,
                                        PsiphonAdManager.InterstitialResult.State.SHOWING));
                            }
                        }

                        @Override
                        public void onInterstitialClicked(String placement) {
                        }

                        @Override
                        public void onInterstitialDismissed(String placement) {
                            if (!emitter.isDisposed()) {
                                emitter.onComplete();
                            }
                        }
                    });
                    if (!emitter.isDisposed()) {
                        emitter.onNext(PsiphonAdManager.InterstitialResult.Freestar.create(interstitialAd,
                                PsiphonAdManager.InterstitialResult.State.LOADING));
                        interstitialAd.loadAd(new AdRequest(mainActivity));
                    }
                }))
                .replay(1)
                .refCount();
    }

    public void setActivityWeakReference(Activity activity) {
        this.activityWeakReference = new WeakReference<>(activity);
    }

    public void setCountDownTextView(TextView tv) {
        this.countDownTextViewWeakReference = new WeakReference<>(tv);
    }

    public void setCountdownSeconds(int countdownSeconds) {
        this.countdownSeconds = countdownSeconds;
    }

    public Observable<PsiphonAdManager.InterstitialResult> getInterstitialResultObservable() {
        if (PsiphonAdManager.canShowAds()) {
            return interstitialResultObservable;
        }
        return Observable.empty();
    }

    public void preloadInterstitial() {
        if (preLoadInterstitialDisposable == null ||
                preLoadInterstitialDisposable.isDisposed()) {
            preLoadInterstitialDisposable = getInterstitialResultObservable()
                    .ignoreElements()
                    .onErrorComplete()
                    .subscribe();
            compositeDisposable.add(preLoadInterstitialDisposable);
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    protected void onLifeCyclePause() {
        if (interstitialAd != null) {
            interstitialAd.onPause();
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    protected void onLifeCycleResume() {
        if (interstitialAd != null) {
            interstitialAd.onResume();
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        compositeDisposable.dispose();
        if (interstitialAd != null) {
            interstitialAd.destroyView();
        }
        interstitialAd = null;
    }

    public boolean inProgress() {
        return startUpInterstitialDisposable != null &&
                !startUpInterstitialDisposable.isDisposed();
    }

    /**
     * @param skipInterstitial indicates if interstitial loading and showing should be bypassed and
     *                         start should be signalled immediately
     */
    public void startUp(boolean skipInterstitial) {
        if (inProgress()) {
            return;
        }

        // Count down from countdownSeconds to 0 and update the textview with the value.
        // Terminates normally when the countdown is complete.
        //
        // Note: completes immediately if skipInterstitial == true
        Completable countdownCompletable = skipInterstitial ? Completable.complete() :
                Observable.intervalRange(0, countdownSeconds + 1, 0, 1, TimeUnit.SECONDS)
                        .map(t -> countdownSeconds - t)
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnNext(t -> {
                            TextView textView = countDownTextViewWeakReference.get();
                            if (textView != null) {
                                textView.setText(String.format(Locale.US, "%d", t));
                            }
                        })
                        .ignoreElements();

        // Loads and shows an interstitial when subscribed to and signals downstream as soon as the
        // ad is presented.
        // Terminates normally when the ad is closed or with an error if the ad failed to load.
        //
        // Note: completes immediately if skipInterstitial == true
        Observable<PsiphonAdManager.InterstitialResult> interstitialResultObservable = skipInterstitial ? Observable.empty() :
                getInterstitialResultObservable()
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnNext(interstitialResult -> {
                            if (interstitialResult.state() == PsiphonAdManager.InterstitialResult.State.READY) {
                                interstitialResult.show();
                            }
                        })
                        // Emit downstream only when the ad is shown because sometimes
                        // calling interstitialResult.show() doesn't result in ad presented.
                        // In such a case let the countdown win the race.
                        .filter(interstitialResult ->
                                interstitialResult.state() == PsiphonAdManager.InterstitialResult.State.SHOWING);

        startUpInterstitialDisposable = countdownCompletable
                // Convert to observable to apply `ambWith`.
                .toObservable()
                // `ambWith` mirrors the ObservableSource that first either emits an
                // item or sends a termination notification.
                .ambWith(interstitialResultObservable)
                // Ignore all emissions, we are only interested in termination events.
                .ignoreElements()
                // On error log and complete this subscription.
                .onErrorComplete()
                // At this point the upstream completes due to one of the following reasons:
                // 1. skipInterstitial == true
                // 2. Countdown completed before interstitial observable emitted anything.
                // 3. There was an error emission from interstitial observable.
                // 4. Interstitial observable completed because it was closed.
                //
                // Now, signal start tunnel.
                .observeOn(AndroidSchedulers.mainThread())
                .doOnComplete(() -> {
                    // Dispose any preload that is in progress
                    if (preLoadInterstitialDisposable != null && !preLoadInterstitialDisposable.isDisposed()) {
                        preLoadInterstitialDisposable.dispose();
                    }
                    startSignalRelay.accept(new ConsumableEvent<>(new Object()));
                })
                .subscribe();

        compositeDisposable.add(startUpInterstitialDisposable);
    }

    public Flowable<ConsumableEvent<Object>> startSignalFlowable() {
        return startSignalRelay
                .hide()
                .toFlowable(BackpressureStrategy.LATEST)
                .observeOn(AndroidSchedulers.mainThread());
    }

    public static class ConsumableEvent<T> {
        private final T payload;
        @NonNull
        private final AtomicBoolean isConsumed = new AtomicBoolean(false);

        public ConsumableEvent(T payload) {
            this.payload = payload;
        }

        public final void consume(@NonNull Consumer<T> action) {
            if (!isConsumed.getAndSet(true)) {
                action.accept(payload);
            }
        }
    }
}
