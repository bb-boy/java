import os
import argparse
import json
import numpy as np
import pandas as pd
from nptdms import TdmsFile
import re
import subprocess

# ========================================
# 配置文件加载
# ========================================
def load_config(config_path: str = None) -> dict:
    """加载配置文件，如果路径不指定，则使用默认位置"""
    if config_path is None:
        script_dir = os.path.dirname(os.path.abspath(__file__))
        config_path = os.path.join(script_dir, "scan_config.json")
    
    if not os.path.exists(config_path):
        print(f"[WARN] 配置文件不存在: {config_path}，使用硬编码默认值")
        return get_default_config()
    
    try:
        with open(config_path, 'r', encoding='utf-8') as f:
            config = json.load(f)
        print(f"[INFO] 成功加载配置文件: {config_path}")
        return config
    except Exception as e:
        print(f"[WARN] 配置文件加载失败: {e}，使用默认值")
        return get_default_config()

def get_default_config() -> dict:
    """返回默认配置"""
    return {
        "paths": {
            "tdms_root_dir": "TUBE",
            "output_dir": "TUBE_logs",
            "only_keyword": "",
            "iplen_unit": "auto"
        },
        "detection_params": {
            "step": {
                "smooth_window": 21,
                "level_window": 200,
                "prepost_window": 100,
                "quantile": 0.99,
                "merge_gap": 40,
                "min_delta_abs": 0.0,
                "min_sigma": 1.5
            },
            "ramp": {
                "smooth_window": 21,
                "slope_window": 250,
                "quantile": 0.98,
                "merge_gap": 120,
                "min_duration_ms": 150,
                "min_delta_abs": 0.0,
                "min_sigma": 2.0,
                "dt_seconds": 0.001
            }
        }
    }

# ========================================
# 基础工具
# ========================================
def robust_sigma(x: np.ndarray) -> float:
    x = np.asarray(x, dtype=float)
    med = np.median(x)
    mad = np.median(np.abs(x - med))
    sig = 1.4826 * mad
    if not np.isfinite(sig) or sig == 0:
        sig = float(np.std(x)) if float(np.std(x)) > 0 else 1e-12
    return sig

def rolling_median(x: np.ndarray, w: int) -> np.ndarray:
    return pd.Series(np.asarray(x, dtype=float)).rolling(w, center=True, min_periods=1).median().to_numpy()

def rolling_mean(x: np.ndarray, w: int) -> np.ndarray:
    return pd.Series(np.asarray(x, dtype=float)).rolling(w, center=True, min_periods=1).mean().to_numpy()

def group_indices(idxs: np.ndarray, merge_gap: int):
    if idxs.size == 0:
        return []
    idxs = np.asarray(idxs, dtype=int)
    groups = []
    s = int(idxs[0]); prev = int(idxs[0])
    for i in idxs[1:]:
        i = int(i)
        if i - prev <= merge_gap:
            prev = i
        else:
            groups.append((s, prev))
            s = i; prev = i
    groups.append((s, prev))
    return groups

def ts_fmt(ts) -> str:
    # 输出绝对时间戳（毫秒）
    try:
        return pd.Timestamp(ts).isoformat(sep=" ", timespec="milliseconds")
    except Exception:
        return str(ts)

def group_channel_names(g):
    return [ch.name for ch in g.channels()]

def get_channel(g, name: str):
    return g[name]

def pick_prop(props, names, contains=None):
    for n in names:
        if n in props:
            return props[n]
    lower_map = {str(k).lower(): k for k in props.keys()}
    for n in names:
        k = lower_map.get(str(n).lower())
        if k is not None:
            return props[k]
    if contains:
        key = str(contains).lower()
        for k in props.keys():
            if key in str(k).lower():
                return props[k]
    return None

def infer_shot_no(tdms_path: str):
    base = os.path.basename(tdms_path)
    m = re.search(r"(\d+)", base)
    return int(m.group(1)) if m else None

