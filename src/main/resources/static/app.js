const { useState, useEffect, useCallback, useRef } = React;

const MAX_POINTS = 50000;
const DEFAULT_DECIMALS = 4;
const SCORE_DECIMALS = 3;

// ── Helpers ──
async function api(url) {
  const r = await fetch(url);
  if (!r.ok) throw new Error(`${r.status}`);
  return r.json();
}

function ensureArray(value, label) {
  if (Array.isArray(value)) return value;
  console.error(`${label} response is not array`, value);
  return [];
}

function waveformUrl(shotNo, ch, start, end, dataType) {
  const p = new URLSearchParams({
    shotNo: String(shotNo), channelName: ch,
    start, end, maxPoints: String(MAX_POINTS),
  });
  if (dataType) p.set('dataType', dataType);
  return `/api/waveform?${p}`;
}

function isoStr(v) {
  if (!v) return null;
  const s = String(v);
  return new Date(/Z$|[+-]\d\d:\d\d$/.test(s) ? s : s + 'Z').toISOString();
}

function fmtDt(v) {
  if (!v) return '—';
  return String(v).replace('T', ' ').substring(0, 19);
}

function fmtTime(v) {
  if (!v) return '—';
  const s = String(v);
  return (s.includes('T') ? s.split('T')[1] : s).substring(0, 12);
}

function titleCase(s) {
  if (!s) return s;
  return s.charAt(0).toUpperCase() + s.slice(1).toLowerCase();
}

function fmtNum(value, digits = DEFAULT_DECIMALS) {
  if (value == null || Number.isNaN(Number(value))) return '—';
  return Number(value).toFixed(digits);
}

function fmtThreshold(op, value) {
  if (op && value != null) return `${op} ${fmtNum(value)}`;
  if (value != null) return fmtNum(value);
  if (op) return op;
  return '—';
}

function fmtRelatedChannels(value) {
  if (!value) return '—';
  if (Array.isArray(value)) return value.join(', ') || '—';
  try {
    const parsed = JSON.parse(value);
    if (Array.isArray(parsed)) return parsed.join(', ') || '—';
    return String(parsed);
  } catch (err) {
    console.error('Invalid related_channels payload', err);
    return `解析失败: ${String(value)}`;
  }
}

function fmtWindow(start, end) {
  if (!start && !end) return '—';
  return `${fmtDt(start)} ~ ${fmtDt(end)}`;
}

const DetailItem = ({ label, children, highlight }) => (
  <div className="detail-item">
    <span className="detail-label">{label}</span>
    <span className={`detail-value${highlight ? ' highlight' : ''}`}>{children}</span>
  </div>
);

const ProtectionEventDetail = ({ event }) => {
  if (!event) {
    return <div className="detail-empty">点击上表中的事件查看详情</div>;
  }
  const sevClass = (event.severity || '').toLowerCase();
  const severityBadge = event.severity
    ? <span className={`sev-badge sev-${sevClass}`}>{event.severity}</span>
    : '—';
  const items = [
    { label: '类型', value: <span className="type-badge">{event.protectionTypeCode || '—'}</span> },
    { label: '范围', value: event.protectionScope || '—' },
    { label: '严重级别', value: severityBadge, highlight: true },
    { label: '触发条件', value: event.triggerCondition || '—' },
    { label: '阈值', value: fmtThreshold(event.thresholdOp, event.thresholdValue), highlight: true },
    { label: '测量值', value: fmtNum(event.measuredValue), highlight: true },
    { label: '动作', value: event.actionTaken || '—', highlight: true },
    { label: '证据分数', value: fmtNum(event.evidenceScore, SCORE_DECIMALS) },
    { label: '关联通道', value: fmtRelatedChannels(event.relatedChannels) },
    { label: '事件窗口', value: fmtWindow(event.windowStart, event.windowEnd) },
    { label: '消息', value: event.messageText || '—' },
  ];
  return (
    <div className="detail-grid">
      {items.map(item => (
        <DetailItem key={item.label} label={item.label} highlight={item.highlight}>{item.value}</DetailItem>
      ))}
    </div>
  );
};

