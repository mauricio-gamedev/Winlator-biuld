package com.winlator.container;

public class Container {
    private String graphicsDriver = "old,gl";
    private String graphicsDriverConfig = "graphics-config";
    private String dxWrapper = "old-dx";
    private String dxWrapperConfig = "dx-config";
    private String box64Preset = "OLD";
    private String wineVersion = "wine-10.10-custom";
    private int saveCount;

    public String getGraphicsDriver() { return graphicsDriver; }
    public void setGraphicsDriver(String value) { graphicsDriver = value; }
    public String getGraphicsDriverConfig() { return graphicsDriverConfig; }
    public void setGraphicsDriverConfig(String value) { graphicsDriverConfig = value; }
    public String getDXWrapper() { return dxWrapper; }
    public void setDXWrapper(String value) { dxWrapper = value; }
    public String getDXWrapperConfig() { return dxWrapperConfig; }
    public void setDXWrapperConfig(String value) { dxWrapperConfig = value; }
    public String getBox64Preset() { return box64Preset; }
    public void setBox64Preset(String value) { box64Preset = value; }
    public String getWineVersion() { return wineVersion; }
    public void setWineVersion(String value) { wineVersion = value; }
    public void saveData() { saveCount++; }
    public int getSaveCount() { return saveCount; }
}
