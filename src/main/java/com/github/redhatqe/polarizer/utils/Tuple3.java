package com.github.redhatqe.polarizer.utils;

public class Tuple3<F, S, T> {
    public F first;
    public S second;
    public T third;

    public Tuple3(F f, S s, T t) {
        this.first = f;
        this.second = s;
        this.third = t;
    }

    public Tuple3() {

    }
}
