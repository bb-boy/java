#!/usr/bin/env python3
"""
Kafka数据发送器 - 用于将TDMS文件和日志数据发送到Kafka
可用于测试网络数据源功能
"""

import json
import argparse
from pathlib import Path
from datetime import datetime
from kafka import KafkaProducer
from nptdms import TdmsFile
import re

class DataPublisher:
    def __init__(self, bootstrap_servers='localhost:19092'):
        """初始化Kafka生产者"""
        self.producer = KafkaProducer(
            bootstrap_servers=bootstrap_servers,
            value_serializer=lambda v: json.dumps(v, ensure_ascii=False).encode('utf-8')
        )
        print(f"Kafka生产者已连接: {bootstrap_servers}")
    
    def publish_metadata(self, shot_no, log_file):
        """发送炮号元数据"""
        metadata = self._parse_metadata(shot_no, log_file)
        if metadata:
            self.producer.send('shot-metadata', metadata)
            print(f"已发送元数据: ShotNo={shot_no}")
            return metadata
        return None
    
    def publish_wave_data(self, shot_no, tdms_file, channel_name):
        """发送波形数据"""
        try:
            tdms = TdmsFile.read(tdms_file)
            
            # 获取第一个组
            group = tdms.groups()[0] if tdms.groups() else None
            if not group:
                print(f"TDMS文件没有组: {tdms_file}")
                return
            
            # 查找指定通道
            channel = None
            for ch in group.channels():
                if ch.name == channel_name:
                    channel = ch
                    break
            
            if not channel:
                print(f"未找到通道 {channel_name}")
                return
            
            # 读取数据
            data = channel[:].tolist()
            
            # 构建波形数据对象
            wave_data = {
                'shotNo': shot_no,
                'channelName': channel_name,
                'startTime': channel.properties.get('wf_start_time', datetime.now()).isoformat() if hasattr(channel.properties.get('wf_start_time', None), 'isoformat') else None,
                'sampleRate': channel.properties.get('wf_increment', 1.0),
                'samples': len(data),
                'data': data,
                'fileSource': str(tdms_file),
                'sourceType': 'NETWORK'
            }
            
            self.producer.send('wave-data', wave_data)
            print(f"已发送波形数据: ShotNo={shot_no}, Channel={channel_name}, Samples={len(data)}")
            
        except Exception as e:
            print(f"发送波形数据失败: {e}")
    
    def publish_operation_logs(self, shot_no, log_file):
        """发送操作日志"""
        logs = self._parse_operation_logs(shot_no, log_file)
        for log in logs:
            self.producer.send('operation-log', log)
        print(f"已发送操作日志: ShotNo={shot_no}, Count={len(logs)}")
    
    def publish_plc_interlocks(self, shot_no, plc_file):
        """发送PLC互锁日志"""
        if not Path(plc_file).exists():
            print(f"PLC互锁文件不存在: {plc_file}")
            return
        
        interlocks = self._parse_plc_interlocks(shot_no, plc_file)
        for interlock in interlocks:
            self.producer.send('plc-interlock', interlock)
        print(f"已发送PLC互锁: ShotNo={shot_no}, Count={len(interlocks)}")
    
    def _parse_metadata(self, shot_no, log_file):
        """解析元数据"""
        metadata = {
            'shotNo': shot_no,
            'fileName': f"{shot_no}_Tube",
            'filePath': str(log_file)
        }
        
        try:
            with open(log_file, 'r', encoding='utf-8') as f:
                for line in f:
                    line = line.strip()
                    if line.startswith('Expected'):
                        val = line.split(':')[1].strip().replace(' s', '')
                        if val != 'nans':
                            metadata['expectedDuration'] = float(val)
                    elif line.startswith('Actual'):
                        val = line.split(':')[1].strip().replace(' s', '')
                        metadata['actualDuration'] = float(val)
                    elif line.startswith('Status'):
                        metadata['status'] = line.split(':')[1].strip()
                    elif line.startswith('Reason'):
                        metadata['reason'] = line.split(':')[1].strip()
                    elif line.startswith('StartTime'):
                        metadata['startTime'] = line.split(':', 1)[1].strip()
                    elif line.startswith('EndTime'):
                        metadata['endTime'] = line.split(':', 1)[1].strip()
                    elif line.startswith('Fs'):
                        val = line.split(':')[1].strip().replace(' Hz', '')
                        metadata['sampleRate'] = float(val)
                    elif line.startswith('N '):
                        val = line.split(':')[1].strip().replace(' samples', '')
                        metadata['totalSamples'] = int(val)
                    elif line.startswith('Tolerance'):
                        val = line.split(':')[1].strip().replace(' s', '')
                        metadata['tolerance'] = float(val)
        except Exception as e:
            print(f"解析元数据失败: {e}")
        
        return metadata
    
    def _parse_operation_logs(self, shot_no, log_file):
        """解析操作日志"""
        logs = []
        pattern = re.compile(
            r'\[([\d\-:\s.]+)\]\s+操作\((.+?)\)\s+(.+?)\s+(\w+)\s+旧=([\d.]+)\s+新=([\d.]+)\s+Δ=([+-][\d.]+)\s+置信度=([\d.]+)σ'
        )
        
        try:
            with open(log_file, 'r', encoding='utf-8') as f:
                in_operations = False
                for line in f:
                    if '--- Operations' in line:
                        in_operations = True
                        continue
                    
                    if not in_operations:
                        continue
                    
                    match = pattern.search(line)
                    if match:
                        log = {
                            'shotNo': shot_no,
                            'timestamp': match.group(1),
                            'operationType': match.group(2),
                            'channelName': match.group(3),
                            'stepType': match.group(4),
                            'oldValue': float(match.group(5)),
                            'newValue': float(match.group(6)),
                            'delta': float(match.group(7)),
                            'confidence': float(match.group(8)),
                            'sourceType': 'NETWORK',
                            'fileSource': str(log_file)
                        }
                        logs.append(log)
        except Exception as e:
            print(f"解析操作日志失败: {e}")
        
        return logs
    
    def _parse_plc_interlocks(self, shot_no, plc_file):
        """解析PLC互锁日志 (根据实际格式调整)"""
        interlocks = []
        # TODO: 根据实际PLC日志格式实现
        return interlocks
    
    def publish_shot(self, shot_no, data_dir='data/TUBE', log_dir='data/TUBE_logs'):
        """发送完整炮号数据"""
        print(f"\n{'='*60}")
        print(f"开始发送炮号 {shot_no} 的数据")
        print(f"{'='*60}")
        
        # 1. 发送元数据
        log_file = Path(log_dir) / str(shot_no) / f"{shot_no}_Tube_operation_log.txt"
        metadata = self.publish_metadata(shot_no, log_file)
        
        # 2. 发送波形数据 (Tube)
        tdms_file = Path(data_dir) / str(shot_no) / f"{shot_no}_Tube.tdms"
        if tdms_file.exists():
            # 获取所有通道并发送
            try:
                tdms = TdmsFile.read(tdms_file)
                group = tdms.groups()[0] if tdms.groups() else None
                if group:
                    for channel in group.channels():
                        self.publish_wave_data(shot_no, tdms_file, channel.name)
            except Exception as e:
                print(f"读取TDMS文件失败: {e}")
        
        # 3. 发送波形数据 (Water)
        water_file = Path(data_dir) / str(shot_no) / f"{shot_no}_Water.tdms"
        if water_file.exists():
            try:
                tdms = TdmsFile.read(water_file)
                group = tdms.groups()[0] if tdms.groups() else None
                if group:
                    for channel in group.channels():
                        self.publish_wave_data(shot_no, water_file, channel.name)
            except Exception as e:
                print(f"读取Water TDMS文件失败: {e}")
        
        # 4. 发送操作日志
        if log_file.exists():
            self.publish_operation_logs(shot_no, log_file)
        
        # 5. 发送PLC互锁 (如果存在)
        plc_file = Path('data/PLC_logs') / str(shot_no) / f"{shot_no}_plc.txt"
        if plc_file.exists():
            self.publish_plc_interlocks(shot_no, plc_file)
        
        print(f"炮号 {shot_no} 数据发送完成\n")
    
    def close(self):
        """关闭生产者"""
        self.producer.flush()
        self.producer.close()
        print("Kafka生产者已关闭")


