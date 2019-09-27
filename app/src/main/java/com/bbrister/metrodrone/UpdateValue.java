package com.bbrister.metrodrone;

public class UpdateValue<T> {
    private T val;
    private UpdateInterface updateInterface;

    public UpdateValue(T init, UpdateInterface updateInterface) {
        this.val = init;
        this.updateInterface = updateInterface;
    }

    public T get() {
        return val;
    }

    public void set(T newVal) {
        if (val.equals(newVal)) {
            return;
        }

        val = newVal;
        updateInterface.update();
    }
}
