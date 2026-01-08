#!/usr/bin/env python3
"""
TDMS 文件监控工具 - 实时监听新文件并自动同步到 Kafka
"""
import os
import sys
import time
import json
import re
import requests
import subprocess
from pathlib import Path
from watchdog.observers import Observer
from watchdog.events import FileSystemEventHandler

# 配置
CONFIG_FILE = "scan_config.json"
API_BASE_URL = "http://localhost:8080"
SYNC_DELAY = 2  # 文件创建后等待N秒（避免文件未完全写入）

class TdmsFileHandler(FileSystemEventHandler):
    """TDMS 文件事件处理器"""
    
    def __init__(self, api_url, only_keyword="", sync_delay=2, auto_scan=True):
        self.api_url = api_url
        self.only_keyword = only_keyword.lower()
        self.sync_delay = sync_delay
        self.auto_scan = auto_scan  # 是否自动运行 scan.py
        self.processed_files = set()  # 防止重复处理
        self.script_dir = os.path.dirname(os.path.abspath(__file__))
        
    def on_created(self, event):
        """文件创建事件"""
        if event.is_directory:
            return
        
        file_path = event.src_path
        
        # 只处理 .tdms 文件
        if not file_path.lower().endswith('.tdms'):
            return
        
        # 跳过索引文件
        if '.tdms_index' in file_path or '_index' in file_path:
            return
        
        # 关键字过滤
        if self.only_keyword and self.only_keyword not in os.path.basename(file_path).lower():
            return
        
        # 防止重复处理
        if file_path in self.processed_files:
            return
        
        print(f"\n[检测到新文件] {file_path}")
        self.processed_files.add(file_path)
        
        # 延迟处理（等待文件写入完成）
        if self.sync_delay > 0:
            print(f"  等待 {self.sync_delay} 秒以确保文件写入完成...")
            time.sleep(self.sync_delay)
        
        # 提取炮号
        shot_no = self.extract_shot_no(file_path)
        if shot_no is None:
            print(f"  [警告] 无法从文件名提取炮号: {os.path.basename(file_path)}")
            return
        
        # 检测并生成操作日志 & 通道元数据
        if self.auto_scan:
            if self.ensure_operation_log(shot_no, file_path):
                # 生成操作日志成功后，确保通道元数据文件存在
                self.ensure_channels_json(shot_no, file_path)
        
        # 调用同步 API
        self.trigger_sync(shot_no, file_path)
    
    def on_modified(self, event):
        """文件修改事件（可选处理）"""
        # 对于 TDMS 文件，通常只在创建时处理
        # 如果需要处理修改事件，可以取消注释下面的代码
        # self.on_created(event)
        pass
    
    def extract_shot_no(self, file_path):
        """从文件路径提取炮号"""
        basename = os.path.basename(file_path)
        # 匹配文件名开头的数字
        match = re.search(r'^(\d+)', basename)
        if match:
            return int(match.group(1))
        
        # 尝试从父目录名提取
        parent_dir = os.path.basename(os.path.dirname(file_path))
        match = re.search(r'^(\d+)', parent_dir)
        if match:
            return int(match.group(1))
        
        return None
    
    def ensure_operation_log(self, shot_no, file_path):
        """确保操作日志存在，如果不存在则运行 scan.py 生成"""
        # 检测日志文件是否存在
        log_dir = os.path.join(self.script_dir, "TUBE_logs", str(shot_no))
        log_file = os.path.join(log_dir, f"{shot_no}_Tube_operation_log.txt")
        
        if os.path.exists(log_file):
            print(f"  [日志] 操作日志已存在: {log_file}")
            return True
        
        print(f"  [日志] 操作日志不存在，正在生成...")
        
        # 运行 scan.py 生成日志（只处理该炮号）
        try:
            scan_script = os.path.join(self.script_dir, "scan.py")
            tdms_root = os.path.join(self.script_dir, "TUBE")
            logs_output = os.path.join(self.script_dir, "TUBE_logs")
            
            # 使用 --only 参数只处理该炮号的文件
            # 例如: --only "3_" 会匹配 3_Tube.tdms
            only_pattern = f"{shot_no}_"
            
            cmd = [
                "python3", scan_script,
                "--root", tdms_root,
                "--out", logs_output,
                "--only", only_pattern
            ]
            
            print(f"    执行命令: {' '.join(cmd)}")
            
            result = subprocess.run(
                cmd,
                cwd=self.script_dir,
                capture_output=True,
                text=True,
                timeout=60  # 最多等待60秒
            )
            
            if result.returncode == 0:
                # 再次检查日志文件是否生成成功
                if os.path.exists(log_file):
                    print(f"  [成功] 操作日志已生成: {log_file}")
                    return True
                else:
                    print(f"  [警告] scan.py 执行成功但日志文件未生成")
                    print(f"    stdout: {result.stdout[:200]}")
                    return False
            else:
                print(f"  [错误] scan.py 执行失败 (返回码: {result.returncode})")
                print(f"    stderr: {result.stderr[:300]}")
                return False
                
        except subprocess.TimeoutExpired:
            print(f"  [错误] scan.py 执行超时（>60秒）")
            return False
        except Exception as e:
            print(f"  [错误] 运行 scan.py 失败: {e}")
            return False

    def ensure_channels_json(self, shot_no, file_path):
        """确保通道 metadata JSON 存在，否则运行 extract_channels.py 生成"""
        channels_dir = os.path.join(self.script_dir, "channels")
        channels_file = os.path.join(channels_dir, f"channels_{shot_no}.json")

        if os.path.exists(channels_file):
            print(f"  [通道] 通道文件已存在: {channels_file}")
            return True

        print(f"  [通道] 通道文件不存在，正在生成...")

        try:
            extract_script = os.path.join(self.script_dir, "extract_channels.py")

            cmd = [
                "python3", extract_script,
                "--shot", str(shot_no),
                "--data-dir", os.path.join(self.script_dir, "TUBE"),
                "--output-dir", os.path.join(self.script_dir, "channels")
            ]

            print(f"    执行命令: {' '.join(cmd)}")
            result = subprocess.run(cmd, cwd=self.script_dir, capture_output=True, text=True, timeout=20)

            if result.returncode == 0:
                if os.path.exists(channels_file):
                    print(f"  [成功] 通道文件已生成: {channels_file}")
                    return True
                else:
                    print(f"  [警告] extract_channels.py 执行成功但未生成文件")
                    print(f"    stdout: {result.stdout[:200]}")
                    return False
            else:
                print(f"  [错误] extract_channels.py 执行失败 (返回码: {result.returncode})")
                print(f"    stderr: {result.stderr[:300]}")
                return False
        except subprocess.TimeoutExpired:
            print(f"  [错误] extract_channels.py 执行超时（>20秒）")
            return False
        except Exception as e:
            print(f"  [错误] 运行 extract_channels.py 失败: {e}")
            return False
    
    def trigger_sync(self, shot_no, file_path):
        """触发同步 API"""
        url = f"{self.api_url}/api/kafka/sync/shot"
        params = {"shotNo": shot_no}
        
        try:
            print(f"  [同步] 调用 API: {url}?shotNo={shot_no}")
            response = requests.get(url, params=params, timeout=60)  # 增加超时到60秒
            
            if response.status_code == 200:
                result = response.json()
                if result.get("status") == "success":
                    data = result.get("data", {})
                    print(f"  [成功] 炮号 {shot_no} 同步完成")
                    print(f"    - 元数据: {data.get('metadata', 0)}")
                    print(f"    - 波形数据: {data.get('waveData', 0)} 通道")
                    print(f"    - 操作日志: {data.get('operationLog', 0)} 条")
                    print(f"    - PLC 互锁: {data.get('plcInterlock', 0)} 条")
                else:
                    error_msg = result.get('message', '未知错误')
                    print(f"  [失败] {error_msg}")
                    
                    # 如果是元数据不存在的错误，尝试重新生成日志并重试
                    if "元数据不存在" in error_msg or "元数据" in error_msg:
                        print(f"  [重试] 检测到元数据问题，尝试重新生成日志...")
                        if self.ensure_operation_log(shot_no, file_path):
                            # 在生成日志后，确保通道元数据也存在
                            self.ensure_channels_json(shot_no, file_path)
                            print(f"  [重试] 日志已生成，重新调用同步 API...")
                            time.sleep(1)
                            # 递归调用（只重试一次，避免无限循环）
                            response_retry = requests.get(url, params=params, timeout=60)
                            if response_retry.status_code == 200:
                                result_retry = response_retry.json()
                                if result_retry.get("status") == "success":
                                    data_retry = result_retry.get("data", {})
                                    print(f"  [成功] 炮号 {shot_no} 重试同步成功")
                                    print(f"    - 元数据: {data_retry.get('metadata', 0)}")
                                    print(f"    - 波形数据: {data_retry.get('waveData', 0)} 通道")
                                else:
                                    print(f"  [失败] 重试仍然失败: {result_retry.get('message', '未知错误')}")
            else:
                print(f"  [错误] HTTP {response.status_code}: {response.text[:200]}")
        
        except requests.exceptions.ConnectionError:
            print(f"  [错误] 无法连接到 API 服务器: {self.api_url}")
            print(f"    请确保 Java 应用正在运行（端口 8080）")
        except requests.exceptions.Timeout:
            print(f"  [错误] API 请求超时（>60秒）")
        except Exception as e:
            print(f"  [错误] 同步失败: {e}")


