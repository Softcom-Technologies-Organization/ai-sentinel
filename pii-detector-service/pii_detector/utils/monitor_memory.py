"""
Memory Monitoring Script for PII Detection Service.

This script monitors the memory usage of the PII detection service and provides
real-time statistics and alerts.
"""

import psutil
import time
import sys
import argparse
import datetime
from typing import Optional


class MemoryMonitor:
    """Monitor memory usage of a process."""
    
    def __init__(self, pid: int, warning_threshold: float = 80.0, critical_threshold: float = 90.0):
        """
        Initialize the memory monitor.
        
        Args:
            pid: Process ID to monitor
            warning_threshold: Memory percentage for warning alert
            critical_threshold: Memory percentage for critical alert
        """
        self.pid = pid
        self.warning_threshold = warning_threshold
        self.critical_threshold = critical_threshold
        self.process = None
        self.peak_memory = 0
        self.start_time = time.time()
        
        try:
            self.process = psutil.Process(pid)
            self.initial_memory = self.process.memory_info().rss / 1024 / 1024
        except psutil.NoSuchProcess:
            print(f"Error: Process with PID {pid} not found")
            sys.exit(1)
    
    def get_memory_stats(self):
        """Get current memory statistics."""
        try:
            memory_info = self.process.memory_info()
            memory_mb = memory_info.rss / 1024 / 1024
            memory_percent = self.process.memory_percent()
            cpu_percent = self.process.cpu_percent(interval=0.1)
            
            # Update peak memory
            if memory_mb > self.peak_memory:
                self.peak_memory = memory_mb
            
            return {
                'memory_mb': memory_mb,
                'memory_percent': memory_percent,
                'cpu_percent': cpu_percent,
                'peak_memory_mb': self.peak_memory,
                'uptime_seconds': time.time() - self.start_time
            }
        except psutil.NoSuchProcess:
            return None
    
    def format_uptime(self, seconds: float) -> str:
        """Format uptime in human-readable format."""
        hours, remainder = divmod(int(seconds), 3600)
        minutes, seconds = divmod(remainder, 60)
        return f"{hours:02d}:{minutes:02d}:{seconds:02d}"
    
    def monitor(self, interval: int = 5, log_file: Optional[str] = None):
        """
        Start monitoring the process.
        
        Args:
            interval: Monitoring interval in seconds
            log_file: Optional file path to save logs
        """
        print(f"Monitoring PID {self.pid} - {self.process.name()}")
        print(f"Initial memory: {self.initial_memory:.2f} MB")
        print(f"Warning threshold: {self.warning_threshold}%")
        print(f"Critical threshold: {self.critical_threshold}%")
        print("-" * 80)
        
        log_handle = None
        if log_file:
            try:
                log_handle = open(log_file, 'a')
                log_handle.write(f"\n\n=== Memory monitoring started at {datetime.datetime.now()} ===\n")
            except Exception as e:
                print(f"Warning: Could not open log file: {e}")
        
        try:
            while True:
                stats = self.get_memory_stats()
                
                if stats is None:
                    print("\nProcess terminated")
                    break
                
                # Format output
                timestamp = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                uptime = self.format_uptime(stats['uptime_seconds'])
                
                # Determine alert level
                alert = ""
                if stats['memory_percent'] >= self.critical_threshold:
                    alert = " [CRITICAL]"
                elif stats['memory_percent'] >= self.warning_threshold:
                    alert = " [WARNING]"
                
                # Create output line
                output = (f"{timestamp} | "
                         f"Memory: {stats['memory_mb']:.2f} MB ({stats['memory_percent']:.1f}%) | "
                         f"CPU: {stats['cpu_percent']:.1f}% | "
                         f"Peak: {stats['peak_memory_mb']:.2f} MB | "
                         f"Uptime: {uptime}{alert}")
                
                print(output)
                
                # Write to log file if specified
                if log_handle:
                    log_handle.write(output + "\n")
                    log_handle.flush()
                
                # Check for critical memory usage
                if stats['memory_percent'] >= self.critical_threshold:
                    print(f"\n⚠️  CRITICAL: Memory usage is at {stats['memory_percent']:.1f}%!")
                    print("Consider restarting the service or investigating memory leaks.")
                
                time.sleep(interval)
                
        except KeyboardInterrupt:
            print("\n\nMonitoring stopped by user")
        except Exception as e:
            print(f"\nError during monitoring: {e}")
        finally:
            if log_handle:
                log_handle.close()


def find_process_by_name(name: str) -> Optional[int]:
    """Find process ID by name."""
    for proc in psutil.process_iter(['pid', 'name', 'cmdline']):
        try:
            # Check process name
            if name.lower() in proc.info['name'].lower():
                return proc.info['pid']
            
            # Check command line
            if proc.info['cmdline']:
                cmdline = ' '.join(proc.info['cmdline'])
                if name.lower() in cmdline.lower():
                    return proc.info['pid']
        except (psutil.NoSuchProcess, psutil.AccessDenied):
            continue
    
    return None


def main():
    """Main function."""
    parser = argparse.ArgumentParser(description="Monitor memory usage of PII Detection Service")
    
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--pid", type=int, help="Process ID to monitor")
    group.add_argument("--name", type=str, help="Process name to monitor (e.g., 'server.py')")
    
    parser.add_argument("--interval", type=int, default=5,
                       help="Monitoring interval in seconds (default: 5)")
    parser.add_argument("--warning", type=float, default=80.0,
                       help="Warning threshold percentage (default: 80)")
    parser.add_argument("--critical", type=float, default=90.0,
                       help="Critical threshold percentage (default: 90)")
    parser.add_argument("--log", type=str,
                       help="Log file path to save monitoring data")
    
    args = parser.parse_args()
    
    # Determine PID
    if args.pid:
        pid = args.pid
    else:
        pid = find_process_by_name(args.name)
        if pid is None:
            print(f"Error: Could not find process with name '{args.name}'")
            print("\nRunning processes:")
            for proc in psutil.process_iter(['pid', 'name']):
                try:
                    if 'python' in proc.info['name'].lower():
                        print(f"  PID {proc.info['pid']}: {proc.info['name']}")
                except (psutil.NoSuchProcess, psutil.AccessDenied):
                    continue
            sys.exit(1)
        print(f"Found process '{args.name}' with PID {pid}")
    
    # Create monitor and start monitoring
    monitor = MemoryMonitor(
        pid=pid,
        warning_threshold=args.warning,
        critical_threshold=args.critical
    )
    
    monitor.monitor(interval=args.interval, log_file=args.log)


if __name__ == "__main__":
    main()
