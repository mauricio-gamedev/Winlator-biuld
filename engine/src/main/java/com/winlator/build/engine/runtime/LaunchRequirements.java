package com.winlator.build.engine.runtime;

public final class LaunchRequirements {
    public enum GraphicsApi {
        UNKNOWN,
        DIRECTX_LEGACY,
        DIRECTX_9_11,
        DIRECTX_12,
        OPENGL
    }

    public enum GuestArchitecture {
        UNKNOWN,
        X86,
        X86_64
    }

    private final GraphicsApi graphicsApi;
    private final GuestArchitecture guestArchitecture;

    public LaunchRequirements(GraphicsApi graphicsApi, GuestArchitecture guestArchitecture) {
        this.graphicsApi = graphicsApi == null ? GraphicsApi.UNKNOWN : graphicsApi;
        this.guestArchitecture = guestArchitecture == null ? GuestArchitecture.UNKNOWN : guestArchitecture;
    }

    public static LaunchRequirements defaults() {
        return new LaunchRequirements(GraphicsApi.UNKNOWN, GuestArchitecture.UNKNOWN);
    }

    public static LaunchRequirements forGraphicsApi(GraphicsApi graphicsApi) {
        return new LaunchRequirements(graphicsApi, GuestArchitecture.UNKNOWN);
    }

    public GraphicsApi getGraphicsApi() { return graphicsApi; }
    public GuestArchitecture getGuestArchitecture() { return guestArchitecture; }
}
