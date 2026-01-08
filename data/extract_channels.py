#!/usr/bin/env python3
"""
通道提取工具 - 从TDMS文件提取所有通道信息
用于解决Java无法读取TDMS文件的问题

使用: python extract_channels.py --shot 1
"""

import json
import argparse
from pathlib import Path
from datetime import datetime
from nptdms import TdmsFile


class ChannelExtractor:
    def __init__(self, data_dir='data/TUBE', output_dir='channels'):
        self.data_dir = Path(data_dir)
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(exist_ok=True)
    
    def extract_shot_channels(self, shot_no):
        """提取指定炮号的所有通道信息"""
        channels_info = {
            'shotNo': shot_no,
            'extractTime': datetime.now().isoformat(),
            'dataTypes': {}
        }
        
        # 提取Tube数据
        tube_channels = self._extract_tdms_channels(shot_no, 'Tube')
        if tube_channels:
            channels_info['dataTypes']['Tube'] = tube_channels
        
        # 提取Water数据
        water_channels = self._extract_tdms_channels(shot_no, 'Water')
        if water_channels:
            channels_info['dataTypes']['Water'] = water_channels
        
        return channels_info
    
    def _extract_tdms_channels(self, shot_no, data_type):
        """从TDMS文件提取通道列表"""
        tdms_file = self.data_dir / str(shot_no) / f"{shot_no}_{data_type}.tdms"
        
        if not tdms_file.exists():
            print(f"⚠️  文件不存在: {tdms_file}")
            return None
        
        try:
            tdms = TdmsFile.read(tdms_file)
            channels = []
            
            if tdms.groups():
                group = tdms.groups()[0]
                
                for ch in group.channels():
                    channel_info = {
                        'name': ch.name,
                        'dataPoints': len(ch[:]),
                        'dataType': str(ch[:].dtype),
                        'properties': {}
                    }
                    
                    # 提取关键属性
                    if hasattr(ch, 'properties'):
                        props = ch.properties
                        if 'wf_start_time' in props:
                            channel_info['startTime'] = str(props['wf_start_time'])
                        if 'wf_increment' in props:
                            channel_info['sampleInterval'] = props['wf_increment']
                        if 'unit_string' in props:
                            channel_info['unit'] = props['unit_string']
                        if 'NI_ChannelName' in props:
                            channel_info['niName'] = props['NI_ChannelName']
                    
                    channels.append(channel_info)
            
            return {
                'count': len(channels),
                'channels': channels
            }
        
        except Exception as e:
            print(f"❌ 提取失败: {e}")
            return None
    
    def save_channels_json(self, shot_no, channels_info):
        """保存通道信息为JSON"""
        output_file = self.output_dir / f"channels_{shot_no}.json"
        
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(channels_info, f, indent=2, ensure_ascii=False)
        
        return output_file
    
    def print_channels_summary(self, channels_info):
        """打印通道摘要"""
        print(f"\n{'='*70}")
        print(f"📊 炮号 {channels_info['shotNo']} 的通道统计")
        print(f"{'='*70}\n")
        
        for data_type, type_info in channels_info.get('dataTypes', {}).items():
            count = type_info['count']
            print(f"📁 {data_type}文件: {count} 个通道")
            print(f"{'-'*70}")
            
            for i, ch in enumerate(type_info['channels'], 1):
                print(f"\n  {i}. 通道名: {ch['name']}")
                print(f"     数据点: {ch['dataPoints']}")
                print(f"     数据类型: {ch['dataType']}")
                
                if 'startTime' in ch:
                    print(f"     开始时间: {ch['startTime']}")
                if 'sampleInterval' in ch:
                    print(f"     采样间隔: {ch['sampleInterval']}")
                if 'unit' in ch:
                    print(f"     单位: {ch['unit']}")
            
            print()


def main():
    parser = argparse.ArgumentParser(description='从TDMS文件提取通道信息')
    parser.add_argument('--shot', type=int, help='指定炮号')
    parser.add_argument('--shots', nargs='+', type=int, help='批量炮号')
    parser.add_argument('--all', action='store_true', help='提取所有炮号')
    parser.add_argument('--data-dir', default='data/TUBE', help='数据目录')
    parser.add_argument('--output-dir', default='channels', help='输出目录')
    
    args = parser.parse_args()
    
    extractor = ChannelExtractor(args.data_dir, args.output_dir)
    
    shot_numbers = []
    
    if args.shot:
        shot_numbers = [args.shot]
    elif args.shots:
        shot_numbers = args.shots
    elif args.all:
        data_path = Path(args.data_dir)
        if data_path.exists():
            shot_dirs = [d for d in data_path.iterdir() 
                        if d.is_dir() and d.name.isdigit()]
            shot_numbers = sorted([int(d.name) for d in shot_dirs])
    else:
        parser.print_help()
        return
    
    print(f"\n正在处理 {len(shot_numbers)} 个炮号...\n")
    
    for shot_no in shot_numbers:
        print(f"⏳ 处理炮号 {shot_no}...")
        
        channels_info = extractor.extract_shot_channels(shot_no)
        
        if channels_info:
            output_file = extractor.save_channels_json(shot_no, channels_info)
            extractor.print_channels_summary(channels_info)
            print(f"✅ 已保存到: {output_file}\n")


if __name__ == '__main__':
    main()