def infer_iplen_expected(iplen, fs, t_actual, unit="auto"):
    if iplen is None:
        return np.nan, "missing"
    try:
        val = float(iplen)
    except Exception:
        return np.nan, "invalid"
    if not np.isfinite(val) or val <= 0:
        return np.nan, "invalid"

    if unit == "samples":
        return val / fs, "samples"
    if unit == "seconds":
        return val, "seconds"

    # auto: pick closer to actual duration
    cand_samples = val / fs
    if np.isfinite(t_actual):
        if abs(cand_samples - t_actual) < abs(val - t_actual):
            return cand_samples, "auto(samples)"
    if np.isfinite(t_actual) and val > (t_actual * 5.0):
        return cand_samples, "auto(samples)"
    return val, "auto(seconds)"

# ========================================
# 变化检测：阶跃 + 斜坡（用于"调参/操作"）
# ========================================
def detect_steps(name, t_abs, x, config_params,
                 min_delta_abs=0.0):
    """
    检测阶跃变化
    
    Args:
        name: 参数名
        t_abs: 绝对时间轴
        x: 数据序列
        config_params: 来自 config 的 step 参数字典
        min_delta_abs: 额外的最小变化量阈值（通常来自 infer_operations）
    """
    smooth_w = config_params.get("smooth_window", 21)
    level_w = config_params.get("level_window", 200)
    prepost_w = config_params.get("prepost_window", 100)
    q = config_params.get("quantile", 0.99)
    merge_gap = config_params.get("merge_gap", 40)
    cfg_min_delta = config_params.get("min_delta_abs", 0.0)
    min_sigma = config_params.get("min_sigma", 1.5)
    
    # 取较大的最小变化量
    min_delta_abs = max(min_delta_abs, cfg_min_delta)
    
    x = np.asarray(x, dtype=float)
    xs = rolling_median(x, smooth_w)
    lvl = rolling_mean(xs, level_w)

    dl = np.diff(lvl, prepend=lvl[0])
    med = float(np.median(dl))
    thr = float(np.quantile(np.abs(dl - med), q))
    cand = np.where(np.abs(dl - med) > thr)[0]
    if cand.size == 0:
        return []

    sig_lvl = robust_sigma(lvl)
    prepost = max(10, int(prepost_w))

    rows = []
    for s, e in group_indices(cand, merge_gap=merge_gap):
        mid = (s + e) // 2

        pre0 = max(0, mid - prepost); pre1 = max(0, mid - 1)
        post0 = min(len(lvl) - 1, mid + 1); post1 = min(len(lvl) - 1, mid + prepost)

        old = float(np.median(lvl[pre0:pre1+1])) if pre1 >= pre0 else float(lvl[mid])
        new = float(np.median(lvl[post0:post1+1])) if post1 >= post0 else float(lvl[mid])
        delta = new - old

        if abs(delta) < min_delta_abs:
            continue
        if abs(delta) < (min_sigma * sig_lvl):
            continue

        rows.append({
            "time": pd.Timestamp(t_abs[mid]),
            "parameter": name,
            "mode": "step",
            "old_value": old,
            "new_value": new,
            "delta": delta,
            "direction": "increase" if delta > 0 else "decrease",
            "confidence_sigma": abs(delta) / (sig_lvl if sig_lvl else 1e-12),
        })
    return rows

