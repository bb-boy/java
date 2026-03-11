package com.example.kafka.service;

import com.example.kafka.model.WaveformPoint;
import com.example.kafka.model.WaveformQueryCriteria;
import com.example.kafka.model.WaveformSeries;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.influxdb.enabled", havingValue = "true")
public class WaveformQueryService {
    private static final String MEASUREMENT = "waveform";

    @Autowired
    private InfluxDBClient influxDBClient;
    @Autowired
    @Qualifier("influxDBBucket")
    private String bucket;
    @Autowired
    @Qualifier("influxDBOrg")
    private String org;
    @Value("${app.waveform.max-points}")
    private int defaultMaxPoints;

    public WaveformSeries querySeries(WaveformQueryCriteria criteria) {
        int limit = criteria.getMaxPoints() == null ? defaultMaxPoints : criteria.getMaxPoints();
        String flux = buildFlux(criteria, limit);
        List<WaveformPoint> points = queryPoints(flux);
        WaveformSeries series = new WaveformSeries();
        series.setShotNo(criteria.getShotNo());
        series.setChannelName(criteria.getChannelName());
        series.setDataType(criteria.getDataType());
        series.setPoints(points);
        return series;
    }

    private String buildFlux(WaveformQueryCriteria criteria, int maxPoints) {
        String startIso = toRfc3339(criteria.getStart());
        String endIso = toRfc3339(criteria.getEnd());
        StringBuilder builder = new StringBuilder();
        builder.append(String.format(
            "from(bucket: \"%s\") |> range(start: time(v: \"%s\"), stop: time(v: \"%s\"))",
            bucket,
            startIso,
            endIso
        ));
        builder.append(String.format(
            " |> filter(fn: (r) => r._measurement == \"%s\" and r._field == \"value\")",
            MEASUREMENT
        ));
        builder.append(String.format(
            " |> filter(fn: (r) => r.shot_no == \"%s\" and r.channel_name == \"%s\")",
            criteria.getShotNo(),
            criteria.getChannelName()
        ));
        if (criteria.getDataType() != null && !criteria.getDataType().isBlank()) {
            builder.append(String.format(
                " |> filter(fn: (r) => r.data_type == \"%s\")",
                criteria.getDataType().toUpperCase()
            ));
        }
        builder.append(String.format(" |> limit(n: %d)", maxPoints));
        return builder.toString();
    }

    private List<WaveformPoint> queryPoints(String flux) {
        List<FluxTable> tables = influxDBClient.getQueryApi().query(flux, org);
        List<WaveformPoint> points = new ArrayList<>();
        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                Instant time = record.getTime();
                LocalDateTime dateTime = time == null
                    ? null
                    : time.atOffset(ZoneOffset.UTC).toLocalDateTime();
                Double value = record.getValue() == null ? null : ((Number) record.getValue()).doubleValue();
                points.add(new WaveformPoint(dateTime, value));
            }
        }
        return points;
    }

    public List<String> queryAvailableChannels(Integer shotNo, String dataType) {
        StringBuilder flux = new StringBuilder();
        flux.append(String.format("from(bucket: \"%s\") |> range(start: 0)", bucket));
        flux.append(String.format(
            " |> filter(fn: (r) => r._measurement == \"%s\" and r.shot_no == \"%d\")",
            MEASUREMENT, shotNo));
        if (dataType != null && !dataType.isBlank()) {
            flux.append(String.format(
                " |> filter(fn: (r) => r.data_type == \"%s\")",
                dataType.toUpperCase()));
        }
        flux.append(" |> keep(columns: [\"channel_name\"])");
        flux.append(" |> distinct(column: \"channel_name\")");

        List<FluxTable> tables = influxDBClient.getQueryApi().query(flux.toString(), org);
        List<String> channels = new ArrayList<>();
        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                Object val = record.getValueByKey("channel_name");
                if (val != null) channels.add(val.toString());
            }
        }
        Collections.sort(channels);
        return channels;
    }

    private String toRfc3339(Instant time) {
        return time.toString();
    }
}
