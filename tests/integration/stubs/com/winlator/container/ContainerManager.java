package com.winlator.container;

import android.content.Context;

import java.util.ArrayList;

public class ContainerManager {
    private static final ArrayList<Container> containers = new ArrayList<>();

    public ContainerManager(Context context) {}
    public ArrayList<Container> getContainers() { return containers; }
    public static ArrayList<Container> mutableContainers() { return containers; }
}