def detect_ramps(name, t_abs, x, config_params,
                 min_delta_abs=0.0):
    """
    检测斜坡变化
    
    Args:
        name: 参数名
        t_abs: 绝对时间轴
        x: 数据序列
        config_params: 来自 config 的 ramp 参数字典
        min_delta_abs: 额外的最小变化量阈值（通常来自 infer_operations）
    """
    smooth_w = config_params.get("smooth_window", 21)
    slope_w = config_params.get("slope_window", 250)
    q = config_params.get("quantile", 0.98)
    merge_gap = config_params.get("merge_gap", 120)
    min_dur_ms = config_params.get("min_duration_ms", 150)
    cfg_min_delta = config_params.get("min_delta_abs", 0.0)
    min_sigma = config_params.get("min_sigma", 2.0)
    dt_s = config_params.get("dt_seconds", 0.001)
    
    # 取较大的最小变化量
    min_delta_abs = max(min_delta_abs, cfg_min_delta)
    
    x = np.asarray(x, dtype=float)
    xs = rolling_median(x, smooth_w)

    slope = np.diff(xs, prepend=xs[0]) / dt_s
    slope_sm = pd.Series(slope).rolling(slope_w, center=True, min_periods=1).mean().to_numpy()

    med = float(np.median(slope_sm))
    thr = float(np.quantile(np.abs(slope_sm - med), q))
    mask = np.abs(slope_sm - med) > thr
    idxs = np.where(mask)[0]
    if idxs.size == 0:
        return []

    sig_x = robust_sigma(xs)
    rows = []
    for s, e in group_indices(idxs, merge_gap=merge_gap):
        if (e - s + 1) < int(min_dur_ms):
            continue

        old = float(xs[s])
        new = float(xs[e])
        delta = new - old

        if abs(delta) < min_delta_abs:
            continue
        if abs(delta) < (min_sigma * sig_x):
            continue

        rows.append({
            "time": pd.Timestamp(t_abs[s]),
            "parameter": name,
            "mode": "ramp",
            "old_value": old,
            "new_value": new,
            "delta": delta,
            "direction": "increase" if delta > 0 else "decrease",
            "confidence_sigma": abs(delta) / (sig_x if sig_x else 1e-12),
        })
    return rows

def infer_operations(t_abs, neg_v, pos_v, fila_i, fila_v, wf_inc_s, config):
    """
    推断操作/调参事件
    
    Args:
        config: 加载的配置对象
    """
    step_cfg = config["detection_params"]["step"]
    ramp_cfg = config["detection_params"]["ramp"]
    
    fila_p = np.asarray(fila_i, dtype=float) * np.asarray(fila_v, dtype=float)
    dt_s = float(wf_inc_s) if wf_inc_s else ramp_cfg.get("dt_seconds", 0.001)

    ops = []
    
    ops += detect_steps("阴极电压(NegVoltage)", t_abs, neg_v, step_cfg, min_delta_abs=0.001)
    ops += detect_ramps("阴极电压(NegVoltage)", t_abs, neg_v, ramp_cfg, min_delta_abs=0.002)

    ops += detect_steps("阳极电压(PosVoltage)", t_abs, pos_v, step_cfg, min_delta_abs=0.001)
    ops += detect_ramps("阳极电压(PosVoltage)", t_abs, pos_v, ramp_cfg, min_delta_abs=0.002)

    sig_fp = robust_sigma(fila_p)
    ops += detect_steps("灯丝功率(FilaI*FilaV)", t_abs, fila_p, step_cfg, 
                       min_delta_abs=max(0.01, 5.0 * sig_fp))
    ops += detect_ramps("灯丝功率(FilaI*FilaV)", t_abs, fila_p, ramp_cfg,
                       min_delta_abs=max(0.015, 8.0 * sig_fp))

    df = pd.DataFrame(ops)
    if len(df):
        df = df.sort_values("time").reset_index(drop=True)
    return df