def main():
    parser = argparse.ArgumentParser(description='将TDMS和日志数据发送到Kafka')
    parser.add_argument('--shot', type=int, help='指定炮号')
    parser.add_argument('--shots', nargs='+', type=int, help='批量炮号列表')
    parser.add_argument('--all', action='store_true', help='发送所有炮号')
    parser.add_argument('--data-dir', default='data/TUBE', help='TDMS数据目录')
    parser.add_argument('--log-dir', default='data/TUBE_logs', help='日志目录')
    parser.add_argument('--kafka', default='localhost:19092', help='Kafka服务器地址')
    
    args = parser.parse_args()
    
    publisher = DataPublisher(args.kafka)
    
    try:
        if args.shot:
            # 发送单个炮号
            publisher.publish_shot(args.shot, args.data_dir, args.log_dir)
        
        elif args.shots:
            # 发送多个炮号
            for shot_no in args.shots:
                publisher.publish_shot(shot_no, args.data_dir, args.log_dir)
        
        elif args.all:
            # 发送所有炮号
            data_path = Path(args.data_dir)
            shot_dirs = [d for d in data_path.iterdir() if d.is_dir() and d.name.isdigit()]
            shot_numbers = sorted([int(d.name) for d in shot_dirs])
            
            print(f"找到 {len(shot_numbers)} 个炮号")
            for shot_no in shot_numbers:
                publisher.publish_shot(shot_no, args.data_dir, args.log_dir)
        
        else:
            print("请指定 --shot, --shots 或 --all 参数")
            parser.print_help()
    
    finally:
        publisher.close()


if __name__ == '__main__':
    main()