def load_config():
    """加载配置文件"""
    script_dir = os.path.dirname(os.path.abspath(__file__))
    config_path = os.path.join(script_dir, CONFIG_FILE)
    
    if os.path.exists(config_path):
        try:
            with open(config_path, 'r', encoding='utf-8') as f:
                config = json.load(f)
            print(f"[配置] 已加载: {config_path}")
            return config
        except Exception as e:
            print(f"[警告] 配置文件加载失败: {e}")
    
    # 返回默认配置
    return {
        "paths": {
            "tdms_root_dir": "TUBE",
            "only_keyword": ""
        }
    }


def main():
    import argparse
    
    parser = argparse.ArgumentParser(description="TDMS 文件实时监控工具")
    parser.add_argument("--watch-dir", help="监控目录（默认从配置文件读取）")
    parser.add_argument("--only", default=None, help="只处理包含关键字的文件")
    parser.add_argument("--api-url", default=API_BASE_URL, help=f"API 服务器地址（默认: {API_BASE_URL}）")
    parser.add_argument("--sync-delay", type=int, default=SYNC_DELAY, 
                       help=f"文件创建后的延迟同步时间（秒，默认: {SYNC_DELAY}）")
    parser.add_argument("--recursive", action="store_true", default=True,
                       help="递归监控子目录（默认开启）")
    
    args = parser.parse_args()
    
    # 加载配置
    config = load_config()
    
    # 确定监控目录
    if args.watch_dir:
        watch_dir = args.watch_dir
    else:
        watch_dir = config["paths"]["tdms_root_dir"]
    
    # 转换为绝对路径
    script_dir = os.path.dirname(os.path.abspath(__file__))
    watch_path = os.path.join(script_dir, watch_dir)
    
    if not os.path.exists(watch_path):
        print(f"[错误] 监控目录不存在: {watch_path}")
        sys.exit(1)
    
    # 确定关键字过滤
    only_keyword = args.only if args.only is not None else config["paths"].get("only_keyword", "")
    
    # 打印配置信息
    print("=" * 60)
    print("TDMS 文件实时监控工具")
    print("=" * 60)
    print(f"监控目录: {watch_path}")
    print(f"关键字过滤: {only_keyword if only_keyword else '(无)'}")
    print(f"API 地址: {args.api_url}")
    print(f"同步延迟: {args.sync_delay} 秒")
    print(f"递归监控: {'是' if args.recursive else '否'}")
    print("=" * 60)
    print("\n按 Ctrl+C 停止监控\n")
    
    # 测试 API 连接
    try:
        response = requests.get(f"{args.api_url}/actuator/health", timeout=5)
        if response.status_code == 200:
            print(f"[连接] API 服务器正常运行 ✓\n")
        else:
            print(f"[警告] API 服务器响应异常: {response.status_code}\n")
    except:
        print(f"[警告] 无法连接到 API 服务器: {args.api_url}")
        print(f"[提示] 请确保 Java 应用正在运行\n")
    
    # 创建事件处理器
    event_handler = TdmsFileHandler(
        api_url=args.api_url,
        only_keyword=only_keyword,
        sync_delay=args.sync_delay
    )
    
    # 创建观察者
    observer = Observer()
    observer.schedule(event_handler, watch_path, recursive=args.recursive)
    observer.start()
    
    print(f"[启动] 正在监控 {watch_path} ...\n")
    
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\n\n[停止] 收到中断信号，正在停止监控...")
        observer.stop()
    
    observer.join()
    print("[完成] 监控已停止")


if __name__ == "__main__":
    main()
