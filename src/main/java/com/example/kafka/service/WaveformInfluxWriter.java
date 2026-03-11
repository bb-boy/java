package com.example.kafka.service;

import com.example.kafka.model.WaveformBatch;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.influxdb.enabled", havingValue = "true")
public class WaveformInfluxWriter {
    private static final Logger logger = LoggerFactory.getLogger(WaveformInfluxWriter.class);
    private static final String MEASUREMENT = "waveform";
    private static final int BATCH_SIZE = 5000;
    private static final double NANOS_PER_SECOND = 1_000_000_000D;

    private final InfluxDBClient influxDBClient;
    private final String bucket;
    private final String org;

    public WaveformInfluxWriter(
        InfluxDBClient influxDBClient,
        @Qualifier("influxDBBucket") String bucket,
        @Qualifier("influxDBOrg") String org
    ) {
        this.influxDBClient = influxDBClient;
        this.bucket = bucket;
        this.org = org;
    }

    public void writeBatch(WaveformBatch batch) {
        validateBatch(batch);
        WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();
        BatchContext context = buildContext(batch);
        List<Point> buffer = new ArrayList<>(BATCH_SIZE);
        WriteContext writeContext = new WriteContext(writeApi, buffer, batch, context);
        writeSamples(writeContext);
        flush(writeContext);
        logger.info("Influx batch written: shot={}, channel={}, points={}",
            batch.getShotNo(), batch.getChannelName(), batch.getSamples().size());
    }

    private void validateBatch(WaveformBatch batch) {
        if (batch.getSamples() == null || batch.getSamples().isEmpty()) {
            throw new IllegalArgumentException("Waveform samples are required");
        }
        if (batch.getSampleRateHz() == null || batch.getSampleRateHz() <= 0) {
            throw new IllegalArgumentException("sample_rate_hz is required");
        }
        if (batch.getChunkStartIndex() == null || batch.getChunkStartIndex() < 0) {
            throw new IllegalArgumentException("chunk_start_index is required");
        }
        if (batch.getWindowStart() == null) {
            throw new IllegalArgumentException("window_start is required");
        }
    }

    private BatchContext buildContext(WaveformBatch batch) {
        Instant baseTime = batch.getWindowStart();
        long intervalNanos = Math.round(NANOS_PER_SECOND / batch.getSampleRateHz());
        int baseIndex = batch.getChunkStartIndex();
        return new BatchContext(baseTime, intervalNanos, baseIndex);
    }

    private void writeSamples(WriteContext context) {
        List<Double> samples = context.batch().getSamples();
        for (int index = 0; index < samples.size(); index++) {
            Double value = samples.get(index);
            if (value == null) {
                continue;
            }
            context.buffer().add(buildPoint(context, index, value));
            if (context.buffer().size() >= BATCH_SIZE) {
                flush(context);
            }
        }
    }

    private Point buildPoint(WriteContext context, int index, Double value) {
        BatchContext batchContext = context.batchContext();
        WaveformBatch batch = context.batch();
        Instant timestamp = batchContext.baseTime()
            .plusNanos(batchContext.intervalNanos() * index);
        return Point.measurement(MEASUREMENT)
            .addTag("shot_no", String.valueOf(batch.getShotNo()))
            .addTag("channel_name", batch.getChannelName())
            .addTag("data_type", batch.getDataType())
            .addTag("source_system", batch.getSourceSystem())
            .addTag("process_id", batch.getProcessId())
            .addTag("artifact_id", batch.getArtifactId())
            .addField("value", value)
            .addField("sample_index", batchContext.baseIndex() + index)
            .time(timestamp, WritePrecision.NS);
    }

    private void flush(WriteContext context) {
        if (context.buffer().isEmpty()) {
            return;
        }
        context.writeApi().writePoints(bucket, org, context.buffer());
        context.buffer().clear();
    }

    private record BatchContext(Instant baseTime, long intervalNanos, int baseIndex) {
    }

    private record WriteContext(
        WriteApiBlocking writeApi,
        List<Point> buffer,
        WaveformBatch batch,
        BatchContext batchContext
    ) {
    }
}