const OperationEventDetail = ({ event }) => {
  if (!event) {
    return <div className="detail-empty">点击上表中的事件查看详情</div>;
  }
  const levelClass = (event.messageLevel || '').toLowerCase();
  const levelBadge = event.messageLevel
    ? <span className={`sev-badge sev-${levelClass}`}>{event.messageLevel}</span>
    : '—';
  const items = [
    { label: '事件编码', value: <span className="type-badge">{event.eventCode || '—'}</span> },
    { label: '事件名称', value: event.eventName || '—' },
    { label: '级别', value: levelBadge },
    { label: '通道', value: event.channelName || '—' },
    { label: '操作类型', value: event.operationTypeCode || '—' },
    { label: '操作模式', value: event.operationModeCode || '—' },
    { label: '旧值', value: fmtNum(event.oldValue, 6), highlight: true },
    { label: '新值', value: fmtNum(event.newValue, 6), highlight: true },
    { label: '变化量', value: fmtNum(event.deltaValue, 6), highlight: true },
    { label: '命令', value: event.commandName || '—' },
    { label: '执行状态', value: event.executionStatus || '—', highlight: true },
    { label: '置信度', value: fmtNum(event.confidence, SCORE_DECIMALS) },
    { label: '进程号', value: event.processId || '—' },
    { label: '操作员', value: event.operatorId || '—' },
    { label: '来源', value: event.sourceSystem || '—' },
    { label: '消息', value: event.messageText || '—' },
  ];
  return (
    <div className="detail-grid">
      {items.map(item => (
        <DetailItem key={item.label} label={item.label} highlight={item.highlight}>{item.value}</DetailItem>
      ))}
    </div>
  );
};

// ── FFT (Cooley-Tukey radix-2) ──
function fft(re, im) {
  const n = re.length;
  if (n <= 1) return;
  // bit-reversal
  for (let i = 1, j = 0; i < n; i++) {
    let bit = n >> 1;
    for (; j & bit; bit >>= 1) j ^= bit;
    j ^= bit;
    if (i < j) {
      [re[i], re[j]] = [re[j], re[i]];
      [im[i], im[j]] = [im[j], im[i]];
    }
  }
  for (let len = 2; len <= n; len <<= 1) {
    const half = len >> 1;
    const angle = -2 * Math.PI / len;
    const wRe = Math.cos(angle), wIm = Math.sin(angle);
    for (let i = 0; i < n; i += len) {
      let curRe = 1, curIm = 0;
      for (let j = 0; j < half; j++) {
        const tRe = curRe * re[i + j + half] - curIm * im[i + j + half];
        const tIm = curRe * im[i + j + half] + curIm * re[i + j + half];
        re[i + j + half] = re[i + j] - tRe;
        im[i + j + half] = im[i + j] - tIm;
        re[i + j] += tRe;
        im[i + j] += tIm;
        const nRe = curRe * wRe - curIm * wIm;
        curIm = curRe * wIm + curIm * wRe;
        curRe = nRe;
      }
    }
  }
}

function computeFFT(values, sampleRate) {
  const n = values.length;
  if (n < 8) return { freqs: [], magnitudes: [] };
  // Pad to next power of 2
  let N = 1;
  while (N < n) N <<= 1;
  const re = new Float64Array(N);
  const im = new Float64Array(N);
  // Apply Hanning window
  for (let i = 0; i < n; i++) {
    const w = 0.5 * (1 - Math.cos(2 * Math.PI * i / (n - 1)));
    re[i] = values[i] * w;
  }
  fft(re, im);
  const half = N >> 1;
  const freqs = new Float64Array(half);
  const mags = new Float64Array(half);
  const scale = 2.0 / N;
  for (let i = 0; i < half; i++) {
    freqs[i] = i * sampleRate / N;
    mags[i] = Math.sqrt(re[i] * re[i] + im[i] * im[i]) * scale;
  }
  return { freqs: Array.from(freqs), magnitudes: Array.from(mags) };
}

