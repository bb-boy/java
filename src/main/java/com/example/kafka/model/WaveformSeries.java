package com.example.kafka.model;

import java.util.List;

public class WaveformSeries {
    private Integer shotNo;
    private String channelName;
    private String dataType;
    private List<WaveformPoint> points;

    public Integer getShotNo() {
        return shotNo;
    }

    public void setShotNo(Integer shotNo) {
        this.shotNo = shotNo;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public List<WaveformPoint> getPoints() {
        return points;
    }

    public void setPoints(List<WaveformPoint> points) {
        this.points = points;
    }
}
