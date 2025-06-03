package com.larkin.observable;

import com.larkin.disposable.Disposable;
import com.larkin.observer.Observer;
import com.larkin.scheduler.Scheduler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ObservableTest {

    @Test
    void testCreateAndSubscribe() {
        List<Integer> received = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        Observable<Integer> observable = Observable.create((observer, disposable) -> {
            observer.onNext(1);
            observer.onNext(2);
            observer.onComplete();
        });

        observable.subscribe(new Observer<Integer>() {

            @Override
            public void onNext(Integer item) {
                received.add(item);
            }

            @Override
            public void onError(Throwable t) {
                fail("Произошла ошибка: " + t.getMessage());
            }

            @Override
            public void onComplete() {
                completed.set(true);
            }
        });

        assertTrue(completed.get(), "Ожидалось завершение потока");
        assertEquals(Arrays.asList(1, 2), received, "Ожидались элементы [1, 2]");
    }

    @Test
    void testErrorHandling() {
        AtomicBoolean errorReceived = new AtomicBoolean(false);

        Observable.create((observer, disposable) -> {
            observer.onError(new RuntimeException("Test exception"));
        }).subscribe(new Observer<Object>() {

            @Override
            public void onNext(Object item) {
                fail("Не ожидались элементы");
            }

            @Override
            public void onError(Throwable t) {
                errorReceived.set(true);
                assertEquals("Test exception", t.getMessage(), "Ожидалось Test exception");
            }

            @Override
            public void onComplete() {
                fail("Не ожидалось завершения");
            }
        });

        assertTrue(errorReceived.get(), "Ожидалась ошибка");
    }

    @Test
    void testNoEmissionAfterComplete() {
        AtomicBoolean completed = new AtomicBoolean(false);
        List<Integer> received = new ArrayList<>();

        Observable<Integer> observable = Observable.create((observer, disposable) -> {
            observer.onNext(1);
            observer.onComplete();
            observer.onNext(2);
        });

        observable.subscribe(new Observer<Integer>() {
            @Override
            public void onNext(Integer item) {
                if (completed.get()) {
                    fail("Получен элемент после завершения");
                }
                received.add(item);
            }

            @Override
            public void onError(Throwable t) {
                fail("Ошибка не ожидалась");
            }

            @Override
            public void onComplete() {
                completed.set(true);
            }
        });

        assertTrue(completed.get(), "Ожидалось завершение");
        assertEquals(List.of(1), received, "Ожидался только элемент [1]");
    }

    @Test
    void testMap() {
        List<String> received = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        Observable<Integer> observable = Observable.create((observer, disposable) -> {
            observer.onNext(1);
            observer.onNext(2);
            observer.onNext(3);
            observer.onComplete();
        });

        Observable<String> mappedObservable = observable.map(x -> "Число: " + x);

        mappedObservable.subscribe(new Observer<String>() {
            @Override
            public void onNext(String item) {
                received.add(item);
            }

            @Override
            public void onError(Throwable t) {
                fail("Ошибка не ожидалась");
            }

            @Override
            public void onComplete() {
                completed.set(true);
            }
        });

        assertTrue(completed.get(), "Ожидалось завершение");
        assertEquals(List.of("Число: 1", "Число: 2", "Число: 3"),
                received,
                "Ожидались преобразованные элементы Integer -> String");
    }

    @Test
    void testFilter() {
        List<Integer> received = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        Observable<Integer> observable = Observable.create(((observer, disposable) -> {
            observer.onNext(1);
            observer.onNext(2);
            observer.onNext(3);
            observer.onNext(4);
            observer.onComplete();
        }));

        Observable<Integer> filteredObservable = observable.filter(x -> x % 2 == 0);
        filteredObservable.subscribe(new Observer<>() {
            @Override
            public void onNext(Integer item) {
                received.add(item);
            }

            @Override
            public void onError(Throwable t) {
                fail("Ошибка не ожидалась");
            }

            @Override
            public void onComplete() {
                completed.set(true);
            }
        });

        assertTrue(completed.get(), "Ожидалось завершение");
        assertEquals(List.of(2, 4), received, "Ожидались только четные числа");
    }

    @Test
    void testFlatMap() {
        List<String> received = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        Observable<Object> observable = Observable.create((observer, disposable) -> {
            observer.onNext(1);
            observer.onNext(2);
            observer.onComplete();
        });

        Observable<String> flatMapResult = observable.flatMap(x -> Observable.create((innerObserver, disposable) -> {
            innerObserver.onNext("A" + x);
            innerObserver.onNext("B" + x);
            innerObserver.onComplete();
        }));

        flatMapResult.subscribe(new Observer<String>() {
            @Override
            public void onNext(String item) {
                received.add(item);
            }

            @Override
            public void onError(Throwable t) {
                fail("Ошибка не ожидалась");
            }

            @Override
            public void onComplete() {
                completed.set(true);
            }
        });

        assertTrue(completed.get(), "Ожидалось завершение");
        assertEquals(List.of("A1", "B1", "A2", "B2"), received, "Ожидались элементы в порядке A1, B1, A2, B2");
    }

    @Test
    void testDisposable() throws InterruptedException {
        AtomicInteger count = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        final Disposable[] disposableHolder = new Disposable[1]; // Initialize array
        Observable<Integer> observable = Observable.create((observer, disp) -> {
            new Thread(() -> {
                for (int i = 0; i < 10 && !disp.isDisposed(); i++) {
                    observer.onNext(i);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                if (!disp.isDisposed()) {
                    observer.onComplete();
                }
            }).start();
        });

        disposableHolder[0] = observable.subscribe(new Observer<Integer>() {
            @Override
            public void onNext(Integer item) {
                count.incrementAndGet();
                if (count.get() == 3 && disposableHolder[0] != null) { // Safety check, though not strictly necessary
                        disposableHolder[0].dispose();
                    }

            }

            @Override
            public void onError(Throwable throwable) {
                fail("Ошибка не ожидалась");
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }
        });

        latch.await(2, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(count.get() >= 3 && count.get() < 10, "Ожидалось, что после dispose() эмиссия остановится");
    }

    @Test
    void testSubscribeOn() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> subscriptionThread = new AtomicReference<>();

        Scheduler scheduler = task -> new Thread(task, "TestThread").start();

        Observable<Integer> observable = Observable.create((observer, disposable) -> {
            subscriptionThread.set(Thread.currentThread().getName());
            observer.onNext(1);
            observer.onComplete();
        });

        observable.subscribeOn(scheduler)
                .subscribe(new Observer<Integer>() {

                    @Override
                    public void onNext(Integer item) {

                    }

                    @Override
                    public void onError(Throwable t) {
                        fail("Ошибка не ожидалась");
                    }

                    @Override
                    public void onComplete() {
                        latch.countDown();
                    }
                });

        latch.await(2, TimeUnit.SECONDS);
        assertEquals("TestThread", subscriptionThread.get(), "Ожидалось выполнение подписки в TestThread");
    }

    @Test
    void testObserveOn() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> observationThread = new AtomicReference<>();

        Scheduler scheduler = task -> new Thread(task, "ObserveThread").start();

        Observable<Object> observable = Observable.create((observer, disposable) -> {
            observer.onNext(1);
            observer.onComplete();
        });

        observable.observeOn(scheduler)
                .subscribe(new Observer<Object>() {

                    @Override
                    public void onNext(Object item) {
                        observationThread.set(Thread.currentThread().getName());
                    }

                    @Override
                    public void onError(Throwable t) {
                        fail("Ошибка не ожидалась");
                    }

                    @Override
                    public void onComplete() {
                        latch.countDown();
                    }
                });

        latch.await(2, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals("ObserveThread", observationThread.get(), "Ожидалось выполнение наблюдения в ObserveThread");
    }

    @Test
    void testOperatorChain() {
        List<String> received = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        Observable<Integer> observable = Observable.create((observer, disposable) -> {
            observer.onNext(1);
            observer.onNext(2);
            observer.onNext(3);
            observer.onNext(4);
            observer.onComplete();
        });

        Observable<String> chainResult = observable
                .filter(x -> x % 2 == 0)
                .map(x -> x * x)
                .map(x -> "Число в квадрате: " + x);

        chainResult.subscribe(new Observer<String>() {
            @Override
            public void onNext(String item) {
                received.add(item);
            }

            @Override
            public void onError(Throwable t) {
                fail("Ошибка не ожидалась");
            }

            @Override
            public void onComplete() {
                completed.set(true);
            }
        });

        assertTrue(completed.get(), "Ожидалось завершение");
        assertEquals(Arrays.asList("Число в квадрате: 4", "Число в квадрате: 16"), received, "Ожидались преобразованные четные числа в квадрате");
    }
}