// ══════════════════════════════════
// App
// ══════════════════════════════════
const App = () => {
  const [shots, setShots] = useState([]);
  const [shotNo, setShotNo] = useState('');
  const [shot, setShot] = useState(null);
  const [tab, setTab] = useState('Tube');
  const [tubeChannels, setTubeChannels] = useState([]);
  const [waterChannels, setWaterChannels] = useState([]);
  const [selCh, setSelCh] = useState(null);
  const [chartTitle, setChartTitle] = useState('');
  const [opEvents, setOpEvents] = useState([]);
  const [protEvents, setProtEvents] = useState([]);
  const [selProtId, setSelProtId] = useState(null);
  const [selOpId, setSelOpId] = useState(null);
  const [loading, setLoading] = useState(false);
  const [chartLoading, setChartLoading] = useState(false);
  const [sampleCounts, setSampleCounts] = useState({});
  const [eventWindow, setEventWindow] = useState(null);

  const waveChartRef = useRef(null);
  const fftChartRef = useRef(null);
  const waveInstanceRef = useRef(null);
  const fftInstanceRef = useRef(null);
  const ctxRef = useRef(null);

  const channels = tab === 'Tube' ? tubeChannels : waterChannels;
  const selectedProtEvent = protEvents.find(ev => ev.eventId === selProtId) || null;
  const selectedOpEvent = opEvents.find(ev => ev.eventId === selOpId) || null;

  // ── Init ECharts instances ──
  useEffect(() => {
    return () => {
      waveInstanceRef.current?.dispose();
      fftInstanceRef.current?.dispose();
    };
  }, []);

  // Resize observer
  useEffect(() => {
    const handleResize = () => {
      waveInstanceRef.current?.resize();
      fftInstanceRef.current?.resize();
    };
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  // ── Init: load shots ──
  useEffect(() => {
    api('/api/shots').then(ss => {
      setShots(ss);
      if (ss.length > 0) setShotNo(String(ss[0].shotNo));
    }).catch(console.error);
  }, []);

  // ── 首次加载：炮号就绪后自动加载 ──
  const autoLoaded = useRef(false);
  useEffect(() => {
    if (autoLoaded.current) return;
    if (shotNo && shots.length > 0) {
      autoLoaded.current = true;
      handleLoad();
    }
  }, [shotNo, shots, handleLoad]);

  const getOrCreateChart = (ref, instanceRef) => {
    if (!ref.current) return null;
    if (!instanceRef.current || instanceRef.current.isDisposed?.()) {
      instanceRef.current = echarts.init(ref.current);
    }
    return instanceRef.current;
  };

  // ── Load shot data ──
  const handleLoad = useCallback(async () => {
    if (!shotNo) return;
    setLoading(true);
    setSelCh(null);
    setSelProtId(null);
    setSelOpId(null);
    setChartTitle('');
    setSampleCounts({});
    setEventWindow(null);
    waveInstanceRef.current?.clear();
    fftInstanceRef.current?.clear();
    ctxRef.current = null;
    try {
      const found = shots.find(s => String(s.shotNo) === String(shotNo));
      setShot(found || null);
      const [op, prot, tc, wc] = await Promise.all([
        api(`/api/events/operation?shotNo=${shotNo}`),
        api(`/api/events/protection?shotNo=${shotNo}`),
        api(`/api/waveform/channels?shotNo=${shotNo}&dataType=Tube`),
        api(`/api/waveform/channels?shotNo=${shotNo}&dataType=Water`),
      ]);
      const opArr = ensureArray(op, 'operation events');
      const protArr = ensureArray(prot, 'protection events');
      const tubeArr = ensureArray(tc, 'tube channels');
      const waterArr = ensureArray(wc, 'water channels');
      setOpEvents(opArr);
      setProtEvents(protArr);
      setTubeChannels(tubeArr);
      setWaterChannels(waterArr);
      if (protArr.length > 0) setSelProtId(protArr[0].eventId);
      if (opArr.length > 0) setSelOpId(opArr[0].eventId);
      const chs = tab === 'Tube' ? tubeArr : waterArr;
      if (chs.length > 0 && found) {
        await loadChannel(chs[0], found);
      }
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }, [shotNo, shots, tab]);

  // ── Switch tab ──
  const handleTabChange = useCallback(async (newTab) => {
    setTab(newTab);
    const chs = newTab === 'Tube' ? tubeChannels : waterChannels;
    if (chs.length > 0 && shot) {
      if (eventWindow) {
        const ch = chs[0];
        await loadChannelWithWindow(ch, eventWindow.start, eventWindow.end, eventWindow.eventIso);
      } else {
        await loadChannel(chs[0], shot);
      }
    }
  }, [tubeChannels, waterChannels, shot, shotNo, eventWindow]);

  // ── Load channel waveform (full shot) ──
  const loadChannel = async (ch, shotObj) => {
    const s = shotObj || shot;
    if (!s?.shotStartTime || !s?.shotEndTime) return;
    setSelCh(ch);
    setEventWindow(null);
    setChartLoading(true);
    const start = isoStr(s.shotStartTime);
    const end = isoStr(s.shotEndTime);
    try {
      const wf = await api(waveformUrl(shotNo, ch.channelName, start, end, ch.dataType));
      ctxRef.current = {
        shotNo, channel: ch.channelName, dataType: ch.dataType,
        origStart: start, origEnd: end, eventIso: null,
      };
      const pts = wf?.points?.length || 0;
      setSampleCounts(prev => ({ ...prev, [ch.channelName]: pts }));
      setChartTitle(`波形数据 - ${ch.channelName} (来自InfluxDB) - ${pts}个采样点`);
      renderCharts(wf, ch.channelName, null);
    } catch (e) {
      console.error(e);
    } finally {
      setChartLoading(false);
    }
  };

  const EVENT_CONTEXT_SEC = 2;

  const loadChannelWithWindow = async (ch, start, end, eventIso) => {
    setSelCh(ch);
    setChartLoading(true);
    try {
      const wf = await api(waveformUrl(shotNo, ch.channelName, start, end, ch.dataType));
      ctxRef.current = {
        shotNo, channel: ch.channelName, dataType: ch.dataType,
        origStart: start, origEnd: end, eventIso,
      };
      const pts = wf?.points?.length || 0;
      setSampleCounts(prev => ({ ...prev, [ch.channelName]: pts }));
      setChartTitle(`波形数据 - ${ch.channelName} (事件前后${EVENT_CONTEXT_SEC}s) - ${pts}个采样点`);
      renderCharts(wf, ch.channelName, eventIso);
    } catch (e) {
      console.error(e);
    } finally {
      setChartLoading(false);
    }
  };

  const handleChClick = (ch) => {
    if (eventWindow) {
      loadChannelWithWindow(ch, eventWindow.start, eventWindow.end, eventWindow.eventIso);
    } else {
      loadChannel(ch, shot);
    }
  };

  // ── Event click ──
  const handleEventClick = useCallback(async (eventId, eventType) => {
    if (eventType === 'prot') setSelProtId(eventId);
    else setSelOpId(eventId);
    const allEvents = [...protEvents, ...opEvents];
    const evt = allEvents.find(e => e.eventId === eventId);
    if (!evt?.eventTime) return;
    const eventIso = isoStr(evt.eventTime);
    const center = new Date(eventIso).getTime();
    const start = new Date(center - EVENT_CONTEXT_SEC * 1000).toISOString();
    const end = new Date(center + EVENT_CONTEXT_SEC * 1000).toISOString();
    setEventWindow({ start, end, eventIso });
    const ch = selCh || (tab === 'Tube' ? tubeChannels : waterChannels)[0];
    if (ch) {
      await loadChannelWithWindow(ch, start, end, eventIso);
    }
  }, [protEvents, opEvents, selCh, tab, tubeChannels, waterChannels, shotNo]);

  // ── Render waveform + FFT ──
  const renderCharts = (wf, chName, eventIso) => {
    if (!wf?.points?.length) {
      waveInstanceRef.current?.clear();
      fftInstanceRef.current?.clear();
      return;
    }
    const times = wf.points.map(p => p.time);
    const vals = wf.points.map(p => p.value);

    // Waveform chart
    const wChart = getOrCreateChart(waveChartRef, waveInstanceRef);
    if (wChart) {
      const markLine = eventIso ? {
        silent: true, symbol: 'none',
        lineStyle: { color: '#e53935', width: 1.5, type: 'dashed' },
        data: [{ xAxis: eventIso }],
      } : undefined;
      wChart.setOption({
        tooltip: {
          trigger: 'axis',
          backgroundColor: '#fff',
          borderColor: '#1976d2',
          textStyle: { color: '#333', fontSize: 13 },
          formatter: params => {
            const p = params[0];
            if (!p) return '';
            return `时间: ${p.axisValue}<br/>数值: ${Number(p.value).toFixed(6)}`;
          },
        },
        grid: { top: 40, right: 30, bottom: 80, left: 70 },
        xAxis: {
          type: 'category', data: times,
          axisLabel: {
            fontSize: 11, color: '#666',
            formatter: v => {
              const s = String(v);
              const t = s.includes('T') ? s.split('T')[1] : s;
              return t.substring(0, 12);
            },
          },
          axisLine: { lineStyle: { color: '#ccc' } },
          splitLine: { show: true, lineStyle: { color: '#f0f0f0' } },
        },
        yAxis: (() => {
          let yMin = Math.min(...vals);
          let yMax = Math.max(...vals);
          const range = yMax - yMin;
          const PAD_RATIO = 0.08;
          const pad = range > 0 ? range * PAD_RATIO : Math.abs(yMax) * 0.1 || 0.1;
          yMin -= pad;
          yMax += pad;
          return {
            type: 'value', name: chName,
            min: +yMin.toPrecision(6),
            max: +yMax.toPrecision(6),
            nameTextStyle: { fontSize: 12, color: '#666' },
            axisLabel: { fontSize: 11, color: '#666' },
            axisLine: { lineStyle: { color: '#ccc' } },
            splitLine: { lineStyle: { color: '#f0f0f0' } },
          };
        })(),
        dataZoom: [
          { type: 'slider', height: 28, bottom: 10, borderColor: '#ddd',
            fillerColor: 'rgba(25,118,210,0.15)',
            handleStyle: { color: '#1976d2' },
            textStyle: { fontSize: 11 },
          },
          { type: 'inside' },
        ],
        series: [{
          type: 'line', data: vals, symbol: 'none',
          lineStyle: { width: 1.2, color: '#1976d2' },
          markLine,
          large: true, largeThreshold: 5000,
        }],
        animation: false,
      }, true);
    }

    // FFT chart
    const fChart = getOrCreateChart(fftChartRef, fftInstanceRef);
    if (fChart && vals.length >= 8) {
      // Estimate sample rate from timestamps
      const t0 = new Date(times[0]).getTime();
      const t1 = new Date(times[times.length - 1]).getTime();
      const dt = (t1 - t0) / 1000; // seconds
      const sampleRate = dt > 0 ? vals.length / dt : 1000;
      const { freqs, magnitudes } = computeFFT(vals, sampleRate);
      // Only show up to Nyquist/2 for relevance, skip DC
      const showN = Math.min(freqs.length, Math.floor(freqs.length / 2));
      const fSlice = freqs.slice(1, showN);
      const mSlice = magnitudes.slice(1, showN);
      fChart.setOption({
        tooltip: {
          trigger: 'axis',
          backgroundColor: '#fff',
          borderColor: '#e67e22',
          textStyle: { color: '#333', fontSize: 13 },
          formatter: params => {
            const p = params[0];
            if (!p) return '';
            return `频率: ${Number(p.axisValue).toFixed(1)} Hz<br/>幅值: ${Number(p.value).toFixed(6)}`;
          },
        },
        grid: { top: 40, right: 30, bottom: 50, left: 70 },
        xAxis: {
          type: 'category', data: fSlice.map(f => f.toFixed(1)),
          name: '频率 (Hz)',
          nameLocation: 'center', nameGap: 30,
          nameTextStyle: { fontSize: 12, color: '#666' },
          axisLabel: { fontSize: 10, color: '#666', rotate: 0,
            interval: Math.max(0, Math.floor(fSlice.length / 10) - 1),
          },
          axisLine: { lineStyle: { color: '#ccc' } },
        },
        yAxis: (() => {
          // Use P95 as Y-max so outlier peaks don't flatten everything else
          const sorted = [...mSlice].sort((a, b) => a - b);
          const p95 = sorted[Math.floor(sorted.length * 0.95)] || 0;
          const mMax = Math.max(...mSlice);
          const yMax = p95 > 0 ? p95 * 2.5 : mMax * 1.1;
          return {
            type: 'value', name: '幅值',
            max: +yMax.toPrecision(4),
            nameTextStyle: { fontSize: 12, color: '#666' },
            axisLabel: { fontSize: 11, color: '#666' },
            axisLine: { lineStyle: { color: '#ccc' } },
            splitLine: { lineStyle: { color: '#f0f0f0' } },
          };
        })(),
        dataZoom: [
          { type: 'slider', height: 20, bottom: 4, borderColor: '#ddd',
            fillerColor: 'rgba(230,126,34,0.15)',
            handleStyle: { color: '#e67e22' },
          },
          { type: 'inside' },
        ],
        series: [{
          type: 'line', data: mSlice, symbol: 'none',
          lineStyle: { width: 1, color: '#e67e22' },
          areaStyle: { color: 'rgba(230,126,34,0.08)' },
          large: true, largeThreshold: 5000,
        }],
        animation: false,
      }, true);
    }
  };

  // ══════════════════════════════════
  // Render
  // ══════════════════════════════════
  return (
    <React.Fragment>
      <div className="header">
        <div className="header-brand">ECRH Event Viewer</div>
        <div className="header-right">
          <label className="header-label">炮号</label>
          <select className="shot-select" value={shotNo}
            onChange={e => setShotNo(e.target.value)}>
            {shots.map(s => (
              <option key={s.shotNo} value={s.shotNo}>#{s.shotNo}</option>
            ))}
          </select>
          <button className="btn btn-primary" onClick={handleLoad} disabled={loading}>
            {loading ? '加载中…' : '加载数据'}
          </button>
        </div>
      </div>

      <main className="main">
        {loading && <div className="progress-bar" />}

        {/* Metadata */}
        {shot && (
          <section className="card meta-card">
            <div className="meta-row">
              <div className="meta-item">
                <span className="meta-label">炮号</span>
                <span className="meta-value accent">{shot.shotNo}</span>
              </div>
              <div className="meta-item">
                <span className="meta-label">起始时间</span>
                <span className="meta-value">{fmtDt(shot.shotStartTime)}</span>
              </div>
              <div className="meta-item">
                <span className="meta-label">结束时间</span>
                <span className="meta-value">{fmtDt(shot.shotEndTime)}</span>
              </div>
            </div>
            <div className="meta-row">
              <div className="meta-item">
                <span className="meta-label">时长</span>
                <span className="meta-value">{shot.actualDuration != null ? `${Number(shot.actualDuration).toFixed(2)} s` : '—'}</span>
              </div>
              <div className="meta-item">
                <span className="meta-label">状态</span>
                <span className="meta-value danger">{shot.statusCode || '—'}</span>
              </div>
              <div className="meta-item">
                <span className="meta-label">Tube 通道</span>
                <span className="meta-value">{tubeChannels.length}</span>
              </div>
              <div className="meta-item">
                <span className="meta-label">Water 通道</span>
                <span className="meta-value">{waterChannels.length}</span>
              </div>
              <div className="meta-item">
                <span className="meta-label">保护事件</span>
                <span className="meta-value danger">{protEvents.length}</span>
              </div>
              <div className="meta-item">
                <span className="meta-label">操作日志</span>
                <span className="meta-value">{opEvents.length}</span>
              </div>
            </div>
          </section>
        )}

        {/* Waveform Card */}
        <section className="card">
          <div className="card-header">
            <h2 className="card-title">波形数据</h2>
            {chartTitle && <span className="chart-info">{chartTitle}</span>}
          </div>

          <div className="tab-bar">
            <button className={`tab-btn${tab === 'Tube' ? ' active' : ''}`}
              onClick={() => handleTabChange('Tube')}>Tube</button>
            <button className={`tab-btn${tab === 'Water' ? ' active' : ''}`}
              onClick={() => handleTabChange('Water')}>Water</button>
          </div>

          <div className="waveform-body">
            <div className="ch-list">
              <div className="ch-list-header">通道列表</div>
              {channels.map(ch => (
                <div key={ch.channelName}
                  className={`ch-item${selCh?.channelName === ch.channelName && selCh?.dataType === ch.dataType ? ' active' : ''}`}
                  onClick={() => handleChClick(ch)}>
                  <span className="ch-name">{ch.channelName}</span>
                  <span className="ch-sub">采样点: {sampleCounts[ch.channelName] != null ? sampleCounts[ch.channelName] : '—'}</span>
                </div>
              ))}
              {channels.length === 0 && <div className="ch-empty">暂无通道</div>}
            </div>

            <div className="charts-area">
              {chartLoading && <div className="progress-bar prog-inline" />}
              {/* Waveform */}
              <div ref={waveChartRef} className="echart-wave" />
              {/* FFT */}
              <div className="fft-divider">
                <span className="fft-divider-label">FFT 频谱分析</span>
              </div>
              <div ref={fftChartRef} className="echart-fft" />
              {!selCh && !chartLoading && (
                <div className="chart-empty">
                  <div style={{ fontSize: 48, opacity: 0.15 }}>📈</div>
                  <div>选择炮号并加载数据，点击左侧通道查看波形</div>
                </div>
              )}
            </div>
          </div>
        </section>

        {/* Protection Events */}
        <section className="card">
          <div className="card-header">
            <h2 className="card-title">保护事件</h2>
            <span className="badge">{protEvents.length}</span>
          </div>
          {protEvents.length === 0 ? (
            <div className="empty-msg">暂无数据</div>
          ) : (
            <React.Fragment>
              <div className="table-wrap">
                <table className="ev-table">
                  <thead><tr><th className="td-seq">#</th><th>时间</th><th>类型</th><th>事件描述</th></tr></thead>
                  <tbody>
                    {protEvents.map((ev, idx) => (
                      <tr key={ev.eventId}
                        className={selProtId === ev.eventId ? 'active' : ''}
                        onClick={() => handleEventClick(ev.eventId, 'prot')}>
                        <td className="td-seq">{idx + 1}</td>
                        <td className="td-mono">{fmtTime(ev.eventTime)}</td>
                        <td><span className="type-badge">{ev.protectionTypeCode || '—'}</span></td>
                        <td className="td-msg" title={ev.messageText}>{ev.messageText || '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="detail-panel">
                <div className="detail-title">保护事件详情</div>
                <ProtectionEventDetail event={selectedProtEvent} />
              </div>
            </React.Fragment>
          )}
        </section>

        {/* Operation Events */}
        <section className="card">
          <div className="card-header">
            <h2 className="card-title">操作日志</h2>
            <span className="badge">{opEvents.length}</span>
          </div>
          {opEvents.length === 0 ? (
            <div className="empty-msg">暂无数据</div>
          ) : (
            <React.Fragment>
              <div className="table-wrap">
                <table className="ev-table">
                  <thead><tr><th className="td-seq">#</th><th>时间</th><th>事件编码</th><th>通道</th><th>旧值</th><th>新值</th><th>Delta</th><th>消息</th></tr></thead>
                  <tbody>
                    {opEvents.map((ev, idx) => (
                      <tr key={ev.eventId}
                        className={selOpId === ev.eventId ? 'active' : ''}
                        onClick={() => handleEventClick(ev.eventId, 'op')}>
                        <td className="td-seq">{idx + 1}</td>
                        <td className="td-mono">{fmtTime(ev.eventTime)}</td>
                        <td><span className="type-badge">{ev.eventCode || '—'}</span></td>
                        <td>{ev.channelName || '—'}</td>
                        <td className="td-num">{fmtNum(ev.oldValue, 6)}</td>
                        <td className="td-num">{fmtNum(ev.newValue, 6)}</td>
                        <td className="td-num">{fmtNum(ev.deltaValue, 6)}</td>
                        <td className="td-msg" title={ev.messageText}>{ev.messageText || '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="detail-panel">
                <div className="detail-title">操作日志详情</div>
                <OperationEventDetail event={selectedOpEvent} />
              </div>
            </React.Fragment>
          )}
        </section>
      </main>
    </React.Fragment>
  );
};

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(React.createElement(App));