# ========================================
# 单个 TDMS：输出 txt（元数据 + 操作）
# ========================================
def process_one_tdms(tdms_path: str, out_txt: str, config, 
                     tol_ratio=0.02, tol_min_s=0.02):
    iplen_unit = config["paths"].get("iplen_unit", "auto")
    
    tdms = TdmsFile.read(tdms_path)
    props = tdms.properties or {}

    shot_no_from_props = pick_prop(props, ["ShotNo", "shot_no", "shotno"], contains="shot")
    shot_no_from_file = infer_shot_no(tdms_path)
    shot_no = shot_no_from_props if shot_no_from_props is not None else shot_no_from_file
    if shot_no_from_props is not None:
        shot_no_src = "tdms"
    elif shot_no_from_file is not None:
        shot_no_src = "file"
    else:
        shot_no_src = "unknown"

    iplen = pick_prop(props, ["LpLen", "lplen", "IpLen", "iplen"], contains="lplen")
    name = props.get("name", os.path.splitext(os.path.basename(tdms_path))[0])

    g = tdms.groups()[0]
    # 采样点数（取最大）
    N = int(max(len(ch) for ch in g.channels()))

    # 采样间隔/频率
    wf_inc = None
    for ch in g.channels():
        if ch.properties and "wf_increment" in ch.properties:
            wf_inc = float(ch.properties["wf_increment"])
            break
    if wf_inc is None:
        t_tmp = g.channels()[0].time_track(absolute_time=True)
        dt_ns = np.median(np.diff(t_tmp).astype("timedelta64[ns]").astype(np.int64))
        wf_inc = dt_ns / 1e9

    Fs = 1.0 / wf_inc
    T_actual = N / Fs

    T_expected, iplen_mode = infer_iplen_expected(iplen, Fs, T_actual, unit=iplen_unit)
    tol = max(tol_ratio * T_expected, tol_min_s) if np.isfinite(T_expected) else tol_min_s

    completed = bool(T_actual >= (T_expected - tol)) if np.isfinite(T_expected) else True
    status = "正常完成" if completed else "保护触发(推断：未跑完)"
    reason = "总采样时长 >= 预设脉宽(考虑容差)" if completed else "总采样时长 < 预设脉宽(考虑容差)"

    channel_names = group_channel_names(g)

    # 绝对时间轴（用 NegVoltage；没有就用第一个通道）
    ch_time = get_channel(g, "NegVoltage") if "NegVoltage" in channel_names else g.channels()[0]
    t_abs = ch_time.time_track(absolute_time=True)
    start_time = pd.Timestamp(t_abs[0])
    end_time = pd.Timestamp(t_abs[min(N-1, len(t_abs)-1)])

    # 关键通道
    need = ["NegVoltage", "PosVoltage", "FilaCurrent", "FilaVoltage"]
    missing = [k for k in need if k not in channel_names]
    if missing:
        raise RuntimeError(f"Missing channels {missing}")

    neg_v = get_channel(g, "NegVoltage")[:]
    pos_v = get_channel(g, "PosVoltage")[:]
    fila_i = get_channel(g, "FilaCurrent")[:]
    fila_v = get_channel(g, "FilaVoltage")[:]

    ops_df = infer_operations(t_abs, neg_v, pos_v, fila_i, fila_v, wf_inc, config)

    # 写 txt
    os.makedirs(os.path.dirname(out_txt), exist_ok=True)
    with open(out_txt, "w", encoding="utf-8") as f:
        f.write("=== TDMS Operation Log ===\n")
        f.write(f"File      : {tdms_path}\n")
        f.write(f"Name      : {name}\n")
        f.write(f"ShotNo    : {shot_no}\n")
        f.write(f"ShotNoSrc : {shot_no_src}\n")
        f.write(f"ShotNoTDMS: {shot_no_from_props}\n")
        f.write(f"ShotNoFile: {shot_no_from_file}\n")
        f.write(f"LpLen     : {iplen}\n")
        f.write(f"LpLenUnit : {iplen_mode}\n")
        f.write(f"Fs        : {Fs:.3f} Hz\n")
        f.write(f"N         : {N} samples\n")
        f.write(f"Expected  : {T_expected:.3f} s\n")
        f.write(f"Actual    : {T_actual:.3f} s\n")
        f.write(f"Tolerance : {tol:.3f} s\n")
        f.write(f"Status    : {status}\n")
        f.write(f"Reason    : {reason}\n")
        f.write(f"StartTime : {ts_fmt(start_time)}\n")
        f.write(f"EndTime   : {ts_fmt(end_time)}\n\n")

        f.write("--- Operations (absolute timestamps) ---\n")
        if len(ops_df) == 0:
            f.write("(no operations detected)\n")
        else:
            for _, r in ops_df.iterrows():
                line = (
                    f"[{ts_fmt(r['time'])}] 操作(调参) {r['parameter']} {r['mode']} "
                    f"旧={r['old_value']:.6g} 新={r['new_value']:.6g} "
                    f"Δ={r['delta']:+.3g} 置信度={r['confidence_sigma']:.2f}σ\n"
                )
                f.write(line)

    # 尝试生成通道 JSON（如果需要）
    try:
        script_dir = os.path.dirname(os.path.abspath(__file__))
        extract_script = os.path.join(script_dir, "extract_channels.py")
        tube_root = os.path.dirname(os.path.dirname(tdms_path))  # /.../data/TUBE
        channels_out = os.path.join(script_dir, "channels")
        cmd = ["python3", extract_script, "--shot", str(shot_no), "--data-dir", tube_root, "--output-dir", channels_out]
        print(f"[INFO] 生成通道文件: {' '.join(cmd)}")
        subprocess.run(cmd, cwd=script_dir, timeout=20, capture_output=True, text=True)
    except Exception as e:
        print(f"[WARN] 自动生成通道文件失败: {e}")

    return {
        "tdms": tdms_path,
        "out_txt": out_txt,
        "ShotNo": shot_no,
        "status": status,
        "actual_s": T_actual,
        "expected_s": T_expected
    }

