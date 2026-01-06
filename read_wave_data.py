#!/usr/bin/env python3
"""
TDMS波形数据读取工具
从TDMS文件中提取指定通道的波形数据，并输出为JSON格式
"""

import json
import sys
from pathlib import Path
from nptdms import TdmsFile

def read_wave_data(shot_no: int, channel_name: str, data_type: str) -> dict:
    """
    从TDMS文件读取波形数据
    
    :param shot_no: 炮号
    :param channel_name: 通道名称
    :param data_type: 数据类型 (Tube 或 Water)
    :return: 波形数据字典
    """
    # 构建TDMS文件路径
    # Tube 和 Water TDMS 文件都在 data/TUBE/{shotNo}/ 目录下
    file_path = Path(f"data/TUBE/{shot_no}/{shot_no}_{data_type}.tdms")
    
    if not file_path.exists():
        return {
            "error": f"文件不存在: {file_path}",
            "shotNo": shot_no,
            "channelName": channel_name,
            "data": None
        }
    
    try:
        # 打开TDMS文件
        tdms_file = TdmsFile.read(str(file_path))
        
        # 遍历所有组和通道找到目标通道
        for group in tdms_file.groups():
            for channel in group.channels():
                if channel.name == channel_name:
                    # 获取波形数据
                    wave_data = channel[:]
                    
                    # 转换为列表并进行降采样（如果数据太大）
                    data_list = wave_data.tolist() if hasattr(wave_data, 'tolist') else list(wave_data)
                    
                    # 如果数据超过10000个点，进行降采样
                    if len(data_list) > 10000:
                        step = len(data_list) // 10000
                        data_list = data_list[::step]
                    
                    return {
                        "shotNo": shot_no,
                        "channelName": channel_name,
                        "dataType": data_type,
                        "samples": len(wave_data),
                        "data": data_list,
                        "sampleRate": getattr(channel, 'properties', {}).get('SamplingFrequency', 1000.0) if hasattr(channel, 'properties') else 1000.0,
                        "status": "success"
                    }
        
        # 如果没找到通道
        return {
            "error": f"通道不存在: {channel_name}",
            "shotNo": shot_no,
            "channelName": channel_name,
            "data": None,
            "availableChannels": [ch.name for group in tdms_file.groups() for ch in group.channels()]
        }
        
    except Exception as e:
        return {
            "error": f"读取失败: {str(e)}",
            "shotNo": shot_no,
            "channelName": channel_name,
            "data": None
        }

if __name__ == "__main__":
    if len(sys.argv) < 4:
        print(json.dumps({
            "error": "用法: python read_wave_data.py <shot_no> <channel_name> <data_type>"
        }))
        sys.exit(1)
    
    shot_no = int(sys.argv[1])
    channel_name = sys.argv[2]
    data_type = sys.argv[3]
    
    result = read_wave_data(shot_no, channel_name, data_type)
    print(json.dumps(result))
