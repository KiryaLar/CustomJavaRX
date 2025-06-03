package com.larkin.observable;

import com.larkin.disposable.Disposable;
import com.larkin.observer.Observer;

public interface ObservableOnSubscribe<T> {
    void subscribe(Observer<T> observer, Disposable disposable) throws Exception;
}