# ========================================
# 扫描目录：批量输出 txt
# ========================================
def scan_root(root_dir: str, out_dir: str, config, only_keyword: str = ""):
    results = []
    for dirpath, _, filenames in os.walk(root_dir):
        for fn in filenames:
            low = fn.lower()
            if not low.endswith(".tdms"):
                continue
            if low.endswith(".tdms_index") or "_index" in low:
                continue
            if only_keyword and (only_keyword.lower() not in low):
                continue

            tdms_path = os.path.join(dirpath, fn)

            # 输出路径：保持子目录结构
            rel_dir = os.path.relpath(dirpath, root_dir)
            out_sub = os.path.join(out_dir, rel_dir)
            base = os.path.splitext(fn)[0]
            out_txt = os.path.join(out_sub, f"{base}_operation_log.txt")

            try:
                res = process_one_tdms(tdms_path, out_txt, config)
                results.append(res)
                print(f"[OK] {tdms_path} -> {out_txt}")
            except Exception as e:
                print(f"[ERR] {tdms_path} -> {e}")
                results.append({"tdms": tdms_path, "error": str(e)})

    # 额外输出一个总索引（方便你快速看哪炮成功/保护）
    index_path = os.path.join(out_dir, "ALL_INDEX.txt")
    os.makedirs(out_dir, exist_ok=True)
    with open(index_path, "w", encoding="utf-8") as f:
        f.write("=== TDMS Batch Index ===\n")
        for r in results:
            if "error" in r:
                f.write(f"[ERR] {r['tdms']} | {r['error']}\n")
            else:
                f.write(f"[OK] ShotNo={r['ShotNo']} | {r['status']} | actual={r['actual_s']:.3f}s expected={r['expected_s']:.3f}s | {r['out_txt']}\n")
    print(f"\nIndex written: {index_path}")

if __name__ == "__main__":
    ap = argparse.ArgumentParser(description="TDMS 扫描和操作检测工具")
    ap.add_argument("--config", default=None, help="配置文件路径（默认为脚本目录下的 scan_config.json）")
    ap.add_argument("--root", default=None, help="TDMS根目录（覆盖配置文件中的设置）")
    ap.add_argument("--out", default=None, help="输出目录（覆盖配置文件中的设置）")
    ap.add_argument("--only", default=None, help="只处理文件名包含关键字的tdms（覆盖配置文件中的设置）")
    ap.add_argument("--iplen-unit", default=None, choices=["auto", "samples", "seconds"], 
                   help="LpLen单位（覆盖配置文件中的设置）")
    args = ap.parse_args()

    # 加载配置文件
    config = load_config(args.config)
    
    # 命令行参数覆盖配置文件中的设置
    root_dir = args.root or config["paths"]["tdms_root_dir"]
    out_dir = args.out or config["paths"]["output_dir"]
    only_keyword = args.only if args.only is not None else config["paths"]["only_keyword"]
    if args.iplen_unit:
        config["paths"]["iplen_unit"] = args.iplen_unit

    print(f"[INFO] 使用配置:")
    print(f"  TDMS根目录: {root_dir}")
    print(f"  输出目录: {out_dir}")
    print(f"  关键字过滤: {only_keyword if only_keyword else '(无)'}")
    print(f"  IpLen单位: {config['paths']['iplen_unit']}\n")

    scan_root(root_dir, out_dir, config, only_keyword=only_keyword)
