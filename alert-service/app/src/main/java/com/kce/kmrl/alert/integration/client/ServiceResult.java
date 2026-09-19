package com.kce.kmrl.alert.integration.client;

public final class ServiceResult<T> {
    private final T data;
    private final boolean available;

    private ServiceResult(T data, boolean available) {
        this.data = data;
        this.available = available;
    }

    public static <T> ServiceResult<T> available(T data) {
        return new ServiceResult<>(data, true);
    }

    public static <T> ServiceResult<T> unavailable() {
        return new ServiceResult<>(null, false);
    }

    public T getData() { return data; }
    public boolean isAvailable() { return available; }
}
