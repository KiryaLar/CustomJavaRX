package com.larkin.observable;

import com.larkin.disposable.Disposable;
import com.larkin.observer.Observer;
import com.larkin.scheduler.Scheduler;
import lombok.Getter;
import lombok.Setter;

import java.util.function.Function;
import java.util.function.Predicate;

public class Observable<T> {
    private final ObservableOnSubscribe<T> source;

    protected Observable(ObservableOnSubscribe<T> source) {
        this.source = source;
    }

    public static <T> Observable<T> create(ObservableOnSubscribe<T> source) {
        return new Observable<>(source);
    }

    public Disposable subscribe(Observer<T> observer) {
        DisposableImpl disposable = new DisposableImpl();
        Observer<T> wrappedObserver = new Observer<T>() {
            private volatile boolean terminated = false;

            @Override
            public void onNext(T item) {
                if (!disposable.isDisposed() && !terminated) {
                    observer.onNext(item);
                }
            }

            @Override
            public void onError(Throwable t) {
                if (!disposable.isDisposed() && !terminated) {
                    terminated = true;
                    observer.onError(t);
                }
            }

            @Override
            public void onComplete() {
                if (!disposable.isDisposed() && !terminated) {
                    terminated = true;
                    observer.onComplete();
                }
            }
        };

        try {
            if (!disposable.isDisposed()) {
                source.subscribe(wrappedObserver, disposable);
            }
        } catch (Exception e) {
            wrappedObserver.onError(e);
        }
        return disposable;
    }

    @Getter
    private static class DisposableImpl implements Disposable {
        private volatile boolean disposed = false;
        @Setter
        private Disposable sourceDisposable;

        @Override
        public void dispose() {
            if (!disposed) {
                disposed = true;
                if (sourceDisposable != null) {
                    sourceDisposable.dispose();
                }
            }
        }
    }

    public <R> Observable<R> map(Function<T, R> mapper) {
        return create((observer, disposable) -> {
            Observer<T> wrappedObserver = new Observer<T>() {
                private boolean terminated = false;
                @Override
                public void onNext(T item) {
                    if (!disposable.isDisposed() && !terminated) {
                        try {
                            observer.onNext(mapper.apply(item));
                        } catch (Exception e) {
                            terminated = true;
                            observer.onError(e);
                        }
                    }
                }

                @Override
                public void onError(Throwable t) {
                    if (!disposable.isDisposed() && !terminated) {
                        terminated = true;
                        observer.onError(t);
                    }
                }

                @Override
                public void onComplete() {
                    if (!disposable.isDisposed() && !terminated) {
                        terminated = true;
                        observer.onComplete();
                    }
                }
            };

            Disposable sourceDisposable = Observable.this.subscribe(wrappedObserver);
            ((DisposableImpl) disposable).setSourceDisposable(sourceDisposable);
        });
    }

    public Observable<T> filter(Predicate<T> predicate) {
        return create((observer, disposable) -> {
            Observer<T> wrappedObserver = new Observer<T>() {
                private boolean terminated = false;

                @Override
                public void onNext(T item) {
                    if (!disposable.isDisposed() && !terminated) {
                        try {
                            if (predicate.test(item)) {
                                observer.onNext(item);
                            }
                        } catch (Exception e) {
                            terminated = true;
                            observer.onError(e);
                        }
                    }
                }

                @Override
                public void onError(Throwable t) {
                    if (!disposable.isDisposed() && !terminated) {
                        terminated = true;
                        observer.onError(t);
                    }
                }

                @Override
                public void onComplete() {
                    if (!disposable.isDisposed() && !terminated) {
                        terminated = true;
                        observer.onComplete();
                    }
                }
            };

            Disposable sourceDisposable = Observable.this.subscribe(wrappedObserver);
            ((DisposableImpl) disposable).setSourceDisposable(sourceDisposable);
        });
    }

    public <R> Observable<R> flatMap(Function<T, Observable<R>> mapper) {
        return create((observer, disposable) -> {
            Observer<T> wrappedObserver = new Observer<T>() {
                private boolean terminated = false;

                @Override
                public void onNext(T item) {
                    if (!disposable.isDisposed() && !terminated) {
                        try {
                            Observable<R> innerObservable = mapper.apply(item);
                            innerObservable.subscribe(new Observer<R>() {
                                @Override
                                public void onNext(R innerItem) {
                                    if (!disposable.isDisposed() && !terminated) {
                                        observer.onNext(innerItem);
                                    }
                                }

                                @Override
                                public void onError(Throwable t) {
                                    if (!disposable.isDisposed() && !terminated) {
                                        terminated = true;
                                        observer.onError(t);
                                    }
                                }

                                @Override
                                public void onComplete() {
//                                  В flatMap не завершаем основной поток при завершении внутреннего
                                }
                            });
                        } catch (Exception e) {
                            terminated = true;
                            observer.onError(e);
                        }
                    }
                }

                @Override
                public void onError(Throwable t) {
                    if (!disposable.isDisposed() && !terminated) {
                        terminated = true;
                        observer.onError(t);
                    }
                }

                @Override
                public void onComplete() {
                    if (!disposable.isDisposed() && !terminated) {
                        terminated = true;
                        observer.onComplete();
                    }
                }
            };

            Disposable sourceDisposable = Observable.this.subscribe(wrappedObserver);
            ((DisposableImpl) disposable).setSourceDisposable(sourceDisposable);
        });
    }

    public Observable<T> subscribeOn(Scheduler scheduler) {
        return create((observer, disposable) -> scheduler.execute(() -> {
            if (!disposable.isDisposed()) {
                Disposable sourceDisposable = Observable.this.subscribe(observer);
                ((DisposableImpl) disposable).setSourceDisposable(sourceDisposable);
            }
        }));
    }

    public Observable<T> observeOn(Scheduler scheduler) {
        return create((observer, disposable) -> {
            Observer<T> wrappedObserver = new Observer<>() {
                private boolean terminated = false;

                @Override
                public void onNext(T item) {
                    if (!disposable.isDisposed() && !terminated) {
                        scheduler.execute(() -> observer.onNext(item));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    if (!disposable.isDisposed() && !terminated) {
                        terminated = true;
                        scheduler.execute(() -> observer.onError(t));
                    }
                }

                @Override
                public void onComplete() {
                    if (!disposable.isDisposed() && !terminated) {
                        terminated = true;
                        scheduler.execute(observer::onComplete);
                    }
                }
            };

            Disposable sourceDisposable = Observable.this.subscribe(wrappedObserver);
            ((DisposableImpl) disposable).setSourceDisposable(sourceDisposable);
        });
    }
}
